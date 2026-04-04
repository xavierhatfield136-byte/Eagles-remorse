import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.File;
import java.util.Iterator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TitanHullRoleIntegrationTest {
    private static final String SKIN_DIR = "assets/ship_skins";

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

    @Test
    void titanHullSilhouettesResolveForLiveGreenSkins() {
        for (TitanArchetype archetype : TitanArchetype.values()) {
            ShipRole role = archetype.shipRole();
            FleetShip ship = new FleetShip(role, Faction.TEAM_C, 0, 0);
            java.awt.Polygon poly = ShipHullSilhouette.hullPolygon(role, ship.radius, Faction.TEAM_C);
            assertTrue(poly != null && poly.npoints >= 3, "expected Team C hull polygon for " + role);
        }

        FleetShip mothership = new FleetShip(ShipRole.MOTHERSHIP, Faction.TEAM_C, 0, 0);
        java.awt.Polygon poly = ShipHullSilhouette.hullPolygon(ShipRole.MOTHERSHIP, mothership.radius, Faction.TEAM_C);
        assertTrue(poly != null && poly.npoints >= 3, "expected Team C hull polygon for MOTHERSHIP");
    }

    @Test
    void titanHullSilhouettesResolveForLiveRedSkins() {
        for (TitanArchetype archetype : TitanArchetype.values()) {
            ShipRole role = archetype.shipRole();
            FleetShip ship = new FleetShip(role, Faction.ENEMY, 0, 0);
            java.awt.Polygon poly = ShipHullSilhouette.hullPolygon(role, ship.radius, Faction.ENEMY);
            assertTrue(poly != null && poly.npoints >= 3, "expected Team B hull polygon for " + role);
        }

        FleetShip mothership = new FleetShip(ShipRole.MOTHERSHIP, Faction.ENEMY, 0, 0);
        java.awt.Polygon poly = ShipHullSilhouette.hullPolygon(ShipRole.MOTHERSHIP, mothership.radius, Faction.ENEMY);
        assertTrue(poly != null && poly.npoints >= 3, "expected Team B hull polygon for MOTHERSHIP");
    }

    @Test
    void titanHullSilhouettesResolveForLiveYellowSkins() {
        for (TitanArchetype archetype : TitanArchetype.values()) {
            ShipRole role = archetype.shipRole();
            FleetShip ship = new FleetShip(role, Faction.TEAM_D, 0, 0);
            java.awt.Polygon poly = ShipHullSilhouette.hullPolygon(role, ship.radius, Faction.TEAM_D);
            assertTrue(poly != null && poly.npoints >= 3, "expected Team D hull polygon for " + role);
        }

        FleetShip mothership = new FleetShip(ShipRole.MOTHERSHIP, Faction.TEAM_D, 0, 0);
        java.awt.Polygon poly = ShipHullSilhouette.hullPolygon(ShipRole.MOTHERSHIP, mothership.radius, Faction.TEAM_D);
        assertTrue(poly != null && poly.npoints >= 3, "expected Team D hull polygon for MOTHERSHIP");
    }

    @Test
    void liveGreenTitanRuntimeSkinsUseSquareCanvases() throws Exception {
        for (TitanArchetype archetype : TitanArchetype.values()) {
            String filename = archetype.shipRole().name().toLowerCase() + "_team_c_albedo.png";
            assertSquareSkin(filename, "Team C");
        }
        assertSquareSkin("mothership_team_c_albedo.png", "Team C");
    }

    @Test
    void liveRedTitanRuntimeSkinsUseSquareCanvases() throws Exception {
        for (TitanArchetype archetype : TitanArchetype.values()) {
            String filename = archetype.shipRole().name().toLowerCase() + "_enemy_albedo.png";
            assertSquareSkin(filename, "Team B");
        }
        assertSquareSkin("mothership_enemy_albedo.png", "Team B");
    }

    @Test
    void liveYellowTitanRuntimeSkinsUseSquareCanvases() throws Exception {
        for (TitanArchetype archetype : TitanArchetype.values()) {
            String filename = archetype.shipRole().name().toLowerCase() + "_team_d_albedo.png";
            assertSquareSkin(filename, "Team D");
        }
        assertSquareSkin("mothership_team_d_albedo.png", "Team D");
    }

    private static void assertSquareSkin(String filename, String label) throws Exception {
        File file = new File(SKIN_DIR, filename);
        assertTrue(file.isFile(), "missing " + label + " runtime skin " + filename);
        try (ImageInputStream in = ImageIO.createImageInputStream(file)) {
            assertTrue(in != null, "failed to open " + label + " runtime skin " + filename);
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            assertTrue(readers.hasNext(), "no reader for " + label + " runtime skin " + filename);
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                assertEquals(reader.getWidth(0), reader.getHeight(0),
                        "expected square runtime canvas for " + filename);
            } finally {
                reader.dispose();
            }
        }
    }
}
