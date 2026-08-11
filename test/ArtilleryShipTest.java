import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtilleryShipTest {

    @Test
    void artilleryShipUsesBattleshipGradePrimaryGun() {
        FleetShip artillery = new FleetShip(ShipRole.ARTILLERY_SHIP, Faction.ALLY, 0.0, 0.0);
        FleetShip battleship = new FleetShip(ShipRole.BATTLESHIP, Faction.ALLY, 0.0, 0.0);

        Turret artilleryMain = strongestPrimaryGun(artillery);
        Turret battleshipMain = strongestPrimaryGun(battleship);

        assertNotNull(artilleryMain, "artillery ship should mount a primary gun");
        assertNotNull(battleshipMain, "battleship should mount a primary gun");
        assertEquals(1, artillery.turrets.size(), "artillery ship should stay focused around one spinal battery");
        assertEquals(battleshipMain.damage, artilleryMain.damage, "artillery gun should match battleship primary damage");
        assertEquals(battleshipMain.cooldown, artilleryMain.cooldown, 1e-9, "artillery gun should match battleship primary cycle");
        assertEquals(battleshipMain.bulletSpeed, artilleryMain.bulletSpeed, 1e-9, "artillery gun should match battleship projectile speed");
        assertEquals(battleshipMain.bulletLife, artilleryMain.bulletLife, "artillery gun should match battleship projectile life");
    }

    @Test
    void artilleryShipIsStarterTierGlassCannon() {
        FleetShip artillery = new FleetShip(ShipRole.ARTILLERY_SHIP, Faction.ALLY, 0.0, 0.0);
        FleetShip frigate = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 0.0, 0.0);
        FleetShip battleship = new FleetShip(ShipRole.BATTLESHIP, Faction.ALLY, 0.0, 0.0);

        assertEquals(0, SpawnSystem.requiredHangarTierForRole(ShipRole.ARTILLERY_SHIP),
                "artillery ship should be available at the first hangar tier");
        assertTrue(artillery.radius < battleship.radius, "artillery ship should remain a small hull");
        assertTrue(artillery.hpMax < frigate.hpMax, "artillery ship should trade durability for firepower");
        assertTrue(artillery.desiredSpeed >= frigate.desiredSpeed * 0.75,
                "artillery ship should still be mobile enough to kite");
    }

    @Test
    void redPlayerArtilleryShipCanFireAtCursorAfterHullSwap() {
        Player player = new Player(ShipRole.FRIGATE, 0.0, 0.0);
        player.faction = Faction.ENEMY;
        player.applyHull(ShipRole.ARTILLERY_SHIP, 0.0, 0.0);
        player.angle = Math.toRadians(90.0);
        for (Turret turret : player.turrets) {
            if (turret != null) turret.setReady();
        }

        List<Projectile> fired = player.firePrimary(900.0, 0.0, GameContext.DT);

        assertFalse(fired.isEmpty(), "red player artillery ship should fire even when the hull nose is not already aligned");
        assertTrue(fired.get(0) instanceof Bullet, "red player artillery should still fire kinetic cannon rounds");
    }

    private static Turret strongestPrimaryGun(Ship ship) {
        Turret best = null;
        for (Turret turret : ship.turrets) {
            if (turret == null || turret.kind != Turret.Kind.GUN || !turret.primary) continue;
            if (best == null || turret.damage > best.damage) best = turret;
        }
        return best;
    }
}
