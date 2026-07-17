package app.config;

/**
 * Game startup options selected in the main menu.
 */
public class GameConfig {
    public final GameMode mode;
    public final int worldW;
    public final int worldH;
    public final boolean randomEvents;
    public final long seed;
    public final boolean fullscreen;
    public final int playerTeamId;
    public final boolean resumeCampaign;
    public final int customBattleEnemyTeamId;
    public final String customBattleFriendlyRoster;
    public final String customBattleEnemyRoster;
    public final String startupPreset;
    public final ExperienceSettings experience;
    public final boolean autoLaunchCampaignStartSite;
    public final MultiplayerLaunchConfig multiplayerLaunch;

    public GameConfig(GameMode mode, int worldW, int worldH, boolean randomEvents, long seed, boolean fullscreen) {
        this(mode, worldW, worldH, randomEvents, seed, fullscreen, 0, false, 1, "", "");
    }

    public GameConfig(GameMode mode, int worldW, int worldH, boolean randomEvents, long seed, boolean fullscreen, int playerTeamId) {
        this(mode, worldW, worldH, randomEvents, seed, fullscreen, playerTeamId, false, 1, "", "");
    }

    public GameConfig(GameMode mode, int worldW, int worldH, boolean randomEvents, long seed, boolean fullscreen, int playerTeamId, boolean resumeCampaign) {
        this(mode, worldW, worldH, randomEvents, seed, fullscreen, playerTeamId, resumeCampaign, 1, "", "");
    }

    public GameConfig(GameMode mode, int worldW, int worldH, boolean randomEvents, long seed, boolean fullscreen,
                      int playerTeamId, boolean resumeCampaign,
                      int customBattleEnemyTeamId, String customBattleFriendlyRoster, String customBattleEnemyRoster) {
        this(mode, worldW, worldH, randomEvents, seed, fullscreen,
                playerTeamId, resumeCampaign,
                customBattleEnemyTeamId, customBattleFriendlyRoster, customBattleEnemyRoster, "", null);
    }

    public GameConfig(GameMode mode, int worldW, int worldH, boolean randomEvents, long seed, boolean fullscreen,
                      int playerTeamId, boolean resumeCampaign,
                      int customBattleEnemyTeamId, String customBattleFriendlyRoster, String customBattleEnemyRoster,
                      String startupPreset) {
        this(mode, worldW, worldH, randomEvents, seed, fullscreen, playerTeamId, resumeCampaign,
                customBattleEnemyTeamId, customBattleFriendlyRoster, customBattleEnemyRoster, startupPreset, null);
    }

    public GameConfig(GameMode mode, int worldW, int worldH, boolean randomEvents, long seed, boolean fullscreen,
                      int playerTeamId, boolean resumeCampaign,
                      int customBattleEnemyTeamId, String customBattleFriendlyRoster, String customBattleEnemyRoster,
                      String startupPreset, ExperienceSettings experience) {
        this(mode, worldW, worldH, randomEvents, seed, fullscreen, playerTeamId, resumeCampaign,
                customBattleEnemyTeamId, customBattleFriendlyRoster, customBattleEnemyRoster, startupPreset, experience, false);
    }

    public GameConfig(GameMode mode, int worldW, int worldH, boolean randomEvents, long seed, boolean fullscreen,
                      int playerTeamId, boolean resumeCampaign,
                      int customBattleEnemyTeamId, String customBattleFriendlyRoster, String customBattleEnemyRoster,
                      String startupPreset, ExperienceSettings experience, boolean autoLaunchCampaignStartSite) {
        this(mode, worldW, worldH, randomEvents, seed, fullscreen, playerTeamId, resumeCampaign,
                customBattleEnemyTeamId, customBattleFriendlyRoster, customBattleEnemyRoster, startupPreset, experience,
                autoLaunchCampaignStartSite, null);
    }

    public GameConfig(GameMode mode, int worldW, int worldH, boolean randomEvents, long seed, boolean fullscreen,
                      int playerTeamId, boolean resumeCampaign,
                      int customBattleEnemyTeamId, String customBattleFriendlyRoster, String customBattleEnemyRoster,
                      String startupPreset, ExperienceSettings experience, boolean autoLaunchCampaignStartSite,
                      MultiplayerLaunchConfig multiplayerLaunch) {
        this.mode = mode;
        this.worldW = worldW;
        this.worldH = worldH;
        this.randomEvents = true;
        this.seed = seed;
        this.fullscreen = fullscreen;
        this.playerTeamId = Math.max(0, Math.min(3, playerTeamId));
        this.resumeCampaign = resumeCampaign;
        this.customBattleEnemyTeamId = Math.max(0, Math.min(3, customBattleEnemyTeamId));
        this.customBattleFriendlyRoster = (customBattleFriendlyRoster == null) ? "" : customBattleFriendlyRoster.trim();
        this.customBattleEnemyRoster = (customBattleEnemyRoster == null) ? "" : customBattleEnemyRoster.trim();
        this.startupPreset = (startupPreset == null) ? "" : startupPreset.trim();
        this.experience = (experience == null) ? ExperienceSettings.defaults() : experience.copy();
        this.experience.normalize();
        this.autoLaunchCampaignStartSite = autoLaunchCampaignStartSite;
        this.multiplayerLaunch = multiplayerLaunch;
    }

    public GameConfig withExperience(ExperienceSettings settings) {
        return new GameConfig(mode, worldW, worldH, randomEvents, seed, fullscreen, playerTeamId, resumeCampaign,
                customBattleEnemyTeamId, customBattleFriendlyRoster, customBattleEnemyRoster, startupPreset, settings,
                autoLaunchCampaignStartSite, multiplayerLaunch);
    }

    public GameConfig withAutoLaunchCampaignStartSite(boolean enabled) {
        return new GameConfig(mode, worldW, worldH, randomEvents, seed, fullscreen, playerTeamId, resumeCampaign,
                customBattleEnemyTeamId, customBattleFriendlyRoster, customBattleEnemyRoster, startupPreset, experience,
                enabled, multiplayerLaunch);
    }

    public GameConfig withMultiplayerLaunch(MultiplayerLaunchConfig launch) {
        return new GameConfig(mode, worldW, worldH, randomEvents, seed, fullscreen, playerTeamId, resumeCampaign,
                customBattleEnemyTeamId, customBattleFriendlyRoster, customBattleEnemyRoster, startupPreset, experience,
                autoLaunchCampaignStartSite, launch);
    }
}
