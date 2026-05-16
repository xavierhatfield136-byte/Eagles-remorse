import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignObjectiveActivationTest {

    @Test
    void activeMissionPressureAnchorCanAdvanceImmediatelyAcrossUnifiedBattlespace() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        startSector(ctx, 2);

        CampaignSystem.CampaignState st = ctx.campaign;
        st.activeMissionSection = 1;

        Object futureSection = st.missionSections.get(1);
        double anchorX = invokeObjectiveAnchorX(ctx, st);
        double anchorY = invokeObjectiveAnchorY(ctx, st);

        assertEquals(getDoubleField(futureSection, "x"), anchorX);
        assertEquals(getDoubleField(futureSection, "y"), anchorY);
    }

    @Test
    void objectiveAssetFailureResolvesWithoutPocketTravelDeferral() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        startSector(ctx, 2);

        CampaignSystem.CampaignState st = ctx.campaign;
        st.sectorElapsed = 10.0;
        st.activeMissionSection = 1;
        st.objectiveAssetLosses = Math.max(st.objectiveAssetLosses, 2);

        CampaignSystem.update(ctx, 0.0);
        assertTrue(ctx.gameOver, "convoy quota failures should resolve immediately now that campaign missions are one continuous area");
    }

    @Test
    void destroyObjectiveCanCompleteOnTheSameTickAsSectorTimeout() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        startSector(ctx, 2);

        CampaignSystem.CampaignState st = ctx.campaign;
        st.sectorElapsed = st.sectorTimeLimit - 0.05;
        st.kills = (int) Math.ceil(st.objectiveGoal);

        CampaignSystem.update(ctx, 0.10);

        assertFalse(ctx.gameOver, "a completed destroy objective should beat a same-tick timeout fail");
        assertTrue(CampaignSystem.isSectorObjectiveSecured(ctx),
                "same-tick completion should secure the sector instead of failing it");
        assertTrue(CampaignSystem.canExtractFromCurrentSector(ctx),
                "once the timer is already spent, extraction should be immediately available after the objective secures");
    }

    @Test
    void sectorTwoTimeoutSucceedsWhenConvoyQuotaSurvives() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        startSector(ctx, 2);

        CampaignSystem.CampaignState st = ctx.campaign;
        st.sectorElapsed = st.sectorTimeLimit - 0.05;
        st.objectiveAssetLosses = 1;

        CampaignSystem.update(ctx, 0.10);

        assertFalse(ctx.gameOver, "sector 2 should resolve as a success if the convoy quota survives to extraction");
        assertTrue(CampaignSystem.isSectorObjectiveSecured(ctx),
                "convoy extraction at timeout should secure the sector and open the post-objective window");
    }

    @Test
    void sectorTwoFailureCallsOutConvoyQuotaBreak() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        startSector(ctx, 2);

        CampaignSystem.CampaignState st = ctx.campaign;
        st.objectiveAssetLosses = 2;

        CampaignSystem.update(ctx, 0.10);

        assertTrue(ctx.gameOver, "dropping below the sector 2 convoy quota should still fail the mission");
        assertEquals("DEFEAT: CONVOYS BELOW SAFE COUNT", ctx.gameOverText);
    }

    @Test
    void genericTimeoutFailureCallsOutUnfinishedObjective() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        startSector(ctx, 10);

        CampaignSystem.CampaignState st = ctx.campaign;
        st.sectorElapsed = st.sectorTimeLimit - 0.05;
        st.objectiveProgress = 0.0;

        CampaignSystem.update(ctx, 0.10);

        assertTrue(ctx.gameOver, "non-extraction sectors should still fail on unresolved timeout");
        assertEquals("DEFEAT: T-0 BEFORE OBJECTIVE COMPLETE", ctx.gameOverText);
    }

    @Test
    void sectorStartEmitsMissionBanterDuringLivePlay() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        startSector(ctx, 2);

        CampaignSystem.update(ctx, 1.1);

        AudioSystem.VoiceTelemetrySnapshot telemetry = AudioSystem.voiceTelemetry(ctx);
        Map<String, Integer> byEvent = telemetry.dispatchByEvent();
        assertTrue(byEvent.getOrDefault("scripted.mission_destroy_start", 0) > 0,
                "campaign sectors should trigger mission banter once combat starts");
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
