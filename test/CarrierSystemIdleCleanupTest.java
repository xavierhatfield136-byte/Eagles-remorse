import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CarrierSystemIdleCleanupTest {

    @Test
    void campaignIdlePlayerSideRetiresTemporarySmallCraftAndHangarCorvettes() {
        GameContext ctx = campaignContext();
        FleetShip carrier = new FleetShip(ShipRole.CARRIER, Faction.ALLY, 1200.0, 1000.0);
        FleetShip fighter = new FleetShip(ShipRole.FIGHTER, Faction.ALLY, 1240.0, 1000.0);
        fighter.carrierOwnerId = carrier.id;
        fighter.minerHomeBase = carrier;
        FleetShip corvette = new FleetShip(ShipRole.CIWS_CORVETTE, Faction.ALLY, 1160.0, 1000.0);
        corvette.minerHomeBase = ctx.player;
        FleetShip persistentCorvette = new FleetShip(ShipRole.CIWS_CORVETTE, Faction.ALLY, 1120.0, 1000.0);
        CampaignSystem.PersistentFleetEntry persistentEntry = CampaignSystem.addPersistentFleetEntry(
                ctx.campaign,
                ShipRole.CIWS_CORVETTE,
                "Blue Persistent Screen",
                0,
                Faction.ALLY);
        persistentEntry.activeShipId = persistentCorvette.id;

        ctx.ships.add(carrier);
        ctx.ships.add(fighter);
        ctx.ships.add(corvette);
        ctx.ships.add(persistentCorvette);
        ctx.entityQuery.rebuild(ctx);

        CarrierSystem.update(ctx, 1.0 / 60.0);

        assertFalse(fighter.alive, "idle carrier-launched fighters should return to hangar");
        assertFalse(corvette.alive, "temporary player hangar corvettes should return to hangar");
        assertTrue(persistentCorvette.alive, "commissioned persistent corvettes must stay in the campaign fleet");
    }

    @Test
    void campaignSmallCraftRemainDeployedWhileHostilesArePresent() {
        GameContext ctx = campaignContext();
        FleetShip carrier = new FleetShip(ShipRole.CARRIER, Faction.ALLY, 1200.0, 1000.0);
        FleetShip fighter = new FleetShip(ShipRole.FIGHTER, Faction.ALLY, 1240.0, 1000.0);
        fighter.carrierOwnerId = carrier.id;
        fighter.minerHomeBase = carrier;
        FleetShip hostile = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 1800.0, 1000.0);

        ctx.ships.add(carrier);
        ctx.ships.add(fighter);
        ctx.ships.add(hostile);
        ctx.entityQuery.rebuild(ctx);

        CarrierSystem.update(ctx, 1.0 / 60.0);

        assertTrue(fighter.alive, "active hostiles should keep deployed craft on the tactical map");
    }

    private static GameContext campaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 3000, 2200, true, 91L, false));
        ctx.campaign = new CampaignSystem.CampaignState();
        ctx.campaign.enabled = true;
        ctx.player = new Player(ShipRole.MOTHERSHIP, 1000.0, 1000.0);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.clear();
        ctx.ships.add(ctx.player);
        return ctx;
    }
}
