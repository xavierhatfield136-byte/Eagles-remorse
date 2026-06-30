import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

/** Bounded host-authoritative feasibility harness; not production transport code. */
public final class CooperativeNetworkPrototype {
    public enum Phase { LOBBY, READY, RUNNING, PAUSED, ENDED }
    public enum PacketType { COMMAND, PING, READY, PAUSE, LEAVE }

    public record Packet(long id, PacketType type, String playerId, CooperativeCommandSystem.Role role,
                         long sequence, String key, String value, long deliverAtMs) {}

    public static final class Client {
        public final String playerId;
        public boolean connected = true;
        public boolean ready;
        public long lastHostChecksum;
        public int latencyMs;
        public int packetLossPercent;
        public double uiScale = 1.0;
        public boolean highContrast;
        public boolean reducedFlash;
        public boolean subtitles = true;

        Client(String playerId, int latencyMs, int packetLossPercent) {
            this.playerId = playerId;
            this.latencyMs = Math.max(0, latencyMs);
            this.packetLossPercent = MathUtil.clamp(packetLossPercent, 0, 100);
        }
    }

    public static final class Session {
        public final String joinCode;
        public final String hostPlayerId;
        public final CooperativeCommandSystem.State authoritative = CooperativeCommandSystem.bootstrap();
        public final Map<String, Client> clients = new LinkedHashMap<>();
        public final List<Packet> inFlight = new ArrayList<>();
        public final List<String> diagnostics = new ArrayList<>();
        public Phase phase = Phase.LOBBY;
        public long clockMs;
        public long nextPacketId = 1;
        public double timeScale = 1.0;
        public int droppedPackets;
        public long authoritativeFrame;
        public String tacticalState = "tactical:initial";
        public String strategicState = "strategic:initial";
        public String sharedUiState = "ui:initial";

        Session(String joinCode, String hostPlayerId) {
            this.joinCode = joinCode;
            this.hostPlayerId = hostPlayerId;
            authoritative.saveOwnerId = hostPlayerId;
        }
    }

    private CooperativeNetworkPrototype() {}

    public static Session host(String joinCode, String hostPlayerId) {
        if (joinCode == null || joinCode.isBlank() || hostPlayerId == null || hostPlayerId.isBlank()) return null;
        Session session = new Session(joinCode.trim(), hostPlayerId.trim());
        session.clients.put(hostPlayerId.trim(), new Client(hostPlayerId.trim(), 0, 0));
        return session;
    }

    public static boolean join(Session session, String joinCode, String playerId, int latencyMs, int packetLossPercent) {
        if (session == null || session.phase != Phase.LOBBY || !session.joinCode.equals(joinCode)
                || playerId == null || playerId.isBlank() || session.clients.containsKey(playerId)
                || session.clients.size() >= session.authoritative.supportedPlayers) return false;
        session.clients.put(playerId, new Client(playerId, latencyMs, packetLossPercent));
        return true;
    }

    public static boolean assignRole(Session session, String playerId, CooperativeCommandSystem.Role role) {
        return session != null && session.phase == Phase.LOBBY && session.clients.containsKey(playerId)
                && CooperativeCommandSystem.assign(session.authoritative, role, playerId);
    }

    public static boolean configureAccessibility(Session session, String playerId, double uiScale,
                                                 boolean highContrast, boolean reducedFlash, boolean subtitles) {
        Client client = session == null ? null : session.clients.get(playerId);
        if (client == null) return false;
        client.uiScale = MathUtil.clamp(uiScale, 0.75, 2.0);
        client.highContrast = highContrast;
        client.reducedFlash = reducedFlash;
        client.subtitles = subtitles;
        return true;
    }

    public static boolean publishAuthoritativeFrame(Session session, String hostPlayerId,
                                                    String tacticalState, String strategicState,
                                                    String sharedUiState, double timeScale) {
        if (session == null || !session.hostPlayerId.equals(hostPlayerId) || session.phase == Phase.ENDED) return false;
        session.authoritativeFrame++;
        session.tacticalState = safeState(tacticalState, "tactical:empty");
        session.strategicState = safeState(strategicState, "strategic:empty");
        session.sharedUiState = safeState(sharedUiState, "ui:empty");
        session.timeScale = MathUtil.clamp(timeScale, 0.0, 4.0);
        long checksum = sessionChecksum(session);
        for (Client client : session.clients.values()) if (client.connected) client.lastHostChecksum = checksum;
        return true;
    }

    public static boolean setReady(Session session, String playerId, boolean ready) {
        Client client = session == null ? null : session.clients.get(playerId);
        if (client == null || !client.connected || session.phase == Phase.ENDED) return false;
        client.ready = ready;
        session.phase = session.clients.values().stream().filter(item -> item.connected).allMatch(item -> item.ready)
                ? Phase.READY : Phase.LOBBY;
        return true;
    }

    public static boolean launch(Session session, String hostPlayerId) {
        if (session == null || !session.hostPlayerId.equals(hostPlayerId) || session.phase != Phase.READY) return false;
        session.phase = Phase.RUNNING;
        return true;
    }

    public static boolean submit(Session session, PacketType type, String playerId, CooperativeCommandSystem.Role role,
                                 long sequence, String key, String value) {
        Client client = session == null ? null : session.clients.get(playerId);
        if (client == null || !client.connected || session.phase == Phase.ENDED) return false;
        long id = session.nextPacketId++;
        if (Math.floorMod(Long.hashCode(id * 31L + playerId.hashCode()), 100) < client.packetLossPercent) {
            session.droppedPackets++;
            session.diagnostics.add("DROP packet=" + id + " player=" + playerId);
            return true;
        }
        session.inFlight.add(new Packet(id, type, playerId, role, sequence, key, value,
                session.clockMs + client.latencyMs));
        return true;
    }

    public static void tick(Session session, long elapsedMs) {
        if (session == null || session.phase == Phase.ENDED) return;
        session.clockMs += Math.max(0, elapsedMs);
        session.inFlight.sort(Comparator.comparingLong(Packet::deliverAtMs).thenComparingLong(Packet::id));
        ArrayList<Packet> delivered = new ArrayList<>();
        for (Packet packet : session.inFlight) {
            if (packet.deliverAtMs() > session.clockMs) break;
            delivered.add(packet);
            applyHostPacket(session, packet);
        }
        session.inFlight.removeAll(delivered);
        long checksum = sessionChecksum(session);
        for (Client client : session.clients.values()) if (client.connected) client.lastHostChecksum = checksum;
    }

    private static void applyHostPacket(Session session, Packet packet) {
        switch (packet.type()) {
            case COMMAND -> {
                CooperativeCommandSystem.CommandResult result = CooperativeCommandSystem.submit(session.authoritative,
                        packet.role(), packet.playerId(), packet.sequence(), packet.key(), packet.value());
                session.diagnostics.add("COMMAND packet=" + packet.id() + " accepted=" + result.accepted()
                        + " reason=" + result.reason());
            }
            case PING -> CooperativeCommandSystem.postMessage(session.authoritative, packet.playerId(), "PING", packet.value());
            case PAUSE -> {
                if (packet.playerId().equals(session.hostPlayerId) || packet.role() == CooperativeCommandSystem.Role.CAPTAIN) {
                    session.phase = session.phase == Phase.PAUSED ? Phase.RUNNING : Phase.PAUSED;
                    session.timeScale = session.phase == Phase.PAUSED ? 0.0 : 1.0;
                }
            }
            case LEAVE -> disconnect(session, packet.playerId());
            case READY -> setReady(session, packet.playerId(), Boolean.parseBoolean(packet.value()));
        }
    }

    public static void disconnect(Session session, String playerId) {
        Client client = session == null ? null : session.clients.get(playerId);
        if (client == null) return;
        client.connected = false;
        CooperativeCommandSystem.disconnect(session.authoritative, playerId);
        if (playerId.equals(session.hostPlayerId)) {
            session.phase = Phase.ENDED;
            session.diagnostics.add("HOST_EXIT: migration unsupported; authoritative save remains with host");
        }
    }

    public static boolean reconnect(Session session, String playerId) {
        Client client = session == null ? null : session.clients.get(playerId);
        if (client == null || session.phase == Phase.ENDED) return false;
        client.connected = true;
        boolean restored = CooperativeCommandSystem.reconnect(session.authoritative, playerId);
        client.lastHostChecksum = sessionChecksum(session);
        return restored;
    }

    public static boolean checksumsMatch(Session session) {
        if (session == null) return false;
        long host = sessionChecksum(session);
        return session.clients.values().stream().filter(client -> client.connected)
                .allMatch(client -> client.lastHostChecksum == host);
    }

    public static long sessionChecksum(Session session) {
        if (session == null) return 0L;
        long hash = CooperativeCommandSystem.diagnosticChecksum(session.authoritative);
        hash = 31 * hash + session.authoritativeFrame;
        hash = 31 * hash + session.tacticalState.hashCode();
        hash = 31 * hash + session.strategicState.hashCode();
        hash = 31 * hash + session.sharedUiState.hashCode();
        hash = 31 * hash + Double.doubleToLongBits(session.timeScale);
        return hash;
    }

    public static String checkpoint(Session session) {
        if (session == null) return "";
        Base64.Encoder e = Base64.getUrlEncoder().withoutPadding();
        StringBuilder clients = new StringBuilder();
        for (Client client : session.clients.values()) {
            if (!clients.isEmpty()) clients.append(';');
            clients.append(enc(e, client.playerId)).append(':').append(client.connected).append(':')
                    .append(client.ready).append(':').append(client.latencyMs).append(':').append(client.packetLossPercent).append(':')
                    .append(client.uiScale).append(':').append(client.highContrast).append(':')
                    .append(client.reducedFlash).append(':').append(client.subtitles);
        }
        return enc(e, session.joinCode) + "|" + enc(e, session.hostPlayerId) + "|" + session.phase + "|"
                + session.clockMs + ":" + session.authoritativeFrame + ":" + session.timeScale + "|"
                + enc(e, session.tacticalState) + "|" + enc(e, session.strategicState) + "|"
                + enc(e, session.sharedUiState) + "|" + enc(e, CooperativeCommandSystem.serialize(session.authoritative))
                + "|" + clients;
    }

    public static Session restoreCheckpoint(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] p = raw.split("\\|", -1);
        if (p.length < 9) return null;
        String hostId = dec(p[1], "host");
        Session session = new Session(dec(p[0], "SESSION"), hostId);
        session.phase = enumeration(p[2], Phase.LOBBY);
        String[] timing = p[3].split(":", -1);
        session.clockMs = number(timing, 0, 0L);
        session.authoritativeFrame = number(timing, 1, 0L);
        session.timeScale = timing.length > 2 ? decimal(timing[2], 1.0) : 1.0;
        session.tacticalState = dec(p[4], "tactical:initial");
        session.strategicState = dec(p[5], "strategic:initial");
        session.sharedUiState = dec(p[6], "ui:initial");
        CooperativeCommandSystem.State restored = CooperativeCommandSystem.restore(dec(p[7], ""));
        copyAuthority(restored, session.authoritative);
        session.clients.clear();
        for (String rawClient : p[8].split(";")) {
            String[] f = rawClient.split(":", -1); if (f.length < 9) continue;
            Client client = new Client(dec(f[0], "client"), (int) decimal(f[3], 0), (int) decimal(f[4], 0));
            client.connected = Boolean.parseBoolean(f[1]); client.ready = Boolean.parseBoolean(f[2]);
            client.uiScale = MathUtil.clamp(decimal(f[5], 1.0), 0.75, 2.0);
            client.highContrast = Boolean.parseBoolean(f[6]); client.reducedFlash = Boolean.parseBoolean(f[7]);
            client.subtitles = Boolean.parseBoolean(f[8]); client.lastHostChecksum = sessionChecksum(session);
            session.clients.put(client.playerId, client);
        }
        return session;
    }

    private static void copyAuthority(CooperativeCommandSystem.State from, CooperativeCommandSystem.State to) {
        for (CooperativeCommandSystem.Role role : CooperativeCommandSystem.Role.values()) {
            CooperativeCommandSystem.Seat src = from.seats.get(role), dst = to.seats.get(role);
            dst.playerId = src.playerId; dst.connection = src.connection; dst.automated = src.automated;
            dst.lastAcceptedSequence = src.lastAcceptedSequence; dst.authority = src.authority;
        }
        to.acceptedCommands.clear(); to.acceptedCommands.putAll(from.acceptedCommands);
        to.sessionActive = from.sessionActive; to.supportedPlayers = from.supportedPlayers;
        to.captainOverrideEnabled = from.captainOverrideEnabled; to.tacticalPauseAllowed = from.tacticalPauseAllowed;
        to.hostMigrationPolicy = from.hostMigrationPolicy; to.saveOwnerId = from.saveOwnerId; to.votePolicy = from.votePolicy;
    }

    private static String safeState(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value.length() > 4096 ? value.substring(0, 4096) : value;
    }
    private static String enc(Base64.Encoder e, String value) { return e.encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)); }
    private static String dec(String raw, String fallback) { try { return new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8); } catch (Exception ignored) { return fallback; } }
    private static long number(String[] values, int index, long fallback) { try { return Long.parseLong(values[index]); } catch (Exception ignored) { return fallback; } }
    private static double decimal(String value, double fallback) { try { return Double.parseDouble(value); } catch (Exception ignored) { return fallback; } }
    private static <T extends Enum<T>> T enumeration(String value, T fallback) { try { return Enum.valueOf(fallback.getDeclaringClass(), value); } catch (Exception ignored) { return fallback; } }
}
