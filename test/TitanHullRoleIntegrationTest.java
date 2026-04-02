import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TitanHullRoleIntegrationTest {

    @Test
    void titanArchetypesMapToRealShipRoles() {
        for (TitanArchetype archetype : TitanArchetype.values()) {
            ShipRole role = archetype.shipRole();
            assertTrue(role.isTitan(), archetype + " should map to a Titan ship role");
            assertEquals(archetype, TitanArchetype.fromShipRole(role));
        }
        assertNull(TitanArchetype.fromShipRole(ShipRole.MOTHERSHIP));
    }

    @Test
    void titanRolesBuildWithExpectedSystems() {
        FleetShip carrierSupport = new FleetShip(ShipRole.CARRIER_SUPPORT_TITAN, Faction.ALLY, 0, 0);
        FleetShip hyperweapon = new FleetShip(ShipRole.HYPERWEAPON_TITAN, Faction.ALLY, 0, 0);
        FleetShip mothership = new FleetShip(ShipRole.MOTHERSHIP, Faction.ALLY, 0, 0);
        FleetShip transportTitan = new FleetShip(ShipRole.TRANSPORT_TITAN, Faction.ALLY, 0, 0);

        assertTrue(carrierSupport.isCarrier);
        assertTrue(carrierSupport.maxFighters >= 14);
        assertTrue(carrierSupport.repairRange >= 400.0);

        assertTrue(hyperweapon.hasSuperweapon);
        assertFalse(hyperweapon.isCarrier);

        assertTrue(mothership.isCarrier);
        assertTrue(mothership.maxFighters >= 18);
        assertTrue(mothership.repairRange >= 500.0);
        assertTrue(mothership.radius > carrierSupport.radius);

        assertTrue(transportTitan.hasCIWS);
        assertTrue(transportTitan.cargoMax >= 900);
        assertTrue(transportTitan.repairShieldPerSec > 0.0);
    }

    @Test
    void titanHullSilhouettesResolveForLiveBlueSkins() {
        for (TitanArchetype archetype : TitanArchetype.values()) {
            ShipRole role = archetype.shipRole();
            FleetShip ship = new FleetShip(role, Faction.ALLY, 0, 0);
            java.awt.Polygon poly = ShipHullSilhouette.hullPolygon(role, ship.radius, Faction.ALLY);
            assertTrue(poly != null && poly.npoints >= 3, "expected hull polygon for " + role);
        }

        FleetShip mothership = new FleetShip(ShipRole.MOTHERSHIP, Faction.ALLY, 0, 0);
        java.awt.Polygon poly = ShipHullSilhouette.hullPolygon(ShipRole.MOTHERSHIP, mothership.radius, Faction.ALLY);
        assertTrue(poly != null && poly.npoints >= 3, "expected hull polygon for MOTHERSHIP");
    }
}
