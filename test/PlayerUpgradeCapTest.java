import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerUpgradeCapTest {

    @Test
    void tierZeroHullStopsAtConfiguredUpgradeCaps() {
        Player player = new Player(ShipRole.FRIGATE, 0.0, 0.0);

        assertEquals(2, player.maxHullPlatingUpgrades(), "starter hulls should only get two hull plating buys");
        assertEquals(2, player.maxShieldArrayUpgrades(), "starter shield hulls should only get two shield buys");
        assertEquals(1, player.maxExtraGunTurrets(), "starter hulls should only get one extra gun mount");
        assertEquals(1, player.maxExtraMissileRacks(), "starter hulls should only get one extra missile rack");

        for (int i = 0; i < player.maxHullPlatingUpgrades(); i++) {
            assertTrue(player.buyHullPlatingUpgrade(), "hull plating buy " + (i + 1) + " should succeed");
        }
        assertEquals(player.maxHullPlatingUpgrades(), player.getHullPlatingUpgradeLevel());
        assertFalse(player.buyHullPlatingUpgrade(), "hull plating should stop at its cap");

        for (int i = 0; i < player.maxShieldArrayUpgrades(); i++) {
            assertTrue(player.buyShieldArrayUpgrade(), "shield array buy " + (i + 1) + " should succeed");
        }
        assertEquals(player.maxShieldArrayUpgrades(), player.getShieldArrayUpgradeLevel());
        assertFalse(player.buyShieldArrayUpgrade(), "shield array should stop at its cap");

        int baseGunMounts = player.gunTurretCount();
        for (int i = 0; i < player.maxExtraGunTurrets(); i++) {
            assertTrue(player.addGunTurretUpgrade(), "gun turret buy " + (i + 1) + " should succeed");
        }
        assertEquals(baseGunMounts + player.maxExtraGunTurrets(), player.gunTurretCount());
        assertEquals(player.maxExtraGunTurrets(), player.getGunTurretUpgradeLevel());
        assertFalse(player.addGunTurretUpgrade(), "gun hardpoints should stop at their cap");

        int baseMissileRacks = player.missileRackCount();
        for (int i = 0; i < player.maxExtraMissileRacks(); i++) {
            assertTrue(player.addMissileRackUpgrade(), "missile rack buy " + (i + 1) + " should succeed");
        }
        assertEquals(baseMissileRacks + player.maxExtraMissileRacks(), player.missileRackCount());
        assertEquals(player.maxExtraMissileRacks(), player.getMissileRackUpgradeLevel());
        assertFalse(player.addMissileRackUpgrade(), "missile hardpoints should stop at their cap");
    }

    @Test
    void hullSwapResetsPerHullUpgradeTrackers() {
        Player player = new Player(ShipRole.FRIGATE, 0.0, 0.0);

        assertTrue(player.buyHullPlatingUpgrade(), "expected a hull plating upgrade before swapping hulls");
        assertTrue(player.buyShieldArrayUpgrade(), "expected a shield upgrade before swapping hulls");
        assertTrue(player.addGunTurretUpgrade(), "expected a gun hardpoint upgrade before swapping hulls");
        assertTrue(player.addMissileRackUpgrade(), "expected a missile hardpoint upgrade before swapping hulls");

        player.applyHull(ShipRole.BATTLECRUISER, 0.0, 0.0);

        assertEquals(0, player.getHullPlatingUpgradeLevel(), "new hull should start with fresh plating slots");
        assertEquals(0, player.getShieldArrayUpgradeLevel(), "new hull should start with fresh shield slots");
        assertEquals(0, player.getGunTurretUpgradeLevel(), "new hull should reset added gun hardpoints");
        assertEquals(0, player.getMissileRackUpgradeLevel(), "new hull should reset added missile hardpoints");
        assertTrue(player.canBuyHullPlatingUpgrade(), "new hull should be able to buy hull plating again");
        assertTrue(player.canAddGunTurretUpgrade(), "new hull should be able to add new gun mounts again");
        assertTrue(player.canAddMissileRackUpgrade(), "new hull should be able to add new missile racks again");
    }
}
