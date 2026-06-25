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
    void sectorTwoTimerExpiryDoesNotAutoResolveWhenConvoyQuotaSurvives() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        startSector(ctx, 2);

        CampaignSystem.CampaignState st = ctx.campaign;
        st.sectorElapsed = st.sectorTimeLimit - 0.05;
        st.objectiveAssetLosses = 1;

        CampaignSystem.update(ctx, 0.10);

        assertFalse(ctx.gameOver, "objective timers should not defeat the player at T-0");
        assertFalse(CampaignSystem.isSectorObjectiveSecured(ctx),
                "objective timers should not auto-secure the sector at T-0");
        assertTrue(CampaignSystem.canStartSafeMissionExit(ctx),
                "player should still be able to leave at their own pace after the old timer expires");
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
    void genericTimerExpiryDoesNotFailUnfinishedObjective() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        startSector(ctx, 10);

        CampaignSystem.CampaignState st = ctx.campaign;
        st.sectorElapsed = st.sectorTimeLimit - 0.05;
        st.objectiveProgress = 0.0;

        CampaignSystem.update(ctx, 0.10);

        assertFalse(ctx.gameOver, "non-extraction sectors should not fail on unresolved objective timer expiry");
        assertFalse(CampaignSystem.isSectorObjectiveSecured(ctx),
                "timer expiry should leave the objective active instead of resolving it");
    }

    @Test
    void safeMissionExitCanRetreatDuringFirstTenSeconds() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        startSector(ctx, 2);

        CampaignSystem.CampaignState st = ctx.campaign;
        st.sectorElapsed = CampaignSystem.safeMissionExitEntryWindowSeconds() - 0.1;
        assertTrue(CampaignSystem.canStartSafeMissionExit(ctx));

        ctx.command.safeMissionExitPending = true;
        int completedBefore = st.completedMainMissions;
        st.sectorElapsed += 7.5;

        assertTrue(CampaignSystem.completeSafeMissionExit(ctx));
        assertTrue(CampaignSystem.isStrategicOvermapMode(ctx));
        assertEquals(completedBefore, st.completedMainMissions,
                "early safe exit should retreat without awarding mission completion");
    }

    @Test
    void runtimeCampaignSafeExitReturnsToOvermapInsteadOfMenuReadyFlag() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        startSector(ctx, 2);

        CampaignSystem.CampaignState st = ctx.campaign;
        st.sectorElapsed = CampaignSystem.safeMissionExitEntryWindowSeconds() - 0.1;
        ctx.command.safeMissionExitPending = true;

        GameSimulationRuntime runtime = new GameSimulationRuntime(ctx);
        Method completeSafeMissionExit = GameSimulationRuntime.class.getDeclaredMethod("completeSafeMissionExit", Ship.class);
        completeSafeMissionExit.setAccessible(true);
        completeSafeMissionExit.invoke(runtime, ctx.player);

        assertTrue(CampaignSystem.isStrategicOvermapMode(ctx));
        assertFalse(ctx.command.safeMissionExitReady,
                "campaign safe exit should stay on the strategic overmap instead of asking GamePanel to return to menu");
    }

    @Test
    void nonCampaignSafeExitRequestsMenuFallback() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.SHOWCASE, 5000, 5000, true, 1234L, false));
        SpawnSystem.initWorld(ctx);

        assertTrue(GameplayActions.trySafeMissionExit(ctx));
        assertTrue(ctx.command.safeMissionExitReady,
                "showcase/test safe exit should use the menu fallback route");
    }

    @Test
    void safeMissionExitStaysAvailableAfterFormerTenSecondWindow() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        startSector(ctx, 2);

        CampaignSystem.CampaignState st = ctx.campaign;
        st.sectorElapsed = CampaignSystem.safeMissionExitEntryWindowSeconds() + 0.1;

        assertTrue(CampaignSystem.canStartSafeMissionExit(ctx));
        assertEquals("WITHDRAW TO STRATEGIC MAP READY", CampaignSystem.extractionReadinessBanner(ctx));
        assertTrue(CampaignSystem.completeSafeMissionExit(ctx));
        assertTrue(CampaignSystem.isStrategicOvermapMode(ctx));
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
