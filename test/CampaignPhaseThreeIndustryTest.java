import app.config.GameConfig;
import app.config.GameMode;
import app.persistence.CampaignCheckpointStore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CampaignPhaseThreeIndustryTest {

    @Test
    void productionLanesUseTheFivePhaseThreeBaseTimes() {
        assertEquals(CampaignSystem.CampaignProductionLane.ESCORT,
                CampaignSystem.campaignProductionLane(ShipRole.PATROL));
        assertEquals(CampaignSystem.CampaignProductionLane.FRIGATE_DESTROYER,
                CampaignSystem.campaignProductionLane(ShipRole.FRIGATE));
        assertEquals(CampaignSystem.CampaignProductionLane.CRUISER,
                CampaignSystem.campaignProductionLane(ShipRole.CRUISER));
        assertEquals(CampaignSystem.CampaignProductionLane.CAPITAL,
                CampaignSystem.campaignProductionLane(ShipRole.BATTLESHIP));
        assertEquals(CampaignSystem.CampaignProductionLane.TITAN_SPECIAL,
                CampaignSystem.campaignProductionLane(ShipRole.TRANSPORT_TITAN));
        assertEquals(5.0, CampaignSystem.campaignLaneBaseSeconds(ShipRole.PATROL));
        assertEquals(10.0, CampaignSystem.campaignLaneBaseSeconds(ShipRole.FRIGATE));
        assertEquals(15.0, CampaignSystem.campaignLaneBaseSeconds(ShipRole.CRUISER));
        assertEquals(20.0, CampaignSystem.campaignLaneBaseSeconds(ShipRole.BATTLESHIP));
        assertEquals(25.0, CampaignSystem.campaignLaneBaseSeconds(ShipRole.TRANSPORT_TITAN));
    }

    @Test
    void unrelatedYardLanesAdvanceTogetherWhileSameLanePreservesQueueOrder() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignLocation yard = shipyard(ctx);
        int fleetBefore = ctx.campaign.persistentBlueFleet.size();

        queuePlayerOrder(ctx, yard, ShipRole.PATROL);
        queuePlayerOrder(ctx, yard, ShipRole.FRIGATE);
        queuePlayerOrder(ctx, yard, ShipRole.MISSILE_BOAT);
        assertEquals(3, CampaignSystem.campaignYardOrders(ctx).size());

        advancePlayerOrders(ctx, 5.0);
        assertEquals(fleetBefore + 1, ctx.campaign.persistentBlueFleet.size());
        List<CampaignSystem.CampaignYardOrder> afterFive = CampaignSystem.campaignYardOrders(ctx);
        assertEquals(2, afterFive.size());
        assertEquals(5.0, afterFive.get(0).remainingSeconds, 1e-6);
        assertEquals(10.0, afterFive.get(1).remainingSeconds, 1e-6,
                "the second frigate-lane order must wait behind the first");

        advancePlayerOrders(ctx, 5.0);
        assertEquals(fleetBefore + 2, ctx.campaign.persistentBlueFleet.size());
        assertEquals(1, CampaignSystem.campaignYardOrders(ctx).size());
        assertEquals(10.0, CampaignSystem.campaignYardOrders(ctx).get(0).remainingSeconds, 1e-6);
    }

    @Test
    void yardDamageSlowsWorkCapturePausesItAndDestructionRefundsOnce() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignLocation yard = shipyard(ctx);
        ctx.credits = 0;
        CampaignSystem.grantCampaignOre(ctx, 0);
        ctx.campaign.campaignSalvage = 0;
        queuePlayerOrder(ctx, yard, ShipRole.FRIGATE, 100, 20, 10);

        yard.stationDamageState = "damaged";
        advancePlayerOrders(ctx, 4.0);
        assertEquals(8.0, CampaignSystem.campaignYardOrders(ctx).get(0).remainingSeconds, 1e-6);

        Faction originalOwner = yard.ownerFaction;
        yard.ownerFaction = Faction.ENEMY;
        advancePlayerOrders(ctx, 4.0);
        assertEquals(8.0, CampaignSystem.campaignYardOrders(ctx).get(0).remainingSeconds, 1e-6);
        assertTrue(String.join("\n", CampaignSystem.campaignYardDocketLines(ctx, 5)).contains("PAUSED: YARD CAPTURED"));

        yard.ownerFaction = originalOwner;
        yard.destroyed = true;
        advancePlayerOrders(ctx, 1.0);
        assertTrue(CampaignSystem.campaignYardOrders(ctx).isEmpty());
        assertEquals(50, ctx.credits);
        assertEquals(10, CampaignSystem.currentCampaignOre(ctx));
        assertEquals(5, ctx.campaign.campaignSalvage);
        advancePlayerOrders(ctx, 1.0);
        assertEquals(50, ctx.credits, "a canceled order must not refund twice");
    }

    @Test
    void oreExistsInCargoBeforeItCanReachTheFactionDestination() throws Exception {
        GameContext ctx = initializedCampaignContext();
        createMiningForce(ctx, Faction.TEAM_C, "Green Phase Three Miner");
        CampaignSystem.CampaignForceSummary miner = CampaignSystem.campaignForceSummaries(ctx).stream()
                .filter(force -> force.kind == CampaignSystem.CampaignForceKind.MINING_GROUP)
                .filter(force -> force.faction != null && force.faction.isFriendlyTo(ctx.player.faction))
                .findFirst()
                .orElseThrow();
        CampaignSystem.CampaignLocation site = allLocations(ctx).stream()
                .filter(location -> location.type == CampaignSystem.CampaignLocationType.RESOURCE_ZONE)
                .filter(location -> !location.consumed && location.oreStockpile > 20)
                .findFirst()
                .orElseThrow();

        assertTrue(CampaignSystem.assignCampaignMiningOrder(ctx, miner.id, site.id));
        Object force = forceById(ctx.campaign, miner.id);
        CampaignSystem.CampaignLocation home = locationById(ctx, getString(force, "homeBaseId"));
        assertNotNull(home);
        int siteBefore = site.oreStockpile;
        int homeBefore = home.oreStockpile;

        setDouble(force, "x", site.x);
        setDouble(force, "y", site.y);
        assertTrue(CampaignSystem.assignCampaignMiningOrder(ctx, miner.id, site.id));
        setDouble(force, "cargoLoad", 84.0);
        invokeCampaign("applyMiningAndHaulingEconomy",
                new Class<?>[]{CampaignSystem.CampaignState.class, double.class},
                ctx.campaign, 10.0);

        double cargo = getDouble(force, "cargoLoad");
        assertTrue(cargo > 0.0);
        assertTrue(site.oreStockpile < siteBefore);
        assertEquals(homeBefore, home.oreStockpile,
                "ore must not be credited before the mining force unloads");
        assertEquals("DOCKING", getField(force, "intent").toString());
        assertEquals(home.id, getString(force, "destinationLocationId"));
        invokeCampaign("applyMiningAndHaulingEconomy",
                new Class<?>[]{CampaignSystem.CampaignState.class, double.class},
                ctx.campaign, 10.0);
        assertEquals(home.id, getString(force, "destinationLocationId"),
                "a loaded miner must keep its return route instead of reacquiring another ore site");

        setField(force, "destinationLocationId", home.id);
        setEnum(force, "stopReason", "UNLOADING");
        setEnum(force, "workState", "WORKING");
        setEnum(force, "missionState", "WORKING");
        setDouble(force, "workRemainingSec", 0.0);
        setDouble(force, "taskDeadlineSec", 0.0);
        invokeCampaign("updateCampaignForceLifecycleAfterMovement",
                new Class<?>[]{CampaignSystem.CampaignState.class, force.getClass(), double.class},
                ctx.campaign, force, 1.0);

        assertEquals(0.0, getDouble(force, "cargoLoad"), 1e-6);
        assertEquals(homeBefore + (int) Math.round(cargo), home.oreStockpile);
    }

    @Test
    void stolenOreRecoveryAwardsCargoExactlyOnce() throws Exception {
        GameContext ctx = initializedCampaignContext();
        createMiningForce(ctx, Faction.ENEMY, "Red Phase Three Miner");
        CampaignSystem.CampaignForceSummary hostile = CampaignSystem.campaignForceSummaries(ctx).stream()
                .filter(force -> force.kind == CampaignSystem.CampaignForceKind.MINING_GROUP)
                .filter(force -> force.faction == Faction.ENEMY)
                .findFirst()
                .orElseThrow();
        Object force = forceById(ctx.campaign, hostile.id);
        setEnum(force, "cargoKind", "ORE");
        setDouble(force, "cargoCapacity", 100.0);
        setDouble(force, "cargoLoad", 37.0);
        int before = CampaignSystem.currentCampaignOre(ctx);

        assertEquals(37, CampaignSystem.recoverCampaignForceOre(ctx, hostile.id));
        assertEquals(before + 37, CampaignSystem.currentCampaignOre(ctx));
        assertEquals(0, CampaignSystem.recoverCampaignForceOre(ctx, hostile.id));
        assertEquals(before + 37, CampaignSystem.currentCampaignOre(ctx));
    }

    @Test
    void transportSupportConsumesSuppliesAndDoesNotRestoreArmorOrShields() throws Exception {
        GameContext ctx = bareCampaignContext();
        ctx.campaign.campaignSupplies = 2;
        Ship transport = new FleetShip(ShipRole.TRANSPORT, Faction.ALLY, 2500.0, 2500.0);
        Ship ally = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 2520.0, 2500.0);
        ally.shieldRegen = 0.0;
        ally.shield = Math.max(0.0, ally.shieldMax * 0.25);
        ally.seedRoomFire(ShipRoomLayout.RoomId.REACTOR, 1.2);
        ctx.ships.add(transport);
        ctx.ships.add(ally);
        double shieldBefore = ally.shield;
        double armorBefore = armorIntegrity(ally);
        double fireBefore = ally.totalFireIntensity();

        for (int i = 0; i < 6; i++) {
            invokeEconomyTransportSupport(ctx, 1.0);
        }

        assertTrue(ctx.campaign.campaignSupplies < 2);
        assertTrue(ally.totalFireIntensity() < fireBefore);
        assertEquals(shieldBefore, ally.shield, 1e-6);
        assertEquals(armorBefore, armorIntegrity(ally), 1e-6);
        assertTrue(String.join("\n", CampaignSystem.campaignTransportRepairSupportLines(ctx))
                .contains("armor and shields excluded"));
    }

    @Test
    void phaseThreeReadoutsExposeOrdersYardsAndRealIndustryMissionTargets() {
        GameContext ctx = initializedCampaignContext();
        try {
            createMiningForce(ctx, Faction.TEAM_C, "Green Readout Miner");
            createMiningForce(ctx, Faction.ENEMY, "Red Readout Miner");
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
        assertTrue(String.join("\n", CampaignSystem.campaignMiningOrderLines(ctx, 12)).contains("route risk"));
        assertTrue(String.join("\n", CampaignSystem.campaignShipyardStateLines(ctx, 12)).contains("local lanes"));
        String missions = String.join("\n", CampaignSystem.campaignMiningProductionMissionLines(ctx));
        assertTrue(missions.contains("MINING CONVOY ESCORT"));
        assertTrue(missions.contains("CAPITAL-COMPLETION INTERDICTION"));
        assertTrue(missions.contains("real cargo or queue state changes"));
    }

    @Test
    void yardLaneAndProducingFactionSurviveCheckpointRoundTrip() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignLocation yard = shipyard(ctx);
        queuePlayerOrder(ctx, yard, ShipRole.CRUISER);
        CampaignCheckpointStore.Checkpoint checkpoint = captureCheckpoint(ctx);
        GameContext restored = initializedCampaignContext();
        assertTrue(applyCheckpoint(restored, checkpoint));
        CampaignSystem.CampaignYardOrder order = CampaignSystem.campaignYardOrders(restored).get(0);
        assertEquals(CampaignSystem.CampaignProductionLane.CRUISER, order.lane);
        assertEquals(yard.ownerFaction, order.producingFaction);
    }

    private static GameContext initializedCampaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 9137L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static GameContext bareCampaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 9138L, false));
        ctx.campaign = new CampaignSystem.CampaignState();
        ctx.campaign.enabled = true;
        ctx.player = new Player(ShipRole.MOTHERSHIP, 2500.0, 2500.0);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.add(ctx.player);
        return ctx;
    }

    private static CampaignSystem.CampaignLocation shipyard(GameContext ctx) {
        return allLocations(ctx).stream()
                .filter(location -> location.facilityType == CampaignSystem.CampaignFacilityType.SHIPYARD)
                .findFirst()
                .orElseThrow();
    }

    private static List<CampaignSystem.CampaignLocation> allLocations(GameContext ctx) {
        java.util.ArrayList<CampaignSystem.CampaignLocation> out = new java.util.ArrayList<>();
        out.addAll(CampaignSystem.mainCampaignLocations(ctx));
        out.addAll(CampaignSystem.campaignAreasOfInterest(ctx));
        return out;
    }

    private static CampaignSystem.CampaignLocation locationById(GameContext ctx, String id) {
        return allLocations(ctx).stream().filter(location -> location.id.equals(id)).findFirst().orElse(null);
    }

    private static Object createMiningForce(GameContext ctx, Faction faction, String name) throws Exception {
        CampaignSystem.CampaignLocation site = allLocations(ctx).stream()
                .filter(location -> location.type == CampaignSystem.CampaignLocationType.RESOURCE_ZONE)
                .filter(location -> !location.consumed && location.oreStockpile > 20)
                .findFirst()
                .orElseThrow();
        CampaignSystem.CampaignLocation home = allLocations(ctx).stream()
                .filter(location -> location.ownerFaction == faction)
                .filter(location -> location.facilityType == CampaignSystem.CampaignFacilityType.SHIPYARD
                        || location.facilityType == CampaignSystem.CampaignFacilityType.MINING_OPERATION
                        || location.facilityType == CampaignSystem.CampaignFacilityType.RESUPPLY_BASE
                        || location.facilityType == CampaignSystem.CampaignFacilityType.CIVILIAN_HUB
                        || location.facilityType == CampaignSystem.CampaignFacilityType.REPAIR_YARD
                        || location.facilityType == CampaignSystem.CampaignFacilityType.FUEL_DEPOT)
                .findFirst()
                .orElseThrow();
        Method method = CampaignSystem.class.getDeclaredMethod(
                "ensureCampaignForceWithoutDeploymentCost",
                CampaignSystem.CampaignState.class,
                CampaignSystem.CampaignForceKind.class,
                Faction.class,
                String.class,
                String.class,
                String.class,
                double.class,
                double.class);
        method.setAccessible(true);
        Object force = method.invoke(null, ctx.campaign, CampaignSystem.CampaignForceKind.MINING_GROUP,
                faction, name, home.name, "Strategic ore extraction", home.x, home.y);
        setField(force, "homeBaseId", home.id);
        setField(force, "sourceLocationId", home.id);
        setField(force, "destinationLocationId", site.id);
        setEnum(force, "cargoKind", "ORE");
        setDouble(force, "cargoCapacity", 100.0);
        setDouble(force, "speed", 55.0);
        return force;
    }

    private static void queuePlayerOrder(GameContext ctx, CampaignSystem.CampaignLocation yard, ShipRole role) throws Exception {
        queuePlayerOrder(ctx, yard, role, 0, 0, 0);
    }

    private static void queuePlayerOrder(GameContext ctx,
                                         CampaignSystem.CampaignLocation yard,
                                         ShipRole role,
                                         int credits,
                                         int ore,
                                         int salvage) throws Exception {
        assertTrue((boolean) invokeCampaign("queueCampaignConstructionOrder",
                new Class<?>[]{GameContext.class, CampaignSystem.CampaignState.class,
                        CampaignSystem.CampaignLocation.class, ShipRole.class, int.class, int.class, int.class},
                ctx, ctx.campaign, yard, role, credits, ore, salvage));
    }

    private static void advancePlayerOrders(GameContext ctx, double dt) throws Exception {
        invokeCampaign("advanceCampaignYardOrders",
                new Class<?>[]{GameContext.class, CampaignSystem.CampaignState.class, double.class},
                ctx, ctx.campaign, dt);
    }

    private static Object forceById(CampaignSystem.CampaignState state, int id) throws Exception {
        Field field = CampaignSystem.CampaignState.class.getDeclaredField("campaignForces");
        field.setAccessible(true);
        for (Object force : (List<?>) field.get(state)) {
            if ((int) getField(force, "id") == id) return force;
        }
        throw new AssertionError("missing force " + id);
    }

    private static void invokeEconomyTransportSupport(GameContext ctx, double dt) throws Exception {
        Method method = EconomySystem.class.getDeclaredMethod("applyTransportSupportAuras", GameContext.class, double.class);
        method.setAccessible(true);
        method.invoke(null, ctx, dt);
    }

    private static double armorIntegrity(Ship ship) {
        return ship.roomStatusSnapshot().stream()
                .filter(room -> room != null && room.roomId != null && ShipRoomLayout.isArmorRoom(room.roomId))
                .mapToDouble(room -> room.hp)
                .sum();
    }

    private static CampaignCheckpointStore.Checkpoint captureCheckpoint(GameContext ctx) throws Exception {
        return (CampaignCheckpointStore.Checkpoint) invokeCampaign("captureCheckpoint",
                new Class<?>[]{GameContext.class, CampaignSystem.CampaignState.class, int.class},
                ctx, ctx.campaign, 2);
    }

    private static boolean applyCheckpoint(GameContext ctx, CampaignCheckpointStore.Checkpoint checkpoint) throws Exception {
        return (boolean) invokeCampaign("applyCheckpoint",
                new Class<?>[]{GameContext.class, CampaignSystem.CampaignState.class, CampaignCheckpointStore.Checkpoint.class},
                ctx, ctx.campaign, checkpoint);
    }

    private static Object invokeCampaign(String name, Class<?>[] signature, Object... args) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(name, signature);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static String getString(Object target, String name) throws Exception {
        Object value = getField(target, name);
        return value == null ? "" : value.toString();
    }

    private static double getDouble(Object target, String name) throws Exception {
        return (double) getField(target, name);
    }

    private static void setDouble(Object target, String name, double value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setDouble(target, value);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setEnum(Object target, String name, String value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, Enum.valueOf((Class<? extends Enum>) field.getType(), value));
    }
}
