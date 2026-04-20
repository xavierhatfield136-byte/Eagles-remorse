import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattlefieldSectorSystemTest {

    @Test
    void resourceRushLayoutSplitsWorldIntoHomeAndCenterZones() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.RESOURCE_RUSH, 9000, 6000, true, 1234L, false));

        BattlefieldSectorSystem.SectorDefinition blueHome = BattlefieldSectorSystem.sectorAt(ctx, 600, 3000);
        BattlefieldSectorSystem.SectorDefinition central = BattlefieldSectorSystem.sectorAt(ctx, 4500, 3000);
        BattlefieldSectorSystem.SectorDefinition redHome = BattlefieldSectorSystem.sectorAt(ctx, 8600, 3000);

        assertNotNull(blueHome);
        assertNotNull(central);
        assertNotNull(redHome);
        assertEquals("blue-home", blueHome.id);
        assertEquals("central-front", central.id);
        assertEquals("red-home", redHome.id);
    }

    @Test
    void fourTeamDominationLayoutUsesCornersLinksAndCenter() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.FOUR_TEAM_DOMINATION, 9000, 9000, true, 1234L, false));

        BattlefieldSectorSystem.SectorDefinition blueOrbit = BattlefieldSectorSystem.sectorAt(ctx, 400, 400);
        BattlefieldSectorSystem.SectorDefinition center = BattlefieldSectorSystem.sectorAt(ctx, 4500, 4500);
        BattlefieldSectorSystem.SectorDefinition yellowOrbit = BattlefieldSectorSystem.sectorAt(ctx, 8600, 8600);

        assertNotNull(blueOrbit);
        assertNotNull(center);
        assertNotNull(yellowOrbit);
        assertEquals("blue-orbit", blueOrbit.id);
        assertEquals("central-warzone", center.id);
        assertEquals("yellow-orbit", yellowOrbit.id);
    }

    @Test
    void sectorSnapshotsExposeFriendlyHostileAndContestedStates() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.FOUR_TEAM_DOMINATION, 9000, 9000, true, 1234L, false));
        ctx.player = new Player(ShipRole.FRIGATE, 500, 500);
        ctx.player.faction = Faction.ALLY;

        ctx.ships.add(new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 800, 800));
        ctx.ships.add(new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 8200, 800));
        ctx.ships.add(new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 4400, 4400));
        ctx.ships.add(new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 4600, 4600));

        BattlefieldSectorSystem.SectorSnapshot blueSnapshot =
                BattlefieldSectorSystem.snapshotForSector(ctx, "blue-orbit");
        BattlefieldSectorSystem.SectorSnapshot redSnapshot =
                BattlefieldSectorSystem.snapshotForSector(ctx, "red-orbit");
        BattlefieldSectorSystem.SectorSnapshot centerSnapshot =
                BattlefieldSectorSystem.snapshotForSector(ctx, "central-warzone");

        assertNotNull(blueSnapshot);
        assertNotNull(redSnapshot);
        assertNotNull(centerSnapshot);
        assertEquals(BattlefieldSectorSystem.ControlState.CONTROLLED, blueSnapshot.controlState);
        assertEquals("Friendly", BattlefieldSectorSystem.relativeStatusLabel(ctx, blueSnapshot));
        assertEquals(BattlefieldSectorSystem.ControlState.CONTROLLED, redSnapshot.controlState);
        assertEquals("Hostile", BattlefieldSectorSystem.relativeStatusLabel(ctx, redSnapshot));
        assertEquals(BattlefieldSectorSystem.ControlState.CONTESTED, centerSnapshot.controlState);
        assertEquals("Contested", BattlefieldSectorSystem.relativeStatusLabel(ctx, centerSnapshot));

        BattlefieldSectorSystem.selectSector(ctx, "red-orbit");
        String line = BattlefieldSectorSystem.currentSectorLine(ctx);
        assertTrue(line.contains("Sector: BLUE ORBIT"));
        assertTrue(line.contains("Friendly"));
        assertTrue(line.contains("Target: RED ORBIT"));
    }

    @Test
    void resourceRushNavigationUsesCenterBeforeEnemyHome() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.RESOURCE_RUSH, 9000, 6000, true, 1234L, false));

        BattlefieldSectorSystem.SectorDefinition openingRoute =
                BattlefieldSectorSystem.navigationSector(ctx, Faction.ALLY, 600, 3000);
        assertNotNull(openingRoute);
        assertEquals("central-front", openingRoute.id);

        ctx.ships.add(new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 4500, 3000));
        ctx.battleElapsed = 1.0;

        BattlefieldSectorSystem.SectorDefinition followThrough =
                BattlefieldSectorSystem.navigationSector(ctx, Faction.ALLY, 4500, 3000);
        assertNotNull(followThrough);
        assertEquals("red-home", followThrough.id);
    }

    @Test
    void threatenedHomeOverridesSelectedAttackSector() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.RESOURCE_RUSH, 9000, 6000, true, 1234L, false));
        ctx.player = new Player(ShipRole.FRIGATE, 900, 3000);
        BattlefieldSectorSystem.selectSector(ctx, "red-home");

        ctx.ships.add(new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 800, 3000));
        ctx.battleElapsed = 1.0;

        BattlefieldSectorSystem.SectorDefinition route =
                BattlefieldSectorSystem.navigationSector(ctx, Faction.ALLY, 4500, 3000);
        assertNotNull(route);
        assertEquals("blue-home", route.id);
    }

    @Test
    void fourTeamNavigationRoutesThroughConnectorGraph() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.FOUR_TEAM_DOMINATION, 9000, 9000, true, 1234L, false));

        BattlefieldSectorSystem.SectorDefinition route =
                BattlefieldSectorSystem.navigationSector(ctx, Faction.ALLY, 500, 500);
        assertNotNull(route);
        assertEquals("north-link", route.id);
    }

    @Test
    void minersPreferSafeHomeSectorAsteroidsBeforeCloserFrontierOre() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.RESOURCE_RUSH, 9000, 6000, true, 1234L, false));
        Ship miner = new FleetShip(ShipRole.MINER, Faction.ALLY, 2500, 3000);
        Asteroid homeField = new Asteroid(1500, 3000, 42.0, 400);
        Asteroid frontierField = new Asteroid(3200, 3000, 42.0, 400);
        ctx.asteroids.add(homeField);
        ctx.asteroids.add(frontierField);

        Asteroid choice = EconomySystem.findBestAsteroidForMiner(ctx, miner, 2400.0);
        assertSame(homeField, choice);
    }
}
