public final class CameraSystem {
    private static final double CAMERA_PAN_SPEED_SCREEN_UNITS = 900.0;

    private CameraSystem(){}

    public static void update(GameContext ctx, int viewportW, int viewportH) {
        if (ctx.player == null) return;
        double viewW = worldViewWidth(ctx, viewportW);
        double viewH = worldViewHeight(ctx, viewportH);
        Ship fleetFocus = CampaignSystem.isFleetHubSession(ctx) ? CampaignSystem.fleetSelectedShip(ctx) : null;
        Ship baseFocus = (fleetFocus != null) ? fleetFocus : ctx.player;
        double focusX = CampaignSystem.hasCinematicFocus(ctx)
                ? CampaignSystem.cinematicFocusX(ctx)
                : baseFocus.x + ctx.cameraOffsetX;
        double focusY = CampaignSystem.hasCinematicFocus(ctx)
                ? CampaignSystem.cinematicFocusY(ctx)
                : baseFocus.y + ctx.cameraOffsetY;
        ctx.camX = focusX - viewW / 2.0;
        ctx.camY = focusY - viewH / 2.0;
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

    public static void updateManualPan(GameContext ctx, double dt) {
        if (ctx == null || dt <= 0.0) return;
        double panX = 0.0;
        double panY = 0.0;
        if (ctx.cameraPanLeft) panX -= 1.0;
        if (ctx.cameraPanRight) panX += 1.0;
        if (ctx.cameraPanUp) panY -= 1.0;
        if (ctx.cameraPanDown) panY += 1.0;
        if (Math.abs(panX) <= 1e-9 && Math.abs(panY) <= 1e-9) return;

        double len = Math.hypot(panX, panY);
        if (len > 1e-9) {
            panX /= len;
            panY /= len;
        }

        double zoom = normalizedZoom(ctx);
        double panSpeed = CAMERA_PAN_SPEED_SCREEN_UNITS / Math.max(zoom, 1e-6);
        ctx.cameraOffsetX += panX * panSpeed * dt;
        ctx.cameraOffsetY += panY * panSpeed * dt;
    }

    public static void resetManualOffset(GameContext ctx) {
        if (ctx == null) return;
        ctx.cameraOffsetX = 0.0;
        ctx.cameraOffsetY = 0.0;
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
