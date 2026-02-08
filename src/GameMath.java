/** Utility helpers used by systems (kept minimal to avoid touching your existing MathUtil). */
public final class GameMath {
    private GameMath(){}

    public static double dist2(double ax, double ay, double bx, double by) {
        double dx = ax - bx, dy = ay - by;
        return dx * dx + dy * dy;
    }

    public static double clamp(double v, double lo, double hi) {
        return (v < lo) ? lo : (v > hi ? hi : v);
    }
}
