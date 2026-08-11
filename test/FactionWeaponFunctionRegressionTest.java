import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactionWeaponFunctionRegressionTest {

    @Test
    void redPlayerHullSwapsCanFirePrimaryGunsAcrossCatalog() {
        for (ShipRole role : ShipRole.values()) {
            Player player = redPlayer(role);
            if (player.turrets.stream().noneMatch(t -> t != null && t.primary && t.kind == Turret.Kind.GUN)) {
                continue;
            }
            assertTrue(canFirePrimaryInAnyArc(player), "red player hull should be able to fire primary guns: " + role);
        }
    }

    @Test
    void npcFactionAisCanFireRepresentativePrimaryGuns() throws Exception {
        Method fireIfAble = AISystem.class.getDeclaredMethod(
                "fireIfAble", GameContext.class, Ship.class, Ship.class, double.class, double.class);
        fireIfAble.setAccessible(true);

        List<ShipRole> roles = List.of(
                ShipRole.FRIGATE,
                ShipRole.MISSILE_BOAT,
                ShipRole.CRUISER,
                ShipRole.BATTLESHIP,
                ShipRole.HYPERWEAPON_TITAN
        );
        for (Faction faction : List.of(Faction.ENEMY, Faction.TEAM_C, Faction.TEAM_D)) {
            for (ShipRole role : roles) {
                GameContext ctx = context();
                Player listener = new Player(ShipRole.FRIGATE, 0.0, -900.0);
                listener.faction = Faction.ALLY;
                ctx.player = listener;
                ctx.ships.add(listener);

                Ship shooter = new FleetShip(role, faction, 0.0, 0.0);
                Ship target = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 360.0, 0.0);
                ctx.ships.add(shooter);
                ctx.ships.add(target);
                ctx.entityQuery.rebuild(ctx);

                readyTurrets(shooter);
                int fired = (int) fireIfAble.invoke(null, ctx, shooter, target, GameContext.DT, 360.0);
                assertTrue(fired > 0 || !ctx.projectiles.isEmpty(),
                        "AI primary guns should fire for " + faction + " " + role);
            }
        }
    }

    private static Player redPlayer(ShipRole role) {
        Player player = new Player(ShipRole.FRIGATE, 0.0, 0.0);
        player.faction = Faction.ENEMY;
        player.applyHull(role, 0.0, 0.0);
        player.angle = 0.0;
        readyTurrets(player);
        return player;
    }

    private static boolean canFirePrimaryInAnyArc(Player player) {
        double[][] targets = {
                {900.0, 0.0},
                {0.0, 900.0},
                {0.0, -900.0},
                {-900.0, 0.0},
                {650.0, 650.0},
                {650.0, -650.0}
        };
        for (double[] target : targets) {
            readyTurrets(player);
            for (int i = 0; i < 90; i++) {
                List<Projectile> fired = player.firePrimary(target[0], target[1], GameContext.DT);
                if (!fired.isEmpty()) return true;
                player.update(GameContext.DT);
            }
        }
        return false;
    }

    private static void readyTurrets(Ship ship) {
        for (Turret turret : ship.turrets) {
            if (turret != null) turret.setReady();
        }
    }

    private static GameContext context() {
        return new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 5000, 5000, true, 624812L, false));
    }
}
