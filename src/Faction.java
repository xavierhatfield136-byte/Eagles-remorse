public enum Faction {
    PLAYER(0, "Blue"),
    ALLY(0, "Blue"),
    ENEMY(1, "Red"),
    TEAM_C(2, "Green"),
    TEAM_D(3, "Yellow"),
    BRIGHT_YELLOW(4, "Bright Yellow"),
    DARK_YELLOW(5, "Dark Orange-Yellow");

    private static volatile boolean campaignBlueGreenAlliance = false;
    private static volatile boolean campaignBlueYellowAlliance = false;
    private static volatile boolean campaignRedYellowAlliance = false;

    private final int teamId;
    private final String teamName;

    public enum ExternalActor { ROGUE_AI, CIVILIAN, PIRATE, UNAFFILIATED }
    public enum ExternalDisposition { PROTECTED, COOPERATIVE, CAUTIOUS, NEUTRAL, PREDATORY, HOSTILE }

    Faction(int teamId, String teamName) {
        this.teamId = teamId;
        this.teamName = teamName;
    }

    public int teamId() { return teamId; }
    public String teamName() { return teamName; }

    /** Both civil-war factions deliberately reuse the legacy Yellow ship catalog and material culture. */
    public boolean isYellowLineage() {
        return this == TEAM_D || this == BRIGHT_YELLOW || this == DARK_YELLOW;
    }

    public Faction hullCatalogFaction() {
        return isYellowLineage() ? TEAM_D : this;
    }

    public String insigniaKey() {
        return switch (this) {
            case BRIGHT_YELLOW -> "yellow_sunburst";
            case DARK_YELLOW -> "yellow_split_chevron";
            default -> name().toLowerCase();
        };
    }

    public String mapPatternKey() {
        return switch (this) {
            case BRIGHT_YELLOW -> "bright_yellow_dots";
            case DARK_YELLOW -> "dark_yellow_diagonal";
            default -> "solid_" + name().toLowerCase();
        };
    }

    public String transponderPrefix() {
        return switch (this) {
            case BRIGHT_YELLOW -> "BYC";
            case DARK_YELLOW -> "DYC";
            case TEAM_D -> "YEL";
            case PLAYER, ALLY -> "BLU";
            case ENEMY -> "RED";
            case TEAM_C -> "GRN";
        };
    }

    public String formationKey() {
        return switch (this) {
            case BRIGHT_YELLOW -> "relief_screen_and_open_escort_columns";
            case DARK_YELLOW -> "compressed_spearhead_and_red_interlock";
            case TEAM_D -> "legacy_yellow_viper_screen";
            case PLAYER, ALLY -> "blue_mutual_support";
            case ENEMY -> "red_pressure_spearhead";
            case TEAM_C -> "green_mobile_screen";
        };
    }

    public String politicalDoctrineKey() {
        return switch (this) {
            case BRIGHT_YELLOW -> "civilian_protection_coalition_legitimacy";
            case DARK_YELLOW -> "centralized_security_red_alignment";
            case TEAM_D -> "legacy_yellow_survival";
            case PLAYER, ALLY -> "blue_command";
            case ENEMY -> "red_domination";
            case TEAM_C -> "green_mutual_defense";
        };
    }

    public ExternalDisposition perceivedBy(ExternalActor actor) {
        if (actor == null) return ExternalDisposition.NEUTRAL;
        return switch (actor) {
            case ROGUE_AI -> ExternalDisposition.HOSTILE;
            case PIRATE -> isYellowLineage() ? ExternalDisposition.PREDATORY : ExternalDisposition.CAUTIOUS;
            case CIVILIAN -> this == BRIGHT_YELLOW ? ExternalDisposition.PROTECTED
                    : (this == DARK_YELLOW ? ExternalDisposition.CAUTIOUS : ExternalDisposition.NEUTRAL);
            case UNAFFILIATED -> this == BRIGHT_YELLOW ? ExternalDisposition.COOPERATIVE
                    : (this == DARK_YELLOW ? ExternalDisposition.CAUTIOUS : ExternalDisposition.NEUTRAL);
        };
    }

    public boolean isHostileTo(Faction other) {
        return other != null && !isFriendlyTo(other);
    }

    public boolean canAnswerSupportCallFrom(Faction other) {
        return isFriendlyTo(other);
    }

    public boolean canTradeWith(Faction other) {
        return other != null && (isFriendlyTo(other) || (!isHostileCoalitionPair(this, other)
                && this != ENEMY && other != ENEMY));
    }

    private static boolean isHostileCoalitionPair(Faction first, Faction second) {
        return (first == BRIGHT_YELLOW && second == DARK_YELLOW)
                || (first == DARK_YELLOW && second == BRIGHT_YELLOW)
                || (first == DARK_YELLOW && (second == PLAYER || second == ALLY || second == TEAM_C))
                || (second == DARK_YELLOW && (first == PLAYER || first == ALLY || first == TEAM_C))
                || (first == BRIGHT_YELLOW && second == ENEMY)
                || (second == BRIGHT_YELLOW && first == ENEMY);
    }

    public boolean isFriendlyTo(Faction other) {
        if (other == null) return false;
        if (this.teamId == other.teamId) return true;
        return campaignAllianceActive(this, other);
    }

    public static void configureCampaignAlliances(boolean blueGreenAlliance, boolean blueYellowAlliance) {
        campaignBlueGreenAlliance = blueGreenAlliance;
        campaignBlueYellowAlliance = blueYellowAlliance;
        campaignRedYellowAlliance = false;
    }

    public static void configureCampaignAlliances(boolean blueGreenAlliance,
                                                  boolean blueYellowAlliance,
                                                  boolean redYellowAlliance) {
        campaignBlueGreenAlliance = blueGreenAlliance;
        campaignBlueYellowAlliance = blueYellowAlliance;
        campaignRedYellowAlliance = redYellowAlliance;
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
        boolean aBrightYellow = a == BRIGHT_YELLOW;
        boolean bBrightYellow = b == BRIGHT_YELLOW;
        boolean aDarkYellow = a == DARK_YELLOW;
        boolean bDarkYellow = b == DARK_YELLOW;

        if ((aBrightYellow && (bBlue || bGreen)) || (bBrightYellow && (aBlue || aGreen))) return true;
        if ((aDarkYellow && b.teamId == 1) || (bDarkYellow && a.teamId == 1)) return true;

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
        if (campaignRedYellowAlliance) {
            boolean aRed = a.teamId == 1;
            boolean bRed = b.teamId == 1;
            if ((aRed && bYellow) || (aYellow && bRed)) {
                return true;
            }
        }
        return false;
    }

    public static Faction forTeamId(int teamId) {
        return switch (teamId) {
            case 0 -> ALLY;
            case 1 -> ENEMY;
            case 2 -> TEAM_C;
            case 3 -> TEAM_D;
            case 4 -> BRIGHT_YELLOW;
            case 5 -> DARK_YELLOW;
            default -> ALLY;
        };
    }

    public static Faction[] fourTeamFactions() {
        return new Faction[]{ALLY, ENEMY, TEAM_C, TEAM_D};
    }
}
