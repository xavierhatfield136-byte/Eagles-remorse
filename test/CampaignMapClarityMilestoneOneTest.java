import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignMapClarityMilestoneOneTest {

    @Test
    void markerGenerationIsReadOnlyForLinkedCampaignForce() throws Exception {
        GameContext ctx = initializedCampaignContext();
        invokeForceSimulation(ctx, 0.2);
        Object force = firstLinkedForce(ctx.campaign);
        assertNotNull(force);

        setDouble(force, "x", getDouble(force, "x") + 137.0);
        setDouble(force, "y", getDouble(force, "y") - 91.0);
        setDouble(force, "targetX", getDouble(force, "targetX") + 53.0);
        setDouble(force, "targetY", getDouble(force, "targetY") + 47.0);
        ForceSnapshot before = snapshot(force);

        CampaignSystem.activeSupportMarkers(ctx);

        assertEquals(before, snapshot(force), "building map markers must not mutate campaign-force truth");
    }

    @Test
    void linkedSearchGroupProducesOneLiveMarkerAndOnePositionAuthority() throws Exception {
        GameContext ctx = initializedCampaignContext();
        invokeForceSimulation(ctx, 0.2);
        Object force = firstLinkedForce(ctx.campaign);
        assertNotNull(force);
        Object group = searchGroup(ctx.campaign, getInt(force, "linkedSearchGroupId"));
        assertNotNull(group);

        setDouble(force, "x", getDouble(group, "x"));
        setDouble(force, "y", getDouble(group, "y"));
        setBoolean(force, "visibleToPlayer", true);
        setDouble(force, "contactConfidence", 0.9);
        setEnum(force, "contactState", "KNOWN");
        setBoolean(group, "visible", true);
        setDouble(group, "trackIntegrity", 80.0);
        setEnum(group, "contactConfidence", "CONFIRMED_HOSTILE");
        setEnum(group, "intelQuality", "TRACKED");
        ctx.campaign.playerGalaxyX = getDouble(group, "x");
        ctx.campaign.playerGalaxyY = getDouble(group, "y");
        CampaignSystem.recordCampaignFleetIntelObservation(ctx, getInt(force, "id"),
                CampaignSystem.CampaignIntelObservationSource.PLAYER_SENSOR,
                CampaignSystem.CampaignIntelPrecision.EXACT,
                ctx.campaign.campaignIntelTick, ctx.campaign.campaignIntelTick,
                0.9, getDouble(group, "x"), getDouble(group, "y"), 0.0);

        CampaignSystem.CampaignSupportMarker rawGroupMarker = rawSearchGroupMarker(group);
        assertNotNull(rawGroupMarker);
        String forceName = fieldString(force, "name");
        List<CampaignSystem.CampaignSupportMarker> visible = CampaignSystem.activeSupportMarkers(ctx);
        long forceMarkers = visible.stream().filter(marker -> marker != null && forceName.equals(marker.label)).count();
        long groupMarkers = visible.stream().filter(marker -> marker != null && rawGroupMarker.label.equals(marker.label)).count();
        assertEquals(1, forceMarkers);
        assertEquals(forceName.equals(rawGroupMarker.label) ? 1 : 0, groupMarkers,
                "the linked search-group projection must be suppressed in favor of its campaign force");

        for (int i = 0; i < 4; i++) {
            invokeForceSimulation(ctx, 0.2);
            assertEquals(getDouble(group, "x"), getDouble(force, "x"), 1e-9);
            assertEquals(getDouble(group, "y"), getDouble(force, "y"), 1e-9);
        }
    }

    @Test
    void normalRoutesAreHiddenAndExplicitRouteOverlayIsSelectedSiteScoped() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignLocation selected = CampaignSystem.mainCampaignLocations(ctx).get(1);
        ctx.campaign.selectedGalaxyLocationId = selected.id;
        ctx.campaign.selectedStrategicOverlayId = "CONTROL";

        assertTrue(CampaignSystem.campaignRouteSegmentsForDisplay(ctx).isEmpty(),
                "normal control view must not expose the full route graph");

        ctx.campaign.selectedStrategicOverlayId = "ROUTES";
        List<CampaignSystem.CampaignRouteSegment> scoped = CampaignSystem.campaignRouteSegmentsForDisplay(ctx);
        assertFalse(scoped.isEmpty());
        assertTrue(scoped.stream().allMatch(segment -> selected.id.equals(segment.fromLocationId)
                        || selected.id.equals(segment.toLocationId)),
                "explicit route view must contain only edges connected to the selected site");
    }

    @Test
    void hiddenRealFleetPersistsButDoesNotRender() throws Exception {
        GameContext ctx = initializedCampaignContext();
        invokeForceSimulation(ctx, 0.2);
        Object force = firstUnlinkedEnemyForce(ctx.campaign);
        assertNotNull(force);
        int id = getInt(force, "id");
        setDouble(force, "x", ctx.campaign.playerGalaxyX + 4200.0);
        setDouble(force, "y", ctx.campaign.playerGalaxyY + 4200.0);
        setDouble(force, "lastKnownX", getDouble(force, "x"));
        setDouble(force, "lastKnownY", getDouble(force, "y"));
        setDouble(force, "lastKnownAgeSec", 90.0);
        setDouble(force, "contactConfidence", 0.18);
        setBoolean(force, "visibleToPlayer", false);
        setEnum(force, "contactState", "STALE");

        List<CampaignSystem.CampaignSupportMarker> markers = CampaignSystem.activeSupportMarkers(ctx);

        assertTrue(forces(ctx.campaign).stream().anyMatch(candidate -> candidate != null
                && getIntUnchecked(candidate, "id") == id));
        assertTrue(markers.stream().noneMatch(marker -> marker != null
                && Math.hypot(marker.x - getDoubleUnchecked(force, "lastKnownX"),
                marker.y - getDoubleUnchecked(force, "lastKnownY")) <= 1.0));
    }

    @Test
    void territoryHaloOutsideMarkerRadiusIsNotASiteHitbox() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignLocation site = CampaignSystem.mainCampaignLocations(ctx).get(0);
        Rectangle mapRect = new Rectangle(0, 0, 900, 700);
        double hitRadius = UISystem.campaignSiteHitRadiusWorld(ctx, mapRect);

        assertSame(site, UISystem.campaignLocationAtMapClick(ctx, site.x, site.y, mapRect));
        assertTrue(hitRadius < 260.0);
        assertNull(UISystem.campaignLocationAtMapClick(ctx, site.x + hitRadius + 8.0, site.y, mapRect),
                "clicking the control halo away from the glyph must be a free-space click");
    }

    @Test
    void campaignMapSidebarCopyStaysShortAndPlayerFacing() {
        GameContext ctx = initializedCampaignContext();

        CampaignMapPresentationModel.SidebarContent nav = CampaignMapPresentationModel.sidebar(ctx);
        assertTrue(nav.primaryLines().size() <= 4);
        assertTrue(nav.secondaryLines().size() <= 5);

        ctx.ui.campaignCommandTab = UiState.CampaignCommandTab.FLEET;
        CampaignMapPresentationModel.SidebarContent fleet = CampaignMapPresentationModel.sidebar(ctx);
        assertTrue(fleet.primaryLines().size() <= 4);
        assertTrue(fleet.secondaryLines().size() <= 4);

        ctx.ui.campaignCommandTab = UiState.CampaignCommandTab.RESOURCES;
        CampaignMapPresentationModel.SidebarContent resources = CampaignMapPresentationModel.sidebar(ctx);
        assertEquals(UiState.CampaignCommandTab.NAV, resources.tab());
        assertTrue(resources.primaryLines().size() <= 4);
        assertTrue(resources.secondaryLines().size() <= 5);

        String combined = String.join("\n", nav.primaryLines()) + "\n"
                + String.join("\n", nav.secondaryLines()) + "\n"
                + String.join("\n", CampaignSystem.campaignActionPreviewLines(ctx));
        assertFalse(combined.contains("Primary Objective:"));
        assertFalse(combined.contains("Secondary Objective:"));
        assertFalse(combined.contains("Failure Risk:"));
        assertFalse(combined.contains("Region Operation Brief:"));
        assertFalse(combined.contains("Sensor Net:"));
    }

    @Test
    void overworldSidebarHoverShowsFullDetailsBehindSimplifiedCopy() {
        GameContext ctx = initializedCampaignContext();
        ctx.ui.mapOpen = true;
        ctx.state = GameState.MAP;
        ctx.campaign.strategicOvermapMode = true;
        CampaignSystem.CampaignLocation site = CampaignSystem.mainCampaignLocations(ctx).get(0);
        ctx.campaign.selectedGalaxyLocationId = site.id;

        Rectangle panel = Renderer.getStrategicMapSidebarRect(1280, 720, true);
        Renderer.HoverTooltip tooltip = Renderer.hoverTooltipAt(
                ctx,
                1280,
                720,
                panel.x + panel.width / 2,
                panel.y + 96);

        assertNotNull(tooltip);
        assertTrue(tooltip.title.contains("Details"));
        assertTrue(tooltip.body.contains("Facility:"));
        assertTrue(tooltip.body.contains("Defense:"));
        assertFalse(tooltip.body.contains("..."));
    }

    private static GameContext initializedCampaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static void invokeForceSimulation(GameContext ctx, double dt) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "updateCampaignForceSimulation", GameContext.class, CampaignSystem.CampaignState.class, double.class);
        method.setAccessible(true);
        method.invoke(null, ctx, ctx.campaign, dt);
    }

    private static CampaignSystem.CampaignSupportMarker rawSearchGroupMarker(Object group) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod("supportMarkerForGalaxySearchGroup", group.getClass());
        method.setAccessible(true);
        return (CampaignSystem.CampaignSupportMarker) method.invoke(null, group);
    }

    private static Object firstLinkedForce(CampaignSystem.CampaignState state) {
        return forces(state).stream()
                .filter(force -> force != null && getIntUnchecked(force, "linkedSearchGroupId") > 0)
                .findFirst()
                .orElse(null);
    }

    private static Object firstUnlinkedEnemyForce(CampaignSystem.CampaignState state) {
        return forces(state).stream()
                .filter(force -> force != null
                        && "ENEMY".equals(fieldString(force, "faction"))
                        && getIntUnchecked(force, "linkedSearchGroupId") <= 0)
                .findFirst()
                .orElse(null);
    }

    private static Object searchGroup(CampaignSystem.CampaignState state, int id) {
        return searchGroups(state).stream()
                .filter(group -> group != null && getIntUnchecked(group, "id") == id)
                .findFirst()
                .orElse(null);
    }

    private static List<?> forces(CampaignSystem.CampaignState state) {
        return state.campaignForces;
    }

    private static List<?> searchGroups(CampaignSystem.CampaignState state) {
        return state.galaxySearchGroups;
    }

    private static ForceSnapshot snapshot(Object force) throws Exception {
        List<String> route = new ArrayList<>();
        for (Object point : (List<?>) getObject(force, "routePoints")) {
            double[] xy = (double[]) point;
            route.add(xy[0] + ":" + xy[1]);
        }
        return new ForceSnapshot(
                getDouble(force, "x"), getDouble(force, "y"),
                getDouble(force, "targetX"), getDouble(force, "targetY"),
                route, fieldString(force, "intent"), fieldString(force, "state"),
                fieldString(force, "mission"), getBoolean(force, "destroyed"));
    }

    private record ForceSnapshot(double x, double y, double targetX, double targetY,
                                 List<String> route, String intent, String state,
                                 String mission, boolean destroyed) {}

    private static Object getObject(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private static String fieldString(Object target, String fieldName) {
        Object value = getObject(target, fieldName);
        return value == null ? "" : value.toString();
    }

    private static double getDouble(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getDouble(target);
    }

    private static double getDoubleUnchecked(Object target, String fieldName) {
        try {
            return getDouble(target, fieldName);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private static int getInt(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static int getIntUnchecked(Object target, String fieldName) {
        try {
            return getInt(target, fieldName);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private static boolean getBoolean(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static void setDouble(Object target, String fieldName, double value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setDouble(target, value);
    }

    private static void setBoolean(Object target, String fieldName, boolean value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setEnum(Object target, String fieldName, String value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Class<? extends Enum> enumType = (Class<? extends Enum>) field.getType();
        field.set(target, Enum.valueOf(enumType, value));
    }
}
