import app.config.GameConfig;
import app.config.GameMode;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Owner-playtest baseline audit for normal Standard Command Open World Campaign runs. */
public final class OwnerPlaytestBaselineHarness {
    public static final long GREEN_HEAVY_SEED = 71000L;
    public static final long YELLOW_HEAVY_SEED = 71001L;
    public static final long RED_HEAVY_SEED = 71002L;
    public static final long[] APPROVED_BASELINE_SEEDS = {
            GREEN_HEAVY_SEED,
            YELLOW_HEAVY_SEED,
            RED_HEAVY_SEED
    };

    private OwnerPlaytestBaselineHarness() {}

    public static void main(String[] args) throws Exception {
        int seconds = intArg(args, "--seconds=", 1200);
        Path timeline = Path.of(stringArg(args, "--timeline=", "build/reports/owner-playtest-baseline-timeline.csv"));
        Path snapshot = Path.of(stringArg(args, "--snapshot=", "build/reports/owner-playtest-baseline-snapshot.md"));
        RunReport report = runAndWrite(seconds, timeline, snapshot);
        if (!report.failures.isEmpty()) {
            for (String failure : report.failures) System.err.println("[owner-baseline] " + failure);
            throw new IllegalStateException("Owner baseline failed with " + report.failures.size() + " invariant violation(s)");
        }
        System.out.println("[owner-baseline] PASS seeds=" + APPROVED_BASELINE_SEEDS.length
                + " seconds=" + seconds
                + " timeline=" + timeline
                + " snapshot=" + snapshot);
    }

    public static RunReport run(int seconds) {
        int safeSeconds = Math.max(0, seconds);
        RunReport report = new RunReport(safeSeconds, gitCommit(), Instant.now().toString());
        report.timelineRows.add(TimelineRow.header());
        for (long seed : APPROVED_BASELINE_SEEDS) {
            runSeed(report, seed, safeSeconds, null);
        }
        report.completed = true;
        return report;
    }

    public static RunReport runAndWrite(int seconds, Path timeline, Path snapshot) throws Exception {
        int safeSeconds = Math.max(0, seconds);
        RunReport report = new RunReport(safeSeconds, gitCommit(), Instant.now().toString());
        report.timelineRows.add(TimelineRow.header());
        CheckpointSink sink = current -> {
            writeTimeline(timeline, current.timelineRows);
            writeSnapshot(snapshot, current);
        };
        sink.afterCheckpoint(report);
        for (long seed : APPROVED_BASELINE_SEEDS) {
            runSeed(report, seed, safeSeconds, sink);
        }
        report.completed = true;
        sink.afterCheckpoint(report);
        return report;
    }

    private static void runSeed(RunReport report, long seed, int seconds, CheckpointSink sink) {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, seed, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        if (!CampaignSystem.isStrategicGalaxyMapMode(ctx)) {
            report.failures.add("seed=" + seed + " normal Standard Command strategic map did not activate");
        }
        Map<String, String> previousOwners = new LinkedHashMap<>(CampaignSystem.campaignTerritoryOwnershipSnapshot(ctx));
        int elapsed = 0;
        for (int checkpoint : checkpoints(seconds)) {
            while (elapsed < checkpoint) {
                for (int i = 0; i < 5; i++) {
                    keepStrategicSoakRunning(ctx);
                    CampaignSystem.update(ctx, 0.2);
                }
                elapsed++;
                ctx.campaign.sectorElapsed = Math.max(ctx.campaign.sectorElapsed, elapsed);
            }
            validateCheckpoint(ctx, report, seed, elapsed, previousOwners);
            report.timelineRows.add(TimelineRow.capture(ctx, seed, elapsed));
            flushCheckpoint(sink, report);
        }
    }

    private static void flushCheckpoint(CheckpointSink sink, RunReport report) {
        if (sink == null) return;
        try {
            sink.afterCheckpoint(report);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to write owner baseline checkpoint", ex);
        }
    }

    private static void validateCheckpoint(GameContext ctx,
                                           RunReport report,
                                           long seed,
                                           int elapsed,
                                           Map<String, String> previousOwners) {
        Map<String, String> owners = CampaignSystem.campaignTerritoryOwnershipSnapshot(ctx);
        List<String> telemetry = CampaignSystem.campaignReleaseTelemetryHistory(ctx);
        for (Map.Entry<String, String> entry : owners.entrySet()) {
            String previous = previousOwners.get(entry.getKey());
            if (previous == null || previous.equals(entry.getValue())) continue;
            String locationToken = "location=" + entry.getKey();
            boolean explained = telemetry.stream().anyMatch(line -> line.contains("campaign.ownership.changed")
                    && line.contains(locationToken)
                    && line.contains("op=")
                    && line.contains("attacker=")
                    && line.contains("defender=")
                    && line.contains("outcome="));
            if (!explained) {
                report.failures.add("seed=" + seed + " t=" + elapsed
                        + " ownership change lacks authorizing operation/fleets/outcome at " + entry.getKey());
            }
        }
        previousOwners.clear();
        previousOwners.putAll(owners);
        for (String line : CampaignSystem.campaignFleetProjectionParityLines(ctx)) {
            if (line.contains("PROJECTION_MISMATCH")) report.failures.add("seed=" + seed + " t=" + elapsed + " " + line);
        }
        for (String line : telemetry) {
            if (line.contains("campaign.fleet.disappeared")
                    && (!line.contains("forceId=") || !line.contains("reason="))) {
                report.failures.add("seed=" + seed + " t=" + elapsed + " unexplained fleet disappearance " + line);
            }
        }
        requireFactionActivity(ctx, report, seed, elapsed, Faction.TEAM_C, "Green");
        requireFactionActivity(ctx, report, seed, elapsed, Faction.BRIGHT_YELLOW, "Yellow");
        requireFactionActivity(ctx, report, seed, elapsed, Faction.ENEMY, "Red");
    }

    private static void requireFactionActivity(GameContext ctx,
                                               RunReport report,
                                               long seed,
                                               int elapsed,
                                               Faction faction,
                                               String label) {
        boolean present = false;
        for (CampaignSystem.CampaignForceSummary force : CampaignSystem.campaignForceSummaries(ctx)) {
            if (force != null && force.faction == faction && force.kind != CampaignSystem.CampaignForceKind.PLAYER_FLEET) {
                present = true;
                break;
            }
        }
        if (!present) report.failures.add("seed=" + seed + " t=" + elapsed + " no " + label + " campaign fleet activity");
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
        return java.util.Arrays.stream(new int[]{0, 30, 60, 180, 600, 1200, Math.max(0, seconds)})
                .map(value -> Math.min(Math.max(0, seconds), value))
                .distinct()
                .sorted()
                .toArray();
    }

    private static void writeTimeline(Path path, List<String> rows) throws Exception {
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.write(path, rows, StandardCharsets.UTF_8);
    }

    private static void writeSnapshot(Path path, RunReport report) throws Exception {
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        ArrayList<String> lines = new ArrayList<>();
        lines.add("# Owner Playtest Baseline Snapshot");
        lines.add("");
        lines.add("- Generated: " + report.generatedAt);
        lines.add("- Git commit: " + report.gitCommit);
        lines.add("- Preset: Standard Command Open World Campaign");
        lines.add("- Resolution: 5000x5000");
        lines.add("- Duration seconds: " + report.seconds);
        lines.add("- Seeds: Green-heavy " + GREEN_HEAVY_SEED
                + ", Yellow-heavy " + YELLOW_HEAVY_SEED
                + ", Red-heavy " + RED_HEAVY_SEED);
        lines.add("- Result: " + report.resultLabel());
        lines.add("");
        lines.add("## Invariant failures");
        if (report.failures.isEmpty()) {
            lines.add("- None");
        } else {
            for (String failure : report.failures) lines.add("- " + failure);
        }
        lines.add("");
        lines.add("## Timeline preview");
        int start = Math.max(1, report.timelineRows.size() - 12);
        for (int i = start; i < report.timelineRows.size(); i++) {
            lines.add("- `" + report.timelineRows.get(i) + "`");
        }
        Files.write(path, lines, StandardCharsets.UTF_8);
    }

    private static String gitCommit() {
        try {
            Process process = new ProcessBuilder("git", "rev-parse", "--short", "HEAD")
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                process.waitFor();
                if (line != null && !line.isBlank()) return line.trim();
            }
        } catch (Exception ignored) {
            return "unknown";
        }
        return "unknown";
    }

    private static int intArg(String[] args, String prefix, int fallback) {
        try { return Integer.parseInt(stringArg(args, prefix, Integer.toString(fallback))); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static String stringArg(String[] args, String prefix, String fallback) {
        if (args != null) {
            for (String arg : args) {
                if (arg != null && arg.startsWith(prefix)) return arg.substring(prefix.length());
            }
        }
        return fallback;
    }

    private interface CheckpointSink {
        void afterCheckpoint(RunReport report) throws Exception;
    }

    public static final class RunReport {
        public final int seconds;
        public final String gitCommit;
        public final String generatedAt;
        public final ArrayList<String> timelineRows = new ArrayList<>();
        public final ArrayList<String> failures = new ArrayList<>();
        public boolean completed = false;

        RunReport(int seconds, String gitCommit, String generatedAt) {
            this.seconds = Math.max(0, seconds);
            this.gitCommit = gitCommit == null || gitCommit.isBlank() ? "unknown" : gitCommit;
            this.generatedAt = generatedAt == null || generatedAt.isBlank() ? Instant.now().toString() : generatedAt;
        }

        public boolean passed() {
            return completed && failures.isEmpty();
        }

        public String resultLabel() {
            if (!failures.isEmpty()) return "FAIL";
            return completed ? "PASS" : "RUNNING/PARTIAL";
        }
    }

    private record TimelineRow(long seed,
                               int second,
                               int greenFleets,
                               int yellowFleets,
                               int redFleets,
                               int activeOperations,
                               int invasionArrows,
                               int routeSegments,
                               int ownershipChanges,
                               int disappearances,
                               String selectedObjective,
                               String resourceState) {
        static String header() {
            return "seed,second,green_fleets,yellow_fleets,red_fleets,active_operations,invasion_arrows,route_segments,"
                    + "ownership_changes,disappearances,selected_objective,resource_state";
        }

        static String capture(GameContext ctx, long seed, int elapsed) {
            int green = 0;
            int yellow = 0;
            int red = 0;
            for (CampaignSystem.CampaignForceSummary force : CampaignSystem.campaignForceSummaries(ctx)) {
                if (force == null || force.kind == CampaignSystem.CampaignForceKind.PLAYER_FLEET) continue;
                if (force.faction == Faction.TEAM_C) green++;
                else if (force.faction == Faction.BRIGHT_YELLOW || force.faction == Faction.DARK_YELLOW || force.faction == Faction.TEAM_D) yellow++;
                else if (force.faction == Faction.ENEMY) red++;
            }
            List<String> telemetry = CampaignSystem.campaignReleaseTelemetryHistory(ctx);
            long ownershipChanges = telemetry.stream().filter(line -> line.contains("campaign.ownership.changed")).count();
            long disappearances = telemetry.stream().filter(line -> line.contains("campaign.fleet.disappeared")).count();
            String objective = CampaignSystem.campaignSummarySidebarLines(ctx).stream().findFirst().orElse("Objective unavailable");
            String resources = CampaignSystem.campaignAuthoritativeEconomyLedgerLines(ctx).stream()
                    .filter(line -> line.startsWith("Fuel: "))
                    .findFirst()
                    .orElse("Fuel unavailable");
            return new TimelineRow(seed, elapsed, green, yellow, red,
                    ctx.campaign.factionAttackCommitments.activeCommitments().size(),
                    CampaignSystem.campaignInvasionArrows(ctx).size(),
                    CampaignSystem.campaignRouteSegments(ctx).size(),
                    (int) ownershipChanges,
                    (int) disappearances,
                    compact(objective),
                    compact(resources)).toCsv();
        }

        String toCsv() {
            return seed + "," + second + "," + greenFleets + "," + yellowFleets + "," + redFleets + ","
                    + activeOperations + "," + invasionArrows + "," + routeSegments + ","
                    + ownershipChanges + "," + disappearances + "," + csv(selectedObjective) + "," + csv(resourceState);
        }

        private static String compact(String value) {
            if (value == null) return "";
            return value.replace('\n', ' ').replace('\r', ' ').trim();
        }

        private static String csv(String value) {
            String raw = value == null ? "" : value;
            if (!raw.contains(",") && !raw.contains("\"") && !raw.contains("\n")) return raw;
            return "\"" + raw.replace("\"", "\"\"") + "\"";
        }
    }
}
