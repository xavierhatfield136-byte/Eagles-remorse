import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AISystemSmallCraftRangeTest {

    @Test
    void ciwsDogfightRangeUsesPracticalPelletReach() {
        FleetShip fighter = new FleetShip(ShipRole.FIGHTER, Faction.ALLY, 0.0, 0.0);
        FleetShip hostile = new FleetShip(ShipRole.FIGHTER, Faction.ENEMY, 280.0, 0.0);
        Turret gun = fighter.turrets.getFirst();

        double baseGunRange = 720.0 * 0.82;
        double allowed = AISystem.effectiveGunRangeForTarget(fighter, gun, hostile, baseGunRange);

        assertTrue(allowed < 300.0, "fighter anti-fighter fire should be clamped well inside generic gun range");
        assertTrue(allowed < baseGunRange, "CIWS-style dogfight fire should not use the coarse shared gun range");
    }

    @Test
    void standardGunRangeRemainsUnchangedAgainstLargerTargets() {
        FleetShip fighter = new FleetShip(ShipRole.FIGHTER, Faction.ALLY, 0.0, 0.0);
        FleetShip frigate = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 500.0, 0.0);
        Turret gun = fighter.turrets.getFirst();

        double baseGunRange = 720.0 * 0.82;
        double allowed = AISystem.effectiveGunRangeForTarget(fighter, gun, frigate, baseGunRange);

        assertEquals(baseGunRange, allowed, 0.001, "non-dogfight gun engagements should keep their normal range gate");
    }
}
