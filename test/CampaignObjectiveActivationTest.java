import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignObjectiveActivationTest {

    @Test
    void futureMissionPocketDoesNotBecomeObjectiveAnchorUntilArrival() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        startSector(ctx, 2);

        CampaignSystem.CampaignState st = ctx.campaign;
        st.activeMissionSection = 1;
        st.missionSectionTravelLocked = true;

        double lockedAnchorX = invokeObjectiveAnchorX(ctx, st);
        double lockedAnchorY = invokeObjectiveAnchorY(ctx, st);
        Object previousSection = st.missionSections.get(0);
        Object futureSection = st.missionSections.get(1);

        assertEquals(getDoubleField(previousSection, "x"), lockedAnchorX);
        assertEquals(getDoubleField(previousSection, "y"), lockedAnchorY);
        assertFalse(nearlyEquals(getDoubleField(futureSection, "x"), lockedAnchorX)
                && nearlyEquals(getDoubleField(futureSection, "y"), lockedAnchorY),
                "locked transit should not retarget the future pocket before arrival");

        st.missionSectionTravelLocked = false;
        double unlockedAnchorX = invokeObjectiveAnchorX(ctx, st);
        double unlockedAnchorY = invokeObjectiveAnchorY(ctx, st);
        assertEquals(getDoubleField(futureSection, "x"), unlockedAnchorX);
        assertEquals(getDoubleField(futureSection, "y"), unlockedAnchorY);
    }

    @Test
    void objectiveAssetFailureIsDeferredWhileTransitToNextPocketIsLocked() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        startSector(ctx, 2);

        CampaignSystem.CampaignState st = ctx.campaign;
        st.sectorElapsed = 10.0;
        st.activeMissionSection = 1;
        st.missionSectionTravelLocked = true;
        st.objectiveAssetLosses = Math.max(st.objectiveAssetLosses, 2);

        CampaignSystem.update(ctx, 0.0);
        assertFalse(ctx.gameOver, "future-pocket loss conditions should stay dormant until the player arrives");

        st.missionSectionTravelLocked = false;
        CampaignSystem.update(ctx, 0.0);
        assertTrue(ctx.gameOver, "once the pocket is active again, the loss condition should resolve normally");
    }

    private static double invokeObjectiveAnchorX(GameContext ctx, CampaignSystem.CampaignState st) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "objectiveAnchorX", GameContext.class, CampaignSystem.CampaignState.class);
        method.setAccessible(true);
        return (double) method.invoke(null, ctx, st);
    }

    private static double invokeObjectiveAnchorY(GameContext ctx, CampaignSystem.CampaignState st) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "objectiveAnchorY", GameContext.class, CampaignSystem.CampaignState.class);
        method.setAccessible(true);
        return (double) method.invoke(null, ctx, st);
    }

    private static void startSector(GameContext ctx, int sector) throws Exception {
        Method startSector = CampaignSystem.class.getDeclaredMethod("startSector", GameContext.class, int.class);
        startSector.setAccessible(true);
        startSector.invoke(null, ctx, sector);
    }

    private static double getDoubleField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getDouble(target);
    }

    private static boolean nearlyEquals(double a, double b) {
        return Math.abs(a - b) < 0.0001;
    }
}
