import java.util.ArrayList;
import java.util.List;

public final class TargetingSystem {
    private TargetingSystem(){}

    public static boolean isCiwsOnlyTarget(Ship target) {
        if (target == null || target.role == null) return false;
        return target.role == ShipRole.FIGHTER || target.role == ShipRole.BOMBER;
    }

    public static boolean isMainBatteryScreenTarget(Ship observer, Ship target) {
        if (observer == null || target == null) return false;
        if (!target.isSmallCraft()) return false;
        if (observer.isSmallCraft()) return false;
        if (observer.role == null) return true;
        return switch (observer.role) {
            case PD_CRAFT, CIWS_CORVETTE, STATIC_TURRET -> false;
            default -> true;
        };
    }

    public static void lockClosestToMouse(GameContext ctx, PlayerControl controls) {
        if (ctx == null || controls == null) return;
        double mx = CameraSystem.screenToWorldX(ctx, controls.getMouseX());
        double my = CameraSystem.screenToWorldY(ctx, controls.getMouseY());
        Ship observer = ctx.player;
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
            if (isCiwsOnlyTarget(s)) continue;
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
                && !isCiwsOnlyTarget(ctx.lockedTarget)
                && isDetectableToObserver(seeker, ctx.lockedTarget)) {
            return ctx.lockedTarget;
        }

        Ship best = null;
        double bestD2 = Double.MAX_VALUE;
        List<Ship> nearby = new ArrayList<>();
        ctx.entityQuery.collectHostileShipsNear(seeker.faction, seeker.x, seeker.y, 2200.0, nearby);

        for (Ship s : nearby) {
            if (s == null) continue;
            if (!isAlive(s)) continue;
            if (isCiwsOnlyTarget(s)) continue;

            if (seeker.faction != null && s.faction != null && !seeker.faction.isFriendlyTo(s.faction)) {
                if (!isDetectableToObserver(seeker, s)) continue;
                double d2 = GameMath.dist2(seeker.x, seeker.y, s.x, s.y);
                if (d2 < bestD2) { bestD2 = d2; best = s; }
            }
        }
        return best;
    }

    public static Ship findClosestEnemyToPoint(GameContext ctx, double x, double y, double maxDist) {
        if (ctx == null) return null;
        Ship observer = ctx.player;
        return findClosestEnemyToPoint(ctx, observer, x, y, maxDist);
    }

    public static Ship findClosestEnemyToPoint(GameContext ctx, Ship observer, double x, double y, double maxDist) {
        if (ctx == null) return null;
        Ship best = null;
        double bestD2 = maxDist * maxDist;
        Faction perspective = (observer == null) ? ((ctx.player == null) ? null : ctx.player.faction) : observer.faction;
        List<Ship> nearby = new ArrayList<>();
        ctx.entityQuery.collectHostileShipsNear(perspective, x, y, maxDist, nearby);
        for (Ship s : nearby) {
            if (s == null) continue;
            if (!isAlive(s)) continue;
            if (!TeamSystem.isHostileToPlayer(ctx, s.faction)) continue;
            if (isCiwsOnlyTarget(s)) continue;
            if (!isDetectableToObserver(observer, s)) continue;
            double d2 = GameMath.dist2(x, y, s.x, s.y);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = s;
            }
        }
        return best;
    }

    public static Ship findClosestEngagementTarget(GameContext ctx, Ship observer, double x, double y, double maxDist) {
        if (ctx == null) return null;
        Ship best = null;
        double bestD2 = maxDist * maxDist;
        Faction perspective = (observer == null) ? ((ctx.player == null) ? null : ctx.player.faction) : observer.faction;
        List<Ship> nearby = new ArrayList<>();
        ctx.entityQuery.collectHostileShipsNear(perspective, x, y, maxDist, nearby);
        for (Ship s : nearby) {
            if (s == null) continue;
            if (!isAlive(s)) continue;
            if (!TeamSystem.isHostileToPlayer(ctx, s.faction)) continue;
            if (isCiwsOnlyTarget(s)) continue;
            if (isMainBatteryScreenTarget(observer, s)) continue;
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
        return false;
    }

    public static void enforceCloakLockRules(GameContext ctx) {
        if (ctx == null) return;
        if (ctx.lockedTarget != null && (isHardCloaked(ctx.lockedTarget) || isCiwsOnlyTarget(ctx.lockedTarget))) {
            ctx.lockedTarget = null;
            ctx.eventBanner = "TARGET LOCK BROKEN";
            ctx.eventBannerT = Math.max(ctx.eventBannerT, 0.9);
        }
        if (ctx.command.fleetSharedTargets != null && !ctx.command.fleetSharedTargets.isEmpty()) {
            java.util.Iterator<java.util.Map.Entry<Faction, Ship>> it = ctx.command.fleetSharedTargets.entrySet().iterator();
            while (it.hasNext()) {
                java.util.Map.Entry<Faction, Ship> e = it.next();
                Ship target = (e == null) ? null : e.getValue();
                if (isHardCloaked(target) || isCiwsOnlyTarget(target)) it.remove();
            }
        }
    }

    private static boolean isHardCloaked(Ship target) {
        if (target == null) return false;
        return target.alive && !target.dying && target.hp > 0
                && target.isStealth
                && target.isCloaked()
                && target.revealTimer <= 0.0;
    }

    private static boolean isAlive(Ship s) {
        if (s == null) return false;
        return s.alive && !s.dying && s.hp > 0;
    }
}
