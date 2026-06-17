import app.config.GameConfig;
import app.config.GameMode;
import app.persistence.CampaignCheckpointStore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignTheaterConquestStateTest {

    @Test
    void theaterConquestStateInitializesAndPersistsThroughCheckpoint() throws Exception {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        assertNotNull(st);

        assertEquals(4, st.campaignTheaters.size(), "expected four hard strategic theaters");
        assertFalse(st.strategicNodes.isEmpty(), "expected strategic nodes seeded from campaign locations");

        Object firstTheater = st.campaignTheaters.get(0);
        setDouble(firstTheater, "controlScore", 73.5);
        setDouble(firstTheater, "supplyState", 68.0);
        setDouble(firstTheater, "threatPressure", 41.0);
        setDouble(firstTheater, "greenInfluence", 77.0);
        setDouble(firstTheater, "yellowInfluence", 18.0);
        setDouble(firstTheater, "redInfluence", 5.0);
        setObject(firstTheater, "controlState", enumConstant(fieldType(firstTheater, "controlState"), "BLUE_GREEN_CONTROLLED"));

        Object firstNode = st.strategicNodes.get(0);
        setObject(firstNode, "owner", enumConstant(fieldType(firstNode, "owner"), "BLUE_GREEN"));
        setDouble(firstNode, "contestProgress", 44.0);

        st.theaterWarTickIndex = 12;
        st.theaterWarTickAccumulatorSec = 1.75;
        st.theaterWarRecentEvents.clear();
        st.theaterWarRecentEvents.add("Southern Theater is now Blue/Green controlled");
        st.theaterWarRecentEvents.add("Red Corridor Breakpoint captured by Blue/Green");

        CampaignCheckpointStore.Checkpoint checkpoint = captureCheckpoint(ctx, 7);

        GameContext restored = initializedCampaignContext();
        assertTrue(applyCheckpoint(restored, checkpoint));
        CampaignSystem.CampaignState restoredState = restored.campaign;
        assertNotNull(restoredState);
        assertEquals(4, restoredState.campaignTheaters.size());
        assertFalse(restoredState.strategicNodes.isEmpty());
        assertEquals(12, restoredState.theaterWarTickIndex);
        assertEquals(1.75, restoredState.theaterWarTickAccumulatorSec, 1e-9);
        assertTrue(restoredState.theaterWarRecentEvents.size() >= 2);
        assertEquals("Southern Theater is now Blue/Green controlled", restoredState.theaterWarRecentEvents.get(0));

        Object restoredTheater = restoredState.campaignTheaters.get(0);
        assertTrue(getDouble(restoredTheater, "controlScore") > 0.0);
        assertTrue(getDouble(restoredTheater, "supplyState") >= 0.0);
        assertTrue(getDouble(restoredTheater, "threatPressure") >= 0.0);
        assertEquals(77.0, getDouble(restoredTheater, "greenInfluence"), 0.001);
        assertEquals(18.0, getDouble(restoredTheater, "yellowInfluence"), 0.001);
        assertEquals(5.0, getDouble(restoredTheater, "redInfluence"), 0.001);
        assertNotNull(getObject(restoredTheater, "controlState"));
        Object restoredNode = restoredState.strategicNodes.get(0);
        assertEquals("BLUE_GREEN", getObject(restoredNode, "owner").toString());
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

    private static Class<?> fieldType(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getType();
    }

    @SuppressWarnings("unchecked")
    private static Object enumConstant(Class<?> enumType, String name) {
        return Enum.valueOf((Class<? extends Enum>) enumType.asSubclass(Enum.class), name);
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
