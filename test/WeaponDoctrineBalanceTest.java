import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponDoctrineBalanceTest {

    @Test
    void beamBoltFamilyUsesTheActualBeamBoltSpeed() {
        FleetShip ship = new FleetShip(ShipRole.BATTLESHIP, Faction.ALLY, 0.0, 0.0);
        Turret gun = firstGun(ship);
        assertTrue(gun != null, "expected a primary gun on the battleship");

        gun.setReady();
        Projectile shot = gun.fire(ship, null, GameContext.DT);
        EnergyBolt bolt = assertInstanceOf(EnergyBolt.class, shot);

        assertTrue(bolt.isBeamBolt(), "beam-bolt family should render as the heavy beam projectile");
        assertEquals(Ship.BEAM_BOLT_SPEED, Math.hypot(bolt.vx, bolt.vy) / GameContext.DT, 1e-6);
    }

    @Test
    void beamBoltUsesOneSecondReloadAndRemembersItsTurretMount() {
        FleetShip ship = new FleetShip(ShipRole.BATTLESHIP, Faction.ALLY, 0.0, 0.0);
        Turret gun = firstGun(ship);
        assertTrue(gun != null, "expected a primary gun on the battleship");
        assertEquals(Ship.BEAM_BOLT_RELOAD_SECONDS, gun.cooldown, 1e-6);

        gun.setReady();
        Projectile shot = gun.fire(ship, null, GameContext.DT);
        EnergyBolt bolt = assertInstanceOf(EnergyBolt.class, shot);

        assertEquals(bolt.x, bolt.spawnX, 1e-6);
        assertEquals(bolt.y, bolt.spawnY, 1e-6);
        assertEquals(gun.localX, bolt.sourceTurretLocalX, 1e-6);
        assertEquals(gun.localY, bolt.sourceTurretLocalY, 1e-6);
    }

    @Test
    void blueMainBatteryReloadFloorsAtOneSecond() {
        FleetShip ship = new FleetShip(ShipRole.PICKET, Faction.ALLY, 0.0, 0.0);
        Turret gun = firstGun(ship);
        assertTrue(gun != null, "expected a primary gun on the picket");

        gun.setReady();
        Projectile first = gun.fire(ship, null, GameContext.DT);
        EnergyBolt bolt = assertInstanceOf(EnergyBolt.class, first);
        assertTrue(bolt.damage > gun.damage,
                "blue main batteries should hit harder after the reload floor");

        assertNull(gun.fire(ship, null, GameContext.DT),
                "blue main batteries should not fire multiple times inside one second");

        gun.update(0.95);
        assertNull(gun.fire(ship, null, GameContext.DT),
                "blue main batteries should still be cooling down before one second elapses");

        gun.update(0.10);
        assertTrue(gun.fire(ship, null, GameContext.DT) != null,
                "blue main batteries should be ready again after roughly one second");
    }

    @Test
    void teamCBeamDamageFallsOffAtLongRange() throws Exception {
        FleetShip beamShip = new FleetShip(ShipRole.BATTLESHIP, Faction.TEAM_C, 0.0, 0.0);
        Turret gun = firstGun(beamShip);
        assertTrue(gun != null, "expected a primary gun on the Team C battleship");

        PhaserBeam beam = new PhaserBeam(beamShip, gun, 0.0, 3200.0, 14.0, 90.0, 90, Faction.TEAM_C);
        Method scale = CollisionSystem.class.getDeclaredMethod("scaleBeamDamage", PhaserBeam.class, int.class, double.class);
        scale.setAccessible(true);

        int closeDamage = (Integer) scale.invoke(null, beam, 12, 0.20);
        int farDamage = (Integer) scale.invoke(null, beam, 12, 0.85);

        assertTrue(closeDamage > farDamage,
                "phaser damage should taper off over long range (close=" + closeDamage + ", far=" + farDamage + ")");
    }

    private static Turret firstGun(Ship ship) {
        if (ship == null || ship.turrets == null) return null;
        for (Turret turret : ship.turrets) {
            if (turret != null && turret.kind == Turret.Kind.GUN && turret.primary) {
                return turret;
            }
        }
        return null;
    }
}
