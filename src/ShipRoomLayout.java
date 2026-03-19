import java.awt.Polygon;
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
        BOW,
        BOW_ARMOR,
        DORSAL_ARMOR,
        VENTRAL_ARMOR,
        AFT_ARMOR,
        CREW_QUARTERS,
        PORT_BATTERY,
        STARBOARD_BATTERY,
        MAIN_WEAPON,
        MISSILE_LAUNCHERS,
        REACTOR,
        POWER_CONDUITS,
        PORT_POWER,
        STARBOARD_POWER,
        INTEGRITY_FIELD,
        SERVICE_BAY,
        CARGO_BAY,
        MAGAZINES,
        ENGINES,
        PORT_ENGINES,
        STARBOARD_ENGINES,
        WARP_DRIVE,
        AFT_SPINE
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

    public static final class VisualCell {
        public final RoomId roomId;
        public final double[] xs;
        public final double[] ys;
        public final boolean labelAnchor;

        private VisualCell(RoomId roomId, double[] xs, double[] ys, boolean labelAnchor) {
            this.roomId = roomId;
            this.xs = xs;
            this.ys = ys;
            this.labelAnchor = labelAnchor;
        }
    }

    private static final Map<String, Profile> PROFILES = new HashMap<>();
    private static final Map<String, List<VisualCell>> VISUAL_CACHE = new HashMap<>();

    public static List<RoomDef> profileFor(ShipRole role) {
        return profileFor(role, null);
    }

    public static List<RoomDef> profileFor(ShipRole role, Faction faction) {
        return profile(role, faction).rooms;
    }

    public static String profileIdForRole(ShipRole role) {
        return profileKey(role);
    }

    public static List<VisualCell> visualCellsFor(ShipRole role) {
        return visualCellsFor(role, null);
    }

    public static List<VisualCell> visualCellsFor(ShipRole role, Faction faction) {
        String key = layoutKey(role, faction);
        List<VisualCell> cached = VISUAL_CACHE.get(key);
        if (cached != null) return cached;

        Profile profile = profile(role, faction);
        List<VisualCell> built = buildVisualCells(role, faction, profile.rooms);
        VISUAL_CACHE.put(key, built);
        return built;
    }

    public static RoomDef roomForHit(ShipRole role, double normalizedX, double normalizedY) {
        return roomForHit(role, null, normalizedX, normalizedY);
    }

    public static RoomDef roomForHit(ShipRole role, Faction faction, double normalizedX, double normalizedY) {
        return RoomHitResolver.resolve(role, faction, normalizedX, normalizedY);
    }

    public static RoomDef roomForId(ShipRole role, RoomId id) {
        return roomForId(role, null, id);
    }

    public static RoomDef roomForId(ShipRole role, Faction faction, RoomId id) {
        if (id == null) return null;
        return profile(role, faction).byId.get(id);
    }

    public static String displayLabel(RoomId roomId) {
        if (roomId == null) return "UNKNOWN";
        return switch (roomId) {
            case BRIDGE -> "BRIDGE";
            case SENSORS -> "SENSORS";
            case BOW -> "BOW SECTION";
            case BOW_ARMOR -> "BOW ARMOR";
            case DORSAL_ARMOR -> "DORSAL ARMOR";
            case VENTRAL_ARMOR -> "VENTRAL ARMOR";
            case AFT_ARMOR -> "AFT ARMOR";
            case CREW_QUARTERS -> "CREW QUARTERS";
            case PORT_BATTERY -> "PORT BATTERY";
            case STARBOARD_BATTERY -> "STARBOARD BATTERY";
            case MAIN_WEAPON -> "PRIMARY WEAPON";
            case MISSILE_LAUNCHERS -> "MISSILE LAUNCHER BANKS";
            case REACTOR -> "REACTOR";
            case POWER_CONDUITS -> "POWER SPINE";
            case PORT_POWER -> "PORT POWER NODE";
            case STARBOARD_POWER -> "STARBOARD POWER NODE";
            case INTEGRITY_FIELD -> "INTEGRITY FIELD GENERATOR";
            case SERVICE_BAY -> "SERVICE BAY";
            case CARGO_BAY -> "CARGO HOLD";
            case MAGAZINES -> "MAGAZINES / AMMO";
            case ENGINES -> "ENGINE CORE";
            case PORT_ENGINES -> "PORT ENGINE BANK";
            case STARBOARD_ENGINES -> "STARBOARD ENGINE BANK";
            case WARP_DRIVE -> "WARP DRIVE";
            case AFT_SPINE -> "AFT SPINE";
        };
    }

    public static String symbol(RoomId roomId) {
        if (roomId == null) return "SYS";
        return switch (roomId) {
            case BRIDGE -> "CMD";
            case SENSORS -> "SNS";
            case BOW -> "BOW";
            case BOW_ARMOR, DORSAL_ARMOR, VENTRAL_ARMOR, AFT_ARMOR -> "ARM";
            case CREW_QUARTERS -> "CRW";
            case PORT_BATTERY -> "PBT";
            case STARBOARD_BATTERY -> "SBT";
            case MAIN_WEAPON -> "WPN";
            case MISSILE_LAUNCHERS -> "MSL";
            case REACTOR -> "RCT";
            case POWER_CONDUITS -> "PWR";
            case PORT_POWER -> "PPW";
            case STARBOARD_POWER -> "SPW";
            case INTEGRITY_FIELD -> "INT";
            case SERVICE_BAY -> "SRV";
            case CARGO_BAY -> "CRG";
            case MAGAZINES -> "MAG";
            case ENGINES -> "ENG";
            case PORT_ENGINES -> "PEN";
            case STARBOARD_ENGINES -> "SEN";
            case WARP_DRIVE -> "WRP";
            case AFT_SPINE -> "AFT";
        };
    }

    public static boolean isWeaponRoom(RoomId roomId) {
        if (roomId == null) return false;
        return switch (roomId) {
            case PORT_BATTERY, STARBOARD_BATTERY, MAIN_WEAPON, MISSILE_LAUNCHERS -> true;
            default -> false;
        };
    }

    public static boolean isArmorRoom(RoomId roomId) {
        if (roomId == null) return false;
        return switch (roomId) {
            case BOW_ARMOR, DORSAL_ARMOR, VENTRAL_ARMOR, AFT_ARMOR -> true;
            default -> false;
        };
    }

    public static boolean isMagazineRoom(RoomId roomId) {
        return roomId == RoomId.MAGAZINES;
    }

    public static boolean isPowerRoom(RoomId roomId) {
        if (roomId == null) return false;
        return switch (roomId) {
            case REACTOR, POWER_CONDUITS, PORT_POWER, STARBOARD_POWER -> true;
            default -> false;
        };
    }

    public static boolean isShieldRoom(RoomId roomId) {
        return roomId == RoomId.INTEGRITY_FIELD;
    }

    public static boolean isBridgeRoom(RoomId roomId) {
        if (roomId == null) return false;
        return switch (roomId) {
            case BRIDGE, SENSORS, CREW_QUARTERS, BOW -> true;
            default -> false;
        };
    }

    public static boolean isEngineRoom(RoomId roomId) {
        if (roomId == null) return false;
        return switch (roomId) {
            case ENGINES, PORT_ENGINES, STARBOARD_ENGINES -> true;
            default -> false;
        };
    }

    public static boolean isWarpRoom(RoomId roomId) {
        return roomId == RoomId.WARP_DRIVE;
    }

    private static Profile profile(ShipRole role, Faction faction) {
        String key = layoutKey(role, faction);
        Profile cached = PROFILES.get(key);
        if (cached != null) return cached;

        Profile built = switch (profileKey(role)) {
            case "small" -> buildSmallProfile(role, faction);
            case "carrier" -> buildCarrierProfile(role, faction);
            case "station" -> buildStationProfile(role, faction);
            default -> buildCapitalProfile(role, faction);
        };
        PROFILES.put(key, built);
        return built;
    }

    private static String layoutKey(ShipRole role, Faction faction) {
        String rolePart = (role == null) ? "capital" : role.name();
        return rolePart + "|" + keyForFaction(faction);
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

    private static Profile buildSmallProfile(ShipRole role, Faction faction) {
        HullProfile hull = resolveProfileHull(role, faction, "small");
        List<RoomDef> rooms = new ArrayList<>();
        rooms.add(cell(hull, RoomId.BOW, displayLabel(RoomId.BOW), 0.76, 0.99, 0.26, 0.74, null, 0.26, false,
                RoomId.BRIDGE, RoomId.PORT_BATTERY, RoomId.STARBOARD_BATTERY));
        rooms.add(cell(hull, RoomId.BRIDGE, displayLabel(RoomId.BRIDGE), 0.54, 0.78, 0.18, 0.42, Ship.InternalSystem.BRIDGE, 0.54, true,
                RoomId.BOW, RoomId.SENSORS, RoomId.CREW_QUARTERS, RoomId.PORT_BATTERY, RoomId.MAIN_WEAPON));
        rooms.add(cell(hull, RoomId.SENSORS, displayLabel(RoomId.SENSORS), 0.26, 0.58, 0.04, 0.20, Ship.InternalSystem.SENSORS, 0.38, false,
                RoomId.BRIDGE, RoomId.CREW_QUARTERS, RoomId.PORT_BATTERY, RoomId.INTEGRITY_FIELD));
        rooms.add(cell(hull, RoomId.CREW_QUARTERS, displayLabel(RoomId.CREW_QUARTERS), 0.20, 0.62, 0.20, 0.34, null, 0.34, false,
                RoomId.SENSORS, RoomId.BRIDGE, RoomId.PORT_BATTERY, RoomId.MAIN_WEAPON, RoomId.INTEGRITY_FIELD));
        rooms.add(cell(hull, RoomId.PORT_BATTERY, displayLabel(RoomId.PORT_BATTERY), 0.14, 0.50, 0.34, 0.50, Ship.InternalSystem.WEAPONS, 0.42, false,
                RoomId.BOW, RoomId.BRIDGE, RoomId.CREW_QUARTERS, RoomId.SENSORS, RoomId.MAIN_WEAPON));
        rooms.add(cell(hull, RoomId.STARBOARD_BATTERY, displayLabel(RoomId.STARBOARD_BATTERY), 0.14, 0.50, 0.50, 0.66, Ship.InternalSystem.WEAPONS, 0.42, false,
                RoomId.BOW, RoomId.MAIN_WEAPON, RoomId.MISSILE_LAUNCHERS));
        rooms.add(cell(hull, RoomId.MAIN_WEAPON, displayLabel(RoomId.MAIN_WEAPON), -0.02, 0.24, 0.32, 0.68, Ship.InternalSystem.WEAPONS, 0.54, false,
                RoomId.BRIDGE, RoomId.CREW_QUARTERS, RoomId.PORT_BATTERY, RoomId.STARBOARD_BATTERY, RoomId.REACTOR));
        rooms.add(cell(hull, RoomId.MISSILE_LAUNCHERS, displayLabel(RoomId.MISSILE_LAUNCHERS), -0.18, 0.12, 0.68, 0.84, Ship.InternalSystem.WEAPONS, 0.38, false,
                RoomId.STARBOARD_BATTERY, RoomId.MAIN_WEAPON, RoomId.CARGO_BAY, RoomId.MAGAZINES));
        rooms.add(cell(hull, RoomId.REACTOR, displayLabel(RoomId.REACTOR), -0.26, 0.02, 0.34, 0.68, Ship.InternalSystem.REACTOR_CORE, 0.66, true,
                RoomId.MAIN_WEAPON, RoomId.PORT_POWER, RoomId.POWER_CONDUITS, RoomId.STARBOARD_POWER));
        rooms.add(cell(hull, RoomId.INTEGRITY_FIELD, displayLabel(RoomId.INTEGRITY_FIELD), -0.04, 0.26, 0.02, 0.18, Ship.InternalSystem.SHIELDS, 0.38, false,
                RoomId.SENSORS, RoomId.CREW_QUARTERS, RoomId.PORT_POWER, RoomId.REACTOR));
        rooms.add(cell(hull, RoomId.PORT_POWER, displayLabel(RoomId.PORT_POWER), -0.36, 0.02, 0.18, 0.34, Ship.InternalSystem.REACTOR_CORE, 0.34, false,
                RoomId.INTEGRITY_FIELD, RoomId.SERVICE_BAY, RoomId.REACTOR, RoomId.POWER_CONDUITS));
        rooms.add(cell(hull, RoomId.POWER_CONDUITS, displayLabel(RoomId.POWER_CONDUITS), -0.50, -0.18, 0.34, 0.66, Ship.InternalSystem.REACTOR_CORE, 0.40, false,
                RoomId.PORT_POWER, RoomId.REACTOR, RoomId.STARBOARD_POWER, RoomId.SERVICE_BAY, RoomId.ENGINES, RoomId.WARP_DRIVE));
        rooms.add(cell(hull, RoomId.STARBOARD_POWER, displayLabel(RoomId.STARBOARD_POWER), -0.36, 0.02, 0.66, 0.82, Ship.InternalSystem.REACTOR_CORE, 0.32, false,
                RoomId.REACTOR, RoomId.POWER_CONDUITS, RoomId.CARGO_BAY, RoomId.MAGAZINES));
        rooms.add(cell(hull, RoomId.SERVICE_BAY, displayLabel(RoomId.SERVICE_BAY), -0.62, -0.18, 0.04, 0.22, null, 0.34, false,
                RoomId.PORT_POWER, RoomId.POWER_CONDUITS, RoomId.ENGINES, RoomId.PORT_ENGINES));
        rooms.add(cell(hull, RoomId.ENGINES, displayLabel(RoomId.ENGINES), -0.74, -0.40, 0.34, 0.66, Ship.InternalSystem.ENGINES, 0.50, false,
                RoomId.SERVICE_BAY, RoomId.POWER_CONDUITS, RoomId.MAGAZINES, RoomId.PORT_ENGINES, RoomId.WARP_DRIVE, RoomId.STARBOARD_ENGINES));
        rooms.add(cell(hull, RoomId.MAGAZINES, displayLabel(RoomId.MAGAZINES), -0.62, -0.34, 0.64, 0.80, Ship.InternalSystem.MAGAZINES, 0.44, true,
                RoomId.MISSILE_LAUNCHERS, RoomId.STARBOARD_POWER, RoomId.CARGO_BAY, RoomId.ENGINES));
        rooms.add(cell(hull, RoomId.CARGO_BAY, displayLabel(RoomId.CARGO_BAY), -0.54, -0.18, 0.80, 0.94, null, 0.34, false,
                RoomId.MISSILE_LAUNCHERS, RoomId.STARBOARD_POWER, RoomId.MAGAZINES, RoomId.ENGINES, RoomId.STARBOARD_ENGINES));
        rooms.add(cell(hull, RoomId.PORT_ENGINES, displayLabel(RoomId.PORT_ENGINES), -0.96, -0.68, 0.18, 0.36, Ship.InternalSystem.ENGINES, 0.34, false,
                RoomId.SERVICE_BAY, RoomId.ENGINES, RoomId.WARP_DRIVE, RoomId.AFT_SPINE));
        rooms.add(cell(hull, RoomId.WARP_DRIVE, displayLabel(RoomId.WARP_DRIVE), -0.98, -0.74, 0.38, 0.62, Ship.InternalSystem.WARP_ENGINES, 0.38, false,
                RoomId.POWER_CONDUITS, RoomId.ENGINES, RoomId.PORT_ENGINES, RoomId.STARBOARD_ENGINES, RoomId.AFT_SPINE));
        rooms.add(cell(hull, RoomId.STARBOARD_ENGINES, displayLabel(RoomId.STARBOARD_ENGINES), -0.96, -0.68, 0.64, 0.82, Ship.InternalSystem.ENGINES, 0.34, false,
                RoomId.ENGINES, RoomId.CARGO_BAY, RoomId.WARP_DRIVE, RoomId.AFT_SPINE));
        rooms.add(cell(hull, RoomId.AFT_SPINE, displayLabel(RoomId.AFT_SPINE), -0.99, -0.86, 0.34, 0.66, null, 0.24, false,
                RoomId.WARP_DRIVE, RoomId.PORT_ENGINES, RoomId.STARBOARD_ENGINES));
        addArmorRooms(hull, rooms);
        return new Profile(rooms, Collections.emptyList());
    }

    private static Profile buildCapitalProfile(ShipRole role, Faction faction) {
        HullProfile hull = resolveProfileHull(role, faction, "capital");
        List<RoomDef> rooms = new ArrayList<>();
        rooms.add(cell(hull, RoomId.BOW, displayLabel(RoomId.BOW), 0.78, 0.99, 0.24, 0.76, null, 0.28, false,
                RoomId.BRIDGE, RoomId.PORT_BATTERY, RoomId.STARBOARD_BATTERY));
        rooms.add(cell(hull, RoomId.BRIDGE, displayLabel(RoomId.BRIDGE), 0.56, 0.82, 0.16, 0.40, Ship.InternalSystem.BRIDGE, 0.58, true,
                RoomId.BOW, RoomId.SENSORS, RoomId.CREW_QUARTERS, RoomId.PORT_BATTERY, RoomId.MAIN_WEAPON));
        rooms.add(cell(hull, RoomId.SENSORS, displayLabel(RoomId.SENSORS), 0.30, 0.62, 0.03, 0.20, Ship.InternalSystem.SENSORS, 0.40, false,
                RoomId.BRIDGE, RoomId.CREW_QUARTERS, RoomId.PORT_BATTERY, RoomId.INTEGRITY_FIELD));
        rooms.add(cell(hull, RoomId.CREW_QUARTERS, displayLabel(RoomId.CREW_QUARTERS), 0.18, 0.64, 0.20, 0.34, null, 0.36, false,
                RoomId.SENSORS, RoomId.BRIDGE, RoomId.PORT_BATTERY, RoomId.MAIN_WEAPON, RoomId.INTEGRITY_FIELD));
        rooms.add(cell(hull, RoomId.PORT_BATTERY, displayLabel(RoomId.PORT_BATTERY), 0.18, 0.58, 0.34, 0.50, Ship.InternalSystem.WEAPONS, 0.44, false,
                RoomId.BOW, RoomId.BRIDGE, RoomId.CREW_QUARTERS, RoomId.SENSORS, RoomId.MAIN_WEAPON));
        rooms.add(cell(hull, RoomId.STARBOARD_BATTERY, displayLabel(RoomId.STARBOARD_BATTERY), 0.18, 0.58, 0.50, 0.66, Ship.InternalSystem.WEAPONS, 0.44, false,
                RoomId.BOW, RoomId.MAIN_WEAPON, RoomId.MISSILE_LAUNCHERS));
        rooms.add(cell(hull, RoomId.MAIN_WEAPON, displayLabel(RoomId.MAIN_WEAPON), -0.04, 0.26, 0.32, 0.68, Ship.InternalSystem.WEAPONS, 0.58, false,
                RoomId.BRIDGE, RoomId.CREW_QUARTERS, RoomId.PORT_BATTERY, RoomId.STARBOARD_BATTERY, RoomId.REACTOR));
        rooms.add(cell(hull, RoomId.MISSILE_LAUNCHERS, displayLabel(RoomId.MISSILE_LAUNCHERS), -0.24, 0.12, 0.68, 0.84, Ship.InternalSystem.WEAPONS, 0.40, false,
                RoomId.STARBOARD_BATTERY, RoomId.MAIN_WEAPON, RoomId.CARGO_BAY, RoomId.MAGAZINES));
        rooms.add(cell(hull, RoomId.REACTOR, displayLabel(RoomId.REACTOR), -0.24, 0.06, 0.34, 0.68, Ship.InternalSystem.REACTOR_CORE, 0.70, true,
                RoomId.MAIN_WEAPON, RoomId.PORT_POWER, RoomId.POWER_CONDUITS, RoomId.STARBOARD_POWER, RoomId.MAGAZINES));
        rooms.add(cell(hull, RoomId.INTEGRITY_FIELD, displayLabel(RoomId.INTEGRITY_FIELD), -0.02, 0.32, 0.02, 0.16, Ship.InternalSystem.SHIELDS, 0.40, false,
                RoomId.SENSORS, RoomId.CREW_QUARTERS, RoomId.PORT_POWER, RoomId.REACTOR));
        rooms.add(cell(hull, RoomId.PORT_POWER, displayLabel(RoomId.PORT_POWER), -0.34, 0.04, 0.16, 0.32, Ship.InternalSystem.REACTOR_CORE, 0.34, false,
                RoomId.INTEGRITY_FIELD, RoomId.SERVICE_BAY, RoomId.REACTOR, RoomId.POWER_CONDUITS));
        rooms.add(cell(hull, RoomId.POWER_CONDUITS, displayLabel(RoomId.POWER_CONDUITS), -0.48, -0.14, 0.34, 0.66, Ship.InternalSystem.REACTOR_CORE, 0.42, false,
                RoomId.PORT_POWER, RoomId.REACTOR, RoomId.STARBOARD_POWER, RoomId.SERVICE_BAY, RoomId.ENGINES, RoomId.WARP_DRIVE));
        rooms.add(cell(hull, RoomId.STARBOARD_POWER, displayLabel(RoomId.STARBOARD_POWER), -0.34, 0.04, 0.68, 0.84, Ship.InternalSystem.REACTOR_CORE, 0.34, false,
                RoomId.REACTOR, RoomId.POWER_CONDUITS, RoomId.CARGO_BAY, RoomId.MAGAZINES));
        rooms.add(cell(hull, RoomId.SERVICE_BAY, displayLabel(RoomId.SERVICE_BAY), -0.66, -0.20, 0.03, 0.20, null, 0.36, false,
                RoomId.PORT_POWER, RoomId.POWER_CONDUITS, RoomId.ENGINES, RoomId.PORT_ENGINES));
        rooms.add(cell(hull, RoomId.ENGINES, displayLabel(RoomId.ENGINES), -0.74, -0.40, 0.34, 0.66, Ship.InternalSystem.ENGINES, 0.54, false,
                RoomId.SERVICE_BAY, RoomId.POWER_CONDUITS, RoomId.MAGAZINES, RoomId.PORT_ENGINES, RoomId.WARP_DRIVE, RoomId.STARBOARD_ENGINES));
        rooms.add(cell(hull, RoomId.MAGAZINES, displayLabel(RoomId.MAGAZINES), -0.66, -0.34, 0.66, 0.82, Ship.InternalSystem.MAGAZINES, 0.46, true,
                RoomId.MISSILE_LAUNCHERS, RoomId.STARBOARD_POWER, RoomId.CARGO_BAY, RoomId.REACTOR, RoomId.ENGINES));
        rooms.add(cell(hull, RoomId.CARGO_BAY, displayLabel(RoomId.CARGO_BAY), -0.58, -0.16, 0.82, 0.96, null, 0.36, false,
                RoomId.MISSILE_LAUNCHERS, RoomId.STARBOARD_POWER, RoomId.MAGAZINES, RoomId.ENGINES, RoomId.STARBOARD_ENGINES));
        rooms.add(cell(hull, RoomId.PORT_ENGINES, displayLabel(RoomId.PORT_ENGINES), -0.98, -0.70, 0.18, 0.36, Ship.InternalSystem.ENGINES, 0.36, false,
                RoomId.SERVICE_BAY, RoomId.ENGINES, RoomId.WARP_DRIVE, RoomId.AFT_SPINE));
        rooms.add(cell(hull, RoomId.WARP_DRIVE, displayLabel(RoomId.WARP_DRIVE), -0.99, -0.74, 0.38, 0.62, Ship.InternalSystem.WARP_ENGINES, 0.40, false,
                RoomId.POWER_CONDUITS, RoomId.ENGINES, RoomId.PORT_ENGINES, RoomId.STARBOARD_ENGINES, RoomId.AFT_SPINE));
        rooms.add(cell(hull, RoomId.STARBOARD_ENGINES, displayLabel(RoomId.STARBOARD_ENGINES), -0.98, -0.70, 0.64, 0.82, Ship.InternalSystem.ENGINES, 0.36, false,
                RoomId.ENGINES, RoomId.CARGO_BAY, RoomId.WARP_DRIVE, RoomId.AFT_SPINE));
        rooms.add(cell(hull, RoomId.AFT_SPINE, displayLabel(RoomId.AFT_SPINE), -0.99, -0.88, 0.34, 0.66, null, 0.24, false,
                RoomId.WARP_DRIVE, RoomId.PORT_ENGINES, RoomId.STARBOARD_ENGINES));
        addArmorRooms(hull, rooms);
        return new Profile(rooms, Collections.emptyList());
    }

    private static Profile buildCarrierProfile(ShipRole role, Faction faction) {
        HullProfile hull = resolveProfileHull(role, faction, "carrier");
        List<RoomDef> rooms = new ArrayList<>();
        rooms.add(cell(hull, RoomId.BOW, displayLabel(RoomId.BOW), 0.78, 0.99, 0.24, 0.76, null, 0.28, false,
                RoomId.BRIDGE, RoomId.PORT_BATTERY, RoomId.STARBOARD_BATTERY));
        rooms.add(cell(hull, RoomId.BRIDGE, displayLabel(RoomId.BRIDGE), 0.50, 0.74, 0.12, 0.34, Ship.InternalSystem.BRIDGE, 0.56, true,
                RoomId.SENSORS, RoomId.BOW, RoomId.CREW_QUARTERS, RoomId.PORT_BATTERY, RoomId.MAIN_WEAPON));
        rooms.add(cell(hull, RoomId.SENSORS, displayLabel(RoomId.SENSORS), 0.16, 0.46, 0.03, 0.18, Ship.InternalSystem.SENSORS, 0.38, false,
                RoomId.BRIDGE, RoomId.CREW_QUARTERS, RoomId.PORT_BATTERY, RoomId.INTEGRITY_FIELD));
        rooms.add(cell(hull, RoomId.CREW_QUARTERS, displayLabel(RoomId.CREW_QUARTERS), 0.00, 0.64, 0.18, 0.34, null, 0.40, false,
                RoomId.SENSORS, RoomId.BRIDGE, RoomId.PORT_BATTERY, RoomId.MAIN_WEAPON, RoomId.INTEGRITY_FIELD));
        rooms.add(cell(hull, RoomId.PORT_BATTERY, displayLabel(RoomId.PORT_BATTERY), 0.10, 0.54, 0.34, 0.50, Ship.InternalSystem.WEAPONS, 0.42, false,
                RoomId.SENSORS, RoomId.BOW, RoomId.BRIDGE, RoomId.CREW_QUARTERS, RoomId.MAIN_WEAPON));
        rooms.add(cell(hull, RoomId.STARBOARD_BATTERY, displayLabel(RoomId.STARBOARD_BATTERY), 0.10, 0.54, 0.50, 0.66, Ship.InternalSystem.WEAPONS, 0.42, false,
                RoomId.BOW, RoomId.MAIN_WEAPON, RoomId.MISSILE_LAUNCHERS));
        rooms.add(cell(hull, RoomId.MAIN_WEAPON, displayLabel(RoomId.MAIN_WEAPON), -0.12, 0.20, 0.32, 0.68, Ship.InternalSystem.WEAPONS, 0.54, false,
                RoomId.BRIDGE, RoomId.CREW_QUARTERS, RoomId.PORT_BATTERY, RoomId.STARBOARD_BATTERY, RoomId.REACTOR));
        rooms.add(cell(hull, RoomId.MISSILE_LAUNCHERS, displayLabel(RoomId.MISSILE_LAUNCHERS), -0.30, 0.06, 0.68, 0.84, Ship.InternalSystem.WEAPONS, 0.40, false,
                RoomId.STARBOARD_BATTERY, RoomId.MAIN_WEAPON, RoomId.CARGO_BAY, RoomId.MAGAZINES));
        rooms.add(cell(hull, RoomId.REACTOR, displayLabel(RoomId.REACTOR), -0.28, 0.00, 0.34, 0.68, Ship.InternalSystem.REACTOR_CORE, 0.66, true,
                RoomId.MAIN_WEAPON, RoomId.PORT_POWER, RoomId.POWER_CONDUITS, RoomId.STARBOARD_POWER));
        rooms.add(cell(hull, RoomId.INTEGRITY_FIELD, displayLabel(RoomId.INTEGRITY_FIELD), -0.08, 0.28, 0.02, 0.15, Ship.InternalSystem.SHIELDS, 0.38, false,
                RoomId.SENSORS, RoomId.CREW_QUARTERS, RoomId.PORT_POWER, RoomId.REACTOR));
        rooms.add(cell(hull, RoomId.PORT_POWER, displayLabel(RoomId.PORT_POWER), -0.36, 0.04, 0.16, 0.32, Ship.InternalSystem.REACTOR_CORE, 0.34, false,
                RoomId.INTEGRITY_FIELD, RoomId.SERVICE_BAY, RoomId.REACTOR, RoomId.POWER_CONDUITS));
        rooms.add(cell(hull, RoomId.POWER_CONDUITS, displayLabel(RoomId.POWER_CONDUITS), -0.48, -0.18, 0.34, 0.66, Ship.InternalSystem.REACTOR_CORE, 0.40, false,
                RoomId.PORT_POWER, RoomId.REACTOR, RoomId.STARBOARD_POWER, RoomId.SERVICE_BAY, RoomId.ENGINES, RoomId.WARP_DRIVE));
        rooms.add(cell(hull, RoomId.STARBOARD_POWER, displayLabel(RoomId.STARBOARD_POWER), -0.36, 0.04, 0.68, 0.84, Ship.InternalSystem.REACTOR_CORE, 0.32, false,
                RoomId.REACTOR, RoomId.POWER_CONDUITS, RoomId.CARGO_BAY, RoomId.MAGAZINES));
        rooms.add(cell(hull, RoomId.SERVICE_BAY, displayLabel(RoomId.SERVICE_BAY), -0.72, -0.16, 0.03, 0.18, null, 0.42, false,
                RoomId.PORT_POWER, RoomId.POWER_CONDUITS, RoomId.ENGINES, RoomId.PORT_ENGINES));
        rooms.add(cell(hull, RoomId.ENGINES, displayLabel(RoomId.ENGINES), -0.78, -0.42, 0.34, 0.66, Ship.InternalSystem.ENGINES, 0.52, false,
                RoomId.SERVICE_BAY, RoomId.POWER_CONDUITS, RoomId.MAGAZINES, RoomId.PORT_ENGINES, RoomId.WARP_DRIVE, RoomId.STARBOARD_ENGINES));
        rooms.add(cell(hull, RoomId.MAGAZINES, displayLabel(RoomId.MAGAZINES), -0.68, -0.32, 0.66, 0.82, Ship.InternalSystem.MAGAZINES, 0.44, true,
                RoomId.MISSILE_LAUNCHERS, RoomId.STARBOARD_POWER, RoomId.CARGO_BAY, RoomId.ENGINES));
        rooms.add(cell(hull, RoomId.CARGO_BAY, displayLabel(RoomId.CARGO_BAY), -0.62, -0.12, 0.82, 0.96, null, 0.40, false,
                RoomId.MISSILE_LAUNCHERS, RoomId.STARBOARD_POWER, RoomId.MAGAZINES, RoomId.ENGINES, RoomId.STARBOARD_ENGINES));
        rooms.add(cell(hull, RoomId.PORT_ENGINES, displayLabel(RoomId.PORT_ENGINES), -0.99, -0.72, 0.18, 0.36, Ship.InternalSystem.ENGINES, 0.34, false,
                RoomId.SERVICE_BAY, RoomId.ENGINES, RoomId.WARP_DRIVE, RoomId.AFT_SPINE));
        rooms.add(cell(hull, RoomId.WARP_DRIVE, displayLabel(RoomId.WARP_DRIVE), -1.00, -0.78, 0.38, 0.62, Ship.InternalSystem.WARP_ENGINES, 0.38, false,
                RoomId.POWER_CONDUITS, RoomId.ENGINES, RoomId.PORT_ENGINES, RoomId.STARBOARD_ENGINES, RoomId.AFT_SPINE));
        rooms.add(cell(hull, RoomId.STARBOARD_ENGINES, displayLabel(RoomId.STARBOARD_ENGINES), -0.99, -0.72, 0.64, 0.82, Ship.InternalSystem.ENGINES, 0.34, false,
                RoomId.ENGINES, RoomId.CARGO_BAY, RoomId.WARP_DRIVE, RoomId.AFT_SPINE));
        rooms.add(cell(hull, RoomId.AFT_SPINE, displayLabel(RoomId.AFT_SPINE), -1.00, -0.88, 0.36, 0.66, null, 0.24, false,
                RoomId.WARP_DRIVE, RoomId.PORT_ENGINES, RoomId.STARBOARD_ENGINES));
        addArmorRooms(hull, rooms);
        return new Profile(rooms, Collections.emptyList());
    }

    private static Profile buildStationProfile(ShipRole role, Faction faction) {
        HullProfile hull = resolveProfileHull(role, faction, "station");
        List<RoomDef> rooms = new ArrayList<>();
        rooms.add(cell(hull, RoomId.BOW, displayLabel(RoomId.BOW), 0.72, 0.99, 0.24, 0.76, null, 0.26, false,
                RoomId.BRIDGE, RoomId.PORT_BATTERY, RoomId.STARBOARD_BATTERY));
        rooms.add(cell(hull, RoomId.BRIDGE, "COMMAND CORE", 0.28, 0.58, 0.20, 0.44, Ship.InternalSystem.BRIDGE, 0.60, true,
                RoomId.BOW, RoomId.SENSORS, RoomId.CREW_QUARTERS, RoomId.PORT_BATTERY, RoomId.MAIN_WEAPON, RoomId.REACTOR));
        rooms.add(cell(hull, RoomId.SENSORS, displayLabel(RoomId.SENSORS), 0.22, 0.56, 0.03, 0.18, Ship.InternalSystem.SENSORS, 0.40, false,
                RoomId.BRIDGE, RoomId.CREW_QUARTERS, RoomId.PORT_BATTERY, RoomId.INTEGRITY_FIELD));
        rooms.add(cell(hull, RoomId.CREW_QUARTERS, displayLabel(RoomId.CREW_QUARTERS), 0.02, 0.62, 0.18, 0.34, null, 0.38, false,
                RoomId.SENSORS, RoomId.BRIDGE, RoomId.PORT_BATTERY, RoomId.MAIN_WEAPON, RoomId.INTEGRITY_FIELD));
        rooms.add(cell(hull, RoomId.PORT_BATTERY, displayLabel(RoomId.PORT_BATTERY), -0.04, 0.32, 0.34, 0.50, Ship.InternalSystem.WEAPONS, 0.42, false,
                RoomId.BOW, RoomId.BRIDGE, RoomId.CREW_QUARTERS, RoomId.SENSORS, RoomId.MAIN_WEAPON));
        rooms.add(cell(hull, RoomId.STARBOARD_BATTERY, displayLabel(RoomId.STARBOARD_BATTERY), -0.04, 0.32, 0.50, 0.66, Ship.InternalSystem.WEAPONS, 0.42, false,
                RoomId.BOW, RoomId.BRIDGE, RoomId.MAIN_WEAPON, RoomId.MISSILE_LAUNCHERS));
        rooms.add(cell(hull, RoomId.MAIN_WEAPON, "DEFENSE GRID", -0.30, 0.06, 0.32, 0.68, Ship.InternalSystem.WEAPONS, 0.56, false,
                RoomId.BRIDGE, RoomId.CREW_QUARTERS, RoomId.PORT_BATTERY, RoomId.STARBOARD_BATTERY, RoomId.REACTOR));
        rooms.add(cell(hull, RoomId.MISSILE_LAUNCHERS, "LAUNCH BAYS", -0.50, -0.06, 0.68, 0.84, Ship.InternalSystem.WEAPONS, 0.40, false,
                RoomId.STARBOARD_BATTERY, RoomId.MAIN_WEAPON, RoomId.CARGO_BAY, RoomId.MAGAZINES));
        rooms.add(cell(hull, RoomId.REACTOR, displayLabel(RoomId.REACTOR), -0.26, 0.08, 0.34, 0.68, Ship.InternalSystem.REACTOR_CORE, 0.72, true,
                RoomId.BRIDGE, RoomId.MAIN_WEAPON, RoomId.PORT_POWER, RoomId.POWER_CONDUITS, RoomId.STARBOARD_POWER));
        rooms.add(cell(hull, RoomId.INTEGRITY_FIELD, displayLabel(RoomId.INTEGRITY_FIELD), -0.06, 0.28, 0.02, 0.16, Ship.InternalSystem.SHIELDS, 0.38, false,
                RoomId.SENSORS, RoomId.CREW_QUARTERS, RoomId.PORT_POWER, RoomId.REACTOR));
        rooms.add(cell(hull, RoomId.PORT_POWER, displayLabel(RoomId.PORT_POWER), -0.34, 0.06, 0.16, 0.32, Ship.InternalSystem.REACTOR_CORE, 0.34, false,
                RoomId.INTEGRITY_FIELD, RoomId.SERVICE_BAY, RoomId.REACTOR, RoomId.POWER_CONDUITS));
        rooms.add(cell(hull, RoomId.POWER_CONDUITS, displayLabel(RoomId.POWER_CONDUITS), -0.50, -0.16, 0.34, 0.66, Ship.InternalSystem.REACTOR_CORE, 0.42, false,
                RoomId.PORT_POWER, RoomId.REACTOR, RoomId.STARBOARD_POWER, RoomId.SERVICE_BAY, RoomId.ENGINES, RoomId.WARP_DRIVE));
        rooms.add(cell(hull, RoomId.STARBOARD_POWER, displayLabel(RoomId.STARBOARD_POWER), -0.34, 0.06, 0.68, 0.84, Ship.InternalSystem.REACTOR_CORE, 0.32, false,
                RoomId.REACTOR, RoomId.POWER_CONDUITS, RoomId.CARGO_BAY, RoomId.MAGAZINES));
        rooms.add(cell(hull, RoomId.SERVICE_BAY, displayLabel(RoomId.SERVICE_BAY), -0.76, -0.18, 0.02, 0.18, null, 0.42, false,
                RoomId.PORT_POWER, RoomId.POWER_CONDUITS, RoomId.ENGINES, RoomId.PORT_ENGINES));
        rooms.add(cell(hull, RoomId.ENGINES, "THRUSTER GRID", -0.78, -0.40, 0.34, 0.66, Ship.InternalSystem.ENGINES, 0.50, false,
                RoomId.SERVICE_BAY, RoomId.POWER_CONDUITS, RoomId.MAGAZINES, RoomId.PORT_ENGINES, RoomId.WARP_DRIVE, RoomId.STARBOARD_ENGINES));
        rooms.add(cell(hull, RoomId.MAGAZINES, displayLabel(RoomId.MAGAZINES), -0.72, -0.30, 0.66, 0.82, Ship.InternalSystem.MAGAZINES, 0.46, true,
                RoomId.MISSILE_LAUNCHERS, RoomId.STARBOARD_POWER, RoomId.CARGO_BAY, RoomId.ENGINES));
        rooms.add(cell(hull, RoomId.CARGO_BAY, displayLabel(RoomId.CARGO_BAY), -0.64, -0.10, 0.82, 0.96, null, 0.40, false,
                RoomId.MISSILE_LAUNCHERS, RoomId.STARBOARD_POWER, RoomId.MAGAZINES, RoomId.ENGINES, RoomId.STARBOARD_ENGINES));
        rooms.add(cell(hull, RoomId.PORT_ENGINES, displayLabel(RoomId.PORT_ENGINES), -0.99, -0.72, 0.18, 0.36, Ship.InternalSystem.ENGINES, 0.34, false,
                RoomId.SERVICE_BAY, RoomId.ENGINES, RoomId.WARP_DRIVE, RoomId.AFT_SPINE));
        rooms.add(cell(hull, RoomId.WARP_DRIVE, "JUMP CORE", -1.00, -0.80, 0.38, 0.62, Ship.InternalSystem.WARP_ENGINES, 0.38, false,
                RoomId.POWER_CONDUITS, RoomId.ENGINES, RoomId.PORT_ENGINES, RoomId.STARBOARD_ENGINES, RoomId.AFT_SPINE));
        rooms.add(cell(hull, RoomId.STARBOARD_ENGINES, displayLabel(RoomId.STARBOARD_ENGINES), -0.99, -0.72, 0.64, 0.82, Ship.InternalSystem.ENGINES, 0.34, false,
                RoomId.ENGINES, RoomId.CARGO_BAY, RoomId.WARP_DRIVE, RoomId.AFT_SPINE));
        rooms.add(cell(hull, RoomId.AFT_SPINE, displayLabel(RoomId.AFT_SPINE), -1.00, -0.88, 0.34, 0.66, null, 0.24, false,
                RoomId.WARP_DRIVE, RoomId.PORT_ENGINES, RoomId.STARBOARD_ENGINES));
        addArmorRooms(hull, rooms);
        return new Profile(rooms, Collections.emptyList());
    }

    private static void addArmorRooms(HullProfile hull, List<RoomDef> rooms) {
        rooms.add(cell(hull, RoomId.DORSAL_ARMOR, displayLabel(RoomId.DORSAL_ARMOR), -0.86, 0.86, 0.00, 0.14, null, 0.42, false,
                RoomId.SENSORS, RoomId.BRIDGE, RoomId.CREW_QUARTERS, RoomId.PORT_BATTERY, RoomId.STARBOARD_BATTERY, RoomId.BOW));
        rooms.add(cell(hull, RoomId.VENTRAL_ARMOR, displayLabel(RoomId.VENTRAL_ARMOR), -0.88, 0.82, 0.86, 1.00, null, 0.44, false,
                RoomId.CARGO_BAY, RoomId.MAGAZINES, RoomId.PORT_ENGINES, RoomId.STARBOARD_ENGINES, RoomId.AFT_SPINE));
        rooms.add(cell(hull, RoomId.BOW_ARMOR, displayLabel(RoomId.BOW_ARMOR), 0.78, 1.00, 0.18, 0.82, null, 0.34, false,
                RoomId.BOW, RoomId.BRIDGE, RoomId.PORT_BATTERY, RoomId.STARBOARD_BATTERY, RoomId.MISSILE_LAUNCHERS));
        rooms.add(cell(hull, RoomId.AFT_ARMOR, displayLabel(RoomId.AFT_ARMOR), -1.00, -0.82, 0.18, 0.82, null, 0.34, false,
                RoomId.AFT_SPINE, RoomId.WARP_DRIVE, RoomId.PORT_ENGINES, RoomId.STARBOARD_ENGINES, RoomId.ENGINES));
    }

    private static List<VisualCell> buildVisualCells(ShipRole role, Faction faction, List<RoomDef> rooms) {
        HullProfile hull = HullProfile.fromSilhouette(role, faction);
        if (hull == null) {
            hull = switch (profileKey(role)) {
                case "small" -> defaultSmallHull();
                case "carrier" -> defaultCarrierHull();
                case "station" -> defaultStationHull();
                default -> defaultCapitalHull();
            };
        }

        return switch (profileKey(role)) {
            case "small" -> buildSmallVisualCells(hull, rooms);
            case "carrier" -> buildCarrierVisualCells(hull, rooms);
            case "station" -> buildStationVisualCells(hull, rooms);
            default -> buildCapitalVisualCells(hull, rooms);
        };
    }

    private static HullProfile resolveProfileHull(ShipRole role, Faction faction, String profileKey) {
        HullProfile hull = HullProfile.fromSilhouette(role, faction);
        if (hull != null) return hull;
        return switch (profileKey) {
            case "small" -> defaultSmallHull();
            case "carrier" -> defaultCarrierHull();
            case "station" -> defaultStationHull();
            default -> defaultCapitalHull();
        };
    }

    private static String keyForFaction(Faction faction) {
        if (faction == null) return "generic";
        return switch (faction) {
            case PLAYER, ALLY -> "ally";
            case ENEMY -> "enemy";
            case TEAM_C -> "team_c";
            case TEAM_D -> "team_d";
        };
    }

    private static HullProfile defaultSmallHull() {
        return new HullProfile(
                new double[]{-1.00, -0.90, -0.74, -0.54, -0.28, 0.00, 0.28, 0.54, 0.78, 0.92, 1.00},
                new double[]{-0.06, -0.12, -0.22, -0.30, -0.35, -0.37, -0.34, -0.28, -0.18, -0.08, 0.00},
                new double[]{ 0.06,  0.12,  0.22,  0.30,  0.35,  0.37,  0.34,  0.28,  0.18,  0.08, 0.00}
        );
    }

    private static HullProfile defaultCapitalHull() {
        return new HullProfile(
                new double[]{-1.00, -0.92, -0.78, -0.58, -0.30, 0.00, 0.30, 0.58, 0.80, 0.94, 1.00},
                new double[]{-0.08, -0.14, -0.24, -0.34, -0.42, -0.44, -0.40, -0.32, -0.22, -0.10, 0.00},
                new double[]{ 0.08,  0.14,  0.24,  0.34,  0.42,  0.44,  0.40,  0.32,  0.22,  0.10, 0.00}
        );
    }

    private static HullProfile defaultCarrierHull() {
        return new HullProfile(
                new double[]{-1.00, -0.92, -0.80, -0.62, -0.34, -0.02, 0.28, 0.56, 0.78, 0.94, 1.00},
                new double[]{-0.09, -0.14, -0.18, -0.24, -0.28, -0.30, -0.28, -0.24, -0.18, -0.10, 0.00},
                new double[]{ 0.09,  0.14,  0.22,  0.32,  0.40,  0.42,  0.42,  0.38,  0.28,  0.14, 0.00}
        );
    }

    private static HullProfile defaultStationHull() {
        return new HullProfile(
                new double[]{-1.00, -0.88, -0.70, -0.44, -0.14, 0.16, 0.44, 0.68, 0.86, 1.00},
                new double[]{-0.12, -0.22, -0.34, -0.44, -0.50, -0.48, -0.40, -0.30, -0.18, 0.00},
                new double[]{ 0.12,  0.22,  0.34,  0.44,  0.50,  0.48,  0.40,  0.30,  0.18, 0.00}
        );
    }

    private static RoomDef cell(HullProfile hull, RoomId id, String label,
                                double x0, double x1, double topFrac, double bottomFrac,
                                Ship.InternalSystem system, double hpWeight, boolean critical,
                                RoomId... neighbors) {
        double xPad = 0.035;
        double yPad = 0.13;
        double xa = Math.max(-1.0, Math.min(1.0, Math.min(x0, x1) - xPad));
        double xb = Math.max(-1.0, Math.min(1.0, Math.max(x0, x1) + xPad));
        double tf = Math.max(0.0, Math.min(0.98, Math.min(topFrac, bottomFrac) - yPad));
        double bf = Math.max(tf + 0.04, Math.min(1.0, Math.max(topFrac, bottomFrac) + yPad));

        double xm = xa + (xb - xa) * 0.5;
        double[] xs = new double[]{xa, xm, xb, xb, xm, xa};
        double[] ys = new double[]{
                hull.innerY(xa, tf),
                hull.innerY(xm, tf),
                hull.innerY(xb, tf),
                hull.innerY(xb, bf),
                hull.innerY(xm, bf),
                hull.innerY(xa, bf)
        };
        return new RoomDef(id, label, xs, ys, system, hpWeight, critical, neighbors);
    }

    private static List<VisualCell> buildSmallVisualCells(HullProfile hull, List<RoomDef> rooms) {
        return buildDeckGrid(hull, rooms, new RowTemplate[]{
                microRow(0.00, 0.08, RoomId.AFT_ARMOR, RoomId.DORSAL_ARMOR, RoomId.DORSAL_ARMOR, RoomId.SENSORS, RoomId.SENSORS, RoomId.DORSAL_ARMOR, RoomId.BRIDGE, RoomId.BOW_ARMOR, RoomId.BOW_ARMOR),
                microRow(0.08, 0.16, RoomId.AFT_ARMOR, RoomId.DORSAL_ARMOR, RoomId.CREW_QUARTERS, RoomId.CREW_QUARTERS, RoomId.CREW_QUARTERS, RoomId.SENSORS, RoomId.DORSAL_ARMOR, RoomId.BRIDGE, RoomId.BOW_ARMOR, RoomId.BOW_ARMOR),
                row(0.16, 0.25, RoomId.SERVICE_BAY, RoomId.PORT_ENGINES, RoomId.ENGINES, RoomId.PORT_POWER, RoomId.INTEGRITY_FIELD, RoomId.SENSORS, RoomId.BRIDGE, RoomId.PORT_BATTERY, RoomId.BOW),
                row(0.25, 0.34, RoomId.PORT_ENGINES, RoomId.ENGINES, RoomId.PORT_POWER, RoomId.POWER_CONDUITS, RoomId.INTEGRITY_FIELD, RoomId.CREW_QUARTERS, RoomId.PORT_BATTERY, RoomId.PORT_BATTERY, RoomId.BOW),
                row(0.34, 0.44, RoomId.AFT_SPINE, RoomId.WARP_DRIVE, RoomId.POWER_CONDUITS, RoomId.REACTOR, RoomId.MAIN_WEAPON, RoomId.PORT_BATTERY, RoomId.PORT_BATTERY, RoomId.BOW),
                row(0.44, 0.54, RoomId.AFT_SPINE, RoomId.WARP_DRIVE, RoomId.POWER_CONDUITS, RoomId.REACTOR, RoomId.REACTOR, RoomId.MAIN_WEAPON, RoomId.STARBOARD_BATTERY, RoomId.BOW),
                row(0.54, 0.64, RoomId.AFT_SPINE, RoomId.ENGINES, RoomId.MAGAZINES, RoomId.REACTOR, RoomId.MAIN_WEAPON, RoomId.STARBOARD_BATTERY, RoomId.STARBOARD_BATTERY, RoomId.MISSILE_LAUNCHERS, RoomId.BOW),
                row(0.64, 0.76, RoomId.PORT_ENGINES, RoomId.SERVICE_BAY, RoomId.CARGO_BAY, RoomId.MAGAZINES, RoomId.STARBOARD_POWER, RoomId.STARBOARD_BATTERY, RoomId.MISSILE_LAUNCHERS),
                microRow(0.76, 0.88, RoomId.AFT_ARMOR, RoomId.VENTRAL_ARMOR, RoomId.SERVICE_BAY, RoomId.CARGO_BAY, RoomId.CARGO_BAY, RoomId.MAGAZINES, RoomId.VENTRAL_ARMOR, RoomId.BOW_ARMOR),
                microRow(0.88, 0.95, RoomId.AFT_ARMOR, RoomId.VENTRAL_ARMOR, RoomId.CARGO_BAY, RoomId.CARGO_BAY, RoomId.VENTRAL_ARMOR, RoomId.BOW_ARMOR),
                microRow(0.95, 1.00, RoomId.AFT_ARMOR, RoomId.VENTRAL_ARMOR, RoomId.BOW_ARMOR)
        });
    }

    private static List<VisualCell> buildCapitalVisualCells(HullProfile hull, List<RoomDef> rooms) {
        return buildDeckGrid(hull, rooms, new RowTemplate[]{
                microRow(0.00, 0.07, RoomId.AFT_ARMOR, RoomId.DORSAL_ARMOR, RoomId.CREW_QUARTERS, RoomId.CREW_QUARTERS, RoomId.SENSORS, RoomId.SENSORS, RoomId.DORSAL_ARMOR, RoomId.CREW_QUARTERS, RoomId.BRIDGE, RoomId.BOW_ARMOR, RoomId.BOW_ARMOR),
                microRow(0.07, 0.15, RoomId.AFT_ARMOR, RoomId.DORSAL_ARMOR, RoomId.DORSAL_ARMOR, RoomId.CREW_QUARTERS, RoomId.CREW_QUARTERS, RoomId.CREW_QUARTERS, RoomId.SENSORS, RoomId.SENSORS, RoomId.DORSAL_ARMOR, RoomId.BRIDGE, RoomId.BOW_ARMOR, RoomId.BOW_ARMOR),
                row(0.15, 0.24, RoomId.SERVICE_BAY, RoomId.PORT_ENGINES, RoomId.ENGINES, RoomId.PORT_POWER, RoomId.INTEGRITY_FIELD, RoomId.CREW_QUARTERS, RoomId.PORT_BATTERY, RoomId.BRIDGE, RoomId.BOW),
                row(0.24, 0.33, RoomId.PORT_ENGINES, RoomId.ENGINES, RoomId.PORT_POWER, RoomId.POWER_CONDUITS, RoomId.INTEGRITY_FIELD, RoomId.CREW_QUARTERS, RoomId.PORT_BATTERY, RoomId.PORT_BATTERY, RoomId.BRIDGE, RoomId.BOW),
                row(0.33, 0.42, RoomId.AFT_SPINE, RoomId.WARP_DRIVE, RoomId.POWER_CONDUITS, RoomId.REACTOR, RoomId.MAIN_WEAPON, RoomId.PORT_BATTERY, RoomId.PORT_BATTERY, RoomId.BRIDGE, RoomId.BOW),
                row(0.42, 0.51, RoomId.AFT_SPINE, RoomId.WARP_DRIVE, RoomId.POWER_CONDUITS, RoomId.REACTOR, RoomId.REACTOR, RoomId.MAIN_WEAPON, RoomId.STARBOARD_BATTERY, RoomId.STARBOARD_BATTERY, RoomId.BOW),
                row(0.51, 0.61, RoomId.AFT_SPINE, RoomId.ENGINES, RoomId.MAGAZINES, RoomId.POWER_CONDUITS, RoomId.REACTOR, RoomId.MAIN_WEAPON, RoomId.STARBOARD_BATTERY, RoomId.MISSILE_LAUNCHERS, RoomId.BOW),
                row(0.61, 0.72, RoomId.PORT_ENGINES, RoomId.ENGINES, RoomId.MAGAZINES, RoomId.CARGO_BAY, RoomId.REACTOR, RoomId.MAIN_WEAPON, RoomId.STARBOARD_BATTERY, RoomId.MISSILE_LAUNCHERS),
                row(0.72, 0.84, RoomId.PORT_ENGINES, RoomId.SERVICE_BAY, RoomId.CARGO_BAY, RoomId.CARGO_BAY, RoomId.MAGAZINES, RoomId.STARBOARD_POWER, RoomId.STARBOARD_ENGINES, RoomId.MISSILE_LAUNCHERS),
                microRow(0.84, 0.92, RoomId.AFT_ARMOR, RoomId.VENTRAL_ARMOR, RoomId.SERVICE_BAY, RoomId.CARGO_BAY, RoomId.CARGO_BAY, RoomId.VENTRAL_ARMOR, RoomId.BOW_ARMOR, RoomId.BOW_ARMOR),
                microRow(0.92, 0.97, RoomId.AFT_ARMOR, RoomId.VENTRAL_ARMOR, RoomId.CARGO_BAY, RoomId.CARGO_BAY, RoomId.VENTRAL_ARMOR, RoomId.BOW_ARMOR),
                microRow(0.97, 1.00, RoomId.AFT_ARMOR, RoomId.VENTRAL_ARMOR, RoomId.BOW_ARMOR)
        });
    }

    private static List<VisualCell> buildCarrierVisualCells(HullProfile hull, List<RoomDef> rooms) {
        return buildDeckGrid(hull, rooms, new RowTemplate[]{
                microRow(0.00, 0.07, RoomId.AFT_ARMOR, RoomId.DORSAL_ARMOR, RoomId.CREW_QUARTERS, RoomId.CREW_QUARTERS, RoomId.CREW_QUARTERS, RoomId.SENSORS, RoomId.DORSAL_ARMOR, RoomId.BRIDGE, RoomId.BOW_ARMOR, RoomId.BOW_ARMOR),
                microRow(0.07, 0.14, RoomId.AFT_ARMOR, RoomId.DORSAL_ARMOR, RoomId.DORSAL_ARMOR, RoomId.CREW_QUARTERS, RoomId.CREW_QUARTERS, RoomId.CREW_QUARTERS, RoomId.CREW_QUARTERS, RoomId.SENSORS, RoomId.DORSAL_ARMOR, RoomId.BRIDGE, RoomId.BOW_ARMOR),
                microRow(0.14, 0.23, RoomId.AFT_ARMOR, RoomId.DORSAL_ARMOR, RoomId.CREW_QUARTERS, RoomId.CREW_QUARTERS, RoomId.CREW_QUARTERS, RoomId.PORT_POWER, RoomId.INTEGRITY_FIELD, RoomId.SENSORS, RoomId.DORSAL_ARMOR, RoomId.BOW_ARMOR),
                row(0.23, 0.32, RoomId.SERVICE_BAY, RoomId.PORT_ENGINES, RoomId.ENGINES, RoomId.PORT_POWER, RoomId.INTEGRITY_FIELD, RoomId.CREW_QUARTERS, RoomId.PORT_BATTERY, RoomId.BRIDGE, RoomId.BOW),
                row(0.32, 0.41, RoomId.AFT_SPINE, RoomId.WARP_DRIVE, RoomId.POWER_CONDUITS, RoomId.REACTOR, RoomId.CARGO_BAY, RoomId.PORT_BATTERY, RoomId.BRIDGE, RoomId.BOW),
                row(0.41, 0.50, RoomId.AFT_SPINE, RoomId.WARP_DRIVE, RoomId.POWER_CONDUITS, RoomId.REACTOR, RoomId.CARGO_BAY, RoomId.MAIN_WEAPON, RoomId.STARBOARD_BATTERY, RoomId.BOW),
                row(0.50, 0.60, RoomId.AFT_SPINE, RoomId.WARP_DRIVE, RoomId.POWER_CONDUITS, RoomId.REACTOR, RoomId.CARGO_BAY, RoomId.CARGO_BAY, RoomId.MAIN_WEAPON, RoomId.STARBOARD_BATTERY, RoomId.BOW),
                row(0.60, 0.71, RoomId.PORT_ENGINES, RoomId.ENGINES, RoomId.CARGO_BAY, RoomId.CARGO_BAY, RoomId.REACTOR, RoomId.MAIN_WEAPON, RoomId.STARBOARD_BATTERY, RoomId.MISSILE_LAUNCHERS, RoomId.BOW),
                row(0.71, 0.83, RoomId.PORT_ENGINES, RoomId.SERVICE_BAY, RoomId.CARGO_BAY, RoomId.CARGO_BAY, RoomId.MAGAZINES, RoomId.STARBOARD_POWER, RoomId.STARBOARD_ENGINES, RoomId.MISSILE_LAUNCHERS),
                microRow(0.83, 0.91, RoomId.AFT_ARMOR, RoomId.VENTRAL_ARMOR, RoomId.SERVICE_BAY, RoomId.CARGO_BAY, RoomId.CARGO_BAY, RoomId.VENTRAL_ARMOR, RoomId.BOW_ARMOR, RoomId.BOW_ARMOR),
                microRow(0.91, 0.97, RoomId.AFT_ARMOR, RoomId.VENTRAL_ARMOR, RoomId.CARGO_BAY, RoomId.CARGO_BAY, RoomId.VENTRAL_ARMOR, RoomId.BOW_ARMOR),
                microRow(0.97, 1.00, RoomId.AFT_ARMOR, RoomId.VENTRAL_ARMOR, RoomId.BOW_ARMOR)
        });
    }

    private static List<VisualCell> buildStationVisualCells(HullProfile hull, List<RoomDef> rooms) {
        return buildDeckGrid(hull, rooms, new RowTemplate[]{
                microRow(0.00, 0.07, RoomId.AFT_ARMOR, RoomId.DORSAL_ARMOR, RoomId.CREW_QUARTERS, RoomId.CREW_QUARTERS, RoomId.SENSORS, RoomId.DORSAL_ARMOR, RoomId.BRIDGE, RoomId.BOW_ARMOR, RoomId.BOW_ARMOR),
                microRow(0.07, 0.14, RoomId.AFT_ARMOR, RoomId.DORSAL_ARMOR, RoomId.DORSAL_ARMOR, RoomId.CREW_QUARTERS, RoomId.SENSORS, RoomId.SENSORS, RoomId.DORSAL_ARMOR, RoomId.BOW_ARMOR),
                microRow(0.14, 0.23, RoomId.AFT_ARMOR, RoomId.DORSAL_ARMOR, RoomId.PORT_POWER, RoomId.PORT_POWER, RoomId.INTEGRITY_FIELD, RoomId.CREW_QUARTERS, RoomId.PORT_BATTERY, RoomId.DORSAL_ARMOR, RoomId.BOW_ARMOR),
                row(0.23, 0.32, RoomId.SERVICE_BAY, RoomId.PORT_ENGINES, RoomId.PORT_POWER, RoomId.POWER_CONDUITS, RoomId.INTEGRITY_FIELD, RoomId.PORT_BATTERY, RoomId.PORT_BATTERY, RoomId.BRIDGE, RoomId.BOW),
                row(0.32, 0.41, RoomId.AFT_SPINE, RoomId.WARP_DRIVE, RoomId.POWER_CONDUITS, RoomId.REACTOR, RoomId.REACTOR, RoomId.MAIN_WEAPON, RoomId.PORT_BATTERY, RoomId.BOW),
                row(0.41, 0.50, RoomId.AFT_SPINE, RoomId.WARP_DRIVE, RoomId.POWER_CONDUITS, RoomId.REACTOR, RoomId.MAIN_WEAPON, RoomId.MAIN_WEAPON, RoomId.STARBOARD_BATTERY, RoomId.BOW),
                row(0.50, 0.60, RoomId.AFT_SPINE, RoomId.ENGINES, RoomId.SERVICE_BAY, RoomId.POWER_CONDUITS, RoomId.REACTOR, RoomId.MAIN_WEAPON, RoomId.STARBOARD_BATTERY, RoomId.MISSILE_LAUNCHERS, RoomId.BOW),
                row(0.60, 0.71, RoomId.PORT_ENGINES, RoomId.ENGINES, RoomId.SERVICE_BAY, RoomId.CARGO_BAY, RoomId.REACTOR, RoomId.MAIN_WEAPON, RoomId.STARBOARD_BATTERY, RoomId.MISSILE_LAUNCHERS),
                row(0.71, 0.83, RoomId.PORT_ENGINES, RoomId.SERVICE_BAY, RoomId.CARGO_BAY, RoomId.CARGO_BAY, RoomId.MAGAZINES, RoomId.STARBOARD_POWER, RoomId.STARBOARD_ENGINES, RoomId.MISSILE_LAUNCHERS),
                microRow(0.83, 0.91, RoomId.AFT_ARMOR, RoomId.VENTRAL_ARMOR, RoomId.SERVICE_BAY, RoomId.CARGO_BAY, RoomId.CARGO_BAY, RoomId.VENTRAL_ARMOR, RoomId.BOW_ARMOR, RoomId.BOW_ARMOR),
                microRow(0.91, 0.97, RoomId.AFT_ARMOR, RoomId.VENTRAL_ARMOR, RoomId.CARGO_BAY, RoomId.CARGO_BAY, RoomId.VENTRAL_ARMOR, RoomId.BOW_ARMOR),
                microRow(0.97, 1.00, RoomId.AFT_ARMOR, RoomId.VENTRAL_ARMOR, RoomId.BOW_ARMOR)
        });
    }

    private static List<VisualCell> buildDeckGrid(HullProfile hull, List<RoomDef> rooms, RowTemplate[] rows) {
        List<VisualCell> raw = new ArrayList<>();
        if (rows != null) {
            for (RowTemplate row : rows) {
                if (row == null || row.rooms == null || row.rooms.length == 0) continue;
                Interval interval = hull.usableInterval(row.topFrac, row.bottomFrac);
                if (interval == null || interval.length() <= 0.04) continue;
                double gap = row.micro ? 0.0012 : 0.0025;
                int count = row.rooms.length;
                double width = interval.length() - gap * (count - 1);
                if (width <= 0.02) continue;
                double cellW = width / count;
                for (int i = 0; i < count; i++) {
                    double x0 = interval.x0 + i * (cellW + gap);
                    double x1 = x0 + cellW;
                    raw.add(visualCell(hull, row.rooms[i], x0, x1, row.topFrac, row.bottomFrac, false, row.micro));
                }
            }
        }
        appendArmorShellCells(raw, hull);
        return assignVisualLabels(raw, rooms);
    }

    private static void appendArmorShellCells(List<VisualCell> raw, HullProfile hull) {
        if (raw == null || hull == null) return;

        double[] shellCuts = new double[]{-1.00, -0.78, -0.54, -0.28, 0.00, 0.28, 0.54, 0.78, 1.00};
        for (int i = 0; i < shellCuts.length - 1; i++) {
            double x0 = shellCuts[i];
            double x1 = shellCuts[i + 1];
            RoomId topRoom = armorRoomForSpan((x0 + x1) * 0.5, true);
            RoomId bottomRoom = armorRoomForSpan((x0 + x1) * 0.5, false);
            VisualCell top = shellStripCell(hull, topRoom, x0, x1, 0.00, 0.12);
            VisualCell bottom = shellStripCell(hull, bottomRoom, x0, x1, 0.88, 1.00);
            if (top != null) raw.add(top);
            if (bottom != null) raw.add(bottom);
        }

        VisualCell aftCap = endCapCell(hull, RoomId.AFT_ARMOR, -1.00, -0.82, 0.18, 0.82);
        VisualCell bowCap = endCapCell(hull, RoomId.BOW_ARMOR, 0.82, 1.00, 0.18, 0.82);
        if (aftCap != null) raw.add(aftCap);
        if (bowCap != null) raw.add(bowCap);
    }

    private static RoomId armorRoomForSpan(double centerX, boolean top) {
        if (centerX >= 0.72) return RoomId.BOW_ARMOR;
        if (centerX <= -0.72) return RoomId.AFT_ARMOR;
        return top ? RoomId.DORSAL_ARMOR : RoomId.VENTRAL_ARMOR;
    }

    private static VisualCell shellStripCell(HullProfile hull, RoomId roomId,
                                             double x0, double x1,
                                             double outerFrac, double innerFrac) {
        double xa = Math.max(-1.0, Math.min(1.0, Math.min(x0, x1)));
        double xb = Math.max(-1.0, Math.min(1.0, Math.max(x0, x1)));
        int samples = 5;
        double[] xs = new double[samples * 2];
        double[] ys = new double[samples * 2];

        double totalArea = 0.0;
        for (int i = 0; i < samples; i++) {
            double t = i / (double) (samples - 1);
            double x = xa + (xb - xa) * t;
            xs[i] = x;
            ys[i] = hull.innerY(x, outerFrac) + ((outerFrac < 0.5) ? 0.0010 : -0.0010);
        }
        for (int i = 0; i < samples; i++) {
            double t = (samples - 1 - i) / (double) (samples - 1);
            double x = xa + (xb - xa) * t;
            xs[samples + i] = x;
            ys[samples + i] = hull.innerY(x, innerFrac) + ((outerFrac < 0.5) ? -0.0016 : 0.0016);
        }
        totalArea = polygonArea(xs, ys);
        if (totalArea <= 0.0008) return null;
        return new VisualCell(roomId, xs, ys, false);
    }

    private static VisualCell endCapCell(HullProfile hull, RoomId roomId,
                                         double x0, double x1,
                                         double topFrac, double bottomFrac) {
        double xa = Math.max(-1.0, Math.min(1.0, Math.min(x0, x1)));
        double xb = Math.max(-1.0, Math.min(1.0, Math.max(x0, x1)));
        double[] xs = new double[]{xa, xb, xb, xa};
        double[] ys = new double[]{
                hull.innerY(xa, topFrac),
                hull.innerY(xb, topFrac),
                hull.innerY(xb, bottomFrac),
                hull.innerY(xa, bottomFrac)
        };
        if (polygonArea(xs, ys) <= 0.0010) return null;
        return new VisualCell(roomId, xs, ys, false);
    }

    private static VisualCell visualCell(HullProfile hull, RoomId roomId,
                                         double x0, double x1, double topFrac, double bottomFrac,
                                         boolean labelAnchor, boolean micro) {
        double xPad = micro ? 0.0002 : 0.0005;
        double yGap = micro ? 0.0014 : 0.0025;
        double xa = Math.max(-1.0, Math.min(1.0, Math.min(x0, x1) + xPad));
        double xb = Math.max(-1.0, Math.min(1.0, Math.max(x0, x1) - xPad));
        Interval interval = hull.usableInterval(topFrac, bottomFrac);
        if (interval != null) {
            xa = Math.max(xa, interval.x0 + xPad);
            xb = Math.min(xb, interval.x1 - xPad);
        }
        double bandTop = hull.rowTop(topFrac);
        double bandBottom = hull.rowBottom(bottomFrac);
        double ya = bandTop + yGap;
        double yb = bandBottom - yGap;
        if (yb <= ya) {
            double mid = (ya + yb) * 0.5;
            ya = mid - 0.01;
            yb = mid + 0.01;
        }
        double[] xs = new double[]{xa, xb, xb, xa};
        double[] ys = new double[]{ya, ya, yb, yb};
        return new VisualCell(roomId, xs, ys, labelAnchor);
    }

    private static List<VisualCell> assignVisualLabels(List<VisualCell> cells, List<RoomDef> rooms) {
        EnumMap<RoomId, RoomDef> gameplay = new EnumMap<>(RoomId.class);
        for (RoomDef room : rooms) {
            if (room != null && room.id != null) gameplay.put(room.id, room);
        }

        EnumMap<RoomId, Integer> anchors = new EnumMap<>(RoomId.class);
        for (RoomId roomId : RoomId.values()) {
            RoomDef target = gameplay.get(roomId);
            if (target == null) continue;
            int bestIdx = -1;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < cells.size(); i++) {
                VisualCell cell = cells.get(i);
                if (cell.roomId != roomId) continue;
                double area = polygonArea(cell.xs, cell.ys);
                double cx = centroid(cell.xs);
                double cy = centroid(cell.ys);
                double dist = target.distanceSqToCentroid(cx, cy);
                double score = area * 1.8 - dist;
                if (score > bestScore) {
                    bestScore = score;
                    bestIdx = i;
                }
            }
            if (bestIdx >= 0) anchors.put(roomId, bestIdx);
        }

        List<VisualCell> out = new ArrayList<>(cells.size());
        for (int i = 0; i < cells.size(); i++) {
            VisualCell cell = cells.get(i);
            boolean anchor = (cell.roomId != null) && anchors.getOrDefault(cell.roomId, -1) == i;
            out.add(new VisualCell(cell.roomId, cell.xs, cell.ys, anchor));
        }
        return Collections.unmodifiableList(out);
    }

    private static double polygonArea(double[] xs, double[] ys) {
        int n = Math.min(xs.length, ys.length);
        if (n < 3) return 0.0;
        double area = 0.0;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            area += xs[j] * ys[i] - xs[i] * ys[j];
        }
        return Math.abs(area) * 0.5;
    }

    private static double centroid(double[] values) {
        if (values == null || values.length == 0) return 0.0;
        double sum = 0.0;
        for (double value : values) sum += value;
        return sum / values.length;
    }

    private static RowTemplate row(double topFrac, double bottomFrac, RoomId... rooms) {
        return new RowTemplate(topFrac, bottomFrac, false, rooms);
    }

    private static RowTemplate microRow(double topFrac, double bottomFrac, RoomId... rooms) {
        return new RowTemplate(topFrac, bottomFrac, true, rooms);
    }

    private static final class RowTemplate {
        final double topFrac;
        final double bottomFrac;
        final boolean micro;
        final RoomId[] rooms;

        RowTemplate(double topFrac, double bottomFrac, boolean micro, RoomId[] rooms) {
            this.topFrac = topFrac;
            this.bottomFrac = bottomFrac;
            this.micro = micro;
            this.rooms = (rooms == null) ? new RoomId[0] : rooms.clone();
        }
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

    private static final class HullProfile {
        final double[] xs;
        final double[] top;
        final double[] bottom;
        final double globalInnerTop;
        final double globalInnerBottom;

        static HullProfile fromSilhouette(ShipRole role) {
            return fromSilhouette(role, null);
        }

        static HullProfile fromSilhouette(ShipRole role, Faction faction) {
            Polygon hull = ShipHullSilhouette.hullPolygon(role, 100.0, faction);
            if (hull == null || hull.npoints < 3) return null;

            java.awt.Rectangle bounds = hull.getBounds();
            if (bounds.width <= 1 || bounds.height <= 1) return null;

            double cx = bounds.getCenterX();
            double cy = bounds.getCenterY();
            double halfW = Math.max(1.0, bounds.width * 0.5);
            double halfH = Math.max(1.0, bounds.height * 0.5);

            int xSamples = 81;
            int ySamples = 241;
            double[] xs = new double[xSamples];
            double[] top = new double[xSamples];
            double[] bottom = new double[xSamples];
            boolean[] valid = new boolean[xSamples];

            for (int i = 0; i < xSamples; i++) {
                double t = (xSamples == 1) ? 0.0 : i / (double) (xSamples - 1);
                double sampleX = bounds.x + t * bounds.width;
                double minY = Double.POSITIVE_INFINITY;
                double maxY = Double.NEGATIVE_INFINITY;
                for (int j = 0; j < ySamples; j++) {
                    double ty = (ySamples == 1) ? 0.0 : j / (double) (ySamples - 1);
                    double sampleY = bounds.y + ty * bounds.height;
                    if (!hull.contains(sampleX, sampleY)) continue;
                    minY = Math.min(minY, sampleY);
                    maxY = Math.max(maxY, sampleY);
                }

                xs[i] = MathUtil.clamp(((sampleX - cx) / halfW) * 0.98, -1.0, 1.0);
                if (Double.isFinite(minY) && Double.isFinite(maxY) && maxY > minY) {
                    top[i] = MathUtil.clamp(((minY - cy) / halfH) * 0.98, -1.0, 1.0);
                    bottom[i] = MathUtil.clamp(((maxY - cy) / halfH) * 0.98, -1.0, 1.0);
                    valid[i] = true;
                }
            }

            fillMissingColumns(top, bottom, valid);
            if (!anyValid(valid)) return null;
            smoothProfile(top, bottom);
            return new HullProfile(xs, top, bottom);
        }

        HullProfile(double[] xs, double[] top, double[] bottom) {
            this.xs = xs.clone();
            this.top = top.clone();
            this.bottom = bottom.clone();
            this.globalInnerTop = sampleGlobalInner(true);
            this.globalInnerBottom = sampleGlobalInner(false);
        }

        double innerY(double x, double frac) {
            double topY = interpolate(top, x);
            double bottomY = interpolate(bottom, x);
            double thickness = Math.max(0.001, bottomY - topY);
            double edgeInset = Math.min(0.024, Math.max(0.006, thickness * 0.06));
            double innerTop = topY + edgeInset;
            double innerBottom = bottomY - edgeInset;
            if (innerBottom <= innerTop) {
                innerTop = topY + thickness * 0.10;
                innerBottom = bottomY - thickness * 0.10;
            }
            double t = Math.max(0.0, Math.min(1.0, frac));
            return innerTop + (innerBottom - innerTop) * t;
        }

        double rowTop(double frac) {
            return globalY(Math.max(0.0, Math.min(1.0, frac)));
        }

        double rowBottom(double frac) {
            return globalY(Math.max(0.0, Math.min(1.0, frac)));
        }

        Interval usableInterval(double topFrac, double bottomFrac) {
            double yTop = rowTop(Math.min(topFrac, bottomFrac));
            double yBottom = rowBottom(Math.max(topFrac, bottomFrac));
            if (yBottom <= yTop) {
                double mid = (yTop + yBottom) * 0.5;
                yTop = mid - 0.01;
                yBottom = mid + 0.01;
            }

            double minX = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            int samples = 320;
            for (int i = 0; i <= samples; i++) {
                double x = -1.0 + 2.0 * i / (double) samples;
                double innerTop = innerY(x, 0.0);
                double innerBottom = innerY(x, 1.0);
                if (innerTop <= yTop && innerBottom >= yBottom) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                }
            }
            if (!Double.isFinite(minX) || !Double.isFinite(maxX) || maxX - minX <= 0.02) {
                return null;
            }
            double safety = Math.min(0.016, Math.max(0.006, (maxX - minX) * 0.01));
            return new Interval(minX + safety, maxX - safety);
        }

        double maxInnerTop(double x0, double x1) {
            return sampleBand(x0, x1, true);
        }

        double minInnerBottom(double x0, double x1) {
            return sampleBand(x0, x1, false);
        }

        private double sampleBand(double x0, double x1, boolean topSide) {
            double lo = Math.min(x0, x1);
            double hi = Math.max(x0, x1);
            double out = topSide ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
            int samples = 9;
            for (int i = 0; i <= samples; i++) {
                double t = i / (double) samples;
                double x = lo + (hi - lo) * t;
                double y = innerY(x, topSide ? 0.0 : 1.0);
                if (topSide) out = Math.max(out, y);
                else out = Math.min(out, y);
            }
            return out;
        }

        private double sampleGlobalInner(boolean topSide) {
            double out = topSide ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
            int samples = 320;
            for (int i = 0; i <= samples; i++) {
                double x = -1.0 + 2.0 * i / (double) samples;
                double y = innerY(x, topSide ? 0.0 : 1.0);
                if (topSide) out = Math.min(out, y);
                else out = Math.max(out, y);
            }
            return out;
        }

        private double globalY(double frac) {
            return globalInnerTop + (globalInnerBottom - globalInnerTop) * frac;
        }

        private static boolean anyValid(boolean[] valid) {
            for (boolean b : valid) if (b) return true;
            return false;
        }

        private static void fillMissingColumns(double[] top, double[] bottom, boolean[] valid) {
            int first = -1;
            int last = -1;
            for (int i = 0; i < valid.length; i++) {
                if (!valid[i]) continue;
                if (first < 0) first = i;
                last = i;
            }
            if (first < 0) return;

            for (int i = 0; i < first; i++) {
                top[i] = top[first];
                bottom[i] = bottom[first];
                valid[i] = true;
            }
            for (int i = last + 1; i < valid.length; i++) {
                top[i] = top[last];
                bottom[i] = bottom[last];
                valid[i] = true;
            }

            int prev = first;
            for (int i = first + 1; i <= last; i++) {
                if (valid[i]) {
                    prev = i;
                    continue;
                }
                int next = i + 1;
                while (next <= last && !valid[next]) next++;
                if (next > last) {
                    top[i] = top[prev];
                    bottom[i] = bottom[prev];
                } else {
                    double t = (i - prev) / (double) (next - prev);
                    top[i] = top[prev] + (top[next] - top[prev]) * t;
                    bottom[i] = bottom[prev] + (bottom[next] - bottom[prev]) * t;
                }
                valid[i] = true;
            }
        }

        private static void smoothProfile(double[] top, double[] bottom) {
            if (top.length < 3 || bottom.length < 3) return;
            double[] topCopy = top.clone();
            double[] bottomCopy = bottom.clone();
            for (int i = 1; i < top.length - 1; i++) {
                top[i] = (topCopy[i - 1] + topCopy[i] + topCopy[i + 1]) / 3.0;
                bottom[i] = (bottomCopy[i - 1] + bottomCopy[i] + bottomCopy[i + 1]) / 3.0;
            }
        }

        private double interpolate(double[] values, double x) {
            if (xs.length == 0) return 0.0;
            if (x <= xs[0]) return values[0];
            int last = xs.length - 1;
            if (x >= xs[last]) return values[last];
            for (int i = 0; i < last; i++) {
                double x0 = xs[i];
                double x1 = xs[i + 1];
                if (x < x0 || x > x1) continue;
                double span = Math.max(1e-9, x1 - x0);
                double t = (x - x0) / span;
                return values[i] + (values[i + 1] - values[i]) * t;
            }
            return values[last];
        }
    }

    private static final class Interval {
        final double x0;
        final double x1;

        Interval(double x0, double x1) {
            this.x0 = x0;
            this.x1 = x1;
        }

        double length() {
            return Math.max(0.0, x1 - x0);
        }
    }

    private static final class Profile {
        final List<RoomDef> rooms;
        final List<VisualCell> visualCells;
        final EnumMap<RoomId, RoomDef> byId = new EnumMap<>(RoomId.class);

        Profile(List<RoomDef> src, List<VisualCell> visualSrc) {
            List<RoomDef> list = new ArrayList<>(src);
            for (RoomDef d : list) byId.put(d.id, d);
            rooms = Collections.unmodifiableList(list);
            visualCells = (visualSrc == null) ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(visualSrc));
        }
    }
}
