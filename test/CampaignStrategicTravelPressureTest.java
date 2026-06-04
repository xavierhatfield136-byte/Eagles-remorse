import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignStrategicTravelPressureTest {

    @Test
    void routeAssessmentMakesNorthernRoutesRiskierAndSlower() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;

        CampaignSystem.CampaignLocation southernHub = findLocation(ctx, "poi-02");
        CampaignSystem.CampaignLocation northernObjective = findLocation(ctx, "poi-22");
        assertNotNull(southernHub);
        assertNotNull(northernObjective);

        st.selectedGalaxyLocationId = southernHub.id;
        CampaignSystem.startTravelToSelectedLocation(ctx);
        double southRisk = st.galaxyTravel.interceptionRisk;
        double southDuration = st.galaxyTravel.durationSec;
        CampaignSystem.stopCampaignTravel(ctx);

        st.selectedGalaxyLocationId = northernObjective.id;
        CampaignSystem.startTravelToSelectedLocation(ctx);
        double northRisk = st.galaxyTravel.interceptionRisk;
        double northDuration = st.galaxyTravel.durationSec;

        assertTrue(northRisk > southRisk, "northern route should be riskier than southern hub route");
        assertTrue(northDuration > southDuration, "northern route should take longer than nearby southern route");

        List<String> routeLines = CampaignSystem.selectedRouteAssessmentLines(ctx);
        assertTrue(routeLines.stream().anyMatch(line -> line.contains("Risk " + Math.round(northRisk) + "%")));
    }

    @Test
    void directEarthwardBurnSeedsVisibleRouteInterdictionForStrikeUse() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation northernObjective = findLocation(ctx, "poi-22");
        assertNotNull(northernObjective);

        double fromX = st.playerGalaxyX;
        double fromY = st.playerGalaxyY;
        st.selectedGalaxyLocationId = northernObjective.id;

        assertTrue(CampaignSystem.startTravelToSelectedLocation(ctx));

        Object laneContact = visibleHostileLaneContact(st, fromX, fromY, northernObjective.x, northernObjective.y);
        assertNotNull(laneContact, "direct Earthward travel should expose an interdiction contact in the route lane");
        assertEquals("INTERCEPTING", getEnumName(laneContact, "behavior"));
        assertEquals("TRACKED", getEnumName(laneContact, "intelQuality"));
    }

    @Test
    void searchGroupConfidenceTransitionsFromPossibleToIdentifiedToLost() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object group = firstSearchGroup(st);
        assertNotNull(group);
        st.campaignIntelLevel = 0.0;
        st.enemyAlertLevel = 0.0;

        double playerX = st.playerGalaxyX;
        double playerY = st.playerGalaxyY;
        double detection = getDouble(group, "detectionRange");

        setDouble(group, "x", playerX + detection * 1.15);
        setDouble(group, "y", playerY);
        invokeSearchUpdate(ctx, st, 0.1);
        assertEquals("POSSIBLE_PATROL", getEnumName(group, "contactConfidence"));

        setDouble(group, "x", playerX + detection * 0.35);
        invokeSearchUpdate(ctx, st, 0.1);
        assertEquals("IDENTIFIED_TASK_FORCE", getEnumName(group, "contactConfidence"));

        setDouble(group, "x", playerX + detection * 6.0);
        setDouble(group, "targetX", playerX + detection * 6.0);
        setDouble(group, "targetY", playerY);
        setDouble(group, "stateTimer", 999.0);
        invokeSearchUpdate(ctx, st, 0.1);
        assertEquals("LOST_CONTACT", getEnumName(group, "contactConfidence"));

        invokeSearchUpdate(ctx, st, 12.0);
        invokeSearchUpdate(ctx, st, 0.1);
        assertEquals("UNKNOWN_CONTACT", getEnumName(group, "contactConfidence"));
    }

    private static GameContext initializedCampaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static void invokeSearchUpdate(GameContext ctx, CampaignSystem.CampaignState st, double dt) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "updateGalaxySearchGroups",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                double.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st, dt);
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
        Field field = CampaignSystem.CampaignState.class.getDeclaredField("galaxySearchGroups");
        field.setAccessible(true);
        List<?> groups = (List<?>) field.get(st);
        return groups.isEmpty() ? null : groups.get(0);
    }

    private static void setDouble(Object target, String fieldName, double value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setDouble(target, value);
    }

    private static double getDouble(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getDouble(target);
    }

    private static String getEnumName(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Object value = field.get(target);
        return (value == null) ? "" : value.toString();
    }

    private static Object visibleHostileLaneContact(CampaignSystem.CampaignState st,
                                                    double fromX,
                                                    double fromY,
                                                    double targetX,
                                                    double targetY) throws Exception {
        Field field = CampaignSystem.CampaignState.class.getDeclaredField("galaxySearchGroups");
        field.setAccessible(true);
        List<?> groups = (List<?>) field.get(st);
        for (Object group : groups) {
            if (group == null) continue;
            if (!getBoolean(group, "hostile") || !getBoolean(group, "visible")) continue;
            double x = getDouble(group, "x");
            double y = getDouble(group, "y");
            double lane = distancePointToSegment(x, y, fromX, fromY, targetX, targetY);
            if (lane <= 620.0) return group;
        }
        return null;
    }

    private static boolean getBoolean(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static double distancePointToSegment(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double len2 = dx * dx + dy * dy;
        if (len2 <= 1e-6) return Math.hypot(px - ax, py - ay);
        double t = Math.max(0.0, Math.min(1.0, ((px - ax) * dx + (py - ay) * dy) / len2));
        double sx = ax + dx * t;
        double sy = ay + dy * t;
        return Math.hypot(px - sx, py - sy);
    }
}
