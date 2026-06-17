import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void minerRemoteDepositsToCampaignMothershipWhenFleetLeavesMiningPosture() {
        GameContext ctx = campaignContext();
        ctx.command.alliedFleetCommand = GameContext.FleetCommand.ATTACK;
        ctx.player.cargo = 30;

        Ship miner = new FleetShip(ShipRole.MINER, Faction.ALLY, ctx.player.x + 900.0, ctx.player.y + 640.0);
        miner.cargo = 80;
        miner.minerState = Ship.MinerState.RETURN_TO_BASE;
        miner.minerHomeBase = ctx.player;
        ctx.ships.add(miner);

        EconomySystem.update(ctx, GameContext.DT);

        assertEquals(110, ctx.player.cargo);
        assertEquals(0, miner.cargo);
        assertEquals(Ship.MinerState.SEEK_ASTEROID, miner.minerState);
    }

    @Test
    void minerDepositAtAlliedStarbaseCreditsCampaignOreToPlayer() {
        GameContext ctx = campaignContext();
        ctx.player.cargo = 20;
        int startingCredits = ctx.credits;
        Faction.configureCampaignAlliances(true, true);
        try {
            Ship greenBase = new FleetShip(ShipRole.BASE, Faction.TEAM_C, ctx.player.x + 40.0, ctx.player.y);
            ctx.ships.add(greenBase);

            Ship miner = new FleetShip(ShipRole.MINER, Faction.ALLY, greenBase.x + 8.0, greenBase.y + 8.0);
            miner.cargo = 70;
            miner.minerState = Ship.MinerState.DEPOSIT;
            miner.minerHomeBase = ctx.player;
            ctx.ships.add(miner);

            EconomySystem.update(ctx, GameContext.DT);

            assertEquals(90, ctx.player.cargo);
            assertEquals(0, miner.cargo);
            assertEquals(startingCredits, ctx.credits);
        } finally {
            Faction.clearCampaignAlliances();
        }
    }

    @Test
    void minersDoNotCollideWithMineableAsteroids() {
        GameContext ctx = campaignContext();
        Ship miner = new FleetShip(ShipRole.MINER, Faction.ALLY, 2500.0, 2500.0);
        ctx.ships.add(miner);
        ctx.asteroids.add(new Asteroid(2500.0, 2500.0, 60.0, 500));

        CollisionSystem.handleShipsVsAsteroids(ctx.ships, ctx.asteroids);

        assertEquals(2500.0, miner.x, 1e-9);
        assertEquals(2500.0, miner.y, 1e-9);
    }

    @Test
    void tacticalCampaignSpawnsSafeMineableAsteroidAboutOncePerMinute() {
        GameContext ctx = campaignContext();
        ctx.campaign.strategicOvermapMode = false;
        ctx.campaign.sectorElapsed = 61.0;
        ctx.campaign.nextTacticalOreAsteroidAtSec = 60.0;
        int before = ctx.asteroids.size();

        assertTrue(CampaignSystem.updatePeriodicTacticalOreSpawn(ctx, ctx.campaign));

        assertEquals(before + 1, ctx.asteroids.size());
        Asteroid spawned = ctx.asteroids.get(ctx.asteroids.size() - 1);
        assertTrue(spawned.ore > 0, "periodic tactical asteroid should be mineable");
        assertTrue(GameMath.dist2(spawned.x, spawned.y, ctx.player.x, ctx.player.y) >= 720.0 * 720.0,
                "periodic tactical asteroid should not spawn directly on the player");
        assertEquals(121.0, ctx.campaign.nextTacticalOreAsteroidAtSec, 1e-9);
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
