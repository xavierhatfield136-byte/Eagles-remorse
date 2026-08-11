import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurretVisualMountRegressionTest {
    private static final Faction[] DISPLAY_FACTIONS = {
            Faction.ALLY,
            Faction.ENEMY,
            Faction.TEAM_C,
            Faction.TEAM_D
    };

    @Test
    void everyShipTurretMountTouchesVisibleHullArt() {
        Ship.enableDeterministicRandom(314159L);
        try {
            for (Faction faction : DISPLAY_FACTIONS) {
                for (ShipRole role : ShipRole.values()) {
                    FleetShip ship = new FleetShip(role, faction, 0.0, 0.0);
                    assertFalse(ship.turrets.isEmpty(), "expected at least one weapon mount on " + faction + " " + role);
                    for (Turret turret : ship.turrets) {
                        assertTrue(ShipHullSilhouette.visualHullContains(
                                        role, ship.radius, faction, turret.localX, turret.localY),
                                faction + " " + role + " turret center is not on visible hull art at "
                                        + turret.localX + "," + turret.localY);
                    }
                }
            }
        } finally {
            Ship.disableDeterministicRandom();
        }
    }

    @Test
    void smallMultiWeaponShipsDoNotStackEveryMountOnCenterline() {
        ShipRole[] roles = {
                ShipRole.STEALTH_SHIP,
                ShipRole.BOMBER,
                ShipRole.PD_CRAFT,
                ShipRole.FRIGATE,
                ShipRole.MISSILE_BOAT,
                ShipRole.CIWS_CORVETTE
        };

        Ship.enableDeterministicRandom(271828L);
        try {
            for (Faction faction : DISPLAY_FACTIONS) {
                for (ShipRole role : roles) {
                    FleetShip ship = new FleetShip(role, faction, 0.0, 0.0);
                    double minY = Double.POSITIVE_INFINITY;
                    double maxY = Double.NEGATIVE_INFINITY;
                    for (Turret turret : ship.turrets) {
                        if (turret == null) continue;
                        minY = Math.min(minY, turret.localY);
                        maxY = Math.max(maxY, turret.localY);
                    }
                    assertTrue((maxY - minY) >= Math.max(4.0, ship.radius * 0.18),
                            faction + " " + role + " should spread small-ship mounts across the hull");
                }
            }
        } finally {
            Ship.disableDeterministicRandom();
        }
    }
}
