import app.config.GameMode;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Physics step: movement, weapons, projectiles, CIWS, and collisions.
 *
 * This replaces the earlier collisions-only version (which caused ships not to move and weapons not to fire).
 * It mirrors the original monolithic GamePanel update order, but operates on GameContext.
 */
public final class PhysicsSystem {
    private static long physicsFrameIndex = 0L;

    private PhysicsSystem() {}

    public static void update(GameContext ctx, double dt) {
        if (ctx == null) return;
        if (TacticalCombatDepthSystem.isTacticalPause(ctx)) return;
        ctx.battleElapsed += Math.max(0.0, dt);
        TargetingSystem.enforceCloakLockRules(ctx);
        long frameIndex = physicsFrameIndex++;
        ProjectileScalePolicy.FramePlan projectilePlan = ProjectileScalePolicy.planFor(ctx, frameIndex);
        long projectileUpdateNs = 0L;
        long projectileIndexNs = 0L;
        long projectileCiwsNs = 0L;
        long projectileVsProjectileNs = 0L;
        long shipAsteroidNs = 0L;
        long projectileVsAsteroidNs = 0L;
        long projectileVsShipNs = 0L;
        long projectileCleanupNs = 0L;
        long physicsShipUpdateNs = 0L;
        long physicsSuperweaponPollNs = 0L;
        long physicsPlayerWeaponNs = 0L;
        long physicsPlayerTargetingNs = 0L;
        long physicsPlayerAimNs = 0L;
        long physicsPlayerPrimaryNs = 0L;
        long physicsPlayerSecondaryNs = 0L;
        long physicsPostCollisionNs = 0L;
        long phaseStart = System.nanoTime();

        // --- Ship movement / regen / turret cooldowns ---
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            s.update(dt);
        }
        constrainPlayerToLoadedSector(ctx);
        constrainWarpChargingShipsToSourceSector(ctx);
        constrainShipsToCampaignSubzones(ctx);
        physicsShipUpdateNs += System.nanoTime() - phaseStart;
        phaseStart = System.nanoTime();
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
                ScreenShake.kick(8.0);
            } else {
                ScreenShake.kick(3.5);
            }
            AudioSystem.onSuperweaponFired(ctx, s);
        }
        physicsSuperweaponPollNs += System.nanoTime() - phaseStart;

        if (ctx.config != null && ctx.config.mode == GameMode.SHOWCASE) {
            return;
        }

        // --- Player weapons ---
        phaseStart = System.nanoTime();
        if (ctx.player != null && ctx.player.alive) {
            long playerPhaseStart = System.nanoTime();
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
                    ctx.playerAutoTargetCache = autoTarget;
                    ctx.playerAutoTargetCacheFrame = frameIndex;
                } else if (!shouldRefreshPlayerAutoTarget(ctx, frameIndex, rangeMul)
                        && isValidPlayerAutoTarget(ctx, ctx.playerAutoTargetCache, rangeMul)) {
                    autoTarget = ctx.playerAutoTargetCache;
                } else {
                    autoTarget = TargetingSystem.findClosestEngagementTarget(
                            ctx, ctx.player, ctx.player.x, ctx.player.y, 1600 * rangeMul
                    );
                    ctx.playerAutoTargetCache = autoTarget;
                    ctx.playerAutoTargetCacheFrame = frameIndex;
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
            physicsPlayerTargetingNs += System.nanoTime() - playerPhaseStart;

            // Turrets track continuously even when not firing.
            playerPhaseStart = System.nanoTime();
            if (aimTarget != null) {
                ctx.player.aimAllTurretsAtTarget(aimTarget, dt);
            } else {
                ctx.player.aimPrimaryTurretsAt(ctx.cursorWorldX, ctx.cursorWorldY, dt);
            }
            physicsPlayerAimNs += System.nanoTime() - playerPhaseStart;

            if (!manualAllowed && !ctx.firingPrimaryAuto) {
                ctx.player.primaryGunStaggerBurstRemaining = 0;
            }

            playerPhaseStart = System.nanoTime();
            boolean continuePrimary = firePrimary || (manualAllowed && ctx.player.primaryGunStaggerBurstRemaining > 0);
            if (continuePrimary) {
                int beforePrimary = ctx.projectiles.size();
                if (autoTarget != null) {
                    ctx.projectiles.addAll(ctx.player.firePrimary(autoTarget, dt, firePrimary));
                } else {
                    ctx.projectiles.addAll(ctx.player.firePrimary(ctx.cursorWorldX, ctx.cursorWorldY, dt, firePrimary));
                }
                if (ctx.projectiles.size() > beforePrimary) {
                    AudioSystem.onWeaponPrimary(
                            ctx,
                            ctx.player,
                            new ArrayList<>(ctx.projectiles.subList(beforePrimary, ctx.projectiles.size()))
                    );
                    if (firePrimary && manualPrimaryMissileVolley) {
                        ctx.firingPrimaryManualLatched = true;
                    }
                }
            }
            physicsPlayerPrimaryNs += System.nanoTime() - playerPhaseStart;

            playerPhaseStart = System.nanoTime();
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
                        AudioSystem.onWeaponSecondary(
                                ctx,
                                ctx.player,
                                new ArrayList<>(ctx.projectiles.subList(beforeSecondary, ctx.projectiles.size()))
                        );
                    }
                }
                if (manualSecondaryRequested) {
                    ctx.firingSecondaryManualLatched = true;
                }
            }
            physicsPlayerSecondaryNs += System.nanoTime() - playerPhaseStart;
        }
        physicsPlayerWeaponNs += System.nanoTime() - phaseStart;

        // --- CIWS (fires pellets) ---
        phaseStart = System.nanoTime();
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            if (!s.alive) continue;
            if (projectilePlan != null && !projectilePlan.shouldRunCiwsAcquisition(s)) continue;
            int beforeCiws = ctx.projectiles.size();
            s.tryCIWS(dt, ctx, projectilePlan);
            if (ctx.projectiles.size() <= beforeCiws) continue;
            boolean ciwsBurst = false;
            for (int i = beforeCiws; i < ctx.projectiles.size(); i++) {
                Projectile p = ctx.projectiles.get(i);
                if (p == null || p.sourceShipId != s.id) continue;
                if (p instanceof CIWSPellet || p instanceof PointDefenseLaser) {
                    ciwsBurst = true;
                    break;
                }
            }
            if (ciwsBurst) {
                AudioSystem.onCiwsFire(ctx, s);
            }
        }
        projectileCiwsNs += System.nanoTime() - phaseStart;

        // --- Projectiles update / cull ---
        phaseStart = System.nanoTime();
        for (Iterator<Projectile> it = ctx.projectiles.iterator(); it.hasNext(); ) {
            Projectile p = it.next();
            if (p == null) {
                it.remove();
                continue;
            }
            if (p instanceof Missile missile) {
                updateMissileTargeting(ctx, missile, projectilePlan);
            }
            p.update(dt);
            if (!p.alive) it.remove();
        }
        projectileUpdateNs += System.nanoTime() - phaseStart;

        phaseStart = System.nanoTime();
        ctx.entityQuery.rebuild(ctx);
        projectileIndexNs += System.nanoTime() - phaseStart;

        // --- Collisions ---
        phaseStart = System.nanoTime();
        CollisionSystem.handleProjectilesVsProjectiles(ctx, ctx.projectiles);
        projectileVsProjectileNs += System.nanoTime() - phaseStart;
        phaseStart = System.nanoTime();
        CollisionSystem.handleShipsVsAsteroids(ctx.ships, ctx.asteroids);
        shipAsteroidNs += System.nanoTime() - phaseStart;
        TacticalCombatDepthSystem.handleRamming(ctx);
        phaseStart = System.nanoTime();
        CollisionSystem.handleProjectilesVsAsteroids(ctx, ctx.projectiles, ctx.asteroids);
        projectileVsAsteroidNs += System.nanoTime() - phaseStart;
        phaseStart = System.nanoTime();
        CollisionSystem.handleProjectilesVsShips(ctx, ctx.projectiles, ctx.ships);
        projectileVsShipNs += System.nanoTime() - phaseStart;
        awardPlayerKillAssistCredits(ctx);
        phaseStart = System.nanoTime();
        CollisionSystem.cleanupProjectiles(ctx.projectiles);
        projectileCleanupNs += System.nanoTime() - phaseStart;

        // --- Cleanup destroyed ships (keep player object even if dead) ---
        phaseStart = System.nanoTime();
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
        TacticalCombatDepthSystem.update(ctx, dt);
        physicsPostCollisionNs += System.nanoTime() - phaseStart;

        if (ctx.perf != null) {
            ctx.perf.physicsShipUpdateMs = physicsShipUpdateNs / 1_000_000.0;
            ctx.perf.physicsSuperweaponPollMs = physicsSuperweaponPollNs / 1_000_000.0;
            ctx.perf.physicsPlayerWeaponMs = physicsPlayerWeaponNs / 1_000_000.0;
            ctx.perf.physicsPlayerTargetingMs = physicsPlayerTargetingNs / 1_000_000.0;
            ctx.perf.physicsPlayerAimMs = physicsPlayerAimNs / 1_000_000.0;
            ctx.perf.physicsPlayerPrimaryMs = physicsPlayerPrimaryNs / 1_000_000.0;
            ctx.perf.physicsPlayerSecondaryMs = physicsPlayerSecondaryNs / 1_000_000.0;
            ctx.perf.physicsPostCollisionMs = physicsPostCollisionNs / 1_000_000.0;
            ctx.perf.projectileUpdateMs = projectileUpdateNs / 1_000_000.0;
            ctx.perf.projectileIndexMs = projectileIndexNs / 1_000_000.0;
            ctx.perf.projectileCiwsMs = projectileCiwsNs / 1_000_000.0;
            ctx.perf.projectileVsProjectileMs = projectileVsProjectileNs / 1_000_000.0;
            ctx.perf.shipAsteroidMs = shipAsteroidNs / 1_000_000.0;
            ctx.perf.projectileVsAsteroidMs = projectileVsAsteroidNs / 1_000_000.0;
            ctx.perf.projectileVsShipMs = projectileVsShipNs / 1_000_000.0;
            ctx.perf.projectileCleanupMs = projectileCleanupNs / 1_000_000.0;
        }
    }

    private static void constrainPlayerToLoadedSector(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        if (!ctx.player.alive || ctx.player.dying || ctx.player.hp <= 0) return;
        if (ctx.player.isWarpCharging()) return;

        if (CampaignSystem.usesMissionSubzones(ctx)) {
            if (CampaignSystem.missionSubzoneBoundaryConstraintsEnabled(ctx)) {
                double[] clamped = CampaignSystem.clampToMissionBounds(
                        ctx, ctx.campaign.sector, ctx.player.x, ctx.player.y);
                if (clamped.length >= 2) {
                    ctx.player.x = clamped[0];
                    ctx.player.y = clamped[1];
                }
            }
            int subzone = CampaignSystem.campaignMapSubzoneAtPoint(ctx, ctx.player.x, ctx.player.y);
            if (subzone >= 0) {
                CampaignSystem.setLoadedMissionSubzone(ctx, subzone);
                ctx.player.campaignMissionSubzone = subzone;
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
                if (CampaignSystem.missionSubzoneBoundaryConstraintsEnabled(ctx)) {
                    double[] clamped = CampaignSystem.clampToMissionSubzone(
                            ctx, ctx.campaign.sector, subzone, ship.x, ship.y);
                    if (clamped.length >= 2) {
                        ship.x = clamped[0];
                        ship.y = clamped[1];
                    }
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
            if (CampaignSystem.missionSubzoneBoundaryConstraintsEnabled(ctx)) {
                double[] clamped = CampaignSystem.clampToMissionBounds(
                        ctx, ctx.campaign.sector, ship.x, ship.y);
                if (clamped.length >= 2) {
                    ship.x = clamped[0];
                    ship.y = clamped[1];
                }
            }
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

    private static boolean shouldRefreshPlayerAutoTarget(GameContext ctx, long frameIndex, double rangeMul) {
        if (ctx == null) return true;
        if (ctx.playerAutoTargetCache == null) return true;
        if (!isValidPlayerAutoTarget(ctx, ctx.playerAutoTargetCache, rangeMul)) return true;
        if (ctx.firingPrimaryManual || ctx.firingSecondaryManual) return true;
        int shipCount = ctx.ships == null ? 0 : ctx.ships.size();
        int stride = shipCount >= 260 ? 10 : (shipCount >= 160 ? 6 : (shipCount >= 96 ? 3 : 1));
        if (stride <= 1) return true;
        long last = ctx.playerAutoTargetCacheFrame;
        return last == Long.MIN_VALUE || frameIndex - last >= stride;
    }

    private static boolean isValidPlayerAutoTarget(GameContext ctx, Ship target, double rangeMul) {
        if (ctx == null || ctx.player == null || target == null) return false;
        if (!isAlive(target)) return false;
        if (!TeamSystem.isHostileToPlayer(ctx, target.faction)) return false;
        if (TargetingSystem.isCiwsOnlyTarget(target)) return false;
        if (TargetingSystem.isMainBatteryScreenTarget(ctx.player, target)) return false;
        double range = 1600.0 * Math.max(0.25, rangeMul) * 1.18;
        if (GameMath.dist2(ctx.player.x, ctx.player.y, target.x, target.y) > range * range) return false;
        return TargetingSystem.isDetectableToObserver(ctx, ctx.player, target);
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

    private static void updateMissileTargeting(GameContext ctx, Missile missile, ProjectileScalePolicy.FramePlan projectilePlan) {
        if (ctx == null || missile == null || !missile.alive || !missile.hasGuidance()) return;
        boolean targetDied = false;
        if (missile.projectileTarget != null && !missile.projectileTarget.alive) {
            missile.projectileTarget = null;
            targetDied = true;
        }
        if (missile.projectileTarget != null && missile.projectileTarget.faction != null
                && missile.faction != null
                && missile.faction.isFriendlyTo(missile.projectileTarget.faction)) {
            missile.projectileTarget = null;
        }
        if (missile.projectileTarget != null) {
            return;
        }
        boolean antiShipTorpedo = missile.role == Turret.MissileRole.ANTI_HEAVY;
        if (missile.target != null
                && missile.target.alive
                && !missile.target.dying
                && missile.target.hp > 0
                && (!missile.preferSmallCraft || missile.target.isSmallCraft())
                && (!antiShipTorpedo || missile.faction == null || missile.target.faction == null
                || missile.faction.isHostileTo(missile.target.faction))
                && (!antiShipTorpedo || !missile.target.isSmallCraft())) {
            return;
        }
        if (missile.target != null && (!missile.target.alive || missile.target.dying || missile.target.hp <= 0)) {
            targetDied = true;
        }
        if (antiShipTorpedo) {
            missile.target = findAntiShipTorpedoRetarget(ctx, missile);
            if (missile.target == null) {
                missile.alive = false;
                Explosion.spawnDeath(missile.x, missile.y);
            }
            return;
        }
        if (!missile.canRetarget) return;
        boolean urgent = targetDied || missile.role == Turret.MissileRole.INTERCEPT;
        if (projectilePlan != null && !projectilePlan.shouldRetargetMissile(missile, urgent)) return;
        missile.target = findMissileRetarget(ctx, missile);
    }

    private static Ship findAntiShipTorpedoRetarget(GameContext ctx, Missile missile) {
        if (ctx == null || ctx.ships == null || missile == null || missile.faction == null) return null;
        Ship best = null;
        double bestD2 = Double.POSITIVE_INFINITY;
        for (Ship ship : ctx.ships) {
            if (!isAlive(ship)) continue;
            if (ship.isSmallCraft()) continue;
            if (ship.faction == null || missile.faction.isFriendlyTo(ship.faction)) continue;
            double d2 = GameMath.dist2(missile.x, missile.y, ship.x, ship.y);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = ship;
            }
        }
        return best;
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
            double d2 = GameMath.dist2(missile.x, missile.y, ship.x, ship.y);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = ship;
            }
        }
        return best;
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
            int reward = GameContext.scaleCreditReward(baseReward);
            if (reward > 0) ctx.credits += reward;
            s.playerKillCreditPaid = true;
            s.bountyClaimed = true;
        }
    }
}
