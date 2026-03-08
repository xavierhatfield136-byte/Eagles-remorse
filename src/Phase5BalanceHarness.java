import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/**
 * Phase 5 balance harness.
 *
 * Covers:
 * - propulsion-room mobility penalties
 * - duel behavior
 * - fleet line engagement mobility spread
 * - dense asteroid navigation pressure
 * - boss chase/disengage windows with emergency thrust risk
 */
public final class Phase5BalanceHarness {
    private static final double DT = GameContext.DT;
    private static final int TICKS_PER_SEC = Math.max(1, (int) Math.round(1.0 / Math.max(1e-9, DT)));

    private Phase5BalanceHarness() {}

    public static void main(String[] args) {
        boolean strict = false;
        for (String arg : args) {
            if (arg != null && "--strict".equalsIgnoreCase(arg.trim())) strict = true;
        }

        List<String> failures = new ArrayList<>();

        PropulsionPenaltyResult propulsion = runPropulsionPenaltyCheck();
        if (!propulsion.pass) failures.add(propulsion.failureReason);

        ScenarioResult duel = runDuelScenario();
        ScenarioResult fleet = runFleetLineScenario();
        ScenarioResult asteroid = runAsteroidScenario();
        ScenarioResult boss = runBossScenario();

        if (!duel.pass) failures.add(duel.failureReason);
        if (!fleet.pass) failures.add(fleet.failureReason);
        if (!asteroid.pass) failures.add(asteroid.failureReason);
        if (!boss.pass) failures.add(boss.failureReason);

        System.out.println("[phase5] propulsion penalty: speed " + fmt(propulsion.speedHealthy) + " -> "
                + fmt(propulsion.speedDamaged) + " turn " + fmt(Math.toDegrees(propulsion.turnHealthy))
                + " -> " + fmt(Math.toDegrees(propulsion.turnDamaged)) + " deg/s");
        System.out.println("[phase5] duel: engaged " + fmtPct(duel.metricA) + " min-range " + fmt(duel.metricB));
        System.out.println("[phase5] fleet-line: outlier-ratio " + fmt(fleet.metricA) + " monotonic=" + fleet.metricB);
        System.out.println("[phase5] asteroid: laps scout/heavy " + fmt(asteroid.metricA) + "/" + fmt(asteroid.metricB)
                + " collisions " + asteroid.extraA + "/" + asteroid.extraB + " cooldownSeen=" + asteroid.extraC);
        System.out.println("[phase5] boss: chase/disengage/reengage median range "
                + fmt(boss.metricA) + "/" + fmt(boss.metricB) + "/" + fmt(boss.metricC));

        if (failures.isEmpty()) {
            System.out.println("[phase5] checks: PASS");
            return;
        }

        System.out.println("[phase5] checks: FAIL");
        for (String failure : failures) {
            System.out.println(" - " + failure);
        }
        if (strict) System.exit(2);
    }

    private static PropulsionPenaltyResult runPropulsionPenaltyCheck() {
        FleetShip ship = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 0.0, 0.0);
        ship.shieldActive = false;
        ship.shield = 0.0;
        ship.angle = 0.0;
        ship.vx = 0.0;
        ship.vy = 0.0;

        double speedHealthy = MovementModel.speedCeiling(ship);
        double turnHealthy = MovementModel.turnRateRadPerSec(ship);

        // Apply localized hits until propulsion rooms clearly degrade.
        for (int i = 0; i < 60 && ship.alive && ship.propulsionRoomIntegrity() > 0.48; i++) {
            hitRoom(ship, ShipRoomLayout.RoomId.ENGINES, 3);
            hitRoom(ship, ShipRoomLayout.RoomId.WARP_DRIVE, 2);
        }
        ship.update(DT);

        double speedDamaged = MovementModel.speedCeiling(ship);
        double turnDamaged = MovementModel.turnRateRadPerSec(ship);

        boolean pass = ship.propulsionRoomIntegrity() < 0.75
                && speedDamaged < speedHealthy * 0.90
                && turnDamaged < turnHealthy * 0.90;

        String reason = pass
                ? ""
                : "propulsion damage should reduce both speed ceiling and turn rate";
        return new PropulsionPenaltyResult(pass, speedHealthy, speedDamaged, turnHealthy, turnDamaged, reason);
    }

    private static ScenarioResult runDuelScenario() {
        FleetShip a = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 320.0, 520.0);
        FleetShip b = new FleetShip(ShipRole.MISSILE_BOAT, Faction.ENEMY, 1480.0, 520.0);
        a.angle = 0.0;
        b.angle = Math.PI;

        int ticks = 48 * TICKS_PER_SEC;
        int engaged = 0;
        double minRange = Double.POSITIVE_INFINITY;
        for (int i = 0; i < ticks; i++) {
            double d = dist(a, b);
            if (d >= 260.0 && d <= 900.0) engaged++;
            minRange = Math.min(minRange, d);

            if (d > 540.0) steerToward(a, b.x, b.y, 1.0);
            else orbit(a, b.x, b.y, 430.0, 1.0, 0.96);

            if (d > 620.0) steerToward(b, a.x, a.y, 1.0);
            else orbit(b, a.x, a.y, 500.0, -1.0, 0.92);

            a.update(DT);
            b.update(DT);
        }

        double engagedRatio = engaged / (double) ticks;
        boolean pass = engagedRatio > 0.40 && engagedRatio < 0.98 && minRange > 140.0;
        String reason = pass ? "" : "duel should sustain contact without permanent point-blank overlap";
        return new ScenarioResult(pass, engagedRatio, minRange, 0.0, 0.0, 0.0, false, reason);
    }

    private static ScenarioResult runFleetLineScenario() {
        ShipRole[] roles = {
                ShipRole.PATROL, ShipRole.FRIGATE, ShipRole.LIGHT_CRUISER, ShipRole.BATTLECRUISER, ShipRole.DREADNOUGHT
        };
        List<FleetShip> allies = new ArrayList<>();
        List<FleetShip> enemies = new ArrayList<>();
        EnumMap<ShipRole, Double> speedSum = new EnumMap<>(ShipRole.class);
        EnumMap<ShipRole, Integer> speedCount = new EnumMap<>(ShipRole.class);

        for (int i = 0; i < roles.length; i++) {
            double y = 280.0 + i * 180.0;
            FleetShip ally = new FleetShip(roles[i], Faction.ALLY, 260.0, y);
            FleetShip enemy = new FleetShip(roles[i], Faction.ENEMY, 1820.0, y);
            ally.angle = 0.0;
            enemy.angle = Math.PI;
            allies.add(ally);
            enemies.add(enemy);
            speedSum.put(roles[i], 0.0);
            speedCount.put(roles[i], 0);
        }

        int ticks = 60 * TICKS_PER_SEC;
        for (int t = 0; t < ticks; t++) {
            for (int i = 0; i < roles.length; i++) {
                FleetShip ally = allies.get(i);
                FleetShip enemy = enemies.get(i);
                double d = dist(ally, enemy);
                if (d > 640.0) {
                    steerToward(ally, enemy.x, enemy.y, 1.0);
                    steerToward(enemy, ally.x, ally.y, 1.0);
                } else {
                    orbit(ally, enemy.x, enemy.y, 520.0, 1.0, 0.92);
                    orbit(enemy, ally.x, ally.y, 520.0, -1.0, 0.92);
                }
            }

            for (FleetShip s : allies) {
                s.update(DT);
                ShipRole role = s.role;
                speedSum.put(role, speedSum.getOrDefault(role, 0.0) + speedPerSec(s));
                speedCount.put(role, speedCount.getOrDefault(role, 0) + 1);
            }
            for (FleetShip s : enemies) {
                s.update(DT);
                ShipRole role = s.role;
                speedSum.put(role, speedSum.getOrDefault(role, 0.0) + speedPerSec(s));
                speedCount.put(role, speedCount.getOrDefault(role, 0) + 1);
            }
        }

        double[] avg = new double[roles.length];
        for (int i = 0; i < roles.length; i++) {
            double sum = speedSum.getOrDefault(roles[i], 0.0);
            int n = Math.max(1, speedCount.getOrDefault(roles[i], 0));
            avg[i] = sum / n;
        }

        double minEff = Double.POSITIVE_INFINITY;
        double maxEff = 0.0;
        for (int i = 0; i < roles.length; i++) {
            double ceiling = MovementModel.speedCeiling(new FleetShip(roles[i], Faction.ALLY, 0.0, 0.0));
            double eff = avg[i] / Math.max(1e-6, ceiling);
            minEff = Math.min(minEff, eff);
            maxEff = Math.max(maxEff, eff);
        }
        double outlierRatio = maxEff / Math.max(1e-6, minEff);

        boolean monotonic = true;
        for (int i = 1; i < avg.length; i++) {
            if (!(avg[i - 1] > avg[i] + 1e-6)) {
                monotonic = false;
                break;
            }
        }

        boolean pass = outlierRatio <= 1.65 && monotonic;
        String reason = pass ? "" : "fleet line scenario should preserve role speed ordering without dominant outlier";
        return new ScenarioResult(pass, outlierRatio, monotonic ? 1.0 : 0.0, 0.0, 0.0, 0.0, false, reason);
    }

    private static ScenarioResult runAsteroidScenario() {
        FleetShip scout = new FleetShip(ShipRole.PATROL, Faction.ALLY, 260.0, 320.0);
        FleetShip heavy = new FleetShip(ShipRole.BATTLECRUISER, Faction.ALLY, 260.0, 620.0);
        scout.angle = 0.0;
        heavy.angle = 0.0;

        Vec2[] waypoints = new Vec2[]{
                new Vec2(440.0, 260.0),
                new Vec2(720.0, 710.0),
                new Vec2(980.0, 230.0),
                new Vec2(1250.0, 700.0),
                new Vec2(1530.0, 250.0),
                new Vec2(1780.0, 640.0)
        };
        Obstacle[] obstacles = new Obstacle[]{
                new Obstacle(640.0, 460.0, 90.0),
                new Obstacle(930.0, 470.0, 110.0),
                new Obstacle(1210.0, 460.0, 95.0),
                new Obstacle(1510.0, 460.0, 100.0)
        };

        int scoutWp = 0;
        int heavyWp = 0;
        int scoutLaps = 0;
        int heavyLaps = 0;
        int scoutCollisions = 0;
        int heavyCollisions = 0;
        boolean[] scoutInside = new boolean[obstacles.length];
        boolean[] heavyInside = new boolean[obstacles.length];
        boolean heavyCooldownSeen = false;

        int ticks = 72 * TICKS_PER_SEC;
        for (int t = 0; t < ticks; t++) {
            int prevScoutWp = scoutWp;
            scoutWp = driveWaypointShip(scout, waypoints, obstacles, scoutWp, 1.0, +1.0);
            if (prevScoutWp == waypoints.length - 1 && scoutWp == 0) scoutLaps++;

            Vec2 heavyWpPos = waypoints[heavyWp];
            double heavyDist = Math.hypot(heavyWpPos.x - heavy.x, heavyWpPos.y - heavy.y);
            if (!heavy.isEmergencyThrustActive() && heavy.emergencyThrustCooldownRemaining() <= 1e-6 && heavyDist > 420.0) {
                heavy.setEmergencyThrustMode(true);
            } else if (heavy.isEmergencyThrustActive() && heavyDist < 180.0) {
                heavy.setEmergencyThrustMode(false);
            }
            int prevHeavyWp = heavyWp;
            heavyWp = driveWaypointShip(heavy, waypoints, obstacles, heavyWp, 0.94, -1.0);
            if (prevHeavyWp == waypoints.length - 1 && heavyWp == 0) heavyLaps++;

            scout.update(DT);
            heavy.update(DT);

            for (int i = 0; i < obstacles.length; i++) {
                Obstacle o = obstacles[i];
                boolean scoutNow = distSq(scout.x, scout.y, o.x, o.y) <= o.r * o.r;
                if (scoutNow && !scoutInside[i]) scoutCollisions++;
                scoutInside[i] = scoutNow;

                boolean heavyNow = distSq(heavy.x, heavy.y, o.x, o.y) <= o.r * o.r;
                if (heavyNow && !heavyInside[i]) heavyCollisions++;
                heavyInside[i] = heavyNow;
            }

            if (heavy.emergencyThrustCooldownRemaining() > 0.05) heavyCooldownSeen = true;
        }

        int scoutCollisionLimit = Math.max(4, scoutLaps * 4);
        int heavyCollisionLimit = Math.max(5, heavyLaps * 4);
        boolean pass = scoutLaps >= 1 && heavyLaps >= 1
                && scoutCollisions <= scoutCollisionLimit && heavyCollisions <= heavyCollisionLimit
                && heavyCooldownSeen;
        String reason = pass ? "" : "asteroid navigation should keep completion pressure and show emergency thrust risk";
        ScenarioResult out = new ScenarioResult(pass, scoutLaps, heavyLaps, 0.0, scoutCollisions, heavyCollisions,
                heavyCooldownSeen, reason);
        return out;
    }

    private static ScenarioResult runBossScenario() {
        FleetShip boss = new FleetShip(ShipRole.DREADNOUGHT, Faction.ENEMY, 220.0, 480.0);
        FleetShip runner = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 880.0, 500.0);
        boss.angle = 0.0;
        runner.angle = Math.PI;

        List<Double> chaseDistances = new ArrayList<>();
        List<Double> disengageDistances = new ArrayList<>();
        List<Double> reengageDistances = new ArrayList<>();

        int ticks = 60 * TICKS_PER_SEC;
        for (int t = 0; t < ticks; t++) {
            double time = t * DT;
            if (time < 20.0) {
                steerToward(boss, runner.x, runner.y, 1.0);
                orbit(runner, boss.x, boss.y, 500.0, 1.0, 0.95);
                chaseDistances.add(dist(boss, runner));
            } else if (time < 40.0) {
                if (!runner.isEmergencyThrustActive() && runner.emergencyThrustCooldownRemaining() <= 1e-6) {
                    runner.setEmergencyThrustMode(true);
                }
                steerToward(boss, runner.x, runner.y, 1.0);
                steerAway(runner, boss.x, boss.y, 1.0);
                disengageDistances.add(dist(boss, runner));
            } else {
                runner.setEmergencyThrustMode(false);
                steerToward(boss, runner.x, runner.y, 1.0);
                steerToward(runner, boss.x, boss.y, 0.95);
                reengageDistances.add(dist(boss, runner));
            }

            boss.update(DT);
            runner.update(DT);
        }

        double chaseMed = median(chaseDistances);
        double disengageMed = median(disengageDistances);
        double reengageMed = median(reengageDistances);

        boolean pass = disengageMed > chaseMed + 90.0
                && reengageMed < disengageMed - 80.0;
        String reason = pass ? "" : "boss chase/disengage window should expand and then close again";
        return new ScenarioResult(pass, chaseMed, disengageMed, reengageMed, 0.0, 0.0, false, reason);
    }

    private static int driveWaypointShip(Ship ship, Vec2[] waypoints, Obstacle[] obstacles,
                                         int idx, double speedMul, double orbitDir) {
        if (ship == null || waypoints == null || waypoints.length == 0) return idx;
        Vec2 wp = waypoints[Math.max(0, Math.min(waypoints.length - 1, idx))];
        double toX = wp.x - ship.x;
        double toY = wp.y - ship.y;
        double toLen = Math.hypot(toX, toY) + 1e-9;
        double dirX = toX / toLen;
        double dirY = toY / toLen;

        double avoidX = 0.0;
        double avoidY = 0.0;
        if (obstacles != null) {
            for (Obstacle o : obstacles) {
                double dx = ship.x - o.x;
                double dy = ship.y - o.y;
                double d = Math.hypot(dx, dy) + 1e-9;
                double range = o.r + 170.0;
                if (d < range) {
                    double w = (range - d) / range;
                    avoidX += (dx / d) * w * 1.25;
                    avoidY += (dy / d) * w * 1.25;
                }
            }
        }

        double vx = dirX + avoidX;
        double vy = dirY + avoidY;
        double vLen = Math.hypot(vx, vy);
        if (vLen <= 1e-9) {
            orbit(ship, wp.x, wp.y, 140.0, orbitDir, speedMul * 0.85);
        } else {
            vx /= vLen;
            vy /= vLen;
            steerVector(ship, vx, vy, speedMul);
        }

        if (toLen < 95.0) {
            idx++;
            if (idx >= waypoints.length) idx = 0;
        }
        return idx;
    }

    private static void steerToward(Ship ship, double tx, double ty, double speedMul) {
        double dx = tx - ship.x;
        double dy = ty - ship.y;
        double len = Math.hypot(dx, dy);
        if (len <= 1e-9) {
            MovementModel.applyDesiredVelocity(ship, 0.0, 0.0, DT, false);
            return;
        }
        steerVector(ship, dx / len, dy / len, speedMul);
    }

    private static void steerAway(Ship ship, double tx, double ty, double speedMul) {
        double dx = ship.x - tx;
        double dy = ship.y - ty;
        double len = Math.hypot(dx, dy);
        if (len <= 1e-9) {
            MovementModel.applyDesiredVelocity(ship, 0.0, 0.0, DT, false);
            return;
        }
        steerVector(ship, dx / len, dy / len, speedMul);
    }

    private static void steerVector(Ship ship, double dirX, double dirY, double speedMul) {
        double desiredAngle = Math.atan2(dirY, dirX);
        rotateToward(ship, desiredAngle);
        double speed = MovementModel.speedCeiling(ship) * Math.max(0.0, speedMul);
        double desiredVx = Math.cos(ship.angle) * speed;
        double desiredVy = Math.sin(ship.angle) * speed;
        MovementModel.applyDesiredVelocity(ship, desiredVx, desiredVy, DT, true);
    }

    private static void orbit(Ship ship, double cx, double cy, double desiredRange, double dir, double speedMul) {
        double dx = cx - ship.x;
        double dy = cy - ship.y;
        double d = Math.hypot(dx, dy) + 1e-9;
        double ux = dx / d;
        double uy = dy / d;
        double tx = -uy * dir;
        double ty = ux * dir;
        double radial = MathUtil.clamp((d - desiredRange) / Math.max(1.0, desiredRange), -1.0, 1.0);
        double blend = 0.58;
        double dirX = tx * (1.0 - blend) + ux * blend * radial;
        double dirY = ty * (1.0 - blend) + uy * blend * radial;
        double len = Math.hypot(dirX, dirY);
        if (len <= 1e-9) {
            steerToward(ship, cx, cy, speedMul);
            return;
        }
        steerVector(ship, dirX / len, dirY / len, speedMul);
    }

    private static void rotateToward(Ship ship, double desiredAngle) {
        double delta = MathUtil.normalizeAngle(desiredAngle - ship.angle);
        double maxStep = MovementModel.turnRateRadPerSec(ship) * DT;
        delta = MathUtil.clamp(delta, -maxStep, maxStep);
        ship.angle = MathUtil.normalizeAngle(ship.angle + delta);
    }

    private static void hitRoom(Ship ship, ShipRoomLayout.RoomId roomId, int damage) {
        if (ship == null || roomId == null || damage <= 0) return;
        ShipRoomLayout.RoomDef room = ShipRoomLayout.roomForId(ship.role, roomId);
        if (room == null) return;
        double nx = average(room.xs);
        double ny = average(room.ys);
        double wx = ship.x + nx * ship.radius;
        double wy = ship.y + ny * ship.radius;
        ship.takeDamage(damage, wx, wy, 0.0, 0.0);
    }

    private static double average(double[] values) {
        if (values == null || values.length == 0) return 0.0;
        double sum = 0.0;
        for (double v : values) sum += v;
        return sum / values.length;
    }

    private static double speedPerSec(Ship s) {
        if (s == null) return 0.0;
        return Math.hypot(s.vx, s.vy) / Math.max(1e-9, DT);
    }

    private static double dist(Ship a, Ship b) {
        if (a == null || b == null) return 0.0;
        return Math.hypot(a.x - b.x, a.y - b.y);
    }

    private static double distSq(double ax, double ay, double bx, double by) {
        double dx = ax - bx;
        double dy = ay - by;
        return dx * dx + dy * dy;
    }

    private static double median(List<Double> values) {
        if (values == null || values.isEmpty()) return 0.0;
        List<Double> copy = new ArrayList<>(values.size());
        for (Double v : values) {
            if (v != null && Double.isFinite(v)) copy.add(v);
        }
        if (copy.isEmpty()) return 0.0;
        copy.sort(Double::compareTo);
        int n = copy.size();
        if ((n & 1) == 1) return copy.get(n / 2);
        return (copy.get(n / 2 - 1) + copy.get(n / 2)) * 0.5;
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.US, "%.3f", v);
    }

    private static String fmtPct(double v) {
        return String.format(java.util.Locale.US, "%.1f%%", v * 100.0);
    }

    private static final class Vec2 {
        final double x;
        final double y;
        Vec2(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    private static final class Obstacle {
        final double x;
        final double y;
        final double r;
        Obstacle(double x, double y, double r) {
            this.x = x;
            this.y = y;
            this.r = r;
        }
    }

    private static final class PropulsionPenaltyResult {
        final boolean pass;
        final double speedHealthy;
        final double speedDamaged;
        final double turnHealthy;
        final double turnDamaged;
        final String failureReason;

        PropulsionPenaltyResult(boolean pass,
                                double speedHealthy,
                                double speedDamaged,
                                double turnHealthy,
                                double turnDamaged,
                                String failureReason) {
            this.pass = pass;
            this.speedHealthy = speedHealthy;
            this.speedDamaged = speedDamaged;
            this.turnHealthy = turnHealthy;
            this.turnDamaged = turnDamaged;
            this.failureReason = failureReason;
        }
    }

    private static final class ScenarioResult {
        final boolean pass;
        final double metricA;
        final double metricB;
        final double metricC;
        final double extraA;
        final double extraB;
        final boolean extraC;
        final String failureReason;

        ScenarioResult(boolean pass,
                       double metricA,
                       double metricB,
                       double metricC,
                       double extraA,
                       double extraB,
                       boolean extraC,
                       String failureReason) {
            this.pass = pass;
            this.metricA = metricA;
            this.metricB = metricB;
            this.metricC = metricC;
            this.extraA = extraA;
            this.extraB = extraB;
            this.extraC = extraC;
            this.failureReason = failureReason;
        }
    }
}
