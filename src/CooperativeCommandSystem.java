import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Networking-neutral authority prototype for cooperative command roles. */
public final class CooperativeCommandSystem {
    public enum Role { CAPTAIN, HELM, TACTICAL, ENGINEERING, SCIENCE, STRATEGIC_COMMAND }
    public enum Connection { CONNECTED, DISCONNECTED }
    public enum Authority { VIEW, PROPOSE, MODIFY, OWN }
    public enum VotePolicy { CAPTAIN_DECIDES, MAJORITY, UNANIMOUS }

    public static final class Seat {
        public final Role role;
        public String playerId = "";
        public Connection connection = Connection.DISCONNECTED;
        public boolean automated = true;
        public long lastAcceptedSequence;
        public String responsibilities = "";
        public Authority authority = Authority.OWN;

        Seat(Role role) { this.role = role; }
    }

    public record CommandResult(boolean accepted, String reason, long sequence) {}

    public static final class State {
        public final EnumMap<Role, Seat> seats = new EnumMap<>(Role.class);
        public final Map<String, String> acceptedCommands = new LinkedHashMap<>();
        public final List<String> sharedMessages = new ArrayList<>();
        public boolean sessionActive;
        public int supportedPlayers = 6;
        public boolean captainOverrideEnabled = true;
        public boolean tacticalPauseAllowed = true;
        public String hostMigrationPolicy = "Unsupported in prototype; campaign host owns the save";
        public String saveOwnerId = "host";
        public VotePolicy votePolicy = VotePolicy.CAPTAIN_DECIDES;
        public String pendingVote = "";
        public final Map<String, Boolean> votes = new LinkedHashMap<>();

        State() {
            for (Role role : Role.values()) {
                Seat seat = new Seat(role);
                seat.responsibilities = responsibilities(role);
                seats.put(role, seat);
            }
        }
    }

    private CooperativeCommandSystem() {}
    public static State bootstrap() { return new State(); }

    public static boolean assign(State state, Role role, String playerId) {
        if (state == null || role == null || playerId == null || playerId.isBlank()) return false;
        for (Seat seat : state.seats.values()) {
            if (playerId.equals(seat.playerId)) {
                seat.playerId = "";
                seat.connection = Connection.DISCONNECTED;
                seat.automated = true;
            }
        }
        Seat seat = state.seats.get(role);
        seat.playerId = playerId.trim();
        seat.connection = Connection.CONNECTED;
        seat.automated = false;
        state.sessionActive = true;
        return true;
    }

    public static void disconnect(State state, String playerId) {
        if (state == null || playerId == null) return;
        for (Seat seat : state.seats.values()) {
            if (!playerId.equals(seat.playerId)) continue;
            seat.connection = Connection.DISCONNECTED;
            seat.automated = true;
        }
    }

    public static boolean reconnect(State state, String playerId) {
        if (state == null || playerId == null) return false;
        for (Seat seat : state.seats.values()) {
            if (!playerId.equals(seat.playerId)) continue;
            seat.connection = Connection.CONNECTED;
            seat.automated = false;
            return true;
        }
        return false;
    }

    public static CommandResult submit(State state, Role role, String playerId, long sequence,
                                       String commandKey, String value) {
        if (state == null || role == null) return new CommandResult(false, "Session unavailable", sequence);
        Seat seat = state.seats.get(role);
        if (seat == null || seat.automated || seat.connection != Connection.CONNECTED) {
            return new CommandResult(false, "Role is currently owned by automation", sequence);
        }
        if (!seat.playerId.equals(playerId)) return new CommandResult(false, "Player does not own this role", sequence);
        if (sequence <= seat.lastAcceptedSequence) return new CommandResult(false, "Stale or duplicate command sequence", sequence);
        if (commandKey == null || commandKey.isBlank() || commandKey.length() > 64 || (value != null && value.length() > 512)) {
            return new CommandResult(false, "Malformed command payload", sequence);
        }
        if (!allowedCommand(role, commandKey)) return new CommandResult(false, "Command is outside this role's authority", sequence);
        seat.lastAcceptedSequence = sequence;
        state.acceptedCommands.put(role + ":" + commandKey, value == null ? "" : value);
        return new CommandResult(true, "Accepted", sequence);
    }

    public static CommandResult captainOverride(State state, String captainPlayerId, long sequence,
                                                Role targetRole, String commandKey, String value) {
        if (state == null || !state.captainOverrideEnabled || targetRole == null) {
            return new CommandResult(false, "Captain override unavailable", sequence);
        }
        Seat captain = state.seats.get(Role.CAPTAIN);
        if (captain == null || captain.automated || captain.connection != Connection.CONNECTED
                || !captain.playerId.equals(captainPlayerId)) {
            return new CommandResult(false, "Player does not own the connected captain role", sequence);
        }
        if (sequence <= captain.lastAcceptedSequence || !allowedCommand(targetRole, commandKey)) {
            return new CommandResult(false, "Stale or unauthorized override", sequence);
        }
        captain.lastAcceptedSequence = sequence;
        state.acceptedCommands.put(targetRole + ":" + commandKey, value == null ? "" : value);
        state.sharedMessages.add("CAPTAIN override -> " + targetRole + ":" + commandKey);
        return new CommandResult(true, "Captain override accepted", sequence);
    }

    public static boolean requestVote(State state, String captainPlayerId, String proposal) {
        Seat captain = state == null ? null : state.seats.get(Role.CAPTAIN);
        if (captain == null || !captainPlayerId.equals(captain.playerId) || captain.automated
                || proposal == null || proposal.isBlank()) return false;
        state.pendingVote = proposal.trim();
        state.votes.clear();
        return true;
    }

    public static boolean castVote(State state, String playerId, boolean approve) {
        if (state == null || state.pendingVote.isBlank() || playerId == null) return false;
        boolean connected = state.seats.values().stream().anyMatch(seat -> playerId.equals(seat.playerId)
                && seat.connection == Connection.CONNECTED && !seat.automated);
        if (!connected) return false;
        state.votes.put(playerId, approve);
        return true;
    }

    public static boolean resolveVote(State state) {
        if (state == null || state.pendingVote.isBlank()) return false;
        long connected = state.seats.values().stream().filter(seat -> seat.connection == Connection.CONNECTED && !seat.automated).count();
        long approvals = state.votes.values().stream().filter(Boolean::booleanValue).count();
        boolean accepted = switch (state.votePolicy) {
            case CAPTAIN_DECIDES -> {
                Seat captain = state.seats.get(Role.CAPTAIN);
                yield captain != null && Boolean.TRUE.equals(state.votes.get(captain.playerId));
            }
            case MAJORITY -> approvals > connected / 2;
            case UNANIMOUS -> connected > 0 && approvals == connected;
        };
        state.sharedMessages.add("VOTE " + (accepted ? "ACCEPTED" : "REJECTED") + " | " + state.pendingVote);
        state.pendingVote = "";
        state.votes.clear();
        return accepted;
    }

    public static List<String> practiceScenario(Role role) {
        if (role == null) return List.of();
        return switch (role) {
            case CAPTAIN -> List.of("Delegate two priorities", "Review a station warning", "Exercise or decline an override");
            case HELM -> List.of("Plot a course", "Change formation", "Execute an evasive turn");
            case TACTICAL -> List.of("Identify a target", "Assign a defensive screen", "Hold and release fire");
            case ENGINEERING -> List.of("Route power", "Seal a hazard", "Assign a repair team");
            case SCIENCE -> List.of("Scan a contact", "Verify allegiance", "Share an intelligence marker");
            case STRATEGIC_COMMAND -> List.of("Inspect territory", "Plot an operation", "Issue a fleet order");
        };
    }

    public static boolean postMessage(State state, String playerId, String type, String text) {
        if (state == null || playerId == null || text == null || text.isBlank() || text.length() > 280) return false;
        boolean connected = state.seats.values().stream().anyMatch(seat -> playerId.equals(seat.playerId)
                && seat.connection == Connection.CONNECTED && !seat.automated);
        if (!connected) return false;
        String safeType = type == null ? "TEXT" : type.replaceAll("[^A-Za-z0-9_-]", "");
        state.sharedMessages.add(safeType + "|" + playerId + "|" + text.trim());
        while (state.sharedMessages.size() > 100) state.sharedMessages.remove(0);
        return true;
    }

    public static long diagnosticChecksum(State state) {
        if (state == null) return 0L;
        long hash = 1125899906842597L;
        for (Seat seat : state.seats.values()) {
            hash = 31 * hash + seat.role.ordinal();
            hash = 31 * hash + seat.lastAcceptedSequence;
            hash = 31 * hash + (seat.automated ? 1 : 0);
        }
        for (Map.Entry<String, String> entry : state.acceptedCommands.entrySet()) {
            hash = 31 * hash + entry.getKey().hashCode();
            hash = 31 * hash + entry.getValue().hashCode();
        }
        return hash;
    }

    public static List<String> roleLines(State state) {
        if (state == null) return List.of("Cooperative session unavailable");
        ArrayList<String> out = new ArrayList<>();
        for (Seat seat : state.seats.values()) {
            out.add(seat.role + "  |  " + (seat.automated ? "AUTOMATED" : seat.playerId)
                    + "  |  " + seat.connection + "  |  seq " + seat.lastAcceptedSequence
                    + "  |  " + seat.responsibilities);
        }
        return List.copyOf(out);
    }

    public static String serialize(State state) {
        if (state == null) return "";
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        StringBuilder seats = new StringBuilder();
        for (Seat seat : state.seats.values()) {
            if (!seats.isEmpty()) seats.append(';');
            seats.append(seat.role).append(':').append(enc(encoder, seat.playerId)).append(':')
                    .append(seat.connection).append(':').append(seat.automated).append(':').append(seat.lastAcceptedSequence);
        }
        StringBuilder commands = new StringBuilder();
        for (Map.Entry<String, String> entry : state.acceptedCommands.entrySet()) {
            if (!commands.isEmpty()) commands.append(';');
            commands.append(enc(encoder, entry.getKey())).append(':').append(enc(encoder, entry.getValue()));
        }
        return state.sessionActive + "|" + seats + "|" + state.supportedPlayers + ":"
                + state.captainOverrideEnabled + ":" + state.tacticalPauseAllowed + ":"
                + enc(encoder, state.hostMigrationPolicy) + ":" + enc(encoder, state.saveOwnerId) + ":"
                + state.votePolicy + "|" + commands;
    }

    public static State restore(String raw) {
        State state = bootstrap();
        if (raw == null || raw.isBlank()) return state;
        String[] p = raw.split("\\|", -1);
        if (p.length < 2) return state;
        state.sessionActive = Boolean.parseBoolean(p[0]);
        for (String rawSeat : p[1].split(";")) {
            String[] f = rawSeat.split(":", -1);
            if (f.length < 5) continue;
            Role role = enumeration(f[0], null);
            if (role == null) continue;
            Seat seat = state.seats.get(role);
            seat.playerId = dec(f[1], "");
            seat.connection = enumeration(f[2], Connection.DISCONNECTED);
            seat.automated = Boolean.parseBoolean(f[3]);
            seat.lastAcceptedSequence = Math.max(0L, longValue(f[4], 0L));
        }
        if (p.length >= 3) {
            String[] f = p[2].split(":", -1);
            if (f.length >= 5) {
                state.supportedPlayers = Math.max(1, Math.min(Role.values().length, (int) longValue(f[0], 6)));
                state.captainOverrideEnabled = Boolean.parseBoolean(f[1]);
                state.tacticalPauseAllowed = Boolean.parseBoolean(f[2]);
                state.hostMigrationPolicy = dec(f[3], state.hostMigrationPolicy);
                state.saveOwnerId = dec(f[4], "host");
                if (f.length >= 6) state.votePolicy = enumeration(f[5], VotePolicy.CAPTAIN_DECIDES);
            }
        }
        if (p.length >= 4) {
            for (String rawCommand : p[3].split(";")) {
                String[] f = rawCommand.split(":", -1);
                if (f.length >= 2) state.acceptedCommands.put(dec(f[0], "command"), dec(f[1], ""));
            }
        }
        return state;
    }

    private static boolean allowedCommand(Role role, String commandKey) {
        if (role == null || commandKey == null) return false;
        String key = commandKey.toLowerCase();
        return switch (role) {
            case CAPTAIN -> List.of("delegate", "override", "pause", "priority", "approve").contains(key);
            case HELM -> List.of("course", "speed", "formation", "evasive").contains(key);
            case TACTICAL -> List.of("target", "fire", "hold_fire", "missile", "screen").contains(key);
            case ENGINEERING -> List.of("power", "repair", "seal", "vent", "automation").contains(key);
            case SCIENCE -> List.of("scan", "jam", "identify", "intel", "marker").contains(key);
            case STRATEGIC_COMMAND -> List.of("operation", "route", "fleet_order", "territory", "time_scale").contains(key);
        };
    }

    private static String responsibilities(Role role) {
        return switch (role) {
            case CAPTAIN -> "delegation, priorities, overrides, pause";
            case HELM -> "course, speed, formation, evasive control";
            case TACTICAL -> "targeting, weapons, missiles, defensive screen";
            case ENGINEERING -> "power, repairs, hazards, automation";
            case SCIENCE -> "sensors, identification, jamming, shared markers";
            case STRATEGIC_COMMAND -> "territory, operations, routes, fleet orders";
        };
    }

    private static String enc(Base64.Encoder e, String value) { return e.encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)); }
    private static String dec(String value, String fallback) { try { return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8); } catch (Exception ignored) { return fallback; } }
    private static long longValue(String value, long fallback) { try { return Long.parseLong(value); } catch (Exception ignored) { return fallback; } }
    private static <T extends Enum<T>> T enumeration(String value, T fallback) {
        if (value == null) return fallback;
        Class<T> type = fallback == null ? null : fallback.getDeclaringClass();
        if (type == null) {
            try { @SuppressWarnings("unchecked") T role = (T) Role.valueOf(value); return role; } catch (Exception ignored) { return null; }
        }
        try { return Enum.valueOf(type, value); } catch (Exception ignored) { return fallback; }
    }
}
