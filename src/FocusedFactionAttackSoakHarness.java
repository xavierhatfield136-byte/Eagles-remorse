import app.config.GameConfig;
import app.config.GameMode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministic owner-facing campaign soak for persistent fleets and lawful faction attacks. */
public final class FocusedFactionAttackSoakHarness {
    private FocusedFactionAttackSoakHarness() {}

    public static void main(String[] args) throws Exception {
        int seeds = intArg(args, "--seeds=", 3);
        int seconds = intArg(args, "--seconds=", 1200);
        Path report = Path.of(stringArg(args, "--report=", "build/reports/focused-faction-attack-soak.csv"));
        ArrayList<String> rows = new ArrayList<>();
        ArrayList<String> failures = new ArrayList<>();
        rows.add("seed,second,green_sites,yellow_sites,dark_yellow_sites,red_sites,other_sites,"
                + "green_fleets,yellow_fleets,dark_yellow_fleets,red_fleets,active_commitments,arrows,routes,"
                + "projection_mismatches,recent_disappearances,recent_ownership_changes,recent_ownership_rejections");
        int[] checkpoints = checkpoints(seconds);

        for (int seedIndex = 0; seedIndex < seeds; seedIndex++) {
            long seed = 71000L + seedIndex;
            GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS,
                    5000, 5000, true, seed, false));
            SpawnSystem.initWorld(ctx);
            if (!CampaignSystem.isStrategicGalaxyMapMode(ctx)) {
                failures.add("seed=" + seed + " normal strategic bootstrap did not activate");
            }
            Map<String, String> initial = CampaignSystem.campaignTerritoryOwnershipSnapshot(ctx);
            Map<String, String> previousOwners = new LinkedHashMap<>(initial);
            int elapsed = 0;
            for (int checkpoint : checkpoints) {
                int target = Math.min(seconds, checkpoint);
                while (elapsed < target) {
                    int step = Math.min(1, target - elapsed);
                    for (int substep = 0; substep < step * 5; substep++) {
                        CampaignSystem.update(ctx, 0.2);
                        keepStrategicSoakRunning(ctx);
                    }
                    elapsed += step;
                    ctx.campaign.sectorElapsed = Math.max(ctx.campaign.sectorElapsed, elapsed);
                    CampaignSystem.ensureOpeningFocusedOperationForTest(ctx);
                    CampaignSystem.updateFactionAttackCommitmentsForTest(ctx);
                    CampaignSystem.updateCampaignIntelligenceForTest(ctx);
                    validate(ctx, seed, elapsed, previousOwners, failures);
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
                                 Map<String, String> previousOwners,
                                 List<String> failures) {
        Map<String, String> owners = CampaignSystem.campaignTerritoryOwnershipSnapshot(ctx);
        List<String> telemetry = CampaignSystem.campaignReleaseTelemetryHistory(ctx);
        for (Map.Entry<String, String> entry : owners.entrySet()) {
            String previous = previousOwners.get(entry.getKey());
            if (previous == null || previous.equals(entry.getValue())) continue;
            String locationToken = "location=" + entry.getKey();
            boolean explained = telemetry.stream().anyMatch(line -> line.contains("campaign.ownership.changed")
                    && line.contains(locationToken) && line.contains("op=") && line.contains("attacker="));
            if (!explained) {
                failures.add("seed=" + seed + " t=" + elapsed + " unexplained ownership change at "
                        + entry.getKey() + " from " + previous + " to " + entry.getValue());
            }
        }
        previousOwners.clear();
        previousOwners.putAll(owners);
        Map<Faction, Integer> fleets = fleetCounts(ctx);
        requireFleet(fleets, Faction.TEAM_C, seed, elapsed, failures);
        requireFleet(fleets, Faction.BRIGHT_YELLOW, seed, elapsed, failures);
        requireFleet(fleets, Faction.ENEMY, seed, elapsed, failures);
        for (String line : CampaignSystem.campaignFleetProjectionParityLines(ctx)) {
            if (line.contains("PROJECTION_MISMATCH")) {
                failures.add("seed=" + seed + " t=" + elapsed + " " + line);
            }
        }
        for (String line : telemetry) {
            if (line.contains("campaign.fleet.disappeared")
                    && (!line.contains("forceId=") || !line.contains("reason="))) {
                failures.add("seed=" + seed + " t=" + elapsed + " unexplained fleet disappearance " + line);
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
        Map<Faction, Integer> fleets = fleetCounts(ctx);
        List<String> telemetry = CampaignSystem.campaignReleaseTelemetryHistory(ctx);
        long mismatches = CampaignSystem.campaignFleetProjectionParityLines(ctx).stream()
                .filter(line -> line.contains("PROJECTION_MISMATCH")).count();
        long disappearances = telemetry.stream().filter(line -> line.contains("campaign.fleet.disappeared")).count();
        long ownershipChanges = telemetry.stream().filter(line -> line.contains("campaign.ownership.changed")).count();
        long ownershipRejections = telemetry.stream().filter(line -> line.contains("campaign.ownership.rejected")).count();
        return seed + "," + elapsed + "," + green + "," + yellow + "," + darkYellow + "," + red + ","
                + other + "," + fleets.getOrDefault(Faction.TEAM_C, 0) + ","
                + fleets.getOrDefault(Faction.BRIGHT_YELLOW, 0) + ","
                + fleets.getOrDefault(Faction.DARK_YELLOW, 0) + ","
                + fleets.getOrDefault(Faction.ENEMY, 0) + ","
                + ctx.campaign.factionAttackCommitments.activeCommitments().size() + ","
                + CampaignSystem.campaignInvasionArrows(ctx).size() + ","
                + CampaignSystem.campaignRouteSegments(ctx).size() + ","
                + mismatches + "," + disappearances + "," + ownershipChanges + "," + ownershipRejections;
    }

    private static Map<Faction, Integer> fleetCounts(GameContext ctx) {
        Map<Faction, Integer> counts = new LinkedHashMap<>();
        for (CampaignSystem.CampaignForceSummary force : CampaignSystem.campaignForceSummaries(ctx)) {
            if (force != null && force.faction != null) counts.merge(force.faction, 1, Integer::sum);
        }
        return counts;
    }

    private static void requireFleet(Map<Faction, Integer> fleets,
                                     Faction faction,
                                     long seed,
                                     int elapsed,
                                     List<String> failures) {
        if (fleets.getOrDefault(faction, 0) <= 0) {
            failures.add("seed=" + seed + " t=" + elapsed + " no physical " + faction + " fleet");
        }
    }

    private static void keepStrategicSoakRunning(GameContext ctx) {
        if (ctx == null || ctx.campaign == null || ctx.ui == null) return;
        if (ctx.ui.strategicEncounterPrompt.active) ctx.ui.clearStrategicEncounterPrompt();
        if (ctx.ui.campaignHubMenu.active) ctx.ui.clearCampaignHubMenu();
        ctx.campaign.strategicOvermapMode = true;
        ctx.ui.mapOpen = true;
        if (ctx.state != GameState.GAME_OVER) ctx.state = GameState.MAP;
    }

    private static int[] checkpoints(int seconds) {
        return java.util.Arrays.stream(new int[]{0, 30, 60, 180, 600, Math.max(0, seconds)})
                .map(value -> Math.min(Math.max(0, seconds), value))
                .distinct()
                .sorted()
                .toArray();
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
