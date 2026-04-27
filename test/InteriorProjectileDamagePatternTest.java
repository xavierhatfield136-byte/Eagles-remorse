import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InteriorProjectileDamagePatternTest {

    @Test
    void bluePierceDamagesMultipleRoomsInALine() {
        FleetShip ship = doctrinalShip(ShipRole.FRIGATE, Faction.ALLY);

        ship.takeDamage(7, ship.x + ship.radius, ship.y, -1.0, 0.0, Ship.InteriorHitProfile.BLUE_PIERCE);

        assertTrue(damagedRoomCount(ship) >= 2, "blue piercing bolts should carry through multiple interior rooms");
    }

    @Test
    void lasersIgniteMultipleRoomsAlongBeamPath() {
        FleetShip ship = doctrinalShip(ShipRole.FRIGATE, Faction.TEAM_C);

        ship.takeDamage(6, ship.x + ship.radius, ship.y, -1.0, 0.0, Ship.InteriorHitProfile.LASER_LINE);

        assertTrue(damagedRoomCount(ship) >= 2, "laser hits should score multiple rooms along the beam path");
        assertTrue(burningRoomCount(ship) >= 2, "laser hits should leave a burning line through the ship");
    }

    @Test
    void missilesDamageMoreRoomsThanRedExplosiveRounds() {
        FleetShip redShip = doctrinalShip(ShipRole.FRIGATE, Faction.ENEMY);
        FleetShip missileShip = doctrinalShip(ShipRole.FRIGATE, Faction.ENEMY);

        redShip.takeDamage(7, redShip.x + redShip.radius, redShip.y, -1.0, 0.0, Ship.InteriorHitProfile.RED_EXPLOSIVE);
        missileShip.takeDamage(7, missileShip.x + missileShip.radius, missileShip.y, -1.0, 0.0, Ship.InteriorHitProfile.MISSILE_BLAST);

        assertTrue(damagedRoomCount(redShip) >= 2, "red explosive rounds should damage nearby rooms");
        assertTrue(damagedRoomCount(missileShip) > damagedRoomCount(redShip),
                "missiles should damage a wider set of rooms than red explosive rounds");
    }

    private static FleetShip doctrinalShip(ShipRole role, Faction faction) {
        FleetShip ship = new FleetShip(role, faction, 0.0, 0.0);
        DoctrineRegistry.applyToShip(ship);
        ship.resetShieldState();
        ship.shield = 0.0;
        ship.shieldActive = false;
        return ship;
    }

    private static int damagedRoomCount(Ship ship) {
        Set<ShipRoomLayout.RoomId> damaged = new HashSet<>();
        for (Ship.RoomDamageEvent event : ship.recentRoomDamageEvents()) {
            if (event != null && event.roomId != null && event.damage > 0.0) damaged.add(event.roomId);
        }
        return damaged.size();
    }

    private static int burningRoomCount(Ship ship) {
        int count = 0;
        for (Ship.RoomStatus room : ship.roomStatusSnapshot()) {
            if (room != null && room.fireIntensity > 0.05) count++;
        }
        return count;
    }
}
