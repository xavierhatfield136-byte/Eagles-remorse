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
        setObject(mission, "intelQuality", enumConstant(findNestedEnum("ContactIntelQuality"), "TRACKED"));
        setObject(mission, "chainType", enumConstant(findNestedEnum("DiscoveryChainType"), "RELAY_ECHO"));
        setIntField(mission, "chainStage", 2);
        mission.scarNote = "Mission scar note";
        mission.routeNote = "Mission route note";
        mission.recurringContactId = "VOSS";
        mission.recurringContactStatus = "trusted route handler";
        mission.supportRouteStabilized = true;
        mission.unresolvedAgeSec = 88.0;
        mission.escalationStage = 1;
        aoi.discovered = false;
        aoi.consumed = true;
        setObject(aoi, "intelQuality", enumConstant(findNestedEnum("ContactIntelQuality"), "CLASSIFIED"));
        setObject(aoi, "chainType", enumConstant(findNestedEnum("DiscoveryChainType"), "SMUGGLER_LEAD"));
        setIntField(aoi, "chainStage", 1);
        aoi.scarNote = "AOI scar note";
        aoi.unresolvedAgeSec = 112.0;
        aoi.escalationStage = 2;

        Object group = firstSearchGroup(st);
        assertNotNull(group);
        st.activeGalaxyEncounterSearchGroupId = getInt(group, "id");
        st.selectedFleetPostureId = "RECON_SWEEP";
        st.selectedSiteResolutionModeId = "MARK_FOR_ALLIES";
        st.activeSiteResolutionModeId = "QUIET_DECODE";
        st.fleetStrain = 57.5;
        st.vossRelationshipStateId = "TRUSTED";
        st.marrRelationshipStateId = "OWED_FAVOR";
        st.rookRelationshipStateId = "HOSTILE";
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
        setObject(group, "intelQuality", enumConstant(findNestedEnum("ContactIntelQuality"), "TARGET_QUALITY"));
        setObject(group, "doctrine", enumConstant(fieldType(group, "doctrine"), "PUNISHMENT_FLEET"));
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
        assertEquals("RECON_SWEEP", restoredState.selectedFleetPostureId);
        assertEquals("MARK_FOR_ALLIES", restoredState.selectedSiteResolutionModeId);
        assertEquals("QUIET_DECODE", restoredState.activeSiteResolutionModeId);
        assertEquals(57.5, restoredState.fleetStrain, 1e-9);
        assertEquals("TRUSTED", restoredState.vossRelationshipStateId);
        assertEquals("OWED_FAVOR", restoredState.marrRelationshipStateId);
        assertEquals("HOSTILE", restoredState.rookRelationshipStateId);
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
        assertEquals("TRACKED", getObject(restoredMission, "intelQuality").toString());
        assertEquals("RELAY_ECHO", getObject(restoredMission, "chainType").toString());
        assertEquals(2, getInt(restoredMission, "chainStage"));
        assertEquals("Mission scar note", restoredMission.scarNote);
        assertEquals("Mission route note", restoredMission.routeNote);
        assertEquals("VOSS", restoredMission.recurringContactId);
        assertEquals("trusted route handler", restoredMission.recurringContactStatus);
        assertTrue(restoredMission.supportRouteStabilized);
        assertEquals(88.0, restoredMission.unresolvedAgeSec, 1e-9);
        assertEquals(1, restoredMission.escalationStage);
        assertFalse(restoredAoi.discovered);
        assertTrue(restoredAoi.consumed);
        assertEquals("CLASSIFIED", getObject(restoredAoi, "intelQuality").toString());
        assertEquals("SMUGGLER_LEAD", getObject(restoredAoi, "chainType").toString());
        assertEquals(1, getInt(restoredAoi, "chainStage"));
        assertEquals("AOI scar note", restoredAoi.scarNote);
        assertEquals(112.0, restoredAoi.unresolvedAgeSec, 1e-9);
        assertEquals(2, restoredAoi.escalationStage);

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
        assertEquals("TARGET_QUALITY", getObject(restoredGroup, "intelQuality").toString());
        assertEquals("PUNISHMENT_FLEET", getObject(restoredGroup, "doctrine").toString());
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
        st.selectedFleetPostureId = "RESCUE_PRIORITY";
        st.fleetStrain = 33.0;
        st.vossRelationshipStateId = "HELPED";
        st.marrRelationshipStateId = "TRUSTED";
        st.rookRelationshipStateId = "HOSTILE";
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
        assertEquals("RESCUE_PRIORITY", loaded.selectedFleetPostureId);
        assertEquals(33.0, loaded.fleetStrain, 1e-9);
        assertEquals("HELPED", loaded.vossRelationshipStateId);
        assertEquals("TRUSTED", loaded.marrRelationshipStateId);
        assertEquals("HOSTILE", loaded.rookRelationshipStateId);
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

    @Test
    void checkpointPreservesDetachedStrategicDivisionOrders() throws Exception {
        GameContext ctx = initializedCampaignContext();
        startSector(ctx, 10);

        assertTrue(CampaignSystem.createDetachedStrategicDivision(ctx));
        int groupId = ctx.ui.selectedStrategicDivisionGroupId;
        assertTrue(groupId > 0);

        int targetSubzone = CampaignSystem.missionSubzoneIndex(5, 2);
        double targetX = CampaignSystem.missionSubzoneCenterX(ctx, ctx.campaign.sector, targetSubzone);
        double targetY = CampaignSystem.missionSubzoneCenterY(ctx, ctx.campaign.sector, targetSubzone);
        assertTrue(CampaignSystem.issueStrategicDivisionOrder(ctx, targetX, targetY));

        CampaignCheckpointStore.Checkpoint checkpoint = captureCheckpoint(ctx, 11);
        assertFalse(checkpoint.strategicDivisions.isBlank());

        GameContext restored = initializedCampaignContext();
        assertTrue(applyCheckpoint(restored, checkpoint));

        Object division = strategicDivision(restored.campaign, groupId);
        assertNotNull(division);
        assertEquals(targetSubzone, getInt(division, "targetSubzone"));
        assertTrue(getDouble(division, "transitRemainingSec") > 0.0);
        assertEquals(targetX, getDouble(division, "lastOrderX"), 1e-6);
        assertEquals(targetY, getDouble(division, "lastOrderY"), 1e-6);
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

    private static void startSector(GameContext ctx, int sector) throws Exception {
        Method startSector = CampaignSystem.class.getDeclaredMethod("startSector", GameContext.class, int.class);
        startSector.setAccessible(true);
        startSector.invoke(null, ctx, sector);
    }

    private static Object strategicDivision(CampaignSystem.CampaignState st, int groupId) throws Exception {
        java.lang.reflect.Field field = CampaignSystem.CampaignState.class.getDeclaredField("strategicDivisions");
        field.setAccessible(true);
        java.util.Map<?, ?> divisions = (java.util.Map<?, ?>) field.get(st);
        return divisions.get(groupId);
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

    private static Class<?> findNestedEnum(String simpleName) {
        for (Class<?> nested : CampaignSystem.class.getDeclaredClasses()) {
            if (nested != null && simpleName.equals(nested.getSimpleName())) {
                return nested;
            }
        }
        throw new AssertionError("Missing nested enum: " + simpleName);
    }

    private static void setDouble(Object target, String fieldName, double value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setDouble(target, value);
    }

    private static void setIntField(Object target, String fieldName, int value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(target, value);
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
