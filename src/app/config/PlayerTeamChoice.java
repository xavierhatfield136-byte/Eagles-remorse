package app.config;

public enum PlayerTeamChoice {
    TEAM_A("Blue Team", 0),
    TEAM_B("Red Team", 1),
    TEAM_C("Green Team", 2),
    TEAM_D("Yellow Team", 3),
    TEAM_E("Custom Team", 6);

    private final String label;
    private final int teamId;

    PlayerTeamChoice(String label, int teamId) {
        this.label = label;
        this.teamId = teamId;
    }

    public int teamId() {
        return teamId;
    }

    @Override
    public String toString() {
        return label;
    }

    public static PlayerTeamChoice forTeamId(int teamId) {
        for (PlayerTeamChoice c : values()) {
            if (c.teamId == teamId) return c;
        }
        return TEAM_A;
    }
}
