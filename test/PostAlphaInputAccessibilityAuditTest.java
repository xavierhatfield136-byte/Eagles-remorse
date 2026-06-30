import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PostAlphaInputAccessibilityAuditTest {
    @Test
    void strategicAndFlagshipFlowsUseTheSharedKeyboardControllerActionDispatcher() {
        GameContext ctx = new GameContext(new GameConfig(
                GameMode.CAMPAIGN_OPS, 5000, 5000, true, 9981L, false));
        SpawnSystem.initWorld(ctx);
        ctx.state = GameState.MAP;
        ctx.ui.mapOpen = true;
        ctx.campaign.strategicOvermapMode = true;

        ctx.ui.campaignCommandTab = UiState.CampaignCommandTab.NAV;
        Set<String> navigation = CampaignSystem.campaignVisibleActions(ctx).stream()
                .map(action -> action.id).collect(Collectors.toSet());
        assertTrue(navigation.containsAll(Set.of("TERRITORY_PREV", "TERRITORY_NEXT",
                "TERRITORY_DETAILS", "WAR_MAP_OVERLAY")));
        assertTrue(CampaignSystem.executeCampaignAction(ctx, "TERRITORY_NEXT"));
        assertTrue(CampaignSystem.executeCampaignAction(ctx, "WAR_MAP_OVERLAY"));
        assertTrue(CampaignSystem.executeCampaignAction(ctx, "TERRITORY_DETAILS"));

        ctx.ui.campaignCommandTab = UiState.CampaignCommandTab.FLEET;
        Set<String> flagship = CampaignSystem.campaignVisibleActions(ctx).stream()
                .map(action -> action.id).collect(Collectors.toSet());
        assertTrue(flagship.containsAll(Set.of("FLAGSHIP_SCHEMATIC", "FLAGSHIP_COMPARTMENT_NEXT",
                "FLAGSHIP_ZOOM", "FLAGSHIP_SLOW_TIME")));
        assertTrue(CampaignSystem.executeCampaignAction(ctx, "FLAGSHIP_SCHEMATIC"));
        assertTrue(CampaignSystem.executeCampaignAction(ctx, "FLAGSHIP_COMPARTMENT_NEXT"));
        assertTrue(CampaignSystem.executeCampaignAction(ctx, "FLAGSHIP_ZOOM"));
        assertTrue(CampaignSystem.executeCampaignAction(ctx, "FLAGSHIP_SLOW_TIME"));
    }
}
