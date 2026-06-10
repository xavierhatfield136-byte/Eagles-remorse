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
        assertEquals(31400, ctx.WORLD_W);
        assertEquals(6000, ctx.WORLD_H);

        BattlefieldSectorSystem.SectorDefinition blueHome = BattlefieldSectorSystem.sectorAt(ctx, 600, 3000);
        BattlefieldSectorSystem.SectorDefinition central = BattlefieldSectorSystem.sectorAt(ctx, 14000, 3000);
        BattlefieldSectorSystem.SectorDefinition redHome = BattlefieldSectorSystem.sectorAt(ctx, 27000, 3000);

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
        assertEquals(31400, ctx.WORLD_W);
        assertEquals(31400, ctx.WORLD_H);

        BattlefieldSectorSystem.SectorDefinition blueOrbit = BattlefieldSectorSystem.sectorAt(ctx, 400, 400);
        BattlefieldSectorSystem.SectorDefinition center = BattlefieldSectorSystem.sectorAt(ctx, 14000, 14000);
        BattlefieldSectorSystem.SectorDefinition yellowOrbit = BattlefieldSectorSystem.sectorAt(ctx, 27000, 27000);

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
        ctx.ships.add(new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 27200, 800));
        ctx.ships.add(new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 13800, 13800));
        ctx.ships.add(new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 14200, 14200));

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
    void battlefieldSectorsExposeLocalHazardsAndControlPointObjectives() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.FOUR_TEAM_DOMINATION, 9000, 9000, true, 1234L, false));
        ctx.player = new Player(ShipRole.FRIGATE, 14000, 14000);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.add(ctx.player);
        ctx.ships.add(new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 14200, 14000));

        BattlefieldSectorSystem.SectorDefinition center = BattlefieldSectorSystem.sectorAt(ctx, 14000, 14000);
        assertNotNull(center);
        assertEquals(BattlefieldSectorSystem.LocalHazard.DEBRIS_STORM, center.localHazard);
        assertEquals(BattlefieldSectorSystem.ControlPoint.CENTRAL_BEACON, center.controlPoint);

        String hazardLine = BattlefieldSectorSystem.sectorHazardLine(center);
        String controlLine = BattlefieldSectorSystem.sectorControlPointLine(center);
        assertTrue(hazardLine.contains("Debris Storm"));
        assertTrue(controlLine.contains("Central Beacon"));

        BattlefieldSectorSystem.ControlPointObjective objective =
                BattlefieldSectorSystem.controlPointObjective(ctx, "central-warzone");
        assertNotNull(objective);
        assertEquals(BattlefieldSectorSystem.ControlState.CONTESTED, objective.controlState);
        assertTrue(objective.capturePressure > 0.0 && objective.capturePressure < 1.0);
        assertTrue(objective.objectiveLine.contains("Central Beacon"));
        assertTrue(objective.objectiveLine.contains("Debris Storm"));

        String line = BattlefieldSectorSystem.currentSectorLine(ctx);
        assertTrue(line.contains("Debris Storm"));
        assertTrue(line.contains("CP: Central Beacon"));
    }

    @Test
    void resourceRushNavigationUsesCenterBeforeEnemyHome() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.RESOURCE_RUSH, 9000, 6000, true, 1234L, false));

        BattlefieldSectorSystem.SectorDefinition openingRoute =
                BattlefieldSectorSystem.navigationSector(ctx, Faction.ALLY, 600, 3000);
        assertNotNull(openingRoute);
        assertEquals("central-front", openingRoute.id);

        ctx.ships.add(new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 14000, 3000));
        ctx.battleElapsed = 1.0;

        BattlefieldSectorSystem.SectorDefinition followThrough =
                BattlefieldSectorSystem.navigationSector(ctx, Faction.ALLY, 14000, 3000);
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
        Asteroid frontierField = new Asteroid(12000, 3000, 42.0, 400);
        ctx.asteroids.add(homeField);
        ctx.asteroids.add(frontierField);

        Asteroid choice = EconomySystem.findBestAsteroidForMiner(ctx, miner, 2400.0);
        assertSame(homeField, choice);
    }

    @Test
    void interSectorGapIsNotPlayableSpace() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.RESOURCE_RUSH, 9000, 6000, true, 1234L, false));
        assertEquals(null, BattlefieldSectorSystem.sectorAt(ctx, 10000, 3000));
        assertEquals("central-front", BattlefieldSectorSystem.sectorAt(ctx, 12000, 3000).id);
    }

    @Test
    void resourceRushGroupCapPreventsInfiniteWaveStacking() {
        assertEquals(1, AISystem.resourceRushCappedGroupCount(12, 2));
        assertEquals(0, AISystem.resourceRushCappedGroupCount(18, 2));
        assertEquals(0, AISystem.resourceRushCappedGroupCount(22, 3));
    }

    @Test
    void fourTeamInitializationSeedsForwardPressureOutsideHomeSectors() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.FOUR_TEAM_DOMINATION, 9000, 9000, true, 1234L, false));
        SpawnSystem.initWorld(ctx);

        long nonHomeCombatants = ctx.ships.stream()
                .filter(s -> s != null && s.alive && !s.dying && s.hp > 0)
                .filter(s -> s.role != ShipRole.BASE && !s.isSmallCraft())
                .filter(s -> {
                    BattlefieldSectorSystem.SectorDefinition sector = BattlefieldSectorSystem.sectorAt(ctx, s.x, s.y);
                    return sector != null && sector.anchorFaction == null;
                })
                .count();

        assertTrue(nonHomeCombatants >= 8, "expected meaningful starting pressure in link/center sectors");
    }

    @Test
    void fourTeamBasesSpawnTowardRearOfHomeSectorsInsteadOfExactCenters() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.FOUR_TEAM_DOMINATION, 9000, 9000, true, 1234L, false));
        SpawnSystem.initWorld(ctx);

        Ship blueBase = ctx.teamBases.get(Faction.ALLY);
        BattlefieldSectorSystem.SectorDefinition blueHome = BattlefieldSectorSystem.homeSector(ctx, Faction.ALLY);
        assertNotNull(blueBase);
        assertNotNull(blueHome);
        assertTrue(Math.abs(blueBase.x - blueHome.centerX(ctx)) > 400.0
                        || Math.abs(blueBase.y - blueHome.centerY(ctx)) > 400.0,
                "home bases should sit toward the rear edge of their sectors to leave room for sector fighting");
    }
}
