import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CampaignSensorSuiteTest {

    @Test
    void focusedTrackBurnsThroughDecoysAndUpgradesTheLock() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object group = firstSearchGroup(st);
        assertNotNull(group);

        setDouble(group, "x", st.playerGalaxyX + 260.0);
        setDouble(group, "y", st.playerGalaxyY + 120.0);
        setDouble(group, "decoyRisk", 0.78);
        setDouble(group, "trackIntegrity", 26.0);
        setBoolean(group, "visible", true);
        setObject(group, "contactConfidence", enumConstant(findNestedEnum("GalaxyContactConfidence"), "POSSIBLE_PATROL"));

        CampaignSystem.selectCampaignContactTarget(ctx, "Unknown Contact", "", "", getDouble(group, "x"), getDouble(group, "y"), true, true);
        assertTrue(CampaignSystem.requestCampaignFocusedTrack(ctx));

        assertTrue(getDouble(group, "decoyRisk") < 0.78, "focused track should burn down decoy clutter");
        assertTrue(getDouble(group, "trackIntegrity") > 26.0, "focused track should materially improve track integrity");
        assertEquals("TARGET_QUALITY", getObject(group, "intelQuality").toString());
    }

    @Test
    void passiveTrackDecayErodesOldContactsWithoutCoverage() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object group = firstSearchGroup(st);
        assertNotNull(group);

        setDouble(group, "x", 4700.0);
        setDouble(group, "y", 4700.0);
        st.playerGalaxyX = 300.0;
        st.playerGalaxyY = 400.0;
        setDouble(group, "trackIntegrity", 62.0);
        setBoolean(group, "visible", true);
        setObject(group, "contactConfidence", enumConstant(findNestedEnum("GalaxyContactConfidence"), "CONFIRMED_HOSTILE"));

        invokeGalaxyGroupUpdate(ctx, st, 12.0);

        assertTrue(getDouble(group, "trackIntegrity") < 62.0, "tracks should decay when nothing is holding the contact");
        assertTrue("LOST_CONTACT".equals(getObject(group, "contactConfidence").toString())
                        || "POSSIBLE_PATROL".equals(getObject(group, "contactConfidence").toString())
                        || "UNKNOWN_CONTACT".equals(getObject(group, "contactConfidence").toString()),
                "stale tracks should soften or drop instead of staying perfectly locked");
    }

    @Test
    void relayDronePlayerActionIsRemoved() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object group = firstSearchGroup(st);
        assertNotNull(group);

        st.playerGalaxyX = 250.0;
        st.playerGalaxyY = 250.0;
        setDouble(group, "x", 4300.0);
        setDouble(group, "y", 4200.0);
        setDouble(group, "trackIntegrity", 6.0);
        setBoolean(group, "visible", false);

        CampaignSystem.selectCampaignContactTarget(ctx, "Distant Return", "", "", getDouble(group, "x"), getDouble(group, "y"), true, true);
        assertFalse(CampaignSystem.requestCampaignDeployRelay(ctx));
        invokeGalaxyGroupUpdate(ctx, st, 1.0);

        List<?> relayNodes = relayNodes(st);
        assertTrue(relayNodes.isEmpty(), "removed player relay action should not create sensor nodes");
        assertFalse(getBoolean(group, "visible"), "removed player relay action should not boost remote tracking");
    }

    @Test
    void scoutSurgePlayerActionIsRemoved() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object group = firstSearchGroup(st);
        assertNotNull(group);

        setDouble(group, "x", st.playerGalaxyX + 900.0);
        setDouble(group, "y", st.playerGalaxyY + 500.0);
        setDouble(group, "trackIntegrity", 14.0);
        CampaignSystem.selectCampaignContactTarget(ctx, "Scout Return", "", "", getDouble(group, "x"), getDouble(group, "y"), true, true);

        assertFalse(CampaignSystem.requestCampaignScoutSurge(ctx));

        List<?> relayNodes = relayNodes(st);
        assertTrue(relayNodes.isEmpty(), "removed scout surge action should not create a scout relay node");
        assertEquals(0.0, getDouble(group, "scoutPressureSec"), 1e-9,
                "removed scout surge action should not apply temporary sensor pressure");
    }

    @Test
    void selectedHostileSubtitleReportsSignatureClassification() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        Object group = firstSearchGroup(st);
        assertNotNull(group);

        setDouble(group, "x", st.playerGalaxyX + 180.0);
        setDouble(group, "y", st.playerGalaxyY + 110.0);
        setBoolean(group, "visible", true);
        setDouble(group, "engineSignature", 0.15);
        setDouble(group, "commsSignature", 0.22);
        setDouble(group, "massSignature", 0.88);
        setDouble(group, "heatSignature", 0.18);
        setDouble(group, "trackIntegrity", 70.0);
        setObject(group, "contactConfidence", enumConstant(findNestedEnum("GalaxyContactConfidence"), "CONFIRMED_HOSTILE"));

        CampaignSystem.selectCampaignContactTarget(ctx, "Weighted Return", "", "", getDouble(group, "x"), getDouble(group, "y"), true, true);

        assertTrue(ctx.ui.selectedCampaignContactSubtitle.toUpperCase().contains("MASS SHADOW"),
                "sensor UI should explain what kind of signature is driving the current track");
    }

    private static GameContext initializedCampaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        CampaignSystem.isStrategicGalaxyMapMode(ctx);
        return ctx;
    }

    private static void invokeGalaxyGroupUpdate(GameContext ctx, CampaignSystem.CampaignState st, double dt) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "updateGalaxySearchGroups",
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

    private static List<?> relayNodes(CampaignSystem.CampaignState st) throws Exception {
        Field field = CampaignSystem.CampaignState.class.getDeclaredField("sensorRelayNodes");
        field.setAccessible(true);
        return (List<?>) field.get(st);
    }

    private static Class<?> findNestedEnum(String simpleName) throws Exception {
        return Class.forName("CampaignSystemModels$" + simpleName);
    }

    private static Object enumConstant(Class<?> type, String name) {
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object value = Enum.valueOf((Class<? extends Enum>) type.asSubclass(Enum.class), name);
        return value;
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

    private static void setBoolean(Object target, String fieldName, boolean value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setBoolean(target, value);
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

    private static Object getObject(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
