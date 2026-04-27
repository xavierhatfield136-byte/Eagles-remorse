import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomOperationalThresholdTest {

    @Test
    void reactorRoomBelowThirtyPercentTriggersFullBlackout() {
        FleetShip ship = doctrinalShip(ShipRole.FRIGATE, Faction.ALLY);
        ShipRoomLayout.RoomDef reactor = ShipRoomLayout.roomForId(ship.role, ship.faction, ShipRoomLayout.RoomId.REACTOR);
        double wx = ship.x + avg(reactor.xs) * ship.radius;
        double wy = ship.y + avg(reactor.ys) * ship.radius;

        int guard = 0;
        while (ship.roomHealthFraction(ShipRoomLayout.RoomId.REACTOR) >= 0.30 && ship.alive && !ship.dying && guard++ < 120) {
            ship.takePenetratingInternalDamage(20, wx, wy, 0.0, 0.0);
        }
        ship.update(GameContext.DT);

        assertTrue(ship.roomHealthFraction(ShipRoomLayout.RoomId.REACTOR) < 0.30,
                "test should drive the reactor room below the operational threshold");
        assertTrue(ship.reactorBlackoutActive(), "reactor room below 30% should force a blackout");
        assertTrue(ship.isSystemDestroyed(Ship.InternalSystem.REACTOR_CORE),
                "reactor subsystem should be treated as offline once the reactor room is non-operational");
        assertTrue(!ship.canUseCombatSystems(), "blackout should disable combat systems entirely");
        assertEquals(0.0, ship.desiredSpeed, 1e-6, "blackout should drop propulsion to zero");
    }

    private static FleetShip doctrinalShip(ShipRole role, Faction faction) {
        FleetShip ship = new FleetShip(role, faction, 0.0, 0.0);
        DoctrineRegistry.applyToShip(ship);
        ship.resetShieldState();
        ship.shield = 0.0;
        ship.shieldActive = false;
        return ship;
    }

    private static double avg(double[] values) {
        if (values == null || values.length == 0) return 0.0;
        double sum = 0.0;
        for (double value : values) sum += value;
        return sum / values.length;
    }
}
