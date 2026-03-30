package app.config;

/**
 * High-level game modes.
 */
public enum GameMode {
    TUTORIAL("Tutorial"),
    CAMPAIGN_OPS("Campaign Ops"),
    LAST_STAND("Last Stand"),
    RESOURCE_RUSH("Resource Rush"),
    FOUR_TEAM_DOMINATION("4 Team Domination"),
    SHOOTING_RANGE("Shooting Range"),
    SHOWCASE("Showcase");

    private final String label;

    GameMode(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
