import app.config.GameConfig;
import app.config.GameMode;
import app.persistence.CampaignCheckpointStore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiOverlayInvariantTest {
    @Test
    void staleEncounterPromptClearsAndReleasesItsPausedState() {
        GameContext ctx = initializedCampaignContext();
        ctx.ui.showGalaxySearchGroupEncounterPrompt(999_999, "CONTACT", "BODY", "HERE", "WEAK");
        ctx.state = GameState.PAUSED;

        assertTrue(UISystem.auditAndRecoverOverlayState(ctx));

        assertFalse(ctx.ui.strategicEncounterPrompt.active);
        assertEquals(GameState.RUNNING, ctx.state);
        assertEquals(1, ctx.ui.overlayInvariantRepairCount);
    }

    @Test
    void manualPauseWithoutEncounterPromptRemainsPaused() {
        GameContext ctx = context();
        ctx.state = GameState.PAUSED;

        assertFalse(UISystem.auditAndRecoverOverlayState(ctx));

        assertEquals(GameState.PAUSED, ctx.state);
        assertEquals(0, ctx.ui.overlayInvariantRepairCount);
    }

    @Test
    void contradictoryPrimaryOverlaysCollapseToCurrentScreenOwner() {
        GameContext ctx = context();
        ctx.ui.shopOpen = true;
        ctx.ui.baseMenuOpen = true;
        ctx.ui.mapOpen = true;
        ctx.state = GameState.MAP;

        assertTrue(UISystem.auditAndRecoverOverlayState(ctx));

        assertTrue(ctx.ui.mapOpen);
        assertFalse(ctx.ui.shopOpen);
        assertFalse(ctx.ui.baseMenuOpen);
        assertEquals(1, ctx.ui.overlayInvariantRepairCount);
    }

    @Test
    void encounterPromptReplacesLowerPriorityCampaignModals() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignLocation location = ctx.campaign.galaxyMainPois.get(0);
        ctx.ui.showCampaignLocationEncounterPrompt(location.id, "CONTACT", "BODY", "HERE", "WEAK");
        ctx.ui.showCampaignHubMenu("dock", "service");
        ctx.ui.showCampaignActionConfirm("repair", "REPAIR", "BODY");

        assertTrue(UISystem.auditAndRecoverOverlayState(ctx));

        assertTrue(ctx.ui.strategicEncounterPrompt.active);
        assertFalse(ctx.ui.campaignHubMenu.active);
        assertFalse(ctx.ui.campaignActionConfirm.active);
        assertEquals(1, ctx.ui.overlayInvariantRepairCount);
    }

    @Test
    void backToBackContactsQueueEveryEncounterSourceBehindOneOwner() {
        UiState ui = context().ui;
        ui.showStrategicEncounterPrompt(1, "TASK", "", "", "");
        ui.showCampaignLocationEncounterPrompt("poi-01", "LOCATION", "", "", "");
        ui.showGalaxySearchGroupEncounterPrompt(2, "SEARCH", "", "", "");
        ui.showInstallationThreatEncounterPrompt(3, "poi-01", "THREAT", "", "", "");
        ui.showCampaignForceEncounterPrompt(4, "FORCE", "", "", "");
        ui.showCampaignBattleInterventionPrompt(5, "BATTLE", "", "", "");

        assertEquals(UiState.StrategicEncounterPrompt.Kind.CAMPAIGN_BATTLE, ui.strategicEncounterPrompt.kind);
        assertEquals(5, ui.queuedStrategicEncounterPromptCount());

        ui.clearStrategicEncounterPrompt();

        assertTrue(ui.strategicEncounterPrompt.active);
        assertEquals(4, ui.queuedStrategicEncounterPromptCount());
    }

    @Test
    void stalePromptCanBeDismissedExplicitly() {
        GameContext ctx = initializedCampaignContext();
        ctx.ui.showGalaxySearchGroupEncounterPrompt(999_999, "CONTACT", "", "", "");
        ctx.state = GameState.PAUSED;

        assertTrue(UISystem.dismissStaleStrategicEncounterPrompt(ctx));

        assertFalse(ctx.ui.strategicEncounterPrompt.active);
        assertEquals(GameState.RUNNING, ctx.state);
    }

    @Test
    void orphanedModalPauseRecoversButManualPauseDoesNot() {
        GameContext ctx = context();
        ctx.state = GameState.PAUSED;
        ctx.ui.modalPauseOwned = true;

        assertTrue(UISystem.auditAndRecoverOverlayState(ctx));
        assertEquals(GameState.RUNNING, ctx.state);

        ctx.state = GameState.PAUSED;
        ctx.ui.modalPauseOwned = false;
        assertFalse(UISystem.auditAndRecoverOverlayState(ctx));
        assertEquals(GameState.PAUSED, ctx.state);
    }

    @Test
    void escapeClosesEveryPrimaryOverlayAndPreservesEncounterDecision() {
        GameContext ctx = context();
        openAndEscape(ctx, GameState.SHOP, () -> ctx.ui.shopOpen = true);
        openAndEscape(ctx, GameState.BASE_MENU, () -> ctx.ui.baseMenuOpen = true);
        openAndEscape(ctx, GameState.MAP, () -> ctx.ui.mapOpen = true);
        openAndEscape(ctx, GameState.POWER_MANAGEMENT, () -> ctx.ui.powerManagementOpen = true);
        openAndEscape(ctx, GameState.CREW_STATIONS, () -> ctx.ui.crewStationsOpen = true);
        openAndEscape(ctx, GameState.FLIGHT_DECK, () -> ctx.ui.flightDeckOpen = true);

        ctx.ui.showStrategicEncounterPrompt(7, "CONTACT", "", "", "");
        ctx.state = GameState.PAUSED;
        GameplayActions.handleEscape(ctx, null);
        assertTrue(ctx.ui.strategicEncounterPrompt.active);
        assertEquals(GameState.PAUSED, ctx.state);
    }

    @Test
    void contactsQueueDuringMiningDockingTravelTacticalEntryAndWarpExit() {
        assertContactQueueSurvivesTimingState(ctx -> ctx.miningKeyDown = true);
        assertContactQueueSurvivesTimingState(ctx -> ctx.campaign.dockedGalaxyLocationId = "poi-01");
        assertContactQueueSurvivesTimingState(ctx -> ctx.campaign.galaxyTravel.traveling = true);
        assertContactQueueSurvivesTimingState(ctx -> ctx.campaign.manualEncounterCommitInProgress = true);
        assertContactQueueSurvivesTimingState(ctx -> ctx.command.playerTeleportCharging = false);
    }

    @Test
    void checkpointRestoreWhilePromptIsOpenDoesNotRestoreADeadModal() throws Exception {
        GameContext source = initializedCampaignContext();
        CampaignSystem.CampaignLocation location = source.campaign.galaxyMainPois.get(0);
        source.ui.showCampaignLocationEncounterPrompt(location.id, "CONTACT", "", "", "");
        source.state = GameState.PAUSED;

        Method capture = CampaignSystem.class.getDeclaredMethod(
                "captureCheckpoint", GameContext.class, CampaignSystem.CampaignState.class, int.class);
        capture.setAccessible(true);
        CampaignCheckpointStore.Checkpoint checkpoint =
                (CampaignCheckpointStore.Checkpoint) capture.invoke(null, source, source.campaign, 2);

        GameContext restored = initializedCampaignContext();
        Method apply = CampaignSystem.class.getDeclaredMethod(
                "applyCheckpoint", GameContext.class, CampaignSystem.CampaignState.class,
                CampaignCheckpointStore.Checkpoint.class);
        apply.setAccessible(true);
        assertTrue((boolean) apply.invoke(null, restored, restored.campaign, checkpoint));

        assertFalse(restored.ui.strategicEncounterPrompt.active);
        assertEquals(GameState.MAP, restored.state);
        assertTrue(restored.ui.mapOpen);
    }

    @Test
    void diagnosticsRecordOverlayStateTransitionsAndPrintCurrentOwner() {
        GameContext ctx = context();
        UISystem.observeStateTransition(ctx, "test start");
        ctx.state = GameState.MAP;
        UISystem.observeStateTransition(ctx, "test map");

        String report = UISystem.printOverlayDiagnostics(ctx);

        assertTrue(report.contains("owner=NONE"));
        assertTrue(report.contains("RUNNING -> MAP"));
    }

    private static void openAndEscape(GameContext ctx, GameState state, Runnable open) {
        UISystem.closeAllOverlays(ctx);
        open.run();
        ctx.state = state;
        GameplayActions.handleEscape(ctx, null);
        assertFalse(ctx.ui.hasBlockingOverlay());
        assertEquals(GameState.RUNNING, ctx.state);
    }

    private static void assertContactQueueSurvivesTimingState(java.util.function.Consumer<GameContext> configure) {
        GameContext ctx = initializedCampaignContext();
        configure.accept(ctx);
        CampaignSystem.CampaignLocation first = ctx.campaign.galaxyMainPois.get(0);
        CampaignSystem.CampaignLocation second = ctx.campaign.galaxyMainPois.get(1);
        ctx.ui.showCampaignLocationEncounterPrompt(first.id, "FIRST", "", "", "");
        ctx.ui.showCampaignLocationEncounterPrompt(second.id, "SECOND", "", "", "");

        assertTrue(ctx.ui.strategicEncounterPrompt.active);
        assertEquals(second.id, ctx.ui.strategicEncounterPrompt.campaignLocationId);
        assertEquals(1, ctx.ui.queuedStrategicEncounterPromptCount());
    }

    private static GameContext context() {
        return new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
    }

    private static GameContext initializedCampaignContext() {
        GameContext ctx = context();
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }
}
