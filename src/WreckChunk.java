import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

final class WreckChunk {
    private static final int MAX_ACTIVE = 500;
    private static final List<WreckChunk> ACTIVE = new ArrayList<>();
    private static final double DEFAULT_DT = GameContext.DT;

    private final Ship parent;
    private final BufferedImage image;
    private final boolean breach;
    private final double localX;
    private final double localY;
    private final double localAngle;
    private final double detachAt;
    private final double scale;
    private double spin;

    private double x;
    private double y;
    private double vx;
    private double vy;
    private double angle;
    private double life;
    private final double maxLife;
    private boolean attached = true;
    private boolean detachedBoosted = false;

    private WreckChunk(Ship parent, BufferedImage image, boolean breach, double localX, double localY,
                       double localAngle, double detachAt, double scale, double spin, double life) {
        this.parent = parent;
        this.image = image;
        this.breach = breach;
        this.localX = localX;
        this.localY = localY;
        this.localAngle = localAngle;
        this.detachAt = Math.max(0.05, detachAt);
        this.scale = Math.max(0.08, scale);
        this.spin = spin;
        this.life = Math.max(0.2, life);
        this.maxLife = this.life;
    }

    static void spawnForShip(Ship ship, double burnDuration) {
        if (ship == null || !ship.alive) return;
        ShipWreckLibrary.WreckSet set = ShipWreckLibrary.getSet(ship.role, ship.faction);
        if (set == null || !set.hasAny()) return;

        List<BufferedImage> chunks = set.chunks.isEmpty() ? set.breaches : set.chunks;
        if (chunks.isEmpty()) return;

        int count = chunks.size();
        double radius = Math.max(12.0, ship.radius);
        double span = Math.max(0.9, radius * 0.82);
        double baseLife = Math.max(2.6, burnDuration + 1.8);

        for (int i = 0; i < count; i++) {
            BufferedImage img = chunks.get(i);
            double t = (count <= 1) ? 0.5 : (double) i / (double) (count - 1);
            double localX = (-0.38 + t * 0.76) * span;
            double localY = ((i % 2 == 0) ? -0.10 : 0.10) * span * (0.55 + 0.25 * t);
            double localAngle = (i - (count - 1) * 0.5) * 0.16;
            double detachAt = burnDuration * (0.22 + t * 0.58);
            double scale = 0.95 + radius / 150.0;
            double spin = (Ship.randomUnit() - 0.5) * 0.18;
            WreckChunk chunk = new WreckChunk(ship, img, false, localX, localY, localAngle, detachAt, scale, spin, baseLife);
            chunk.syncWithParent();
            chunk.vx = ship.vx;
            chunk.vy = ship.vy;
            add(chunk);
        }

        List<BufferedImage> breaches = set.breaches;
        for (int i = 0; i < breaches.size(); i++) {
            BufferedImage img = breaches.get(i);
            double t = (breaches.size() <= 1) ? 0.5 : (double) i / (double) (breaches.size() - 1);
            double localX = (-0.10 + t * 0.20) * span;
            double localY = (i == 0) ? -0.06 * span : 0.07 * span;
            double localAngle = (Ship.randomUnit() - 0.5) * 0.30;
            double detachAt = burnDuration * (0.72 + t * 0.22);
            double scale = 1.10 + radius / 170.0;
            double spin = (Ship.randomUnit() - 0.5) * 0.10;
            WreckChunk breach = new WreckChunk(ship, img, true, localX, localY, localAngle, detachAt, scale, spin, baseLife + 0.6);
            breach.syncWithParent();
            breach.vx = ship.vx;
            breach.vy = ship.vy;
            add(breach);
        }
    }

    static void releaseForShip(Ship ship, double baseVx, double baseVy) {
        if (ship == null || ACTIVE.isEmpty()) return;
        for (WreckChunk chunk : ACTIVE) {
            if (chunk == null || chunk.parent != ship) continue;
            chunk.forceDetach(baseVx, baseVy);
        }
    }

    static void updateAll(double dt) {
        if (ACTIVE.isEmpty()) return;
        double step = Math.max(0.0, dt);
        for (Iterator<WreckChunk> it = ACTIVE.iterator(); it.hasNext(); ) {
            WreckChunk c = it.next();
            if (c == null) {
                it.remove();
                continue;
            }
            c.update(step);
            if (c.life <= 0.0) {
                it.remove();
            }
        }
    }

    static int drawAll(Graphics2D g2, double minX, double minY, double maxX, double maxY) {
        if (g2 == null || ACTIVE.isEmpty()) return 0;
        int drawn = 0;
        Graphics2D g = (Graphics2D) g2.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            for (WreckChunk c : ACTIVE) {
                if (c == null || c.image == null) continue;
                if (c.attached) c.syncWithParent();
                if (!c.isVisible(minX, minY, maxX, maxY)) continue;
                c.draw(g);
                drawn++;
            }
        } finally {
            g.dispose();
        }
        return drawn;
    }

    private static void add(WreckChunk chunk) {
        ACTIVE.add(chunk);
        while (ACTIVE.size() > MAX_ACTIVE) {
            ACTIVE.remove(0);
        }
    }

    private void update(double dt) {
        life -= dt;
        if (life <= 0.0) return;

        if (attached) {
            if (parent != null && parent.dying) {
                syncWithParent();
            }
            if (parent == null || !parent.dying) {
                forceDetach(parent == null ? 0.0 : parent.vx, parent == null ? 0.0 : parent.vy);
            } else if (parent != null && parent.dyingTimerSeconds() >= detachAt) {
                forceDetach(parent.vx, parent.vy);
            }
        } else {
            x += vx;
            y += vy;
            angle += spin * dt * 60.0;
            double drag = Math.pow(0.985, dt * 60.0);
            vx *= drag;
            vy *= drag;
        }
    }

    private void forceDetach(double baseVx, double baseVy) {
        if (!attached) return;
        attached = false;
        if (!detachedBoosted) {
            double burst = breach ? 2.0 : 4.0;
            double dir = angle;
            vx = baseVx + Math.cos(dir) * burst * DEFAULT_DT;
            vy = baseVy + Math.sin(dir) * burst * DEFAULT_DT;
            spin = breach ? spin * 0.6 : spin * 1.4;
            detachedBoosted = true;
        }
    }

    private void syncWithParent() {
        if (parent == null) return;
        double cos = Math.cos(parent.angle);
        double sin = Math.sin(parent.angle);
        x = parent.x + localX * cos - localY * sin;
        y = parent.y + localX * sin + localY * cos;
        angle = parent.angle + localAngle;
    }

    private boolean isVisible(double minX, double minY, double maxX, double maxY) {
        double size = drawSize();
        return x + size >= minX && x - size <= maxX && y + size >= minY && y - size <= maxY;
    }

    private double drawSize() {
        double base = Math.max(24.0, (image == null ? 48.0 : Math.max(image.getWidth(), image.getHeight()) * 0.14));
        return base * scale;
    }

    private void draw(Graphics2D g2) {
        double size = drawSize();
        double alpha = attached ? 0.96 : Math.max(0.0, Math.min(1.0, life / maxLife));
        if (breach) {
            alpha *= attached ? 0.90 : 0.80;
        }
        if (alpha <= 0.01) return;

        AffineTransform old = g2.getTransform();
        java.awt.Composite oldComposite = g2.getComposite();
        try {
            g2.translate(x, y);
            g2.rotate(angle);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) alpha));
            g2.drawImage(image, (int) Math.round(-size), (int) Math.round(-size),
                    (int) Math.round(size * 2.0), (int) Math.round(size * 2.0), null);
            if (breach) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (alpha * 0.32)));
                g2.setColor(new Color(255, 180, 90, 100));
                g2.fillOval((int) Math.round(-size * 0.28), (int) Math.round(-size * 0.28),
                        (int) Math.round(size * 0.56), (int) Math.round(size * 0.56));
            }
        } finally {
            g2.setTransform(old);
            g2.setComposite(oldComposite);
        }
    }
}
