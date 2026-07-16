import app.config.GameConfig;
import app.config.GameMode;
import app.config.ExperienceSettings;
import app.state.AssetLoadGuard;
import app.state.SpriteAtlasRegistry;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Repeatable late-campaign, stress-battle, and memory-soak performance scenarios.
 */
public final class PerformanceGuardrailHarness {
    private PerformanceGuardrailHarness() {}

    public static void main(String[] args) {
        int ticks = 600;
        PerformanceBattleScale.Scenario scenario = PerformanceBattleScale.Scenario.LARGEST_SUPPORTED;
        int shipsPerSide = -1;
        int viewportW = 1280;
        int viewportH = 720;
        String reportPath = "";
        ExperienceSettings.VisualDetail visualDetail = ExperienceSettings.VisualDetail.AUTO;
        boolean tacticalFpsView = false;
        boolean prewarmDestroyedParts = false;
        boolean strict = false;
        for (String arg : args) {
            if (arg == null) continue;
            if (arg.startsWith("--ticks=")) ticks = Math.max(60, parseInt(arg.substring(8), ticks));
            else if (arg.startsWith("--ships-per-side=")) shipsPerSide = Math.max(20, parseInt(arg.substring(17), shipsPerSide));
            else if (arg.startsWith("--scenario=")) scenario = PerformanceBattleScale.scenario(arg.substring(11));
            else if (arg.startsWith("--viewport=")) {
                int[] parsed = parseViewport(arg.substring(11), viewportW, viewportH);
                viewportW = parsed[0];
                viewportH = parsed[1];
            } else if (arg.startsWith("--report=")) reportPath = arg.substring(9).trim();
            else if (arg.startsWith("--visual-detail=")) visualDetail = parseVisualDetail(arg.substring(16), visualDetail);
            else if ("--tactical-fps-view".equalsIgnoreCase(arg)) tacticalFpsView = true;
            else if ("--prewarm-destroyed-parts".equalsIgnoreCase(arg)) prewarmDestroyedParts = true;
            else if ("--strict".equalsIgnoreCase(arg)) strict = true;
        }
        if (shipsPerSide < 0) shipsPerSide = scenario.shipsPerSide > 0 ? scenario.shipsPerSide : 120;

        AssetLoadGuard.resetForTest();
        PerformanceGuardrails.resetForTest();
        ExperienceSettings experience = ExperienceSettings.defaults();
        experience.visualDetail = visualDetail;
        experience.normalize();
        GameContext ctx = new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 20000, 20000, true, 731991L, false)
                .withExperience(experience));
        SpawnSystem.initWorld(ctx);
        clearDefaultBattle(ctx);
        Renderer.prewarmAssetCaches(ctx.config.mode);
        PerformanceGuardrails.applyExperienceSettings(ctx.config.experience);
        PerformanceGuardrails.update(ctx);
        seedStressBattle(ctx, scenario, shipsPerSide);
        ShipPartLibrary.prewarmDamageCachesForShips(ctx.ships);
        centerCameraOnBattle(ctx, viewportW, viewportH);
        if (tacticalFpsView && ctx.ui != null) {
            ctx.ui.tacticalViewEnabled = true;
            ctx.ui.hudDetail = GameContext.HudDetail.MINIMAL;
        }
        warmupSimulationAndRender(ctx, viewportW, viewportH, Math.min(60, Math.max(12, ticks / 6)));
        AssetLoadGuard.resetForTest();
        PerformanceGuardrails.applyExperienceSettings(ctx.config.experience);
        PerformanceGuardrails.update(ctx);
        AssetLoadGuard.markGameplayBegun();

        BufferedImage frame = new BufferedImage(viewportW, viewportH, BufferedImage.TYPE_INT_ARGB);
        long start = System.nanoTime();
        long peakHeap = 0L;
        long updateNs = 0L;
        long physicsNs = 0L;
        long aiNs = 0L;
        long renderNs = 0L;
        long campaignMapNs = 0L;
        int peakProjectiles = ctx.projectiles.size();
        int peakWreckChunks = WreckChunk.activeCount();
        int peakVfx = VFX.activeCount();
        int peakExplosions = Explosion.active.size();
        int maxDrawnShips = 0;
        int maxDrawnProjectiles = 0;
        int maxDrawnWreckChunks = 0;
        int maxDrawnVfx = 0;
        double maxRenderShipsMs = 0.0;
        double maxRenderHudMs = 0.0;
        double maxRenderMapMs = 0.0;
        AiPerfAverages aiPerf = new AiPerfAverages();
        BroadPhysicsPerfAverages broadPhysicsPerf = new BroadPhysicsPerfAverages();
        ProjectilePhysicsPerfAverages projectilePerf = new ProjectilePhysicsPerfAverages();
        ShipRenderPerfAverages shipRenderPerf = new ShipRenderPerfAverages();
        RenderPhasePerfAverages renderPhasePerf = new RenderPhasePerfAverages();
        int missilesPerBurst = missilesPerBurst(scenario);
        for (int tick = 0; tick < ticks; tick++) {
            if (missilesPerBurst > 0 && (tick % 12) == 0) seedMissilePressure(ctx, missilesPerBurst);
            long updateStart = System.nanoTime();
            long physicsStart = System.nanoTime();
            PhysicsSystem.update(ctx, GameContext.DT);
            physicsNs += System.nanoTime() - physicsStart;
            ctx.entityQuery.rebuild(ctx);
            long aiStart = System.nanoTime();
            AISystem.update(ctx, GameContext.DT);
            aiNs += System.nanoTime() - aiStart;
            VFX.updateAll(GameContext.DT);
            long campaignMapStart = System.nanoTime();
            CampaignSystem.enforceCampaignMapDiscipline(ctx);
            campaignMapNs += System.nanoTime() - campaignMapStart;
            updateNs += System.nanoTime() - updateStart;
            Graphics2D g2 = frame.createGraphics();
            long renderStart = System.nanoTime();
            try {
                AssetLoadGuard.beginRenderedFrame();
                g2.setClip(0, 0, viewportW, viewportH);
                GameRenderSystem.render(ctx, g2, viewportW, viewportH);
            } finally {
                AssetLoadGuard.endRenderedFrame();
                g2.dispose();
            }
            long renderFrameNs = System.nanoTime() - renderStart;
            renderNs += renderFrameNs;
            ctx.perf.updateMs = updateNs / (double) (tick + 1) / 1_000_000.0;
            ctx.perf.renderMs = renderNs / (double) (tick + 1) / 1_000_000.0;
            ctx.perf.frameMs = (updateNs + renderNs) / (double) (tick + 1) / 1_000_000.0;
            PerformanceGuardrails.update(ctx);
            peakHeap = Math.max(peakHeap, usedHeap());
            peakProjectiles = Math.max(peakProjectiles, ctx.projectiles.size());
            peakWreckChunks = Math.max(peakWreckChunks, WreckChunk.activeCount());
            peakVfx = Math.max(peakVfx, VFX.activeCount());
            peakExplosions = Math.max(peakExplosions, Explosion.active.size());
            maxDrawnShips = Math.max(maxDrawnShips, ctx.perf.drawnShips);
            maxDrawnProjectiles = Math.max(maxDrawnProjectiles, ctx.perf.drawnProjectiles);
            maxDrawnWreckChunks = Math.max(maxDrawnWreckChunks, ctx.perf.drawnWreckChunks);
            maxDrawnVfx = Math.max(maxDrawnVfx, ctx.perf.drawnVfx);
            maxRenderShipsMs = Math.max(maxRenderShipsMs, ctx.perf.renderShipsMs);
            maxRenderHudMs = Math.max(maxRenderHudMs, ctx.perf.renderHudMs);
            maxRenderMapMs = Math.max(maxRenderMapMs, ctx.perf.renderMapMs);
            aiPerf.sample(ctx.perf);
            broadPhysicsPerf.sample(ctx.perf);
            projectilePerf.sample(ctx.perf);
            shipRenderPerf.sample(ctx.perf);
            renderPhasePerf.sample(ctx.perf);
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
        double avgUpdateMs = updateNs / (double) ticks / 1_000_000.0;
        double avgPhysicsMs = physicsNs / (double) ticks / 1_000_000.0;
        double avgAiMs = aiNs / (double) ticks / 1_000_000.0;
        double avgRenderMs = renderNs / (double) ticks / 1_000_000.0;
        double avgFrameMs = (updateNs + renderNs) / (double) ticks / 1_000_000.0;
        double avgCampaignMapMs = campaignMapNs / (double) ticks / 1_000_000.0;
        double estimatedFps = avgFrameMs <= 1e-6 ? 0.0 : 1000.0 / avgFrameMs;
        boolean frameBudgetPass = avgFrameMs <= scenario.frameBudgetMs();
        pass = pass && ctx.ships.size() >= shipsPerSide * 2;

        System.out.println("[performance-guardrail] scenario=" + scenario.id
                + " viewport=" + viewportW + "x" + viewportH
                + " ships=" + ctx.ships.size()
                + " projectiles=" + ctx.projectiles.size()
                + " peakProjectiles=" + peakProjectiles
                + " peakWreckChunks=" + peakWreckChunks
                + " peakVfx=" + peakVfx
                + " ticks=" + ticks
                + " elapsedMs=" + String.format(java.util.Locale.US, "%.2f", elapsedMs)
                + " avgFrameMs=" + String.format(Locale.US, "%.3f", avgFrameMs)
                + " estimatedFps=" + String.format(Locale.US, "%.1f", estimatedFps)
                + " avgUpdateMs=" + String.format(Locale.US, "%.3f", avgUpdateMs)
                + " avgRenderMs=" + String.format(Locale.US, "%.3f", avgRenderMs)
                + " avgRenderBackgroundMs=" + String.format(Locale.US, "%.3f", renderPhasePerf.backgroundMs())
                + " avgRenderProjectilesMs=" + String.format(Locale.US, "%.3f", renderPhasePerf.projectilesMs())
                + " avgRenderWorldMarkersMs=" + String.format(Locale.US, "%.3f", renderPhasePerf.worldMarkersMs())
                + " avgRenderVfxMs=" + String.format(Locale.US, "%.3f", renderPhasePerf.vfxMs())
                + " avgRenderExplosionsMs=" + String.format(Locale.US, "%.3f", renderPhasePerf.explosionsMs())
                + " maxRenderShipsMs=" + String.format(Locale.US, "%.3f", maxRenderShipsMs)
                + " maxRenderHudMs=" + String.format(Locale.US, "%.3f", maxRenderHudMs)
                + " maxRenderMapMs=" + String.format(Locale.US, "%.3f", maxRenderMapMs)
                + " avgAiMs=" + String.format(Locale.US, "%.3f", avgAiMs)
                + " avgAiFleetStateMs=" + String.format(Locale.US, "%.3f", aiPerf.fleetStateMs())
                + " avgAiShipCombatMs=" + String.format(Locale.US, "%.3f", aiPerf.shipCombatMs())
                + " avgAiAvoidanceMs=" + String.format(Locale.US, "%.3f", aiPerf.avoidanceMs())
                + " avgProjectilePhysicsMs=" + String.format(Locale.US, "%.3f", avgPhysicsMs)
                + " avgPhysicsShipUpdateMs=" + String.format(Locale.US, "%.3f", broadPhysicsPerf.shipUpdateMs())
                + " avgPhysicsSuperweaponPollMs=" + String.format(Locale.US, "%.3f", broadPhysicsPerf.superweaponPollMs())
                + " avgPhysicsPlayerWeaponMs=" + String.format(Locale.US, "%.3f", broadPhysicsPerf.playerWeaponMs())
                + " avgPhysicsPlayerTargetingMs=" + String.format(Locale.US, "%.3f", broadPhysicsPerf.playerTargetingMs())
                + " avgPhysicsPlayerAimMs=" + String.format(Locale.US, "%.3f", broadPhysicsPerf.playerAimMs())
                + " avgPhysicsPlayerPrimaryMs=" + String.format(Locale.US, "%.3f", broadPhysicsPerf.playerPrimaryMs())
                + " avgPhysicsPlayerSecondaryMs=" + String.format(Locale.US, "%.3f", broadPhysicsPerf.playerSecondaryMs())
                + " avgPhysicsPostCollisionMs=" + String.format(Locale.US, "%.3f", broadPhysicsPerf.postCollisionMs())
                + " avgProjectileCiwsMs=" + String.format(Locale.US, "%.3f", projectilePerf.projectileCiwsMs())
                + " avgProjectileVsShipMs=" + String.format(Locale.US, "%.3f", projectilePerf.projectileVsShipMs())
                + " avgProjectileVsProjectileMs=" + String.format(Locale.US, "%.3f", projectilePerf.projectileVsProjectileMs())
                + " avgShipSkinMs=" + String.format(Locale.US, "%.3f", shipRenderPerf.skinMs())
                + " avgShipDamageMs=" + String.format(Locale.US, "%.3f", shipRenderPerf.damageMs())
                + " avgCampaignMapMs=" + String.format(Locale.US, "%.3f", avgCampaignMapMs)
                + " frameBudgetPass=" + frameBudgetPass
                + " peakHeapMb=" + String.format(java.util.Locale.US, "%.2f", peakHeap / 1048576.0)
                + " quality=" + PerformanceGuardrails.quality()
                + " visualDetail=" + PerformanceGuardrails.requestedVisualDetail()
                + " atlases=" + SpriteAtlasRegistry.atlasCount()
                + " frameDecodes=" + AssetLoadGuard.decodeDuringFrameCount()
                + " gameplayDiskLoads=" + AssetLoadGuard.gameplayDiskLoadCount());
        if (!AssetLoadGuard.lastWarning().isBlank()) {
            System.out.println("[performance-guardrail] lastWarning=" + AssetLoadGuard.lastWarning());
        }
        if (!reportPath.isBlank()) {
            writeReport(reportPath, scenario, viewportW, viewportH, ticks, ctx, peakHeap, peakProjectiles,
                    peakWreckChunks, peakVfx, peakExplosions, maxDrawnShips, maxDrawnProjectiles,
                    maxDrawnWreckChunks, maxDrawnVfx, elapsedMs, avgFrameMs, estimatedFps, avgUpdateMs,
                    avgRenderMs, maxRenderShipsMs, maxRenderHudMs, maxRenderMapMs, avgAiMs,
                    aiPerf, broadPhysicsPerf, projectilePerf, shipRenderPerf, renderPhasePerf, avgPhysicsMs,
                    avgCampaignMapMs, frameBudgetPass, pass, strict);
        }
        System.out.println("[performance-guardrail] checks: " + (pass ? "PASS" : "FAIL"));
        if (!pass && strict) System.exit(2);
    }

    private static void seedStressBattle(GameContext ctx, PerformanceBattleScale.Scenario scenario, int shipsPerSide) {
        ShipRole[] roles = rolesFor(scenario);
        for (int i = 0; i < shipsPerSide; i++) {
            double y = 1500.0 + (i % 30) * 85.0;
            ctx.ships.add(new FleetShip(roles[i % roles.length], Faction.ALLY, 6500.0 + (i / 30) * 90.0, y));
            ctx.ships.add(new FleetShip(roles[(i + 1) % roles.length], Faction.ENEMY, 8500.0 + (i / 30) * 90.0, y));
        }
    }

    private static void clearDefaultBattle(GameContext ctx) {
        if (ctx == null) return;
        ctx.ships.clear();
        ctx.projectiles.clear();
        ctx.asteroids.clear();
        ctx.salvage.clear();
        VFX.clearAll();
        Explosion.active.clear();
        if (ctx.player != null) {
            ctx.player.x = 7500.0;
            ctx.player.y = 2500.0;
            ctx.player.vx = 0.0;
            ctx.player.vy = 0.0;
            ctx.ships.add(ctx.player);
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

    private static ShipRole[] rolesFor(PerformanceBattleScale.Scenario scenario) {
        if (scenario == PerformanceBattleScale.Scenario.CAPITAL_HEAVY) {
            return new ShipRole[]{ShipRole.BATTLECRUISER, ShipRole.CARRIER, ShipRole.LIGHT_CRUISER, ShipRole.FRIGATE};
        }
        if (scenario == PerformanceBattleScale.Scenario.TITAN_HEAVY) {
            return new ShipRole[]{ShipRole.HYPERWEAPON_TITAN, ShipRole.CARRIER, ShipRole.BATTLECRUISER, ShipRole.FRIGATE};
        }
        if (scenario == PerformanceBattleScale.Scenario.MISSILE_HEAVY) {
            return new ShipRole[]{ShipRole.MISSILE_BOAT, ShipRole.CIWS_CORVETTE, ShipRole.CARRIER, ShipRole.FRIGATE};
        }
        if (scenario == PerformanceBattleScale.Scenario.WRECK_HEAVY) {
            return new ShipRole[]{ShipRole.BATTLECRUISER, ShipRole.LIGHT_CRUISER, ShipRole.MISSILE_BOAT, ShipRole.CIWS_CORVETTE};
        }
        return new ShipRole[]{ShipRole.FRIGATE, ShipRole.MISSILE_BOAT, ShipRole.CIWS_CORVETTE, ShipRole.LIGHT_CRUISER};
    }

    private static int missilesPerBurst(PerformanceBattleScale.Scenario scenario) {
        if (scenario == PerformanceBattleScale.Scenario.ORDINARY) return 0;
        if (scenario == PerformanceBattleScale.Scenario.MISSILE_HEAVY) return 70;
        if (scenario == PerformanceBattleScale.Scenario.LARGEST_SUPPORTED) return 4;
        if (scenario == PerformanceBattleScale.Scenario.STRESS_160_PER_SIDE) return 24;
        if (scenario == PerformanceBattleScale.Scenario.TITAN_HEAVY) return 34;
        if (scenario == PerformanceBattleScale.Scenario.CAPITAL_HEAVY) return 28;
        if (scenario == PerformanceBattleScale.Scenario.WRECK_HEAVY) return 24;
        return 20;
    }

    private static void centerCameraOnBattle(GameContext ctx, int viewportW, int viewportH) {
        if (ctx == null) return;
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (Ship ship : ctx.ships) {
            if (ship == null || ship == ctx.player) continue;
            minX = Math.min(minX, ship.x);
            minY = Math.min(minY, ship.y);
            maxX = Math.max(maxX, ship.x);
            maxY = Math.max(maxY, ship.y);
        }
        if (!Double.isFinite(minX) || !Double.isFinite(maxX)) return;
        double cx = (minX + maxX) * 0.5;
        double cy = (minY + maxY) * 0.5;
        ctx.camX = Math.max(0.0, cx - CameraSystem.worldViewWidth(ctx, viewportW) * 0.5);
        ctx.camY = Math.max(0.0, cy - CameraSystem.worldViewHeight(ctx, viewportH) * 0.5);
    }

    private static void warmupSimulationAndRender(GameContext ctx, int viewportW, int viewportH, int frames) {
        BufferedImage frame = new BufferedImage(viewportW, viewportH, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < Math.max(1, frames); i++) {
            PhysicsSystem.update(ctx, GameContext.DT);
            ctx.entityQuery.rebuild(ctx);
            AISystem.update(ctx, GameContext.DT);
            VFX.updateAll(GameContext.DT);
            Graphics2D g2 = frame.createGraphics();
            try {
                g2.setClip(0, 0, viewportW, viewportH);
                GameRenderSystem.render(ctx, g2, viewportW, viewportH);
            } finally {
                g2.dispose();
            }
        }
    }

    private static void writeReport(String reportPath, PerformanceBattleScale.Scenario scenario, int viewportW, int viewportH,
                                    int ticks, GameContext ctx, long peakHeap, int peakProjectiles,
                                    int peakWreckChunks, int peakVfx, int peakExplosions, int maxDrawnShips,
                                    int maxDrawnProjectiles, int maxDrawnWreckChunks, int maxDrawnVfx,
                                    double elapsedMs, double avgFrameMs, double estimatedFps, double avgUpdateMs,
                                    double avgRenderMs, double maxRenderShipsMs, double maxRenderHudMs,
                                    double maxRenderMapMs, double avgAiMs, AiPerfAverages aiPerf,
                                    BroadPhysicsPerfAverages broadPhysicsPerf,
                                    ProjectilePhysicsPerfAverages projectilePerf, ShipRenderPerfAverages shipRenderPerf,
                                    RenderPhasePerfAverages renderPhasePerf,
                                    double avgPhysicsMs,
                                    double avgCampaignMapMs, boolean frameBudgetPass, boolean pass, boolean strict) {
        String json = "{\n"
                + "  \"scenario\": \"" + scenario.id + "\",\n"
                + "  \"viewport\": \"" + viewportW + "x" + viewportH + "\",\n"
                + "  \"ticks\": " + ticks + ",\n"
                + "  \"ships\": " + ctx.ships.size() + ",\n"
                + "  \"shipsPerSide\": " + scenario.shipsPerSide + ",\n"
                + "  \"projectiles\": " + ctx.projectiles.size() + ",\n"
                + "  \"peakProjectiles\": " + peakProjectiles + ",\n"
                + "  \"peakWreckChunks\": " + peakWreckChunks + ",\n"
                + "  \"peakVfx\": " + peakVfx + ",\n"
                + "  \"peakExplosions\": " + peakExplosions + ",\n"
                + "  \"maxDrawnShips\": " + maxDrawnShips + ",\n"
                + "  \"maxDrawnProjectiles\": " + maxDrawnProjectiles + ",\n"
                + "  \"maxDrawnWreckChunks\": " + maxDrawnWreckChunks + ",\n"
                + "  \"maxDrawnVfx\": " + maxDrawnVfx + ",\n"
                + "  \"elapsedMs\": " + fmt(elapsedMs) + ",\n"
                + "  \"avgFrameMs\": " + fmt(avgFrameMs) + ",\n"
                + "  \"estimatedFps\": " + fmt(estimatedFps) + ",\n"
                + "  \"avgUpdateMs\": " + fmt(avgUpdateMs) + ",\n"
                + "  \"avgRenderMs\": " + fmt(avgRenderMs) + ",\n"
                + "  \"avgRenderBackgroundMs\": " + fmt(renderPhasePerf.backgroundMs()) + ",\n"
                + "  \"avgRenderWorldCompositeMs\": " + fmt(renderPhasePerf.worldCompositeMs()) + ",\n"
                + "  \"avgRenderVfxMs\": " + fmt(renderPhasePerf.vfxMs()) + ",\n"
                + "  \"avgRenderExplosionsMs\": " + fmt(renderPhasePerf.explosionsMs()) + ",\n"
                + "  \"avgRenderProjectilesMs\": " + fmt(renderPhasePerf.projectilesMs()) + ",\n"
                + "  \"avgRenderWorldMarkersMs\": " + fmt(renderPhasePerf.worldMarkersMs()) + ",\n"
                + "  \"avgRenderHudPhaseMs\": " + fmt(renderPhasePerf.hudMs()) + ",\n"
                + "  \"maxSimplifiedProjectiles\": " + renderPhasePerf.maxSimplifiedProjectiles() + ",\n"
                + "  \"maxRenderShipsMs\": " + fmt(maxRenderShipsMs) + ",\n"
                + "  \"maxRenderHudMs\": " + fmt(maxRenderHudMs) + ",\n"
                + "  \"maxRenderMapMs\": " + fmt(maxRenderMapMs) + ",\n"
                + "  \"avgRenderShipSkinMs\": " + fmt(shipRenderPerf.skinMs()) + ",\n"
                + "  \"avgRenderShipDetailMs\": " + fmt(shipRenderPerf.detailMs()) + ",\n"
                + "  \"avgRenderShipEngineMs\": " + fmt(shipRenderPerf.engineMs()) + ",\n"
                + "  \"avgRenderShipHardpointMs\": " + fmt(shipRenderPerf.hardpointMs()) + ",\n"
                + "  \"avgRenderShipDamageMs\": " + fmt(shipRenderPerf.damageMs()) + ",\n"
                + "  \"avgRenderShipEnergyMs\": " + fmt(shipRenderPerf.energyMs()) + ",\n"
                + "  \"avgRenderShipNameMs\": " + fmt(shipRenderPerf.nameMs()) + ",\n"
                + "  \"avgRenderShipTokenMs\": " + fmt(shipRenderPerf.tokenMs()) + ",\n"
                + "  \"avgAiMs\": " + fmt(avgAiMs) + ",\n"
                + "  \"avgAiMaintenanceMs\": " + fmt(aiPerf.maintenanceMs()) + ",\n"
                + "  \"avgAiFleetStateMs\": " + fmt(aiPerf.fleetStateMs()) + ",\n"
                + "  \"avgAiShipUtilityMs\": " + fmt(aiPerf.shipUtilityMs()) + ",\n"
                + "  \"avgAiShipCombatMs\": " + fmt(aiPerf.shipCombatMs()) + ",\n"
                + "  \"avgAiShipCombatTargetMs\": " + fmt(aiPerf.shipCombatTargetMs()) + ",\n"
                + "  \"avgAiShipCombatFightMs\": " + fmt(aiPerf.shipCombatFightMs()) + ",\n"
                + "  \"avgAiShipCombatFireMs\": " + fmt(aiPerf.shipCombatFireMs()) + ",\n"
                + "  \"avgAiAvoidanceMs\": " + fmt(aiPerf.avoidanceMs()) + ",\n"
                + "  \"avgAiFormationSyncMs\": " + fmt(aiPerf.formationSyncMs()) + ",\n"
                + "  \"avgAiBoundsMs\": " + fmt(aiPerf.boundsMs()) + ",\n"
                + "  \"avgAiCacheQueryMs\": " + fmt(aiPerf.cacheQueryMs()) + ",\n"
                + "  \"avgAiIntentCacheHits\": " + fmt(aiPerf.intentCacheHits()) + ",\n"
                + "  \"avgAiIntentCacheMisses\": " + fmt(aiPerf.intentCacheMisses()) + ",\n"
                + "  \"avgAiIntentInvalidations\": " + fmt(aiPerf.intentInvalidations()) + ",\n"
                + "  \"avgAiCheapTargetScores\": " + fmt(aiPerf.cheapTargetScores()) + ",\n"
                + "  \"avgAiMediumTargetScores\": " + fmt(aiPerf.mediumTargetScores()) + ",\n"
                + "  \"avgAiExpensiveTargetScores\": " + fmt(aiPerf.expensiveTargetScores()) + ",\n"
                + "  \"avgAiMovementReuseFrames\": " + fmt(aiPerf.movementReuseFrames()) + ",\n"
                + "  \"avgProjectilePhysicsMs\": " + fmt(avgPhysicsMs) + ",\n"
                + "  \"avgPhysicsShipUpdateMs\": " + fmt(broadPhysicsPerf.shipUpdateMs()) + ",\n"
                + "  \"avgPhysicsSuperweaponPollMs\": " + fmt(broadPhysicsPerf.superweaponPollMs()) + ",\n"
                + "  \"avgPhysicsPlayerWeaponMs\": " + fmt(broadPhysicsPerf.playerWeaponMs()) + ",\n"
                + "  \"avgPhysicsPlayerTargetingMs\": " + fmt(broadPhysicsPerf.playerTargetingMs()) + ",\n"
                + "  \"avgPhysicsPlayerAimMs\": " + fmt(broadPhysicsPerf.playerAimMs()) + ",\n"
                + "  \"avgPhysicsPlayerPrimaryMs\": " + fmt(broadPhysicsPerf.playerPrimaryMs()) + ",\n"
                + "  \"avgPhysicsPlayerSecondaryMs\": " + fmt(broadPhysicsPerf.playerSecondaryMs()) + ",\n"
                + "  \"avgPhysicsPostCollisionMs\": " + fmt(broadPhysicsPerf.postCollisionMs()) + ",\n"
                + "  \"avgProjectileUpdateMs\": " + fmt(projectilePerf.projectileUpdateMs()) + ",\n"
                + "  \"avgProjectileIndexMs\": " + fmt(projectilePerf.projectileIndexMs()) + ",\n"
                + "  \"avgProjectileCiwsMs\": " + fmt(projectilePerf.projectileCiwsMs()) + ",\n"
                + "  \"avgProjectileVsProjectileMs\": " + fmt(projectilePerf.projectileVsProjectileMs()) + ",\n"
                + "  \"avgProjectileVsAsteroidMs\": " + fmt(projectilePerf.projectileVsAsteroidMs()) + ",\n"
                + "  \"avgProjectileVsShipMs\": " + fmt(projectilePerf.projectileVsShipMs()) + ",\n"
                + "  \"avgProjectileCleanupMs\": " + fmt(projectilePerf.projectileCleanupMs()) + ",\n"
                + "  \"avgShipAsteroidMs\": " + fmt(projectilePerf.shipAsteroidMs()) + ",\n"
                + "  \"avgCampaignMapMs\": " + fmt(avgCampaignMapMs) + ",\n"
                + "  \"frameBudgetMs\": " + fmt(scenario.frameBudgetMs()) + ",\n"
                + "  \"frameBudgetPass\": " + frameBudgetPass + ",\n"
                + "  \"peakHeapMb\": " + fmt(peakHeap / 1048576.0) + ",\n"
                + "  \"gcCollections\": " + ctx.perf.gcCollections + ",\n"
                + "  \"gcMs\": " + fmt(ctx.perf.gcMs) + ",\n"
                + "  \"visualQuality\": \"" + ctx.perf.visualQuality + "\",\n"
                + "  \"frameDecodes\": " + AssetLoadGuard.decodeDuringFrameCount() + ",\n"
                + "  \"gameplayDiskLoads\": " + AssetLoadGuard.gameplayDiskLoadCount() + ",\n"
                + "  \"pass\": " + pass + "\n"
                + "}\n";
        Path out = Paths.get(reportPath);
        try {
            Path parent = out.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(out, json, StandardCharsets.UTF_8);
            System.out.println("[performance-guardrail] wrote " + out.toAbsolutePath());
        } catch (IOException ex) {
            System.out.println("[performance-guardrail] reportWriteFailed=" + ex.getMessage());
            if (strict) System.exit(2);
        }
    }

    private static int[] parseViewport(String raw, int fallbackW, int fallbackH) {
        if (raw == null) return new int[]{fallbackW, fallbackH};
        String[] parts = raw.toLowerCase(Locale.US).split("x");
        if (parts.length != 2) return new int[]{fallbackW, fallbackH};
        return new int[]{
                Math.max(640, parseInt(parts[0].trim(), fallbackW)),
                Math.max(360, parseInt(parts[1].trim(), fallbackH))
        };
    }

    private static ExperienceSettings.VisualDetail parseVisualDetail(String raw, ExperienceSettings.VisualDetail fallback) {
        if (raw == null) return fallback;
        try {
            return ExperienceSettings.VisualDetail.valueOf(raw.trim().toUpperCase(Locale.US).replace('-', '_'));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static long usedHeap() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static int parseInt(String raw, int fallback) {
        try { return Integer.parseInt(raw); } catch (Exception ignored) { return fallback; }
    }

    private static String fmt(double value) {
        return String.format(Locale.US, "%.3f", value);
    }
}
