public class Missile extends Projectile {
    public static final int BASE_INTERCEPT_HP = 3;
    public static final int HEAVY_INTERCEPT_HP = 4;
    public static final int INFINITE_GUIDANCE_TICKS = Integer.MAX_VALUE;
    public static final double GLOBAL_SPEED_MULT = 2.0;
    public static final double MAX_RUNTIME_SPEED_M_PER_SEC = 1000.0;
    public static final double HEAVY_RUNTIME_SPEED_M_PER_SEC = MAX_RUNTIME_SPEED_M_PER_SEC;
    private static final double NON_YELLOW_SPEED_MULT = 2.35;
    private static final double YELLOW_SPEED_MULT = 1.00;

    public enum StrikeVisual {
        DEFAULT,
        TORPEDO,
        ATOMIC
    }

    public double angle;
    public double speed = 300;                   // units/sec
    public double turnRate = Math.toRadians(280);// rad/sec

    public Ship target;
    public Projectile projectileTarget;
    public Turret.MissileRole role = Turret.MissileRole.ANTI_MEDIUM;
    public int interceptHp = 2;
    public double blastRadius = 56.0;
    public double splashDamageMul = 0.60;
    public int guidanceTicksRemaining = INFINITE_GUIDANCE_TICKS;
    public boolean canRetarget = false;
    public boolean preferSmallCraft = false;
    public double retargetRange = 900.0;
    public double visualScale = 1.0;
    public StrikeVisual strikeVisual = StrikeVisual.DEFAULT;

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

        this.speed = capSpeedForRole(speed * GLOBAL_SPEED_MULT * factionMissileSpeedMultiplier(faction), role);
        this.turnRate = turnRate;
        this.interceptHp = (damage >= 8) ? HEAVY_INTERCEPT_HP : BASE_INTERCEPT_HP;
        this.blastRadius = Math.max(38.0, radius * 8.0);
        this.splashDamageMul = 0.60;

        refreshVelocity(dt);
    }

    public boolean hasGuidance() {
        return guidanceTicksRemaining > 0;
    }

    public void copyBehaviorFrom(Missile other) {
        if (other == null) return;
        projectileTarget = other.projectileTarget;
        role = other.role;
        interceptHp = other.interceptHp;
        blastRadius = other.blastRadius;
        splashDamageMul = other.splashDamageMul;
        guidanceTicksRemaining = other.guidanceTicksRemaining;
        canRetarget = other.canRetarget;
        preferSmallCraft = other.preferSmallCraft;
        retargetRange = other.retargetRange;
        visualScale = other.visualScale;
        strikeVisual = (other.strikeVisual == null) ? StrikeVisual.DEFAULT : other.strikeVisual;
    }

    public void applyRoleSpeedCap(Turret.MissileRole role, double dt) {
        this.role = (role == null) ? Turret.MissileRole.ANTI_MEDIUM : role;
        speed = capSpeedForRole(speed, this.role);
        refreshVelocity(dt);
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
        speed = capSpeedForRole(speed, role);
        if (guidanceTicksRemaining != INFINITE_GUIDANCE_TICKS) {
            guidanceTicksRemaining = Math.max(0, guidanceTicksRemaining - 1);
        }

        if (!hasGuidance()) {
            target = null;
            projectileTarget = null;
        } else if (projectileTarget != null && projectileTarget.alive) {
            double desired = Math.atan2(projectileTarget.y - y, projectileTarget.x - x);
            double delta = MathUtil.normalizeAngle(desired - angle);
            delta = MathUtil.clamp(delta, -turnRate * dt, turnRate * dt);
            angle = MathUtil.normalizeAngle(angle + delta);
        } else if (target != null && target.alive && !target.dying && target.hp > 0) {
            double desired = Math.atan2(target.y - y, target.x - x);
            double delta = MathUtil.normalizeAngle(desired - angle);
            delta = MathUtil.clamp(delta, -turnRate * dt, turnRate * dt);
            angle = MathUtil.normalizeAngle(angle + delta);
        }

        refreshVelocity(dt);

        super.update(dt);
    }

    public static double configuredSpeedForRuntimeSpeed(double runtimeSpeed, Faction faction) {
        double mult = GLOBAL_SPEED_MULT * factionMissileSpeedMultiplier(faction);
        if (!Double.isFinite(runtimeSpeed) || runtimeSpeed <= 0.0 || mult <= 1e-9) return 0.0;
        return runtimeSpeed / mult;
    }

    private static double factionMissileSpeedMultiplier(Faction faction) {
        return faction != null && faction.isYellowLineage() ? YELLOW_SPEED_MULT : NON_YELLOW_SPEED_MULT;
    }

    private static double capSpeedForRole(double runtimeSpeed, Turret.MissileRole role) {
        if (!Double.isFinite(runtimeSpeed) || runtimeSpeed <= 0.0) return 0.0;
        if (role == Turret.MissileRole.ANTI_HEAVY) return HEAVY_RUNTIME_SPEED_M_PER_SEC;
        return Math.min(runtimeSpeed, MAX_RUNTIME_SPEED_M_PER_SEC);
    }

    private void refreshVelocity(double dt) {
        vx = Math.cos(angle) * speed * dt;
        vy = Math.sin(angle) * speed * dt;
    }
}
