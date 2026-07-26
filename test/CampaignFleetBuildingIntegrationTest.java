import app.config.GameConfig;
import app.config.GameMode;
import app.persistence.CampaignCheckpointStore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CampaignFleetBuildingIntegrationTest {
    @Test
    void shipyardOffersIncludeBroaderFactionCapitalCatalogs() {
        GameContext ctx = campaignContext();
        Set<ShipRole> offers = new HashSet<>();
        for (CampaignSystem.CampaignLocation location : CampaignSystem.mainCampaignLocations(ctx)) {
            if (location == null || !location.services.contains(CampaignSystem.HubService.SHIPYARD)) continue;
            offers.add(CampaignSystem.shipyardOfferRole(location, CampaignSystem.hubProfile(ctx, location)));
        }

        assertTrue(offers.size() > 3, "campaign shipyards should expose more than the old tiny offer set");
        assertTrue(offers.stream().anyMatch(role -> role == ShipRole.BATTLECRUISER
                        || role == ShipRole.BATTLESHIP
                        || role == ShipRole.CARRIER
                        || role == ShipRole.DREADNOUGHT
                        || role.isTitan()),
                "later shipyards should sell larger vessels the player needs for fleet growth");
    }

    @Test
    void factionShipyardPurchasePreservesSellerHullIdentity() throws Exception {
        GameContext ctx = campaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation yard = shipyard(ctx);
        yard.ownerFaction = Faction.TEAM_C;
        ctx.credits = 1_000_000;
        CampaignSystem.grantCampaignOre(ctx, 1_000_000);
        int fleetBefore = liveFleetCount(st);

        assertTrue(invokeHubService(ctx, st, yard, CampaignSystem.HubService.SHIPYARD));
        CampaignSystem.CampaignYardOrder order = CampaignSystem.campaignYardOrders(ctx).get(0);
        assertEquals(Faction.TEAM_C, order.producingFaction);

        invokeAdvanceYardOrders(ctx, st, 5_000.0);

        Object entry = newestFleetEntry(st);
        assertEquals(fleetBefore + 1, liveFleetCount(st));
        assertEquals(Faction.TEAM_C.name(), getString(entry, "factionName"));
        assertEquals(order.role, getField(entry, "role"));
        int activeShipId = getInt(entry, "activeShipId");
        Ship delivered = findShipById(ctx, activeShipId);
        assertNotNull(delivered);
        assertEquals(Faction.TEAM_C, delivered.faction);
        assertEquals(order.role, delivered.role);
    }

    @Test
    void shipyardConstructionAndRefitAdvanceThroughCampaignTimeAndCheckpoint() throws Exception {
        GameContext ctx = campaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation yard = shipyard(ctx);
        ctx.credits = 100_000;
        ctx.player.cargo = 10_000;
        st.campaignSalvage = 1_000;

        int fleetBefore = liveFleetCount(st);
        assertTrue(invokeHubService(ctx, st, yard, CampaignSystem.HubService.SHIPYARD));
        assertEquals(1, CampaignSystem.campaignYardOrders(ctx).size());
        assertEquals(fleetBefore, liveFleetCount(st), "construction should deliver only after campaign time advances");

        CampaignCheckpointStore.Checkpoint checkpoint = captureCheckpoint(ctx, 3);
        GameContext restored = campaignContext();
        assertTrue(applyCheckpoint(restored, checkpoint));
        assertEquals(1, CampaignSystem.campaignYardOrders(restored).size());

        invokeAdvanceYardOrders(restored, restored.campaign, 200.0);
        assertTrue(CampaignSystem.campaignYardOrders(restored).isEmpty());
        assertEquals(fleetBefore + 1, liveFleetCount(restored.campaign));

        restored.credits = 100_000;
        restored.campaign.campaignSalvage = 1_000;
        assertTrue(invokeHubService(restored, restored.campaign, yard, CampaignSystem.HubService.REFIT));
        assertEquals(CampaignSystem.CampaignYardOrderKind.REFIT, CampaignSystem.campaignYardOrders(restored).get(0).kind);
        assertTrue(CampaignSystem.campaignFleetRefitScreenLines(restored).stream()
                .anyMatch(line -> line.startsWith("SAVED LOADOUTS  |  ")));
        invokeAdvanceYardOrders(restored, restored.campaign, 200.0);
        assertTrue(CampaignSystem.campaignYardOrders(restored).isEmpty());
    }

    @Test
    void fleetRecordsTrackRetreatTransferMemorialAndSuccessorLifecycle() throws Exception {
        GameContext ctx = campaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object entry = firstLiveFleetEntry(st);
        int slotId = getInt(entry, "slotId");

        assertTrue(CampaignSystem.recordPersistentFleetRetreat(ctx));
        assertTrue(getInt(entry, "retreats") > 0);
        assertTrue(getString(entry, "serviceHistory").contains("TACTICAL RETREAT"));

        assertTrue(CampaignSystem.transferPersistentFleetFaction(ctx, slotId, Faction.TEAM_C, "rescued crew contract"));
        assertEquals(Faction.TEAM_C.name(), getString(entry, "factionName"));
        assertTrue(getString(entry, "serviceHistory").contains("TRANSFER"));

        CampaignSystem.CampaignLocation yard = shipyard(ctx);
        ctx.credits = 100_000;
        ctx.player.cargo = 10_000;
        st.campaignSalvage = 1_000;
        assertTrue(invokeHubService(ctx, st, yard, CampaignSystem.HubService.SHIPYARD));
        CampaignSystem.CampaignYardOrder order = CampaignSystem.campaignYardOrders(ctx).get(0);
        Object predecessor = null;
        for (Object candidate : st.persistentBlueFleet) {
            if (!getBoolean(candidate, "destroyed") && getField(candidate, "role") == order.role) {
                predecessor = candidate;
                break;
            }
        }
        if (predecessor == null) {
            predecessor = CampaignSystem.addPersistentFleetEntry(st, order.role, "Blue Guard Legacy",
                    CampaignSystem.CAMPAIGN_FLAGSHIP_COMMAND_GROUP, Faction.ALLY);
        }
        assertNotNull(predecessor);
        setBoolean(predecessor, "destroyed", true);
        setString(predecessor, "name", "Blue Guard Legacy");
        invokeAdvanceYardOrders(ctx, st, 200.0);
        boolean successor = false;
        for (Object candidate : st.persistentBlueFleet) {
            if (getString(candidate, "name").endsWith(" II")
                    && getString(candidate, "serviceHistory").contains("SUCCESSOR TO SLOT")) {
                successor = true;
            }
        }
        assertTrue(successor);
    }

    @Test
    void strategicMapMissingActiveShipIdsDoNotDestroyPurchasedFleetRecords() throws Exception {
        GameContext ctx = campaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        ctx.credits = 100_000;
        ctx.player.cargo = 10_000;
        int before = liveFleetCount(st);

        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.FRIGATE, 0, 0));
        Object purchased = newestFleetEntry(st);
        assertEquals("COMMIT", getString(purchased, "tacticalCommitmentId"));
        setField(purchased, "activeShipId", 987654321);
        st.strategicOvermapMode = true;
        ctx.ships.removeIf(ship -> ship != null && ship != ctx.player);

        invokeSyncPersistentFleetCasualties(ctx, st);

        assertEquals(before + 1, liveFleetCount(st));
        assertFalse(getBoolean(purchased, "destroyed"),
                "a tactical ship missing on the strategic map should mean awaiting redeploy, not lost in action");
        assertEquals(-1, getInt(purchased, "activeShipId"));
    }

    @Test
    void everySectionFourHullHasAnExactLiveEncounterPath() {
        GameContext ctx = campaignContext();
        int index = 0;
        for (ShipRole role : ShipRole.values()) {
            Ship spawned = CampaignSystem.spawnSectionFourHullEncounter(
                    ctx, role, Faction.ENEMY, 300.0 + index * 7.0, 300.0 + index * 5.0);
            assertNotNull(spawned, role + " should have a live encounter path");
            assertEquals(role, spawned.role);
            index++;
        }
        assertEquals(ShipRole.values().length, CampaignSystem.sectionFourHullFlowLines().size());
    }

    @Test
    void liveStrategicControlsApplyOrdersAndExposeBalancePass() {
        GameContext ctx = campaignContext();
        String overlay = ctx.campaign.selectedStrategicOverlayId;
        assertTrue(CampaignSystem.cycleStrategicMapOverlay(ctx));
        assertNotEquals(overlay, ctx.campaign.selectedStrategicOverlayId);
        assertTrue(CampaignSystem.cycleCampaignTaskGroup(ctx));
        assertTrue(ctx.campaign.selectedCampaignTaskGroupId > 0);
        assertTrue(CampaignSystem.cycleSelectedCampaignTaskGroupOrder(ctx));
        assertTrue(CampaignSystem.campaignStrategicAuthorityLines(ctx).stream()
                .anyMatch(line -> line.startsWith("OVERLAY  |  ")));
        assertTrue(CampaignSystem.campaignScaleBalanceLines(ctx).stream()
                .anyMatch(line -> line.startsWith("MULTI-FRONT PRESSURE  |  ")));
    }

    private static GameContext campaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 441L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static CampaignSystem.CampaignLocation shipyard(GameContext ctx) {
        return CampaignSystem.mainCampaignLocations(ctx).stream()
                .filter(location -> location != null && location.services.contains(CampaignSystem.HubService.SHIPYARD))
                .findFirst()
                .orElseThrow();
    }

    private static int liveFleetCount(CampaignSystem.CampaignState st) throws Exception {
        int count = 0;
        for (Object entry : st.persistentBlueFleet) if (!getBoolean(entry, "destroyed")) count++;
        return count;
    }

    private static Object firstLiveFleetEntry(CampaignSystem.CampaignState st) throws Exception {
        for (Object entry : st.persistentBlueFleet) if (!getBoolean(entry, "destroyed")) return entry;
        throw new AssertionError("missing persistent fleet entry");
    }

    private static Object newestFleetEntry(CampaignSystem.CampaignState st) {
        if (st.persistentBlueFleet.isEmpty()) throw new AssertionError("missing persistent fleet entry");
        return st.persistentBlueFleet.get(st.persistentBlueFleet.size() - 1);
    }

    private static Ship findShipById(GameContext ctx, int id) {
        if (ctx == null || id < 0) return null;
        for (Ship ship : ctx.ships) {
            if (ship != null && ship.id == id) return ship;
        }
        return null;
    }

    private static boolean invokeHubService(GameContext ctx, CampaignSystem.CampaignState st,
                                            CampaignSystem.CampaignLocation yard,
                                            CampaignSystem.HubService service) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "performHubService", GameContext.class, CampaignSystem.CampaignState.class,
                CampaignSystem.CampaignLocation.class, CampaignSystem.HubService.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, ctx, st, yard, service);
    }

    private static void invokeAdvanceYardOrders(GameContext ctx, CampaignSystem.CampaignState st, double dt) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "advanceCampaignYardOrders", GameContext.class, CampaignSystem.CampaignState.class, double.class);
        method.setAccessible(true);
        method.invoke(null, ctx, st, dt);
    }

    private static void invokeSyncPersistentFleetCasualties(GameContext ctx, CampaignSystem.CampaignState st) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "syncPersistentFleetCasualties", GameContext.class, CampaignSystem.CampaignState.class);
        method.setAccessible(true);
        method.invoke(null, ctx, st);
    }

    private static CampaignCheckpointStore.Checkpoint captureCheckpoint(GameContext ctx, int nextSector) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "captureCheckpoint", GameContext.class, CampaignSystem.CampaignState.class, int.class);
        method.setAccessible(true);
        return (CampaignCheckpointStore.Checkpoint) method.invoke(null, ctx, ctx.campaign, nextSector);
    }

    private static boolean applyCheckpoint(GameContext ctx, CampaignCheckpointStore.Checkpoint checkpoint) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "applyCheckpoint", GameContext.class, CampaignSystem.CampaignState.class, CampaignCheckpointStore.Checkpoint.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, ctx, ctx.campaign, checkpoint);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static int getInt(Object target, String name) throws Exception {
        return (int) getField(target, name);
    }

    private static boolean getBoolean(Object target, String name) throws Exception {
        return (boolean) getField(target, name);
    }

    private static String getString(Object target, String name) throws Exception {
        Object value = getField(target, name);
        return value == null ? "" : value.toString();
    }

    private static void setBoolean(Object target, String name, boolean value) throws Exception {
        setField(target, name, value);
    }

    private static void setString(Object target, String name, String value) throws Exception {
        setField(target, name, value);
    }
}
