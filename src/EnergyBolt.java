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
                radius,
                damage,
                life,
                faction
        );
        this.angle = angle;
    }

    public EnergyBolt(double x, double y, double angle, double dt, Faction faction) {
        this(x, y, angle, dt,
                DoctrineRegistry.ENERGY_NAVY.mainProjectileSpeed,
                DoctrineRegistry.ENERGY_NAVY.mainDamage,
                120,
                4.5,
                faction);
    }
}
