import app.config.GameConfig;
import app.config.GameMode;
import app.persistence.CampaignCheckpointStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignOvermapCheckpointTest {

    @AfterEach
    void cleanupCheckpoint() {
        CampaignCheckpointStore.clear();
    }

    @Test
    void checkpointPreservesStrategicOvermapContinuity() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;

        st.strategicOvermapMode = true;
        st.currentGalaxyLocationId = "poi-05";
        st.selectedGalaxyLocationId = "poi-06";
        st.dockedGalaxyLocationId = "";
        st.completedMainMissions = 4;
        st.earthProgress = 4.0 / 24.0;
        st.enemyAlertLevel = 41.0;
        st.campaignFuel = 147;
        st.campaignSupplies = 103;
        st.campaignAmmo = 126;
        st.campaignSalvage = 52;
        st.playerGalaxyX = 8123.5;
        st.playerGalaxyY = 611.75;
        st.playerGalaxyHeadingDeg = -24.5;
        st.strategicTorpedoCharges = 3;
        st.strategicSortiesLaunched = 1;
        st.strategicAtomicCharges = 1;
        st.galaxyTravel.originId = "poi-05";
        st.galaxyTravel.destinationId = "poi-06";
        st.galaxyTravel.progress = 0.37;
        st.galaxyTravel.durationSec = 18.0;
        st.galaxyTravel.traveling = true;
        st.galaxyTravel.interceptionRisk = 44.0f;
        st.galaxyTravel.targetX = 9100.0;
        st.galaxyTravel.targetY = 540.0;
        st.galaxyTravel.speed = 188.0;

        CampaignSystem.CampaignLocation mission = findLocation(ctx, "poi-06");
        CampaignSystem.CampaignLocation aoi = findLocation(ctx, "aoi-cache-1");
        assertNotNull(mission);
        assertNotNull(aoi);
        mission.completed = true;
        mission.consumed = true;
        aoi.discovered = false;
        aoi.consumed = true;

        Object group = firstSearchGroup(st);
        assertNotNull(group);
        st.activeGalaxyEncounterSearchGroupId = getInt(group, "id");
        st.galaxyEncounterActive = true;
        setDouble(group, "x", 7777.0);
        setDouble(group, "y", 888.0);
        setDouble(group, "targetX", 7900.0);
        setDouble(group, "targetY", 1020.0);
        setDouble(group, "searchRadius", 555.0);
        setDouble(group, "stateTimer", 12.5);
        setDouble(group, "contactFadeSec", 7.5);
        setBoolean(group, "visible", true);
        setBoolean(group, "identified", true);
        setObject(group, "behavior", enumConstant(fieldType(group, "behavior"), "INTERCEPTING"));
        setObject(group, "contactConfidence", enumConstant(fieldType(group, "contactConfidence"), "IDENTIFIED_TASK_FORCE"));

        CampaignCheckpointStore.Checkpoint checkpoint = captureCheckpoint(ctx, 6);

        GameContext restored = initializedCampaignContext();
        assertTrue(applyCheckpoint(restored, checkpoint));

        CampaignSystem.CampaignState restoredState = restored.campaign;
        assertTrue(restoredState.strategicOvermapMode);
        assertEquals("poi-05", restoredState.currentGalaxyLocationId);
        assertEquals("poi-06", restoredState.selectedGalaxyLocationId);
        assertEquals("", restoredState.dockedGalaxyLocationId);
        assertEquals(getInt(group, "id"), restoredState.activeGalaxyEncounterSearchGroupId);
        assertEquals(4, restoredState.completedMainMissions);
        assertEquals(4.0 / 24.0, restoredState.earthProgress, 1e-9);
        assertEquals(41.0, restoredState.enemyAlertLevel, 1e-9);
        assertEquals(147, restoredState.campaignFuel);
        assertEquals(103, restoredState.campaignSupplies);
        assertEquals(126, restoredState.campaignAmmo);
        assertEquals(52, restoredState.campaignSalvage);
        assertEquals(8123.5, restoredState.playerGalaxyX, 1e-9);
        assertEquals(611.75, restoredState.playerGalaxyY, 1e-9);
        assertEquals(-24.5, restoredState.playerGalaxyHeadingDeg, 1e-9);
        assertEquals(3, restoredState.strategicTorpedoCharges);
        assertEquals(1, restoredState.strategicSortiesLaunched);
        assertEquals(1, restoredState.strategicAtomicCharges);
        assertTrue(restoredState.galaxyTravel.traveling);
        assertEquals("poi-05", restoredState.galaxyTravel.originId);
        assertEquals("poi-06", restoredState.galaxyTravel.destinationId);
        assertEquals(0.37, restoredState.galaxyTravel.progress, 1e-9);
        assertEquals(18.0, restoredState.galaxyTravel.durationSec, 1e-9);
        assertEquals(44.0, restoredState.galaxyTravel.interceptionRisk, 1e-9);
        assertEquals(9100.0, restoredState.galaxyTravel.targetX, 1e-9);
        assertEquals(540.0, restoredState.galaxyTravel.targetY, 1e-9);
        assertEquals(188.0, restoredState.galaxyTravel.speed, 1e-9);

        CampaignSystem.CampaignLocation restoredMission = findLocation(restored, "poi-06");
        CampaignSystem.CampaignLocation restoredAoi = findLocation(restored, "aoi-cache-1");
        assertNotNull(restoredMission);
        assertNotNull(restoredAoi);
        assertTrue(restoredMission.completed);
        assertTrue(restoredMission.consumed);
        assertFalse(restoredAoi.discovered);
        assertTrue(restoredAoi.consumed);

        Object restoredGroup = firstSearchGroup(restoredState);
        assertNotNull(restoredGroup);
        assertEquals(7777.0, getDouble(restoredGroup, "x"), 1e-9);
        assertEquals(888.0, getDouble(restoredGroup, "y"), 1e-9);
        assertEquals(7900.0, getDouble(restoredGroup, "targetX"), 1e-9);
        assertEquals(1020.0, getDouble(restoredGroup, "targetY"), 1e-9);
        assertEquals(555.0, getDouble(restoredGroup, "searchRadius"), 1e-9);
        assertEquals(12.5, getDouble(restoredGroup, "stateTimer"), 1e-9);
        assertEquals(7.5, getDouble(restoredGroup, "contactFadeSec"), 1e-9);
        assertTrue(getBoolean(restoredGroup, "visible"));
        assertTrue(getBoolean(restoredGroup, "identified"));
        assertEquals("INTERCEPTING", getObject(restoredGroup, "behavior").toString());
        assertEquals("IDENTIFIED_TASK_FORCE", getObject(restoredGroup, "contactConfidence").toString());
    }

    @Test
    void checkpointStoreRoundTripPreservesStrategicOvermapFields() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;

        st.strategicOvermapMode = true;
        st.currentGalaxyLocationId = "poi-05";
        st.selectedGalaxyLocationId = "poi-06";
        st.dockedGalaxyLocationId = "poi-05";
        st.completedMainMissions = 5;
        st.earthProgress = 5.0 / 24.0;
        st.enemyAlertLevel = 52.0;
        st.campaignFuel = 160;
        st.campaignSupplies = 118;
        st.campaignAmmo = 140;
        st.campaignSalvage = 61;
        st.playerGalaxyX = 7001.25;
        st.playerGalaxyY = 933.5;
        st.playerGalaxyHeadingDeg = -12.0;
        st.activeGalaxyEncounterSearchGroupId = 3;
        st.strategicTorpedoCharges = 4;
        st.strategicSortiesLaunched = 2;
        st.strategicAtomicCharges = 1;
        st.galaxyTravel.originId = "poi-05";
        st.galaxyTravel.destinationId = "poi-06";
        st.galaxyTravel.progress = 0.625;
        st.galaxyTravel.durationSec = 22.0;
        st.galaxyTravel.traveling = true;
        st.galaxyTravel.interceptionRisk = 47.0f;
        st.galaxyTravel.targetX = 8120.0;
        st.galaxyTravel.targetY = 840.0;
        st.galaxyTravel.speed = 205.0;

        CampaignCheckpointStore.Checkpoint checkpoint = captureCheckpoint(ctx, 6);
        CampaignCheckpointStore.save(checkpoint);
        CampaignCheckpointStore.Checkpoint loaded = CampaignCheckpointStore.load();

        assertNotNull(loaded);
        assertTrue(loaded.strategicOvermapMode);
        assertEquals("poi-05", loaded.currentGalaxyLocationId);
        assertEquals("poi-06", loaded.selectedGalaxyLocationId);
        assertEquals("poi-05", loaded.dockedGalaxyLocationId);
        assertEquals(5, loaded.completedMainMissions);
        assertEquals(5.0 / 24.0, loaded.earthProgress, 1e-9);
        assertEquals(52.0, loaded.enemyAlertLevel, 1e-9);
        assertEquals(160, loaded.campaignFuel);
        assertEquals(118, loaded.campaignSupplies);
        assertEquals(140, loaded.campaignAmmo);
        assertEquals(61, loaded.campaignSalvage);
        assertEquals(7001.25, loaded.playerGalaxyX, 1e-9);
        assertEquals(933.5, loaded.playerGalaxyY, 1e-9);
        assertEquals(-12.0, loaded.playerGalaxyHeadingDeg, 1e-9);
        assertEquals(3, loaded.activeGalaxyEncounterSearchGroupId);
        assertEquals(4, loaded.strategicTorpedoCharges);
        assertEquals(2, loaded.strategicSortiesLaunched);
        assertEquals(1, loaded.strategicAtomicCharges);
        assertTrue(loaded.galaxyTravelTraveling);
        assertEquals("poi-05", loaded.galaxyTravelOriginId);
        assertEquals("poi-06", loaded.galaxyTravelDestinationId);
        assertEquals(0.625, loaded.galaxyTravelProgress, 1e-9);
        assertEquals(22.0, loaded.galaxyTravelDurationSec, 1e-9);
        assertEquals(47.0, loaded.galaxyTravelInterceptionRisk, 1e-9);
        assertEquals(8120.0, loaded.galaxyTravelTargetX, 1e-9);
        assertEquals(840.0, loaded.galaxyTravelTargetY, 1e-9);
        assertEquals(205.0, loaded.galaxyTravelSpeed, 1e-9);
    }

    private static GameContext initializedCampaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static CampaignCheckpointStore.Checkpoint captureCheckpoint(GameContext ctx, int nextSector) throws Exception {
        Method captureCheckpoint = CampaignSystem.class.getDeclaredMethod(
                "captureCheckpoint",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                int.class
        );
        captureCheckpoint.setAccessible(true);
        return (CampaignCheckpointStore.Checkpoint) captureCheckpoint.invoke(null, ctx, ctx.campaign, nextSector);
    }

    private static boolean applyCheckpoint(GameContext ctx, CampaignCheckpointStore.Checkpoint checkpoint) throws Exception {
        Method applyCheckpoint = CampaignSystem.class.getDeclaredMethod(
                "applyCheckpoint",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                CampaignCheckpointStore.Checkpoint.class
        );
        applyCheckpoint.setAccessible(true);
        return (boolean) applyCheckpoint.invoke(null, ctx, ctx.campaign, checkpoint);
    }

    private static CampaignSystem.CampaignLocation findLocation(GameContext ctx, String id) {
        for (CampaignSystem.CampaignLocation location : CampaignSystem.mainCampaignLocations(ctx)) {
            if (location != null && id.equals(location.id)) return location;
        }
        for (CampaignSystem.CampaignLocation location : CampaignSystem.campaignAreasOfInterest(ctx)) {
            if (location != null && id.equals(location.id)) return location;
        }
        return null;
    }

    private static Object firstSearchGroup(CampaignSystem.CampaignState st) throws Exception {
        java.lang.reflect.Field field = CampaignSystem.CampaignState.class.getDeclaredField("galaxySearchGroups");
        field.setAccessible(true);
        java.util.List<?> groups = (java.util.List<?>) field.get(st);
        return groups.isEmpty() ? null : groups.get(0);
    }

    private static Class<?> fieldType(Object target, String fieldName) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getType();
    }

    @SuppressWarnings("unchecked")
    private static Object enumConstant(Class<?> enumType, String name) {
        return java.lang.Enum.valueOf((Class<? extends Enum>) enumType.asSubclass(Enum.class), name);
    }

    private static void setDouble(Object target, String fieldName, double value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setDouble(target, value);
    }

    private static double getDouble(Object target, String fieldName) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getDouble(target);
    }

    private static int getInt(Object target, String fieldName) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static void setBoolean(Object target, String fieldName, boolean value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }

    private static boolean getBoolean(Object target, String fieldName) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static void setObject(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getObject(Object target, String fieldName) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
