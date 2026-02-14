/**
 * Yamato 2199-style heavy energy bolt.
 *
 * This is NOT a hitscan beam: it's a visible projectile with medium speed
 * and a chunky impact. Visual styling is handled in Renderer.drawProjectiles
 * (via instanceof EnergyBolt).
 */
public class EnergyBolt extends Projectile {

    /** Angle for rendering the bolt "capsule" shape. */
    public final double angle;
    /** True for the heavier BEAM_BOLT family (visual + tuning). */
    public final boolean beamBolt;

    public EnergyBolt(
            double x,
            double y,
            double angle,
            double dt,
            double speed,
            int damage,
            int life,
            double radius,
            Faction faction
    ) {
        super(
                x,
                y,
                Math.cos(angle) * speed * dt,
                Math.sin(angle) * speed * dt,
                computeRadius(speed, radius),
                damage,
                life,
                faction
        );
        this.angle = angle;
        this.beamBolt = isBeamBoltSpeed(speed);
    }

    public EnergyBolt(double x, double y, double angle, double dt, Faction faction) {
        this(x, y, angle, dt,
                DoctrineRegistry.ENERGY_NAVY.mainProjectileSpeed,
                DoctrineRegistry.ENERGY_NAVY.mainDamage,
                120,
                4.5,
                faction);
    }

    public boolean isBeamBolt() {
        return beamBolt;
    }

    private static boolean isBeamBoltSpeed(double speed) {
        return speed <= Ship.BEAM_BOLT_SPEED + 1e-6;
    }

    private static double computeRadius(double speed, double radius) {
        if (isBeamBoltSpeed(speed)) {
            return Math.max(radius, 7.0);
        }
        return radius;
    }
}
