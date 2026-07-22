package app.config;

/** In-game launcher request for the V1 multiplayer LAN duel harness. */
public final class MultiplayerLaunchConfig {
    public enum Role {
        HOST,
        CLIENT
    }

    public final Role role;
    public final String directAddress;
    public final String advertisedHostAddress;
    public final String advertisedClientAddress;
    public final int port;
    public final int timeoutMs;
    public final String matchId;
    public final String reportPath;
    public final boolean loopbackOnly;
    public final boolean diagnosticsHarness;
    public final String missionId;
    public final long missionSeed;
    public final int missionWorldW;
    public final int missionWorldH;
    public final String hostPlayerName;
    public final String clientPlayerName;

    public MultiplayerLaunchConfig(Role role,
                                   String directAddress,
                                   String advertisedHostAddress,
                                   String advertisedClientAddress,
                                   int port,
                                   int timeoutMs,
                                   String matchId,
                                   String reportPath,
                                   boolean loopbackOnly) {
        this(role, directAddress, advertisedHostAddress, advertisedClientAddress, port, timeoutMs,
                matchId, reportPath, loopbackOnly, false);
    }

    public MultiplayerLaunchConfig(Role role,
                                   String directAddress,
                                   String advertisedHostAddress,
                                   String advertisedClientAddress,
                                   int port,
                                   int timeoutMs,
                                   String matchId,
                                   String reportPath,
                                   boolean loopbackOnly,
                                   boolean diagnosticsHarness) {
        this(role, directAddress, advertisedHostAddress, advertisedClientAddress, port, timeoutMs,
                matchId, reportPath, loopbackOnly, diagnosticsHarness,
                MultiplayerMissionChoice.DEFAULT_MISSION_ID, 0L, 0, 0);
    }

    public MultiplayerLaunchConfig(Role role,
                                   String directAddress,
                                   String advertisedHostAddress,
                                   String advertisedClientAddress,
                                   int port,
                                   int timeoutMs,
                                   String matchId,
                                   String reportPath,
                                   boolean loopbackOnly,
                                   boolean diagnosticsHarness,
                                   String missionId) {
        this(role, directAddress, advertisedHostAddress, advertisedClientAddress, port, timeoutMs,
                matchId, reportPath, loopbackOnly, diagnosticsHarness, missionId, 0L, 0, 0);
    }

    public MultiplayerLaunchConfig(Role role,
                                   String directAddress,
                                   String advertisedHostAddress,
                                   String advertisedClientAddress,
                                   int port,
                                   int timeoutMs,
                                   String matchId,
                                   String reportPath,
                                   boolean loopbackOnly,
                                   boolean diagnosticsHarness,
                                   String missionId,
                                   long missionSeed,
                                   int missionWorldW,
                                   int missionWorldH) {
        this(role, directAddress, advertisedHostAddress, advertisedClientAddress, port, timeoutMs,
                matchId, reportPath, loopbackOnly, diagnosticsHarness,
                missionId, missionSeed, missionWorldW, missionWorldH, "Host", "Client");
    }

    public MultiplayerLaunchConfig(Role role,
                                   String directAddress,
                                   String advertisedHostAddress,
                                   String advertisedClientAddress,
                                   int port,
                                   int timeoutMs,
                                   String matchId,
                                   String reportPath,
                                   boolean loopbackOnly,
                                   boolean diagnosticsHarness,
                                   String missionId,
                                   long missionSeed,
                                   int missionWorldW,
                                   int missionWorldH,
                                   String hostPlayerName,
                                   String clientPlayerName) {
        this.role = role == null ? Role.HOST : role;
        this.directAddress = clean(directAddress);
        this.advertisedHostAddress = clean(advertisedHostAddress);
        this.advertisedClientAddress = clean(advertisedClientAddress);
        this.port = port <= 0 || port > 65_535 ? 46717 : port;
        this.timeoutMs = Math.max(1_000, timeoutMs);
        this.matchId = clean(matchId).isBlank() ? "in-game-multiplayer" : clean(matchId);
        this.reportPath = clean(reportPath).isBlank() ? defaultReportPath(this.role) : clean(reportPath);
        this.loopbackOnly = loopbackOnly;
        this.diagnosticsHarness = diagnosticsHarness;
        this.missionId = clean(missionId).isBlank()
                ? MultiplayerMissionChoice.DEFAULT_MISSION_ID
                : clean(missionId);
        this.missionSeed = Math.max(0L, missionSeed);
        this.missionWorldW = missionWorldW <= 0 ? 0 : clampWorld(missionWorldW);
        this.missionWorldH = missionWorldH <= 0 ? 0 : clampWorld(missionWorldH);
        this.hostPlayerName = cleanPlayerName(hostPlayerName, "Host");
        this.clientPlayerName = cleanPlayerName(clientPlayerName, "Client");
    }

    public static MultiplayerLaunchConfig host(int port, String advertisedHostAddress) {
        return new MultiplayerLaunchConfig(
                Role.HOST,
                "",
                advertisedHostAddress,
                "",
                port,
                60_000,
                "in-game-multiplayer",
                "build/reports/multiplayer-in-game-host.txt",
                false,
                false);
    }

    public static MultiplayerLaunchConfig client(String directAddress, String advertisedClientAddress) {
        return new MultiplayerLaunchConfig(
                Role.CLIENT,
                directAddress,
                "",
                advertisedClientAddress,
                46717,
                60_000,
                "in-game-multiplayer",
                "build/reports/multiplayer-in-game-client.txt",
                false,
                false);
    }

    public MultiplayerLaunchConfig withDiagnosticsHarness(boolean diagnosticsHarness) {
        return new MultiplayerLaunchConfig(role, directAddress, advertisedHostAddress, advertisedClientAddress,
                port, timeoutMs, matchId, reportPath, loopbackOnly, diagnosticsHarness,
                missionId, missionSeed, missionWorldW, missionWorldH, hostPlayerName, clientPlayerName);
    }

    public MultiplayerLaunchConfig withMatchId(String matchId) {
        return new MultiplayerLaunchConfig(role, directAddress, advertisedHostAddress, advertisedClientAddress,
                port, timeoutMs, matchId, reportPath, loopbackOnly, diagnosticsHarness,
                missionId, missionSeed, missionWorldW, missionWorldH, hostPlayerName, clientPlayerName);
    }

    public MultiplayerLaunchConfig withMissionId(String missionId) {
        return new MultiplayerLaunchConfig(role, directAddress, advertisedHostAddress, advertisedClientAddress,
                port, timeoutMs, matchId, reportPath, loopbackOnly, diagnosticsHarness,
                missionId, missionSeed, missionWorldW, missionWorldH, hostPlayerName, clientPlayerName);
    }

    public MultiplayerLaunchConfig withMissionSettings(String missionId, long seed, int worldW, int worldH) {
        return new MultiplayerLaunchConfig(role, directAddress, advertisedHostAddress, advertisedClientAddress,
                port, timeoutMs, matchId, reportPath, loopbackOnly, diagnosticsHarness,
                missionId, seed, worldW, worldH, hostPlayerName, clientPlayerName);
    }

    public MultiplayerLaunchConfig withPlayerName(String playerName) {
        if (host()) return withPlayerNames(playerName, clientPlayerName);
        return withPlayerNames(hostPlayerName, playerName);
    }

    public MultiplayerLaunchConfig withPlayerNames(String hostPlayerName, String clientPlayerName) {
        return new MultiplayerLaunchConfig(role, directAddress, advertisedHostAddress, advertisedClientAddress,
                port, timeoutMs, matchId, reportPath, loopbackOnly, diagnosticsHarness,
                missionId, missionSeed, missionWorldW, missionWorldH, hostPlayerName, clientPlayerName);
    }

    public boolean host() {
        return role == Role.HOST;
    }

    public String resolvedDirectAddress() {
        if (!clean(directAddress).isBlank()) return clean(directAddress);
        return "127.0.0.1:" + port;
    }

    private static String defaultReportPath(Role role) {
        return role == Role.CLIENT
                ? "build/reports/multiplayer-in-game-client.txt"
                : "build/reports/multiplayer-in-game-host.txt";
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String cleanPlayerName(String value, String fallback) {
        String clean = clean(value);
        if (clean.isBlank()) clean = fallback;
        if (clean.length() > 32) clean = clean.substring(0, 32);
        return clean;
    }

    private static int clampWorld(int value) {
        return Math.max(1800, Math.min(60000, value));
    }
}
