public final class MathUtil {

    private MathUtil() {}

    public static double sq(double v) { return v * v; }

    public static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static double dist2(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        return dx * dx + dy * dy;
    }

    public static double normalizeAngle(double a) {
        if (!Double.isFinite(a)) return 0.0;
        double twoPi = Math.PI * 2.0;
        double wrapped = Math.IEEEremainder(a, twoPi);
        if (wrapped <= -Math.PI) wrapped += twoPi;
        if (wrapped > Math.PI) wrapped -= twoPi;
        return wrapped;
    }

    /**
     * Predictive aiming: solve for an intercept point assuming the projectile travels
     * at constant speed and the target continues with constant velocity.
     *
     * @return double[]{ix, iy, t} where t is time-to-intercept in seconds.
     * If no solution exists, returns target position with t=0.
     */
    public static double[] interceptPoint(
            double shooterX,
            double shooterY,
            double targetX,
            double targetY,
            double targetVx,
            double targetVy,
            double projectileSpeed
    ) {
        if (projectileSpeed <= 0.0001) {
            return new double[]{targetX, targetY, 0.0};
        }

        double rx = targetX - shooterX;
        double ry = targetY - shooterY;

        // (v*v - s^2) t^2 + 2(r*v) t + (r*r) = 0
        double a = (targetVx * targetVx + targetVy * targetVy) - projectileSpeed * projectileSpeed;
        double b = 2.0 * (rx * targetVx + ry * targetVy);
        double c = (rx * rx + ry * ry);

        double t;

        if (Math.abs(a) < 1e-9) {
            if (Math.abs(b) < 1e-9) {
                return new double[]{targetX, targetY, 0.0};
            }
            t = -c / b;
            if (t < 0) return new double[]{targetX, targetY, 0.0};
        } else {
            double disc = b * b - 4.0 * a * c;
            if (disc < 0) return new double[]{targetX, targetY, 0.0};
            double sqrt = Math.sqrt(disc);

            double t1 = (-b - sqrt) / (2.0 * a);
            double t2 = (-b + sqrt) / (2.0 * a);

            t = Double.POSITIVE_INFINITY;
            if (t1 > 0 && t1 < t) t = t1;
            if (t2 > 0 && t2 < t) t = t2;

            if (!Double.isFinite(t)) {
                return new double[]{targetX, targetY, 0.0};
            }
        }

        // Keep AI stable
        t = clamp(t, 0.0, 2.2);

        return new double[]{
                targetX + targetVx * t,
                targetY + targetVy * t,
                t
        };
    }
}
