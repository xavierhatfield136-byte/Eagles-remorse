import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Explosion {

    // Hard cap so even if something goes wrong, the game never floods effects.
    private static final int MAX_EFFECTS = 1200;

    public static final List<Explosion> active = new ArrayList<>();

    public enum Kind {
        SHIELD_HIT,
        DEATH
    }

    public final double x, y;
    public final Kind kind;

    // seconds remaining
    private double t;

    // for drawing convenience (optional)
    public final double maxT;

    private Explosion(double x, double y, double seconds, Kind kind) {
        this.x = x;
        this.y = y;
        this.kind = (kind == null) ? Kind.DEATH : kind;
        this.t = seconds;
        this.maxT = seconds;
    }

    public static void spawnShieldHit(double x, double y) {
        addCapped(new Explosion(x, y, 0.16, Kind.SHIELD_HIT)); // short impact ripple
    }

    public static void spawnDeath(double x, double y) {
        addCapped(new Explosion(x, y, 0.64, Kind.DEATH)); // staged blast
    }

    private static void addCapped(Explosion e) {
        active.add(e);
        // drop oldest if over cap
        while (active.size() > MAX_EFFECTS) {
            active.remove(0);
        }
    }

    /** Call once per tick with dt (seconds). */
    public static void updateAll(double dt) {
        if (active.isEmpty()) return;

        for (Iterator<Explosion> it = active.iterator(); it.hasNext(); ) {
            Explosion e = it.next();
            e.t -= dt;
            if (e.t <= 0) it.remove();
        }
    }

    /** Backwards compatible (assumes ~60fps). */
    public static void updateAll() {
        updateAll(1.0 / 60.0);
    }

    /** 0..1 fraction remaining (useful if you want fade-out). */
    public double frac() {
        if (maxT <= 0) return 0;
        return Math.max(0.0, Math.min(1.0, t / maxT));
    }

    /** 0..1 fraction elapsed. */
    public double ageFrac() {
        return 1.0 - frac();
    }
}
