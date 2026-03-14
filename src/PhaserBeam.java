/**
 * Continuous tracking beam projectile (phaser-like).
 *
 * The beam is anchored to an emitter ship/turret and persists for a short
 * duration, updating its origin and angle every frame as the turret tracks.
 */
public class PhaserBeam extends Projectile {
    private final Ship emitter;
    private final Turret emitterTurret;
    private final double muzzleOffset;

    public double angle;
    public double length;
    public double width;
    public double damagePerSecond;

    public PhaserBeam(
            Ship emitter,
            Turret emitterTurret,
            double angle,
            double length,
            double width,
            double damagePerSecond,
            int life,
            Faction faction
    ) {
        super(
                emitter != null ? emitter.x : 0.0,
                emitter != null ? emitter.y : 0.0,
                0.0,
                0.0,
                Math.max(1.0, width),
                1,
                Math.max(1, life),
                faction
        );
        this.emitter = emitter;
        this.emitterTurret = emitterTurret;
        this.muzzleOffset = (emitterTurret == null) ? 10.0 : (emitterTurret.radius + 4.0);
        this.angle = angle;
        this.length = Math.max(80.0, length);
        this.width = Math.max(1.0, width);
        this.damagePerSecond = Math.max(0.0, damagePerSecond);
        syncFromEmitter();
    }

    @Override
    public void update(double dt) {
        if (!alive) return;
        if (emitter == null || emitterTurret == null) {
            alive = false;
            return;
        }
        if (!emitter.alive || emitter.dying || emitter.hp <= 0) {
            alive = false;
            return;
        }

        syncFromEmitter();
        life--;
        if (life <= 0) alive = false;
    }

    public double startX() {
        return x;
    }

    public double startY() {
        return y;
    }

    public double endX() {
        return x + Math.cos(angle) * length;
    }

    public double endY() {
        return y + Math.sin(angle) * length;
    }

    public int rollFrameDamage(java.util.Random rng, double dt) {
        double expected = Math.max(0.0, damagePerSecond * Math.max(0.0, dt));
        if (expected <= 0.0) return 0;
        int whole = (int) Math.floor(expected);
        double frac = expected - whole;
        if (frac > 1e-9) {
            double roll = (rng == null) ? Math.random() : rng.nextDouble();
            if (roll < frac) whole++;
        }
        return Math.max(0, whole);
    }

    private void syncFromEmitter() {
        if (emitter == null || emitterTurret == null) return;
        angle = emitterTurret.angle;
        x = emitterTurret.worldX(emitter) + Math.cos(angle) * muzzleOffset;
        y = emitterTurret.worldY(emitter) + Math.sin(angle) * muzzleOffset;
    }
}

