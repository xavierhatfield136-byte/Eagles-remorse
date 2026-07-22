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
    private final Map<Integer, Integer> spawnedShipIdsBySlot;
    private String matchId = "match:local";
    private String sessionNonce = MultiplayerProtocolV1.sessionNonceForMatch(matchId);

    private record CreatedBattleContext(GameContext context, int hostShipId, int clientShipId) {}

    private MultiplayerBattleRuntime(MultiplayerRulesV1.BattleSetup setup,
                                     GameContext context,
                                     Map<Integer, Integer> spawnedShipIdsBySlot) {
        this.setup = setup;
        this.context = context;
        this.spawnedShipIdsBySlot = spawnedShipIdsBySlot == null ? Map.of() : Map.copyOf(spawnedShipIdsBySlot);
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
        return createAuthoritative(setup, hostAndClientLocal, MultiplayerRulesV1.HOST_SLOT_ID);
    }

    public static MultiplayerBattleRuntime createAuthoritative(MultiplayerRulesV1.BattleSetup setup,
                                                               boolean hostAndClientLocal,
                                                               int localSlotId) {
        return createAuthoritative(setup, hostAndClientLocal, localSlotId, 3600, 2200);
    }

    public static MultiplayerBattleRuntime createAuthoritative(MultiplayerRulesV1.BattleSetup setup,
                                                               boolean hostAndClientLocal,
                                                               int localSlotId,
                                                               int worldW,
                                                               int worldH) {
        MultiplayerRulesV1.ValidationResult validation = MultiplayerRulesV1.validate(setup);
        if (!validation.accepted()) {
            throw new IllegalArgumentException(validation.message());
        }
        CreatedBattleContext created = createFreshBattleContext(
                setup, hostAndClientLocal, localSlotId, worldW, worldH);
        return new MultiplayerBattleRuntime(setup, created.context(), Map.of(
                MultiplayerRulesV1.HOST_SLOT_ID, created.hostShipId(),
                MultiplayerRulesV1.CLIENT_SLOT_ID, created.clientShipId()));
    }

    private static CreatedBattleContext createFreshBattleContext(MultiplayerRulesV1.BattleSetup setup,
                                                                 boolean hostAndClientLocal,
                                                                 int localSlotId,
                                                                 int worldW,
                                                                 int worldH) {
        GameConfig config = new GameConfig(GameMode.CUSTOM_BATTLES,
                Math.max(1800, Math.min(60000, worldW)),
                Math.max(1800, Math.min(60000, worldH)),
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
        ctx.multiplayerBattle = true;
        ctx.multiplayerAuthorityMode = localSlotId == MultiplayerRulesV1.CLIENT_SLOT_ID
                ? MultiplayerAuthorityMode.CLIENT_PRESENTATION
                : MultiplayerAuthorityMode.HOST;
        ctx.multiplayerLocalSlotId = localSlotId;
        ctx.multiplayerPlayerControlledShipIds.clear();
        ctx.campaign = null;
        ctx.command.alliedFleetCommand = GameContext.FleetCommand.AUTO;
        ctx.command.alliedFleetFormation = GameContext.FleetFormation.WEDGE;

        double cy = ctx.WORLD_H * 0.5;
        boolean hostLocal = localSlotId != MultiplayerRulesV1.CLIENT_SLOT_ID;
        Ship host = createPlayerSlotShip(setup.hostSlot(), ctx.WORLD_W * 0.28, cy);
        host.faction = setup.hostSlot().team();
        host.name = setup.hostSlot().displayName();
        if (hostLocal && host instanceof Player player) {
            ctx.player = player;
        }
        ctx.ships.add(host);

        boolean clientLocal = hostAndClientLocal || localSlotId == MultiplayerRulesV1.CLIENT_SLOT_ID;
        Ship client = createPlayerSlotShip(setup.clientSlot(), ctx.WORLD_W * 0.72, cy);
        client.faction = setup.clientSlot().team();
        client.name = setup.clientSlot().displayName();
        client.angle = Math.PI;
        if (client instanceof Player player && localSlotId == MultiplayerRulesV1.CLIENT_SLOT_ID) {
            ctx.player = player;
        }
        ctx.ships.add(client);
        ctx.multiplayerPlayerControlledShipIds.add(host.id);
        ctx.multiplayerPlayerControlledShipIds.add(client.id);
        if (setup.aiShips()) {
            spawnAiSupportShips(ctx, setup);
        }

        ctx.entityQuery.rebuild(ctx);
        return new CreatedBattleContext(ctx, host.id, client.id);
    }

    private static void spawnAiSupportShips(GameContext ctx, MultiplayerRulesV1.BattleSetup setup) {
        if (ctx == null || setup == null) return;
        double cy = ctx.WORLD_H * 0.5;
        Ship hostSupport = new FleetShip(ShipRole.CIWS_CORVETTE, setup.hostSlot().team(),
                ctx.WORLD_W * 0.38, cy - 170.0);
        hostSupport.name = "Host Support";
        hostSupport.angle = 0.0;
        ctx.ships.add(hostSupport);

        Ship clientSupport = new FleetShip(ShipRole.CIWS_CORVETTE, setup.clientSlot().team(),
                ctx.WORLD_W * 0.62, cy + 170.0);
        clientSupport.name = "Client Support";
        clientSupport.angle = Math.PI;
        ctx.ships.add(clientSupport);
    }

    private static Ship createPlayerSlotShip(MultiplayerRulesV1.PlayerSlot slot, double x, double y) {
        ShipRole hull = slot == null ? ShipRole.FRIGATE : slot.hull();
        Faction faction = slot == null ? Faction.ALLY : slot.team();
        Player ship = new Player(hull, x, y);
        ship.faction = faction;
        return ship;
    }

    private void registerSlotsFromContext() {
        Ship hostShip = findShipById(spawnedShipIdsBySlot.getOrDefault(MultiplayerRulesV1.HOST_SLOT_ID, 0));
        Ship clientShip = findShipById(spawnedShipIdsBySlot.getOrDefault(MultiplayerRulesV1.CLIENT_SLOT_ID, 0));
        registerSlot(setup.hostSlot(), hostShip, MultiplayerRulesV1.ConnectionState.LOCAL);
        registerSlot(setup.clientSlot(), clientShip, MultiplayerRulesV1.ConnectionState.CONNECTED);
    }

    private void registerSlot(MultiplayerRulesV1.PlayerSlot slot, Ship ship,
                              MultiplayerRulesV1.ConnectionState connectionState) {
        int shipId = (ship == null) ? 0 : ship.id;
        String playerId = MultiplayerProtocolV1.playerIdForSlot(slot.slotId());
        MultiplayerPlayerSlotState state = new MultiplayerPlayerSlotState(
                slot.slotId(), slot.team().teamId(), shipId,
                MultiplayerRulesV1.PlayerRole.DIRECT_SHIP,
                connectionState, slot.displayName());
        slots.put(slot.slotId(), state);
        context.multiplayerPlayerControlledShipIds.add(shipId);
        commandGate.registerSlot(new MultiplayerCommandGate.SlotOwnership(
                slot.slotId(), shipId, state.connected(), true, playerId));
    }

    public void configureMatchIdentity(String matchId, String sessionNonce) {
        this.matchId = clean(matchId).isBlank() ? "match:local" : clean(matchId);
        this.sessionNonce = clean(sessionNonce).isBlank()
                ? MultiplayerProtocolV1.sessionNonceForMatch(this.matchId)
                : clean(sessionNonce);
        commandGate.configureMatchIdentity(this.matchId, this.sessionNonce);
        context.multiplayerMatchId = this.matchId;
        context.multiplayerSessionNonce = this.sessionNonce;
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
        MultiplayerCommandGate.CommandResult runtimeResult =
                validateRuntimeCommandState(frame, authoritativeTick);
        if (runtimeResult != null) return runtimeResult;
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
        return new MultiplayerBattleSnapshot(
                hostTick,
                commandGate.lastProcessedInputSequence(),
                shipSnapshots,
                slotSnapshots);
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

    private MultiplayerCommandGate.CommandResult validateRuntimeCommandState(
            MultiplayerCommandGate.PlayerInputFrame frame,
            long authoritativeTick) {
        if (frame == null) return null;
        if (context.gameOver || context.state == GameState.GAME_OVER) {
            return new MultiplayerCommandGate.CommandResult(
                    false,
                    "Match is over",
                    frame.sequence(),
                    Math.max(-1L, authoritativeTick));
        }
        MultiplayerPlayerSlotState slot = slots.get(frame.slotId());
        if (slot == null || slot.controlledShipId != frame.controlledShipId()) {
            return null;
        }
        Ship ship = findShipById(frame.controlledShipId());
        if (ship == null || !ship.alive || ship.dying || ship.hp <= 0) {
            return new MultiplayerCommandGate.CommandResult(
                    false,
                    "Player ship is destroyed",
                    frame.sequence(),
                    Math.max(-1L, authoritativeTick));
        }
        return null;
    }

    private Ship findShipById(int shipId) {
        for (Ship ship : context.ships) {
            if (ship != null && ship.id == shipId) return ship;
        }
        return null;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
