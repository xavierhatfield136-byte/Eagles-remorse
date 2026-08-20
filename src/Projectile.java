/**
 * Base projectile type.
 *
 * IMPORTANT: This matches the "per-tick delta" pattern used in your Ship code:
 * - vx/vy are PER-TICK deltas (already scaled by dt), so update() does x += vx; y += vy;
 * - life is counted in frames (ticks).
 */
public abstract class Projectile {
    public double x, y;
    public final double originX, originY;
    public double vx, vy;     // per-tick delta
    public double radius;
    public int damage;
    public int life;          // frames remaining

    // Who fired this projectile (used for friendly-fire rules)
    public Faction faction = Faction.ENEMY;
    // Runtime source tracking for kill-assist crediting and telemetry.
    public int sourceShipId = -1;

    public boolean alive = true;
    
    // Phase 5.7: Damage growth - accumulate distance traveled, scale damage accordingly
    public double distanceTraveled = 0.0;
    public double damageGrowthPerUnit = 0.0;  // damage multiplier per unit of distance

    protected Projectile(double x, double y, double vx, double vy, double radius, int damage, int life, Faction faction) {
        this.x = x;
        this.y = y;
        this.originX = x;
        this.originY = y;
        this.vx = vx;
        this.vy = vy;
        this.radius = radius;
        this.damage = damage;
        this.life = life;
        if (faction != null) this.faction = faction;
    }

    // Backwards-compatible constructor (defaults faction to ENEMY)
    protected Projectile(double x, double y, double vx, double vy, double radius, int damage, int life) {
        this(x, y, vx, vy, radius, damage, life, Faction.ENEMY);
    }

    public void update(double dt) {
        x += vx;
        y += vy;
        
        // Phase 5.7: Track distance traveled for damage growth
        if (damageGrowthPerUnit > 1e-6) {
            double dist = Math.hypot(vx, vy);
            distanceTraveled += dist;
        }

        life--;
        if (life <= 0) alive = false;
    }
    
    /** Return effective damage accounting for growth. Used during impact calculations. */
    public int getEffectiveDamage() {
        if (damageGrowthPerUnit <= 1e-6) return damage;
        double growth = 1.0 + (distanceTraveled * damageGrowthPerUnit);
        return (int) Math.round(damage * growth);
    }
}
