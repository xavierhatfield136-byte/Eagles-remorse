/**
 * A simple turret/hardpoint attached to a ship.
 *
 * Turrets are modeled visually and can fire either bullets or missiles.
 * For bullets, they fire toward a target point.
 * For missiles, they fire at a target ship.
 */
public class Turret {

    // Universal missile buff (applies to all factions/ships).
    public static final double MISSILE_DAMAGE_MULT = 1.55;
    public static final double MISSILE_SPEED_MULT = 1.40;
    public static final double MISSILE_TURN_MULT = 1.32;
    public static final double MISSILE_LIFE_MULT = 1.22;
    public static final double GUN_PROJECTILE_SPEED_MULT = 1.18;

    public enum Kind {
        GUN,
        MISSILE
    }

    // Local offset from ship center (in ship-local coordinates)
    public final double localX;
    public final double localY;

    // Current turret angle (world radians)
    public double angle;

    // How fast the turret can rotate (rad/sec)
    public double turnRate = Math.toRadians(240);

    // Fire control
    public double cooldown = 0.15;      // seconds between shots
    private double coolLeft = 0;

    // Weapon stats
    public Kind kind;
    public int damage = 1;
    public double bulletSpeed = 750;
    public int bulletLife = 120;

    public double missileSpeed = 220;
    public double missileTurnRate = Math.toRadians(180);
    public int missileLife = 180;

    // Render
    public double radius = 6;
    public double barrelLen = 14;

    public boolean primary = true; // primary fire if true, secondary if false

    public Turret(Kind kind, double localX, double localY) {
        this.kind = kind;
        this.localX = localX;
        this.localY = localY;
    }

    public void update(double dt) {
        if (coolLeft > 0) {
            coolLeft -= dt;
            if (coolLeft < 0) coolLeft = 0;
        }
    }

    /** Useful for transports/resupply: reduce current cooldown timer. */
    public void reduceCooldown(double seconds) {
        if (seconds <= 0) return;
        coolLeft -= seconds;
        if (coolLeft < 0) coolLeft = 0;
    }

    public void setReady() {
        coolLeft = 0;
    }

    public double getCooldownRemaining() {
        return coolLeft;
    }

    /** Aim the turret toward a target point in world-space. */
    public void aimAt(double dt, Ship host, double targetX, double targetY) {
        double wx = worldX(host);
        double wy = worldY(host);
        double desired = Math.atan2(targetY - wy, targetX - wx);
        rotateToward(dt, desired);
    }

    /** Aim the turret toward a target ship. */
    public void aimAt(double dt, Ship host, Ship target) {
        if (target == null) return;
        aimAt(dt, host, target.x, target.y);
    }

    /**
     * Aim the turret using predictive leading for a moving target.
     *
     * NOTE: This game stores vx/vy as per-tick deltas (already scaled by dt).
     * We convert back to per-second velocity by dividing by dt.
     */
    public void aimAtLead(double dt, Ship host, Ship target, double projectileSpeed) {
        if (target == null) return;
        if (dt <= 0) {
            aimAt(dt, host, target);
            return;
        }

        double wx = worldX(host);
        double wy = worldY(host);

        double tvx = target.vx / dt;
        double tvy = target.vy / dt;

        double[] ip = MathUtil.interceptPoint(wx, wy, target.x, target.y, tvx, tvy, projectileSpeed);
        aimAt(dt, host, ip[0], ip[1]);
    }

    public static double effectiveGunProjectileSpeed(Turret t) {
        if (t == null) return 0.0;
        return t.bulletSpeed * GUN_PROJECTILE_SPEED_MULT;
    }

    private void rotateToward(double dt, double desired) {
        double delta = MathUtil.normalizeAngle(desired - angle);
        double max = turnRate * dt;
        delta = MathUtil.clamp(delta, -max, max);
        angle = MathUtil.normalizeAngle(angle + delta);
    }

    public boolean canFire() {
        return coolLeft <= 0;
    }

    public Projectile fire(Ship host, Ship missileTarget, double dt) {
        if (!canFire()) return null;

        double cycleMul = (host == null) ? 1.0 : host.weaponCycleRateMultiplier();
        double damageMul = (host == null) ? 1.0 : host.weaponDamageMultiplier();
        cycleMul = Math.max(0.20, cycleMul);
        damageMul = Math.max(0.20, damageMul);
        coolLeft = cooldown / cycleMul;

        if (host != null) host.onFire();

        // Spawn at turret muzzle
        double mx = worldX(host) + Math.cos(angle) * (radius + 4);
        double my = worldY(host) + Math.sin(angle) * (radius + 4);

        // Cosmetic muzzle flash
        VFX.spawnMuzzleFlash(mx, my, angle, kind == Kind.MISSILE);

        if (kind == Kind.GUN) {
            // Doctrine-based main projectile style.
            // ENERGY_NAVY uses a Yamato 2199-style heavy energy bolt (visible, medium speed).
            // KINETIC_CONSORTIUM uses the existing fast conventional rounds.
            double projectileSpeed = bulletSpeed * GUN_PROJECTILE_SPEED_MULT;
            int gunDamage = Math.max(1, (int) Math.round(damage * damageMul));
            DoctrineProfile prof = DoctrineRegistry.forFaction(host.faction);
            if (prof.doctrine == Doctrine.ENERGY_NAVY) {
                return new EnergyBolt(mx, my, angle, dt, projectileSpeed, gunDamage, bulletLife, 4.5, host.faction);
            }
            return new Bullet(mx, my, angle, dt, projectileSpeed, gunDamage, bulletLife, 3.0, host.faction);
        } else {
            int missileDamage = Math.max(1, (int) Math.round(damage * MISSILE_DAMAGE_MULT * damageMul));
            double missileSpd = missileSpeed * MISSILE_SPEED_MULT;
            double missileTurn = missileTurnRate * MISSILE_TURN_MULT;
            int missileLifetime = Math.max(1, (int) Math.round(missileLife * MISSILE_LIFE_MULT));
            double missileRadius = Math.max(6.0, radius);
            return new Missile(mx, my, angle, missileTarget, dt, missileSpd, missileTurn, missileDamage, missileLifetime, missileRadius, host.faction);
        }
    }

    public double worldX(Ship host) {
        double ca = Math.cos(host.angle);
        double sa = Math.sin(host.angle);
        return host.x + localX * ca - localY * sa;
    }

    public double worldY(Ship host) {
        double ca = Math.cos(host.angle);
        double sa = Math.sin(host.angle);
        return host.y + localX * sa + localY * ca;
    }
}
