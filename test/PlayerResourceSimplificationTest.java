import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerResourceSimplificationTest {
    @Test
    void salvagePickupConvertsToOneHundredOre() {
        GameContext ctx = context();
        ctx.player = new Player(ShipRole.MOTHERSHIP, 100.0, 100.0);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.add(ctx.player);
        ctx.campaign = new CampaignSystem.CampaignState();
        ctx.campaign.enabled = true;
        ctx.campaign.oreLedger.storedOre = 0;
        ctx.salvage.add(new Salvage(100.0, 100.0, 25, 5, 20.0));

        EconomySystem.update(ctx, GameContext.DT);

        assertTrue(ctx.salvage.isEmpty());
        assertEquals(100, CampaignSystem.currentCampaignOre(ctx));
    }

    @Test
    void transportRepairSupportDoesNotSpendOrRequireSupplies() {
        GameContext ctx = context();
        ctx.campaign = new CampaignSystem.CampaignState();
        ctx.campaign.enabled = true;
        ctx.campaign.campaignSupplies = 0;

        CampaignSystem.reportTransportRepairSupport(ctx, 1);

        assertTrue(ctx.campaign.transportRepairSupportActive);
        assertTrue(CampaignSystem.consumeTransportRepairSupport(ctx, 999.0, 10.0));
        assertEquals(0, ctx.campaign.campaignSupplies);
    }

    private static GameContext context() {
        return new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
    }
}
