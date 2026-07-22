import app.config.MultiplayerLaunchConfig;

/** Runtime multiplayer metadata carried beside a resolved mission launch specification. */
public record MultiplayerLaunchContext(MultiplayerLaunchConfig launchConfig,
                                       String matchId,
                                       String sessionNonce,
                                       long lockedConfigRevision) {
    public MultiplayerLaunchContext {
        if (launchConfig == null) {
            launchConfig = MultiplayerLaunchConfig.host(MultiplayerLanTransportV1.DEFAULT_PORT, "127.0.0.1");
        }
        matchId = clean(matchId, launchConfig.matchId);
        sessionNonce = clean(sessionNonce, MultiplayerProtocolV1.sessionNonceForMatch(matchId));
        lockedConfigRevision = Math.max(0L, lockedConfigRevision);
    }

    public static MultiplayerLaunchContext fromLaunchConfig(MultiplayerLaunchConfig launchConfig) {
        MultiplayerLaunchConfig safe = launchConfig == null
                ? MultiplayerLaunchConfig.host(MultiplayerLanTransportV1.DEFAULT_PORT, "127.0.0.1")
                : launchConfig;
        return new MultiplayerLaunchContext(
                safe,
                safe.matchId,
                MultiplayerProtocolV1.sessionNonceForMatch(safe.matchId),
                0L);
    }

    private static String clean(String value, String fallback) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}
