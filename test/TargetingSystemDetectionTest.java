import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetingSystemDetectionTest {

    @Test
    void capitalsAcquireCombatContactsAtThreeThousandMetersButNotMapWide() {
        FleetShip enemySupership = new FleetShip(ShipRole.SUPERSHIP, Faction.ENEMY, 0.0, 0.0);
        Player player = new Player(ShipRole.FRIGATE, 3000.0, 0.0);
        player.faction = Faction.ALLY;

        assertTrue(TargetingSystem.isDetectableToObserver(enemySupership, player),
                "capital ships should acquire normal combat contacts at 3,000m");

        player.x = 3600.0;
        assertFalse(TargetingSystem.isDetectableToObserver(enemySupership, player),
                "capital ships should not retain map-wide awareness beyond combat range");
    }

    @Test
    void allHullClassesShareTheThreeThousandMeterCombatFloor() {
        FleetShip scout = new FleetShip(ShipRole.PATROL, Faction.ENEMY, 0.0, 0.0);
        FleetShip heavy = new FleetShip(ShipRole.SUPERSHIP, Faction.ENEMY, 0.0, 0.0);
        Player player = new Player(ShipRole.FRIGATE, 3000.0, 0.0);
        player.faction = Faction.ALLY;

        assertTrue(TargetingSystem.isDetectableToObserver(scout, player),
                "scouts should acquire contacts at the shared combat floor");
        assertTrue(TargetingSystem.isDetectableToObserver(heavy, player),
                "heavy strike ships should also acquire contacts at the shared combat floor");

        player.x = 3600.0;
        assertFalse(TargetingSystem.isDetectableToObserver(heavy, player),
                "heavy strike ships should still stop beyond the shared combat floor");
    }
}
