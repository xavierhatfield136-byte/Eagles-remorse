import app.config.GameConfig;
import app.config.GameMode;
import app.state.AssetLoadGuard;
import app.state.SpriteAtlasRegistry;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Repeatable late-campaign, stress-battle, and memory-soak performance scenarios.
 */
public final class PerformanceGuardrailHarness {
    private PerformanceGuardrailHarness() {}

    public static void main(String[] args) {
        int ticks = 600;
        int shipsPerSide = 120;
        boolean strict = false;
        for (String arg : args) {
            if (arg == null) continue;
            if (arg.startsWith("--ticks=")) ticks = Math.max(60, parseInt(arg.substring(8), ticks));
            else if (arg.startsWith("--ships-per-side=")) shipsPerSide = Math.max(20, parseInt(arg.substring(17), shipsPerSide));
            else if ("--strict".equalsIgnoreCase(arg)) strict = true;
        }

        AssetLoadGuard.resetForTest();
        PerformanceGuardrails.resetForTest();
        GameContext ctx = new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 20000, 20000, true, 731991L, false));
        SpawnSystem.initWorld(ctx);
        Renderer.prewarmAssetCaches(ctx.config.mode);
        AssetLoadGuard.markGameplayBegun();
        seedStressBattle(ctx, shipsPerSide);

        BufferedImage frame = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_ARGB);
        long start = System.nanoTime();
        long peakHeap = 0L;
        for (int tick = 0; tick < ticks; tick++) {
            if ((tick % 12) == 0) seedMissilePressure(ctx, 40);
            PhysicsSystem.update(ctx, GameContext.DT);
            ctx.entityQuery.rebuild(ctx);
            AISystem.update(ctx, GameContext.DT);
            VFX.updateAll(GameContext.DT);
            Graphics2D g2 = frame.createGraphics();
            try {
                AssetLoadGuard.beginRenderedFrame();
                GameRenderSystem.render(ctx, g2, 1280, 720);
            } finally {
                AssetLoadGuard.endRenderedFrame();
                g2.dispose();
            }
            ctx.perf.frameMs = 18.0;
            PerformanceGuardrails.update(ctx.perf);
            peakHeap = Math.max(peakHeap, usedHeap());
        }
        double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
        SpriteAtlasRegistry.atlas("multipart");
        SpriteAtlasRegistry.atlas("turret");
        SpriteAtlasRegistry.atlas("wreck");
        SpriteAtlasRegistry.atlas("projectile");
        SpriteAtlasRegistry.atlas("ui");

        boolean pass = ctx.ships.size() >= shipsPerSide * 2
                && AssetLoadGuard.decodeDuringFrameCount() == 0
                && AssetLoadGuard.gameplayDiskLoadCount() == 0;
        System.out.println("[performance-guardrail] ships=" + ctx.ships.size()
                + " projectiles=" + ctx.projectiles.size()
                + " ticks=" + ticks
                + " elapsedMs=" + String.format(java.util.Locale.US, "%.2f", elapsedMs)
                + " peakHeapMb=" + String.format(java.util.Locale.US, "%.2f", peakHeap / 1048576.0)
                + " quality=" + PerformanceGuardrails.quality()
                + " atlases=" + SpriteAtlasRegistry.atlasCount()
                + " frameDecodes=" + AssetLoadGuard.decodeDuringFrameCount()
                + " gameplayDiskLoads=" + AssetLoadGuard.gameplayDiskLoadCount());
        if (!AssetLoadGuard.lastWarning().isBlank()) {
            System.out.println("[performance-guardrail] lastWarning=" + AssetLoadGuard.lastWarning());
        }
        System.out.println("[performance-guardrail] checks: " + (pass ? "PASS" : "FAIL"));
        if (!pass && strict) System.exit(2);
    }

    private static void seedStressBattle(GameContext ctx, int shipsPerSide) {
        ShipRole[] roles = {ShipRole.FRIGATE, ShipRole.MISSILE_BOAT, ShipRole.CIWS_CORVETTE, ShipRole.LIGHT_CRUISER};
        for (int i = 0; i < shipsPerSide; i++) {
            double y = 1500.0 + (i % 30) * 85.0;
            ctx.ships.add(new FleetShip(roles[i % roles.length], Faction.ALLY, 6500.0 + (i / 30) * 90.0, y));
            ctx.ships.add(new FleetShip(roles[(i + 1) % roles.length], Faction.ENEMY, 8500.0 + (i / 30) * 90.0, y));
        }
    }

    private static void seedMissilePressure(GameContext ctx, int count) {
        if (ctx.ships.size() < 4) return;
        Ship owner = ctx.ships.get(0);
        Ship target = ctx.ships.get(ctx.ships.size() - 1);
        for (int i = 0; i < count; i++) {
            double angle = Math.atan2(target.y - owner.y, target.x - owner.x);
            ctx.projectiles.add(new Missile(owner.x + i, owner.y + (i % 8), angle, target, GameContext.DT, owner.faction));
            VFX.spawnImpactSparks(owner.x + i, owner.y + (i % 8), 1.0, 0.0, 2);
        }
    }

    private static long usedHeap() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static int parseInt(String raw, int fallback) {
        try { return Integer.parseInt(raw); } catch (Exception ignored) { return fallback; }
    }
}
