import app.config.GameMode;
import app.persistence.AcademyProgressStore;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.WeakHashMap;

public final class TutorialSystem {
    private TutorialSystem() {}

    private static final WeakHashMap<GameContext, TutorialState> STATES = new WeakHashMap<>();
    private static final double ACTIVE_PING_PERIOD = 1.0;
    private static final double POINT_REACHED_RADIUS = 110.0;
    private static final double MINING_RADIUS = 180.0;
    private static final double WEAPON_RANGE_RADIUS = 170.0;
    private static final double PING_MATCH_RADIUS = 220.0;
    private enum LessonId {
        OVERWORLD_MAP_READING,
        SITE_SELECTION,
        PLOT_MOVEMENT,
        SCAN_AND_INTEL,
        RESOURCE_SITE,
        STATION_SERVICES,
        FLEET_ORGANIZATION,
        OVERWORLD_TO_MISSION,
        FLIGHT_BASICS,
        TARGETING_AND_SENSORS,
        LOGISTICS_AND_REFIT,
        BRIDGE_SYSTEMS,
        CARRIER_AND_WARP,
        COMPLETE
    }

    private static final LessonId[] LESSON_FLOW = new LessonId[]{
            LessonId.FLIGHT_BASICS,
            LessonId.TARGETING_AND_SENSORS,
            LessonId.LOGISTICS_AND_REFIT,
            LessonId.BRIDGE_SYSTEMS,
            LessonId.CARRIER_AND_WARP,
            LessonId.OVERWORLD_MAP_READING,
            LessonId.SITE_SELECTION,
            LessonId.PLOT_MOVEMENT,
            LessonId.SCAN_AND_INTEL,
            LessonId.RESOURCE_SITE,
            LessonId.STATION_SERVICES,
            LessonId.FLEET_ORGANIZATION,
            LessonId.OVERWORLD_TO_MISSION,
            LessonId.COMPLETE
    };
    private static final int LESSON_COUNT = LESSON_FLOW.length - 1;

    private static final class Marker {
        final String label;
        final double x;
        final double y;
        final double radius;

        Marker(String label, double x, double y, double radius) {
            this.label = label;
            this.x = x;
            this.y = y;
            this.radius = radius;
        }
    }

    private static final class ChecklistItem {
        final String label;
        final String hint;
        final boolean complete;

        ChecklistItem(String label, String hint, boolean complete) {
            this.label = label;
            this.hint = hint;
            this.complete = complete;
        }
    }

    private static final class TutorialState {
        String academySessionId = UUID.randomUUID().toString();
        Faction playerFaction;
        Faction hostileFaction;
        int homeBaseId = -1;
        int combatTargetId = -1;
        double alphaX;
        double alphaY;
        double betaX;
        double betaY;
        double weaponsX;
        double weaponsY;
        double miningX;
        double miningY;
        double pingTimer = 0.0;
        int lessonIndex = 0;
        double lessonElapsedSec = 0.0;
        int startingOreTotal = 0;
        int startingPlayerCargo = 0;
        int miningPocketOreStart = 0;
        boolean overworldMapReviewed = false;
        boolean selectedTrainingSite = false;
        boolean plottedTrainingCourse = false;
        boolean engagedTrainingCourse = false;
        boolean reachedTrainingHub = false;
        boolean selectedTrainingContact = false;
        boolean trackedTrainingContact = false;
        boolean reachedResourceSite = false;
        boolean reviewedStationServices = false;
        boolean changedFleetCommitment = false;
        boolean enteredTrainingMission = false;
        boolean tacticalSandboxPrepared = false;
        boolean archiveOpen = false;
        final EnumSet<LessonId> skippedLessons = EnumSet.noneOf(LessonId.class);
        boolean reachedAlpha = false;
        boolean betaWaypointSet = false;
        boolean reachedBeta = false;
        boolean pingedWeaponsRange = false;
        boolean tutorialDroneDamaged = false;
        boolean xrayFilterUsed = false;
        boolean xrayRoomFocused = false;
        boolean minedOre = false;
        boolean dockedAtHome = false;
        boolean hangarTierThree = false;
        boolean openedShop = false;
        boolean swappedToCarrier = false;
        boolean powerCrewBaselineCaptured = false;
        boolean powerAdjusted = false;
        boolean crewAdjusted = false;
        boolean seededDamageControlFire = false;
        boolean fireSuppressed = false;
        boolean flightDeckBaselineCaptured = false;
        boolean openedFlightDeck = false;
        boolean launchedWing = false;
        boolean carrierModeChanged = false;
        boolean carrierAutoLaunchChanged = false;
        boolean withdrewToOverworld = false;
        Ship.PowerPreset baselinePowerPreset = Ship.PowerPreset.BALANCED;
        double[] baselinePowerBuses = new double[]{};
        GameContext.CaptainDirective baselineCaptainDirective = GameContext.CaptainDirective.BALANCED;
        GameContext.HelmMode baselineHelmMode = GameContext.HelmMode.INTERCEPT;
        GameContext.TacticalMode baselineTacticalMode = GameContext.TacticalMode.DEFENSIVE;
        GameContext.EngineeringMode baselineEngineeringMode = GameContext.EngineeringMode.BALANCED;
        Ship.CarrierCommandMode baselineCarrierMode = Ship.CarrierCommandMode.ATTACK;
        boolean baselineCarrierAutoLaunch = false;
    }

    public static void init(GameContext ctx, Faction playerFaction) {
        if (ctx == null) return;

        ctx.ships.clear();
        ctx.projectiles.clear();
        ctx.asteroids.clear();
        ctx.salvage.clear();
        ctx.damageEvents.clear();
        ctx.audioEvents.clear();
        ctx.teamBases.clear();
        ctx.baseUpgrades.clear();
        ctx.lockedTarget = null;
        ctx.allyBase = null;
        ctx.enemyBase = null;
        ctx.ui.waypointX = Double.NaN;
        ctx.ui.waypointY = Double.NaN;
        ctx.ui.mapPings.clear();
        ctx.campaign = null;
        ctx.credits = 15000;
        ctx.enemyWaveTimer = Double.POSITIVE_INFINITY;
        ctx.nextEventTimer = Double.POSITIVE_INFINITY;
        ctx.minerReinforcementTimer = Double.POSITIVE_INFINITY;
        ctx.orePriceMul = 1.0;
        ctx.orePriceT = 0.0;
        ctx.miningMul = 1.0;
        ctx.miningT = 0.0;
        ctx.ui.hudDetail = GameContext.HudDetail.FULL;

        TutorialState st = new TutorialState();
        st.playerFaction = (playerFaction == null) ? Faction.ALLY : playerFaction;
        st.hostileFaction = defaultHostileFaction(st.playerFaction);
        STATES.put(ctx, st);
        AcademyProgressStore.markStepStarted(st.academySessionId, "Academy", "start");
        AcademyProgressStore.recordEvent(st.academySessionId,
                "academy_started",
                "Academy",
                "start",
                0.0,
                "started");
        CampaignSystem.initCommandSchoolOverworld(ctx, st.playerFaction);
        enterLesson(ctx, st, LessonId.FLIGHT_BASICS, true);
    }

    public static boolean isActive(GameContext ctx) {
        return state(ctx) != null;
    }

    public static void skipCurrent(GameContext ctx) {
        TutorialState st = state(ctx);
        if (st == null) return;
        LessonId lesson = currentLesson(st);
        if (lesson == LessonId.COMPLETE) return;
        st.skippedLessons.add(lesson);
        AcademyProgressStore.markStepRecovered(st.academySessionId,
                academyChapterLabel(lesson),
                academyStepLabel(lesson),
                "skipped");
        enterLesson(ctx, st, nextLesson(lesson), true);
    }

    public static void toggleArchive(GameContext ctx) {
        TutorialState st = state(ctx);
        if (st == null) return;
        st.archiveOpen = !st.archiveOpen;
    }

    public static void update(GameContext ctx, double dt) {
        TutorialState st = state(ctx);
        if (st == null || ctx == null || ctx.player == null) return;

        st.lessonElapsedSec += Math.max(0.0, dt);
        refreshPersistentProgress(ctx, st);
        handleLessonSideEffects(ctx, st);

        st.pingTimer -= Math.max(0.0, dt);
        if (st.pingTimer <= 0.0) {
            Marker marker = activeMarker(ctx, st);
            if (marker != null) {
                UISystem.addPing(ctx, marker.x, marker.y, 1.6);
            }
            st.pingTimer = ACTIVE_PING_PERIOD;
        }

        for (int i = 0; i < 8; i++) {
            LessonId lesson = currentLesson(st);
            if (lesson == LessonId.COMPLETE) {
                ctx.ui.waypointX = Double.NaN;
                ctx.ui.waypointY = Double.NaN;
                return;
            }
            if (!lessonComplete(ctx, st, lesson)) {
                return;
            }
            AcademyProgressStore.markStepCompleted(st.academySessionId, academyChapterLabel(lesson), academyStepLabel(lesson));
            AcademyProgressStore.recordEvent(st.academySessionId,
                    "academy_chapter_completed",
                    academyChapterLabel(lesson),
                    academyStepLabel(lesson),
                    st.lessonElapsedSec,
                    "complete");
            enterLesson(ctx, st, nextLesson(lesson), true);
            refreshPersistentProgress(ctx, st);
            handleLessonSideEffects(ctx, st);
        }
    }

    public static String hudTitle(GameContext ctx) {
        TutorialState st = state(ctx);
        if (st == null) return "";
        LessonId lesson = currentLesson(st);
        if (lesson == LessonId.COMPLETE) return "TUTORIAL   COMPLETE";
        return "TUTORIAL   " + (lessonFlowPosition(lesson) + 1) + "/" + LESSON_COUNT;
    }

    public static String hudDetail(GameContext ctx) {
        TutorialState st = state(ctx);
        if (st == null) return "";
        LessonId lesson = currentLesson(st);
        if (lesson == LessonId.COMPLETE) {
            return "Command school complete. Stay in the sandbox or press F10 to return to the menu.";
        }

        ChecklistItem next = nextIncompleteItem(ctx, st, lesson);
        String summary = lessonSummary(lesson);
        if (next == null) return summary;
        return summary + " Next: " + next.label;
    }

    public static String contextHint(GameContext ctx) {
        TutorialState st = state(ctx);
        if (st == null) return "";
        LessonId lesson = currentLesson(st);
        if (lesson == LessonId.COMPLETE) {
            return "Command school complete. Stay in this sandbox or press F10 to return to the menu.";
        }

        ChecklistItem next = nextIncompleteItem(ctx, st, lesson);
        return (next == null) ? lessonSummary(lesson) : next.hint;
    }

    public static void drawWorldMarkers(GameContext ctx, Graphics2D g2) {
        TutorialState st = state(ctx);
        if (st == null || g2 == null) return;

        List<Marker> markers = markers(ctx, st);
        Marker active = activeMarker(ctx, st);
        for (Marker marker : markers) {
            if (marker == null) continue;
            boolean isActive = active != null && marker.label.equals(active.label);
            drawMarker(g2, marker, isActive);
        }
    }

    public static void drawOverlay(GameContext ctx, Graphics2D g2, int viewportW, int viewportH) {
        TutorialState st = state(ctx);
        if (st == null || g2 == null || viewportW <= 0 || viewportH <= 0) return;
        if (st.archiveOpen) {
            drawArchive(ctx, st, g2, viewportW, viewportH);
            return;
        }

        LessonId lesson = currentLesson(st);
        List<ChecklistItem> items = checklist(ctx, st, lesson);
        ChecklistItem next = nextIncompleteItem(items);

        int panelW = Math.min(560, Math.max(280, viewportW - 36));
        int contentW = panelW - 24;

        Graphics2D gx = (Graphics2D) g2.create();
        gx.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Font eyebrowFont = new Font("Consolas", Font.BOLD, 12);
        Font titleFont = new Font("Consolas", Font.BOLD, 21);
        Font bodyFont = new Font("Consolas", Font.PLAIN, 13);
        Font footerFont = new Font("Consolas", Font.PLAIN, 12);

        FontMetrics bodyFm = gx.getFontMetrics(bodyFont);
        List<String> summaryLines = wrapLines(lesson == LessonId.COMPLETE
                        ? "Tutorial complete. Keep practicing or return to the menu."
                        : lessonSummary(lesson),
                bodyFm,
                contentW);

        int headerH = 62;
        int summaryH = Math.max(16, summaryLines.size() * 14);
        List<String> nextLabelLines = next == null ? List.of("Step complete.") : wrapLines(next.label, bodyFm, contentW - 36);
        List<String> nextHintLines = next == null ? List.of() : wrapLines(next.hint, bodyFm, contentW);
        int checklistH = 24 + Math.max(1, nextLabelLines.size()) * 15
                + Math.min(2, nextHintLines.size()) * 14;
        int footerH = 28;
        int panelH = headerH + summaryH + checklistH + footerH + 22;
        Rectangle panelRect = tutorialOverlayPanelRect(viewportW, viewportH, panelW, panelH);
        int x = panelRect.x;
        int y = panelRect.y;

        gx.translate(x, y);
        gx.setColor(new Color(0, 0, 0, 108));
        gx.fillRoundRect(5, 6, panelW, panelH, 24, 24);
        gx.setPaint(new GradientPaint(0, 0, new Color(9, 18, 33, 236), 0, panelH, new Color(7, 11, 23, 226)));
        gx.fillRoundRect(0, 0, panelW, panelH, 24, 24);
        gx.setColor(new Color(114, 176, 242, 150));
        gx.drawRoundRect(0, 0, panelW - 1, panelH - 1, 24, 24);
        gx.setColor(new Color(255, 255, 255, 24));
        gx.drawRoundRect(1, 1, panelW - 3, panelH - 3, 22, 22);

        int progressOuterX = 16;
        int progressOuterY = 14;
        int progressOuterW = panelW - 32;
        int progressOuterH = 8;
        int completedLessons = Math.min(LESSON_COUNT,
                lesson == LessonId.COMPLETE ? LESSON_COUNT : lessonFlowPosition(lesson));
        int filledW = (int) Math.round(progressOuterW * (completedLessons / (double) LESSON_COUNT));
        gx.setColor(new Color(255, 255, 255, 18));
        gx.fillRoundRect(progressOuterX, progressOuterY, progressOuterW, progressOuterH, 8, 8);
        gx.setPaint(new GradientPaint(progressOuterX, progressOuterY, new Color(96, 198, 255, 220),
                progressOuterX + Math.max(1, filledW), progressOuterY, new Color(255, 213, 110, 210)));
        gx.fillRoundRect(progressOuterX, progressOuterY, Math.max(10, filledW), progressOuterH, 8, 8);

        gx.setFont(eyebrowFont);
        gx.setColor(new Color(142, 198, 255, 210));
        gx.drawString("TUTORIAL", 14, 34);

        gx.setFont(titleFont);
        gx.setColor(Color.WHITE);
        gx.drawString(lessonName(lesson), 14, 56);

        gx.setFont(bodyFont);
        gx.setColor(new Color(196, 214, 236, 210));
        String counter = (lesson == LessonId.COMPLETE) ? "Sandbox complete"
                : "Step " + (lessonFlowPosition(lesson) + 1) + " of " + LESSON_COUNT;
        counter = fitLine(bodyFm, counter, Math.max(80, panelW - 210));
        gx.drawString(counter, panelW - 14 - bodyFm.stringWidth(counter), 34);

        int cursorY = 74;
        for (String line : summaryLines) {
            gx.drawString(line, 14, cursorY);
            cursorY += 14;
        }

        cursorY += 8;
        int boxX = 14;
        int boxY = cursorY - 12;
        gx.setColor(new Color(255, 216, 120, 220));
        gx.setStroke(new BasicStroke(1.6f));
        gx.drawRoundRect(boxX, boxY, 16, 16, 6, 6);
        gx.setColor(new Color(255, 216, 120, 42));
        gx.fillRoundRect(boxX, boxY, 16, 16, 6, 6);
        gx.setColor(new Color(255, 240, 190, 235));
        for (String line : nextLabelLines) {
            gx.drawString(line, 38, cursorY);
            cursorY += 15;
        }
        gx.setColor(new Color(218, 230, 246, 220));
        int hintLines = 0;
        for (String line : nextHintLines) {
            gx.drawString(line, 14, cursorY + 2);
            cursorY += 14;
            if (++hintLines >= 2) break;
        }

        gx.setFont(footerFont);
        gx.setColor(new Color(176, 188, 206, 188));
        gx.drawString(fitLine(gx.getFontMetrics(),
                "Ctrl+F1 skip  Ctrl+F2 archive  F10 menu.",
                contentW), 14, panelH - 10);
        gx.dispose();
    }

    private static void drawArchive(GameContext ctx, TutorialState st, Graphics2D g2, int viewportW, int viewportH) {
        int panelW = Math.max(440, Math.min(620, viewportW - 48));
        int panelH = Math.max(360, Math.min(560, viewportH - 72));
        int x = Math.max(24, (viewportW - panelW) / 2);
        int y = Math.max(24, (viewportH - panelH) / 2);
        Graphics2D gx = (Graphics2D) g2.create();
        gx.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gx.setColor(new Color(5, 10, 20, 238));
        gx.fillRoundRect(x, y, panelW, panelH, 24, 24);
        gx.setColor(new Color(114, 176, 242, 170));
        gx.drawRoundRect(x, y, panelW - 1, panelH - 1, 24, 24);
        gx.setFont(new Font("Consolas", Font.BOLD, 20));
        gx.setColor(Color.WHITE);
        gx.drawString("TUTORIAL ARCHIVE", x + 20, y + 34);
        gx.setFont(new Font("Consolas", Font.PLAIN, 12));
        gx.setColor(new Color(196, 214, 236, 220));
        gx.drawString("Ctrl+F2 closes. Completed and skipped lessons stay visible for replay reference.", x + 20, y + panelH - 18);

        Font rowFont = new Font("Consolas", Font.PLAIN, 13);
        FontMetrics fm = gx.getFontMetrics(rowFont);
        gx.setFont(rowFont);
        int cy = y + 66;
        for (LessonId lesson : LESSON_FLOW) {
            if (lesson == LessonId.COMPLETE) continue;
            boolean done = lessonFlowPosition(lesson) < lessonFlowPosition(currentLesson(st))
                    || currentLesson(st) == LessonId.COMPLETE;
            boolean current = lesson == currentLesson(st);
            boolean skipped = st.skippedLessons.contains(lesson);
            String label = (done ? "[x] " : current ? "[>] " : "[ ] ")
                    + lessonName(lesson)
                    + (skipped ? "  (skipped)" : "");
            gx.setColor(current ? new Color(255, 224, 132, 235)
                    : done ? new Color(126, 224, 166, 220)
                    : new Color(205, 218, 236, 205));
            gx.drawString(label, x + 22, cy);
            cy += 17;
            gx.setColor(new Color(160, 178, 204, 180));
            for (String line : wrapLines(lessonSummary(lesson), fm, panelW - 58)) {
                gx.drawString("   " + line, x + 22, cy);
                cy += 15;
                if (cy > y + panelH - 42) break;
            }
            cy += 3;
            if (cy > y + panelH - 42) break;
        }
        gx.dispose();
    }

    static Rectangle tutorialOverlayPanelRect(int viewportW, int viewportH, int panelW, int panelH) {
        int margin = 18;
        int w = Math.max(260, Math.min(panelW, Math.max(260, viewportW - margin * 2)));
        int h = Math.max(120, Math.min(panelH, Math.max(120, viewportH - margin * 2)));
        Rectangle coreMenu = Renderer.getCoreMenuBarRect(viewportW, viewportH);
        Rectangle rightHudReserve = new Rectangle(Math.max(margin, viewportW - 250), margin, 232,
                Math.max(170, Math.min(360, viewportH - margin * 2)));
        Rectangle[] candidates = new Rectangle[]{
                new Rectangle(Math.max(margin, (viewportW - w) / 2), margin, w, h),
                new Rectangle(margin, margin, w, h),
                new Rectangle(Math.max(margin, viewportW - w - margin), margin, w, h),
                new Rectangle(Math.max(margin, (viewportW - w) / 2),
                        Math.max(margin, coreMenu.y - h - margin), w, h)
        };
        for (Rectangle candidate : candidates) {
            if (candidate.x < margin || candidate.y < margin) continue;
            if (candidate.x + candidate.width > viewportW - margin) continue;
            if (candidate.y + candidate.height > viewportH - margin) continue;
            if (candidate.intersects(coreMenu)) continue;
            if (candidate.intersects(rightHudReserve)) continue;
            return candidate;
        }
        int fallbackY = Math.max(margin, Math.min(coreMenu.y - h - margin, viewportH - h - margin));
        return new Rectangle(Math.max(margin, (viewportW - w) / 2), fallbackY, w, h);
    }

    public static void drawMinimapOverlay(GameContext ctx, Graphics2D g2, int viewW, int viewH) {
        TutorialState st = state(ctx);
        if (st == null || g2 == null || ctx == null || ctx.player == null) return;

        int pad = 14;
        int size = 170;
        int x0 = viewW - size - pad;
        int y0 = pad;
        double view = 1500.0;
        double left = ctx.player.x - view / 2.0;
        double top = ctx.player.y - view / 2.0;
        Marker active = activeMarker(ctx, st);

        Graphics2D gx = (Graphics2D) g2.create();
        gx.setFont(new Font("Consolas", Font.BOLD, 9));
        for (Marker marker : markers(ctx, st)) {
            if (marker == null) continue;
            double rx = (marker.x - left) / view;
            double ry = (marker.y - top) / view;
            if (rx < 0 || rx > 1 || ry < 0 || ry > 1) continue;

            int px = x0 + (int) Math.round(rx * size);
            int py = y0 + (int) Math.round(ry * size);
            boolean isActive = active != null && active.label.equals(marker.label);

            gx.setColor(isActive ? new Color(255, 228, 138, 230) : new Color(168, 220, 255, 180));
            int r = isActive ? 6 : 4;
            gx.drawOval(px - r, py - r, r * 2, r * 2);
            gx.drawLine(px - r - 2, py, px + r + 2, py);
            gx.drawLine(px, py - r - 2, px, py + r + 2);

            if (isActive) {
                String shortLabel = minimapShortLabel(marker.label);
                gx.setColor(new Color(250, 246, 224, 225));
                gx.drawString(shortLabel, px + 7, py - 5);
            }
        }
        gx.dispose();
    }

    public static void drawStrategicMapOverlay(GameContext ctx, Graphics2D g2, int viewW, int viewH) {
        TutorialState st = state(ctx);
        if (st == null || g2 == null || ctx == null) return;

        java.awt.Rectangle m = Renderer.getStrategicMapInnerRect(
                viewW, viewH, CampaignSystem.isStrategicGalaxyMapMode(ctx));
        Marker active = activeMarker(ctx, st);
        Graphics2D gx = (Graphics2D) g2.create();
        gx.setFont(new Font("Consolas", Font.BOLD, 11));
        gx.setClip(m.x, m.y, m.width, m.height);

        for (Marker marker : markers(ctx, st)) {
            if (marker == null) continue;
            Point point = strategicMapPointForWorld(ctx, marker.x, marker.y, m);
            if (point == null) continue;
            int px = point.x;
            int py = point.y;
            boolean isActive = active != null && active.label.equals(marker.label);
            int rr = isActive ? 10 : 7;

            gx.setColor(isActive ? new Color(255, 224, 132, 220) : new Color(148, 208, 255, 170));
            gx.drawOval(px - rr, py - rr, rr * 2, rr * 2);
            gx.drawLine(px - rr - 4, py, px + rr + 4, py);
            gx.drawLine(px, py - rr - 4, px, py + rr + 4);

            gx.setColor(isActive ? new Color(255, 246, 218, 235) : new Color(214, 230, 248, 210));
            gx.drawString(marker.label, px + rr + 6, py - 4);
        }
        gx.dispose();
    }

    static Point strategicMapPointForWorld(GameContext ctx, double worldX, double worldY, Rectangle mapRect) {
        if (ctx == null || mapRect == null || mapRect.width <= 0 || mapRect.height <= 0) return null;
        double worldMinX = UISystem.strategicMapWorldMinX(ctx);
        double worldMinY = UISystem.strategicMapWorldMinY(ctx);
        double visibleWorldW = UISystem.strategicMapViewWidth(ctx);
        double visibleWorldH = UISystem.strategicMapViewHeight(ctx);
        double nx = (worldX - worldMinX) / Math.max(1.0, visibleWorldW);
        double ny = (worldY - worldMinY) / Math.max(1.0, visibleWorldH);
        if (nx < 0.0 || nx > 1.0 || ny < 0.0 || ny > 1.0) return null;
        return new Point(
                mapRect.x + (int) Math.round(nx * mapRect.width),
                mapRect.y + (int) Math.round(ny * mapRect.height));
    }

    public static boolean handleStrategicMapClick(GameContext ctx, double worldX, double worldY, boolean rightMouse) {
        TutorialState st = state(ctx);
        if (ctx == null || st == null || CampaignSystem.isStrategicOvermapMode(ctx)) return false;
        Marker active = activeMarker(ctx, st);
        if (active == null) return false;

        double pickRadius = Math.max(95.0, Math.min(150.0, active.radius + 28.0));
        if (!nearPoint(worldX, worldY, active.x, active.y, pickRadius)) {
            if (CampaignSystem.usesMissionSubzones(ctx)) {
                EventSystem.showBanner(ctx, "CLICK THE " + active.label + " MARKER", 1.0);
                return true;
            }
            return false;
        }

        if (rightMouse) {
            UISystem.addPing(ctx, active.x, active.y, 2.2);
            EventSystem.showBanner(ctx, active.label + " PING", 1.0);
            return true;
        }

        ctx.ui.waypointX = GameMath.clamp(active.x, 0.0, ctx.WORLD_W);
        ctx.ui.waypointY = GameMath.clamp(active.y, 0.0, ctx.WORLD_H);
        UISystem.addPing(ctx, ctx.ui.waypointX, ctx.ui.waypointY, 2.2);
        EventSystem.showBanner(ctx, "COURSE SET: " + active.label, 1.0);
        return true;
    }

    private static void refreshPersistentProgress(GameContext ctx, TutorialState st) {
        CampaignSystem.CampaignState campaign = (ctx == null) ? null : ctx.campaign;
        if (campaign != null && campaign.commandSchoolTraining) {
            st.overworldMapReviewed |= ctx.ui != null && ctx.ui.mapOpen && st.lessonElapsedSec >= 0.75;
            st.selectedTrainingSite |= commandSchoolHubSelected(campaign);
            st.plottedTrainingCourse |= commandSchoolHubCoursePlotted(campaign);
            st.engagedTrainingCourse |= campaign.galaxyTravel.traveling
                    || "ENGAGE_COURSE".equals(campaign.commandSchoolLastActionId);
            st.reachedTrainingHub |= commandSchoolAtTrainingHub(campaign);
            st.selectedTrainingContact |= commandSchoolTrainingContactSelected(ctx);
            st.trackedTrainingContact |= isTrainingReconAction(campaign.commandSchoolLastActionId);
            st.reachedResourceSite |= CampaignSystem.COMMAND_SCHOOL_RESOURCE_SITE_ID.equals(campaign.currentGalaxyLocationId)
                    || CampaignSystem.COMMAND_SCHOOL_RESOURCE_SITE_ID.equals(campaign.dockedGalaxyLocationId);
            st.reviewedStationServices |= CampaignSystem.COMMAND_SCHOOL_TRADE_HUB_ID.equals(campaign.selectedGalaxyLocationId)
                    && (st.lessonElapsedSec >= 1.0 || ctx.ui != null && ctx.ui.campaignHubMenu.active);
            st.changedFleetCommitment |= campaign.commandSchoolLastActionId != null
                    && campaign.commandSchoolLastActionId.startsWith("FLEET_COMMIT_");
            st.enteredTrainingMission |= currentLesson(st) == LessonId.OVERWORLD_TO_MISSION
                    && campaign.galaxyEncounterActive
                    && campaign.galaxyAmbientEncounterActive
                    && CampaignSystem.COMMAND_SCHOOL_RED_SITE_ID.equals(campaign.activeGalaxyEncounterLocationId)
                    && !campaign.strategicOvermapMode;
            st.withdrewToOverworld |= !campaign.galaxyEncounterActive
                    && campaign.strategicOvermapMode
                    && currentLesson(st) == LessonId.CARRIER_AND_WARP
                    && st.lessonElapsedSec >= 0.5;
        }

        st.betaWaypointSet |= nearPoint(ctx.ui.waypointX, ctx.ui.waypointY, st.betaX, st.betaY, 150.0);
        st.reachedAlpha |= near(ctx.player, st.alphaX, st.alphaY, POINT_REACHED_RADIUS);
        st.reachedBeta |= near(ctx.player, st.betaX, st.betaY, POINT_REACHED_RADIUS);
        st.pingedWeaponsRange |= hasPingNear(ctx, st.weaponsX, st.weaponsY, PING_MATCH_RADIUS);
        st.xrayFilterUsed |= ctx.ui.xrayFilterMode != GameContext.XrayFilterMode.ALL;
        st.xrayRoomFocused |= ctx.ui.xrayFocusedRoom != null;
        st.openedShop |= ctx.ui.shopOpen;
        st.openedFlightDeck |= ctx.ui.flightDeckOpen;
        st.minedOre |= playerHasMinedOre(ctx, st);
        st.hangarTierThree |= currentHangarLevel(ctx, st.homeBaseId) >= 3;

        Ship target = shipById(ctx, st.combatTargetId);
        st.tutorialDroneDamaged |= target == null || target.hp < target.hpMax || !target.alive || target.dying;

        Ship docked = EconomySystem.getDockedFriendlyBase(ctx);
        st.dockedAtHome |= docked != null && docked.id == st.homeBaseId;
        st.swappedToCarrier |= ctx.player != null && ctx.player.isCarrier;
        st.fireSuppressed |= st.seededDamageControlFire && ctx.player != null && ctx.player.totalFireIntensity() <= 0.05;
        if (ctx.player != null && ctx.player.isCarrier) {
            if (!st.flightDeckBaselineCaptured && currentLesson(st) == LessonId.CARRIER_AND_WARP) {
                captureCarrierBaseline(ctx, st);
            }
            int activeWing = CarrierSystem.countActiveWingByCarrier(ctx, ctx.player);
            if (activeWing > 0) st.launchedWing = true;
            if (st.flightDeckBaselineCaptured) {
                st.carrierModeChanged |= ctx.player.carrierCommandMode != st.baselineCarrierMode;
                st.carrierAutoLaunchChanged |= ctx.player.carrierAutoLaunch != st.baselineCarrierAutoLaunch;
            }
        }

        if (st.powerCrewBaselineCaptured) {
            st.powerAdjusted |= powerAdjustedSinceBaseline(ctx, st);
            st.crewAdjusted |= crewAdjustedSinceBaseline(ctx, st);
        }
    }

    private static boolean isTrainingReconAction(String actionId) {
        if (actionId == null || actionId.isBlank()) return false;
        return switch (actionId.trim().toUpperCase(java.util.Locale.US)) {
            case "TRACK_TARGET", "SIGNAL_SWEEP", "FOCUSED_TRACK", "TRAFFIC_AUDIT" -> true;
            default -> false;
        };
    }

    private static boolean commandSchoolHubSelected(CampaignSystem.CampaignState campaign) {
        return campaign != null
                && CampaignSystem.COMMAND_SCHOOL_TRADE_HUB_ID.equals(campaign.selectedGalaxyLocationId);
    }

    private static boolean commandSchoolTravelingToHub(CampaignSystem.CampaignState campaign) {
        return campaign != null
                && campaign.galaxyTravel != null
                && campaign.galaxyTravel.traveling
                && CampaignSystem.COMMAND_SCHOOL_TRADE_HUB_ID.equals(campaign.galaxyTravel.destinationId);
    }

    private static boolean commandSchoolAtTrainingHub(CampaignSystem.CampaignState campaign) {
        return campaign != null
                && (CampaignSystem.COMMAND_SCHOOL_TRADE_HUB_ID.equals(campaign.currentGalaxyLocationId)
                || CampaignSystem.COMMAND_SCHOOL_TRADE_HUB_ID.equals(campaign.dockedGalaxyLocationId));
    }

    private static boolean commandSchoolHubCoursePlotted(CampaignSystem.CampaignState campaign) {
        if (campaign == null) return false;
        String action = campaign.commandSchoolLastActionId;
        if ("PLOT_COURSE".equals(action) && commandSchoolHubSelected(campaign)) return true;
        if (commandSchoolTravelingToHub(campaign)) return true;
        return "ENGAGE_COURSE".equals(action)
                && (commandSchoolHubSelected(campaign) || commandSchoolAtTrainingHub(campaign));
    }

    private static boolean commandSchoolTrainingContactSelected(GameContext ctx) {
        CampaignSystem.CampaignState campaign = (ctx == null) ? null : ctx.campaign;
        if (ctx == null || ctx.ui == null || campaign == null || !CampaignSystem.selectedCampaignContactHostile(ctx)) {
            return false;
        }
        String label = CampaignSystem.selectedCampaignContactLabel(ctx).toUpperCase(java.util.Locale.US);
        if (label.contains("DRONE")) return true;
        CampaignSystem.CampaignLocation red = trainingLocation(ctx, CampaignSystem.COMMAND_SCHOOL_RED_SITE_ID);
        if (red == null
                || !Double.isFinite(ctx.ui.selectedCampaignContactX)
                || !Double.isFinite(ctx.ui.selectedCampaignContactY)) {
            return false;
        }
        return GameMath.dist2(ctx.ui.selectedCampaignContactX, ctx.ui.selectedCampaignContactY, red.x, red.y)
                <= 260.0 * 260.0;
    }

    private static void handleLessonSideEffects(GameContext ctx, TutorialState st) {
        LessonId lesson = currentLesson(st);
        if (lesson == LessonId.OVERWORLD_MAP_READING) {
            ensureCommandSchoolOverworld(ctx);
            if (ctx.ui != null) ctx.ui.mapOpen = true;
        } else if (lesson == LessonId.SITE_SELECTION) {
            ensureCommandSchoolOverworld(ctx);
        } else if (lesson == LessonId.PLOT_MOVEMENT) {
            ensureCommandSchoolOverworld(ctx);
            if (!CampaignSystem.COMMAND_SCHOOL_TRADE_HUB_ID.equals(ctx.campaign.selectedGalaxyLocationId)) {
                selectTrainingLocation(ctx, CampaignSystem.COMMAND_SCHOOL_TRADE_HUB_ID);
            }
        } else if (lesson == LessonId.SCAN_AND_INTEL) {
            ensureCommandSchoolOverworld(ctx);
            if (ctx.ui != null) ctx.ui.campaignCommandTab = UiState.CampaignCommandTab.NAV;
            if (!CampaignSystem.hasSelectedCampaignContactTarget(ctx)) {
                selectTrainingContact(ctx);
            }
        } else if (lesson == LessonId.RESOURCE_SITE) {
            ensureCommandSchoolOverworld(ctx);
            if (!CampaignSystem.COMMAND_SCHOOL_RESOURCE_SITE_ID.equals(ctx.campaign.selectedGalaxyLocationId)
                    && !CampaignSystem.COMMAND_SCHOOL_RESOURCE_SITE_ID.equals(ctx.campaign.currentGalaxyLocationId)) {
                selectTrainingLocation(ctx, CampaignSystem.COMMAND_SCHOOL_RESOURCE_SITE_ID);
            }
        } else if (lesson == LessonId.STATION_SERVICES) {
            ensureCommandSchoolOverworld(ctx);
            if (!CampaignSystem.COMMAND_SCHOOL_TRADE_HUB_ID.equals(ctx.campaign.selectedGalaxyLocationId)) {
                selectTrainingLocation(ctx, CampaignSystem.COMMAND_SCHOOL_TRADE_HUB_ID);
            }
        } else if (lesson == LessonId.FLEET_ORGANIZATION) {
            ensureCommandSchoolOverworld(ctx);
            if (ctx.ui != null) ctx.ui.campaignCommandTab = UiState.CampaignCommandTab.FLEET;
        } else if (lesson == LessonId.OVERWORLD_TO_MISSION) {
            ensureCommandSchoolOverworld(ctx);
            if (ctx.ui != null) ctx.ui.campaignCommandTab = UiState.CampaignCommandTab.NAV;
            if (!CampaignSystem.COMMAND_SCHOOL_RED_SITE_ID.equals(ctx.campaign.selectedGalaxyLocationId)) {
                selectTrainingLocation(ctx, CampaignSystem.COMMAND_SCHOOL_RED_SITE_ID);
            }
        } else if (lesson == LessonId.BRIDGE_SYSTEMS) {
            focusHomeBase(ctx, st);
            if (ctx.player != null && ctx.player.isCarrier && !st.powerCrewBaselineCaptured) {
                capturePowerAndCrewBaseline(ctx, st);
            }
            if (ctx.player != null
                    && ctx.player.isCarrier
                    && st.powerAdjusted
                    && st.crewAdjusted
                    && !st.seededDamageControlFire) {
                seedDamageControlFire(ctx, st);
            }
        } else if (lesson == LessonId.CARRIER_AND_WARP) {
            if (ctx.player != null && ctx.player.isCarrier && !st.flightDeckBaselineCaptured) {
                captureCarrierBaseline(ctx, st);
            }
        } else if (lesson == LessonId.COMPLETE) {
            ctx.ui.waypointX = Double.NaN;
            ctx.ui.waypointY = Double.NaN;
        }
    }

    private static TutorialState state(GameContext ctx) {
        if (ctx == null || ctx.config == null || ctx.config.mode != GameMode.TUTORIAL) return null;
        return STATES.get(ctx);
    }

    private static LessonId currentLesson(TutorialState st) {
        LessonId[] lessons = LessonId.values();
        int idx = Math.max(0, Math.min(lessons.length - 1, st.lessonIndex));
        return lessons[idx];
    }

    private static int lessonFlowPosition(LessonId lesson) {
        if (lesson == null) return 0;
        for (int i = 0; i < LESSON_FLOW.length; i++) {
            if (LESSON_FLOW[i] == lesson) return i;
        }
        return LESSON_FLOW.length - 1;
    }

    private static boolean isOverworldLesson(LessonId lesson) {
        if (lesson == null) return false;
        return switch (lesson) {
            case OVERWORLD_MAP_READING, SITE_SELECTION, PLOT_MOVEMENT, SCAN_AND_INTEL,
                    RESOURCE_SITE, STATION_SERVICES, FLEET_ORGANIZATION, OVERWORLD_TO_MISSION -> true;
            case FLIGHT_BASICS, TARGETING_AND_SENSORS, LOGISTICS_AND_REFIT, BRIDGE_SYSTEMS,
                    CARRIER_AND_WARP, COMPLETE -> false;
        };
    }

    private static LessonId nextLesson(LessonId lesson) {
        int pos = lessonFlowPosition(lesson);
        if (pos >= LESSON_FLOW.length - 1) return LessonId.COMPLETE;
        return LESSON_FLOW[pos + 1];
    }

    private static boolean lessonComplete(GameContext ctx, TutorialState st, LessonId lesson) {
        List<ChecklistItem> items = checklist(ctx, st, lesson);
        if (items.isEmpty()) return lesson == LessonId.COMPLETE;
        for (ChecklistItem item : items) {
            if (!item.complete) return false;
        }
        return true;
    }

    private static void enterLesson(GameContext ctx, TutorialState st, LessonId lesson, boolean announce) {
        if (ctx == null || st == null || lesson == null) return;
        st.lessonIndex = lesson.ordinal();
        st.lessonElapsedSec = 0.0;
        st.pingTimer = 0.0;
        AcademyProgressStore.markStepStarted(st.academySessionId, academyChapterLabel(lesson), academyStepLabel(lesson));
        AcademyProgressStore.recordEvent(st.academySessionId,
                "academy_chapter_started",
                academyChapterLabel(lesson),
                academyStepLabel(lesson),
                0.0,
                "started");

        switch (lesson) {
            case OVERWORLD_MAP_READING -> {
                ensureCommandSchoolOverworld(ctx);
                if (ctx.ui != null) ctx.ui.mapOpen = true;
                if (announce) EventSystem.showBanner(ctx, "OVERWORLD SCHOOL 1: READ THE MAP", 2.4);
            }
            case SITE_SELECTION -> {
                ensureCommandSchoolOverworld(ctx);
                if (ctx.ui != null) ctx.ui.mapOpen = true;
                if (announce) EventSystem.showBanner(ctx, "OVERWORLD SCHOOL 2: SELECT A SITE", 2.4);
            }
            case PLOT_MOVEMENT -> {
                ensureCommandSchoolOverworld(ctx);
                selectTrainingLocation(ctx, CampaignSystem.COMMAND_SCHOOL_TRADE_HUB_ID);
                if (announce) EventSystem.showBanner(ctx, "OVERWORLD SCHOOL 3: PLOT + ENGAGE COURSE", 2.4);
            }
            case SCAN_AND_INTEL -> {
                ensureCommandSchoolOverworld(ctx);
                if (ctx.ui != null) ctx.ui.campaignCommandTab = UiState.CampaignCommandTab.NAV;
                selectTrainingContact(ctx);
                if (announce) EventSystem.showBanner(ctx, "OVERWORLD SCHOOL 4: SCAN + INTEL", 2.4);
            }
            case RESOURCE_SITE -> {
                ensureCommandSchoolOverworld(ctx);
                selectTrainingLocation(ctx, CampaignSystem.COMMAND_SCHOOL_RESOURCE_SITE_ID);
                if (announce) EventSystem.showBanner(ctx, "OVERWORLD SCHOOL 5: RESOURCE SITE", 2.4);
            }
            case STATION_SERVICES -> {
                ensureCommandSchoolOverworld(ctx);
                selectTrainingLocation(ctx, CampaignSystem.COMMAND_SCHOOL_TRADE_HUB_ID);
                if (announce) EventSystem.showBanner(ctx, "OVERWORLD SCHOOL 6: STATION SERVICES", 2.4);
            }
            case FLEET_ORGANIZATION -> {
                ensureCommandSchoolOverworld(ctx);
                if (ctx.ui != null) ctx.ui.campaignCommandTab = UiState.CampaignCommandTab.FLEET;
                if (announce) EventSystem.showBanner(ctx, "OVERWORLD SCHOOL 7: FLEET ORGANIZATION", 2.4);
            }
            case OVERWORLD_TO_MISSION -> {
                ensureCommandSchoolOverworld(ctx);
                if (ctx.ui != null) ctx.ui.campaignCommandTab = UiState.CampaignCommandTab.NAV;
                selectTrainingLocation(ctx, CampaignSystem.COMMAND_SCHOOL_RED_SITE_ID);
                st.tacticalSandboxPrepared = false;
                if (announce) EventSystem.showBanner(ctx, "OVERWORLD SCHOOL 8: ENTER MISSION", 2.4);
            }
            case FLIGHT_BASICS -> {
                prepareTacticalCommandSchoolSandbox(ctx, st);
                ctx.ui.waypointX = st.alphaX;
                ctx.ui.waypointY = st.alphaY;
                if (announce) EventSystem.showBanner(ctx, "TACTICAL SCHOOL 1: FLIGHT BASICS", 2.4);
            }
            case TARGETING_AND_SENSORS -> {
                prepareTacticalCommandSchoolSandbox(ctx, st);
                ctx.ui.waypointX = st.weaponsX;
                ctx.ui.waypointY = st.weaponsY;
                if (announce) EventSystem.showBanner(ctx, "TACTICAL SCHOOL 2: TARGETING + XRAY", 2.4);
            }
            case LOGISTICS_AND_REFIT -> {
                prepareTacticalCommandSchoolSandbox(ctx, st);
                ctx.ui.waypointX = st.miningX;
                ctx.ui.waypointY = st.miningY;
                if (announce) EventSystem.showBanner(ctx, "TACTICAL SCHOOL 3: LOGISTICS LOOP", 2.4);
            }
            case BRIDGE_SYSTEMS -> {
                prepareTacticalCommandSchoolSandbox(ctx, st);
                ctx.ui.waypointX = Double.NaN;
                ctx.ui.waypointY = Double.NaN;
                st.powerCrewBaselineCaptured = false;
                st.powerAdjusted = false;
                st.crewAdjusted = false;
                st.seededDamageControlFire = false;
                st.fireSuppressed = false;
                focusHomeBase(ctx, st);
                if (announce) EventSystem.showBanner(ctx, "TACTICAL SCHOOL 4: BRIDGE SYSTEMS", 2.4);
            }
            case CARRIER_AND_WARP -> {
                prepareTacticalCommandSchoolSandbox(ctx, st);
                ctx.ui.waypointX = Double.NaN;
                ctx.ui.waypointY = Double.NaN;
                st.flightDeckBaselineCaptured = false;
                st.openedFlightDeck = false;
                st.launchedWing = false;
                st.carrierModeChanged = false;
                st.carrierAutoLaunchChanged = false;
                st.withdrewToOverworld = false;
                if (ctx.player != null && ctx.player.isCarrier) {
                    captureCarrierBaseline(ctx, st);
                }
                if (announce) EventSystem.showBanner(ctx, "TACTICAL SCHOOL 5: CARRIER + WITHDRAW", 2.4);
            }
            case COMPLETE -> {
                ctx.ui.waypointX = Double.NaN;
                ctx.ui.waypointY = Double.NaN;
                AcademyProgressStore.markCompleted(st.academySessionId, graduationSnapshot(ctx, st));
                AcademyProgressStore.recordEvent(st.academySessionId,
                        "academy_completed",
                        academyChapterLabel(lesson),
                        academyStepLabel(lesson),
                        0.0,
                        "complete");
                EventSystem.showBanner(ctx, "TUTORIAL COMPLETE", 3.0);
            }
        }
    }

    private static String academyChapterLabel(LessonId lesson) {
        if (lesson == null) return "Academy";
        if (lesson == LessonId.COMPLETE) return "Graduation";
        return isOverworldLesson(lesson) ? "Overworld School" : "Tactical School";
    }

    private static String academyStepLabel(LessonId lesson) {
        return lesson == null ? "unknown" : lesson.name().toLowerCase().replace('_', '-');
    }

    private static String graduationSnapshot(GameContext ctx, TutorialState st) {
        int live = 0;
        int damaged = 0;
        if (ctx != null && ctx.ships != null && st != null) {
            for (Ship ship : ctx.ships) {
                if (ship == null || ship.faction == null || !ship.faction.isFriendlyTo(st.playerFaction)) continue;
                if (!ship.alive || ship.dying || ship.hp <= 0) continue;
                live++;
                double hpFrac = ship.hpMax <= 0 ? 0.0 : ship.hp / (double) ship.hpMax;
                double shieldFrac = ship.shieldMax <= 1e-6 ? 1.0 : ship.shield / ship.shieldMax;
                if (hpFrac < 0.80 || shieldFrac < 0.65) damaged++;
            }
        }
        return "version=" + AcademyProgressStore.ACADEMY_VERSION
                + ";liveFriendly=" + live
                + ";damagedFriendly=" + damaged;
    }

    private static String lessonName(LessonId lesson) {
        return switch (lesson) {
            case OVERWORLD_MAP_READING -> "Overworld Map Reading";
            case SITE_SELECTION -> "Site Selection";
            case PLOT_MOVEMENT -> "Plot And Move";
            case SCAN_AND_INTEL -> "Scan And Intel";
            case RESOURCE_SITE -> "Resource Site";
            case STATION_SERVICES -> "Station Services";
            case FLEET_ORGANIZATION -> "Fleet Organization";
            case OVERWORLD_TO_MISSION -> "Enter A Mission";
            case FLIGHT_BASICS -> "Flight Basics";
            case TARGETING_AND_SENSORS -> "Targeting And Sensors";
            case LOGISTICS_AND_REFIT -> "Logistics And Refit";
            case BRIDGE_SYSTEMS -> "Bridge Systems";
            case CARRIER_AND_WARP -> "Carrier And Withdraw";
            case COMPLETE -> "Tutorial Clear";
        };
    }

    private static String lessonSummary(LessonId lesson) {
        return switch (lesson) {
            case OVERWORLD_MAP_READING ->
                    "Read the route map: colors show owners, labels show sites, and the sidebar shows what is selected.";
            case SITE_SELECTION ->
                    "Select a major site and learn what the sidebar tells you: owner, services, threat, and whether the site can be entered.";
            case PLOT_MOVEMENT ->
                    "Practice plotting and engaging a course. The sample route is safe and uses the same travel feedback as Open World Campaign.";
            case SCAN_AND_INTEL ->
                    "Practice hostile-contact selection and tracking. Intel quality tells you what is known, uncertain, or worth avoiding.";
            case RESOURCE_SITE ->
                    "Visit a resource site and note the difference: ore fields are local opportunities, not major station backdrops.";
            case STATION_SERVICES ->
                    "Use a safe station to review trade, contracts, refit, and shipyard/service options without spending a real campaign save.";
            case FLEET_ORGANIZATION ->
                    "Review the persistent fleet roster and change one tactical commitment so you know what deploys into a mission.";
            case OVERWORLD_TO_MISSION ->
                    "Enter a controlled training site. This shows how route-map choices load a local zone.";
            case FLIGHT_BASICS ->
                    "Start in the safe zone. Learn movement first, then use the map waypoint.";
            case TARGETING_AND_SENSORS ->
                    "Practice tactical pings, target locking, live-fire damage confirmation, and the x-ray room inspection tools.";
            case LOGISTICS_AND_REFIT ->
                    "Run the game's economic loop: mine ore, dock at base, and spend that income on a hangar upgrade.";
            case BRIDGE_SYSTEMS ->
                    "Refit into a carrier, touch power management, touch crew command, then clear an engineering emergency.";
            case CARRIER_AND_WARP ->
                    "Try the carrier deck, then use Withdraw to leave the safe zone and open the route map.";
            case COMPLETE ->
                    "Every tutorial lesson is complete. You can keep practicing here or start Open World Campaign with the same controls.";
        };
    }

    private static List<ChecklistItem> checklist(GameContext ctx, TutorialState st, LessonId lesson) {
        ArrayList<ChecklistItem> items = new ArrayList<>();
        switch (lesson) {
            case OVERWORLD_MAP_READING -> {
                items.add(new ChecklistItem(
                        "[M] Review the sample overworld map.",
                        "Keep the map open and read Green, Bright Yellow [BYC/sunburst], Dark Orange-Yellow [DYC/split chevron], Red, ore, and contact markers.",
                        st.overworldMapReviewed));
            }
            case SITE_SELECTION -> {
                items.add(new ChecklistItem(
                        "[LMB] Select Broker Practice Hub.",
                        "Select the Bright Yellow [BYC/sunburst] practice hub and read owner, services, threat, and enter-site status.",
                        st.selectedTrainingSite));
            }
            case PLOT_MOVEMENT -> {
                items.add(new ChecklistItem(
                        "[Plot] Mark a course to the hub.",
                        "Use Plot Course on the selected hub so the map confirms your intended route.",
                        st.plottedTrainingCourse));
                items.add(new ChecklistItem(
                        "[Engage] Begin travel.",
                        "Use Engage Course and watch the route ETA/risk feedback.",
                        st.engagedTrainingCourse));
                items.add(new ChecklistItem(
                        "[Arrive] Reach Broker Practice Hub.",
                        "Let the safe training route finish so arrival and docking range become clear.",
                        st.reachedTrainingHub));
            }
            case SCAN_AND_INTEL -> {
                items.add(new ChecklistItem(
                        "[Contact] Select the Red drone patrol.",
                        "Select or use the highlighted Red training contact; incomplete intel should say what is uncertain.",
                        st.selectedTrainingContact));
                items.add(new ChecklistItem(
                        "[Track] Track the hostile contact.",
                        "Use Track Contact, Signal Sweep, Focused Track, or Traffic Audit to refresh the hostile picture before committing.",
                        st.trackedTrainingContact));
            }
            case RESOURCE_SITE -> {
                items.add(new ChecklistItem(
                        "[Travel] Visit Ore Practice Field.",
                        "Select the ore field and travel there; it is a resource site, not a major station.",
                        st.reachedResourceSite));
            }
            case STATION_SERVICES -> {
                items.add(new ChecklistItem(
                        "[Services] Review Broker Practice Hub.",
                        "Return/select the hub and open or review trade, contracts, refit, or shipyard services.",
                        st.reviewedStationServices));
            }
            case FLEET_ORGANIZATION -> {
                items.add(new ChecklistItem(
                        "[Fleet] Change one hull commitment.",
                        "Open the fleet command tab and set a training hull to Commit, Reserve, Hold, or Auto.",
                        st.changedFleetCommitment));
            }
            case OVERWORLD_TO_MISSION -> {
                items.add(new ChecklistItem(
                        "[Enter Site] Load Red Drone Contact.",
                        "Move/select the Red training site and use Enter Site to start the in-mission school.",
                        st.enteredTrainingMission));
            }
            case FLIGHT_BASICS -> {
                items.add(new ChecklistItem(
                        "[" + HotkeyRegistry.movementLabel() + "] Reach NAV ALPHA.",
                        "Use thrust and steering to fly into the NAV ALPHA ring.",
                        st.reachedAlpha));
                items.add(new ChecklistItem(
                        "[M + click] Set NAV BETA on the strategic map.",
                        "Open the map with M and click NAV BETA to place a waypoint there.",
                        st.betaWaypointSet));
                items.add(new ChecklistItem(
                        "[Waypoint] Fly to NAV BETA.",
                        "Follow the waypoint ring or on-screen nav cue until you reach NAV BETA.",
                        st.reachedBeta));
            }
            case TARGETING_AND_SENSORS -> {
                items.add(new ChecklistItem(
                        "[P] Ping the weapons range for your crew.",
                        "Move the cursor over WEAPONS RANGE and press P to drop a tactical ping.",
                        st.pingedWeaponsRange));
                items.add(new ChecklistItem(
                        "[L / LMB] Lock and damage the practice drone.",
                        "Lock the stationary drone with L, then hold LMB or SPACE to fire guns and missiles.",
                        st.tutorialDroneDamaged));
                items.add(new ChecklistItem(
                        "[` + click] Cycle x-ray and focus a room.",
                        "Press backquote to leave ALL mode, then click a room on your ship to inspect it.",
                        st.xrayFilterUsed && st.xrayRoomFocused));
            }
            case LOGISTICS_AND_REFIT -> {
                items.add(new ChecklistItem(
                        "[F] Mine ore from the rich asteroid pocket.",
                        "Move into the mining cluster and hold F near an asteroid until you collect ore.",
                        st.minedOre));
                items.add(new ChecklistItem(
                        "[Dock] Return to Tutorial Base.",
                        "Dock with Tutorial Base to convert your mining run into a base-side upgrade step.",
                        st.dockedAtHome));
                items.add(new ChecklistItem(
                        "[B then 5] Upgrade the hangar to tier 3.",
                        "Open the base menu with B and buy the hangar upgrade with key 5.",
                        st.hangarTierThree));
            }
            case BRIDGE_SYSTEMS -> {
                items.add(new ChecklistItem(
                        "[TAB] Open loadout, then Capital > Carrier.",
                        "Open the loadout panel at base, choose the Capital hull band, and swap into the Carrier.",
                        st.swappedToCarrier));
                items.add(new ChecklistItem(
                        "[O or Y] Change one power state or preset.",
                        "Open power management with O or cycle presets with Y to prove you can manage ship power.",
                        st.powerAdjusted));
                items.add(new ChecklistItem(
                        "[H] Change one crew directive or station mode.",
                        "Open crew stations with H and change any crew order, mode, or automation setting.",
                        st.crewAdjusted));
                items.add(new ChecklistItem(
                        "[H then Engineering 8] Suppress the scripted fire.",
                        "Open crew stations, switch to Engineering, and use key 8 until the fire is gone.",
                        st.fireSuppressed));
            }
            case CARRIER_AND_WARP -> {
                items.add(new ChecklistItem(
                        "[/] Open the flight deck once.",
                        "Open the flight deck overlay so you can review the carrier wing loadout.",
                        st.openedFlightDeck));
                items.add(new ChecklistItem(
                        "[C] Launch a wing.",
                        "Send at least one strike wing into space with C.",
                        st.launchedWing));
                items.add(new ChecklistItem(
                        "[V or Z] Change carrier behavior.",
                        "Toggle wing mode with V or auto-launch with Z to change carrier behavior.",
                        st.carrierModeChanged || st.carrierAutoLaunchChanged));
                items.add(new ChecklistItem(
                        "[Withdraw] Leave the training zone.",
                        "Click Withdraw when you are ready. After the short spool, the tutorial opens the route map.",
                        st.withdrewToOverworld));
            }
            case COMPLETE -> {
                items.add(new ChecklistItem(
                        "Training complete. Continue practicing in the sandbox or return to menu.",
                        "Everything is unlocked in this safe sector now.",
                        true));
            }
        }
        return items;
    }

    private static ChecklistItem nextIncompleteItem(GameContext ctx, TutorialState st, LessonId lesson) {
        return nextIncompleteItem(checklist(ctx, st, lesson));
    }

    private static ChecklistItem nextIncompleteItem(List<ChecklistItem> items) {
        for (ChecklistItem item : items) {
            if (!item.complete) return item;
        }
        return null;
    }

    private static void ensureCommandSchoolOverworld(GameContext ctx) {
        if (ctx == null || ctx.config == null || ctx.config.mode != GameMode.TUTORIAL) return;
        if (ctx.campaign == null || !ctx.campaign.commandSchoolTraining) {
            CampaignSystem.initCommandSchoolOverworld(ctx, Faction.ALLY);
        }
        if (ctx.campaign != null && ctx.campaign.commandSchoolTraining && !ctx.campaign.galaxyEncounterActive) {
            ctx.campaign.strategicOvermapMode = true;
            if (ctx.ui != null) ctx.ui.mapOpen = true;
            if (ctx.state != GameState.MAP) ctx.state = GameState.MAP;
        }
    }

    private static CampaignSystem.CampaignLocation trainingLocation(GameContext ctx, String id) {
        if (ctx == null || id == null || id.isBlank()) return null;
        for (CampaignSystem.CampaignLocation location : CampaignSystem.mainCampaignLocations(ctx)) {
            if (location != null && id.equals(location.id)) return location;
        }
        for (CampaignSystem.CampaignLocation location : CampaignSystem.campaignAreasOfInterest(ctx)) {
            if (location != null && id.equals(location.id)) return location;
        }
        return null;
    }

    private static void selectTrainingLocation(GameContext ctx, String id) {
        ensureCommandSchoolOverworld(ctx);
        CampaignSystem.CampaignLocation location = trainingLocation(ctx, id);
        if (ctx == null || ctx.campaign == null || location == null) return;
        ctx.campaign.selectedGalaxyLocationId = location.id;
        ctx.campaign.selectedFreeGalaxyTargetX = Double.NaN;
        ctx.campaign.selectedFreeGalaxyTargetY = Double.NaN;
        CampaignSystem.clearSelectedCampaignContact(ctx);
        if (ctx.ui != null) {
            ctx.ui.mapOpen = true;
            ctx.ui.strategicMapFocusX = location.x;
            ctx.ui.strategicMapFocusY = location.y;
        }
        UISystem.addPing(ctx, location.x, location.y, 1.6);
    }

    private static void selectTrainingContact(GameContext ctx) {
        ensureCommandSchoolOverworld(ctx);
        CampaignSystem.CampaignLocation red = trainingLocation(ctx, CampaignSystem.COMMAND_SCHOOL_RED_SITE_ID);
        if (ctx == null || red == null) return;
        CampaignSystem.selectCampaignContactTarget(ctx,
                "Red Drone Training Patrol",
                "Partial Intel  |  hostile practice contact",
                "Partial",
                red.x - 105.0,
                red.y + 70.0,
                true,
                true);
        UISystem.addPing(ctx, red.x - 105.0, red.y + 70.0, 1.8);
    }

    private static void prepareTacticalCommandSchoolSandbox(GameContext ctx, TutorialState st) {
        if (ctx == null || st == null) return;
        if (st.tacticalSandboxPrepared
                && !CampaignSystem.isStrategicOvermapMode(ctx)
                && ctx.player != null
                && shipById(ctx, st.homeBaseId) != null) {
            return;
        }
        ctx.ships.clear();
        ctx.projectiles.clear();
        ctx.asteroids.clear();
        ctx.salvage.clear();
        ctx.damageEvents.clear();
        ctx.audioEvents.clear();
        ctx.teamBases.clear();
        ctx.baseUpgrades.clear();
        ctx.lockedTarget = null;
        ctx.allyBase = null;
        ctx.enemyBase = null;
        ctx.ui.waypointX = Double.NaN;
        ctx.ui.waypointY = Double.NaN;
        ctx.ui.mapPings.clear();
        ctx.ui.mapOpen = false;
        ctx.ui.shopOpen = false;
        ctx.ui.baseMenuOpen = false;
        ctx.ui.powerManagementOpen = false;
        ctx.ui.crewStationsOpen = false;
        ctx.ui.flightDeckOpen = false;
        ctx.state = GameState.RUNNING;

        double baseX = GameMath.clamp(ctx.WORLD_W * 0.15, 220.0, ctx.WORLD_W - 260.0);
        double baseY = GameMath.clamp(ctx.WORLD_H * 0.58, 220.0, ctx.WORLD_H - 220.0);
        double stationVisualClearance = RoleStats.get(ShipRole.BASE).radius
                * ShipHullSilhouette.skinRenderScale() + 260.0;
        double playerX = GameMath.clamp(baseX + stationVisualClearance, 90.0, ctx.WORLD_W - 90.0);
        double playerY = baseY;

        st.alphaX = GameMath.clamp(ctx.WORLD_W * 0.28, 260.0, ctx.WORLD_W - 260.0);
        st.alphaY = GameMath.clamp(ctx.WORLD_H * 0.28, 220.0, ctx.WORLD_H - 220.0);
        st.betaX = GameMath.clamp(ctx.WORLD_W * 0.48, 260.0, ctx.WORLD_W - 260.0);
        st.betaY = GameMath.clamp(ctx.WORLD_H * 0.70, 220.0, ctx.WORLD_H - 220.0);
        st.weaponsX = GameMath.clamp(ctx.WORLD_W * 0.72, 260.0, ctx.WORLD_W - 260.0);
        st.weaponsY = GameMath.clamp(ctx.WORLD_H * 0.48, 220.0, ctx.WORLD_H - 220.0);
        st.miningX = GameMath.clamp(ctx.WORLD_W * 0.38, 260.0, ctx.WORLD_W - 260.0);
        st.miningY = GameMath.clamp(ctx.WORLD_H * 0.18, 220.0, ctx.WORLD_H - 220.0);
        Ship homeBase = new FleetShip(ShipRole.BASE, st.playerFaction, baseX, baseY);
        homeBase.name = "Tutorial Base";
        homeBase.oreStockpile = 1800;
        ctx.ships.add(homeBase);
        ctx.teamBases.put(st.playerFaction, homeBase);
        BaseUpgrades tutorialUpgrades = new BaseUpgrades();
        tutorialUpgrades.hullLv = 1;
        tutorialUpgrades.shieldLv = 0;
        tutorialUpgrades.turretLv = 1;
        tutorialUpgrades.miningLv = 1;
        tutorialUpgrades.hangarLv = 2;
        tutorialUpgrades.bindTo(homeBase);
        ctx.baseUpgrades.put(homeBase, tutorialUpgrades);
        st.homeBaseId = homeBase.id;
        if (st.playerFaction == Faction.ALLY) ctx.allyBase = homeBase;
        if (st.playerFaction == Faction.ENEMY) ctx.enemyBase = homeBase;

        ctx.player = new Player(ShipRole.FRIGATE, playerX, playerY);
        ctx.player.faction = st.playerFaction;
        ctx.player.name = "Player";
        ctx.player.angle = 0.0;
        ctx.player.vx = 0.0;
        ctx.player.vy = 0.0;
        ctx.player.setPowerPreset(Ship.PowerPreset.BALANCED);
        ctx.ships.add(ctx.player);

        Ship target = new FleetShip(ShipRole.PATROL, st.hostileFaction, st.weaponsX + 70.0, st.weaponsY);
        configureTutorialTarget(target, "Tutorial Drone");
        ctx.ships.add(target);
        st.combatTargetId = target.id;

        addAsteroid(ctx, st.miningX - 80.0, st.miningY + 24.0, 34.0, 620);
        addAsteroid(ctx, st.miningX + 30.0, st.miningY - 36.0, 28.0, 520);
        addAsteroid(ctx, st.miningX + 110.0, st.miningY + 42.0, 30.0, 560);

        try {
            DoctrineRegistry.applyToShip(homeBase);
            DoctrineRegistry.applyToShip(ctx.player);
            DoctrineRegistry.applyToShip(target);
        } catch (Throwable ignored) {}
        homeBase.shieldActive = false;
        homeBase.shieldMax = 0.0;
        homeBase.shield = 0.0;
        homeBase.shieldRegen = 0.0;
        homeBase.repairRange = 0.0;
        homeBase.resetShieldState();

        st.startingOreTotal = combinedOreTotal(ctx, st.homeBaseId);
        st.startingPlayerCargo = (ctx.player == null) ? 0 : ctx.player.cargo;
        st.miningPocketOreStart = miningPocketOreTotal(ctx, st);
        st.reachedAlpha = false;
        st.betaWaypointSet = false;
        st.reachedBeta = false;
        st.pingedWeaponsRange = false;
        st.tutorialDroneDamaged = false;
        st.xrayFilterUsed = false;
        st.xrayRoomFocused = false;
        st.minedOre = false;
        st.dockedAtHome = false;
        st.hangarTierThree = false;
        st.swappedToCarrier = false;
        st.tacticalSandboxPrepared = true;

        if (ctx.campaign != null && ctx.campaign.commandSchoolTraining) {
            ctx.campaign.strategicOvermapMode = false;
            ctx.campaign.galaxyEncounterActive = true;
            ctx.campaign.galaxyAmbientEncounterActive = true;
            ctx.campaign.activeGalaxyEncounterLocationId = CampaignSystem.COMMAND_SCHOOL_RED_SITE_ID;
            CampaignSystem.syncLoadedMissionSubzoneFromPlayer(ctx);
        }
        UISystem.focusTacticalMapOnCurrentMission(ctx);
        FogOfWarSystem.reset(ctx);
    }

    private static void focusHomeBase(GameContext ctx, TutorialState st) {
        Ship base = shipById(ctx, st.homeBaseId);
        if (base != null) {
            ctx.ui.waypointX = base.x;
            ctx.ui.waypointY = base.y;
        }
    }

    private static void capturePowerAndCrewBaseline(GameContext ctx, TutorialState st) {
        if (ctx == null || st == null || ctx.player == null) return;
        st.powerCrewBaselineCaptured = true;
        st.baselinePowerPreset = ctx.player.powerPreset;
        st.baselinePowerBuses = ctx.player.powerBusFractions();
        st.baselineCaptainDirective = ctx.command.captainDirective;
        st.baselineHelmMode = ctx.command.helmMode;
        st.baselineTacticalMode = ctx.command.tacticalMode;
        st.baselineEngineeringMode = ctx.command.engineeringMode;
    }

    private static void captureCarrierBaseline(GameContext ctx, TutorialState st) {
        if (ctx == null || st == null || ctx.player == null) return;
        st.flightDeckBaselineCaptured = true;
        st.baselineCarrierMode = ctx.player.carrierCommandMode;
        st.baselineCarrierAutoLaunch = ctx.player.carrierAutoLaunch;
    }

    private static boolean powerAdjustedSinceBaseline(GameContext ctx, TutorialState st) {
        if (ctx == null || st == null || ctx.player == null || !st.powerCrewBaselineCaptured) return false;
        if (ctx.player.isOverloadActive() || ctx.player.isEmergencyThrustActive()) return true;
        if (ctx.player.powerPreset != st.baselinePowerPreset) return true;
        return powerBusDelta(ctx.player.powerBusFractions(), st.baselinePowerBuses) > 0.045;
    }

    private static boolean crewAdjustedSinceBaseline(GameContext ctx, TutorialState st) {
        if (ctx == null || st == null || !st.powerCrewBaselineCaptured) return false;
        return ctx.command.captainDirective != st.baselineCaptainDirective
                || ctx.command.helmMode != st.baselineHelmMode
                || ctx.command.tacticalMode != st.baselineTacticalMode
                || ctx.command.engineeringMode != st.baselineEngineeringMode
                || ctx.command.activeCrewStation != GameContext.CrewStation.CAPTAIN;
    }

    private static double powerBusDelta(double[] now, double[] before) {
        if (now == null || before == null || now.length == 0 || before.length == 0) return 0.0;
        int n = Math.min(now.length, before.length);
        double total = 0.0;
        for (int i = 0; i < n; i++) {
            total += Math.abs(now[i] - before[i]);
        }
        return total;
    }

    private static void seedDamageControlFire(GameContext ctx, TutorialState st) {
        if (ctx == null || st == null || ctx.player == null) return;
        ctx.player.seedRoomFire(ShipRoomLayout.RoomId.ENGINES, 1.35);
        ctx.player.seedRoomFire(ShipRoomLayout.RoomId.POWER_CONDUITS, 0.55);
        st.seededDamageControlFire = true;
        EventSystem.showBanner(ctx, "ENGINEERING DRILL: FIRE IN ENGINES", 1.4);
    }

    private static List<Marker> markers(GameContext ctx, TutorialState st) {
        ArrayList<Marker> out = new ArrayList<>();
        if (ctx != null && CampaignSystem.isStrategicOvermapMode(ctx)) return out;
        out.add(new Marker("NAV ALPHA", st.alphaX, st.alphaY, POINT_REACHED_RADIUS));
        out.add(new Marker("NAV BETA", st.betaX, st.betaY, POINT_REACHED_RADIUS));
        out.add(new Marker("WEAPONS RANGE", st.weaponsX, st.weaponsY, WEAPON_RANGE_RADIUS));
        out.add(new Marker("MINING POCKET", st.miningX, st.miningY, MINING_RADIUS));
        return out;
    }

    private static Marker activeMarker(GameContext ctx, TutorialState st) {
        return switch (currentLesson(st)) {
            case OVERWORLD_MAP_READING, SITE_SELECTION, PLOT_MOVEMENT, SCAN_AND_INTEL,
                    RESOURCE_SITE, STATION_SERVICES, FLEET_ORGANIZATION, OVERWORLD_TO_MISSION -> null;
            case FLIGHT_BASICS -> !st.reachedAlpha
                    ? new Marker("NAV ALPHA", st.alphaX, st.alphaY, POINT_REACHED_RADIUS)
                    : new Marker("NAV BETA", st.betaX, st.betaY, POINT_REACHED_RADIUS);
            case TARGETING_AND_SENSORS -> (st.pingedWeaponsRange && st.tutorialDroneDamaged)
                    ? null
                    : new Marker("WEAPONS RANGE", st.weaponsX, st.weaponsY, WEAPON_RANGE_RADIUS);
            case LOGISTICS_AND_REFIT -> !st.minedOre
                    ? new Marker("MINING POCKET", st.miningX, st.miningY, MINING_RADIUS)
                    : homeBaseMarker(ctx, st);
            case BRIDGE_SYSTEMS -> st.swappedToCarrier ? null : homeBaseMarker(ctx, st);
            case CARRIER_AND_WARP -> null;
            case COMPLETE -> null;
        };
    }

    private static Marker homeBaseMarker(GameContext ctx, TutorialState st) {
        Ship base = shipById(ctx, st.homeBaseId);
        return (base == null) ? null : new Marker("HOME BASE", base.x, base.y, 120.0);
    }

    private static void drawMarker(Graphics2D g2, Marker marker, boolean active) {
        int x = (int) Math.round(marker.x);
        int y = (int) Math.round(marker.y);
        int r = (int) Math.round(marker.radius);
        Color ring = active ? new Color(255, 235, 150, 210) : new Color(150, 205, 255, 110);
        Color fill = active ? new Color(255, 215, 120, 28) : new Color(120, 170, 255, 14);
        g2.setColor(fill);
        g2.fillOval(x - r, y - r, r * 2, r * 2);
        g2.setColor(ring);
        g2.drawOval(x - r, y - r, r * 2, r * 2);
        g2.drawLine(x - 10, y, x + 10, y);
        g2.drawLine(x, y - 10, x, y + 10);
        g2.setFont(new Font("Consolas", active ? Font.BOLD : Font.PLAIN, active ? 14 : 12));
        g2.drawString(marker.label, x - 42, y - r - 10);
    }

    private static List<String> wrapLines(String text, FontMetrics fm, int maxWidth) {
        ArrayList<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            lines.add("");
            return lines;
        }
        if (fm == null || maxWidth <= 24) {
            lines.add(text.trim());
            return lines;
        }

        String[] words = text.trim().split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && fm.stringWidth(candidate) > maxWidth) {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            } else {
                if (!line.isEmpty()) line.append(' ');
                line.append(word);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        if (lines.isEmpty()) {
            lines.add(text.trim());
        }
        return lines;
    }

    private static String fitLine(FontMetrics fm, String text, int maxWidth) {
        if (text == null) return "";
        if (fm == null || maxWidth <= 0 || fm.stringWidth(text) <= maxWidth) return text;
        String ellipsis = "...";
        int ellipsisWidth = fm.stringWidth(ellipsis);
        int end = text.length();
        while (end > 1 && fm.stringWidth(text.substring(0, end)) + ellipsisWidth > maxWidth) {
            end--;
        }
        return text.substring(0, Math.max(1, end)) + ellipsis;
    }

    private static int combinedOreTotal(GameContext ctx, int baseId) {
        Ship base = shipById(ctx, baseId);
        int playerOre = (ctx == null || ctx.player == null) ? 0 : ctx.player.cargo;
        int baseOre = (base == null) ? 0 : base.oreStockpile;
        return playerOre + baseOre;
    }

    private static boolean playerHasMinedOre(GameContext ctx, TutorialState st) {
        if (ctx == null || st == null || ctx.player == null) return false;
        if (ctx.player.cargo > st.startingPlayerCargo) return true;
        if (combinedOreTotal(ctx, st.homeBaseId) > st.startingOreTotal) return true;
        return miningPocketOreTotal(ctx, st) < st.miningPocketOreStart;
    }

    private static int miningPocketOreTotal(GameContext ctx, TutorialState st) {
        if (ctx == null || st == null || ctx.asteroids == null) return 0;
        int total = 0;
        double maxD2 = 260.0 * 260.0;
        for (Asteroid a : ctx.asteroids) {
            if (a == null) continue;
            if (GameMath.dist2(a.x, a.y, st.miningX, st.miningY) > maxD2) continue;
            total += Math.max(0, a.ore);
        }
        return total;
    }

    private static Faction defaultHostileFaction(Faction playerFaction) {
        if (playerFaction != null && playerFaction.isFriendlyTo(Faction.ENEMY)) {
            return Faction.ALLY;
        }
        return Faction.ENEMY;
    }

    private static String minimapShortLabel(String label) {
        if (label == null || label.isBlank()) return "?";
        return switch (label) {
            case "NAV ALPHA" -> "A";
            case "NAV BETA" -> "B";
            case "WEAPONS RANGE" -> "WR";
            case "MINING POCKET" -> "MP";
            case "HOME BASE" -> "HB";
            default -> label.substring(0, Math.min(2, label.length())).toUpperCase();
        };
    }

    private static boolean near(Ship ship, double x, double y, double radius) {
        return ship != null && GameMath.dist2(ship.x, ship.y, x, y) <= radius * radius;
    }

    private static boolean nearPoint(double x1, double y1, double x2, double y2, double radius) {
        if (!Double.isFinite(x1) || !Double.isFinite(y1)) return false;
        return GameMath.dist2(x1, y1, x2, y2) <= radius * radius;
    }

    private static boolean hasPingNear(GameContext ctx, double x, double y, double radius) {
        if (ctx == null || ctx.ui.mapPings == null) return false;
        double r2 = radius * radius;
        for (Renderer.MapPing ping : ctx.ui.mapPings) {
            if (ping == null) continue;
            if (GameMath.dist2(ping.x, ping.y, x, y) <= r2) return true;
        }
        return false;
    }

    private static int currentHangarLevel(GameContext ctx, int baseId) {
        Ship base = shipById(ctx, baseId);
        if (ctx == null || base == null) return 0;
        BaseUpgrades up = ctx.baseUpgrades.get(base);
        return (up == null) ? 0 : up.hangarLv;
    }

    private static Ship shipById(GameContext ctx, int id) {
        if (ctx == null || id <= 0) return null;
        for (Ship s : ctx.ships) {
            if (s != null && s.id == id) return s;
        }
        return null;
    }

    private static void configureTutorialTarget(Ship s, String label) {
        if (s == null) return;
        s.name = label;
        s.angle = Math.PI;
        s.vx = 0.0;
        s.vy = 0.0;
        s.desiredSpeed = 0.0;
        s.desiredSpeedBase = 0.0;
        s.bountyValue = 0;
        s.turrets.clear();
        s.surrendered = true;
        s.surrenderLockTimer = Double.POSITIVE_INFINITY;
        s.surrenderSelfDestructTimer = 0.0;
        s.aiCommittedTargetId = -1;
        s.aiForcedEngageTimer = 0.0;
        s.aiArrivalFireDelayTimer = Double.POSITIVE_INFINITY;
        s.hasCIWS = false;
        s.isCarrier = false;
        s.carrierAutoLaunch = false;
        s.hasSuperweapon = false;
        s.shieldMax = 0.0;
        s.shield = 0.0;
        s.shieldRegen = 0.0;
        s.shieldActive = false;
    }

    private static void addAsteroid(GameContext ctx, double x, double y, double radius, int ore) {
        if (ctx == null) return;
        Asteroid a = new Asteroid(x, y, radius, ore);
        a.rich = true;
        a.richness = 2.0;
        ctx.asteroids.add(a);
    }
}
