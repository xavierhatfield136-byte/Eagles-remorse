import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Deterministic replay harness for room hit resolution.
 * Builds a fixed 100-impact script per representative hull profile and checks:
 * - same script produces identical room resolution sequence across replays
 * - boundary points resolve deterministically
 * - interior points map to expected rooms at or above threshold
 */
public final class RoomHitReplayHarness {
    private static final ShipRole[] HULL_TYPES = {
            ShipRole.FRIGATE,       // small
            ShipRole.BATTLECRUISER, // capital
            ShipRole.CARRIER,       // carrier
            ShipRole.BASE            // station
    };

    private static final class ImpactPoint {
        final double x;
        final double y;
        final ShipRoomLayout.RoomId expectedRoom;
        final boolean boundary;

        ImpactPoint(double x, double y, ShipRoomLayout.RoomId expectedRoom, boolean boundary) {
            this.x = x;
            this.y = y;
            this.expectedRoom = expectedRoom;
            this.boundary = boundary;
        }
    }

    private RoomHitReplayHarness() {}

    public static void main(String[] args) {
        boolean strict = false;
        int impacts = 100;
        long seed = 424242L;
        for (String arg : args) {
            if (arg == null || arg.isBlank()) continue;
            String a = arg.trim();
            if ("--strict".equalsIgnoreCase(a)) {
                strict = true;
                continue;
            }
            if (a.startsWith("--impacts=")) {
                impacts = parseIntOrDefault(a.substring("--impacts=".length()), impacts);
                continue;
            }
            if (a.startsWith("--seed=")) {
                seed = parseLongOrDefault(a.substring("--seed=".length()), seed);
            }
        }
        impacts = Math.max(1, impacts);

        boolean deterministicAll = true;
        boolean boundaryAll = true;
        int expectedTotal = 0;
        int expectedOkTotal = 0;

        for (ShipRole role : HULL_TYPES) {
            long roleSeed = seed + (long) role.ordinal() * 9_973L;
            List<ImpactPoint> script = buildImpactScript(role, impacts, roleSeed);
            List<ShipRoomLayout.RoomId> runA = replay(role, script);
            List<ShipRoomLayout.RoomId> runB = replay(role, script);

            boolean deterministic = runA.equals(runB);
            boolean boundaryDeterministic = verifyBoundaryDeterminism(role, script);
            int expectedHits = 0;
            int expectedOk = 0;
            for (int i = 0; i < script.size(); i++) {
                ImpactPoint p = script.get(i);
                if (p.expectedRoom == null) continue;
                expectedHits++;
                if (runA.get(i) == p.expectedRoom) expectedOk++;
            }
            double expectedPct = (expectedHits <= 0) ? 100.0 : (100.0 * expectedOk / expectedHits);

            deterministicAll &= deterministic;
            boundaryAll &= boundaryDeterministic;
            expectedTotal += expectedHits;
            expectedOkTotal += expectedOk;

            System.out.println("[room-hit-replay] role=" + role.name()
                    + " profile=" + ShipRoomLayout.profileIdForRole(role)
                    + " impacts=" + script.size()
                    + " deterministic=" + passFail(deterministic)
                    + " boundary=" + passFail(boundaryDeterministic)
                    + " interior=" + expectedOk + "/" + expectedHits
                    + " (" + fmtPct(expectedPct) + ")");
        }

        double overallPct = (expectedTotal <= 0) ? 100.0 : (100.0 * expectedOkTotal / expectedTotal);
        boolean consistencyPass = overallPct >= 95.0;
        System.out.println("[room-hit-replay] overall deterministic=" + passFail(deterministicAll)
                + " boundary=" + passFail(boundaryAll)
                + " localizedConsistency=" + expectedOkTotal + "/" + expectedTotal
                + " (" + fmtPct(overallPct) + ")"
                + " threshold95=" + passFail(consistencyPass));

        if (strict && (!deterministicAll || !boundaryAll || !consistencyPass)) {
            System.exit(2);
        }
    }

    private static List<ImpactPoint> buildImpactScript(ShipRole role, int impacts, long seed) {
        List<ShipRoomLayout.RoomDef> rooms = ShipRoomLayout.profileFor(role);
        List<ImpactPoint> out = new ArrayList<>(impacts);

        // Deterministic interior samples with expected room IDs.
        for (ShipRoomLayout.RoomDef room : rooms) {
            if (room == null || room.id == null) continue;
            for (int sample = 0; sample < 2 && out.size() < impacts; sample++) {
                double[] pt = sampleInteriorPoint(room, seed + sample * 131L);
                if (pt == null) continue;
                out.add(new ImpactPoint(pt[0], pt[1], room.id, false));
            }
        }

        // Boundary script: edge midpoints.
        for (ShipRoomLayout.RoomDef room : rooms) {
            if (room == null || room.xs == null || room.ys == null) continue;
            int n = Math.min(room.xs.length, room.ys.length);
            for (int i = 0; i < n && out.size() < impacts; i++) {
                int j = (i + 1) % n;
                double mx = (room.xs[i] + room.xs[j]) * 0.5;
                double my = (room.ys[i] + room.ys[j]) * 0.5;
                out.add(new ImpactPoint(mx, my, null, true));
            }
        }

        // Boundary script: vertices.
        for (ShipRoomLayout.RoomDef room : rooms) {
            if (room == null || room.xs == null || room.ys == null) continue;
            int n = Math.min(room.xs.length, room.ys.length);
            for (int i = 0; i < n && out.size() < impacts; i++) {
                out.add(new ImpactPoint(room.xs[i], room.ys[i], null, true));
            }
        }

        // Fill remainder with deterministic random samples inside normalized hull square.
        Random rng = new Random(seed ^ 0x9E3779B97F4A7C15L);
        while (out.size() < impacts) {
            double x = -1.0 + rng.nextDouble() * 2.0;
            double y = -1.0 + rng.nextDouble() * 2.0;
            out.add(new ImpactPoint(x, y, null, false));
        }

        if (out.size() > impacts) {
            out = new ArrayList<>(out.subList(0, impacts));
        }
        return out;
    }

    private static List<ShipRoomLayout.RoomId> replay(ShipRole role, List<ImpactPoint> script) {
        List<ShipRoomLayout.RoomId> out = new ArrayList<>(script.size());
        for (ImpactPoint p : script) {
            ShipRoomLayout.RoomDef resolved = RoomHitResolver.resolve(role, p.x, p.y);
            out.add((resolved == null) ? null : resolved.id);
        }
        return out;
    }

    private static boolean verifyBoundaryDeterminism(ShipRole role, List<ImpactPoint> script) {
        for (ImpactPoint p : script) {
            if (!p.boundary) continue;
            ShipRoomLayout.RoomDef baseline = RoomHitResolver.resolve(role, p.x, p.y);
            ShipRoomLayout.RoomId expected = (baseline == null) ? null : baseline.id;
            for (int i = 0; i < 12; i++) {
                ShipRoomLayout.RoomDef rerun = RoomHitResolver.resolve(role, p.x, p.y);
                ShipRoomLayout.RoomId actual = (rerun == null) ? null : rerun.id;
                if (actual != expected) return false;
            }
        }
        return true;
    }

    private static double[] sampleInteriorPoint(ShipRoomLayout.RoomDef room, long seed) {
        if (room == null || room.xs == null || room.ys == null) return null;
        int n = Math.min(room.xs.length, room.ys.length);
        if (n < 3) return null;

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double cx = 0.0;
        double cy = 0.0;
        for (int i = 0; i < n; i++) {
            double x = room.xs[i];
            double y = room.ys[i];
            cx += x;
            cy += y;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        cx /= n;
        cy /= n;
        if (room.contains(cx, cy)) return new double[]{cx, cy};

        Random rng = new Random(seed + room.id.ordinal() * 67_541L);
        for (int i = 0; i < 64; i++) {
            double x = minX + rng.nextDouble() * Math.max(1e-9, maxX - minX);
            double y = minY + rng.nextDouble() * Math.max(1e-9, maxY - minY);
            if (room.contains(x, y)) return new double[]{x, y};
        }
        return null;
    }

    private static String passFail(boolean ok) {
        return ok ? "PASS" : "FAIL";
    }

    private static String fmtPct(double v) {
        return String.format(Locale.US, "%.1f%%", v);
    }

    private static int parseIntOrDefault(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static long parseLongOrDefault(String raw, long fallback) {
        try {
            return Long.parseLong(raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
