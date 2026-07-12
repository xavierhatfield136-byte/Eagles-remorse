import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CampaignSystemDecompositionBoundaryTest {
    @Test
    void actionAndPresenterBoundariesDoNotMutatePreparedCampaignState() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        st.selectedGalaxyLocationId = "poi-05";

        invokeReadOnlyBoundaries(ctx);
        String before = campaignFingerprint(ctx);

        invokeReadOnlyBoundaries(ctx);

        assertEquals(before, campaignFingerprint(ctx));
    }

    private static void invokeReadOnlyBoundaries(GameContext ctx) {
        CampaignSystem.campaignVisibleActions(ctx);
        CampaignSystem.tacticalMapVisibleActions(ctx);
        CampaignSystem.campaignSummarySidebarLines(ctx);
        CampaignSystem.selectedRouteAssessmentLines(ctx);
        CampaignSystem.campaignWarBaselineTelemetryLines(ctx);
        CampaignSystem.campaignTerritoryOverlayViews(ctx);
        CampaignSystem.campaignBattleWarningLines(ctx, 4);

        CampaignSystem.CampaignLocation hub = CampaignSystem.mainCampaignLocations(ctx).stream()
                .filter(location -> location != null && !location.services.isEmpty())
                .findFirst()
                .orElse(null);
        if (hub != null) {
            CampaignSystem.hubServicePreviewLines(ctx, hub, hub.services.get(0));
        }
    }

    private static String campaignFingerprint(GameContext ctx) {
        CampaignSystem.CampaignState st = ctx.campaign;
        return "resources=" + st.campaignFuel + "," + st.campaignSupplies + "," + st.campaignAmmo + "," + st.campaignSalvage
                + "|selection=" + st.selectedGalaxyLocationId
                + "|travel=" + st.galaxyTravel.traveling + "," + st.galaxyTravel.destinationId + ","
                + rounded(st.galaxyTravel.targetX) + "," + rounded(st.galaxyTravel.targetY)
                + "|forces=" + st.campaignForces.stream()
                .map(force -> force.id + ":" + rounded(force.x) + ":" + rounded(force.y) + ":"
                        + force.destinationLocationId + ":" + force.destroyed)
                .collect(Collectors.joining(","))
                + "|battles=" + st.campaignBattles.size()
                + "|contacts=" + st.galaxySearchGroups.stream()
                .map(group -> group.label + ":" + rounded(group.x) + ":" + rounded(group.y) + ":"
                        + group.visible + ":" + group.contactConfidence)
                .collect(Collectors.joining(","))
                + "|owners=" + ownershipFingerprint(ctx);
    }

    private static String ownershipFingerprint(GameContext ctx) {
        Map<String, String> snapshot = CampaignSystem.campaignTerritoryOwnershipSnapshot(ctx);
        return snapshot.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(","));
    }

    private static String rounded(double value) {
        if (!Double.isFinite(value)) return "nan";
        return String.format(java.util.Locale.US, "%.3f", value);
    }

    private static GameContext initializedCampaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        SpawnSystem.initWorld(ctx);
        return ctx;
    }
}
