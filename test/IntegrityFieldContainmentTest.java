import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrityFieldContainmentTest {

    @Test
    void singleFireRoomStaysContainedWhileIntegrityFieldIsOperational() {
        Ship.enableDeterministicRandom(1234L);
        try {
            FleetShip ship = doctrinalShip(ShipRole.FRIGATE, Faction.ALLY);
            ship.seedRoomFire(ShipRoomLayout.RoomId.REACTOR, 1.35);

            for (int i = 0; i < 180; i++) {
                ship.update(GameContext.DT);
            }

            assertTrue(ship.activeFireRoomCount() <= 1,
                    "a lone burning room should stay contained by the integrity field");
            assertEquals(0.0, ship.roomFireIntensity(ShipRoomLayout.RoomId.PORT_POWER), 1e-6,
                    "contained fire should not spread into neighboring rooms");
            assertEquals(1.0, ship.roomHealthFraction(ShipRoomLayout.RoomId.PORT_POWER), 1e-6,
                    "contained DOT should not chew through neighboring rooms");
        } finally {
            Ship.disableDeterministicRandom();
        }
    }

    @Test
    void singleDamagedRoomGetsAutomaticIntegrityProtection() {
        FleetShip protectedShip = doctrinalShip(ShipRole.FRIGATE, Faction.ALLY);
        FleetShip unprotectedShip = doctrinalShip(ShipRole.FRIGATE, Faction.ALLY);

        crippleIntegrityField(unprotectedShip);

        ShipRoomLayout.RoomDef reactorProtected =
                ShipRoomLayout.roomForId(protectedShip.role, protectedShip.faction, ShipRoomLayout.RoomId.REACTOR);
        ShipRoomLayout.RoomDef reactorUnprotected =
                ShipRoomLayout.roomForId(unprotectedShip.role, unprotectedShip.faction, ShipRoomLayout.RoomId.REACTOR);

        protectedShip.takePenetratingInternalDamage(18,
                roomCenterX(protectedShip, reactorProtected),
                roomCenterY(protectedShip, reactorProtected),
                0.0, 0.0);
        unprotectedShip.takePenetratingInternalDamage(18,
                roomCenterX(unprotectedShip, reactorUnprotected),
                roomCenterY(unprotectedShip, reactorUnprotected),
                0.0, 0.0);

        assertTrue(protectedShip.roomHealthFraction(ShipRoomLayout.RoomId.REACTOR)
                        > unprotectedShip.roomHealthFraction(ShipRoomLayout.RoomId.REACTOR) + 0.05,
                "the integrity field should automatically blunt damage on the only compromised room");
    }

    private static void crippleIntegrityField(FleetShip ship) {
        ShipRoomLayout.RoomDef integrity =
                ShipRoomLayout.roomForId(ship.role, ship.faction, ShipRoomLayout.RoomId.INTEGRITY_FIELD);
        int guard = 0;
        while (ship.roomHealthFraction(ShipRoomLayout.RoomId.INTEGRITY_FIELD) >= 0.30 && guard++ < 120) {
            ship.takePenetratingInternalDamage(20,
                    roomCenterX(ship, integrity),
                    roomCenterY(ship, integrity),
                    0.0, 0.0);
        }
        ship.update(GameContext.DT);
    }

    private static FleetShip doctrinalShip(ShipRole role, Faction faction) {
        FleetShip ship = new FleetShip(role, faction, 0.0, 0.0);
        DoctrineRegistry.applyToShip(ship);
        ship.resetShieldState();
        ship.shield = 0.0;
        ship.shieldActive = false;
        return ship;
    }

    private static double roomCenterX(Ship ship, ShipRoomLayout.RoomDef room) {
        return ship.x + avg(room.xs) * ship.radius;
    }

    private static double roomCenterY(Ship ship, ShipRoomLayout.RoomDef room) {
        return ship.y + avg(room.ys) * ship.radius;
    }

    private static double avg(double[] values) {
        if (values == null || values.length == 0) return 0.0;
        double sum = 0.0;
        for (double value : values) sum += value;
        return sum / values.length;
    }
}
