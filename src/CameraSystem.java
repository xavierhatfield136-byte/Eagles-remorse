public final class CameraSystem {
    private CameraSystem(){}

    public static void update(GameContext ctx, int viewportW, int viewportH) {
        if (ctx.player == null) return;
        double viewW = worldViewWidth(ctx, viewportW);
        double viewH = worldViewHeight(ctx, viewportH);
        ctx.camX = ctx.player.x - viewW / 2.0;
        ctx.camY = ctx.player.y - viewH / 2.0;

        double maxCamX = Math.max(0.0, ctx.WORLD_W - viewW);
        double maxCamY = Math.max(0.0, ctx.WORLD_H - viewH);
        ctx.camX = GameMath.clamp(ctx.camX, 0.0, maxCamX);
        ctx.camY = GameMath.clamp(ctx.camY, 0.0, maxCamY);
    }

    public static double normalizedZoom(GameContext ctx) {
        if (ctx == null) return GameContext.DEFAULT_ZOOM;
        double z = (Double.isFinite(ctx.zoom) ? ctx.zoom : GameContext.DEFAULT_ZOOM);
        z = GameMath.clamp(z, GameContext.MIN_ZOOM, GameContext.MAX_ZOOM);
        ctx.zoom = z;
        return z;
    }

    public static void setZoom(GameContext ctx, double zoom) {
        if (ctx == null) return;
        ctx.zoom = GameMath.clamp(zoom, GameContext.MIN_ZOOM, GameContext.MAX_ZOOM);
    }

    public static void resetZoom(GameContext ctx) {
        if (ctx == null) return;
        ctx.zoom = GameContext.DEFAULT_ZOOM;
    }

    public static void stepZoom(GameContext ctx, int step) {
        if (ctx == null || step == 0) return;
        double current = normalizedZoom(ctx);
        double next = current * Math.pow(1.15, step);
        setZoom(ctx, next);
    }

    public static double worldViewWidth(GameContext ctx, int viewportW) {
        double z = normalizedZoom(ctx);
        return Math.max(1.0, viewportW / z);
    }

    public static double worldViewHeight(GameContext ctx, int viewportH) {
        double z = normalizedZoom(ctx);
        return Math.max(1.0, viewportH / z);
    }

    public static double screenToWorldX(GameContext ctx, double screenX) {
        if (ctx == null) return screenX;
        return ctx.camX + screenX / normalizedZoom(ctx);
    }

    public static double screenToWorldY(GameContext ctx, double screenY) {
        if (ctx == null) return screenY;
        return ctx.camY + screenY / normalizedZoom(ctx);
    }
}
