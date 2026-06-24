import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignOvermapEncounterFlowTest {

    @Test
    void campaignStartsDirectlyInStrategicOvermapLayer() {
        GameContext ctx = initializedCampaignContext();

        assertTrue(CampaignSystem.isStrategicGalaxyMapMode(ctx));
        assertTrue(ctx.ui.mapOpen);
        assertEquals(GameState.MAP, ctx.state);
        assertEquals(1, ctx.ships.size());
        assertTrue(ctx.campaign.strategicOvermapMode);
    }

    @Test
    void campaignClockSlowsForLocationMenusAndStopsForEncounterDecisions() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.introSequenceActive = false;
        st.sectorElapsed = 0.0;

        double start = st.sectorElapsed;
        CampaignSystem.update(ctx, 10.0);
        assertEquals(start + 10.0, st.sectorElapsed, 0.001);
        assertEquals(1.0, CampaignSystem.campaignTimeScale(ctx), 0.001);

        ctx.ui.showCampaignHubMenu("test-hub", "REPAIR");
        CampaignSystem.update(ctx, 10.0);
        assertEquals(start + 11.0, st.sectorElapsed, 0.001);
        assertEquals(0.10, CampaignSystem.campaignTimeScale(ctx), 0.001);

        ctx.ui.clearCampaignHubMenu();
        ctx.ui.showGalaxySearchGroupEncounterPrompt(1, "CONTACT", "", "", "");
        CampaignSystem.update(ctx, 10.0);
        assertEquals(start + 11.0, st.sectorElapsed, 0.001);
        assertEquals(0.0, CampaignSystem.campaignTimeScale(ctx), 0.001);
    }

    @Test
    void manualEncounterCommitLatchesUntilStrategicOvermapReturns() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object group = firstSearchGroup(st);
        assertNotNull(group);
        setDouble(group, "x", st.playerGalaxyX);
        setDouble(group, "y", st.playerGalaxyY);
        invokeDetectionUpdate(ctx, st, 0.1);

        assertTrue(CampaignSystem.takeCommandOfPendingStrategicEncounter(ctx));
        assertTrue(st.manualEncounterCommitInProgress);
        assertFalse(st.strategicOvermapMode);
        assertFalse(CampaignSystem.autoResolvePendingStrategicEncounter(ctx));
    }

    @Test
    void staleSecondPromptDuringManualEncounterDismissesInsteadOfLockingInput() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.manualEncounterCommitInProgress = true;
        st.strategicOvermapMode = false;
        ctx.ui.showStrategicEncounterPrompt(99, "CONTACT: RED PATROL GROUP", "", "", "");
        ctx.state = GameState.PAUSED;

        assertTrue(CampaignSystem.autoResolvePendingStrategicEncounter(ctx));
        assertFalse(ctx.ui.strategicEncounterPrompt.active);
        assertEquals(GameState.RUNNING, ctx.state);
        assertTrue(st.manualEncounterCommitInProgress);
    }

    @Test
    void tacticalManualEntryAutoJoinsSecondStrategicTaskForce() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object taskForce = firstStrategicTaskForce(ctx, st);
        assertNotNull(taskForce);
        st.manualEncounterCommitInProgress = true;
        st.strategicOvermapMode = false;
        setInt(taskForce, "currentSubzone", 0);

        Method method = CampaignSystem.class.getDeclaredMethod(
                "updateStrategicTaskForceEncounter",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                taskForce.getClass(),
                int.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st, taskForce, 0);

        assertFalse(ctx.ui.strategicEncounterPrompt.active);
        assertTrue(getBoolean(taskForce, "encounterSpawned"));
        assertTrue(ctx.eventBanner.contains("NEW ENEMY TASK FORCE HAS ARRIVED"));
    }

    @Test
    void changingEncounterPromptKindsClearsPriorIdentifiers() {
        GameContext ctx = initializedCampaignContext();
        UiState ui = ctx.ui;
        ui.showStrategicEncounterPrompt(7, "TASK FORCE", "", "", "");
        ui.showCampaignForceEncounterPrompt(11, "CAMPAIGN FORCE", "", "", "");

        assertEquals(-1, ui.strategicEncounterPrompt.taskForceId);
        assertEquals(11, ui.strategicEncounterPrompt.campaignForceId);
        assertTrue(CampaignSystem.hasPendingStrategicEncounterChoice(ctx));

        ui.showCampaignBattleInterventionPrompt(13, "BATTLE", "", "", "");
        assertEquals(-1, ui.strategicEncounterPrompt.campaignForceId);
        assertEquals(13, ui.strategicEncounterPrompt.campaignBattleId);
    }

    @Test
    void hostileSearchGroupInterceptionUsesDedicatedPromptAndAutoResolveReturnsToOvermap() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;

        Object group = firstSearchGroup(st);
        assertNotNull(group);
        setDouble(group, "x", st.playerGalaxyX);
        setDouble(group, "y", st.playerGalaxyY);

        invokeDetectionUpdate(ctx, st, 0.1);

        assertTrue(ctx.ui.strategicEncounterPrompt.active);
        assertEquals(UiState.StrategicEncounterPrompt.Kind.GALAXY_SEARCH_GROUP, ctx.ui.strategicEncounterPrompt.kind);
        assertEquals(getInt(group, "id"), ctx.ui.strategicEncounterPrompt.galaxySearchGroupId);

        assertTrue(CampaignSystem.autoResolvePendingStrategicEncounter(ctx));

        assertTrue(st.strategicOvermapMode);
        assertFalse(ctx.ui.strategicEncounterPrompt.active);
        assertTrue(ctx.ui.mapOpen);
        assertEquals(0, st.activeGalaxyEncounterSearchGroupId);
        assertFalse(st.galaxyEncounterActive);
        assertEquals("RETURNING", getObject(group, "behavior").toString());
    }

    @Test
    void hostileSearchGroupInterceptionInOpenSpaceStillCreatesEncounterPrompt() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object group = firstSearchGroup(st);
        assertNotNull(group);

        st.selectedGalaxyLocationId = "";
        st.playerGalaxyX = 2500.0;
        st.playerGalaxyY = 2500.0;
        setDouble(group, "x", 2500.0);
        setDouble(group, "y", 2500.0);

        invokeDetectionUpdate(ctx, st, 0.1);

        assertTrue(ctx.ui.strategicEncounterPrompt.active);
        assertEquals(UiState.StrategicEncounterPrompt.Kind.GALAXY_SEARCH_GROUP, ctx.ui.strategicEncounterPrompt.kind);
        assertEquals("Open-space intercept", ctx.ui.strategicEncounterPrompt.location);
        assertTrue(ctx.ui.strategicEncounterPrompt.body.toLowerCase().contains("route intercept"));
        assertTrue(ctx.ui.strategicEncounterPrompt.body.toLowerCase().contains("compact three-zone"));
    }

    @Test
    void pointOfInterestDefenseContactFoldsIntoMissionPromptInsteadOfOpenSpaceClash() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation mission = firstCombatMission(ctx);
        assertNotNull(mission);
        Object group = firstSearchGroup(st);
        assertNotNull(group);

        st.playerGalaxyX = mission.x;
        st.playerGalaxyY = mission.y;
        st.selectedGalaxyLocationId = mission.id;
        setDouble(group, "x", mission.x);
        setDouble(group, "y", mission.y);
        setObject(group, "anchorLocationId", mission.id);

        invokeDetectionUpdate(ctx, st, 0.1);

        assertTrue(ctx.ui.strategicEncounterPrompt.active);
        assertEquals(UiState.StrategicEncounterPrompt.Kind.CAMPAIGN_LOCATION, ctx.ui.strategicEncounterPrompt.kind);
        assertEquals(mission.id, ctx.ui.strategicEncounterPrompt.campaignLocationId);
        assertTrue(ctx.ui.strategicEncounterPrompt.body.toLowerCase().contains("site assault"));
    }

    @Test
    void openSpaceFleetClashExposesThreeOwnedTacticalZones() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object group = firstSearchGroup(st);
        assertNotNull(group);

        assertTrue(launchGalaxySearchGroupEncounter(ctx, st, group));

        assertTrue(CampaignSystem.activeObjectiveMarkers(ctx).stream()
                .anyMatch(marker -> marker.label.equals("Allied Spawn Zone")
                        && marker.faction == Faction.ALLY
                        && marker.subtitle.toLowerCase().contains("left zone")));
        assertTrue(CampaignSystem.activeObjectiveMarkers(ctx).stream()
                .anyMatch(marker -> marker.label.equals("Neutral Transit Zone")
                        && marker.faction == null
                        && marker.subtitle.toLowerCase().contains("middle zone")));
        assertTrue(CampaignSystem.activeObjectiveMarkers(ctx).stream()
                .anyMatch(marker -> marker.label.equals("Hostile Contact Zone")
                        && marker.faction == Faction.ENEMY
                        && marker.subtitle.toLowerCase().contains("right zone")));
        CampaignSystem.CampaignObjectiveMarker allied = CampaignSystem.activeObjectiveMarkers(ctx).stream()
                .filter(marker -> marker.label.equals("Allied Spawn Zone"))
                .findFirst()
                .orElse(null);
        CampaignSystem.CampaignObjectiveMarker neutral = CampaignSystem.activeObjectiveMarkers(ctx).stream()
                .filter(marker -> marker.label.equals("Neutral Transit Zone"))
                .findFirst()
                .orElse(null);
        CampaignSystem.CampaignObjectiveMarker hostile = CampaignSystem.activeObjectiveMarkers(ctx).stream()
                .filter(marker -> marker.label.equals("Hostile Contact Zone"))
                .findFirst()
                .orElse(null);
        assertNotNull(allied);
        assertNotNull(neutral);
        assertNotNull(hostile);
        assertTrue(neutral.x - allied.x < 1200.0, "open-space allied and neutral lanes should not span the whole tactical map");
        assertTrue(hostile.x - neutral.x < 1200.0, "open-space neutral and hostile lanes should stay in the same battle pocket");
        assertTrue(st.objectivePhaseLabel.toLowerCase().contains("left allied"));
        assertTrue(st.threatStateLabel.toLowerCase().contains("no authored mission blockers"));
    }

    @Test
    void enemyActivityArrivalUsesTheSameStrategicEncounterPipeline() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation threat = findAreaOfInterest(ctx, "aoi-threat-1");

        assertNotNull(threat);
        st.selectedGalaxyLocationId = threat.id;
        st.playerGalaxyX = threat.x;
        st.playerGalaxyY = threat.y;

        assertTrue(CampaignSystem.startTravelToSelectedLocation(ctx));
        assertTrue(ctx.ui.strategicEncounterPrompt.active);
        assertEquals(UiState.StrategicEncounterPrompt.Kind.GALAXY_SEARCH_GROUP, ctx.ui.strategicEncounterPrompt.kind);

        assertTrue(CampaignSystem.autoResolvePendingStrategicEncounter(ctx));
        assertTrue(st.strategicOvermapMode);
        assertFalse(st.galaxyEncounterActive);
        assertEquals(0, st.activeGalaxyEncounterSearchGroupId);
    }

    private static GameContext initializedCampaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        ctx.campaign.sectorElapsed = 240.0;
        return ctx;
    }

    private static void invokeDetectionUpdate(GameContext ctx, CampaignSystem.CampaignState st, double dt) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "updateGalaxyDetectionAndInterception",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                double.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st, dt);
    }

    private static boolean launchGalaxySearchGroupEncounter(GameContext ctx, CampaignSystem.CampaignState st, Object group) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "launchGalaxySearchGroupEncounter",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                group.getClass()
        );
        method.setAccessible(true);
        return (boolean) method.invoke(null, ctx, st, group);
    }

    private static Object firstSearchGroup(CampaignSystem.CampaignState st) throws Exception {
        Field field = CampaignSystem.CampaignState.class.getDeclaredField("galaxySearchGroups");
        field.setAccessible(true);
        List<?> groups = (List<?>) field.get(st);
        for (Object group : groups) {
            if (group != null && getBoolean(group, "hostile")) return group;
        }
        return groups.isEmpty() ? null : groups.get(0);
    }

    private static Object firstStrategicTaskForce(GameContext ctx, CampaignSystem.CampaignState st) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "initializeStrategicTaskForces",
                GameContext.class,
                CampaignSystem.CampaignState.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st);
        Field field = CampaignSystem.CampaignState.class.getDeclaredField("strategicTaskForces");
        field.setAccessible(true);
        List<?> taskForces = (List<?>) field.get(st);
        return taskForces.isEmpty() ? null : taskForces.get(0);
    }

    private static CampaignSystem.CampaignLocation findAreaOfInterest(GameContext ctx, String id) {
        for (CampaignSystem.CampaignLocation location : CampaignSystem.campaignAreasOfInterest(ctx)) {
            if (location != null && id.equals(location.id)) return location;
        }
        return null;
    }

    private static CampaignSystem.CampaignLocation firstCombatMission(GameContext ctx) {
        for (CampaignSystem.CampaignLocation location : CampaignSystem.mainCampaignLocations(ctx)) {
            if (location != null && location.primaryMission && "poi-08".equals(location.id)) return location;
        }
        return null;
    }

    private static void setDouble(Object target, String fieldName, double value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setDouble(target, value);
    }

    private static int getInt(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static void setInt(Object target, String fieldName, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static Object getObject(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static boolean getBoolean(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static void setObject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
