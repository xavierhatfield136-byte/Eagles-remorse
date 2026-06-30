import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignTerritorialInvasionLegalityTest {
    @Test
    void liveRedDirectorTargetsOnlyAdjacentTerritories() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 321L, false));
        SpawnSystem.initWorld(ctx);

        Method sync = CampaignSystem.class.getDeclaredMethod(
                "synchronizeStrategicExpansionFromLive", CampaignSystem.CampaignState.class);
        sync.setAccessible(true);
        sync.invoke(null, ctx.campaign);
        List<String> graphIssues = StrategicCampaignExpansionSystem.validateTerritoryGraph(ctx.campaign.strategicExpansion);
        assertTrue(graphIssues.isEmpty(), graphIssues.toString());

        List<CampaignSystem.CampaignLocation> locations = new ArrayList<>(CampaignSystem.mainCampaignLocations(ctx));
        locations.addAll(CampaignSystem.campaignAreasOfInterest(ctx));
        CampaignSystem.CampaignLocation staging = locations.stream()
                .filter(location -> location.ownerFaction == Faction.ENEMY)
                .findFirst()
                .orElseThrow();

        Method chooser = CampaignSystem.class.getDeclaredMethod("redInvasionTargets",
                CampaignSystem.CampaignState.class, CampaignSystem.CampaignLocation.class, int.class);
        chooser.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<CampaignSystem.CampaignLocation> targets =
                (List<CampaignSystem.CampaignLocation>) chooser.invoke(null, ctx.campaign, staging, 8);

        for (CampaignSystem.CampaignLocation target : targets) {
            assertTrue(StrategicCampaignExpansionSystem.operationLegality(ctx.campaign.strategicExpansion,
                    StrategicCampaignExpansionSystem.OperationType.INVASION,
                    Faction.ENEMY.name(), staging.id, target.id).legal(),
                    "director returned non-adjacent target " + target.id);
        }
        CampaignSystem.CampaignLocation skipped = locations.stream()
                .filter(location -> !location.id.equals(staging.id))
                .filter(location -> !StrategicCampaignExpansionSystem.adjacentTerritoryIds(
                        ctx.campaign.strategicExpansion, staging.id).contains(location.id))
                .findFirst()
                .orElseThrow();
        assertFalse(StrategicCampaignExpansionSystem.operationLegality(ctx.campaign.strategicExpansion,
                StrategicCampaignExpansionSystem.OperationType.INVASION,
                Faction.ENEMY.name(), staging.id, skipped.id).legal());
    }
}
