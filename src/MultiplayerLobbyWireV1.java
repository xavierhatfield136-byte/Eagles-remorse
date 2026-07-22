import app.config.MultiplayerMissionChoice;

import java.util.HashMap;
import java.util.Map;

/** Small text payloads for the V1 in-app lobby, carried by the LAN transport. */
public final class MultiplayerLobbyWireV1 {
    public enum CommandType {
        HELLO,
        READY,
        LEAVE,
        MATCH_LOADED
    }

    public record Snapshot(long revision,
                           String lobbyId,
                           String matchId,
                           String sessionNonce,
                           long lockedConfigRevision,
                           String missionId,
                           long seed,
                           int worldW,
                           int worldH,
                           ShipRole hostHull,
                           ShipRole clientHull,
                           boolean hostReady,
                           boolean clientReady,
                           boolean matchStarting,
                           String hostName,
                           String clientName,
                           String status) {
        public Snapshot(long revision,
                        String missionId,
                        long seed,
                        int worldW,
                        int worldH,
                        boolean hostReady,
                        boolean clientReady,
                        boolean matchStarting,
                        String hostName,
                        String clientName,
                        String status) {
            this(revision, "lobby:local", "match:local",
                    MultiplayerProtocolV1.sessionNonceForMatch("match:local"), revision,
                    missionId, seed, worldW, worldH, ShipRole.FRIGATE, ShipRole.FRIGATE,
                    hostReady, clientReady, matchStarting,
                    hostName, clientName, status);
        }

        public Snapshot {
            revision = Math.max(0L, revision);
            lobbyId = clean(lobbyId, "lobby:local");
            matchId = clean(matchId, "match:local");
            sessionNonce = clean(sessionNonce, MultiplayerProtocolV1.sessionNonceForMatch(matchId));
            lockedConfigRevision = Math.max(0L, lockedConfigRevision);
            missionId = MultiplayerMissionChoice.fromMissionId(missionId).missionId();
            seed = Math.max(0L, seed);
            worldW = clampWorld(worldW);
            worldH = clampWorld(worldH);
            hostHull = directHullOrDefault(hostHull);
            clientHull = directHullOrDefault(clientHull);
            hostName = clean(hostName, "Host");
            clientName = clean(clientName, "Client");
            status = clean(status, "");
        }
    }

    public record PrepareMatch(String matchId,
                               String lockedLaunchSpecDigest,
                               long lockedConfigRevision) {
        public PrepareMatch {
            matchId = clean(matchId, "match:local");
            lockedLaunchSpecDigest = clean(lockedLaunchSpecDigest, "");
            lockedConfigRevision = Math.max(0L, lockedConfigRevision);
        }
    }

    public record BeginMatch(String matchId,
                             String lockedLaunchSpecDigest,
                             long startTick) {
        public BeginMatch {
            matchId = clean(matchId, "match:local");
            lockedLaunchSpecDigest = clean(lockedLaunchSpecDigest, "");
            startTick = Math.max(1L, startTick);
        }
    }

    public record Command(CommandType type,
                          boolean ready,
                          long acceptedRevision,
                          String playerName,
                          String matchId,
                          String lockedLaunchSpecDigest,
                          boolean loadAccepted,
                          String loadStatus) {
        public Command(CommandType type, boolean ready, long acceptedRevision) {
            this(type, ready, acceptedRevision, "");
        }

        public Command(CommandType type, boolean ready, long acceptedRevision, String playerName) {
            this(type, ready, acceptedRevision, playerName, "", "", false, "");
        }

        public Command {
            if (type == null) type = CommandType.READY;
            acceptedRevision = Math.max(0L, acceptedRevision);
            playerName = clean(playerName, "");
            matchId = clean(matchId, "");
            lockedLaunchSpecDigest = clean(lockedLaunchSpecDigest, "");
            loadStatus = clean(loadStatus, loadAccepted ? "Loaded" : "");
        }
    }

    private MultiplayerLobbyWireV1() {}

    public static String encodeSnapshot(Snapshot snapshot) {
        if (snapshot == null) {
            snapshot = new Snapshot(0L, MultiplayerMissionChoice.DEFAULT_MISSION_ID,
                    0L, 3600, 2200, false, false, false, "Host", "Client", "");
        }
        return "kind=snapshot"
                + "|revision=" + snapshot.revision()
                + "|lobbyId=" + esc(snapshot.lobbyId())
                + "|matchId=" + esc(snapshot.matchId())
                + "|sessionNonce=" + esc(snapshot.sessionNonce())
                + "|lockedConfigRevision=" + snapshot.lockedConfigRevision()
                + "|missionId=" + snapshot.missionId()
                + "|seed=" + snapshot.seed()
                + "|worldW=" + snapshot.worldW()
                + "|worldH=" + snapshot.worldH()
                + "|hostHull=" + snapshot.hostHull().name()
                + "|clientHull=" + snapshot.clientHull().name()
                + "|hostReady=" + snapshot.hostReady()
                + "|clientReady=" + snapshot.clientReady()
                + "|matchStarting=" + snapshot.matchStarting()
                + "|hostName=" + esc(snapshot.hostName())
                + "|clientName=" + esc(snapshot.clientName())
                + "|status=" + esc(snapshot.status());
    }

    public static String encodePrepareMatch(PrepareMatch prepare) {
        if (prepare == null) prepare = new PrepareMatch("match:local", "", 0L);
        return "kind=prepare"
                + "|matchId=" + esc(prepare.matchId())
                + "|lockedLaunchSpecDigest=" + esc(prepare.lockedLaunchSpecDigest())
                + "|lockedConfigRevision=" + prepare.lockedConfigRevision();
    }

    public static PrepareMatch decodePrepareMatch(String payload) {
        Map<String, String> fields = fields(payload);
        return new PrepareMatch(
                unesc(fields.getOrDefault("matchId", "match:local")),
                unesc(fields.getOrDefault("lockedLaunchSpecDigest", "")),
                parseLong(fields.get("lockedConfigRevision"), 0L));
    }

    public static String encodeBeginMatch(BeginMatch begin) {
        if (begin == null) begin = new BeginMatch("match:local", "", 1L);
        return "kind=begin"
                + "|matchId=" + esc(begin.matchId())
                + "|lockedLaunchSpecDigest=" + esc(begin.lockedLaunchSpecDigest())
                + "|startTick=" + begin.startTick();
    }

    public static BeginMatch decodeBeginMatch(String payload) {
        Map<String, String> fields = fields(payload);
        return new BeginMatch(
                unesc(fields.getOrDefault("matchId", "match:local")),
                unesc(fields.getOrDefault("lockedLaunchSpecDigest", "")),
                parseLong(fields.get("startTick"), 1L));
    }

    public static Snapshot decodeSnapshot(String payload) {
        Map<String, String> fields = fields(payload);
        return new Snapshot(
                parseLong(fields.get("revision"), 0L),
                unesc(fields.getOrDefault("lobbyId", "lobby:local")),
                unesc(fields.getOrDefault("matchId", "match:local")),
                unesc(fields.getOrDefault("sessionNonce",
                        MultiplayerProtocolV1.sessionNonceForMatch(fields.getOrDefault("matchId", "match:local")))),
                parseLong(fields.get("lockedConfigRevision"), parseLong(fields.get("revision"), 0L)),
                fields.getOrDefault("missionId", MultiplayerMissionChoice.DEFAULT_MISSION_ID),
                parseLong(fields.get("seed"), 0L),
                parseInt(fields.get("worldW"), 3600),
                parseInt(fields.get("worldH"), 2200),
                parseHull(fields.get("hostHull"), ShipRole.FRIGATE),
                parseHull(fields.get("clientHull"), ShipRole.FRIGATE),
                parseBoolean(fields.get("hostReady")),
                parseBoolean(fields.get("clientReady")),
                parseBoolean(fields.get("matchStarting")),
                unesc(fields.getOrDefault("hostName", "Host")),
                unesc(fields.getOrDefault("clientName", "Client")),
                unesc(fields.getOrDefault("status", "")));
    }

    public static String encodeCommand(Command command) {
        if (command == null) command = new Command(CommandType.READY, false, 0L);
        return "kind=command"
                + "|type=" + command.type().name()
                + "|ready=" + command.ready()
                + "|acceptedRevision=" + command.acceptedRevision()
                + "|playerName=" + esc(command.playerName())
                + "|matchId=" + esc(command.matchId())
                + "|lockedLaunchSpecDigest=" + esc(command.lockedLaunchSpecDigest())
                + "|loadAccepted=" + command.loadAccepted()
                + "|loadStatus=" + esc(command.loadStatus());
    }

    public static Command decodeCommand(String payload) {
        Map<String, String> fields = fields(payload);
        CommandType type = CommandType.READY;
        try {
            type = CommandType.valueOf(fields.getOrDefault("type", "READY"));
        } catch (RuntimeException ignored) {
        }
        return new Command(type,
                parseBoolean(fields.get("ready")),
                parseLong(fields.get("acceptedRevision"), 0L),
                unesc(fields.getOrDefault("playerName", "")),
                unesc(fields.getOrDefault("matchId", "")),
                unesc(fields.getOrDefault("lockedLaunchSpecDigest", "")),
                parseBoolean(fields.get("loadAccepted")),
                unesc(fields.getOrDefault("loadStatus", "")));
    }

    public static String payloadKind(String payload) {
        Map<String, String> fields = fields(payload);
        return clean(fields.get("kind"), "");
    }

    private static Map<String, String> fields(String payload) {
        Map<String, String> out = new HashMap<>();
        String[] parts = (payload == null ? "" : payload).split("\\|");
        for (String part : parts) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            out.put(part.substring(0, eq), part.substring(eq + 1));
        }
        return out;
    }

    private static String esc(String value) {
        return clean(value, "").replace("%", "%25").replace("|", "%7C").replace("=", "%3D");
    }

    private static String unesc(String value) {
        return clean(value, "").replace("%3D", "=").replace("%7C", "|").replace("%25", "%");
    }

    private static boolean parseBoolean(String value) {
        return Boolean.parseBoolean(clean(value, "false"));
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(clean(value, String.valueOf(fallback)));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(clean(value, String.valueOf(fallback)));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int clampWorld(int value) {
        return Math.max(1800, Math.min(60000, value));
    }

    private static ShipRole parseHull(String value, ShipRole fallback) {
        try {
            return directHullOrDefault(ShipRole.valueOf(clean(value, fallback.name())));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static ShipRole directHullOrDefault(ShipRole role) {
        if (role == null || role == ShipRole.BASE || role == ShipRole.STATIC_TURRET) return ShipRole.FRIGATE;
        return role;
    }

    private static String clean(String value, String fallback) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}
