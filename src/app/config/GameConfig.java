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
    public final String campaignSlotId;

    /**
     * @deprecated Prefer explicit launch construction at the menu boundary and
     * `GameLaunchRequest.fromGameConfig(...)` for launch resolution.
     */
    @Deprecated(since = "multiplayer-custom-mission-lobby", forRemoval = false)
    public GameConfig(GameMode mode, int worldW, int worldH, boolean randomEvents, long seed, boolean fullscreen) {
        this(mode, worldW, worldH, randomEvents, seed, fullscreen, 0, false, 1, "", "");
    }

    /**
     * @deprecated Prefer explicit launch construction at the menu boundary and
     * `GameLaunchRequest.fromGameConfig(...)` for launch resolution.
     */
    @Deprecated(since = "multiplayer-custom-mission-lobby", forRemoval = false)
    public GameConfig(GameMode mode, int worldW, int worldH, boolean randomEvents, long seed, boolean fullscreen, int playerTeamId) {
        this(mode, worldW, worldH, randomEvents, seed, fullscreen, playerTeamId, false, 1, "", "");
    }

    /**
     * @deprecated Prefer explicit launch construction at the menu boundary and
     * `GameLaunchRequest.fromGameConfig(...)` for launch resolution.
     */
    @Deprecated(since = "multiplayer-custom-mission-lobby", forRemoval = false)
    public GameConfig(GameMode mode, int worldW, int worldH, boolean randomEvents, long seed, boolean fullscreen, int playerTeamId, boolean resumeCampaign) {
        this(mode, worldW, worldH, randomEvents, seed, fullscreen, playerTeamId, resumeCampaign, 1, "", "");
    }

    /**
     * @deprecated Prefer explicit launch construction at the menu boundary and
     * `GameLaunchRequest.fromGameConfig(...)` for launch resolution.
     */
    @Deprecated(since = "multiplayer-custom-mission-lobby", forRemoval = false)
    public GameConfig(GameMode mode, int worldW, int worldH, boolean randomEvents, long seed, boolean fullscreen,
                      int playerTeamId, boolean resumeCampaign,
                      int customBattleEnemyTeamId, String customBattleFriendlyRoster, String customBattleEnemyRoster) {
        this(mode, worldW, worldH, randomEvents, seed, fullscreen,
                playerTeamId, resumeCampaign,
                customBattleEnemyTeamId, customBattleFriendlyRoster, customBattleEnemyRoster, "", null);
    }

    /**
     * @deprecated Prefer explicit launch construction at the menu boundary and
     * `GameLaunchRequest.fromGameConfig(...)` for launch resolution.
     */
    @Deprecated(since = "multiplayer-custom-mission-lobby", forRemoval = false)
    public GameConfig(GameMode mode, int worldW, int worldH, boolean randomEvents, long seed, boolean fullscreen,
                      int playerTeamId, boolean resumeCampaign,
                      int customBattleEnemyTeamId, String customBattleFriendlyRoster, String customBattleEnemyRoster,
                      String startupPreset) {
        this(mode, worldW, worldH, randomEvents, seed, fullscreen, playerTeamId, resumeCampaign,
                customBattleEnemyTeamId, customBattleFriendlyRoster, customBattleEnemyRoster, startupPreset, null);
    }

    /**
     * @deprecated Prefer explicit launch construction at the menu boundary and
     * `GameLaunchRequest.fromGameConfig(...)` for launch resolution.
     */
    @Deprecated(since = "multiplayer-custom-mission-lobby", forRemoval = false)
    public GameConfig(GameMode mode, int worldW, int worldH, boolean randomEvents, long seed, boolean fullscreen,
                      int playerTeamId, boolean resumeCampaign,
                      int customBattleEnemyTeamId, String customBattleFriendlyRoster, String customBattleEnemyRoster,
                      String startupPreset, ExperienceSettings experience) {
        this(mode, worldW, worldH, randomEvents, seed, fullscreen, playerTeamId, resumeCampaign,
                customBattleEnemyTeamId, customBattleFriendlyRoster, customBattleEnemyRoster, startupPreset, experience, false);
    }

    /**
     * @deprecated Prefer explicit launch construction at the menu boundary and
     * `GameLaunchRequest.fromGameConfig(...)` for launch resolution.
     */
    @Deprecated(since = "multiplayer-custom-mission-lobby", forRemoval = false)
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
        this(mode, worldW, worldH, randomEvents, seed, fullscreen, playerTeamId, resumeCampaign,
                customBattleEnemyTeamId, customBattleFriendlyRoster, customBattleEnemyRoster, startupPreset, experience,
                autoLaunchCampaignStartSite, multiplayerLaunch, "primary");
    }

    public GameConfig(GameMode mode, int worldW, int worldH, boolean randomEvents, long seed, boolean fullscreen,
                      int playerTeamId, boolean resumeCampaign,
                      int customBattleEnemyTeamId, String customBattleFriendlyRoster, String customBattleEnemyRoster,
                      String startupPreset, ExperienceSettings experience, boolean autoLaunchCampaignStartSite,
                      MultiplayerLaunchConfig multiplayerLaunch, String campaignSlotId) {
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
        this.campaignSlotId = normalizeCampaignSlotId(campaignSlotId);
    }

    public GameConfig withExperience(ExperienceSettings settings) {
        return new GameConfig(mode, worldW, worldH, randomEvents, seed, fullscreen, playerTeamId, resumeCampaign,
                customBattleEnemyTeamId, customBattleFriendlyRoster, customBattleEnemyRoster, startupPreset, settings,
                autoLaunchCampaignStartSite, multiplayerLaunch, campaignSlotId);
    }

    public GameConfig withAutoLaunchCampaignStartSite(boolean enabled) {
        return new GameConfig(mode, worldW, worldH, randomEvents, seed, fullscreen, playerTeamId, resumeCampaign,
                customBattleEnemyTeamId, customBattleFriendlyRoster, customBattleEnemyRoster, startupPreset, experience,
                enabled, multiplayerLaunch, campaignSlotId);
    }

    public GameConfig withMultiplayerLaunch(MultiplayerLaunchConfig launch) {
        return new GameConfig(mode, worldW, worldH, randomEvents, seed, fullscreen, playerTeamId, resumeCampaign,
                customBattleEnemyTeamId, customBattleFriendlyRoster, customBattleEnemyRoster, startupPreset, experience,
                autoLaunchCampaignStartSite, launch, campaignSlotId);
    }

    public GameConfig withCampaignSlot(String slotId) {
        return new GameConfig(mode, worldW, worldH, randomEvents, seed, fullscreen, playerTeamId, resumeCampaign,
                customBattleEnemyTeamId, customBattleFriendlyRoster, customBattleEnemyRoster, startupPreset, experience,
                autoLaunchCampaignStartSite, multiplayerLaunch, slotId);
    }

    private static String normalizeCampaignSlotId(String slotId) {
        String value = (slotId == null || slotId.isBlank()) ? "primary" : slotId.trim().toLowerCase();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_') {
                out.append(c);
            } else if (Character.isWhitespace(c)) {
                out.append('-');
            }
        }
        return out.length() == 0 ? "primary" : out.toString();
    }
}
