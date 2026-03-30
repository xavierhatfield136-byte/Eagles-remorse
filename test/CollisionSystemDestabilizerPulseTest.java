import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollisionSystemDestabilizerPulseTest {

    @AfterEach
    void clearEffects() {
        Explosion.active.clear();
    }

    @Test
    void destabilizerPulseDoesNotDamageNearbyAllies() {
        FleetShip shooter = new FleetShip(ShipRole.SUPERSHIP, Faction.ALLY, -420.0, 0.0);
        FleetShip ally = new FleetShip(ShipRole.CARRIER, Faction.ALLY, 72.0, 0.0);
        FleetShip enemy = new FleetShip(ShipRole.CARRIER, Faction.ENEMY, 0.0, 0.0);

        double allyShieldBefore = ally.shield;
        int allyHpBefore = ally.hp;

        DestabilizerPulse pulse = pulseAt(enemy.x, enemy.y, shooter.id, shooter.faction, 48, 36.0, 8.0);
        List<Projectile> projectiles = new ArrayList<>(List.of(pulse));
        List<Ship> ships = new ArrayList<>(List.of(ally, enemy, shooter));

        CollisionSystem.handleProjectilesVsShips(null, projectiles, ships);

        assertEquals(allyHpBefore, ally.hp);
        assertEquals(allyShieldBefore, ally.shield, 1e-6);
        assertFalse(ally.isDestabilized());
        assertEquals(0.0, ally.getTemporaryDisableRemaining(), 1e-6);
    }

    @Test
    void destabilizerPulseDirectHitStripsShieldAndDestabilizesTarget() {
        FleetShip shooter = new FleetShip(ShipRole.SUPERSHIP, Faction.ALLY, -420.0, 0.0);
        FleetShip enemy = new FleetShip(ShipRole.CARRIER, Faction.ENEMY, 0.0, 0.0);

        double shieldBefore = enemy.shield;
        int hpBefore = enemy.hp;

        DestabilizerPulse pulse = pulseAt(enemy.x, enemy.y, shooter.id, shooter.faction, 72, 64.0, 10.0);
        List<Projectile> projectiles = new ArrayList<>(List.of(pulse));
        List<Ship> ships = new ArrayList<>(List.of(enemy, shooter));

        CollisionSystem.handleProjectilesVsShips(null, projectiles, ships);

        assertTrue(enemy.shield < shieldBefore, "direct pulse hit should strip shield capacity");
        assertTrue(enemy.hp < hpBefore, "direct pulse hit should deal penetrating hull damage");
        assertTrue(enemy.isDestabilized(), "direct pulse hit should destabilize the target");
        assertTrue(enemy.getTemporaryDisableRemaining() > 0.0, "direct pulse hit should briefly disable the target");
    }

    private static DestabilizerPulse pulseAt(double x,
                                             double y,
                                             int sourceShipId,
                                             Faction faction,
                                             int hullDamage,
                                             double shieldDamage,
                                             double destabilizeSeconds) {
        DestabilizerPulse pulse = new DestabilizerPulse(
                x,
                y,
                0.0,
                GameContext.DT,
                1000.0,
                hullDamage,
                90,
                20.0,
                360.0,
                shieldDamage,
                destabilizeSeconds,
                faction
        );
        pulse.sourceShipId = sourceShipId;
        return pulse;
    }
}
