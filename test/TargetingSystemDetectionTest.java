import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetingSystemDetectionTest {

    @Test
    void capitalsDoNotGetInfiniteSpottingAgainstThePlayer() {
        FleetShip enemySupership = new FleetShip(ShipRole.SUPERSHIP, Faction.ENEMY, 0.0, 0.0);
        Player player = new Player(ShipRole.FRIGATE, 1750.0, 0.0);
        player.faction = Faction.ALLY;

        assertFalse(TargetingSystem.isDetectableToObserver(enemySupership, player),
                "capital ships should not retain map-wide awareness of the player");
    }

    @Test
    void scoutCraftSpotTargetsFartherOutThanHeavyShips() {
        FleetShip scout = new FleetShip(ShipRole.PATROL, Faction.ENEMY, 0.0, 0.0);
        FleetShip heavy = new FleetShip(ShipRole.SUPERSHIP, Faction.ENEMY, 0.0, 0.0);
        Player player = new Player(ShipRole.FRIGATE, 1700.0, 0.0);
        player.faction = Faction.ALLY;

        assertTrue(TargetingSystem.isDetectableToObserver(scout, player),
                "scouts should be able to acquire contacts that capital ships cannot");
        assertFalse(TargetingSystem.isDetectableToObserver(heavy, player),
                "heavy strike ships should rely on scouting instead of perfect vision");
    }
}
