package app.config;

/**
 * High-level game modes.
 */
public enum GameMode {
    TUTORIAL("Commander's Academy"),
    CAMPAIGN_OPS("Open World Campaign"),
    FLEET("Fleet"),
    LAST_STAND("Last Stand"),
    RESOURCE_RUSH("Resource Rush"),
    FOUR_TEAM_DOMINATION("4 Team Domination"),
    CUSTOM_BATTLES("Custom Battles"),
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
