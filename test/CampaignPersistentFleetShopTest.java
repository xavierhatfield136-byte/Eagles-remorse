import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignPersistentFleetShopTest {

    @Test
    void logisticsHullsCanBeCommissionedWithCampaignOrePricing() {
        GameContext ctx = campaignShopContext(10_000, 2_000, 1, 1);

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
    void campaignShipyardMustClimbBeforeTitansCanBeCommissioned() {
        GameContext ctx = campaignShopContext(100_000, 10_000, 1, 1);

        assertFalse(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.TRANSPORT_TITAN, TitanArchetype.TRANSPORT.costCredits(), 3));

        ctx.baseUpgrades.get(ctx.player).hangarLv = 3;
        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.TRANSPORT_TITAN, TitanArchetype.TRANSPORT.costCredits(), 3));
        assertEquals(10_000, ctx.player.cargoMax);
    }

    @Test
    void commandGridLimitsEarlyFleetSnowballUntilTitansExpandIt() {
        GameContext ctx = campaignShopContext(250_000, 25_000, 3, 3);

        for (int i = 0; i < 5; i++) {
            assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.PATROL, 0, 0));
        }
        assertFalse(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.FRIGATE, 0, 0));

        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.TRANSPORT_TITAN, TitanArchetype.TRANSPORT.costCredits(), 3));
        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.FRIGATE, 0, 0));
    }

    @Test
    void midAndLateTitansRespectSectorAndInfrastructureRequirements() {
        GameContext ctx = campaignShopContext(250_000, 25_000, 5, 5);

        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.COMMAND_INTEL_TITAN, TitanArchetype.COMMAND_INTEL.costCredits(), 3));
        assertFalse(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.HYPERWEAPON_TITAN, TitanArchetype.HYPERWEAPON.costCredits(), 3));

        ctx.campaign.sector = 9;
        assertFalse(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.HYPERWEAPON_TITAN, TitanArchetype.HYPERWEAPON.costCredits(), 3));

        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.MOBILE_STATION_TITAN, TitanArchetype.MOBILE_STATION.costCredits(), 3));
        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.HYPERWEAPON_TITAN, TitanArchetype.HYPERWEAPON.costCredits(), 3));
    }

    @Test
    void eliteReinforcementsTitanAddsHonorGuardTaskGroup() {
        GameContext ctx = campaignShopContext(250_000, 25_000, 5, 10);

        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.MOBILE_STATION_TITAN, TitanArchetype.MOBILE_STATION.costCredits(), 3));
        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.ELITE_REINFORCEMENTS_TITAN, TitanArchetype.ELITE_REINFORCEMENTS.costCredits(), 3));

        assertEquals(2, CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.ESCORT));
        assertEquals(2, CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.LINE));
        assertEquals(0, CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.CAPITAL));
        assertEquals(2, CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.TITAN));
        assertEquals(21, CampaignSystem.campaignStandardCommandCapacity(ctx));

        long battleships = ctx.ships.stream()
                .filter(ship -> ship != null && ship.role == ShipRole.BATTLESHIP && ship.faction == Faction.ALLY)
                .count();
        long battlecruisers = ctx.ships.stream()
                .filter(ship -> ship != null && ship.role == ShipRole.BATTLECRUISER && ship.faction == Faction.ALLY)
                .count();
        long frigates = ctx.ships.stream()
                .filter(ship -> ship != null && ship.role == ShipRole.FRIGATE && ship.faction == Faction.ALLY)
                .count();
        long screens = ctx.ships.stream()
                .filter(ship -> ship != null && ship.role == ShipRole.CIWS_CORVETTE && ship.faction == Faction.ALLY)
                .count();

        assertEquals(1, battleships);
        assertEquals(1, battlecruisers);
        assertEquals(1, frigates);
        assertEquals(1, screens);
    }

    @Test
    void eliteSupershipCommandTitanRemainsSeparatePurchaseWithoutAutoPackage() {
        GameContext ctx = campaignShopContext(250_000, 25_000, 5, 10);

        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.MOBILE_STATION_TITAN, TitanArchetype.MOBILE_STATION.costCredits(), 3));
        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.ELITE_SUPERSHIP_COMMAND_TITAN, TitanArchetype.ELITE_SUPERSHIP_COMMAND.costCredits(), 3));

        assertEquals(0, CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.ESCORT));
        assertEquals(0, CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.LINE));
        assertEquals(0, CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.CAPITAL));
        assertEquals(2, CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.TITAN));
    }

    @Test
    void persistentFleetCapsAreTrackedPerBandOnceCommandGridIsExpanded() {
        GameContext ctx = campaignShopContext(250_000, 25_000, 5, 9);

        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.TRANSPORT_TITAN, TitanArchetype.TRANSPORT.costCredits(), 3));
        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.BULWARK_TITAN, TitanArchetype.BULWARK.costCredits(), 3));

        int escortCap = CampaignSystem.persistentFleetCap(ShopHullCategory.ESCORT);
        for (int i = 0; i < escortCap; i++) {
            assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.PATROL, 0, 0));
        }
        assertFalse(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.MINER, 160, 0));
        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.HAULER, 260, 1));
        assertTrue(CampaignSystem.purchasePersistentBlueShip(ctx, ShipRole.DREADNOUGHT, 3200, 3));

        assertEquals(escortCap, CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.ESCORT));
        assertEquals(1, CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.LINE));
        assertEquals(1, CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.CAPITAL));
        assertEquals(2, CampaignSystem.livePersistentFleetCount(ctx, ShopHullCategory.TITAN));
    }

    private static GameContext campaignShopContext(int credits, int ore, int hangarTier, int sector) {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        CampaignSystem.CampaignState st = new CampaignSystem.CampaignState();
        st.enabled = true;
        st.sector = sector;
        ctx.campaign = st;

        ctx.player = new Player(ShipRole.MOTHERSHIP, 2500.0, 2500.0);
        ctx.player.faction = Faction.ALLY;
        ctx.player.cargo = ore;
        ctx.player.cargoMax = Math.max(ctx.player.cargoMax, ore);
        ctx.ships.add(ctx.player);

        BaseUpgrades upgrades = new BaseUpgrades();
        upgrades.hangarLv = hangarTier;
        ctx.baseUpgrades.put(ctx.player, upgrades);
        ctx.credits = credits;
        return ctx;
    }
}
