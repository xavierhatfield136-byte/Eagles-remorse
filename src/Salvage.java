/**
 * Simple world pickup.
 * - Spawned by random events (Salvage Drift) and now also by ship explosions.
 * - Collected by the player ship by flying close.
 *
 * NOTE: This project uses a per-tick delta pattern:
 * - vx/vy are per-tick deltas (already scaled for the fixed DT), so update is x += vx; y += vy.
 */
public class Salvage {
    public double x, y;

    // Optional drift (per-tick delta). Event-spawned salvage uses 0 drift by default.
    public double vx = 0, vy = 0;

    public double radius = 10;

    // Reward payload
    public int credits;
    public int ore;

    // Lifetime in seconds
    public double life;

    /** Original constructor used by earlier random events (no drift). */
    public Salvage(double x, double y, int credits, int ore, double life) {
        this(x, y, 0, 0, credits, ore, life);
    }

    /** Constructor with drift (used by ship explosion drops). */
    public Salvage(double x, double y, double vx, double vy, int credits, int ore, double life) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.credits = Math.max(0, credits);
        this.ore = Math.max(0, ore);
        this.life = Math.max(0.0, life);
    }

    public void update(double dt) {
        // Drift (if any)
        x += vx;
        y += vy;

        // Gentle drag so drops settle down.
        double drag = Math.pow(0.992, dt * 60.0);
        vx *= drag;
        vy *= drag;

        life -= dt;
    }

    public boolean alive() {
        return life > 0;
    }
}
