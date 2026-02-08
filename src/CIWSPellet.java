/**
 * A tiny fast projectile used by CIWS point-defense.
 *
 * CIWS pellets are visible and can shoot down missiles.
 */
public class CIWSPellet extends Projectile {

    public double angle;

    public CIWSPellet(
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
}
