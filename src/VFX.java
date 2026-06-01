import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.function.BiPredicate;

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
    private static final double MIN_DRAWN_SCREEN_SPAN = 0.55;
    private static final double TINY_SCREEN_SPAN = 2.2;
    private static final double SIMPLE_SCREEN_SPAN = 5.2;
    private static final double DETAIL_SCREEN_SPAN = 10.0;
    private static final int STRESS_ACTIVE_COUNT = 520;
    private static final int PANIC_ACTIVE_COUNT = 760;
    private static final List<Particle> active = new ArrayList<>();
    private static final List<Particle> pool = new ArrayList<>();
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
            if (p.life <= 0) {
                it.remove();
                releaseParticle(p);
            }
        }
    }

    public static int activeCount() {
        return active.size();
    }

    public static void clearAll() {
        active.clear();
        pool.clear();
    }

    /**
     * Allocate a particle from the pool or create a new one.
     * Performance optimization: reuses objects to reduce garbage collection pressure.
     */
    private static Particle allocParticle() {
        if (pool.isEmpty()) {
            return new Particle();
        }
        Particle p = pool.remove(pool.size() - 1);
        resetParticle(p);
        return p;
    }

    /**
     * Release a particle back to the pool for reuse.
     * Called when particles die to avoid garbage collection.
     */
    private static void releaseParticle(Particle p) {
        if (p == null) return;
        resetParticle(p);
        if (pool.size() < MAX * 2) {  // Keep reasonable pool size
            pool.add(p);
        }
    }

    private static void resetParticle(Particle p) {
        if (p == null) return;
        p.type = null;
        p.x = 0.0;
        p.y = 0.0;
        p.vx = 0.0;
        p.vy = 0.0;
        p.angle = 0.0;
        p.angleVel = 0.0;
        p.size = 0.0;
        p.life = 0;
        p.maxLife = 0;
        p.baseAlpha = 0;
        p.color = null;
    }

    /** Draw all particles in world space. */
    public static int drawAll(Graphics2D g2) {
        return drawAll(g2, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    public static int drawAll(Graphics2D g2, double minX, double minY, double maxX, double maxY) {
        return drawAll(g2, minX, minY, maxX, maxY, null);
    }

    public static int drawAll(Graphics2D g2, double minX, double minY, double maxX, double maxY,
                              BiPredicate<Double, Double> worldFilter) {
        if (active.isEmpty()) return 0;
        Object oldAA = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        int drawn = 0;
        int visibleIndex = 0;
        int qualityStride = PerformanceGuardrails.vfxDrawStride();
        double screenScale = screenScale(g2);
        boolean stressMode = active.size() >= STRESS_ACTIVE_COUNT;
        boolean panicMode = active.size() >= PANIC_ACTIVE_COUNT;

        try {
            for (Particle p : active) {
                if (!isVisible(p, minX, minY, maxX, maxY)) continue;
                if (worldFilter != null && !worldFilter.test(p.x, p.y)) continue;
                visibleIndex++;
                if (qualityStride > 1 && (visibleIndex % qualityStride) != 0 && isSkippableUnderPanic(p.type)) continue;
                if (panicMode && isSkippableUnderPanic(p.type) && (visibleIndex & 1) == 0) continue;

                double f = (p.maxLife <= 0) ? 0 : Math.max(0.0, Math.min(1.0, p.life / (double) p.maxLife));
                int alpha = (int) MathUtil.clamp(p.baseAlpha * f, 0, 255);
                double screenSpan = particleScreenSpan(p, screenScale);
                if (alpha <= 6 || screenSpan < MIN_DRAWN_SCREEN_SPAN) continue;
                if (screenSpan <= TINY_SCREEN_SPAN && canFallbackToPixel(p.type)) {
                    drawTinyParticle(g2, p, alpha);
                    drawn++;
                    continue;
                }

                boolean simple = screenSpan <= SIMPLE_SCREEN_SPAN || panicMode;
                boolean reducedDetail = screenSpan <= DETAIL_SCREEN_SPAN || stressMode;
                switch (p.type) {
                    case MUZZLE -> {
                        int a = (int) MathUtil.clamp(alpha, 0, 220);
                        int w = (int) Math.max(2, Math.round(p.size * 1.6));
                        int h = (int) Math.max(2, Math.round(p.size * 1.0));
                        if (!simple) {
                            g2.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), (int) MathUtil.clamp(a * 0.35, 0, 120)));
                            g2.fillOval((int) Math.round(p.x - w), (int) Math.round(p.y - h), w * 2, h * 2);
                        }
                        g2.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), a));
                        if (simple) {
                            g2.fillRect((int) Math.round(p.x - Math.max(1, w / 3.0)), (int) Math.round(p.y - Math.max(1, h / 3.0)),
                                    Math.max(2, w / 2), Math.max(2, h / 2));
                        } else {
                            g2.fillOval((int) Math.round(p.x - w / 2.0), (int) Math.round(p.y - h / 2.0), w, h);
                        }
                        if (!reducedDetail) {
                            g2.setColor(new Color(255, 255, 255, (int) MathUtil.clamp(alpha * 0.7, 0, 200)));
                            int sx = (int) Math.round(p.x);
                            int sy = (int) Math.round(p.y);
                            int ex = (int) Math.round(p.x + Math.cos(p.angle) * (p.size * 2.0));
                            int ey = (int) Math.round(p.y + Math.sin(p.angle) * (p.size * 2.0));
                            g2.drawLine(sx, sy, ex, ey);
                        }
                    }
                    case MUZZLE_BLOOM -> {
                        if (screenSpan <= SIMPLE_SCREEN_SPAN && stressMode) continue;
                        int a = (int) MathUtil.clamp(alpha, 0, 160);
                        Stroke old = g2.getStroke();
                        g2.setStroke(new BasicStroke((float) Math.max(1.0, p.size * 0.2)));
                        g2.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), a));
                        int r = (int) Math.max(2, Math.round(p.size));
                        g2.drawOval((int) Math.round(p.x - r), (int) Math.round(p.y - r), r * 2, r * 2);
                        g2.setStroke(old);
                    }
                    case SPARK -> {
                        g2.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), alpha));
                        int r = (int) Math.max(1, Math.round(p.size));
                        if (simple) {
                            g2.fillRect((int) Math.round(p.x), (int) Math.round(p.y), 1, 1);
                        } else {
                            g2.fillOval((int) Math.round(p.x - r), (int) Math.round(p.y - r), r * 2, r * 2);
                        }
                    }
                    case SMOKE -> {
                        int a = (int) MathUtil.clamp(alpha, 0, 130);
                        Color c = (p.color != null) ? p.color : new Color(120, 200, 255);
                        g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), a));
                        int r = (int) Math.max(1, Math.round(p.size));
                        if (simple) {
                            g2.fillRect((int) Math.round(p.x - 1), (int) Math.round(p.y - 1), 2, 2);
                        } else {
                            g2.fillOval((int) Math.round(p.x - r), (int) Math.round(p.y - r), r * 2, r * 2);
                        }
                    }
                    case ENGINE -> {
                        int a = (int) MathUtil.clamp(alpha, 0, 120);
                        g2.setColor(new Color(120, 220, 255, a));
                        int w = (int) Math.max(2, Math.round(p.size * 1.8));
                        int h = (int) Math.max(2, Math.round(p.size * 0.9));
                        if (simple) {
                            g2.fillRect((int) Math.round(p.x - w / 3.0), (int) Math.round(p.y - h / 3.0),
                                    Math.max(2, w / 2), Math.max(2, h / 2));
                        } else {
                            g2.fillOval((int) Math.round(p.x - w / 2.0), (int) Math.round(p.y - h / 2.0), w, h);
                        }
                    }
                    case FIRE -> {
                        int a = (int) MathUtil.clamp(alpha, 0, 180);
                        int r = (int) Math.max(1, Math.round(p.size));
                        if (!reducedDetail) {
                            int glowA = (int) MathUtil.clamp(a * 0.35, 0, 110);
                            int glowR = (int) Math.max(2, Math.round(p.size * 1.9));
                            g2.setColor(new Color(255, 160, 88, glowA));
                            g2.fillOval((int) Math.round(p.x - glowR), (int) Math.round(p.y - glowR), glowR * 2, glowR * 2);
                        }

                        g2.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), a));
                        if (simple) {
                            g2.fillRect((int) Math.round(p.x - 1), (int) Math.round(p.y - 1), 2, 2);
                        } else {
                            g2.fillOval((int) Math.round(p.x - r), (int) Math.round(p.y - r), r * 2, r * 2);
                        }

                        if (!reducedDetail) {
                            g2.setColor(new Color(255, 245, 220, (int) MathUtil.clamp(a * 0.55, 0, 140)));
                            int r2 = (int) Math.max(1, Math.round(p.size * 0.45));
                            g2.fillOval((int) Math.round(p.x - r2), (int) Math.round(p.y - r2), r2 * 2, r2 * 2);
                        }
                    }
                    case DEBRIS -> {
                        g2.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), alpha));
                        int len = (int) Math.max(2, Math.round(p.size * (reducedDetail ? 1.8 : 2.6)));
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

                        if (!simple) {
                            g2.setColor(new Color(255, 255, 255, (int) MathUtil.clamp(a * 0.6, 0, 160)));
                            g2.drawPolygon(xs, ys, 4);
                        }
                    }
                    case SHIELD -> {
                        int a = (int) MathUtil.clamp(alpha, 0, 190);
                        Stroke old = g2.getStroke();
                        g2.setStroke(new BasicStroke((float) Math.max(1.0, p.size * 0.10)));
                        g2.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), a));
                        int r = (int) Math.max(2, Math.round(p.size));
                        g2.drawOval((int) Math.round(p.x - r), (int) Math.round(p.y - r), r * 2, r * 2);
                        if (!reducedDetail) {
                            g2.setColor(new Color(220, 245, 255, (int) MathUtil.clamp(a * 0.55, 0, 140)));
                            int r2 = (int) Math.max(2, Math.round(p.size * 0.68));
                            g2.drawOval((int) Math.round(p.x - r2), (int) Math.round(p.y - r2), r2 * 2, r2 * 2);
                        }
                        g2.setStroke(old);
                    }
                    case IMPACT_BLOOM -> {
                        int a = (int) MathUtil.clamp(alpha, 0, 180);
                        int r = (int) Math.max(1, Math.round(p.size));
                        g2.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), (int) MathUtil.clamp(a * (reducedDetail ? 0.24 : 0.35), 0, 120)));
                        if (simple) {
                            g2.fillRect((int) Math.round(p.x - 1), (int) Math.round(p.y - 1), 3, 3);
                        } else {
                            g2.fillOval((int) Math.round(p.x - r), (int) Math.round(p.y - r), r * 2, r * 2);
                        }
                        if (!reducedDetail) {
                            g2.setColor(new Color(255, 255, 255, (int) MathUtil.clamp(a * 0.40, 0, 120)));
                            int r2 = (int) Math.max(1, Math.round(p.size * 0.42));
                            g2.fillOval((int) Math.round(p.x - r2), (int) Math.round(p.y - r2), r2 * 2, r2 * 2);
                        }
                    }
                }
                drawn++;
            }
        } finally {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA);
        }
        return drawn;
    }

    private static boolean isVisible(Particle p, double minX, double minY, double maxX, double maxY) {
        if (p == null) return false;
        double radius = Math.max(4.0, p.size * 2.4);
        return p.x + radius >= minX && p.x - radius <= maxX && p.y + radius >= minY && p.y - radius <= maxY;
    }

    private static double screenScale(Graphics2D g2) {
        if (g2 == null) return 1.0;
        AffineTransform tx = g2.getTransform();
        if (tx == null) return 1.0;
        double sx = Math.hypot(tx.getScaleX(), tx.getShearX());
        double sy = Math.hypot(tx.getScaleY(), tx.getShearY());
        double scale = Math.max(sx, sy);
        if (!Double.isFinite(scale) || scale <= 1e-6) return 1.0;
        return scale;
    }

    private static double particleScreenSpan(Particle p, double screenScale) {
        if (p == null) return 0.0;
        double base = switch (p.type) {
            case MUZZLE -> p.size * 3.2;
            case MUZZLE_BLOOM -> p.size * 2.2;
            case SPARK -> p.size * 1.6;
            case SMOKE -> p.size * 2.0;
            case ENGINE -> p.size * 1.8;
            case FIRE -> p.size * 2.4;
            case DEBRIS -> p.size * 2.6;
            case SALVAGE -> p.size * 2.0;
            case SHIELD -> p.size * 2.0;
            case IMPACT_BLOOM -> p.size * 2.0;
        };
        return Math.max(1.0, base * Math.max(0.05, screenScale));
    }

    private static boolean canFallbackToPixel(Type type) {
        return type == Type.SPARK
                || type == Type.SMOKE
                || type == Type.ENGINE
                || type == Type.FIRE
                || type == Type.IMPACT_BLOOM
                || type == Type.MUZZLE;
    }

    private static boolean isSkippableUnderPanic(Type type) {
        return type == Type.SPARK
                || type == Type.SMOKE
                || type == Type.ENGINE
                || type == Type.MUZZLE_BLOOM
                || type == Type.IMPACT_BLOOM;
    }

    private static void drawTinyParticle(Graphics2D g2, Particle p, int alpha) {
        if (g2 == null || p == null) return;
        Color c = (p.color != null) ? p.color : Color.WHITE;
        g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), MathUtil.clamp(alpha, 0, 255)));
        int px = (int) Math.round(p.x);
        int py = (int) Math.round(p.y);
        g2.fillRect(px, py, 1, 1);
    }

    /** Small flame puffs for burning wrecks. Purely cosmetic. */
    public static void spawnShipFire(double x, double y, double intensity) {
        // Ship fire visuals retired so hull deformation remains readable under damage.
    }

    /** Debris burst when a ship finally explodes. baseVx/baseVy are per-tick deltas to inherit. */
    public static void spawnDebrisBurst(double x, double y, double baseVx, double baseVy, int count) {
        count = MathUtil.clamp(count, 5, 30);
        for (int i = 0; i < count; i++) {
            double a = RNG.nextDouble() * Math.PI * 2.0;
            double sp = 80 + RNG.nextDouble() * 300;

            Particle p = allocParticle();
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

            Particle p = allocParticle();
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
        Particle p = allocParticle();
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

        Particle bloom = allocParticle();
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
            case EXPLOSIVE -> 4.8 + sev * 0.9;
            case BEAM -> 3.8 + sev * 0.7;
            case ENERGY -> 3.6 + sev * 0.7;
            case KINETIC -> 3.4 + sev * 0.6;
        };
        int life = switch (s) {
            case EXPLOSIVE -> 10;
            case BEAM -> 7;
            case ENERGY -> 8;
            case KINETIC -> 7;
        };
        int alpha = switch (s) {
            case EXPLOSIVE -> 170;
            case BEAM -> 142;
            case ENERGY -> 150;
            case KINETIC -> 145;
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

            Particle p = allocParticle();
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
        Particle p = allocParticle();
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
            Particle p = allocParticle();
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
        Particle p = allocParticle();
        p.type = Type.SHIELD;
        p.x = x;
        p.y = y;
        p.vx = 0;
        p.vy = 0;
        p.angle = 0;
        p.size = Math.max(8.0, radius);
        p.maxLife = p.life = 10;
        p.baseAlpha = 190;
        p.color = (color != null) ? color : ExperienceRuntime.shieldStateColor();
        addCapped(p);
    }

    public static void spawnBoardingCaptureEffect(double craftX, double craftY, double targetX, double targetY, Color color) {
        Color tint = (color != null) ? color : new Color(126, 255, 204);
        double dx = craftX - targetX;
        double dy = craftY - targetY;
        spawnShieldRipple(targetX, targetY, 22.0, tint);
        spawnShieldRipple(targetX, targetY, 34.0, mixColor(tint, Color.WHITE, 0.38));
        spawnImpactBloom(targetX, targetY, 20.0, tint, 14, 190);
        spawnImpactBurst(targetX, targetY, dx, dy, 18, tint, Math.toRadians(42.0),
                120.0, 180.0, 10, 10, 1.2, 1.9, 210);
        for (int i = 1; i <= 3; i++) {
            double t = i / 4.0;
            double mx = targetX + dx * t;
            double my = targetY + dy * t;
            spawnImpactBloom(mx, my, 7.5 - i * 1.2, tint, 8, 135);
        }
        spawnImpactBloom(craftX, craftY, 10.0, mixColor(tint, Color.WHITE, 0.52), 10, 160);
    }

    public static void spawnArtilleryExecutionEffect(double x, double y, double radius) {
        double rr = Math.max(34.0, radius);
        Color core = new Color(132, 242, 255);
        Color hot = new Color(220, 250, 255);
        spawnShieldRipple(x, y, rr * 0.55, core);
        spawnShieldRipple(x, y, rr * 0.92, hot);
        spawnImpactBloom(x, y, rr * 0.44, core, 15, 205);
        spawnImpactBloom(x, y, rr * 0.24, Color.WHITE, 12, 185);
        spawnImpactBurst(x, y, 1.0, 0.0, 22, core, Math.PI * 2.0,
                130.0, 220.0, 12, 12, 1.2, 1.8, 215);
    }

    public static void spawnHyperLanceFireEffect(double x, double y, double angle, double length) {
        Color base = new Color(116, 228, 255);
        double dx = Math.cos(angle);
        double dy = Math.sin(angle);
        spawnImpactBloom(x, y, 18.0, base, 12, 190);
        spawnShieldRipple(x, y, 24.0, base);
        spawnImpactBurst(x, y, dx, dy, 14, base, Math.toRadians(34.0),
                160.0, 220.0, 10, 10, 1.0, 1.7, 205);
        double leadX = x + dx * Math.max(36.0, length * 0.18);
        double leadY = y + dy * Math.max(36.0, length * 0.18);
        spawnImpactBloom(leadX, leadY, 12.0, mixColor(base, Color.WHITE, 0.38), 9, 150);
    }

    public static void spawnHyperLanceFractureEffect(double x, double y, double angle) {
        Color base = new Color(132, 240, 255);
        spawnShieldRipple(x, y, 20.0, base);
        spawnImpactBloom(x, y, 16.0, base, 12, 180);
        spawnImpactBurst(x, y, Math.cos(angle), Math.sin(angle), 18, base, Math.toRadians(72.0),
                170.0, 210.0, 10, 12, 1.1, 1.9, 205);
    }

    public static void spawnHitSparks(double x, double y, double dirX, double dirY) {
        spawnImpactSparks(x, y, dirX, dirY, 1);
    }

    private static void spawnMuzzleSparks(double x, double y, double angle, int count) {
        int n = MathUtil.clamp(count, 3, 8);
        for (int i = 0; i < n; i++) {
            double a = angle + (RNG.nextDouble() - 0.5) * Math.toRadians(50);
            double sp = 140 + RNG.nextDouble() * 220;

            Particle p = allocParticle();
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
        Particle p = allocParticle();
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

        Particle p = allocParticle();
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

    private static Color mixColor(Color a, Color b, double t) {
        if (a == null) return (b == null) ? Color.WHITE : b;
        if (b == null) return a;
        double u = MathUtil.clamp(t, 0.0, 1.0);
        int r = (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * u);
        int g = (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * u);
        int bb = (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * u);
        return new Color(MathUtil.clamp(r, 0, 255), MathUtil.clamp(g, 0, 255), MathUtil.clamp(bb, 0, 255));
    }
}
