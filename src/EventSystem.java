public final class EventSystem {
    private EventSystem(){}

    public static void showBanner(GameContext ctx, String msg, double seconds) {
        ctx.eventBanner = msg;
        ctx.eventBannerT = seconds;
    }

    public static void update(GameContext ctx, double dt) {
        if (ctx == null) return;
        if (ctx.eventBannerT > 0) ctx.eventBannerT -= dt;
        if (ctx.hazardHintCooldown > 0.0) ctx.hazardHintCooldown -= dt;
        if (ctx.hazardCriticalCooldown > 0.0) ctx.hazardCriticalCooldown -= dt;
        updateHazardWarnings(ctx);

        // decay modifiers
        if (ctx.orePriceT > 0) {
            ctx.orePriceT -= dt;
            if (ctx.orePriceT <= 0) ctx.orePriceMul = 1.0;
        }
        if (ctx.miningT > 0) {
            ctx.miningT -= dt;
            if (ctx.miningT <= 0) ctx.miningMul = 1.0;
        }

        if (ctx.config == null || !ctx.config.randomEvents) return;
        if (CampaignSystem.suppressRandomEvents(ctx)) return;

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

    private static void updateHazardWarnings(GameContext ctx) {
        if (ctx == null || ctx.player == null) return;
        if (!ctx.player.alive || ctx.player.dying || ctx.player.hp <= 0) return;
        if (ctx.eventBannerT > 0.20) return;

        int fireRooms = ctx.player.activeFireRoomCount();
        if (fireRooms <= 0) return;
        double fireLoad = ctx.player.totalFireIntensity();

        ShipRoomLayout.RoomId hotspot = ctx.player.hottestFireRoom();
        String hotspotLabel = (hotspot == null) ? "UNKNOWN" : hotspot.name();
        if (hotspot != null) {
            ShipRoomLayout.RoomDef def = ShipRoomLayout.roomForId(ctx.player.role, hotspot);
            if (def != null && def.label != null && !def.label.isBlank()) {
                hotspotLabel = def.label;
            }
        }

        if ((fireRooms >= 3 || fireLoad >= 3.0) && ctx.hazardCriticalCooldown <= 0.0) {
            showBanner(ctx, "CRITICAL FIRE ALERT: " + hotspotLabel + " - PRIORITIZE DAMAGE CONTROL", 1.2);
            ctx.hazardCriticalCooldown = 4.0;
            ctx.hazardHintCooldown = Math.max(ctx.hazardHintCooldown, 2.0);
            return;
        }
        if (ctx.hazardHintCooldown <= 0.0) {
            showBanner(ctx, "FIRE DETECTED: " + hotspotLabel + " - ENGINEERING CAN SUPPRESS (KEY 8)", 1.1);
            ctx.hazardHintCooldown = 6.0;
        }
    }
}
