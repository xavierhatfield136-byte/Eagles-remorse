import app.config.GameMode;
import java.awt.Color;
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
        ctx.battleElapsed += Math.max(0.0, dt);
        TargetingSystem.enforceCloakLockRules(ctx);

        // --- Ship movement / regen / turret cooldowns ---
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            s.update(dt);
        }
        constrainPlayerToLoadedSector(ctx);
        constrainWarpChargingShipsToSourceSector(ctx);
        constrainShipsToCampaignSubzones(ctx);
        syncPlayerEcmFlag(ctx);
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            boolean superFired = false;
            boolean hyperLanceBeam = false;
            boolean hyperLanceBurst = false;
            while (true) {
                Projectile shot = s.pollSuperweaponShot();
                if (shot == null) break;
                ctx.projectiles.add(shot);
                superFired = true;
                if (s.role == ShipRole.HYPERWEAPON_TITAN
                        && s.superweaponPattern == Ship.SuperweaponPattern.LANCE_CONE
                        && shot instanceof PhaserBeam) {
                    hyperLanceBeam = true;
                }
                if (!hyperLanceBurst
                        && s.role == ShipRole.HYPERWEAPON_TITAN
                        && s.superweaponPattern == Ship.SuperweaponPattern.LANCE_CONE
                        && shot instanceof SuperweaponShot) {
                    hyperLanceBurst = true;
                    VFX.spawnHyperLanceFractureEffect(shot.x, shot.y, ((SuperweaponShot) shot).angle);
                    EventSystem.showWorldCallout(ctx, shot.x, shot.y - 24.0, "FRACTURE",
                            new Color(136, 240, 255), 0.95);
                }
            }
            if (!superFired) continue;
            if (hyperLanceBeam) {
                VFX.spawnHyperLanceFireEffect(
                        s.x + Math.cos(s.angle) * (s.radius + 14.0),
                        s.y + Math.sin(s.angle) * (s.radius + 14.0),
                        s.angle,
                        Math.max(220.0, s.superweaponSpeed * 0.76));
                EventSystem.showWorldCallout(ctx, s.x, s.y - s.radius - 26.0, "LANCE",
                        new Color(122, 232, 255), 0.95);
            }
            if (s == ctx.player) {
                String banner = hyperLanceBeam
                        ? "HYPER LANCE FIRING"
                        : "SUPERWEAPON FIRED";
                if (!hyperLanceBurst || hyperLanceBeam) {
                    EventSystem.showBanner(ctx, banner, 1.0);
                }
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
                    && (!TargetingSystem.isDetectableToObserver(ctx, ctx.player, ctx.lockedTarget)
                    || TargetingSystem.isCiwsOnlyTarget(ctx.lockedTarget))) {
                ctx.lockedTarget = null;
            }
            Ship autoTarget = null;
            double rangeMul = CampaignSystem.targetingRangeMul(ctx);
            boolean autoLockSuppressed = CampaignSystem.suppressAutoLock(ctx);
            boolean manualAllowed = !ctx.ui.shopOpen && !ctx.ui.baseMenuOpen && !ctx.ui.mapOpen
                    && !ctx.ui.powerManagementOpen && !ctx.ui.crewStationsOpen
                    && !CampaignSystem.isPlayerControlLocked(ctx);
            boolean manualPrimaryRequested = manualAllowed && ctx.firingPrimaryManual;
            boolean manualPrimaryMissileVolley = manualPrimaryRequested
                    && hasPrimaryMissileTurrets(ctx.player);
            if (!manualAllowed || !ctx.firingPrimaryManual || !manualPrimaryMissileVolley) {
                ctx.firingPrimaryManualLatched = false;
            }
            boolean firePrimary = ctx.firingPrimaryAuto
                    || (manualPrimaryRequested && (!manualPrimaryMissileVolley || !ctx.firingPrimaryManualLatched));

            if (ctx.autoLockTurrets && !autoLockSuppressed) {
                // Prefer explicit lock if valid, otherwise closest enemy near player.
                if (isAlive(ctx.lockedTarget)
                        && TeamSystem.isHostileToPlayer(ctx, ctx.lockedTarget.faction)
                        && !TargetingSystem.isCiwsOnlyTarget(ctx.lockedTarget)
                        && !TargetingSystem.isMainBatteryScreenTarget(ctx.player, ctx.lockedTarget)
                        && TargetingSystem.isDetectableToObserver(ctx, ctx.player, ctx.lockedTarget)) {
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
                    && TargetingSystem.isDetectableToObserver(ctx, ctx.player, autoTarget)) {
                aimTarget = autoTarget;
            } else if (isAlive(ctx.lockedTarget)
                    && TeamSystem.isHostileToPlayer(ctx, ctx.lockedTarget.faction)
                    && !TargetingSystem.isCiwsOnlyTarget(ctx.lockedTarget)
                    && !TargetingSystem.isMainBatteryScreenTarget(ctx.player, ctx.lockedTarget)
                    && TargetingSystem.isDetectableToObserver(ctx, ctx.player, ctx.lockedTarget)) {
                aimTarget = ctx.lockedTarget;
            }

            // Turrets track continuously even when not firing.
            if (aimTarget != null) {
                ctx.player.aimAllTurretsAtTarget(aimTarget, dt);
            } else {
                ctx.player.aimPrimaryTurretsAt(ctx.cursorWorldX, ctx.cursorWorldY, dt);
            }

            if (!manualAllowed && !ctx.firingPrimaryAuto) {
                ctx.player.primaryGunStaggerBurstRemaining = 0;
            }

            boolean continuePrimary = firePrimary || (manualAllowed && ctx.player.primaryGunStaggerBurstRemaining > 0);
            if (continuePrimary) {
                int beforePrimary = ctx.projectiles.size();
                if (autoTarget != null) {
                    ctx.projectiles.addAll(ctx.player.firePrimary(autoTarget, dt, firePrimary));
                } else {
                    ctx.projectiles.addAll(ctx.player.firePrimary(ctx.cursorWorldX, ctx.cursorWorldY, dt, firePrimary));
                }
                if (ctx.projectiles.size() > beforePrimary) {
                    AudioSystem.onWeaponPrimary(ctx, ctx.player);
                    if (firePrimary && manualPrimaryMissileVolley) {
                        ctx.firingPrimaryManualLatched = true;
                    }
                }
            }

            boolean manualSecondaryRequested = manualAllowed
                    && ctx.firingSecondaryManual
                    && !ctx.firingSecondaryManualLatched;
            if (!manualAllowed || !ctx.firingSecondaryManual) {
                ctx.firingSecondaryManualLatched = false;
            }
            if (ctx.firingSecondaryAuto || manualSecondaryRequested) {
                double secondarySearchRange = playerSecondarySearchRange(ctx);
                Ship target = preferredSecondaryTarget(ctx, secondarySearchRange * rangeMul);
                if (target != null && TeamSystem.isHostileToPlayer(ctx, target.faction)) {
                    int beforeSecondary = ctx.projectiles.size();
                    ctx.projectiles.addAll(ctx.player.fireSecondary(ctx, target, dt));
                    if (ctx.projectiles.size() > beforeSecondary) {
                        AudioSystem.onWeaponSecondary(ctx, ctx.player);
                    }
                }
                if (manualSecondaryRequested) {
                    ctx.firingSecondaryManualLatched = true;
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
            if (p instanceof Missile missile) {
                updateMissileTargeting(ctx, missile);
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
            CollisionSystem.handleStasisFields(ctx);
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

    private static void constrainPlayerToLoadedSector(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        if (!ctx.player.alive || ctx.player.dying || ctx.player.hp <= 0) return;
        if (ctx.player.isWarpCharging()) return;

        if (CampaignSystem.usesMissionSubzones(ctx)) {
            double[] clamped = CampaignSystem.clampToMissionBounds(
                    ctx, ctx.campaign.sector, ctx.player.x, ctx.player.y);
            if (clamped.length >= 2) {
                ctx.player.x = clamped[0];
                ctx.player.y = clamped[1];
                int subzone = CampaignSystem.campaignMapSubzoneAtPoint(ctx, ctx.player.x, ctx.player.y);
                if (subzone >= 0) {
                    CampaignSystem.setLoadedMissionSubzone(ctx, subzone);
                    ctx.player.campaignMissionSubzone = subzone;
                }
            }
            return;
        }
        if (!BattlefieldSectorSystem.isEnabled(ctx)) return;

        BattlefieldSectorSystem.ensureLoadedSector(ctx);
        BattlefieldSectorSystem.SectorDefinition loaded = BattlefieldSectorSystem.loadedSector(ctx);
        if (loaded == null) return;
        double[] clamped = BattlefieldSectorSystem.clampToLoadedSectorBounds(
                ctx, loaded, ctx.ui.tacticalSectorScalePreset, ctx.player.x, ctx.player.y);
        if (clamped == null || clamped.length < 2) return;
        ctx.player.x = clamped[0];
        ctx.player.y = clamped[1];
    }

    private static void constrainWarpChargingShipsToSourceSector(GameContext ctx) {
        if (ctx == null || ctx.ships == null) return;
        for (Ship ship : ctx.ships) {
            if (ship == null || !ship.alive || ship.dying || ship.hp <= 0) continue;
            if (!ship.isWarpCharging()) continue;
            if (CampaignSystem.usesMissionSubzones(ctx)) {
                int subzone = ship.campaignWarpSourceSubzone;
                if (subzone < 0) {
                    subzone = CampaignSystem.ensureShipMissionSubzone(ctx, ship);
                    ship.campaignWarpSourceSubzone = subzone;
                }
                double[] clamped = CampaignSystem.clampToMissionSubzone(
                        ctx, ctx.campaign.sector, subzone, ship.x, ship.y);
                if (clamped.length >= 2) {
                    ship.x = clamped[0];
                    ship.y = clamped[1];
                }
                continue;
            }
            if (!BattlefieldSectorSystem.isEnabled(ctx)) continue;
            BattlefieldSectorSystem.SectorDefinition sector =
                    BattlefieldSectorSystem.findSector(ctx, ship.warpSourceSectorId());
            if (sector == null) {
                sector = BattlefieldSectorSystem.sectorAt(ctx, ship.x, ship.y);
                ship.setWarpSourceSectorId(sector == null ? "" : sector.id);
            }
            if (sector == null) continue;
            double[] clamped = BattlefieldSectorSystem.clampToLoadedSectorBounds(
                    ctx, sector, ctx.ui.tacticalSectorScalePreset, ship.x, ship.y);
            if (clamped == null || clamped.length < 2) continue;
            ship.x = clamped[0];
            ship.y = clamped[1];
        }
    }

    private static void constrainShipsToCampaignSubzones(GameContext ctx) {
        if (!CampaignSystem.usesMissionSubzones(ctx) || ctx.ships == null || ctx.campaign == null) return;
        for (Ship ship : ctx.ships) {
            if (ship == null || !ship.alive || ship.dying || ship.hp <= 0) continue;
            if (ship.isWarpCharging()) continue;
            double[] clamped = CampaignSystem.clampToMissionBounds(
                    ctx, ctx.campaign.sector, ship.x, ship.y);
            if (clamped.length < 2) continue;
            ship.x = clamped[0];
            ship.y = clamped[1];
            ship.campaignMissionSubzone = CampaignSystem.campaignMapSubzoneAtPoint(ctx, ship.x, ship.y);
        }
    }

    private static boolean isAlive(Ship s) {
        if (s == null) return false;
        // Prefer the concrete fields in your codebase.
        return s.alive && !s.dying && s.hp > 0;
    }

    private static Ship findClosestEnemyToPoint(GameContext ctx, double x, double y, double maxDist) {
        return TargetingSystem.findClosestEnemyToPoint(ctx, ctx.player, x, y, maxDist);
    }

    private static boolean hasPrimaryMissileTurrets(Ship ship) {
        if (ship == null || ship.turrets == null) return false;
        for (Turret turret : ship.turrets) {
            if (turret == null) continue;
            if (!turret.primary) continue;
            if (turret.kind == Turret.Kind.MISSILE) return true;
        }
        return false;
    }

    private static double playerSecondarySearchRange(GameContext ctx) {
        if (ctx == null || ctx.player == null || ctx.player.turrets == null) return 1100.0;
        double range = 1100.0;
        for (Turret turret : ctx.player.turrets) {
            if (turret == null || turret.kind != Turret.Kind.MISSILE || turret.primary) continue;
            Turret.MissileRole role = (turret.missileRole == null) ? Turret.MissileRole.ANTI_MEDIUM : turret.missileRole;
            switch (role) {
                case ANTI_HEAVY -> range = Math.max(range, 1100.0);
                case ANTI_LIGHT -> range = Math.max(range, Math.hypot(ctx.WORLD_W, ctx.WORLD_H));
                case ANTI_MEDIUM -> range = Math.max(range, 1600.0);
                case INTERCEPT -> range = Math.max(range, 820.0);
            }
        }
        return range;
    }

    private static Ship preferredSecondaryTarget(GameContext ctx, double searchRange) {
        if (ctx == null || ctx.player == null) return null;
        if (isAlive(ctx.lockedTarget)
                && TargetingSystem.isDetectableToObserver(ctx, ctx.player, ctx.lockedTarget)
                && !TargetingSystem.isCiwsOnlyTarget(ctx.lockedTarget)) {
            return ctx.lockedTarget;
        }
        if (playerHasSecondaryInterceptMissiles(ctx)) {
            Ship smallCraft = TargetingSystem.findClosestHostileSmallCraft(
                    ctx, ctx.player, ctx.player.x, ctx.player.y, searchRange);
            if (smallCraft != null) return smallCraft;
        }
        return findClosestEnemyToPoint(ctx, ctx.player.x, ctx.player.y, searchRange);
    }

    private static boolean playerHasSecondaryInterceptMissiles(GameContext ctx) {
        if (ctx == null || ctx.player == null || ctx.player.turrets == null) return false;
        for (Turret turret : ctx.player.turrets) {
            if (turret == null || turret.kind != Turret.Kind.MISSILE || turret.primary) continue;
            Turret.MissileRole role = (turret.missileRole == null) ? Turret.MissileRole.ANTI_MEDIUM : turret.missileRole;
            if (role == Turret.MissileRole.INTERCEPT) return true;
        }
        return false;
    }

    private static void updateMissileTargeting(GameContext ctx, Missile missile) {
        if (ctx == null || missile == null || !missile.alive || !missile.hasGuidance()) return;
        if (missile.projectileTarget != null && !missile.projectileTarget.alive) {
            missile.projectileTarget = null;
        }
        if (missile.projectileTarget != null && missile.projectileTarget.faction != null
                && missile.faction != null
                && missile.faction.isFriendlyTo(missile.projectileTarget.faction)) {
            missile.projectileTarget = null;
        }
        if (missile.target != null && missile.target.blocksMissileLocksFrom(missile.x, missile.y)) {
            missile.target = null;
        }
        if (missile.projectileTarget != null) {
            return;
        }
        if (missile.target != null
                && missile.target.alive
                && !missile.target.dying
                && missile.target.hp > 0
                && (!missile.preferSmallCraft || missile.target.isSmallCraft())) {
            return;
        }
        if (!missile.canRetarget) return;
        missile.target = findMissileRetarget(ctx, missile);
    }

    private static Ship findMissileRetarget(GameContext ctx, Missile missile) {
        if (ctx == null || missile == null || missile.faction == null) return null;
        double maxRange = Math.max(120.0, missile.retargetRange);
        if (missile.role == Turret.MissileRole.INTERCEPT) {
            missile.projectileTarget = TargetingSystem.findClosestHostileMissile(ctx, missile.faction, missile.x, missile.y, maxRange);
            if (missile.projectileTarget != null) return null;
        }
        Ship best = null;
        double bestD2 = maxRange * maxRange;
        java.util.ArrayList<Ship> nearby = new java.util.ArrayList<>();
        ctx.entityQuery.collectHostileShipsNear(missile.faction, missile.x, missile.y, maxRange, nearby);
        for (Ship ship : nearby) {
            if (!isAlive(ship)) continue;
            if (missile.preferSmallCraft && !ship.isSmallCraft()) continue;
            if (ship.blocksMissileLocksFrom(missile.x, missile.y)) continue;
            double d2 = GameMath.dist2(missile.x, missile.y, ship.x, ship.y);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = ship;
            }
        }
        return best;
    }

    private static void syncPlayerEcmFlag(GameContext ctx) {
        if (ctx == null) return;
        ctx.command.scienceJamming = ctx.player != null && ctx.player.hasActiveEcm();
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
