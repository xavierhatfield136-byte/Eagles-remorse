import app.config.GameMode;
import app.state.PerfTelemetry;
import java.awt.*;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;

public final class DevOverlay {
    private DevOverlay() {}

    public static void draw(Graphics2D g2, GameContext ctx, int w, int h) {
        int pad = 10;
        int x = pad, y = pad;
        int lineH = 16;

        int minerLines = 0;
        if (ctx != null && ctx.ships != null) {
            for (Ship s : ctx.ships) {
                if (s == null) continue;
                if (s.role != ShipRole.MINER) continue;
                minerLines++;
                if (minerLines >= 6) break;
            }
        }

        g2.setFont(new Font("Consolas", Font.PLAIN, 14));

        int lineCount = 26 + minerLines;
        if (ctx != null && ctx.config != null && ctx.config.mode == GameMode.FOUR_TEAM_DOMINATION) {
            lineCount++;
        }

        // Background panel
        int boxW = 360;
        int boxH = 10 + lineH * lineCount;
        g2.setColor(new Color(0, 0, 0, 165));
        g2.fillRoundRect(x - 6, y - 6, boxW, boxH, 12, 12);
        g2.setColor(new Color(255, 255, 255, 60));
        g2.drawRoundRect(x - 6, y - 6, boxW, boxH, 12, 12);

        g2.setColor(new Color(255, 255, 255, 220));
        y += lineH;
        drawLine(g2, x, y, "DEV OVERLAY (F3)  HEATMAP(F2): " + (DevTools.isAsteroidHeatmapEnabled() ? "ON" : "OFF")
                + "  AI: " + (DevTools.isAIEnabled() ? "ON" : "OFF") + "  Time: " + DevTools.getTimeScale() + "x");

        y += lineH;
        drawLine(g2, x, y, "Scenarios: F6/F7/F8/F9, Ctrl+F12=shooting range");

        y += lineH;
        drawLine(g2, x, y, "State: " + safe(ctx, "state"));

        y += lineH;
        drawLine(g2, x, y, "Campaign Clock: " + CampaignSystem.campaignTimeScaleReadout(ctx));

        y += lineH;
        drawLine(g2, x, y, "Living War: " + CampaignSystem.campaignLivingWarDebugReadout(ctx));

        y += lineH;
        drawLine(g2, x, y, "Ships: " + sizeOf(ctx, "ships") +
                "  Projectiles: " + sizeOf(ctx, "projectiles") +
                "  Asteroids: " + sizeOf(ctx, "asteroids"));

        if (ctx != null && ctx.config != null && ctx.config.mode == GameMode.FOUR_TEAM_DOMINATION) {
            Faction[] teams = Faction.fourTeamFactions();
            int alive = TeamSystem.countAliveTeams(ctx, teams);
            Faction leader = TeamSystem.getShipCountLeader(ctx, teams);
            y += lineH;
            drawLine(g2, x, y, "Teams Alive: " + alive +
                    "  Leader: " + (leader == null ? "?" : leader.teamName()));
        }

        y += lineH;
        drawLine(g2, x, y, "Salvage: " + sizeOf(ctx, "salvage") +
                "  Explosions: " + staticSize("Explosion", "active"));

        y += lineH;
        drawLine(g2, x, y, "Credits: " + safe(ctx, "credits") + "  OrePriceMul: " + safe(ctx, "orePriceMul"));

        PerfTelemetry perf = (ctx == null) ? null : ctx.perf;

        y += lineH;
        drawLine(g2, x, y, "Perf: " + fmt2(perfValue(perf, "fps")) + " fps"
                + "  Frame: " + fmt2(perfValue(perf, "frameMs")) + "ms"
                + "  Jitter: " + fmt2(perfValue(perf, "frameJitterMs")) + "ms");

        y += lineH;
        drawLine(g2, x, y, "Update: " + fmt2(perfValue(perf, "updateMs")) + "ms"
                + "  Render: " + fmt2(perfValue(perf, "renderMs")) + "ms"
                + "  Steps: " + perfInt(perf, "updateSteps")
                + "  Drop: " + perfInt(perf, "droppedUpdates"));

        y += lineH;
        drawLine(g2, x, y, "Hot: AI " + fmt2(perfValue(perf, "aiMs")) + "  Campaign " + fmt2(perfValue(perf, "campaignMs"))
                + "  Ships " + fmt2(perfValue(perf, "renderShipsMs")) + "  HUD " + fmt2(perfValue(perf, "renderHudMs")));

        y += lineH;
        drawLine(g2, x, y, "Hot2: Shield " + fmt2(perfValue(perf, "shieldRenderMs")) + "  Map " + fmt2(perfValue(perf, "renderMapMs")));

        y += lineH;
        drawLine(g2, x, y, "AI: Maint " + fmt2(perfValue(perf, "aiMaintenanceMs")) + "  Fleet " + fmt2(perfValue(perf, "aiFleetStateMs"))
                + "  Util " + fmt2(perfValue(perf, "aiShipUtilityMs")) + "  Combat " + fmt2(perfValue(perf, "aiShipCombatMs")));

        y += lineH;
        drawLine(g2, x, y, "AI2: Target " + fmt2(perfValue(perf, "aiShipCombatTargetMs")) + "  Fight " + fmt2(perfValue(perf, "aiShipCombatFightMs"))
                + "  Fire " + fmt2(perfValue(perf, "aiShipCombatFireMs")));

        y += lineH;
        drawLine(g2, x, y, "AI3: Avoid " + fmt2(perfValue(perf, "aiAvoidanceMs")) + "  Sync " + fmt2(perfValue(perf, "aiFormationSyncMs"))
                + "  Bounds " + fmt2(perfValue(perf, "aiBoundsMs")));

        y += lineH;
        drawLine(g2, x, y, "AI Cache: " + fmt2(perfValue(perf, "aiCacheQueryMs"))
                + "  Pref " + perfInt(perf, "aiPreferredTargetHits") + "/" + perfInt(perf, "aiPreferredTargetMisses")
                + "  Threat " + perfInt(perf, "aiImmediateThreatHits") + "/" + perfInt(perf, "aiImmediateThreatMisses")
                + "  Signal " + perfInt(perf, "aiSensorSignalHits") + "/" + perfInt(perf, "aiSensorSignalMisses"));

        y += lineH;
        drawLine(g2, x, y, "Drawn: Ships " + perfInt(perf, "drawnShips") + "/" + sizeOf(ctx, "ships")
                + "  Proj " + perfInt(perf, "drawnProjectiles") + "/" + sizeOf(ctx, "projectiles")
                + "  Ast " + perfInt(perf, "drawnAsteroids") + "/" + sizeOf(ctx, "asteroids"));

        y += lineH;
        drawLine(g2, x, y, "FX: Salv " + perfInt(perf, "drawnSalvage") + "/" + sizeOf(ctx, "salvage")
                + "  VFX " + perfInt(perf, "drawnVfx") + "/" + perfInt(perf, "totalVfx")
                + "  Expl " + perfInt(perf, "drawnExplosions") + "/" + perfInt(perf, "totalExplosions"));

        y += lineH;
        drawLine(g2, x, y, "Cam: (" + (int) safeD(ctx, "camX") + ", " + (int) safeD(ctx, "camY") + ")  View: " + w + "x" + h);

        y += lineH;
        // cursor world coords are optional
        Object cwx = getFieldValue(ctx, "cursorWorldX");
        Object cwy = getFieldValue(ctx, "cursorWorldY");
        if (cwx != null && cwy != null) {
            drawLine(g2, x, y, "CursorWorld: (" + (int) toDouble(cwx) + ", " + (int) toDouble(cwy) + ")");
        } else {
            drawLine(g2, x, y, "CursorWorld: (n/a)");
        }

        y += lineH;
        Object player = getFieldValue(ctx, "player");
        if (player != null) {
            drawLine(g2, x, y, "Player HP: " + safe(player, "hp") + "/" + safe(player, "hpMax") +
                    "  SH: " + safe(player, "shield") + "/" + safe(player, "shieldMax"));
        } else {
            drawLine(g2, x, y, "Player: null");
        }

        y += lineH;
        drawLine(g2, x, y, "Locked: " + (getFieldValue(ctx, "lockedTarget") != null));

        y += lineH;
        drawLine(g2, x, y, "Shop: " + safe(ctx, "shopOpen") +
                "  BaseMenu: " + safe(ctx, "baseMenuOpen") +
                "  Map: " + safe(ctx, "mapOpen"));

        y += lineH;
        boolean lMan = boolField(ctx, "firingPrimaryManual");
        boolean rMan = boolField(ctx, "firingSecondaryManual");
        boolean lAuto = boolField(ctx, "firingPrimaryAuto");
        boolean rAuto = boolField(ctx, "firingSecondaryAuto");
        drawLine(g2, x, y, "Fire M[L=" + lMan + " R=" + rMan + "]  AI[L=" + lAuto + " R=" + rAuto + "]"
                + "  AutoLock=" + safe(ctx, "autoLockTurrets"));

        y += lineH;
        drawLine(g2, x, y, "Fancy VFX (F10): " + (DevTools.isFancyVfxEnabled() ? "ON" : "OFF"));

        y += lineH;
        drawLine(g2, x, y, "RoomDbg Ctrl+F2 poly[" + onOff(DevTools.isRoomPolygonsEnabled())
                + "] Ctrl+F3 hits[" + onOff(DevTools.isRoomImpactPointsEnabled())
                + "] Ctrl+F4 hp[" + onOff(DevTools.isRoomHpBarsEnabled())
                + "] Ctrl+F5 hazard[" + onOff(DevTools.isRoomHazardsEnabled()) + "]");

        if (minerLines > 0) {
            int shown = 0;
            for (Ship s : ctx.ships) {
                if (s == null) continue;
                if (s.role != ShipRole.MINER) continue;

                double distA = -1;
                if (s.minerTarget != null) distA = Math.hypot(s.minerTarget.x - s.x, s.minerTarget.y - s.y);
                double distB = -1;
                if (s.minerHomeBase != null) distB = Math.hypot(s.minerHomeBase.x - s.x, s.minerHomeBase.y - s.y);

                y += lineH;
                String line = "MINER #" + s.id + " " + (s.minerState == null ? "?" : s.minerState.name()) +
                        " cargo=" + s.cargo + "/" + s.cargoMax +
                        " distA=" + (distA < 0 ? "?" : (int) Math.round(distA)) +
                        " distB=" + (distB < 0 ? "?" : (int) Math.round(distB));
                drawLine(g2, x, y, line);

                shown++;
                if (shown >= 6) break;
            }
        }
    }

    private static void drawLine(Graphics2D g2, int x, int y, String s) {
        g2.drawString(s, x, y);
    }

    private static String fmt2(double v) {
        return String.format(Locale.US, "%.2f", v);
    }

    private static String onOff(boolean v) {
        return v ? "ON" : "OFF";
    }

    private static Object getFieldValue(Object obj, String field) {
        if (obj == null) return null;
        try {
            Field f = obj.getClass().getDeclaredField(field);
            f.setAccessible(true);
            return f.get(obj);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String safe(Object obj, String field) {
        Object v = getFieldValue(obj, field);
        return v == null ? "?" : String.valueOf(v);
    }

    private static boolean boolField(Object obj, String field) {
        Object v = getFieldValue(obj, field);
        if (v instanceof Boolean b) return b;
        return false;
    }

    private static double safeD(Object obj, String field) {
        Object v = getFieldValue(obj, field);
        if (v == null) return 0.0;
        return toDouble(v);
    }

    private static double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); } catch (Throwable ignored) { return 0.0; }
    }

    private static int sizeOf(Object obj, String field) {
        Object v = getFieldValue(obj, field);
        if (v instanceof List<?> list) return list.size();
        return -1;
    }

    private static int staticSize(String className, String staticField) {
        try {
            Class<?> c = Class.forName(className);
            Field f = c.getDeclaredField(staticField);
            f.setAccessible(true);
            Object v = f.get(null);
            if (v instanceof List<?> list) return list.size();
        } catch (Throwable ignored) {}
        return -1;
    }

    private static double perfValue(PerfTelemetry perf, String field) {
        if (perf == null) return 0.0;
        return safeD(perf, field);
    }

    private static String perfInt(PerfTelemetry perf, String field) {
        if (perf == null) return "?";
        return safe(perf, field);
    }
}
