import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CampaignTheaterConquestChecklistTest {

    @Test
    void theaterControlStateThresholdsMatchDesign() throws Exception {
        Object blue = invokePrivate(
                "theaterControlStateForScore",
                new Class[]{double.class},
                32.0
        );
        Object contested = invokePrivate(
                "theaterControlStateForScore",
                new Class[]{double.class},
                0.0
        );
        Object red = invokePrivate(
                "theaterControlStateForScore",
                new Class[]{double.class},
                -32.0
        );

        assertEquals("BLUE_GREEN_CONTROLLED", blue.toString());
        assertEquals("CONTESTED", contested.toString());
        assertEquals("RED_CONTROLLED", red.toString());
    }

    @Test
    void shipyardOwnershipAffectsTheaterSupplyState() throws Exception {
        GameContext ctx = initCampaign();
        CampaignSystem.CampaignState st = ctx.campaign;
        bootOvermap(ctx);

        Object shipyardNode = firstNodeByType(st, "SHIPYARD");
        assertNotNull(shipyardNode, "expected at least one shipyard strategic node");
        Object theaterId = getField(shipyardNode, "theaterId");

        setField(shipyardNode, "owner", enumConstant(fieldType(shipyardNode, "owner"), "RED"));
        invokePrivate("recomputeCampaignTheaterStates", new Class[]{CampaignSystem.CampaignState.class}, st);
        double redSupply = theaterSupplyState(st, theaterId);

        setField(shipyardNode, "owner", enumConstant(fieldType(shipyardNode, "owner"), "BLUE_GREEN"));
        invokePrivate("recomputeCampaignTheaterStates", new Class[]{CampaignSystem.CampaignState.class}, st);
        double blueSupply = theaterSupplyState(st, theaterId);

        assertTrue(blueSupply > redSupply, "friendly shipyard control should improve theater supply state");
    }

    @Test
    void lowIntegrityTaskForceTransitionsIntoRecoveryBehavior() throws Exception {
        GameContext ctx = initCampaign();
        CampaignSystem.CampaignState st = ctx.campaign;
        bootOvermap(ctx);

        Object force = firstNonPlayerCampaignForce(st);
        assertNotNull(force, "expected at least one autonomous campaign force");

        setDoubleField(force, "hullIntegrity", 20.0);
        setDoubleField(force, "readiness", 18.0);
        setDoubleField(force, "supply", 16.0);

        invokePrivate(
                "updateCampaignForceOrders",
                new Class[]{GameContext.class, CampaignSystem.CampaignState.class, force.getClass(), double.class},
                ctx, st, force, 1.0
        );
        Object intent = getField(force, "intent");
        assertNotNull(intent);
        String name = intent.toString();
        assertTrue(name.equals("REPAIRING") || name.equals("REGROUPING") || name.equals("RETREATING"));
    }

    @Test
    void strategicOperationsModifyControlAndConsumeBlueReserve() throws Exception {
        GameContext ctx = initCampaign();
        CampaignSystem.CampaignState st = ctx.campaign;
        bootOvermap(ctx);
        CampaignSystem.CampaignLocation current = CampaignSystem.currentCampaignLocation(ctx);
        assertNotNull(current);
        st.selectedGalaxyLocationId = current.id;
        st.dockedGalaxyLocationId = current.id;
        st.playerGalaxyX = current.x;
        st.playerGalaxyY = current.y;

        Object node = strategicNodeForLocation(st, current.id);
        assertNotNull(node);
        double beforeProgress = getDoubleField(node, "contestProgress");
        double beforeReserve = st.blueInterventionReserve;

        assertTrue(CampaignSystem.executeCampaignAction(ctx, "OP_COMMAND_STRIKE"));
        double afterProgress = getDoubleField(node, "contestProgress");
        double afterReserve = st.blueInterventionReserve;

        assertTrue(afterProgress > beforeProgress, "command strike should push control toward Blue/Green");
        assertTrue(afterReserve < beforeReserve, "blue reserve should be consumed by strategic operations");
    }

    @Test
    void earthGateUnlockDependsOnStabilizedTheaters() throws Exception {
        GameContext ctx = initCampaign();
        CampaignSystem.CampaignState st = ctx.campaign;
        bootOvermap(ctx);
        CampaignSystem.CampaignLocation earthGateMission = earthMission(ctx);
        assertNotNull(earthGateMission, "expected an Earth-phase mission node");
        st.selectedGalaxyLocationId = earthGateMission.id;
        st.playerGalaxyX = earthGateMission.x;
        st.playerGalaxyY = earthGateMission.y;
        st.dockedGalaxyLocationId = earthGateMission.id;

        boolean locked = (boolean) invokePrivate(
                "earthPhaseUnlocked",
                new Class[]{CampaignSystem.CampaignState.class},
                st
        );
        assertFalse(locked, "earth phase should stay locked by default");

        forceBlueControlOnFirstTheaters(st, 2);
        boolean unlocked = (boolean) invokePrivate(
                "earthPhaseUnlocked",
                new Class[]{CampaignSystem.CampaignState.class},
                st
        );
        assertTrue(unlocked, "earth phase should unlock after two stabilized theaters");
    }

    @Test
    void routeSanitizerRemovesInvalidForceRoutePointsAfterLoad() throws Exception {
        GameContext ctx = initCampaign();
        CampaignSystem.CampaignState st = ctx.campaign;
        bootOvermap(ctx);
        Object force = firstNonPlayerCampaignForce(st);
        assertNotNull(force);

        @SuppressWarnings("unchecked")
        List<double[]> route = (List<double[]>) getField(force, "routePoints");
        route.clear();
        route.add(new double[]{Double.NaN, 10.0});
        route.add(new double[]{ctx.WORLD_W + 5000.0, ctx.WORLD_H + 7000.0});

        invokePrivate("sanitizeCampaignForceRoutesAfterLoad",
                new Class[]{CampaignSystem.CampaignState.class, GameContext.class},
                st, ctx);

        @SuppressWarnings("unchecked")
        List<double[]> sanitized = (List<double[]>) getField(force, "routePoints");
        assertFalse(sanitized.isEmpty());
        for (double[] point : sanitized) {
            assertNotNull(point);
            assertEquals(2, point.length);
            assertTrue(Double.isFinite(point[0]) && Double.isFinite(point[1]));
            assertTrue(point[0] >= 0.0 && point[0] <= ctx.WORLD_W);
            assertTrue(point[1] >= 0.0 && point[1] <= ctx.WORLD_H);
        }
    }

    @Test
    void theaterWarLongRunStaysStableAndBounded() throws Exception {
        GameContext ctx = initCampaign();
        CampaignSystem.CampaignState st = ctx.campaign;
        bootOvermap(ctx);

        for (int i = 0; i < 420; i++) {
            invokePrivate("updateCampaignTheaterWar",
                    new Class[]{GameContext.class, CampaignSystem.CampaignState.class, double.class},
                    ctx, st, 1.0);
        }

        assertTrue(st.theaterWarRecentEvents.size() <= 16);
        for (Object theater : st.campaignTheaters) {
            assertTrue(getDoubleField(theater, "controlScore") >= -100.0);
            assertTrue(getDoubleField(theater, "controlScore") <= 100.0);
            assertTrue(getDoubleField(theater, "supplyState") >= 0.0);
            assertTrue(getDoubleField(theater, "supplyState") <= 100.0);
            assertTrue(getDoubleField(theater, "threatPressure") >= 0.0);
            assertTrue(getDoubleField(theater, "threatPressure") <= 100.0);
        }
    }

    @Test
    void theaterWarReplayIsDeterministicForFixedSeed() throws Exception {
        GameContext a = initCampaign();
        GameContext b = initCampaign();
        bootOvermap(a);
        bootOvermap(b);

        for (int i = 0; i < 240; i++) {
            invokePrivate("updateCampaignTheaterWar",
                    new Class[]{GameContext.class, CampaignSystem.CampaignState.class, double.class},
                    a, a.campaign, 1.0);
            invokePrivate("updateCampaignTheaterWar",
                    new Class[]{GameContext.class, CampaignSystem.CampaignState.class, double.class},
                    b, b.campaign, 1.0);
        }

        assertEquals(theaterSnapshot(a.campaign), theaterSnapshot(b.campaign));
        assertEquals(nodeSnapshot(a.campaign), nodeSnapshot(b.campaign));
    }

    @Test
    void highContactStrategicUpdateRemainsWithinReasonableBudget() throws Exception {
        GameContext ctx = initCampaign();
        CampaignSystem.CampaignState st = ctx.campaign;
        bootOvermap(ctx);

        long t0 = System.nanoTime();
        for (int i = 0; i < 180; i++) {
            invokePrivate("updateStrategicOvermapCampaign",
                    new Class[]{GameContext.class, CampaignSystem.CampaignState.class, double.class},
                    ctx, st, 0.5);
        }
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
        assertTrue(elapsedMs < 5000, "strategic overmap update loop regressed: " + elapsedMs + "ms");
    }

    private static String theaterSnapshot(CampaignSystem.CampaignState st) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (Object theater : st.campaignTheaters) {
            if (theater == null) continue;
            sb.append(getField(theater, "id")).append('|')
                    .append((int) Math.round(getDoubleField(theater, "controlScore"))).append('|')
                    .append((int) Math.round(getDoubleField(theater, "supplyState"))).append('|')
                    .append((int) Math.round(getDoubleField(theater, "threatPressure"))).append('|')
                    .append(getField(theater, "controlState")).append(';');
        }
        return sb.toString();
    }

    private static String nodeSnapshot(CampaignSystem.CampaignState st) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (Object node : st.strategicNodes) {
            if (node == null) continue;
            sb.append(getField(node, "locationId")).append('|')
                    .append(getField(node, "owner")).append('|')
                    .append((int) Math.round(getDoubleField(node, "contestProgress"))).append(';');
        }
        return sb.toString();
    }

    private static GameContext initCampaign() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 9876L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static void bootOvermap(GameContext ctx) {
        CampaignSystem.campaignSummarySidebarLines(ctx);
        assertNotNull(ctx.campaign);
    }

    private static CampaignSystem.CampaignLocation earthMission(GameContext ctx) {
        CampaignSystem.CampaignLocation best = null;
        for (CampaignSystem.CampaignLocation location : CampaignSystem.mainCampaignLocations(ctx)) {
            if (location == null || !location.primaryMission) continue;
            if (location.missionIndex >= 23) {
                best = location;
                break;
            }
        }
        return best;
    }

    private static void forceBlueControlOnFirstTheaters(CampaignSystem.CampaignState st, int count) throws Exception {
        int done = 0;
        for (Object theater : st.campaignTheaters) {
            if (theater == null) continue;
            setField(theater, "controlState", enumConstant(fieldType(theater, "controlState"), "BLUE_GREEN_CONTROLLED"));
            done++;
            if (done >= count) break;
        }
    }

    private static Object firstNodeByType(CampaignSystem.CampaignState st, String typeName) throws Exception {
        for (Object node : st.strategicNodes) {
            if (node == null) continue;
            Object type = getField(node, "type");
            if (type != null && typeName.equals(type.toString())) return node;
        }
        return null;
    }

    private static Object strategicNodeForLocation(CampaignSystem.CampaignState st, String locationId) throws Exception {
        for (Object node : st.strategicNodes) {
            if (node == null) continue;
            Object id = getField(node, "locationId");
            if (id != null && id.toString().equalsIgnoreCase(locationId)) return node;
        }
        return null;
    }

    private static double theaterSupplyState(CampaignSystem.CampaignState st, Object theaterId) throws Exception {
        for (Object theater : st.campaignTheaters) {
            if (theater == null) continue;
            Object id = getField(theater, "id");
            if (id != null && id.equals(theaterId)) {
                return getDoubleField(theater, "supplyState");
            }
        }
        return 0.0;
    }

    private static Object firstNonPlayerCampaignForce(CampaignSystem.CampaignState st) throws Exception {
        for (Object force : st.campaignForces) {
            if (force == null) continue;
            Object kind = getField(force, "kind");
            if (kind != null && !"PLAYER_FLEET".equals(kind.toString())) return force;
        }
        return null;
    }

    private static Object invokePrivate(String methodName, Class<?>[] sig, Object... args) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(methodName, sig);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    private static Class<?> fieldType(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getType();
    }

    @SuppressWarnings("unchecked")
    private static Object enumConstant(Class<?> enumType, String name) {
        return Enum.valueOf((Class<? extends Enum>) enumType.asSubclass(Enum.class), name);
    }

    private static Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static double getDoubleField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getDouble(target);
    }

    private static void setDoubleField(Object target, String fieldName, double value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setDouble(target, value);
    }
}
