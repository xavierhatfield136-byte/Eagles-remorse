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

        ctx.perfFrameMs = emaFrameMs;
        ctx.perfFps = (emaFrameMs <= 1e-6) ? 0.0 : (1000.0 / emaFrameMs);
        ctx.perfFrameJitterMs = emaJitterMs;
        ctx.perfUpdateMs = emaUpdateMs;
        ctx.perfUpdateSteps = steps;
        ctx.perfDroppedUpdates = droppedUpdateSteps;
        ctx.perfRenderMs = emaRenderMs;

        return (steps > 0 || ctx.state != GameState.PAUSED || ctx.eventBannerT > 0 || ctx.gameOver);
    }

    public void recordRenderMs(double renderMs) {
        emaRenderMs = smooth(emaRenderMs, renderMs, 0.20);
        ctx.perfRenderMs = emaRenderMs;
    }

    private void tick(double dt, InputSnapshot input, int viewportW, int viewportH) {
        if (ctx.state == GameState.PAUSED) {
            if (ctx.eventBannerT > 0) ctx.eventBannerT -= dt;
            return;
        }

        applyPlayerInput(dt, input);
        applyPlayerRepairOrderInstantHeal();
        holdWarpChargingShips();

        if (ctx.config.mode == GameMode.SHOWCASE) {
            PhysicsSystem.update(ctx, dt);
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
        updateBattlefieldWarpCharges(dt);
        AISystem.update(ctx, dt);
        CarrierSystem.update(ctx, dt);
        EconomySystem.update(ctx, dt);
        TutorialSystem.update(ctx, dt);
        CampaignSystem.update(ctx, dt);
        LastStandSystem.update(ctx, dt);
        UISystem.updatePings(ctx, dt);
        EventSystem.update(ctx, dt);
        AudioSystem.update(ctx, dt);
        CameraSystem.update(ctx, viewportW, viewportH);
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

    private void holdWarpChargingShips() {
        if (ctx == null || ctx.ships == null) return;
        for (Ship s : ctx.ships) {
            if (s == null || !s.isWarpCharging()) continue;
            s.vx = 0.0;
            s.vy = 0.0;
        }
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

        double chargeDuration = ship.warpChargeDuration();
        double elapsed = Math.max(0.0, chargeDuration - ship.warpChargeRemaining());
        if (ship.secondsSinceDamage() + BATTLEFIELD_WARP_DISRUPT_TOLERANCE_SECONDS < elapsed) {
            cancelBattlefieldWarp(ship, isPlayer ? "BATTLEFIELD WARP DISRUPTED" : null, 1.2);
            return;
        }

        ship.tickBattlefieldWarp(dt);
        if (!ship.isBattlefieldWarpReady()) return;

        double tx = GameMath.clamp(ship.warpExitX(), 0, ctx.WORLD_W);
        double ty = GameMath.clamp(ship.warpExitY(), 0, ctx.WORLD_H);
        ship.x = tx;
        ship.y = ty;
        ship.vx = 0.0;
        ship.vy = 0.0;
        ship.cancelBattlefieldWarp();
        if (isPlayer) {
            ctx.waypointX = tx;
            ctx.waypointY = ty;
            EventSystem.showBanner(ctx, "BATTLEFIELD WARP COMPLETE", 1.1);
        }
    }

    private void cancelBattlefieldWarp(Ship ship, String banner, double seconds) {
        if (ship == null || !ship.isWarpCharging()) return;
        boolean isPlayer = (ship == ctx.player);
        ship.cancelBattlefieldWarp();
        if (isPlayer && banner != null && !banner.isBlank()) {
            EventSystem.showBanner(ctx, banner, Math.max(0.1, seconds));
        }
    }

    private void syncPlayerWarpHudState() {
        if (ctx == null || ctx.player == null) return;
        ctx.playerTeleportCharging = ctx.player.isWarpCharging();
        ctx.playerTeleportChargeRemaining = ctx.player.warpChargeRemaining();
    }

    private boolean isPlayerRepairOrderActive() {
        if (ctx == null) return false;
        if (ctx.captainDirective == GameContext.CaptainDirective.REPAIR) return true;
        return ctx.alliedFleetCommand == GameContext.FleetCommand.REPAIR;
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

        Player p = ctx.player;
        if (p == null) return;
        if (p.hasWaveMotionGun) p.trackWaveMotionAim(mouseWorldX, mouseWorldY);

        boolean helmAutoApplied = CrewStationsSystem.updatePlayerAutomation(ctx, snap, dt);

        if (ctx.state != GameState.RUNNING) {
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

        if (p.hasWaveMotionGun && p.isWaveMotionCharging()) {
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


