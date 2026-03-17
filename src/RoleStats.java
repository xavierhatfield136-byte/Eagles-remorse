/**
 * Single source of truth for baseline ship stats per role (Step 5D).
 *
 * Tuning workflow:
 *  - Adjust values here
 *  - Use dev scenarios (F6/F7/F8/Ctrl+F12) + F5 time-scale + debug overlay to evaluate
 *  - Commit when a set feels good
 *
 * Note: Doctrine multipliers (Energy vs Kinetic, etc.) still apply on top of these baselines.
 */
public final class RoleStats {

    private RoleStats() {}

    /** Baseline role stats. Values use the game's existing units. */
    public static final class Stats {
        public final double radius;
        public final int hpMax;
        public final double shieldMax;
        public final double shieldRegen;
        public final double desiredSpeed;
        public final int bountyValue;

        /** Optional: if >= 0, overrides Ship.cargoMax */
        public final int cargoMax;
        /** Optional: if >= 0, overrides Ship.miningRate */
        public final double miningRate;
        /** Optional: if >= 0, overrides Ship.miningRange */
        public final double miningRange;

        public Stats(double radius, int hpMax, double shieldMax, double shieldRegen,
                     double desiredSpeed, int bountyValue,
                     int cargoMax, double miningRate, double miningRange) {
            this.radius = radius;
            this.hpMax = hpMax;
            this.shieldMax = shieldMax;
            this.shieldRegen = shieldRegen;
            this.desiredSpeed = desiredSpeed;
            this.bountyValue = bountyValue;
            this.cargoMax = cargoMax;
            this.miningRate = miningRate;
            this.miningRange = miningRange;
        }
    }

    /** Get baseline stats for a role. */
    public static Stats get(ShipRole role) {
        if (role == null) return defaultStats();
        return switch (role) {
            case PICKET -> new Stats(16, 13, 16, 1.8, 160, 88, -1, -1, -1);
            case PATROL -> new Stats(13, 9, 7, 1.0, 225, 62, -1, -1, -1);
            case STEALTH_SHIP -> new Stats(15, 10, 9, 1.2, 245, 145, -1, -1, -1);
            case FIGHTER -> new Stats(12, 1, 1, 0.0, 270, 35, -1, -1, -1);
            case BOMBER -> new Stats(14, 1, 1, 0.0, 205, 58, -1, -1, -1);
            case PD_CRAFT -> new Stats(18, 1, 1, 0.0, 170, 105, -1, -1, -1);
            case DRONE -> new Stats(11, 1, 1, 0.0, 285, 38, -1, -1, -1);
            case FRIGATE -> new Stats(18, 19, 18, 2.0, 152, 96, -1, -1, -1);
            case MISSILE_BOAT -> new Stats(20, 11, 8, 1.2, 108, 112, -1, -1, -1);
            case CIWS_CORVETTE -> new Stats(16, 12, 8, 1.4, 205, 98, -1, -1, -1);
            case LIGHT_CRUISER -> new Stats(23, 26, 24, 2.3, 128, 152, -1, -1, -1);
            case MEDIUM_CRUISER -> new Stats(27, 40, 30, 2.7, 102, 198, -1, -1, -1);
            case CRUISER -> new Stats(27, 36, 30, 2.7, 104, 230, -1, -1, -1);
            case BATTLECRUISER -> new Stats(32, 52, 32, 2.6, 104, 388, -1, -1, -1);
            case BATTLESHIP -> new Stats(36, 84, 56, 3.4, 68, 440, -1, -1, -1);
            case DREADNOUGHT -> new Stats(44, 122, 82, 3.8, 58, 900, -1, -1, -1);
            case SUPERSHIP -> new Stats(52, 182, 134, 4.6, 48, 1700, -1, -1, -1);
            case CARRIER -> new Stats(34, 54, 38, 3.2, 80, 372, -1, -1, -1);
            case DRONE_CARRIER -> new Stats(32, 38, 20, 2.2, 110, 332, -1, -1, -1);
            case TRANSPORT -> new Stats(24, 28, 24, 2.3, 116, 170, -1, -1, -1);
            case MINER -> new Stats(18, 15, 9, 1.4, 136, 122, 180, 22.0, 64.0);
            case HAULER -> new Stats(22, 24, 16, 1.9, 102, 145, 420, 0.0, 0.0);
            case BASE -> new Stats(60, 240, 190, 7.0, 0, 900, -1, -1, -1);
            case STATIC_TURRET -> new Stats(16, 22, 10, 1.5, 0, 120, -1, -1, -1);
            default -> defaultStats();
        };
    }

    private static Stats defaultStats() {
        return new Stats(16, 10, 0, 0.0, 110, 0, -1, -1, -1);
    }

    /**
     * Apply baseline stats to a ship.
     * Safe to call during ship construction/setup (it resets hp/shields to max).
     */
    public static void applyCore(Ship s, ShipRole role) {
        if (s == null) return;
        Stats st = get(role);

        s.radius = st.radius;

        s.hpMax = st.hpMax;
        s.hp = s.hpMax;

        s.shieldMax = st.shieldMax;
        s.shieldRegen = st.shieldRegen;
        s.shieldActive = st.shieldMax > 0.0;
        s.shield = s.shieldMax;

        s.desiredSpeed = st.desiredSpeed;

        s.bountyValue = st.bountyValue;

        if (st.cargoMax >= 0) s.cargoMax = st.cargoMax;
        if (st.miningRate >= 0) s.miningRate = st.miningRate;
        if (st.miningRange >= 0) s.miningRange = st.miningRange;

        // Energy Navy heavy ships prefer the BEAM_BOLT primary.
        try {
            DoctrineProfile prof = DoctrineRegistry.forFaction(s.faction);
            if (prof.doctrine == Doctrine.ENERGY_NAVY && !(s instanceof Player)) {
                if (role == ShipRole.BATTLESHIP || role == ShipRole.BATTLECRUISER
                        || role == ShipRole.DREADNOUGHT || role == ShipRole.SUPERSHIP) {
                    s.primaryWeaponFamily = Ship.PrimaryWeaponFamily.BEAM_BOLT;
                } else {
                    s.primaryWeaponFamily = Ship.PrimaryWeaponFamily.ENERGY_BOLT;
                }
            }
        } catch (Throwable ignored) {}

        s.applyPrimaryWeaponFamily();
    }
}
