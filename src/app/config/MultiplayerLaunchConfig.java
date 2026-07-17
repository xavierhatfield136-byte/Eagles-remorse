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

    public MultiplayerLaunchConfig(Role role,
                                   String directAddress,
                                   String advertisedHostAddress,
                                   String advertisedClientAddress,
                                   int port,
                                   int timeoutMs,
                                   String matchId,
                                   String reportPath,
                                   boolean loopbackOnly) {
        this.role = role == null ? Role.HOST : role;
        this.directAddress = clean(directAddress);
        this.advertisedHostAddress = clean(advertisedHostAddress);
        this.advertisedClientAddress = clean(advertisedClientAddress);
        this.port = port <= 0 || port > 65_535 ? 46717 : port;
        this.timeoutMs = Math.max(1_000, timeoutMs);
        this.matchId = clean(matchId).isBlank() ? "in-game-multiplayer" : clean(matchId);
        this.reportPath = clean(reportPath).isBlank() ? defaultReportPath(this.role) : clean(reportPath);
        this.loopbackOnly = loopbackOnly;
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
                false);
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
}
