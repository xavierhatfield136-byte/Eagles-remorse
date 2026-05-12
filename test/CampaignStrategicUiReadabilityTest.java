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
