import java.util.Iterator;
import java.util.List;

/**
 * Physics step: movement, weapons, projectiles, CIWS, and collisions.
 *
 * This replaces the earlier collisions-only version (which caused ships not to move and weapons not to fire).
 * It mirrors the original monolithic GamePanel update order, but operates on GameContext.
 */
public final class PhysicsSystem {

    private PhysicsSystem() {}

    public static void update(GameContext ctx, double dt) {
        if (ctx == null) return;

        // --- Ship movement / regen / turret cooldowns ---
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            s.update(dt);
        }

        // --- Player weapons (skip while in menus) ---
        if (ctx.player != null && ctx.player.alive && !ctx.shopOpen && !ctx.baseMenuOpen && !ctx.mapOpen) {
            Ship autoTarget = null;

            if (ctx.autoLockTurrets) {
                // Prefer explicit lock if valid, otherwise closest enemy near player.
                if (isAlive(ctx.lockedTarget) && ctx.lockedTarget.faction == Faction.ENEMY) {
                    autoTarget = ctx.lockedTarget;
                } else {
                    autoTarget = findClosestEnemyToPoint(ctx, ctx.player.x, ctx.player.y, 1600);
                }
            }

            if (ctx.firingPrimary) {
                if (autoTarget != null) {
                    ctx.projectiles.addAll(ctx.player.firePrimary(autoTarget, dt));
                } else {
                    ctx.projectiles.addAll(ctx.player.firePrimary(ctx.cursorWorldX, ctx.cursorWorldY, dt));
                }
            }

            if (ctx.firingSecondary) {
                Ship target = isAlive(ctx.lockedTarget) ? ctx.lockedTarget : findClosestEnemyToPoint(ctx, ctx.player.x, ctx.player.y, 1100);
                if (target != null && target.faction == Faction.ENEMY) {
                    ctx.projectiles.addAll(ctx.player.fireSecondary(target, dt));
                }
            }
        }

        // --- CIWS (fires pellets) ---
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!s.alive) continue;
            s.tryCIWS(dt, ctx.projectiles);
        }

        // --- Projectiles update / cull ---
        for (Iterator<Projectile> it = ctx.projectiles.iterator(); it.hasNext(); ) {
            Projectile p = it.next();
            if (p == null) { it.remove(); continue; }
            p.update(dt);
            if (!p.alive) it.remove();
        }

        // --- Collisions ---
        CollisionSystem.handleProjectilesVsProjectiles(ctx.projectiles);
        CollisionSystem.handleShipsVsAsteroids(ctx.ships, ctx.asteroids);
        CollisionSystem.handleProjectilesVsAsteroids(ctx.projectiles, ctx.asteroids);
        CollisionSystem.handleProjectilesVsShips(ctx.projectiles, ctx.ships);

        // --- VFX / explosions ---
        try { Explosion.updateAll(dt); } catch (Throwable ignored) {}
        try { VFX.updateAll(dt); } catch (Throwable ignored) {}
    }

    private static boolean isAlive(Ship s) {
        if (s == null) return false;
        // Prefer the concrete fields in your codebase.
        return s.alive && !s.dying && s.hp > 0;
    }

    private static Ship findClosestEnemyToPoint(GameContext ctx, double x, double y, double maxDist) {
        Ship best = null;
        double bestD2 = maxDist * maxDist;
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!isAlive(s)) continue;
            if (s.faction != Faction.ENEMY) continue;
            if (s.role == ShipRole.BASE) continue;
            double d2 = GameMath.dist2(x, y, s.x, s.y);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = s;
            }
        }
        return best;
    }
}
