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

    private static final int MAX = 2200;
    private static final List<Particle> active = new ArrayList<>();
    private static final Random RNG = new Random();

    // Spawn helpers may be called from places that don't have dt handy.
    private static final double DEFAULT_DT = 1.0 / 60.0;

    public static void updateAll(double dt) {
        if (active.isEmpty()) return;
        for (Iterator<Particle> it = active.iterator(); it.hasNext(); ) {
            Particle p = it.next();
            p.x += p.vx;
            p.y += p.vy;
            p.angle += p.angleVel;
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
                    g2.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), a));
                    int w = (int) Math.round(p.size * 1.6);
                    int h = (int) Math.round(p.size * 1.0);
                    g2.fillOval((int) Math.round(p.x - w / 2.0), (int) Math.round(p.y - h / 2.0), w, h);

                    // little streak
                    g2.setColor(new Color(255, 255, 255, (int) MathUtil.clamp(alpha * 0.7, 0, 200)));
                    int sx = (int) Math.round(p.x);
                    int sy = (int) Math.round(p.y);
                    int ex = (int) Math.round(p.x + Math.cos(p.angle) * (p.size * 2.0));
                    int ey = (int) Math.round(p.y + Math.sin(p.angle) * (p.size * 2.0));
                    g2.drawLine(sx, sy, ex, ey);
                }
                case SPARK -> {
                    g2.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), alpha));
                    int r = (int) Math.max(1, Math.round(p.size));
                    g2.fillOval((int) Math.round(p.x - r), (int) Math.round(p.y - r), r * 2, r * 2);
                }
                case SMOKE -> {
                    int a = (int) MathUtil.clamp(alpha, 0, 130);
                    g2.setColor(new Color(120, 200, 255, a));
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
        count = MathUtil.clamp(count, 6, 42);
        for (int i = 0; i < count; i++) {
            double a = RNG.nextDouble() * Math.PI * 2.0;
            double sp = 90 + RNG.nextDouble() * 340;

            Particle p = new Particle();
            p.type = Type.DEBRIS;
            p.x = x + (RNG.nextDouble() - 0.5) * 6.0;
            p.y = y + (RNG.nextDouble() - 0.5) * 6.0;
            p.angle = a;
            p.angleVel = (RNG.nextDouble() - 0.5) * 0.25;
            p.size = 1.6 + RNG.nextDouble() * 2.2;
            p.maxLife = p.life = 34 + RNG.nextInt(52);
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
    }

    public static void spawnImpactSparks(double x, double y, double dirX, double dirY, int strength) {
        if (strength <= 0) strength = 1;
        int count = MathUtil.clamp(4 + strength * 2, 4, 18);
        double baseAng = Math.atan2(dirY, dirX);

        for (int i = 0; i < count; i++) {
            double a = baseAng + (RNG.nextDouble() - 0.5) * Math.toRadians(140);
            double sp = 110 + RNG.nextDouble() * 240;
            int life = 10 + RNG.nextInt(12);

            Particle p = new Particle();
            p.type = Type.SPARK;
            p.x = x;
            p.y = y;
            p.angle = a;
            p.size = 1.2 + RNG.nextDouble() * 1.8;
            p.maxLife = p.life = life;
            p.baseAlpha = 200;
            p.color = new Color(255, 210, 120);

            // Convert to per-tick deltas
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
        while (active.size() > MAX) {
            active.remove(0);
        }
    }

    private enum Type {
        MUZZLE,
        SPARK,
        SMOKE,
        ENGINE,
        FIRE,
        DEBRIS,
        SALVAGE
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
