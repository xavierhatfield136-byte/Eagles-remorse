import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignOperationIntentMilestoneSevenTest {

    @Test
    void unknownOperationHasNoArrowAndStrategicIntelDoesNotRevealFleetPosition() {
        String property = "game.feature.focused_faction_attacks";
        String previous = System.getProperty(property);
        System.setProperty(property, "true");
        try {
            GameContext ctx = initializedCampaignContext();
            CampaignSystem.CampaignLocation origin = ctx.campaign.galaxyMainPois.stream()
                    .filter(location -> location.ownerFaction == Faction.ENEMY).findFirst().orElseThrow();
            CampaignSystem.CampaignLocation target = ctx.campaign.galaxyMainPois.stream()
                    .filter(location -> location.ownerFaction != Faction.ENEMY).findFirst().orElseThrow();
            FactionAttackCommitmentSystem.Result result = FactionAttackCommitmentSystem.request(
                    ctx.campaign.factionAttackCommitments,
                    new FactionAttackCommitmentSystem.Request(Faction.ENEMY, origin.id, target.id, 0, 0.0, 300.0),
                    target.ownerFaction.name(), ignored -> FactionAttackCommitmentSystem.Validation.allow());
            assertTrue(result.accepted());
            assertTrue(CampaignSystem.campaignInvasionArrows(ctx).isEmpty());

            ctx.campaign.campaignIntelTick = 12L;
            CampaignSystem.recordCampaignOperationIntelObservation(ctx, result.operationId(),
                    CampaignSystem.CampaignIntelObservationSource.OPERATION_INTEL,
                    CampaignSystem.CampaignIntelPrecision.STRATEGIC_ONLY,
                    12L, 20L, 0.7, target.x, target.y, 0.0);
            List<CampaignSystem.CampaignInvasionArrow> arrows = CampaignSystem.campaignInvasionArrows(ctx);
            assertEquals(1, arrows.size());
            CampaignSystem.CampaignInvasionArrow arrow = arrows.get(0);
            assertEquals(0, arrow.forceId);
            assertEquals(0.0, arrow.strength, 1e-9);
            assertEquals(0.0, arrow.etaSeconds, 1e-9);
            assertEquals(origin.x, arrow.fromX, 1e-9);
            assertEquals(target.x, arrow.toX, 1e-9);
        } finally {
            if (previous == null) System.clearProperty(property);
            else System.setProperty(property, previous);
        }
    }

    private static GameContext initializedCampaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 77001L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }
}
