import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phase 4 harness: multiple command sources and player ownership.
 * This is not split-screen and not network transport; it proves two queues can drive one authoritative battle.
 */
public final class MultiplayerMultipleCommandSourcesScenario {
    private static final double DIRECT_FIRE_RANGE = 900.0;
    private static final int DIRECT_FIRE_DAMAGE = 240;

    private final MultiplayerBattleRuntime runtime;
    private final MultiplayerNetworkCommandQueue queue = new MultiplayerNetworkCommandQueue();
    private final Set<Integer> playerControlledShipIds = new HashSet<>();
    private final Map<Integer, Double> lastAcceptedAimBySlot = new HashMap<>();
    private MultiplayerDuelVictoryEvaluator.MatchResult lastResult =
            new MultiplayerDuelVictoryEvaluator.MatchResult(false, -1, "In progress");

    public MultiplayerMultipleCommandSourcesScenario(MultiplayerRulesV1.BattleSetup setup) {
        this.runtime = MultiplayerBattleRuntime.createAuthoritative(setup, true);
        for (MultiplayerPlayerSlotState slot : runtime.slots().values()) {
            if (slot != null && slot.controlledShipId > 0) {
                playerControlledShipIds.add(slot.controlledShipId);
            }
        }
    }

    public MultiplayerBattleRuntime runtime() {
        return runtime;
    }

    public MultiplayerDuelVictoryEvaluator.MatchResult lastResult() {
        return lastResult;
    }

    public boolean shouldRunAiFor(Ship ship) {
        return ship != null && !playerControlledShipIds.contains(ship.id);
    }

    public double lastAcceptedAimAngle(int slotId) {
        return lastAcceptedAimBySlot.getOrDefault(slotId, Double.NaN);
    }

    public void enqueue(MultiplayerCommandSource source, long hostTick) {
        if (source == null) return;
        for (MultiplayerCommandGate.PlayerInputFrame frame : source.inputFrames(hostTick)) {
            enqueueInputFrame(frame);
        }
        for (MultiplayerCommandGate.DiscreteCommand command : source.discreteCommands(hostTick)) {
            enqueueDiscreteCommand(command);
        }
    }

    public void enqueueInputFrame(MultiplayerCommandGate.PlayerInputFrame frame) {
        queue.enqueueInput(frame);
    }

    public void enqueueDiscreteCommand(MultiplayerCommandGate.DiscreteCommand command) {
        queue.enqueueCommand(command);
    }

    public void tick(double dt, long hostTick) {
        runtime.threadGuard().assertOwnerThread("multiple command source tick");
        List<MultiplayerCommandGate.PlayerInputFrame> frames = queue.drainInputs();
        for (MultiplayerCommandGate.PlayerInputFrame frame : frames) {
            MultiplayerCommandGate.CommandResult result = runtime.acceptInput(frame, hostTick);
            if (result.accepted()) {
                applyInput(frame, Math.max(0.0, dt));
            }
        }
        queue.drainCommands().forEach(runtime.commandGate()::validateDiscreteCommand);
        runtime.context().entityQuery.rebuild(runtime.context());
        lastResult = MultiplayerDuelVictoryEvaluator.evaluate(runtime.context(), runtime.slots());
    }

    private void applyInput(MultiplayerCommandGate.PlayerInputFrame frame, double dt) {
        Ship ship = findShip(frame.controlledShipId());
        if (!isAlive(ship)) return;
        lastAcceptedAimBySlot.put(frame.slotId(), frame.aimAngle());
        ship.angle += frame.turn() * Math.PI * 0.95 * dt;
        double speed = MovementModel.speedCeiling(ship) * 0.72;
        double vx = Math.cos(ship.angle) * frame.thrust() * speed;
        double vy = Math.sin(ship.angle) * frame.thrust() * speed;
        ship.vx = vx * dt;
        ship.vy = vy * dt;
        ship.x = GameMath.clamp(ship.x + ship.vx, ship.radius, runtime.context().WORLD_W - ship.radius);
        ship.y = GameMath.clamp(ship.y + ship.vy, ship.radius, runtime.context().WORLD_H - ship.radius);
        if (frame.primaryHeld()) {
            applyHostSideDirectFire(ship);
        }
    }

    private void applyHostSideDirectFire(Ship shooter) {
        Ship target = nearestHostile(shooter);
        if (!isAlive(target)) return;
        double dist = Math.hypot(target.x - shooter.x, target.y - shooter.y);
        if (dist > DIRECT_FIRE_RANGE) return;
        int damage = Math.max(DIRECT_FIRE_DAMAGE, target.hpMax * 2);
        target.takePenetratingInternalDamage(damage, target.x, target.y, 0.0, 0.0);
        if (target.hp > 0 && !target.dying) {
            target.scaleCurrentHullIntegrity(0.0);
        }
        if (target.hp <= 0 || target.dying) {
            target.alive = false;
            target.dying = false;
        }
    }

    private Ship nearestHostile(Ship shooter) {
        if (!isAlive(shooter) || shooter.faction == null) return null;
        Ship best = null;
        double bestD2 = DIRECT_FIRE_RANGE * DIRECT_FIRE_RANGE;
        for (Ship ship : runtime.context().ships) {
            if (!isAlive(ship) || ship == shooter || ship.faction == null) continue;
            if (shooter.faction.isFriendlyTo(ship.faction)) continue;
            double d2 = dist2(shooter.x, shooter.y, ship.x, ship.y);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = ship;
            }
        }
        return best;
    }

    private Ship findShip(int shipId) {
        for (Ship ship : runtime.context().ships) {
            if (ship != null && ship.id == shipId) return ship;
        }
        return null;
    }

    private static boolean isAlive(Ship ship) {
        return ship != null && ship.alive && !ship.dying && ship.hp > 0;
    }

    private static double dist2(double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        return dx * dx + dy * dy;
    }
}
