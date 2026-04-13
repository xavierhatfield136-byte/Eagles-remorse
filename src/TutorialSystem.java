import app.config.GameMode;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

public final class TutorialSystem {
    private TutorialSystem() {}

    private static final WeakHashMap<GameContext, TutorialState> STATES = new WeakHashMap<>();
    private static final double ACTIVE_PING_PERIOD = 1.0;
    private static final double POINT_REACHED_RADIUS = 110.0;
    private static final double MINING_RADIUS = 180.0;
    private static final double WEAPON_RANGE_RADIUS = 170.0;
    private static final double PING_MATCH_RADIUS = 220.0;
    private static final int LESSON_COUNT = 5;

    private enum LessonId {
        FLIGHT_BASICS,
        TARGETING_AND_SENSORS,
        LOGISTICS_AND_REFIT,
        BRIDGE_SYSTEMS,
        CARRIER_AND_WARP,
        COMPLETE
    }

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
        double gammaX;
        double gammaY;
        double pingTimer = 0.0;
        int lessonIndex = 0;
        int startingOreTotal = 0;
        int startingPlayerCargo = 0;
        int miningPocketOreStart = 0;
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
        boolean gammaWaypointSet = false;
        boolean warpChargeStarted = false;
        Ship.PowerPreset baselinePowerPreset = Ship.PowerPreset.BALANCED;
        double[] baselinePowerBuses = new double[]{};
        GameContext.CaptainDirective baselineCaptainDirective = GameContext.CaptainDirective.BALANCED;
        GameContext.HelmMode baselineHelmMode = GameContext.HelmMode.INTERCEPT;
        GameContext.TacticalMode baselineTacticalMode = GameContext.TacticalMode.DEFENSIVE;
        GameContext.EngineeringMode baselineEngineeringMode = GameContext.EngineeringMode.BALANCED;
        boolean baselineScienceJamming = false;
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

        double baseX = GameMath.clamp(ctx.WORLD_W * 0.15, 220.0, ctx.WORLD_W - 260.0);
        double baseY = GameMath.clamp(ctx.WORLD_H * 0.58, 220.0, ctx.WORLD_H - 220.0);
        double playerX = GameMath.clamp(baseX + 180.0, 90.0, ctx.WORLD_W - 90.0);
        double playerY = baseY;

        st.alphaX = GameMath.clamp(ctx.WORLD_W * 0.28, 260.0, ctx.WORLD_W - 260.0);
        st.alphaY = GameMath.clamp(ctx.WORLD_H * 0.28, 220.0, ctx.WORLD_H - 220.0);
        st.betaX = GameMath.clamp(ctx.WORLD_W * 0.48, 260.0, ctx.WORLD_W - 260.0);
        st.betaY = GameMath.clamp(ctx.WORLD_H * 0.70, 220.0, ctx.WORLD_H - 220.0);
        st.weaponsX = GameMath.clamp(ctx.WORLD_W * 0.72, 260.0, ctx.WORLD_W - 260.0);
        st.weaponsY = GameMath.clamp(ctx.WORLD_H * 0.48, 220.0, ctx.WORLD_H - 220.0);
        st.miningX = GameMath.clamp(ctx.WORLD_W * 0.38, 260.0, ctx.WORLD_W - 260.0);
        st.miningY = GameMath.clamp(ctx.WORLD_H * 0.18, 220.0, ctx.WORLD_H - 220.0);
        st.gammaX = GameMath.clamp(ctx.WORLD_W * 0.82, 260.0, ctx.WORLD_W - 260.0);
        st.gammaY = GameMath.clamp(ctx.WORLD_H * 0.16, 220.0, ctx.WORLD_H - 220.0);

        Ship homeBase = new FleetShip(ShipRole.BASE, st.playerFaction, baseX, baseY);
        homeBase.name = "Tutorial Base";
        homeBase.oreStockpile = 1800;
        ctx.ships.add(homeBase);
        ctx.teamBases.put(st.playerFaction, homeBase);
        BaseUpgrades tutorialUpgrades = new BaseUpgrades();
        tutorialUpgrades.hullLv = 1;
        tutorialUpgrades.shieldLv = 1;
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

        st.startingOreTotal = combinedOreTotal(ctx, st.homeBaseId);
        st.startingPlayerCargo = (ctx.player == null) ? 0 : ctx.player.cargo;
        st.miningPocketOreStart = miningPocketOreTotal(ctx, st);
        STATES.put(ctx, st);
        enterLesson(ctx, st, LessonId.FLIGHT_BASICS, true);
    }

    public static boolean isActive(GameContext ctx) {
        return state(ctx) != null;
    }

    public static void update(GameContext ctx, double dt) {
        TutorialState st = state(ctx);
        if (st == null || ctx == null || ctx.player == null) return;

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
            enterLesson(ctx, st, nextLesson(lesson), true);
            refreshPersistentProgress(ctx, st);
            handleLessonSideEffects(ctx, st);
        }
    }

    public static String hudTitle(GameContext ctx) {
        TutorialState st = state(ctx);
        if (st == null) return "";
        LessonId lesson = currentLesson(st);
        if (lesson == LessonId.COMPLETE) return "COMMAND SCHOOL   COMPLETE";
        return "COMMAND SCHOOL   LESSON " + (st.lessonIndex + 1) + "/" + LESSON_COUNT;
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

        LessonId lesson = currentLesson(st);
        List<ChecklistItem> items = checklist(ctx, st, lesson);
        ChecklistItem next = nextIncompleteItem(items);

        int panelW = Math.max(280, Math.min(350, viewportW / 4));
        int margin = 18;
        int x = margin;
        int y = margin;
        int contentW = panelW - 24;

        Graphics2D gx = (Graphics2D) g2.create();
        gx.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Font eyebrowFont = new Font("Consolas", Font.BOLD, 12);
        Font titleFont = new Font("Consolas", Font.BOLD, 21);
        Font bodyFont = new Font("Consolas", Font.PLAIN, 13);
        Font footerFont = new Font("Consolas", Font.PLAIN, 12);

        FontMetrics bodyFm = gx.getFontMetrics(bodyFont);
        List<String> summaryLines = wrapLines(lesson == LessonId.COMPLETE
                        ? "You have cleared the full command school and can keep experimenting in this controlled sector."
                        : lessonSummary(lesson),
                bodyFm,
                contentW);

        int headerH = 62;
        int summaryH = Math.max(16, summaryLines.size() * 14);
        int checklistH = 0;
        for (ChecklistItem item : items) {
            int lineCount = Math.max(1, wrapLines(item.label, bodyFm, contentW - 36).size());
            checklistH += 10 + (lineCount * 14);
        }
        int footerH = (next == null) ? 28 : 42;
        int panelH = headerH + summaryH + checklistH + footerH + 22;

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
        int completedLessons = Math.min(LESSON_COUNT, lesson == LessonId.COMPLETE ? LESSON_COUNT : st.lessonIndex);
        int filledW = (int) Math.round(progressOuterW * (completedLessons / (double) LESSON_COUNT));
        gx.setColor(new Color(255, 255, 255, 18));
        gx.fillRoundRect(progressOuterX, progressOuterY, progressOuterW, progressOuterH, 8, 8);
        gx.setPaint(new GradientPaint(progressOuterX, progressOuterY, new Color(96, 198, 255, 220),
                progressOuterX + Math.max(1, filledW), progressOuterY, new Color(255, 213, 110, 210)));
        gx.fillRoundRect(progressOuterX, progressOuterY, Math.max(10, filledW), progressOuterH, 8, 8);

        gx.setFont(eyebrowFont);
        gx.setColor(new Color(142, 198, 255, 210));
        gx.drawString("COMMAND SCHOOL", 14, 34);

        gx.setFont(titleFont);
        gx.setColor(Color.WHITE);
        gx.drawString(lessonName(lesson), 14, 56);

        gx.setFont(bodyFont);
        gx.setColor(new Color(196, 214, 236, 210));
        String counter = (lesson == LessonId.COMPLETE) ? "Sandbox complete" : "Lesson " + (st.lessonIndex + 1) + " of " + LESSON_COUNT;
        gx.drawString(counter, panelW - 14 - bodyFm.stringWidth(counter), 34);

        int cursorY = 74;
        for (String line : summaryLines) {
            gx.drawString(line, 14, cursorY);
            cursorY += 14;
        }

        cursorY += 4;
        for (ChecklistItem item : items) {
            List<String> wrapped = wrapLines(item.label, bodyFm, contentW - 36);
            boolean pending = !item.complete && next != null && next.label.equals(item.label);
            int boxX = 14;
            int boxY = cursorY - 10;

            gx.setColor(item.complete ? new Color(74, 204, 132, 228)
                    : pending ? new Color(255, 216, 120, 220)
                    : new Color(118, 140, 164, 170));
            gx.setStroke(new BasicStroke(1.6f));
            gx.drawRoundRect(boxX, boxY, 16, 16, 6, 6);
            if (item.complete) {
                gx.setColor(new Color(74, 204, 132, 70));
                gx.fillRoundRect(boxX, boxY, 16, 16, 6, 6);
                gx.setColor(new Color(232, 255, 240, 230));
                gx.drawLine(boxX + 4, boxY + 8, boxX + 7, boxY + 12);
                gx.drawLine(boxX + 7, boxY + 12, boxX + 13, boxY + 4);
            } else if (pending) {
                gx.setColor(new Color(255, 216, 120, 42));
                gx.fillRoundRect(boxX, boxY, 16, 16, 6, 6);
            }

            gx.setColor(item.complete ? new Color(216, 245, 226, 230)
                    : pending ? new Color(255, 240, 190, 235)
                    : new Color(198, 214, 232, 205));
            for (String line : wrapped) {
                gx.drawString(line, 38, cursorY);
                cursorY += 14;
            }
            cursorY += 1;
        }

        gx.setFont(footerFont);
        if (next != null) {
            gx.setColor(new Color(255, 214, 140, 220));
            gx.drawString("Focus", 14, panelH - 26);
            gx.setColor(new Color(218, 230, 246, 220));
            for (String line : wrapLines(next.hint, gx.getFontMetrics(footerFont), panelW - 72)) {
                gx.drawString(line, 50, panelH - 26);
                break;
            }
        }
        gx.setColor(new Color(176, 188, 206, 188));
        gx.drawString("Lessons advance automatically. F10 returns to menu.", 14, panelH - 10);
        gx.dispose();
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

        java.awt.Rectangle r = Renderer.getStrategicMapRect(viewW, viewH);
        int pad = 18;
        java.awt.Rectangle m = new java.awt.Rectangle(r.x + pad, r.y + 44, r.width - pad * 2, r.height - 60);
        Marker active = activeMarker(ctx, st);
        Graphics2D gx = (Graphics2D) g2.create();
        gx.setFont(new Font("Consolas", Font.BOLD, 11));

        for (Marker marker : markers(ctx, st)) {
            if (marker == null) continue;
            int px = m.x + (int) Math.round((marker.x / Math.max(1.0, ctx.WORLD_W)) * m.width);
            int py = m.y + (int) Math.round((marker.y / Math.max(1.0, ctx.WORLD_H)) * m.height);
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

    private static void refreshPersistentProgress(GameContext ctx, TutorialState st) {
        st.betaWaypointSet |= nearPoint(ctx.ui.waypointX, ctx.ui.waypointY, st.betaX, st.betaY, 150.0);
        st.gammaWaypointSet |= nearPoint(ctx.ui.waypointX, ctx.ui.waypointY, st.gammaX, st.gammaY, 150.0);
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
        st.warpChargeStarted |= st.gammaWaypointSet && ctx.command.playerTeleportCharging;

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

    private static void handleLessonSideEffects(GameContext ctx, TutorialState st) {
        LessonId lesson = currentLesson(st);
        if (lesson == LessonId.BRIDGE_SYSTEMS) {
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
            if (carrierWarpObjectiveReady(st) && !st.warpChargeStarted) {
                focusGammaWaypoint(ctx, st);
                st.gammaWaypointSet = true;
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

    private static LessonId nextLesson(LessonId lesson) {
        return switch (lesson) {
            case FLIGHT_BASICS -> LessonId.TARGETING_AND_SENSORS;
            case TARGETING_AND_SENSORS -> LessonId.LOGISTICS_AND_REFIT;
            case LOGISTICS_AND_REFIT -> LessonId.BRIDGE_SYSTEMS;
            case BRIDGE_SYSTEMS -> LessonId.CARRIER_AND_WARP;
            case CARRIER_AND_WARP, COMPLETE -> LessonId.COMPLETE;
        };
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
        st.pingTimer = 0.0;

        switch (lesson) {
            case FLIGHT_BASICS -> {
                ctx.ui.waypointX = st.alphaX;
                ctx.ui.waypointY = st.alphaY;
                if (announce) EventSystem.showBanner(ctx, "LESSON 1: FLIGHT BASICS", 2.4);
            }
            case TARGETING_AND_SENSORS -> {
                ctx.ui.waypointX = st.weaponsX;
                ctx.ui.waypointY = st.weaponsY;
                if (announce) EventSystem.showBanner(ctx, "LESSON 2: TARGETING + XRAY", 2.4);
            }
            case LOGISTICS_AND_REFIT -> {
                ctx.ui.waypointX = st.miningX;
                ctx.ui.waypointY = st.miningY;
                if (announce) EventSystem.showBanner(ctx, "LESSON 3: LOGISTICS LOOP", 2.4);
            }
            case BRIDGE_SYSTEMS -> {
                ctx.ui.waypointX = Double.NaN;
                ctx.ui.waypointY = Double.NaN;
                st.powerCrewBaselineCaptured = false;
                st.powerAdjusted = false;
                st.crewAdjusted = false;
                st.seededDamageControlFire = false;
                st.fireSuppressed = false;
                focusHomeBase(ctx, st);
                if (announce) EventSystem.showBanner(ctx, "LESSON 4: BRIDGE SYSTEMS", 2.4);
            }
            case CARRIER_AND_WARP -> {
                ctx.ui.waypointX = Double.NaN;
                ctx.ui.waypointY = Double.NaN;
                st.flightDeckBaselineCaptured = false;
                st.openedFlightDeck = false;
                st.launchedWing = false;
                st.carrierModeChanged = false;
                st.carrierAutoLaunchChanged = false;
                if (ctx.player != null && ctx.player.isCarrier) {
                    captureCarrierBaseline(ctx, st);
                }
                if (announce) EventSystem.showBanner(ctx, "LESSON 5: CARRIER + WARP", 2.4);
            }
            case COMPLETE -> {
                ctx.ui.waypointX = Double.NaN;
                ctx.ui.waypointY = Double.NaN;
                EventSystem.showBanner(ctx, "COMMAND SCHOOL COMPLETE", 3.0);
            }
        }
    }

    private static String lessonName(LessonId lesson) {
        return switch (lesson) {
            case FLIGHT_BASICS -> "Flight Basics";
            case TARGETING_AND_SENSORS -> "Targeting And Sensors";
            case LOGISTICS_AND_REFIT -> "Logistics And Refit";
            case BRIDGE_SYSTEMS -> "Bridge Systems";
            case CARRIER_AND_WARP -> "Carrier And Warp";
            case COMPLETE -> "Command School Clear";
        };
    }

    private static String lessonSummary(LessonId lesson) {
        return switch (lesson) {
            case FLIGHT_BASICS ->
                    "Learn the ship's movement model, waypoint flow, and how the minimap supports a simple navigation run.";
            case TARGETING_AND_SENSORS ->
                    "Practice tactical pings, target locking, live-fire damage confirmation, and the x-ray room inspection tools.";
            case LOGISTICS_AND_REFIT ->
                    "Run the game's economic loop: mine ore, dock at base, and spend that income on a hangar upgrade.";
            case BRIDGE_SYSTEMS ->
                    "Refit into a carrier, touch power management, touch crew command, then clear an engineering emergency.";
            case CARRIER_AND_WARP ->
                    "Open the flight deck, launch a wing, change carrier behavior, and finish with a controlled warp setup.";
            case COMPLETE ->
                    "Every command school lesson is complete and the full sandbox remains open for practice.";
        };
    }

    private static List<ChecklistItem> checklist(GameContext ctx, TutorialState st, LessonId lesson) {
        ArrayList<ChecklistItem> items = new ArrayList<>();
        switch (lesson) {
            case FLIGHT_BASICS -> {
                items.add(new ChecklistItem(
                        "[WASD] Reach NAV ALPHA.",
                        "Use thrust and steering to fly into the NAV ALPHA ring.",
                        st.reachedAlpha));
                items.add(new ChecklistItem(
                        "[M + click] Set NAV BETA on the strategic map.",
                        "Open the map with M and click NAV BETA to place a waypoint there.",
                        st.betaWaypointSet));
                items.add(new ChecklistItem(
                        "[Waypoint] Fly to NAV BETA.",
                        "Follow the waypoint ring or minimap cue until you reach NAV BETA.",
                        st.reachedBeta));
            }
            case TARGETING_AND_SENSORS -> {
                items.add(new ChecklistItem(
                        "[P] Ping the weapons range for your crew.",
                        "Move the cursor over WEAPONS RANGE and press P to drop a tactical ping.",
                        st.pingedWeaponsRange));
                items.add(new ChecklistItem(
                        "[L / SPACE] Lock and damage the tutorial drone.",
                        "Lock the tutorial drone with L and land any hit with SPACE or SHIFT.",
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
                        "[TAB] Swap your hull to a carrier.",
                        "Use the loadout panel at base and switch into a carrier hull.",
                        st.swappedToCarrier));
                items.add(new ChecklistItem(
                        "[O or Y] Change one power state or preset.",
                        "Open power management with O or cycle presets with Y to prove you can manage ship power.",
                        st.powerAdjusted));
                items.add(new ChecklistItem(
                        "[H] Change one crew directive or station mode.",
                        "Open crew stations with H and alter any directive, mode, or automation posture.",
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
                        "[-] Begin warp charge to NAV GAMMA.",
                        "Once the carrier tasks are complete, the tutorial locks your active waypoint onto NAV GAMMA. Open the map if you want to confirm it, then start battlefield warp with - or Backspace.",
                        st.warpChargeStarted));
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

    private static void focusHomeBase(GameContext ctx, TutorialState st) {
        Ship base = shipById(ctx, st.homeBaseId);
        if (base != null) {
            ctx.ui.waypointX = base.x;
            ctx.ui.waypointY = base.y;
        }
    }

    private static void focusGammaWaypoint(GameContext ctx, TutorialState st) {
        if (ctx == null || st == null) return;
        ctx.ui.waypointX = st.gammaX;
        ctx.ui.waypointY = st.gammaY;
    }

    private static boolean carrierWarpObjectiveReady(TutorialState st) {
        return st != null
                && st.openedFlightDeck
                && st.launchedWing
                && (st.carrierModeChanged || st.carrierAutoLaunchChanged);
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
        st.baselineScienceJamming = ctx.command.scienceJamming;
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
                || ctx.command.scienceJamming != st.baselineScienceJamming
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
        Ship base = shipById(ctx, st.homeBaseId);
        if (base != null) out.add(new Marker("HOME BASE", base.x, base.y, 120.0));
        out.add(new Marker("NAV ALPHA", st.alphaX, st.alphaY, POINT_REACHED_RADIUS));
        out.add(new Marker("NAV BETA", st.betaX, st.betaY, POINT_REACHED_RADIUS));
        out.add(new Marker("WEAPONS RANGE", st.weaponsX, st.weaponsY, WEAPON_RANGE_RADIUS));
        out.add(new Marker("MINING POCKET", st.miningX, st.miningY, MINING_RADIUS));
        out.add(new Marker("NAV GAMMA", st.gammaX, st.gammaY, POINT_REACHED_RADIUS));
        return out;
    }

    private static Marker activeMarker(GameContext ctx, TutorialState st) {
        return switch (currentLesson(st)) {
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
            case CARRIER_AND_WARP -> carrierWarpObjectiveReady(st)
                    ? new Marker("NAV GAMMA", st.gammaX, st.gammaY, POINT_REACHED_RADIUS)
                    : null;
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
            case "NAV GAMMA" -> "G";
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
