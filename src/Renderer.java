import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.geom.RoundRectangle2D;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Comparator;
import javax.imageio.ImageIO;

public class Renderer {
    private static final double IMPACT_DECAL_SCALE = 0.25;
    private static final double HULL_DAMAGE_DETAIL_MIN_SCREEN_SPAN = 72.0;

    private static final String[] CORE_MENU_LABELS = {"SHOP", "BASE", "MAP", "POWER", "CREW"};
    private static final String[] CORE_MENU_HOTKEYS = {"TAB", "B", "M", "O", "H"};
    private static final long XRAY_PERCENT_REFRESH_NS = 180_000_000L;
    private static final Font XRAY_TITLE_FONT = new Font("Consolas", Font.BOLD, 13);
    private static final Font XRAY_SUBTITLE_FONT = new Font("Consolas", Font.PLAIN, 11);
    private static final Font XRAY_SYMBOL_FONT = new Font("Consolas", Font.BOLD, 10);
    private static final Font XRAY_HP_FONT = new Font("Consolas", Font.PLAIN, 10);
    private static final String[] XRAY_PCT_LABELS = buildXrayPctLabels();
    // Cache rendered x-ray panels for a short window to avoid rebuilding the full panel every draw.
    private static final long XRAY_PANEL_FRAME_CACHE_NS = 36_000_000L;
    private static final java.util.WeakHashMap<Ship, EnumMap<ShipRoomLayout.RoomId, Integer>> XRAY_ROOM_PCT_CACHE =
            new java.util.WeakHashMap<>();
    private static final java.util.WeakHashMap<Ship, Long> XRAY_ROOM_PCT_CACHE_TS =
            new java.util.WeakHashMap<>();
    private static final java.util.WeakHashMap<Ship, XrayPanelFrameCache> XRAY_PANEL_CACHE =
            new java.util.WeakHashMap<>();
    private static final Font XRAY_META_FONT = new Font("Consolas", Font.PLAIN, 10);
    private static final Font XRAY_REPAIR_FONT = new Font("Consolas", Font.BOLD, 8);
    private static final Stroke XRAY_HIT_STROKE = new BasicStroke(1.8f);
    private static final Stroke XRAY_DISABLED_STROKE = new BasicStroke(1.5f);
    private static final Stroke XRAY_FOCUS_STROKE = new BasicStroke(2.1f);

    // ------------------------------------------------------------
    // Option 8: Strategic map / waypoints / pings
    // ------------------------------------------------------------
    public static final class MapPing {
        public double x, y;
        public double t; // seconds remaining
        public int faction; // 0=player, 1=team A, 2=team B, 3=team C, 4=team D

        public MapPing(double x, double y, double t, int faction) {
            this.x = x;
            this.y = y;
            this.t = t;
            this.faction = faction;
        }
    }

    public static Rectangle getStrategicMapRect(int viewW, int viewH) {
        int pad = 52;
        int w = Math.min(860, viewW - pad * 2);
        int h = Math.min(560, viewH - pad * 2);
        int x = (viewW - w) / 2;
        int y = (viewH - h) / 2;
        return new Rectangle(x, y, w, h);
    }

    public static Rectangle getCoreMenuBarRect(int viewW, int viewH) {
        int margin = 10;
        int h = 42;
        int maxW = 740;
        int avail = Math.max(220, viewW - margin * 2);
        int w = Math.min(maxW, avail);
        int x = (viewW - w) / 2;
        int y = viewH - h - margin;
        return new Rectangle(x, y, w, h);
    }

    public static Rectangle getCoreMenuButtonRect(int viewW, int viewH, int index) {
        if (index < 0 || index >= CORE_MENU_LABELS.length) return new Rectangle();
        Rectangle bar = getCoreMenuBarRect(viewW, viewH);
        int pad = 8;
        int gap = 6;
        int innerW = Math.max(1, bar.width - pad * 2);
        int cellW = (innerW - gap * (CORE_MENU_LABELS.length - 1)) / CORE_MENU_LABELS.length;
        int x = bar.x + pad + index * (cellW + gap);
        int y = bar.y + 6;
        int w = Math.max(24, cellW);
        int h = Math.max(18, bar.height - 12);
        return new Rectangle(x, y, w, h);
    }

    public static int coreMenuButtonAt(int viewW, int viewH, int mouseX, int mouseY) {
        for (int i = 0; i < CORE_MENU_LABELS.length; i++) {
            Rectangle r = getCoreMenuButtonRect(viewW, viewH, i);
            if (r.contains(mouseX, mouseY)) return i;
        }
        return -1;
    }

    public static void drawCoreMenuBar(Graphics2D g2, GameContext ctx, int viewW, int viewH) {
        if (g2 == null || ctx == null) return;
        Rectangle bar = getCoreMenuBarRect(viewW, viewH);

        g2.setColor(new Color(0, 0, 0, 158));
        g2.fillRoundRect(bar.x, bar.y, bar.width, bar.height, 14, 14);
        g2.setColor(new Color(255, 255, 255, 95));
        g2.drawRoundRect(bar.x, bar.y, bar.width, bar.height, 14, 14);

        boolean[] open = {
                ctx.shopOpen,
                ctx.baseMenuOpen,
                ctx.mapOpen,
                ctx.powerManagementOpen,
                ctx.crewStationsOpen
        };
        boolean baseAvailable = EconomySystem.getDockedFriendlyBase(ctx) != null;
        boolean controlsDisabled = ctx.state == GameState.PAUSED || ctx.state == GameState.GAME_OVER;

        Font oldFont = g2.getFont();
        g2.setFont(new Font("Consolas", Font.BOLD, 12));
        FontMetrics fm = g2.getFontMetrics();

        for (int i = 0; i < CORE_MENU_LABELS.length; i++) {
            Rectangle br = getCoreMenuButtonRect(viewW, viewH, i);
            boolean disabled = controlsDisabled || (i == 1 && !baseAvailable);
            boolean active = open[i];

            Color fill;
            if (disabled) fill = new Color(60, 60, 65, 160);
            else if (active) fill = new Color(70, 145, 220, 185);
            else fill = new Color(28, 32, 40, 180);
            g2.setColor(fill);
            g2.fillRoundRect(br.x, br.y, br.width, br.height, 10, 10);

            if (disabled) g2.setColor(new Color(160, 160, 170, 130));
            else if (active) g2.setColor(new Color(215, 242, 255, 220));
            else g2.setColor(new Color(200, 220, 255, 180));
            g2.drawRoundRect(br.x, br.y, br.width, br.height, 10, 10);

            String label;
            if (br.width < 64) label = CORE_MENU_LABELS[i].substring(0, Math.min(2, CORE_MENU_LABELS[i].length()));
            else if (br.width < 96) label = CORE_MENU_LABELS[i];
            else label = CORE_MENU_LABELS[i] + " [" + CORE_MENU_HOTKEYS[i] + "]";
            int tx = br.x + (br.width - fm.stringWidth(label)) / 2;
            int ty = br.y + (br.height + fm.getAscent() - fm.getDescent()) / 2;
            if (disabled) g2.setColor(new Color(170, 170, 176, 155));
            else g2.setColor(new Color(240, 245, 255, active ? 240 : 210));
            g2.drawString(label, tx, ty);
        }

        g2.setFont(oldFont);
    }



    private static String fmt1(double v) {
        return String.format(java.util.Locale.US, "%.1f", v);
    }

    private static String signedPct(double mul) {
        double v = (mul - 1.0) * 100.0;
        if (!Double.isFinite(v)) v = 0.0;
        return String.format(Locale.US, "%+.0f%%", v);
    }

    private static Color mixColor(Color a, Color b, double t) {
        if (a == null) a = Color.WHITE;
        if (b == null) b = Color.WHITE;
        double k = Math.max(0.0, Math.min(1.0, t));
        int r = (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * k);
        int g = (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * k);
        int bl = (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * k);
        return new Color(MathUtil.clamp(r, 0, 255), MathUtil.clamp(g, 0, 255), MathUtil.clamp(bl, 0, 255));
    }

    private static Color shieldTeamColor(Ship ship) {
        Faction faction = (ship == null || ship.faction == null) ? Faction.ALLY : ship.faction;
        return switch (faction.teamId()) {
            case 1 -> new Color(255, 132, 132); // Team B / Enemy
            case 2 -> new Color(130, 255, 132); // Team C (Aegis Lattice)
            case 3 -> new Color(255, 212, 132); // Team D (Viper Barrage)
            default -> new Color(128, 206, 255); // Team A / Player / Ally
        };
    }

    private static Color shieldFaceColor(Ship ship, int face, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        Color base = shieldTeamColor(ship);
        Color faceTint = switch (face) {
            case Ship.SHIELD_FACE_FORE -> mixColor(base, Color.WHITE, 0.24);
            case Ship.SHIELD_FACE_LEFT -> base;
            case Ship.SHIELD_FACE_RIGHT -> base;
            case Ship.SHIELD_FACE_REAR -> mixColor(base, new Color(44, 50, 68), 0.20);
            default -> base;
        };
        return new Color(faceTint.getRed(), faceTint.getGreen(), faceTint.getBlue(), a);
    }

    private static String shieldFaceReadout(Ship ship) {
        if (ship == null || ship.shieldFaceCount() <= 0) return "N/A";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ship.shieldFaceCount(); i++) {
            if (i > 0) sb.append("  ");
            int cur = (int) Math.round(ship.shieldFaceValue(i));
            int max = (int) Math.round(ship.shieldFaceMax(i));
            sb.append(ship.shieldFaceName(i)).append(" ").append(cur).append("/").append(max);
        }
        return sb.toString();
    }

    private static void drawShieldArcSegment(Graphics2D g, double radius, double centerAngle, double span) {
        int steps = 18;
        double start = centerAngle - span * 0.5;
        Path2D.Double path = new Path2D.Double();
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double a = start + span * t;
            double px = Math.cos(a) * radius;
            double py = Math.sin(a) * radius;
            if (i == 0) path.moveTo(px, py);
            else path.lineTo(px, py);
        }
        g.draw(path);
    }

    private static void drawShieldArcBand(Graphics2D g, double innerRadius, double outerRadius, double centerAngle, double span) {
        if (g == null) return;
        if (!Double.isFinite(innerRadius) || !Double.isFinite(outerRadius)) return;
        if (outerRadius <= innerRadius || span <= 0.0) return;

        int steps = 24;
        double start = centerAngle - span * 0.5;
        Path2D.Double path = new Path2D.Double();
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double a = start + span * t;
            double x = Math.cos(a) * outerRadius;
            double y = Math.sin(a) * outerRadius;
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        for (int i = steps; i >= 0; i--) {
            double t = i / (double) steps;
            double a = start + span * t;
            double x = Math.cos(a) * innerRadius;
            double y = Math.sin(a) * innerRadius;
            path.lineTo(x, y);
        }
        path.closePath();
        g.fill(path);
    }

    private static double shieldFaceCenterAngle(Ship ship, int face) {
        double facing = (ship == null) ? 0.0 : ship.getShieldFacingAngle();
        return switch (face) {
            case Ship.SHIELD_FACE_FORE -> facing;
            case Ship.SHIELD_FACE_LEFT -> facing - Math.PI * 0.5;
            case Ship.SHIELD_FACE_RIGHT -> facing + Math.PI * 0.5;
            case Ship.SHIELD_FACE_REAR -> facing + Math.PI;
            default -> facing;
        };
    }

    private static void drawShipShieldFaces(Graphics2D g, Ship ship) {
        if (g == null || ship == null) return;
        if (isTinyStrikeCraft(ship.role)) return;
        if (!ship.shieldActive || ship.shieldMax <= 0.0 || ship.shield <= 0.0) return;
        if (!ship.hasRecentShieldImpactTelemetry()) return;

        double radius = shieldEnvelopeRadius(ship);
        double span = Math.toRadians(78.0);
        double pulse = 0.5 + 0.5 * Math.sin(System.nanoTime() * 1e-9 * 5.5);
        double fade = ship.recentShieldImpactTelemetryFraction();
        Stroke prevStroke = g.getStroke();
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        int impactedFace = ship.recentShieldImpactFace();
        for (int i = 0; i < ship.shieldFaceCount(); i++) {
            if (i != impactedFace) continue;
            // drawShip() already rotated the graphics context by ship.angle,
            // so convert world-space shield-facing angles into ship-local angles.
            double center = MathUtil.normalizeAngle(shieldFaceCenterAngle(ship, i) - ship.angle);
            double frac = ship.shieldFaceFraction(i);
            double energy = Math.max(0.0, Math.min(1.0, frac));
            Color base = shieldFaceColor(ship, i, 255);

            // Depleted face still shows a dim scaffold.
            g.setColor(withAlpha(base, (int) Math.round(18 + 38 * fade)));
            drawShieldArcBand(g, radius - 0.2, radius + 1.2, center, span + Math.toRadians(2.0));

            // Layered bands produce a faceted energy-field look.
            for (int layer = 0; layer < 3; layer++) {
                double t = layer / 2.0;
                double inner = radius + layer * 1.35;
                double outer = inner + 1.35 + (1.05 - t * 0.32) * (0.8 + energy * 1.6);
                int alpha = (int) Math.round((28 + energy * 92 + pulse * 20) * (1.0 - layer * 0.24) * fade);
                Color layerColor = mixColor(base, new Color(220, 246, 255), 0.15 + 0.20 * (1.0 - t));
                g.setColor(withAlpha(layerColor, alpha));
                drawShieldArcBand(g, inner, outer, center, span * (1.0 - layer * 0.05));
            }

            int edgeAlpha = (int) Math.round((52 + energy * 132 + pulse * 18) * fade);
            g.setColor(withAlpha(mixColor(base, Color.WHITE, 0.38), edgeAlpha));
            drawShieldArcSegment(g, radius + 3.8, center, span * 0.96);

            // Angled field struts for directional readability.
            double edgeA = span * 0.43;
            double r1 = radius + 0.4;
            double r2 = radius + 4.5;
            for (int side = -1; side <= 1; side += 2) {
                double a = center + edgeA * side;
                int sx = (int) Math.round(Math.cos(a) * r1);
                int sy = (int) Math.round(Math.sin(a) * r1);
                int ex = (int) Math.round(Math.cos(a) * r2);
                int ey = (int) Math.round(Math.sin(a) * r2);
                g.setColor(withAlpha(layerColorForFace(base), (int) Math.round((36 + energy * 82) * fade)));
                g.drawLine(sx, sy, ex, ey);
            }

            drawShieldFaceTelemetry(g, ship, i, center, radius + 10.0, fade, base);
        }

        g.setStroke(prevStroke);
    }

    private static void drawShieldFaceTelemetry(Graphics2D g, Ship ship, int face, double centerAngle,
                                                double radius, double fade, Color accent) {
        if (g == null || ship == null || face < 0) return;
        String text = ship.shieldFaceName(face) + " "
                + (int) Math.round(ship.shieldFaceValue(face)) + "/"
                + (int) Math.round(ship.shieldFaceMax(face));
        Font oldFont = g.getFont();
        g.setFont(new Font("Consolas", Font.BOLD, 10));
        FontMetrics fm = g.getFontMetrics();
        int textW = fm.stringWidth(text);
        int tx = (int) Math.round(Math.cos(centerAngle) * radius) - textW / 2;
        int ty = (int) Math.round(Math.sin(centerAngle) * radius);
        int padX = 4;
        int padY = 2;
        int boxX = tx - padX;
        int boxY = ty - fm.getAscent() + 1 - padY;
        int boxW = textW + padX * 2;
        int boxH = fm.getAscent() + fm.getDescent() + padY * 2;
        int fillAlpha = MathUtil.clamp((int) Math.round(118 * fade), 0, 255);
        int edgeAlpha = MathUtil.clamp((int) Math.round(182 * fade), 0, 255);
        g.setColor(new Color(8, 14, 24, fillAlpha));
        g.fillRoundRect(boxX, boxY, boxW, boxH, 8, 8);
        g.setColor(withAlpha(accent, edgeAlpha));
        g.drawRoundRect(boxX, boxY, boxW, boxH, 8, 8);
        g.setColor(new Color(240, 248, 255, MathUtil.clamp((int) Math.round(228 * fade), 0, 255)));
        g.drawString(text, tx, ty);
        g.setFont(oldFont);
    }

    private static double shieldEnvelopeRadius(Ship ship) {
        if (ship == null) return 21.8;
        double base = ship.radius + 5.8;
        ShipRole role = ship.role;
        if (role == null) return base;
        return switch (role) {
            case TRANSPORT, LIGHT_CRUISER, MEDIUM_CRUISER, CRUISER -> base + 2.0;
            case BATTLECRUISER, BATTLESHIP -> base + 3.6;
            case CARRIER, DRONE_CARRIER, DREADNOUGHT, SUPERSHIP, BASE -> base + 5.4;
            default -> base;
        };
    }

    private static Color layerColorForFace(Color base) {
        return mixColor(base, new Color(208, 242, 255), 0.42);
    }

    // Layered environment backgrounds with procedural fallback.
    public static void drawSpaceBackground(Graphics2D g2, double camX, double camY, int viewW, int viewH, long seed) {
        BufferedImage bgBase = EnvironmentSkinLibrary.backgroundBase();
        BufferedImage bgNebula = EnvironmentSkinLibrary.backgroundNebula();
        BufferedImage bgStars = EnvironmentSkinLibrary.backgroundStars();
        BufferedImage bgDust = EnvironmentSkinLibrary.backgroundDust();

        if (bgBase == null && bgNebula == null && bgStars == null && bgDust == null) {
            drawSpaceBackgroundFallback(g2, camX, camY, viewW, viewH, seed);
            return;
        }

        drawTiledParallaxLayer(g2, bgBase, camX, camY, viewW, viewH, 0.05, 1.00f);
        drawTiledParallaxLayer(g2, bgNebula, camX, camY, viewW, viewH, 0.10, 0.72f);
        drawTiledParallaxLayer(g2, bgStars, camX, camY, viewW, viewH, 0.16, 0.95f);
        drawTiledParallaxLayer(g2, bgDust, camX, camY, viewW, viewH, 0.24, 0.62f);
    }

    public static void drawShips(Graphics2D g2, List<Ship> ships) {
        for (Ship s : ships) {
            if (s.alive) drawShip(g2, s);
        }
    }

    // ------------------------------
    // Asteroids (obstacles/resources)
    // ------------------------------

    public static void drawAsteroids(Graphics2D g2, List<Asteroid> asteroids) {
        drawAsteroids(g2, asteroids, null);
    }

    public static void drawAsteroids(Graphics2D g2, List<Asteroid> asteroids, Player player) {
        if (asteroids == null) return;
        Asteroid promptAsteroid = findNearbyAsteroidPromptTarget(asteroids, player);
        for (Asteroid a : asteroids) {
            if (a == null) continue;

            BufferedImage skin = EnvironmentSkinLibrary.pickAsteroidSprite(a);
            if (skin != null) {
                drawAsteroidSprite(g2, a, skin);
                continue;
            }

            int r = (int) Math.round(a.radius);
            int x = (int) Math.round(a.x);
            int y = (int) Math.round(a.y);

            double frac = (a.oreMax <= 0) ? 0.0 : Math.max(0.0, Math.min(1.0, (double) a.ore / (double) a.oreMax));

            // Main body
            int baseA = 150;
            int shade = (int) Math.round(70 + 80 * (0.35 + 0.65 * frac));
            g2.setColor(new Color(shade, shade, shade, baseA));
            g2.fillOval(x - r, y - r, r * 2, r * 2);

            // Subtle rim
            g2.setColor(new Color(255, 255, 255, 28));
            g2.drawOval(x - r, y - r, r * 2, r * 2);

            // Ore glow
            if (a.ore > 0) {
                int ir = Math.max(6, (int) Math.round(r * 0.55));
                int alpha = (int) Math.round(30 + 120 * frac);
                g2.setColor(new Color(255, 220, 140, MathUtil.clamp(alpha, 0, 200)));
                g2.fillOval(x - ir, y - ir, ir * 2, ir * 2);

                // A little"twist" highlight
                double ang = a.spin;
                int hx = (int) Math.round(x + Math.cos(ang) * ir * 0.65);
                int hy = (int) Math.round(y + Math.sin(ang) * ir * 0.65);
                g2.setColor(new Color(255, 255, 255, MathUtil.clamp((int) (20 + 80 * frac), 0, 120)));
                g2.fillOval(hx - 3, hy - 3, 6, 6);
            }

            // Rich vein highlight
            if (a.rich) {
                int rr = (int) Math.round(r * 1.25);
                g2.setColor(new Color(255, 220, 120, 34));
                g2.drawOval(x - rr, y - rr, rr * 2, rr * 2);
                int rr2 = (int) Math.round(r * 1.45);
                g2.setColor(new Color(255, 255, 255, 18));
                g2.drawOval(x - rr2, y - rr2, rr2 * 2, rr2 * 2);
            }
        }
        drawAsteroidMinePrompt(g2, promptAsteroid);
    }

    public static void drawAsteroidDangerHeatmap(Graphics2D g2, List<Asteroid> asteroids) {
        if (g2 == null || asteroids == null || asteroids.isEmpty()) return;

        Stroke old = g2.getStroke();
        g2.setStroke(new BasicStroke(1.1f));
        for (Asteroid a : asteroids) {
            if (a == null) continue;
            int x = (int) Math.round(a.x);
            int y = (int) Math.round(a.y);

            double coll = a.collisionRadius();
            int cr = (int) Math.round(coll);
            g2.setColor(new Color(255, 80, 80, 130));
            g2.drawOval(x - cr, y - cr, cr * 2, cr * 2);

            double light = coll + BalanceConfig.ASTEROID_AVOID_CLEARANCE_BASE * BalanceConfig.asteroidAvoidanceClearanceScale(ShipRole.FIGHTER);
            double frig = coll + BalanceConfig.ASTEROID_AVOID_CLEARANCE_BASE * BalanceConfig.asteroidAvoidanceClearanceScale(ShipRole.FRIGATE);
            double cap = coll + BalanceConfig.ASTEROID_AVOID_CLEARANCE_BASE * BalanceConfig.asteroidAvoidanceClearanceScale(ShipRole.BATTLESHIP);

            int lr = (int) Math.round(light);
            int fr = (int) Math.round(frig);
            int car = (int) Math.round(cap);
            g2.setColor(new Color(255, 190, 80, 90));
            g2.drawOval(x - lr, y - lr, lr * 2, lr * 2);
            g2.setColor(new Color(255, 215, 120, 70));
            g2.drawOval(x - fr, y - fr, fr * 2, fr * 2);
            g2.setColor(new Color(255, 240, 170, 55));
            g2.drawOval(x - car, y - car, car * 2, car * 2);
        }
        g2.setStroke(old);
    }

    private static void drawSpaceBackgroundFallback(Graphics2D g2, double camX, double camY, int viewW, int viewH, long seed) {
        double px = camX * 0.20;
        double py = camY * 0.20;

        int tile = 256;
        int startX = (int) Math.floor(px / tile) - 1;
        int startY = (int) Math.floor(py / tile) - 1;
        int endX = (int) Math.floor((px + viewW) / tile) + 1;
        int endY = (int) Math.floor((py + viewH) / tile) + 1;

        for (int tx = startX; tx <= endX; tx++) {
            for (int ty = startY; ty <= endY; ty++) {
                long mix = seed;
                mix ^= (long) tx * 0x9E3779B97F4A7C15L;
                mix ^= (long) ty * 0xC2B2AE3D27D4EB4FL;
                mix ^= (mix >>> 33);
                mix *= 0xff51afd7ed558ccdL;
                mix ^= (mix >>> 33);

                Random r = new Random(mix);
                int stars = 10 + r.nextInt(10);
                for (int i = 0; i < stars; i++) {
                    int sx = tx * tile + r.nextInt(tile);
                    int sy = ty * tile + r.nextInt(tile);
                    int x = (int) Math.round(sx - px);
                    int y = (int) Math.round(sy - py);
                    int size = 1 + r.nextInt(2);
                    int a = 40 + r.nextInt(90);
                    g2.setColor(new Color(255, 255, 255, a));
                    g2.fillRect(x, y, size, size);
                }
            }
        }
    }

    private static void drawTiledParallaxLayer(Graphics2D g2, BufferedImage tile,
                                               double camX, double camY, int viewW, int viewH,
                                               double parallax, float alpha) {
        if (tile == null || alpha <= 0f) return;

        int tw = Math.max(1, tile.getWidth());
        int th = Math.max(1, tile.getHeight());
        double px = camX * parallax;
        double py = camY * parallax;
        int startX = (int) Math.floor(px / tw) - 1;
        int startY = (int) Math.floor(py / th) - 1;
        int endX = (int) Math.floor((px + viewW) / tw) + 1;
        int endY = (int) Math.floor((py + viewH) / th) + 1;

        Composite old = g2.getComposite();
        if (alpha < 0.999f) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, alpha))));
        }

        for (int tx = startX; tx <= endX; tx++) {
            for (int ty = startY; ty <= endY; ty++) {
                int x = (int) Math.round(tx * tw - px);
                int y = (int) Math.round(ty * th - py);
                g2.drawImage(tile, x, y, tw, th, null);
            }
        }

        g2.setComposite(old);
    }

    private static void drawAsteroidSprite(Graphics2D g2, Asteroid a, BufferedImage skin) {
        int x = (int) Math.round(a.x);
        int y = (int) Math.round(a.y);
        int draw = Math.max(14, (int) Math.round(a.radius * 3.0));

        Graphics2D ga = (Graphics2D) g2.create();
        ga.translate(x, y);
        ga.rotate(a.spin * 0.35);
        ga.drawImage(skin, -draw / 2, -draw / 2, draw, draw, null);

        double frac = (a.oreMax <= 0) ? 0.0 : Math.max(0.0, Math.min(1.0, (double) a.ore / (double) a.oreMax));
        if (a.rich && frac > 0.05) {
            int rr = Math.max(8, (int) Math.round(a.radius * 1.28));
            int alpha = MathUtil.clamp((int) Math.round(24 + 72 * frac), 0, 140);
            ga.setColor(new Color(255, 210, 120, alpha));
            ga.drawOval(-rr, -rr, rr * 2, rr * 2);
        }

        ga.dispose();
    }

    private static Asteroid findNearbyAsteroidPromptTarget(List<Asteroid> asteroids, Player player) {
        if (asteroids == null || asteroids.isEmpty() || player == null) return null;
        if (!player.alive || player.dying || player.hp <= 0) return null;
        double range = Math.max(150.0, player.miningRange + 48.0);
        double bestD2 = range * range;
        Asteroid best = null;
        for (Asteroid a : asteroids) {
            if (a == null || a.ore <= 0) continue;
            double d2 = GameMath.dist2(player.x, player.y, a.x, a.y);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = a;
            }
        }
        return best;
    }

    private static void drawAsteroidMinePrompt(Graphics2D g2, Asteroid asteroid) {
        if (g2 == null || asteroid == null) return;
        String label = "ORE [F]";
        Font oldFont = g2.getFont();
        Color oldColor = g2.getColor();
        g2.setFont(new Font("Consolas", Font.BOLD, 11));
        FontMetrics fm = g2.getFontMetrics();

        int tx = (int) Math.round(asteroid.x - fm.stringWidth(label) / 2.0);
        int ty = (int) Math.round(asteroid.y - asteroid.radius - 12.0);
        int pad = 6;
        int bw = fm.stringWidth(label) + pad * 2;
        int bh = 16;
        int bx = tx - pad;
        int by = ty - fm.getAscent() + 1;

        g2.setColor(new Color(8, 10, 16, 172));
        g2.fillRoundRect(bx, by, bw, bh, 10, 10);
        g2.setColor(new Color(168, 218, 255, 176));
        g2.drawRoundRect(bx, by, bw, bh, 10, 10);
        g2.setColor(new Color(255, 244, 170, 226));
        g2.drawString(label, tx, ty);

        g2.setFont(oldFont);
        g2.setColor(oldColor);
    }

    // ------------------------------
    // Salvage pickups (random events)
    // ------------------------------

    public static void drawSalvage(Graphics2D g2, List<Salvage> salvage) {
        if (salvage == null) return;
        for (Salvage s : salvage) {
            if (s == null || !s.alive()) continue;

            int x = (int) Math.round(s.x);
            int y = (int) Math.round(s.y);
            int r = (int) Math.round(s.radius);

            // Soft glow
            g2.setColor(new Color(255, 255, 255, 22));
            g2.fillOval(x - r * 2, y - r * 2, r * 4, r * 4);

            // Diamond "crate"
            Polygon p = new Polygon();
            p.addPoint(x, y - r);
            p.addPoint(x + r, y);
            p.addPoint(x, y + r);
            p.addPoint(x - r, y);

            int a = (int) Math.round(160 + 80 * Math.max(0.0, Math.min(1.0, s.life / 25.0)));
            g2.setColor(new Color(220, 240, 255, MathUtil.clamp(a, 0, 240)));
            g2.fillPolygon(p);

            g2.setColor(new Color(255, 255, 255, 60));
            g2.drawPolygon(p);

            // Tiny hint for valuable drops
            if (s.credits >= 500 || s.ore >= 80) {
                g2.setColor(new Color(255, 220, 120, 60));
                g2.drawOval(x - r - 6, y - r - 6, (r + 6) * 2, (r + 6) * 2);
            }
        }
    }


    public static void drawProjectiles(Graphics2D g2, List<Projectile> projectiles) {
        for (Projectile p : projectiles) {
            if (!p.alive) continue;

            if (p instanceof CIWSPellet pellet) {
                int r = (int) Math.round(Math.max(1.0, pellet.radius));
                int x = (int) Math.round(pellet.x);
                int y = (int) Math.round(pellet.y);
                Color core = mixColor(projectileCoreColor(pellet.faction), Color.WHITE, 0.42);
                Color trail = projectileTrailColor(pellet.faction);
                double speed = Math.hypot(pellet.vx, pellet.vy);
                double trailLen = Math.max(8.0, Math.min(22.0, 8.0 + speed * 0.16));
                double nx = Math.cos(pellet.angle);
                double ny = Math.sin(pellet.angle);

                BufferedImage skin = ProjectileSkinLibrary.getCiwsPelletSkin();
                if (skin != null) {
                    drawOrientedProjectileSkin(g2, skin, pellet.x, pellet.y, pellet.angle,
                            Math.max(7.0, r * 3.6), Math.max(3.0, r * 1.8), 0.95f);
                }

                Stroke old = g2.getStroke();
                g2.setStroke(new BasicStroke(Math.max(1.2f, r * 0.9f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(withAlpha(trail, 145));
                g2.drawLine(x, y,
                        (int) Math.round(pellet.x - nx * trailLen),
                        (int) Math.round(pellet.y - ny * trailLen));

                g2.setStroke(new BasicStroke(Math.max(1.0f, r * 0.55f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(255, 255, 255, 185));
                g2.drawLine(x, y,
                        (int) Math.round(pellet.x - nx * (trailLen * 0.55)),
                        (int) Math.round(pellet.y - ny * (trailLen * 0.55)));
                g2.setStroke(old);

                g2.setColor(withAlpha(core, 228));
                g2.fillOval(x - r, y - r, r * 2, r * 2);
                continue;
            }

            if (p instanceof PhaserBeam beam) {
                drawPhaserBeam(g2, beam);
                continue;
            }
            if (p instanceof PointDefenseLaser laser) {
                drawPointDefenseLaser(g2, laser);
                continue;
            }

            if (p instanceof Missile m) {
                drawMissile(g2, m);
            } else if (p instanceof SuperweaponShot ws) {
                int x = (int) Math.round(ws.x);
                int y = (int) Math.round(ws.y);
                double nx = Math.cos(ws.angle);
                double ny = Math.sin(ws.angle);
                Color beam = beamColorForFaction(ws.faction);
                Color hot = mixColor(beam, Color.WHITE, 0.76);
                double pulse = 0.5 + 0.5 * Math.sin(System.nanoTime() * 1e-9 * 9.5);

                int len = (int) Math.round(Math.max(30.0, ws.radius * 5.6));
                int tail = len / 2;
                int head = len / 2;

                int x1 = (int) Math.round(ws.x - nx * tail);
                int y1 = (int) Math.round(ws.y - ny * tail);
                int x2 = (int) Math.round(ws.x + nx * head);
                int y2 = (int) Math.round(ws.y + ny * head);

                BufferedImage skin = ProjectileSkinLibrary.getWaveShotSkin();
                if (skin != null) {
                    drawOrientedProjectileSkin(g2, skin, ws.x, ws.y, ws.angle,
                            Math.max(28.0, ws.radius * 5.8), Math.max(8.0, ws.radius * 2.8), 0.92f);
                }

                Stroke old = g2.getStroke();
                g2.setStroke(new BasicStroke((float) Math.max(6.0, ws.radius * 2.5), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(withAlpha(beam, (int) Math.round(115 + pulse * 28)));
                g2.drawLine(x1, y1, x2, y2);

                g2.setStroke(new BasicStroke((float) Math.max(3.2, ws.radius * 1.3), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(withAlpha(hot, 236));
                g2.drawLine(x1, y1, x2, y2);

                g2.setStroke(new BasicStroke((float) Math.max(1.4, ws.radius * 0.58), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(withAlpha(Color.WHITE, 190));
                g2.drawLine(x1, y1, x2, y2);

                int glow = (int) Math.round(Math.max(8.0, ws.radius * 1.6));
                g2.setColor(withAlpha(mixColor(beam, Color.WHITE, 0.26), (int) Math.round(150 + pulse * 30)));
                g2.fillOval(x - glow, y - glow, glow * 2, glow * 2);
                g2.setStroke(old);
            } else if (p instanceof EnergyBolt eb) {
                // Yamato 2199-style energy bolts (standard + BEAM_BOLT variant)
                int x = (int) Math.round(eb.x);
                int y = (int) Math.round(eb.y);

                double vx = eb.vx;
                double vy = eb.vy;
                double vlen = Math.hypot(vx, vy);
                double nx = (vlen > 1e-6) ? (vx / vlen) : Math.cos(eb.angle);
                double ny = (vlen > 1e-6) ? (vy / vlen) : Math.sin(eb.angle);
                Color base = eb.isBeamBolt()
                        ? mixColor(beamColorForFaction(eb.faction), new Color(135, 230, 255), 0.36)
                        : projectileCoreColor(eb.faction);
                Color glow = mixColor(base, Color.WHITE, eb.isBeamBolt() ? 0.42 : 0.30);

                int r = (int) Math.round(Math.max(2.0, eb.radius));
                if (eb.isBeamBolt()) r = (int) Math.round(Math.max(r, 4.0));

                BufferedImage skin = ProjectileSkinLibrary.getEnergyBoltSkin(eb.isBeamBolt());
                if (skin != null) {
                    drawOrientedProjectileSkin(g2, skin, eb.x, eb.y, Math.atan2(ny, nx),
                            Math.max(14.0, r * (eb.isBeamBolt() ? 4.4 : 3.6)),
                            Math.max(6.0, r * 1.8), 0.90f);
                }

                Stroke old = g2.getStroke();

                // soft outer glow line
                g2.setStroke(new BasicStroke(r * 1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(withAlpha(glow, eb.isBeamBolt() ? 90 : 74));
                int gx1 = (int) Math.round(eb.x - nx * (r * 2.6));
                int gy1 = (int) Math.round(eb.y - ny * (r * 2.6));
                int gx2 = (int) Math.round(eb.x + nx * (r * 1.4));
                int gy2 = (int) Math.round(eb.y + ny * (r * 1.4));
                g2.drawLine(gx1, gy1, gx2, gy2);

                // bright core line
                g2.setStroke(new BasicStroke(r * 0.75f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(withAlpha(mixColor(base, Color.WHITE, 0.72), eb.isBeamBolt() ? 238 : 224));
                g2.drawLine(gx1, gy1, gx2, gy2);

                // end-cap flare
                int fx = (int) Math.round(eb.x + nx * (r * 2.0));
                int fy = (int) Math.round(eb.y + ny * (r * 2.0));
                g2.setColor(withAlpha(glow, eb.isBeamBolt() ? 210 : 182));
                g2.fillOval(fx - r, fy - r, r * 2, r * 2);

                // subtle trailing segments (motion blur)
                g2.setStroke(new BasicStroke(r * 0.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(withAlpha(glow, 82));
                for (int i = 1; i <= 2; i++) {
                    double t = r * (3.0 + i * 2.0);
                    int tx1 = (int) Math.round(eb.x - nx * (r * 1.6 + t));
                    int ty1 = (int) Math.round(eb.y - ny * (r * 1.6 + t));
                    int tx2 = (int) Math.round(eb.x - nx * (r * 0.4 + t));
                    int ty2 = (int) Math.round(eb.y - ny * (r * 0.4 + t));
                    g2.drawLine(tx1, ty1, tx2, ty2);
                }

                g2.setStroke(old);
            } else {
                // Bullet / generic projectile with a small motion trail
                int r = (int) Math.round(Math.max(1.0, p.radius));
                int x = (int) Math.round(p.x);
                int y = (int) Math.round(p.y);
                double speed = Math.hypot(p.vx, p.vy);
                double nx = (speed > 1e-6) ? p.vx / speed : 1.0;
                double ny = (speed > 1e-6) ? p.vy / speed : 0.0;
                double trailLen = Math.max(6.0, Math.min(28.0, 7.0 + speed * 0.15));
                Color trail = projectileTrailColor(p.faction);
                Color core = projectileCoreColor(p.faction);

                BufferedImage skin = ProjectileSkinLibrary.getBulletSkin();
                if (skin != null) {
                    drawOrientedProjectileSkin(g2, skin, p.x, p.y, Math.atan2(ny, nx),
                            Math.max(7.0, r * 2.7), Math.max(3.0, r * 1.8), 0.9f);
                }

                int tx = (int) Math.round(p.x - nx * trailLen);
                int ty = (int) Math.round(p.y - ny * trailLen);
                int tx2 = (int) Math.round(p.x - nx * (trailLen * 0.56));
                int ty2 = (int) Math.round(p.y - ny * (trailLen * 0.56));

                Stroke old = g2.getStroke();
                g2.setStroke(new BasicStroke(Math.max(1.1f, r * 0.86f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(withAlpha(trail, 132));
                g2.drawLine(tx, ty, x, y);

                g2.setStroke(new BasicStroke(Math.max(1.0f, r * 0.50f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(255, 255, 255, 172));
                g2.drawLine(tx2, ty2, x, y);
                g2.setStroke(old);

                g2.setColor(withAlpha(core, 224));
                g2.fillOval(x - r, y - r, r * 2, r * 2);
            }
        }
    }

    private static void drawPhaserBeam(Graphics2D g2, PhaserBeam beam) {
        if (g2 == null || beam == null || !beam.alive) return;

        double sx = beam.startX();
        double sy = beam.startY();
        double ex = beam.endX();
        double ey = beam.endY();
        Color base = beamColorForFaction(beam.faction);
        Color hot = mixColor(base, Color.WHITE, 0.72);
        double pulse = 0.5 + 0.5 * Math.sin(System.nanoTime() * 1e-9 * 10.0);
        float width = (float) Math.max(2.2, beam.width * (0.90 + 0.16 * pulse));

        Stroke old = g2.getStroke();

        g2.setStroke(new BasicStroke(width * 2.15f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(withAlpha(base, (int) Math.round(56 + pulse * 28)));
        g2.drawLine((int) Math.round(sx), (int) Math.round(sy), (int) Math.round(ex), (int) Math.round(ey));

        g2.setStroke(new BasicStroke(width * 1.05f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(withAlpha(hot, 210));
        g2.drawLine((int) Math.round(sx), (int) Math.round(sy), (int) Math.round(ex), (int) Math.round(ey));

        g2.setStroke(new BasicStroke(Math.max(1.1f, width * 0.40f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(withAlpha(Color.WHITE, 165));
        g2.drawLine((int) Math.round(sx), (int) Math.round(sy), (int) Math.round(ex), (int) Math.round(ey));

        int glowR = (int) Math.round(Math.max(5.0, beam.width * 1.3));
        g2.setColor(withAlpha(base, 150));
        g2.fillOval((int) Math.round(sx) - glowR, (int) Math.round(sy) - glowR, glowR * 2, glowR * 2);
        g2.setColor(withAlpha(hot, 126));
        g2.fillOval((int) Math.round(ex) - glowR, (int) Math.round(ey) - glowR, glowR * 2, glowR * 2);

        g2.setStroke(old);
    }

    private static void drawPointDefenseLaser(Graphics2D g2, PointDefenseLaser laser) {
        if (g2 == null || laser == null || !laser.alive) return;

        double sx = laser.startX();
        double sy = laser.startY();
        double ex = laser.endX;
        double ey = laser.endY;

        Color base = mixColor(beamColorForFaction(laser.faction), new Color(130, 245, 210), 0.34);
        Color hot = mixColor(base, Color.WHITE, 0.78);
        float width = (float) Math.max(1.1, laser.width);

        Stroke old = g2.getStroke();

        g2.setStroke(new BasicStroke(width * 2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(withAlpha(base, 102));
        g2.drawLine((int) Math.round(sx), (int) Math.round(sy), (int) Math.round(ex), (int) Math.round(ey));

        g2.setStroke(new BasicStroke(Math.max(1.0f, width * 0.85f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(withAlpha(hot, 212));
        g2.drawLine((int) Math.round(sx), (int) Math.round(sy), (int) Math.round(ex), (int) Math.round(ey));

        int r = (int) Math.round(Math.max(2.0, laser.width * 1.5));
        g2.setColor(withAlpha(hot, 178));
        g2.fillOval((int) Math.round(ex) - r, (int) Math.round(ey) - r, r * 2, r * 2);

        g2.setStroke(old);
    }

    public static void drawSuperweaponAimCue(Graphics2D g2, Player player, double cursorWorldX, double cursorWorldY) {
        if (g2 == null || player == null) return;
        if (!player.alive || player.dying || player.hp <= 0) return;
        if (!player.hasSuperweapon) return;
        if (!player.isSuperweaponCharging()) return;

        double aim = player.getSuperweaponAimAngle();
        double len = 2200.0;
        double sx = player.x + Math.cos(aim) * (player.radius + 10.0);
        double sy = player.y + Math.sin(aim) * (player.radius + 10.0);
        double ex = sx + Math.cos(aim) * len;
        double ey = sy + Math.sin(aim) * len;

        float chargeFrac = (float) Math.max(0.0, Math.min(1.0, player.getSuperweaponChargeProgress()));
        boolean charging = player.isSuperweaponCharging();
        double pulse = 0.5 + 0.5 * Math.sin(System.nanoTime() * 1e-9 * 8.0);

        Stroke oldStroke = g2.getStroke();
        int warnAlpha = charging ? (int) Math.round(120 + 95 * Math.max(chargeFrac, pulse)) : 72;

        g2.setStroke(new BasicStroke(charging ? (7.2f + chargeFrac * 4.0f) : 5.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(255, 32, 32, MathUtil.clamp(warnAlpha, 40, 230)));
        g2.drawLine((int) Math.round(sx), (int) Math.round(sy), (int) Math.round(ex), (int) Math.round(ey));

        g2.setStroke(new BasicStroke(charging ? 2.8f : 2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(255, 210, 210, charging ? 200 : 130));
        g2.drawLine((int) Math.round(sx), (int) Math.round(sy), (int) Math.round(ex), (int) Math.round(ey));

        if (Double.isFinite(cursorWorldX) && Double.isFinite(cursorWorldY)) {
            int cx = (int) Math.round(cursorWorldX);
            int cy = (int) Math.round(cursorWorldY);
            int r = charging ? 28 : 22;
            int r2 = charging ? 44 : 34;
            g2.setColor(new Color(255, 64, 64, charging ? 215 : 160));
            g2.drawOval(cx - r, cy - r, r * 2, r * 2);
            g2.setColor(new Color(255, 140, 140, charging ? 180 : 120));
            g2.drawOval(cx - r2, cy - r2, r2 * 2, r2 * 2);
            int tick = charging ? 20 : 14;
            g2.drawLine(cx - tick, cy, cx - 5, cy);
            g2.drawLine(cx + 5, cy, cx + tick, cy);
            g2.drawLine(cx, cy - tick, cx, cy - 5);
            g2.drawLine(cx, cy + 5, cx, cy + tick);
        }

        g2.setStroke(oldStroke);
    }

    public static void drawNpcSuperweaponAimCues(Graphics2D g2, List<Ship> ships, Ship player) {
        if (g2 == null || ships == null || ships.isEmpty()) return;
        for (Ship ship : ships) {
            if (ship == null || ship == player) continue;
            if (!ship.alive || ship.dying || ship.hp <= 0) continue;
            if (ship.role != ShipRole.SUPERSHIP) continue;
            if (!ship.hasSuperweapon || !ship.isSuperweaponCharging()) continue;
            drawNpcSuperweaponAimCue(g2, ship);
        }
    }

    private static void drawNpcSuperweaponAimCue(Graphics2D g2, Ship ship) {
        if (g2 == null || ship == null) return;
        double aim = ship.getSuperweaponAimAngle();
        double len = npcSuperweaponCueLength(ship);
        double sx = ship.x + Math.cos(aim) * (ship.radius + 10.0);
        double sy = ship.y + Math.sin(aim) * (ship.radius + 10.0);
        double ex = sx + Math.cos(aim) * len;
        double ey = sy + Math.sin(aim) * len;

        float chargeFrac = (float) Math.max(0.0, Math.min(1.0, ship.getSuperweaponChargeProgress()));
        double pulse = 0.5 + 0.5 * Math.sin(System.nanoTime() * 1e-9 * 8.6 + ship.id * 0.17);

        Color base = npcSuperweaponCueColor(ship.faction);
        Color hot = mixColor(base, Color.WHITE, 0.58);

        Stroke oldStroke = g2.getStroke();
        g2.setStroke(new BasicStroke(6.2f + chargeFrac * 3.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(withAlpha(base, (int) Math.round(88 + 78 * Math.max(chargeFrac, pulse))));
        g2.drawLine((int) Math.round(sx), (int) Math.round(sy), (int) Math.round(ex), (int) Math.round(ey));

        g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(withAlpha(hot, 196));
        g2.drawLine((int) Math.round(sx), (int) Math.round(sy), (int) Math.round(ex), (int) Math.round(ey));

        int r = (int) Math.round(12 + 10 * Math.max(chargeFrac, pulse));
        g2.setColor(withAlpha(base, 148));
        g2.drawOval((int) Math.round(ex) - r, (int) Math.round(ey) - r, r * 2, r * 2);

        g2.setStroke(oldStroke);
    }

    private static double npcSuperweaponCueLength(Ship ship) {
        if (ship == null) return 2200.0;
        if (ship.superweaponPattern == Ship.SuperweaponPattern.DIRECT_BEAM) {
            return MathUtil.clamp(ship.superweaponSpeed * 0.96, 760.0, 1760.0);
        }
        return 2200.0;
    }

    private static Color npcSuperweaponCueColor(Faction faction) {
        if (faction == null) return new Color(255, 120, 120);
        return switch (faction) {
            case ALLY, PLAYER -> new Color(130, 220, 255);
            case ENEMY -> new Color(255, 96, 96);
            case TEAM_C -> new Color(154, 255, 138);
            case TEAM_D -> new Color(255, 198, 126);
        };
    }

    private static void drawMissile(Graphics2D g2, Missile m) {
        BufferedImage skin = ProjectileSkinLibrary.getMissileSkin();
        if (skin != null) {
            drawMissileSkin(g2, m, skin);
        } else {
            drawMissileFallback(g2, m);
        }

        double nx = Math.cos(m.angle);
        double ny = Math.sin(m.angle);
        double tailOffset = m.radius * 1.1;
        double trailLen = Math.max(12.0, m.radius * 4.8);

        int x1 = (int) Math.round(m.x - nx * tailOffset);
        int y1 = (int) Math.round(m.y - ny * tailOffset);
        int x2 = (int) Math.round(m.x - nx * (tailOffset + trailLen));
        int y2 = (int) Math.round(m.y - ny * (tailOffset + trailLen));

        Color trail = missileExhaustColor(m.faction);
        g2.setColor(new Color(trail.getRed(), trail.getGreen(), trail.getBlue(), 120));
        g2.drawLine(x1, y1, x2, y2);
    }

    private static void drawMissileSkin(Graphics2D g2, Missile m, BufferedImage skin) {
        double len = Math.max(16.0, m.radius * 3.8);
        double width = Math.max(6.0, m.radius * 1.8);
        int drawW = (int) Math.round(len);
        int drawH = (int) Math.round(width);

        Graphics2D gx = (Graphics2D) g2.create();
        gx.translate(m.x, m.y);
        gx.rotate(m.angle);
        gx.drawImage(skin, -drawW / 2, -drawH / 2, drawW, drawH, null);

        Color stripe = missileStripeColor(m.faction);
        int bandW = Math.max(2, (int) Math.round(drawW * 0.12));
        int bandH = Math.max(3, (int) Math.round(drawH * 0.64));
        int bandX = (int) Math.round(-drawW * 0.10);
        gx.setColor(new Color(stripe.getRed(), stripe.getGreen(), stripe.getBlue(), 170));
        gx.fillRoundRect(bandX, -bandH / 2, bandW, bandH, bandW, bandW);

        int flare = Math.max(2, (int) Math.round(drawH * 0.34));
        int flareX = (int) Math.round(drawW * 0.30);
        gx.setColor(new Color(255, 250, 220, 170));
        gx.fillOval(flareX, -flare / 2, flare, flare);
        gx.dispose();
    }

    private static void drawMissileFallback(Graphics2D g2, Missile m) {
        double len = Math.max(16.0, m.radius * 3.8);
        double width = Math.max(6.0, m.radius * 1.8);
        int hw = (int) Math.round(width * 0.5);
        int hl = (int) Math.round(len * 0.5);
        int nose = (int) Math.round(len * 0.22);
        int tail = (int) Math.round(len * 0.20);

        Graphics2D gx = (Graphics2D) g2.create();
        gx.translate(m.x, m.y);
        gx.rotate(m.angle);

        Polygon body = new Polygon();
        body.addPoint(-hl + tail, -hw);
        body.addPoint(hl - nose, -hw);
        body.addPoint(hl, 0);
        body.addPoint(hl - nose, hw);
        body.addPoint(-hl + tail, hw);
        body.addPoint(-hl, hw / 2);
        body.addPoint(-hl, -hw / 2);
        gx.setColor(new Color(176, 192, 208, 230));
        gx.fillPolygon(body);
        gx.setColor(new Color(255, 255, 255, 70));
        gx.drawPolygon(body);

        Color stripe = missileStripeColor(m.faction);
        int bandW = Math.max(2, (int) Math.round(len * 0.12));
        int bandH = Math.max(3, (int) Math.round(width * 0.64));
        int bandX = (int) Math.round(-len * 0.08);
        gx.setColor(new Color(stripe.getRed(), stripe.getGreen(), stripe.getBlue(), 170));
        gx.fillRoundRect(bandX, -bandH / 2, bandW, bandH, bandW, bandW);

        gx.dispose();
    }

    private static Color missileStripeColor(Faction faction) {
        if (faction == null) return new Color(110, 220, 255);
        return switch (faction) {
            case PLAYER, ALLY -> new Color(110, 220, 255);
            case ENEMY -> new Color(255, 122, 94);
            case TEAM_C -> new Color(146, 255, 118);
            case TEAM_D -> new Color(255, 186, 92);
        };
    }

    private static Color missileExhaustColor(Faction faction) {
        if (faction == null) return new Color(255, 186, 120);
        return switch (faction) {
            case PLAYER, ALLY -> new Color(130, 226, 255);
            case ENEMY -> new Color(255, 170, 112);
            case TEAM_C -> new Color(164, 255, 140);
            case TEAM_D -> new Color(255, 210, 128);
        };
    }

    private static Color projectileCoreColor(Faction faction) {
        if (faction == null) return new Color(255, 232, 162);
        return switch (faction) {
            case PLAYER, ALLY -> new Color(180, 232, 255);
            case ENEMY -> new Color(255, 188, 142);
            case TEAM_C -> new Color(190, 255, 172);
            case TEAM_D -> new Color(255, 220, 146);
        };
    }

    private static Color projectileTrailColor(Faction faction) {
        if (faction == null) return new Color(255, 202, 130);
        return switch (faction) {
            case PLAYER, ALLY -> new Color(132, 214, 255);
            case ENEMY -> new Color(255, 150, 110);
            case TEAM_C -> new Color(136, 240, 112);
            case TEAM_D -> new Color(255, 194, 116);
        };
    }

    private static Color beamColorForFaction(Faction faction) {
        if (faction == null) return new Color(125, 226, 255);
        return switch (faction) {
            case PLAYER, ALLY -> new Color(110, 225, 255);
            case ENEMY -> new Color(255, 122, 96);
            case TEAM_C -> new Color(154, 255, 138);
            case TEAM_D -> new Color(255, 206, 118);
        };
    }

    private static void drawOrientedProjectileSkin(Graphics2D g2, BufferedImage skin, double x, double y, double angle,
                                                   double length, double width, float alpha) {
        if (g2 == null || skin == null) return;
        int drawW = (int) Math.round(Math.max(2.0, length));
        int drawH = (int) Math.round(Math.max(2.0, width));
        Graphics2D gx = (Graphics2D) g2.create();
        gx.translate(x, y);
        gx.rotate(angle);
        if (alpha < 0.999f) {
            float a = Math.max(0.0f, Math.min(1.0f, alpha));
            gx.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
        }
        gx.drawImage(skin, -drawW / 2, -drawH / 2, drawW, drawH, null);
        gx.dispose();
    }

    public static void drawHUD(Graphics2D g2, Player player, int credits, int hangarTier, boolean dockedAtBase, boolean shopOpen, boolean autoLock, Ship lockedTarget,
                               int playerWingActive, int playerWingCap, int lockedWingActive, int lockedWingCap,
                               boolean resourceRush, int allyOre, int enemyOre, int goal, String gameOverText,
                               String objectiveTitle, String objectiveDetail,
                               String eventBanner, double eventBannerT, double orePriceMul, double orePriceT, double miningMul, double miningT,
                               double camX, double camY, int viewW, int viewH, double zoom, String stationStatus,
                               GameContext ctx, GameContext.HudDetail hudDetail, String contextHint, String overlayStatus) {
        XrayStackLayout xrayLayout = computeXrayStackLayout(player, lockedTarget, shopOpen, viewW, viewH);
        GameContext.HudDetail detail = (hudDetail == null) ? GameContext.HudDetail.FULL : hudDetail;

        int leftX = 14;
        int topY = 16;
        int leftW = (xrayLayout != null)
                ? Math.max(240, Math.min(416, xrayLayout.panelX - leftX - 18))
                : Math.max(320, Math.min(430, viewW / 3));
        leftW = Math.max(240, Math.min(leftW, viewW - 28));

        int cardY = topY;
        cardY += drawCommandOverviewCard(g2, player, credits, hangarTier, dockedAtBase,
                resourceRush, allyOre, enemyOre, goal, objectiveTitle, objectiveDetail,
                orePriceMul, orePriceT, miningMul, miningT, gameOverText,
                leftX, cardY, leftW, detail, ctx);
        cardY += 10;
        cardY += drawActionStripCard(g2, player, detail, leftX, cardY, leftW);
        cardY += 10;
        drawShipSystemsCard(g2, player, lockedTarget, autoLock, playerWingActive, playerWingCap,
                stationStatus, overlayStatus, contextHint, leftX, cardY, leftW, detail);

        if (!resourceRush && gameOverText != null && !gameOverText.isBlank()) {
            String msg = gameOverText;
            g2.setFont(new Font("Consolas", Font.BOLD, 22));
            g2.setColor(new Color(255, 255, 255, 220));
            FontMetrics fm = g2.getFontMetrics();
            int tx = (g2.getClipBounds().width - fm.stringWidth(msg)) / 2;
            g2.drawString(msg, Math.max(10, tx), 52);
            g2.setFont(new Font("Consolas", Font.PLAIN, 14));
            g2.setColor(new Color(255, 255, 255, 220));
        }

        if (lockedTarget != null && lockedTarget.alive) {
            drawOffscreenTargetIndicator(g2, lockedTarget, camX, camY, viewW, viewH, zoom);
        }
        // Top-center event banner
        if (eventBanner != null && !eventBanner.isBlank() && eventBannerT > 0) {
            int bw = 720;
            int bh = 34;
            int bx = (g2.getClipBounds().width - bw) / 2;
            int by = 10;

            int a = (int) Math.round(60 + 140 * Math.max(0.0, Math.min(1.0, eventBannerT / 3.0)));
            g2.setColor(new Color(0, 0, 0, MathUtil.clamp(a, 0, 190)));
            g2.fillRoundRect(bx, by, bw, bh, 14, 14);
            g2.setColor(new Color(255, 255, 255, 210));
            g2.setFont(new Font("Consolas", Font.BOLD, 15));
            FontMetrics fm = g2.getFontMetrics();
            int tx = bx + (bw - fm.stringWidth(eventBanner)) / 2;
            int ty = by + 22;
            g2.drawString(eventBanner, tx, ty);

            // restore
            g2.setFont(new Font("Consolas", Font.PLAIN, 14));
            g2.setColor(new Color(255, 255, 255, 220));
        }

        drawLockedTargetXrayHud(g2, ctx, player, lockedTarget, shopOpen, viewW, viewH);
        drawBottomCombatVitals(g2, player, lockedTarget, xrayLayout, viewW, viewH);
        drawCursorWeaponHints(g2, ctx, player, camX, camY, zoom, viewW, viewH);



        if (shopOpen) {
            drawShopOverlay(g2, player, credits, hangarTier);
        }
    }

    private static int drawCommandOverviewCard(Graphics2D g2, Player player, int credits, int hangarTier, boolean dockedAtBase,
                                               boolean resourceRush, int allyOre, int enemyOre, int goal,
                                               String objectiveTitle, String objectiveDetail,
                                               double orePriceMul, double orePriceT, double miningMul, double miningT,
                                               String gameOverText, int x, int y, int w,
                                               GameContext.HudDetail detail, GameContext ctx) {
        if (g2 == null || player == null) return 0;

        Font oldFont = g2.getFont();
        Color oldColor = g2.getColor();
        Font bodyFont = new Font("Consolas", Font.PLAIN, 13);
        FontMetrics bodyFm = g2.getFontMetrics(bodyFont);
        int contentW = Math.max(220, w - 24);

        List<String> objectiveLines = wrapHudText(bodyFm,
                (objectiveDetail == null || objectiveDetail.isBlank()) ? "Free navigation." : objectiveDetail,
                contentW);

        ArrayList<String> statusLines = new ArrayList<>();
        statusLines.add("Mode: " + ((ctx == null || ctx.config == null) ? "Unknown" : ctx.config.mode.toString())
                + "   Hangar Tier: " + hangarTier);
        if (player.cargoMax > 0) {
            statusLines.add("Cargo: " + player.cargo + " / " + player.cargoMax
                    + (dockedAtBase ? "   Docked: yes" : "   Docked: no"));
        }
        if (resourceRush) {
            statusLines.add("Race: ally " + allyOre + "   enemy " + enemyOre + "   goal " + goal);
        }
        if (Math.abs(orePriceMul - 1.0) > 0.01 && orePriceT > 0.0) {
            statusLines.add("Ore price x" + fmt1(orePriceMul) + "   " + (int) Math.ceil(orePriceT) + "s remaining");
        }
        if (Math.abs(miningMul - 1.0) > 0.01 && miningT > 0.0) {
            statusLines.add("Mining x" + fmt1(miningMul) + "   " + (int) Math.ceil(miningT) + "s remaining");
        }
        if (gameOverText != null && !gameOverText.isBlank() && resourceRush) {
            statusLines.add("Status: " + gameOverText);
        }

        int h = 72 + objectiveLines.size() * 15 + statusLines.size() * 15;
        if (detail == GameContext.HudDetail.MINIMAL) {
            h -= Math.max(0, (statusLines.size() - 2) * 15);
        }

        drawHudPanelFrame(g2, x, y, w, h, "COMMAND", factionHudColor(player.faction, 210));

        int titleY = y + 34;
        g2.setFont(new Font("Consolas", Font.BOLD, 18));
        g2.setColor(new Color(244, 248, 255, 235));
        String shipLabel = (player.role == null) ? "COMMAND SHIP" : player.role.name().replace('_', ' ');
        g2.drawString(shipLabel, x + 12, titleY);

        String creditLabel = "CREDITS " + credits;
        FontMetrics headerFm = g2.getFontMetrics();
        g2.setFont(new Font("Consolas", Font.BOLD, 14));
        FontMetrics creditFm = g2.getFontMetrics();
        g2.setColor(new Color(150, 214, 255, 225));
        g2.drawString(creditLabel, x + w - 12 - creditFm.stringWidth(creditLabel), titleY);

        int rowY = y + 56;
        if (objectiveTitle != null && !objectiveTitle.isBlank()) {
            g2.setFont(new Font("Consolas", Font.BOLD, 13));
            g2.setColor(new Color(255, 226, 154, 230));
            g2.drawString(objectiveTitle, x + 12, rowY);
            rowY += 17;
        }

        g2.setFont(bodyFont);
        g2.setColor(new Color(222, 234, 246, 208));
        for (String line : objectiveLines) {
            g2.drawString(line, x + 12, rowY);
            rowY += 15;
        }

        g2.setColor(new Color(255, 255, 255, 58));
        g2.drawLine(x + 12, rowY + 2, x + w - 12, rowY + 2);
        rowY += 18;

        int maxStatusLines = (detail == GameContext.HudDetail.MINIMAL) ? Math.min(2, statusLines.size()) : statusLines.size();
        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        for (int i = 0; i < maxStatusLines; i++) {
            String line = statusLines.get(i);
            g2.setColor(line.startsWith("Status:")
                    ? new Color(255, 196, 148, 226)
                    : new Color(190, 214, 236, 198));
            g2.drawString(line, x + 12, rowY);
            rowY += 15;
        }

        g2.setFont(oldFont);
        g2.setColor(oldColor);
        return h;
    }

    private static int drawActionStripCard(Graphics2D g2, Player player, GameContext.HudDetail detail, int x, int y, int w) {
        if (g2 == null || player == null) return 0;
        List<String> chips = buildActionStripLabels(player, detail);
        if (chips.isEmpty()) return 0;

        Font oldFont = g2.getFont();
        Color oldColor = g2.getColor();
        Font chipFont = new Font("Consolas", Font.BOLD, 11);
        g2.setFont(chipFont);
        FontMetrics fm = g2.getFontMetrics();

        int chipX = x + 12;
        int chipY = y + 34;
        int lineHeight = 28;
        int chipH = 18;
        int maxX = x + w - 12;
        int rows = 1;

        int panelH = 60;
        drawHudPanelFrame(g2, x, y, w, panelH, "ACTION STRIP", new Color(132, 196, 255, 210));
        for (String chip : chips) {
            int chipW = fm.stringWidth(chip) + 14;
            if (chipX + chipW > maxX) {
                chipX = x + 12;
                chipY += lineHeight;
                rows++;
            }
            drawHudStatusChip(g2, chip, chipX, chipY - 12, chipW, chipH, new Color(125, 190, 255, 210), false);
            chipX += chipW + 8;
        }
        if (rows > 1) {
            panelH = 60 + (rows - 1) * 28;
            drawHudPanelFrame(g2, x, y, w, panelH, "ACTION STRIP", new Color(132, 196, 255, 210));
            chipX = x + 12;
            chipY = y + 34;
            for (String chip : chips) {
                int chipW = fm.stringWidth(chip) + 14;
                if (chipX + chipW > maxX) {
                    chipX = x + 12;
                    chipY += lineHeight;
                }
                drawHudStatusChip(g2, chip, chipX, chipY - 12, chipW, chipH, new Color(125, 190, 255, 210), false);
                chipX += chipW + 8;
            }
        }

        g2.setFont(oldFont);
        g2.setColor(oldColor);
        return panelH;
    }

    private static void drawShipSystemsCard(Graphics2D g2, Player player, Ship lockedTarget, boolean autoLock,
                                            int playerWingActive, int playerWingCap, String stationStatus,
                                            String overlayStatus, String contextHint,
                                            int x, int y, int w, GameContext.HudDetail detail) {
        if (g2 == null || player == null) return;

        Font oldFont = g2.getFont();
        Color oldColor = g2.getColor();
        Font bodyFont = new Font("Consolas", Font.PLAIN, 12);
        FontMetrics bodyFm = g2.getFontMetrics(bodyFont);
        int contentW = Math.max(220, w - 24);

        ArrayList<String> noteLines = new ArrayList<>();
        String overload = player.isOverloadActive()
                ? "Overload " + player.overloadBus().name() + " heat " + (int) Math.round(player.overloadHeat() * 100.0) + "%"
                : "Overload standby   cd " + (int) Math.ceil(player.overloadCooldownRemaining()) + "s";
        String thrust = player.isEmergencyThrustActive()
                ? "Emergency thrust active   heat " + (int) Math.round(player.emergencyThrustHeat() * 100.0) + "%"
                : "Emergency thrust standby   cd " + (int) Math.ceil(player.emergencyThrustCooldownRemaining()) + "s";
        noteLines.add(overload);
        noteLines.add(thrust);
        if (stationStatus != null && !stationStatus.isBlank()) noteLines.add(stationStatus);
        if (overlayStatus != null && !overlayStatus.isBlank()) noteLines.add(overlayStatus);
        if (playerWingCap > 0) {
            noteLines.add("Wing " + playerWingActive + "/" + playerWingCap
                    + "   " + player.carrierCommandMode.name()
                    + "   auto " + (player.carrierAutoLaunch ? "ON" : "OFF"));
        }
        if (lockedTarget != null && lockedTarget.alive && !lockedTarget.dying && lockedTarget.hp > 0) {
            int dist = (int) Math.round(Math.hypot(lockedTarget.x - player.x, lockedTarget.y - player.y));
            noteLines.add("Lock: " + lockedTarget.name + "   D " + dist);
            String counter = EnemyArchetypeIntel.counterHint(lockedTarget.role);
            if (detail == GameContext.HudDetail.FULL && counter != null && !counter.isBlank()) {
                noteLines.addAll(wrapHudText(bodyFm, "Counter: " + counter, contentW));
            }
        }
        if (contextHint != null && !contextHint.isBlank()) {
            noteLines.addAll(wrapHudText(bodyFm, "Hint: " + contextHint, contentW));
        }

        int h = 134 + noteLines.size() * 15;
        drawHudPanelFrame(g2, x, y, w, h, "SHIP STATUS", factionHudColor(player.faction, 210));

        int chipY = y + 32;
        int chipX = x + 12;
        chipX = drawHudChipAuto(g2, "AUTO-LOCK " + (autoLock ? "ON" : "OFF"), chipX, chipY, new Color(124, 208, 255, 210), autoLock);
        chipX = drawHudChipAuto(g2, "POWER " + player.powerPreset.name(), chipX, chipY, new Color(114, 226, 166, 208), true);
        chipX = drawHudChipAuto(g2, "CREW " + player.crewOrder.name(), chipX, chipY, new Color(244, 198, 116, 208), true);
        if (player.shieldActive && player.shieldMax > 0.0) {
            drawHudChipAuto(g2, "SHIELD " + player.shieldFacingMode.name(), chipX, chipY, new Color(154, 186, 255, 208), true);
        }

        int barY = chipY + 34;
        drawPowerAllocationStrip(g2, player, x + 12, barY, w - 24, 16);

        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(new Color(198, 218, 238, 195));
        int textY = barY + 32;
        for (String line : noteLines) {
            if (line == null || line.isBlank()) continue;
            boolean emphasis = line.startsWith("Hint:") || line.startsWith("Counter:") || line.startsWith("OVERLAY:");
            g2.setColor(emphasis ? new Color(255, 226, 154, 224) : new Color(198, 218, 238, 195));
            g2.drawString(line, x + 12, textY);
            textY += 15;
        }

        g2.setFont(oldFont);
        g2.setColor(oldColor);
    }

    private static List<String> buildActionStripLabels(Player player, GameContext.HudDetail detail) {
        ArrayList<String> out = new ArrayList<>();
        out.add("SPACE FIRE");
        out.add("SHIFT MISSILES");
        out.add("L LOCK");
        out.add("Q SALVO");
        out.add("TAB SHOP");
        if (detail != GameContext.HudDetail.MINIMAL) {
            out.add("M MAP");
            out.add("H CREW");
            out.add("O POWER");
            out.add("B BASE");
        }
        if (detail == GameContext.HudDetail.FULL) {
            out.add("F MINE");
            out.add("E OVERCHARGE");
            out.add("I SHIELD");
            out.add("Y PRESET");
        }
        if (player.hasSuperweapon) out.add("X SUPERWEAPON");
        if (player.isCarrier) {
            out.add("/ FLIGHT");
            out.add("C LAUNCH");
        }
        out.add("N DETAIL");
        return out;
    }

    private static void drawHudPanelFrame(Graphics2D g2, int x, int y, int w, int h, String title, Color accent) {
        if (g2 == null) return;
        Color base = (accent == null) ? new Color(150, 190, 235, 180) : accent;
        g2.setColor(new Color(7, 14, 24, 188));
        g2.fillRoundRect(x, y, w, h, 18, 18);
        g2.setColor(withAlpha(base, 110));
        g2.drawRoundRect(x, y, w - 1, h - 1, 18, 18);
        g2.setColor(new Color(255, 255, 255, 22));
        g2.drawRoundRect(x + 1, y + 1, w - 3, h - 3, 16, 16);
        g2.setFont(new Font("Consolas", Font.BOLD, 12));
        g2.setColor(withAlpha(base, 220));
        g2.drawString(title, x + 12, y + 16);
        g2.setColor(withAlpha(base, 72));
        g2.drawLine(x + 12, y + 22, x + w - 12, y + 22);
    }

    private static int drawHudChipAuto(Graphics2D g2, String text, int x, int y, Color accent, boolean strong) {
        if (g2 == null || text == null || text.isBlank()) return x;
        Font oldFont = g2.getFont();
        g2.setFont(new Font("Consolas", Font.BOLD, 11));
        FontMetrics fm = g2.getFontMetrics();
        int chipW = fm.stringWidth(text) + 14;
        drawHudStatusChip(g2, text, x, y - 12, chipW, 18, accent, strong);
        g2.setFont(oldFont);
        return x + chipW + 8;
    }

    private static void drawHudStatusChip(Graphics2D g2, String text, int x, int y, int w, int h, Color accent, boolean strong) {
        if (g2 == null || text == null) return;
        Color base = (accent == null) ? new Color(180, 205, 235, 220) : accent;
        int fillAlpha = strong ? 82 : 54;
        g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), fillAlpha));
        g2.fillRoundRect(x, y, w, h, 12, 12);
        g2.setColor(withAlpha(base, strong ? 210 : 170));
        g2.drawRoundRect(x, y, w, h, 12, 12);
        g2.setColor(new Color(245, 250, 255, strong ? 228 : 210));
        g2.drawString(text, x + 7, y + 12);
    }

    private static void drawPowerAllocationStrip(Graphics2D g2, Player player, int x, int y, int w, int h) {
        if (g2 == null || player == null) return;
        double[] fracs = new double[]{
                player.powerEnginesFrac(),
                player.powerShieldsFrac(),
                player.powerWeaponsFrac(),
                player.powerSensorsFrac(),
                player.powerEngineeringFrac()
        };
        String[] labels = new String[]{"P", "SH", "T", "SN", "EN", "AX"};
        Color[] colors = new Color[]{
                new Color(110, 212, 255),
                new Color(138, 168, 255),
                new Color(255, 132, 132),
                new Color(128, 240, 190),
                new Color(255, 206, 118),
                new Color(188, 188, 205)
        };

        int[] values = new int[6];
        int total = 0;
        for (int i = 0; i < fracs.length; i++) {
            values[i] = MathUtil.clamp((int) Math.round(fracs[i] * 100.0), 0, 100);
            total += values[i];
        }
        values[5] = Math.max(0, 100 - total);

        g2.setColor(new Color(255, 255, 255, 48));
        g2.drawRoundRect(x, y, w, h, 10, 10);
        int innerX = x + 1;
        int innerW = Math.max(1, w - 1);
        for (int i = 0; i < values.length; i++) {
            int segW = (int) Math.round(innerW * (values[i] / 100.0));
            if (i == values.length - 1) {
                segW = Math.max(0, x + w - innerX);
            }
            if (segW <= 0) continue;
            g2.setColor(withAlpha(colors[i], 150));
            g2.fillRect(innerX, y + 1, segW, h - 1);
            innerX += segW;
        }

        g2.setFont(new Font("Consolas", Font.BOLD, 10));
        FontMetrics fm = g2.getFontMetrics();
        int labelX = x;
        int labelY = y + h + 12;
        for (int i = 0; i < labels.length; i++) {
            String text = labels[i] + " " + values[i] + "%";
            g2.setColor(withAlpha(colors[i], 216));
            g2.drawString(text, labelX, labelY);
            labelX += fm.stringWidth(text) + 12;
        }
    }

    private static int drawHudControlsCard(Graphics2D g2, Player player, GameContext.HudDetail detail, int x, int y, int viewW) {
        if (g2 == null || player == null) return y;
        GameContext.HudDetail mode = (detail == null) ? GameContext.HudDetail.FULL : detail;
        java.util.List<String> rows = buildHudControlsRows(player, mode);
        if (rows.isEmpty()) return y;

        Font oldFont = g2.getFont();
        Color oldColor = g2.getColor();

        Font titleFont = new Font("Consolas", Font.BOLD, 13);
        Font rowFont = new Font("Consolas", Font.PLAIN, 13);
        g2.setFont(rowFont);
        FontMetrics rowFm = g2.getFontMetrics();

        int panelW = Math.max(360, Math.min(780, viewW - x - 18));
        int contentW = Math.max(220, panelW - 24);

        java.util.List<String> wrappedRows = new ArrayList<>();
        for (String row : rows) {
            wrappedRows.addAll(wrapHudText(rowFm, row, contentW));
        }

        int rowH = 15;
        g2.setFont(titleFont);
        g2.setColor(new Color(210, 234, 255, 220));
        g2.drawString("HUD [" + mode.name() + "]  N: cycle detail", x, y + 14);

        int rowY = y + 31;
        for (int i = 0; i < wrappedRows.size(); i++) {
            g2.setColor(new Color(206, 224, 244, 190));
            g2.drawString(wrappedRows.get(i), x, rowY);
            rowY += rowH;
        }

        g2.setFont(oldFont);
        g2.setColor(oldColor);
        return rowY + 6;
    }

    private static java.util.List<String> buildHudControlsRows(Player player, GameContext.HudDetail detail) {
        java.util.List<String> rows = new ArrayList<>();
        if (detail == GameContext.HudDetail.MINIMAL) {
            rows.add("QUICK: L lock target | TAB/B/M/O/H overlays | bottom bar access");
            rows.add("META: ESC pause/resume | N HUD detail");
            return rows;
        }

        if (detail == GameContext.HudDetail.COMPACT) {
            rows.add("CURSOR COMBAT: LMB guns | RMB missiles | Q salvo" + (player.hasSuperweapon ? " | X superweapon" : ""));
            rows.add("TARGETING: L lock | [ ] cycle | T auto-lock");
            rows.add("SYSTEM: Y preset | U crew | I shield mode | J/K shield face");
            rows.add("OVERLAYS: TAB shop | B base | M map | O power | H crew | bottom bar");
            rows.add("X-RAY: ` filter | ' clear focus | click room focus");
            rows.add("META: ESC pause/resume");
            return rows;
        }

        rows.add("CURSOR COMBAT: LMB guns | RMB missiles | Q salvo" + (player.hasSuperweapon ? " | X superweapon" : ""));
        rows.add("UTILITY: F mine | ; emergency thrust | E shield overcharge");
        rows.add("TARGETING: L lock under mouse | [ ] cycle targets | T auto-lock");
        rows.add("SYSTEMS: O power mgmt | H crew stations | Y power preset | U crew order");
        rows.add("SHIELDS: I shield mode | J/K shield facing");
        rows.add("X-RAY: ` cycle filter | ' clear focus | click room to focus | RMB clears focus");
        rows.add("OVERLAYS: TAB shop/loadout | B base upgrades | bottom bar quick access");
        rows.add("WARP: - or BACKSPACE charge 10s warp to waypoint or friendly base");
        if (player.isStealth) rows.add("STEALTH: cloak auto-engages while not firing or taking hits");
        if (player.isCarrier) rows.add("CARRIER: C launch wing | R recall | V attack/defend | Z auto-launch");
        rows.add("META: ESC pause/resume | Alt+Enter fullscreen");
        return rows;
    }

    private static java.util.List<String> wrapHudText(FontMetrics fm, String text, int maxWidth) {
        java.util.List<String> out = new ArrayList<>();
        if (fm == null || text == null || text.isBlank() || maxWidth <= 0) return out;
        String[] words = text.trim().split("\\s+");
        String line = "";
        for (String word : words) {
            if (word == null || word.isBlank()) continue;
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && fm.stringWidth(candidate) > maxWidth) {
                out.add(line);
                line = word;
            } else {
                line = candidate;
            }
        }
        if (!line.isEmpty()) out.add(line);
        return out;
    }

    private static void drawBottomCombatVitals(Graphics2D g2, Player player, Ship lockedTarget,
                                               XrayStackLayout layout, int viewW, int viewH) {
        if (g2 == null || player == null) return;

        int cardW = 220;
        int cardH = 116;
        int margin = 12;
        int sideGap = 12;

        int playerX;
        int playerY;
        int targetX;
        int targetY;

        if (layout != null) {
            playerX = Math.max(margin, layout.panelX - cardW - sideGap);
            playerY = layout.playerY + Math.max(0, (layout.playerH - cardH) / 2);
            targetX = Math.min(viewW - cardW - margin, layout.targetX + layout.panelW + sideGap);
            targetY = (layout.targetVisible && layout.targetH > 0)
                    ? layout.targetY + Math.max(0, (layout.targetH - cardH) / 2)
                    : playerY;
        } else {
            Rectangle menu = getCoreMenuBarRect(viewW, viewH);
            int cx = viewW / 2;
            playerX = Math.max(margin, cx - cardW - 18);
            targetX = Math.min(viewW - cardW - margin, cx + 18);
            playerY = Math.max(100, menu.y - cardH - 12);
            targetY = playerY;
        }

        drawShipVitalsCard(
                g2, player, "PLAYER VITALS", playerX, playerY, cardW, cardH,
                factionHudColor(player.faction, 220),
                true
        );

        boolean validTarget = lockedTarget != null && lockedTarget.alive && !lockedTarget.dying && lockedTarget.hp > 0
                && (lockedTarget.faction == null || player.faction == null || !lockedTarget.faction.isFriendlyTo(player.faction));
        if (validTarget) {
            String title = "TARGET VITALS";
            if (lockedTarget.name != null && !lockedTarget.name.isBlank()) title = lockedTarget.name;
            drawShipVitalsCard(
                    g2, lockedTarget, title, targetX, targetY, cardW, cardH,
                    factionHudColor(lockedTarget.faction, 220),
                    false
            );
        }
    }

    private static void drawShipVitalsCard(Graphics2D g2, Ship ship, String title,
                                           int x, int y, int w, int h, Color accent, boolean showOverchargeHint) {
        if (g2 == null || ship == null) return;
        Color frame = (accent == null) ? new Color(175, 210, 255, 150) : withAlpha(accent, 178);

        Font oldFont = g2.getFont();
        Color oldColor = g2.getColor();

        g2.setFont(new Font("Consolas", Font.BOLD, 12));
        g2.setColor(new Color(frame.getRed(), frame.getGreen(), frame.getBlue(), 220));
        g2.drawString(title, x, y + 12);
        g2.setColor(new Color(frame.getRed(), frame.getGreen(), frame.getBlue(), 120));
        g2.drawLine(x, y + 16, x + w, y + 16);

        g2.setFont(new Font("Consolas", Font.PLAIN, 11));
        int meterW = w;
        int meterH = 10;
        int meterX = x;
        int hullY = y + 34;
        int shieldY = y + 62;
        int cloakY = y + 90;

        double hullFrac = (ship.hpMax <= 0) ? 0.0 : Math.max(0.0, Math.min(1.0, ship.hp / (double) ship.hpMax));
        drawVitalsMeter(g2, meterX, hullY, meterW, meterH, "HULL " + ship.hp + "/" + ship.hpMax, hullFrac,
                new Color(92, 246, 124, 218));

        if (ship.shieldActive && ship.shieldMax > 0.0) {
            double shieldFrac = Math.max(0.0, Math.min(1.0, ship.shield / Math.max(1e-9, ship.shieldMax)));
            int shieldNow = (int) Math.round(Math.max(0.0, ship.shield));
            int shieldMax = (int) Math.round(Math.max(0.0, ship.shieldMax));
            drawVitalsMeter(g2, meterX, shieldY, meterW, meterH, "SHIELD " + shieldNow + "/" + shieldMax, shieldFrac,
                    shieldFaceColor(ship, Ship.SHIELD_FACE_FORE, 216));
            if (showOverchargeHint) {
                drawHudHintChip(g2, "E shield overcharge", x + w - 2, shieldY - 2, -1);
            }
            if (!ship.isShieldOnline()) {
                g2.setColor(new Color(255, 185, 136, 210));
                g2.drawString("REBOOT " + fmt1(ship.getShieldOfflineRemaining()) + "s", meterX, y + h - 16);
            }
        } else {
            drawVitalsMeter(g2, meterX, shieldY, meterW, meterH, "SHIELD N/A", 0.0, new Color(135, 160, 190, 160));
        }

        if (ship.isTemporarilyDisabled()) {
            String disabled = "DISABLED " + fmt1(ship.getTemporaryDisableRemaining()) + "s";
            FontMetrics statusFm = g2.getFontMetrics();
            g2.setColor(new Color(255, 134, 118, 220));
            g2.drawString(disabled, x + w - statusFm.stringWidth(disabled), y + h - 16);
        }

        if (ship.isStealth) {
            int pct = (int) Math.round(ship.cloakEnergyFrac() * 100.0);
            String state = ship.isCloaked() ? "ACTIVE" : "RECHARGE";
            drawVitalsMeter(
                    g2,
                    meterX,
                    cloakY,
                    meterW,
                    meterH,
                    "CLOAK " + MathUtil.clamp(pct, 0, 100) + "% " + state,
                    ship.cloakEnergyFrac(),
                    new Color(168, 130, 255, 210)
            );
        }

        g2.setFont(oldFont);
        g2.setColor(oldColor);
    }

    private static void drawVitalsMeter(Graphics2D g2, int x, int y, int w, int h,
                                        String label, double frac, Color fillColor) {
        if (g2 == null) return;
        double f = Math.max(0.0, Math.min(1.0, frac));
        g2.setColor(new Color(255, 255, 255, 75));
        g2.drawRect(x, y, w, h);
        int fillW = (int) Math.round((w - 1) * f);
        g2.setColor(fillColor == null ? new Color(160, 210, 255, 188) : fillColor);
        if (fillW > 0) g2.fillRect(x + 1, y + 1, fillW, h - 1);
        g2.setColor(new Color(225, 240, 255, 212));
        g2.drawString(label, x, y - 2);
    }

    private static void drawCursorWeaponHints(Graphics2D g2, GameContext ctx, Player player,
                                              double camX, double camY, double zoom, int viewW, int viewH) {
        if (g2 == null || ctx == null || player == null) return;
        if (hudBlockingMenuOpen(ctx)) return;
        if (zoom <= 0.0) return;

        double sx = (ctx.cursorWorldX - camX) * zoom;
        double sy = (ctx.cursorWorldY - camY) * zoom;
        if (!Double.isFinite(sx) || !Double.isFinite(sy)) return;

        int mx = MathUtil.clamp((int) Math.round(sx), 18, Math.max(18, viewW - 18));
        int my = MathUtil.clamp((int) Math.round(sy), 18, Math.max(18, viewH - 18));

        int horizontalGap = 16;
        int verticalGap = 24;
        drawHudHintChip(g2, "LMB guns", mx - horizontalGap, my, -1);
        drawHudHintChip(g2, "RMB missiles", mx + horizontalGap, my, +1);
        drawHudHintChip(g2, "Q missile salvo", mx, my + verticalGap, 0);
        if (player.role == ShipRole.SUPERSHIP || player.hasSuperweapon) {
            drawHudHintChip(g2, "X superweapon", mx, my - verticalGap, 0);
        }
    }

    private static boolean hudBlockingMenuOpen(GameContext ctx) {
        if (ctx == null) return false;
        return ctx.shopOpen
                || ctx.baseMenuOpen
                || ctx.mapOpen
                || ctx.powerManagementOpen
                || ctx.crewStationsOpen
                || ctx.flightDeckOpen
                || ctx.state == GameState.PAUSED
                || ctx.state == GameState.GAME_OVER;
    }

    // align: -1 right-align to anchor, +1 left-align to anchor, 0 center on anchor.
    private static void drawHudHintChip(Graphics2D g2, String text, int anchorX, int baselineY, int align) {
        if (g2 == null || text == null || text.isBlank()) return;
        Font oldFont = g2.getFont();
        Color oldColor = g2.getColor();

        g2.setFont(new Font("Consolas", Font.BOLD, 11));
        FontMetrics fm = g2.getFontMetrics();
        int padX = 2;
        int textW = fm.stringWidth(text);

        int textX;
        if (align < 0) {
            textX = anchorX - textW - padX;
        } else if (align > 0) {
            textX = anchorX + padX;
        } else {
            textX = anchorX - textW / 2;
        }

        g2.setColor(new Color(4, 8, 14, 210));
        g2.drawString(text, textX + 1, baselineY + 1);
        g2.setColor(new Color(236, 244, 255, 228));
        g2.drawString(text, textX, baselineY);

        g2.setFont(oldFont);
        g2.setColor(oldColor);
    }

    public static void drawWorldMarkers(Graphics2D g2, List<Ship> ships, Ship lockedTarget,
                                        java.util.Map<Faction, Ship> commandShips,
                                        java.util.Map<Faction, Ship> sharedTargets) {
        if (lockedTarget != null && lockedTarget.alive) {
            int x = (int) Math.round(lockedTarget.x);
            int y = (int) Math.round(lockedTarget.y);
            int rr = (int) Math.round(lockedTarget.radius + 18);
            g2.setColor(new Color(255, 255, 255, 170));
            g2.drawOval(x - rr, y - rr, rr * 2, rr * 2);
            g2.drawLine(x - rr, y, x - rr + 10, y);
            g2.drawLine(x + rr, y, x + rr - 10, y);
            g2.drawLine(x, y - rr, x, y - rr + 10);
            g2.drawLine(x, y + rr, x, y + rr - 10);
        }

        if (commandShips != null && !commandShips.isEmpty()) {
            for (java.util.Map.Entry<Faction, Ship> e : commandShips.entrySet()) {
                Ship cmd = (e == null) ? null : e.getValue();
                if (cmd == null || !cmd.alive || cmd.dying || cmd.hp <= 0) continue;
                drawCommandShipBeacon(g2, cmd, e.getKey(), (sharedTargets == null) ? null : sharedTargets.get(e.getKey()));
            }
        }

        if (ships == null) return;
        for (Ship s : ships) {
            if (s == null || !s.alive || s.dying || s.hp <= 0) continue;
            if (!s.isWarpCharging()) continue;
            if (!Double.isFinite(s.warpExitX()) || !Double.isFinite(s.warpExitY())) continue;
            drawWarpArrivalTell(g2, s);
        }
        for (Ship s : ships) {
            if (!s.alive) continue;
            if (s.role != ShipRole.BASE) continue;

            int x = (int) Math.round(s.x);
            int y = (int) Math.round(s.y - s.radius - 26);
            int w = 110;
            int h = 8;

            double p = Math.max(0, Math.min(1, s.captureProgress));

            g2.setColor(new Color(0, 0, 0, 140));
            g2.fillRoundRect(x - w / 2, y, w, h, 8, 8);
            g2.setColor(new Color(255, 255, 255, 70));
            g2.drawRoundRect(x - w / 2, y, w, h, 8, 8);

            g2.setColor(new Color(9, 189, 67, 200));
            g2.fillRoundRect(x - w / 2 + 1, y + 1, (int) Math.round((w - 2) * p), h - 2, 7, 7);

            g2.setColor(new Color(255, 90, 90, 110));
            int start = x - w / 2 + 1 + (int) Math.round((w - 2) * p);
            int rem = (x + w / 2 - 1) - start;
            if (rem > 0) g2.fillRoundRect(start, y + 1, rem, h - 2, 7, 7);
        }
    }

    private static void drawWarpArrivalTell(Graphics2D g2, Ship ship) {
        if (g2 == null || ship == null) return;
        double pulse = 0.45 + 0.55 * Math.sin(System.nanoTime() * 1e-9 * 4.4 + ship.id * 0.19);
        double progress = ship.warpChargeProgress();
        int x = (int) Math.round(ship.warpExitX());
        int y = (int) Math.round(ship.warpExitY());
        int baseR = (int) Math.round(Math.max(34.0, ship.radius * 1.4));
        int outerR = (int) Math.round(baseR + 18 + pulse * 18 + progress * 12);
        Color base = factionHudColor(ship.faction, 220);

        g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 32 + (int) Math.round(progress * 42.0)));
        g2.fillOval(x - outerR, y - outerR, outerR * 2, outerR * 2);

        Stroke old = g2.getStroke();
        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 170));
        g2.drawOval(x - outerR, y - outerR, outerR * 2, outerR * 2);
        g2.setColor(new Color(235, 245, 255, 215));
        g2.drawOval(x - baseR, y - baseR, baseR * 2, baseR * 2);
        g2.drawLine(x - outerR - 8, y, x - baseR + 2, y);
        g2.drawLine(x + baseR - 2, y, x + outerR + 8, y);
        g2.drawLine(x, y - outerR - 8, x, y - baseR + 2);
        g2.drawLine(x, y + baseR - 2, x, y + outerR + 8);
        g2.setStroke(old);

        g2.setFont(new Font("Consolas", Font.BOLD, 10));
        g2.setColor(new Color(240, 247, 255, 220));
        g2.drawString("WARP IN", x - 19, y - outerR - 8);
    }

    private static void drawCommandShipBeacon(Graphics2D g2, Ship cmd, Faction faction, Ship sharedTarget) {
        if (g2 == null || cmd == null) return;
        double pulse = 0.5 + 0.5 * Math.sin(System.nanoTime() * 1e-9 * 3.2 + cmd.id * 0.31);
        int x = (int) Math.round(cmd.x);
        int y = (int) Math.round(cmd.y - cmd.radius - 34);
        int r = (int) Math.round(8 + 5 * pulse);

        Color base = factionHudColor(faction, 220);
        g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 70));
        g2.fillOval(x - r - 6, y - r - 6, (r + 6) * 2, (r + 6) * 2);

        Polygon p = new Polygon();
        p.addPoint(x, y - r);
        p.addPoint(x + r, y);
        p.addPoint(x, y + r);
        p.addPoint(x - r, y);
        g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 210));
        g2.fillPolygon(p);
        g2.setColor(new Color(255, 255, 255, 190));
        g2.drawPolygon(p);

        g2.setFont(new Font("Consolas", Font.BOLD, 10));
        g2.setColor(new Color(255, 255, 255, 220));
        g2.drawString("CMD", x - 10, y - r - 4);

        if (sharedTarget != null && sharedTarget.alive && !sharedTarget.dying && sharedTarget.hp > 0) {
            Stroke old = g2.getStroke();
            g2.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0f, new float[]{6f, 6f}, 0f));
            g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 90));
            g2.drawLine((int) Math.round(cmd.x), (int) Math.round(cmd.y), (int) Math.round(sharedTarget.x), (int) Math.round(sharedTarget.y));
            g2.setStroke(old);
        }
    }


    private static void drawShopOverlay(Graphics2D g2, Player player, int credits, int hangarTier) {
        // Step 4B: "Shop clarity"
        // - Show what upgrades do (with current -> next deltas)
        // - Highlight affordability / requirements
        // - Keep layout readable in fullscreen by anchoring to bottom-left.

        Rectangle clip = g2.getClipBounds();
        int viewW = clip.width;
        int viewH = clip.height;

        int w = 700;
        int h = 700;
        int x = 10;
        int y = Math.max(40, viewH - h - 150);

        // Panel
        g2.setColor(new Color(0, 0, 0, 190));
        g2.fillRoundRect(x, y, w, h, 18, 18);
        g2.setColor(new Color(255, 255, 255, 95));
        g2.drawRoundRect(x, y, w, h, 18, 18);

        // Header
        g2.setFont(new Font("Consolas", Font.BOLD, 15));
        g2.setColor(new Color(255, 255, 255, 230));
        g2.drawString("SHOP / LOADOUT", x + 14, y + 26);

        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(new Color(255, 255, 255, 150));
        g2.drawString("TAB/ESC close   1-9 buy   F-keys + \\ + 0/-/= swap hull", x + 14, y + 44);

        // Readouts
        int ty = y + 70;
        g2.setFont(new Font("Consolas", Font.PLAIN, 13));
        g2.setColor(new Color(255, 255, 255, 210));
        g2.drawString("Credits: " + credits, x + 14, ty);
        g2.drawString("Hangar Tier: " + hangarTier, x + 190, ty);
        g2.drawString("Hull: " + shopRoleTitle(player.role), x + 350, ty);

        // Divider
        ty += 14;
        g2.setColor(new Color(255, 255, 255, 60));
        g2.drawLine(x + 14, ty, x + w - 14, ty);
        ty += 20;

        // ------------------------------
        // Upgrades 5-9
        // ------------------------------
        g2.setFont(new Font("Consolas", Font.BOLD, 13));
        g2.setColor(new Color(255, 255, 255, 220));
        g2.drawString("UPGRADES", x + 14, ty);
        ty += 18;

        int gunCount = 0;
        int missileCount = 0;
        if (player.turrets != null) {
            for (Turret t : player.turrets) {
                if (t == null) continue;
                if (t.kind == Turret.Kind.GUN) gunCount++;
                else if (t.kind == Turret.Kind.MISSILE) missileCount++;
            }
        }

        // 3: Energy bolt primary
        {
            boolean isEnergy = player.primaryWeaponFamily == Ship.PrimaryWeaponFamily.ENERGY_BOLT;
            int cost = 0;
            boolean can = true;
            String detail = isEnergy
                    ? "Primary: ENERGY_BOLT (standard)"
                    : "Primary: Beam Bolt \u2192 Energy Bolt";
            drawShopLine(g2, x + 14, ty, "3", "Energy Bolt Primary", detail, cost, can, true, isEnergy ? "ACTIVE" : null);
            ty += 22;
        }

        // 4: Beam bolt primary
        {
            boolean isBeam = player.primaryWeaponFamily == Ship.PrimaryWeaponFamily.BEAM_BOLT;
            int cost = isBeam ? 0 : 220;
            boolean can = credits >= cost;
            String detail = isBeam
                    ? "Primary: BEAM_BOLT (heavy energy bolt)"
                    : "Primary: Energy Bolt \u2192 Beam Bolt";
            drawShopLine(g2, x + 14, ty, "4", "Beam Bolt Primary", detail, cost, can, true, isBeam ? "ACTIVE" : null);
            ty += 22;
        }

        // 5: Hull +10
        {
            int cost = 60;
            boolean can = credits >= cost;
            String detail = "HP " + player.hpMax + " \u2192 " + (player.hpMax + 10);
            drawShopLine(g2, x + 14, ty, "5", "Hull Plating", detail, cost, can, true, null);
            ty += 22;
        }

        // 6: Shield +12 / regen +0.3
        {
            int cost = 70;
            boolean available = player.shieldActive && player.shieldMax > 0;
            boolean can = available && credits >= cost;
            String detail = available
                    ? ("Shield " + (int) Math.round(player.shieldMax) + " \u2192 " + (int) Math.round(player.shieldMax + 12)
                    + "   Regen " + fmt1(player.shieldRegen) + " \u2192 " + fmt1(player.shieldRegen + 0.3))
                    : "Unavailable (this hull has no shields)";
            drawShopLine(g2, x + 14, ty, "6", "Shield Array", detail, cost, can, available, null);
            ty += 22;
        }

        // 7: Add gun turret
        {
            int cost = 100;
            boolean can = credits >= cost;
            String detail = "Gun turrets " + gunCount + " \u2192 " + (gunCount + 1);
            drawShopLine(g2, x + 14, ty, "7", "Add Gun Turret", detail, cost, can, true, null);
            ty += 22;
        }

        // 8: Add missile rack
        {
            int cost = 140;
            boolean can = credits >= cost;
            String detail = "Missile racks " + missileCount + " \u2192 " + (missileCount + 1);
            drawShopLine(g2, x + 14, ty, "8", "Add Missile Rack", detail, cost, can, true, null);
            ty += 22;
        }

        // 9: CIWS upgrade
        {
            boolean hasCiws = player.hasCIWS;
            boolean maxed = hasCiws && player.isCIWSUpgradeMaxed();
            int cost = maxed ? 0 : 120;
            boolean available = hasCiws;
            boolean can = available && (maxed || credits >= cost);

            double nextQ = Math.min(1.0, player.ciwsQuality + 0.20);
            double nextRange = Math.min(380.0, player.ciwsRange + 25.0);
            int nextPellets = Math.min(8, player.ciwsPelletsPerBurst + 1);
            double nextCd = Math.max(0.04, player.ciwsCooldown - 0.01);

            String detail = available
                    ? (maxed
                    ? ("Quality " + fmt1(player.ciwsQuality)
                    + "   Range " + (int) Math.round(player.ciwsRange)
                    + "   Burst " + player.ciwsPelletsPerBurst
                    + "   CD " + fmt1(player.ciwsCooldown))
                    : ("Quality " + fmt1(player.ciwsQuality) + " \u2192 " + fmt1(nextQ)
                    + "   Range " + (int) Math.round(player.ciwsRange) + " \u2192 " + (int) Math.round(nextRange)
                    + "   Burst " + player.ciwsPelletsPerBurst + " \u2192 " + nextPellets
                    + "   CD " + fmt1(player.ciwsCooldown) + " \u2192 " + fmt1(nextCd)))
                    : "Unavailable (this hull has no CIWS)";
            drawShopLine(g2, x + 14, ty, "9", "Upgrade CIWS", detail, cost, can, available, maxed ? "MAX" : null);
            ty += 26;
        }

        // Divider
        g2.setColor(new Color(255, 255, 255, 60));
        g2.drawLine(x + 14, ty - 10, x + w - 14, ty - 10);

        // ------------------------------
        // Hull swaps
        // ------------------------------
        g2.setFont(new Font("Consolas", Font.BOLD, 13));
        g2.setColor(new Color(255, 255, 255, 220));
        g2.drawString("HULL SWAP", x + 14, ty + 6);
        ty += 24;

        // Helper to draw hull option
        java.util.function.BiFunction<ShipRole, Integer, Integer> reqTier = (role, unused) -> switch (role) {
            case PATROL, PICKET, FRIGATE, MISSILE_BOAT, CIWS_CORVETTE -> 0;
            case LIGHT_CRUISER, MEDIUM_CRUISER, CRUISER -> 1;
            case BATTLECRUISER, BATTLESHIP, STEALTH_SHIP -> 2;
            case DREADNOUGHT, CARRIER, DRONE_CARRIER, TRANSPORT, SUPERSHIP -> 3;
            default -> 0;
        };

        ty = drawHullLine(g2, x + 14, ty, "F1", ShipRole.PATROL, 0, credits, hangarTier, player, reqTier);
        ty = drawHullLine(g2, x + 14, ty, "F2", ShipRole.PICKET, 180, credits, hangarTier, player, reqTier);
        ty = drawHullLine(g2, x + 14, ty, "F3", ShipRole.FRIGATE, 0, credits, hangarTier, player, reqTier);
        ty = drawHullLine(g2, x + 14, ty, "F4", ShipRole.MISSILE_BOAT, 300, credits, hangarTier, player, reqTier);
        ty = drawHullLine(g2, x + 14, ty, "F5", ShipRole.CIWS_CORVETTE, 250, credits, hangarTier, player, reqTier);
        ty = drawHullLine(g2, x + 14, ty, "F6", ShipRole.LIGHT_CRUISER, 700, credits, hangarTier, player, reqTier);
        ty = drawHullLine(g2, x + 14, ty, "F7", ShipRole.MEDIUM_CRUISER, 950, credits, hangarTier, player, reqTier);
        ty = drawHullLine(g2, x + 14, ty, "\\", ShipRole.CRUISER, 1100, credits, hangarTier, player, reqTier);
        ty = drawHullLine(g2, x + 14, ty, "F8", ShipRole.BATTLECRUISER, 1600, credits, hangarTier, player, reqTier);
        ty = drawHullLine(g2, x + 14, ty, "F9", ShipRole.BATTLESHIP, 2200, credits, hangarTier, player, reqTier);
        ty = drawHullLine(g2, x + 14, ty, "F11", ShipRole.STEALTH_SHIP, 1200, credits, hangarTier, player, reqTier);
        ty = drawHullLine(g2, x + 14, ty, "F12", ShipRole.DREADNOUGHT, 3200, credits, hangarTier, player, reqTier);
        ty = drawHullLine(g2, x + 14, ty, "0", ShipRole.CARRIER, 2800, credits, hangarTier, player, reqTier);
        ty = drawHullLine(g2, x + 14, ty, "-", ShipRole.DRONE_CARRIER, 3000, credits, hangarTier, player, reqTier);
        ty = drawHullLine(g2, x + 14, ty, "=", ShipRole.SUPERSHIP, 5200, credits, hangarTier, player, reqTier);

        // Footer hint
        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(new Color(255, 255, 255, 130));
        g2.drawString("Tip: If a hull is locked, upgrade a friendly base (B) to raise hangar tier.", x + 14, y + h - 16);
    }

    private static void drawShopLine(Graphics2D g2, int x, int y,
                                     String key, String title, String detail,
                                     int cost, boolean canAfford, boolean available, String rightTag) {

        // Key capsule
        g2.setFont(new Font("Consolas", Font.BOLD, 13));
        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRoundRect(x, y - 12, 30, 18, 10, 10);
        g2.setColor(new Color(255, 255, 255, 70));
        g2.drawRoundRect(x, y - 12, 30, 18, 10, 10);
        g2.setColor(new Color(255, 255, 255, 220));
        g2.drawString(key, x + 8, y + 2);

        int tx = x + 38;

        // Title
        g2.setFont(new Font("Consolas", Font.BOLD, 13));
        g2.setColor(available ? new Color(255, 255, 255, 220) : new Color(255, 255, 255, 110));
        g2.drawString(title, tx, y + 2);

        // Detail
        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(available ? new Color(255, 255, 255, 170) : new Color(255, 255, 255, 95));
        g2.drawString(detail, tx, y + 18);

        // Cost + tag (right aligned)
        String costStr = "$" + cost;
        if (rightTag != null && !rightTag.isBlank()) costStr = rightTag + "  " + costStr;

        FontMetrics fm = g2.getFontMetrics();
        int rightX = x + 540;
        int costW = fm.stringWidth(costStr);

        if (!available) {
            g2.setColor(new Color(255, 255, 255, 90));
        } else if (canAfford) {
            g2.setColor(new Color(120, 255, 170, 210));
        } else {
            g2.setColor(new Color(255, 120, 120, 210));
        }
        g2.drawString(costStr, rightX - costW, y + 2);
    }

    private static int drawHullLine(Graphics2D g2, int x, int y, String key, ShipRole role, int cost,
                                    int credits, int hangarTier, Player player,
                                    java.util.function.BiFunction<ShipRole, Integer, Integer> reqTier) {

        int req = reqTier.apply(role, 0);
        boolean meets = hangarTier >= req;
        boolean canAfford = credits >= cost;
        boolean current = player.role == role;

        String title = shopRoleTitle(role);
        String detail = "Requires Tier " + req + (req == 0 ? "" : "  (upgrade base)");
        String tag = current ? "CURRENT" : ("T" + req);

        // Color for requirement fail vs afford fail
        boolean available = meets;
        boolean canBuy = meets && canAfford;

        drawShopLine(g2, x, y, key, title, detail, cost, canBuy, available, tag);

        // Extra hint if locked by hangar tier
        if (!meets) {
            g2.setFont(new Font("Consolas", Font.PLAIN, 12));
            g2.setColor(new Color(255, 200, 120, 200));
            g2.drawString("Locked: need hangar tier " + req, x + 38, y + 34);
            return y + 40;
        }

        return y + 22;
    }

    private static String shopRoleTitle(ShipRole role) {
        if (role == null) return "UNKNOWN";
        return switch (role) {
            case CRUISER -> "GUIDED MISSILE CRUISER";
            default -> role.name().replace('_', ' ');
        };
    }




    public static void drawPowerManagementOverlay(Graphics2D g2, Player player, int focusSlot) {
        if (g2 == null || player == null) return;

        Rectangle clip = g2.getClipBounds();
        int w = Math.min(700, clip.width - 110);
        int h = 430;
        int x = (clip.width - w) / 2;
        int y = Math.max(54, (clip.height - h) / 2);

        g2.setColor(new Color(0, 0, 0, 205));
        g2.fillRoundRect(x, y, w, h, 18, 18);
        g2.setColor(new Color(255, 255, 255, 110));
        g2.drawRoundRect(x, y, w, h, 18, 18);

        g2.setFont(new Font("Consolas", Font.BOLD, 18));
        g2.setColor(new Color(255, 240, 180, 230));
        g2.drawString("POWER MANAGEMENT", x + 18, y + 30);

        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(new Color(255, 255, 255, 170));
        g2.drawString("O/ESC close   1-6 select bus   <-/-> or [/] adjust   F1-F4 presets", x + 18, y + 48);
        g2.drawString("7 overload on/off   8 cycle overload bus   9 cycle repair priority   0 emergency thrust", x + 18, y + 64);

        String[] labels = {"PROPULSION", "SHIELD", "TACTICAL", "SENSOR", "ENGINEERING", "AUXILIARY"};
        double[] values = player.powerBusFractions();

        int rowY = y + 96;
        int barW = 330;
        int barH = 16;
        for (int i = 0; i < labels.length; i++) {
            int ry = rowY + i * 28;
            boolean focus = (i == Math.max(0, Math.min(5, focusSlot)));
            int pct = (int) Math.round(values[i] * 100.0);
            double eff = player.powerBusEffect(Ship.PowerBus.values()[i]);

            g2.setColor(focus ? new Color(255, 230, 170, 220) : new Color(255, 255, 255, 200));
            g2.setFont(new Font("Consolas", focus ? Font.BOLD : Font.PLAIN, 14));
            g2.drawString((i + 1) + ": " + labels[i], x + 20, ry + 13);

            int bx = x + 150;
            int by = ry;
            g2.setColor(new Color(255, 255, 255, 70));
            g2.drawRoundRect(bx, by, barW, barH, 8, 8);
            int fill = (int) Math.round((barW - 2) * Math.max(0.0, Math.min(1.0, values[i])));
            Color c = switch (i) {
                case 0 -> new Color(120, 255, 150, 210);
                case 1 -> new Color(120, 210, 255, 210);
                case 2 -> new Color(255, 170, 120, 210);
                case 3 -> new Color(195, 170, 255, 210);
                case 4 -> new Color(255, 225, 130, 210);
                default -> new Color(175, 220, 190, 210);
            };
            g2.setColor(c);
            g2.fillRoundRect(bx + 1, by + 1, Math.max(0, fill), barH - 2, 7, 7);

            g2.setColor(new Color(255, 255, 255, 220));
            g2.setFont(new Font("Consolas", Font.BOLD, 13));
            g2.drawString(String.format(Locale.US, "%3d%%", pct), bx + barW + 14, ry + 13);
            g2.setFont(new Font("Consolas", Font.PLAIN, 11));
            g2.setColor(new Color(230, 240, 255, 185));
            g2.drawString("eff " + signedPct(eff), bx + barW + 72, ry + 13);
        }

        double speedMul = (player.desiredSpeedBase > 0.01) ? (player.desiredSpeed / player.desiredSpeedBase) : 1.0;
        double weaponDmg = player.weaponDamageMultiplier();
        double weaponCycle = player.weaponCycleRateMultiplier();
        double sensor = player.sensorRangeMultiplier();
        double shield = player.shieldRegenMultiplier();

        int py = y + 278;
        g2.setFont(new Font("Consolas", Font.BOLD, 13));
        g2.setColor(new Color(255, 255, 255, 210));
        g2.drawString("Effects Preview", x + 20, py);
        py += 20;

        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(new Color(200, 245, 220, 220));
        g2.drawString("Mobility: " + signedPct(speedMul) + "   Weapon Damage: " + signedPct(weaponDmg), x + 20, py);
        py += 16;
        g2.setColor(new Color(255, 225, 180, 220));
        g2.drawString("Fire Rate: " + signedPct(weaponCycle) + "   Sensor Range: " + signedPct(sensor), x + 20, py);
        py += 16;
        g2.setColor(new Color(180, 225, 255, 220));
        g2.drawString("Shield Effectiveness: " + signedPct(shield), x + 20, py);
        py += 16;
        g2.drawString("Shield Faces: " + shieldFaceReadout(player), x + 20, py);
        py += 20;

        String overload = player.isOverloadActive()
                ? "ACTIVE"
                : (player.isOverloadAvailable() ? "READY" : "COOLDOWN");
        g2.setColor(new Color(255, 214, 150, 225));
        g2.drawString("Overload " + overload + "  Bus " + player.overloadBus().name()
                + "  Heat " + (int) Math.round(player.overloadHeat() * 100.0) + "%  Debt "
                + (int) Math.round(player.overloadStressDebt() * 100.0) + "%  CD "
                + (int) Math.ceil(player.overloadCooldownRemaining()) + "s", x + 20, py);
        py += 18;
        String emergencyStatus = player.isEmergencyThrustActive() ? "ACTIVE" : "STANDBY";
        g2.setColor(new Color(255, 190, 150, 225));
        g2.drawString("Emergency Thrust " + emergencyStatus
                + "  Heat " + (int) Math.round(player.emergencyThrustHeat() * 100.0) + "%  CD "
                + (int) Math.ceil(player.emergencyThrustCooldownRemaining()) + "s  Propulsion "
                + (int) Math.round(player.propulsionRoomIntegrity() * 100.0) + "%", x + 20, py);
        py += 18;
        g2.setColor(new Color(200, 255, 200, 220));
        g2.drawString("Repair Priority: " + player.engineeringPriority().name(), x + 20, py);
        py += 20;

        g2.setFont(new Font("Consolas", Font.BOLD, 12));
        g2.setColor(new Color(225, 240, 255, 220));
        g2.drawString("Subsystem States", x + 20, py);
        py += 16;
        g2.setFont(new Font("Consolas", Font.PLAIN, 11));
        StringBuilder stateLine = new StringBuilder();
        for (Ship.InternalSystem system : Ship.InternalSystem.values()) {
            Ship.SubsystemState st = player.subsystemState(system);
            if (stateLine.length() > 0) stateLine.append("   ");
            stateLine.append(shortSystemName(system)).append(":").append(st.name());
        }
        g2.setColor(new Color(220, 230, 245, 210));
        g2.drawString(stateLine.toString(), x + 20, py);

        g2.setColor(new Color(255, 255, 255, 145));
        g2.drawString("Presets: F1 BALANCED   F2 ATTACK   F3 DEFENSE   F4 PURSUIT", x + 20, y + h - 18);
    }

    public static void drawFlightDeckOverlay(Graphics2D g2, Ship carrier, int focusSlot) {
        if (g2 == null || carrier == null || !carrier.isCarrier) return;

        Rectangle clip = g2.getClipBounds();
        int w = Math.min(820, clip.width - 100);
        int h = 356;
        int x = (clip.width - w) / 2;
        int y = Math.max(48, (clip.height - h) / 2);

        drawHudPanelFrame(g2, x, y, w, h, "FLIGHT DECK CONTROL", new Color(146, 210, 255, 225));

        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(new Color(225, 236, 250, 188));
        g2.drawString("/ or ESC close   F1-F5 select slot   [ ] move focus   -/+ cycle role", x + 18, y + 46);
        g2.drawString("Each slot is a 2-ship squad pair. 6 fighter   7 drone   8 bomber   9 all fighters   0 all bombers", x + 18, y + 62);
        g2.drawString("Backspace default mix   Total deck: 5 squads / 10 craft", x + 18, y + 78);

        int focus = Math.max(0, Math.min(4, focusSlot));
        int slotGap = 12;
        int slotW = (w - 36 - slotGap * 4) / 5;
        int slotH = 132;
        int slotY = y + 108;
        int fighters = 0;
        int bombers = 0;
        int drones = 0;

        for (int i = 0; i < 5; i++) {
            ShipRole role = carrier.flightDeckRoleAt(i);
            if (role == ShipRole.BOMBER) bombers += 2;
            else if (role == ShipRole.DRONE) drones += 2;
            else fighters += 2;

            int slotX = x + 18 + i * (slotW + slotGap);
            boolean selected = (i == focus);
            Color accent = flightDeckRoleColor(role);

            g2.setColor(selected ? new Color(26, 42, 64, 224) : new Color(12, 20, 32, 196));
            g2.fillRoundRect(slotX, slotY, slotW, slotH, 16, 16);
            g2.setColor(withAlpha(accent, selected ? 220 : 140));
            g2.drawRoundRect(slotX, slotY, slotW, slotH, 16, 16);
            g2.setColor(new Color(255, 255, 255, 22));
            g2.drawRoundRect(slotX + 1, slotY + 1, slotW - 2, slotH - 2, 14, 14);

            g2.setFont(new Font("Consolas", Font.BOLD, 13));
            g2.setColor(new Color(246, 250, 255, 228));
            g2.drawString("SQUAD " + (i + 1), slotX + 12, slotY + 20);

            int chipW = Math.max(70, Math.min(slotW - 24, g2.getFontMetrics(new Font("Consolas", Font.BOLD, 12)).stringWidth(flightDeckRoleLabel(role)) + 18));
            drawHudStatusChip(g2, flightDeckRoleLabel(role), slotX + 12, slotY + 30, chipW, 20, accent, true);

            g2.setFont(new Font("Consolas", Font.BOLD, 24));
            g2.setColor(withAlpha(accent, 228));
            g2.drawString(flightDeckRoleAbbrev(role), slotX + 12, slotY + 76);

            g2.setFont(new Font("Consolas", Font.PLAIN, 11));
            g2.setColor(new Color(216, 228, 242, 178));
            g2.drawString(flightDeckRoleDescription(role), slotX + 12, slotY + 98);
            g2.drawString(selected ? "ACTIVE 2-SHIP PAIR" : "READY", slotX + 12, slotY + 116);
        }

        int summaryY = slotY + slotH + 34;
        drawHudStatusChip(g2, "PAIR SIZE 2", x + 18, summaryY, 104, 18, new Color(140, 210, 255, 214), true);
        drawHudStatusChip(g2, "FIGHTER " + fighters, x + 132, summaryY, 102, 18, flightDeckRoleColor(ShipRole.FIGHTER), fighters > 0);
        drawHudStatusChip(g2, "DRONE " + drones, x + 244, summaryY, 94, 18, flightDeckRoleColor(ShipRole.DRONE), drones > 0);
        drawHudStatusChip(g2, "BOMBER " + bombers, x + 348, summaryY, 104, 18, flightDeckRoleColor(ShipRole.BOMBER), bombers > 0);
        drawHudStatusChip(g2, "MODE " + carrier.carrierCommandMode.name(), x + 462, summaryY, 122, 18,
                new Color(236, 196, 132, 214), carrier.carrierCommandMode == Ship.CarrierCommandMode.DEFEND);
        drawHudStatusChip(g2, "AUTO " + (carrier.carrierAutoLaunch ? "ON" : "OFF"), x + 594, summaryY, 96, 18,
                new Color(148, 228, 182, 214), carrier.carrierAutoLaunch);

        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(new Color(216, 228, 240, 190));
        g2.drawString("Launch rhythm: each launch call emits one 2-ship squad from the next squad slot in sequence.", x + 18, y + h - 38);
        g2.drawString("Defend mode recalls bomber squads and fighter escorts before the next pair leaves the deck.", x + 18, y + h - 20);
    }

    private static String shortSystemName(Ship.InternalSystem system) {
        if (system == null) return "?";
        return switch (system) {
            case ENGINES -> "ENG";
            case SHIELDS -> "SHD";
            case REACTOR_CORE -> "RCT";
            case SENSORS -> "SNS";
            case WEAPONS -> "WPN";
            case BRIDGE -> "BRG";
            case WARP_ENGINES -> "WRP";
            case MAGAZINES -> "MAG";
        };
    }

    private static Color flightDeckRoleColor(ShipRole role) {
        if (role == ShipRole.BOMBER) return new Color(255, 168, 124);
        if (role == ShipRole.DRONE) return new Color(150, 226, 204);
        return new Color(132, 190, 255);
    }

    private static String flightDeckRoleLabel(ShipRole role) {
        if (role == ShipRole.BOMBER) return "HEAVY BOMBER";
        if (role == ShipRole.DRONE) return "MULTIROLE DRONE";
        return "ESCORT FIGHTER";
    }

    private static String flightDeckRoleAbbrev(ShipRole role) {
        if (role == ShipRole.BOMBER) return "BMB";
        if (role == ShipRole.DRONE) return "DRN";
        return "FGT";
    }

    private static String flightDeckRoleDescription(ShipRole role) {
        if (role == ShipRole.BOMBER) return "ANTI-SHIP STRIKE";
        if (role == ShipRole.DRONE) return "FLEX SUPPORT";
        return "BOMBER ESCORT";
    }

    public static void drawCrewStationsOverlay(Graphics2D g2, GameContext ctx) {
        if (g2 == null || ctx == null || ctx.player == null) return;

        Rectangle clip = g2.getClipBounds();
        int w = Math.min(1010, clip.width - 56);
        int h = 438;
        int x = (clip.width - w) / 2;
        int y = Math.max(34, (clip.height - h) / 2);

        g2.setColor(new Color(0, 0, 0, 214));
        g2.fillRoundRect(x, y, w, h, 18, 18);
        g2.setColor(new Color(255, 255, 255, 110));
        g2.drawRoundRect(x, y, w, h, 18, 18);

        g2.setColor(new Color(255, 240, 180, 230));
        g2.setFont(new Font("Consolas", Font.BOLD, 18));
        g2.drawString("CREW STATIONS", x + 18, y + 30);

        g2.setColor(new Color(255, 255, 255, 170));
        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.drawString("H/ESC close   F1-F5 stations   A toggle station AI   <-/-> cycle station", x + 18, y + 48);

        int portraitPaneX = x + 18;
        int portraitPaneY = y + 70;
        int portraitPaneW = 232;
        int portraitPaneH = h - 88;

        g2.setColor(new Color(255, 255, 255, 28));
        g2.fillRoundRect(portraitPaneX, portraitPaneY, portraitPaneW, portraitPaneH, 12, 12);
        g2.setColor(new Color(255, 255, 255, 95));
        g2.drawRoundRect(portraitPaneX, portraitPaneY, portraitPaneW, portraitPaneH, 12, 12);

        CrewPortraitSystem.PortraitAsset activePortrait = CrewPortraitSystem.getPortrait(ctx.activeCrewStation);
        BufferedImage portraitImage = activePortrait.image();

        int portraitX = portraitPaneX + 10;
        int portraitY = portraitPaneY + 24;
        int portraitW = portraitPaneW - 20;
        int portraitH = portraitPaneH - 62;

        g2.setColor(new Color(0, 0, 0, 145));
        g2.fillRoundRect(portraitX, portraitY, portraitW, portraitH, 10, 10);

        if (portraitImage != null) {
            double sx = portraitW / (double) portraitImage.getWidth();
            double sy = portraitH / (double) portraitImage.getHeight();
            double scale = Math.min(sx, sy);
            int dw = Math.max(1, (int) Math.round(portraitImage.getWidth() * scale));
            int dh = Math.max(1, (int) Math.round(portraitImage.getHeight() * scale));
            int dx = portraitX + (portraitW - dw) / 2;
            int dy = portraitY + (portraitH - dh) / 2;
            g2.drawImage(portraitImage, dx, dy, dw, dh, null);
        }

        g2.setColor(new Color(255, 255, 255, 118));
        g2.drawRoundRect(portraitX, portraitY, portraitW, portraitH, 10, 10);

        g2.setFont(new Font("Consolas", Font.BOLD, 13));
        g2.setColor(new Color(255, 245, 210, 225));
        g2.drawString(ctx.activeCrewStation.name(), portraitPaneX + 12, portraitPaneY + 16);

        int panelX = portraitPaneX + portraitPaneW + 14;
        int panelW = x + w - panelX - 14;
        int textRight = x + w - 18;

        int tabX = panelX + 8;
        int tabY = y + 70;
        int tabGap = 8;
        int stationCount = GameContext.CrewStation.values().length;
        int tw = Math.max(104, (panelW - 16 - tabGap * (stationCount - 1)) / stationCount);

        for (GameContext.CrewStation station : GameContext.CrewStation.values()) {
            boolean active = (station == ctx.activeCrewStation);
            boolean auto = UISystem.stationAutomation(ctx, station);
            g2.setColor(active ? new Color(255, 220, 140, 180) : new Color(255, 255, 255, 45));
            g2.fillRoundRect(tabX, tabY, tw, 24, 10, 10);
            g2.setColor(active ? new Color(255, 245, 210, 220) : new Color(255, 255, 255, 120));
            g2.drawRoundRect(tabX, tabY, tw, 24, 10, 10);

            CrewPortraitSystem.PortraitAsset iconAsset = CrewPortraitSystem.getPortrait(station);
            BufferedImage icon = iconAsset.image();
            if (icon != null) {
                g2.drawImage(icon, tabX + 6, tabY + 4, 16, 16, null);
            }

            g2.setFont(new Font("Consolas", active ? Font.BOLD : Font.PLAIN, 12));
            g2.setColor(new Color(250, 250, 250, 220));
            g2.drawString(station.name(), tabX + 26, tabY + 16);
            g2.setColor(auto ? new Color(120, 255, 170, 220) : new Color(255, 150, 140, 220));
            g2.drawString(auto ? "AI" : "MAN", tabX + tw - 34, tabY + 16);
            tabX += tw + tabGap;
        }

        int readoutX = panelX + 12;
        int ly = y + 126;

        Shape oldClip = g2.getClip();
        g2.setClip(new Rectangle(readoutX - 4, y + 92, Math.max(20, textRight - readoutX), h - 108));

        g2.setColor(new Color(255, 255, 255, 210));
        g2.setFont(new Font("Consolas", Font.BOLD, 14));
        g2.drawString("Current Readouts", readoutX, ly);
        ly += 20;

        int lockDist = -1;
        if (ctx.lockedTarget != null && ctx.lockedTarget.alive) {
            lockDist = (int) Math.round(Math.hypot(ctx.lockedTarget.x - ctx.player.x, ctx.lockedTarget.y - ctx.player.y));
        }
        boolean sensorsOnline = !ctx.player.isSystemDestroyed(Ship.InternalSystem.SENSORS);

        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(new Color(210, 235, 255, 220));
        g2.drawString("Captain: " + ctx.captainDirective + "   Helm: " + ctx.helmMode + "   Tactical: " + ctx.tacticalMode, readoutX, ly);
        ly += 16;
        g2.drawString("Engineering: " + ctx.engineeringMode + "   Fleet: " + ctx.alliedFleetCommand + " / " + ctx.alliedFleetFormation, readoutX, ly);
        ly += 16;
        g2.drawString("Engineering Priority: " + ctx.player.engineeringPriority()
                + "   Overload: " + (ctx.player.isOverloadActive() ? ("ACTIVE " + ctx.player.overloadBus().name()) : "STANDBY")
                + "   Heat " + (int) Math.round(ctx.player.overloadHeat() * 100.0) + "%", readoutX, ly);
        ly += 16;
        g2.drawString("Emergency Thrust: " + (ctx.player.isEmergencyThrustActive() ? "ACTIVE" : "STANDBY")
                + "   Heat " + (int) Math.round(ctx.player.emergencyThrustHeat() * 100.0) + "%"
                + "   Cooldown " + (int) Math.ceil(ctx.player.emergencyThrustCooldownRemaining()) + "s"
                + "   Propulsion " + (int) Math.round(ctx.player.propulsionRoomIntegrity() * 100.0) + "%", readoutX, ly);
        ly += 16;
        g2.drawString("Lock: " + ((ctx.lockedTarget == null) ? "NONE" : (ctx.lockedTarget.name + " (" + Math.max(0, lockDist) + "m)"))
                + "   Science EW: " + (ctx.scienceJamming ? "JAMMING" : "PASSIVE"), readoutX, ly);
        ly += 16;
        g2.drawString("Sensors: " + (sensorsOnline ? "ONLINE" : "DISABLED"), readoutX, ly);
        ly += 16;
        g2.drawString("Crew: " + ctx.player.crewOrder + "  Readiness " + (int) Math.round(ctx.player.crewReadiness() * 100.0) + "%", readoutX, ly);
        ly += 16;
        int fireRooms = ctx.player.activeFireRoomCount();
        double fireLoad = ctx.player.totalFireIntensity();
        ShipRoomLayout.RoomId hotspot = ctx.player.hottestFireRoom();
        String hotspotLabel = "NONE";
        if (hotspot != null) {
            ShipRoomLayout.RoomDef hotspotDef = ShipRoomLayout.roomForId(ctx.player.role, ctx.player.faction, hotspot);
            if (hotspotDef != null && hotspotDef.label != null && !hotspotDef.label.isBlank()) {
                hotspotLabel = hotspotDef.label;
            } else {
                hotspotLabel = hotspot.name();
            }
        }
        g2.drawString("Hazards: FIRE " + fireRooms + " room" + (fireRooms == 1 ? "" : "s")
                + "  Load " + String.format("%.1f", fireLoad)
                + "  Hotspot " + hotspotLabel, readoutX, ly);
        ly += 16;
        String voice = (ctx.voiceCaptionT > 0.0 && ctx.voiceCaption != null && !ctx.voiceCaption.isBlank()) ? ctx.voiceCaption : "IDLE";
        g2.drawString("Voice: " + voice, readoutX, ly);
        ly += 16;
        g2.drawString("Captions: " + (ctx.voiceCaptionsEnabled ? "ON" : "OFF")
                + "   Mix Focus: " + ctx.voiceMixFocus.name()
                + " (" + (int) Math.round(ctx.voiceRoleVolume(ctx.voiceMixFocus) * 100.0) + "%)", readoutX, ly);
        ly += 16;
        g2.drawString("Role Volumes C/H/T/E/S: "
                + (int) Math.round(ctx.voiceRoleVolume(GameContext.CrewStation.CAPTAIN) * 100.0) + "/"
                + (int) Math.round(ctx.voiceRoleVolume(GameContext.CrewStation.HELM) * 100.0) + "/"
                + (int) Math.round(ctx.voiceRoleVolume(GameContext.CrewStation.TACTICAL) * 100.0) + "/"
                + (int) Math.round(ctx.voiceRoleVolume(GameContext.CrewStation.ENGINEERING) * 100.0) + "/"
                + (int) Math.round(ctx.voiceRoleVolume(GameContext.CrewStation.SCIENCE) * 100.0), readoutX, ly);

        ly += 28;
        g2.setColor(new Color(255, 255, 255, 220));
        g2.setFont(new Font("Consolas", Font.BOLD, 14));
        g2.drawString("Station Controls", readoutX, ly);
        ly += 20;
        g2.setFont(new Font("Consolas", Font.PLAIN, 12));

        switch (ctx.activeCrewStation) {
            case CAPTAIN -> {
                g2.setColor(new Color(255, 230, 175, 220));
                g2.drawString("1 BALANCED  2 ATTACK  3 DEFENSE  4 EMERGENCY  5 MINE", readoutX, ly);
                ly += 16;
                g2.drawString("6 ESCORT  7 DEFEND  8 REPAIR  9 RTB  0 CYCLE FLEET FORMATION", readoutX, ly);
                ly += 16;
                g2.drawString("Q/W/E/R assign nearest friendly ATTACK/DEFEND/REPAIR/RTB, T clears override.", readoutX, ly);
                ly += 16;
                g2.drawString("- or BACKSPACE: charge 10s battlefield warp to waypoint/base (damage disrupts).", readoutX, ly);
                ly += 16;
                g2.drawString("Captain directives set ship posture and allied fleet command behavior.", readoutX, ly);
            }
            case HELM -> {
                g2.setColor(new Color(200, 240, 255, 220));
                g2.drawString("1 INTERCEPT  2 ORBIT  3 MAINTAIN RANGE  4 EVASIVE  5 E-THRUST", readoutX, ly);
                ly += 16;
                g2.drawString("Emergency thrust adds burst speed but can overheat propulsion into cooldown.", readoutX, ly);
                ly += 16;
                g2.drawString("Helm automation sets heading/throttle for target pursuit and maneuvering.", readoutX, ly);
            }
            case TACTICAL -> {
                g2.setColor(new Color(255, 210, 180, 220));
                g2.drawString("1 HOLD FIRE  2 DEFENSIVE FIRE  3 AGGRESSIVE FIRE", readoutX, ly);
                ly += 16;
                g2.drawString("Tactical automation drives primary/secondary firing states and lock usage.", readoutX, ly);
            }
            case ENGINEERING -> {
                g2.setColor(new Color(200, 255, 200, 220));
                g2.drawString("1 BALANCED  2 ATTACK BIAS  3 DEFENSE BIAS  4 DAMAGE CONTROL", readoutX, ly);
                ly += 16;
                g2.drawString("5 OVERLOAD ON/OFF  6 CYCLE OVERLOAD BUS  7 CYCLE REPAIR PRIORITY  8 SUPPRESS FIRE", readoutX, ly);
                ly += 16;
                g2.drawString("Engineering automation enforces policy table; manual edits override AI immediately.", readoutX, ly);
            }
            case SCIENCE -> {
                g2.setColor(new Color(220, 210, 255, 220));
                g2.drawString("1 LOCK NEAREST  2 CLEAR LOCK  3 TOGGLE EW/JAMMING", readoutX, ly);
                ly += 16;
                g2.drawString("Science automation manages target acquisition using current sensor capability.", readoutX, ly);
            }
        }

        ly += 24;
        g2.setColor(new Color(190, 245, 220, 220));
        g2.drawString("Voice: C captions on/off  Z/X role focus  ,/. volume -/+  (persisted)", readoutX, ly);

        g2.setClip(oldClip);

        g2.setColor(new Color(255, 255, 255, 145));
        g2.drawString("Manual flight/fire/power input immediately disables corresponding station AI.", readoutX, y + h - 16);
    }

    private static final class XrayStackLayout {
        final int panelX;
        final int panelW;
        final int targetX;
        final int playerY;
        final int playerH;
        final int targetY;
        final int targetH;
        final boolean targetVisible;

        XrayStackLayout(int panelX, int panelW, int targetX, int playerY, int playerH, int targetY, int targetH, boolean targetVisible) {
            this.panelX = panelX;
            this.panelW = panelW;
            this.targetX = targetX;
            this.playerY = playerY;
            this.playerH = playerH;
            this.targetY = targetY;
            this.targetH = targetH;
            this.targetVisible = targetVisible;
        }
    }

    private static final class XrayPanelFrameCache {
        BufferedImage image;
        int width;
        int height;
        int titleHash;
        int subtitleHash;
        boolean interactive;
        GameContext.XrayFilterMode filterMode;
        ShipRoomLayout.RoomId focusedRoom;
        int cursorX;
        int cursorY;
        ShipRoomLayout.RoomId hoveredRoom;
        long renderedAtNanos;
    }

    private static XrayPanelFrameCache xrayPanelCacheFor(Ship ship) {
        return XRAY_PANEL_CACHE.computeIfAbsent(ship, k -> new XrayPanelFrameCache());
    }

    private static BufferedImage ensureXrayPanelImage(XrayPanelFrameCache cache, int w, int h) {
        if (cache.image == null || cache.width != w || cache.height != h) {
            cache.image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            cache.width = w;
            cache.height = h;
        }
        return cache.image;
    }

    private static boolean canReuseXrayPanelCache(XrayPanelFrameCache cache,
                                                  long nowNanos,
                                                  int w, int h,
                                                  String title, String subtitle,
                                                  boolean interactive,
                                                  GameContext.XrayFilterMode filterMode,
                                                  ShipRoomLayout.RoomId focusedRoom,
                                                  int cursorX, int cursorY) {
        if (cache == null || cache.image == null) return false;
        if (cache.width != w || cache.height != h) return false;
        if (cache.interactive != interactive) return false;
        if ((nowNanos - cache.renderedAtNanos) > XRAY_PANEL_FRAME_CACHE_NS) return false;
        if (cache.titleHash != java.util.Objects.hashCode(title)) return false;
        if (cache.subtitleHash != java.util.Objects.hashCode(subtitle)) return false;
        if (!interactive) return true;
        if (cache.filterMode != filterMode) return false;
        if (cache.focusedRoom != focusedRoom) return false;
        return cache.cursorX == cursorX && cache.cursorY == cursorY;
    }

    private static void updateXrayPanelCacheMeta(XrayPanelFrameCache cache,
                                                 long nowNanos,
                                                 String title, String subtitle,
                                                 boolean interactive,
                                                 GameContext.XrayFilterMode filterMode,
                                                 ShipRoomLayout.RoomId focusedRoom,
                                                 int cursorX, int cursorY,
                                                 ShipRoomLayout.RoomId hoveredRoom) {
        cache.renderedAtNanos = nowNanos;
        cache.titleHash = java.util.Objects.hashCode(title);
        cache.subtitleHash = java.util.Objects.hashCode(subtitle);
        cache.interactive = interactive;
        cache.filterMode = filterMode;
        cache.focusedRoom = focusedRoom;
        cache.cursorX = cursorX;
        cache.cursorY = cursorY;
        cache.hoveredRoom = hoveredRoom;
    }

    public static ShipRoomLayout.RoomId playerXrayRoomAt(GameContext ctx, int viewW, int viewH, int mouseX, int mouseY) {
        if (ctx == null || ctx.player == null) return null;
        XrayStackLayout layout = computeXrayStackLayout(ctx.player, ctx.lockedTarget, ctx.shopOpen, viewW, viewH);
        if (layout == null) return null;
        Rectangle mapRect = xrayMapRect(layout.panelX, layout.playerY, layout.panelW, layout.playerH);
        if (!mapRect.contains(mouseX, mouseY)) return null;
        for (ShipRoomLayout.RoomDef room : ShipRoomLayout.profileFor(ctx.player.role, ctx.player.faction)) {
            if (room == null || room.id == null) continue;
            Polygon p = xrayRoomPolygon(mapRect.x, mapRect.y, mapRect.width, mapRect.height, room.xs, room.ys);
            if (p != null && p.contains(mouseX, mouseY)) return room.id;
        }
        return null;
    }

    private static void drawShipXrayPanel(Graphics2D g2, GameContext ctx, Ship ship, int x, int y, int w, int h,
                                          String title, String subtitle, boolean interactive) {
        if (g2 == null || ship == null) return;
        if (w < 80 || h < 80) return;

        long nowNanos = System.nanoTime();
        GameContext.XrayFilterMode filterMode = (ctx == null || ctx.xrayFilterMode == null)
                ? GameContext.XrayFilterMode.ALL
                : ctx.xrayFilterMode;
        ShipRoomLayout.RoomId focusedRoom = (ctx == null) ? null : ctx.xrayFocusedRoom;
        int cursorX = (ctx == null) ? Integer.MIN_VALUE : (int) Math.round(ctx.cursorScreenX);
        int cursorY = (ctx == null) ? Integer.MIN_VALUE : (int) Math.round(ctx.cursorScreenY);

        XrayPanelFrameCache cache = xrayPanelCacheFor(ship);
        if (canReuseXrayPanelCache(cache, nowNanos, w, h, title, subtitle, interactive, filterMode, focusedRoom, cursorX, cursorY)) {
            if (interactive && ctx != null) ctx.xrayHoveredRoom = cache.hoveredRoom;
            g2.drawImage(cache.image, x, y, null);
            return;
        }

        BufferedImage panelImage = ensureXrayPanelImage(cache, w, h);
        Graphics2D cg = panelImage.createGraphics();
        try {
            cg.setComposite(AlphaComposite.Clear);
            cg.fillRect(0, 0, w, h);
            cg.setComposite(AlphaComposite.SrcOver);
            cg.translate(-x, -y);
            drawShipXrayPanelImmediate(cg, ctx, ship, x, y, w, h, title, subtitle, interactive);
        } finally {
            cg.dispose();
        }

        ShipRoomLayout.RoomId hoveredRoom = (interactive && ctx != null) ? ctx.xrayHoveredRoom : null;
        updateXrayPanelCacheMeta(
                cache,
                nowNanos,
                title, subtitle,
                interactive,
                filterMode,
                focusedRoom,
                cursorX, cursorY,
                hoveredRoom
        );
        g2.drawImage(panelImage, x, y, null);
    }

    private static void drawShipXrayPanelImmediate(Graphics2D g2, GameContext ctx, Ship ship, int x, int y, int w, int h,
                                                   String title, String subtitle, boolean interactive) {
        if (g2 == null || ship == null) return;
        if (w < 80 || h < 80) return;
        long nowNanos = System.nanoTime();

        g2.setColor(new Color(16, 20, 28, 206));
        g2.fillRoundRect(x, y, w, h, 12, 12);
        g2.setColor(new Color(170, 210, 255, 115));
        g2.drawRoundRect(x, y, w, h, 12, 12);

        g2.setColor(new Color(205, 235, 255, 220));
        g2.setFont(XRAY_TITLE_FONT);
        g2.drawString((title == null || title.isBlank()) ? "TACTICAL X-RAY" : title, x + 10, y + 18);
        g2.setFont(XRAY_SUBTITLE_FONT);
        g2.setColor(new Color(175, 218, 255, 205));
        if (subtitle != null && !subtitle.isBlank()) {
            g2.drawString(subtitle, x + 10, y + 32);
        }

        Rectangle mapRect = xrayMapRect(x, y, w, h);
        int mapX = mapRect.x;
        int mapY = mapRect.y;
        int mapW = mapRect.width;
        int mapH = mapRect.height;
        g2.setColor(new Color(255, 255, 255, 20));
        g2.fillRoundRect(mapX, mapY, mapW, mapH, 10, 10);
        g2.setColor(new Color(255, 255, 255, 60));
        g2.drawRoundRect(mapX, mapY, mapW, mapH, 10, 10);

        drawXrayShipUnderlay(g2, ship, mapRect, nowNanos);

        g2.setColor(new Color(255, 255, 255, 20));
        g2.drawLine(mapX + mapW / 2, mapY + 6, mapX + mapW / 2, mapY + mapH - 6);
        g2.drawLine(mapX + 6, mapY + mapH / 2, mapX + mapW - 6, mapY + mapH / 2);

        double[] hitFlash = new double[ShipRoomLayout.RoomId.values().length];
        List<ShipRoomLayout.RoomDef> rooms = ShipRoomLayout.profileFor(ship.role, ship.faction);
        refreshXrayPercentCache(ship, rooms, nowNanos);
        EnumMap<ShipRoomLayout.RoomId, Integer> pctCache = xrayPercentCacheFor(ship);

        List<Ship.RoomDamageEvent> events = ship.recentRoomDamageEvents();
        if (events != null) {
            for (int i = events.size() - 1; i >= 0; i--) {
                Ship.RoomDamageEvent ev = events.get(i);
                if (ev == null || ev.roomId == null) continue;
                double ageSec = (nowNanos - ev.timestampNanos) / 1_000_000_000.0;
                if (ageSec < 0.0 || ageSec > 2.4) continue;
                double strength = Math.max(0.0, 1.0 - ageSec / 2.4);
                int roomIdx = ev.roomId.ordinal();
                if (roomIdx < 0 || roomIdx >= hitFlash.length) continue;
                if (strength > hitFlash[roomIdx]) hitFlash[roomIdx] = strength;
            }
        }

        GameContext.XrayFilterMode filterMode = (ctx == null || ctx.xrayFilterMode == null)
                ? GameContext.XrayFilterMode.ALL
                : ctx.xrayFilterMode;
        ShipRoomLayout.RoomId focusedRoom = (ctx == null) ? null : ctx.xrayFocusedRoom;
        ShipRoomLayout.RoomId hoveredRoom = null;
        int cursorX = (ctx == null) ? Integer.MIN_VALUE : (int) Math.round(ctx.cursorScreenX);
        int cursorY = (ctx == null) ? Integer.MIN_VALUE : (int) Math.round(ctx.cursorScreenY);
        ShipRoomLayout.RoomId repairRoom = xrayRepairTargetRoom(ship);

        String hottestRoomLabel = null;
        double hottestHit = 0.0;

        g2.setFont(XRAY_SYMBOL_FONT);
        FontMetrics symFm = g2.getFontMetrics();
        g2.setFont(XRAY_HP_FONT);
        FontMetrics hpFm = g2.getFontMetrics();

        List<ShipRoomLayout.VisualCell> visualCells = ShipRoomLayout.visualCellsFor(ship.role, ship.faction);
        List<ShipRoomLayout.VisualCell> drawCells = new ArrayList<>();
        List<Polygon> cellPolygons = new ArrayList<>();
        for (ShipRoomLayout.VisualCell cell : visualCells) {
            if (cell == null || cell.roomId == null) continue;
            Polygon p = xrayRoomPolygon(mapX, mapY, mapW, mapH, cell.xs, cell.ys);
            if (p == null || p.npoints < 3) continue;
            drawCells.add(cell);
            cellPolygons.add(p);
            if (interactive && p.contains(cursorX, cursorY)) hoveredRoom = cell.roomId;
        }
        if (interactive && ctx != null) ctx.xrayHoveredRoom = hoveredRoom;

        Stroke oldStroke = g2.getStroke();
        for (int cellIdx = 0; cellIdx < drawCells.size(); cellIdx++) {
            ShipRoomLayout.VisualCell cell = drawCells.get(cellIdx);
            Polygon p = cellPolygons.get(cellIdx);
            ShipRoomLayout.RoomDef room = ShipRoomLayout.roomForId(ship.role, ship.faction, cell.roomId);
            if (room == null || cell.roomId == null || p == null || p.npoints < 3) continue;

            int pctVal = pctCache.getOrDefault(cell.roomId, -1);
            if (pctVal < 0) {
                pctVal = MathUtil.clamp((int) Math.round(ship.roomHealthFraction(cell.roomId) * 100.0), 0, 100);
            }
            double frac = pctVal * 0.01;
            double fireIntensity = ship.roomFireIntensity(cell.roomId);
            int roomIdx = cell.roomId.ordinal();
            double hitStrength = (roomIdx >= 0 && roomIdx < hitFlash.length) ? hitFlash[roomIdx] : 0.0;
            boolean disabled = pctVal <= 0 || (room.primarySystem != null && ship.isSystemDestroyed(room.primarySystem));
            double powerIntensity = xrayPowerRoutingIntensity(ship, room);
            boolean powerOutOfBand = Math.abs(powerIntensity - xrayNominalPowerTarget(room)) >= 0.035;
            boolean filteredIn = xrayRoomMatchesFilter(filterMode, frac, fireIntensity, disabled, powerOutOfBand);
            boolean focused = interactive && focusedRoom == cell.roomId;
            boolean hovered = interactive && hoveredRoom == cell.roomId;

            Color fill;
            if (!filteredIn) fill = new Color(70, 78, 96, 66);
            else if (disabled) fill = new Color(120, 120, 132, 132);
            else if (frac > 0.70) fill = new Color(95, 210, 255, 88);
            else if (frac > 0.35) fill = new Color(255, 195, 90, 120);
            else fill = new Color(255, 82, 82, 155);

            g2.setColor(fill);
            g2.fillPolygon(p);
            g2.setColor(new Color(220, 245, 255, filteredIn ? 130 : 65));
            g2.drawPolygon(p);

            if (hitStrength > 0.01) {
                int a = MathUtil.clamp((int) Math.round(130 + hitStrength * 110), 0, 255);
                g2.setStroke(XRAY_HIT_STROKE);
                g2.setColor(new Color(255, 245, 145, a));
                g2.drawPolygon(p);
                g2.setStroke(oldStroke);
                if (hitStrength > hottestHit) {
                    hottestHit = hitStrength;
                    hottestRoomLabel = xrayRoomDisplayLabel(cell.roomId);
                }
            }

            if (disabled) {
                Rectangle b = p.getBounds();
                g2.setColor(new Color(20, 22, 28, 180));
                g2.setStroke(XRAY_DISABLED_STROKE);
                g2.drawLine(b.x + 2, b.y + 2, b.x + b.width - 2, b.y + b.height - 2);
                g2.drawLine(b.x + 2, b.y + b.height - 2, b.x + b.width - 2, b.y + 2);
                g2.setStroke(oldStroke);
            }

            Rectangle b = p.getBounds();
            int cx = (int) Math.round(b.getCenterX());
            int cy = (int) Math.round(b.getCenterY());

            if (b.width >= 10 && b.height >= 8) {
                String symbol = xrayRoomSymbol(cell.roomId);
                Font labelFont = (b.width >= 20 && b.height >= 14) ? XRAY_SYMBOL_FONT : XRAY_REPAIR_FONT;
                g2.setFont(labelFont);
                FontMetrics labelFm = g2.getFontMetrics();
                int sw = labelFm.stringWidth(symbol);
                int sh = labelFm.getAscent();
                int sx = cx - sw / 2 - 4;
                int sy = cy - (sh + 5) / 2 - 1;
                Color symBg = focused
                        ? new Color(120, 210, 255, 200)
                        : (hovered ? new Color(80, 190, 255, 185)
                        : (hitStrength > 0.01)
                        ? new Color(255, 96, 72, MathUtil.clamp((int) Math.round(140 + 85 * hitStrength), 0, 255))
                        : new Color(18, 28, 44, filteredIn ? 156 : 96));
                g2.setColor(symBg);
                g2.fillRoundRect(sx, sy, sw + 8, sh + 5, 8, 8);
                g2.setColor(new Color(220, 245, 255, 190));
                g2.drawRoundRect(sx, sy, sw + 8, sh + 5, 8, 8);
                g2.setColor(new Color(250, 252, 255, 230));
                g2.drawString(symbol, sx + 4, sy + sh);
            }

            if (cell.labelAnchor && b.width >= 28 && b.height >= 20) {
                String pct = XRAY_PCT_LABELS[MathUtil.clamp(pctVal, 0, 100)];
                g2.setFont(XRAY_HP_FONT);
                int px = cx - hpFm.stringWidth(pct) / 2;
                int py = Math.min(b.y + b.height - 4, cy + Math.max(8, b.height / 4));
                g2.setColor(new Color(245, 250, 255, filteredIn ? 220 : 120));
                g2.drawString(pct, px, py);
            }

            // Overlay: repair team/task marker
            if (repairRoom == cell.roomId) {
                g2.setColor(new Color(145, 255, 170, 200));
                g2.fillOval(cx - 4, cy + 14, 8, 8);
                g2.setColor(new Color(10, 35, 16, 220));
                g2.setFont(XRAY_REPAIR_FONT);
                g2.drawString("R", cx - 3, cy + 21);
            }
            // Overlay: power routing intensity bar
            int barX = b.x + 2;
            int barY = b.y + b.height - 4;
            int barW = Math.max(6, b.width - 4);
            int barFill = MathUtil.clamp((int) Math.round(barW * MathUtil.clamp(powerIntensity / 0.36, 0.0, 1.0)), 0, barW);
            g2.setColor(new Color(16, 18, 26, 140));
            g2.fillRect(barX, barY, barW, 2);
            g2.setColor(new Color(
                    MathUtil.clamp((int) Math.round(255 - powerIntensity * 420), 70, 255),
                    MathUtil.clamp((int) Math.round(110 + powerIntensity * 320), 80, 255),
                    255,
                    filteredIn ? 205 : 95
            ));
            g2.fillRect(barX, barY, barFill, 2);

            if (focused || hovered) {
                g2.setStroke(XRAY_FOCUS_STROKE);
                g2.setColor(new Color(130, 220, 255, 230));
                g2.drawPolygon(p);
                g2.setStroke(oldStroke);
            }
        }
        g2.setStroke(oldStroke);

        ShipRoomLayout.RoomId detailRoom = (interactive && hoveredRoom != null) ? hoveredRoom : focusedRoom;
        if (detailRoom != null) {
            boolean present = false;
            for (ShipRoomLayout.VisualCell cell : drawCells) {
                if (cell != null && detailRoom == cell.roomId) {
                    present = true;
                    break;
                }
            }
            if (!present) detailRoom = null;
        }
        if (interactive && detailRoom != null) {
            ShipRoomLayout.RoomDef roomDef = ShipRoomLayout.roomForId(ship.role, ship.faction, detailRoom);
            int pct = MathUtil.clamp((int) Math.round(ship.roomHealthFraction(detailRoom) * 100.0), 0, 100);
            double fire = ship.roomFireIntensity(detailRoom);
            String roomLabel = xrayRoomDisplayLabel(detailRoom);
            double power = xrayPowerRoutingIntensity(ship, roomDef);
            String line = roomLabel + "  HP " + pct + "%  FIRE " + String.format("%.2f", fire) + "  POWER " + (int) Math.round(power * 100.0) + "%";
            g2.setFont(XRAY_META_FONT);
            g2.setColor(new Color(220, 244, 255, 220));
            g2.drawString(line, x + 10, y + h - 10);
            drawXrayTooltip(g2, mapRect, cursorX, cursorY, roomLabel, pct, fire, power, ship, roomDef);
        } else if (hottestRoomLabel != null && hottestHit > 0.01) {
            g2.setFont(XRAY_META_FONT);
            g2.setColor(new Color(255, 228, 164, 230));
            g2.drawString("HIT ROOM: " + hottestRoomLabel, x + 10, y + h - 10);
        } else {
            g2.setFont(XRAY_META_FONT);
            g2.setColor(new Color(170, 210, 240, 180));
            g2.drawString("HIT ROOM: NONE", x + 10, y + h - 10);
        }

        g2.setFont(XRAY_META_FONT);
        g2.setColor(new Color(190, 230, 255, 180));
        g2.drawString("RED<35%  AMBER<70%  BLUE>=70%", x + 10, y + h - 34);
        String filterLabel = (filterMode == null) ? "ALL" : filterMode.name();
        String focusLabel = (focusedRoom == null) ? "NONE" : xrayRoomDisplayLabel(focusedRoom);
        g2.drawString("FILTER[" + filterLabel + "] ` cycle   ' clear focus   FOCUS: " + focusLabel, x + 10, y + h - 22);
    }

    private static Rectangle xrayMapRect(int panelX, int panelY, int panelW, int panelH) {
        return new Rectangle(
                panelX + 10,
                panelY + 38,
                Math.max(20, panelW - 20),
                Math.max(20, panelH - 76)
        );
    }

    private static boolean xrayRoomMatchesFilter(GameContext.XrayFilterMode mode,
                                                 double hpFrac, double fireIntensity,
                                                 boolean disabled, boolean powerOutOfBand) {
        if (mode == null || mode == GameContext.XrayFilterMode.ALL) return true;
        return switch (mode) {
            case DAMAGE -> hpFrac < 0.99 || disabled;
            case HAZARD -> fireIntensity > 0.05;
            case POWER -> powerOutOfBand;
            case DISABLED -> disabled;
            default -> true;
        };
    }

    private static double xrayPowerRoutingIntensity(Ship ship, ShipRoomLayout.RoomDef room) {
        if (ship == null || room == null) return 0.0;
        Ship.InternalSystem system = room.primarySystem;
        if (system == null) return ship.powerAuxiliaryFrac();
        return switch (system) {
            case ENGINES, WARP_ENGINES -> ship.powerEnginesFrac();
            case SHIELDS -> ship.powerShieldsFrac();
            case WEAPONS, MAGAZINES -> ship.powerWeaponsFrac();
            case SENSORS, BRIDGE -> ship.powerSensorsFrac();
            case REACTOR_CORE -> ship.powerEngineeringFrac();
        };
    }

    private static double xrayNominalPowerTarget(ShipRoomLayout.RoomDef room) {
        if (room == null || room.primarySystem == null) return 0.12;
        return switch (room.primarySystem) {
            case ENGINES, WARP_ENGINES, SHIELDS, WEAPONS, MAGAZINES, REACTOR_CORE -> 0.18;
            case SENSORS, BRIDGE -> 0.15;
        };
    }

    private static void drawXrayShipUnderlay(Graphics2D g2, Ship ship, Rectangle mapRect, long nowNanos) {
        if (g2 == null || ship == null || mapRect == null) return;

        Graphics2D ug = (Graphics2D) g2.create();
        try {
            RoundRectangle2D.Float clipShape = new RoundRectangle2D.Float(
                    mapRect.x, mapRect.y, mapRect.width, mapRect.height, 10, 10
            );
            ug.clip(clipShape);

            Polygon hull = xrayHullPolygon(ship.role, ship.faction, mapRect);
            if (hull != null && hull.npoints >= 3) {
                Rectangle hb = hull.getBounds();
                if (hb.width > 0 && hb.height > 0) {
                    BufferedImage skin = ShipSkinLibrary.getSkin(ship.role, ship.faction);
                    if (skin != null) {
                        double scale = Math.min(hb.width / (double) Math.max(1, skin.getWidth()),
                                hb.height / (double) Math.max(1, skin.getHeight()));
                        int drawW = Math.max(1, (int) Math.round(skin.getWidth() * scale));
                        int drawH = Math.max(1, (int) Math.round(skin.getHeight() * scale));
                        int dx = (int) Math.round(hb.getCenterX() - drawW / 2.0);
                        int dy = (int) Math.round(hb.getCenterY() - drawH / 2.0);
                        ug.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.17f));
                        ug.drawImage(skin, dx, dy, drawW, drawH, null);
                        ug.setComposite(AlphaComposite.SrcOver);
                    }

                    Shape oldClip = ug.getClip();
                    ug.clip(hull);
                    float pulse = (float) (0.5 + 0.5 * Math.sin(nowNanos / 220_000_000.0));
                    ug.setPaint(new GradientPaint(
                            hb.x, hb.y, new Color(78, 134, 180, 34 + (int) Math.round(10 * pulse)),
                            hb.x, hb.y + hb.height, new Color(18, 34, 56, 10)
                    ));
                    ug.fillRect(hb.x, hb.y, hb.width, hb.height);
                    ug.setClip(oldClip);

                    ug.setStroke(new BasicStroke(1.1f));
                    ug.setColor(new Color(165, 218, 255, 72));
                    ug.drawPolygon(hull);
                    ug.setColor(new Color(160, 222, 255, 28));
                    ug.drawLine(hb.x + 6, (int) Math.round(hb.getCenterY()), hb.x + hb.width - 6, (int) Math.round(hb.getCenterY()));

                    for (int i = 1; i <= 4; i++) {
                        int sx = hb.x + (int) Math.round(hb.width * (i / 5.0));
                        ug.drawLine(sx, hb.y + 6, sx, hb.y + hb.height - 6);
                    }
                }
            }

            int scanAlpha = 10 + (int) Math.round(4 * (0.5 + 0.5 * Math.sin(nowNanos / 180_000_000.0)));
            ug.setColor(new Color(170, 228, 255, scanAlpha));
            for (int yy = mapRect.y + 3; yy < mapRect.y + mapRect.height; yy += 6) {
                ug.drawLine(mapRect.x + 4, yy, mapRect.x + mapRect.width - 4, yy);
            }
        } finally {
            ug.dispose();
        }
    }

    private static Polygon xrayHullPolygon(ShipRole role, Rectangle mapRect) {
        return xrayHullPolygon(role, null, mapRect);
    }

    private static Polygon xrayHullPolygon(ShipRole role, Faction faction, Rectangle mapRect) {
        if (mapRect == null) return null;
        Polygon hull = ShipHullSilhouette.hullPolygon(role, 100.0, faction);
        if (hull == null || hull.npoints < 3) return null;

        Rectangle b = hull.getBounds();
        if (b.width <= 0 || b.height <= 0) return null;

        double cx = b.getCenterX();
        double cy = b.getCenterY();
        double halfW = Math.max(1.0, b.width * 0.5);
        double halfH = Math.max(1.0, b.height * 0.5);
        double[] normalizedXs = new double[hull.npoints];
        double[] normalizedYs = new double[hull.npoints];
        for (int i = 0; i < hull.npoints; i++) {
            normalizedXs[i] = MathUtil.clamp(((hull.xpoints[i] - cx) / halfW) * 0.98, -1.0, 1.0);
            normalizedYs[i] = MathUtil.clamp(((hull.ypoints[i] - cy) / halfH) * 0.98, -1.0, 1.0);
        }
        return xrayRoomPolygon(mapRect.x, mapRect.y, mapRect.width, mapRect.height, normalizedXs, normalizedYs);
    }

    private static ShipRoomLayout.RoomId xrayRepairTargetRoom(Ship ship) {
        if (ship == null) return null;
        if (ship.crewOrder != Ship.CrewOrder.DAMAGE_CONTROL) return null;
        ShipRoomLayout.RoomId hotspot = ship.hottestFireRoom();
        if (hotspot != null) return hotspot;
        ShipRoomLayout.RoomId best = null;
        double lowest = 1.0;
        for (Ship.RoomStatus rs : ship.roomStatusSnapshot()) {
            if (rs == null || rs.roomId == null) continue;
            if (rs.hpMax <= 1e-9) continue;
            double frac = MathUtil.clamp(rs.hp / rs.hpMax, 0.0, 1.0);
            if (frac < lowest) {
                lowest = frac;
                best = rs.roomId;
            }
        }
        return (lowest < 0.995) ? best : null;
    }

    private static String xrayRoomDisplayLabel(ShipRoomLayout.RoomId roomId) {
        return ShipRoomLayout.displayLabel(roomId);
    }

    private static void drawXrayTooltip(Graphics2D g2, Rectangle mapRect, int cursorX, int cursorY,
                                        String roomLabel, int hpPct, double fireIntensity, double powerIntensity,
                                        Ship ship, ShipRoomLayout.RoomDef roomDef) {
        if (g2 == null || mapRect == null) return;
        if (!mapRect.contains(cursorX, cursorY)) return;
        String system = (roomDef == null || roomDef.primarySystem == null)
                ? "AUX"
                : roomDef.primarySystem.name();
        String line1 = roomLabel;
        String line2 = "HP " + hpPct + "%  FIRE " + String.format("%.2f", fireIntensity)
                + "  POWER " + (int) Math.round(powerIntensity * 100.0) + "%";
        String line3 = "SYSTEM " + system + "  " + ((ship != null && roomDef != null && roomDef.primarySystem != null
                && ship.isSystemDestroyed(roomDef.primarySystem)) ? "DISABLED" : "ONLINE");

        Font oldFont = g2.getFont();
        g2.setFont(XRAY_META_FONT);
        FontMetrics fm = g2.getFontMetrics();
        int tw = Math.max(fm.stringWidth(line1), Math.max(fm.stringWidth(line2), fm.stringWidth(line3))) + 14;
        int th = 44;
        int tx = cursorX + 12;
        int ty = cursorY - th - 8;
        if (tx + tw > mapRect.x + mapRect.width) tx = cursorX - tw - 12;
        if (ty < mapRect.y + 2) ty = cursorY + 12;
        tx = MathUtil.clamp(tx, mapRect.x + 2, mapRect.x + mapRect.width - tw - 2);
        ty = MathUtil.clamp(ty, mapRect.y + 2, mapRect.y + mapRect.height - th - 2);

        g2.setColor(new Color(6, 10, 18, 222));
        g2.fillRoundRect(tx, ty, tw, th, 10, 10);
        g2.setColor(new Color(145, 206, 255, 190));
        g2.drawRoundRect(tx, ty, tw, th, 10, 10);
        g2.setColor(new Color(236, 248, 255, 230));
        g2.drawString(line1, tx + 7, ty + 13);
        g2.setColor(new Color(205, 236, 255, 220));
        g2.drawString(line2, tx + 7, ty + 26);
        g2.setColor(new Color(182, 220, 250, 210));
        g2.drawString(line3, tx + 7, ty + 39);
        g2.setFont(oldFont);
    }

    private static Polygon xrayRoomPolygon(int x, int y, int w, int h, double[] normalizedXs, double[] normalizedYs) {
        if (normalizedXs == null || normalizedYs == null) return null;
        int n = Math.min(normalizedXs.length, normalizedYs.length);
        if (n < 3) return null;
        int[] xs = new int[n];
        int[] ys = new int[n];
        for (int i = 0; i < n; i++) {
            double nx = Math.max(-1.0, Math.min(1.0, normalizedXs[i]));
            double ny = Math.max(-1.0, Math.min(1.0, normalizedYs[i]));
            xs[i] = x + (int) Math.round((nx * 0.5 + 0.5) * w);
            ys[i] = y + (int) Math.round((ny * 0.5 + 0.5) * h);
        }
        return new Polygon(xs, ys, n);
    }

    private static String[] buildXrayPctLabels() {
        String[] labels = new String[101];
        for (int i = 0; i <= 100; i++) {
            labels[i] = i + "%";
        }
        return labels;
    }

    private static EnumMap<ShipRoomLayout.RoomId, Integer> xrayPercentCacheFor(Ship ship) {
        return XRAY_ROOM_PCT_CACHE.computeIfAbsent(
                ship,
                k -> new EnumMap<>(ShipRoomLayout.RoomId.class)
        );
    }

    private static void refreshXrayPercentCache(Ship ship, List<ShipRoomLayout.RoomDef> rooms, long nowNanos) {
        if (ship == null || rooms == null || rooms.isEmpty()) return;
        EnumMap<ShipRoomLayout.RoomId, Integer> cache = xrayPercentCacheFor(ship);
        long last = XRAY_ROOM_PCT_CACHE_TS.getOrDefault(ship, 0L);
        boolean refreshDue = (nowNanos - last) >= XRAY_PERCENT_REFRESH_NS;
        if (!refreshDue && cache.size() >= rooms.size()) return;

        for (ShipRoomLayout.RoomDef room : rooms) {
            if (room == null || room.id == null) continue;
            int pct = MathUtil.clamp((int) Math.round(ship.roomHealthFraction(room.id) * 100.0), 0, 100);
            cache.put(room.id, pct);
        }
        XRAY_ROOM_PCT_CACHE_TS.put(ship, nowNanos);
    }

    private static XrayStackLayout computeXrayStackLayout(Player player, Ship lockedTarget, boolean shopOpen,
                                                          int viewW, int viewH) {
        if (player == null || shopOpen) return null;
        if (!player.alive || player.dying || player.hp <= 0) return null;

        Rectangle menu = getCoreMenuBarRect(viewW, viewH);
        int availableH = menu.y - 54;
        if (availableH < 130) return null;

        boolean sensorsOnline = !player.isSystemDestroyed(Ship.InternalSystem.SENSORS);
        boolean targetVisible = lockedTarget != null
                && lockedTarget.alive && !lockedTarget.dying && lockedTarget.hp > 0
                && sensorsOnline
                && !(lockedTarget.faction != null && player.faction != null
                && lockedTarget.faction.isFriendlyTo(player.faction));

        int gap = 12;
        int panelH = targetVisible
                ? Math.max(156, Math.min(214, (int) Math.round(availableH * 0.44)))
                : Math.max(170, Math.min(228, (int) Math.round(availableH * 0.58)));
        if (panelH > availableH - 8) {
            panelH = Math.max(136, availableH - 8);
        }

        int panelW;
        int playerX;
        int targetX;
        if (targetVisible) {
            int totalAvailW = Math.max(540, viewW - 40);
            panelW = Math.max(250, Math.min(344, (totalAvailW - gap) / 2));
            int totalW = panelW * 2 + gap;
            playerX = (viewW - totalW) / 2;
            targetX = playerX + panelW + gap;
        } else {
            panelW = Math.max(270, Math.min(396, menu.width - 170));
            playerX = menu.x + (menu.width - panelW) / 2;
            targetX = playerX;
        }

        int playerH = panelH;
        int targetH = targetVisible ? panelH : 0;
        int playerY = menu.y - playerH - 8;
        int targetY = playerY;
        if (playerY < 48) return null;

        return new XrayStackLayout(playerX, panelW, targetX, playerY, playerH, targetY, targetH, targetVisible);
    }

    private static void drawLockedTargetXrayHud(Graphics2D g2, GameContext ctx, Player player, Ship lockedTarget,
                                                boolean shopOpen, int viewW, int viewH) {
        if (g2 == null || player == null) return;
        XrayStackLayout layout = computeXrayStackLayout(player, lockedTarget, shopOpen, viewW, viewH);
        if (layout == null) return;

        drawShipXrayPanel(g2, ctx, player, layout.panelX, layout.playerY, layout.panelW, layout.playerH,
                "SHIP X-RAY", "OWN HULL TELEMETRY", true);

        if (layout.targetH > 0 && layout.targetVisible) {
            String role = (lockedTarget.role == null) ? "UNKNOWN" : lockedTarget.role.name();
            String subtitle = lockedTarget.name + " / " + role;
            drawShipXrayPanel(g2, ctx, lockedTarget, layout.targetX, layout.targetY, layout.panelW, layout.targetH,
                    "TARGET X-RAY", subtitle, false);
        }
    }

    private static String xrayRoomSymbol(ShipRoomLayout.RoomId roomId) {
        return ShipRoomLayout.symbol(roomId);
    }

    public static void drawBaseUpgradeOverlay(Graphics2D g2, String baseName, int credits, int baseOre,
                                              int hullLv, int shieldLv, int turretLv, int miningLv, int hangarLv,
                                              int maxHangarTier) {
        // "B" style: a diegetic sci-fi console panel (glow edges, grid, bars, subtle scanline).
        int w = 520;
        int h = 284;
        int pad = 22;
        int viewW = g2.getClipBounds().width;
        int x = viewW - w - pad;
        int y = 240;

        double t = System.nanoTime() / 1_000_000_000.0;
        int glowA = 55 + (int) Math.round(25 * (0.5 + 0.5 * Math.sin(t * 2.2)));

        // Outer glow
        g2.setColor(new Color(90, 220, 255, MathUtil.clamp(glowA, 30, 90)));
        g2.fillRoundRect(x - 4, y - 4, w + 8, h + 8, 24, 24);

        // Panel body
        g2.setColor(new Color(0, 0, 0, 190));
        g2.fillRoundRect(x, y, w, h, 20, 20);

        // Inner border
        g2.setColor(new Color(255, 255, 255, 95));
        g2.drawRoundRect(x, y, w, h, 20, 20);

        // Subtle grid
        g2.setColor(new Color(255, 255, 255, 18));
        for (int gx = x + 14; gx < x + w - 14; gx += 28) g2.drawLine(gx, y + 40, gx, y + h - 14);
        for (int gy = y + 40; gy < y + h - 14; gy += 22) g2.drawLine(x + 14, gy, x + w - 14, gy);

        // Header bar
        g2.setColor(new Color(20, 70, 90, 190));
        g2.fillRoundRect(x + 10, y + 10, w - 20, 26, 14, 14);
        g2.setColor(new Color(90, 220, 255, 110));
        g2.drawRoundRect(x + 10, y + 10, w - 20, 26, 14, 14);

        g2.setFont(new Font("Consolas", Font.BOLD, 14));
        g2.setColor(new Color(230, 250, 255, 230));
        g2.drawString("BASE UPGRADE CONSOLE  (ESC)", x + 18, y + 28);

        // Scanline sweep
        int sweepY = y + 42 + (int) Math.round(((Math.sin(t * 0.9) * 0.5 + 0.5)) * (h - 70));
        g2.setColor(new Color(90, 220, 255, 14));
        g2.fillRect(x + 10, sweepY, w - 20, 12);

        // Info
        if (baseName == null) baseName = "Base";
        g2.setFont(new Font("Consolas", Font.PLAIN, 13));

        int ty = y + 58;
        g2.setColor(new Color(255, 255, 255, 210));
        g2.drawString("Base: " + baseName, x + 18, ty);
        ty += 18;

        // Resource readouts (with small pills)
        drawPill(g2, x + 18, ty - 12, 150, "CREDITS", String.valueOf(credits));
        drawPill(g2, x + 178, ty - 12, 150, "BASE ORE", String.valueOf(baseOre));
        drawPill(g2, x + 338, ty - 12, 160, "HANGAR", hangarLv + " / " + maxHangarTier);
        ty += 30;

        g2.setColor(new Color(255, 255, 255, 180));
        g2.drawString("Press 1-5 to purchase:", x + 18, ty);
        ty += 18;

        // Costs mirror GamePanel (keep in sync)
        java.util.function.IntBinaryOperator cCost = (which, nextLv) -> switch (which) {
            case 1 -> 150 + 200 * nextLv;
            case 2 -> 170 + 210 * nextLv;
            case 3 -> 210 + 250 * nextLv;
            case 4 -> 140 + 170 * nextLv;
            case 5 -> 380 + 420 * nextLv;
            default -> 0;
        };
        java.util.function.IntBinaryOperator oCost = (which, nextLv) -> switch (which) {
            case 1 -> 40 + 70 * nextLv;
            case 2 -> 50 + 80 * nextLv;
            case 3 -> 60 + 90 * nextLv;
            case 4 -> 40 + 110 * nextLv;
            case 5 -> 100 + 170 * nextLv;
            default -> 0;
        };

        ty = drawUpgradeLineConsole(g2, x + 18, ty, 1, "Hull Fortification", hullLv, 5, new Color(120, 255, 170, 220), cCost, oCost);
        ty = drawUpgradeLineConsole(g2, x + 18, ty, 2, "Shield Array",      shieldLv, 5, new Color(120, 200, 255, 220), cCost, oCost);
        ty = drawUpgradeLineConsole(g2, x + 18, ty, 3, "Turret Systems",    turretLv, 5, new Color(255, 210, 130, 220), cCost, oCost);
        ty = drawUpgradeLineConsole(g2, x + 18, ty, 4, "Mining Ops",        miningLv, 5, new Color(255, 230, 120, 220), cCost, oCost);
        ty = drawUpgradeLineConsole(g2, x + 18, ty, 5, "Hangar Expansion",  hangarLv, 3, new Color(210, 170, 255, 220), cCost, oCost);

        g2.setColor(new Color(255, 255, 255, 130));
        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.drawString("Mining Ops boosts mining rate + ore sell value.", x + 18, y + h - 16);
    }

    private static void drawPill(Graphics2D g2, int x, int y, int w, String label, String value) {
        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRoundRect(x, y, w, 20, 12, 12);
        g2.setColor(new Color(90, 220, 255, 70));
        g2.drawRoundRect(x, y, w, 20, 12, 12);
        g2.setFont(new Font("Consolas", Font.BOLD, 11));
        g2.setColor(new Color(200, 240, 255, 210));
        g2.drawString(label, x + 8, y + 14);
        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(new Color(255, 255, 255, 220));
        int vw = g2.getFontMetrics().stringWidth(value);
        g2.drawString(value, x + w - 8 - vw, y + 15);
    }

    private static int drawUpgradeLineConsole(Graphics2D g2, int x, int ty,
                                              int key, String name, int lv, int max, Color accent,
                                              java.util.function.IntBinaryOperator cCost,
                                              java.util.function.IntBinaryOperator oCost) {
        g2.setFont(new Font("Consolas", Font.PLAIN, 13));

        // Key capsule
        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRoundRect(x, ty - 12, 22, 18, 10, 10);
        g2.setColor(new Color(255, 255, 255, 70));
        g2.drawRoundRect(x, ty - 12, 22, 18, 10, 10);
        g2.setColor(new Color(255, 255, 255, 210));
        g2.setFont(new Font("Consolas", Font.BOLD, 13));
        g2.drawString(String.valueOf(key), x + 7, ty + 2);

        int textX = x + 30;

        // Name
        g2.setFont(new Font("Consolas", Font.PLAIN, 13));
        g2.setColor(new Color(255, 255, 255, 215));
        g2.drawString(name, textX, ty + 2);

        // Level bars
        int barX = x + 250;
        int barY = ty - 10;
        int barW = 10;
        int barH = 16;
        for (int i = 0; i < max; i++) {
            boolean on = i < lv;
            g2.setColor(on ? accent : new Color(255, 255, 255, 40));
            g2.fillRoundRect(barX + i * (barW + 4), barY, barW, barH, 6, 6);
        }

        // Cost / status
        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        if (lv >= max) {
            g2.setColor(new Color(120, 255, 170, 210));
            g2.drawString("MAX", x + 250 + max * 14 + 12, ty + 2);
        } else {
            int next = lv + 1;
            int c = cCost.applyAsInt(key, next);
            int o = oCost.applyAsInt(key, next);
            g2.setColor(new Color(255, 255, 255, 190));
            g2.drawString(c + "c + " + o + " ore", x + 250 + max * 14 + 12, ty + 2);
        }

        // Divider line
        g2.setColor(new Color(255, 255, 255, 26));
        g2.drawLine(x, ty + 8, x + 480, ty + 8);
        return ty + 26;
    }

public static void drawMinimap(Graphics2D g2, List<Ship> ships, Player player, int viewW, int viewH, double waypointX, double waypointY, List<MapPing> pings) {
        if (ships == null || ships.isEmpty() || player == null) return;

        int pad = 14;
        int size = 170;
        int x0 = viewW - size - pad;
        int y0 = pad;

        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRoundRect(x0, y0, size, size, 16, 16);
        g2.setColor(new Color(255, 255, 255, 80));
        g2.drawRoundRect(x0, y0, size, size, 16, 16);

        double view = 1500;
        double left = player.x - view / 2.0;
        double top = player.y - view / 2.0;

        for (Ship s : ships) {
            if (!s.alive) continue;

            double rx = (s.x - left) / view;
            double ry = (s.y - top) / view;
            if (rx < 0 || rx > 1 || ry < 0 || ry > 1) continue;

            int px = x0 + (int) Math.round(rx * size);
            int py = y0 + (int) Math.round(ry * size);

            g2.setColor(factionMapColor(s.faction, (s == player), 220));

            int r = (s.role == ShipRole.BASE) ? 4 : 2;
            g2.fillOval(px - r, py - r, r * 2, r * 2);
        }

        // Waypoint marker (if inside minimap view)
        if (!Double.isNaN(waypointX) && !Double.isNaN(waypointY)) {
            double rx = (waypointX - left) / view;
            double ry = (waypointY - top) / view;
            if (rx >= 0 && rx <= 1 && ry >= 0 && ry <= 1) {
                int px = x0 + (int) Math.round(rx * size);
                int py = y0 + (int) Math.round(ry * size);
                g2.setColor(new Color(255, 255, 255, 210));
                g2.drawOval(px - 4, py - 4, 8, 8);
                g2.drawLine(px - 6, py, px - 2, py);
                g2.drawLine(px + 2, py, px + 6, py);
                g2.drawLine(px, py - 6, px, py - 2);
                g2.drawLine(px, py + 2, px, py + 6);
            }
        }

        // Pings (if inside minimap view)
        if (pings != null) {
            for (MapPing ping : pings) {
                if (ping == null || ping.t <= 0) continue;
                double rx = (ping.x - left) / view;
                double ry = (ping.y - top) / view;
                if (rx < 0 || rx > 1 || ry < 0 || ry > 1) continue;

                int px = x0 + (int) Math.round(rx * size);
                int py = y0 + (int) Math.round(ry * size);

                int a = MathUtil.clamp((int) Math.round(60 + 120 * Math.min(1, ping.t)), 0, 220);
                Color c = switch (ping.faction) {
                    case 2 -> new Color(255, 90, 90, a);
                    case 1 -> new Color(140, 180, 255, a);
                    case 3 -> new Color(255, 200, 90, a);
                    case 4 -> new Color(200, 140, 255, a);
                    default -> new Color(90, 255, 140, a);
                };
                g2.setColor(c);
                g2.drawOval(px - 5, py - 5, 10, 10);
            }
        }

        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(new Color(255, 255, 255, 110));
        g2.drawString("MINIMAP", x0 + 10, y0 + size - 10);
    }


    public static void drawStrategicMap(Graphics2D g2,
                                        int viewW, int viewH,
                                        int worldW, int worldH,
                                        double camX, double camY,
                                        double camViewW, double camViewH,
                                        Player player,
                                        List<Ship> ships,
                                        List<Asteroid> asteroids,
                                        List<Salvage> salvage,
                                        double waypointX, double waypointY,
                                        List<MapPing> pings,
                                        String bannerTopLine) {

        Rectangle r = getStrategicMapRect(viewW, viewH);

        // Backdrop + glow border (Style B)
        g2.setColor(new Color(0, 0, 0, 205));
        g2.fillRoundRect(r.x, r.y, r.width, r.height, 22, 22);

        g2.setColor(new Color(140, 200, 255, 55));
        g2.drawRoundRect(r.x - 2, r.y - 2, r.width + 4, r.height + 4, 24, 24);
        g2.setColor(new Color(255, 255, 255, 95));
        g2.drawRoundRect(r.x, r.y, r.width, r.height, 22, 22);

        // Inner map area
        int pad = 18;
        Rectangle m = new Rectangle(r.x + pad, r.y + 44, r.width - pad * 2, r.height - 60);

        g2.setColor(new Color(255, 255, 255, 22));
        g2.fillRoundRect(m.x, m.y, m.width, m.height, 16, 16);
        g2.setColor(new Color(255, 255, 255, 55));
        g2.drawRoundRect(m.x, m.y, m.width, m.height, 16, 16);

        // Subtle grid
        g2.setColor(new Color(255, 255, 255, 22));
        int step = 80;
        for (int x = m.x + step; x < m.x + m.width; x += step) g2.drawLine(x, m.y, x, m.y + m.height);
        for (int y = m.y + step; y < m.y + m.height; y += step) g2.drawLine(m.x, y, m.x + m.width, y);

        // Title + help
        g2.setFont(new Font("Consolas", Font.BOLD, 16));
        g2.setColor(new Color(255, 255, 255, 225));
        g2.drawString("STRATEGIC MAP", r.x + 18, r.y + 28);

        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(new Color(255, 255, 255, 170));
        g2.drawString("LMB: waypoint   RMB: ping   M/ESC: close", r.x + 18, r.y + r.height - 16);

        if (bannerTopLine != null && !bannerTopLine.isBlank()) {
            g2.setColor(new Color(140, 200, 255, 200));
            g2.drawString(bannerTopLine, r.x + 190, r.y + 28);
        }

        // Helpers: world -> map
        java.util.function.BiFunction<Double, Double, Point> W2M = (wx, wy) -> {
            int px = m.x + (int) Math.round((wx / Math.max(1.0, worldW)) * m.width);
            int py = m.y + (int) Math.round((wy / Math.max(1.0, worldH)) * m.height);
            return new Point(px, py);
        };

        // Asteroids
        if (asteroids != null) {
            g2.setColor(new Color(200, 200, 200, 80));
            for (Asteroid a : asteroids) {
                if (a == null) continue;
                Point p = W2M.apply(a.x, a.y);
                g2.fillRect(p.x, p.y, 2, 2);
            }
        }

        // Salvage
        if (salvage != null) {
            g2.setColor(new Color(255, 255, 255, 120));
            for (Salvage s : salvage) {
                if (s == null || !s.alive()) continue;
                Point p = W2M.apply(s.x, s.y);
                g2.fillOval(p.x - 1, p.y - 1, 3, 3);
            }
        }

        // Ships + bases
        if (ships != null) {
            for (Ship s : ships) {
                if (s == null || !s.alive) continue;
                Point p = W2M.apply(s.x, s.y);

                Color c = factionMapColor(s.faction, (s == player), 200);

                int rr = (s.role == ShipRole.BASE) ? 4 : 2;
                g2.setColor(c);
                g2.fillOval(p.x - rr, p.y - rr, rr * 2, rr * 2);
            }
        }

        // Waypoint
        if (!Double.isNaN(waypointX) && !Double.isNaN(waypointY)) {
            Point wp = W2M.apply(waypointX, waypointY);
            g2.setColor(new Color(255, 255, 255, 220));
            g2.drawOval(wp.x - 6, wp.y - 6, 12, 12);
            g2.drawLine(wp.x - 10, wp.y, wp.x - 3, wp.y);
            g2.drawLine(wp.x + 3, wp.y, wp.x + 10, wp.y);
            g2.drawLine(wp.x, wp.y - 10, wp.x, wp.y - 3);
            g2.drawLine(wp.x, wp.y + 3, wp.x, wp.y + 10);
        }

        // Pings
        if (pings != null) {
            for (MapPing ping : pings) {
                if (ping == null || ping.t <= 0) continue;
                Point pp = W2M.apply(ping.x, ping.y);

                int a = MathUtil.clamp((int) Math.round(60 + 120 * Math.min(1, ping.t)), 0, 220);
                Color c = switch (ping.faction) {
                    case 2 -> new Color(255, 90, 90, a);
                    case 1 -> new Color(140, 180, 255, a);
                    case 3 -> new Color(255, 200, 90, a);
                    case 4 -> new Color(200, 140, 255, a);
                    default -> new Color(90, 255, 140, a);
                };

                g2.setColor(c);
                g2.drawOval(pp.x - 8, pp.y - 8, 16, 16);
                g2.drawOval(pp.x - 4, pp.y - 4, 8, 8);
            }
        }

        // Camera viewport rectangle
        double vx0 = camX;
        double vy0 = camY;
        double vx1 = camX + camViewW;
        double vy1 = camY + camViewH;

        Point p0 = W2M.apply(vx0, vy0);
        Point p1 = W2M.apply(vx1, vy1);

        int rx = Math.min(p0.x, p1.x);
        int ry = Math.min(p0.y, p1.y);
        int rw = Math.abs(p1.x - p0.x);
        int rh = Math.abs(p1.y - p0.y);

        g2.setColor(new Color(255, 255, 255, 120));
        g2.drawRect(rx, ry, rw, rh);
    }


    // IMPORTANT: This is the method that was likely stubbed/empty in your current project.
    public static void drawShip(Graphics2D g2, Ship ship) {
        ShipRenderer.drawShip(g2, ship);
    }

    /**
     * Modular ship visual pipeline:
     * - Role-based local-coordinate silhouettes
     * - Deterministic panel/window greebles
     * - Engine cones and hardpoint mounts
     */
    private static final class ShipRenderer {
        private static final Map<String, ShipVisual> CACHE = new HashMap<>();

        static void drawShip(Graphics2D g2, Ship ship) {
            if (!ship.alive) return;

            Color hull;
            Color trim;
            hull = factionHullColor(ship.faction);
            trim = factionTrimColor(ship.faction);

            int wx = (int) Math.round(ship.x);
            int wy = (int) Math.round(ship.y);

            Graphics2D g = (Graphics2D) g2.create();
            g.translate(wx, wy);
            g.rotate(ship.angle);
            double roleScale = roleVisualScale(ship.role);
            if (Math.abs(roleScale - 1.0) > 1e-6) {
                g.scale(roleScale, roleScale);
            }

            double sig = ship.effectiveSignature();
            if (ship.isStealth && sig < 0.99) {
                float a = (float) (0.22 + 0.78 * sig);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
            }

            ShipVisual visual = getVisual(ship);
            Area hullArea = buildArea(visual.hullPolys);
            ShipSkinSet skinSet = ShipSkinLibrary.getSkinSet(ship.role, ship.faction);
            boolean hasAlbedoSkin = skinSet != null && skinSet.hasAlbedo();

            if (!hasAlbedoSkin) {
                drawHullShadow(g, visual);
                drawHullAndSuper(g, visual, hull, trim);
            }
            drawHullSkin(g, ship, visual, hullArea, hull, trim, skinSet);
            drawPanelsAndWindows(g, ship, visual, hullArea, hasAlbedoSkin);
            drawEngines(g, ship, visual);
            drawHardpoints(g, ship, visual);

            drawShipShieldFaces(g, ship);

            if (hullArea != null) {
                drawDamageDecals(g, ship, hullArea);
            }

            if (DevTools.isDebugOverlay()) {
                drawRoomDebugOverlay(g, ship);
            }

            if (ship.isStealth && sig < 0.99 && !visual.hullPolys.isEmpty()) {
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f));
                g.setColor(new Color(120, 220, 255, 110));
                g.draw(visual.hullPolys.get(0));
            }

            g.dispose();

            if (!isTinyStrikeCraft(ship.role)) {
                g2.setFont(new Font("Consolas", Font.PLAIN, 12));
                g2.setColor(new Color(255, 255, 255, 130));
                g2.drawString(ship.name, wx - 18, wy - (int) ship.radius - 10);
            }
        }

        private static double roleVisualScale(ShipRole role) {
            if (role == null) return 1.0;
            return switch (role) {
                case FIGHTER -> 0.16;
                case BOMBER -> 0.17;
                case DRONE -> 0.20;
                default -> HullGeometry.roleVisualScale(role);
            };
        }

        private static ShipVisual getVisual(Ship ship) {
            int r = (int) Math.round(Math.max(8.0, ship.radius));
            String key = ship.role + ":" + ship.faction + ":" + r;
            ShipVisual cached = CACHE.get(key);
            if (cached != null) return cached;

            ShipVisual v = buildVisual(ship.role, ship.faction, r);
            CACHE.put(key, v);
            return v;
        }

        private static ShipVisual buildVisual(ShipRole role, int r) {
            return buildVisual(role, null, r);
        }

        private static ShipVisual buildVisual(ShipRole role, Faction faction, int r) {
            ShipVisual v = new ShipVisual();
            if (role == null) role = ShipRole.FRIGATE;

            switch (role) {
                case PICKET -> {
                    v.hullPolys.add(poly(new int[]{r + 9, r - 4, -r + 2, -r, -r + 2, r - 4},
                            new int[]{0, -r / 2, -r / 3, 0, r / 3, r / 2}));
                    v.superPolys.add(poly(new int[]{-r / 4, r / 2, r / 5}, new int[]{-r / 5, 0, r / 5}));
                    v.fins.add(poly(new int[]{-r + 2, -r / 2, -r + 2}, new int[]{-r / 3, -r / 2, -r / 6}));
                    v.fins.add(poly(new int[]{-r + 2, -r / 2, -r + 2}, new int[]{r / 3, r / 2, r / 6}));
                    v.engines.add(new EnginePoint(-r + 1, 0));
                }
                case PATROL -> {
                    v.hullPolys.add(poly(new int[]{r + 7, r - 2, -r + 4, -r, -r + 4, r - 2},
                            new int[]{0, -r / 2, -r / 3, 0, r / 3, r / 2}));
                    v.superPolys.add(poly(new int[]{0, r / 3, r / 6, -r / 6}, new int[]{-r / 5, 0, r / 5, r / 5}));
                    v.engines.add(new EnginePoint(-r + 1, -r / 4));
                    v.engines.add(new EnginePoint(-r + 1, r / 4));
                }
                case LIGHT_CRUISER -> {
                    v.hullPolys.add(poly(new int[]{r + 12, r - 7, -r + 2, -r, -r + 8, -r, -r + 2, r - 7},
                            new int[]{0, -r / 2, -r / 2, -r / 6, 0, r / 6, r / 2, r / 2}));
                    v.superPolys.add(poly(new int[]{-r / 5, r / 4, r / 8, -r / 6}, new int[]{-r / 4, -r / 8, r / 4, r / 4}));
                    v.superPolys.add(poly(new int[]{r / 10, r / 3, r / 4, r / 12}, new int[]{-r / 7, -r / 10, r / 7, r / 6}));
                    v.fins.add(poly(new int[]{-r + 2, -r / 2, -r / 2, -r + 2}, new int[]{-r / 2, -r / 3, -r / 6, -r / 4}));
                    v.fins.add(poly(new int[]{-r + 2, -r / 2, -r / 2, -r + 2}, new int[]{r / 2, r / 3, r / 6, r / 4}));
                    v.engines.add(new EnginePoint(-r + 1, -r / 3));
                    v.engines.add(new EnginePoint(-r + 1, r / 3));
                }
                case MEDIUM_CRUISER, CRUISER -> {
                    v.hullPolys.add(poly(new int[]{r + 14, r - 7, r - 14, -r + 1, -r, -r + 10, -r, -r + 1, r - 14, r - 7},
                            new int[]{0, -r / 2, -r / 2, -r / 2, -r / 6, 0, r / 6, r / 2, r / 2, r / 2}));
                    v.superPolys.add(poly(new int[]{-r / 6, r / 3, r / 5, -r / 8}, new int[]{-r / 5, -r / 8, r / 5, r / 4}));
                    v.superPolys.add(poly(new int[]{r / 8, r / 2, r / 3, r / 12}, new int[]{-r / 7, -r / 12, r / 7, r / 5}));
                    v.fins.add(poly(new int[]{-r + 2, -r / 2, -r + 2}, new int[]{-r / 2, -r / 3, -r / 5}));
                    v.fins.add(poly(new int[]{-r + 2, -r / 2, -r + 2}, new int[]{r / 2, r / 3, r / 5}));
                    v.engines.add(new EnginePoint(-r + 1, -r / 2 + 3));
                    v.engines.add(new EnginePoint(-r + 1, 0));
                    v.engines.add(new EnginePoint(-r + 1, r / 2 - 3));
                }
                case BATTLECRUISER -> {
                    v.hullPolys.add(poly(new int[]{r + 16, r - 6, r - 16, -r + 2, -r, -r + 13, -r, -r + 2, r - 16, r - 6},
                            new int[]{0, -r / 2, -r / 2, -r / 2, -r / 4, 0, r / 4, r / 2, r / 2, r / 2}));
                    v.superPolys.add(poly(new int[]{-r / 5, r / 3, r / 4, -r / 7}, new int[]{-r / 4, -r / 6, r / 4, r / 3}));
                    v.superPolys.add(poly(new int[]{r / 8, r / 2, r / 3, r / 10}, new int[]{-r / 6, -r / 9, r / 8, r / 6}));
                    v.fins.add(poly(new int[]{-r + 2, -r / 3, -r + 2}, new int[]{-r / 2, -r / 3, -r / 6}));
                    v.fins.add(poly(new int[]{-r + 2, -r / 3, -r + 2}, new int[]{r / 2, r / 3, r / 6}));
                    v.engines.add(new EnginePoint(-r + 1, -r / 2 + 4));
                    v.engines.add(new EnginePoint(-r + 1, -r / 6));
                    v.engines.add(new EnginePoint(-r + 1, r / 6));
                    v.engines.add(new EnginePoint(-r + 1, r / 2 - 4));
                }
                case BATTLESHIP -> {
                    v.hullPolys.add(poly(new int[]{r + 18, r - 8, r - 18, -r + 2, -r, -r + 15, -r, -r + 2, r - 18, r - 8},
                            new int[]{0, -r / 2, -r / 2, -r / 2, -r / 3, 0, r / 3, r / 2, r / 2, r / 2}));
                    v.superPolys.add(poly(new int[]{-r / 6, r / 3, r / 4, -r / 8}, new int[]{-r / 4, -r / 6, r / 4, r / 4}));
                    v.superPolys.add(poly(new int[]{r / 8, r / 2, r / 3, r / 8}, new int[]{-r / 6, -r / 8, r / 8, r / 6}));
                    v.fins.add(poly(new int[]{-r + 2, -r / 3, -r + 2}, new int[]{-r / 2, -r / 3, -r / 8}));
                    v.fins.add(poly(new int[]{-r + 2, -r / 3, -r + 2}, new int[]{r / 2, r / 3, r / 8}));
                    v.engines.add(new EnginePoint(-r + 1, -r / 2 + 4));
                    v.engines.add(new EnginePoint(-r + 1, -r / 4));
                    v.engines.add(new EnginePoint(-r + 1, 0));
                    v.engines.add(new EnginePoint(-r + 1, r / 4));
                    v.engines.add(new EnginePoint(-r + 1, r / 2 - 4));
                }
                case DREADNOUGHT -> {
                    v.hullPolys.add(poly(new int[]{r + 20, r - 11, r - 22, -r + 2, -r, -r + 17, -r, -r + 2, r - 22, r - 11},
                            new int[]{0, -r / 2, -r / 2, -r / 2, -r / 3, 0, r / 3, r / 2, r / 2, r / 2}));
                    v.superPolys.add(poly(new int[]{-r / 6, r / 3, r / 4, -r / 10}, new int[]{-r / 4, -r / 7, r / 4, r / 3}));
                    v.superPolys.add(poly(new int[]{r / 12, r / 2, r / 3, r / 8}, new int[]{-r / 5, -r / 8, r / 8, r / 5}));
                    v.fins.add(poly(new int[]{-r + 2, -r / 4, -r + 2}, new int[]{-r / 2, -r / 3, -r / 7}));
                    v.fins.add(poly(new int[]{-r + 2, -r / 4, -r + 2}, new int[]{r / 2, r / 3, r / 7}));
                    v.engines.add(new EnginePoint(-r + 1, -r / 2 + 5));
                    v.engines.add(new EnginePoint(-r + 1, -r / 3));
                    v.engines.add(new EnginePoint(-r + 1, -r / 6));
                    v.engines.add(new EnginePoint(-r + 1, r / 6));
                    v.engines.add(new EnginePoint(-r + 1, r / 3));
                    v.engines.add(new EnginePoint(-r + 1, r / 2 - 5));
                }
                case SUPERSHIP -> {
                    v.hullPolys.add(poly(new int[]{r + 24, r - 8, r - 24, -r + 3, -r, -r + 18, -r, -r + 3, r - 24, r - 8},
                            new int[]{0, -r / 2, -r / 2, -r / 2, -r / 3, 0, r / 3, r / 2, r / 2, r / 2}));
                    v.superPolys.add(poly(new int[]{-r / 6, r / 4, r / 4, -r / 10}, new int[]{-r / 4, -r / 8, r / 4, r / 3}));
                    v.superPolys.add(poly(new int[]{r / 6, r / 2, r / 3, r / 7}, new int[]{-r / 6, -r / 10, r / 10, r / 6}));
                    v.superPolys.add(poly(new int[]{r / 3, r / 2, r / 2, r / 3}, new int[]{-r / 7, -r / 11, r / 11, r / 7}));
                    v.fins.add(poly(new int[]{-r + 2, -r / 3, -r + 2}, new int[]{-r / 2, -r / 4, -r / 8}));
                    v.fins.add(poly(new int[]{-r + 2, -r / 3, -r + 2}, new int[]{r / 2, r / 4, r / 8}));
                    v.engines.add(new EnginePoint(-r + 1, -r / 2 + 6));
                    v.engines.add(new EnginePoint(-r + 1, -r / 4));
                    v.engines.add(new EnginePoint(-r + 1, 0));
                    v.engines.add(new EnginePoint(-r + 1, r / 4));
                    v.engines.add(new EnginePoint(-r + 1, r / 2 - 6));
                }
                case MINER -> {
                    // Industrial silhouette: chunkier bow, side pods, mining rig.
                    v.hullPolys.add(poly(new int[]{r + 5, r - 7, -r + 6, -r, -r + 6, r - 7},
                            new int[]{0, -r / 2, -r / 2, 0, r / 2, r / 2}));
                    v.superPolys.add(poly(new int[]{-r / 3, r / 3, r / 4, -r / 3}, new int[]{-r / 4, -r / 4, r / 4, r / 4}));
                    v.superPolys.add(poly(new int[]{r / 4, r / 2, r / 2, r / 4}, new int[]{-r / 5, -r / 6, r / 6, r / 5}));
                    v.fins.add(poly(new int[]{-r + 2, -r / 2, -r / 2, -r + 2}, new int[]{-r / 2, -r / 2, -r / 6, -r / 4}));
                    v.fins.add(poly(new int[]{-r + 2, -r / 2, -r / 2, -r + 2}, new int[]{r / 2, r / 2, r / 6, r / 4}));
                    v.engines.add(new EnginePoint(-r + 1, -r / 4));
                    v.engines.add(new EnginePoint(-r + 1, r / 4));
                }
                case BASE -> {
                    v.station = true;
                    v.stationOuter = r;
                    v.stationInner = Math.max(8, r - 14);
                    v.stationSpokes = 6;
                }
                default -> {
                    // Generic frigate line
                    v.hullPolys.add(poly(new int[]{r + 8, r - 6, -r, -r + 8, -r, r - 6},
                            new int[]{0, -r / 2, -r / 2, 0, r / 2, r / 2}));
                    v.superPolys.add(poly(new int[]{-r / 4, r / 3, r / 5, -r / 6}, new int[]{-r / 5, 0, r / 5, r / 5}));
                    v.engines.add(new EnginePoint(-r + 1, 0));
                }
            }

            if (!v.station) {
                Polygon canonicalHull = ShipHullSilhouette.hullPolygon(role, r, faction);
                if (canonicalHull != null && canonicalHull.npoints >= 3) {
                    v.hullPolys.clear();
                    v.hullPolys.add(canonicalHull);
                }
            }

            return v;
        }

        private static void drawHullShadow(Graphics2D g, ShipVisual v) {
            g.setColor(new Color(0, 0, 0, 70));
            g.translate(4, 4);
            if (v.station) {
                int ro = (int) Math.round(v.stationOuter);
                int ri = (int) Math.round(v.stationInner);
                g.fillOval(-ro, -ro, ro * 2, ro * 2);
                g.setColor(new Color(0, 0, 0, 120));
                g.fillOval(-ri, -ri, ri * 2, ri * 2);
            } else {
                for (Polygon p : v.hullPolys) g.fillPolygon(p);
            }
            g.translate(-4, -4);
        }

        private static void drawHullAndSuper(Graphics2D g, ShipVisual v, Color hull, Color trim) {
            if (v.station) {
                int ro = (int) Math.round(v.stationOuter);
                int ri = (int) Math.round(v.stationInner);
                g.setColor(new Color(hull.getRed(), hull.getGreen(), hull.getBlue(), 190));
                g.fillOval(-ro, -ro, ro * 2, ro * 2);
                g.setColor(new Color(0, 0, 0, 160));
                g.fillOval(-ri, -ri, ri * 2, ri * 2);
                g.setColor(new Color(trim.getRed(), trim.getGreen(), trim.getBlue(), 170));
                g.drawOval(-ro, -ro, ro * 2, ro * 2);
                g.drawOval(-ri, -ri, ri * 2, ri * 2);
                for (int i = 0; i < v.stationSpokes; i++) {
                    double a = (Math.PI * 2.0 * i) / v.stationSpokes;
                    int x1 = (int) Math.round(Math.cos(a) * (ri + 2));
                    int y1 = (int) Math.round(Math.sin(a) * (ri + 2));
                    int x2 = (int) Math.round(Math.cos(a) * (ro - 2));
                    int y2 = (int) Math.round(Math.sin(a) * (ro - 2));
                    g.drawLine(x1, y1, x2, y2);
                }
                return;
            }

            Rectangle2D bounds = buildArea(v.hullPolys).getBounds2D();
            int backX = (int) Math.round(bounds.getMinX());
            int frontX = (int) Math.round(bounds.getMaxX());
            Color hullDark = new Color(Math.max(0, hull.getRed() - 35), Math.max(0, hull.getGreen() - 35), Math.max(0, hull.getBlue() - 35));
            Color hullLight = new Color(Math.min(255, hull.getRed() + 25), Math.min(255, hull.getGreen() + 25), Math.min(255, hull.getBlue() + 25));
            GradientPaint gp = new GradientPaint(backX, 0, hullDark, frontX, 0, hullLight);

            g.setPaint(gp);
            for (Polygon p : v.hullPolys) g.fillPolygon(p);
            g.setPaint(null);

            for (Polygon p : v.superPolys) {
                g.setColor(new Color(trim.getRed(), trim.getGreen(), trim.getBlue(), 120));
                g.fillPolygon(p);
                g.setColor(new Color(0, 0, 0, 100));
                g.drawPolygon(p);
            }

            for (Polygon p : v.fins) {
                g.setColor(new Color(hullDark.getRed(), hullDark.getGreen(), hullDark.getBlue(), 160));
                g.fillPolygon(p);
            }

            g.setColor(new Color(0, 0, 0, 115));
            for (Polygon p : v.hullPolys) g.drawPolygon(p);
        }

        private static void drawPanelsAndWindows(Graphics2D g, Ship ship, ShipVisual v, Area hullArea, boolean hasAlbedoSkin) {
            if (v.station || hullArea == null) return;
            if (hasAlbedoSkin) return;

            Shape oldClip = g.getClip();
            g.setClip(hullArea);

            int seed = System.identityHashCode(ship) * 31 + (ship.role == null ? 0 : ship.role.ordinal() * 17);
            Random rng = new Random(seed);
            int detail = Math.max(4, (int) Math.round(ship.radius / 4.0));

            g.setColor(new Color(255, 255, 255, 55));
            for (int i = 0; i < detail; i++) {
                int x1 = (int) Math.round(-ship.radius + rng.nextDouble() * ship.radius * 2.0);
                int y1 = (int) Math.round(-ship.radius + rng.nextDouble() * ship.radius * 2.0);
                int x2 = x1 + 4 + rng.nextInt(Math.max(4, (int) ship.radius / 2 + 2));
                int y2 = y1 + rng.nextInt(5) - 2;
                g.drawLine(x1, y1, x2, y2);
            }

            g.setColor(new Color(230, 245, 255, 75));
            int windows = Math.max(3, detail / 2);
            for (int i = 0; i < windows; i++) {
                int x = (int) Math.round(-ship.radius / 2 + rng.nextDouble() * ship.radius);
                int y = (int) Math.round(-ship.radius / 3 + rng.nextDouble() * ship.radius * 0.66);
                g.fillRect(x, y, 2, 2);
            }

            g.setClip(oldClip);
        }

        private static void drawHullSkin(Graphics2D g, Ship ship, ShipVisual v, Area hullArea,
                                         Color hull, Color trim, ShipSkinSet skinSet) {
            if (skinSet == null || !skinSet.hasAnyLayer()) return;

            Rectangle2D bounds = (hullArea == null)
                    ? new Rectangle2D.Double(-ship.radius, -ship.radius, ship.radius * 2.0, ship.radius * 2.0)
                    : hullArea.getBounds2D();

            // Draw the authored sprite on a square canvas around the ship center.
            int baseSpan = Math.max(1, (int) Math.round(ship.radius * 2.0));
            int sw = Math.max(1, (int) Math.round(baseSpan * ShipHullSilhouette.skinRenderScale()));
            int sh = sw;
            int sx = -sw / 2;
            int sy = -sh / 2;

            drawSkinLayer(g, skinSet.albedo, sx, sy, sw, sh, 0.98f);
            boolean hasAuxLayers = skinSet.panel != null || skinSet.ao != null
                    || skinSet.emissive != null || skinSet.damage != null;
            if (!hasAuxLayers) return;

            Shape oldClip = g.getClip();
            if (hullArea != null) {
                if (oldClip == null) {
                    g.setClip(hullArea);
                } else {
                    Area combined = new Area(oldClip);
                    combined.intersect(hullArea);
                    g.setClip(combined);
                }
            }

            drawSkinLayer(g, skinSet.panel, sx, sy, sw, sh, 0.46f);
            drawSkinLayer(g, skinSet.ao, sx, sy, sw, sh, 0.50f);

            if (skinSet.damage != null && ship.hpMax > 0) {
                double damageFrac = Math.max(0.0, Math.min(1.0, 1.0 - ship.hp / (double) ship.hpMax));
                float damageAlpha = (float) Math.min(0.88, 0.18 + damageFrac * 0.72);
                if (damageAlpha > 0.16f) {
                    drawSkinLayer(g, skinSet.damage, sx, sy, sw, sh, damageAlpha);
                    if (damageFrac > 0.50) drawSkinLayer(g, skinSet.damage, sx, sy, sw, sh, damageAlpha * 0.36f);
                }
            }

            if (skinSet.emissive != null) {
                drawSkinLayer(g, skinSet.emissive, sx, sy, sw, sh, 0.50f);
                drawSkinLayer(g, skinSet.emissive, sx, sy, sw, sh, 0.17f);
            }

            applyFactionSkinLighting(g, bounds, ship.faction, hull, trim);
            g.setClip(oldClip);
        }

        private static void drawSkinLayer(Graphics2D g, BufferedImage layer,
                                          int x, int y, int w, int h, float alpha) {
            if (layer == null || alpha <= 0f) return;
            float a = (float) Math.max(0.0, Math.min(1.0, alpha));
            Composite old = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
            g.drawImage(layer, x, y, w, h, null);
            g.setComposite(old);
        }

        private static void applyFactionSkinLighting(Graphics2D g, Rectangle2D bounds, Faction faction, Color hull, Color trim) {
            int x = (int) Math.round(bounds.getMinX());
            int y = (int) Math.round(bounds.getMinY());
            int w = Math.max(1, (int) Math.round(bounds.getWidth()));
            int h = Math.max(1, (int) Math.round(bounds.getHeight()));
            HullLightingPreset preset = HullLightingPreset.forFaction(faction, hull, trim);

            Paint oldPaint = g.getPaint();
            Composite oldComposite = g.getComposite();

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            g.setPaint(new GradientPaint(
                    x, y + h / 2f, withAlpha(preset.rimColor, preset.rimAlpha),
                    x + w * 0.40f, y + h / 2f, withAlpha(preset.rimColor, 0)));
            g.fillRect(x, y, w, h);

            g.setPaint(new GradientPaint(
                    x, y + h / 2f, withAlpha(preset.keyColor, 0),
                    x + w, y + h / 2f, withAlpha(preset.keyColor, preset.keyAlpha)));
            g.fillRect(x, y, w, h);

            g.setPaint(new GradientPaint(
                    x, y, withAlpha(Color.WHITE, preset.deckAlpha),
                    x, y + h, withAlpha(Color.BLACK, preset.bellyAlpha)));
            g.fillRect(x, y, w, h);

            g.setPaint(oldPaint);
            g.setComposite(oldComposite);
        }

        private static Color withAlpha(Color c, int alpha) {
            return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, alpha)));
        }

        private static void drawEngines(Graphics2D g, Ship ship, ShipVisual v) {
            if (ship == null || v == null || v.station || v.engines.isEmpty()) return;
            drawEngineNozzlePass(g, ship, v.engines, ship.radius);
        }

        private static void drawHardpoints(Graphics2D g, Ship ship, ShipVisual v) {
            drawTurrets(g, ship);
        }

        private static void drawRoomDebugOverlay(Graphics2D g, Ship ship) {
            List<Ship.RoomStatus> rooms = ship.roomStatusSnapshot();
            if (rooms == null || rooms.isEmpty()) return;
            boolean showPolygons = DevTools.isRoomPolygonsEnabled();
            boolean showImpactPoints = DevTools.isRoomImpactPointsEnabled();
            boolean showHpBars = DevTools.isRoomHpBarsEnabled();
            boolean showHazards = DevTools.isRoomHazardsEnabled();
            if (!showPolygons && !showImpactPoints && !showHpBars && !showHazards) return;

            Color stroke = new Color(255, 240, 150, 110);
            Font oldFont = g.getFont();
            g.setFont(new Font("Consolas", Font.PLAIN, 9));

            if (showPolygons || showHazards) {
                for (Ship.RoomStatus rs : rooms) {
                    Polygon p = roomPolygonShipLocal(ship.radius, rs.normalizedXs, rs.normalizedYs);
                    if (p == null || p.npoints < 3) continue;

                    double frac = (rs.hpMax <= 1e-9) ? 1.0 : Math.max(0.0, Math.min(1.0, rs.hp / rs.hpMax));
                    int alpha = 28 + (int) Math.round((1.0 - frac) * 135.0);
                    boolean fire = rs.fireIntensity > 0.06;

                    if (showPolygons) {
                        Color fill = (showHazards && fire)
                                ? new Color(255, 120, 50, Math.min(180, alpha + 35))
                                : new Color(255, 70, 70, Math.min(170, alpha));
                        g.setColor(fill);
                        g.fillPolygon(p);
                        g.setColor(stroke);
                        g.drawPolygon(p);

                        Rectangle b = p.getBounds();
                        int tx = (int) Math.round(b.getCenterX()) - 14;
                        int ty = (int) Math.round(b.getCenterY());
                        g.setColor(new Color(255, 255, 255, 210));
                        g.drawString((int) Math.round(frac * 100.0) + "%", tx, ty);
                    }

                    if (showHazards && fire) {
                        Point c = roomDebugCentroid(p);
                        int hzR = Math.max(3, (int) Math.round(2.5 + rs.fireIntensity * 2.3));
                        g.setColor(new Color(255, 165, 70, 205));
                        g.drawOval(c.x - hzR, c.y - hzR, hzR * 2, hzR * 2);
                        g.setColor(new Color(255, 220, 150, 225));
                        g.drawLine(c.x - hzR, c.y, c.x + hzR, c.y);
                        g.drawLine(c.x, c.y - hzR, c.x, c.y + hzR);
                    }
                }
            }

            if (showHpBars) {
                drawRoomDebugHpBars(g, ship, rooms, showHazards);
            }

            if (showImpactPoints) {
                List<Ship.RoomDamageEvent> events = ship.recentRoomDamageEvents();
                if (events != null) {
                    int start = Math.max(0, events.size() - 8);
                    for (int i = start; i < events.size(); i++) {
                        Ship.RoomDamageEvent ev = events.get(i);
                        if (!Double.isFinite(ev.normalizedX) || !Double.isFinite(ev.normalizedY)) continue;
                        int px = (int) Math.round(ev.normalizedX * ship.radius);
                        int py = (int) Math.round(ev.normalizedY * ship.radius);
                        g.setColor(ev.fromHazard ? new Color(255, 130, 70, 200) : new Color(255, 250, 170, 210));
                        g.fillOval(px - 2, py - 2, 4, 4);
                    }
                }
            }

            g.setFont(oldFont);
        }

        private static void drawRoomDebugHpBars(Graphics2D g, Ship ship, List<Ship.RoomStatus> rooms, boolean showHazards) {
            if (g == null || ship == null || rooms == null || rooms.isEmpty()) return;
            int barW = Math.max(14, (int) Math.round(ship.radius * 0.74));
            int barH = 3;
            int gap = 1;
            int count = rooms.size();
            int listH = count * barH + (count - 1) * gap;
            int baseX = -((int) Math.round(ship.radius)) - barW - 9;
            int baseY = -(listH / 2);
            int i = 0;
            for (Ship.RoomStatus rs : rooms) {
                double frac = (rs.hpMax <= 1e-9) ? 1.0 : Math.max(0.0, Math.min(1.0, rs.hp / rs.hpMax));
                int y = baseY + i * (barH + gap);
                int fillW = MathUtil.clamp((int) Math.round(barW * frac), 0, barW);
                Color hpColor = new Color(
                        MathUtil.clamp((int) Math.round((1.0 - frac) * 220.0), 0, 220),
                        MathUtil.clamp((int) Math.round(90.0 + frac * 165.0), 0, 255),
                        80,
                        220
                );
                if (showHazards && rs.fireIntensity > 0.06) {
                    hpColor = new Color(255, 130, 70, 220);
                }
                g.setColor(new Color(18, 18, 22, 170));
                g.fillRect(baseX, y, barW, barH);
                if (fillW > 0) {
                    g.setColor(hpColor);
                    g.fillRect(baseX, y, fillW, barH);
                }
                g.setColor(new Color(255, 255, 220, 130));
                g.drawRect(baseX, y, barW, barH);
                if (rs.critical) {
                    g.setColor(new Color(255, 220, 120, 210));
                    g.fillRect(baseX - 3, y, 2, barH);
                }
                i++;
            }
        }

        private static Point roomDebugCentroid(Polygon p) {
            if (p == null || p.npoints <= 0) return new Point(0, 0);
            int sx = 0;
            int sy = 0;
            for (int i = 0; i < p.npoints; i++) {
                sx += p.xpoints[i];
                sy += p.ypoints[i];
            }
            return new Point(Math.round(sx / (float) p.npoints), Math.round(sy / (float) p.npoints));
        }

        private static Area buildArea(List<Polygon> polys) {
            if (polys == null || polys.isEmpty()) return null;
            Area a = new Area();
            for (Polygon p : polys) a.add(new Area(p));
            return a;
        }

        private static Polygon poly(int[] xs, int[] ys) {
            return new Polygon(xs, ys, Math.min(xs.length, ys.length));
        }
    }

    private static final class ShipVisual {
        final List<Polygon> hullPolys = new ArrayList<>();
        final List<Polygon> superPolys = new ArrayList<>();
        final List<Polygon> fins = new ArrayList<>();
        final List<EnginePoint> engines = new ArrayList<>();
        boolean station = false;
        double stationOuter = 0;
        double stationInner = 0;
        int stationSpokes = 0;
    }

    private static final class EnginePoint {
        final int x;
        final int y;

        EnginePoint(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    private static final class ShipSkinSet {
        final BufferedImage albedo;
        final BufferedImage panel;
        final BufferedImage ao;
        final BufferedImage emissive;
        final BufferedImage damage;

        ShipSkinSet(BufferedImage albedo, BufferedImage panel, BufferedImage ao,
                    BufferedImage emissive, BufferedImage damage) {
            this.albedo = albedo;
            this.panel = panel;
            this.ao = ao;
            this.emissive = emissive;
            this.damage = damage;
        }

        boolean hasAlbedo() {
            return albedo != null;
        }

        boolean hasAnyLayer() {
            return albedo != null || panel != null || ao != null || emissive != null || damage != null;
        }
    }

    private static final class HullLightingPreset {
        final Color keyColor;
        final Color rimColor;
        final int keyAlpha;
        final int rimAlpha;
        final int deckAlpha;
        final int bellyAlpha;

        HullLightingPreset(Color keyColor, Color rimColor,
                           int keyAlpha, int rimAlpha, int deckAlpha, int bellyAlpha) {
            this.keyColor = keyColor;
            this.rimColor = rimColor;
            this.keyAlpha = keyAlpha;
            this.rimAlpha = rimAlpha;
            this.deckAlpha = deckAlpha;
            this.bellyAlpha = bellyAlpha;
        }

        static HullLightingPreset forFaction(Faction faction, Color hull, Color trim) {
            Color baseKey = brighten(hull, 46);
            Color baseRim = brighten(trim, 24);
            if (faction == null) {
                return new HullLightingPreset(baseKey, baseRim, 56, 48, 30, 24);
            }
            return switch (faction) {
                case PLAYER, ALLY -> new HullLightingPreset(baseKey, baseRim, 62, 52, 30, 23);
                case ENEMY -> new HullLightingPreset(baseKey, baseRim, 52, 40, 26, 26);
                case TEAM_C -> new HullLightingPreset(baseKey, baseRim, 58, 46, 29, 24);
                case TEAM_D -> new HullLightingPreset(baseKey, baseRim, 57, 48, 28, 24);
            };
        }

        private static Color brighten(Color c, int delta) {
            return new Color(
                    Math.min(255, c.getRed() + delta),
                    Math.min(255, c.getGreen() + delta),
                    Math.min(255, c.getBlue() + delta));
        }
    }

    private static final class ShipSkinLibrary {
        private static final String SKIN_DIR = "assets/ship_skins";
        private static final List<File> SKIN_ROOTS = resolveSkinRoots(SKIN_DIR);
        private static final Map<String, ShipSkinSet> CACHE = new HashMap<>();
        private static final Set<String> MISS = new HashSet<>();

        static boolean hasSkin(ShipRole role, Faction faction) {
            ShipSkinSet set = getSkinSet(role, faction);
            return set != null && set.hasAlbedo();
        }

        static BufferedImage getSkin(ShipRole role, Faction faction) {
            ShipSkinSet set = getSkinSet(role, faction);
            return (set == null) ? null : set.albedo;
        }

        static ShipSkinSet getSkinSet(ShipRole role, Faction faction) {
            String roleKey = keyForRole(role);
            String factionKey = keyForFaction(faction);
            String key = roleKey + "|" + factionKey;
            if (CACHE.containsKey(key)) return CACHE.get(key);
            if (MISS.contains(key)) return null;

            BufferedImage albedo = loadLayer(roleKey, factionKey, "albedo", true);
            BufferedImage panel = loadLayer(roleKey, factionKey, "panel", false);
            BufferedImage ao = loadLayer(roleKey, factionKey, "ao", false);
            BufferedImage emissive = loadLayer(roleKey, factionKey, "emissive", false);
            BufferedImage damage = loadLayer(roleKey, factionKey, "damage", false);

            ShipSkinSet set = new ShipSkinSet(albedo, panel, ao, emissive, damage);
            if (set.hasAnyLayer()) {
                CACHE.put(key, set);
                return set;
            }

            MISS.add(key);
            return null;
        }

        private static BufferedImage loadLayer(String roleKey, String factionKey, String layerKey, boolean includeLegacyRoleFallback) {
            String layerSuffix = "_" + layerKey;

            BufferedImage img = loadRoleSkin(factionKey + "/" + roleKey + layerSuffix);
            if (img != null) return img;

            img = loadRoleSkin(roleKey + "_" + factionKey + layerSuffix);
            if (img != null) return img;

            img = loadRoleSkin(roleKey + layerSuffix);
            if (img != null) return img;

            img = loadRoleSkin("default_" + factionKey + layerSuffix);
            if (img != null) return img;

            img = loadRoleSkin("default" + layerSuffix);
            if (img != null) return img;

            if (!includeLegacyRoleFallback) return null;

            img = loadRoleSkin(factionKey, roleKey);
            if (img != null) return img;

            img = loadRoleSkin(roleKey + "_" + factionKey);
            if (img != null) return img;

            img = loadRoleSkin(roleKey);
            if (img != null) return img;

            img = loadRoleSkin("default_" + factionKey);
            if (img != null) return img;

            return loadRoleSkin("default");
        }

        private static BufferedImage loadRoleSkin(String key) {
            for (File root : SKIN_ROOTS) {
                File f = new File(root, key + ".png");
                try {
                    if (f.isFile()) return ImageIO.read(f);
                } catch (IOException ignored) {}
            }
            return null;
        }

        private static BufferedImage loadRoleSkin(String factionKey, String roleKey) {
            return loadRoleSkin(factionKey + "/" + roleKey);
        }

        private static String keyForRole(ShipRole role) {
            if (role == null) return "frigate";
            return role.name().toLowerCase(Locale.ROOT);
        }

        private static String keyForFaction(Faction faction) {
            if (faction == null) return "ally";
            return switch (faction) {
                case PLAYER, ALLY -> "ally";
                case ENEMY -> "enemy";
                case TEAM_C -> "team_c";
                case TEAM_D -> "team_d";
            };
        }

        private static List<File> resolveSkinRoots(String relativeDir) {
            List<File> roots = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();

            addRootCandidate(new File(relativeDir), roots, seen);
            addAncestorCandidates(new File(System.getProperty("user.dir", ".")), relativeDir, 8, roots, seen);

            try {
                File codeSource = new File(Renderer.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                File start = codeSource.isDirectory() ? codeSource : codeSource.getParentFile();
                addAncestorCandidates(start, relativeDir, 8, roots, seen);
            } catch (Exception ignored) {}

            return roots;
        }

        private static void addAncestorCandidates(File start, String relativeDir, int maxDepth,
                                                  List<File> roots, Set<String> seen) {
            File current = start;
            for (int i = 0; i <= maxDepth && current != null; i++) {
                addRootCandidate(new File(current, relativeDir), roots, seen);
                current = current.getParentFile();
            }
        }

        private static void addRootCandidate(File dir, List<File> roots, Set<String> seen) {
            if (dir == null || !dir.isDirectory()) return;
            try {
                String canonical = dir.getCanonicalPath();
                if (seen.add(canonical)) roots.add(dir);
            } catch (IOException ignored) {}
        }
    }

    private static final class TurretSkinLibrary {
        private static final String SKIN_DIR = "assets/turret_skins";
        private static final List<File> SKIN_ROOTS = resolveSkinRoots(SKIN_DIR);
        private static final Map<String, BufferedImage> CACHE = new HashMap<>();
        private static final Set<String> MISS = new HashSet<>();

        static BufferedImage getTurretSkin(String styleKey, ShipRole role, Faction faction) {
            String safeStyle = (styleKey == null || styleKey.isBlank()) ? "twin_gun" : styleKey.toLowerCase(Locale.ROOT);
            String roleKey = keyForRole(role);
            String factionKey = keyForFaction(faction);
            String key = roleKey + "|" + factionKey + "|" + safeStyle;
            if (CACHE.containsKey(key)) return CACHE.get(key);
            if (MISS.contains(key)) return null;

            BufferedImage img = loadSkin(factionKey + "/" + roleKey + "_" + safeStyle);
            if (img == null) img = loadSkin(roleKey + "_" + factionKey + "_" + safeStyle);
            if (img == null) img = loadSkin(roleKey + "_" + safeStyle);
            if (img == null) img = loadSkin(factionKey + "/" + safeStyle);
            if (img == null) img = loadSkin(safeStyle + "_" + factionKey);
            if (img == null) img = loadSkin(safeStyle);
            if (img == null) img = loadSkin("default_" + factionKey + "_" + safeStyle);
            if (img == null) img = loadSkin("default_" + safeStyle);
            if (img == null) img = loadSkin("default_" + factionKey);
            if (img == null) img = loadSkin("default");

            if (img != null) {
                CACHE.put(key, img);
                return img;
            }

            MISS.add(key);
            return null;
        }

        private static BufferedImage loadSkin(String key) {
            for (File root : SKIN_ROOTS) {
                File f = new File(root, key + ".png");
                try {
                    if (f.isFile()) return ImageIO.read(f);
                } catch (IOException ignored) {}
            }
            return null;
        }

        private static String keyForRole(ShipRole role) {
            if (role == null) return "frigate";
            return role.name().toLowerCase(Locale.ROOT);
        }

        private static String keyForFaction(Faction faction) {
            if (faction == null) return "ally";
            return switch (faction) {
                case PLAYER, ALLY -> "ally";
                case ENEMY -> "enemy";
                case TEAM_C -> "team_c";
                case TEAM_D -> "team_d";
            };
        }

        private static List<File> resolveSkinRoots(String relativeDir) {
            List<File> roots = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();

            addRootCandidate(new File(relativeDir), roots, seen);
            addAncestorCandidates(new File(System.getProperty("user.dir", ".")), relativeDir, 8, roots, seen);

            try {
                File codeSource = new File(Renderer.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                File start = codeSource.isDirectory() ? codeSource : codeSource.getParentFile();
                addAncestorCandidates(start, relativeDir, 8, roots, seen);
            } catch (Exception ignored) {}

            return roots;
        }

        private static void addAncestorCandidates(File start, String relativeDir, int maxDepth,
                                                  List<File> roots, Set<String> seen) {
            File current = start;
            for (int i = 0; i <= maxDepth && current != null; i++) {
                addRootCandidate(new File(current, relativeDir), roots, seen);
                current = current.getParentFile();
            }
        }

        private static void addRootCandidate(File dir, List<File> roots, Set<String> seen) {
            if (dir == null || !dir.isDirectory()) return;
            try {
                String canonical = dir.getCanonicalPath();
                if (seen.add(canonical)) roots.add(dir);
            } catch (IOException ignored) {}
        }
    }

    private static final class EnvironmentSkinLibrary {
        private static final String BG_DIR = "assets/environment_overhaul_dropzone/background";
        private static final String AST_DIR = "assets/environment_overhaul_dropzone/asteroids";
        private static final List<File> BG_ROOTS = resolveRoots(BG_DIR);
        private static final List<File> AST_ROOTS = resolveRoots(AST_DIR);

        private static boolean bgLoaded = false;
        private static BufferedImage bgBase;
        private static BufferedImage bgNebula;
        private static BufferedImage bgStars;
        private static BufferedImage bgDust;

        private static boolean astLoaded = false;
        private static final Map<String, List<BufferedImage>> AST_NORMAL = new HashMap<>();
        private static final Map<String, List<BufferedImage>> AST_ORE = new HashMap<>();

        static BufferedImage backgroundBase() {
            ensureBackgroundLoaded();
            return bgBase;
        }

        static BufferedImage backgroundNebula() {
            ensureBackgroundLoaded();
            return bgNebula;
        }

        static BufferedImage backgroundStars() {
            ensureBackgroundLoaded();
            return bgStars;
        }

        static BufferedImage backgroundDust() {
            ensureBackgroundLoaded();
            return bgDust;
        }

        static BufferedImage pickAsteroidSprite(Asteroid a) {
            if (a == null) return null;
            ensureAsteroidsLoaded();
            if (AST_NORMAL.isEmpty() && AST_ORE.isEmpty()) return null;

            String sizeKey = sizeKeyForRadius(a.radius);
            List<BufferedImage> preferred = a.rich ? AST_ORE.get(sizeKey) : AST_NORMAL.get(sizeKey);
            List<BufferedImage> fallback = a.rich ? AST_NORMAL.get(sizeKey) : AST_ORE.get(sizeKey);
            List<BufferedImage> pool = (preferred != null && !preferred.isEmpty()) ? preferred : fallback;
            if (pool == null || pool.isEmpty()) return null;

            int idx = stableVariantIndex(a, pool.size());
            return pool.get(idx);
        }

        private static void ensureBackgroundLoaded() {
            if (bgLoaded) return;
            bgLoaded = true;

            bgBase = loadFirst(BG_ROOTS, new String[]{
                    "bg_space_base_4096_a",
                    "bg_space_base_tile_4096",
                    "bg_space_base_4096",
                    "bg_space_base_tile",
                    "bg_space_base"
            });
            bgNebula = loadFirst(BG_ROOTS, new String[]{
                    "bg_nebula_overlay_4096_a",
                    "bg_nebula_overlay_tile_4096",
                    "bg_nebula_overlay_4096",
                    "bg_nebula_overlay_tile",
                    "bg_nebula_overlay"
            });
            bgStars = loadFirst(BG_ROOTS, new String[]{
                    "bg_star_overlay_sparse_2048_a",
                    "bg_star_overlay_tile_2048",
                    "bg_star_overlay_sparse_2048",
                    "bg_star_overlay_tile",
                    "bg_star_overlay_sparse"
            });
            bgDust = loadFirst(BG_ROOTS, new String[]{
                    "bg_dust_parallax_2048_a",
                    "bg_dust_overlay_tile_2048",
                    "bg_dust_parallax_2048",
                    "bg_dust_overlay_tile",
                    "bg_dust_overlay"
            });
        }

        private static void ensureAsteroidsLoaded() {
            if (astLoaded) return;
            astLoaded = true;

            AST_NORMAL.clear();
            AST_ORE.clear();
            AST_NORMAL.put("small", new ArrayList<>());
            AST_NORMAL.put("med", new ArrayList<>());
            AST_NORMAL.put("large", new ArrayList<>());
            AST_ORE.put("small", new ArrayList<>());
            AST_ORE.put("med", new ArrayList<>());
            AST_ORE.put("large", new ArrayList<>());

            List<File> files = new ArrayList<>();
            for (File root : AST_ROOTS) {
                File[] pngs = root.listFiles((d, n) -> n != null && n.toLowerCase(Locale.ROOT).endsWith(".png"));
                if (pngs == null) continue;
                for (File f : pngs) files.add(f);
                if (!files.isEmpty()) break;
            }

            files.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File f : files) {
                String name = f.getName().toLowerCase(Locale.ROOT);
                if (!name.startsWith("ast_") || !name.endsWith(".png")) continue;

                String stem = name.substring(0, name.length() - 4);
                boolean ore = stem.endsWith("_ore");
                if (ore) stem = stem.substring(0, stem.length() - 4);

                String[] parts = stem.split("_");
                if (parts.length < 3) continue;
                String size = normalizeAstSize(parts[1]);
                if (size == null) continue;

                try {
                    BufferedImage img = ImageIO.read(f);
                    if (img == null) continue;
                    (ore ? AST_ORE : AST_NORMAL).get(size).add(img);
                } catch (IOException ex) {
                    System.err.println("[renderer] asteroid_skin_read_failed "
                            + f.getAbsolutePath() + " :: " + ex.getMessage());
                }
            }
        }

        private static String normalizeAstSize(String raw) {
            if (raw == null) return null;
            String s = raw.toLowerCase(Locale.ROOT);
            if (s.equals("small")) return "small";
            if (s.equals("med") || s.equals("medium")) return "med";
            if (s.equals("large")) return "large";
            return null;
        }

        private static String sizeKeyForRadius(double radius) {
            if (radius <= 30.0) return "small";
            if (radius <= 46.0) return "med";
            return "large";
        }

        private static int stableVariantIndex(Asteroid a, int modulo) {
            if (modulo <= 1) return 0;
            long h = 1469598103934665603L;
            h ^= Double.doubleToLongBits(a.x);
            h *= 1099511628211L;
            h ^= Double.doubleToLongBits(a.y);
            h *= 1099511628211L;
            h ^= Double.doubleToLongBits(a.radius);
            h *= 1099511628211L;
            h ^= (long) a.oreMax * 1315423911L;
            int v = (int) (h ^ (h >>> 32));
            return Math.floorMod(v, modulo);
        }

        private static BufferedImage loadFirst(List<File> roots, String[] keys) {
            for (File root : roots) {
                for (String key : keys) {
                    File f = new File(root, key + ".png");
                    try {
                        if (f.isFile()) return ImageIO.read(f);
                    } catch (IOException ignored) {}
                }
            }
            return null;
        }

        private static List<File> resolveRoots(String relativeDir) {
            List<File> roots = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();

            addRootCandidate(new File(relativeDir), roots, seen);
            addAncestorCandidates(new File(System.getProperty("user.dir", ".")), relativeDir, 8, roots, seen);

            try {
                File codeSource = new File(Renderer.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                File start = codeSource.isDirectory() ? codeSource : codeSource.getParentFile();
                addAncestorCandidates(start, relativeDir, 8, roots, seen);
            } catch (Exception ignored) {}

            return roots;
        }

        private static void addAncestorCandidates(File start, String relativeDir, int maxDepth,
                                                  List<File> roots, Set<String> seen) {
            File current = start;
            for (int i = 0; i <= maxDepth && current != null; i++) {
                addRootCandidate(new File(current, relativeDir), roots, seen);
                current = current.getParentFile();
            }
        }

        private static void addRootCandidate(File dir, List<File> roots, Set<String> seen) {
            if (dir == null || !dir.isDirectory()) return;
            try {
                String canonical = dir.getCanonicalPath();
                if (seen.add(canonical)) roots.add(dir);
            } catch (IOException ignored) {}
        }
    }

    private static final class ProjectileSkinLibrary {
        private static final String SKIN_DIR = "assets/projectile_skins";
        private static BufferedImage missileSkin;
        private static BufferedImage energyBoltSkin;
        private static BufferedImage beamBoltSkin;
        private static BufferedImage waveShotSkin;
        private static BufferedImage bulletSkin;
        private static BufferedImage ciwsPelletSkin;
        private static boolean missileSkinLoaded = false;
        private static boolean energyBoltSkinLoaded = false;
        private static boolean beamBoltSkinLoaded = false;
        private static boolean waveShotSkinLoaded = false;
        private static boolean bulletSkinLoaded = false;
        private static boolean ciwsPelletSkinLoaded = false;

        static BufferedImage getMissileSkin() {
            if (missileSkinLoaded) return missileSkin;
            missileSkinLoaded = true;
            missileSkin = loadSkin("missile");
            return missileSkin;
        }

        static BufferedImage getEnergyBoltSkin(boolean beamBoltVariant) {
            if (beamBoltVariant) {
                if (beamBoltSkinLoaded) return beamBoltSkin;
                beamBoltSkinLoaded = true;
                beamBoltSkin = loadSkin("beam_bolt");
                return beamBoltSkin;
            }
            if (energyBoltSkinLoaded) return energyBoltSkin;
            energyBoltSkinLoaded = true;
            energyBoltSkin = loadSkin("energy_bolt");
            return energyBoltSkin;
        }

        static BufferedImage getWaveShotSkin() {
            if (waveShotSkinLoaded) return waveShotSkin;
            waveShotSkinLoaded = true;
            waveShotSkin = loadSkin("wave_shot");
            return waveShotSkin;
        }

        static BufferedImage getBulletSkin() {
            if (bulletSkinLoaded) return bulletSkin;
            bulletSkinLoaded = true;
            bulletSkin = loadSkin("bullet");
            return bulletSkin;
        }

        static BufferedImage getCiwsPelletSkin() {
            if (ciwsPelletSkinLoaded) return ciwsPelletSkin;
            ciwsPelletSkinLoaded = true;
            ciwsPelletSkin = loadSkin("ciws_pellet");
            return ciwsPelletSkin;
        }

        private static BufferedImage loadSkin(String key) {
            String path = SKIN_DIR + "/" + key + ".png";
            try {
                File f = new File(path);
                if (f.isFile()) return ImageIO.read(f);
            } catch (IOException ignored) {}
            return null;
        }
    }

    private static void drawShipLegacy(Graphics2D g2, Ship ship) {
        if (!ship.alive) return;

        // Color palette per faction
        Color hull;
        Color trim;
        hull = factionHullColor(ship.faction);
        trim = factionTrimColor(ship.faction);

        int wx = (int) Math.round(ship.x);
        int wy = (int) Math.round(ship.y);

        Graphics2D g = (Graphics2D) g2.create();
        g.translate(wx, wy);
        g.rotate(ship.angle);

        // Stealth rendering: fade when not revealed.
        double sig = ship.effectiveSignature();
        if (ship.isStealth && sig < 0.99) {
            float a = (float) (0.22 + 0.78 * sig);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
        }

        Polygon hullPoly = switch (ship.role) {
            case PATROL -> hullPatrol(ship.radius);
            case PICKET -> hullPicket(ship.radius);
            case STEALTH_SHIP -> hullStealth(ship.radius);
            case FIGHTER -> hullFighter(ship.radius);
            case MISSILE_BOAT -> hullMissileBoat(ship.radius);
            case CIWS_CORVETTE -> hullCIWS(ship.radius);
            case LIGHT_CRUISER -> hullLightCruiser(ship.radius);
            case CRUISER, MEDIUM_CRUISER -> hullMediumCruiser(ship.radius);
            case BATTLECRUISER -> hullBattlecruiser(ship.radius);
            case BATTLESHIP -> hullBattleship(ship.radius);
            case DREADNOUGHT -> hullDreadnought(ship.radius);
            case SUPERSHIP -> hullDreadnought(ship.radius);
            case CARRIER -> hullCarrier(ship.radius);
            case BASE -> hullBase(ship.radius);
            default -> hullFrigate(ship.radius);
        };

        // Shadow
        g.setColor(new Color(0, 0, 0, 70));
        g.translate(4, 4);
        g.fillPolygon(hullPoly);
        g.translate(-4, -4);

        // Main hull (subtle shading gradient)
        int frontX = 0;
        int backX = 0;
        for (int i = 0; i < hullPoly.npoints; i++) {
            int px = hullPoly.xpoints[i];
            if (i == 0) { frontX = backX = px; }
            else {
                if (px > frontX) frontX = px;
                if (px < backX) backX = px;
            }
        }
        Color hullDark = new Color(Math.max(0, hull.getRed() - 35), Math.max(0, hull.getGreen() - 35), Math.max(0, hull.getBlue() - 35));
        Color hullLight = new Color(Math.min(255, hull.getRed() + 25), Math.min(255, hull.getGreen() + 25), Math.min(255, hull.getBlue() + 25));
        GradientPaint gp = new GradientPaint(backX, 0, hullDark, frontX, 0, hullLight);
        g.setPaint(gp);
        g.fillPolygon(hullPoly);
        g.setPaint(null);

        // Outline
        g.setColor(new Color(0, 0, 0, 110));
        g.drawPolygon(hullPoly);

        // Plating + deck details
        drawPlating(g, ship, hull, trim);

        // Engines
        drawEngines(g, ship);

        // Bridge / superstructure
        drawBridge(g, ship);

        // Shield ring/faces
        drawShipShieldFaces(g, ship);

        // Turrets
        drawTurrets(g, ship);

        // Damage decals / scorch marks
        drawDamageDecals(g, ship, hullPoly);

        // Stealth shimmer outline
        if (ship.isStealth && sig < 0.99) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f));
            g.setColor(new Color(120, 220, 255, 110));
            g.drawPolygon(hullPoly);
        }

        g.dispose();

        // Name tag
        if (!isTinyStrikeCraft(ship.role)) {
            g2.setFont(new Font("Consolas", Font.PLAIN, 12));
            g2.setColor(new Color(255, 255, 255, 130));
            g2.drawString(ship.name, wx - 18, wy - (int) ship.radius - 10);
        }
    }

    private static boolean isTinyStrikeCraft(ShipRole role) {
        if (role == null) return false;
        return role == ShipRole.FIGHTER || role == ShipRole.BOMBER || role == ShipRole.DRONE;
    }

    private static void drawPlating(Graphics2D g, Ship ship, Color hull, Color trim) {
        int r = (int) Math.round(ship.radius);

        // Armor belt (inset polygon)
        Polygon base = switch (ship.role) {
            case PATROL -> hullPatrol(ship.radius);
            case PICKET -> hullPicket(ship.radius);
            case STEALTH_SHIP -> hullStealth(ship.radius);
            case FIGHTER -> hullFighter(ship.radius);
            case MISSILE_BOAT -> hullMissileBoat(ship.radius);
            case CIWS_CORVETTE -> hullCIWS(ship.radius);
            case LIGHT_CRUISER -> hullLightCruiser(ship.radius);
            case CRUISER, MEDIUM_CRUISER -> hullMediumCruiser(ship.radius);
            case BATTLECRUISER -> hullBattlecruiser(ship.radius);
            case BATTLESHIP -> hullBattleship(ship.radius);
            case DREADNOUGHT -> hullDreadnought(ship.radius);
            case SUPERSHIP -> hullDreadnought(ship.radius);
            case CARRIER -> hullCarrier(ship.radius);
            case BASE -> hullBase(ship.radius);
            default -> hullFrigate(ship.radius);
        };

        if (ship.role != ShipRole.BASE) {
            Polygon inset = scalePolygon(base, 0.78);
            int dr = clamp255(hull.getRed() - 40);
            int dg = clamp255(hull.getGreen() - 40);
            int db = clamp255(hull.getBlue() - 40);
            g.setColor(new Color(dr, dg, db, 120));
            g.fillPolygon(inset);

            g.setColor(new Color(255, 255, 255, 45));
            g.drawPolygon(inset);
        }

        // Deck stripe / panels
        g.setColor(new Color(trim.getRed(), trim.getGreen(), trim.getBlue(), 120));
        drawDeckDetails(g, ship);

        // Simple portholes / windows on larger hulls
        if (ship.role == ShipRole.LIGHT_CRUISER || ship.role == ShipRole.MEDIUM_CRUISER || ship.role == ShipRole.CRUISER
                || ship.role == ShipRole.BATTLECRUISER || ship.role == ShipRole.BATTLESHIP
                || ship.role == ShipRole.DREADNOUGHT || ship.role == ShipRole.SUPERSHIP
                || ship.role == ShipRole.CARRIER) {
            g.setColor(new Color(255, 255, 255, 65));
            int n = Math.max(4, r / 4);
            for (int i = 0; i < n; i++) {
                int px = -r / 3 + i * (r / 3);
                g.fillRect(px, -r / 4, 2, 2);
                g.fillRect(px, r / 4, 2, 2);
            }
        }
    }

    private static void drawBridge(Graphics2D g, Ship ship) {
        int r = (int) Math.round(ship.radius);
        if (ship.role == ShipRole.BASE) return;

        // Carriers already have a runway-style deck; give them an offset island.
        if (ship.role == ShipRole.CARRIER) {
            g.setColor(new Color(255, 255, 255, 120));
            g.fillRoundRect(2, -r / 2, r / 3, r / 3, 8, 8);
            g.setColor(new Color(0, 0, 0, 80));
            g.drawRoundRect(2, -r / 2, r / 3, r / 3, 8, 8);
            return;
        }

        // Stealth ships: low-profile bridge
        if (ship.role == ShipRole.STEALTH_SHIP) {
            g.setColor(new Color(255, 255, 255, 70));
            g.fillRoundRect(r / 6, -r / 6, r / 5, r / 3, 10, 10);
            return;
        }

        int bx = r / 6;
        int by = -r / 6;
        int bw = r / 3;
        int bh = r / 3;

        if (ship.role == ShipRole.BATTLESHIP || ship.role == ShipRole.DREADNOUGHT
                || ship.role == ShipRole.BATTLECRUISER || ship.role == ShipRole.SUPERSHIP) {
            bx = r / 10;
            by = -r / 5;
            bw = r / 2;
            bh = r / 2;
        }

        g.setColor(new Color(255, 255, 255, 110));
        g.fillRoundRect(bx, by, bw, bh, 10, 10);
        g.setColor(new Color(0, 0, 0, 90));
        g.drawRoundRect(bx, by, bw, bh, 10, 10);
    }

    private static void drawDeckDetails(Graphics2D g, Ship ship) {
        int r = (int) Math.round(ship.radius);

        switch (ship.role) {
            case CARRIER -> {
                g.setColor(new Color(255, 255, 255, 90));
                g.drawLine(-r + 8, 0, r + 8, 0);
                g.drawLine(-r + 8, -r / 3, r + 4, -r / 3);
                g.drawLine(-r + 8, r / 3, r + 4, r / 3);

                g.setColor(new Color(255, 255, 255, 120));
                g.drawRect(-r / 2, -r + 6, r / 3, r / 2);

                g.setColor(new Color(255, 255, 255, 90));
                for (int i = 0; i < 5; i++) g.fillRect(-r / 2 + 3 + i * 5, -r + 10, 2, 2);
            }
            case MISSILE_BOAT -> {
                g.setColor(new Color(255, 255, 255, 110));
                g.drawRect(-r / 4, -r / 2, r / 2, r / 3);
                g.drawRect(-r / 4, r / 6, r / 2, r / 3);

                g.setColor(new Color(255, 255, 255, 70));
                g.drawLine(-r + 6, -r / 4, r - 2, -r / 4);
                g.drawLine(-r + 6, r / 4, r - 2, r / 4);
            }
            case CIWS_CORVETTE -> {
                g.setColor(new Color(255, 255, 255, 120));
                g.drawLine(-r / 2, 0, -r / 2, -r / 2);
                g.drawOval(-r / 2 - 4, -r / 2 - 10, 8, 8);

                g.setColor(new Color(255, 255, 255, 90));
                g.drawOval(-2, -2, 4, 4);

                g.setColor(new Color(255, 255, 255, 70));
                g.drawLine(-r + 4, 0, r, 0);
            }
            case BASE -> {
                g.setColor(new Color(255, 255, 255, 80));
                g.drawOval(-r, -r, r * 2, r * 2);
                g.drawOval(-(r - 10), -(r - 10), (r - 10) * 2, (r - 10) * 2);

                g.setColor(new Color(255, 255, 255, 110));
                g.drawLine(0, -r, 0, r);
                g.drawLine(-r, 0, r, 0);
            }
            case PATROL -> {
                g.setColor(new Color(255, 255, 255, 90));
                g.drawLine(-r + 6, -r / 4, r + 6, -r / 6);
                g.drawLine(-r + 6, r / 4, r + 6, r / 6);

                g.setColor(new Color(255, 255, 255, 120));
                g.drawOval(r / 6, -3, 6, 6);
            }
            case PICKET -> {
                g.setColor(new Color(255, 255, 255, 90));
                g.drawLine(-r + 6, 0, r + 8, 0);
                g.drawLine(-r / 2, -r / 3, r / 2, -r / 6);
                g.drawLine(-r / 2, r / 3, r / 2, r / 6);
            }
            case LIGHT_CRUISER, MEDIUM_CRUISER, CRUISER -> {
                g.setColor(new Color(255, 255, 255, 80));
                g.drawLine(-r + 6, -r / 3, r + 8, -r / 8);
                g.drawLine(-r + 6, r / 3, r + 8, r / 8);
                g.setColor(new Color(255, 255, 255, 115));
                g.drawRect(-r / 4, -r / 5, r / 3, r / 2);
                g.drawRect(r / 10, -r / 7, r / 4, r / 3);
            }
            case BATTLECRUISER, BATTLESHIP, DREADNOUGHT, SUPERSHIP -> {
                g.setColor(new Color(255, 255, 255, 75));
                g.drawLine(-r + 6, -r / 2, r + 10, -r / 6);
                g.drawLine(-r + 6, r / 2, r + 10, r / 6);
                g.drawLine(-r + 6, 0, r + 10, 0);

                g.setColor(new Color(255, 255, 255, 120));
                g.drawRect(-r / 5, -r / 4, r / 3, r / 2);
                g.drawRect(r / 8, -r / 6, r / 3, r / 3);
            }
            default -> {
                g.setColor(new Color(255, 255, 255, 80));
                g.drawLine(-r + 6, -r / 3, r + 4, -r / 6);
                g.drawLine(-r + 6, r / 3, r + 4, r / 6);

                g.setColor(new Color(255, 255, 255, 110));
                g.drawRect(-r / 3, -r / 4, r / 3, r / 2);
            }
        }
    }

    private static Polygon scalePolygon(Polygon p, double s) {
        int n = p.npoints;
        int[] xs = new int[n];
        int[] ys = new int[n];
        for (int i = 0; i < n; i++) {
            xs[i] = (int) Math.round(p.xpoints[i] * s);
            ys[i] = (int) Math.round(p.ypoints[i] * s);
        }
        return new Polygon(xs, ys, n);
    }

    private static Polygon roomPolygonShipLocal(double radius, double[] normalizedXs, double[] normalizedYs) {
        if (normalizedXs == null || normalizedYs == null) return null;
        int n = Math.min(normalizedXs.length, normalizedYs.length);
        if (n < 3) return null;
        int[] xs = new int[n];
        int[] ys = new int[n];
        for (int i = 0; i < n; i++) {
            xs[i] = (int) Math.round(normalizedXs[i] * radius);
            ys[i] = (int) Math.round(normalizedYs[i] * radius);
        }
        return new Polygon(xs, ys, n);
    }

    private static int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static void drawEngineNozzlePass(Graphics2D g, Ship ship, List<EnginePoint> engines, double radius) {
        if (g == null || ship == null || engines == null || engines.isEmpty()) return;

        Graphics2D g2 = (Graphics2D) g.create();
        double shimmer = engineShimmerIntensity(ship);
        double nozzleLen = Math.max(5.0, radius * 0.20);
        double nozzleDia = Math.max(4.0, radius * (engines.size() >= 4 ? 0.11 : 0.15));
        double shimmerLen = Math.max(3.5, radius * (0.18 + 0.28 * shimmer));

        for (EnginePoint p : engines) {
            if (p == null) continue;

            double x = p.x - nozzleLen * 0.35;
            double y = p.y - nozzleDia * 0.5;
            int rx = (int) Math.round(x);
            int ry = (int) Math.round(y);
            int rw = Math.max(3, (int) Math.round(nozzleLen));
            int rh = Math.max(3, (int) Math.round(nozzleDia));

            g2.setColor(new Color(18, 22, 28, 220));
            g2.fillRoundRect(rx, ry, rw, rh, rh, rh);

            g2.setColor(new Color(66, 78, 92, 176));
            g2.drawRoundRect(rx, ry, rw, rh, rh, rh);

            int throatW = Math.max(2, (int) Math.round(rw * 0.42));
            int throatH = Math.max(2, (int) Math.round(rh * 0.55));
            int throatX = rx - Math.max(1, (int) Math.round(rw * 0.12));
            int throatY = ry + (rh - throatH) / 2;
            g2.setColor(new Color(5, 7, 10, 230));
            g2.fillRoundRect(throatX, throatY, throatW, throatH, throatH, throatH);

            g2.setColor(new Color(118, 134, 148, 120));
            g2.drawLine(rx + Math.max(1, rw / 3), ry + 1, rx + rw - 2, ry + 1);

            if (shimmer > 0.01) {
                int aftX = throatX;
                int aftY0 = throatY;
                int aftY1 = throatY + throatH;
                int tailX = (int) Math.round(aftX - shimmerLen);
                int flare = Math.max(1, (int) Math.round(throatH * (0.18 + 0.30 * shimmer)));

                Polygon wake = new Polygon(
                        new int[]{aftX, tailX, aftX},
                        new int[]{aftY0, p.y, aftY1},
                        3
                );
                g2.setColor(new Color(158, 196, 210, MathUtil.clamp((int) Math.round(26 + 48 * shimmer), 0, 96)));
                g2.fillPolygon(wake);

                g2.setStroke(new BasicStroke(Math.max(1.1f, (float) (0.9 + shimmer * 0.9)), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(232, 245, 250, MathUtil.clamp((int) Math.round(18 + 34 * shimmer), 0, 88)));
                g2.drawLine(aftX, p.y, tailX, p.y);
                g2.setColor(new Color(112, 150, 168, MathUtil.clamp((int) Math.round(18 + 28 * shimmer), 0, 70)));
                g2.drawLine(aftX, aftY0 - flare, tailX + Math.max(1, flare), p.y);
                g2.drawLine(aftX, aftY1 + flare, tailX + Math.max(1, flare), p.y);
            }
        }

        g2.dispose();
    }

    private static double engineShimmerIntensity(Ship ship) {
        if (ship == null || ship.role == ShipRole.BASE || ship.role == ShipRole.STATIC_TURRET) return 0.0;
        double dt = Math.max(1e-6, GameContext.DT);
        double speedPerSec = Math.hypot(ship.vx, ship.vy) / dt;
        double ceiling = Math.max(1.0, MovementModel.speedCeiling(ship));
        double speedFrac = MathUtil.clamp(speedPerSec / ceiling, 0.0, 1.0);
        double drive = speedFrac * (0.58 + 0.42 * ship.powerEnginesFrac());
        if (ship.isEmergencyThrustActive()) {
            drive = Math.max(drive, 0.88 + 0.12 * (1.0 - ship.emergencyThrustHeat()));
        }
        return MathUtil.clamp((drive - 0.52) / 0.40, 0.0, 1.0);
    }

    private static List<EnginePoint> enginePointsForLegacy(Ship ship) {
        if (ship == null || ship.role == null || ship.role == ShipRole.BASE || ship.role == ShipRole.STATIC_TURRET) {
            return java.util.Collections.emptyList();
        }

        int count = switch (ship.role) {
            case DRONE, FIGHTER, BOMBER, STEALTH_SHIP, PD_CRAFT -> 1;
            case PATROL, FRIGATE, PICKET, LIGHT_CRUISER, MISSILE_BOAT, MINER, TRANSPORT, HAULER, DRONE_CARRIER, CARRIER -> 2;
            case MEDIUM_CRUISER, CRUISER -> 3;
            case BATTLECRUISER, SUPERSHIP -> 4;
            case BATTLESHIP -> 5;
            case DREADNOUGHT -> 6;
            default -> 2;
        };

        int r = Math.max(6, (int) Math.round(ship.radius));
        int x = -r + 1;
        if (count == 1) {
            return java.util.Collections.singletonList(new EnginePoint(x, 0));
        }

        List<EnginePoint> points = new ArrayList<>();
        double span = Math.max(3.0, r * 0.48);
        for (int i = 0; i < count; i++) {
            double t = (count == 1) ? 0.5 : (double) i / (double) (count - 1);
            int y = (int) Math.round(-span + t * span * 2.0);
            points.add(new EnginePoint(x, y));
        }
        return points;
    }

    private static void drawEngines(Graphics2D g, Ship ship) {
        if (ship == null) return;
        drawEngineNozzlePass(g, ship, enginePointsForLegacy(ship), ship.radius);
    }

    private static void drawTurrets(Graphics2D g2, Ship ship) {
        if (ship == null || ship.turrets == null) return;
        if (ship.role == ShipRole.FIGHTER || ship.role == ShipRole.BOMBER || ship.role == ShipRole.DRONE) return;

        Color accent = factionTrimColor(ship.faction);
        final double GLOBAL_TURRET_SCALE = 0.5;
        for (Turret t : ship.turrets) {
            if (t == null) continue;

            double rel = MathUtil.normalizeAngle(t.angle - ship.angle);
            double fireFrac = turretFireFraction(t);
            TurretVisualScale scale = turretVisualScale(ship.role, t.kind);
            double bodyScale = scale.bodyScale * GLOBAL_TURRET_SCALE;
            double barrelScale = scale.barrelScale * GLOBAL_TURRET_SCALE;
            String styleKey = turretStyleKey(ship, t);
            BufferedImage turretSkin = TurretSkinLibrary.getTurretSkin(styleKey, ship.role, ship.faction);

            Graphics2D tg = (Graphics2D) g2.create();
            tg.translate(t.localX, t.localY);
            tg.rotate(rel);

            if (turretSkin != null) {
                drawTurretSkinSprite(tg, turretSkin, styleKey, fireFrac, bodyScale);
            } else {
                if (t.kind == Turret.Kind.MISSILE) {
                    drawMissilePodTurret(tg, t, accent, fireFrac, bodyScale, barrelScale);
                } else if (isHeavyTurretRole(ship.role)) {
                    drawHeavyTripleTurret(tg, t, accent, fireFrac, bodyScale, barrelScale);
                } else if (ship.role == ShipRole.STEALTH_SHIP) {
                    drawStealthFlushTurret(tg, t, accent, fireFrac, bodyScale, barrelScale);
                } else if (ship.primaryWeaponFamily == Ship.PrimaryWeaponFamily.BEAM_BOLT) {
                    drawBeamEmitterTurret(tg, t, accent, fireFrac, bodyScale, barrelScale);
                } else {
                    drawTwinGunTurret(tg, t, accent, fireFrac, bodyScale, barrelScale);
                }
            }

            tg.dispose();
        }

        if (ship.hasCIWS) {
            double ciwsScale = turretVisualScale(ship.role, Turret.Kind.GUN).ciwsScale * GLOBAL_TURRET_SCALE;
            BufferedImage ciwsSkin = TurretSkinLibrary.getTurretSkin("ciws", ship.role, ship.faction);
            if (ciwsSkin != null) drawCiwsSkinSprite(g2, ciwsSkin, ciwsScale);
            else drawCIWSTurret(g2, ciwsScale);
        }
    }

    private static String turretStyleKey(Ship ship, Turret t) {
        if (t == null) return "twin_gun";
        if (t.kind == Turret.Kind.MISSILE) return "missile_pod";
        if (ship != null && isHeavyTurretRole(ship.role)) return "heavy_triple";
        if (ship != null && ship.role == ShipRole.STEALTH_SHIP) return "stealth_flush";
        if (ship != null && ship.primaryWeaponFamily == Ship.PrimaryWeaponFamily.BEAM_BOLT) return "beam_emitter";
        return "twin_gun";
    }

    private static void drawTurretSkinSprite(Graphics2D g, BufferedImage skin, String styleKey,
                                             double fireFrac, double bodyScale) {
        if (skin == null) return;
        double scaleNorm = Math.max(0.55, bodyScale / 0.5);
        double w = 26.0 * scaleNorm;
        double h = 16.0 * scaleNorm;

        if ("heavy_triple".equals(styleKey)) {
            w *= 1.24;
            h *= 1.12;
        } else if ("missile_pod".equals(styleKey)) {
            w *= 1.14;
            h *= 1.08;
        } else if ("stealth_flush".equals(styleKey)) {
            w *= 0.96;
            h *= 0.88;
        } else if ("beam_emitter".equals(styleKey)) {
            w *= 1.05;
            h *= 1.02;
        }

        int drawW = Math.max(8, (int) Math.round(w));
        int drawH = Math.max(6, (int) Math.round(h));
        int recoilPx = (int) Math.round(fireFrac * Math.max(1.0, drawW * 0.07));
        int x = -drawW / 2 - recoilPx;
        int y = -drawH / 2;
        g.drawImage(skin, x, y, drawW, drawH, null);
    }

    private static void drawCiwsSkinSprite(Graphics2D g, BufferedImage skin, double ciwsScale) {
        if (skin == null) return;
        double scaleNorm = Math.max(0.65, ciwsScale / 0.5);
        int draw = Math.max(10, (int) Math.round(20.0 * scaleNorm));
        g.drawImage(skin, -draw / 2, -draw / 2, draw, draw, null);
    }

    private static boolean isHeavyTurretRole(ShipRole role) {
        return role == ShipRole.BATTLECRUISER || role == ShipRole.BATTLESHIP
                || role == ShipRole.DREADNOUGHT || role == ShipRole.SUPERSHIP;
    }

    private static double turretFireFraction(Turret t) {
        if (t == null || t.cooldown <= 1e-6) return 0.0;
        double frac = t.getCooldownRemaining() / t.cooldown;
        return Math.max(0.0, Math.min(1.0, frac));
    }

    private static Color mix(Color a, Color b, double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        int r = (int) Math.round(a.getRed() * (1.0 - clamped) + b.getRed() * clamped);
        int g = (int) Math.round(a.getGreen() * (1.0 - clamped) + b.getGreen() * clamped);
        int bl = (int) Math.round(a.getBlue() * (1.0 - clamped) + b.getBlue() * clamped);
        return new Color(clamp255(r), clamp255(g), clamp255(bl));
    }

    private static TurretVisualScale turretVisualScale(ShipRole role, Turret.Kind kind) {
        if (role == null) return new TurretVisualScale(1.0, 1.0, 1.0);
        return switch (role) {
            case PATROL, PICKET, FIGHTER -> new TurretVisualScale(0.84, 0.86, 0.88);
            case FRIGATE, MISSILE_BOAT, CIWS_CORVETTE, MINER -> new TurretVisualScale(0.95, 0.96, 0.95);
            case LIGHT_CRUISER, CRUISER, MEDIUM_CRUISER, STEALTH_SHIP -> new TurretVisualScale(1.08, 1.07, 1.02);
            case BATTLECRUISER -> new TurretVisualScale(1.25, 1.15, 1.10);
            case BATTLESHIP -> new TurretVisualScale(1.35, 1.20, 1.18);
            case DREADNOUGHT -> new TurretVisualScale(1.48, 1.26, 1.26);
            case SUPERSHIP -> new TurretVisualScale(1.64, 1.34, 1.34);
            case CARRIER -> {
                if (kind == Turret.Kind.MISSILE) yield new TurretVisualScale(1.12, 1.06, 1.10);
                yield new TurretVisualScale(0.96, 0.94, 1.05);
            }
            case BASE -> new TurretVisualScale(1.30, 1.18, 1.30);
            default -> new TurretVisualScale(1.0, 1.0, 1.0);
        };
    }

    private static void drawTwinGunTurret(Graphics2D g, Turret t, Color accent, double fireFrac, double bodyScale, double barrelScale) {
        int r = (int) Math.max(4, Math.round(t.radius * bodyScale));
        int baseW = r + 4;
        int baseH = r + 3;
        int capW = r + 8;
        int capH = r + 5;
        int barrelLen = (int) Math.max(8, Math.round(t.barrelLen * barrelScale));
        int recoil = (int) Math.round(fireFrac * 3.0);

        g.setColor(new Color(36, 40, 48, 210));
        g.fillOval(-baseW / 2, -baseH / 2, baseW, baseH);

        g.setColor(mix(new Color(120, 128, 140), accent, 0.35));
        g.fillRoundRect(-capW / 2, -capH / 2, capW, capH, 4, 4);
        g.setColor(new Color(0, 0, 0, 150));
        g.drawRoundRect(-capW / 2, -capH / 2, capW, capH, 4, 4);

        int yOff = Math.max(2, r / 3);
        int bw = Math.max(2, r / 3);
        g.setColor(new Color(210, 220, 235, 235));
        g.fillRoundRect(0 - recoil, -yOff - bw / 2, barrelLen, bw, 2, 2);
        g.fillRoundRect(0 - recoil, yOff - bw / 2, barrelLen, bw, 2, 2);
        g.setColor(new Color(28, 30, 36, 180));
        g.drawRoundRect(0 - recoil, -yOff - bw / 2, barrelLen, bw, 2, 2);
        g.drawRoundRect(0 - recoil, yOff - bw / 2, barrelLen, bw, 2, 2);

        if (fireFrac > 0.82) {
            int fx = barrelLen - recoil;
            g.setColor(new Color(255, 230, 150, 160));
            g.fillOval(fx - 3, -yOff - 2, 6, 6);
            g.fillOval(fx - 3, yOff - 2, 6, 6);
        }
    }

    private static void drawHeavyTripleTurret(Graphics2D g, Turret t, Color accent, double fireFrac, double bodyScale, double barrelScale) {
        int r = (int) Math.max(5, Math.round((t.radius + 1) * bodyScale));
        int baseW = r + 7;
        int baseH = r + 5;
        int capW = r + 12;
        int capH = r + 7;
        int barrelLen = (int) Math.max(11, Math.round((t.barrelLen + 2) * barrelScale));
        int recoil = (int) Math.round(fireFrac * 4.0);

        g.setColor(new Color(58, 66, 80, 200));
        g.fillRoundRect(-baseW / 2, -baseH / 2, baseW, baseH, 4, 4);

        g.setColor(mix(new Color(110, 118, 134), accent, 0.40));
        g.fillRoundRect(-capW / 2, -capH / 2, capW, capH, 5, 5);
        g.setColor(new Color(0, 0, 0, 160));
        g.drawRoundRect(-capW / 2, -capH / 2, capW, capH, 5, 5);

        int bw = Math.max(2, r / 3);
        int[] ys = new int[]{-Math.max(3, r / 2), 0, Math.max(3, r / 2)};
        g.setColor(new Color(210, 220, 235, 235));
        for (int y : ys) {
            g.fillRoundRect(1 - recoil, y - bw / 2, barrelLen, bw, 2, 2);
            g.setColor(new Color(28, 30, 36, 180));
            g.drawRoundRect(1 - recoil, y - bw / 2, barrelLen, bw, 2, 2);
            g.setColor(new Color(210, 220, 235, 235));
        }

        if (fireFrac > 0.8) {
            int fx = 1 + barrelLen - recoil;
            g.setColor(new Color(255, 225, 135, 170));
            for (int y : ys) g.fillOval(fx - 3, y - 3, 6, 6);
        }
    }

    private static void drawMissilePodTurret(Graphics2D g, Turret t, Color accent, double fireFrac, double bodyScale, double barrelScale) {
        int r = (int) Math.max(4, Math.round(t.radius * bodyScale));
        int w = r + 10;
        int h = r + 8;
        int recoil = (int) Math.round(fireFrac * 2.0);

        g.setColor(new Color(34, 38, 46, 215));
        g.fillRoundRect(-w / 2, -h / 2, w, h, 4, 4);
        g.setColor(mix(new Color(126, 132, 142), accent, 0.30));
        g.fillRoundRect(-w / 2 + 1, -h / 2 + 1, w - 2, h - 2, 4, 4);

        g.setColor(new Color(20, 24, 30, 190));
        int cell = Math.max(2, r / 3);
        for (int yy = -1; yy <= 1; yy += 2) {
            for (int xx = 0; xx < 3; xx++) {
                int cx = -w / 4 + xx * (cell + 2);
                int cy = yy * (cell + 1) - recoil;
                g.fillRect(cx, cy, cell, cell);
            }
        }

        int hatchLen = (int) Math.max(8, Math.round(t.barrelLen * 0.55 * barrelScale));
        g.setColor(new Color(190, 205, 225, 220));
        g.fillRoundRect(0 - recoil, -1, hatchLen, 2, 2, 2);
        if (fireFrac > 0.85) {
            int fx = hatchLen - recoil;
            g.setColor(new Color(255, 180, 110, 170));
            g.fillOval(fx - 3, -3, 6, 6);
        }
    }

    private static void drawBeamEmitterTurret(Graphics2D g, Turret t, Color accent, double fireFrac, double bodyScale, double barrelScale) {
        int r = (int) Math.max(4, Math.round(t.radius * bodyScale));
        int w = r + 8;
        int h = r + 5;
        int barrelLen = (int) Math.max(8, Math.round(t.barrelLen * 0.85 * barrelScale));
        int recoil = (int) Math.round(fireFrac * 2.0);

        g.setColor(new Color(34, 40, 50, 210));
        g.fillOval(-w / 2, -h / 2, w, h);
        g.setColor(mix(new Color(118, 130, 146), accent, 0.25));
        g.fillRoundRect(-w / 2, -h / 2, w, h, 5, 5);

        g.setColor(new Color(150, 210, 255, 160));
        g.fillOval(-2, -2, 4, 4);
        g.setColor(new Color(210, 240, 255, 215));
        g.drawRoundRect(0 - recoil, -1, barrelLen, 2, 2, 2);

        if (fireFrac > 0.62) {
            int glow = (int) Math.round(70 + fireFrac * 120);
            g.setColor(new Color(120, 220, 255, Math.max(0, Math.min(220, glow))));
            g.fillOval(barrelLen - recoil - 4, -4, 8, 8);
        }
    }

    private static void drawStealthFlushTurret(Graphics2D g, Turret t, Color accent, double fireFrac, double bodyScale, double barrelScale) {
        int r = (int) Math.max(4, Math.round(t.radius * bodyScale));
        int len = (int) Math.max(9, Math.round(t.barrelLen * 0.8 * barrelScale));
        int recoil = (int) Math.round(fireFrac * 2.0);

        Polygon p = new Polygon(
                new int[]{-r, 0, r + 2, 0},
                new int[]{0, -r / 2, 0, r / 2}, 4);
        g.setColor(mix(new Color(68, 86, 108), accent, 0.18));
        g.fillPolygon(p);
        g.setColor(new Color(150, 210, 245, 130));
        g.drawLine(-1, 0, len - recoil, 0);

        if (fireFrac > 0.8) {
            int fx = len - recoil;
            g.setColor(new Color(170, 235, 255, 130));
            g.fillOval(fx - 2, -2, 4, 4);
        }
    }

    private static void drawCIWSTurret(Graphics2D g2, double ciwsScale) {
        Graphics2D g = (Graphics2D) g2.create();
        int rr = Math.max(3, (int) Math.round(4 * ciwsScale));
        int barrelLen = Math.max(7, (int) Math.round(9 * ciwsScale));
        g.setColor(new Color(80, 90, 105, 200));
        g.fillOval(-rr, -rr, rr * 2, rr * 2);
        g.setColor(new Color(205, 220, 240, 200));
        g.drawOval(-rr, -rr, rr * 2, rr * 2);

        long t = System.nanoTime();
        double a = (t % 2_000_000_000L) / 2_000_000_000.0 * Math.PI * 2.0;
        for (int i = 0; i < 3; i++) {
            double aa = a + i * (Math.PI * 2.0 / 3.0);
            int x2 = (int) Math.round(Math.cos(aa) * barrelLen);
            int y2 = (int) Math.round(Math.sin(aa) * barrelLen);
            g.setColor(new Color(190, 210, 235, 170));
            g.drawLine(0, 0, x2, y2);
        }
        g.dispose();
    }

    private static final class TurretVisualScale {
        final double bodyScale;
        final double barrelScale;
        final double ciwsScale;

        TurretVisualScale(double bodyScale, double barrelScale, double ciwsScale) {
            this.bodyScale = bodyScale;
            this.barrelScale = barrelScale;
            this.ciwsScale = ciwsScale;
        }
    }


    private static void drawDamageDecals(Graphics2D g, Ship ship, Shape hullShape) {
        if (ship == null || hullShape == null) return;
        if (ship.hpMax <= 0) return;

        double hpFrac = Math.max(0.0, Math.min(1.0, ship.hp / (double) ship.hpMax));
        double dmg = 1.0 - hpFrac; // 0..1
        if (dmg < 0.12) return;

        Rectangle bounds = hullShape.getBounds();
        if (bounds.width <= 0 || bounds.height <= 0) return;
        int span = Math.max(bounds.width, bounds.height);
        double screenScale = hullDamageDetailScale(g);
        if (span * screenScale < HULL_DAMAGE_DETAIL_MIN_SCREEN_SPAN) return;
        List<Ship.HullImpactMark> marks = ship.hullImpactMarks();

        Shape oldClip = g.getClip();
        Stroke oldStroke = g.getStroke();
        g.setClip(hullShape);

        try {
            if (!marks.isEmpty()) {
                int mCount = marks.size();
                int start = Math.max(0, mCount - 18);
                for (int i = start; i < mCount; i++) {
                    Ship.HullImpactMark mark = marks.get(i);
                    int px = (int) Math.round(mark.localX);
                    int py = (int) Math.round(mark.localY);
                    double sev = MathUtil.clamp(mark.severity, 0.04, 1.0);

                    int scorchSz = (int) Math.round(Math.max(1.0, (2.0 + sev * 10.0 + dmg * 5.0) * IMPACT_DECAL_SCALE));
                    int scorchA = (int) MathUtil.clamp(54 + sev * 108 + dmg * 42, 0, 200);
                    g.setColor(new Color(0, 0, 0, scorchA));
                    g.fillOval(px - scorchSz, py - scorchSz, scorchSz * 2, scorchSz * 2);
                    Color traceTint = roomTraceTint(mark.roomId,
                            (int) MathUtil.clamp(14 + sev * 60 + dmg * 24, 0, 132));

                    // Deformation: a displaced dent shadow + warm rim at the impact point.
                    int dent = Math.max(1, (int) Math.round((2 + sev * 6) * IMPACT_DECAL_SCALE));
                    g.setColor(new Color(5, 5, 6, (int) MathUtil.clamp(26 + sev * 80, 0, 145)));
                    g.fillOval(px - dent + 1, py - dent + 1, dent * 2, dent * 2);
                    g.setColor(traceTint);
                    g.drawOval(px - scorchSz, py - scorchSz, scorchSz * 2, scorchSz * 2);

                    double seedA = Math.abs(mark.localX * 0.027 + mark.localY * 0.019 + i * 0.171);
                    double dir = (seedA - Math.floor(seedA)) * Math.PI * 2.0;
                    int len = (int) Math.round(Math.max(1.0, (4 + sev * 18 + dmg * span * 0.10) * IMPACT_DECAL_SCALE));
                    int x2 = px + (int) Math.round(Math.cos(dir) * len);
                    int y2 = py + (int) Math.round(Math.sin(dir) * len);
                    float width = (float) Math.max(0.45, (0.9 + sev * 2.2) * IMPACT_DECAL_SCALE);
                    g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.setColor(new Color(12, 12, 14, (int) MathUtil.clamp(52 + sev * 95, 0, 180)));
                    g.drawLine(px, py, x2, y2);
                    g.setStroke(new BasicStroke(Math.max(0.35f, width * 0.42f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.setColor(roomTraceTint(mark.roomId, (int) MathUtil.clamp(12 + sev * 52, 0, 116)));
                    g.drawLine(px, py, x2, y2);

                }

                if (dmg > 0.55) {
                    int smoke = Math.min(8, Math.max(2, (int) Math.round(2 + dmg * 6)));
                    for (int i = 0; i < smoke; i++) {
                        Ship.HullImpactMark mark = marks.get(Math.max(0, mCount - 1 - i % Math.max(1, mCount)));
                        int px = (int) Math.round(mark.localX);
                        int py = (int) Math.round(mark.localY);
                        int sz = (int) Math.max(2, Math.round((6 + (0.4 + mark.severity) * 8) * IMPACT_DECAL_SCALE));
                        int a = (int) MathUtil.clamp(20 + (dmg - 0.55) * 140, 0, 110);
                        g.setColor(new Color(30, 30, 30, a));
                        g.fillOval(px - sz, py - sz, sz * 2, sz * 2);
                    }
                }
            } else {
                // Fallback for legacy/non-positional damage events.
                int n = (int) Math.round(4 + dmg * 12);
                long seed = (long) System.identityHashCode(ship) * 0x9E3779B97F4A7C15L;
                Random rng = new Random(seed);
                for (int i = 0; i < n; i++) {
                    Point hit = randomPointInShape(rng, bounds, hullShape, 18);
                    int px = hit.x;
                    int py = hit.y;
                    int sz = (int) Math.max(1, Math.round((2 + rng.nextDouble() * (4 + dmg * 10)) * IMPACT_DECAL_SCALE));
                    int a = (int) MathUtil.clamp(48 + dmg * 140, 0, 175);
                    g.setColor(new Color(0, 0, 0, a));
                    g.fillOval(px - sz, py - sz, sz * 2, sz * 2);
                }
            }
            drawDestroyedHullBreaches(g, ship, hullShape, marks, span);
            drawImpactHoleOverlays(g, marks);
        } finally {
            g.setStroke(oldStroke);
            g.setClip(oldClip);
        }
    }

    private static void drawImpactHoleOverlays(Graphics2D g, List<Ship.HullImpactMark> marks) {
        if (g == null || marks == null || marks.isEmpty()) return;
        int start = Math.max(0, marks.size() - 18);
        for (int i = start; i < marks.size(); i++) {
            Ship.HullImpactMark mark = marks.get(i);
            if (mark == null || mark.breachRadius <= 0.01) continue;

            int px = (int) Math.round(mark.localX);
            int py = (int) Math.round(mark.localY);
            double sev = MathUtil.clamp(mark.severity, 0.04, 1.0);
            int br = (int) Math.round(Math.max(1.0, mark.breachRadius * IMPACT_DECAL_SCALE));

            g.setColor(new Color(8, 8, 10, (int) MathUtil.clamp(95 + sev * 110, 0, 220)));
            g.fillOval(px - br, py - br, br * 2, br * 2);
            g.setColor(roomTraceTint(mark.roomId, (int) MathUtil.clamp(26 + sev * 58, 0, 140)));
            g.drawOval(px - br, py - br, br * 2, br * 2);
        }
    }

    private static void drawDestroyedHullBreaches(Graphics2D g,
                                                  Ship ship,
                                                  Shape hullShape,
                                                  List<Ship.HullImpactMark> marks,
                                                  int span) {
        if (g == null || ship == null || hullShape == null) return;

        EnumMap<ShipRoomLayout.RoomId, Area> shellAreas = destroyedHullShellAreas(ship, hullShape);
        if (shellAreas.isEmpty()) return;

        Stroke oldStroke = g.getStroke();
        for (Map.Entry<ShipRoomLayout.RoomId, Area> entry : shellAreas.entrySet()) {
            ShipRoomLayout.RoomId roomId = entry.getKey();
            Area shellArea = entry.getValue();
            if (shellArea == null || shellArea.isEmpty()) continue;
            ShipRoomLayout.RoomId facingRoom = breachFacingRoomId(roomId, shellArea.getBounds(), ship.radius);

            Area breachArea = buildDestroyedRoomBreachArea(ship, roomId, shellArea, marks, span);
            if (breachArea == null || breachArea.isEmpty()) continue;

            g.setColor(new Color(5, 6, 9, 232));
            g.fill(breachArea);

            float rim = Math.max(1.1f, (float) (1.0 + span * 0.0035));
            g.setStroke(new BasicStroke(rim, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(198, 208, 220, 82));
            g.draw(breachArea);

            drawHullBreachInterior(g, breachArea, roomId, facingRoom, span, ship);
        }
        g.setStroke(oldStroke);
    }

    private static EnumMap<ShipRoomLayout.RoomId, Area> destroyedHullShellAreas(Ship ship, Shape hullShape) {
        EnumMap<ShipRoomLayout.RoomId, Area> out = new EnumMap<>(ShipRoomLayout.RoomId.class);
        if (ship == null || hullShape == null) return out;

        Area hullArea = new Area(hullShape);
        Shape hullEdgeShape = new BasicStroke((float) Math.max(6.0, ship.radius * 0.22),
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(hullShape);
        Area hullEdgeBand = new Area(hullEdgeShape);
        for (ShipRoomLayout.VisualCell cell : ShipRoomLayout.visualCellsFor(ship.role, ship.faction)) {
            if (cell == null || cell.roomId == null) continue;
            if (ship.roomHealthFraction(cell.roomId) > 1e-3) continue;

            Polygon poly = roomPolygonShipLocal(ship.radius, cell.xs, cell.ys);
            if (poly == null || poly.npoints < 3) continue;

            Area cellArea = new Area(poly);
            cellArea.intersect(new Area(hullArea));
            if (cellArea.isEmpty()) continue;
            boolean hullFacing = isHullFacingCell(cellArea, hullEdgeBand);
            if (!ShipRoomLayout.isArmorRoom(cell.roomId) && !hullFacing) {
                cellArea = new Area(poly);
                cellArea.intersect(new Area(hullArea));
                if (cellArea.isEmpty()) continue;
            }

            out.computeIfAbsent(cell.roomId, key -> new Area()).add(cellArea);
        }
        return out;
    }

    private static boolean isHullFacingCell(Area cellArea, Area hullEdgeBand) {
        if (cellArea == null || hullEdgeBand == null || cellArea.isEmpty() || hullEdgeBand.isEmpty()) return false;
        Area overlap = new Area(cellArea);
        overlap.intersect(new Area(hullEdgeBand));
        return !overlap.isEmpty() && overlap.getBounds().width > 0 && overlap.getBounds().height > 0;
    }

    private static ShipRoomLayout.RoomId breachFacingRoomId(ShipRoomLayout.RoomId roomId, Rectangle bounds, double radius) {
        if (ShipRoomLayout.isArmorRoom(roomId)) return roomId;
        if (bounds == null || radius <= 1e-6) return ShipRoomLayout.RoomId.DORSAL_ARMOR;

        double nx = bounds.getCenterX() / Math.max(1.0, radius);
        double ny = bounds.getCenterY() / Math.max(1.0, radius);
        if (Math.abs(nx) > Math.abs(ny) * 1.15) {
            return (nx >= 0.0) ? ShipRoomLayout.RoomId.BOW_ARMOR : ShipRoomLayout.RoomId.AFT_ARMOR;
        }
        return (ny <= 0.0) ? ShipRoomLayout.RoomId.DORSAL_ARMOR : ShipRoomLayout.RoomId.VENTRAL_ARMOR;
    }

    private static Area buildDestroyedRoomBreachArea(Ship ship,
                                                     ShipRoomLayout.RoomId roomId,
                                                     Area shellArea,
                                                     List<Ship.HullImpactMark> marks,
                                                     int span) {
        if (ship == null || roomId == null || shellArea == null || shellArea.isEmpty()) return null;

        Rectangle bounds = shellArea.getBounds();
        if (bounds.width <= 0 || bounds.height <= 0) return null;

        Area breach = new Area();
        int placed = 0;
        if (marks != null && !marks.isEmpty()) {
            for (int i = Math.max(0, marks.size() - 8); i < marks.size(); i++) {
                Ship.HullImpactMark mark = marks.get(i);
                if (mark == null || mark.roomId != roomId) continue;

                double base = Math.max(5.0, Math.min(bounds.width, bounds.height) * 0.22);
                double radius = Math.max(base, mark.breachRadius * 2.8 + mark.severity * span * 0.050);
                Area shard = new Area(createBreachBlob(mark.localX, mark.localY, radius,
                        radius * (0.90 + mark.severity * 0.45),
                        breachSeed(ship, roomId, i)));
                shard.intersect(new Area(shellArea));
                if (!shard.isEmpty()) {
                    breach.add(shard);
                    placed++;
                }
            }
        }

        if (placed == 0) {
            double cx = bounds.getCenterX();
            double cy = bounds.getCenterY();
            double radius = Math.max(7.0, Math.min(bounds.width, bounds.height) * 0.34);
            Area fallback = new Area(createBreachBlob(cx, cy, radius, radius * 0.92,
                    breachSeed(ship, roomId, 0)));
            fallback.intersect(new Area(shellArea));
            breach.add(fallback);
        }

        Rectangle breachBounds = breach.getBounds();
        double shellScale = Math.max(8.0, Math.min(bounds.width, bounds.height));
        if (breachBounds.width < shellScale * 0.22 && breachBounds.height < shellScale * 0.22) {
            double cx = bounds.getCenterX();
            double cy = bounds.getCenterY();
            double radius = Math.max(5.0, Math.min(bounds.width, bounds.height) * 0.18);
            Area supplement = new Area(createBreachBlob(cx, cy, radius, radius * 0.78,
                    breachSeed(ship, roomId, 17)));
            supplement.intersect(new Area(shellArea));
            breach.add(supplement);
        }

        return breach;
    }

    private static double hullDamageDetailScale(Graphics2D g) {
        if (g == null) return 1.0;
        java.awt.geom.AffineTransform tx = g.getTransform();
        double sx = Math.hypot(tx.getScaleX(), tx.getShearX());
        double sy = Math.hypot(tx.getScaleY(), tx.getShearY());
        double scale = Math.max(Math.abs(sx), Math.abs(sy));
        if (!Double.isFinite(scale) || scale <= 1e-6) return 1.0;
        return scale;
    }

    private static Shape createBreachBlob(double cx, double cy, double rx, double ry, long seed) {
        Random rng = new Random(seed);
        int points = 10;
        Path2D.Double path = new Path2D.Double();
        for (int i = 0; i < points; i++) {
            double t = (Math.PI * 2.0 * i) / points;
            double jitter = 0.68 + rng.nextDouble() * 0.52;
            double px = cx + Math.cos(t) * rx * jitter;
            double py = cy + Math.sin(t) * ry * jitter;
            if (i == 0) path.moveTo(px, py);
            else path.lineTo(px, py);
        }
        path.closePath();
        return path;
    }

    private static void drawHullBreachInterior(Graphics2D g,
                                               Shape breachShape,
                                               ShipRoomLayout.RoomId roomId,
                                               ShipRoomLayout.RoomId facingRoom,
                                               int span,
                                               Ship ship) {
        if (g == null || breachShape == null) return;
        Rectangle b = breachShape.getBounds();
        if (b.width <= 0 || b.height <= 0) return;

        Graphics2D gi = (Graphics2D) g.create();
        gi.setClip(breachShape);

        drawClassSpecificBreachBackdrop(gi, ship, b, facingRoom);
        drawExposedInteriorRooms(gi, ship, breachShape, roomId, facingRoom);

        GradientPaint depth = new GradientPaint(
                b.x, b.y,
                new Color(18, 20, 24, 120),
                b.x + b.width, b.y + b.height,
                new Color(2, 3, 5, 0)
        );
        gi.setPaint(depth);
        gi.fillRect(b.x, b.y, b.width, b.height);

        long seed = breachSeed(ship, roomId, 91);
        Random rng = new Random(seed);
        gi.setStroke(new BasicStroke(Math.max(1.0f, (float) (span * 0.0018)), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        gi.setColor(new Color(76, 82, 92, 92));

        int verticals = Math.max(2, Math.min(6, b.width / 8));
        for (int i = 0; i < verticals; i++) {
            int x = b.x + (int) Math.round((i + 1.0) * b.width / (verticals + 1.0)) + rng.nextInt(3) - 1;
            gi.drawLine(x, b.y, x, b.y + b.height);
        }

        int horizontals = Math.max(1, Math.min(4, b.height / 10));
        for (int i = 0; i < horizontals; i++) {
            int y = b.y + (int) Math.round((i + 1.0) * b.height / (horizontals + 1.0)) + rng.nextInt(3) - 1;
            gi.drawLine(b.x, y, b.x + b.width, y);
        }

        gi.dispose();
    }

    private static void drawClassSpecificBreachBackdrop(Graphics2D g,
                                                        Ship ship,
                                                        Rectangle breachBounds,
                                                        ShipRoomLayout.RoomId breachedRoom) {
        if (g == null || ship == null || breachBounds == null) return;
        if (breachBounds.width <= 0 || breachBounds.height <= 0) return;

        String profile = ShipRoomLayout.profileIdForRole(ship.role);
        if (ship.role == ShipRole.CARRIER || ship.role == ShipRole.DRONE_CARRIER
                || "carrier".equals(profile)) {
            drawCarrierBreachBackdrop(g, breachBounds, breachedRoom, ship);
            return;
        }
        if (ship.role == ShipRole.BATTLESHIP || ship.role == ShipRole.DREADNOUGHT || ship.role == ShipRole.SUPERSHIP) {
            drawHeavyCapitalBreachBackdrop(g, breachBounds, breachedRoom, ship);
            return;
        }
        if (ship.role == ShipRole.LIGHT_CRUISER || ship.role == ShipRole.MEDIUM_CRUISER
                || ship.role == ShipRole.CRUISER || ship.role == ShipRole.BATTLECRUISER) {
            drawCruiserBreachBackdrop(g, breachBounds, breachedRoom, ship);
            return;
        }
        if (ship.role == ShipRole.BASE || ship.role == ShipRole.STATIC_TURRET || "station".equals(profile)) {
            drawStationBreachBackdrop(g, breachBounds, ship);
            return;
        }
        drawLightHullBreachBackdrop(g, breachBounds, breachedRoom, ship);
    }

    private static void drawCarrierBreachBackdrop(Graphics2D g,
                                                  Rectangle b,
                                                  ShipRoomLayout.RoomId breachedRoom,
                                                  Ship ship) {
        g.setColor(new Color(20, 24, 30, 160));
        g.fillRect(b.x, b.y, b.width, b.height);

        int laneCount = Math.max(2, Math.min(5, b.height / 9));
        for (int i = 0; i < laneCount; i++) {
            int y = b.y + (int) Math.round((i + 1.0) * b.height / (laneCount + 1.0));
            g.setColor(new Color(62, 72, 84, 110));
            g.drawLine(b.x, y, b.x + b.width, y);
        }

        int bayCount = Math.max(2, Math.min(6, b.width / 11));
        int bayW = Math.max(4, b.width / Math.max(3, bayCount + 1));
        int bayH = Math.max(4, b.height / Math.max(3, laneCount + 1));
        for (int i = 0; i < bayCount; i++) {
            int x = b.x + 2 + i * Math.max(5, bayW - 1);
            int y = b.y + ((i % 2 == 0) ? 2 : Math.max(2, b.height - bayH - 2));
            g.setColor(new Color(38, 46, 56, 132));
            g.fillRoundRect(x, y, bayW, bayH, 4, 4);
            g.setColor(new Color(108, 128, 146, 92));
            g.drawRoundRect(x, y, bayW, bayH, 4, 4);
        }

        if (breachedRoom == ShipRoomLayout.RoomId.BOW_ARMOR || breachedRoom == ShipRoomLayout.RoomId.AFT_ARMOR) {
            int catwalkY = b.y + b.height / 2;
            g.setColor(new Color(146, 164, 180, 86));
            g.drawLine(b.x, catwalkY, b.x + b.width, catwalkY);
        }
    }

    private static void drawCruiserBreachBackdrop(Graphics2D g,
                                                  Rectangle b,
                                                  ShipRoomLayout.RoomId breachedRoom,
                                                  Ship ship) {
        g.setColor(new Color(18, 22, 28, 152));
        g.fillRect(b.x, b.y, b.width, b.height);

        int ribs = Math.max(3, Math.min(8, b.width / 9));
        for (int i = 0; i < ribs; i++) {
            int x = b.x + (int) Math.round((i + 1.0) * b.width / (ribs + 1.0));
            g.setColor(new Color(74, 86, 98, 118));
            g.drawLine(x, b.y, x, b.y + b.height);
        }

        int decks = Math.max(1, Math.min(4, b.height / 12));
        for (int i = 0; i < decks; i++) {
            int y = b.y + (int) Math.round((i + 1.0) * b.height / (decks + 1.0));
            g.setColor(new Color(52, 60, 70, 92));
            g.drawLine(b.x, y, b.x + b.width, y);
        }

        if (breachedRoom == ShipRoomLayout.RoomId.DORSAL_ARMOR || breachedRoom == ShipRoomLayout.RoomId.VENTRAL_ARMOR) {
            int conduitX = b.x + b.width / 2;
            g.setColor(new Color(128, 142, 158, 84));
            g.drawLine(conduitX, b.y, conduitX, b.y + b.height);
        }
    }

    private static void drawHeavyCapitalBreachBackdrop(Graphics2D g,
                                                       Rectangle b,
                                                       ShipRoomLayout.RoomId breachedRoom,
                                                       Ship ship) {
        g.setColor(new Color(16, 18, 24, 168));
        g.fillRect(b.x, b.y, b.width, b.height);

        int bulkheads = Math.max(2, Math.min(5, b.width / 15));
        int bandW = Math.max(3, b.width / Math.max(3, bulkheads * 2));
        for (int i = 0; i < bulkheads; i++) {
            int x = b.x + 2 + i * Math.max(6, bandW + 3);
            g.setColor(new Color(44, 50, 58, 150));
            g.fillRect(x, b.y, Math.max(2, bandW / 2), b.height);
            g.setColor(new Color(118, 128, 138, 96));
            g.drawLine(x + Math.max(1, bandW / 4), b.y, x + Math.max(1, bandW / 4), b.y + b.height);
        }

        int armorBelts = Math.max(2, Math.min(4, b.height / 10));
        for (int i = 0; i < armorBelts; i++) {
            int y = b.y + (int) Math.round((i + 1.0) * b.height / (armorBelts + 1.0));
            g.setColor(new Color(70, 78, 88, 108));
            g.fillRect(b.x, y - 1, b.width, 2);
        }

        int spineX = (breachedRoom == ShipRoomLayout.RoomId.AFT_ARMOR) ? b.x + b.width / 3 : b.x + (b.width * 2) / 3;
        g.setColor(new Color(154, 166, 176, 74));
        g.drawLine(spineX, b.y, spineX, b.y + b.height);
    }

    private static void drawStationBreachBackdrop(Graphics2D g, Rectangle b, Ship ship) {
        g.setColor(new Color(18, 22, 30, 158));
        g.fillRect(b.x, b.y, b.width, b.height);

        int cx = b.x + b.width / 2;
        int cy = b.y + b.height / 2;
        int spokes = Math.max(4, Math.min(8, Math.max(b.width, b.height) / 8));
        g.setColor(new Color(88, 102, 120, 112));
        for (int i = 0; i < spokes; i++) {
            double a = (Math.PI * 2.0 * i) / spokes;
            int x2 = cx + (int) Math.round(Math.cos(a) * b.width * 0.55);
            int y2 = cy + (int) Math.round(Math.sin(a) * b.height * 0.55);
            g.drawLine(cx, cy, x2, y2);
        }
        g.setColor(new Color(62, 72, 86, 104));
        g.drawOval(b.x + b.width / 5, b.y + b.height / 5, Math.max(6, b.width * 3 / 5), Math.max(6, b.height * 3 / 5));
    }

    private static void drawLightHullBreachBackdrop(Graphics2D g,
                                                    Rectangle b,
                                                    ShipRoomLayout.RoomId breachedRoom,
                                                    Ship ship) {
        g.setColor(new Color(18, 22, 28, 144));
        g.fillRect(b.x, b.y, b.width, b.height);

        int frames = Math.max(2, Math.min(4, Math.max(b.width, b.height) / 10));
        for (int i = 0; i < frames; i++) {
            int inset = 1 + i * 3;
            int w = Math.max(4, b.width - inset * 2);
            int h = Math.max(4, b.height - inset * 2);
            if (w <= 4 || h <= 4) break;
            g.setColor(new Color(80, 94, 108, Math.max(38, 104 - i * 20)));
            g.drawRoundRect(b.x + inset, b.y + inset, w, h, 4, 4);
        }
    }

    private static void drawExposedInteriorRooms(Graphics2D g,
                                                 Ship ship,
                                                 Shape breachShape,
                                                 ShipRoomLayout.RoomId breachedRoom,
                                                 ShipRoomLayout.RoomId facingRoom) {
        if (g == null || ship == null || breachShape == null || breachedRoom == null || facingRoom == null) return;

        LinkedHashSet<ShipRoomLayout.RoomId> exposedRooms = exposedInteriorRoomIds(ship.role, breachedRoom);
        if (exposedRooms.isEmpty()) return;

        Rectangle breachBounds = breachShape.getBounds();
        double offsetStrength = breachOffsetStrength(breachBounds, ship.radius);
        double offset = Math.max(0.0, Math.min(breachBounds.width, breachBounds.height) * 0.24 * offsetStrength);
        int shiftX = 0;
        int shiftY = 0;
        switch (facingRoom) {
            case DORSAL_ARMOR -> shiftY = (int) Math.round(-offset);
            case VENTRAL_ARMOR -> shiftY = (int) Math.round(offset);
            case BOW_ARMOR -> shiftX = (int) Math.round(offset);
            case AFT_ARMOR -> shiftX = (int) Math.round(-offset);
            default -> {
                return;
            }
        }

        g.translate(shiftX, shiftY);
        for (ShipRoomLayout.VisualCell cell : ShipRoomLayout.visualCellsFor(ship.role, ship.faction)) {
            if (cell == null || cell.roomId == null) continue;
            if (!exposedRooms.contains(cell.roomId) || ShipRoomLayout.isArmorRoom(cell.roomId)) continue;

            Polygon poly = roomPolygonShipLocal(ship.radius, cell.xs, cell.ys);
            if (poly == null || poly.npoints < 3) continue;

            double frac = ship.roomHealthFraction(cell.roomId);
            Color tint = roomTraceTint(cell.roomId, 150);
            Color fill = new Color(
                    MathUtil.clamp((tint.getRed() + 16) / 2, 0, 255),
                    MathUtil.clamp((tint.getGreen() + 18) / 2, 0, 255),
                    MathUtil.clamp((tint.getBlue() + 22) / 2, 0, 255),
                    MathUtil.clamp((int) Math.round(96 + (1.0 - frac) * 54), 0, 170)
            );
            g.setColor(fill);
            g.fillPolygon(poly);
            g.setColor(roomTraceTint(cell.roomId, 112));
            g.drawPolygon(poly);
            drawSpecialRoomInteriorGraphic(g, poly, cell.roomId, frac);

            if (cell.labelAnchor) {
                Rectangle pb = poly.getBounds();
                if (pb.width >= 16 && pb.height >= 11) {
                    String symbol = xrayRoomSymbol(cell.roomId);
                    Font font = (pb.width >= 24 && pb.height >= 14) ? XRAY_REPAIR_FONT : new Font("Consolas", Font.BOLD, 7);
                    g.setFont(font);
                    FontMetrics fm = g.getFontMetrics();
                    int tx = (int) Math.round(pb.getCenterX()) - fm.stringWidth(symbol) / 2;
                    int ty = (int) Math.round(pb.getCenterY()) + Math.max(4, fm.getAscent() / 2 - 1);
                    g.setColor(new Color(238, 244, 248, 210));
                    g.drawString(symbol, tx, ty);
                }
            }
        }
        g.translate(-shiftX, -shiftY);
    }

    private static double breachOffsetStrength(Rectangle breachBounds, double radius) {
        if (breachBounds == null || radius <= 1e-6) return 1.0;
        double nx = Math.abs(breachBounds.getCenterX()) / Math.max(1.0, radius);
        double ny = Math.abs(breachBounds.getCenterY()) / Math.max(1.0, radius);
        double edge = Math.max(nx, ny);
        return MathUtil.clamp((edge - 0.38) / 0.40, 0.0, 1.0);
    }

    private static void drawSpecialRoomInteriorGraphic(Graphics2D g,
                                                       Polygon poly,
                                                       ShipRoomLayout.RoomId roomId,
                                                       double hpFrac) {
        if (g == null || poly == null || roomId == null) return;
        Rectangle b = poly.getBounds();
        if (b.width < 10 || b.height < 8) return;

        Graphics2D gi = (Graphics2D) g.create();
        gi.clip(poly);

        Color accent = roomTraceTint(roomId, MathUtil.clamp((int) Math.round(98 + (1.0 - hpFrac) * 42), 0, 150));
        Color fill = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 40);
        int cx = b.x + b.width / 2;
        int cy = b.y + b.height / 2;

        if (roomId == ShipRoomLayout.RoomId.REACTOR) {
            int r = Math.max(3, Math.min(b.width, b.height) / 4);
            gi.setColor(fill);
            gi.fillOval(cx - r, cy - r, r * 2, r * 2);
            gi.setColor(accent);
            gi.drawOval(cx - r - 2, cy - r - 2, (r + 2) * 2, (r + 2) * 2);
            gi.drawLine(cx, b.y + 2, cx, b.y + b.height - 2);
            gi.drawLine(b.x + 2, cy, b.x + b.width - 2, cy);
        } else if (roomId == ShipRoomLayout.RoomId.BRIDGE || roomId == ShipRoomLayout.RoomId.BOW) {
            gi.setColor(accent);
            gi.drawArc(b.x + 2, b.y + 2, Math.max(6, b.width - 4), Math.max(6, b.height - 4), 200, 140);
            gi.drawLine(b.x + 3, b.y + b.height - 4, b.x + b.width - 3, b.y + b.height - 4);
            gi.drawLine(cx, b.y + b.height / 2, cx, b.y + b.height - 4);
        } else if (ShipRoomLayout.isMagazineRoom(roomId)) {
            int cols = Math.max(2, Math.min(4, b.width / 10));
            int shellW = Math.max(3, b.width / Math.max(3, cols + 1));
            int shellH = Math.max(4, b.height / 3);
            gi.setColor(fill);
            for (int i = 0; i < cols; i++) {
                int x = b.x + 2 + i * (shellW + 2);
                int y = cy - shellH / 2;
                gi.fillRoundRect(x, y, shellW, shellH, 3, 3);
            }
            gi.setColor(accent);
            gi.drawLine(b.x + 2, b.y + b.height - 3, b.x + b.width - 2, b.y + b.height - 3);
        } else if (ShipRoomLayout.isShieldRoom(roomId)) {
            int r = Math.max(4, Math.min(b.width, b.height) / 3);
            gi.setColor(accent);
            gi.drawOval(cx - r, cy - r, r * 2, r * 2);
            gi.drawOval(cx - r / 2, cy - r / 2, r, r);
            gi.drawLine(b.x + 2, cy, b.x + b.width - 2, cy);
        } else if (ShipRoomLayout.isWarpRoom(roomId)) {
            int r = Math.max(4, Math.min(b.width, b.height) / 3);
            gi.setColor(accent);
            gi.drawOval(cx - r, cy - r / 2, r * 2, r);
            gi.drawOval(cx - r + 3, cy - r / 2 + 2, Math.max(4, r * 2 - 6), Math.max(4, r - 4));
            gi.drawLine(b.x + 3, cy, b.x + b.width - 3, cy);
        } else if (ShipRoomLayout.isPowerRoom(roomId)) {
            gi.setColor(accent);
            gi.drawLine(b.x + 2, cy, b.x + b.width - 2, cy);
            gi.drawLine(cx, b.y + 2, cx, b.y + b.height - 2);
            gi.fillOval(cx - 2, cy - 2, 4, 4);
        } else if (ShipRoomLayout.isEngineRoom(roomId) || roomId == ShipRoomLayout.RoomId.AFT_SPINE) {
            gi.setColor(accent);
            int lanes = Math.max(2, Math.min(4, b.height / 5));
            for (int i = 0; i < lanes; i++) {
                int y = b.y + 2 + i * Math.max(3, (b.height - 4) / Math.max(1, lanes));
                gi.drawLine(b.x + 2, y, b.x + b.width - 2, y);
            }
            gi.drawLine(b.x + b.width - 4, b.y + 2, b.x + b.width - 4, b.y + b.height - 2);
        } else if (roomId == ShipRoomLayout.RoomId.SENSORS) {
            gi.setColor(accent);
            gi.drawArc(b.x + 2, b.y + 2, Math.max(6, b.width - 4), Math.max(6, b.height - 4), 220, 100);
            gi.drawArc(b.x + 4, b.y + 4, Math.max(4, b.width - 8), Math.max(4, b.height - 8), 220, 100);
            gi.drawLine(cx, cy, b.x + b.width - 3, b.y + 3);
        } else if (ShipRoomLayout.isWeaponRoom(roomId)) {
            gi.setColor(accent);
            gi.drawLine(b.x + 2, cy - 2, b.x + b.width - 4, cy - 2);
            gi.drawLine(b.x + 2, cy + 2, b.x + b.width - 4, cy + 2);
            gi.drawLine(b.x + b.width - 5, cy - 4, b.x + b.width - 2, cy);
            gi.drawLine(b.x + b.width - 5, cy + 4, b.x + b.width - 2, cy);
        } else if (roomId == ShipRoomLayout.RoomId.CARGO_BAY) {
            gi.setColor(fill);
            int crate = Math.max(4, Math.min(b.width, b.height) / 4);
            gi.fillRect(b.x + 2, b.y + 2, crate, crate);
            gi.fillRect(cx - crate / 2, cy - crate / 2, crate, crate);
            gi.fillRect(b.x + b.width - crate - 2, b.y + b.height - crate - 2, crate, crate);
            gi.setColor(accent);
            gi.drawLine(b.x + 2, b.y + 2, b.x + b.width - 2, b.y + b.height - 2);
        } else if (roomId == ShipRoomLayout.RoomId.CREW_QUARTERS || roomId == ShipRoomLayout.RoomId.SERVICE_BAY) {
            gi.setColor(accent);
            gi.drawLine(b.x + 2, cy, b.x + b.width - 2, cy);
            gi.drawLine(b.x + 2, b.y + 3, b.x + b.width - 2, b.y + 3);
            gi.drawLine(b.x + 2, b.y + b.height - 3, b.x + b.width - 2, b.y + b.height - 3);
        }

        gi.dispose();
    }

    private static LinkedHashSet<ShipRoomLayout.RoomId> exposedInteriorRoomIds(ShipRole role,
                                                                                ShipRoomLayout.RoomId breachedRoom) {
        LinkedHashSet<ShipRoomLayout.RoomId> out = new LinkedHashSet<>();
        HashSet<ShipRoomLayout.RoomId> visited = new HashSet<>();
        ShipRoomLayout.RoomDef root = ShipRoomLayout.roomForId(role, breachedRoom);
        if (root == null) return out;

        if (!ShipRoomLayout.isArmorRoom(breachedRoom)) {
            out.add(breachedRoom);
        }
        for (ShipRoomLayout.RoomId neighbor : root.neighbors) {
            collectExposedInteriorRoomIds(role, neighbor, 0, 2, visited, out);
        }
        return out;
    }

    private static void collectExposedInteriorRoomIds(ShipRole role,
                                                      ShipRoomLayout.RoomId roomId,
                                                      int depth,
                                                      int maxDepth,
                                                      Set<ShipRoomLayout.RoomId> visited,
                                                      LinkedHashSet<ShipRoomLayout.RoomId> out) {
        if (roomId == null || depth > maxDepth || visited.contains(roomId)) return;
        visited.add(roomId);

        ShipRoomLayout.RoomDef def = ShipRoomLayout.roomForId(role, roomId);
        if (def == null) return;
        if (!ShipRoomLayout.isArmorRoom(roomId)) {
            out.add(roomId);
        }
        if (depth == maxDepth) return;
        for (ShipRoomLayout.RoomId next : def.neighbors) {
            if (next == null || ShipRoomLayout.isArmorRoom(next)) continue;
            collectExposedInteriorRoomIds(role, next, depth + 1, maxDepth, visited, out);
        }
    }

    private static long breachSeed(Ship ship, ShipRoomLayout.RoomId roomId, int salt) {
        long base = (ship == null) ? 0L : (long) System.identityHashCode(ship);
        long room = (roomId == null) ? 0L : (roomId.ordinal() + 1L) * 0x9E3779B97F4A7C15L;
        return base * 1103515245L + room + salt * 2654435761L;
    }

    private static Color roomTraceTint(ShipRoomLayout.RoomId roomId, int alpha) {
        int a = MathUtil.clamp(alpha, 0, 255);
        if (roomId == null) return new Color(255, 178, 105, a);
        if (ShipRoomLayout.isArmorRoom(roomId)) return new Color(210, 224, 236, a);
        if (ShipRoomLayout.isPowerRoom(roomId)) return new Color(255, 198, 112, a);
        if (ShipRoomLayout.isWeaponRoom(roomId)) return new Color(255, 164, 94, a);
        if (ShipRoomLayout.isMagazineRoom(roomId)) return new Color(255, 96, 86, a);
        if (ShipRoomLayout.isShieldRoom(roomId)) return new Color(178, 166, 255, a);
        if (ShipRoomLayout.isEngineRoom(roomId) || ShipRoomLayout.isWarpRoom(roomId) || roomId == ShipRoomLayout.RoomId.AFT_SPINE) {
            return new Color((roomId == ShipRoomLayout.RoomId.WARP_DRIVE) ? 144 : 130,
                    (roomId == ShipRoomLayout.RoomId.WARP_DRIVE) ? 186 : 208,
                    255,
                    a);
        }
        if (roomId == ShipRoomLayout.RoomId.SENSORS) return new Color(132, 238, 226, a);
        if (roomId == ShipRoomLayout.RoomId.BRIDGE || roomId == ShipRoomLayout.RoomId.BOW) return new Color(255, 214, 138, a);
        return new Color(200, 214, 230, a);
    }

    private static Point randomPointInShape(Random rng, Rectangle bounds, Shape shape, int tries) {
        int px = (int) Math.round(bounds.getCenterX());
        int py = (int) Math.round(bounds.getCenterY());
        int maxTries = Math.max(6, tries);
        for (int t = 0; t < maxTries; t++) {
            px = bounds.x + rng.nextInt(Math.max(1, bounds.width));
            py = bounds.y + rng.nextInt(Math.max(1, bounds.height));
            if (shape.contains(px, py)) return new Point(px, py);
        }
        return new Point(px, py);
    }

    private static Polygon hullFighter(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        p.addPoint(r + 6, 0);
        p.addPoint(-r + 1, -r / 2);
        p.addPoint(-r, 0);
        p.addPoint(-r + 1, r / 2);
        return p;
    }

    private static Polygon hullFrigate(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        p.addPoint(r + 8, 0);
        p.addPoint(r - 6, -r / 2);
        p.addPoint(-r, -r / 2);
        p.addPoint(-r + 8, 0);
        p.addPoint(-r, r / 2);
        p.addPoint(r - 6, r / 2);
        return p;
    }

    private static Polygon hullMissileBoat(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        p.addPoint(r + 6, 0);
        p.addPoint(r - 8, -r / 2);
        p.addPoint(-r, -r / 2);
        p.addPoint(-r + 10, 0);
        p.addPoint(-r, r / 2);
        p.addPoint(r - 8, r / 2);
        return p;
    }

    private static Polygon hullCarrier(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        p.addPoint(r + 8, 0);
        p.addPoint(r - 8, -r);
        p.addPoint(-r, -r);
        p.addPoint(-r + 14, 0);
        p.addPoint(-r, r);
        p.addPoint(r - 8, r);
        return p;
    }

    private static Polygon hullCIWS(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        p.addPoint(r + 6, 0);
        p.addPoint(r - 6, -r / 2);
        p.addPoint(-r, -r / 3);
        p.addPoint(-r + 6, 0);
        p.addPoint(-r, r / 3);
        p.addPoint(r - 6, r / 2);
        return p;
    }

    private static Polygon hullBase(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        // diamond-ish station
        p.addPoint(0, -r);
        p.addPoint(r, 0);
        p.addPoint(0, r);
        p.addPoint(-r, 0);
        return p;
    }

    // ------------------------------
    // New hull silhouettes (art pass)
    // ------------------------------

    private static Polygon hullPatrol(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        p.addPoint(r + 7, 0);
        p.addPoint(r - 2, -r / 2);
        p.addPoint(-r, -r / 3);
        p.addPoint(-r + 6, 0);
        p.addPoint(-r, r / 3);
        p.addPoint(r - 2, r / 2);
        return p;
    }

    private static Polygon hullPicket(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        p.addPoint(r + 9, 0);
        p.addPoint(r - 4, -r / 2);
        p.addPoint(-r, -r / 2);
        p.addPoint(-r + 10, 0);
        p.addPoint(-r, r / 2);
        p.addPoint(r - 4, r / 2);
        return p;
    }

    private static Polygon hullStealth(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        // sleek diamond/knife
        p.addPoint(r + 10, 0);
        p.addPoint(r - 4, -r / 3);
        p.addPoint(-r + 2, -r / 2);
        p.addPoint(-r, 0);
        p.addPoint(-r + 2, r / 2);
        p.addPoint(r - 4, r / 3);
        return p;
    }

    private static Polygon hullLightCruiser(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        p.addPoint(r + 10, 0);
        p.addPoint(r - 6, -r / 2);
        p.addPoint(-r + 4, -r / 2);
        p.addPoint(-r, -r / 5);
        p.addPoint(-r + 6, 0);
        p.addPoint(-r, r / 5);
        p.addPoint(-r + 4, r / 2);
        p.addPoint(r - 6, r / 2);
        return p;
    }

    private static Polygon hullMediumCruiser(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        p.addPoint(r + 12, 0);
        p.addPoint(r - 7, -r / 2);
        p.addPoint(r - 12, -r / 2);
        p.addPoint(-r + 2, -r / 2);
        p.addPoint(-r, -r / 6);
        p.addPoint(-r + 10, 0);
        p.addPoint(-r, r / 6);
        p.addPoint(-r + 2, r / 2);
        p.addPoint(r - 12, r / 2);
        p.addPoint(r - 7, r / 2);
        return p;
    }

    private static Polygon hullBattlecruiser(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        p.addPoint(r + 14, 0);
        p.addPoint(r - 6, -r / 2);
        p.addPoint(r - 14, -r / 2);
        p.addPoint(-r + 2, -r / 2);
        p.addPoint(-r, -r / 4);
        p.addPoint(-r + 12, 0);
        p.addPoint(-r, r / 4);
        p.addPoint(-r + 2, r / 2);
        p.addPoint(r - 14, r / 2);
        p.addPoint(r - 6, r / 2);
        return p;
    }

    private static Polygon hullBattleship(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        p.addPoint(r + 16, 0);
        p.addPoint(r - 8, -r / 2);
        p.addPoint(r - 18, -r / 2);
        p.addPoint(-r + 2, -r / 2);
        p.addPoint(-r, -r / 3);
        p.addPoint(-r + 14, 0);
        p.addPoint(-r, r / 3);
        p.addPoint(-r + 2, r / 2);
        p.addPoint(r - 18, r / 2);
        p.addPoint(r - 8, r / 2);
        return p;
    }

    private static Polygon hullDreadnought(double radius) {
        int r = (int) Math.round(radius);
        Polygon p = new Polygon();
        p.addPoint(r + 18, 0);
        p.addPoint(r - 10, -r / 2);
        p.addPoint(r - 22, -r / 2);
        p.addPoint(-r + 2, -r / 2);
        p.addPoint(-r, -r / 2 + r / 6);
        p.addPoint(-r + 16, 0);
        p.addPoint(-r, r / 2 - r / 6);
        p.addPoint(-r + 2, r / 2);
        p.addPoint(r - 22, r / 2);
        p.addPoint(r - 10, r / 2);
        return p;
    }

    /**
     * If the locked target is offscreen, draw a small arrow at the edge of the screen pointing toward it.
     * Coordinates are in screen space (camX/camY are the world-space camera origin).
     */
    static void drawOffscreenTargetIndicator(Graphics2D g2, Ship target, double camX, double camY, int viewW, int viewH, double zoom) {
        if (target == null || !target.alive) return;

        double z = Math.max(1e-6, zoom);
        // Target in screen coords
        double sx = (target.x - camX) * z;
        double sy = (target.y - camY) * z;

        if (sx >= 0 && sx <= viewW && sy >= 0 && sy <= viewH) return; // on screen

        double cx = viewW / 2.0;
        double cy = viewH / 2.0;

        double vx = sx - cx;
        double vy = sy - cy;
        double len = Math.hypot(vx, vy);
        if (len < 1e-6) return;

        vx /= len;
        vy /= len;

        double margin = 22.0;

        // Ray from screen center: find earliest intersection with inset rectangle.
        double t = Double.POSITIVE_INFINITY;
        if (vx >  1e-6) t = Math.min(t, (viewW - margin - cx) / vx);
        if (vx < -1e-6) t = Math.min(t, (margin - cx) / vx);
        if (vy >  1e-6) t = Math.min(t, (viewH - margin - cy) / vy);
        if (vy < -1e-6) t = Math.min(t, (margin - cy) / vy);

        if (!Double.isFinite(t)) return;

        double px = cx + vx * t;
        double py = cy + vy * t;

        double size = 13.0;
        double perpX = -vy;
        double perpY =  vx;

        int x0 = (int) Math.round(px);
        int y0 = (int) Math.round(py);

        int x1 = (int) Math.round(px - vx * size + perpX * size * 0.55);
        int y1 = (int) Math.round(py - vy * size + perpY * size * 0.55);

        int x2 = (int) Math.round(px - vx * size - perpX * size * 0.55);
        int y2 = (int) Math.round(py - vy * size - perpY * size * 0.55);

        int[] xs = {x0, x1, x2};
        int[] ys = {y0, y1, y2};

        Color fill = factionHudColor(target.faction, 220);

        g2.setColor(fill);
        g2.fillPolygon(xs, ys, 3);
        g2.setColor(new Color(0, 0, 0, 160));
        g2.drawPolygon(xs, ys, 3);
    }

    private static Color factionHullColor(Faction f) {
        if (f == Faction.ENEMY) return new Color(220, 80, 80);
        if (f == Faction.PLAYER) return new Color(70, 220, 120);
        if (f == Faction.TEAM_C) return new Color(86, 196, 102);
        if (f == Faction.TEAM_D) return new Color(230, 166, 88);
        return new Color(120, 160, 245);
    }

    private static Color factionTrimColor(Faction f) {
        if (f == Faction.ENEMY) return new Color(255, 170, 170);
        if (f == Faction.PLAYER) return new Color(200, 255, 220);
        if (f == Faction.TEAM_C) return new Color(188, 255, 186);
        if (f == Faction.TEAM_D) return new Color(255, 218, 160);
        return new Color(220, 230, 255);
    }

    private static Color factionHudColor(Faction f, int alpha) {
        Color base;
        if (f == Faction.ENEMY) base = new Color(255, 170, 170);
        else if (f == Faction.PLAYER) base = new Color(180, 255, 220);
        else if (f == Faction.TEAM_C) base = new Color(188, 255, 186);
        else if (f == Faction.TEAM_D) base = new Color(255, 218, 160);
        else base = new Color(170, 220, 255);
        return withAlpha(base, alpha);
    }

    private static Color factionMapColor(Faction f, boolean isPlayer, int alpha) {
        Color base;
        if (isPlayer || f == Faction.PLAYER) base = new Color(90, 255, 140);
        else if (f == Faction.ENEMY) base = new Color(255, 90, 90);
        else if (f == Faction.TEAM_C) base = new Color(114, 230, 116);
        else if (f == Faction.TEAM_D) base = new Color(255, 188, 108);
        else base = new Color(140, 180, 255);
        return withAlpha(base, alpha);
    }

    private static Color withAlpha(Color c, int alpha) {
        if (c == null) c = Color.WHITE;
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), MathUtil.clamp(alpha, 0, 255));
    }

}



