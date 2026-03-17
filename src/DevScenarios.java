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
 *  Ctrl+F12 = Shooting range (stationary target lineup)
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
        bind(panel, "dev_ctrl_f12", KeyStroke.getKeyStroke("ctrl F12"), e -> spawnShootingRange(ctx));
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

    public static void spawnShootingRange(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;

        resetArena(ctx);
        double x = ctx.player.x;
        double y = ctx.player.y;

        Ship t1 = spawn(ctx, ShipRole.PATROL, Faction.ENEMY, x + 360, y - 220);
        setupRangeTarget(t1, "RANGE TARGET LIGHT (HULL)", false);

        Ship t2 = spawn(ctx, ShipRole.PICKET, Faction.ENEMY, x + 470, y + 170);
        setupRangeTarget(t2, "RANGE TARGET PICKET (HULL)", false);

        Ship t3 = spawn(ctx, ShipRole.FRIGATE, Faction.ENEMY, x + 580, y - 70);
        setupRangeTarget(t3, "RANGE TARGET MEDIUM (SHIELD)", true);

        Ship t4 = spawn(ctx, ShipRole.MISSILE_BOAT, Faction.ENEMY, x + 700, y - 240);
        setupRangeTarget(t4, "RANGE TARGET MISSILE BOAT (HULL)", false);

        Ship t5 = spawn(ctx, ShipRole.CIWS_CORVETTE, Faction.ENEMY, x + 800, y + 110);
        setupRangeTarget(t5, "RANGE TARGET CIWS (HULL)", false);

        Ship t6 = spawn(ctx, ShipRole.TRANSPORT, Faction.ENEMY, x + 940, y + 260);
        setupRangeTarget(t6, "RANGE TARGET TRANSPORT (HULL)", false);

        Ship t7 = spawn(ctx, ShipRole.LIGHT_CRUISER, Faction.ENEMY, x + 980, y - 130);
        setupRangeTarget(t7, "RANGE TARGET LIGHT CRUISER (SHIELD)", true);

        Ship t8 = spawn(ctx, ShipRole.CRUISER, Faction.ENEMY, x + 1160, y + 170);
        setupRangeTarget(t8, "RANGE TARGET CRUISER HEAVY (SHIELD)", true);

        Ship t9 = spawn(ctx, ShipRole.BATTLECRUISER, Faction.ENEMY, x + 1280, y + 20);
        setupRangeTarget(t9, "RANGE TARGET HEAVY (SHIELD)", true);

        Ship t10 = spawn(ctx, ShipRole.BATTLESHIP, Faction.ENEMY, x + 1500, y - 170);
        setupRangeTarget(t10, "RANGE TARGET BATTLESHIP (SHIELD)", true);

        Ship t11 = spawn(ctx, ShipRole.HAULER, Faction.ENEMY, x + 1640, y + 250);
        setupRangeTarget(t11, "RANGE TARGET HAULER (HULL)", false);

        EventSystem.showBanner(ctx, "DEV SCENARIO: SHOOTING RANGE", 2.2);
    }

    private static void setupRangeTarget(Ship s, String label, boolean keepShields) {
        if (s == null) return;
        s.name = label;
        s.desiredSpeed = 0;
        s.desiredSpeedBase = 0;
        s.vx = 0;
        s.vy = 0;
        s.bountyValue = 0;
        s.turrets.clear();
        s.hasCIWS = false;
        s.isCarrier = false;
        s.carrierAutoLaunch = false;
        s.hasWaveMotionGun = false;

        if (!keepShields) {
            s.shieldMax = 0;
            s.shield = 0;
            s.shieldRegen = 0;
            s.shieldActive = false;
        } else {
            s.shieldActive = s.shieldMax > 0;
            s.shield = Math.min(s.shieldMax, Math.max(0.0, s.shield));
        }
    }

    private static Ship spawn(GameContext ctx, ShipRole role, Faction faction, double x, double y) {
        if (role == ShipRole.MINER &&
                TeamSystem.countAliveMiners(ctx, faction) >= SpawnSystem.MAX_MINERS_PER_FACTION) {
            return null;
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
        return s;
    }
}
