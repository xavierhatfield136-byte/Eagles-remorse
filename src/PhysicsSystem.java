import app.config.GameMode;
import java.util.Iterator;

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
        TargetingSystem.enforceCloakLockRules(ctx);

        // --- Ship movement / regen / turret cooldowns ---
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            s.update(dt);
        }
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            boolean superFired = false;
            while (true) {
                Projectile shot = s.pollSuperweaponShot();
                if (shot == null) break;
                ctx.projectiles.add(shot);
                superFired = true;
            }
            if (!superFired) continue;
            if (s == ctx.player) {
                EventSystem.showBanner(ctx, "SUPERWEAPON FIRED", 1.0);
                AudioSystem.onWeaponWave(ctx, s);
                ScreenShake.kick(8.0);
            } else {
                ScreenShake.kick(3.5);
            }
        }

        if (ctx.config != null && ctx.config.mode == GameMode.SHOWCASE) {
            return;
        }

        // --- Player weapons ---
        if (ctx.player != null && ctx.player.alive) {
            if (ctx.lockedTarget != null
                    && (!TargetingSystem.isDetectableToObserver(ctx.player, ctx.lockedTarget)
                    || TargetingSystem.isCiwsOnlyTarget(ctx.lockedTarget))) {
                ctx.lockedTarget = null;
            }
            Ship autoTarget = null;
            double rangeMul = CampaignSystem.targetingRangeMul(ctx);
            boolean autoLockSuppressed = CampaignSystem.suppressAutoLock(ctx);
            boolean manualAllowed = !ctx.ui.shopOpen && !ctx.ui.baseMenuOpen && !ctx.ui.mapOpen
                    && !ctx.ui.powerManagementOpen && !ctx.ui.crewStationsOpen;
            boolean firePrimary = (manualAllowed && ctx.firingPrimaryManual) || ctx.firingPrimaryAuto;
            boolean fireSecondary = (manualAllowed && ctx.firingSecondaryManual) || ctx.firingSecondaryAuto;

            if (ctx.autoLockTurrets && !autoLockSuppressed) {
                // Prefer explicit lock if valid, otherwise closest enemy near player.
                if (isAlive(ctx.lockedTarget)
                        && TeamSystem.isHostileToPlayer(ctx, ctx.lockedTarget.faction)
                        && !TargetingSystem.isCiwsOnlyTarget(ctx.lockedTarget)
                        && !TargetingSystem.isMainBatteryScreenTarget(ctx.player, ctx.lockedTarget)
                        && TargetingSystem.isDetectableToObserver(ctx.player, ctx.lockedTarget)) {
                    autoTarget = ctx.lockedTarget;
                } else {
                    autoTarget = TargetingSystem.findClosestEngagementTarget(
                            ctx, ctx.player, ctx.player.x, ctx.player.y, 1600 * rangeMul
                    );
                }
            }

            Ship aimTarget = null;
            if (isAlive(autoTarget)
                    && TeamSystem.isHostileToPlayer(ctx, autoTarget.faction)
                    && TargetingSystem.isDetectableToObserver(ctx.player, autoTarget)) {
                aimTarget = autoTarget;
            } else if (isAlive(ctx.lockedTarget)
                    && TeamSystem.isHostileToPlayer(ctx, ctx.lockedTarget.faction)
                    && !TargetingSystem.isCiwsOnlyTarget(ctx.lockedTarget)
                    && !TargetingSystem.isMainBatteryScreenTarget(ctx.player, ctx.lockedTarget)
                    && TargetingSystem.isDetectableToObserver(ctx.player, ctx.lockedTarget)) {
                aimTarget = ctx.lockedTarget;
            }

            // Turrets track continuously even when not firing.
            if (aimTarget != null) {
                ctx.player.aimAllTurretsAtTarget(aimTarget, dt);
            } else {
                ctx.player.aimPrimaryTurretsAt(ctx.cursorWorldX, ctx.cursorWorldY, dt);
            }

            if (firePrimary) {
                int beforePrimary = ctx.projectiles.size();
                if (autoTarget != null) {
                    ctx.projectiles.addAll(ctx.player.firePrimary(autoTarget, dt));
                } else {
                    ctx.projectiles.addAll(ctx.player.firePrimary(ctx.cursorWorldX, ctx.cursorWorldY, dt));
                }
                if (ctx.projectiles.size() > beforePrimary) {
                    AudioSystem.onWeaponPrimary(ctx, ctx.player);
                }
            }

            if (fireSecondary) {
                Ship target = (isAlive(ctx.lockedTarget)
                        && !TargetingSystem.isCiwsOnlyTarget(ctx.lockedTarget)
                        && TargetingSystem.isDetectableToObserver(ctx.player, ctx.lockedTarget))
                        ? ctx.lockedTarget
                        : findClosestEnemyToPoint(ctx, ctx.player.x, ctx.player.y, 1100 * rangeMul);
                if (target != null && TeamSystem.isHostileToPlayer(ctx, target.faction)) {
                    int beforeSecondary = ctx.projectiles.size();
                    ctx.projectiles.addAll(ctx.player.fireSecondary(target, dt));
                    if (ctx.projectiles.size() > beforeSecondary) {
                        AudioSystem.onWeaponSecondary(ctx, ctx.player);
                    }
                }
            }
        }

        // --- CIWS (fires pellets) ---
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!s.alive) continue;
            s.tryCIWS(dt, ctx);
        }

        // --- Projectiles update / cull ---
        for (Iterator<Projectile> it = ctx.projectiles.iterator(); it.hasNext(); ) {
            Projectile p = it.next();
            if (p == null) {
                it.remove();
                continue;
            }
            p.update(dt);
            if (!p.alive) it.remove();
        }

        ctx.entityQuery.rebuild(ctx);

        // --- Collisions ---
        CollisionSystem.handleProjectilesVsProjectiles(ctx, ctx.projectiles);
        CollisionSystem.handleShipsVsAsteroids(ctx.ships, ctx.asteroids);
        CollisionSystem.handleProjectilesVsAsteroids(ctx, ctx.projectiles, ctx.asteroids);
        CollisionSystem.handleProjectilesVsShips(ctx, ctx.projectiles, ctx.ships);
        awardPlayerKillAssistCredits(ctx);
        CollisionSystem.cleanupProjectiles(ctx.projectiles);

        // --- Cleanup destroyed ships (keep player object even if dead) ---
        ctx.ships.removeIf(s -> s == null || (s != ctx.player && !s.alive && !s.dying));

        // --- VFX / explosions ---
        try {
            Explosion.updateAll(dt);
        } catch (Throwable ignored) {
        }
        try {
            CollisionSystem.handleSuperweaponBlastRings(ctx);
        } catch (Throwable ignored) {
        }
        try {
            VFX.updateAll(dt);
        } catch (Throwable ignored) {
        }
        try {
            WreckChunk.updateAll(dt);
        } catch (Throwable ignored) {
        }

        ctx.entityQuery.rebuild(ctx);
    }

    private static boolean isAlive(Ship s) {
        if (s == null) return false;
        // Prefer the concrete fields in your codebase.
        return s.alive && !s.dying && s.hp > 0;
    }

    private static Ship findClosestEnemyToPoint(GameContext ctx, double x, double y, double maxDist) {
        return TargetingSystem.findClosestEnemyToPoint(ctx, ctx.player, x, y, maxDist);
    }

    private static void awardPlayerKillAssistCredits(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        for (Ship s : ctx.ships) {
            if (s == null || s == ctx.player) continue;
            if (!s.dying) continue;
            if (!s.playerTaggedForKillCredit) continue;
            if (s.playerKillCreditPaid) continue;
            if (!TeamSystem.isHostileToPlayer(ctx, s.faction)) continue;

            int baseReward = Math.max(0, (int) Math.round(Math.max(0, s.bountyValue) * 0.5));
            int reward = GameContext.scaleCreditEarnings(baseReward);
            if (reward > 0) ctx.credits += reward;
            s.playerKillCreditPaid = true;
            s.bountyClaimed = true;
        }
    }
}

