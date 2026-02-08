public final class CameraSystem {
    private CameraSystem(){}

    public static void update(GameContext ctx, int viewportW, int viewportH) {
        if (ctx.player == null) return;
        ctx.camX = ctx.player.x - viewportW / 2.0;
        ctx.camY = ctx.player.y - viewportH / 2.0;

        ctx.camX = GameMath.clamp(ctx.camX, 0, ctx.WORLD_W - viewportW);
        ctx.camY = GameMath.clamp(ctx.camY, 0, ctx.WORLD_H - viewportH);
    }
}
