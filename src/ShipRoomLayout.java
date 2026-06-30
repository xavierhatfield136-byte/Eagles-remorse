import java.awt.Polygon;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
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
        BOW_ARMOR_INNER,
        BOW_SHIELD_STRIP,
        DORSAL_ARMOR,
        DORSAL_ARMOR_INNER,
        DORSAL_SHIELD_STRIP,
        VENTRAL_ARMOR,
        VENTRAL_ARMOR_INNER,
        VENTRAL_SHIELD_STRIP,
        AFT_ARMOR,
        AFT_ARMOR_INNER,
        AFT_SHIELD_STRIP,
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
        private final double[][] coverageXs;
        private final double[][] coverageYs;
        public final Ship.InternalSystem primarySystem;
        public final double hpWeight;
        public final boolean critical;
        public final RoomId[] neighbors;
        private final double centroidX;
        private final double centroidY;

        private RoomDef(RoomId id, String label, double[] xs, double[] ys,
                        Ship.InternalSystem primarySystem, double hpWeight, boolean critical,
                        RoomId... neighbors) {
            this(id, label, xs, ys, null, null, primarySystem, hpWeight, critical, neighbors);
        }

        private RoomDef(RoomId id, String label, double[] xs, double[] ys,
                        double[][] coverageXs, double[][] coverageYs,
                        Ship.InternalSystem primarySystem, double hpWeight, boolean critical,
                        RoomId... neighbors) {
            this.id = id;
            this.label = label;
            this.xs = xs;
            this.ys = ys;
            this.coverageXs = normalizeCoverage(coverageXs, xs);
            this.coverageYs = normalizeCoverage(coverageYs, ys);
            this.primarySystem = primarySystem;
            this.hpWeight = hpWeight;
            this.critical = critical;
            this.neighbors = (neighbors == null) ? new RoomId[0] : neighbors.clone();

            double sx = 0.0;
            double sy = 0.0;
            int n = 0;
            for (int poly = 0; poly < this.coverageXs.length; poly++) {
                double[] polyXs = this.coverageXs[poly];
                double[] polyYs = this.coverageYs[poly];
                int pn = Math.min(polyXs.length, polyYs.length);
                for (int i = 0; i < pn; i++) {
                    sx += polyXs[i];
                    sy += polyYs[i];
                }
                n += pn;
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
            for (int i = 0; i < coverageXs.length; i++) {
                if (pointInPolygon(coverageXs[i], coverageYs[i], x, y)) return true;
            }
            return false;
        }

        public double distanceSqToCentroid(double x, double y) {
            double dx = x - centroidX;
            double dy = y - centroidY;
            return dx * dx + dy * dy;
        }

        public double distanceSqToBoundary(double x, double y) {
            double best = Double.POSITIVE_INFINITY;
            for (int poly = 0; poly < coverageXs.length; poly++) {
                double[] polyXs = coverageXs[poly];
                double[] polyYs = coverageYs[poly];
                int n = Math.min(polyXs.length, polyYs.length);
                if (n < 2) continue;
                for (int i = 0, j = n - 1; i < n; j = i++) {
                    double dsq = pointSegmentDistanceSq(x, y, polyXs[j], polyYs[j], polyXs[i], polyYs[i]);
                    if (dsq < best) best = dsq;
                }
            }
            return best;
        }

        public boolean overlapsAabb(double minX, double minY, double maxX, double maxY) {
            if (minX > maxX || minY > maxY) return false;
            for (int poly = 0; poly < coverageXs.length; poly++) {
                double[] polyXs = coverageXs[poly];
                double[] polyYs = coverageYs[poly];
                int n = Math.min(polyXs.length, polyYs.length);
                if (n < 3) continue;

                for (int i = 0; i < n; i++) {
                    double px = polyXs[i];
                    double py = polyYs[i];
                    if (px >= minX && px <= maxX && py >= minY && py <= maxY) return true;
                }
                if (pointInPolygon(polyXs, polyYs, minX, minY)) return true;
                if (pointInPolygon(polyXs, polyYs, minX, maxY)) return true;
                if (pointInPolygon(polyXs, polyYs, maxX, minY)) return true;
                if (pointInPolygon(polyXs, polyYs, maxX, maxY)) return true;
                for (int i = 0, j = n - 1; i < n; j = i++) {
                    double ax = polyXs[j];
                    double ay = polyYs[j];
                    double bx = polyXs[i];
                    double by = polyYs[i];
                    if (segmentIntersectsAabb(ax, ay, bx, by, minX, minY, maxX, maxY)) return true;
                }
            }
            return false;
        }

        private static double[][] normalizeCoverage(double[][] coverage, double[] fallback) {
            if (coverage != null && coverage.length > 0) return cloneNested(coverage);
            if (fallback == null) return new double[0][];
            return new double[][]{fallback.clone()};
        }

        private static double[][] cloneNested(double[][] src) {
            double[][] out = new double[src.length][];
            for (int i = 0; i < src.length; i++) {
                out[i] = (src[i] == null) ? new double[0] : src[i].clone();
            }
            return out;
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
    private static final EnumMap<ShipRole, EnumMap<Faction, String>> LAYOUT_KEYS = buildLayoutKeys();

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
        List<VisualCell> built = profile.visualCells.isEmpty()
                ? buildVisualCells(role, faction, profile.rooms)
                : profile.visualCells;
        VISUAL_CACHE.put(key, built);
        return built;
    }

    public static RoomDef roomForHit(ShipRole role, double normalizedX, double normalizedY) {
        return roomForHit(role, null, normalizedX, normalizedY);
    }

    public static RoomDef roomForHit(ShipRole role, Faction faction, double normalizedX, double normalizedY) {
        RoomDef resolved = RoomHitResolver.resolve(role, faction, normalizedX, normalizedY);
        if (resolved != null) {
            double boundarySq = RoomHitResolver.distanceToBoundarySq(resolved, normalizedX, normalizedY);
            if (resolved.contains(normalizedX, normalizedY) || boundarySq <= 1e-10) {
                return resolved;
            }
        }

        RoomId visualRoomId = visualRoomIdForHit(role, faction, normalizedX, normalizedY);
        if (visualRoomId != null) {
            RoomDef visualRoom = roomForId(role, faction, visualRoomId);
            if (visualRoom != null) return visualRoom;
        }
        return resolved;
    }

    public static RoomDef roomForId(ShipRole role, RoomId id) {
        return roomForId(role, null, id);
    }

    public static RoomDef roomForId(ShipRole role, Faction faction, RoomId id) {
        if (id == null) return null;
        return profile(role, faction).byId.get(id);
    }

    public static RoomId visualRoomIdForHit(ShipRole role, Faction faction, double normalizedX, double normalizedY) {
        if (!Double.isFinite(normalizedX) || !Double.isFinite(normalizedY)) return null;
        List<VisualCell> cells = visualCellsFor(role, faction);
        if (cells == null || cells.isEmpty()) return null;

        VisualCell containing = null;
        double containingBoundary = Double.POSITIVE_INFINITY;
        double containingCentroid = Double.POSITIVE_INFINITY;

        VisualCell nearest = null;
        double nearestBoundary = Double.POSITIVE_INFINITY;
        double nearestCentroid = Double.POSITIVE_INFINITY;

        for (VisualCell cell : cells) {
            if (cell == null || cell.roomId == null) continue;
            double boundarySq = distanceToBoundarySq(cell.xs, cell.ys, normalizedX, normalizedY);
            double centroidSq = distanceSqToCentroid(cell.xs, cell.ys, normalizedX, normalizedY);
            boolean inside = pointInPolygon(cell.xs, cell.ys, normalizedX, normalizedY);
            boolean onBoundary = boundarySq <= 1e-10;

            if (inside || onBoundary) {
                if (boundarySq < containingBoundary
                        || (Math.abs(boundarySq - containingBoundary) <= 1e-12 && centroidSq < containingCentroid)
                        || (Math.abs(boundarySq - containingBoundary) <= 1e-12
                        && Math.abs(centroidSq - containingCentroid) <= 1e-12
                        && compareRoomId(cell.roomId, containing == null ? null : containing.roomId) < 0)) {
                    containing = cell;
                    containingBoundary = boundarySq;
                    containingCentroid = centroidSq;
                }
            }

            if (boundarySq < nearestBoundary
                    || (Math.abs(boundarySq - nearestBoundary) <= 1e-12 && centroidSq < nearestCentroid)
                    || (Math.abs(boundarySq - nearestBoundary) <= 1e-12
                    && Math.abs(centroidSq - nearestCentroid) <= 1e-12
                    && compareRoomId(cell.roomId, nearest == null ? null : nearest.roomId) < 0)) {
                nearest = cell;
                nearestBoundary = boundarySq;
                nearestCentroid = centroidSq;
            }
        }

        if (containing != null) return containing.roomId;
        return (nearest == null) ? null : nearest.roomId;
    }

    public static String displayLabel(RoomId roomId) {
        if (roomId == null) return "UNKNOWN";
        return switch (roomId) {
            case BRIDGE -> "BRIDGE";
            case SENSORS -> "SENSORS";
            case BOW -> "BOW SECTION";
            case BOW_ARMOR -> "BOW ARMOR";
            case BOW_ARMOR_INNER -> "INNER BOW ARMOR";
            case BOW_SHIELD_STRIP -> "BOW SHIELD STRIP";
            case DORSAL_ARMOR -> "DORSAL ARMOR";
            case DORSAL_ARMOR_INNER -> "INNER DORSAL ARMOR";
            case DORSAL_SHIELD_STRIP -> "DORSAL SHIELD STRIP";
            case VENTRAL_ARMOR -> "VENTRAL ARMOR";
            case VENTRAL_ARMOR_INNER -> "INNER VENTRAL ARMOR";
            case VENTRAL_SHIELD_STRIP -> "VENTRAL SHIELD STRIP";
            case AFT_ARMOR -> "AFT ARMOR";
            case AFT_ARMOR_INNER -> "INNER AFT ARMOR";
            case AFT_SHIELD_STRIP -> "AFT SHIELD STRIP";
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
            case BOW_ARMOR_INNER, DORSAL_ARMOR_INNER, VENTRAL_ARMOR_INNER, AFT_ARMOR_INNER -> "INR";
            case BOW_SHIELD_STRIP, DORSAL_SHIELD_STRIP, VENTRAL_SHIELD_STRIP, AFT_SHIELD_STRIP -> "STR";
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
            case BOW_ARMOR, DORSAL_ARMOR, VENTRAL_ARMOR, AFT_ARMOR,
                 BOW_ARMOR_INNER, DORSAL_ARMOR_INNER, VENTRAL_ARMOR_INNER, AFT_ARMOR_INNER,
                 BOW_SHIELD_STRIP, DORSAL_SHIELD_STRIP, VENTRAL_SHIELD_STRIP, AFT_SHIELD_STRIP -> true;
            default -> false;
        };
    }

    public static boolean isShieldStripRoom(RoomId roomId) {
        if (roomId == null) return false;
        return switch (roomId) {
            case BOW_SHIELD_STRIP, DORSAL_SHIELD_STRIP, VENTRAL_SHIELD_STRIP, AFT_SHIELD_STRIP -> true;
            default -> false;
        };
    }

    public static boolean isInnerArmorRoom(RoomId roomId) {
        if (roomId == null) return false;
        return switch (roomId) {
            case BOW_ARMOR_INNER, DORSAL_ARMOR_INNER, VENTRAL_ARMOR_INNER, AFT_ARMOR_INNER -> true;
            default -> false;
        };
    }

    public static RoomId outerArmorRoomFor(RoomId roomId) {
        if (roomId == null) return null;
        return switch (roomId) {
            case BOW_ARMOR, BOW_ARMOR_INNER, BOW_SHIELD_STRIP -> RoomId.BOW_ARMOR;
            case DORSAL_ARMOR, DORSAL_ARMOR_INNER, DORSAL_SHIELD_STRIP -> RoomId.DORSAL_ARMOR;
            case VENTRAL_ARMOR, VENTRAL_ARMOR_INNER, VENTRAL_SHIELD_STRIP -> RoomId.VENTRAL_ARMOR;
            case AFT_ARMOR, AFT_ARMOR_INNER, AFT_SHIELD_STRIP -> RoomId.AFT_ARMOR;
            default -> null;
        };
    }

    public static RoomId innerArmorRoomFor(RoomId roomId) {
        if (roomId == null) return null;
        return switch (roomId) {
            case BOW_ARMOR, BOW_ARMOR_INNER, BOW_SHIELD_STRIP -> RoomId.BOW_ARMOR_INNER;
            case DORSAL_ARMOR, DORSAL_ARMOR_INNER, DORSAL_SHIELD_STRIP -> RoomId.DORSAL_ARMOR_INNER;
            case VENTRAL_ARMOR, VENTRAL_ARMOR_INNER, VENTRAL_SHIELD_STRIP -> RoomId.VENTRAL_ARMOR_INNER;
            case AFT_ARMOR, AFT_ARMOR_INNER, AFT_SHIELD_STRIP -> RoomId.AFT_ARMOR_INNER;
            default -> null;
        };
    }

    public static RoomId shieldStripRoomFor(RoomId roomId) {
        if (roomId == null) return null;
        return switch (roomId) {
            case BOW_ARMOR, BOW_ARMOR_INNER, BOW_SHIELD_STRIP -> RoomId.BOW_SHIELD_STRIP;
            case DORSAL_ARMOR, DORSAL_ARMOR_INNER, DORSAL_SHIELD_STRIP -> RoomId.DORSAL_SHIELD_STRIP;
            case VENTRAL_ARMOR, VENTRAL_ARMOR_INNER, VENTRAL_SHIELD_STRIP -> RoomId.VENTRAL_SHIELD_STRIP;
            case AFT_ARMOR, AFT_ARMOR_INNER, AFT_SHIELD_STRIP -> RoomId.AFT_SHIELD_STRIP;
            default -> null;
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

        Profile template = switch (profileKey(role)) {
            case "small" -> buildSmallProfile(role, faction);
            case "carrier" -> buildCarrierProfile(role, faction);
            case "station" -> buildStationProfile(role, faction);
            case "titan" -> buildTitanProfile(role, faction);
            default -> buildCapitalProfile(role, faction);
        };
        Profile built = buildGeneratedProfile(role, faction, template);
        if (built == null) built = template;
        PROFILES.put(key, built);
        return built;
    }

    private static String layoutKey(ShipRole role, Faction faction) {
        ShipRole roleKey = (role == null) ? ShipRole.CRUISER : role;
        Faction factionKey = (faction == null) ? Faction.ALLY : faction;
        EnumMap<Faction, String> byFaction = LAYOUT_KEYS.get(roleKey);
        if (byFaction == null) {
            return roleKey.name() + "|" + keyForFaction(factionKey);
        }
        String cached = byFaction.get(factionKey);
        if (cached != null) return cached;
        return roleKey.name() + "|" + keyForFaction(factionKey);
    }

    private static String profileKey(ShipRole role) {
        if (role == null) return "capital";
        if (role.isTitanOrMothership()) return "titan";
        return switch (role) {
            case FIGHTER, BOMBER, DRONE, PD_CRAFT, PICKET, PATROL, STEALTH_SHIP,
                 FRIGATE, ARTILLERY_SHIP, MISSILE_BOAT, CIWS_CORVETTE -> "small";
            case CARRIER, DRONE_CARRIER, TRANSPORT, HAULER, MINER -> "carrier";
            case BASE, STATIC_TURRET -> "station";
            default -> "capital";
        };
    }

    private static Profile buildTitanProfile(ShipRole role, Faction faction) {
        if (role == null) return buildCapitalProfile(null, faction);
        return switch (role) {
            case TRANSPORT_TITAN,
                 CARRIER_SUPPORT_TITAN,
                 BOARDING_RECOVERY_TITAN,
                 MOBILE_STATION_TITAN,
                 MOTHERSHIP -> buildCarrierProfile(role, faction);
            default -> buildCapitalProfile(role, faction);
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
        addPerimeterDefenseRooms(hull, rooms, faction);
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
        addPerimeterDefenseRooms(hull, rooms, faction);
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
        addPerimeterDefenseRooms(hull, rooms, faction);
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
        addPerimeterDefenseRooms(hull, rooms, faction);
        return new Profile(rooms, Collections.emptyList());
    }

    private static void addPerimeterDefenseRooms(HullProfile hull, List<RoomDef> rooms, Faction faction) {
        if (faction == Faction.TEAM_C) {
            addShieldStripRooms(hull, rooms);
            return;
        }
        addArmorRooms(hull, rooms);
        if (faction != null && faction.isYellowLineage()) {
            addInnerArmorRooms(hull, rooms);
        }
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

    private static void addInnerArmorRooms(HullProfile hull, List<RoomDef> rooms) {
        rooms.add(cell(hull, RoomId.DORSAL_ARMOR_INNER, displayLabel(RoomId.DORSAL_ARMOR_INNER), -0.74, 0.72, 0.08, 0.20, null, 0.28, false,
                RoomId.SENSORS, RoomId.BRIDGE, RoomId.CREW_QUARTERS, RoomId.PORT_BATTERY, RoomId.STARBOARD_BATTERY, RoomId.BOW));
        rooms.add(cell(hull, RoomId.VENTRAL_ARMOR_INNER, displayLabel(RoomId.VENTRAL_ARMOR_INNER), -0.74, 0.70, 0.80, 0.92, null, 0.30, false,
                RoomId.CARGO_BAY, RoomId.MAGAZINES, RoomId.PORT_ENGINES, RoomId.STARBOARD_ENGINES, RoomId.AFT_SPINE));
        rooms.add(cell(hull, RoomId.BOW_ARMOR_INNER, displayLabel(RoomId.BOW_ARMOR_INNER), 0.66, 0.90, 0.28, 0.72, null, 0.24, false,
                RoomId.BOW, RoomId.BRIDGE, RoomId.PORT_BATTERY, RoomId.STARBOARD_BATTERY, RoomId.MISSILE_LAUNCHERS));
        rooms.add(cell(hull, RoomId.AFT_ARMOR_INNER, displayLabel(RoomId.AFT_ARMOR_INNER), -0.90, -0.68, 0.28, 0.72, null, 0.24, false,
                RoomId.AFT_SPINE, RoomId.WARP_DRIVE, RoomId.PORT_ENGINES, RoomId.STARBOARD_ENGINES, RoomId.ENGINES));
    }

    private static void addShieldStripRooms(HullProfile hull, List<RoomDef> rooms) {
        rooms.add(cell(hull, RoomId.DORSAL_SHIELD_STRIP, displayLabel(RoomId.DORSAL_SHIELD_STRIP), -0.88, 0.88, 0.00, 0.11, null, 0.18, false,
                RoomId.SENSORS, RoomId.BRIDGE, RoomId.CREW_QUARTERS, RoomId.PORT_BATTERY, RoomId.STARBOARD_BATTERY, RoomId.BOW));
        rooms.add(cell(hull, RoomId.VENTRAL_SHIELD_STRIP, displayLabel(RoomId.VENTRAL_SHIELD_STRIP), -0.88, 0.84, 0.89, 1.00, null, 0.18, false,
                RoomId.CARGO_BAY, RoomId.MAGAZINES, RoomId.PORT_ENGINES, RoomId.STARBOARD_ENGINES, RoomId.AFT_SPINE));
        rooms.add(cell(hull, RoomId.BOW_SHIELD_STRIP, displayLabel(RoomId.BOW_SHIELD_STRIP), 0.80, 1.00, 0.18, 0.82, null, 0.16, false,
                RoomId.BOW, RoomId.BRIDGE, RoomId.PORT_BATTERY, RoomId.STARBOARD_BATTERY, RoomId.MISSILE_LAUNCHERS));
        rooms.add(cell(hull, RoomId.AFT_SHIELD_STRIP, displayLabel(RoomId.AFT_SHIELD_STRIP), -1.00, -0.82, 0.18, 0.82, null, 0.16, false,
                RoomId.AFT_SPINE, RoomId.WARP_DRIVE, RoomId.PORT_ENGINES, RoomId.STARBOARD_ENGINES, RoomId.ENGINES));
    }

    private static List<VisualCell> buildVisualCells(ShipRole role, Faction faction, List<RoomDef> rooms) {
        HullProfile hull = HullProfile.fromSilhouette(role, faction);
        if (hull == null) {
            hull = switch (profileKey(role)) {
                case "small" -> defaultSmallHull();
                case "titan" -> defaultCapitalHull();
                case "carrier" -> defaultCarrierHull();
                case "station" -> defaultStationHull();
                default -> defaultCapitalHull();
            };
        }

        List<VisualCell> hullConforming = buildGeneratedVisualCells(role, faction, hull, rooms);
        if (!hullConforming.isEmpty()) return hullConforming;

        return switch (profileKey(role)) {
            case "small" -> buildSmallVisualCells(hull, faction, rooms);
            case "titan" -> buildTitanVisualCells(role, faction, hull, rooms);
            case "carrier" -> buildCarrierVisualCells(hull, faction, rooms);
            case "station" -> buildStationVisualCells(hull, faction, rooms);
            default -> buildCapitalVisualCells(hull, faction, rooms);
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
            case TEAM_D, BRIGHT_YELLOW, DARK_YELLOW -> "team_d";
        };
    }

    private static EnumMap<ShipRole, EnumMap<Faction, String>> buildLayoutKeys() {
        EnumMap<ShipRole, EnumMap<Faction, String>> keys = new EnumMap<>(ShipRole.class);
        for (ShipRole role : ShipRole.values()) {
            EnumMap<Faction, String> byFaction = new EnumMap<>(Faction.class);
            for (Faction faction : Faction.values()) {
                ShipRole roleKey = (role == null) ? ShipRole.CRUISER : role;
                Faction factionKey = (faction == null) ? Faction.ALLY : faction;
                byFaction.put(faction, roleKey.name() + "|" + keyForFaction(factionKey));
            }
            keys.put(role, byFaction);
        }
        return keys;
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

    private static Profile buildGeneratedProfile(ShipRole role, Faction faction, Profile template) {
        if (template == null || template.rooms.isEmpty()) return null;
        HullProfile hull = HullProfile.fromSilhouette(role, faction);
        if (hull == null) return null;

        List<VisualCell> visualCells = buildGeneratedVisualCells(role, faction, hull, template.rooms);
        if (visualCells == null || visualCells.isEmpty()) return null;

        EnumMap<RoomId, Area> coverageAreas = new EnumMap<>(RoomId.class);
        for (VisualCell cell : visualCells) {
            if (cell == null || cell.roomId == null) continue;
            Area cellArea = polygonAreaShape(cell.xs, cell.ys);
            if (cellArea == null || cellArea.isEmpty()) continue;
            coverageAreas.computeIfAbsent(cell.roomId, key -> new Area()).add(cellArea);
        }

        List<RoomDef> rooms = new ArrayList<>(template.rooms.size());
        for (RoomDef base : template.rooms) {
            if (base == null || base.id == null) continue;
            RoomDef generated = roomFromCoverage(base, coverageAreas.get(base.id));
            rooms.add((generated != null) ? generated : base);
        }

        return new Profile(rooms, visualCells);
    }

    private static List<VisualCell> buildGeneratedVisualCells(ShipRole role,
                                                              Faction faction,
                                                              HullProfile hull,
                                                              List<RoomDef> rooms) {
        if ("titan".equals(profileKey(role))) {
            return buildTitanVisualCells(role, faction, hull, rooms);
        }
        return buildHullConformingVisualCells(role, faction, hull, rooms);
    }

    private static List<VisualCell> buildTitanVisualCells(ShipRole role,
                                                          Faction faction,
                                                          HullProfile hull,
                                                          List<RoomDef> rooms) {
        if (hull == null) return Collections.emptyList();

        int cols = (role == ShipRole.MOTHERSHIP) ? 42 : 38;
        int rows = (role == ShipRole.MOTHERSHIP) ? 24 : 22;

        List<VisualCell> raw = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            double y0 = -1.0 + 2.0 * row / (double) rows;
            double y1 = -1.0 + 2.0 * (row + 1) / (double) rows;
            RoomId activeRoom = null;
            int spanStart = -1;

            for (int col = 0; col <= cols; col++) {
                RoomId roomId = null;
                if (col < cols) {
                    double x0 = -1.0 + 2.0 * col / (double) cols;
                    double x1 = -1.0 + 2.0 * (col + 1) / (double) cols;
                    double cx = (x0 + x1) * 0.5;
                    double cy = (y0 + y1) * 0.5;
                    if (pointInsideHull(hull, cx, cy)) {
                        roomId = titanRoomForPoint(role, faction, hull, cx, cy);
                    }
                }

                if (col == 0) {
                    activeRoom = roomId;
                    spanStart = (roomId == null) ? -1 : 0;
                    continue;
                }

                if (roomId == activeRoom) continue;

                if (activeRoom != null && spanStart >= 0) {
                    double spanX0 = -1.0 + 2.0 * spanStart / (double) cols;
                    double spanX1 = -1.0 + 2.0 * col / (double) cols;
                    VisualCell cell = hullGridCell(hull, activeRoom, spanX0, spanX1, y0, y1);
                    if (cell != null) raw.add(cell);
                }

                activeRoom = roomId;
                spanStart = (roomId == null) ? -1 : col;
            }
        }

        if (raw.isEmpty()) return Collections.emptyList();
        appendPerimeterShellCells(raw, hull, faction);
        return assignVisualLabels(raw, rooms);
    }

    private static RoomId titanRoomForPoint(ShipRole role,
                                            Faction faction,
                                            HullProfile hull,
                                            double x,
                                            double y) {
        double top = hull.innerY(x, 0.0);
        double bottom = hull.innerY(x, 1.0);
        double thickness = Math.max(1e-4, bottom - top);
        double v = MathUtil.clamp((y - top) / thickness, 0.0, 1.0);
        double edgeFrac = Math.min(v, 1.0 - v);
        boolean topSide = v <= 0.5;
        double along = MathUtil.clamp((x + 1.0) * 0.5, 0.0, 1.0);

        if (faction != null && faction.isYellowLineage() && edgeFrac <= 0.18) {
            if (edgeFrac <= 0.09) {
                RoomId outer = outerDefenseRoomForSpan(x, topSide, faction);
                if (outer != null) return outer;
            }
            RoomId inner = innerArmorRoomFor(outerArmorRoomForSpan(x, topSide));
            if (inner != null) return inner;
        } else if (edgeFrac <= 0.08) {
            RoomId perimeter = outerDefenseRoomForSpan(x, topSide, faction);
            if (perimeter != null) return perimeter;
        }

        TitanRoomMode mode = titanRoomMode(role);
        boolean upper = v < 0.34;
        boolean lower = v > 0.66;
        boolean centerline = !upper && !lower;
        boolean extremeUpper = v < 0.18;
        boolean extremeLower = v > 0.82;

        if (along <= 0.08) {
            return centerline ? RoomId.AFT_SPINE : (topSide ? RoomId.PORT_ENGINES : RoomId.STARBOARD_ENGINES);
        }
        if (along <= 0.18) {
            if (centerline) return RoomId.WARP_DRIVE;
            return topSide ? RoomId.PORT_ENGINES : RoomId.STARBOARD_ENGINES;
        }
        if (along <= 0.30) {
            return switch (mode) {
                case LOGISTICS, MOTHERSHIP -> centerline ? RoomId.ENGINES : (topSide ? RoomId.SERVICE_BAY : RoomId.CARGO_BAY);
                case CONTROL -> centerline ? RoomId.ENGINES : (topSide ? RoomId.PORT_POWER : RoomId.STARBOARD_POWER);
                default -> centerline ? RoomId.ENGINES : (topSide ? RoomId.PORT_ENGINES : RoomId.STARBOARD_ENGINES);
            };
        }
        if (along <= 0.44) {
            return switch (mode) {
                case LOGISTICS -> {
                    if (centerline) yield RoomId.POWER_CONDUITS;
                    yield topSide ? RoomId.SERVICE_BAY : RoomId.CARGO_BAY;
                }
                case CONTROL -> {
                    if (centerline) yield RoomId.POWER_CONDUITS;
                    yield topSide ? RoomId.INTEGRITY_FIELD : RoomId.MISSILE_LAUNCHERS;
                }
                case BASTION -> {
                    if (centerline) yield RoomId.REACTOR;
                    yield topSide ? RoomId.INTEGRITY_FIELD : RoomId.MAGAZINES;
                }
                case ARTILLERY -> {
                    if (centerline) yield RoomId.MAIN_WEAPON;
                    yield topSide ? RoomId.PORT_BATTERY : RoomId.STARBOARD_BATTERY;
                }
                case MOTHERSHIP -> {
                    if (centerline) yield RoomId.REACTOR;
                    yield topSide ? RoomId.SERVICE_BAY : RoomId.CARGO_BAY;
                }
                default -> {
                    if (centerline) yield RoomId.REACTOR;
                    yield topSide ? RoomId.PORT_POWER : RoomId.STARBOARD_POWER;
                }
            };
        }
        if (along <= 0.60) {
            return switch (mode) {
                case LOGISTICS -> {
                    if (centerline) yield (role == ShipRole.TRANSPORT_TITAN) ? RoomId.CARGO_BAY : RoomId.REACTOR;
                    yield topSide ? RoomId.SERVICE_BAY : RoomId.CARGO_BAY;
                }
                case CONTROL -> {
                    if (centerline) yield RoomId.INTEGRITY_FIELD;
                    yield topSide ? RoomId.SENSORS : RoomId.MISSILE_LAUNCHERS;
                }
                case BASTION -> {
                    if (centerline) yield RoomId.INTEGRITY_FIELD;
                    yield topSide ? RoomId.PORT_BATTERY : RoomId.STARBOARD_BATTERY;
                }
                case ARTILLERY -> {
                    if (centerline) yield RoomId.MAIN_WEAPON;
                    yield topSide ? RoomId.PORT_BATTERY : RoomId.STARBOARD_BATTERY;
                }
                case MOTHERSHIP -> {
                    if (centerline) yield RoomId.MAIN_WEAPON;
                    yield topSide ? RoomId.CREW_QUARTERS : RoomId.CARGO_BAY;
                }
                default -> {
                    if (centerline) yield RoomId.MAIN_WEAPON;
                    yield topSide ? RoomId.PORT_BATTERY : RoomId.STARBOARD_BATTERY;
                }
            };
        }
        if (along <= 0.74) {
            return switch (mode) {
                case LOGISTICS -> {
                    if (centerline) yield RoomId.CREW_QUARTERS;
                    yield topSide ? RoomId.SENSORS : RoomId.MISSILE_LAUNCHERS;
                }
                case CONTROL -> {
                    if (centerline) yield RoomId.BRIDGE;
                    yield topSide ? RoomId.SENSORS : RoomId.MISSILE_LAUNCHERS;
                }
                case BASTION -> {
                    if (centerline) yield RoomId.BRIDGE;
                    yield topSide ? RoomId.SENSORS : RoomId.MISSILE_LAUNCHERS;
                }
                case ARTILLERY -> {
                    if (centerline) yield RoomId.MAIN_WEAPON;
                    yield topSide ? RoomId.BRIDGE : RoomId.MISSILE_LAUNCHERS;
                }
                case MOTHERSHIP -> {
                    if (centerline) yield RoomId.BRIDGE;
                    yield topSide ? RoomId.SENSORS : RoomId.MISSILE_LAUNCHERS;
                }
                default -> {
                    if (centerline) yield RoomId.BRIDGE;
                    yield topSide ? RoomId.PORT_BATTERY : RoomId.STARBOARD_BATTERY;
                }
            };
        }
        if (along <= 0.88) {
            if (centerline) {
                return switch (mode) {
                    case ARTILLERY -> RoomId.MAIN_WEAPON;
                    case LOGISTICS -> RoomId.BOW;
                    default -> RoomId.BOW;
                };
            }
            if (mode == TitanRoomMode.CONTROL || mode == TitanRoomMode.MOTHERSHIP) {
                return topSide ? RoomId.SENSORS : RoomId.MISSILE_LAUNCHERS;
            }
            return topSide ? RoomId.PORT_BATTERY : RoomId.STARBOARD_BATTERY;
        }

        if (centerline || (!extremeUpper && !extremeLower)) return RoomId.BOW;
        return topSide ? RoomId.PORT_BATTERY : RoomId.STARBOARD_BATTERY;
    }

    private static TitanRoomMode titanRoomMode(ShipRole role) {
        if (role == null) return TitanRoomMode.ASSAULT;
        return switch (role) {
            case TRANSPORT_TITAN,
                 CARRIER_SUPPORT_TITAN,
                 BOARDING_RECOVERY_TITAN,
                 MOBILE_STATION_TITAN -> TitanRoomMode.LOGISTICS;
            case INTERDICTION_TITAN,
                 COMMAND_INTEL_TITAN,
                 FLEET_TELEPORTER_TITAN -> TitanRoomMode.CONTROL;
            case BULWARK_TITAN,
                 SHIELD_BASTION_TITAN -> TitanRoomMode.BASTION;
            case ARTILLERY_TITAN,
                 HYPERWEAPON_TITAN -> TitanRoomMode.ARTILLERY;
            case MOTHERSHIP -> TitanRoomMode.MOTHERSHIP;
            default -> TitanRoomMode.ASSAULT;
        };
    }

    private enum TitanRoomMode {
        LOGISTICS,
        CONTROL,
        BASTION,
        ARTILLERY,
        ASSAULT,
        MOTHERSHIP
    }

    private static List<VisualCell> buildHullConformingVisualCells(ShipRole role,
                                                                   Faction faction,
                                                                   HullProfile hull,
                                                                   List<RoomDef> rooms) {
        if (hull == null) return Collections.emptyList();

        int cols = switch (profileKey(role)) {
            case "small" -> 18;
            case "station" -> 18;
            case "carrier" -> 24;
            default -> 26;
        };
        int rows = switch (profileKey(role)) {
            case "small" -> 12;
            case "station" -> 18;
            case "carrier" -> 14;
            default -> 16;
        };

        List<VisualCell> raw = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            double y0 = -1.0 + 2.0 * row / (double) rows;
            double y1 = -1.0 + 2.0 * (row + 1) / (double) rows;
            RoomId activeRoom = null;
            int spanStart = -1;

            for (int col = 0; col <= cols; col++) {
                RoomId roomId = null;
                if (col < cols) {
                    double x0 = -1.0 + 2.0 * col / (double) cols;
                    double x1 = -1.0 + 2.0 * (col + 1) / (double) cols;
                    double cx = (x0 + x1) * 0.5;
                    double cy = (y0 + y1) * 0.5;
                    if (pointInsideHull(hull, cx, cy)) {
                        roomId = visualRoomForPoint(rooms, faction, hull, cx, cy);
                    }
                }

                if (col == 0) {
                    activeRoom = roomId;
                    spanStart = (roomId == null) ? -1 : 0;
                    continue;
                }

                if (roomId == activeRoom) continue;

                if (activeRoom != null && spanStart >= 0) {
                    double spanX0 = -1.0 + 2.0 * spanStart / (double) cols;
                    double spanX1 = -1.0 + 2.0 * col / (double) cols;
                    VisualCell cell = hullGridCell(hull, activeRoom, spanX0, spanX1, y0, y1);
                    if (cell != null) raw.add(cell);
                }

                activeRoom = roomId;
                spanStart = (roomId == null) ? -1 : col;
            }
        }

        if (raw.isEmpty()) return Collections.emptyList();
        return assignVisualLabels(raw, rooms);
    }

    private static boolean pointInsideHull(HullProfile hull, double x, double y) {
        if (hull == null) return false;
        double top = hull.innerY(x, 0.0);
        double bottom = hull.innerY(x, 1.0);
        return y >= top && y <= bottom;
    }

    private static RoomId visualRoomForPoint(List<RoomDef> templateRooms,
                                             Faction faction,
                                             HullProfile hull,
                                             double x,
                                             double y) {
        double top = hull.innerY(x, 0.0);
        double bottom = hull.innerY(x, 1.0);
        double thickness = Math.max(1e-4, bottom - top);
        double distTop = Math.max(0.0, y - top);
        double distBottom = Math.max(0.0, bottom - y);
        double edgeFrac = Math.min(distTop, distBottom) / thickness;
        boolean topSide = distTop <= distBottom;

        if (faction != null && faction.isYellowLineage() && edgeFrac <= 0.22) {
            if (edgeFrac <= 0.10) {
                RoomId outer = outerDefenseRoomForSpan(x, topSide, faction);
                if (outer != null) return outer;
            }
            RoomId inner = innerArmorRoomFor(outerArmorRoomForSpan(x, topSide));
            if (inner != null) return inner;
        } else if (edgeFrac <= 0.10) {
            RoomId perimeter = outerDefenseRoomForSpan(x, topSide, faction);
            if (perimeter != null) return perimeter;
        }

        RoomDef resolved = resolveFromTemplate(templateRooms, x, y);
        if (resolved != null && resolved.id != null) return resolved.id;
        return outerDefenseRoomForSpan(x, topSide, faction);
    }

    private static VisualCell hullGridCell(HullProfile hull,
                                           RoomId roomId,
                                           double x0,
                                           double x1,
                                           double y0,
                                           double y1) {
        if (hull == null || roomId == null) return null;
        double xa = Math.max(-1.0, Math.min(1.0, Math.min(x0, x1) + 0.0015));
        double xb = Math.max(-1.0, Math.min(1.0, Math.max(x0, x1) - 0.0015));
        if (xb - xa <= 0.01) return null;

        int samples = Math.max(4, Math.min(10, (int) Math.round((xb - xa) * 18.0)));
        List<Double> topXs = new ArrayList<>(samples);
        List<Double> topYs = new ArrayList<>(samples);
        List<Double> bottomXs = new ArrayList<>(samples);
        List<Double> bottomYs = new ArrayList<>(samples);

        for (int i = 0; i < samples; i++) {
            double t = (samples == 1) ? 0.0 : i / (double) (samples - 1);
            double x = xa + (xb - xa) * t;
            double top = Math.max(y0 + 0.0015, hull.innerY(x, 0.0));
            double bottom = Math.min(y1 - 0.0015, hull.innerY(x, 1.0));
            if (bottom <= top + 0.0015) continue;
            topXs.add(x);
            topYs.add(top);
            bottomXs.add(x);
            bottomYs.add(bottom);
        }

        int n = topXs.size();
        if (n < 2) return null;

        double[] xs = new double[n * 2];
        double[] ys = new double[n * 2];
        for (int i = 0; i < n; i++) {
            xs[i] = topXs.get(i);
            ys[i] = topYs.get(i);
        }
        for (int i = 0; i < n; i++) {
            int src = n - 1 - i;
            xs[n + i] = bottomXs.get(src);
            ys[n + i] = bottomYs.get(src);
        }

        if (polygonArea(xs, ys) <= 0.0004) return null;
        return new VisualCell(roomId, xs, ys, false);
    }

    private static RoomDef resolveFromTemplate(List<RoomDef> rooms, double normalizedX, double normalizedY) {
        if (rooms == null || rooms.isEmpty()) return null;
        RoomDef containing = null;
        int containingCount = 0;
        double containingBestScore = Double.POSITIVE_INFINITY;
        double containingBestCentroid = Double.POSITIVE_INFINITY;

        RoomDef nearest = null;
        double nearestBoundary = Double.POSITIVE_INFINITY;
        double nearestCentroid = Double.POSITIVE_INFINITY;

        for (RoomDef room : rooms) {
            if (room == null) continue;

            double boundarySq = room.distanceSqToBoundary(normalizedX, normalizedY);
            double centroidSq = room.distanceSqToCentroid(normalizedX, normalizedY);
            boolean inside = room.contains(normalizedX, normalizedY);
            boolean onBoundary = boundarySq <= 1e-10;

            if (inside || onBoundary) {
                containingCount++;
                double score = inside ? 0.0 : boundarySq;
                if (score < containingBestScore
                        || (Math.abs(score - containingBestScore) <= 1e-12 && centroidSq < containingBestCentroid)
                        || (Math.abs(score - containingBestScore) <= 1e-12
                        && Math.abs(centroidSq - containingBestCentroid) <= 1e-12
                        && compareRoomId(room.id, containing == null ? null : containing.id) < 0)) {
                    containing = room;
                    containingBestScore = score;
                    containingBestCentroid = centroidSq;
                }
            }

            if (boundarySq < nearestBoundary
                    || (Math.abs(boundarySq - nearestBoundary) <= 1e-12 && centroidSq < nearestCentroid)
                    || (Math.abs(boundarySq - nearestBoundary) <= 1e-12
                    && Math.abs(centroidSq - nearestCentroid) <= 1e-12
                    && compareRoomId(room.id, nearest == null ? null : nearest.id) < 0)) {
                nearest = room;
                nearestBoundary = boundarySq;
                nearestCentroid = centroidSq;
            }
        }

        if (containingCount > 0 && containing != null) return containing;
        return nearest;
    }

    private static RoomDef roomFromCoverage(RoomDef base, Area area) {
        if (base == null || area == null || area.isEmpty()) return null;
        List<PolygonData> polygons = extractPolygons(area);
        if (polygons.isEmpty()) return null;

        PolygonData primary = polygons.get(0);
        for (PolygonData candidate : polygons) {
            if (candidate.area > primary.area) primary = candidate;
        }

        double[][] coverageXs = new double[polygons.size()][];
        double[][] coverageYs = new double[polygons.size()][];
        for (int i = 0; i < polygons.size(); i++) {
            coverageXs[i] = polygons.get(i).xs;
            coverageYs[i] = polygons.get(i).ys;
        }

        return new RoomDef(
                base.id,
                base.label,
                primary.xs,
                primary.ys,
                coverageXs,
                coverageYs,
                base.primarySystem,
                base.hpWeight,
                base.critical,
                base.neighbors
        );
    }

    private static Area polygonAreaShape(double[] xs, double[] ys) {
        int n = Math.min(xs.length, ys.length);
        if (n < 3) return null;
        Path2D.Double path = new Path2D.Double();
        path.moveTo(xs[0], ys[0]);
        for (int i = 1; i < n; i++) path.lineTo(xs[i], ys[i]);
        path.closePath();
        return new Area(path);
    }

    private static List<PolygonData> extractPolygons(Area area) {
        if (area == null || area.isEmpty()) return Collections.emptyList();
        List<PolygonData> out = new ArrayList<>();
        PathIterator it = area.getPathIterator(null, 0.0015);
        List<Double> xs = new ArrayList<>();
        List<Double> ys = new ArrayList<>();
        double[] coords = new double[6];
        while (!it.isDone()) {
            int type = it.currentSegment(coords);
            switch (type) {
                case PathIterator.SEG_MOVETO -> {
                    if (!xs.isEmpty()) {
                        PolygonData poly = polygonData(xs, ys);
                        if (poly != null) out.add(poly);
                        xs.clear();
                        ys.clear();
                    }
                    xs.add(coords[0]);
                    ys.add(coords[1]);
                }
                case PathIterator.SEG_LINETO -> {
                    xs.add(coords[0]);
                    ys.add(coords[1]);
                }
                case PathIterator.SEG_CLOSE -> {
                    PolygonData poly = polygonData(xs, ys);
                    if (poly != null) out.add(poly);
                    xs = new ArrayList<>();
                    ys = new ArrayList<>();
                }
                default -> {}
            }
            it.next();
        }
        if (!xs.isEmpty()) {
            PolygonData poly = polygonData(xs, ys);
            if (poly != null) out.add(poly);
        }
        return out;
    }

    private static PolygonData polygonData(List<Double> xs, List<Double> ys) {
        int n = Math.min(xs.size(), ys.size());
        if (n < 3) return null;
        double[] xa = new double[n];
        double[] ya = new double[n];
        for (int i = 0; i < n; i++) {
            xa[i] = xs.get(i);
            ya[i] = ys.get(i);
        }
        double area = polygonArea(xa, ya);
        if (area <= 0.0002) return null;
        return new PolygonData(xa, ya, area);
    }

    private static List<VisualCell> buildSmallVisualCells(HullProfile hull, Faction faction, List<RoomDef> rooms) {
        return buildDeckGrid(hull, faction, rooms, new RowTemplate[]{
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

    private static List<VisualCell> buildCapitalVisualCells(HullProfile hull, Faction faction, List<RoomDef> rooms) {
        return buildDeckGrid(hull, faction, rooms, new RowTemplate[]{
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

    private static List<VisualCell> buildCarrierVisualCells(HullProfile hull, Faction faction, List<RoomDef> rooms) {
        return buildDeckGrid(hull, faction, rooms, new RowTemplate[]{
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

    private static List<VisualCell> buildStationVisualCells(HullProfile hull, Faction faction, List<RoomDef> rooms) {
        return buildDeckGrid(hull, faction, rooms, new RowTemplate[]{
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

    private static List<VisualCell> buildDeckGrid(HullProfile hull, Faction faction, List<RoomDef> rooms, RowTemplate[] rows) {
        List<VisualCell> raw = new ArrayList<>();
        RowTemplate[] mappedRows = remapDefenseRows(faction, rows);
        if (mappedRows != null) {
            for (RowTemplate row : mappedRows) {
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
        appendPerimeterShellCells(raw, hull, faction);
        return assignVisualLabels(raw, rooms);
    }

    private static void appendPerimeterShellCells(List<VisualCell> raw, HullProfile hull, Faction faction) {
        if (raw == null || hull == null) return;

        double[] shellCuts = new double[]{-1.00, -0.78, -0.54, -0.28, 0.00, 0.28, 0.54, 0.78, 1.00};
        for (int i = 0; i < shellCuts.length - 1; i++) {
            double x0 = shellCuts[i];
            double x1 = shellCuts[i + 1];
            RoomId topRoom = outerDefenseRoomForSpan((x0 + x1) * 0.5, true, faction);
            RoomId bottomRoom = outerDefenseRoomForSpan((x0 + x1) * 0.5, false, faction);
            VisualCell top = shellStripCell(hull, topRoom, x0, x1, 0.00, 0.12);
            VisualCell bottom = shellStripCell(hull, bottomRoom, x0, x1, 0.88, 1.00);
            if (top != null) raw.add(top);
            if (bottom != null) raw.add(bottom);
        }

        VisualCell aftCap = endCapCell(hull, remapOuterDefenseRoom(RoomId.AFT_ARMOR, faction), -1.00, -0.82, 0.18, 0.82);
        VisualCell bowCap = endCapCell(hull, remapOuterDefenseRoom(RoomId.BOW_ARMOR, faction), 0.82, 1.00, 0.18, 0.82);
        if (aftCap != null) raw.add(aftCap);
        if (bowCap != null) raw.add(bowCap);

        if (faction != null && faction.isYellowLineage()) {
            appendInnerArmorShellCells(raw, hull);
        }
    }

    private static void appendInnerArmorShellCells(List<VisualCell> raw, HullProfile hull) {
        double[] shellCuts = new double[]{-0.92, -0.70, -0.44, -0.18, 0.10, 0.36, 0.62, 0.86};
        for (int i = 0; i < shellCuts.length - 1; i++) {
            double x0 = shellCuts[i];
            double x1 = shellCuts[i + 1];
            RoomId topRoom = innerArmorRoomFor(outerArmorRoomForSpan((x0 + x1) * 0.5, true));
            RoomId bottomRoom = innerArmorRoomFor(outerArmorRoomForSpan((x0 + x1) * 0.5, false));
            VisualCell top = shellStripCell(hull, topRoom, x0, x1, 0.10, 0.20);
            VisualCell bottom = shellStripCell(hull, bottomRoom, x0, x1, 0.80, 0.90);
            if (top != null) raw.add(top);
            if (bottom != null) raw.add(bottom);
        }

        VisualCell aftCap = endCapCell(hull, RoomId.AFT_ARMOR_INNER, -0.90, -0.68, 0.28, 0.72);
        VisualCell bowCap = endCapCell(hull, RoomId.BOW_ARMOR_INNER, 0.66, 0.90, 0.28, 0.72);
        if (aftCap != null) raw.add(aftCap);
        if (bowCap != null) raw.add(bowCap);
    }

    private static RoomId outerArmorRoomForSpan(double centerX, boolean top) {
        if (centerX >= 0.72) return RoomId.BOW_ARMOR;
        if (centerX <= -0.72) return RoomId.AFT_ARMOR;
        return top ? RoomId.DORSAL_ARMOR : RoomId.VENTRAL_ARMOR;
    }

    private static RoomId outerDefenseRoomForSpan(double centerX, boolean top, Faction faction) {
        return remapOuterDefenseRoom(outerArmorRoomForSpan(centerX, top), faction);
    }

    private static RoomId remapOuterDefenseRoom(RoomId roomId, Faction faction) {
        if (faction != Faction.TEAM_C) return roomId;
        RoomId remapped = shieldStripRoomFor(roomId);
        return (remapped != null) ? remapped : roomId;
    }

    private static RowTemplate[] remapDefenseRows(Faction faction, RowTemplate[] rows) {
        if (rows == null || rows.length == 0) return rows;
        RowTemplate[] mapped = new RowTemplate[rows.length];
        for (int i = 0; i < rows.length; i++) {
            RowTemplate row = rows[i];
            if (row == null) {
                mapped[i] = null;
                continue;
            }
            RoomId[] roomIds = new RoomId[row.rooms.length];
            for (int j = 0; j < row.rooms.length; j++) {
                roomIds[j] = remapOuterDefenseRoom(row.rooms[j], faction);
            }
            mapped[i] = new RowTemplate(row.topFrac, row.bottomFrac, row.micro, roomIds);
        }
        return mapped;
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

    private static int compareRoomId(RoomId a, RoomId b) {
        if (a == b) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        return Integer.compare(a.ordinal(), b.ordinal());
    }

    private static double distanceSqToCentroid(double[] xs, double[] ys, double px, double py) {
        int n = Math.min(xs.length, ys.length);
        if (n <= 0) return Double.POSITIVE_INFINITY;
        double cx = 0.0;
        double cy = 0.0;
        for (int i = 0; i < n; i++) {
            cx += xs[i];
            cy += ys[i];
        }
        cx /= n;
        cy /= n;
        double dx = px - cx;
        double dy = py - cy;
        return dx * dx + dy * dy;
    }

    private static double distanceToBoundarySq(double[] xs, double[] ys, double px, double py) {
        int n = Math.min(xs.length, ys.length);
        if (n < 2) return Double.POSITIVE_INFINITY;
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double dsq = pointSegmentDistanceSq(px, py, xs[j], ys[j], xs[i], ys[i]);
            if (dsq < best) best = dsq;
        }
        return best;
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

    private static boolean segmentIntersectsAabb(double ax, double ay, double bx, double by,
                                                 double minX, double minY, double maxX, double maxY) {
        if (pointInsideAabb(ax, ay, minX, minY, maxX, maxY)) return true;
        if (pointInsideAabb(bx, by, minX, minY, maxX, maxY)) return true;
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

    private static final class PolygonData {
        final double[] xs;
        final double[] ys;
        final double area;

        PolygonData(double[] xs, double[] ys, double area) {
            this.xs = xs;
            this.ys = ys;
            this.area = area;
        }
    }

    private static final class HullProfile {
        private static final EnumMap<ShipRole, EnumMap<Faction, HullProfile>> SILHOUETTE_CACHE =
                new EnumMap<>(ShipRole.class);
        final double[] xs;
        final double[] top;
        final double[] bottom;
        final double globalInnerTop;
        final double globalInnerBottom;

        static HullProfile fromSilhouette(ShipRole role) {
            return fromSilhouette(role, null);
        }

        static HullProfile fromSilhouette(ShipRole role, Faction faction) {
            ShipRole roleKey = (role == null) ? ShipRole.CRUISER : role;
            Faction factionKey = (faction == null) ? Faction.ALLY : faction;
            EnumMap<Faction, HullProfile> byFaction = SILHOUETTE_CACHE.computeIfAbsent(roleKey, k -> new EnumMap<>(Faction.class));
            HullProfile cached = byFaction.get(factionKey);
            if (cached != null) return cached;
            Polygon hull = ShipHullSilhouette.hullPolygon(role, 100.0, faction);
            if (hull == null || hull.npoints < 3) return null;

            java.awt.Rectangle bounds = hull.getBounds();
            if (bounds.width <= 1 || bounds.height <= 1) return null;

            double minX = bounds.getMinX();
            double maxX = bounds.getMaxX();
            double minYBound = bounds.getMinY();
            double maxYBound = bounds.getMaxY();
            double halfW = Math.max(1.0, Math.max(Math.abs(minX), Math.abs(maxX)));
            double halfH = Math.max(1.0, Math.max(Math.abs(minYBound), Math.abs(maxYBound)));

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

                xs[i] = MathUtil.clamp((sampleX / halfW) * 0.98, -1.0, 1.0);
                if (Double.isFinite(minY) && Double.isFinite(maxY) && maxY > minY) {
                    top[i] = MathUtil.clamp((minY / halfH) * 0.98, -1.0, 1.0);
                    bottom[i] = MathUtil.clamp((maxY / halfH) * 0.98, -1.0, 1.0);
                    valid[i] = true;
                }
            }

            fillMissingColumns(top, bottom, valid);
            if (!anyValid(valid)) return null;
            smoothProfile(top, bottom);
            HullProfile built = new HullProfile(xs, top, bottom);
            byFaction.put(factionKey, built);
            return built;
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
