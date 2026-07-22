import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Direct TCP LAN transport slice for V1 multiplayer custom battles. */
public final class MultiplayerLanTransportV1 {
    public static final int DEFAULT_PORT = 46717;
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = MultiplayerTimeoutsV1.HANDSHAKE_TIMEOUT_MS;
    public static final int DEFAULT_ACCEPT_TIMEOUT_MS = MultiplayerTimeoutsV1.HANDSHAKE_TIMEOUT_MS;
    public static final int HEARTBEAT_INTERVAL_TICKS = MultiplayerTimeoutsV1.MATCH_HEARTBEAT_INTERVAL_TICKS;
    public static final int HEARTBEAT_TIMEOUT_TICKS = MultiplayerTimeoutsV1.MATCH_HEARTBEAT_TIMEOUT_TICKS;

    private static final String HELLO = "HELLO";
    private static final String ACCEPT = "ACCEPT";
    private static final String REJECT = "REJECT";
    private static final String HEARTBEAT = "HEARTBEAT";
    private static final String DISCONNECT = "DISCONNECT";
    private static final String INPUT = "INPUT";
    private static final String SNAPSHOT = "SNAPSHOT";
    private static final String ACK = "ACK";
    private static final String EVENT = "EVENT";
    private static final String LOBBY_STATE = "LOBBY_STATE";
    private static final String LOBBY_COMMAND = "LOBBY_COMMAND";

    private MultiplayerLanTransportV1() {}

    public record TransportRequirements(boolean reliableOrderedDelivery,
                                        boolean sequencedReplaceableSnapshots,
                                        boolean connectionOrientedSessions,
                                        boolean directIpJoin,
                                        boolean manualLanAddressJoin,
                                        boolean lanDiscovery,
                                        boolean encryption,
                                        boolean platformInvites,
                                        boolean relay,
                                        boolean internetHostingClaimed,
                                        String limitationSummary) {
        public TransportRequirements {
            limitationSummary = (limitationSummary == null || limitationSummary.isBlank())
                    ? "Direct LAN/manual IP only; no discovery, relay, NAT traversal, or internet hosting claim."
                    : limitationSummary.trim();
        }
    }

    public record DirectAddress(String host, int port) {
        public DirectAddress {
            host = (host == null || host.isBlank()) ? "127.0.0.1" : host.trim();
            if (port <= 0 || port > 65_535) {
                throw new IllegalArgumentException("LAN port must be between 1 and 65535");
            }
        }

        public InetSocketAddress socketAddress() {
            return new InetSocketAddress(host, port);
        }

        @Override
        public String toString() {
            return host + ":" + port;
        }
    }

    public record TransportResult(boolean accepted, String reason, ConnectedPeer peer) {
        public TransportResult {
            reason = (reason == null || reason.isBlank())
                    ? (accepted ? "Connected" : "Connection failed")
                    : reason.trim();
        }
    }

    public record LifecycleEntry(String event,
                                 String matchId,
                                 String connectionId,
                                 int playerSlotId,
                                 long hostTick,
                                 long commandSequence,
                                 long snapshotSequence,
                                 int protocolVersion,
                                 String gameBuild,
                                 String remoteAddress,
                                 String disconnectReason) {
        public LifecycleEntry {
            event = clean(event, "event");
            matchId = clean(matchId, "");
            connectionId = clean(connectionId, "");
            playerSlotId = Math.max(0, playerSlotId);
            hostTick = Math.max(-1L, hostTick);
            commandSequence = Math.max(-1L, commandSequence);
            snapshotSequence = Math.max(-1L, snapshotSequence);
            protocolVersion = Math.max(0, protocolVersion);
            gameBuild = clean(gameBuild, "dev");
            remoteAddress = clean(remoteAddress, "");
            disconnectReason = clean(disconnectReason, "");
        }
    }

    public static final class LifecycleLog {
        private final ArrayList<LifecycleEntry> entries = new ArrayList<>();

        public synchronized void record(String event, String matchId, String connectionId,
                                        int playerSlotId, long hostTick,
                                        long commandSequence, long snapshotSequence,
                                        String gameBuild, String remoteAddress,
                                        String disconnectReason) {
            entries.add(new LifecycleEntry(event, matchId, connectionId, playerSlotId,
                    hostTick, commandSequence, snapshotSequence,
                    MultiplayerProtocolV1.PROTOCOL_VERSION,
                    gameBuild, remoteAddress, disconnectReason));
        }

        public synchronized List<LifecycleEntry> entries() {
            return List.copyOf(entries);
        }

        public synchronized boolean containsEvent(String event) {
            for (LifecycleEntry entry : entries) {
                if (entry.event().equals(event)) return true;
            }
            return false;
        }
    }

    public enum WireKind {
        CLIENT_INPUT,
        FULL_SNAPSHOT,
        INPUT_ACK,
        LOBBY_STATE,
        LOBBY_COMMAND,
        AUTHORITATIVE_EVENT,
        HEARTBEAT,
        DISCONNECT,
        ERROR
    }

    public record WireMessage(WireKind kind,
                              long hostTick,
                              String text,
                              long sequence,
                              MultiplayerCommandGate.PlayerInputFrame inputFrame,
                              MultiplayerBattleSnapshot snapshot,
                              MultiplayerProtocolV1.InputAck inputAck,
                              MultiplayerReplicationV1.AuthoritativeEvent event) {
        public WireMessage(WireKind kind, long hostTick, String text) {
            this(kind, hostTick, text, -1L, null, null, null, null);
        }

        public WireMessage {
            if (kind == null) kind = WireKind.ERROR;
            hostTick = Math.max(0L, hostTick);
            text = text == null ? "" : text.trim();
            sequence = Math.max(-1L, sequence);
        }
    }

    public static TransportRequirements requirements() {
        return new TransportRequirements(
                true,
                true,
                true,
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                "V1 supports direct LAN/manual IP only. Firewalls may block inbound hosting. "
                        + "NAT traversal, internet hosting, relay, encryption, platform invites, and LAN discovery are unsupported.");
    }

    public static DirectAddress parseDirectAddress(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) return new DirectAddress("127.0.0.1", DEFAULT_PORT);
        if (text.startsWith("[") && text.contains("]")) {
            int close = text.indexOf(']');
            String host = text.substring(1, close);
            int port = DEFAULT_PORT;
            if (close + 1 < text.length()) {
                if (text.charAt(close + 1) != ':') {
                    throw new IllegalArgumentException("IPv6 LAN address must use [host]:port format");
                }
                port = parsePort(text.substring(close + 2));
            }
            return new DirectAddress(host, port);
        }
        int colon = text.lastIndexOf(':');
        if (colon > 0 && text.indexOf(':') == colon) {
            return new DirectAddress(text.substring(0, colon), parsePort(text.substring(colon + 1)));
        }
        if (text.indexOf(':') >= 0) {
            throw new IllegalArgumentException("IPv6 LAN address must use [host]:port format");
        }
        return new DirectAddress(text, DEFAULT_PORT);
    }

    public static List<DirectAddress> detectedPrivateLanAddresses(int port) {
        ArrayList<DirectAddress> out = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (network == null || !network.isUp() || network.isLoopback()) continue;
                Enumeration<InetAddress> addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (isPrivateLanAddress(address)) {
                        out.add(new DirectAddress(address.getHostAddress(), port));
                    }
                }
            }
        } catch (IOException ignored) {
            return List.of();
        }
        out.sort(Comparator.comparing(DirectAddress::host).thenComparingInt(DirectAddress::port));
        return List.copyOf(out);
    }

    public static boolean isPrivateLanAddress(InetAddress address) {
        return address != null
                && !address.isAnyLocalAddress()
                && !address.isLoopbackAddress()
                && !address.isLinkLocalAddress()
                && address.isSiteLocalAddress()
                && !address.getHostAddress().contains(":");
    }

    public static Host bindLoopback(int port, String matchId, LifecycleLog log) throws IOException {
        return bind(new DirectAddress("127.0.0.1", port <= 0 ? 1 : port), true, matchId, log);
    }

    public static Host bindAny(int port, String matchId, LifecycleLog log) throws IOException {
        return bind(new DirectAddress("0.0.0.0", port <= 0 ? DEFAULT_PORT : port), false, matchId, log);
    }

    public static TransportResult connect(DirectAddress address,
                                          MultiplayerProtocolV1.CompatibilityFingerprint clientFingerprint,
                                          int playerSlotId,
                                          String matchId,
                                          LifecycleLog log,
                                          int timeoutMs) {
        String connectionId = UUID.randomUUID().toString();
        String remote = address == null ? "" : address.toString();
        if (address == null) {
            return new TransportResult(false, "Missing LAN address", null);
        }
        int timeout = Math.max(1, timeoutMs);
        if (log != null) {
            log.record("connect_attempt", matchId, connectionId, playerSlotId,
                    -1L, -1L, -1L, build(clientFingerprint), remote, "");
        }
        try {
            Socket socket = new Socket();
            socket.connect(address.socketAddress(), timeout);
            socket.setSoTimeout(timeout);
            BufferedReader reader = reader(socket);
            BufferedWriter writer = writer(socket);
            writer.write(encodeHello(clientFingerprint));
            writer.newLine();
            writer.flush();

            String response = reader.readLine();
            TransportResult result = decodeConnectResponse(socket, reader, writer, response,
                    connectionId, playerSlotId, matchId, log, clientFingerprint);
            if (!result.accepted()) {
                closeQuietly(socket);
            }
            return result;
        } catch (IOException | IllegalArgumentException ex) {
            if (log != null) {
                log.record("connect_failed", matchId, connectionId, playerSlotId,
                        -1L, -1L, -1L, build(clientFingerprint), remote, ex.getMessage());
            }
            return new TransportResult(false, readableIoReason(ex), null);
        }
    }

    public static final class Host implements Closeable {
        private final ServerSocket serverSocket;
        private final String matchId;
        private final LifecycleLog log;

        private Host(ServerSocket serverSocket, String matchId, LifecycleLog log) {
            this.serverSocket = serverSocket;
            this.matchId = clean(matchId, UUID.randomUUID().toString());
            this.log = log;
        }

        public DirectAddress boundAddress() {
            InetAddress address = serverSocket.getInetAddress();
            String host = address == null || address.isAnyLocalAddress()
                    ? "127.0.0.1"
                    : address.getHostAddress();
            return new DirectAddress(host, serverSocket.getLocalPort());
        }

        public TransportResult acceptOnce(MultiplayerProtocolV1.CompatibilityFingerprint hostFingerprint,
                                          int playerSlotId,
                                          int timeoutMs) {
            String connectionId = UUID.randomUUID().toString();
            try {
                serverSocket.setSoTimeout(Math.max(1, timeoutMs));
                Socket socket = serverSocket.accept();
                socket.setSoTimeout(Math.max(1, timeoutMs));
                String remote = String.valueOf(socket.getRemoteSocketAddress());
                if (log != null) {
                    log.record("accept", matchId, connectionId, playerSlotId,
                            -1L, -1L, -1L, build(hostFingerprint), remote, "");
                }
                BufferedReader reader = reader(socket);
                BufferedWriter writer = writer(socket);
                String hello = reader.readLine();
                MultiplayerProtocolV1.CompatibilityFingerprint client = decodeHello(hello);
                MultiplayerProtocolV1.CompatibilityResult compatibility =
                        MultiplayerProtocolV1.validateCompatibility(hostFingerprint, client);
                if (!compatibility.accepted()) {
                    writer.write(REJECT + "|" + encodeText(compatibility.reason()));
                    writer.newLine();
                    writer.flush();
                    closeQuietly(socket);
                    if (log != null) {
                        log.record("reject", matchId, connectionId, playerSlotId,
                                -1L, -1L, -1L, build(hostFingerprint), remote, compatibility.reason());
                    }
                    return new TransportResult(false, compatibility.reason(), null);
                }
                writer.write(ACCEPT + "|" + encodeText(connectionId));
                writer.newLine();
                writer.flush();
                ConnectedPeer peer = new ConnectedPeer(socket, reader, writer,
                        matchId, connectionId, playerSlotId, build(hostFingerprint), remote, log);
                if (log != null) {
                    log.record("connected", matchId, connectionId, playerSlotId,
                            -1L, -1L, -1L, build(hostFingerprint), remote, "");
                }
                return new TransportResult(true, "Connected", peer);
            } catch (IOException | IllegalArgumentException ex) {
                if (log != null) {
                    log.record("accept_failed", matchId, connectionId, playerSlotId,
                            -1L, -1L, -1L, build(hostFingerprint), "", ex.getMessage());
                }
                return new TransportResult(false, readableIoReason(ex), null);
            }
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
        }
    }

    public static final class ConnectedPeer implements Closeable {
        private final Socket socket;
        private final BufferedReader reader;
        private final BufferedWriter writer;
        private final String matchId;
        private final String connectionId;
        private final int playerSlotId;
        private final String gameBuild;
        private final String remoteAddress;
        private final LifecycleLog log;
        private long lastHeartbeatTick;
        private long lastValidMessageTick;
        private long lastOutboundMessageTick;
        private boolean disconnected;

        private ConnectedPeer(Socket socket, BufferedReader reader, BufferedWriter writer,
                              String matchId, String connectionId, int playerSlotId,
                              String gameBuild, String remoteAddress, LifecycleLog log) {
            this.socket = socket;
            this.reader = reader;
            this.writer = writer;
            this.matchId = matchId;
            this.connectionId = connectionId;
            this.playerSlotId = Math.max(0, playerSlotId);
            this.gameBuild = clean(gameBuild, "dev");
            this.remoteAddress = clean(remoteAddress, "");
            this.log = log;
        }

        public String connectionId() {
            return connectionId;
        }

        public int playerSlotId() {
            return playerSlotId;
        }

        public String matchId() {
            return matchId;
        }

        public String localEndpoint() {
            return endpoint(socket.getLocalSocketAddress());
        }

        public String remoteEndpoint() {
            return endpoint(socket.getRemoteSocketAddress());
        }

        public long lastHeartbeatTick() {
            return lastHeartbeatTick;
        }

        public long lastValidMessageTick() {
            return lastValidMessageTick;
        }

        public long lastOutboundMessageTick() {
            return lastOutboundMessageTick;
        }

        public boolean disconnected() {
            return disconnected;
        }

        public void noteValidTraffic(long currentHostTick) {
            markValidMessage(currentHostTick);
        }

        public boolean heartbeatTimedOut(long currentHostTick) {
            return peerTimedOut(currentHostTick);
        }

        public boolean peerTimedOut(long currentHostTick) {
            return Math.max(0L, currentHostTick) - lastValidMessageTick > HEARTBEAT_TIMEOUT_TICKS;
        }

        public boolean markDisconnectedIfTimedOut(long currentHostTick) {
            if (!peerTimedOut(currentHostTick)) return false;
            disconnected = true;
            if (log != null) {
                log.record("peer_timeout", matchId, connectionId, playerSlotId,
                        Math.max(0L, currentHostTick), -1L, -1L,
                        gameBuild, remoteAddress, "Heartbeat timeout");
            }
            closeQuietly(socket);
            return true;
        }

        public void setReadTimeoutMs(int timeoutMs) throws IOException {
            socket.setSoTimeout(Math.max(1, timeoutMs));
        }

        public synchronized void sendHeartbeat(long hostTick) throws IOException {
            long safeTick = Math.max(0L, hostTick);
            writer.write(HEARTBEAT + "|" + safeTick);
            writer.newLine();
            writer.flush();
            markOutboundMessage(safeTick);
            if (log != null) {
                log.record("heartbeat_send", matchId, connectionId, playerSlotId,
                        safeTick, -1L, -1L, gameBuild, remoteAddress, "");
            }
        }

        public synchronized boolean sendHeartbeatIfIdle(long currentHostTick) throws IOException {
            long safeTick = Math.max(0L, currentHostTick);
            if (safeTick - lastOutboundMessageTick < HEARTBEAT_INTERVAL_TICKS) return false;
            sendHeartbeat(safeTick);
            return true;
        }

        public synchronized void sendDisconnect(String reason) throws IOException {
            String cleanReason = clean(reason, "Disconnected");
            writer.write(DISCONNECT + "|" + encodeText(cleanReason));
            writer.newLine();
            writer.flush();
            markOutboundMessage(lastOutboundMessageTick);
            disconnected = true;
            if (log != null) {
                log.record("disconnect_send", matchId, connectionId, playerSlotId,
                        -1L, -1L, -1L, gameBuild, remoteAddress, cleanReason);
            }
        }

        public synchronized void sendInputFrame(MultiplayerCommandGate.PlayerInputFrame frame) throws IOException {
            if (frame == null) throw new IOException("Missing LAN input frame");
            writer.write(INPUT + "|"
                    + encodeText(frame.matchId()) + '|'
                    + encodeText(frame.sessionNonce()) + '|'
                    + encodeText(frame.playerId()) + '|'
                    + frame.commandType().name() + '|'
                    + frame.slotId() + '|'
                    + frame.controlledShipId() + '|'
                    + frame.sequence() + '|'
                    + frame.clientTick() + '|'
                    + frame.thrust() + '|'
                    + frame.turn() + '|'
                    + frame.aimAngle() + '|'
                    + frame.primaryHeld() + '|'
                    + frame.secondaryHeld());
            writer.newLine();
            writer.flush();
            markOutboundMessage(frame.clientTick());
            if (log != null) {
                log.record("input_send", matchId, connectionId, playerSlotId,
                        frame.clientTick(), frame.sequence(), -1L, gameBuild, remoteAddress, "");
            }
        }

        public synchronized void sendSnapshot(long snapshotSequence,
                                              MultiplayerBattleSnapshot snapshot) throws IOException {
            byte[] payload = MultiplayerSerializationV1.encodeSnapshot(snapshot);
            MultiplayerProtocolV1.ProtocolValidation validation =
                    MultiplayerProtocolV1.validatePayloadBytes(payload);
            if (!validation.accepted()) throw new IOException(validation.reason());
            long hostTick = snapshot == null ? 0L : snapshot.hostTick();
            writer.write(SNAPSHOT + "|" + Math.max(0L, snapshotSequence) + "|"
                    + encodeBytes(payload));
            writer.newLine();
            writer.flush();
            markOutboundMessage(hostTick);
            if (log != null) {
                log.record("snapshot_send", matchId, connectionId, playerSlotId,
                        hostTick, -1L, snapshotSequence, gameBuild, remoteAddress, "");
            }
        }

        public synchronized void sendInputAck(MultiplayerProtocolV1.InputAck ack) throws IOException {
            if (ack == null) throw new IOException("Missing LAN input acknowledgement");
            writer.write(ACK + "|"
                    + ack.slotId() + '|'
                    + ack.inputSequence() + '|'
                    + ack.authoritativeTick());
            writer.newLine();
            writer.flush();
            markOutboundMessage(ack.authoritativeTick());
            if (log != null) {
                log.record("ack_send", matchId, connectionId, playerSlotId,
                        ack.authoritativeTick(), ack.inputSequence(), -1L, gameBuild, remoteAddress, "");
            }
        }

        public synchronized void sendLobbyState(String payload) throws IOException {
            writer.write(LOBBY_STATE + "|" + encodeText(payload));
            writer.newLine();
            writer.flush();
            markOutboundMessage(lastOutboundMessageTick);
        }

        public synchronized void sendLobbyCommand(String payload) throws IOException {
            writer.write(LOBBY_COMMAND + "|" + encodeText(payload));
            writer.newLine();
            writer.flush();
            markOutboundMessage(lastOutboundMessageTick);
        }

        public synchronized void sendEvent(MultiplayerReplicationV1.AuthoritativeEvent event) throws IOException {
            byte[] payload = MultiplayerSerializationV1.encodeEvent(event);
            MultiplayerProtocolV1.ProtocolValidation validation =
                    MultiplayerProtocolV1.validatePayloadBytes(payload);
            if (!validation.accepted()) throw new IOException(validation.reason());
            long sequence = event == null ? 0L : event.eventSequence();
            long hostTick = event == null ? 0L : event.hostTick();
            writer.write(EVENT + "|" + sequence + "|" + encodeBytes(payload));
            writer.newLine();
            writer.flush();
            markOutboundMessage(hostTick);
            if (log != null) {
                log.record("event_send", matchId, connectionId, playerSlotId,
                        hostTick, -1L, -1L, gameBuild, remoteAddress, "");
            }
        }

        public WireMessage readNextMessage() throws IOException {
            String line;
            try {
                line = reader.readLine();
            } catch (SocketTimeoutException ex) {
                if (log != null) {
                    log.record("peer_read_timeout", matchId, connectionId, playerSlotId,
                            lastValidMessageTick, -1L, -1L, gameBuild, remoteAddress, ex.getMessage());
                }
                throw ex;
            } catch (IOException ex) {
                if (log != null) {
                    log.record("peer_read_failure", matchId, connectionId, playerSlotId,
                            lastValidMessageTick, -1L, -1L, gameBuild, remoteAddress, ex.getMessage());
                }
                throw ex;
            }
            if (line == null) {
                disconnected = true;
                if (log != null) {
                    log.record("peer_closed_graceful", matchId, connectionId, playerSlotId,
                            lastValidMessageTick, -1L, -1L, gameBuild, remoteAddress, "Peer closed");
                }
                return new WireMessage(WireKind.DISCONNECT, lastValidMessageTick, "Peer closed");
            }
            if (line.getBytes(StandardCharsets.UTF_8).length > MultiplayerProtocolV1.MAX_MESSAGE_BYTES) {
                throw new IOException("LAN message exceeds size limit");
            }
            String[] parts = line.split("\\|", 2);
            String kind = parts[0].trim().toUpperCase(Locale.ROOT);
            if (HEARTBEAT.equals(kind)) {
                long tick = parseLong(parts.length > 1 ? parts[1] : "0");
                lastHeartbeatTick = tick;
                markValidMessage(tick);
                if (log != null) {
                    log.record("heartbeat_receive", matchId, connectionId, playerSlotId,
                            tick, -1L, -1L, gameBuild, remoteAddress, "");
                }
                return new WireMessage(WireKind.HEARTBEAT, tick, "");
            }
            if (DISCONNECT.equals(kind)) {
                String reason = parts.length > 1 ? decodeText(parts[1]) : "Disconnected";
                disconnected = true;
                markValidMessage(lastValidMessageTick);
                if (log != null) {
                    log.record("disconnect_receive", matchId, connectionId, playerSlotId,
                            -1L, -1L, -1L, gameBuild, remoteAddress, reason);
                }
                return new WireMessage(WireKind.DISCONNECT, lastHeartbeatTick, reason);
            }
            if (INPUT.equals(kind)) {
                MultiplayerCommandGate.PlayerInputFrame frame = decodeInput(parts.length > 1 ? parts[1] : "");
                markValidMessage(frame.clientTick());
                if (log != null) {
                    log.record("input_receive", matchId, connectionId, playerSlotId,
                            frame.clientTick(), frame.sequence(), -1L, gameBuild, remoteAddress, "");
                }
                return new WireMessage(WireKind.CLIENT_INPUT, frame.clientTick(), "",
                        frame.sequence(), frame, null, null, null);
            }
            if (SNAPSHOT.equals(kind)) {
                String[] fields = splitFields(parts.length > 1 ? parts[1] : "", 2, "Malformed LAN snapshot");
                long sequence = parseLong(fields[0]);
                MultiplayerBattleSnapshot snapshot =
                        MultiplayerSerializationV1.decodeSnapshot(decodeBytes(fields[1]));
                markValidMessage(snapshot.hostTick());
                if (log != null) {
                    log.record("snapshot_receive", matchId, connectionId, playerSlotId,
                            snapshot.hostTick(), -1L, sequence, gameBuild, remoteAddress, "");
                }
                return new WireMessage(WireKind.FULL_SNAPSHOT, snapshot.hostTick(), "",
                        sequence, null, snapshot, null, null);
            }
            if (ACK.equals(kind)) {
                String[] fields = splitFields(parts.length > 1 ? parts[1] : "", 3, "Malformed LAN input acknowledgement");
                MultiplayerProtocolV1.InputAck ack = new MultiplayerProtocolV1.InputAck(
                        parseInt(fields[0]), parseLong(fields[1]), parseLong(fields[2]));
                markValidMessage(ack.authoritativeTick());
                if (log != null) {
                    log.record("ack_receive", matchId, connectionId, playerSlotId,
                            ack.authoritativeTick(), ack.inputSequence(), -1L, gameBuild, remoteAddress, "");
                }
                return new WireMessage(WireKind.INPUT_ACK, ack.authoritativeTick(), "",
                        ack.inputSequence(), null, null, ack, null);
            }
            if (LOBBY_STATE.equals(kind)) {
                String payload = parts.length > 1 ? decodeText(parts[1]) : "";
                markValidMessage(lastValidMessageTick);
                return new WireMessage(WireKind.LOBBY_STATE, lastHeartbeatTick, payload);
            }
            if (LOBBY_COMMAND.equals(kind)) {
                String payload = parts.length > 1 ? decodeText(parts[1]) : "";
                markValidMessage(lastValidMessageTick);
                return new WireMessage(WireKind.LOBBY_COMMAND, lastHeartbeatTick, payload);
            }
            if (EVENT.equals(kind)) {
                String[] fields = splitFields(parts.length > 1 ? parts[1] : "", 2, "Malformed LAN event");
                long sequence = parseLong(fields[0]);
                MultiplayerReplicationV1.AuthoritativeEvent event =
                        MultiplayerSerializationV1.decodeEvent(decodeBytes(fields[1]));
                markValidMessage(event.hostTick());
                if (log != null) {
                    log.record("event_receive", matchId, connectionId, playerSlotId,
                            event.hostTick(), -1L, -1L, gameBuild, remoteAddress, "");
                }
                return new WireMessage(WireKind.AUTHORITATIVE_EVENT, event.hostTick(), "",
                        sequence, null, null, null, event);
            }
            return new WireMessage(WireKind.ERROR, lastHeartbeatTick, "Unknown LAN message: " + kind);
        }

        @Override
        public void close() throws IOException {
            disconnected = true;
            socket.close();
        }

        private void markValidMessage(long hostTick) {
            lastValidMessageTick = Math.max(lastValidMessageTick, Math.max(0L, hostTick));
        }

        private void markOutboundMessage(long hostTick) {
            lastOutboundMessageTick = Math.max(lastOutboundMessageTick, Math.max(0L, hostTick));
        }
    }

    private static Host bind(DirectAddress address, boolean loopbackOnly,
                             String matchId, LifecycleLog log) throws IOException {
        ServerSocket server = new ServerSocket();
        InetAddress bindAddress = loopbackOnly
                ? InetAddress.getByName("127.0.0.1")
                : InetAddress.getByName(address.host());
        server.bind(new InetSocketAddress(bindAddress, address.port() == 1 ? 0 : address.port()));
        if (log != null) {
            log.record(loopbackOnly ? "bind_loopback" : "bind_lan",
                    matchId, "", 0, -1L, -1L, -1L,
                    MultiplayerProtocolV1.localFingerprint().gameBuild(),
                    server.getLocalSocketAddress().toString(), "");
        }
        return new Host(server, matchId, log);
    }

    private static TransportResult decodeConnectResponse(Socket socket, BufferedReader reader,
                                                         BufferedWriter writer, String response,
                                                         String connectionId, int playerSlotId,
                                                         String matchId, LifecycleLog log,
                                                         MultiplayerProtocolV1.CompatibilityFingerprint clientFingerprint) {
        if (response == null) return new TransportResult(false, "Host closed connection during handshake", null);
        String[] parts = response.split("\\|", 2);
        String kind = parts[0].trim().toUpperCase(Locale.ROOT);
        String remote = String.valueOf(socket.getRemoteSocketAddress());
        if (ACCEPT.equals(kind)) {
            String acceptedConnectionId = parts.length > 1 ? decodeText(parts[1]) : connectionId;
            ConnectedPeer peer = new ConnectedPeer(socket, reader, writer, matchId,
                    clean(acceptedConnectionId, connectionId), playerSlotId,
                    build(clientFingerprint), remote, log);
            if (log != null) {
                log.record("connected", matchId, peer.connectionId(), playerSlotId,
                        -1L, -1L, -1L, build(clientFingerprint), remote, "");
            }
            return new TransportResult(true, "Connected", peer);
        }
        if (REJECT.equals(kind)) {
            String reason = parts.length > 1 ? decodeText(parts[1]) : "Host rejected connection";
            if (log != null) {
                log.record("connect_rejected", matchId, connectionId, playerSlotId,
                        -1L, -1L, -1L, build(clientFingerprint), remote, reason);
            }
            return new TransportResult(false, reason, null);
        }
        return new TransportResult(false, "Malformed host handshake response", null);
    }

    private static String encodeHello(MultiplayerProtocolV1.CompatibilityFingerprint fingerprint) {
        MultiplayerProtocolV1.CompatibilityFingerprint safe = fingerprint == null
                ? MultiplayerProtocolV1.localFingerprint()
                : fingerprint;
        MultiplayerProtocolV1.ContentManifest manifest = safe.manifest();
        return String.join("|",
                HELLO,
                String.valueOf(safe.protocolVersion()),
                encodeText(safe.gameBuild()),
                encodeText(manifest.rulesHash()),
                encodeText(manifest.hullDefinitionsHash()),
                encodeText(manifest.weaponsHash()),
                encodeText(manifest.abilitiesHash()),
                encodeText(manifest.arenaHash()),
                encodeText(manifest.enabledModsHash()),
                encodeText(manifest.requiredAssetsHash()));
    }

    private static String endpoint(java.net.SocketAddress address) {
        if (address instanceof InetSocketAddress inet) {
            InetAddress host = inet.getAddress();
            String text = host == null ? inet.getHostString() : host.getHostAddress();
            return clean(text, "") + ":" + inet.getPort();
        }
        return String.valueOf(address);
    }

    private static MultiplayerProtocolV1.CompatibilityFingerprint decodeHello(String line) {
        if (line == null) throw new IllegalArgumentException("Missing LAN hello");
        if (line.getBytes(StandardCharsets.UTF_8).length > MultiplayerProtocolV1.MAX_MESSAGE_BYTES) {
            throw new IllegalArgumentException("LAN hello exceeds size limit");
        }
        String[] parts = line.split("\\|", -1);
        if (parts.length != 10 || !HELLO.equals(parts[0])) {
            throw new IllegalArgumentException("Malformed LAN hello");
        }
        MultiplayerProtocolV1.ContentManifest manifest = new MultiplayerProtocolV1.ContentManifest(
                decodeText(parts[3]), decodeText(parts[4]), decodeText(parts[5]),
                decodeText(parts[6]), decodeText(parts[7]), decodeText(parts[8]), decodeText(parts[9]));
        return new MultiplayerProtocolV1.CompatibilityFingerprint(
                parseInt(parts[1]), decodeText(parts[2]), manifest);
    }

    private static BufferedReader reader(Socket socket) throws IOException {
        return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    }

    private static BufferedWriter writer(Socket socket) throws IOException {
        return new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    private static int parsePort(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid LAN port: " + text);
        }
    }

    private static int parseInt(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid LAN protocol version: " + text);
        }
    }

    private static long parseLong(String text) {
        try {
            return Long.parseLong(text.trim());
        } catch (RuntimeException ex) {
            throw new IOExceptionUnchecked("Invalid LAN tick: " + text);
        }
    }

    private static MultiplayerCommandGate.PlayerInputFrame decodeInput(String payload) {
        String[] fields = (payload == null ? "" : payload).split("\\|", -1);
        if (fields.length == 13) {
            return new MultiplayerCommandGate.PlayerInputFrame(
                    decodeText(fields[0]),
                    decodeText(fields[1]),
                    decodeText(fields[2]),
                    parseGameplayCommandType(fields[3]),
                    parseInt(fields[4]),
                    parseInt(fields[5]),
                    parseLong(fields[6]),
                    parseLong(fields[7]),
                    parseFloat(fields[8]),
                    parseFloat(fields[9]),
                    parseDouble(fields[10]),
                    Boolean.parseBoolean(fields[11]),
                    Boolean.parseBoolean(fields[12]));
        }
        if (fields.length == 12) {
            return new MultiplayerCommandGate.PlayerInputFrame(
                    decodeText(fields[0]),
                    decodeText(fields[1]),
                    decodeText(fields[2]),
                    parseInt(fields[3]),
                    parseInt(fields[4]),
                    parseLong(fields[5]),
                    parseLong(fields[6]),
                    parseFloat(fields[7]),
                    parseFloat(fields[8]),
                    parseDouble(fields[9]),
                    Boolean.parseBoolean(fields[10]),
                    Boolean.parseBoolean(fields[11]));
        }
        if (fields.length != 9) throw new IllegalArgumentException("Malformed LAN input frame");
        return new MultiplayerCommandGate.PlayerInputFrame(
                parseInt(fields[0]),
                parseInt(fields[1]),
                parseLong(fields[2]),
                parseLong(fields[3]),
                parseFloat(fields[4]),
                parseFloat(fields[5]),
                parseDouble(fields[6]),
                Boolean.parseBoolean(fields[7]),
                Boolean.parseBoolean(fields[8]));
    }

    private static MultiplayerCommandGate.GameplayCommandType parseGameplayCommandType(String text) {
        try {
            return MultiplayerCommandGate.GameplayCommandType.valueOf(
                    clean(text, MultiplayerCommandGate.GameplayCommandType.DIRECT_SHIP_INPUT.name())
                            .toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            return MultiplayerCommandGate.GameplayCommandType.DIRECT_SHIP_INPUT;
        }
    }

    private static String[] splitFields(String payload, int expected, String message) {
        String[] fields = (payload == null ? "" : payload).split("\\|", -1);
        if (fields.length != expected) throw new IllegalArgumentException(message);
        return fields;
    }

    private static float parseFloat(String text) {
        try {
            return Float.parseFloat(text.trim());
        } catch (RuntimeException ex) {
            throw new IOExceptionUnchecked("Invalid LAN float: " + text);
        }
    }

    private static double parseDouble(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (RuntimeException ex) {
            throw new IOExceptionUnchecked("Invalid LAN double: " + text);
        }
    }

    private static String encodeBytes(byte[] payload) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload == null ? new byte[0] : payload);
    }

    private static byte[] decodeBytes(String value) {
        if (value == null || value.isBlank()) return new byte[0];
        return Base64.getUrlDecoder().decode(value);
    }

    private static String encodeText(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(clean(value, "").getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeText(String value) {
        if (value == null || value.isBlank()) return "";
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static String build(MultiplayerProtocolV1.CompatibilityFingerprint fingerprint) {
        return fingerprint == null ? "dev" : fingerprint.gameBuild();
    }

    private static String clean(String value, String fallback) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static String readableIoReason(Exception ex) {
        String message = ex == null ? "" : ex.getMessage();
        if (message == null || message.isBlank()) return "LAN connection failed";
        return message;
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // best effort
        }
    }

    private static final class IOExceptionUnchecked extends RuntimeException {
        IOExceptionUnchecked(String message) {
            super(message);
        }
    }
}
