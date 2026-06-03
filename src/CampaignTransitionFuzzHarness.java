import app.config.GameConfig;
import app.config.GameMode;
import app.persistence.CampaignCheckpointStore;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Random;

/** Randomized but deterministic campaign transition fuzzing for save/load and overmap travel. */
public final class CampaignTransitionFuzzHarness {
    private CampaignTransitionFuzzHarness() {}

    public static void main(String[] args) throws Exception {
        long seed = 20260602L;
        int cycles = 24;
        int ticksPerCycle = 45;
        boolean strict = false;
        for (String arg : args == null ? new String[0] : args) {
            if (arg == null || arg.isBlank()) continue;
            String a = arg.trim();
            if (a.startsWith("--seed=")) seed = parseLong(a.substring("--seed=".length()), seed);
            else if (a.startsWith("--cycles=")) cycles = Math.max(1, parseInt(a.substring("--cycles=".length()), cycles));
            else if (a.startsWith("--ticks=")) ticksPerCycle = Math.max(1, parseInt(a.substring("--ticks=".length()), ticksPerCycle));
            else if ("--strict".equalsIgnoreCase(a)) strict = true;
        }

        Result result = run(seed, cycles, ticksPerCycle);
        System.out.println("[campaign-transition-fuzz] seed=" + seed
                + " cycles=" + cycles
                + " ticksPerCycle=" + ticksPerCycle
                + " checkpoints=" + result.checkpoints
                + " restores=" + result.restores
                + " travelStarts=" + result.travelStarts
                + " signature=" + result.signature
                + " pass=" + result.pass);
        if (!result.pass && strict) System.exit(2);
    }

    public static Result run(long seed, int cycles, int ticksPerCycle) throws Exception {
        Random rng = new Random(seed ^ 0xC0FFEE5EEDL);
        GameContext ctx = newContext(seed);
        Result result = new Result();
        int safeCycles = Math.max(1, cycles);
        int safeTicks = Math.max(1, ticksPerCycle);

        for (int cycle = 0; cycle < safeCycles; cycle++) {
            double tx = 300.0 + rng.nextDouble() * Math.max(1.0, ctx.WORLD_W - 600.0);
            double ty = 300.0 + rng.nextDouble() * Math.max(1.0, ctx.WORLD_H - 600.0);
            if (CampaignSystem.selectCampaignFreeTravelTarget(ctx, tx, ty)
                    && CampaignSystem.startTravelToSelectedLocation(ctx)) {
                result.travelStarts++;
            }
            for (int tick = 0; tick < safeTicks; tick++) {
                CampaignSystem.update(ctx, GameContext.DT);
            }

            CampaignCheckpointStore.Checkpoint checkpoint = captureCheckpoint(ctx, 2 + (cycle % 8));
            result.checkpoints++;
            GameContext restored = newContext(seed);
            if (!applyCheckpoint(restored, checkpoint)) {
                result.pass = false;
                result.signature = "restore_failed_cycle_" + cycle;
                return result;
            }
            result.restores++;
            if (restored.campaign == null || restored.campaign.campaignFuel < 0 || restored.campaign.campaignSupplies < 0
                    || CampaignSystem.campaignForceSummaries(restored).isEmpty()) {
                result.pass = false;
                result.signature = "invalid_restore_cycle_" + cycle;
                return result;
            }
            ctx = restored;
        }
        result.signature = signature(ctx);
        result.pass = true;
        return result;
    }

    private static GameContext newContext(long seed) {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, seed, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static CampaignCheckpointStore.Checkpoint captureCheckpoint(GameContext ctx, int nextSector) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "captureCheckpoint", GameContext.class, CampaignSystem.CampaignState.class, int.class);
        method.setAccessible(true);
        return (CampaignCheckpointStore.Checkpoint) method.invoke(null, ctx, ctx.campaign, nextSector);
    }

    private static boolean applyCheckpoint(GameContext ctx, CampaignCheckpointStore.Checkpoint checkpoint) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "applyCheckpoint", GameContext.class, CampaignSystem.CampaignState.class, CampaignCheckpointStore.Checkpoint.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, ctx, ctx.campaign, checkpoint);
    }

    private static String signature(GameContext ctx) {
        CampaignSystem.CampaignState st = ctx.campaign;
        int forces = CampaignSystem.campaignForceSummaries(ctx).size();
        return "sector=" + st.sector
                + ",fuel=" + st.campaignFuel
                + ",sup=" + st.campaignSupplies
                + ",ammo=" + st.campaignAmmo
                + ",forces=" + forces
                + ",pos=" + fmt(st.playerGalaxyX) + "/" + fmt(st.playerGalaxyY);
    }

    private static String fmt(double v) {
        return String.format(Locale.US, "%.1f", v);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public static final class Result {
        public boolean pass;
        public int checkpoints;
        public int restores;
        public int travelStarts;
        public String signature = "";
    }
}
