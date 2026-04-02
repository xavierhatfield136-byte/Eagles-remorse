public enum Faction {
    PLAYER(0, "Blue"),
    ALLY(0, "Blue"),
    ENEMY(1, "Red"),
    TEAM_C(2, "Green"),
    TEAM_D(3, "Yellow");

    private static volatile boolean campaignBlueGreenAlliance = false;
    private static volatile boolean campaignBlueYellowAlliance = false;

    private final int teamId;
    private final String teamName;

    Faction(int teamId, String teamName) {
        this.teamId = teamId;
        this.teamName = teamName;
    }

    public int teamId() { return teamId; }
    public String teamName() { return teamName; }

    public boolean isFriendlyTo(Faction other) {
        if (other == null) return false;
        if (this.teamId == other.teamId) return true;
        return campaignAllianceActive(this, other);
    }

    public static void configureCampaignAlliances(boolean blueGreenAlliance, boolean blueYellowAlliance) {
        campaignBlueGreenAlliance = blueGreenAlliance;
        campaignBlueYellowAlliance = blueYellowAlliance;
    }

    public static void clearCampaignAlliances() {
        configureCampaignAlliances(false, false);
    }

    private static boolean campaignAllianceActive(Faction a, Faction b) {
        boolean aBlue = a.teamId == 0;
        boolean bBlue = b.teamId == 0;
        boolean aGreen = a.teamId == 2;
        boolean bGreen = b.teamId == 2;
        boolean aYellow = a.teamId == 3;
        boolean bYellow = b.teamId == 3;

        if (campaignBlueGreenAlliance && ((aBlue && bGreen) || (aGreen && bBlue))) {
            return true;
        }
        if (campaignBlueYellowAlliance && ((aBlue && bYellow) || (aYellow && bBlue))) {
            return true;
        }
        if (campaignBlueGreenAlliance && campaignBlueYellowAlliance
                && ((aGreen && bYellow) || (aYellow && bGreen))) {
            return true;
        }
        return false;
    }

    public static Faction forTeamId(int teamId) {
        return switch (teamId) {
            case 0 -> ALLY;
            case 1 -> ENEMY;
            case 2 -> TEAM_C;
            case 3 -> TEAM_D;
            default -> ALLY;
        };
    }

    public static Faction[] fourTeamFactions() {
        return new Faction[]{ALLY, ENEMY, TEAM_C, TEAM_D};
    }
}
