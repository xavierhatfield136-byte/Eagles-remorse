import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Regression harness for menu-open simulation continuity.
 * Validates that AI, crew, economy, and station automation continue while overlays are open.
 */
public final class MenuOverlayContinuityHarness {
    private MenuOverlayContinuityHarness() {}

    private static final int[] DEFAULT_SEEDS = {41001, 41002, 41003};
    private static final int DEFAULT_TICKS = 7200; // 120s @ 60Hz
    private static final int VIEW_W = 1280;
    private static final int VIEW_H = 720;
    private static final long STEP_NS = 16_666_667L;

    private static final class Result {
        int seed;
        boolean pass = true;
        String failReason = "";

        int overlayTicks = 0;
        double aiOverlayMotion = 0.0;
        int overlayAutoFireTicks = 0;
        int startOreTotal = 0;
        int endOreTotal = 0;
        int startCredits = 0;
        int endCredits = 0;
        double startCrewReadiness = 0.0;
        double endCrewReadiness = 0.0;
    }

    public static void main(String[] args) throws Exception {
        HarnessArgs cfg = HarnessArgs.parse(args);
        List<Result> results = new ArrayList<>();
        for (int seed : cfg.seeds) {
            results.add(runSeed(seed, cfg.ticks));
        }
        String json = toJson(results, cfg.ticks);

        if (cfg.outputPath == null || cfg.outputPath.isBlank()) {
            System.out.println(json);
            return;
        }
        Path out = Paths.get(cfg.outputPath);
        Path parent = out.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(out, json, StandardCharsets.UTF_8);
        System.out.println("[overlay-harness] wrote " + out.toAbsolutePath());
    }

    private static Result runSeed(int seed, int ticks) {
        Result r = new Result();
        r.seed = seed;

        GameConfig config = new GameConfig(GameMode.RESOURCE_RUSH, 5000, 5000, true, seed, false);
        GameContext ctx = new GameContext(config);
        SpawnSystem.initWorld(ctx);
        if (ctx.player == null) {
            r.pass = false;
            r.failReason = "player_missing";
            return r;
        }

        // Keep station automation active for continuity testing.
        ctx.captainAutomation = true;
        ctx.helmAutomation = true;
        ctx.tacticalAutomation = true;
        ctx.engineeringAutomation = true;
        ctx.scienceAutomation = true;
        ctx.tacticalMode = GameContext.TacticalMode.AGGRESSIVE;
        ctx.engineeringMode = GameContext.EngineeringMode.ATTACK;

        // Make crew readiness responsive to incoming hull damage.
        ctx.player.shieldActive = false;
        ctx.player.shield = 0.0;
        ctx.player.shieldMax = 0.0;

        // Ensure tactical automation has nearby threats in every run.
        SpawnSystem.spawnEnemy(ctx, ShipRole.FRIGATE, ctx.player.x + 280, ctx.player.y + 20);
        SpawnSystem.spawnEnemy(ctx, ShipRole.MISSILE_BOAT, ctx.player.x + 340, ctx.player.y - 80);
        SpawnSystem.spawnAlly(ctx, ShipRole.CIWS_CORVETTE, ctx.player.x - 260, ctx.player.y + 40);

        r.startOreTotal = EconomySystem.getOreTotalForFaction(ctx, Faction.ALLY) + EconomySystem.getOreTotalForFaction(ctx, Faction.ENEMY);
        r.startCredits = ctx.credits;
        r.startCrewReadiness = ctx.player.crewReadiness();

        Map<Integer, double[]> lastPos = new LinkedHashMap<>();
        for (Ship s : ctx.ships) {
            if (s == null) continue;
            lastPos.put(s.id, new double[]{s.x, s.y});
        }

        GameSimulationRuntime runtime = new GameSimulationRuntime(ctx);
        long now = System.nanoTime();
        int prevProjectileCount = ctx.projectiles.size();

        for (int tick = 1; tick <= ticks; tick++) {
            setOverlayState(ctx, tick);
            if ((tick % 180) == 0) {
                // Drive crew-state changes under overlays.
                ctx.player.takeDamage(1, ctx.player.x + 1, ctx.player.y + 1);
            }

            now += STEP_NS;
            runtime.advanceFrame(now, new InputSnapshot(false, false, false, false, false, 0, 0), VIEW_W, VIEW_H, 1.0);

            boolean overlayOpen = ctx.shopOpen || ctx.mapOpen || ctx.powerManagementOpen || ctx.crewStationsOpen || ctx.baseMenuOpen;
            if (overlayOpen) r.overlayTicks++;

            if (overlayOpen) {
                double movedThisTick = 0.0;
                for (Ship s : ctx.ships) {
                    if (s == null || !s.alive || s == ctx.player || s.role == ShipRole.BASE) continue;
                    double[] last = lastPos.get(s.id);
                    if (last == null) continue;
                    movedThisTick += Math.hypot(s.x - last[0], s.y - last[1]);
                }
                r.aiOverlayMotion += movedThisTick;

                int proj = ctx.projectiles.size();
                if (proj > prevProjectileCount) r.overlayAutoFireTicks++;
            }

            prevProjectileCount = ctx.projectiles.size();
            for (Ship s : ctx.ships) {
                if (s == null) continue;
                lastPos.put(s.id, new double[]{s.x, s.y});
            }
            if (ctx.gameOver) break;
        }

        r.endOreTotal = EconomySystem.getOreTotalForFaction(ctx, Faction.ALLY) + EconomySystem.getOreTotalForFaction(ctx, Faction.ENEMY);
        r.endCredits = ctx.credits;
        r.endCrewReadiness = ctx.player.crewReadiness();

        List<String> fails = new ArrayList<>();
        if (r.overlayTicks <= 0) fails.add("no_overlay_ticks");
        if (r.aiOverlayMotion < 120.0) fails.add("ai_motion_low");
        if (r.endOreTotal <= r.startOreTotal) fails.add("economy_ore_stalled");
        if (r.overlayAutoFireTicks <= 0) fails.add("station_auto_fire_absent");
        if (Math.abs(r.endCrewReadiness - r.startCrewReadiness) < 0.005) fails.add("crew_state_static");

        if (!fails.isEmpty()) {
            r.pass = false;
            r.failReason = String.join("|", fails);
        }
        return r;
    }

    private static void setOverlayState(GameContext ctx, int tick) {
        UISystem.closeAllOverlays(ctx);
        if (ctx.gameOver) return;
        int phase = (tick / 150) % 4;
        switch (phase) {
            case 0 -> {
                ctx.shopOpen = true;
                ctx.state = GameState.SHOP;
            }
            case 1 -> {
                ctx.mapOpen = true;
                ctx.state = GameState.MAP;
            }
            case 2 -> {
                ctx.powerManagementOpen = true;
                ctx.state = GameState.POWER_MANAGEMENT;
            }
            default -> {
                ctx.crewStationsOpen = true;
                ctx.state = GameState.CREW_STATIONS;
            }
        }
    }

    private static String toJson(List<Result> results, int ticks) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("{\n");
        sb.append("  \"scenario\": \"menu_overlay_continuity\",\n");
        sb.append("  \"ticks\": ").append(ticks).append(",\n");
        sb.append("  \"results\": [\n");
        for (int i = 0; i < results.size(); i++) {
            Result r = results.get(i);
            if (i > 0) sb.append(",\n");
            sb.append("    {\n");
            sb.append("      \"seed\": ").append(r.seed).append(",\n");
            sb.append("      \"pass\": ").append(r.pass).append(",\n");
            sb.append("      \"failReason\": ").append(q(r.failReason)).append(",\n");
            sb.append("      \"overlayTicks\": ").append(r.overlayTicks).append(",\n");
            sb.append("      \"aiOverlayMotion\": ").append(fmt(r.aiOverlayMotion)).append(",\n");
            sb.append("      \"overlayAutoFireTicks\": ").append(r.overlayAutoFireTicks).append(",\n");
            sb.append("      \"oreDelta\": ").append(r.endOreTotal - r.startOreTotal).append(",\n");
            sb.append("      \"creditDelta\": ").append(r.endCredits - r.startCredits).append(",\n");
            sb.append("      \"crewReadinessDelta\": ").append(fmt(r.endCrewReadiness - r.startCrewReadiness)).append("\n");
            sb.append("    }");
        }
        sb.append("\n  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String fmt(double v) {
        return String.format(Locale.US, "%.4f", v);
    }

    private static String q(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static final class HarnessArgs {
        final int[] seeds;
        final int ticks;
        final String outputPath;

        private HarnessArgs(int[] seeds, int ticks, String outputPath) {
            this.seeds = seeds;
            this.ticks = ticks;
            this.outputPath = outputPath;
        }

        static HarnessArgs parse(String[] args) {
            int[] seeds = Arrays.copyOf(DEFAULT_SEEDS, DEFAULT_SEEDS.length);
            int ticks = DEFAULT_TICKS;
            String output = "";

            if (args != null) {
                for (String arg : args) {
                    if (arg == null) continue;
                    if (arg.startsWith("--seeds=")) {
                        String raw = arg.substring("--seeds=".length()).trim();
                        if (!raw.isBlank()) {
                            String[] parts = raw.split(",");
                            int[] parsed = new int[parts.length];
                            int n = 0;
                            for (String p : parts) {
                                try { parsed[n++] = Integer.parseInt(p.trim()); } catch (Throwable ignored) {}
                            }
                            if (n > 0) seeds = Arrays.copyOf(parsed, n);
                        }
                    } else if (arg.startsWith("--ticks=")) {
                        try { ticks = Math.max(1200, Integer.parseInt(arg.substring("--ticks=".length()).trim())); } catch (Throwable ignored) {}
                    } else if (arg.startsWith("--output=")) {
                        output = arg.substring("--output=".length()).trim();
                    }
                }
            }
            return new HarnessArgs(seeds, ticks, output);
        }
    }
}
