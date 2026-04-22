import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OffSectorSimulationSystemTest {

    @Test
    void remoteCombatPressureDamagesUnloadedSectorWithoutTouchingLoadedSector() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.RESOURCE_RUSH, 9000, 6000, true, 1234L, false));
        ctx.player = new Player(ShipRole.FRIGATE, 700, 3000);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.add(ctx.player);

        Ship localEnemy = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 900, 3000);
        Ship remoteAlly = new FleetShip(ShipRole.BATTLECRUISER, Faction.ALLY, 7800, 3000);
        Ship remoteEnemy = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 8100, 3000);
        ctx.ships.add(localEnemy);
        ctx.ships.add(remoteAlly);
        ctx.ships.add(remoteEnemy);

        double localDurabilityBefore = localEnemy.hp + localEnemy.shield;

        OffSectorSimulationSystem.update(ctx, 1.3);

        assertEquals(localDurabilityBefore, localEnemy.hp + localEnemy.shield, 0.001);
        assertTrue(OffSectorSimulationSystem.collapsedIntegrityFraction(ctx, "red-home", Faction.ENEMY) < 1.0);
    }

    @Test
    void sectorTargetPriorityBiasFavorsSelectedObjectiveUntilHomeIsThreatened() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.RESOURCE_RUSH, 9000, 6000, true, 1234L, false));
        ctx.player = new Player(ShipRole.FRIGATE, 700, 3000);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.add(ctx.player);
        BattlefieldSectorSystem.selectSector(ctx, "red-home");

        Ship seeker = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 14000, 3000);
        Ship redHomeTarget = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 27000, 3000);
        Ship blueHomeThreat = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 900, 3000);

        double attackBias = AISystem.sectorTargetPriorityBias(ctx, seeker, redHomeTarget);
        double quietHomeBias = AISystem.sectorTargetPriorityBias(ctx, seeker, blueHomeThreat);
        assertTrue(attackBias > quietHomeBias);

        ctx.ships.add(blueHomeThreat);
        ctx.battleElapsed = 1.0;

        double threatenedHomeBias = AISystem.sectorTargetPriorityBias(ctx, seeker, blueHomeThreat);
        double redUnderThreatBias = AISystem.sectorTargetPriorityBias(ctx, seeker, redHomeTarget);
        assertTrue(threatenedHomeBias > redUnderThreatBias);
    }

    @Test
    void reinforcementDirectiveTurnsDefensiveWhenHomeSectorIsUnderPressure() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.RESOURCE_RUSH, 9000, 6000, true, 1234L, false));
        ctx.player = new Player(ShipRole.FRIGATE, 700, 3000);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.add(ctx.player);
        ctx.ships.add(new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 900, 3000));
        ctx.battleElapsed = 1.0;

        OffSectorSimulationSystem.ReinforcementDirective directive =
                OffSectorSimulationSystem.reinforcementDirective(ctx, Faction.ALLY);

        assertEquals(OffSectorSimulationSystem.ReinforcementProfile.DEFENSE, directive.profile);
        assertTrue(directive.budgetDelta > 0);
        assertEquals("blue-home", directive.targetSectorId);
    }

    @Test
    void quietRemoteSectorCanCollapseToAbstractState() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.FOUR_TEAM_DOMINATION, 9000, 9000, true, 1234L, false));
        ctx.player = new Player(ShipRole.FRIGATE, 500, 500);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.add(ctx.player);
        ctx.ships.add(new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 8200, 800));
        ctx.battleElapsed = 1.0;

        BattlefieldSectorSystem.SectorDefinition remote = BattlefieldSectorSystem.findSector(ctx, "yellow-orbit");
        assertTrue(OffSectorSimulationSystem.isSectorAbstracted(ctx, remote));
    }

    @Test
    void nonLoadedSectorPreservesStrategicPresenceAfterLiveShipsAreParked() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.FOUR_TEAM_DOMINATION, 9000, 9000, true, 1234L, false));
        ctx.player = new Player(ShipRole.FRIGATE, 500, 500);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.add(ctx.player);

        Ship remoteEnemyA = new FleetShip(ShipRole.FRIGATE, Faction.TEAM_D, 27000, 27000);
        Ship remoteEnemyB = new FleetShip(ShipRole.CRUISER, Faction.TEAM_D, 27350, 27350);
        ctx.ships.add(remoteEnemyA);
        ctx.ships.add(remoteEnemyB);
        ctx.battleElapsed = 1.0;

        assertTrue(OffSectorSimulationSystem.update(ctx, 0.2));
        assertFalse(ctx.ships.contains(remoteEnemyA));
        assertFalse(ctx.ships.contains(remoteEnemyB));

        BattlefieldSectorSystem.SectorSnapshot snapshot =
                BattlefieldSectorSystem.snapshotForSector(ctx, "yellow-orbit");
        assertNotNull(snapshot);
        assertEquals(Faction.TEAM_D, snapshot.dominantFaction);
        assertTrue(snapshot.presenceForTeamId(Faction.TEAM_D.teamId()) > 0);
        assertEquals(2, TeamSystem.countAliveShips(ctx, Faction.TEAM_D));
    }

    @Test
    void selectedSectorStaysCollapsedUntilItBecomesLoaded() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.FOUR_TEAM_DOMINATION, 9000, 9000, true, 1234L, false));
        ctx.player = new Player(ShipRole.FRIGATE, 500, 500);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.add(ctx.player);

        ctx.ships.add(new FleetShip(ShipRole.FRIGATE, Faction.TEAM_D, 27000, 27000));
        ctx.ships.add(new FleetShip(ShipRole.CRUISER, Faction.TEAM_D, 27350, 27350));
        ctx.battleElapsed = 1.0;

        OffSectorSimulationSystem.update(ctx, 0.2);
        assertEquals(0, countLiveShips(ctx, Faction.TEAM_D));

        BattlefieldSectorSystem.selectSector(ctx, "yellow-orbit");
        assertFalse(OffSectorSimulationSystem.update(ctx, 0.2));
        assertEquals(0, countLiveShips(ctx, Faction.TEAM_D));

        BattlefieldSectorSystem.setLoadedSector(ctx, "yellow-orbit");
        assertTrue(OffSectorSimulationSystem.update(ctx, 0.2));
        assertTrue(countLiveShips(ctx, Faction.TEAM_D) > 0);
    }

    @Test
    void resourceRushDoesNotCollapseRemoteForcesIntoHiddenSummaries() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.RESOURCE_RUSH, 9000, 6000, true, 1234L, false));
        ctx.player = new Player(ShipRole.FRIGATE, 700, 3000);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.add(ctx.player);

        Ship remoteAlly = new FleetShip(ShipRole.CRUISER, Faction.ALLY, 26800, 2800);
        Ship remoteEnemy = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 27000, 3200);
        ctx.ships.add(remoteAlly);
        ctx.ships.add(remoteEnemy);
        ctx.battleElapsed = 1.0;

        OffSectorSimulationSystem.update(ctx, 1.3);

        assertTrue(ctx.ships.contains(remoteAlly));
        assertTrue(ctx.ships.contains(remoteEnemy));
        assertEquals(0.0, OffSectorSimulationSystem.collapsedIntegrityFraction(ctx, "red-home", Faction.ENEMY), 0.0001);
    }

    @Test
    void resourceRushKeepsAdjacentSectorsLiveInsteadOfAbstractingThem() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.RESOURCE_RUSH, 9000, 6000, true, 1234L, false));
        ctx.player = new Player(ShipRole.FRIGATE, 700, 3000);
        ctx.player.faction = Faction.ALLY;
        ctx.ships.add(ctx.player);
        ctx.battleElapsed = 1.0;

        OffSectorSimulationSystem.update(ctx, 0.2);

        assertFalse(OffSectorSimulationSystem.isSectorAbstracted(
                ctx, BattlefieldSectorSystem.findSector(ctx, "central-front")));
        assertFalse(OffSectorSimulationSystem.isSectorAbstracted(
                ctx, BattlefieldSectorSystem.findSector(ctx, "red-home")));
    }

    private static int countLiveShips(GameContext ctx, Faction faction) {
        int count = 0;
        for (Ship ship : ctx.ships) {
            if (ship == null || !ship.alive || ship.dying || ship.hp <= 0) continue;
            if (ship.faction == null || ship.faction.teamId() != faction.teamId()) continue;
            if (ship.role == ShipRole.BASE) continue;
            count++;
        }
        return count;
    }
}
