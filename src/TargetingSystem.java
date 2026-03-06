import java.util.ArrayList;
import java.util.List;

public final class TargetingSystem {
    private TargetingSystem(){}
    private static final double CLOAK_PROX_REVEAL_RANGE = 220.0;
    private static final double CLOAK_BASE_SENSOR_BONUS = 130.0;
    private static final double CLOAK_CARRIER_SENSOR_BONUS = 70.0;

    public static void lockClosestToMouse(GameContext ctx, PlayerControl controls) {
        double mx = CameraSystem.screenToWorldX(ctx, controls.getMouseX());
        double my = CameraSystem.screenToWorldY(ctx, controls.getMouseY());
        Ship observer = (ctx == null ? null : ctx.player);
        Ship s = findClosestEnemyToPoint(ctx, observer, mx, my, 280);
        if (s == null) {
            ctx.eventBanner = "NO ENEMY NEAR CURSOR";
            ctx.eventBannerT = 1.2;
            return;
        }
        ctx.lockedTarget = s;
        ctx.eventBanner = "LOCKED: " + s.name;
        ctx.eventBannerT = 1.0;
    }

    public static void cycleLockedTarget(GameContext ctx, int dir) {
        List<Ship> enemies = new ArrayList<>();
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!isAlive(s)) continue;
            if (s.role == ShipRole.BASE) continue;
            if (!TeamSystem.isHostileToPlayer(ctx, s.faction)) continue;
            if (!isDetectableToObserver(ctx.player, s)) continue;
            enemies.add(s);
        }
        if (enemies.isEmpty()) {
            ctx.lockedTarget = null;
            return;
        }

        int idx = -1;
        for (int i = 0; i < enemies.size(); i++) {
            if (enemies.get(i) == ctx.lockedTarget) { idx = i; break; }
        }
        if (idx < 0) idx = (ctx.lockedIndexHint < enemies.size()) ? ctx.lockedIndexHint : 0;

        idx = (idx + dir) % enemies.size();
        if (idx < 0) idx += enemies.size();

        ctx.lockedIndexHint = idx;
        ctx.lockedTarget = enemies.get(idx);
        ctx.eventBanner = "LOCKED: " + ctx.lockedTarget.name;
        ctx.eventBannerT = 0.9;
    }

    public static Ship getPreferredEnemyTarget(GameContext ctx, Ship seeker) {
        if (ctx.lockedTarget != null && isAlive(ctx.lockedTarget)
                && seeker.faction != null
                && ctx.player != null
                && seeker.faction.isFriendlyTo(ctx.player.faction)
                && !seeker.faction.isFriendlyTo(ctx.lockedTarget.faction)
                && isDetectableToObserver(seeker, ctx.lockedTarget)) {
            return ctx.lockedTarget;
        }

        Ship best = null;
        double bestD2 = Double.MAX_VALUE;

        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!isAlive(s)) continue;

            if (seeker.faction != null && s.faction != null && !seeker.faction.isFriendlyTo(s.faction)) {
                if (!isDetectableToObserver(seeker, s)) continue;
                double d2 = GameMath.dist2(seeker.x, seeker.y, s.x, s.y);
                if (d2 < bestD2) { bestD2 = d2; best = s; }
            }
        }
        return best;
    }

    public static Ship findClosestEnemyToPoint(GameContext ctx, double x, double y, double maxDist) {
        Ship observer = (ctx == null ? null : ctx.player);
        return findClosestEnemyToPoint(ctx, observer, x, y, maxDist);
    }

    public static Ship findClosestEnemyToPoint(GameContext ctx, Ship observer, double x, double y, double maxDist) {
        Ship best = null;
        double bestD2 = maxDist * maxDist;
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!isAlive(s)) continue;
            if (!TeamSystem.isHostileToPlayer(ctx, s.faction)) continue;
            if (s.role == ShipRole.BASE) continue;
            if (!isDetectableToObserver(observer, s)) continue;
            double d2 = GameMath.dist2(x, y, s.x, s.y);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = s;
            }
        }
        return best;
    }

    public static boolean isDetectableToObserver(Ship observer, Ship target) {
        if (target == null) return false;
        if (!target.isStealth) return true;
        if (!target.isCloaked()) return true;
        if (target.revealTimer > 0.0) return true;
        if (observer == null) return false;

        double revealRange = CLOAK_PROX_REVEAL_RANGE + target.radius + observer.radius * 0.25;
        if (observer.role == ShipRole.BASE || observer.role == ShipRole.STATIC_TURRET) {
            revealRange += CLOAK_BASE_SENSOR_BONUS;
        } else if (observer.isCarrier) {
            revealRange += CLOAK_CARRIER_SENSOR_BONUS;
        }
        revealRange *= Math.max(0.20, observer.sensorRangeMultiplier());
        return GameMath.dist2(observer.x, observer.y, target.x, target.y) <= revealRange * revealRange;
    }

    private static boolean isAlive(Ship s) {
        if (s == null) return false;
        return s.alive && !s.dying && s.hp > 0;
    }
}
