import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignPersistentFleetShopTest {

    @Test
    void logisticsHullsCanBeCommissionedWithCampaignOrePricing() {
        GameContext ctx = campaignShopContext(10_000, 2_000);

        assertEquals(14, CampaignSystem.campaignOreCost(ShipRole.MINER, 160, 0));
        assertEquals(34, CampaignSystem.campaignOreCost(ShipRole.HAULER, 260, 1));

        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.MINER, 160, 0));
        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.HAULER, 260, 1));

        assertEquals(9_580, ctx.credits);
        assertEquals(1_952, ctx.player.cargo);
        assertEquals(1, CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.ESCORT));
        assertEquals(1, CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.LINE));
    }

    @Test
    void persistentFleetCapsAreTrackedPerBand() {
        GameContext ctx = campaignShopContext(250_000, 25_000);

        int escortCap = CampaignSystem.persistentFleetCap(ShopHullCategory.ESCORT);
        for (int i = 0; i < escortCap; i++) {
            assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.PATROL, 0, 0));
        }

        assertFalse(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.MINER, 160, 0));
        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.HAULER, 260, 1));
        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.DREADNOUGHT, 3200, 3));
        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.TRANSPORT_TITAN, TitanArchetype.TRANSPORT.costCredits(), 3));

        assertEquals(escortCap, CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.ESCORT));
        assertEquals(1, CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.LINE));
        assertEquals(1, CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.CAPITAL));
        assertEquals(1, CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.TITAN));
    }

    private static GameContext campaignShopContext(int credits, int ore) {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        CampaignSystem.CampaignState st = new CampaignSystem.CampaignState();
        st.enabled = true;
        ctx.campaign = st;

        ctx.player = new Player(ShipRole.MOTHERSHIP, 2500.0, 2500.0);
        ctx.player.faction = Faction.ALLY;
        ctx.player.cargo = ore;
        ctx.player.cargoMax = Math.max(ctx.player.cargoMax, ore);
        ctx.ships.add(ctx.player);

        BaseUpgrades upgrades = new BaseUpgrades();
        upgrades.hangarLv = 3;
        ctx.baseUpgrades.put(ctx.player, upgrades);
        ctx.credits = credits;
        return ctx;
    }
}
