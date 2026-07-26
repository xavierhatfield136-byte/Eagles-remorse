import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TacticalCombatDepthSystemTest {
    @Test
    void groupOrdersWaitForAcknowledgmentAndThenReachTheFleetAi() {
        GameContext ctx = context();
        Player player = new Player(0.0, 0.0);
        FleetShip escort = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 160.0, 0.0);
        ctx.player = player;
        ctx.ships.add(player);
        ctx.ships.add(escort);
        ctx.cursorWorldX = escort.x;
        ctx.cursorWorldY = escort.y;
        TacticalCombatDepthSystem.init(ctx);

        TacticalCombatDepthSystem.selectNearestFriendlyIntoActiveGroup(ctx);
        TacticalCombatDepthSystem.issueSelectedOrder(ctx, 320.0, 0.0);

        assertFalse(ctx.command.shipFleetCommandOverrides.containsKey(escort.id));
        TacticalCombatDepthSystem.update(ctx, 5.0);
        assertEquals(GameContext.FleetCommand.ESCORT, ctx.command.shipFleetCommandOverrides.get(escort.id));
        assertTrue(TacticalCombatDepthSystem.timeline(ctx).stream().anyMatch(marker -> marker.text().contains("ACK")));
    }

    @Test
    void hazardsMinesCollisionlessShipsAndWeaponRolesExposeConcreteTacticalState() {
        GameContext ctx = context();
        Player player = new Player(0.0, 0.0);
        FleetShip enemy = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 10.0, 0.0);
        ctx.player = player;
        ctx.ships.add(player);
        ctx.ships.add(enemy);
        TacticalCombatDepthSystem.init(ctx);
        TacticalCombatDepthSystem.seedHazard(ctx, player, TacticalCombatDepthSystem.Hazard.COOLANT_LEAK, 1.0);

        player.vx = 8.0;
        enemy.vx = -8.0;
        TacticalCombatDepthSystem.handleRamming(ctx);

        assertTrue(TacticalCombatDepthSystem.hazardIntensity(ctx, player, TacticalCombatDepthSystem.Hazard.COOLANT_LEAK) > 0.0);
        assertEquals(0.0, player.x);
        assertEquals(10.0, enemy.x);
        assertEquals(0, TacticalCombatDepthSystem.persistentScarCount(ctx, player));
        assertEquals(0, TacticalCombatDepthSystem.persistentScarCount(ctx, enemy));
        assertTrue(TacticalCombatDepthSystem.weaponRoleTooltip(player, player.turrets.get(0)).contains("/"));

        for (int i = 0; i < 4; i++) TacticalCombatDepthSystem.cycleSupportMode(ctx);
        ctx.cursorWorldX = 900.0;
        ctx.cursorWorldY = 900.0;
        TacticalCombatDepthSystem.activateSupportAtCursor(ctx);
        assertEquals(1, TacticalCombatDepthSystem.mineCount(ctx));
        assertEquals(6, TacticalCombatDepthSystem.SupportMode.values().length);
    }

    @Test
    void volatileOreAndWeaponHeatProvideEnvironmentalAndLogisticsPressure() {
        GameContext ctx = context();
        Player player = new Player(0.0, 0.0);
        FleetShip enemy = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 400.0, 0.0);
        ctx.player = player;
        ctx.ships.add(player);
        ctx.ships.add(enemy);
        TacticalCombatDepthSystem.init(ctx);
        TacticalCombatDepthSystem.update(ctx, GameContext.DT);
        Turret turret = player.turrets.get(0);
        Turret enemyTurret = enemy.turrets.get(0);
        Asteroid richOre = new Asteroid(20.0, 0.0, 20.0, 500);
        int hullBefore = player.hp;

        for (int i = 0; i < 120; i++) {
            TacticalCombatDepthSystem.onWeaponFired(player, turret);
            TacticalCombatDepthSystem.onWeaponFired(enemy, enemyTurret);
        }

        assertTrue(TacticalCombatDepthSystem.canFireWeapon(player, turret));
        assertFalse(TacticalCombatDepthSystem.canFireWeapon(enemy, enemyTurret));
        TacticalCombatDepthSystem.detonateVolatileOre(ctx, richOre);
        assertTrue(player.hp < hullBefore || player.shield < player.shieldMax
                || TacticalCombatDepthSystem.timeline(ctx).stream().anyMatch(marker -> marker.text().contains("ORE DETONATION")));
    }

    @Test
    void weaponsIgnoreLegacyAmmoCountersButStillRespectHeat() throws Exception {
        GameContext ctx = context();
        Player player = new Player(0.0, 0.0);
        FleetShip enemy = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 400.0, 0.0);
        Turret missile = new Turret(Turret.Kind.MISSILE, 0.0, 0.0);
        ctx.player = player;
        ctx.ships.add(player);
        ctx.ships.add(enemy);
        TacticalCombatDepthSystem.init(ctx);
        TacticalCombatDepthSystem.update(ctx, GameContext.DT);

        Object tactical = tacticalStateFor(ctx, player);
        setInt(tactical, "ballisticAmmo", 0);
        setInt(tactical, "missileAmmo", 0);
        setDouble(tactical, "weaponHeat", 0.25);

        assertTrue(TacticalCombatDepthSystem.canFireWeapon(player, player.turrets.get(0)));
        assertTrue(TacticalCombatDepthSystem.canFireWeapon(player, missile));

        setDouble(tactical, "weaponHeat", 0.99);
        assertFalse(TacticalCombatDepthSystem.canFireWeapon(player, missile));
    }

    private static GameContext context() {
        return new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 5000, 5000, true, 1234L, false));
    }

    private static Object tacticalStateFor(GameContext ctx, Ship ship) throws Exception {
        Field statesField = TacticalCombatDepthSystem.class.getDeclaredField("STATES");
        statesField.setAccessible(true);
        Map<?, ?> states = (Map<?, ?>) statesField.get(null);
        Object state = states.get(ctx);
        Field shipsField = state.getClass().getDeclaredField("ships");
        shipsField.setAccessible(true);
        Map<?, ?> ships = (Map<?, ?>) shipsField.get(state);
        return ships.get(ship.id);
    }

    private static void setInt(Object target, String fieldName, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static void setDouble(Object target, String fieldName, double value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setDouble(target, value);
    }
}
