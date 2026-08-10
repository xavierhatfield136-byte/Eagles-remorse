public enum FleetLevel {
    LEVEL_1_LIGHT(1),
    LEVEL_2_MEDIUM(2),
    LEVEL_3_LARGE_CAPITAL(3),
    LEVEL_4_TITAN_TASK_FORCE(4),
    LEVEL_5_GRAND_FLEET(5);

    public final int level;

    FleetLevel(int level) {
        this.level = level;
    }

    public String label() {
        return starLabel();
    }

    public String shortLabel() {
        return starLabel();
    }

    public String starLabel() {
        return Math.max(1, Math.min(5, level)) + "-STAR FLEET";
    }
}
