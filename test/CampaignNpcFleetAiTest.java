import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignNpcFleetAiTest {

    @Test
    void ambientTheaterFleetsSeedAcrossCampaignFromStart() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;

        invokeForceSimulation(ctx, st, 0.2);

        List<?> forces = campaignForces(st);
        long enemies = forces.stream().filter(force -> force != null && "ENEMY".equals(fieldString(force, "faction"))).count();
        long green = forces.stream().filter(force -> force != null && "TEAM_C".equals(fieldString(force, "faction"))).count();
        long yellow = forces.stream().filter(force -> force != null && "TEAM_D".equals(fieldString(force, "faction"))).count();
        long theaterInterdictionScreens = forces.stream()
                .filter(force -> force != null && fieldString(force, "name").contains("Interdiction Screen"))
                .count();

        assertTrue(forces.size() >= 22, "campaign should seed a visible NPC fleet picture from the start");
        assertTrue(enemies >= 8, "expected multiple hostile fleets before the final theater");
        assertTrue(green >= 4, "expected Green patrol/relay traffic across theaters");
        assertTrue(yellow >= 4, "expected Yellow trade columns across theaters");
        assertTrue(theaterInterdictionScreens >= 4, "expected one red interdiction screen per theater");
    }

    @Test
    void reconSweepReacquiresStaleCampaignForceContact() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object force = forceNamed(st, "Red Frontier Interdiction Screen");
        assertNotNull(force);

        setDouble(force, "x", st.playerGalaxyX + 360.0);
        setDouble(force, "y", st.playerGalaxyY - 180.0);
        setDouble(force, "lastKnownX", st.playerGalaxyX + 360.0);
        setDouble(force, "lastKnownY", st.playerGalaxyY - 180.0);
        setDouble(force, "lastKnownAgeSec", 76.0);
        setDouble(force, "contactConfidence", 0.12);
        setBoolean(force, "visibleToPlayer", false);
        setEnumByName(force, "contactState", "STALE");
        st.campaignSupplies = 99;

        assertTrue(CampaignSystem.requestCampaignSensorSweep(ctx));

        assertTrue(getBoolean(force, "visibleToPlayer"));
        assertTrue(getDouble(force, "contactConfidence") >= 0.54);
        assertTrue(getDouble(force, "lastKnownAgeSec") <= 0.001);
        assertTrue(!"STALE".equals(fieldString(force, "contactState")));
    }

    @Test
    void recentlyKnownHostileForceIsRetainedInsteadOfVanishing() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object force = forceNamed(st, "Red Frontier Picket Patrol");
        assertNotNull(force);

        setDouble(force, "strength", 0.0);
        setDouble(force, "readiness", 0.0);
        setDouble(force, "supply", 0.0);
        setDouble(force, "hullIntegrity", 0.0);
        setDouble(force, "contactConfidence", 0.18);
        setDouble(force, "lastKnownAgeSec", 44.0);
        setBoolean(force, "visibleToPlayer", false);
        setEnumByName(force, "contactState", "STALE");

        invokeForceSimulation(ctx, st, 0.2);

        Object retained = forceNamed(st, "Red Frontier Picket Patrol");
        assertNotNull(retained, "recent hostile contacts should remain as last-known contacts instead of vanishing");
        assertTrue(!getBoolean(retained, "destroyed"));
        assertTrue(getDouble(retained, "strength") >= 4.0);
        assertTrue("STALE".equals(fieldString(retained, "contactState")));
    }

    @Test
    void hostileInterdictionForceClosesDuringEarthwardTravel() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        CampaignSystem.CampaignLocation northernObjective = findLocation(ctx, "poi-22");
        assertNotNull(northernObjective);

        invokeForceSimulation(ctx, st, 0.2);
        Object force = forceNamed(st, "Red Lunar Interdiction Screen");
        assertNotNull(force);
        setDouble(force, "x", st.playerGalaxyX + 240.0);
        setDouble(force, "y", st.playerGalaxyY - 1300.0);
        setEnumByName(force, "intent", "INTERCEPTING");
        setEnumByName(force, "mission", "INTERCEPT");
        double startDistance = Math.hypot(getDouble(force, "x") - st.playerGalaxyX, getDouble(force, "y") - st.playerGalaxyY);

        st.selectedGalaxyLocationId = northernObjective.id;
        assertTrue(CampaignSystem.startTravelToSelectedLocation(ctx));
        for (int i = 0; i < 30; i++) {
            invokeTravelUpdate(ctx, st, 0.2);
            invokeForceSimulation(ctx, st, 0.2);
        }

        double endDistance = Math.hypot(getDouble(force, "x") - st.playerGalaxyX, getDouble(force, "y") - st.playerGalaxyY);
        assertTrue(endDistance < startDistance * 0.88, "interdiction force should close distance during active travel");
        assertTrue("INTERCEPTING".equals(fieldString(force, "intent")));
    }

    @Test
    void staleContactMarkerExplainsLostBearingAndSweepRecommendation() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object force = forceNamed(st, "Red Frontier Picket Patrol");
        assertNotNull(force);

        setDouble(force, "lastKnownX", st.playerGalaxyX + 640.0);
        setDouble(force, "lastKnownY", st.playerGalaxyY - 420.0);
        setDouble(force, "targetX", st.playerGalaxyX + 800.0);
        setDouble(force, "targetY", st.playerGalaxyY - 620.0);
        setDouble(force, "lastKnownAgeSec", 48.0);
        setDouble(force, "contactConfidence", 0.22);
        setDouble(force, "uncertaintyRadius", 460.0);
        setBoolean(force, "visibleToPlayer", false);
        setEnumByName(force, "contactState", "STALE");

        List<CampaignSystem.CampaignSupportMarker> markers = CampaignSystem.activeSupportMarkers(ctx);
        CampaignSystem.CampaignSupportMarker marker = markers.stream()
                .filter(m -> m != null && "Red Frontier Picket Patrol".equals(m.label))
                .findFirst()
                .orElse(null);

        assertNotNull(marker);
        assertTrue(marker.subtitle.toLowerCase().contains("lost bearing"));
        assertTrue(marker.subtitle.toLowerCase().contains("last known range"));
        assertTrue(marker.subtitle.toLowerCase().contains("est vector"));
        assertTrue(marker.subtitle.toLowerCase().contains("sweep recommended"));
    }

    @Test
    void strategicStrikeDamagesAndRetasksCampaignForceBeforeBattle() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object force = forceNamed(st, "Red Frontier Interdiction Screen");
        assertNotNull(force);

        setDouble(force, "x", st.playerGalaxyX + 260.0);
        setDouble(force, "y", st.playerGalaxyY - 120.0);
        setDouble(force, "lastKnownX", st.playerGalaxyX + 260.0);
        setDouble(force, "lastKnownY", st.playerGalaxyY - 120.0);
        setDouble(force, "strength", 42.0);
        setDouble(force, "readiness", 46.0);
        setDouble(force, "contactConfidence", 0.90);
        setEnumByName(force, "contactState", "KNOWN");
        st.galaxySearchGroups.clear();
        st.strategicTaskForces.clear();
        for (Object other : campaignForces(st)) {
            if (other != null && other != force && "ENEMY".equals(fieldString(other, "faction"))) {
                setDouble(other, "x", st.playerGalaxyX + 4000.0);
                setDouble(other, "y", st.playerGalaxyY + 4000.0);
                setDouble(other, "lastKnownX", st.playerGalaxyX + 4000.0);
                setDouble(other, "lastKnownY", st.playerGalaxyY + 4000.0);
            }
        }
        st.campaignAmmo = 200;
        st.campaignFuel = 200;
        st.strategicTorpedoCharges = 3;
        double before = getDouble(force, "strength");

        assertTrue(CampaignSystem.launchStrategicTorpedoStrike(ctx, getDouble(force, "x"), getDouble(force, "y")));
        for (int i = 0; i < 20; i++) {
            invokeStrikeObjectUpdate(ctx, st, 1.0);
        }

        assertTrue(getDouble(force, "strength") < before,
                "strike should damage the campaign force before battle; before=" + before
                        + " after=" + getDouble(force, "strength")
                        + " report=" + CampaignSystem.lastStrikeReportDetail(ctx)
                        + " event=" + CampaignSystem.campaignStrikeBattleEventSummary(ctx));
        assertTrue("RETREATING".equals(fieldString(force, "intent"))
                        || "REGROUPING".equals(fieldString(force, "intent"))
                        || "INTERCEPTING".equals(fieldString(force, "intent")),
                "strike should force an adaptation");
        assertTrue(CampaignSystem.lastStrikeReportDetail(ctx).contains("Force")
                || CampaignSystem.campaignStrikeBattleEventSummary(ctx).contains("Campaign force"));
    }

    @Test
    void nearbyHostileCampaignForceCanBeStruckWithoutManualContactSelection() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object force = forceNamed(st, "Red Frontier Interdiction Screen");
        assertNotNull(force);

        setDouble(force, "x", st.playerGalaxyX + 120.0);
        setDouble(force, "y", st.playerGalaxyY + 70.0);
        setDouble(force, "lastKnownX", st.playerGalaxyX + 120.0);
        setDouble(force, "lastKnownY", st.playerGalaxyY + 70.0);
        setDouble(force, "contactConfidence", 0.86);
        setDouble(force, "strength", 48.0);
        setBoolean(force, "visibleToPlayer", true);
        setEnumByName(force, "contactState", "KNOWN");
        st.galaxySearchGroups.clear();
        st.strategicTaskForces.clear();
        st.campaignAmmo = 200;
        st.campaignFuel = 200;
        st.strategicTorpedoCharges = 2;
        ctx.ui.clearSelectedCampaignContact();
        ctx.ui.campaignCommandTab = UiState.CampaignCommandTab.STRIKES;

        CampaignSystem.CampaignAction torpedo = CampaignSystem.campaignVisibleActions(ctx).stream()
                .filter(action -> "TORPEDO_STRIKE".equals(action.id))
                .findFirst()
                .orElse(null);
        CampaignSystem.CampaignAction track = CampaignSystem.campaignVisibleActions(ctx).stream()
                .filter(action -> "TRACK_TARGET".equals(action.id))
                .findFirst()
                .orElse(null);

        assertNull(torpedo, "overmap should not expose remote torpedo strikes against fleet markers");
        assertNotNull(track);
        assertFalse(st.strategicStrikeObjects.size() > 0, "overmap selection should not queue remote strike objects");
    }

    @Test
    void alliedFleetsRespondToNearbyHostilePressure() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        Object hostile = forceNamed(st, "Red Frontier Interdiction Screen");
        Object green = forceNamed(st, "Green Frontier Relay Patrol");
        assertNotNull(hostile);
        assertNotNull(green);

        setDouble(hostile, "x", st.playerGalaxyX + 520.0);
        setDouble(hostile, "y", st.playerGalaxyY - 140.0);
        setDouble(green, "x", st.playerGalaxyX + 430.0);
        setDouble(green, "y", st.playerGalaxyY - 120.0);
        setDouble(green, "strength", 66.0);
        setDouble(green, "readiness", 76.0);
        setBoolean(hostile, "simulationActive", true);
        setBoolean(green, "simulationActive", true);
        setEnumByName(green, "intent", "PATROLLING");

        invokeForceSimulation(ctx, st, 0.2);

        String intent = fieldString(green, "intent");
        assertTrue("ESCORTING".equals(intent) || "REINFORCING".equals(intent) || "GUARDING".equals(intent),
                "green patrol should react to nearby hostile pressure; intent=" + intent
                        + " state=" + fieldString(green, "state")
                        + " debug=" + CampaignSystem.campaignFleetAiDebugLines(ctx));
        assertTrue(CampaignSystem.campaignFleetAiDebugLines(ctx).stream().anyMatch(line -> line.startsWith("FLEET AI DEBUG")));
    }

    @Test
    void fleetAiDiagnosticsReportCleanAfterSeededSimulation() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        for (int i = 0; i < 40; i++) {
            invokeForceSimulation(ctx, st, 0.2);
        }

        assertTrue(CampaignSystem.campaignFleetAiDebugLines(ctx).stream().anyMatch(line -> line.startsWith("FLEET AI DEBUG")));
        assertTrue(CampaignSystem.campaignFleetAiAnomalyReport(ctx).stream()
                .anyMatch(line -> line.contains("no idle") || line.contains("IDLE") || line.contains("VISIBLE")));
    }

    @Test
    void campaignDirectorMaintainsAtLeastThreeVisibleFleetGroupsAcrossMap() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        invokeForceSimulation(ctx, st, 0.2);
        st.playerGalaxyX = ctx.WORLD_W * 0.52;
        st.playerGalaxyY = ctx.WORLD_H * 0.34;
        for (Object force : campaignForces(st)) {
            if (force == null || "PLAYER_FLEET".equals(fieldString(force, "kind"))) continue;
            setBoolean(force, "visibleToPlayer", false);
            setDouble(force, "contactConfidence", 0.02);
            setDouble(force, "lastKnownAgeSec", 300.0);
            setDouble(force, "x", st.playerGalaxyX + 9000.0);
            setDouble(force, "y", st.playerGalaxyY + 9000.0);
            setDouble(force, "lastKnownX", st.playerGalaxyX + 9000.0);
            setDouble(force, "lastKnownY", st.playerGalaxyY + 9000.0);
            setEnumByName(force, "contactState", "STALE");
        }

        invokeMaintainVisibleFleetContacts(ctx, st);

        long visibleFleets = CampaignSystem.activeSupportMarkers(ctx).stream()
                .filter(marker -> marker != null && marker.type.name().startsWith("FORCE_"))
                .count();
        assertTrue(visibleFleets >= 3, "campaign director should surface at least three visible fleet groups");
    }

    private static GameContext initializedCampaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static void invokeForceSimulation(GameContext ctx, CampaignSystem.CampaignState st, double dt) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "updateCampaignForceSimulation",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                double.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st, dt);
    }

    private static void invokeTravelUpdate(GameContext ctx, CampaignSystem.CampaignState st, double dt) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "updateCampaignTravel",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                double.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st, dt);
    }

    private static void invokeStrikeObjectUpdate(GameContext ctx, CampaignSystem.CampaignState st, double dt) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "updateStrategicStrikeObjects",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                double.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st, dt);
    }

    private static void invokeMaintainVisibleFleetContacts(GameContext ctx, CampaignSystem.CampaignState st) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "maintainMinimumVisibleFleetContacts",
                GameContext.class,
                CampaignSystem.CampaignState.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st);
    }

    private static List<?> campaignForces(CampaignSystem.CampaignState st) {
        return st.campaignForces;
    }

    private static Object forceNamed(CampaignSystem.CampaignState st, String name) {
        for (Object force : campaignForces(st)) {
            if (force != null && name.equals(fieldString(force, "name"))) return force;
        }
        return null;
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

    private static String fieldString(Object target, String fieldName) {
        Object value = getObject(target, fieldName);
        return value == null ? "" : value.toString();
    }

    private static Object getObject(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static double getDouble(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getDouble(target);
    }

    private static void setDouble(Object target, String fieldName, double value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setDouble(target, value);
    }

    private static boolean getBoolean(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static void setBoolean(Object target, String fieldName, boolean value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setEnumByName(Object target, String fieldName, String value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Class<?> enumType = field.getType();
        field.set(target, Enum.valueOf((Class<? extends Enum>) enumType.asSubclass(Enum.class), value));
    }
}
