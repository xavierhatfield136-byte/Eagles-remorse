import app.config.GameConfig;
import app.config.GameMode;
import app.persistence.CampaignCheckpointStore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CampaignFleetBuildingIntegrationTest {
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
