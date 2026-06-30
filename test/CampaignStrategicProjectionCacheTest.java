import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignStrategicProjectionCacheTest {
    private static GameContext campaign(long seed) {
        GameContext ctx = new GameContext(new GameConfig(
                GameMode.CAMPAIGN_OPS, 5000, 5000, true, seed, false));
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static List<CampaignSystem.CampaignLocation> locations(GameContext ctx) {
        ArrayList<CampaignSystem.CampaignLocation> result =
                new ArrayList<>(CampaignSystem.mainCampaignLocations(ctx));
        result.addAll(CampaignSystem.campaignAreasOfInterest(ctx));
        return result;
    }

    @Test
    void repeatedOverlayQueriesReuseTheValidatedProjection() {
        GameContext ctx = campaign(9601L);

        CampaignSystem.campaignTerritoryOverlayViews(ctx);
        int firstBuildCount = ctx.campaign.strategicProjectionBuildCount;
        assertTrue(firstBuildCount > 0);

        for (int i = 0; i < 20; i++) {
            CampaignSystem.campaignTerritoryOverlayViews(ctx);
        }

        assertEquals(firstBuildCount, ctx.campaign.strategicProjectionBuildCount,
                "render and inspection queries must not reconstruct the territory graph every frame");
    }

    @Test
    void ownershipChangesInvalidateProjectionAndPreserveLaneConditions() {
        GameContext ctx = campaign(9602L);
        CampaignSystem.campaignTerritoryOverlayViews(ctx);
        int firstBuildCount = ctx.campaign.strategicProjectionBuildCount;
        StrategicCampaignExpansionSystem.TravelLane lane =
                ctx.campaign.strategicExpansion.lanes.stream().findFirst().orElseThrow();
        lane.blockaded = true;
        lane.requiredAccess = "coalition-cipher";
        lane.requiresTechnology = true;
        String laneKey = lane.from + ">" + lane.to;

        CampaignSystem.CampaignLocation changed = locations(ctx).stream()
                .filter(location -> location.ownerFaction != Faction.ENEMY)
                .findFirst().orElseThrow();
        Faction originalOwner = changed.ownerFaction;
        changed.ownerFaction = Faction.ENEMY;
        CampaignSystem.campaignTerritoryOverlayViews(ctx);

        assertEquals(firstBuildCount + 1, ctx.campaign.strategicProjectionBuildCount);
        assertNotEquals(originalOwner.name(),
                StrategicCampaignExpansionSystem.territory(ctx.campaign.strategicExpansion, changed.id).owner);
        StrategicCampaignExpansionSystem.TravelLane restored = ctx.campaign.strategicExpansion.lanes.stream()
                .filter(item -> (item.from + ">" + item.to).equals(laneKey))
                .findFirst().orElseThrow();
        assertTrue(restored.blockaded);
        assertEquals("coalition-cipher", restored.requiredAccess);
        assertTrue(restored.requiresTechnology);
    }

    @Test
    void cachedStrategicSupplyStateFeedsEveryLiveCampaignMultiplier() {
        GameContext ctx = campaign(9603L);
        CampaignSystem.campaignTerritoryOverlayViews(ctx);
        CampaignSystem.CampaignLocation location = locations(ctx).stream().findFirst().orElseThrow();
        StrategicCampaignExpansionSystem.Territory territory =
                StrategicCampaignExpansionSystem.territory(ctx.campaign.strategicExpansion, location.id);
        territory.supplyState = StrategicCampaignExpansionSystem.SupplyState.ISOLATED;
        StrategicCampaignExpansionSystem.SupplyEffects expected =
                StrategicCampaignExpansionSystem.supplyEffects(territory);
        int buildCount = ctx.campaign.strategicProjectionBuildCount;

        CampaignSystem.campaignTerritoryOverlayViews(ctx);

        assertEquals(buildCount, ctx.campaign.strategicProjectionBuildCount);
        assertEquals(expected.repair(), location.strategicRepairMultiplier, 0.0001);
        assertEquals(expected.ammunition(), location.strategicAmmoMultiplier, 0.0001);
        assertEquals(expected.reinforcement(), location.strategicReinforcementMultiplier, 0.0001);
        assertEquals(expected.construction(), location.strategicConstructionMultiplier, 0.0001);
        assertEquals(expected.morale(), location.strategicMoraleMultiplier, 0.0001);
        assertEquals(expected.invasionReadiness(), location.strategicInvasionMultiplier, 0.0001);
    }

    @Test
    void explicitInvalidationSupportsAuthoredGraphChanges() {
        GameContext ctx = campaign(9604L);
        CampaignSystem.campaignTerritoryOverlayViews(ctx);
        int firstBuildCount = ctx.campaign.strategicProjectionBuildCount;

        CampaignSystem.invalidateStrategicProjection(ctx);
        CampaignSystem.campaignTerritoryOverlayViews(ctx);

        assertEquals(firstBuildCount + 1, ctx.campaign.strategicProjectionBuildCount);
    }
}
