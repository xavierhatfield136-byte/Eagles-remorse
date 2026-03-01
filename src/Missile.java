public class Missile extends Projectile {
    public static final int BASE_INTERCEPT_HP = 3;
    public static final int HEAVY_INTERCEPT_HP = 4;

    public double angle;
    public double speed = 300;                   // units/sec
    public double turnRate = Math.toRadians(280);// rad/sec

    public Ship target;
    public int interceptHp = 2;
    public double blastRadius = 56.0;
    public double splashDamageMul = 0.60;

    public Missile(double x, double y, double angle, Ship target, double dt) {
        this(x, y, angle, target, dt, 300, Math.toRadians(280), 5, 240, 7.0, Faction.PLAYER);
    }

    public Missile(double x, double y, double angle, Ship target, double dt, Faction faction) {
        this(x, y, angle, target, dt, 300, Math.toRadians(280), 5, 240, 7.0, faction);
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
        this.interceptHp = (damage >= 8) ? HEAVY_INTERCEPT_HP : BASE_INTERCEPT_HP;
        this.blastRadius = Math.max(38.0, radius * 8.0);
        this.splashDamageMul = 0.60;

        vx = Math.cos(angle) * this.speed * dt;
        vy = Math.sin(angle) * this.speed * dt;
    }

    public boolean applyInterceptHit(int damage) {
        int d = Math.max(1, damage);
        interceptHp -= d;
        if (interceptHp <= 0) {
            alive = false;
            return true;
        }
        return false;
    }

    @Override
    public void update(double dt) {
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
