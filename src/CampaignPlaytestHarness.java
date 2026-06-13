import app.config.GameConfig;
import app.config.GameMode;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Deterministic campaign playtest harness.
 *
 * It is intentionally report-driven: each agent records every issue it finds so
 * a failed run gives a usable playtest report instead of stopping at the first
 * broken assertion.
 */
public final class CampaignPlaytestHarness {
    private static final int[] CAMPAIGN_SECTORS = {
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12,
            13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23
    };
    private static final long[] DEFAULT_SEEDS = {1234L, 778899L, 424242L};

    private CampaignPlaytestHarness() {}

    public static PlaytestReport runDefaultSuite() {
        PlaytestReport report = new PlaytestReport();
        List<PlaytestAgent> agents = List.of(
                new BootstrapAgent(),
                new StrategicTravelAgent(),
                new MissionSectorSweepAgent(),
                new SafeExitAgent(),
                new RemovedFeatureGuardAgent(),
                new TextClarityAgent()
        );
        for (PlaytestAgent agent : agents) {
            try {
                agent.run(report);
            } catch (Throwable t) {
                report.issue(agent.name(), "agent crashed: " + t.getClass().getSimpleName() + " - " + safeMessage(t));
            }
        }
        return report;
    }

    private interface PlaytestAgent {
        String name();
        void run(PlaytestReport report) throws Exception;
    }

    public static final class PlaytestReport {
        private final ArrayList<String> issues = new ArrayList<>();
        private final ArrayList<String> notes = new ArrayList<>();

        public boolean passed() {
            return issues.isEmpty();
        }

        public List<String> issues() {
            return List.copyOf(issues);
        }

        public List<String> notes() {
            return List.copyOf(notes);
        }

        private void issue(String agent, String detail) {
            issues.add(agent + ": " + detail);
        }

        private void note(String agent, String detail) {
            notes.add(agent + ": " + detail);
        }

        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append(passed() ? "PLAYTEST PASS" : "PLAYTEST FAIL");
            sb.append("  |  issues ").append(issues.size());
            sb.append("  |  notes ").append(notes.size());
            if (!issues.isEmpty()) {
                sb.append(System.lineSeparator()).append("Issues:");
                for (String issue : issues) {
                    sb.append(System.lineSeparator()).append("- ").append(issue);
                }
            }
            if (!notes.isEmpty()) {
                sb.append(System.lineSeparator()).append("Notes:");
                int limit = Math.min(12, notes.size());
                for (int i = 0; i < limit; i++) {
                    sb.append(System.lineSeparator()).append("- ").append(notes.get(i));
                }
                if (notes.size() > limit) {
                    sb.append(System.lineSeparator()).append("- ... ").append(notes.size() - limit).append(" more notes");
                }
            }
            return sb.toString();
        }
    }

    private static final class BootstrapAgent implements PlaytestAgent {
        @Override
        public String name() {
            return "bootstrap";
        }

        @Override
        public void run(PlaytestReport report) throws Exception {
            for (long seed : DEFAULT_SEEDS) {
                GameContext ctx = campaignContext(seed);
                tickCampaign(ctx, 180, GameContext.DT);
                check(ctx.campaign != null && ctx.campaign.enabled, report, name(), "campaign not enabled for seed " + seed);
                check(ctx.player != null && ctx.player.alive && ctx.player.hp > 0, report, name(), "player not alive after bootstrap seed " + seed);
                check(!CampaignSystem.mainCampaignLocations(ctx).isEmpty(), report, name(), "no main map locations seed " + seed);
                check(!CampaignSystem.campaignVisibleActions(ctx).isEmpty(), report, name(), "no command actions seed " + seed);
                check(ctx.ui == null || ctx.ui.voiceCaption == null || !ctx.ui.voiceCaption.contains("Earth has fallen"),
                        report, name(), "legacy Earthfall caption returned seed " + seed);
                report.note(name(), "seed " + seed + " booted with " + CampaignSystem.mainCampaignLocations(ctx).size() + " main locations");
            }
        }
    }

    private static final class StrategicTravelAgent implements PlaytestAgent {
        @Override
        public String name() {
            return "strategic-travel";
        }

        @Override
        public void run(PlaytestReport report) throws Exception {
            GameContext ctx = campaignContext(778899L);
            CampaignSystem.CampaignState st = ctx.campaign;
            double startX = st.playerGalaxyX;
            double startY = st.playerGalaxyY;
            double targetX = GameMath.clamp(startX + 900.0, 0.0, ctx.WORLD_W);
            double targetY = GameMath.clamp(startY + 350.0, 0.0, ctx.WORLD_H);
            boolean selected = CampaignSystem.selectCampaignFreeTravelTarget(ctx, targetX, targetY);
            check(selected, report, name(), "could not select free travel target");
            boolean started = CampaignSystem.startTravelToSelectedLocation(ctx);
            check(started || st.galaxyTravel.traveling, report, name(), "travel did not start toward free target");
            for (int i = 0; i < 360; i++) {
                updateCampaignTravel(ctx, st, GameContext.DT);
                CampaignSystem.update(ctx, GameContext.DT);
            }
            double moved = Math.hypot(st.playerGalaxyX - startX, st.playerGalaxyY - startY);
            check(moved > 1.0, report, name(), "fleet did not move after travel start");
            check(finite(st.playerGalaxyX) && finite(st.playerGalaxyY), report, name(), "fleet position became non-finite");
            check(CampaignSystem.campaignNavigationStationLines(ctx).stream().anyMatch(line -> line.startsWith("Travel: ")),
                    report, name(), "navigation station missing Travel readout");
            report.note(name(), "travel moved " + (int) Math.round(moved) + " units toward free target");
        }
    }

    private static final class MissionSectorSweepAgent implements PlaytestAgent {
        @Override
        public String name() {
            return "mission-sector-sweep";
        }

        @Override
        public void run(PlaytestReport report) throws Exception {
            for (int sector : CAMPAIGN_SECTORS) {
                GameContext ctx = campaignContext(9000L + sector);
                startSector(ctx, sector);
                ctx.campaign.introSequenceActive = false;
                tickFull(ctx, 120);

                check(ctx.player != null && ctx.player.alive, report, name(), "player dead after sector start " + sector);
                check(!ctx.gameOver, report, name(), "game over during sector start " + sector);
                check(finite(ctx.player.x) && finite(ctx.player.y), report, name(), "player position non-finite sector " + sector);
                check(!CampaignSystem.activeObjectiveMarkers(ctx).isEmpty(), report, name(), "no objective markers sector " + sector);
                checkLocalSupportMarkers(ctx, report, name(), sector);
                checkLocalMapView(ctx, report, name(), sector);
            }
            report.note(name(), "swept " + CAMPAIGN_SECTORS.length + " campaign sectors");
        }
    }

    private static final class SafeExitAgent implements PlaytestAgent {
        @Override
        public String name() {
            return "safe-exit";
        }

        @Override
        public void run(PlaytestReport report) throws Exception {
            GameContext early = campaignContext(1010L);
            startSector(early, 10);
            early.campaign.introSequenceActive = false;
            early.campaign.sectorElapsed = 2.0;
            check(CampaignSystem.canStartSafeMissionExit(early), report, name(), "safe exit unavailable inside entry window");
            check(CampaignSystem.completeSafeMissionExit(early), report, name(), "safe exit failed inside entry window");
            check(CampaignSystem.isStrategicOvermapMode(early), report, name(), "safe exit did not return to strategic map");

            GameContext late = campaignContext(1011L);
            startSector(late, 10);
            late.campaign.introSequenceActive = false;
            late.campaign.sectorElapsed = CampaignSystem.safeMissionExitEntryWindowSeconds() + 2.0;
            late.campaign.objectiveSecured = false;
            check(!CampaignSystem.canStartSafeMissionExit(late), report, name(), "safe exit still starts after entry window without objective");
            check(!CampaignSystem.completeSafeMissionExit(late), report, name(), "safe exit completed after entry window without objective");
            report.note(name(), "entry-window safe exit behavior verified");
        }
    }

    private static final class RemovedFeatureGuardAgent implements PlaytestAgent {
        @Override
        public String name() {
            return "removed-feature-guard";
        }

        @Override
        public void run(PlaytestReport report) throws Exception {
            GameContext strategic = campaignContext(2020L);
            strategic.ui.campaignCommandTab = UiState.CampaignCommandTab.STRIKES;
            List<CampaignSystem.CampaignAction> strategicActions = CampaignSystem.campaignVisibleActions(strategic);
            check(strategicActions.stream().noneMatch(action -> action.category == CampaignSystem.CampaignActionCategory.STRIKES),
                    report, name(), "strategic Strike tab still exposes strike actions");

            GameContext tactical = campaignContext(2021L);
            startSector(tactical, 10);
            tactical.campaign.introSequenceActive = false;
            tactical.ui.tacticalMapTab = UiState.TacticalMapTab.STRIKES;
            List<CampaignSystem.CampaignAction> tacticalActions = CampaignSystem.tacticalMapVisibleActions(tactical);
            check(tacticalActions.stream().noneMatch(action -> action.category == CampaignSystem.CampaignActionCategory.STRIKES),
                    report, name(), "tactical Strike tab still exposes strike actions");
            check(!CampaignSystem.executeTacticalMapAction(tactical, "TACTICAL_TORPEDO_STRIKE"),
                    report, name(), "hidden tactical torpedo action still executes");
            report.note(name(), "removed strike tabs stay inert");
        }
    }

    private static final class TextClarityAgent implements PlaytestAgent {
        private static final String[] BANNED_VISIBLE_PHRASES = {
                "ROUTE STATUS",
                "Action Window",
                "Time Window",
                "Route State",
                "Route Risk",
                "Theater Shift",
                "Contact Net",
                "Pressure Band",
                "Logistics Pressure",
                "Regional Pressure",
                "LIVE AUTHORITY",
                "TASK GROUPS",
                "WAR TIMELINE",
                "DIRECTORS  |",
                "route pressure",
                "command picture",
                "search picture",
                "contact picture",
                "Strike Heat",
                "INTEL LOCK",
                "strike windows",
                "Earth has fallen",
                "return home immediately",
                "TAB opens persistent fleet management",
                "B opens command-ship upgrades",
                "REMOTE STRIKES HELD"
        };

        @Override
        public String name() {
            return "text-clarity";
        }

        @Override
        public void run(PlaytestReport report) throws Exception {
            GameContext ctx = campaignContext(3030L);
            ArrayList<String> live = new ArrayList<>();
            live.addAll(CampaignSystem.campaignNavigationStationLines(ctx));
            live.addAll(CampaignSystem.campaignReceiverBoardLines(ctx));
            live.addAll(CampaignSystem.campaignDirectionFinderLines(ctx));
            live.addAll(CampaignSystem.campaignCommsBoardLines(ctx));
            live.addAll(CampaignSystem.campaignSummarySidebarLines(ctx));
            live.addAll(CampaignSystem.campaignStrategicAuthorityLines(ctx));
            for (CampaignSystem.CampaignAction action : CampaignSystem.campaignVisibleActions(ctx)) {
                if (action == null) continue;
                live.add(action.label);
                live.add(action.shortDescription);
                live.add(action.tooltip);
                live.add(action.disabledReason);
            }
            scanText("live-ui", live, report);
            scanSource(report);
            report.note(name(), "checked live campaign text and source for removed phrases");
        }

        private void scanText(String scope, List<String> lines, PlaytestReport report) {
            for (String line : lines) {
                if (line == null || line.isBlank()) continue;
                for (String banned : BANNED_VISIBLE_PHRASES) {
                    if (line.contains(banned)) {
                        report.issue(name(), scope + " contains stale phrase '" + banned + "': " + line);
                    }
                }
            }
        }

        private void scanSource(PlaytestReport report) throws Exception {
            Path root = Path.of("src");
            if (!Files.isDirectory(root)) return;
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(path -> path.toString().endsWith(".java"))
                        .filter(path -> !path.getFileName().toString().equals("CampaignPlaytestHarness.java"))
                        .forEach(path -> scanSourceFile(path, report));
            }
        }

        private void scanSourceFile(Path path, PlaytestReport report) {
            try {
                String text = Files.readString(path);
                for (String banned : BANNED_VISIBLE_PHRASES) {
                    if (text.contains(banned)) {
                        report.issue(name(), path + " contains stale phrase '" + banned + "'");
                    }
                }
            } catch (Exception e) {
                report.issue(name(), "could not scan " + path + ": " + safeMessage(e));
            }
        }
    }

    private static GameContext campaignContext(long seed) {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, seed, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static void tickCampaign(GameContext ctx, int ticks, double dt) {
        for (int i = 0; i < Math.max(0, ticks); i++) {
            CampaignSystem.update(ctx, dt);
            EventSystem.update(ctx, dt);
            AudioSystem.update(ctx, dt);
        }
    }

    private static void tickFull(GameContext ctx, int ticks) {
        for (int i = 0; i < Math.max(0, ticks); i++) {
            double dt = GameContext.DT;
            PhysicsSystem.update(ctx, dt);
            ctx.entityQuery.rebuild(ctx);
            AISystem.update(ctx, dt);
            CarrierSystem.update(ctx, dt);
            EconomySystem.update(ctx, dt);
            CampaignSystem.update(ctx, dt);
            EventSystem.update(ctx, dt);
            AudioSystem.update(ctx, dt);
            if (ctx.player != null) {
                ctx.player.x = GameMath.clamp(ctx.player.x, 0.0, ctx.WORLD_W);
                ctx.player.y = GameMath.clamp(ctx.player.y, 0.0, ctx.WORLD_H);
            }
        }
    }

    private static void checkLocalSupportMarkers(GameContext ctx, PlaytestReport report, String agent, int sector) {
        if (ctx == null || ctx.campaign == null || !CampaignSystem.usesMissionSubzones(ctx)) return;
        int loaded = CampaignSystem.currentLoadedMissionSubzone(ctx);
        if (loaded < 0 && ctx.player != null) {
            loaded = CampaignSystem.missionSubzoneForPoint(ctx, ctx.campaign.sector, ctx.player.x, ctx.player.y);
        }
        if (loaded < 0) return;
        for (CampaignSystem.CampaignSupportMarker marker : CampaignSystem.activeSupportMarkers(ctx)) {
            if (marker == null) continue;
            int markerSubzone = CampaignSystem.missionSubzoneForPoint(ctx, ctx.campaign.sector, marker.x, marker.y);
            if (markerSubzone != loaded) {
                report.issue(agent, "sector " + sector + " has distant support marker on tactical map: " + marker.label);
            }
        }
    }

    private static void checkLocalMapView(GameContext ctx, PlaytestReport report, String agent, int sector) {
        if (ctx == null || ctx.campaign == null || !CampaignSystem.usesMissionSubzones(ctx)) return;
        double viewW = UISystem.strategicMapViewWidth(ctx);
        double viewH = UISystem.strategicMapViewHeight(ctx);
        check(viewW < ctx.WORLD_W && viewH < ctx.WORLD_H, report, agent,
                "sector " + sector + " tactical map is not visually focused on local combat");
    }

    private static void startSector(GameContext ctx, int sector) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod("startSector", GameContext.class, int.class);
        method.setAccessible(true);
        method.invoke(null, ctx, sector);
    }

    private static void updateCampaignTravel(GameContext ctx, CampaignSystem.CampaignState st, double dt) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "updateCampaignTravel",
                GameContext.class,
                CampaignSystem.CampaignState.class,
                double.class
        );
        method.setAccessible(true);
        method.invoke(null, ctx, st, dt);
    }

    private static void check(boolean condition, PlaytestReport report, String agent, String detail) {
        if (!condition) report.issue(agent, detail);
    }

    private static boolean finite(double value) {
        return Double.isFinite(value) && !Double.isNaN(value);
    }

    private static double dist2(double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        return dx * dx + dy * dy;
    }

    private static String safeMessage(Throwable t) {
        String message = (t == null) ? "" : t.getMessage();
        if (message == null || message.isBlank()) return "(no message)";
        return message.replace('\n', ' ').replace('\r', ' ');
    }
}
