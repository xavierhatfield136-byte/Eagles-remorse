import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class CampaignMiningDepositTest {

    @Test
    void minerDepositsOreIntoCampaignMothershipStores() {
        GameContext ctx = campaignContext();
        ctx.credits = 1000;
        ctx.player.cargo = 50;

        Ship miner = new FleetShip(ShipRole.MINER, Faction.ALLY, ctx.player.x + 12.0, ctx.player.y + 12.0);
        miner.cargo = 80;
        miner.minerState = Ship.MinerState.DEPOSIT;
        miner.minerHomeBase = ctx.player;
        ctx.ships.add(miner);

        EconomySystem.update(ctx, GameContext.DT);

        assertEquals(130, ctx.player.cargo);
        assertEquals(0, miner.cargo);
        assertEquals(1000, ctx.credits);
    }

    @Test
    void minerCanReacquireMothershipAsHomeBase() {
        GameContext ctx = campaignContext();

        Ship miner = new FleetShip(ShipRole.MINER, Faction.ALLY, ctx.player.x + 180.0, ctx.player.y + 20.0);
        miner.minerHomeBase = null;
        miner.minerState = Ship.MinerState.IDLE;
        ctx.ships.add(miner);

        EconomySystem.update(ctx, GameContext.DT);

        assertSame(ctx.player, miner.minerHomeBase);
    }

    private static GameContext campaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        CampaignSystem.CampaignState st = new CampaignSystem.CampaignState();
        st.enabled = true;
        ctx.campaign = st;

        ctx.player = new Player(ShipRole.MOTHERSHIP, 2500.0, 2500.0);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.add(ctx.player);
        return ctx;
    }
}
