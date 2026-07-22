package app.config;

/** Stable menu-facing IDs for multiplayer-eligible custom missions. */
public enum MultiplayerMissionChoice {
    LAST_STAND("core:last_stand", "Last Stand", GameMode.LAST_STAND),
    RESOURCE_RUSH("core:resource_rush", "Resource Rush", GameMode.RESOURCE_RUSH),
    FOUR_TEAM_DOMINATION("core:four_team_domination", "4 Team Domination", GameMode.FOUR_TEAM_DOMINATION),
    CUSTOM_BATTLE("core:custom_battle", "Custom Battles", GameMode.CUSTOM_BATTLES),
    SHOOTING_RANGE("debug:shooting_range", "Shooting Range", GameMode.SHOOTING_RANGE),
    SHOWCASE("showcase:fleet_showcase", "Showcase", GameMode.SHOWCASE),
    V1_DUEL("core:v1_duel", "V1 Duel", GameMode.CUSTOM_BATTLES),
    HEAVY_DUEL("core:heavy_duel", "Heavy Duel", GameMode.CUSTOM_BATTLES);

    public static final String DEFAULT_MISSION_ID = "core:custom_battle";

    private final String missionId;
    private final String displayName;
    private final GameMode menuMode;

    MultiplayerMissionChoice(String missionId, String displayName, GameMode menuMode) {
        this.missionId = missionId;
        this.displayName = displayName;
        this.menuMode = menuMode;
    }

    public String missionId() {
        return missionId;
    }

    public String displayName() {
        return displayName;
    }

    public GameMode menuMode() {
        return menuMode;
    }

    public static MultiplayerMissionChoice fromMissionId(String missionId) {
        String clean = clean(missionId);
        for (MultiplayerMissionChoice choice : values()) {
            if (choice.missionId.equals(clean)) return choice;
        }
        return CUSTOM_BATTLE;
    }

    public static MultiplayerMissionChoice fromGameMode(GameMode mode) {
        if (mode == null) return CUSTOM_BATTLE;
        for (MultiplayerMissionChoice choice : values()) {
            if (choice.menuMode == mode) return choice;
        }
        return CUSTOM_BATTLE;
    }

    @Override
    public String toString() {
        return displayName;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
