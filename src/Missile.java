public class Missile extends Projectile {

    public double angle;
    public double speed = 220;                   // units/sec
    public double turnRate = Math.toRadians(180);// rad/sec

    public Ship target;

    public Missile(double x, double y, double angle, Ship target, double dt) {
        this(x, y, angle, target, dt, 220, Math.toRadians(180), 3, 180, 6.0, Faction.PLAYER);
    }

    public Missile(double x, double y, double angle, Ship target, double dt, Faction faction) {
        this(x, y, angle, target, dt, 220, Math.toRadians(180), 3, 180, 6.0, faction);
    }

    public Missile(
            double x,
            double y,
            double angle,
            Ship target,
            double dt,
            double speed,
            double turnRate,
            int damage,
            int life,
            double radius,
            Faction faction
    ) {
        super(x, y, 0, 0, radius, damage, life, faction);
        this.angle = angle;
        this.target = target;

        this.speed = speed;
        this.turnRate = turnRate;

        vx = Math.cos(angle) * this.speed * dt;
        vy = Math.sin(angle) * this.speed * dt;
    }

    @Override
    public void update(double dt) {
        // Cosmetic smoke trail
        double tx = x - Math.cos(angle) * (radius + 5);
        double ty = y - Math.sin(angle) * (radius + 5);
        VFX.spawnMissileSmoke(tx, ty);

        if (target != null && target.alive) {
            double desired = Math.atan2(target.y - y, target.x - x);
            double delta = MathUtil.normalizeAngle(desired - angle);
            delta = MathUtil.clamp(delta, -turnRate * dt, turnRate * dt);
            angle = MathUtil.normalizeAngle(angle + delta);
        }

        vx = Math.cos(angle) * speed * dt;
        vy = Math.sin(angle) * speed * dt;

        super.update(dt);
    }
}
