import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void sustainedEngagementRangeUsesSensorContactForCapitals() {
        FleetShip mothership = new FleetShip(ShipRole.MOTHERSHIP, Faction.ALLY, 0.0, 0.0);
        FleetShip enemyCruiser = new FleetShip(ShipRole.CRUISER, Faction.ENEMY, 1000.0, 0.0);

        double sustainedRange = AISystem.sustainedEngagementRangeForTarget(null, mothership, enemyCruiser);
        double baseGunRange = 720.0;
        double practicalGunRange = AISystem.effectivePrimaryGunRangeAgainstTarget(mothership, enemyCruiser, baseGunRange);

        assertTrue(TargetingSystem.isDetectableToObserver(mothership, enemyCruiser),
                "the hostile should be inside the mothership's sensor envelope");
        assertTrue(sustainedRange >= 1000.0,
                "sensor contact should keep the target in the sustained engagement envelope");
        assertTrue(sustainedRange > practicalGunRange,
                "the sustained engagement envelope should extend beyond the coarse gun range gate");
    }

    @Test
    void fighterOnlyTeamsDoNotFormUpAroundAFighterFlagship() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 2400, 1800, true, 44L, false));
        FleetShip friendlyOne = new FleetShip(ShipRole.FIGHTER, Faction.ALLY, 400.0, 900.0);
        FleetShip friendlyTwo = new FleetShip(ShipRole.FIGHTER, Faction.ALLY, 430.0, 940.0);
        FleetShip hostile = new FleetShip(ShipRole.FIGHTER, Faction.ENEMY, 610.0, 905.0);
        ctx.ships.clear();
        ctx.ships.add(friendlyOne);
        ctx.ships.add(friendlyTwo);
        ctx.ships.add(hostile);
        ctx.entityQuery.rebuild(ctx);

        AISystem.update(ctx, 1.0 / 60.0);

        assertFalse(ctx.command.fleetCommandShips.containsKey(Faction.ALLY),
                "fighter-only teams should not elect a fighter as fleet command anchor");
        assertTrue(friendlyOne.aiCommittedTargetId == hostile.id || friendlyTwo.aiCommittedTargetId == hostile.id,
                "at least one friendly fighter should commit to the hostile instead of circling a friendly fighter");
        assertTrue(Math.hypot(friendlyOne.vx, friendlyOne.vy) > 0.0001
                        || Math.hypot(friendlyTwo.vx, friendlyTwo.vy) > 0.0001,
                "friendly fighters should keep moving without a friendly capital ship");
    }

    @Test
    void fighterCommitsToHostileSmallCraftBeforeLargerTargets() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 2600, 1800, true, 45L, false));
        FleetShip fighter = new FleetShip(ShipRole.FIGHTER, Faction.ALLY, 500.0, 900.0);
        FleetShip bomber = new FleetShip(ShipRole.BOMBER, Faction.ENEMY, 760.0, 900.0);
        FleetShip frigate = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 650.0, 900.0);
        ctx.ships.clear();
        ctx.ships.add(fighter);
        ctx.ships.add(bomber);
        ctx.ships.add(frigate);
        ctx.entityQuery.rebuild(ctx);

        AISystem.update(ctx, 1.0 / 60.0);

        assertEquals(bomber.id, fighter.aiCommittedTargetId,
                "fighters should aggressively intercept hostile small craft before heavier hulls");
    }

    @Test
    void opposingFightersFireOrRepositionInsteadOfOrbitingIndefinitely() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 2600, 1800, true, 46L, false));
        FleetShip blue = new FleetShip(ShipRole.FIGHTER, Faction.ALLY, 900.0, 900.0);
        FleetShip red = new FleetShip(ShipRole.FIGHTER, Faction.ENEMY, 1180.0, 900.0);
        ctx.ships.clear();
        ctx.ships.add(blue);
        ctx.ships.add(red);
        ctx.entityQuery.rebuild(ctx);

        double initialDistance = Math.hypot(blue.x - red.x, blue.y - red.y);
        int blueHp = blue.hp;
        int redHp = red.hp;
        double blueShield = blue.shield;
        double redShield = red.shield;

        for (int i = 0; i < 8 * 60; i++) {
            AISystem.update(ctx, 1.0 / 60.0);
            for (Ship ship : ctx.ships) ship.update(1.0 / 60.0);
            ctx.entityQuery.rebuild(ctx);
        }

        double finalDistance = Math.hypot(blue.x - red.x, blue.y - red.y);
        boolean damageExchanged = blue.hp < blueHp || red.hp < redHp || blue.shield < blueShield || red.shield < redShield;
        boolean deliberateReposition = Math.abs(finalDistance - initialDistance) >= 48.0
                && (blue.aiCommittedTargetId == red.id || red.aiCommittedTargetId == blue.id);
        assertTrue(damageExchanged || deliberateReposition,
                "opposing fighters must fire, disengage, or deliberately reposition instead of orbiting indefinitely");
    }
}
