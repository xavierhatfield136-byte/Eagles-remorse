public final class BalanceConfig {
    private BalanceConfig() {}

    // Asteroid collision tuning (shared by collision + AI avoidance).
    public static final double ASTEROID_COLLISION_RADIUS_SCALE = 0.82;
    public static final double ASTEROID_COLLISION_RADIUS_MIN = 6.0;

    public static final double ASTEROID_AVOID_LOOKAHEAD_BASE = 120.0;
    public static final double ASTEROID_AVOID_LOOKAHEAD_SPEED = 0.55;
    public static final double ASTEROID_AVOID_CLEARANCE_BASE = 14.0;

    public static double asteroidAvoidanceLookaheadScale(ShipRole role) {
        if (role == null) return 1.0;
        return switch (role) {
            case FIGHTER, DRONE, PD_CRAFT -> 0.80;
            case PATROL, PICKET, STEALTH_SHIP -> 0.90;
            case FRIGATE, ARTILLERY_SHIP, MISSILE_BOAT, CIWS_CORVETTE, LIGHT_CRUISER -> 1.00;
            case MEDIUM_CRUISER, CRUISER, BATTLECRUISER -> 1.20;
            case BATTLESHIP, DREADNOUGHT, SUPERSHIP -> 1.45;
            case CARRIER, DRONE_CARRIER, TRANSPORT, HAULER, BASE, STATIC_TURRET -> 1.35;
            default -> 1.00;
        };
    }

    public static double asteroidAvoidanceClearanceScale(ShipRole role) {
        if (role == null) return 1.0;
        return switch (role) {
            case FIGHTER, DRONE, PD_CRAFT -> 0.70;
            case PATROL, PICKET, STEALTH_SHIP -> 0.80;
            case FRIGATE, ARTILLERY_SHIP, MISSILE_BOAT, CIWS_CORVETTE, LIGHT_CRUISER -> 1.00;
            case MEDIUM_CRUISER, CRUISER, BATTLECRUISER -> 1.25;
            case BATTLESHIP, DREADNOUGHT, SUPERSHIP -> 1.55;
            case CARRIER, DRONE_CARRIER, TRANSPORT, HAULER, BASE, STATIC_TURRET -> 1.45;
            default -> 1.00;
        };
    }
}
