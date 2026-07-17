import app.config.GameConfig;
import app.config.GameMode;

import java.util.Collections;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;

/** Shared V1 custom-battle runtime shell for authoritative sessions. */
public final class MultiplayerBattleRuntime {
    public interface TickRunner {
        void run(GameContext context, double tickSeconds, long hostTick);
    }

    private final MultiplayerRulesV1.BattleSetup setup;
    private final GameContext context;
    private final MultiplayerFixedStepClock clock;
    private final MultiplayerCommandGate commandGate;
    private final MultiplayerEntityIdAllocator entityIds;
    private final MultiplayerBattleThreadGuard threadGuard;
    private final Map<Integer, MultiplayerPlayerSlotState> slots = new HashMap<>();

    private MultiplayerBattleRuntime(MultiplayerRulesV1.BattleSetup setup, GameContext context) {
        this.setup = setup;
        this.context = context;
        this.clock = new MultiplayerFixedStepClock();
        this.commandGate = new MultiplayerCommandGate();
        this.entityIds = new MultiplayerEntityIdAllocator();
        this.threadGuard = new MultiplayerBattleThreadGuard();
        registerSlotsFromContext();
    }

    public static MultiplayerRulesV1.ValidationResult validateSetup(MultiplayerRulesV1.BattleSetup setup) {
        return MultiplayerRulesV1.validate(setup);
    }

    public static MultiplayerBattleRuntime createAuthoritative(MultiplayerRulesV1.BattleSetup setup,
                                                               boolean hostAndClientLocal) {
        MultiplayerRulesV1.ValidationResult validation = MultiplayerRulesV1.validate(setup);
        if (!validation.accepted()) {
            throw new IllegalArgumentException(validation.message());
        }
        GameContext context = createFreshBattleContext(setup, hostAndClientLocal);
        return new MultiplayerBattleRuntime(setup, context);
    }

    private static GameContext createFreshBattleContext(MultiplayerRulesV1.BattleSetup setup,
                                                       boolean hostAndClientLocal) {
        GameConfig config = new GameConfig(GameMode.CUSTOM_BATTLES, 3600, 2200,
                true, setup.seed(), false, setup.hostSlot().team().teamId(),
                false, setup.clientSlot().team().teamId(), "", "");
        MultiplayerRulesV1.ValidationResult configValidation = MultiplayerRulesV1.validateBattleOnlyConfig(config);
        if (!configValidation.accepted()) {
            throw new IllegalArgumentException(configValidation.message());
        }

        GameContext ctx = new GameContext(config);
        ctx.ships.clear();
        ctx.projectiles.clear();
        ctx.asteroids.clear();
        ctx.salvage.clear();
        ctx.damageEvents.clear();
        ctx.audioEvents.clear();
        ctx.fleetCommLog.clear();
        ctx.teamBases.clear();
        ctx.allyBase = null;
        ctx.enemyBase = null;
        ctx.campaign = null;
        ctx.command.alliedFleetCommand = GameContext.FleetCommand.AUTO;
        ctx.command.alliedFleetFormation = GameContext.FleetFormation.WEDGE;

        double cy = ctx.WORLD_H * 0.5;
        Player host = new Player(setup.hostSlot().hull(), ctx.WORLD_W * 0.28, cy);
        host.faction = setup.hostSlot().team();
        host.name = setup.hostSlot().displayName();
        ctx.player = host;
        ctx.ships.add(host);

        Ship client = hostAndClientLocal
                ? new Player(setup.clientSlot().hull(), ctx.WORLD_W * 0.72, cy)
                : new FleetShip(setup.clientSlot().hull(), setup.clientSlot().team(), ctx.WORLD_W * 0.72, cy);
        client.faction = setup.clientSlot().team();
        client.name = setup.clientSlot().displayName();
        client.angle = Math.PI;
        ctx.ships.add(client);

        ctx.entityQuery.rebuild(ctx);
        return ctx;
    }

    private void registerSlotsFromContext() {
        Ship hostShip = findShipByName(setup.hostSlot().displayName());
        Ship clientShip = findShipByName(setup.clientSlot().displayName());
        registerSlot(setup.hostSlot(), hostShip, MultiplayerRulesV1.ConnectionState.LOCAL);
        registerSlot(setup.clientSlot(), clientShip, MultiplayerRulesV1.ConnectionState.CONNECTED);
    }

    private void registerSlot(MultiplayerRulesV1.PlayerSlot slot, Ship ship,
                              MultiplayerRulesV1.ConnectionState connectionState) {
        int shipId = (ship == null) ? 0 : ship.id;
        MultiplayerPlayerSlotState state = new MultiplayerPlayerSlotState(
                slot.slotId(), slot.team().teamId(), shipId,
                MultiplayerRulesV1.PlayerRole.DIRECT_SHIP,
                connectionState, slot.displayName());
        slots.put(slot.slotId(), state);
        commandGate.registerSlot(new MultiplayerCommandGate.SlotOwnership(
                slot.slotId(), shipId, state.connected(), true));
    }

    private Ship findShipByName(String name) {
        for (Ship ship : context.ships) {
            if (ship != null && ship.name != null && ship.name.equals(name)) return ship;
        }
        return null;
    }

    public MultiplayerFixedStepClock.StepPlan planFrame(double elapsedSeconds) {
        threadGuard.assertOwnerThread("fixed-step planning");
        return clock.planFrame(elapsedSeconds);
    }

    public MultiplayerFixedStepClock.StepPlan advanceFrame(double elapsedSeconds, TickRunner runner) {
        threadGuard.assertOwnerThread("fixed-step advance");
        MultiplayerFixedStepClock.StepPlan plan = clock.planFrame(elapsedSeconds);
        if (runner != null) {
            for (int i = 0; i < plan.ticksToRun(); i++) {
                runner.run(context, plan.tickSeconds(), plan.firstTick() + i);
            }
        }
        return plan;
    }

    public MultiplayerCommandGate.CommandResult acceptInput(MultiplayerCommandGate.PlayerInputFrame frame,
                                                            long authoritativeTick) {
        threadGuard.assertOwnerThread("input validation");
        return commandGate.validateInputFrame(frame, authoritativeTick);
    }

    public MultiplayerCommandGate.CommandResult acceptLocalInput(InputSnapshot input, long sequence, long hostTick,
                                                                 boolean primaryHeld, boolean secondaryHeld) {
        MultiplayerPlayerSlotState slot = slots.get(MultiplayerRulesV1.HOST_SLOT_ID);
        int shipId = (slot == null) ? 0 : slot.controlledShipId;
        MultiplayerCommandGate.PlayerInputFrame frame = MultiplayerInputFrameAdapter.fromLocalInput(
                MultiplayerRulesV1.HOST_SLOT_ID, shipId, sequence, hostTick, input, primaryHeld, secondaryHeld);
        return acceptInput(frame, hostTick);
    }

    public MultiplayerBattleSnapshot snapshot(long hostTick) {
        threadGuard.assertOwnerThread("snapshot publication");
        ArrayList<MultiplayerBattleSnapshot.ShipSnapshot> shipSnapshots = new ArrayList<>();
        for (Ship ship : context.ships) {
            if (ship == null) continue;
            shipSnapshots.add(new MultiplayerBattleSnapshot.ShipSnapshot(
                    ship.id, ship.role, ship.faction, ship.x, ship.y, ship.vx, ship.vy,
                    ship.angle, ship.hp, ship.shield, ship.alive));
        }
        ArrayList<MultiplayerBattleSnapshot.SlotSnapshot> slotSnapshots = new ArrayList<>();
        for (MultiplayerPlayerSlotState slot : slots.values()) {
            if (slot == null) continue;
            slotSnapshots.add(new MultiplayerBattleSnapshot.SlotSnapshot(
                    slot.slotId, slot.teamId, slot.controlledShipId, slot.role,
                    slot.connectionState, slot.displayName));
        }
        return new MultiplayerBattleSnapshot(hostTick, shipSnapshots, slotSnapshots);
    }

    public GameContext context() {
        return context;
    }

    public MultiplayerRulesV1.BattleSetup setup() {
        return setup;
    }

    public MultiplayerFixedStepClock clock() {
        return clock;
    }

    public MultiplayerCommandGate commandGate() {
        return commandGate;
    }

    public MultiplayerEntityIdAllocator entityIds() {
        return entityIds;
    }

    public MultiplayerBattleThreadGuard threadGuard() {
        return threadGuard;
    }

    public Map<Integer, MultiplayerPlayerSlotState> slots() {
        return Collections.unmodifiableMap(slots);
    }
}
