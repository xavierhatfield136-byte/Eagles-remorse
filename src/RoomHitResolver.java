import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Deterministic room hit resolution in ship-local normalized space.
 */
public final class RoomHitResolver {
    private static final double BOUNDARY_EPS = 1e-5;

    private RoomHitResolver() {}

    public static ShipRoomLayout.RoomDef resolve(ShipRole role, double normalizedX, double normalizedY) {
        return resolve(role, null, normalizedX, normalizedY);
    }

    public static ShipRoomLayout.RoomDef resolve(ShipRole role, Faction faction, double normalizedX, double normalizedY) {
        if (!Double.isFinite(normalizedX) || !Double.isFinite(normalizedY)) return null;
        List<ShipRoomLayout.RoomDef> rooms = ShipRoomLayout.profileFor(role, faction);
        if (rooms == null || rooms.isEmpty()) return null;

        ShipRoomLayout.RoomDef containing = null;
        int containingCount = 0;
        double containingBestScore = Double.POSITIVE_INFINITY;
        double containingBestCentroid = Double.POSITIVE_INFINITY;

        ShipRoomLayout.RoomDef nearest = null;
        double nearestBoundary = Double.POSITIVE_INFINITY;
        double nearestCentroid = Double.POSITIVE_INFINITY;

        for (ShipRoomLayout.RoomDef room : rooms) {
            if (room == null) continue;

            double boundarySq = distanceToBoundarySq(room, normalizedX, normalizedY);
            double centroidSq = room.distanceSqToCentroid(normalizedX, normalizedY);
            boolean inside = room.contains(normalizedX, normalizedY);
            boolean onBoundary = boundarySq <= BOUNDARY_EPS * BOUNDARY_EPS;

            if (inside || onBoundary) {
                containingCount++;
                double score = inside ? 0.0 : boundarySq;
                if (score < containingBestScore
                        || (Math.abs(score - containingBestScore) <= 1e-12 && centroidSq < containingBestCentroid)
                        || (Math.abs(score - containingBestScore) <= 1e-12
                        && Math.abs(centroidSq - containingBestCentroid) <= 1e-12
                        && compareRoomId(room, containing) < 0)) {
                    containing = room;
                    containingBestScore = score;
                    containingBestCentroid = centroidSq;
                }
            }

            if (boundarySq < nearestBoundary
                    || (Math.abs(boundarySq - nearestBoundary) <= 1e-12 && centroidSq < nearestCentroid)
                    || (Math.abs(boundarySq - nearestBoundary) <= 1e-12
                    && Math.abs(centroidSq - nearestCentroid) <= 1e-12
                    && compareRoomId(room, nearest) < 0)) {
                nearest = room;
                nearestBoundary = boundarySq;
                nearestCentroid = centroidSq;
            }
        }

        if (containingCount > 0 && containing != null) return containing;
        return nearest;
    }

    public static double distanceToBoundarySq(ShipRoomLayout.RoomDef room, double normalizedX, double normalizedY) {
        if (room == null) return Double.POSITIVE_INFINITY;
        int n = Math.min(room.xs.length, room.ys.length);
        if (n < 2) return Double.POSITIVE_INFINITY;
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double dsq = pointSegmentDistanceSq(
                    normalizedX, normalizedY,
                    room.xs[j], room.ys[j],
                    room.xs[i], room.ys[i]
            );
            if (dsq < best) best = dsq;
        }
        return best;
    }

    public static boolean roomOverlapsAabb(ShipRoomLayout.RoomDef room,
                                           double minX,
                                           double minY,
                                           double maxX,
                                           double maxY) {
        if (room == null) return false;
        if (minX > maxX || minY > maxY) return false;
        int n = Math.min(room.xs.length, room.ys.length);
        if (n < 3) return false;

        // Any polygon vertex inside the AABB.
        for (int i = 0; i < n; i++) {
            double x = room.xs[i];
            double y = room.ys[i];
            if (x >= minX && x <= maxX && y >= minY && y <= maxY) return true;
        }

        // Any AABB corner inside polygon.
        if (room.contains(minX, minY)) return true;
        if (room.contains(minX, maxY)) return true;
        if (room.contains(maxX, minY)) return true;
        if (room.contains(maxX, maxY)) return true;

        // Edge intersections.
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double ax = room.xs[j];
            double ay = room.ys[j];
            double bx = room.xs[i];
            double by = room.ys[i];
            if (segmentIntersectsAabb(ax, ay, bx, by, minX, minY, maxX, maxY)) return true;
        }

        return false;
    }

    public static List<ShipRoomLayout.RoomDef> roomsIntersectingSegment(ShipRole role,
                                                                        Faction faction,
                                                                        double startX,
                                                                        double startY,
                                                                        double endX,
                                                                        double endY,
                                                                        double halfWidth) {
        if (!Double.isFinite(startX) || !Double.isFinite(startY)
                || !Double.isFinite(endX) || !Double.isFinite(endY)) {
            return List.of();
        }
        List<ShipRoomLayout.RoomDef> rooms = ShipRoomLayout.profileFor(role, faction);
        if (rooms == null || rooms.isEmpty()) return List.of();

        double hw = Math.max(0.0, halfWidth);
        double minX = Math.min(startX, endX) - hw;
        double maxX = Math.max(startX, endX) + hw;
        double minY = Math.min(startY, endY) - hw;
        double maxY = Math.max(startY, endY) + hw;
        ArrayList<SegmentRoomHit> hits = new ArrayList<>();

        for (ShipRoomLayout.RoomDef room : rooms) {
            if (room == null || room.id == null) continue;
            if (!roomOverlapsAabb(room, minX, minY, maxX, maxY)) continue;
            double t = segmentEntryParam(room, startX, startY, endX, endY, hw);
            if (Double.isFinite(t)) hits.add(new SegmentRoomHit(room, t));
        }

        if (hits.isEmpty()) return List.of();
        hits.sort(Comparator
                .comparingDouble((SegmentRoomHit hit) -> hit.t)
                .thenComparingInt(hit -> hit.room.id.ordinal()));

        ArrayList<ShipRoomLayout.RoomDef> out = new ArrayList<>(hits.size());
        ShipRoomLayout.RoomId lastId = null;
        for (SegmentRoomHit hit : hits) {
            if (hit.room.id == lastId) continue;
            out.add(hit.room);
            lastId = hit.room.id;
        }
        return out;
    }

    public static List<ShipRoomLayout.RoomDef> roomsWithinRadius(ShipRole role,
                                                                 Faction faction,
                                                                 double centerX,
                                                                 double centerY,
                                                                 double radius) {
        if (!Double.isFinite(centerX) || !Double.isFinite(centerY)) return List.of();
        List<ShipRoomLayout.RoomDef> rooms = ShipRoomLayout.profileFor(role, faction);
        if (rooms == null || rooms.isEmpty()) return List.of();

        double rr = Math.max(0.0, radius);
        double minX = centerX - rr;
        double maxX = centerX + rr;
        double minY = centerY - rr;
        double maxY = centerY + rr;
        double rrSq = rr * rr;
        ArrayList<ShipRoomLayout.RoomDef> out = new ArrayList<>();

        for (ShipRoomLayout.RoomDef room : rooms) {
            if (room == null || room.id == null) continue;
            if (!roomOverlapsAabb(room, minX, minY, maxX, maxY)) continue;
            if (room.contains(centerX, centerY) || room.distanceSqToBoundary(centerX, centerY) <= rrSq) {
                out.add(room);
            }
        }

        out.sort(Comparator
                .comparingDouble((ShipRoomLayout.RoomDef room) -> room.distanceSqToCentroid(centerX, centerY))
                .thenComparingInt(room -> room.id.ordinal()));
        return out;
    }

    private static int compareRoomId(ShipRoomLayout.RoomDef a, ShipRoomLayout.RoomDef b) {
        if (a == b) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        int ao = (a.id == null) ? Integer.MAX_VALUE : a.id.ordinal();
        int bo = (b.id == null) ? Integer.MAX_VALUE : b.id.ordinal();
        return Integer.compare(ao, bo);
    }

    private static boolean segmentIntersectsAabb(double ax, double ay, double bx, double by,
                                                 double minX, double minY, double maxX, double maxY) {
        if (pointInsideAabb(ax, ay, minX, minY, maxX, maxY)) return true;
        if (pointInsideAabb(bx, by, minX, minY, maxX, maxY)) return true;

        // AABB edges.
        return segmentsIntersect(ax, ay, bx, by, minX, minY, maxX, minY)
                || segmentsIntersect(ax, ay, bx, by, maxX, minY, maxX, maxY)
                || segmentsIntersect(ax, ay, bx, by, maxX, maxY, minX, maxY)
                || segmentsIntersect(ax, ay, bx, by, minX, maxY, minX, minY);
    }

    private static boolean pointInsideAabb(double x, double y,
                                           double minX, double minY,
                                           double maxX, double maxY) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY;
    }

    private static boolean segmentsIntersect(double ax, double ay, double bx, double by,
                                             double cx, double cy, double dx, double dy) {
        double o1 = orientation(ax, ay, bx, by, cx, cy);
        double o2 = orientation(ax, ay, bx, by, dx, dy);
        double o3 = orientation(cx, cy, dx, dy, ax, ay);
        double o4 = orientation(cx, cy, dx, dy, bx, by);

        if (o1 * o2 < 0.0 && o3 * o4 < 0.0) return true;

        // Collinear edge-touch cases.
        return (Math.abs(o1) <= 1e-12 && onSegment(ax, ay, bx, by, cx, cy))
                || (Math.abs(o2) <= 1e-12 && onSegment(ax, ay, bx, by, dx, dy))
                || (Math.abs(o3) <= 1e-12 && onSegment(cx, cy, dx, dy, ax, ay))
                || (Math.abs(o4) <= 1e-12 && onSegment(cx, cy, dx, dy, bx, by));
    }

    private static double orientation(double ax, double ay, double bx, double by, double px, double py) {
        return (bx - ax) * (py - ay) - (by - ay) * (px - ax);
    }

    private static boolean onSegment(double ax, double ay, double bx, double by, double px, double py) {
        return px >= Math.min(ax, bx) - 1e-12 && px <= Math.max(ax, bx) + 1e-12
                && py >= Math.min(ay, by) - 1e-12 && py <= Math.max(ay, by) + 1e-12;
    }

    private static double pointSegmentDistanceSq(double px, double py,
                                                 double ax, double ay,
                                                 double bx, double by) {
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

    private static double segmentEntryParam(ShipRoomLayout.RoomDef room,
                                            double startX,
                                            double startY,
                                            double endX,
                                            double endY,
                                            double halfWidth) {
        if (room == null) return Double.NaN;
        double rrSq = halfWidth * halfWidth;
        int steps = 56;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double x = startX + (endX - startX) * t;
            double y = startY + (endY - startY) * t;
            if (room.contains(x, y) || room.distanceSqToBoundary(x, y) <= rrSq) {
                return t;
            }
        }
        return Double.NaN;
    }

    private static final class SegmentRoomHit {
        final ShipRoomLayout.RoomDef room;
        final double t;

        SegmentRoomHit(ShipRoomLayout.RoomDef room, double t) {
            this.room = room;
            this.t = t;
        }
    }
}
