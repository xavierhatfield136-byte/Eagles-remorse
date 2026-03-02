import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.Iterator;

/**
 * Quick spawn/reset scenarios to speed up balancing and AI iteration.
 *
 * Keys (bound in InputSystem):
 *  F6 = 1v1 frigate duel near player
 *  F7 = Missile Boat vs CIWS Corvette
 *  F8 = Cruiser vs 2 frigates
 *  F9 = Reset arena (remove NPC ships + clear projectiles)
 */
public final class DevScenarios {
    private DevScenarios(){}

    /** Attach scenario keybinds to the panel. */
    public static void installBindings(JComponent panel, GameContext ctx) {
        bind(panel, "dev_f6", KeyStroke.getKeyStroke("F6"), e -> spawn1v1(ctx));
        bind(panel, "dev_f7", KeyStroke.getKeyStroke("F7"), e -> spawnMissileVsCiws(ctx));
        bind(panel, "dev_f8", KeyStroke.getKeyStroke("F8"), e -> spawnCruiserVsTwoFrigates(ctx));
        bind(panel, "dev_f9", KeyStroke.getKeyStroke("F9"), e -> resetArena(ctx));
        bind(panel, "dev_f11", KeyStroke.getKeyStroke("F11"), e -> spawnMiners(ctx));
    }

    private static void bind(JComponent c, String name, KeyStroke ks, java.util.function.Consumer<ActionEvent> fn) {
        InputMap im = c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = c.getActionMap();
        im.put(ks, name);
        am.put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                // Keep scenario hotkeys in explicit dev mode so gameplay/shop hotkeys don't trigger them.
                if (!DevTools.isDebugOverlay()) return;
                fn.accept(e);
            }
        });
    }

    public static void resetArena(GameContext ctx) {
        // Clear projectiles
        if (ctx.projectiles != null) ctx.projectiles.clear();

        // Remove all ships except player and bases (if present)
        Iterator<Ship> it = ctx.ships.iterator();
        while (it.hasNext()) {
            Ship s = it.next();
            if (s == null) { it.remove(); continue; }
            if (s == ctx.player) continue;
            if (s.isBase) continue;
            it.remove();
        }
    }

    public static void spawn1v1(GameContext ctx) {
        resetArena(ctx);
        double x = ctx.player.x;
        double y = ctx.player.y;
        spawn(ctx, ShipRole.FRIGATE, Faction.ALLY, x + 420, y + 120);
        spawn(ctx, ShipRole.FRIGATE, Faction.ENEMY, x + 820, y + 120);
    }

    public static void spawnMissileVsCiws(GameContext ctx) {
        resetArena(ctx);
        double x = ctx.player.x;
        double y = ctx.player.y;
        spawn(ctx, ShipRole.MISSILE_BOAT, Faction.ENEMY, x + 900, y - 80);
        spawn(ctx, ShipRole.CIWS_CORVETTE, Faction.ALLY, x + 520, y - 80);
    }

    public static void spawnCruiserVsTwoFrigates(GameContext ctx) {
        resetArena(ctx);
        double x = ctx.player.x;
        double y = ctx.player.y;
        spawn(ctx, ShipRole.CRUISER, Faction.ENEMY, x + 980, y + 0);
        spawn(ctx, ShipRole.FRIGATE, Faction.ALLY, x + 560, y - 120);
        spawn(ctx, ShipRole.FRIGATE, Faction.ALLY, x + 560, y + 120);
    }

    public static void spawnMiners(GameContext ctx) {
        if (ctx.allyBase == null || ctx.enemyBase == null) return;
        spawn(ctx, ShipRole.MINER, Faction.ALLY, ctx.allyBase.x - 140, ctx.allyBase.y + 120);
        spawn(ctx, ShipRole.MINER, Faction.ALLY, ctx.allyBase.x - 190, ctx.allyBase.y - 40);
        spawn(ctx, ShipRole.MINER, Faction.ENEMY, ctx.enemyBase.x + 140, ctx.enemyBase.y - 120);
        spawn(ctx, ShipRole.MINER, Faction.ENEMY, ctx.enemyBase.x + 190, ctx.enemyBase.y + 40);
    }

    private static void spawn(GameContext ctx, ShipRole role, Faction faction, double x, double y) {
        if (role == ShipRole.MINER &&
                TeamSystem.countAliveMiners(ctx, faction) >= SpawnSystem.MAX_MINERS_PER_FACTION) {
            return;
        }

        Ship s;
        try {
            // Preferred constructor in the split build
            s = new FleetShip(role, faction, x, y);
        } catch (Throwable t) {
            // Fallback: try role-less enemy ship constructor
            if (faction == Faction.ENEMY) s = new EnemyShip(x, y);
            else s = new FleetShip(ShipRole.FRIGATE, faction, x, y);
        }

        // Apply doctrine/balance if your project has a hook for it (optional).
        try {
            Class<?> dr = Class.forName("DoctrineRegistry");
            dr.getMethod("applyToShip", GameContext.class, Ship.class).invoke(null, ctx, s);
        } catch (Throwable ignored) {}

        ctx.ships.add(s);
        if (role == ShipRole.MINER) {
            System.out.println("MINER SPAWN #" + s.id +
                    " role=" + s.role +
                    " faction=" + s.faction +
                    " pos=(" + (int) Math.round(s.x) + "," + (int) Math.round(s.y) + ")" +
                    " speed=" + s.desiredSpeed +
                    " miningRate=" + s.miningRate +
                    " miningRange=" + s.miningRange +
                    " cargoMax=" + s.cargoMax);
        }
    }
}
