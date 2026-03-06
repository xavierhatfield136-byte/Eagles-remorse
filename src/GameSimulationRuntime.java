public final class GameSimulationRuntime {
    private static final int TARGET_FPS = 60;
    private static final double TARGET_FRAME_MS = 1000.0 / TARGET_FPS;
    private static final long STEP_NS = 1_000_000_000L / TARGET_FPS;
    private static final long MAX_ELAPSED_NS = 250_000_000L;
    private static final int MAX_UPDATE_STEPS = 6;

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

        if (ctx.config.mode == GameMode.SHOWCASE) {
            PhysicsSystem.update(ctx, dt);
            if (ctx.player != null) {
                ctx.player.x = GameMath.clamp(ctx.player.x, 0, ctx.WORLD_W);
                ctx.player.y = GameMath.clamp(ctx.player.y, 0, ctx.WORLD_H);
            }
            UISystem.updatePings(ctx, dt);
            CameraSystem.update(ctx, viewportW, viewportH);
            return;
        }

        PhysicsSystem.update(ctx, dt);
        AISystem.update(ctx, dt);
        CarrierSystem.update(ctx, dt);
        EconomySystem.update(ctx, dt);
        CampaignSystem.update(ctx, dt);
        LastStandSystem.update(ctx, dt);
        UISystem.updatePings(ctx, dt);
        EventSystem.update(ctx, dt);
        AudioSystem.update(ctx, dt);
        CameraSystem.update(ctx, viewportW, viewportH);
    }

    private void applyPlayerInput(double dt, InputSnapshot input) {
        InputSnapshot snap = (input == null)
                ? new InputSnapshot(false, false, false, false, false, 0, 0)
                : input;

        double mouseWorldX = CameraSystem.screenToWorldX(ctx, snap.mouseX);
        double mouseWorldY = CameraSystem.screenToWorldY(ctx, snap.mouseY);
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
        double speed = Math.max(55.0, p.desiredSpeed);

        // Hull steering: A/D rotate the craft, with larger ships turning more slowly.
        double turnInput = 0.0;
        if (snap.left) turnInput -= 1.0;
        if (snap.right) turnInput += 1.0;
        double turnRate = turnRateForRadius(p.radius);
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
        double vx = Math.cos(p.angle) * speed * throttle;
        double vy = Math.sin(p.angle) * speed * throttle;
        p.vx = vx * dt;
        p.vy = vy * dt;
    }

    private static double rotateToward(double current, double desired, double maxStep) {
        double delta = MathUtil.normalizeAngle(desired - current);
        double step = MathUtil.clamp(delta, -Math.abs(maxStep), Math.abs(maxStep));
        return MathUtil.normalizeAngle(current + step);
    }

    private static double turnRateForRadius(double radius) {
        double r = Math.max(8.0, radius);
        return GameMath.clamp(4.2 - r * 0.05, 0.85, 3.8);
    }

    private static double smooth(double prev, double sample, double alpha) {
        if (sample < 0.0) sample = 0.0;
        if (prev <= 1e-9) return sample;
        return prev + (sample - prev) * Math.max(0.0, Math.min(1.0, alpha));
    }
}


