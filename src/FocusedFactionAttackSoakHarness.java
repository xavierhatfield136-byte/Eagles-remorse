import app.config.GameConfig;
import app.config.GameMode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministic ten-minute campaign soak for focused faction attacks. */
public final class FocusedFactionAttackSoakHarness {
    private FocusedFactionAttackSoakHarness() {}

    public static void main(String[] args) throws Exception {
        int seeds = intArg(args, "--seeds=", 3);
        int seconds = intArg(args, "--seconds=", 600);
        Path report = Path.of(stringArg(args, "--report=", "build/reports/focused-faction-attack-soak.csv"));
        ArrayList<String> rows = new ArrayList<>();
        ArrayList<String> failures = new ArrayList<>();
        rows.add("seed,second,green,yellow,dark_yellow,red,other,active_commitments,arrows,routes");
        int[] checkpoints = {0, 30, 60, 180, seconds};

        for (int seedIndex = 0; seedIndex < seeds; seedIndex++) {
            long seed = 71000L + seedIndex;
            GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS,
                    5000, 5000, true, seed, false));
            SpawnSystem.initWorld(ctx);
            ctx.campaign.strategicOvermapMode = true;
            Map<String, String> initial = CampaignSystem.campaignTerritoryOwnershipSnapshot(ctx);
            List<String> initialYellow = initial.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(Faction.BRIGHT_YELLOW.name()))
                    .map(Map.Entry::getKey).toList();
            int elapsed = 0;
            for (int checkpoint : checkpoints) {
                int target = Math.min(seconds, checkpoint);
                while (elapsed < target) {
                    int step = Math.min(10, target - elapsed);
                    CampaignSystem.update(ctx, step);
                    elapsed += step;
                    validate(ctx, seed, elapsed, initialYellow, failures);
                }
                rows.add(row(ctx, seed, elapsed));
            }
        }

        Path parent = report.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.write(report, rows, StandardCharsets.UTF_8);
        if (!failures.isEmpty()) {
            for (String failure : failures) System.err.println("[focused-attack-soak] " + failure);
            throw new IllegalStateException("Focused faction attack soak failed with " + failures.size() + " invariant violations");
        }
        System.out.println("[focused-attack-soak] PASS seeds=" + seeds + " seconds=" + seconds + " report=" + report);
    }

    private static void validate(GameContext ctx,
                                 long seed,
                                 int elapsed,
                                 List<String> initialYellow,
                                 List<String> failures) {
        Map<String, String> owners = CampaignSystem.campaignTerritoryOwnershipSnapshot(ctx);
        for (String id : initialYellow) {
            if (!Faction.BRIGHT_YELLOW.name().equals(owners.get(id))) {
                failures.add("seed=" + seed + " t=" + elapsed + " Yellow ownership changed at " + id
                        + " to " + owners.get(id));
            }
        }
        int active = ctx.campaign.factionAttackCommitments.activeCommitments().size();
        if (active > FactionAttackCommitmentSystem.Slot.values().length) {
            failures.add("seed=" + seed + " t=" + elapsed + " active commitment overflow " + active);
        }
        int arrows = CampaignSystem.campaignInvasionArrows(ctx).size();
        if (arrows > active) failures.add("seed=" + seed + " t=" + elapsed + " arrows exceed commitments");
        if (CampaignSystem.campaignRouteSegments(ctx).isEmpty()) {
            failures.add("seed=" + seed + " t=" + elapsed + " route geometry disappeared");
        }
    }

    private static String row(GameContext ctx, long seed, int elapsed) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String owner : CampaignSystem.campaignTerritoryOwnershipSnapshot(ctx).values()) {
            counts.merge(owner, 1, Integer::sum);
        }
        int green = counts.getOrDefault(Faction.TEAM_C.name(), 0) + counts.getOrDefault(Faction.ALLY.name(), 0)
                + counts.getOrDefault(Faction.PLAYER.name(), 0);
        int yellow = counts.getOrDefault(Faction.BRIGHT_YELLOW.name(), 0) + counts.getOrDefault(Faction.TEAM_D.name(), 0);
        int darkYellow = counts.getOrDefault(Faction.DARK_YELLOW.name(), 0);
        int red = counts.getOrDefault(Faction.ENEMY.name(), 0);
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        int other = Math.max(0, total - green - yellow - darkYellow - red);
        return seed + "," + elapsed + "," + green + "," + yellow + "," + darkYellow + "," + red + ","
                + other + "," + ctx.campaign.factionAttackCommitments.activeCommitments().size() + ","
                + CampaignSystem.campaignInvasionArrows(ctx).size() + "," + CampaignSystem.campaignRouteSegments(ctx).size();
    }

    private static int intArg(String[] args, String prefix, int fallback) {
        try { return Integer.parseInt(stringArg(args, prefix, Integer.toString(fallback))); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static String stringArg(String[] args, String prefix, String fallback) {
        if (args != null) for (String arg : args) if (arg != null && arg.startsWith(prefix)) return arg.substring(prefix.length());
        return fallback;
    }
}
