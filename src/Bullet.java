public class Bullet extends Projectile {

    private static final double SPEED = 750; // units/sec

    public Bullet(double x, double y, double angle, double dt) {
        this(x, y, angle, dt, SPEED, 1, 120, 3.0, Faction.PLAYER);
    }

    public Bullet(double x, double y, double angle, double dt, Faction faction) {
        this(x, y, angle, dt, SPEED, 1, 120, 3.0, faction);
    }

    public Bullet(
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
    }
}
