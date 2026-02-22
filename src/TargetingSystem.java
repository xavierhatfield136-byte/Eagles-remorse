import java.util.ArrayList;
import java.util.List;

public final class TargetingSystem {
    private TargetingSystem(){}

    public static void lockClosestToMouse(GameContext ctx, PlayerControl controls) {
        double mx = ctx.camX + controls.getMouseX();
        double my = ctx.camY + controls.getMouseY();
        Ship s = findClosestEnemyToPoint(ctx, mx, my, 280);
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
            if (TeamSystem.isHostileToPlayer(ctx, s.faction)) enemies.add(s);
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
                && !seeker.faction.isFriendlyTo(ctx.lockedTarget.faction)) {
            return ctx.lockedTarget;
        }

        Ship best = null;
        double bestD2 = Double.MAX_VALUE;

        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!isAlive(s)) continue;

            if (seeker.faction != null && s.faction != null && !seeker.faction.isFriendlyTo(s.faction)) {
                double d2 = GameMath.dist2(seeker.x, seeker.y, s.x, s.y);
                if (d2 < bestD2) { bestD2 = d2; best = s; }
            }
        }
        return best;
    }

    public static Ship findClosestEnemyToPoint(GameContext ctx, double x, double y, double maxDist) {
        Ship best = null;
        double bestD2 = maxDist * maxDist;
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!isAlive(s)) continue;
            if (!TeamSystem.isHostileToPlayer(ctx, s.faction)) continue;
            if (s.role == ShipRole.BASE) continue;
            double d2 = GameMath.dist2(x, y, s.x, s.y);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = s;
            }
        }
        return best;
    }

    // Compatibility: some versions have s.dead, others have isAlive() or hp<=0; handle both.
    private static boolean isAlive(Ship s) {
        try {
            // If Ship has boolean dead
            java.lang.reflect.Field f = s.getClass().getField("dead");
            Object v = f.get(s);
            if (v instanceof Boolean) return !((Boolean)v);
        } catch (Throwable ignored) {}
        try {
            java.lang.reflect.Method m = s.getClass().getMethod("isAlive");
            Object v = m.invoke(s);
            if (v instanceof Boolean) return (Boolean)v;
        } catch (Throwable ignored) {}
        // fallback
        return s.hp > 0;
    }
}
