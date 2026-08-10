import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RefitHullFilterTest {
    @Test
    void allKeepsSupportHullsReachableWhileBandsUseCanonicalShopCategory() {
        assertEquals("PICKET", ShopHullCategory.ESCORT.label());
        assertTrue(RefitHullFilter.PICKET.matches(ShipRole.PICKET));
        assertTrue(RefitHullFilter.PICKET.matches(ShipRole.MINER));
        assertFalse(RefitHullFilter.PICKET.matches(ShipRole.CRUISER));

        assertTrue(RefitHullFilter.LINE.matches(ShipRole.HAULER));
        assertTrue(RefitHullFilter.CAPITAL.matches(ShipRole.CARRIER));
        assertTrue(RefitHullFilter.TITAN.matches(ShipRole.MOTHERSHIP));

        assertTrue(RefitHullFilter.ALL.matches(ShipRole.MINER));
        assertTrue(RefitHullFilter.ALL.matches(ShipRole.HAULER));
        assertTrue(RefitHullFilter.ALL.matches(ShipRole.MOBILE_STATION_TITAN));
    }

    @Test
    void campaignRefitOnlyAllowsBlueShipsEvenWhenAllied() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaign = new CampaignSystem.CampaignState();
        ctx.campaign.enabled = true;
        ctx.player = new Player(ShipRole.FRIGATE, 0.0, 0.0);

        FleetShip blueFleetShip = new FleetShip(ShipRole.CRUISER, Faction.ALLY, 100.0, 100.0);
        FleetShip greenAlly = new FleetShip(ShipRole.CRUISER, Faction.TEAM_C, 200.0, 100.0);
        FleetShip yellowAlly = new FleetShip(ShipRole.CRUISER, Faction.TEAM_D, 300.0, 100.0);
        FleetShip brightYellowAlly = new FleetShip(ShipRole.CRUISER, Faction.BRIGHT_YELLOW, 400.0, 100.0);

        Faction.configureCampaignAlliances(true, true);
        try {
            assertTrue(CampaignSystem.isFleetRefitEditableCandidate(ctx, ctx.player));
            assertTrue(CampaignSystem.isFleetRefitEditableCandidate(ctx, blueFleetShip));
            assertFalse(CampaignSystem.isFleetRefitEditableCandidate(ctx, greenAlly));
            assertFalse(CampaignSystem.isFleetRefitEditableCandidate(ctx, yellowAlly));
            assertFalse(CampaignSystem.isFleetRefitEditableCandidate(ctx, brightYellowAlly));
        } finally {
            Faction.clearCampaignAlliances();
        }
    }

    @Test
    void customBattleRefitAllowsAnyLivingShipFaction() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 5000, 5000, true, 5678L, false));

        assertTrue(CampaignSystem.isFleetRefitEditableCandidate(ctx,
                new FleetShip(ShipRole.CRUISER, Faction.ALLY, 100.0, 100.0)));
        assertTrue(CampaignSystem.isFleetRefitEditableCandidate(ctx,
                new FleetShip(ShipRole.CRUISER, Faction.TEAM_C, 200.0, 100.0)));
        assertTrue(CampaignSystem.isFleetRefitEditableCandidate(ctx,
                new FleetShip(ShipRole.CRUISER, Faction.TEAM_D, 300.0, 100.0)));
        assertTrue(CampaignSystem.isFleetRefitEditableCandidate(ctx,
                new FleetShip(ShipRole.CRUISER, Faction.ENEMY, 400.0, 100.0)));
    }
}
