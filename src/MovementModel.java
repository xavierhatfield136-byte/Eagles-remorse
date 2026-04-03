public final class MovementModel {
    private MovementModel() {}

    public static final class Profile {
        final double maxSpeedMul;
        final double accelPerSec;
        final double decelPerSec;
        final double turnRateRadPerSec;
        final double reverseMul;
        final double rotationCoupling;

        Profile(double maxSpeedMul,
                double accelPerSec,
                double decelPerSec,
                double turnRateRadPerSec,
                double reverseMul,
                double rotationCoupling) {
            this.maxSpeedMul = maxSpeedMul;
            this.accelPerSec = accelPerSec;
            this.decelPerSec = decelPerSec;
            this.turnRateRadPerSec = turnRateRadPerSec;
            this.reverseMul = reverseMul;
            this.rotationCoupling = rotationCoupling;
        }
    }

    public static Profile profile(ShipRole role) {
        if (role == null) {
            return new Profile(1.00, 8.6, 7.8, Math.toRadians(136.0), 0.62, 0.20);
        }
        return switch (role) {
            case DRONE -> new Profile(1.08, 13.2, 12.8, Math.toRadians(198.0), 0.98, 0.10);
            case FIGHTER -> new Profile(1.06, 12.4, 12.0, Math.toRadians(188.0), 0.95, 0.11);
            case STEALTH_SHIP -> new Profile(1.05, 11.7, 11.2, Math.toRadians(176.0), 0.86, 0.14);
            case PD_CRAFT -> new Profile(1.04, 11.4, 10.8, Math.toRadians(178.0), 0.88, 0.14);
            case PATROL -> new Profile(1.03, 10.9, 10.3, Math.toRadians(170.0), 0.84, 0.15);
            case CIWS_CORVETTE -> new Profile(1.03, 10.5, 10.0, Math.toRadians(158.0), 0.82, 0.15);
            case BOMBER -> new Profile(1.02, 9.4, 8.8, Math.toRadians(162.0), 0.72, 0.16);
            case FRIGATE -> new Profile(1.00, 9.0, 8.4, Math.toRadians(134.0), 0.74, 0.18);
            case PICKET -> new Profile(1.00, 8.7, 8.2, Math.toRadians(146.0), 0.70, 0.18);
            case ARTILLERY_SHIP -> new Profile(1.01, 8.2, 7.6, Math.toRadians(122.0), 0.66, 0.20);
            case LIGHT_CRUISER -> new Profile(0.98, 8.1, 7.6, Math.toRadians(124.0), 0.66, 0.20);
            case MISSILE_BOAT -> new Profile(0.99, 7.8, 7.2, Math.toRadians(116.0), 0.64, 0.22);
            case BATTLECRUISER -> new Profile(0.96, 7.3, 6.8, Math.toRadians(104.0), 0.60, 0.23);
            case MINER -> new Profile(0.95, 7.2, 6.6, Math.toRadians(98.0), 0.56, 0.24);
            case MEDIUM_CRUISER, CRUISER -> new Profile(0.93, 6.6, 6.1, Math.toRadians(92.0), 0.56, 0.25);
            case TRANSPORT, HAULER -> new Profile(0.92, 6.2, 5.8, Math.toRadians(82.0), 0.52, 0.27);
            case DRONE_CARRIER -> new Profile(0.91, 6.1, 5.7, Math.toRadians(78.0), 0.50, 0.27);
            case BATTLESHIP -> new Profile(0.89, 5.5, 5.2, Math.toRadians(72.0), 0.45, 0.29);
            case CARRIER -> new Profile(0.88, 5.1, 4.8, Math.toRadians(68.0), 0.48, 0.30);
            case DREADNOUGHT -> new Profile(0.86, 4.7, 4.4, Math.toRadians(62.0), 0.42, 0.32);
            case SUPERSHIP -> new Profile(0.84, 4.1, 3.9, Math.toRadians(50.0), 0.38, 0.34);
            case TRANSPORT_TITAN -> new Profile(0.82, 3.8, 3.6, Math.toRadians(46.0), 0.36, 0.36);
            case BULWARK_TITAN -> new Profile(0.80, 3.5, 3.3, Math.toRadians(42.0), 0.34, 0.38);
            case CARRIER_SUPPORT_TITAN -> new Profile(0.81, 3.7, 3.5, Math.toRadians(44.0), 0.35, 0.37);
            case VANGUARD_TITAN -> new Profile(0.87, 4.3, 4.1, Math.toRadians(56.0), 0.42, 0.33);
            case INTERDICTION_TITAN -> new Profile(0.85, 4.0, 3.8, Math.toRadians(52.0), 0.40, 0.35);
            case COMMAND_INTEL_TITAN -> new Profile(0.84, 3.9, 3.7, Math.toRadians(50.0), 0.39, 0.35);
            case BOARDING_RECOVERY_TITAN -> new Profile(0.83, 3.8, 3.6, Math.toRadians(48.0), 0.38, 0.36);
            case ARTILLERY_TITAN -> new Profile(0.80, 3.4, 3.2, Math.toRadians(40.0), 0.34, 0.39);
            case SHIELD_BASTION_TITAN -> new Profile(0.79, 3.3, 3.1, Math.toRadians(38.0), 0.32, 0.40);
            case FLEET_TELEPORTER_TITAN -> new Profile(0.88, 4.5, 4.2, Math.toRadians(58.0), 0.44, 0.33);
            case ELITE_SUPERSHIP_COMMAND_TITAN -> new Profile(0.82, 3.7, 3.5, Math.toRadians(44.0), 0.36, 0.36);
            case MOBILE_STATION_TITAN -> new Profile(0.74, 2.8, 2.7, Math.toRadians(28.0), 0.24, 0.44);
            case HYPERWEAPON_TITAN -> new Profile(0.78, 3.2, 3.0, Math.toRadians(34.0), 0.28, 0.41);
            case MOTHERSHIP -> new Profile(0.68, 2.2, 2.1, Math.toRadians(22.0), 0.18, 0.52);
            case BASE, STATIC_TURRET -> new Profile(0.0, 0.0, 1.0, 0.0, 0.0, 0.0);
        };
    }

    public static double speedCeiling(Ship ship) {
        if (ship == null) return 55.0;
        if (ship.isTemporarilyDisabled()) return 0.0;
        Profile p = profile(ship.role);
        if (p.maxSpeedMul <= 1e-6) return 0.0;
        double base = Math.max(0.0, ship.desiredSpeed);
        double cap = Math.max(40.0, base * p.maxSpeedMul);
        if (ship.isStasisFieldTrapped()) {
            return cap * 0.10;
        }
        return cap * ship.warpChargeSpeedMultiplier();
    }

    public static double turnRateRadPerSec(Ship ship) {
        if (ship == null) return Math.toRadians(136.0);
        if (ship.isTemporarilyDisabled()) return 0.0;
        Profile p = profile(ship.role);
        return Math.max(0.0, p.turnRateRadPerSec * ship.propulsionHandlingMultiplier());
    }

    public static double reverseThrustMul(Ship ship) {
        if (ship == null) return 0.62;
        return profile(ship.role).reverseMul;
    }

    public static double rotationCoupling(Ship ship) {
        if (ship == null) return 0.20;
        return profile(ship.role).rotationCoupling;
    }

    public static double responsePerSec(Ship ship, boolean thrusting) {
        if (ship == null) return thrusting ? 8.6 : 7.8;
        Profile p = profile(ship.role);
        double base = thrusting ? p.accelPerSec : p.decelPerSec;
        return Math.max(0.0, base * ship.propulsionHandlingMultiplier());
    }

    public static double approach(double current, double desired, double responsePerSec, double dt) {
        double blend = MathUtil.clamp(Math.max(0.0, responsePerSec) * Math.max(0.0, dt), 0.0, 1.0);
        return current + (desired - current) * blend;
    }

    public static void applyDesiredVelocity(Ship ship, double desiredVxPerSec, double desiredVyPerSec, double dt, boolean thrusting) {
        if (ship == null) return;
        if (ship.isTemporarilyDisabled()) {
            ship.vx = 0.0;
            ship.vy = 0.0;
            return;
        }
        double cap = speedCeiling(ship);
        if (cap <= 1e-6) {
            desiredVxPerSec = 0.0;
            desiredVyPerSec = 0.0;
        } else {
            double desiredLen = Math.hypot(desiredVxPerSec, desiredVyPerSec);
            if (desiredLen > cap + 1e-6) {
                double scl = cap / desiredLen;
                desiredVxPerSec *= scl;
                desiredVyPerSec *= scl;
            }
        }
        if (dt <= 1e-9) {
            ship.vx = 0.0;
            ship.vy = 0.0;
            return;
        }
        double curVx = ship.vx / dt;
        double curVy = ship.vy / dt;
        double rsp = responsePerSec(ship, thrusting);
        curVx = approach(curVx, desiredVxPerSec, rsp, dt);
        curVy = approach(curVy, desiredVyPerSec, rsp, dt);
        ship.vx = curVx * dt;
        ship.vy = curVy * dt;
    }
}
