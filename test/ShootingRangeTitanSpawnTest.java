import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShootingRangeTitanSpawnTest {

    @Test
    void shootingRangeTitanLayoutSpawnsCarrierGroupWithoutTierDowngrades() {
        GameContext ctx = shootingRangeContext();

        SpawnSystem.initWorld(ctx);

        assertTrue(SpawnSystem.setShootingRangeTitanLayout(ctx, TitanArchetype.CARRIER_SUPPORT));
        assertEquals(TitanArchetype.CARRIER_SUPPORT, ctx.command.shootingRangeTitanArchetype);
        assertEquals(11, nonPlayerShips(ctx).size());
        assertRoleCountAtLeast(ctx, ShipRole.CARRIER_SUPPORT_TITAN, 1);
        assertRoleCountAtLeast(ctx, ShipRole.DRONE_CARRIER, 1);
        assertRoleCountAtLeast(ctx, ShipRole.FIGHTER, 1);
        assertRoleCountAtLeast(ctx, ShipRole.BOMBER, 1);
    }

    @Test
    void shootingRangeTitanLayoutKeepsSelectionWhenFactionChanges() {
        GameContext ctx = shootingRangeContext();

        SpawnSystem.initWorld(ctx);
        assertTrue(SpawnSystem.setShootingRangeTitanLayout(ctx, TitanArchetype.ELITE_SUPERSHIP_COMMAND));
        assertTrue(SpawnSystem.setShootingRangeTargetFaction(ctx, Faction.TEAM_C));

        List<Ship> targets = nonPlayerShips(ctx);
        assertEquals(TitanArchetype.ELITE_SUPERSHIP_COMMAND, ctx.command.shootingRangeTitanArchetype);
        assertEquals(6, targets.size());
        assertTrue(targets.stream().allMatch(s -> s.faction == Faction.TEAM_C));
        assertRoleCountAtLeast(ctx, ShipRole.SUPERSHIP, 5);
    }

    @Test
    void shootingRangeTitanLayoutSupportsEliteReinforcementPackage() {
        GameContext ctx = shootingRangeContext();

        SpawnSystem.initWorld(ctx);
        assertTrue(SpawnSystem.setShootingRangeTitanLayout(ctx, TitanArchetype.ELITE_REINFORCEMENTS));

        assertEquals(TitanArchetype.ELITE_REINFORCEMENTS, ctx.command.shootingRangeTitanArchetype);
        assertRoleCountAtLeast(ctx, ShipRole.ELITE_REINFORCEMENTS_TITAN, 1);
        assertRoleCountAtLeast(ctx, ShipRole.BATTLESHIP, 1);
        assertRoleCountAtLeast(ctx, ShipRole.BATTLECRUISER, 1);
        assertRoleCountAtLeast(ctx, ShipRole.CIWS_CORVETTE, 1);
    }

    @Test
    void shootingRangeTitanLayoutCanResetToDefaultWall() {
        GameContext ctx = shootingRangeContext();

        SpawnSystem.initWorld(ctx);
        assertTrue(SpawnSystem.setShootingRangeTitanLayout(ctx, TitanArchetype.HYPERWEAPON));
        assertTrue(SpawnSystem.clearShootingRangeTitanLayout(ctx));

        assertNull(ctx.command.shootingRangeTitanArchetype);
        assertTrue(nonPlayerShips(ctx).size() > 20);
        assertRoleCountAtLeast(ctx, ShipRole.BASE, 1);
        assertRoleCountAtLeast(ctx, ShipRole.STATIC_TURRET, 2);
    }

    private static GameContext shootingRangeContext() {
        return new GameContext(new GameConfig(GameMode.SHOOTING_RANGE, 5000, 5000, true, 1234L, false));
    }

    private static List<Ship> nonPlayerShips(GameContext ctx) {
        return ctx.ships.stream()
                .filter(s -> s != null && s != ctx.player)
                .toList();
    }

    private static void assertRoleCountAtLeast(GameContext ctx, ShipRole role, int minCount) {
        long count = nonPlayerShips(ctx).stream()
                .filter(s -> s.role == role)
                .count();
        assertTrue(count >= minCount, "expected at least " + minCount + " " + role + " targets but found " + count);
    }
}
