import java.awt.Rectangle;
import java.awt.event.MouseEvent;

public final class UISystem {
    private UISystem(){}

    public static void closeAllOverlays(GameContext ctx) {
        ctx.shopOpen = false;
        ctx.baseMenuOpen = false;
        ctx.mapOpen = false;
        if (!ctx.gameOver) ctx.state = GameState.RUNNING;
    }

    public static void toggleShop(GameContext ctx) {
        if (ctx.state == GameState.PAUSED || ctx.state == GameState.GAME_OVER) return;
        ctx.shopOpen = !ctx.shopOpen;
        if (ctx.shopOpen) {
            ctx.baseMenuOpen = false;
            ctx.mapOpen = false;
            ctx.state = GameState.SHOP;
        } else {
            ctx.state = GameState.RUNNING;
        }
    }

    public static void toggleMap(GameContext ctx) {
        if (ctx.state == GameState.PAUSED || ctx.state == GameState.GAME_OVER) return;
        ctx.mapOpen = !ctx.mapOpen;
        if (ctx.mapOpen) {
            ctx.shopOpen = false;
            ctx.baseMenuOpen = false;
            ctx.state = GameState.MAP;
        } else {
            ctx.state = GameState.RUNNING;
        }
    }

    public static void toggleBaseMenu(GameContext ctx) {
        if (ctx.state == GameState.PAUSED || ctx.state == GameState.GAME_OVER) return;
        Ship dock = EconomySystem.getDockedFriendlyBase(ctx);
        if (dock == null) {
            EventSystem.showBanner(ctx, "DOCK AT A FRIENDLY BASE TO UPGRADE", 2.0);
            return;
        }
        ctx.baseMenuOpen = !ctx.baseMenuOpen;
        if (ctx.baseMenuOpen) {
            ctx.shopOpen = false;
            ctx.mapOpen = false;
            ctx.state = GameState.BASE_MENU;
        } else {
            ctx.state = GameState.RUNNING;
        }
    }

    public static void handleMapClick(GameContext ctx, MouseEvent e, int viewportW, int viewportH) {
        Rectangle rect = Renderer.getStrategicMapRect(viewportW, viewportH);
        if (!rect.contains(e.getPoint())) return;

        double nx = (e.getX() - rect.x) / (double) rect.width;
        double ny = (e.getY() - rect.y) / (double) rect.height;

        ctx.waypointX = GameMath.clamp(nx * ctx.WORLD_W, 0, ctx.WORLD_W);
        ctx.waypointY = GameMath.clamp(ny * ctx.WORLD_H, 0, ctx.WORLD_H);

        addPing(ctx, ctx.waypointX, ctx.waypointY, 2.2);
        EventSystem.showBanner(ctx, "WAYPOINT SET", 1.2);
    }

    public static void setWaypointAtCursor(GameContext ctx, PlayerControl controls) {
        double wx = ctx.camX + controls.getMouseX();
        double wy = ctx.camY + controls.getMouseY();
        ctx.waypointX = GameMath.clamp(wx, 0, ctx.WORLD_W);
        ctx.waypointY = GameMath.clamp(wy, 0, ctx.WORLD_H);
        addPing(ctx, ctx.waypointX, ctx.waypointY, 2.2);
        EventSystem.showBanner(ctx, "WAYPOINT SET", 1.0);
    }

    public static void pingAtCursor(GameContext ctx, PlayerControl controls) {
        double wx = ctx.camX + controls.getMouseX();
        double wy = ctx.camY + controls.getMouseY();
        addPing(ctx, wx, wy, 1.8);
    }

    public static void addPing(GameContext ctx, double x, double y, double seconds) {
        int factionCode = 0;
        if (ctx.player != null) {
            if (ctx.player.faction == Faction.ALLY) factionCode = 1;
            else if (ctx.player.faction == Faction.ENEMY) factionCode = 2;
        }
        ctx.mapPings.add(new Renderer.MapPing(x, y, seconds, factionCode));
    }

    public static void updatePings(GameContext ctx, double dt) {
        for (int i = ctx.mapPings.size() - 1; i >= 0; i--) {
            Renderer.MapPing p = ctx.mapPings.get(i);
            p.t -= dt;
            if (p.t <= 0) ctx.mapPings.remove(i);
        }
    }
}
