import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Lightweight visual effects system (world-space).
 *
 * Purely cosmetic:
 *  - muzzle flashes
 *  - impact sparks
 *  - missile smoke
 *  - subtle engine wisps
 *
 * Effects are capped so they can't tank performance.
 */
public final class VFX {

    private VFX() {}

    // Keep this tighter during fleet-heavy modes (Resource Rush, late waves).
    private static final int MAX = 1100;
    private static final List<Particle> active = new ArrayList<>();
    private static final Random RNG = new Random();

    public enum ImpactStyle {
        KINETIC,
        ENERGY,
        EXPLOSIVE,
        BEAM
    }

    // Spawn helpers may be called from places that don't have dt handy.
    private static final double DEFAULT_DT = 1.0 / 60.0;

    public static void updateAll(double dt) {
        if (active.isEmpty()) return;
        for (Iterator<Particle> it = active.iterator(); it.hasNext(); ) {
            Particle p = it.next();
            p.x += p.vx;
            p.y += p.vy;
            p.angle += p.angleVel;
            if (p.type == Type.MUZZLE_BLOOM) p.size += 0.8;
            if (p.type == Type.SHIELD) p.size += 1.4;
            if (p.type == Type.SMOKE) p.size += 0.12;
            if (p.type == Type.FIRE) p.size += 0.08;
            p.life--;
            if (p.life <= 0) it.remove();
        }
    }

    /** Draw all particles in world space. */
    public static void drawAll(Graphics2D g2) {
        if (active.isEmpty()) return;

        for (Particle p : active) {
            double f = (p.maxLife <= 0) ? 0 : Math.max(0.0, Math.min(1.0, p.life / (double) p.maxLife));
            int alpha = (int) MathUtil.clamp(p.baseAlpha * f, 0, 255);

            switch (p.type) {
                case MUZZLE -> {
                    int a = (int) MathUtil.clamp(alpha, 0, 220);
                    int w = (int) Math.round(p.size * 1.6);
                    int h = (int) Math.round(p.size * 1.0);

                    // soft glow
                    g2.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), (int) MathUtil.clamp(a * 0.35, 0, 120)));
                    g2.fillOval((int) Math.round(p.x - w), (int) Math.round(p.y - h), w * 2, h * 2);

                    // bright core
                    g2.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), a));
                    g2.fillOval((int) Math.round(p.x - w / 2.0), (int) Math.round(p.y - h / 2.0), w, h);

                    // little streak
                    g2.setColor(new Color(255, 255, 255, (int) MathUtil.clamp(alpha * 0.7, 0, 200)));
                    int sx = (int) Math.round(p.x);
                    int sy = (int) Math.round(p.y);
                    int ex = (int) Math.round(p.x + Math.cos(p.angle) * (p.size * 2.0));
                    int ey = (int) Math.round(p.y + Math.sin(p.angle) * (p.size * 2.0));
                    g2.drawLine(sx, sy, ex, ey);
                }
                case MUZZLE_BLOOM -> {
                    int a = (int) MathUtil.clamp(alpha, 0, 160);
                    Stroke old = g2.getStroke();
                    g2.setStroke(new BasicStroke((float) Math.max(1.0, p.size * 0.25)));
                    g2.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), a));
                    int r = (int) Math.round(p.size);
                    g2.drawOval((int) Math.round(p.x - r), (int) Math.round(p.y - r), r * 2, r * 2);
                    g2.setStroke(old);
                }
                case SPARK -> {
                    g2.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), alpha));
                    int r = (int) Math.max(1, Math.round(p.size));
                    g2.fillOval((int) Math.round(p.x - r), (int) Math.round(p.y - r), r * 2, r * 2);
                }
                case SMOKE -> {
                    int a = (int) MathUtil.clamp(alpha, 0, 130);
                    Color c = (p.color != null) ? p.color : new Color(120, 200, 255);
                    g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), a));
                    int r = (int) Math.round(p.size);
                    g2.fillOval((int) Math.round(p.x - r), (int) Math.round(p.y - r), r * 2, r * 2);
                }
                case ENGINE -> {
                    int a = (int) MathUtil.clamp(alpha, 0, 120);
                    g2.setColor(new Color(120, 220, 255, a));
                    int w = (int) Math.round(p.size * 1.8);
                    int h = (int) Math.round(p.size * 0.9);
                    g2.fillOval((int) Math.round(p.x - w / 2.0), (int) Math.round(p.y - h / 2.0), w, h);
                }
                case FIRE -> {
                    int a = (int) MathUtil.clamp(alpha, 0, 180);
                    int glowA = (int) MathUtil.clamp(a * 0.35, 0, 110);
                    int glowR = (int) Math.max(2, Math.round(p.size * 1.9));
                    g2.setColor(new Color(255, 160, 88, glowA));
                    g2.fillOval((int) Math.round(p.x - glowR), (int) Math.round(p.y - glowR), glowR * 2, glowR * 2);

                    g2.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), a));
                    int r = (int) Math.max(1, Math.round(p.size));
                    g2.fillOval((int) Math.round(p.x - r), (int) Math.round(p.y - r), r * 2, r * 2);

                    // hotter core
                    g2.setColor(new Color(255, 245, 220, (int) MathUtil.clamp(a * 0.55, 0, 140)));
                    int r2 = (int) Math.max(1, Math.round(p.size * 0.45));
                    g2.fillOval((int) Math.round(p.x - r2), (int) Math.round(p.y - r2), r2 * 2, r2 * 2);
                }
                case DEBRIS -> {
                    g2.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), alpha));
                    int len = (int) Math.max(2, Math.round(p.size * 2.6));
                    int x1 = (int) Math.round(p.x - Math.cos(p.angle) * len);
                    int y1 = (int) Math.round(p.y - Math.sin(p.angle) * len);
                    int x2 = (int) Math.round(p.x + Math.cos(p.angle) * len);
                    int y2 = (int) Math.round(p.y + Math.sin(p.angle) * len);
                    g2.drawLine(x1, y1, x2, y2);
                }
                case SALVAGE -> {
                    int a = (int) MathUtil.clamp(alpha, 0, 200);
                    g2.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), a));
                    int r = (int) Math.max(2, Math.round(p.size));
                    int cx = (int) Math.round(p.x);
                    int cy = (int) Math.round(p.y);

                    int[] xs = {cx, cx + r, cx, cx - r};
                    int[] ys = {cy - r, cy, cy + r, cy};
                    g2.fillPolygon(xs, ys, 4);

                    // outline
                    g2.setColor(new Color(255, 255, 255, (int) MathUtil.clamp(a * 0.6, 0, 160)));
                    g2.drawPolygon(xs, ys, 4);
                }
                case SHIELD -> {
                    int a = (int) MathUtil.clamp(alpha, 0, 190);
                    Stroke old = g2.getStroke();
                    g2.setStroke(new BasicStroke((float) Math.max(1.2, p.size * 0.12)));
                    g2.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), a));
                    int r = (int) Math.round(p.size);
                    g2.drawOval((int) Math.round(p.x - r), (int) Math.round(p.y - r), r * 2, r * 2);
                    g2.setColor(new Color(220, 245, 255, (int) MathUtil.clamp(a * 0.55, 0, 140)));
                    int r2 = (int) Math.max(2, Math.round(p.size * 0.68));
                    g2.drawOval((int) Math.round(p.x - r2), (int) Math.round(p.y - r2), r2 * 2, r2 * 2);
                    g2.setStroke(old);
                }
                case IMPACT_BLOOM -> {
                    int a = (int) MathUtil.clamp(alpha, 0, 180);
                    int r = (int) Math.max(2, Math.round(p.size));
                    g2.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), (int) MathUtil.clamp(a * 0.35, 0, 120)));
                    g2.fillOval((int) Math.round(p.x - r), (int) Math.round(p.y - r), r * 2, r * 2);
                    g2.setColor(new Color(255, 255, 255, (int) MathUtil.clamp(a * 0.40, 0, 120)));
                    int r2 = (int) Math.max(1, Math.round(p.size * 0.42));
                    g2.fillOval((int) Math.round(p.x - r2), (int) Math.round(p.y - r2), r2 * 2, r2 * 2);
                }
            }
        }
    }

    /** Small flame puffs for burning wrecks. Purely cosmetic. */
    public static void spawnShipFire(double x, double y, double intensity) {
        Particle p = new Particle();
        p.type = Type.FIRE;
        p.x = x;
        p.y = y;
        p.angle = RNG.nextDouble() * Math.PI * 2.0;
        p.angleVel = (RNG.nextDouble() - 0.5) * 0.12;
        p.size = 3.5 + intensity * (2.5 + RNG.nextDouble() * 3.0);
        p.maxLife = p.life = 16 + RNG.nextInt(16);
        p.baseAlpha = 190;

        // gentle drift
        double a = RNG.nextDouble() * Math.PI * 2.0;
        double sp = 10 + RNG.nextDouble() * 35;
        p.vx = Math.cos(a) * sp * DEFAULT_DT;
        p.vy = Math.sin(a) * sp * DEFAULT_DT;

        // warm oranges
        int g = 90 + RNG.nextInt(80);
        p.color = new Color(255, g, 70);
        addCapped(p);
    }

    /** Debris burst when a ship finally explodes. baseVx/baseVy are per-tick deltas to inherit. */
    public static void spawnDebrisBurst(double x, double y, double baseVx, double baseVy, int count) {
        count = MathUtil.clamp(count, 5, 30);
        for (int i = 0; i < count; i++) {
            double a = RNG.nextDouble() * Math.PI * 2.0;
            double sp = 80 + RNG.nextDouble() * 300;

            Particle p = new Particle();
            p.type = Type.DEBRIS;
            p.x = x + (RNG.nextDouble() - 0.5) * 6.0;
            p.y = y + (RNG.nextDouble() - 0.5) * 6.0;
            p.angle = a;
            p.angleVel = (RNG.nextDouble() - 0.5) * 0.25;
            p.size = 1.6 + RNG.nextDouble() * 2.2;
            p.maxLife = p.life = 30 + RNG.nextInt(40);
            p.baseAlpha = 200;
            p.color = new Color(200, 210, 225);

            // inherit drift + blast impulse
            p.vx = baseVx + Math.cos(a) * sp * DEFAULT_DT;
            p.vy = baseVy + Math.sin(a) * sp * DEFAULT_DT;

            addCapped(p);
        }
    }

    /** Visual salvage shards that shoot out on explosion (pickup logic can be added separately). */
    public static void spawnSalvageBurst(double x, double y, double baseVx, double baseVy, int count) {
        count = MathUtil.clamp(count, 2, 10);
        for (int i = 0; i < count; i++) {
            double a = RNG.nextDouble() * Math.PI * 2.0;
            double sp = 60 + RNG.nextDouble() * 180;

            Particle p = new Particle();
            p.type = Type.SALVAGE;
            p.x = x + (RNG.nextDouble() - 0.5) * 6.0;
            p.y = y + (RNG.nextDouble() - 0.5) * 6.0;
            p.angle = a;
            p.angleVel = (RNG.nextDouble() - 0.5) * 0.10;
            p.size = 4.0 + RNG.nextDouble() * 4.0;
            p.maxLife = p.life = 90 + RNG.nextInt(70);
            p.baseAlpha = 180;
            p.color = new Color(100, 240, 255);

            p.vx = baseVx + Math.cos(a) * sp * DEFAULT_DT;
            p.vy = baseVy + Math.sin(a) * sp * DEFAULT_DT;

            addCapped(p);
        }
    }

    public static void spawnMuzzleFlash(double x, double y, double angle, boolean missile) {
        Particle p = new Particle();
        p.type = Type.MUZZLE;
        p.x = x;
        p.y = y;
        p.vx = 0;
        p.vy = 0;
        p.angle = angle;
        p.size = missile ? 10 : 7;
        p.maxLife = p.life = missile ? 7 : 5; // frames
        p.baseAlpha = 220;
        p.color = missile ? new Color(255, 230, 140) : new Color(255, 245, 210);
        addCapped(p);

        if (!DevTools.isFancyVfxEnabled()) return;

        Particle bloom = new Particle();
        bloom.type = Type.MUZZLE_BLOOM;
        bloom.x = x;
        bloom.y = y;
        bloom.angle = angle;
        bloom.size = missile ? 14 : 10;
        bloom.maxLife = bloom.life = missile ? 9 : 7;
        bloom.baseAlpha = 160;
        bloom.color = missile ? new Color(255, 220, 140) : new Color(180, 235, 255);
        addCapped(bloom);

        if (!missile) {
            spawnMuzzleSparks(x, y, angle, 5);
        }
    }

    public static void spawnImpactSparks(double x, double y, double dirX, double dirY, int strength) {
        // Lightweight fallback impact: single compact bloom (no spark spray).
        int sev = Math.max(1, strength);
        spawnImpactBloom(
                x, y,
                4.4 + sev * 0.9,
                new Color(255, 160, 84),
                8,
                175
        );
    }

    public static void spawnHullImpact(double x, double y, double dirX, double dirY, int strength, ImpactStyle style) {
        ImpactStyle s = (style == null) ? ImpactStyle.KINETIC : style;
        int sev = Math.max(1, strength);
        Color tint = switch (s) {
            case KINETIC -> new Color(255, 205, 130);
            case ENERGY -> new Color(130, 225, 255);
            case EXPLOSIVE -> new Color(255, 165, 95);
            case BEAM -> new Color(165, 245, 255);
        };

        // Performance-focused hull hit: no particle spray, just a compact flash.
        double size = switch (s) {
            case EXPLOSIVE -> 6.4 + sev * 1.2;
            case BEAM -> 5.2 + sev * 0.9;
            case ENERGY -> 5.0 + sev * 0.95;
            case KINETIC -> 4.8 + sev * 0.85;
        };
        int life = switch (s) {
            case EXPLOSIVE -> 12;
            case BEAM -> 9;
            case ENERGY -> 10;
            case KINETIC -> 9;
        };
        int alpha = switch (s) {
            case EXPLOSIVE -> 195;
            case BEAM -> 170;
            case ENERGY -> 180;
            case KINETIC -> 175;
        };
        spawnImpactBloom(x, y, size, tint, life, alpha);

        // Explosive impacts get a tiny secondary flash instead of debris/smoke plumes.
        if (s == ImpactStyle.EXPLOSIVE && sev >= 3) {
            spawnImpactBloom(x, y, Math.max(3.5, size * 0.62), new Color(255, 216, 170), 8, 155);
        }
    }

    public static void spawnShieldImpact(double x, double y, double dirX, double dirY, int strength, ImpactStyle style) {
        ImpactStyle s = (style == null) ? ImpactStyle.KINETIC : style;
        Color tint = switch (s) {
            case KINETIC -> new Color(132, 216, 255);
            case ENERGY -> new Color(120, 235, 255);
            case EXPLOSIVE -> new Color(156, 226, 255);
            case BEAM -> new Color(175, 248, 255);
        };
        spawnShieldRipple(x, y, 7.0 + Math.max(0, strength) * 1.8, tint);
        spawnImpactBurst(x, y, dirX, dirY,
                MathUtil.clamp(3 + Math.max(1, strength), 3, 12),
                tint,
                Math.toRadians(105),
                80, 190,
                8, 8,
                1.0, 1.4,
                185);
        spawnImpactBloom(x, y, 7 + strength * 1.1, tint, 10, 130);
        if (s == ImpactStyle.EXPLOSIVE) {
            spawnSmokeBurst(x, y, 1 + strength / 5, new Color(88, 106, 122), 2.8, 2.4);
        }
    }

    private static void spawnImpactBurst(double x, double y, double dirX, double dirY,
                                         int count, Color color, double spread,
                                         double speedMin, double speedRange,
                                         int lifeMin, int lifeRange,
                                         double sizeMin, double sizeRange,
                                         int baseAlpha) {
        count = MathUtil.clamp(count, 2, 26);
        if (color == null) color = new Color(255, 210, 120);
        double baseAng = Math.atan2(dirY, dirX);
        if (!Double.isFinite(baseAng)) baseAng = RNG.nextDouble() * Math.PI * 2.0;
        double halfSpread = Math.max(0.02, spread * 0.5);

        for (int i = 0; i < count; i++) {
            double a = baseAng + (RNG.nextDouble() - 0.5) * (halfSpread * 2.0);
            double sp = Math.max(1.0, speedMin + RNG.nextDouble() * Math.max(1.0, speedRange));
            int life = Math.max(4, lifeMin + RNG.nextInt(Math.max(1, lifeRange)));

            Particle p = new Particle();
            p.type = Type.SPARK;
            p.x = x;
            p.y = y;
            p.angle = a;
            p.size = Math.max(0.6, sizeMin + RNG.nextDouble() * Math.max(0.1, sizeRange));
            p.maxLife = p.life = life;
            p.baseAlpha = MathUtil.clamp(baseAlpha, 20, 255);
            p.color = color;

            // Convert to per-tick deltas
            p.vx = Math.cos(a) * sp * DEFAULT_DT;
            p.vy = Math.sin(a) * sp * DEFAULT_DT;

            addCapped(p);
        }
    }

    private static void spawnImpactBloom(double x, double y, double size, Color color, int life, int alpha) {
        Particle p = new Particle();
        p.type = Type.IMPACT_BLOOM;
        p.x = x;
        p.y = y;
        p.vx = 0.0;
        p.vy = 0.0;
        p.angle = 0.0;
        p.size = Math.max(2.0, size);
        p.maxLife = p.life = Math.max(4, life);
        p.baseAlpha = MathUtil.clamp(alpha, 24, 255);
        p.color = (color != null) ? color : new Color(255, 200, 135);
        addCapped(p);
    }

    private static void spawnSmokeBurst(double x, double y, int count, Color color, double sizeMin, double sizeRange) {
        int n = MathUtil.clamp(count, 1, 10);
        for (int i = 0; i < n; i++) {
            Particle p = new Particle();
            p.type = Type.SMOKE;
            p.x = x + (RNG.nextDouble() - 0.5) * 6.0;
            p.y = y + (RNG.nextDouble() - 0.5) * 6.0;
            p.angle = 0.0;
            p.size = Math.max(1.8, sizeMin + RNG.nextDouble() * Math.max(0.1, sizeRange));
            p.maxLife = p.life = 16 + RNG.nextInt(22);
            p.baseAlpha = 120;
            double a = RNG.nextDouble() * Math.PI * 2.0;
            double sp = 10 + RNG.nextDouble() * 28;
            p.vx = Math.cos(a) * sp * DEFAULT_DT;
            p.vy = Math.sin(a) * sp * DEFAULT_DT;
            p.color = (color != null) ? color : new Color(112, 112, 116);
            addCapped(p);
        }
    }

    public static void spawnShieldRipple(double x, double y, double radius, Color color) {
        Particle p = new Particle();
        p.type = Type.SHIELD;
        p.x = x;
        p.y = y;
        p.vx = 0;
        p.vy = 0;
        p.angle = 0;
        p.size = Math.max(8.0, radius);
        p.maxLife = p.life = 10;
        p.baseAlpha = 190;
        p.color = (color != null) ? color : new Color(120, 220, 255);
        addCapped(p);
    }

    public static void spawnHitSparks(double x, double y, double dirX, double dirY) {
        spawnImpactSparks(x, y, dirX, dirY, 1);
    }

    private static void spawnMuzzleSparks(double x, double y, double angle, int count) {
        int n = MathUtil.clamp(count, 3, 8);
        for (int i = 0; i < n; i++) {
            double a = angle + (RNG.nextDouble() - 0.5) * Math.toRadians(50);
            double sp = 140 + RNG.nextDouble() * 220;

            Particle p = new Particle();
            p.type = Type.SPARK;
            p.x = x + Math.cos(a) * 2.0;
            p.y = y + Math.sin(a) * 2.0;
            p.angle = a;
            p.size = 1.1 + RNG.nextDouble() * 1.6;
            p.maxLife = p.life = 8 + RNG.nextInt(6);
            p.baseAlpha = 190;
            p.color = new Color(255, 220, 140);

            p.vx = Math.cos(a) * sp * DEFAULT_DT;
            p.vy = Math.sin(a) * sp * DEFAULT_DT;
            addCapped(p);
        }
    }

    public static void spawnMissileSmoke(double x, double y) {
        Particle p = new Particle();
        p.type = Type.SMOKE;
        p.x = x + (RNG.nextDouble() - 0.5) * 4.0;
        p.y = y + (RNG.nextDouble() - 0.5) * 4.0;
        p.angle = 0;
        p.size = 3.8 + RNG.nextDouble() * 2.6;
        p.maxLife = p.life = 18 + RNG.nextInt(10);
        p.baseAlpha = 130;

        // drift a bit
        double a = RNG.nextDouble() * Math.PI * 2.0;
        double sp = 15 + RNG.nextDouble() * 30;
        p.vx = Math.cos(a) * sp * DEFAULT_DT;
        p.vy = Math.sin(a) * sp * DEFAULT_DT;

        p.color = new Color(120, 200, 255);
        addCapped(p);
    }

    public static void spawnEngineWisp(double x, double y, double angle, double intensity) {
        if (intensity <= 0.01) return;

        Particle p = new Particle();
        p.type = Type.ENGINE;
        p.x = x;
        p.y = y;
        p.angle = angle;
        p.size = 3.0 + intensity * 3.0;
        p.maxLife = p.life = 10 + (int) Math.round(intensity * 6);
        p.baseAlpha = 110;

        // push opposite facing
        double sp = 40 + intensity * 90;
        double a = angle + Math.PI + (RNG.nextDouble() - 0.5) * 0.35;
        p.vx = Math.cos(a) * sp * DEFAULT_DT;
        p.vy = Math.sin(a) * sp * DEFAULT_DT;

        p.color = new Color(120, 220, 255);
        addCapped(p);
    }

    private static void addCapped(Particle p) {
        active.add(p);
        int overflow = active.size() - MAX;
        if (overflow > 0) {
            active.subList(0, overflow).clear();
        }
    }

    private enum Type {
        MUZZLE,
        MUZZLE_BLOOM,
        SPARK,
        SMOKE,
        ENGINE,
        FIRE,
        DEBRIS,
        SALVAGE,
        SHIELD,
        IMPACT_BLOOM
    }

    private static final class Particle {
        Type type;
        double x, y;
        double vx, vy;   // per-tick delta
        double angle;
        double angleVel;
        double size;
        int life;
        int maxLife;
        int baseAlpha;
        Color color;
    }
}
