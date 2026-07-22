import app.config.GameConfig;
import app.config.GameMode;

/** Boundary object for game startup before creating a concrete GamePanel or multiplayer lobby. */
public record GameLaunchRequest(GameMode gameMode,
                                GameConfig legacyConfig,
                                MissionLaunchSpec missionLaunchSpec,
                                MultiplayerLaunchContext multiplayerContext) {
    public GameLaunchRequest {
        if (legacyConfig == null) {
            legacyConfig = new GameConfig(
                    gameMode == null ? GameMode.CAMPAIGN_OPS : gameMode,
                    5000, 5000, true, System.nanoTime(), false);
        }
        if (gameMode == null) gameMode = legacyConfig.mode;
    }

    public static GameLaunchRequest fromGameConfig(GameConfig config) {
        GameConfig safe = config == null
                ? new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, System.nanoTime(), false)
                : config;
        MultiplayerLaunchContext multiplayer = safe.multiplayerLaunch == null
                ? null
                : MultiplayerLaunchContext.fromLaunchConfig(safe.multiplayerLaunch);
        MissionLaunchSpec spec = multiplayer != null
                ? MultiplayerPlayableDuelContextFactory.resolveMissionSpec(
                        multiplayer.launchConfig(), safe.seed)
                : singlePlayerSpecOrNull(safe);
        return new GameLaunchRequest(safe.mode, safe, spec, multiplayer);
    }

    public boolean multiplayer() {
        return multiplayerContext != null;
    }

    private static MissionLaunchSpec singlePlayerSpecOrNull(GameConfig config) {
        if (config == null || config.mode != GameMode.CUSTOM_BATTLES || config.multiplayerLaunch != null) {
            return null;
        }
        return SinglePlayerLaunchAdapter.fromGameConfig(config);
    }
}
