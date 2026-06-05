import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignStrategicUiReadabilityTest {

    @Test
    void campaignSummarySidebarHighlightsTravelHuntAndPressure() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.selectedGalaxyLocationId = "poi-10";
        CampaignSystem.startTravelToSelectedLocation(ctx);

        List<String> lines = CampaignSystem.campaignSummarySidebarLines(ctx);

        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Travel: ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Hunt Status: ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Alert / Pressure: ")));
    }

    @Test
    void selectedLocationSidebarIncludesDockingThreatAndRouteSignal() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.selectedGalaxyLocationId = "poi-05";

        List<String> lines = CampaignSystem.selectedLocationSidebarLines(ctx);

        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Threat: ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Docking: ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Route: ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Risk: ")));
    }

    @Test
    void navigationAndStrikeSidebarsStayCompactEnoughToScan() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.selectedGalaxyLocationId = "poi-05";

        List<String> fleet = CampaignSystem.campaignFleetManagerLines(ctx);
        List<String> summary = CampaignSystem.campaignSummarySidebarLines(ctx);
        List<String> selected = CampaignSystem.selectedLocationSidebarLines(ctx);
        List<String> strikeTop = CampaignSystem.campaignStrikeManagerLines(ctx);
        List<String> strikeReadiness = CampaignSystem.campaignStrikeReadinessLines(ctx);
        List<String> strikeConsequences = CampaignSystem.campaignStrikeConsequenceLines(ctx);

        assertTrue(fleet.stream().anyMatch(line -> line.startsWith("Overmap Berths: ")));
        assertTrue(fleet.stream().anyMatch(line -> line.startsWith("Flagship Capability: ")));
        assertTrue(summary.size() <= 7, "navigation summary should be glanceable");
        assertTrue(selected.size() <= 15, "selected location details should fit the sidebar");
        assertTrue(strikeTop.size() <= 5, "strike control should avoid duplicated readiness prose");
        assertTrue(strikeReadiness.size() <= 4, "strike readiness should stay compact");
        assertTrue(strikeConsequences.size() <= 7, "strike consequences should stay compact");
    }

    @Test
    void overmapStrikeTabShowsReconButNoRemoteWeaponButtons() {
        GameContext ctx = initializedCampaignContext();
        ctx.ui.campaignCommandTab = UiState.CampaignCommandTab.STRIKES;

        List<CampaignSystem.CampaignAction> actions = CampaignSystem.campaignVisibleActions(ctx);

        assertFalse(actions.stream().anyMatch(action -> action.id.startsWith("POSTURE_")));
        assertFalse(actions.stream().anyMatch(action -> action.id.equals("TORPEDO_STRIKE")));
        assertFalse(actions.stream().anyMatch(action -> action.id.equals("CARRIER_SORTIE")));
        assertFalse(actions.stream().anyMatch(action -> action.id.equals("ATOMIC_STRIKE")));
        assertTrue(actions.stream().anyMatch(action -> action.id.equals("TRACK_TARGET")));
        assertTrue(actions.stream().anyMatch(action -> action.id.equals("RECON_SWEEP")
                || action.id.equals("SIGNAL_SWEEP")));
    }

    @Test
    void hubActionDetailShowsApproachUntilDockedThenShowsActionCost() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.selectedGalaxyLocationId = "poi-05";
        CampaignSystem.CampaignLocation selected = CampaignSystem.selectedCampaignLocation(ctx);

        String undocked = CampaignSystem.hubServiceActionDetail(ctx, selected, CampaignSystem.HubService.REPAIR);
        assertTrue(undocked.contains("APPROACH"));

        st.dockedGalaxyLocationId = selected.id;
        st.currentGalaxyLocationId = selected.id;
        String docked = CampaignSystem.hubServiceActionDetail(ctx, selected, CampaignSystem.HubService.REPAIR);
        assertFalse(docked.contains("APPROACH"));
        assertTrue(docked.startsWith("C "));
    }

    private static GameContext initializedCampaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }
}
