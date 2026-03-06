import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Canonical 2D room layout definitions used by localized damage and x-ray UI.
 * Room polygons are defined in normalized ship-local space [-1..1].
 */
public final class ShipRoomLayout {
    private ShipRoomLayout() {}

    public enum RoomId {
        BRIDGE,
        SENSORS,
        MAIN_WEAPON,
        MISSILE_LAUNCHERS,
        REACTOR,
        POWER_CONDUITS,
        INTEGRITY_FIELD,
        ENGINES,
        WARP_DRIVE,
        MAGAZINES
    }

    public static final class RoomDef {
        public final RoomId id;
        public final String label;
        public final double[] xs;
        public final double[] ys;
        public final Ship.InternalSystem primarySystem;
        public final double hpWeight;
        public final boolean critical;
        public final RoomId[] neighbors;
        private final double centroidX;
        private final double centroidY;

        private RoomDef(RoomId id, String label, double[] xs, double[] ys,
                        Ship.InternalSystem primarySystem, double hpWeight, boolean critical,
                        RoomId... neighbors) {
            this.id = id;
            this.label = label;
            this.xs = xs;
            this.ys = ys;
            this.primarySystem = primarySystem;
            this.hpWeight = hpWeight;
            this.critical = critical;
            this.neighbors = (neighbors == null) ? new RoomId[0] : neighbors.clone();

            double sx = 0.0;
            double sy = 0.0;
            int n = Math.min(xs.length, ys.length);
            for (int i = 0; i < n; i++) {
                sx += xs[i];
                sy += ys[i];
            }
            if (n <= 0) {
                this.centroidX = 0.0;
                this.centroidY = 0.0;
            } else {
                this.centroidX = sx / n;
                this.centroidY = sy / n;
            }
        }

        public boolean contains(double x, double y) {
            return pointInPolygon(xs, ys, x, y);
        }

        public double distanceSqToCentroid(double x, double y) {
            double dx = x - centroidX;
            double dy = y - centroidY;
            return dx * dx + dy * dy;
        }
    }

    private static final Map<String, Profile> PROFILES = new HashMap<>();

    public static List<RoomDef> profileFor(ShipRole role) {
        return profile(role).rooms;
    }

    public static RoomDef roomForHit(ShipRole role, double normalizedX, double normalizedY) {
        if (!Double.isFinite(normalizedX) || !Double.isFinite(normalizedY)) return null;
        Profile p = profile(role);
        RoomDef nearest = null;
        double bestD2 = Double.POSITIVE_INFINITY;
        for (RoomDef r : p.rooms) {
            if (r.contains(normalizedX, normalizedY)) return r;
            double d2 = r.distanceSqToCentroid(normalizedX, normalizedY);
            if (d2 < bestD2) {
                bestD2 = d2;
                nearest = r;
            }
        }
        return nearest;
    }

    public static RoomDef roomForId(ShipRole role, RoomId id) {
        if (id == null) return null;
        return profile(role).byId.get(id);
    }

    private static Profile profile(ShipRole role) {
        String key = profileKey(role);
        Profile cached = PROFILES.get(key);
        if (cached != null) return cached;

        Profile built = switch (key) {
            case "small" -> buildSmallProfile();
            case "carrier" -> buildCarrierProfile();
            case "station" -> buildStationProfile();
            default -> buildCapitalProfile();
        };
        PROFILES.put(key, built);
        return built;
    }

    private static String profileKey(ShipRole role) {
        if (role == null) return "capital";
        return switch (role) {
            case FIGHTER, BOMBER, DRONE, PD_CRAFT, PICKET, PATROL, STEALTH_SHIP,
                 FRIGATE, MISSILE_BOAT, CIWS_CORVETTE -> "small";
            case CARRIER, DRONE_CARRIER, TRANSPORT, HAULER, MINER -> "carrier";
            case BASE, STATIC_TURRET -> "station";
            default -> "capital";
        };
    }

    private static Profile buildSmallProfile() {
        List<RoomDef> rooms = new ArrayList<>();
        rooms.add(rect(RoomId.BRIDGE, "BRIDGE", 0.48, 0.90, -0.16, 0.16,
                Ship.InternalSystem.BRIDGE, 0.85, true, RoomId.SENSORS, RoomId.MAIN_WEAPON));
        rooms.add(rect(RoomId.SENSORS, "SENSORS", 0.30, 0.72, -0.56, -0.20,
                Ship.InternalSystem.SENSORS, 0.72, false, RoomId.BRIDGE, RoomId.MAIN_WEAPON));
        rooms.add(rect(RoomId.MAIN_WEAPON, "MAIN WEAPON", 0.08, 0.58, -0.24, 0.24,
                Ship.InternalSystem.WEAPONS, 0.95, false, RoomId.BRIDGE, RoomId.REACTOR, RoomId.MISSILE_LAUNCHERS));
        rooms.add(rect(RoomId.MISSILE_LAUNCHERS, "MISSILES", -0.02, 0.38, 0.28, 0.58,
                Ship.InternalSystem.WEAPONS, 0.72, false, RoomId.MAIN_WEAPON, RoomId.MAGAZINES));
        rooms.add(rect(RoomId.REACTOR, "REACTOR", -0.12, 0.24, -0.20, 0.20,
                Ship.InternalSystem.REACTOR_CORE, 1.05, true, RoomId.POWER_CONDUITS, RoomId.INTEGRITY_FIELD));
        rooms.add(rect(RoomId.POWER_CONDUITS, "POWER", -0.30, 0.08, -0.18, 0.18,
                Ship.InternalSystem.REACTOR_CORE, 0.88, false, RoomId.REACTOR, RoomId.ENGINES, RoomId.WARP_DRIVE));
        rooms.add(rect(RoomId.INTEGRITY_FIELD, "INTEGRITY", -0.18, 0.16, -0.52, -0.24,
                Ship.InternalSystem.SHIELDS, 0.75, false, RoomId.REACTOR, RoomId.SENSORS));
        rooms.add(rect(RoomId.ENGINES, "ENGINES", -0.88, -0.28, -0.30, 0.30,
                Ship.InternalSystem.ENGINES, 1.12, false, RoomId.WARP_DRIVE, RoomId.POWER_CONDUITS));
        rooms.add(rect(RoomId.WARP_DRIVE, "WARP", -0.80, -0.34, -0.14, 0.14,
                Ship.InternalSystem.WARP_ENGINES, 0.86, false, RoomId.ENGINES, RoomId.POWER_CONDUITS));
        rooms.add(rect(RoomId.MAGAZINES, "MAGAZINES", -0.52, -0.08, 0.26, 0.58,
                Ship.InternalSystem.MAGAZINES, 0.80, true, RoomId.MISSILE_LAUNCHERS, RoomId.REACTOR));
        return new Profile(rooms);
    }

    private static Profile buildCapitalProfile() {
        List<RoomDef> rooms = new ArrayList<>();
        rooms.add(rect(RoomId.BRIDGE, "BRIDGE", 0.56, 0.96, -0.18, 0.18,
                Ship.InternalSystem.BRIDGE, 0.95, true, RoomId.SENSORS, RoomId.MAIN_WEAPON));
        rooms.add(rect(RoomId.SENSORS, "SENSORS", 0.34, 0.78, -0.58, -0.20,
                Ship.InternalSystem.SENSORS, 0.78, false, RoomId.BRIDGE, RoomId.INTEGRITY_FIELD));
        rooms.add(rect(RoomId.MAIN_WEAPON, "MAIN WEAPON", 0.14, 0.62, -0.25, 0.25,
                Ship.InternalSystem.WEAPONS, 1.08, false, RoomId.BRIDGE, RoomId.REACTOR, RoomId.MISSILE_LAUNCHERS));
        rooms.add(rect(RoomId.MISSILE_LAUNCHERS, "MISSILES", -0.06, 0.42, 0.30, 0.62,
                Ship.InternalSystem.WEAPONS, 0.86, false, RoomId.MAIN_WEAPON, RoomId.MAGAZINES));
        rooms.add(rect(RoomId.REACTOR, "REACTOR", -0.10, 0.26, -0.22, 0.22,
                Ship.InternalSystem.REACTOR_CORE, 1.20, true, RoomId.POWER_CONDUITS, RoomId.INTEGRITY_FIELD, RoomId.MAGAZINES));
        rooms.add(rect(RoomId.POWER_CONDUITS, "POWER", -0.34, 0.08, -0.18, 0.18,
                Ship.InternalSystem.REACTOR_CORE, 0.95, false, RoomId.REACTOR, RoomId.ENGINES, RoomId.WARP_DRIVE));
        rooms.add(rect(RoomId.INTEGRITY_FIELD, "INTEGRITY", -0.24, 0.18, -0.56, -0.26,
                Ship.InternalSystem.SHIELDS, 0.92, false, RoomId.REACTOR, RoomId.SENSORS));
        rooms.add(rect(RoomId.ENGINES, "ENGINES", -0.92, -0.30, -0.34, 0.34,
                Ship.InternalSystem.ENGINES, 1.28, false, RoomId.WARP_DRIVE, RoomId.POWER_CONDUITS));
        rooms.add(rect(RoomId.WARP_DRIVE, "WARP", -0.84, -0.36, -0.15, 0.15,
                Ship.InternalSystem.WARP_ENGINES, 0.95, false, RoomId.ENGINES, RoomId.POWER_CONDUITS));
        rooms.add(rect(RoomId.MAGAZINES, "MAGAZINES", -0.54, -0.06, 0.30, 0.62,
                Ship.InternalSystem.MAGAZINES, 0.92, true, RoomId.MISSILE_LAUNCHERS, RoomId.REACTOR));
        return new Profile(rooms);
    }

    private static Profile buildCarrierProfile() {
        List<RoomDef> rooms = new ArrayList<>();
        rooms.add(rect(RoomId.BRIDGE, "BRIDGE", 0.42, 0.82, -0.42, -0.10,
                Ship.InternalSystem.BRIDGE, 0.92, true, RoomId.SENSORS, RoomId.MAIN_WEAPON));
        rooms.add(rect(RoomId.SENSORS, "SENSORS", 0.20, 0.58, -0.66, -0.36,
                Ship.InternalSystem.SENSORS, 0.76, false, RoomId.BRIDGE, RoomId.INTEGRITY_FIELD));
        rooms.add(rect(RoomId.MAIN_WEAPON, "MAIN WEAPON", 0.00, 0.52, -0.18, 0.18,
                Ship.InternalSystem.WEAPONS, 0.92, false, RoomId.BRIDGE, RoomId.REACTOR));
        rooms.add(rect(RoomId.MISSILE_LAUNCHERS, "MISSILES", -0.12, 0.28, 0.30, 0.62,
                Ship.InternalSystem.WEAPONS, 0.80, false, RoomId.MAIN_WEAPON, RoomId.MAGAZINES));
        rooms.add(rect(RoomId.REACTOR, "REACTOR", -0.08, 0.20, -0.22, 0.22,
                Ship.InternalSystem.REACTOR_CORE, 1.15, true, RoomId.POWER_CONDUITS, RoomId.INTEGRITY_FIELD));
        rooms.add(rect(RoomId.POWER_CONDUITS, "POWER", -0.30, 0.02, -0.18, 0.18,
                Ship.InternalSystem.REACTOR_CORE, 0.90, false, RoomId.REACTOR, RoomId.ENGINES, RoomId.WARP_DRIVE));
        rooms.add(rect(RoomId.INTEGRITY_FIELD, "INTEGRITY", -0.22, 0.14, -0.52, -0.24,
                Ship.InternalSystem.SHIELDS, 0.90, false, RoomId.REACTOR, RoomId.SENSORS));
        rooms.add(rect(RoomId.ENGINES, "ENGINES", -0.92, -0.30, -0.38, 0.38,
                Ship.InternalSystem.ENGINES, 1.24, false, RoomId.WARP_DRIVE, RoomId.POWER_CONDUITS));
        rooms.add(rect(RoomId.WARP_DRIVE, "WARP", -0.86, -0.38, -0.16, 0.16,
                Ship.InternalSystem.WARP_ENGINES, 0.90, false, RoomId.ENGINES, RoomId.POWER_CONDUITS));
        rooms.add(rect(RoomId.MAGAZINES, "MAGAZINES", -0.54, -0.12, 0.28, 0.62,
                Ship.InternalSystem.MAGAZINES, 0.90, true, RoomId.MISSILE_LAUNCHERS, RoomId.REACTOR));
        return new Profile(rooms);
    }

    private static Profile buildStationProfile() {
        List<RoomDef> rooms = new ArrayList<>();
        rooms.add(rect(RoomId.BRIDGE, "COMMAND CORE", 0.12, 0.56, -0.20, 0.20,
                Ship.InternalSystem.BRIDGE, 1.00, true, RoomId.REACTOR, RoomId.SENSORS));
        rooms.add(rect(RoomId.SENSORS, "SENSORS", 0.28, 0.70, -0.56, -0.24,
                Ship.InternalSystem.SENSORS, 0.90, false, RoomId.BRIDGE, RoomId.INTEGRITY_FIELD));
        rooms.add(rect(RoomId.MAIN_WEAPON, "DEFENSE GRID", -0.18, 0.20, -0.20, 0.20,
                Ship.InternalSystem.WEAPONS, 1.00, false, RoomId.BRIDGE, RoomId.REACTOR));
        rooms.add(rect(RoomId.MISSILE_LAUNCHERS, "LAUNCH BAYS", -0.52, -0.14, 0.26, 0.60,
                Ship.InternalSystem.WEAPONS, 0.85, false, RoomId.MAGAZINES, RoomId.MAIN_WEAPON));
        rooms.add(rect(RoomId.REACTOR, "REACTOR", -0.06, 0.24, -0.24, 0.24,
                Ship.InternalSystem.REACTOR_CORE, 1.30, true, RoomId.POWER_CONDUITS, RoomId.INTEGRITY_FIELD));
        rooms.add(rect(RoomId.POWER_CONDUITS, "POWER", -0.36, 0.00, -0.18, 0.18,
                Ship.InternalSystem.REACTOR_CORE, 1.00, false, RoomId.REACTOR, RoomId.ENGINES, RoomId.WARP_DRIVE));
        rooms.add(rect(RoomId.INTEGRITY_FIELD, "INTEGRITY", -0.20, 0.18, -0.60, -0.28,
                Ship.InternalSystem.SHIELDS, 1.00, false, RoomId.REACTOR, RoomId.SENSORS));
        rooms.add(rect(RoomId.ENGINES, "THRUSTER GRID", -0.92, -0.34, -0.32, 0.32,
                Ship.InternalSystem.ENGINES, 1.10, false, RoomId.WARP_DRIVE, RoomId.POWER_CONDUITS));
        rooms.add(rect(RoomId.WARP_DRIVE, "JUMP CORE", -0.82, -0.40, -0.14, 0.14,
                Ship.InternalSystem.WARP_ENGINES, 0.95, false, RoomId.ENGINES, RoomId.POWER_CONDUITS));
        rooms.add(rect(RoomId.MAGAZINES, "MAGAZINES", -0.66, -0.22, 0.26, 0.60,
                Ship.InternalSystem.MAGAZINES, 0.95, true, RoomId.MISSILE_LAUNCHERS, RoomId.REACTOR));
        return new Profile(rooms);
    }

    private static RoomDef rect(RoomId id, String label,
                                double x0, double x1, double y0, double y1,
                                Ship.InternalSystem system, double hpWeight, boolean critical,
                                RoomId... neighbors) {
        double[] xs = new double[]{x0, x1, x1, x0};
        double[] ys = new double[]{y0, y0, y1, y1};
        return new RoomDef(id, label, xs, ys, system, hpWeight, critical, neighbors);
    }

    private static boolean pointInPolygon(double[] xs, double[] ys, double px, double py) {
        int n = Math.min(xs.length, ys.length);
        if (n < 3) return false;
        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = xs[i], yi = ys[i];
            double xj = xs[j], yj = ys[j];
            boolean intersect = ((yi > py) != (yj > py))
                    && (px < (xj - xi) * (py - yi) / ((yj - yi) + 1e-12) + xi);
            if (intersect) inside = !inside;
        }
        return inside;
    }

    private static final class Profile {
        final List<RoomDef> rooms;
        final EnumMap<RoomId, RoomDef> byId = new EnumMap<>(RoomId.class);

        Profile(List<RoomDef> src) {
            List<RoomDef> list = new ArrayList<>(src);
            for (RoomDef d : list) byId.put(d.id, d);
            rooms = Collections.unmodifiableList(list);
        }
    }
}
