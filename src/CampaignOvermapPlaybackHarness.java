import app.config.GameConfig;
import app.config.GameMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Deterministic headless strategic-overmap playback harness for section-28 validation. */
public final class CampaignOvermapPlaybackHarness {
    private CampaignOvermapPlaybackHarness() {}

    public static void main(String[] args) {
        long seed = 778899L;
        int ticks = 900;
        for (String arg : args == null ? new String[0] : args) {
            if (arg == null || arg.isBlank()) continue;
            String a = arg.trim();
            if (a.startsWith("--seed=")) seed = parseLong(a.substring("--seed=".length()), seed);
            if (a.startsWith("--ticks=")) ticks = Math.max(1, parseInt(a.substring("--ticks=".length()), ticks));
        }
        System.out.println(runSignature(seed, ticks));
    }

    public static String runSignature(long seed, int ticks) {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, seed, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);

        int safeTicks = Math.max(0, ticks);
        for (int i = 0; i < safeTicks; i++) {
            CampaignSystem.update(ctx, GameContext.DT);
        }
        return signature(ctx, safeTicks);
    }

    private static String signature(GameContext ctx, int ticks) {
        List<String> parts = new ArrayList<>();
        CampaignSystem.CampaignState st = ctx.campaign;
        parts.add("seed=" + (ctx.config == null ? 0L : ctx.config.seed));
        parts.add("ticks=" + ticks);
        parts.add("sector=" + (st == null ? 0 : st.sector));
        parts.add("fuel=" + (st == null ? 0 : st.campaignFuel));
        parts.add("supplies=" + (st == null ? 0 : st.campaignSupplies));
        parts.add("ammo=" + (st == null ? 0 : st.campaignAmmo));
        parts.add("salvage=" + (st == null ? 0 : st.campaignSalvage));
        parts.add("player=" + fmt(st == null ? 0.0 : st.playerGalaxyX) + "," + fmt(st == null ? 0.0 : st.playerGalaxyY));
        for (CampaignSystem.CampaignLocation location : CampaignSystem.mainCampaignLocations(ctx)) {
            parts.add("poi:" + location.id + ":" + fmt(location.x) + ":" + fmt(location.y) + ":" + location.discovered);
        }
        for (CampaignSystem.CampaignLocation location : CampaignSystem.campaignAreasOfInterest(ctx)) {
            parts.add("aoi:" + location.id + ":" + fmt(location.x) + ":" + fmt(location.y) + ":" + location.discovered);
        }
        for (CampaignSystem.CampaignForceSummary force : CampaignSystem.campaignForceSummaries(ctx)) {
            parts.add("force:" + force.id + ":" + force.kind + ":" + force.faction + ":" + force.intent
                    + ":" + fmt(force.x) + ":" + fmt(force.y) + ":" + fmt(force.targetX) + ":" + fmt(force.targetY));
        }
        return String.join("|", parts);
    }

    private static String fmt(double value) {
        return String.format(Locale.US, "%.2f", value);
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
}
