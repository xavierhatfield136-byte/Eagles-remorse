import java.util.HashMap;
import java.util.Map;
import java.awt.Polygon;

/**
 * Shared hull-shape geometry used by collision and damage systems.
 * Shapes are defined in ship-local coordinates and mirror the active 2D renderer silhouettes.
 */
public final class HullGeometry {
    private static final Map<String, HullProfile> PROFILE_CACHE = new HashMap<>();

    private HullGeometry() {}

    public static boolean projectileIntersectsShip(Projectile projectile, Ship ship) {
        if (projectile == null || ship == null) return false;
        if (!Double.isFinite(projectile.x) || !Double.isFinite(projectile.y)) return false;
        HullProfile profile = profileFor(ship);
        LocalPoint local = worldToLocal(ship, projectile.x, projectile.y);
        double pr = Math.max(0.0, projectile.radius);
        return profile.intersectsCircle(local.x, local.y, pr);
    }

    /**
     * Segment-vs-hull capsule test in world space.
     * The segment is sampled in ship-local space and checked as a sequence of
     * small circles (capsule approximation).
     */
    public static boolean segmentIntersectsShip(double ax, double ay, double bx, double by, double halfWidth, Ship ship) {
        if (ship == null) return false;
        if (!Double.isFinite(ax) || !Double.isFinite(ay) || !Double.isFinite(bx) || !Double.isFinite(by)) return false;

        HullProfile profile = profileFor(ship);
        LocalPoint a = worldToLocal(ship, ax, ay);
        LocalPoint b = worldToLocal(ship, bx, by);
        double hw = Math.max(0.0, halfWidth);

        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double len = Math.hypot(dx, dy);
        int steps = (int) Math.ceil(len / Math.max(3.0, hw * 1.2 + 1.0));
        steps = Math.max(6, Math.min(96, steps));

        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double px = a.x + dx * t;
            double py = a.y + dy * t;
            if (profile.intersectsCircle(px, py, hw)) return true;
        }
        return false;
    }

    public static double broadPhaseRadius(Ship ship) {
        if (ship == null) return 0.0;
        return profileFor(ship).maxRadius;
    }

    public static ImpactSample sampleImpact(Ship ship, double worldX, double worldY) {
        return sampleImpact(ship, worldX, worldY, false);
    }

    public static ImpactSample sampleImpact(Ship ship, double worldX, double worldY, boolean snapToHull) {
        if (ship == null) return null;
        if (!Double.isFinite(worldX) || !Double.isFinite(worldY)) return null;

        HullProfile profile = profileFor(ship);
        LocalPoint local = worldToLocal(ship, worldX, worldY);
        double px = local.x;
        double py = local.y;
        boolean onHull = profile.contains(px, py);

        if (snapToHull && !onHull) {
            double lo = 0.0;
            double hi = 1.0;
            for (int i = 0; i < 22; i++) {
                double t = 0.5 * (lo + hi);
                double tx = px * t;
                double ty = py * t;
                if (profile.contains(tx, ty)) {
                    lo = t;
                } else {
                    hi = t;
                }
            }
            px *= lo;
            py *= lo;
            onHull = profile.contains(px, py);
        }

        double ex = Math.max(1.0, profile.extentX);
        double ey = Math.max(1.0, profile.extentY);
        double nx = MathUtil.clamp(px / ex, -1.0, 1.0);
        double ny = MathUtil.clamp(py / ey, -1.0, 1.0);
        return new ImpactSample(px, py, nx, ny, onHull);
    }

    public static LocalPoint worldToLocal(Ship ship, double worldX, double worldY) {
        double dx = worldX - ship.x;
        double dy = worldY - ship.y;
        double c = Math.cos(ship.angle);
        double s = Math.sin(ship.angle);

        // Rotate by -angle to enter ship-local space.
        double lx = dx * c + dy * s;
        double ly = -dx * s + dy * c;
        return new LocalPoint(lx, ly);
    }

    public static double[] localToWorld(Ship ship, double localX, double localY) {
        if (ship == null) return new double[]{localX, localY};
        double c = Math.cos(ship.angle);
        double s = Math.sin(ship.angle);
        return new double[]{
                ship.x + localX * c - localY * s,
                ship.y + localX * s + localY * c
        };
    }

    public static double roleVisualScale(ShipRole role) {
        if (role == null) return 1.0;
        return switch (role) {
            case FIGHTER -> 0.78;
            case BOMBER -> 0.84;
            default -> 1.0;
        };
    }

    private static HullProfile profileFor(Ship ship) {
        ShipRole role = (ship.role == null) ? ShipRole.FRIGATE : ship.role;
        long r = Math.round(Math.max(8.0, ship.radius) * 1000.0);
        String key = role.name() + ":" + ship.faction + ":" + r;
        HullProfile cached = PROFILE_CACHE.get(key);
        if (cached != null) return cached;

        HullProfile created = buildProfile(role, ship.faction, Math.max(8.0, ship.radius));
        PROFILE_CACHE.put(key, created);
        return created;
    }

    private static HullProfile buildProfile(ShipRole role, Faction faction, double radius) {
        double scale = roleVisualScale(role);
        int r = (int) Math.round(radius);

        if (role == ShipRole.BASE) {
            return HullProfile.circular(r * scale);
        }

        Polygon p = ShipHullSilhouette.hullPolygon(role, r, faction);
        if (p == null || p.npoints < 3) {
            return HullProfile.circular(Math.max(1.0, r * scale));
        }

        double[] xs = new double[p.npoints];
        double[] ys = new double[p.npoints];
        for (int i = 0; i < p.npoints; i++) {
            xs[i] = p.xpoints[i] * scale;
            ys[i] = p.ypoints[i] * scale;
        }
        return HullProfile.polygon(xs, ys);
    }

    public static final class LocalPoint {
        public final double x;
        public final double y;

        private LocalPoint(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    public static final class ImpactSample {
        public final double localX;
        public final double localY;
        public final double normalizedX;
        public final double normalizedY;
        public final boolean onHull;

        private ImpactSample(double localX, double localY, double normalizedX, double normalizedY, boolean onHull) {
            this.localX = localX;
            this.localY = localY;
            this.normalizedX = normalizedX;
            this.normalizedY = normalizedY;
            this.onHull = onHull;
        }
    }

    private static final class HullProfile {
        final boolean circular;
        final double circleRadius;
        final double[] xs;
        final double[] ys;
        final int n;
        final double minX;
        final double maxX;
        final double minY;
        final double maxY;
        final double extentX;
        final double extentY;
        final double maxRadius;

        private HullProfile(boolean circular, double circleRadius, double[] xs, double[] ys) {
            this.circular = circular;
            this.circleRadius = circleRadius;
            this.xs = xs;
            this.ys = ys;
            this.n = (xs == null || ys == null) ? 0 : Math.min(xs.length, ys.length);

            if (circular) {
                minX = -circleRadius;
                maxX = circleRadius;
                minY = -circleRadius;
                maxY = circleRadius;
            } else if (n > 0) {
                double loX = xs[0];
                double hiX = xs[0];
                double loY = ys[0];
                double hiY = ys[0];
                for (int i = 1; i < n; i++) {
                    loX = Math.min(loX, xs[i]);
                    hiX = Math.max(hiX, xs[i]);
                    loY = Math.min(loY, ys[i]);
                    hiY = Math.max(hiY, ys[i]);
                }
                minX = loX;
                maxX = hiX;
                minY = loY;
                maxY = hiY;
            } else {
                minX = -1.0;
                maxX = 1.0;
                minY = -1.0;
                maxY = 1.0;
            }

            extentX = Math.max(Math.abs(minX), Math.abs(maxX));
            extentY = Math.max(Math.abs(minY), Math.abs(maxY));
            if (circular) {
                maxRadius = circleRadius;
            } else {
                double mr = 1.0;
                for (int i = 0; i < n; i++) {
                    mr = Math.max(mr, Math.hypot(xs[i], ys[i]));
                }
                maxRadius = mr;
            }
        }

        static HullProfile circular(double radius) {
            return new HullProfile(true, Math.max(1.0, radius), null, null);
        }

        static HullProfile polygon(double[] xs, double[] ys) {
            return new HullProfile(false, 0.0, xs, ys);
        }

        boolean contains(double px, double py) {
            if (circular) {
                double rr = circleRadius * circleRadius;
                return px * px + py * py <= rr;
            }
            return pointInPolygon(px, py);
        }

        boolean intersectsCircle(double cx, double cy, double radius) {
            double rr = radius * radius;
            if (circular) {
                double r = circleRadius + radius;
                return cx * cx + cy * cy <= r * r;
            }
            if (pointInPolygon(cx, cy)) return true;
            for (int i = 0, j = n - 1; i < n; j = i++) {
                double dsq = pointSegmentDistanceSq(cx, cy, xs[j], ys[j], xs[i], ys[i]);
                if (dsq <= rr) return true;
            }
            return false;
        }

        private boolean pointInPolygon(double px, double py) {
            boolean inside = false;
            for (int i = 0, j = n - 1; i < n; j = i++) {
                double xi = xs[i];
                double yi = ys[i];
                double xj = xs[j];
                double yj = ys[j];
                boolean crosses = ((yi > py) != (yj > py))
                        && (px < (xj - xi) * (py - yi) / ((yj - yi) + 1e-12) + xi);
                if (crosses) inside = !inside;
            }
            return inside;
        }

        private static double pointSegmentDistanceSq(double px, double py, double ax, double ay, double bx, double by) {
            double abx = bx - ax;
            double aby = by - ay;
            double apx = px - ax;
            double apy = py - ay;
            double ab2 = abx * abx + aby * aby;
            if (ab2 <= 1e-12) {
                double dx = px - ax;
                double dy = py - ay;
                return dx * dx + dy * dy;
            }
            double t = (apx * abx + apy * aby) / ab2;
            t = Math.max(0.0, Math.min(1.0, t));
            double cx = ax + abx * t;
            double cy = ay + aby * t;
            double dx = px - cx;
            double dy = py - cy;
            return dx * dx + dy * dy;
        }
    }
}
