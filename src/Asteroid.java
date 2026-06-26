/**
 * Solid obstacle in the world.
 *
 * Asteroids:
 * - Block ship movement (ships are pushed out, no damage)
 * - Can be mined for ore
 * - Can be destroyed by sustained weapons fire
 */
public class Asteroid {
    private static int NEXT_ID = 1;

    static int beginDeterministicIdScope() {
        int previous = NEXT_ID;
        NEXT_ID = 1;
        return previous;
    }

    static void endDeterministicIdScope(int previousNextId) {
        NEXT_ID = Math.max(previousNextId, NEXT_ID);
    }
    public final int id = NEXT_ID++;

    public double x, y;
    public double radius;

    // Resource content
    public int oreMax;
    public int ore;
    public int hpMax;
    public int hp;


    // Event modifiers / visuals
    public boolean rich = false;
    public double richness = 1.0;
    // purely visual
    public double spin;
    public double spinRate;

    public Asteroid(double x, double y, double radius) {
        this(x, y, radius, 120);
    }

    public Asteroid(double x, double y, double radius, int oreAmount) {
        this.x = x;
        this.y = y;
        this.radius = radius;

        this.oreMax = Math.max(0, oreAmount);
        this.ore = this.oreMax;
        this.hpMax = computeDurability();
        this.hp = this.hpMax;


        // Mark as \"rich\" for rendering emphasis when ore payload is high.
        this.rich = this.oreMax >= 420;
        this.richness = this.rich ? 1.8 : 1.0;
        this.spin = Math.random() * Math.PI * 2.0;
        this.spinRate = (Math.random() - 0.5) * 0.6;
    }

    public void update(double dt) {
        spin = MathUtil.normalizeAngle(spin + spinRate * dt);
    }

    public double collisionRadius() {
        return Math.max(BalanceConfig.ASTEROID_COLLISION_RADIUS_MIN,
                radius * BalanceConfig.ASTEROID_COLLISION_RADIUS_SCALE);
    }

    /** Take up to amount ore from this asteroid and return what was actually taken. */
    public int takeOre(int amount) {
        if (amount <= 0 || ore <= 0) return 0;
        int take = Math.min(amount, ore);
        ore -= take;
        return take;
    }

    public boolean applyWeaponDamage(int damage) {
        if (damage <= 0) return hp <= 0;
        if (hp <= 0) return true;
        hp = Math.max(0, hp - damage);
        if (hp <= 0) {
            // Destroyed asteroids are no longer mineable.
            ore = 0;
        }
        return hp <= 0;
    }

    private int computeDurability() {
        double sizeFactor = Math.max(8.0, collisionRadius()) * 4.2;
        double oreFactor = Math.sqrt(Math.max(0, oreMax)) * 1.25;
        int durability = (int) Math.round(48.0 + sizeFactor + oreFactor);
        return Math.max(72, durability);
    }
}
