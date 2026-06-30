import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarMemoryPlayerFacingIntegrationTest {
    @Test
    void recordedBattlesBecomeStrategicMapScarsAndRemainRevisitable() {
        GameContext ctx = new GameContext(new GameConfig(
                GameMode.CAMPAIGN_OPS, 5000, 5000, true, 9951L, false));
        SpawnSystem.initWorld(ctx);
        CampaignSystem.CampaignLocation location = CampaignSystem.mainCampaignLocations(ctx).get(0);
        CampaignSystem.campaignTerritoryOverlayViews(ctx);
        StrategicCampaignExpansionSystem.BattleReport report =
                StrategicCampaignExpansionSystem.recordBattle(ctx.campaign.strategicExpansion,
                        "scar-test", "BRIGHT_YELLOW", "DARK_YELLOW");
        StrategicCampaignExpansionSystem.configureBattleReport(ctx.campaign.strategicExpansion, report,
                location.id, 44, List.of("hold the relief route"), 63,
                "costly coalition victory", 80, 55, 6, "BRIGHT_YELLOW");

        List<CampaignSystem.CampaignBattleScarView> views = CampaignSystem.campaignBattleScarViews(ctx);
        assertEquals(1, views.size());
        assertTrue(views.get(0).scar().contains("major wreck"));
        assertTrue(StrategicCampaignExpansionSystem.revisitBattleSite(
                ctx.campaign.strategicExpansion, "scar-test", 20, 2, "BRIGHT_YELLOW"));
        assertTrue(StrategicCampaignExpansionSystem.battleSiteLines(
                ctx.campaign.strategicExpansion, location.id).get(0).contains("salvage 60"));
    }

    @Test
    void unresolvedDamageAndPopulationNeedsGenerateTraceableFollowUpsAndNews() {
        WarMemorySystem.State memory = WarMemorySystem.bootstrap();
        WarMemorySystem.recordLocation(memory, "lunar-hab", "Lunar Habitat", "BRIGHT_YELLOW", 72,
                List.of("reactor", "clinic"), true, false, "medical resupply", 20);
        WarMemorySystem.addChronicle(memory, new WarMemorySystem.ChronicleEntry(
                "battle-lunar", 20, "battle", "BRIGHT_YELLOW", "lunar-hab", "fleet-9", "cmd-2",
                "Bright Yellow held the habitat", "clinic disabled", "battle-lunar", true));

        List<WarMemorySystem.FollowUpMission> followUps = WarMemorySystem.followUpMissions(memory);
        assertTrue(followUps.stream().anyMatch(item -> item.locationId().equals("lunar-hab")
                && item.objective().contains("medical resupply")));
        assertTrue(followUps.stream().anyMatch(item -> item.title().contains("Rebuild")));
        assertTrue(followUps.stream().anyMatch(item -> item.title().contains("Population")));
        List<String> news = WarMemorySystem.factionNewsLines(memory, "BRIGHT_YELLOW");
        assertEquals(1, news.size());
        assertTrue(news.get(0).contains("verified source battle-lunar"));
    }

    @Test
    void boundedLateCampaignArchiveBrowsingStaysWithinInteractiveBudget() {
        WarMemorySystem.State memory = WarMemorySystem.bootstrap();
        for (int i = 0; i < WarMemorySystem.MAX_CHRONICLE * 3; i++) {
            WarMemorySystem.addChronicle(memory, new WarMemorySystem.ChronicleEntry(
                    "event-" + i, i, i % 3 == 0 ? "battle" : "logistics",
                    i % 2 == 0 ? "BRIGHT_YELLOW" : "DARK_YELLOW", "territory-" + (i % 40),
                    "fleet-" + (i % 80), "commander-" + (i % 12), "Fact " + i,
                    "Consequence " + i, "source-" + i, i % 30 == 0));
        }
        long start = System.nanoTime();
        for (int i = 0; i < 500; i++) {
            assertFalse(WarMemorySystem.filter(memory, 100, 3000, "YELLOW", "territory-",
                    "fleet-", "commander-", "battle").isEmpty());
            WarMemorySystem.turningPointSummary(memory);
            WarMemorySystem.memorialSearch(memory, "captain");
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertTrue(elapsedMs < 3000, "archive queries took " + elapsedMs + "ms");
        assertTrue(memory.chronicle.size() <= WarMemorySystem.MAX_CHRONICLE);
    }
}
