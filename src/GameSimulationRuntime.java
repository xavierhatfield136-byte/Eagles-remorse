public final class GameSimulationRuntime {
    private static final int TARGET_FPS = 60;
    private static final double TARGET_FRAME_MS = 1000.0 / TARGET_FPS;
    private static final long STEP_NS = 1_000_000_000L / TARGET_FPS;
    private static final long MAX_ELAPSED_NS = 250_000_000L;
    private static final int MAX_UPDATE_STEPS = 6;
    private static final double REPAIR_ORDER_SAFE_SECONDS = 20.0;

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

    private void applyPlayerRepairOrderInstantHeal() {
        if (ctx == null || ctx.player == null) return;
        if (!ctx.player.alive || ctx.player.dying || ctx.player.hp <= 0) return;
        if (!isPlayerRepairOrderActive()) return;
        if (ctx.player.tryInstantRepairFromOrder(REPAIR_ORDER_SAFE_SECONDS)) {
            EventSystem.showBanner(ctx, "DAMAGE CONTROL COMPLETE", 1.4);
        }
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
        double turnRate = turnRateForShip(p);
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
        double thrustMul = (throttle >= 0.0) ? 1.0 : reverseThrustMul((p == null) ? null : p.role);
        double desiredVxPerSec = Math.cos(p.angle) * speed * throttle * thrustMul;
        double desiredVyPerSec = Math.sin(p.angle) * speed * throttle * thrustMul;

        double curVxPerSec = (dt <= 1e-9) ? 0.0 : (p.vx / dt);
        double curVyPerSec = (dt <= 1e-9) ? 0.0 : (p.vy / dt);
        double responsePerSec = handlingResponsePerSec((p == null) ? null : p.role);
        double blend = MathUtil.clamp(responsePerSec * dt, 0.0, 1.0);

        if (Math.abs(throttle) <= 1e-6) {
            // No thrust input: bleed momentum by role handling, so heavier hulls drift longer.
            double damp = MathUtil.clamp(responsePerSec * 0.90 * dt, 0.0, 1.0);
            curVxPerSec += (0.0 - curVxPerSec) * damp;
            curVyPerSec += (0.0 - curVyPerSec) * damp;
        } else {
            curVxPerSec += (desiredVxPerSec - curVxPerSec) * blend;
            curVyPerSec += (desiredVyPerSec - curVyPerSec) * blend;
        }

        p.vx = curVxPerSec * dt;
        p.vy = curVyPerSec * dt;
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

    private static double turnRateForShip(Ship ship) {
        if (ship == null) return turnRateForRadius(16.0);
        ShipRole role = ship.role;
        double roleMul = (role == null) ? 1.0 : switch (role) {
            case DRONE -> 1.28;
            case FIGHTER -> 1.24;
            case STEALTH_SHIP -> 1.20;
            case PATROL -> 1.15;
            case PD_CRAFT -> 1.14;
            case CIWS_CORVETTE -> 1.10;
            case BOMBER -> 1.05;
            case FRIGATE -> 1.00;
            case PICKET -> 0.95;
            case LIGHT_CRUISER -> 0.92;
            case BATTLECRUISER -> 0.90;
            case MINER -> 0.88;
            case MISSILE_BOAT -> 0.85;
            case MEDIUM_CRUISER, CRUISER -> 0.80;
            case TRANSPORT -> 0.78;
            case DRONE_CARRIER -> 0.76;
            case HAULER -> 0.74;
            case BATTLESHIP -> 0.72;
            case CARRIER -> 0.68;
            case DREADNOUGHT -> 0.62;
            case SUPERSHIP -> 0.50;
            default -> 1.0;
        };
        return turnRateForRadius(ship.radius) * roleMul;
    }

    private static double reverseThrustMul(ShipRole role) {
        if (role == null) return 0.62;
        return switch (role) {
            case DRONE, FIGHTER -> 0.96;
            case STEALTH_SHIP, PATROL, PD_CRAFT, CIWS_CORVETTE -> 0.86;
            case BOMBER, PICKET, MISSILE_BOAT -> 0.68;
            case FRIGATE, LIGHT_CRUISER -> 0.72;
            case MEDIUM_CRUISER, CRUISER, BATTLECRUISER -> 0.58;
            case BATTLESHIP, DREADNOUGHT, SUPERSHIP -> 0.42;
            case CARRIER, DRONE_CARRIER -> 0.46;
            case TRANSPORT, HAULER, MINER -> 0.52;
            default -> 0.62;
        };
    }

    private static double handlingResponsePerSec(ShipRole role) {
        if (role == null) return 8.5;
        return switch (role) {
            case DRONE -> 13.0;
            case FIGHTER -> 12.0;
            case STEALTH_SHIP -> 11.5;
            case PATROL, PD_CRAFT -> 10.8;
            case CIWS_CORVETTE -> 10.2;
            case BOMBER -> 9.2;
            case FRIGATE -> 9.0;
            case PICKET -> 8.4;
            case LIGHT_CRUISER -> 8.0;
            case MISSILE_BOAT -> 7.6;
            case BATTLECRUISER -> 7.2;
            case MINER -> 7.0;
            case MEDIUM_CRUISER, CRUISER -> 6.5;
            case DRONE_CARRIER, TRANSPORT, HAULER -> 6.0;
            case BATTLESHIP -> 5.4;
            case CARRIER -> 5.0;
            case DREADNOUGHT -> 4.6;
            case SUPERSHIP -> 4.0;
            default -> 8.5;
        };
    }

    private static double smooth(double prev, double sample, double alpha) {
        if (sample < 0.0) sample = 0.0;
        if (prev <= 1e-9) return sample;
        return prev + (sample - prev) * Math.max(0.0, Math.min(1.0, alpha));
    }
}


