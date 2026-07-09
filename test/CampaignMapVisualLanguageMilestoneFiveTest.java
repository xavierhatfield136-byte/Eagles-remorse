import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignMapVisualLanguageMilestoneFiveTest {

    @Test
    void sensorRingUsesTheSameRangeAsCampaignDetection() {
        GameContext ctx = initializedCampaignContext();
        ctx.campaign.campaignIntelLevel = 28.0;
        assertEquals(2580.0, CampaignSystem.playerCampaignSensorRange(ctx), 1e-9);
        ctx.campaign.campaignIntelLevel = 60.0;
        assertEquals(3060.0, CampaignSystem.playerCampaignSensorRange(ctx), 1e-9);
    }

    @Test
    void sensorSphereUsesOnePixelRadiusInsteadOfAStretchedEllipse() {
        Rectangle map = new Rectangle(0, 0, 1600, 900);
        int radius = Renderer.campaignSensorSpherePixelRadius(2160.0, map, 5000.0, 5000.0);
        assertEquals(389, radius);
    }

    @Test
    void legendExplainsDistinctMapLanguages() {
        GameContext ctx = initializedCampaignContext();
        List<String> entries = CampaignSystem.campaignMapLegendEntries(ctx);
        assertTrue(entries.stream().anyMatch(line -> line.contains("SITE ICON")));
        assertTrue(entries.stream().anyMatch(line -> line.contains("TERRITORY")));
        assertTrue(entries.stream().anyMatch(line -> line.contains("EXACT LIVE")));
        assertTrue(entries.stream().anyMatch(line -> line.contains("APPROXIMATE")));
        assertTrue(entries.stream().anyMatch(line -> line.contains("SENSOR SPHERE")));
        assertTrue(entries.stream().anyMatch(line -> line.contains("OPERATION INTENT")));
    }

    @Test
    void territoryHaloRemainsNonInteractiveWhileSiteGlyphIsClickable() {
        GameContext ctx = initializedCampaignContext();
        CampaignSystem.CampaignLocation site = CampaignSystem.mainCampaignLocations(ctx).get(0);
        Rectangle map = new Rectangle(100, 100, 1000, 760);
        double radius = UISystem.campaignSiteHitRadiusWorld(ctx, map);
        assertEquals(site, UISystem.campaignLocationAtMapClick(ctx, site.x, site.y, map));
        assertFalse(site.equals(UISystem.campaignLocationAtMapClick(ctx, site.x + radius + 20.0, site.y, map)));
    }

    private static GameContext initializedCampaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 55001L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }
}
