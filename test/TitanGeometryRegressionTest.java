import org.junit.jupiter.api.Test;

import java.awt.Polygon;
import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TitanGeometryRegressionTest {

    private static final ShipRole[] BLUE_CAPITAL_ROLES = {
            ShipRole.TRANSPORT_TITAN,
            ShipRole.BULWARK_TITAN,
            ShipRole.CARRIER_SUPPORT_TITAN,
            ShipRole.VANGUARD_TITAN,
            ShipRole.INTERDICTION_TITAN,
            ShipRole.COMMAND_INTEL_TITAN,
            ShipRole.BOARDING_RECOVERY_TITAN,
            ShipRole.ARTILLERY_TITAN,
            ShipRole.SHIELD_BASTION_TITAN,
            ShipRole.FLEET_TELEPORTER_TITAN,
            ShipRole.ELITE_SUPERSHIP_COMMAND_TITAN,
            ShipRole.MOBILE_STATION_TITAN,
            ShipRole.HYPERWEAPON_TITAN,
            ShipRole.MOTHERSHIP
    };

    private static final ShipRole[] LONGITUDINAL_SPREAD_FOCUS = {
            ShipRole.SHIELD_BASTION_TITAN,
            ShipRole.FLEET_TELEPORTER_TITAN,
            ShipRole.ELITE_SUPERSHIP_COMMAND_TITAN,
            ShipRole.MOBILE_STATION_TITAN,
            ShipRole.HYPERWEAPON_TITAN,
            ShipRole.MOTHERSHIP
    };

    @Test
    void blueTitanRoomCoverageTracksHullSilhouettes() {
        Ship.enableDeterministicRandom(1234L);
        try {
            for (ShipRole role : BLUE_CAPITAL_ROLES) {
                Polygon hull = ShipHullSilhouette.hullPolygon(role, 100.0, Faction.ALLY);
                assertNotNull(hull, "expected silhouette for " + role);

                Rectangle bounds = hull.getBounds();
                double halfW = Math.max(1.0, Math.max(Math.abs(bounds.getMinX()), Math.abs(bounds.getMaxX())));
                double halfH = Math.max(1.0, Math.max(Math.abs(bounds.getMinY()), Math.abs(bounds.getMaxY())));

                int inside = 0;
                int covered = 0;
                int samplesX = 140;
                int samplesY = 96;
                for (int ix = 0; ix <= samplesX; ix++) {
                    double nx = -1.0 + 2.0 * ix / (double) samplesX;
                    for (int iy = 0; iy <= samplesY; iy++) {
                        double ny = -1.0 + 2.0 * iy / (double) samplesY;
                        double px = (nx / 0.98) * halfW;
                        double py = (ny / 0.98) * halfH;
                        if (!hull.contains(px, py)) continue;
                        inside++;
                        if (ShipRoomLayout.visualRoomIdForHit(role, Faction.ALLY, nx, ny) != null) {
                            covered++;
                        }
                    }
                }

                assertTrue(inside > 0, "expected hull interior samples for " + role);
                double coverage = covered / (double) inside;
                assertTrue(coverage >= 0.74, role + " room coverage too sparse: " + coverage);
            }
        } finally {
            Ship.disableDeterministicRandom();
        }
    }

    @Test
    void silhouetteRelativeTitanTurretMountsStayOnHull() {
        Ship.enableDeterministicRandom(5678L);
        try {
            for (ShipRole role : BLUE_CAPITAL_ROLES) {
                FleetShip ship = new FleetShip(role, Faction.ALLY, 0.0, 0.0);
                assertTrue(!ship.turrets.isEmpty(), "expected turrets on " + role);
                for (Turret turret : ship.turrets) {
                    HullGeometry.ImpactSample sample = HullGeometry.sampleImpact(
                            ship, ship.x + turret.localX, ship.y + turret.localY);
                    assertNotNull(sample, "expected impact sample for " + role);
                    assertTrue(sample.onHull, role + " turret drifted off hull at "
                            + turret.localX + "," + turret.localY);
                }
            }
        } finally {
            Ship.disableDeterministicRandom();
        }
    }

    @Test
    void titanTurretLayoutsSpanBothFlanksWithoutStarboardBias() {
        Ship.enableDeterministicRandom(9012L);
        try {
            for (ShipRole role : BLUE_CAPITAL_ROLES) {
                FleetShip ship = new FleetShip(role, Faction.ALLY, 0.0, 0.0);
                double minY = Double.POSITIVE_INFINITY;
                double maxY = Double.NEGATIVE_INFINITY;
                double sumY = 0.0;
                int count = 0;
                for (Turret turret : ship.turrets) {
                    if (turret == null) continue;
                    minY = Math.min(minY, turret.localY);
                    maxY = Math.max(maxY, turret.localY);
                    sumY += turret.localY;
                    count++;
                }

                assertTrue(count >= 4, "expected capital hardpoints for " + role);
                assertTrue(minY < -4.0, role + " should place mounts on the port flank");
                assertTrue(maxY > 4.0, role + " should place mounts on the starboard flank");
                assertTrue((maxY - minY) >= ship.radius * 0.45, role + " mounts are still too clustered");
                assertTrue(Math.abs(sumY / count) <= ship.radius * 0.12,
                        role + " mount pattern still leans off-center");
            }
        } finally {
            Ship.disableDeterministicRandom();
        }
    }

    @Test
    void selectedCapitalLayoutsUseDistinctLongitudinalWeaponBands() {
        Ship.enableDeterministicRandom(2468L);
        try {
            for (ShipRole role : LONGITUDINAL_SPREAD_FOCUS) {
                FleetShip ship = new FleetShip(role, Faction.ALLY, 0.0, 0.0);
                java.util.ArrayList<Double> xs = new java.util.ArrayList<>();
                for (Turret turret : ship.turrets) {
                    if (turret == null) continue;
                    xs.add(turret.localX);
                }
                xs.sort(Double::compareTo);

                int bands = 0;
                double lastBandX = Double.NEGATIVE_INFINITY;
                double minGap = Math.max(7.5, ship.radius * 0.15);
                for (double x : xs) {
                    if (bands == 0 || Math.abs(x - lastBandX) >= minGap) {
                        bands++;
                        lastBandX = x;
                    }
                }

                int minBands = (role == ShipRole.HYPERWEAPON_TITAN) ? 2 : (role == ShipRole.MOTHERSHIP ? 4 : 3);
                assertTrue(bands >= minBands, role + " should use more distinct weapon bands along the hull");
                assertTrue((xs.get(xs.size() - 1) - xs.get(0)) >= ship.radius * 0.60,
                        role + " should spread hardpoints farther from aft to fore");
            }
        } finally {
            Ship.disableDeterministicRandom();
        }
    }
}
