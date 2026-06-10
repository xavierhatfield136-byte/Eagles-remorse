import java.awt.*;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Placeholder pseudo-3D renderer for migration bootstrap.
 * Uses isometric-style projection with depth-scaled sprites.
 */
final class Sandbox3DRenderer {
    private static final int SHIELD_RING_MIN_SCREEN_SIZE = 14;
    private static final double SHIELD_FX_MIN_MARK_FRESHNESS = 0.06;
    private static final Sandbox3DModelLibrary MODEL_LIBRARY = Sandbox3DModelLibrary.discoverDefault();

    private Sandbox3DRenderer() {}

    private static final class Proj {
        final double x;
        final double y;
        final double scale;
        final double depth;

        Proj(double x, double y, double scale, double depth) {
            this.x = x;
            this.y = y;
            this.scale = scale;
            this.depth = depth;
        }
    }

    private static final class DrawOp {
        final double depth;
        final Runnable draw;

        DrawOp(double depth, Runnable draw) {
            this.depth = depth;
            this.draw = draw;
        }
    }

    private static final class ModelTriOp {
        final double depth;
        final int[] xs;
        final int[] ys;
        final Color fill;

        ModelTriOp(double depth, int[] xs, int[] ys, Color fill) {
            this.depth = depth;
            this.xs = xs;
            this.ys = ys;
            this.fill = fill;
        }
    }

    private static final class Vertex {
        final int x;
        final int y;
        final double depth;

        Vertex(int x, int y, double depth) {
            this.x = x;
            this.y = y;
            this.depth = depth;
        }
    }

    static void render(GameContext ctx, Graphics2D g2, int w, int h, double cameraTilt, double cameraZoom) {
        paintBackdrop(ctx, g2, w, h);
        drawGroundGrid(ctx, g2, w, h, cameraTilt, cameraZoom);

        List<DrawOp> ops = new ArrayList<>();
        collectAsteroidOps(ctx, g2, w, h, cameraTilt, cameraZoom, ops);
        collectShipOps(ctx, g2, w, h, cameraTilt, cameraZoom, ops);
        collectProjectileOps(ctx, g2, w, h, cameraTilt, cameraZoom, ops);

        ops.sort(Comparator.comparingDouble(o -> o.depth));
        for (DrawOp op : ops) op.draw.run();

        drawCaptureIndicator(ctx, g2, w, h, cameraTilt, cameraZoom);
        drawWaypointIndicator(ctx, g2, w, h, cameraTilt, cameraZoom);
        drawTargetIndicator(ctx, g2, w, h, cameraTilt, cameraZoom);
        drawHud(ctx, g2, w, h, cameraTilt, cameraZoom);
    }

    private static void paintBackdrop(GameContext ctx, Graphics2D g2, int w, int h) {
        GradientPaint bg = new GradientPaint(
                0, 0, new Color(9, 18, 32),
                0, h, new Color(5, 8, 14));
        g2.setPaint(bg);
        g2.fillRect(0, 0, w, h);

        // Sparse stars
        g2.setColor(new Color(220, 230, 255, 120));
        for (int i = 0; i < 180; i++) {
            int sx = (int) ((i * 97L + 41L) % Math.max(1, w));
            int sy = (int) ((i * 53L + 29L) % Math.max(1, h));
            int r = ((i % 9) == 0) ? 2 : 1;
            g2.fillRect(sx, sy, r, r);
        }

        Color tint = CampaignSystem.worldTint(ctx);
        if (tint.getAlpha() > 0) {
            g2.setColor(tint);
            g2.fillRect(0, 0, w, h);
        }
    }

    private static void drawGroundGrid(GameContext ctx, Graphics2D g2, int w, int h, double cameraTilt, double cameraZoom) {
        g2.setColor(new Color(70, 120, 170, 36));

        double startX = Math.floor((ctx.camX - 800) / 360.0) * 360.0;
        double endX = ctx.camX + w + 800;
        double startY = Math.floor((ctx.camY - 800) / 360.0) * 360.0;
        double endY = ctx.camY + h + 800;

        for (double wy = startY; wy <= endY; wy += 360.0) {
            Proj p0 = project(ctx, w, h, startX, wy, 0, cameraTilt, cameraZoom);
            Proj p1 = project(ctx, w, h, endX, wy, 0, cameraTilt, cameraZoom);
            if (p0 == null || p1 == null) continue;
            g2.drawLine((int) Math.round(p0.x), (int) Math.round(p0.y),
                    (int) Math.round(p1.x), (int) Math.round(p1.y));
        }
        for (double wx = startX; wx <= endX; wx += 360.0) {
            Proj p0 = project(ctx, w, h, wx, startY, 0, cameraTilt, cameraZoom);
            Proj p1 = project(ctx, w, h, wx, endY, 0, cameraTilt, cameraZoom);
            if (p0 == null || p1 == null) continue;
            g2.drawLine((int) Math.round(p0.x), (int) Math.round(p0.y),
                    (int) Math.round(p1.x), (int) Math.round(p1.y));
        }
    }

    private static void collectAsteroidOps(GameContext ctx, Graphics2D g2, int w, int h, double cameraTilt, double cameraZoom, List<DrawOp> out) {
        for (Asteroid a : ctx.asteroids) {
            if (a == null || a.ore <= 0) continue;
            Proj p = project(ctx, w, h, a.x, a.y, 8.0, cameraTilt, cameraZoom);
            if (p == null) continue;

            out.add(new DrawOp(p.depth, () -> {
                int rr = (int) Math.max(3, Math.round(a.radius * 0.18 * p.scale));
                int sx = (int) Math.round(p.x);
                int sy = (int) Math.round(p.y);
                g2.setColor(new Color(0, 0, 0, 75));
                g2.fillOval(sx - rr, sy - rr / 3 + rr + 3, rr * 2, rr * 2 / 3);
                g2.setColor(a.rich ? new Color(170, 150, 95) : new Color(110, 118, 132));
                g2.fillOval(sx - rr, sy - rr, rr * 2, rr * 2);
                g2.setColor(new Color(220, 230, 255, 80));
                g2.drawOval(sx - rr, sy - rr, rr * 2, rr * 2);
            }));
        }
    }

    private static void collectShipOps(GameContext ctx, Graphics2D g2, int w, int h, double cameraTilt, double cameraZoom, List<DrawOp> out) {
        for (Ship s : ctx.ships) {
            if (s == null || !s.alive || s.hp <= 0) continue;
            Proj p = project(ctx, w, h, s.x, s.y, shipAltitude(s), cameraTilt, cameraZoom);
            if (p == null) continue;

            out.add(new DrawOp(p.depth, () -> drawShip(ctx, g2, w, h, s, p, cameraTilt, cameraZoom)));
        }
    }

    private static void drawShip(GameContext ctx, Graphics2D g2, int w, int h, Ship s, Proj p,
                                 double cameraTilt, double cameraZoom) {
        int size = (int) Math.max(5, Math.round((s.radius * 0.32 + 4.0) * p.scale));
        int sx = (int) Math.round(p.x);
        int sy = (int) Math.round(p.y);

        g2.setColor(new Color(0, 0, 0, 80));
        g2.fillOval(sx - size, sy + size / 2, size * 2, Math.max(3, size / 2));

        GlbModel model = MODEL_LIBRARY.modelFor(s.role, s.faction);
        if (model != null && model.isRenderable()) {
            drawGlbShip(ctx, g2, w, h, s, model, cameraTilt, cameraZoom, p.scale);
        } else {
            drawFallbackShip(g2, s, sx, sy, size);
            if (model != null && model.issue != null) {
                g2.setColor(new Color(255, 180, 90, 180));
                g2.drawString("GLB review", sx + size + 5, sy + 4);
            }
        }

        // Health sliver
        int barW = Math.max(10, size * 2);
        int barX = sx - barW / 2;
        int barY = sy - size - 10;
        g2.setColor(new Color(15, 20, 30, 180));
        g2.fillRect(barX, barY, barW, 4);
        double hpFrac = (s.hpMax <= 0) ? 0.0 : (s.hp / (double) s.hpMax);
        hpFrac = GameMath.clamp(hpFrac, 0.0, 1.0);
        g2.setColor(hpFrac > 0.5 ? new Color(70, 215, 120) : (hpFrac > 0.25 ? new Color(235, 186, 70) : new Color(220, 80, 80)));
        g2.fillRect(barX, barY, (int) Math.round(barW * hpFrac), 4);

        double effectiveShieldMax = s.effectiveShieldCapacityMax();
        if (s.shieldActive && effectiveShieldMax > 0.0 && s.shield > 0.0 && shouldRenderShieldRing(s, size)) {
            drawShieldRing(g2, s, sx, sy, size, effectiveShieldMax);
        }

        if (s == ctx.player) {
            g2.setColor(new Color(255, 255, 255, 120));
            g2.drawOval(sx - size - 6, sy - size - 6, size * 2 + 12, size * 2 + 12);
        }
    }

    private static void drawFallbackShip(Graphics2D g2, Ship s, int sx, int sy, int size) {
        double ang = s.angle;
        double cos = Math.cos(ang);
        double sin = Math.sin(ang);

        int fx = sx + (int) Math.round(cos * size * 1.45);
        int fy = sy + (int) Math.round(sin * size * 1.45);
        int lx = sx + (int) Math.round(Math.cos(ang + 2.36) * size);
        int ly = sy + (int) Math.round(Math.sin(ang + 2.36) * size);
        int rx = sx + (int) Math.round(Math.cos(ang - 2.36) * size);
        int ry = sy + (int) Math.round(Math.sin(ang - 2.36) * size);

        Path2D hull = new Path2D.Double();
        hull.moveTo(fx, fy);
        hull.lineTo(lx, ly);
        hull.lineTo(rx, ry);
        hull.closePath();

        g2.setColor(colorForFaction(s.faction));
        g2.fill(hull);
        g2.setColor(new Color(240, 245, 255, 170));
        g2.draw(hull);
    }

    private static void drawGlbShip(GameContext ctx, Graphics2D g2, int w, int h, Ship ship, GlbModel model,
                                    double cameraTilt, double cameraZoom, double projectedScale) {
        double size = Math.max(10.0, ship.radius * 0.70) * projectedScale;
        if (ship.role != null && ship.role.isTitanOrMothership()) size *= 1.18;
        if (ship.role == ShipRole.FIGHTER || ship.role == ShipRole.DRONE) size *= 0.82;

        double cos = Math.cos(ship.angle);
        double sin = Math.sin(ship.angle);
        Color base = colorForFaction(ship.faction);
        List<ModelTriOp> ops = new ArrayList<>(model.triangles.size());

        for (GlbModel.Triangle tri : model.triangles) {
            Vertex a = transformModelVertex(ctx, w, h, ship, tri.a, size, cos, sin, cameraTilt, cameraZoom);
            Vertex b = transformModelVertex(ctx, w, h, ship, tri.b, size, cos, sin, cameraTilt, cameraZoom);
            Vertex c = transformModelVertex(ctx, w, h, ship, tri.c, size, cos, sin, cameraTilt, cameraZoom);
            if (a == null || b == null || c == null) continue;

            double brightness = GameMath.clamp(0.56 + tri.avgZ * 0.30, 0.32, 1.0);
            Color fill = shade(base, brightness, 205);
            ops.add(new ModelTriOp(
                    (a.depth + b.depth + c.depth) / 3.0,
                    new int[]{a.x, b.x, c.x},
                    new int[]{a.y, b.y, c.y},
                    fill));
        }

        ops.sort(Comparator.comparingDouble(o -> o.depth));
        for (ModelTriOp op : ops) {
            g2.setColor(op.fill);
            g2.fillPolygon(op.xs, op.ys, 3);
        }
        if (!ops.isEmpty()) {
            g2.setColor(new Color(235, 245, 255, 96));
            int stride = Math.max(1, ops.size() / 110);
            for (int i = 0; i < ops.size(); i += stride) {
                ModelTriOp op = ops.get(i);
                g2.drawPolygon(op.xs, op.ys, 3);
            }
        }
    }

    private static Vertex transformModelVertex(GameContext ctx, int w, int h, Ship ship, double[] v, double size,
                                               double cos, double sin, double cameraTilt, double cameraZoom) {
        // Most generated ships are longest along local X; rotate local X/Y into the 2D battle plane.
        double localX = v[0] * size;
        double localY = v[1] * size * 0.54;
        double localZ = v[2] * size * 0.72;
        double wx = ship.x + localX * cos - localY * sin;
        double wy = ship.y + localX * sin + localY * cos;
        Proj p = project(ctx, w, h, wx, wy, shipAltitude(ship) + localZ, cameraTilt, cameraZoom);
        if (p == null) return null;
        return new Vertex((int) Math.round(p.x), (int) Math.round(p.y), p.depth);
    }

    private static Color shade(Color base, double mul, int alpha) {
        int r = (int) GameMath.clamp(base.getRed() * mul, 0, 255);
        int g = (int) GameMath.clamp(base.getGreen() * mul, 0, 255);
        int b = (int) GameMath.clamp(base.getBlue() * mul, 0, 255);
        return new Color(r, g, b, alpha);
    }

    private static void drawShieldRing(Graphics2D g2, Ship ship, int sx, int sy, int size, double effectiveShieldMax) {
        if (g2 == null || ship == null || effectiveShieldMax <= 0.0) return;
        double shieldFrac = GameMath.clamp(ship.shield / Math.max(1e-9, effectiveShieldMax), 0.0, 1.0);
        double wear = 1.0 - shieldFrac;
        int ring = size + 3;
        int diameter = ring * 2;
        int segments = 18;
        int segmentSpan = Math.max(8, (int) Math.round(360.0 / segments - 4.0));
        int startBase = Math.floorMod(ship.id * 37, 360);

        Graphics2D gx = (Graphics2D) g2.create();
        Stroke oldStroke = gx.getStroke();
        gx.setStroke(new BasicStroke(Math.max(1.2f, size * 0.18f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < segments; i++) {
            if (!shieldSegmentVisible(ship, i, wear)) continue;
            int start = startBase + (int) Math.round(i * (360.0 / segments));
            int alpha = (int) Math.round(42 + shieldFrac * 118);
            gx.setColor(new Color(70, 180, 255, Math.max(0, Math.min(255, alpha))));
            gx.drawArc(sx - ring, sy - ring, diameter, diameter, start, segmentSpan);
        }

        if (wear > 0.08) {
            int innerRing = Math.max(2, ring - 2);
            int innerDiameter = innerRing * 2;
            gx.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < segments; i++) {
                if (!shieldSegmentVisible(ship, i + segments, wear * 1.2)) continue;
                int start = startBase + 8 + (int) Math.round(i * (360.0 / segments));
                int alpha = (int) Math.round(26 + shieldFrac * 74);
                gx.setColor(new Color(160, 225, 255, Math.max(0, Math.min(255, alpha))));
                gx.drawArc(sx - innerRing, sy - innerRing, innerDiameter, innerDiameter, start, Math.max(6, segmentSpan - 8));
            }
        }

        if (ship.hasRecentShieldImpactTelemetry() && Double.isFinite(ship.recentShieldImpactAngle())) {
            double local = MathUtil.normalizeAngle(ship.recentShieldImpactAngle() - ship.angle);
            int hx = sx + (int) Math.round(Math.cos(local) * ring);
            int hy = sy + (int) Math.round(Math.sin(local) * ring);
            int haloR = Math.max(3, (int) Math.round(size * 0.28));
            gx.setColor(new Color(255, 255, 255, 120));
            gx.fillOval(hx - haloR, hy - haloR, haloR * 2, haloR * 2);
        }

        gx.setStroke(oldStroke);
        gx.dispose();
    }

    private static boolean shieldSegmentVisible(Ship ship, int segment, double wear) {
        double holeChance = GameMath.clamp(Math.max(0.0, wear - 0.04) * 1.08, 0.0, 0.92);
        long hash = ((long) ship.id * 1103515245L) ^ ((long) (segment + 1) * 12345L);
        hash ^= (hash >>> 16);
        double unit = (hash & 0xFFFFL) / 65535.0;
        return unit >= holeChance;
    }

    private static boolean shouldRenderShieldRing(Ship ship, int size) {
        if (ship == null || size < SHIELD_RING_MIN_SCREEN_SIZE) return false;
        if (ship.hasRecentShieldImpactTelemetry()) return true;
        List<Ship.ShieldImpactMark> marks = ship.shieldImpactMarks();
        if (marks == null || marks.isEmpty()) return false;
        for (int i = marks.size() - 1; i >= 0; i--) {
            Ship.ShieldImpactMark mark = marks.get(i);
            if (mark != null && mark.freshness() >= SHIELD_FX_MIN_MARK_FRESHNESS) return true;
        }
        return false;
    }

    private static void collectProjectileOps(GameContext ctx, Graphics2D g2, int w, int h, double cameraTilt, double cameraZoom, List<DrawOp> out) {
        for (Projectile p : ctx.projectiles) {
            if (p == null || !p.alive) continue;
            Proj a = project(ctx, w, h, p.x, p.y, 5.0, cameraTilt, cameraZoom);
            if (a == null) continue;
            Proj b = project(ctx, w, h, p.x + p.vx * 8.0, p.y + p.vy * 8.0, 5.0, cameraTilt, cameraZoom);
            if (b == null) b = a;

            final Proj aa = a;
            final Proj bb = b;
            out.add(new DrawOp(a.depth, () -> {
                g2.setColor(colorForFaction(p.faction));
                g2.drawLine((int) Math.round(aa.x), (int) Math.round(aa.y),
                        (int) Math.round(bb.x), (int) Math.round(bb.y));
            }));
        }
    }

    private static void drawCaptureIndicator(GameContext ctx, Graphics2D g2, int w, int h, double cameraTilt, double cameraZoom) {
        if (!CampaignSystem.hasCapturePoint(ctx)) return;
        Proj p = project(ctx, w, h, CampaignSystem.captureX(ctx), CampaignSystem.captureY(ctx), 0, cameraTilt, cameraZoom);
        if (p == null) return;
        double radius = CampaignSystem.captureRadius(ctx) * 0.32 * p.scale;
        int rr = (int) Math.max(12, Math.round(radius));
        int sx = (int) Math.round(p.x);
        int sy = (int) Math.round(p.y);
        g2.setColor(new Color(96, 218, 230, 110));
        g2.drawOval(sx - rr, sy - rr, rr * 2, rr * 2);
        g2.setColor(new Color(150, 240, 245, 120));
        g2.drawString("CAPTURE ZONE", sx - rr, sy - rr - 8);
    }

    private static void drawWaypointIndicator(GameContext ctx, Graphics2D g2, int w, int h, double cameraTilt, double cameraZoom) {
        if (Double.isNaN(ctx.ui.waypointX) || Double.isNaN(ctx.ui.waypointY)) return;
        Proj p = project(ctx, w, h, ctx.ui.waypointX, ctx.ui.waypointY, 20, cameraTilt, cameraZoom);
        if (p == null) return;
        int sx = (int) Math.round(p.x);
        int sy = (int) Math.round(p.y);
        int r = (int) Math.max(8, Math.round(10 * p.scale));
        g2.setColor(new Color(95, 225, 170, 220));
        g2.drawLine(sx, sy - r, sx + r, sy);
        g2.drawLine(sx + r, sy, sx, sy + r);
        g2.drawLine(sx, sy + r, sx - r, sy);
        g2.drawLine(sx - r, sy, sx, sy - r);
        g2.drawString("WP", sx + r + 4, sy - 2);
    }

    private static void drawTargetIndicator(GameContext ctx, Graphics2D g2, int w, int h, double cameraTilt, double cameraZoom) {
        Ship target = ctx.lockedTarget;
        if (target == null || !target.alive || target.hp <= 0) return;
        Proj p = project(ctx, w, h, target.x, target.y, shipAltitude(target), cameraTilt, cameraZoom);
        if (p == null) return;
        int sx = (int) Math.round(p.x);
        int sy = (int) Math.round(p.y);
        int rr = (int) Math.max(12, Math.round(target.radius * 0.50 * p.scale + 8));
        g2.setColor(new Color(255, 90, 90, 210));
        g2.drawOval(sx - rr, sy - rr, rr * 2, rr * 2);
        g2.drawString("LOCK", sx - rr + 2, sy - rr - 6);
    }

    private static void drawHud(GameContext ctx, Graphics2D g2, int w, int h, double cameraTilt, double cameraZoom) {
        g2.setFont(new Font("Consolas", Font.PLAIN, 14));

        g2.setColor(new Color(8, 14, 24, 190));
        g2.fillRoundRect(12, 10, w - 24, 70, 10, 10);
        g2.setColor(new Color(190, 220, 245, 220));
        g2.drawRoundRect(12, 10, w - 24, 70, 10, 10);

        String title = CampaignSystem.hudObjectiveTitle(ctx);
        String detail = CampaignSystem.hudObjectiveDetail(ctx);
        if (title == null || title.isBlank()) title = "SANDBOX";
        if (detail == null || detail.isBlank()) detail = "No objective";

        g2.setColor(Color.WHITE);
        g2.drawString(title, 24, 32);
        g2.setColor(new Color(210, 220, 230));
        g2.drawString(detail, 24, 52);

        g2.setColor(new Color(205, 220, 230));
        String right = String.format(
                "FPS %.1f  CREDITS %d  ORE x%.2f  TILT %.2f  ZOOM %.2f",
                ctx.perf.fps, ctx.credits, CampaignSystem.oreCreditMul(ctx), cameraTilt, cameraZoom);
        int rw = g2.getFontMetrics().stringWidth(right);
        g2.drawString(right, Math.max(24, w - rw - 24), 32);

        String models = MODEL_LIBRARY.summary();
        g2.setColor(new Color(150, 230, 210, 215));
        g2.drawString(models, 24, 72);

        if (ctx.eventBannerT > 0 && ctx.eventBanner != null && !ctx.eventBanner.isBlank()) {
            g2.setFont(new Font("Consolas", Font.BOLD, 18));
            int bw = g2.getFontMetrics().stringWidth(ctx.eventBanner);
            int bx = Math.max(16, (w - bw) / 2);
            int by = h - 42;
            g2.setColor(new Color(0, 0, 0, 170));
            g2.fillRoundRect(bx - 10, by - 20, bw + 20, 30, 8, 8);
            g2.setColor(new Color(245, 250, 255));
            g2.drawString(ctx.eventBanner, bx, by);
        }
    }

    private static Proj project(GameContext ctx, int w, int h, double wx, double wy, double altitude, double cameraTilt, double cameraZoom) {
        double localX = wx - ctx.camX;
        double localY = wy - ctx.camY;

        double isoX = (localX - localY * 0.42) * cameraZoom;
        double isoY = (localX * 0.08 + localY * (0.46 + cameraTilt * 0.26) - altitude) * cameraZoom;

        double depthNorm = GameMath.clamp(localY / Math.max(1.0, h), -0.4, 1.6);
        double scale = GameMath.clamp(0.62 + depthNorm * 0.58, 0.22, 1.45) * cameraZoom;

        double sx = w * 0.50 + isoX;
        double sy = h * 0.11 + isoY;

        if (sx < -260 || sx > w + 260 || sy < -260 || sy > h + 300) return null;
        return new Proj(sx, sy, scale, localY);
    }

    private static double shipAltitude(Ship s) {
        if (s == null) return 10;
        return switch (s.role) {
            case BASE -> 20;
            case BATTLESHIP, DREADNOUGHT -> 16;
            case BATTLECRUISER, MEDIUM_CRUISER, LIGHT_CRUISER, CRUISER -> 14;
            case CARRIER, DRONE_CARRIER -> 15;
            case FIGHTER, DRONE -> 9;
            default -> 11;
        };
    }

    private static Color colorForFaction(Faction faction) {
        if (faction == null) return new Color(180, 200, 220);
        return switch (faction) {
            case PLAYER, ALLY -> new Color(96, 175, 255);
            case ENEMY -> new Color(255, 110, 110);
            case TEAM_C -> new Color(255, 196, 90);
            case TEAM_D -> new Color(168, 132, 255);
        };
    }
}
