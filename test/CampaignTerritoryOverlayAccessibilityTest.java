import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignTerritoryOverlayAccessibilityTest {
    private static GameContext campaign() {
        GameContext ctx = new GameContext(new GameConfig(
                GameMode.CAMPAIGN_OPS, 5000, 5000, true, 9801L, false));
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    @Test
    void blockedEdgesExposeDistinctStateAndHumanReadableExplanation() {
        GameContext ctx = campaign();
        CampaignSystem.campaignTerritoryOverlayViews(ctx);
        StrategicCampaignExpansionSystem.TravelLane lane =
                ctx.campaign.strategicExpansion.lanes.stream().findFirst().orElseThrow();
        lane.blockaded = true;
        ctx.campaign.selectedGalaxyLocationId = lane.from;

        CampaignSystem.CampaignTerritoryEdgeView edge = CampaignSystem.campaignSelectedTerritoryEdges(ctx).stream()
                .filter(item -> item.toId.equals(lane.to)).findFirst().orElseThrow();

        assertTrue(edge.blocked);
        assertFalse(edge.legalInvasion);
        assertFalse(edge.explanation.isBlank());
    }

    @Test
    void compactExpandedAndDirectionalNavigationAreAvailableWithoutPointerInput() {
        GameContext ctx = campaign();
        List<String> compact = CampaignSystem.campaignTerritoryDetailLines(ctx, false);
        List<String> expanded = CampaignSystem.campaignTerritoryDetailLines(ctx, true);
        assertTrue(compact.size() <= 4);
        assertTrue(expanded.size() > compact.size());

        String before = ctx.campaign.selectedGalaxyLocationId;
        assertTrue(CampaignSystem.cycleCampaignTerritorySelection(ctx, 1));
        assertNotEquals(before, ctx.campaign.selectedGalaxyLocationId);
        assertTrue(CampaignSystem.cycleStrategicMapOverlay(ctx));
        assertNotEquals(StrategicCampaignExpansionSystem.MapOverlay.CONTROL.name(),
                ctx.campaign.selectedStrategicOverlayId);
        assertTrue(CampaignSystem.toggleCampaignTerritoryDetails(ctx));
        assertFalse(ctx.campaign.expandedTerritoryDetails);
    }

    @Test
    void strategicMapRendersAtSupportedDesktopAndHighDensitySizes() {
        for (int[] size : new int[][]{{1280, 720}, {1920, 1080}, {2560, 1440}}) {
            BufferedImage standard = ScreenshotRegressionHarness.capture("campaign-map", size[0], size[1]);
            BufferedImage scaled = ScreenshotRegressionHarness.capture("accessibility-hud", size[0], size[1]);
            assertEquals(size[0], standard.getWidth());
            assertEquals(size[1], standard.getHeight());
            assertTrue(nonTransparentSamples(standard) > 100);
            assertTrue(nonTransparentSamples(scaled) > 100);
        }
    }

    @Test
    void bothYellowFactionsRemainLegibleInTheSameTacticalRoster() {
        app.config.ExperienceSettings original = ExperienceRuntime.active().copy();
        app.config.ExperienceSettings settings = app.config.ExperienceSettings.defaults();
        settings.colorblindPalette = app.config.ExperienceSettings.ColorblindPalette.DEUTERANOPIA;
        ExperienceRuntime.activate(settings);
        try {
        GameContext ctx = campaign();
        Ship bright = SpawnSystem.spawnCatalogShip(ctx, ShipRole.FRIGATE, Faction.BRIGHT_YELLOW, 2100, 2400);
        Ship dark = SpawnSystem.spawnCatalogShip(ctx, ShipRole.FRIGATE, Faction.DARK_YELLOW, 2400, 2400);
        assertEquals(bright.role, dark.role);
        assertNotEquals(bright.faction.teamName(), dark.faction.teamName());
        assertNotEquals(bright.faction.transponderPrefix(), dark.faction.transponderPrefix());
        assertNotEquals(bright.faction.insigniaKey(), dark.faction.insigniaKey());
        assertNotEquals(ExperienceRuntime.factionColor(bright.faction, true),
                ExperienceRuntime.factionColor(dark.faction, true));
        } finally {
            ExperienceRuntime.activate(original);
        }
    }

    private static int nonTransparentSamples(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y += 32) {
            for (int x = 0; x < image.getWidth(); x += 32) {
                if (((image.getRGB(x, y) >>> 24) & 0xff) > 0) count++;
            }
        }
        return count;
    }
}
