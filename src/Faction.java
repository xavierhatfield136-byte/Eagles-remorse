public enum Faction {
    PLAYER(0, "Blue"),
    ALLY(0, "Blue"),
    ENEMY(1, "Red"),
    TEAM_C(2, "Green"),
    TEAM_D(3, "Yellow");

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
        return this.teamId == other.teamId;
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
