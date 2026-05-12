import app.config.GameMode;
public final class GameSimulationRuntime {
    private static final int TARGET_FPS = 60;
    private static final double TARGET_FRAME_MS = 1000.0 / TARGET_FPS;
    private static final long STEP_NS = 1_000_000_000L / TARGET_FPS;
    private static final long MAX_ELAPSED_NS = 250_000_000L;
    private static final int MAX_UPDATE_STEPS = 6;
    private static final double REPAIR_ORDER_SAFE_SECONDS = 20.0;
    private static final double BATTLEFIELD_WARP_CHARGE_SECONDS = 10.0;
    private static final double BATTLEFIELD_WARP_DISRUPT_TOLERANCE_SECONDS = 0.05;

    private final GameContext ctx;

    private long lastTickNs = 0L;
    private double accumulatorNs = 0.0;
    private double emaFrameMs = 0.0;
    private double emaJitterMs = 0.0;
    private double emaUpdateMs = 0.0;
    private double emaRenderMs = 0.0;
    private int droppedUpdateSteps = 0;

    public GameSimulationRuntime(GameContext ctx) {
        this.ctx = ctx;
        this.lastTickNs = System.nanoTime();
    }

    public boolean advanceFrame(long nowNs, InputSnapshot input, int viewportW, int viewportH, double timeScale) {
        if (lastTickNs <= 0L) lastTickNs = nowNs;

        long elapsedNs = nowNs - lastTickNs;
        lastTickNs = nowNs;
        if (elapsedNs < 0L) elapsedNs = 0L;
        if (elapsedNs > MAX_ELAPSED_NS) elapsedNs = MAX_ELAPSED_NS;

        double frameMs = elapsedNs / 1_000_000.0;
        emaFrameMs = smooth(emaFrameMs, frameMs, 0.15);
        emaJitterMs = smooth(emaJitterMs, Math.abs(frameMs - TARGET_FRAME_MS), 0.15);

        accumulatorNs += elapsedNs * Math.max(0.0, timeScale);

        int steps = 0;
        long updateNsTotal = 0L;
        while (accumulatorNs >= STEP_NS && steps < MAX_UPDATE_STEPS) {
            long t0 = System.nanoTime();
            tick(GameContext.DT, input, viewportW, viewportH);
            updateNsTotal += (System.nanoTime() - t0);
            accumulatorNs -= STEP_NS;
            steps++;
        }

        if (accumulatorNs >= STEP_NS) {
            int dropped = (int) Math.min(Integer.MAX_VALUE, Math.floor(accumulatorNs / STEP_NS));
            droppedUpdateSteps += Math.max(0, dropped);
            accumulatorNs = STEP_NS * 0.5;
        }

        double updateMs = updateNsTotal / 1_000_000.0;
        emaUpdateMs = smooth(emaUpdateMs, updateMs, 0.20);

        ctx.perf.frameMs = emaFrameMs;
        ctx.perf.fps = (emaFrameMs <= 1e-6) ? 0.0 : (1000.0 / emaFrameMs);
        ctx.perf.frameJitterMs = emaJitterMs;
        ctx.perf.updateMs = emaUpdateMs;
        ctx.perf.updateSteps = steps;
        ctx.perf.droppedUpdates = droppedUpdateSteps;
        ctx.perf.renderMs = emaRenderMs;

        return (steps > 0 || ctx.state != GameState.PAUSED || ctx.eventBannerT > 0 || ctx.gameOver);
    }

    public void recordRenderMs(double renderMs) {
        emaRenderMs = smooth(emaRenderMs, renderMs, 0.20);
        ctx.perf.renderMs = emaRenderMs;
    }

    public boolean consumeSafeMissionExitReady() {
        if (ctx == null || ctx.command == null || !ctx.command.safeMissionExitReady) return false;
        ctx.command.safeMissionExitReady = false;
        return true;
    }

    private void tick(double dt, InputSnapshot input, int viewportW, int viewportH) {
        if (ctx.state == GameState.PAUSED) {
            if (ctx.eventBannerT > 0) ctx.eventBannerT -= dt;
            return;
        }

        applyPlayerInput(dt, input);
        if (CampaignSystem.isCampaignMapScreenActive(ctx)) {
            CampaignSystem.enforceCampaignMapDiscipline(ctx);
            UISystem.updateStrategicMapCameraPan(ctx, dt);
            long campaignStart = System.nanoTime();
            CampaignSystem.update(ctx, dt);
            ctx.perf.campaignMs = (System.nanoTime() - campaignStart) / 1_000_000.0;
            UISystem.updatePings(ctx, dt);
            EventSystem.update(ctx, dt);
            AudioSystem.update(ctx, dt);
            syncPlayerWarpHudState();
            return;
        }

        BattlefieldSectorSystem.ensureLoadedSector(ctx);

        if (CampaignSystem.isFleetHubSession(ctx)) {
            PhysicsSystem.update(ctx, dt);
            ctx.entityQuery.rebuild(ctx);
            AISystem.update(ctx, dt);
            CarrierSystem.update(ctx, dt);
            EconomySystem.update(ctx, dt);
            long campaignStart = System.nanoTime();
            CampaignSystem.update(ctx, dt);
            ctx.perf.campaignMs = (System.nanoTime() - campaignStart) / 1_000_000.0;
            UISystem.updatePings(ctx, dt);
            EventSystem.update(ctx, dt);
            AudioSystem.update(ctx, dt);
            CameraSystem.update(ctx, viewportW, viewportH);
            if (FogOfWarSystem.isCombatFogEnabled(ctx)) {
                FogOfWarSystem.update(ctx);
            }
            syncPlayerWarpHudState();
            return;
        }

        applyPlayerRepairOrderInstantHeal();

        if (ctx.config.mode == GameMode.SHOWCASE) {
            PhysicsSystem.update(ctx, dt);
            ctx.entityQuery.rebuild(ctx);
            updatePlayerRespawn(dt);
            updateBattlefieldWarpCharges(dt);
            if (ctx.player != null) {
                ctx.player.x = GameMath.clamp(ctx.player.x, 0, ctx.WORLD_W);
                ctx.player.y = GameMath.clamp(ctx.player.y, 0, ctx.WORLD_H);
            }
            UISystem.updatePings(ctx, dt);
            CameraSystem.update(ctx, viewportW, viewportH);
            syncPlayerWarpHudState();
            return;
        }

        PhysicsSystem.update(ctx, dt);
        ctx.entityQuery.rebuild(ctx);
        updatePlayerRespawn(dt);
        updateBattlefieldWarpCharges(dt);
        TitanAbilitySystem.update(ctx, dt);
        AISystem.update(ctx, dt);
        CarrierSystem.update(ctx, dt);
        EconomySystem.update(ctx, dt);
        TutorialSystem.update(ctx, dt);
        long campaignStart = System.nanoTime();
        CampaignSystem.update(ctx, dt);
        ctx.perf.campaignMs = (System.nanoTime() - campaignStart) / 1_000_000.0;
        LastStandSystem.update(ctx, dt);
        UISystem.updatePings(ctx, dt);
        EventSystem.update(ctx, dt);
        AudioSystem.update(ctx, dt);
        CameraSystem.update(ctx, viewportW, viewportH);
        if (FogOfWarSystem.isCombatFogEnabled(ctx)) {
            FogOfWarSystem.update(ctx);
        }
        syncPlayerWarpHudState();
    }

    private void applyPlayerRepairOrderInstantHeal() {
        if (ctx == null || ctx.player == null) return;
        if (!ctx.player.alive || ctx.player.dying || ctx.player.hp <= 0) return;
        if (!isPlayerRepairOrderActive()) return;
        if (ctx.player.tryInstantRepairFromOrder(REPAIR_ORDER_SAFE_SECONDS)) {
            EventSystem.showBanner(ctx, "DAMAGE CONTROL COMPLETE", 1.4);
        }
    }

    private void updatePlayerRespawn(double dt) {
        if (ctx == null) return;
        if (ctx.gameOver || !supportsPlayerRespawn()) {
            ctx.playerRespawnPending = false;
            ctx.playerRespawnTimer = 0.0;
            return;
        }

        Player player = ctx.player;
        if (player == null) return;

        boolean fullyDestroyed = !player.alive && !player.dying;
        if (!fullyDestroyed) {
            if (ctx.playerRespawnPending) {
                ctx.playerRespawnPending = false;
                ctx.playerRespawnTimer = 0.0;
            }
            return;
        }

        if (!ctx.playerRespawnPending) {
            ctx.playerRespawnPending = true;
            ctx.playerRespawnTimer = Math.max(0.1, ctx.playerRespawnDelaySeconds);
            EventSystem.showBanner(ctx, "FLAGSHIP LOST - REDEPLOYING", 1.4);
        }

        ctx.playerRespawnTimer -= Math.max(0.0, dt);
        if (ctx.playerRespawnTimer > 1e-6) return;

        double[] pose = SpawnSystem.playerRespawnPose(ctx);
        player.respawnAt(pose[0], pose[1], pose[2]);
        ctx.playerRespawnPending = false;
        ctx.playerRespawnTimer = 0.0;
        ctx.lockedTarget = null;
        ctx.command.playerTeleportCharging = false;
        ctx.command.playerTeleportChargeRemaining = 0.0;
        ctx.command.safeMissionExitPending = false;
        ctx.command.safeMissionExitReady = false;
        ctx.firingPrimaryAuto = false;
        ctx.firingSecondaryAuto = false;
        ctx.miningKeyDown = false;
        UISystem.closeAllOverlays(ctx);
        EventSystem.showBanner(ctx, "FLAGSHIP REDEPLOYED", 1.5);
    }

    private boolean supportsPlayerRespawn() {
        if (ctx == null || ctx.config == null) return false;
        return switch (ctx.config.mode) {
            case CAMPAIGN_OPS, LAST_STAND, FLEET -> false;
            default -> true;
        };
    }

    private void updateBattlefieldWarpCharges(double dt) {
        if (ctx == null || ctx.ships == null || dt <= 0.0) return;
        for (Ship s : new java.util.ArrayList<>(ctx.ships)) {
            if (s == null || !s.isWarpCharging()) continue;
            updateSingleBattlefieldWarp(s, dt);
        }
    }

    private void updateSingleBattlefieldWarp(Ship ship, double dt) {
        if (ship == null) return;
        boolean isPlayer = (ship == ctx.player);
        if (!ship.alive || ship.dying || ship.hp <= 0 || !ship.canUseBattlefieldWarp()) {
            cancelBattlefieldWarp(ship, isPlayer ? "BATTLEFIELD WARP ABORTED" : null, 1.0);
            return;
        }
        if (ship.warpFormationLeaderId() > 0) {
            Ship leader = ctx.entityQuery.findShipById(ship.warpFormationLeaderId());
            if (leader == null || !leader.alive || leader.dying || leader.hp <= 0) {
                cancelBattlefieldWarp(ship, isPlayer ? "BATTLEFIELD WARP ABORTED" : null, 1.0);
                return;
            }
        }

        double chargeDuration = ship.warpChargeDuration();
        double elapsed = Math.max(0.0, chargeDuration - ship.warpChargeRemaining());
        if (ship.secondsSinceDamage() + BATTLEFIELD_WARP_DISRUPT_TOLERANCE_SECONDS < elapsed) {
            cancelBattlefieldWarp(ship, isPlayer ? "BATTLEFIELD WARP DISRUPTED" : null, 1.2);
            return;
        }

        ship.tickBattlefieldWarp(dt);
        if (!ship.isBattlefieldWarpReady()) return;

        if (isPlayer && ctx.command.safeMissionExitPending) {
            completeSafeMissionExit(ship);
            return;
        }

        double tx = GameMath.clamp(ship.warpExitX(), 0, ctx.WORLD_W);
        double ty = GameMath.clamp(ship.warpExitY(), 0, ctx.WORLD_H);
        int arrivedCampaignSubzone = -1;
        if (CampaignSystem.usesMissionSubzones(ctx) && ctx.campaign != null) {
            arrivedCampaignSubzone = CampaignSystem.missionSubzoneForPoint(ctx, ctx.campaign.sector, tx, ty);
            if (arrivedCampaignSubzone < 0) {
                arrivedCampaignSubzone = CampaignSystem.nearestMissionSubzone(ctx, ctx.campaign.sector, tx, ty);
            }
            double[] clamped = CampaignSystem.clampToMissionSubzone(
                    ctx, ctx.campaign.sector, arrivedCampaignSubzone, tx, ty);
            if (clamped != null && clamped.length >= 2) {
                tx = clamped[0];
                ty = clamped[1];
            }
        }
        ship.x = tx;
        ship.y = ty;
        ship.vx = 0.0;
        ship.vy = 0.0;
        ship.cancelBattlefieldWarp();
        AudioSystem.onWarpExit(ctx, ship);
        ship.campaignMissionSubzone = arrivedCampaignSubzone;
        ship.campaignWarpSourceSubzone = -1;
        BattlefieldSectorSystem.SectorDefinition arrivedSector = BattlefieldSectorSystem.sectorAt(ctx, tx, ty);
        relocateOwnedSmallCraftAfterWarp(ship, tx, ty, arrivedSector, arrivedCampaignSubzone);
        if (isPlayer) {
            CampaignSystem.warpPersistentFleetMinersWithPlayer(ctx, arrivedCampaignSubzone);
            ctx.ui.waypointX = tx;
            ctx.ui.waypointY = ty;
            if (arrivedCampaignSubzone >= 0) {
                CampaignSystem.setLoadedMissionSubzone(ctx, arrivedCampaignSubzone);
            }
            if (arrivedSector != null) {
                BattlefieldSectorSystem.setLoadedSector(ctx, arrivedSector.id);
                CameraSystem.setZoom(ctx, BattlefieldSectorSystem.sectorTravelZoom(ctx.ui.tacticalSectorScalePreset));
                CameraSystem.resetManualOffset(ctx);
                BattlefieldSectorSystem.SectorDefinition selectedSector = BattlefieldSectorSystem.selectedSector(ctx);
                if (selectedSector != null && !selectedSector.id.equalsIgnoreCase(arrivedSector.id)) {
                    BattlefieldSectorSystem.SectorDefinition nextHop =
                            BattlefieldSectorSystem.nextWarpHop(ctx, arrivedSector, selectedSector);
                    double[] routePoint = BattlefieldSectorSystem.warpArrivalPoint(
                            ctx, arrivedSector, nextHop, ctx.ui.tacticalSectorScalePreset);
                    if (routePoint != null) {
                        ctx.ui.waypointX = routePoint[0];
                        ctx.ui.waypointY = routePoint[1];
                    }
                }
            }
            String label = (arrivedSector == null) ? "BATTLEFIELD WARP COMPLETE"
                    : "BATTLEFIELD WARP COMPLETE: " + arrivedSector.label;
            EventSystem.showBanner(ctx, label, 1.1);
        }
    }

    private void relocateOwnedSmallCraftAfterWarp(Ship carrier, double centerX, double centerY,
                                                  BattlefieldSectorSystem.SectorDefinition arrivedSector,
                                                  int arrivedCampaignSubzone) {
        if (ctx == null || carrier == null || ctx.ships == null) return;
        int slot = 0;
        for (Ship craft : ctx.ships) {
            if (craft == null || craft == carrier) continue;
            if (!craft.alive || craft.dying || craft.hp <= 0) continue;
            if (craft.carrierOwnerId != carrier.id) continue;
            if (!craft.isSmallCraft()) continue;

            double side = ((slot & 1) == 0) ? -1.0 : 1.0;
            double row = slot / 2;
            double lateral = side * (carrier.radius + 70.0 + row * 26.0);
            double trail = carrier.radius + 120.0 + row * 38.0;
            double ca = Math.cos(carrier.angle);
            double sa = Math.sin(carrier.angle);
            double craftX = centerX - ca * trail - sa * lateral;
            double craftY = centerY - sa * trail + ca * lateral;
            if (arrivedSector != null) {
                double[] clamped = BattlefieldSectorSystem.clampToLoadedSectorBounds(
                        ctx, arrivedSector, ctx.ui.tacticalSectorScalePreset, craftX, craftY);
                if (clamped != null && clamped.length >= 2) {
                    craftX = clamped[0];
                    craftY = clamped[1];
                }
            }
            if (CampaignSystem.usesMissionSubzones(ctx) && ctx.campaign != null && arrivedCampaignSubzone >= 0) {
                double[] clamped = CampaignSystem.clampToMissionSubzone(
                        ctx, ctx.campaign.sector, arrivedCampaignSubzone, craftX, craftY);
                if (clamped != null && clamped.length >= 2) {
                    craftX = clamped[0];
                    craftY = clamped[1];
                }
                craft.campaignMissionSubzone = arrivedCampaignSubzone;
                craft.campaignWarpSourceSubzone = -1;
            }
            craft.x = craftX;
            craft.y = craftY;
            craft.angle = carrier.angle;
            craft.vx = carrier.vx;
            craft.vy = carrier.vy;
            slot++;
        }
    }

    private void cancelBattlefieldWarp(Ship ship, String banner, double seconds) {
        if (ship == null || !ship.isWarpCharging()) return;
        boolean isPlayer = (ship == ctx.player);
        if (isPlayer && ctx.command.safeMissionExitPending) {
            GameplayActions.cancelSafeMissionExit(ctx, banner, seconds);
            return;
        }
        ship.cancelBattlefieldWarp();
        if (isPlayer && banner != null && !banner.isBlank()) {
            EventSystem.showBanner(ctx, banner, Math.max(0.1, seconds));
        }
    }

    private void completeSafeMissionExit(Ship ship) {
        if (ship == null) return;
        ship.cancelBattlefieldWarp();
        for (Ship ally : ctx.ships) {
            if (ally == null || ally == ship) continue;
            if (!ally.isWarpCharging()) continue;
            if (ally.warpFormationLeaderId() != ship.id) continue;
            ally.cancelBattlefieldWarp();
        }
        ctx.command.safeMissionExitPending = false;
        ctx.command.playerTeleportCharging = false;
       ctx.command.playerTeleportChargeRemaining = 0.0;
        if (CampaignSystem.completeMissionExtraction(ctx)) {
            ctx.command.safeMissionExitReady = true;
            return;
        }
        ctx.command.safeMissionExitReady = true;
    }

    private void syncPlayerWarpHudState() {
        if (ctx == null || ctx.player == null) return;
        if (ctx.command.safeMissionExitPending && !ctx.player.isWarpCharging()) {
            ctx.command.safeMissionExitPending = false;
        }
        ctx.command.playerTeleportCharging = ctx.player.isWarpCharging();
        ctx.command.playerTeleportChargeRemaining = ctx.player.warpChargeRemaining();
    }

    private boolean isPlayerRepairOrderActive() {
        if (ctx == null) return false;
        if (ctx.command.captainDirective == GameContext.CaptainDirective.REPAIR) return true;
        return ctx.command.alliedFleetCommand == GameContext.FleetCommand.REPAIR;
    }

    private void applyPlayerInput(double dt, InputSnapshot input) {
        InputSnapshot snap = (input == null)
                ? new InputSnapshot(false, false, false, false, false, 0, 0)
                : input;

        double mouseWorldX = CameraSystem.screenToWorldX(ctx, snap.mouseX);
        double mouseWorldY = CameraSystem.screenToWorldY(ctx, snap.mouseY);
        ctx.cursorScreenX = snap.mouseX;
        ctx.cursorScreenY = snap.mouseY;
        ctx.cursorWorldX = mouseWorldX;
        ctx.cursorWorldY = mouseWorldY;
        if (CampaignSystem.isCampaignMapScreenActive(ctx)) {
            return;
        }

        Player p = ctx.player;
        if (p == null) return;
        boolean fleetHubSession = CampaignSystem.isFleetHubSession(ctx);
        if (!ctx.ui.powerManagementOpen && !ctx.ui.crewStationsOpen && !ctx.ui.flightDeckOpen) {
            CameraSystem.updateManualPan(ctx, dt);
        }
        if (!p.alive || p.dying || p.hp <= 0 || ctx.playerRespawnPending) {
            p.vx = 0.0;
            p.vy = 0.0;
            return;
        }
        if (CampaignSystem.isPlayerControlLocked(ctx)) {
            p.vx = 0.0;
            p.vy = 0.0;
            return;
        }
        if (p.hasSuperweapon) p.trackSuperweaponAim(mouseWorldX, mouseWorldY);

        boolean helmAutoApplied = CrewStationsSystem.updatePlayerAutomation(ctx, snap, dt);

        if (ctx.state != GameState.RUNNING && !(fleetHubSession && ctx.state == GameState.FLEET)) {
            if (helmAutoApplied) return;
            p.vx = 0.0;
            p.vy = 0.0;
            return;
        }

        if (helmAutoApplied) return;

        // Manual WASD uses the same speed ceiling basis as AI/autopilot movement.
        double speed = MovementModel.speedCeiling(p);

        // Hull steering: A/D rotate the craft, with larger ships turning more slowly.
        double turnInput = 0.0;
        if (snap.left) turnInput -= 1.0;
        if (snap.right) turnInput += 1.0;
        double turnRate = MovementModel.turnRateRadPerSec(p);
        p.angle = MathUtil.normalizeAngle(p.angle + turnInput * turnRate * dt);

        if (p.hasSuperweapon && p.isSuperweaponCharging()) {
            double desired = Math.atan2(mouseWorldY - p.y, mouseWorldX - p.x);
            double assistRate = Math.toRadians(260.0);
            p.angle = rotateToward(p.angle, desired, assistRate * dt);
        }

        // Thrust follows hull heading.
        double throttle = 0.0;
        if (snap.up) throttle += 1.0;
        if (snap.down) throttle -= 1.0;
        double thrustMul = (throttle >= 0.0) ? 1.0 : MovementModel.reverseThrustMul(p);
        double coupling = MovementModel.rotationCoupling(p);
        double rotationPenalty = 1.0 - coupling * Math.min(1.0, Math.abs(turnInput));
        rotationPenalty = MathUtil.clamp(rotationPenalty, 0.62, 1.0);
        double desiredVxPerSec = Math.cos(p.angle) * speed * throttle * thrustMul * rotationPenalty;
        double desiredVyPerSec = Math.sin(p.angle) * speed * throttle * thrustMul * rotationPenalty;

        if (Math.abs(throttle) <= 1e-6) {
            MovementModel.applyDesiredVelocity(p, 0.0, 0.0, dt, false);
        } else {
            MovementModel.applyDesiredVelocity(p, desiredVxPerSec, desiredVyPerSec, dt, true);
        }
    }

    private static double rotateToward(double current, double desired, double maxStep) {
        double delta = MathUtil.normalizeAngle(desired - current);
        double step = MathUtil.clamp(delta, -Math.abs(maxStep), Math.abs(maxStep));
        return MathUtil.normalizeAngle(current + step);
    }

    private static double smooth(double prev, double sample, double alpha) {
        if (sample < 0.0) sample = 0.0;
        if (prev <= 1e-9) return sample;
        return prev + (sample - prev) * Math.max(0.0, Math.min(1.0, alpha));
    }
}
