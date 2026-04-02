import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SupershipLayoutRegressionTest {

    @Test
    void supershipGunTurretsFormCenterlineBattery() {
        FleetShip ship = new FleetShip(ShipRole.SUPERSHIP, Faction.ALLY, 0.0, 0.0);
        int gunCount = 0;
        for (Turret turret : ship.turrets) {
            if (turret == null || turret.kind != Turret.Kind.GUN) continue;
            gunCount++;
            assertTrue(Math.abs(turret.localY) <= 1.0,
                    "expected centerline gun mount, got y=" + turret.localY);
        }
        assertTrue(gunCount >= 4, "expected full supership gun battery");
    }
}
