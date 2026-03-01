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

            out.add(new DrawOp(p.depth, () -> drawShip(ctx, g2, s, p)));
        }
    }

    private static void drawShip(GameContext ctx, Graphics2D g2, Ship s, Proj p) {
        int size = (int) Math.max(5, Math.round((s.radius * 0.32 + 4.0) * p.scale));
        int sx = (int) Math.round(p.x);
        int sy = (int) Math.round(p.y);

        g2.setColor(new Color(0, 0, 0, 80));
        g2.fillOval(sx - size, sy + size / 2, size * 2, Math.max(3, size / 2));

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

        if (s.shieldActive && s.shieldMax > 0 && s.shield > 0) {
            double sf = GameMath.clamp(s.shield / s.shieldMax, 0.0, 1.0);
            g2.setColor(new Color(70, 180, 255, (int) Math.round(60 + 120 * sf)));
            g2.drawOval(sx - size - 3, sy - size - 3, size * 2 + 6, size * 2 + 6);
        }

        if (s == ctx.player) {
            g2.setColor(new Color(255, 255, 255, 120));
            g2.drawOval(sx - size - 6, sy - size - 6, size * 2 + 12, size * 2 + 12);
        }
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
        if (Double.isNaN(ctx.waypointX) || Double.isNaN(ctx.waypointY)) return;
        Proj p = project(ctx, w, h, ctx.waypointX, ctx.waypointY, 20, cameraTilt, cameraZoom);
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
                ctx.perfFps, ctx.credits, CampaignSystem.oreCreditMul(ctx), cameraTilt, cameraZoom);
        int rw = g2.getFontMetrics().stringWidth(right);
        g2.drawString(right, Math.max(24, w - rw - 24), 72);

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
