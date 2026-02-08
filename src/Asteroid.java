/**
 * Solid obstacle in the world.
 *
 * Asteroids:
 * - Block ship movement (ships are pushed out, no damage)
 * - Block projectiles (projectiles die on contact)
 */
public class Asteroid {
    public double x, y;
    public double radius;

    // Resource content
    public int oreMax;
    public int ore;


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


        // Mark as \"rich\" for rendering emphasis when ore payload is high.
        this.rich = this.oreMax >= 420;
        this.richness = this.rich ? 1.8 : 1.0;
        this.spin = Math.random() * Math.PI * 2.0;
        this.spinRate = (Math.random() - 0.5) * 0.6;
    }

    public void update(double dt) {
        spin = MathUtil.normalizeAngle(spin + spinRate * dt);
    }

    /** Take up to amount ore from this asteroid and return what was actually taken. */
    public int takeOre(int amount) {
        if (amount <= 0 || ore <= 0) return 0;
        int take = Math.min(amount, ore);
        ore -= take;
        return take;
    }
}
