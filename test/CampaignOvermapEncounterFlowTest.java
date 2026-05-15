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

    private static Object firstSearchGroup(CampaignSystem.CampaignState st) throws Exception {
        Field field = CampaignSystem.CampaignState.class.getDeclaredField("galaxySearchGroups");
        field.setAccessible(true);
        List<?> groups = (List<?>) field.get(st);
        return groups.isEmpty() ? null : groups.get(0);
    }

    private static CampaignSystem.CampaignLocation findAreaOfInterest(GameContext ctx, String id) {
        for (CampaignSystem.CampaignLocation location : CampaignSystem.campaignAreasOfInterest(ctx)) {
            if (location != null && id.equals(location.id)) return location;
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

    private static Object getObject(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
