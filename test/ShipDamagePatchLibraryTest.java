import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipDamagePatchLibraryTest {

    @Test
    void curatedDamagePatchFamiliesLoadForEveryFactionAndFallback() {
        assertTrue(ShipDamagePatchLibrary.hasAnyPatch());
        for (Faction faction : Faction.values()) {
            assertSelectionLoads(faction);
        }
        assertSelectionLoads(null);
    }

    @Test
    void damagePatchSelectionIsDeterministicForStableImpactData() {
        ShipDamagePatchLibrary.Selection first =
                ShipDamagePatchLibrary.select(Faction.TEAM_C, 12.5, -8.25, 0.72, 3);
        ShipDamagePatchLibrary.Selection second =
                ShipDamagePatchLibrary.select(Faction.TEAM_C, 12.5, -8.25, 0.72, 3);

        assertNotNull(first);
        assertNotNull(second);
        assertSame(first.image, second.image);
        assertTrue(first.quarterTurns == second.quarterTurns);
        assertTrue(first.flipX == second.flipX);
    }

    @Test
    void multipartFleetWorkingSetDoesNotDecodeImagesAgainOnSecondPass() {
        ShipPartLibrary.clearCachesForTest();
        try {
            ShipRole[] roles = {
                    ShipRole.FRIGATE, ShipRole.CRUISER, ShipRole.BATTLESHIP,
                    ShipRole.CARRIER, ShipRole.MINER, ShipRole.PATROL
            };
            Faction[] factions = {Faction.ALLY, Faction.ENEMY};
            ShipPartLibrary.Variant[] variants = {
                    ShipPartLibrary.Variant.NORMAL,
                    ShipPartLibrary.Variant.DAMAGED,
                    ShipPartLibrary.Variant.CRITICAL
            };

            loadMultipartWorkingSet(roles, factions, variants);
            int firstPassDecodes = ShipPartLibrary.imageDecodeCount();
            assertTrue(firstPassDecodes > 0);

            loadMultipartWorkingSet(roles, factions, variants);
            assertTrue(ShipPartLibrary.imageDecodeCount() == firstPassDecodes,
                    "active fleet multipart sprites should stay cached between rendered frames");
        } finally {
            ShipPartLibrary.clearCachesForTest();
        }
    }

    @Test
    void repeatedFleetRenderingDoesNotDecodeMultipartImagesAgain() {
        ShipPartLibrary.clearCachesForTest();
        ShipRole[] roles = {
                ShipRole.FRIGATE, ShipRole.CRUISER, ShipRole.BATTLESHIP,
                ShipRole.CARRIER, ShipRole.MINER, ShipRole.PATROL
        };
        Faction[] factions = {Faction.ALLY, Faction.ENEMY};
        List<Ship> ships = new ArrayList<>();
        for (int i = 0; i < roles.length; i++) {
            for (int j = 0; j < factions.length; j++) {
                FleetShip ship = new FleetShip(roles[i], factions[j], 80.0 + i * 100.0, 80.0 + j * 130.0);
                if ((i + j) % 3 == 1) ship.hp = Math.max(1, (int) Math.round(ship.hpMax * 0.55));
                if ((i + j) % 3 == 2) ship.hp = Math.max(1, (int) Math.round(ship.hpMax * 0.25));
                ships.add(ship);
            }
        }

        BufferedImage canvas = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = canvas.createGraphics();
        try {
            Renderer.drawShips(g2, ships);
            int firstFrameDecodes = ShipPartLibrary.imageDecodeCount();
            assertTrue(firstFrameDecodes > 0);
            for (int i = 0; i < 5; i++) Renderer.drawShips(g2, ships);
            assertTrue(ShipPartLibrary.imageDecodeCount() == firstFrameDecodes,
                    "repainting the same fleet must not reload multipart sprites");
        } finally {
            g2.dispose();
            canvas.flush();
            ShipPartLibrary.clearCachesForTest();
        }
    }

    private static void loadMultipartWorkingSet(ShipRole[] roles,
                                                Faction[] factions,
                                                ShipPartLibrary.Variant[] variants) {
        for (ShipRole role : roles) {
            for (Faction faction : factions) {
                for (ShipPartLibrary.Variant variant : variants) {
                    ShipPartLibrary.getSet(role, faction, variant);
                }
            }
        }
    }

    private static void assertSelectionLoads(Faction faction) {
        ShipDamagePatchLibrary.Selection selection =
                ShipDamagePatchLibrary.select(faction, 4.0, -3.0, 0.45, 1);
        assertNotNull(selection);
        assertNotNull(selection.image);
        assertTrue(selection.image.getWidth() > 0);
        assertTrue(selection.image.getHeight() > 0);
    }
}
