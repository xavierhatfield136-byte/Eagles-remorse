import java.io.IOException;
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
 * Deterministic M1 parity harness for campaign sectors 1-3.
 * Runs headless, applies controlled assistance, and emits JSON metrics.
 */
public final class CampaignParityHarness {
    private CampaignParityHarness() {}

    private static final int HARNESS_VERSION = 1;
    private static final String SCENARIO = "campaign_ops_m1_assisted";
    private static final int[] DEFAULT_SEEDS = {10101, 20202, 30303};
    private static final int MAX_SECTOR = 3;
    private static final int FORCED_SURVIVE_TICKS = 21600; // 360 seconds @ 60 Hz
    private static final int FORCED_CAPTURE_TICKS = 7200;  // 120 seconds @ 60 Hz

    private static final class SectorMetric {
        int sector;
        String objectiveType;
        double objectiveGoal;
        double timeLimitSec;
        String sideObjectiveType;
        int clearElapsedSecRounded = -1;
        int clearTicks = -1;
        int creditsAfterClear = 0;
        boolean sideCompleted = false;
        boolean sideFailed = false;
    }

    private static final class RunResult {
        long seed;
        boolean pass = true;
        String failReason = "";
        int finalSector = 0;
        boolean gameOver = false;
        int finalCredits = 0;
        String objectiveFlow = "";
        final List<SectorMetric> sectors = new ArrayList<>();
    }

    public static void main(String[] args) {
        HarnessArgs cfg = HarnessArgs.parse(args);
        List<RunResult> results = new ArrayList<>();
        for (int seed : cfg.seeds) {
            results.add(runSeed(seed, cfg.maxTicks));
        }

        String json = toJson(cfg.seeds, results);
        if (cfg.outputPath != null && !cfg.outputPath.isBlank()) {
            Path out = Paths.get(cfg.outputPath);
            try {
                Path parent = out.getParent();
                if (parent != null) Files.createDirectories(parent);
                Files.writeString(out, json, StandardCharsets.UTF_8);
                System.out.println("[parity] wrote " + out.toAbsolutePath());
            } catch (IOException ex) {
                System.err.println("[parity] write_failed path=" + out + " error=" + ex.getMessage());
                System.out.println(json);
                System.exit(1);
            }
        } else {
            System.out.println(json);
        }
    }

    private static RunResult runSeed(int seed, int maxTicks) {
        RunResult rr = new RunResult();
        rr.seed = seed;

        GameConfig config = new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, seed, false);
        GameContext ctx = new GameContext(config);

        // Disable profile persistence side-effects to keep parity deterministic and local.
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);

        CampaignSystem.CampaignState st = ctx.campaign;
        if (st == null || !st.enabled) {
            rr.pass = false;
            rr.failReason = "campaign_not_initialized";
            return rr;
        }

        Map<Integer, SectorMetric> metrics = new LinkedHashMap<>();
        Map<Integer, Integer> sectorStartTick = new LinkedHashMap<>();
        List<String> objectiveFlow = new ArrayList<>();

        registerSectorStart(st, metrics, sectorStartTick, objectiveFlow, 0);

        int tick = 0;
        int lastSector = st.sector;
        while (tick < maxTicks) {
            tick++;
            applyAssist(ctx, tick, sectorStartTick);
            CampaignSystem.update(ctx, GameContext.DT);

            st = ctx.campaign;
            if (st == null) {
                rr.pass = false;
                rr.failReason = "campaign_state_lost";
                break;
            }
            if (ctx.gameOver) {
                rr.pass = false;
                rr.failReason = (ctx.gameOverText == null || ctx.gameOverText.isBlank())
                        ? "game_over"
                        : ("game_over:" + ctx.gameOverText);
                break;
            }

            if (st.sector != lastSector) {
                lastSector = st.sector;
                registerSectorStart(st, metrics, sectorStartTick, objectiveFlow, tick);
            }

            // Sector clear is represented by transitionTimer > 0 while sector index is unchanged.
            if (st.sector <= MAX_SECTOR && st.transitionTimer > 0.0) {
                SectorMetric sm = metrics.get(st.sector);
                if (sm != null && sm.clearTicks < 0) {
                    int startTick = sectorStartTick.getOrDefault(st.sector, 0);
                    sm.clearTicks = Math.max(1, tick - startTick);
                    sm.clearElapsedSecRounded = (int) Math.round(st.sectorElapsed);
                    sm.creditsAfterClear = ctx.credits;
                    sm.sideCompleted = st.sideObjectiveCompleted;
                    sm.sideFailed = st.sideObjectiveFailed;
                }
            }

            if (hasAllClears(metrics, MAX_SECTOR) && st.sector >= (MAX_SECTOR + 1) && st.transitionTimer <= 0.0) {
                break;
            }
        }

        if (tick >= maxTicks && rr.pass) {
            rr.pass = false;
            rr.failReason = "timeout_max_ticks";
        }

        rr.finalSector = (ctx.campaign == null) ? 0 : ctx.campaign.sector;
        rr.gameOver = ctx.gameOver;
        rr.finalCredits = ctx.credits;
        rr.objectiveFlow = String.join(">", objectiveFlow);
        rr.sectors.addAll(metrics.values());
        return rr;
    }

    private static void applyAssist(GameContext ctx, int globalTick, Map<Integer, Integer> sectorStartTick) {
        CampaignSystem.CampaignState st = ctx.campaign;
        if (st == null) return;

        if (ctx.player != null) {
            ctx.player.alive = true;
            ctx.player.dying = false;
            ctx.player.hp = Math.max(ctx.player.hp, ctx.player.hpMax);
            if (ctx.player.shieldActive && ctx.player.shieldMax > 0) {
                ctx.player.shield = Math.max(ctx.player.shield, ctx.player.shieldMax);
            }
        }

        int startTick = sectorStartTick.getOrDefault(st.sector, 0);
        int sectorTicks = Math.max(1, globalTick - startTick);

        if (st.sector == 1 && sectorTicks >= FORCED_SURVIVE_TICKS) {
            st.objectiveGoal = 1.0;
            st.objectiveProgress = 1.0;
        }

        if (st.sector == 2 && sectorTicks >= 1) {
            st.authoredObjectiveKills = (int) Math.ceil(st.objectiveGoal);
        }

        if (st.sector == 3) {
            st.captureArmed = true;
            if (ctx.player != null) {
                ctx.player.x = st.captureX;
                ctx.player.y = st.captureY;
            }
            for (Ship s : ctx.ships) {
                if (s == null) continue;
                if (!TeamSystem.isHostileToPlayer(ctx, s.faction)) continue;
                if (s.role == ShipRole.BASE) continue;
                s.alive = false;
                s.dying = true;
                s.hp = 0;
            }
            if (sectorTicks >= FORCED_CAPTURE_TICKS) {
                st.objectiveGoal = 1.0;
                st.objectiveProgress = 1.0;
            }
        }
    }

    private static void registerSectorStart(CampaignSystem.CampaignState st,
                                            Map<Integer, SectorMetric> metrics,
                                            Map<Integer, Integer> sectorStartTick,
                                            List<String> objectiveFlow,
                                            int tick) {
        if (st == null) return;
        if (st.sector > MAX_SECTOR) return;
        if (metrics.containsKey(st.sector)) return;

        SectorMetric sm = new SectorMetric();
        sm.sector = st.sector;
        sm.objectiveType = safe(st.objectiveType);
        sm.objectiveGoal = st.objectiveGoal;
        sm.timeLimitSec = st.sectorTimeLimit;
        sm.sideObjectiveType = safe(st.sideObjectiveType);
        metrics.put(sm.sector, sm);
        sectorStartTick.put(sm.sector, tick);
        objectiveFlow.add(sm.objectiveType);
    }

    private static boolean hasAllClears(Map<Integer, SectorMetric> metrics, int maxSector) {
        if (metrics.size() < maxSector) return false;
        for (int s = 1; s <= maxSector; s++) {
            SectorMetric m = metrics.get(s);
            if (m == null || m.clearTicks < 0) return false;
        }
        return true;
    }

    private static String toJson(int[] seeds, List<RunResult> results) {
        StringBuilder sb = new StringBuilder(8192);
        sb.append("{\n");
        sb.append("  \"harnessVersion\": ").append(HARNESS_VERSION).append(",\n");
        sb.append("  \"scenario\": ").append(q(SCENARIO)).append(",\n");
        sb.append("  \"seeds\": [");
        for (int i = 0; i < seeds.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(seeds[i]);
        }
        sb.append("],\n");
        sb.append("  \"results\": [\n");
        for (int i = 0; i < results.size(); i++) {
            RunResult rr = results.get(i);
            if (i > 0) sb.append(",\n");
            sb.append("    {\n");
            sb.append("      \"seed\": ").append(rr.seed).append(",\n");
            sb.append("      \"pass\": ").append(rr.pass).append(",\n");
            sb.append("      \"failReason\": ").append(q(rr.failReason)).append(",\n");
            sb.append("      \"finalSector\": ").append(rr.finalSector).append(",\n");
            sb.append("      \"gameOver\": ").append(rr.gameOver).append(",\n");
            sb.append("      \"finalCredits\": ").append(rr.finalCredits).append(",\n");
            sb.append("      \"objectiveFlow\": ").append(q(rr.objectiveFlow)).append(",\n");
            sb.append("      \"sectors\": [\n");
            for (int j = 0; j < rr.sectors.size(); j++) {
                SectorMetric sm = rr.sectors.get(j);
                if (j > 0) sb.append(",\n");
                sb.append("        {\n");
                sb.append("          \"sector\": ").append(sm.sector).append(",\n");
                sb.append("          \"objectiveType\": ").append(q(sm.objectiveType)).append(",\n");
                sb.append("          \"objectiveGoal\": ").append(fmt(sm.objectiveGoal)).append(",\n");
                sb.append("          \"timeLimitSec\": ").append(fmt(sm.timeLimitSec)).append(",\n");
                sb.append("          \"sideObjectiveType\": ").append(q(sm.sideObjectiveType)).append(",\n");
                sb.append("          \"clearElapsedSecRounded\": ").append(sm.clearElapsedSecRounded).append(",\n");
                sb.append("          \"clearTicks\": ").append(sm.clearTicks).append(",\n");
                sb.append("          \"creditsAfterClear\": ").append(sm.creditsAfterClear).append(",\n");
                sb.append("          \"sideCompleted\": ").append(sm.sideCompleted).append(",\n");
                sb.append("          \"sideFailed\": ").append(sm.sideFailed).append("\n");
                sb.append("        }");
            }
            sb.append("\n      ]\n");
            sb.append("    }");
        }
        sb.append("\n  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String safe(Enum<?> e) {
        return (e == null) ? "" : e.name();
    }

    private static String q(String s) {
        if (s == null) return "\"\"";
        return "\"" + jsonEscape(s) + "\"";
    }

    private static String fmt(double d) {
        if (Math.abs(d - Math.rint(d)) < 1e-9) {
            return String.format(Locale.ROOT, "%.0f", d);
        }
        return String.format(Locale.ROOT, "%.6f", d);
    }

    private static String jsonEscape(String s) {
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    private static final class HarnessArgs {
        final int[] seeds;
        final String outputPath;
        final int maxTicks;

        private HarnessArgs(int[] seeds, String outputPath, int maxTicks) {
            this.seeds = seeds;
            this.outputPath = outputPath;
            this.maxTicks = maxTicks;
        }

        static HarnessArgs parse(String[] args) {
            int[] seeds = Arrays.copyOf(DEFAULT_SEEDS, DEFAULT_SEEDS.length);
            String output = "build/parity/campaign_parity_latest.json";
            int maxTicks = 450000;

            if (args != null) {
                for (String raw : args) {
                    if (raw == null) continue;
                    String a = raw.trim();
                    if (a.startsWith("--seeds=")) {
                        seeds = parseSeeds(a.substring("--seeds=".length()));
                        continue;
                    }
                    if (a.startsWith("--output=")) {
                        output = a.substring("--output=".length()).trim();
                        continue;
                    }
                    if (a.startsWith("--maxTicks=")) {
                        try {
                            maxTicks = Math.max(1000, Integer.parseInt(a.substring("--maxTicks=".length()).trim()));
                        } catch (Exception ignored) {
                            maxTicks = 450000;
                        }
                    }
                }
            }
            return new HarnessArgs(seeds, output, maxTicks);
        }

        private static int[] parseSeeds(String csv) {
            if (csv == null || csv.isBlank()) return Arrays.copyOf(DEFAULT_SEEDS, DEFAULT_SEEDS.length);
            String[] parts = csv.split(",");
            List<Integer> out = new ArrayList<>();
            for (String p : parts) {
                try {
                    out.add(Integer.parseInt(p.trim()));
                } catch (Exception ignored) {
                    // Ignore malformed seed token.
                }
            }
            if (out.isEmpty()) return Arrays.copyOf(DEFAULT_SEEDS, DEFAULT_SEEDS.length);
            int[] seeds = new int[out.size()];
            for (int i = 0; i < out.size(); i++) seeds[i] = out.get(i);
            return seeds;
        }
    }
}
