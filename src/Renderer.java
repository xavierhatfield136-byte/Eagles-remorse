import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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



    private static String fmt1(double v) {
        return String.format(java.util.Locale.US, "%.1f", v);
    }

    private static String signedPct(double mul) {
        double v = (mul - 1.0) * 100.0;
        if (!Double.isFinite(v)) v = 0.0;
        return String.format(Locale.US, "%+.0f%%", v);
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
        if (asteroids == null) return;
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

                g2.setColor(new Color(255, 255, 255, 220));
                g2.fillOval(x - r, y - r, r * 2, r * 2);

                double lx = pellet.x - Math.cos(pellet.angle) * 10;
                double ly = pellet.y - Math.sin(pellet.angle) * 10;
                g2.setColor(new Color(255, 255, 255, 140));
                g2.drawLine(x, y, (int) Math.round(lx), (int) Math.round(ly));
                continue;
            }

            if (p instanceof Missile m) {
                drawMissile(g2, m);
            } else if (p instanceof WaveMotionShot ws) {
                int x = (int) Math.round(ws.x);
                int y = (int) Math.round(ws.y);
                double nx = Math.cos(ws.angle);
                double ny = Math.sin(ws.angle);

                int len = (int) Math.round(Math.max(30.0, ws.radius * 5.6));
                int tail = len / 2;
                int head = len / 2;

                int x1 = (int) Math.round(ws.x - nx * tail);
                int y1 = (int) Math.round(ws.y - ny * tail);
                int x2 = (int) Math.round(ws.x + nx * head);
                int y2 = (int) Math.round(ws.y + ny * head);

                Stroke old = g2.getStroke();
                g2.setStroke(new BasicStroke((float) Math.max(6.0, ws.radius * 2.3), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(80, 205, 255, 110));
                g2.drawLine(x1, y1, x2, y2);

                g2.setStroke(new BasicStroke((float) Math.max(2.8, ws.radius * 1.1), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(230, 255, 255, 240));
                g2.drawLine(x1, y1, x2, y2);

                int glow = (int) Math.round(Math.max(8.0, ws.radius * 1.6));
                g2.setColor(new Color(140, 235, 255, 170));
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

                int r = (int) Math.round(Math.max(2.0, eb.radius));
                if (eb.isBeamBolt()) r = (int) Math.round(Math.max(r, 4.0));

                Stroke old = g2.getStroke();

                // soft outer glow line
                g2.setStroke(new BasicStroke(r * 1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(eb.isBeamBolt() ? new Color(80, 190, 255, 70) : new Color(110, 210, 255, 70));
                int gx1 = (int) Math.round(eb.x - nx * (r * 2.6));
                int gy1 = (int) Math.round(eb.y - ny * (r * 2.6));
                int gx2 = (int) Math.round(eb.x + nx * (r * 1.4));
                int gy2 = (int) Math.round(eb.y + ny * (r * 1.4));
                g2.drawLine(gx1, gy1, gx2, gy2);

                // bright core line
                g2.setStroke(new BasicStroke(r * 0.75f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(eb.isBeamBolt() ? new Color(220, 255, 255, 235) : new Color(190, 245, 255, 220));
                g2.drawLine(gx1, gy1, gx2, gy2);

                // end-cap flare
                int fx = (int) Math.round(eb.x + nx * (r * 2.0));
                int fy = (int) Math.round(eb.y + ny * (r * 2.0));
                g2.setColor(eb.isBeamBolt() ? new Color(235, 255, 255, 200) : new Color(220, 255, 255, 180));
                g2.fillOval(fx - r, fy - r, r * 2, r * 2);

                // subtle trailing segments (motion blur)
                g2.setStroke(new BasicStroke(r * 0.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(140, 220, 255, 70));
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

                int tx = (int) Math.round(p.x - p.vx * 3.0);
                int ty = (int) Math.round(p.y - p.vy * 3.0);

                g2.setColor(new Color(255, 255, 160, 120));
                g2.drawLine(tx, ty, x, y);

                g2.setColor(new Color(255, 255, 180, 220));
                g2.fillOval(x - r, y - r, r * 2, r * 2);
            }
        }
    }

    public static void drawWaveMotionAimCue(Graphics2D g2, Player player, double cursorWorldX, double cursorWorldY) {
        if (g2 == null || player == null) return;
        if (!player.alive || player.dying || player.hp <= 0) return;
        if (!player.hasWaveMotionGun) return;
        if (!player.isWaveMotionCharging()) return;

        double aim = player.getWaveMotionAimAngle();
        double len = 2200.0;
        double sx = player.x + Math.cos(aim) * (player.radius + 10.0);
        double sy = player.y + Math.sin(aim) * (player.radius + 10.0);
        double ex = sx + Math.cos(aim) * len;
        double ey = sy + Math.sin(aim) * len;

        float chargeFrac = (float) Math.max(0.0, Math.min(1.0, player.getWaveMotionChargeProgress()));
        boolean charging = player.isWaveMotionCharging();
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
            case TEAM_C -> new Color(150, 255, 120);
            case TEAM_D -> new Color(238, 186, 78);
        };
    }

    private static Color missileExhaustColor(Faction faction) {
        if (faction == null) return new Color(255, 186, 120);
        return switch (faction) {
            case PLAYER, ALLY -> new Color(130, 226, 255);
            case ENEMY -> new Color(255, 170, 112);
            case TEAM_C -> new Color(160, 255, 148);
            case TEAM_D -> new Color(255, 220, 138);
        };
    }

    public static void drawHUD(Graphics2D g2, Player player, int credits, int hangarTier, boolean dockedAtBase, boolean shopOpen, boolean autoLock, Ship lockedTarget,
                               int playerWingActive, int playerWingCap, int lockedWingActive, int lockedWingCap,
                               boolean resourceRush, int allyOre, int enemyOre, int goal, String gameOverText,
                               String objectiveTitle, String objectiveDetail,
                               String eventBanner, double eventBannerT, double orePriceMul, double orePriceT, double miningMul, double miningT,
                               double camX, double camY, int viewW, int viewH, double zoom, String stationStatus,
                               GameContext.HudDetail hudDetail, String contextHint, String overlayStatus) {
        int x = 14;
        int y = 18;

        g2.setFont(new Font("Consolas", Font.PLAIN, 14));
        g2.setColor(new Color(255, 255, 255, 220));

        g2.drawString("SHIP: " + (player.role == null ? "" : player.role.name()), x, y);
        y += 15;

        g2.drawString("CREDITS: " + credits, x, y);
        y += 30;

        if (objectiveTitle != null && !objectiveTitle.isBlank()) {
            g2.setColor(new Color(255, 230, 150, 230));
            g2.drawString(objectiveTitle, x, y);
            y += 18;
            g2.setColor(new Color(255, 255, 255, 220));
        }
        if (objectiveDetail != null && !objectiveDetail.isBlank()) {
            g2.drawString("OBJ: " + objectiveDetail, x, y);
            y += 20;
        }

        g2.drawString("HANGAR TIER: " + hangarTier + "  (dock + B to upgrade)", x, y);
        y += 18;

        // Cargo / mining
        if (player.cargoMax > 0) {
            g2.drawString("CARGO: " + player.cargo + " / " + player.cargoMax + "   (Hold F to mine)", x, y);
            y += 18;
            if (dockedAtBase) {
                g2.setColor(new Color(160, 220, 255, 220));
                g2.drawString("DOCKED: Ore auto-deposits   Press B for Base Upgrades (1-5)", x, y);
                g2.setColor(new Color(255, 255, 255, 220));
            }
            y += 18;
        }


        // Event modifiers
        if (Math.abs(orePriceMul - 1.0) > 0.01 && orePriceT > 0) {
            g2.drawString("ORE PRICE: x" + fmt1(orePriceMul) + "  (" + (int) Math.ceil(orePriceT) + "s)", x, y);
            y += 18;
        }
        if (Math.abs(miningMul - 1.0) > 0.01 && miningT > 0) {
            g2.drawString("MINING RATE: x" + fmt1(miningMul) + "  (" + (int) Math.ceil(miningT) + "s)", x, y);
            y += 18;
        }

        if (resourceRush) {
            g2.drawString("RESOURCE RUSH: ALLY " + allyOre + "  ENEMY " + enemyOre + "  GOAL " + goal, x, y);
            y += 18;
            if (gameOverText != null && !gameOverText.isBlank()) {
                g2.setFont(new Font("Consolas", Font.BOLD, 18));
                g2.setColor(new Color(255, 255, 255, 220));
                g2.drawString(gameOverText, x, y + 6);
                g2.setFont(new Font("Consolas", Font.PLAIN, 14));
                g2.setColor(new Color(255, 255, 255, 220));
                y += 24;
            }
        }
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

        g2.drawString("HP: " + player.hp + " / " + player.hpMax, x, y);
        int barW = 240;
        int barH = 10;

        int hpY = y + 8;
        g2.setColor(new Color(255, 255, 255, 70));
        g2.drawRect(x, hpY, barW, barH);
        double hpFrac = player.hpMax <= 0 ? 0 : Math.max(0, Math.min(1, (double) player.hp / player.hpMax));
        g2.setColor(new Color(80, 255, 120, 210));
        g2.fillRect(x + 1, hpY + 1, (int) Math.round((barW - 1) * hpFrac), barH - 1);

        int shY = hpY + 18;
        if (player.shieldActive && player.shieldMax > 0) {
            g2.setColor(new Color(255, 255, 255, 70));
            g2.drawRect(x, shY, barW, barH);
            double shFrac = Math.max(0, Math.min(1, player.shield / player.shieldMax));
            g2.setColor(new Color(120, 200, 255, 210));
            g2.fillRect(x + 1, shY + 1, (int) Math.round((barW - 1) * shFrac), barH - 1);
            if (!player.isShieldOnline()) {
                g2.setColor(new Color(255, 170, 120, 220));
                g2.drawString("SHIELD REBOOT: " + fmt1(player.getShieldOfflineRemaining()) + "s", x + barW + 12, shY + barH);
            }
        }
        if (player.isStealth) {
            int cy = shY + 18;
            g2.setColor(new Color(255, 255, 255, 70));
            g2.drawRect(x, cy, barW, barH);
            double cFrac = player.cloakEnergyFrac();
            g2.setColor(player.isCloaked() ? new Color(120, 255, 200, 210) : new Color(200, 220, 255, 170));
            g2.fillRect(x + 1, cy + 1, (int) Math.round((barW - 1) * cFrac), barH - 1);
            g2.setColor(new Color(190, 245, 220, 210));
            g2.drawString("CLOAK: " + (player.isCloaked() ? "ACTIVE" : "EXPOSED"), x + barW + 12, cy + barH);
        }

        y = 200;
        GameContext.HudDetail detail = (hudDetail == null) ? GameContext.HudDetail.FULL : hudDetail;
        g2.setColor(new Color(255, 255, 255, 170));
        if (detail == GameContext.HudDetail.FULL) {
            g2.drawString("LMB: guns   RMB: missiles", x, y);
            y += 18;
            g2.drawString("L: lock under mouse   [ ]: cycle targets   T: auto-lock", x, y);
            y += 18;
            g2.drawString("TAB: shop/loadout (3-9 upgrades, F1-F9/F11/F12/0/-/= hulls)   B: base upgrades", x, y);
            y += 18;
            String abilityKeys = player.hasWaveMotionGun
                    ? "Q: missile salvo   E: shield overcharge   X: wave gun   F: mine"
                    : "Q: missile salvo   E: shield overcharge   F: mine";
            g2.drawString(abilityKeys, x, y);
            y += 18;
            g2.drawString("O: power mgmt   H: crew stations   Y: power preset   U: crew order   I: shield mode   J/K: shield face", x, y);
            y += 18;
            if (player.isStealth) {
                g2.drawString("Stealth hull: cloak auto-engages when not firing/taking hits", x, y);
                y += 18;
            }
            if (player.isCarrier) {
                g2.drawString("C: launch wing   R: recall wing   V: attack/defend   Z: auto-launch", x, y);
                y += 18;
            }
            g2.drawString("ESC: pause/resume   Alt+Enter: fullscreen   N: cycle HUD detail", x, y);
            y += 22;
        } else if (detail == GameContext.HudDetail.COMPACT) {
            g2.drawString("LOCK: L / [ ]   FIRE: LMB RMB SPACE SHIFT   TARGET AI: T", x, y);
            y += 18;
            g2.drawString("ABILITIES: Q salvo   E overcharge" + (player.hasWaveMotionGun ? "   X wave gun" : "") + "   F mine", x, y);
            y += 18;
            g2.drawString("OVERLAYS: TAB shop   B base   M map   O power   H stations", x, y);
            y += 18;
            g2.drawString("N: HUD detail   ESC: pause/resume", x, y);
            y += 22;
        } else {
            g2.drawString("N: HUD detail   L: lock   TAB/B/M/O/H: overlays", x, y);
            y += 22;
        }

        if (contextHint != null && !contextHint.isBlank()) {
            g2.setColor(new Color(255, 225, 150, 225));
            g2.drawString("HINT: " + contextHint, x, y);
            y += 20;
            g2.setColor(new Color(255, 255, 255, 170));
        }

        g2.setColor(new Color(255, 255, 255, 170));
        g2.drawString("AUTO-LOCK: " + (autoLock ? "ON" : "OFF"), x, y);
        y += 18;
        if (stationStatus != null && !stationStatus.isBlank()) {
            g2.drawString(stationStatus, x, y);
            y += 18;
        }
        if (overlayStatus != null && !overlayStatus.isBlank()) {
            g2.setColor(new Color(180, 220, 255, 220));
            g2.drawString(overlayStatus, x, y);
            y += 18;
            g2.setColor(new Color(255, 255, 255, 170));
        }

        int pEng = (int) Math.round(player.powerEnginesFrac() * 100.0);
        int pShd = (int) Math.round(player.powerShieldsFrac() * 100.0);
        int pWep = (int) Math.round(player.powerWeaponsFrac() * 100.0);
        int pSys = Math.max(0, 100 - pEng - pShd - pWep);
        g2.drawString("POWER[" + player.powerPreset.name() + "] E:" + pEng + "% S:" + pShd + "% W:" + pWep + "% SYS:" + pSys + "%", x, y);
        y += 18;

        int readinessPct = (int) Math.round(player.crewReadiness() * 100.0);
        g2.drawString("CREW[" + player.crewOrder.name() + "] READINESS " + readinessPct + "%", x, y);
        y += 18;

        if (player.shieldActive && player.shieldMax > 0) {
            int facingDeg = (int) Math.round(Math.toDegrees(MathUtil.normalizeAngle(player.getShieldFacingAngle())));
            g2.drawString("SHIELD[" + player.shieldFacingMode.name() + "] FACING " + facingDeg + " DEG  ARC " + (int) Math.round(player.shieldArcDegrees()) + " DEG", x, y);
            y += 18;
        }

        if (player.hasWaveMotionGun) {
            double rem = player.getWaveMotionRemaining();
            String wave = player.isWaveMotionCharging()
                    ? ("CHARGING " + Math.max(1, (int) Math.ceil(rem)) + "s")
                    : ((rem <= 0.0) ? "READY" : (Math.max(1, (int) Math.ceil(rem)) + "s"));
            g2.drawString("WAVE GUN: " + wave, x, y);
            y += 18;
        }

        if (playerWingCap > 0) {
            g2.drawString("WING: " + playerWingActive + " / " + playerWingCap
                    + "  MODE: " + player.carrierCommandMode.name()
                    + "  AUTO: " + (player.carrierAutoLaunch ? "ON" : "OFF"), x, y);
            y += 18;
        }

        if (lockedTarget == null || !lockedTarget.alive) {
            g2.drawString("LOCK: None", x, y);
        } else {
            double dx = lockedTarget.x - player.x;
            double dy = lockedTarget.y - player.y;
            int dist = (int) Math.round(Math.hypot(dx, dy));

            String role = (lockedTarget.role == null ? "" : lockedTarget.role.name());
            String fac  = (lockedTarget.faction == null ? "" : lockedTarget.faction.name());
            String hp   = lockedTarget.hp + "/" + lockedTarget.hpMax;
            String wing = (lockedWingCap > 0) ? ("  WING " + lockedWingActive + "/" + lockedWingCap) : "";

            // Color the lock line slightly by faction for readability.
            g2.setColor(factionHudColor(lockedTarget.faction, 220));

            g2.drawString("LOCK: " + lockedTarget.name + "  " + role + "  " + fac + "  HP " + hp + "  D " + dist + wing, x, y);
            g2.setColor(new Color(255, 255, 255, 170));

            String archetype = EnemyArchetypeIntel.archetypeLabel(lockedTarget.role);
            String counter = EnemyArchetypeIntel.counterHint(lockedTarget.role);
            if (!archetype.isBlank()) {
                y += 18;
                g2.setColor(new Color(220, 235, 255, 215));
                g2.drawString("ARCHETYPE: " + archetype, x, y);
            }
            if (!counter.isBlank()) {
                y += 18;
                g2.setColor(new Color(255, 225, 160, 220));
                g2.drawString("COUNTER: " + counter, x, y);
                g2.setColor(new Color(255, 255, 255, 170));
            }

            drawOffscreenTargetIndicator(g2, lockedTarget, camX, camY, viewW, viewH, zoom);
        }
        y += 18;// Top-center event banner
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



        if (shopOpen) {
            drawShopOverlay(g2, player, credits, hangarTier);
        }
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
        g2.drawString("TAB/ESC close   1-9 buy   F-keys/0/-/= swap hull", x + 14, y + 44);

        // Readouts
        int ty = y + 70;
        g2.setFont(new Font("Consolas", Font.PLAIN, 13));
        g2.setColor(new Color(255, 255, 255, 210));
        g2.drawString("Credits: " + credits, x + 14, ty);
        g2.drawString("Hangar Tier: " + hangarTier, x + 190, ty);
        g2.drawString("Hull: " + (player.role == null ? "UNKNOWN" : player.role.name()), x + 350, ty);

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

        String title = role == null ? "UNKNOWN" : role.name();
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




    public static void drawPowerManagementOverlay(Graphics2D g2, Player player, int focusSlot) {
        if (g2 == null || player == null) return;

        Rectangle clip = g2.getClipBounds();
        int w = Math.min(620, clip.width - 120);
        int h = 340;
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
        g2.drawString("O/ESC close   1-4 select bus   <-/-> or [/] adjust   F1-F4 presets", x + 18, y + 48);

        String[] labels = {"ENGINES", "SHIELDS", "WEAPONS", "SYSTEMS"};
        double[] values = {
                player.powerEnginesFrac(),
                player.powerShieldsFrac(),
                player.powerWeaponsFrac(),
                player.powerSystemsFrac()
        };

        int rowY = y + 82;
        int barW = 300;
        int barH = 16;
        for (int i = 0; i < labels.length; i++) {
            int ry = rowY + i * 38;
            boolean focus = (i == Math.max(0, Math.min(3, focusSlot)));
            int pct = (int) Math.round(values[i] * 100.0);

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
                default -> new Color(220, 180, 255, 210);
            };
            g2.setColor(c);
            g2.fillRoundRect(bx + 1, by + 1, Math.max(0, fill), barH - 2, 7, 7);

            g2.setColor(new Color(255, 255, 255, 220));
            g2.setFont(new Font("Consolas", Font.BOLD, 13));
            g2.drawString(String.format(Locale.US, "%3d%%", pct), bx + barW + 14, ry + 13);
        }

        double speedMul = (player.desiredSpeedBase > 0.01) ? (player.desiredSpeed / player.desiredSpeedBase) : 1.0;
        double weaponDmg = player.weaponDamageMultiplier();
        double weaponCycle = player.weaponCycleRateMultiplier();
        double sensor = player.sensorRangeMultiplier();
        double shield = player.shieldRegenMultiplier();

        int py = y + 248;
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

        g2.setColor(new Color(255, 255, 255, 145));
        g2.drawString("Presets: F1 BALANCED   F2 ATTACK   F3 DEFENSE   F4 PURSUIT", x + 20, y + h - 18);
    }

    public static void drawCrewStationsOverlay(Graphics2D g2, GameContext ctx) {
        if (g2 == null || ctx == null || ctx.player == null) return;

        Rectangle clip = g2.getClipBounds();
        int w = Math.min(760, clip.width - 80);
        int h = 390;
        int x = (clip.width - w) / 2;
        int y = Math.max(40, (clip.height - h) / 2);

        g2.setColor(new Color(0, 0, 0, 210));
        g2.fillRoundRect(x, y, w, h, 18, 18);
        g2.setColor(new Color(255, 255, 255, 110));
        g2.drawRoundRect(x, y, w, h, 18, 18);

        g2.setColor(new Color(255, 240, 180, 230));
        g2.setFont(new Font("Consolas", Font.BOLD, 18));
        g2.drawString("CREW STATIONS", x + 18, y + 30);

        g2.setColor(new Color(255, 255, 255, 170));
        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.drawString("H/ESC close   F1-F5 stations   A toggle station AI   <-/-> cycle station", x + 18, y + 48);

        int tx = x + 18;
        int ty = y + 66;
        for (GameContext.CrewStation station : GameContext.CrewStation.values()) {
            boolean active = (station == ctx.activeCrewStation);
            boolean auto = UISystem.stationAutomation(ctx, station);
            int tw = 122;
            g2.setColor(active ? new Color(255, 220, 140, 180) : new Color(255, 255, 255, 45));
            g2.fillRoundRect(tx, ty, tw, 24, 10, 10);
            g2.setColor(active ? new Color(255, 245, 210, 220) : new Color(255, 255, 255, 120));
            g2.drawRoundRect(tx, ty, tw, 24, 10, 10);
            g2.setFont(new Font("Consolas", active ? Font.BOLD : Font.PLAIN, 12));
            g2.drawString(station.name(), tx + 8, ty + 16);
            g2.setColor(auto ? new Color(120, 255, 170, 210) : new Color(255, 150, 140, 210));
            g2.drawString(auto ? "AI" : "MAN", tx + 92, ty + 16);
            tx += tw + 8;
        }

        int ly = y + 126;
        g2.setColor(new Color(255, 255, 255, 210));
        g2.setFont(new Font("Consolas", Font.BOLD, 14));
        g2.drawString("Current Readouts", x + 18, ly);
        ly += 20;

        int lockDist = -1;
        if (ctx.lockedTarget != null && ctx.lockedTarget.alive) {
            lockDist = (int) Math.round(Math.hypot(ctx.lockedTarget.x - ctx.player.x, ctx.lockedTarget.y - ctx.player.y));
        }

        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(new Color(210, 235, 255, 220));
        g2.drawString("Captain: " + ctx.captainDirective + "   Helm: " + ctx.helmMode + "   Tactical: " + ctx.tacticalMode, x + 18, ly);
        ly += 16;
        g2.drawString("Engineering: " + ctx.engineeringMode + "   Fleet: " + ctx.alliedFleetCommand + " / " + ctx.alliedFleetFormation, x + 18, ly);
        ly += 16;
        g2.drawString("Lock: " + ((ctx.lockedTarget == null) ? "NONE" : (ctx.lockedTarget.name + " (" + Math.max(0, lockDist) + "m)"))
                + "   Science EW: " + (ctx.scienceJamming ? "JAMMING" : "PASSIVE"), x + 18, ly);
        ly += 16;
        g2.drawString("Crew: " + ctx.player.crewOrder + "  Readiness " + (int) Math.round(ctx.player.crewReadiness() * 100.0) + "%", x + 18, ly);

        ly += 28;
        g2.setColor(new Color(255, 255, 255, 220));
        g2.setFont(new Font("Consolas", Font.BOLD, 14));
        g2.drawString("Station Controls", x + 18, ly);
        ly += 20;
        g2.setFont(new Font("Consolas", Font.PLAIN, 12));

        switch (ctx.activeCrewStation) {
            case CAPTAIN -> {
                g2.setColor(new Color(255, 230, 175, 220));
                g2.drawString("1 BALANCED  2 ATTACK  3 DEFENSE  4 EMERGENCY  5 MINE", x + 18, ly);
                ly += 16;
                g2.drawString("6 ESCORT  7 DEFEND  8 REPAIR  9 RTB  0 CYCLE FLEET FORMATION", x + 18, ly);
                ly += 16;
                g2.drawString("Q/W/E/R assign nearest friendly ATTACK/DEFEND/REPAIR/RTB, T clears override.", x + 18, ly);
                ly += 16;
                g2.drawString("Captain directives set ship posture and allied fleet command behavior.", x + 18, ly);
            }
            case HELM -> {
                g2.setColor(new Color(200, 240, 255, 220));
                g2.drawString("1 INTERCEPT  2 ORBIT  3 MAINTAIN RANGE  4 EVASIVE", x + 18, ly);
                ly += 16;
                g2.drawString("Helm automation sets heading/throttle for target pursuit and maneuvering.", x + 18, ly);
            }
            case TACTICAL -> {
                g2.setColor(new Color(255, 210, 180, 220));
                g2.drawString("1 HOLD FIRE  2 DEFENSIVE FIRE  3 AGGRESSIVE FIRE", x + 18, ly);
                ly += 16;
                g2.drawString("Tactical automation drives primary/secondary firing states and lock usage.", x + 18, ly);
            }
            case ENGINEERING -> {
                g2.setColor(new Color(200, 255, 200, 220));
                g2.drawString("1 BALANCED  2 ATTACK BIAS  3 DEFENSE BIAS  4 DAMAGE CONTROL", x + 18, ly);
                ly += 16;
                g2.drawString("Engineering automation controls power distribution and repair-focused crew orders.", x + 18, ly);
            }
            case SCIENCE -> {
                g2.setColor(new Color(220, 210, 255, 220));
                g2.drawString("1 LOCK NEAREST  2 CLEAR LOCK  3 TOGGLE EW/JAMMING", x + 18, ly);
                ly += 16;
                g2.drawString("Science automation manages target acquisition using current sensor capability.", x + 18, ly);
            }
        }

        g2.setColor(new Color(255, 255, 255, 145));
        g2.drawString("Manual flight/fire/power input immediately disables corresponding station AI.", x + 18, y + h - 16);
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

            if (ship.shieldActive && ship.shieldMax > 0 && ship.shield > 0) {
                double frac = Math.max(0, Math.min(1, ship.shield / ship.shieldMax));
                g.setColor(new Color(120, 200, 255, (int) (40 + 90 * frac)));
                int rr = (int) Math.round(ship.radius + 7);
                g.drawOval(-rr, -rr, rr * 2, rr * 2);
            }

            if (hullArea != null) {
                drawDamageDecals(g, ship, hullArea);
            }

            if (ship.isStealth && sig < 0.99 && !visual.hullPolys.isEmpty()) {
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f));
                g.setColor(new Color(120, 220, 255, 110));
                g.draw(visual.hullPolys.get(0));
            }

            g.dispose();

            g2.setFont(new Font("Consolas", Font.PLAIN, 12));
            g2.setColor(new Color(255, 255, 255, 130));
            g2.drawString(ship.name, wx - 18, wy - (int) ship.radius - 10);
        }

        private static double roleVisualScale(ShipRole role) {
            if (role == null) return 1.0;
            return switch (role) {
                case FIGHTER -> 0.78;
                case BOMBER -> 0.84;
                default -> 1.0;
            };
        }

        private static ShipVisual getVisual(Ship ship) {
            int r = (int) Math.round(Math.max(8.0, ship.radius));
            String key = ship.role + ":" + r;
            ShipVisual cached = CACHE.get(key);
            if (cached != null) return cached;

            ShipVisual v = buildVisual(ship.role, r);
            CACHE.put(key, v);
            return v;
        }

        private static ShipVisual buildVisual(ShipRole role, int r) {
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

            double pad = Math.max(1.0, ship.radius * 0.08);
            int dx = (int) Math.round(bounds.getMinX() - pad);
            int dy = (int) Math.round(bounds.getMinY() - pad);
            int dw = Math.max(1, (int) Math.round(bounds.getWidth() + pad * 2.0));
            int dh = Math.max(1, (int) Math.round(bounds.getHeight() + pad * 2.0));

            // Draw the authored sprite on a square canvas around the ship center.
            // Using hull bounds directly can collapse wide sprites into a thin strip.
            final double SKIN_SCALE = 2.1;
            int baseSpan = Math.max(Math.max(dw, dh), (int) Math.round(ship.radius * 2.0));
            int sw = Math.max(1, (int) Math.round(baseSpan * SKIN_SCALE));
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
            if (v.station) {
                int ro = (int) Math.round(v.stationOuter + 6);
                g.setColor(new Color(120, 220, 255, 95));
                g.drawOval(-ro, -ro, ro * 2, ro * 2);
                return;
            }

            for (EnginePoint p : v.engines) {
                int ex = p.x;
                int ey = p.y;

                // cone bloom behind engine
                Polygon cone = new Polygon(
                        new int[]{ex - 1, ex - (int) (ship.radius * 0.42), ex - (int) (ship.radius * 0.42)},
                        new int[]{ey, ey - 5, ey + 5}, 3);
                g.setColor(new Color(120, 220, 255, 60));
                g.fillPolygon(cone);

                g.setColor(new Color(120, 220, 255, 120));
                g.fillOval(ex - 5, ey - 3, 7, 7);
                g.setColor(new Color(120, 220, 255, 70));
                g.fillOval(ex - 9, ey - 7, 13, 13);
            }
        }

        private static void drawHardpoints(Graphics2D g, Ship ship, ShipVisual v) {
            drawTurrets(g, ship);
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
                } catch (IOException ignored) {}
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
        private static boolean missileSkinLoaded = false;

        static BufferedImage getMissileSkin() {
            if (missileSkinLoaded) return missileSkin;
            missileSkinLoaded = true;
            missileSkin = loadSkin("missile");
            return missileSkin;
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

        // Shield ring
        if (ship.shieldActive && ship.shieldMax > 0 && ship.shield > 0) {
            double frac = Math.max(0, Math.min(1, ship.shield / ship.shieldMax));
            g.setColor(new Color(120, 200, 255, (int) (40 + 90 * frac)));
            int rr = (int) Math.round(ship.radius + 7);
            g.drawOval(-rr, -rr, rr * 2, rr * 2);
        }

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
        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.setColor(new Color(255, 255, 255, 130));
        g2.drawString(ship.name, wx - 18, wy - (int) ship.radius - 10);
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

    private static int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static void drawEngines(Graphics2D g, Ship ship) {
        int r = (int) Math.round(ship.radius);
        if (ship.role == ShipRole.BASE) return;

        int ex = -r;
        int ey = 0;

        g.setColor(new Color(120, 220, 255, 120));
        g.fillOval(ex - 6, ey - 4, 8, 8);

        g.setColor(new Color(120, 220, 255, 70));
        g.fillOval(ex - 10, ey - 8, 14, 14);

        if (ship.role == ShipRole.CARRIER || ship.role == ShipRole.MISSILE_BOAT
                || ship.role == ShipRole.LIGHT_CRUISER || ship.role == ShipRole.MEDIUM_CRUISER || ship.role == ShipRole.CRUISER
                || ship.role == ShipRole.BATTLECRUISER || ship.role == ShipRole.BATTLESHIP
                || ship.role == ShipRole.DREADNOUGHT || ship.role == ShipRole.SUPERSHIP) {
            g.setColor(new Color(120, 220, 255, 120));
            g.fillOval(ex - 6, -r / 3 - 4, 8, 8);
            g.fillOval(ex - 6, r / 3 - 4, 8, 8);
        }

        if (ship.role == ShipRole.BATTLESHIP || ship.role == ShipRole.DREADNOUGHT || ship.role == ShipRole.SUPERSHIP) {
            g.setColor(new Color(120, 220, 255, 105));
            g.fillOval(ex - 8, -r / 2 - 4, 10, 10);
            g.fillOval(ex - 8, r / 2 - 4, 10, 10);
        }
    }

    private static void drawTurrets(Graphics2D g2, Ship ship) {
        if (ship == null || ship.turrets == null) return;

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
        int n = (int) Math.round(4 + dmg * 12);

        long seed = (long) System.identityHashCode(ship) * 0x9E3779B97F4A7C15L;
        Random rng = new Random(seed);
        Shape oldClip = g.getClip();
        Stroke oldStroke = g.getStroke();
        g.setClip(hullShape);

        try {
            // Scorch marks
            for (int i = 0; i < n; i++) {
                Point hit = randomPointInShape(rng, bounds, hullShape, 18);
                int px = hit.x;
                int py = hit.y;

                int sz = (int) Math.max(3, Math.round(2 + rng.nextDouble() * (4 + dmg * 10)));
                int a = (int) MathUtil.clamp(48 + dmg * 140, 0, 175);
                g.setColor(new Color(0, 0, 0, a));
                g.fillOval(px - sz, py - sz, sz * 2, sz * 2);

                // Hot edge / ember tint
                g.setColor(new Color(255, 196, 116, (int) MathUtil.clamp(20 + dmg * 56, 0, 96)));
                g.drawOval(px - sz, py - sz, sz * 2, sz * 2);
            }

            // Raking impact streaks and carved plate scratches.
            int streaks = (int) Math.round(2 + dmg * 10);
            for (int i = 0; i < streaks; i++) {
                Point hit = randomPointInShape(rng, bounds, hullShape, 18);
                int px = hit.x;
                int py = hit.y;

                double ang = rng.nextDouble() * Math.PI * 2.0;
                double bias = (rng.nextDouble() - 0.5) * 0.45;
                double dir = ang * 0.35 + bias;
                int len = (int) Math.round(4 + rng.nextDouble() * (6 + dmg * span * 0.38));
                int x2 = px + (int) Math.round(Math.cos(dir) * len);
                int y2 = py + (int) Math.round(Math.sin(dir) * len);

                float w = (float) Math.max(1.0, 0.8 + dmg * 2.1 * (0.6 + rng.nextDouble() * 0.8));
                g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(new Color(10, 10, 12, (int) MathUtil.clamp(42 + dmg * 120, 0, 170)));
                g.drawLine(px, py, x2, y2);

                g.setStroke(new BasicStroke(Math.max(1f, w * 0.42f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(new Color(255, 166, 98, (int) MathUtil.clamp(14 + dmg * 56, 0, 90)));
                g.drawLine(px, py, x2, y2);
            }

            // Breach holes at critical damage.
            if (dmg > 0.72) {
                int breaches = (int) Math.round(1 + (dmg - 0.72) * 8.0);
                for (int i = 0; i < breaches; i++) {
                    Point hit = randomPointInShape(rng, bounds, hullShape, 22);
                    int px = hit.x;
                    int py = hit.y;
                    int sz = (int) Math.max(4, Math.round(4 + rng.nextDouble() * (3 + dmg * 8)));
                    g.setColor(new Color(8, 8, 10, (int) MathUtil.clamp(88 + dmg * 122, 0, 205)));
                    g.fillOval(px - sz, py - sz, sz * 2, sz * 2);
                    g.setColor(new Color(255, 174, 102, (int) MathUtil.clamp(24 + dmg * 52, 0, 96)));
                    g.drawOval(px - sz, py - sz, sz * 2, sz * 2);
                }
            }

            // If very damaged, add a little smoke haze on top.
            if (dmg > 0.55) {
                int smoke = (int) Math.round(2 + dmg * 6);
                for (int i = 0; i < smoke; i++) {
                    Point hit = randomPointInShape(rng, bounds, hullShape, 18);
                    int px = hit.x;
                    int py = hit.y;
                    int sz = (int) Math.max(6, Math.round(6 + rng.nextDouble() * 10));
                    int a = (int) MathUtil.clamp(20 + (dmg - 0.55) * 140, 0, 110);
                    g.setColor(new Color(30, 30, 30, a));
                    g.fillOval(px - sz, py - sz, sz * 2, sz * 2);
                }
            }
        } finally {
            g.setStroke(oldStroke);
            g.setClip(oldClip);
        }
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
        if (f == Faction.TEAM_C) return new Color(220, 170, 70);
        if (f == Faction.TEAM_D) return new Color(165, 120, 220);
        return new Color(120, 160, 245);
    }

    private static Color factionTrimColor(Faction f) {
        if (f == Faction.ENEMY) return new Color(255, 170, 170);
        if (f == Faction.PLAYER) return new Color(200, 255, 220);
        if (f == Faction.TEAM_C) return new Color(255, 220, 160);
        if (f == Faction.TEAM_D) return new Color(220, 190, 255);
        return new Color(220, 230, 255);
    }

    private static Color factionHudColor(Faction f, int alpha) {
        Color base;
        if (f == Faction.ENEMY) base = new Color(255, 170, 170);
        else if (f == Faction.PLAYER) base = new Color(180, 255, 220);
        else if (f == Faction.TEAM_C) base = new Color(255, 220, 160);
        else if (f == Faction.TEAM_D) base = new Color(220, 190, 255);
        else base = new Color(170, 220, 255);
        return withAlpha(base, alpha);
    }

    private static Color factionMapColor(Faction f, boolean isPlayer, int alpha) {
        Color base;
        if (isPlayer || f == Faction.PLAYER) base = new Color(90, 255, 140);
        else if (f == Faction.ENEMY) base = new Color(255, 90, 90);
        else if (f == Faction.TEAM_C) base = new Color(255, 200, 90);
        else if (f == Faction.TEAM_D) base = new Color(200, 140, 255);
        else base = new Color(140, 180, 255);
        return withAlpha(base, alpha);
    }

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), MathUtil.clamp(alpha, 0, 255));
    }

}
