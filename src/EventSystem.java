public final class EventSystem {
    private EventSystem(){}

    public static void showBanner(GameContext ctx, String msg, double seconds) {
        ctx.eventBanner = msg;
        ctx.eventBannerT = seconds;
    }

    public static void update(GameContext ctx, double dt) {
        if (ctx.config == null || !ctx.config.randomEvents) return;

        if (ctx.eventBannerT > 0) ctx.eventBannerT -= dt;

        // decay modifiers
        if (ctx.orePriceT > 0) {
            ctx.orePriceT -= dt;
            if (ctx.orePriceT <= 0) ctx.orePriceMul = 1.0;
        }
        if (ctx.miningT > 0) {
            ctx.miningT -= dt;
            if (ctx.miningT <= 0) ctx.miningMul = 1.0;
        }

        ctx.nextEventTimer -= dt;
        if (ctx.nextEventTimer > 0) return;
        ctx.nextEventTimer = 18.0 + ctx.rng.nextDouble() * 18.0;

        int which = ctx.rng.nextInt(4);
        switch (which) {
            case 0 -> triggerMarketShift(ctx);
            case 1 -> triggerMiningSurge(ctx);
            case 2 -> triggerRichVein(ctx);
            default -> triggerRaiders(ctx);
        }
    }

    private static void triggerMarketShift(GameContext ctx) {
        ctx.orePriceMul = 1.2 + ctx.rng.nextDouble() * 0.9;
        ctx.orePriceT = 14.0 + ctx.rng.nextDouble() * 10.0;
        showBanner(ctx, "MARKET SHIFT: ORE PRICE x" + String.format("%.2f", ctx.orePriceMul), 3.0);
    }

    private static void triggerMiningSurge(GameContext ctx) {
        ctx.miningMul = 1.2 + ctx.rng.nextDouble();
        ctx.miningT = 12.0 + ctx.rng.nextDouble() * 10.0;
        showBanner(ctx, "MINING SURGE: MINING x" + String.format("%.2f", ctx.miningMul), 3.0);
    }

    private static void triggerRichVein(GameContext ctx) {
        double cx = 600 + ctx.rng.nextDouble() * (ctx.WORLD_W - 1200);
        double cy = 600 + ctx.rng.nextDouble() * (ctx.WORLD_H - 1200);
        for (int i = 0; i < 12; i++) {
            double x = cx + ctx.rng.nextGaussian() * 220;
            double y = cy + ctx.rng.nextGaussian() * 220;
            x = GameMath.clamp(x, 200, ctx.WORLD_W - 200);
            y = GameMath.clamp(y, 200, ctx.WORLD_H - 200);
            ctx.asteroids.add(new Asteroid(x, y, 26 + ctx.rng.nextDouble() * 36, (int)Math.round(800 + ctx.rng.nextDouble() * 1600)));
        }
        showBanner(ctx, "RICH VEIN DETECTED", 3.0);
    }

    private static void triggerRaiders(GameContext ctx) {
        SpawnSystem.spawnEnemyGroup(ctx, ctx.player.x + 900 + ctx.rng.nextDouble() * 400, ctx.player.y + 600 + ctx.rng.nextDouble() * 400);
        SpawnSystem.spawnAllyGroup(ctx, ctx.player.x - 900 - ctx.rng.nextDouble() * 400, ctx.player.y - 600 - ctx.rng.nextDouble() * 400);
        showBanner(ctx, "RAIDERS INBOUND", 2.5);
    }
}
