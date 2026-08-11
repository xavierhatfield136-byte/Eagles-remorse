import javax.imageio.ImageIO;
import app.state.AssetLoadGuard;
import app.state.BoundedCache;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Shared hull silhouette source for rendering and collision.
 * <p>
 * Pipeline:
 * 1) Try to derive a polygon from the role's authored albedo skin alpha.
 * 2) If unavailable, fall back to deterministic role geometry.
 */
public final class ShipHullSilhouette {
    private static final double SKIN_SCALE = 2.1;
    private static final int ALPHA_THRESHOLD = 40;
    private static final int MIN_OPAQUE_PER_COLUMN = 2;
    private static final String SKIN_DIR = "assets/ship_skins";
    private static final String SKIN_RESOURCE_DIR = "ship_skins";

    private static final Map<String, Polygon> HULL_CACHE = new BoundedCache<>(512);
    private static final Map<String, BufferedImage> SKIN_CACHE = new BoundedCache<>(192);
    private static final Map<String, VisualBounds> VISUAL_BOUNDS_CACHE = new BoundedCache<>(512);
    private static final Set<String> SKIN_MISS = new HashSet<>();
    private static final List<File> SKIN_ROOTS = resolveSkinRoots(SKIN_DIR);
    private static boolean cachesPrewarmed = false;

    private ShipHullSilhouette() {}

    public static double skinRenderScale() {
        return SKIN_SCALE;
    }

    public static Polygon hullPolygon(ShipRole role, double radius) {
        return hullPolygon(role, radius, null);
    }

    public static synchronized void prewarmCaches() {
        if (cachesPrewarmed) return;
        for (ShipRole role : ShipRole.values()) {
            double radius = Math.max(8.0, RoleStats.get(role).radius);
            hullPolygon(role, radius, null);
            for (Faction faction : Faction.values()) {
                hullPolygon(role, radius, faction);
            }
        }
        cachesPrewarmed = true;
    }

    public static Polygon hullPolygon(ShipRole role, double radius, Faction faction) {
        ShipRole resolved = (role == null) ? ShipRole.FRIGATE : role;
        int r = (int) Math.round(Math.max(8.0, radius));
        String key = resolved.name() + ":" + keyForFaction(faction) + ":" + r;
        Polygon cached = HULL_CACHE.get(key);
        if (cached != null) return cached;

        Polygon fallback = fallbackPolygon(resolved, r);
        Polygon fromSkin = buildFromSkin(resolved, r, faction);
        Polygon out = (fromSkin != null && fromSkin.npoints >= 3) ? fromSkin : fallback;
        HULL_CACHE.put(key, out);
        return out;
    }

    public static boolean visualHullTouches(ShipRole role, double radius, Faction faction,
                                            double localX, double localY, double footprint) {
        double probe = Math.max(1.5, footprint);
        return visualHullContains(role, radius, faction, localX, localY)
                || visualHullContains(role, radius, faction, localX - probe, localY)
                || visualHullContains(role, radius, faction, localX + probe, localY)
                || visualHullContains(role, radius, faction, localX, localY - probe)
                || visualHullContains(role, radius, faction, localX, localY + probe);
    }

    public static boolean visualHullContains(ShipRole role, double radius, Faction faction,
                                             double localX, double localY) {
        BufferedImage skin = loadSkin(role, faction);
        if (skin == null) {
            Polygon fallback = hullPolygon(role, radius, faction);
            return fallback != null && fallback.contains(localX, localY);
        }
        int[] pixel = localToSkinPixel(role, radius, faction, skin, localX, localY);
        if (pixel == null) return false;
        int alpha = (skin.getRGB(pixel[0], pixel[1]) >>> 24) & 0xFF;
        return alpha >= ALPHA_THRESHOLD;
    }

    public static VisualColumn visualHullColumn(ShipRole role, double radius, Faction faction,
                                                double alongFrac) {
        VisualBounds bounds = visualBounds(role, radius, faction);
        if (bounds == null) return null;
        double x = bounds.minX + MathUtil.clamp(alongFrac, 0.0, 1.0) * Math.max(1.0, bounds.maxX - bounds.minX);
        VisualColumn column = visualHullColumnAtX(role, radius, faction, x);
        if (column != null) return column;

        double span = Math.max(8.0, bounds.maxX - bounds.minX);
        double[] offsets = {2.0, -2.0, 4.0, -4.0, 7.0, -7.0, 11.0, -11.0, span * 0.08, -span * 0.08};
        for (double offset : offsets) {
            column = visualHullColumnAtX(role, radius, faction, x + offset);
            if (column != null) return column;
        }
        return null;
    }

    public static VisualColumn visualHullColumnAtX(ShipRole role, double radius, Faction faction,
                                                   double localX) {
        BufferedImage skin = loadSkin(role, faction);
        if (skin == null) return null;
        VisualBounds bounds = visualBounds(role, radius, faction);
        if (bounds == null || localX < bounds.minX || localX > bounds.maxX) return null;

        double span = skinLocalSpan(radius);
        double nx = (localX + span * 0.5) / span;
        int px = MathUtil.clamp((int) Math.round(nx * (skin.getWidth() - 1)), 0, skin.getWidth() - 1);
        int top = -1;
        int bottom = -1;
        int count = 0;
        for (int y = 0; y < skin.getHeight(); y++) {
            int alpha = (skin.getRGB(px, y) >>> 24) & 0xFF;
            if (alpha < ALPHA_THRESHOLD) continue;
            if (top < 0) top = y;
            bottom = y;
            count++;
        }
        if (top < 0 || bottom <= top || count < MIN_OPAQUE_PER_COLUMN) return null;

        double resolvedX = skinPixelToLocalX(radius, skin, px);
        double topY = skinPixelToLocalY(radius, skin, top);
        double bottomY = skinPixelToLocalY(radius, skin, bottom);
        return new VisualColumn(resolvedX, topY, bottomY);
    }

    public static VisualBounds visualBounds(ShipRole role, double radius, Faction faction) {
        BufferedImage skin = loadSkin(role, faction);
        if (skin == null) return null;
        ShipRole resolved = (role == null) ? ShipRole.FRIGATE : role;
        int r = (int) Math.round(Math.max(8.0, radius));
        String key = resolved.name() + ":" + keyForFaction(faction) + ":" + r;
        VisualBounds cached = VISUAL_BOUNDS_CACHE.get(key);
        if (cached != null) return cached;

        int minX = skin.getWidth();
        int minY = skin.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < skin.getHeight(); y++) {
            for (int x = 0; x < skin.getWidth(); x++) {
                int alpha = (skin.getRGB(x, y) >>> 24) & 0xFF;
                if (alpha < ALPHA_THRESHOLD) continue;
                if (x < minX) minX = x;
                if (y < minY) minY = y;
                if (x > maxX) maxX = x;
                if (y > maxY) maxY = y;
            }
        }
        if (maxX < minX || maxY < minY) return null;

        VisualBounds bounds = new VisualBounds(
                skinPixelToLocalX(radius, skin, minX),
                skinPixelToLocalY(radius, skin, minY),
                skinPixelToLocalX(radius, skin, maxX),
                skinPixelToLocalY(radius, skin, maxY));
        VISUAL_BOUNDS_CACHE.put(key, bounds);
        return bounds;
    }

    private static int[] localToSkinPixel(ShipRole role, double radius, Faction faction, BufferedImage skin,
                                          double localX, double localY) {
        if (skin == null || skin.getWidth() <= 1 || skin.getHeight() <= 1) return null;
        double span = skinLocalSpan(radius);
        double nx = (localX + span * 0.5) / span;
        double ny = (localY + span * 0.5) / span;
        if (nx < 0.0 || nx > 1.0 || ny < 0.0 || ny > 1.0) return null;
        int x = MathUtil.clamp((int) Math.round(nx * (skin.getWidth() - 1)), 0, skin.getWidth() - 1);
        int y = MathUtil.clamp((int) Math.round(ny * (skin.getHeight() - 1)), 0, skin.getHeight() - 1);
        return new int[]{x, y};
    }

    private static double skinPixelToLocalX(double radius, BufferedImage skin, int x) {
        double span = skinLocalSpan(radius);
        return -span * 0.5 + (x / (double) Math.max(1, skin.getWidth() - 1)) * span;
    }

    private static double skinPixelToLocalY(double radius, BufferedImage skin, int y) {
        double span = skinLocalSpan(radius);
        return -span * 0.5 + (y / (double) Math.max(1, skin.getHeight() - 1)) * span;
    }

    private static double skinLocalSpan(double radius) {
        return Math.max(4.0, Math.max(8.0, radius) * 2.0 * SKIN_SCALE);
    }

    private static Polygon buildFromSkin(ShipRole role, int r, Faction faction) {
        BufferedImage skin = loadSkin(role, faction);
        if (skin == null) return null;

        double sw = Math.max(4.0, r * 2.0 * SKIN_SCALE);
        double sh = sw;
        double sx = -sw * 0.5;
        double sy = -sh * 0.5;

        int w = skin.getWidth();
        int h = skin.getHeight();
        if (w <= 1 || h <= 1) return null;

        int samples = Math.max(24, Math.min(96, w / 4));
        samples = Math.max(samples, 2);

        int[] top = new int[samples];
        int[] bot = new int[samples];
        boolean[] valid = new boolean[samples];

        for (int i = 0; i < samples; i++) {
            int x = (int) Math.round((w - 1) * (samples == 1 ? 0.0 : (i / (double) (samples - 1))));
            int lo = h;
            int hi = -1;
            int count = 0;
            for (int y = 0; y < h; y++) {
                int a = (skin.getRGB(x, y) >>> 24) & 0xFF;
                if (a >= ALPHA_THRESHOLD) {
                    count++;
                    if (y < lo) lo = y;
                    if (y > hi) hi = y;
                }
            }
            if (hi >= lo && count >= MIN_OPAQUE_PER_COLUMN) {
                valid[i] = true;
                top[i] = lo;
                bot[i] = hi;
            }
        }

        int first = -1;
        int last = -1;
        for (int i = 0; i < samples; i++) {
            if (!valid[i]) continue;
            if (first < 0) first = i;
            last = i;
        }
        if (first < 0 || last <= first) return null;

        int prev = first;
        for (int i = first; i <= last; i++) {
            if (valid[i]) {
                prev = i;
                continue;
            }
            int next = -1;
            for (int j = i + 1; j <= last; j++) {
                if (valid[j]) {
                    next = j;
                    break;
                }
            }
            if (next < 0) {
                top[i] = top[prev];
                bot[i] = bot[prev];
            } else {
                double t = (i - prev) / (double) (next - prev);
                top[i] = (int) Math.round(top[prev] + (top[next] - top[prev]) * t);
                bot[i] = (int) Math.round(bot[prev] + (bot[next] - bot[prev]) * t);
            }
            valid[i] = true;
        }

        for (int i = first + 1; i < last; i++) {
            top[i] = (top[i - 1] + top[i] + top[i + 1]) / 3;
            bot[i] = (bot[i - 1] + bot[i] + bot[i + 1]) / 3;
        }

        List<Integer> xs = new ArrayList<>();
        List<Integer> ys = new ArrayList<>();

        for (int i = first; i <= last; i++) {
            double tx = (samples == 1) ? 0.0 : (i / (double) (samples - 1));
            double ty = top[i] / (double) (h - 1);
            int lx = (int) Math.round(sx + tx * sw);
            int ly = (int) Math.round(sy + ty * sh);
            appendPoint(xs, ys, lx, ly);
        }
        for (int i = last; i >= first; i--) {
            double tx = (samples == 1) ? 0.0 : (i / (double) (samples - 1));
            double ty = bot[i] / (double) (h - 1);
            int lx = (int) Math.round(sx + tx * sw);
            int ly = (int) Math.round(sy + ty * sh);
            appendPoint(xs, ys, lx, ly);
        }
        if (xs.size() < 3) return null;

        int n = xs.size();
        int[] xa = new int[n];
        int[] ya = new int[n];
        for (int i = 0; i < n; i++) {
            xa[i] = xs.get(i);
            ya[i] = ys.get(i);
        }
        return new Polygon(xa, ya, n);
    }

    private static void appendPoint(List<Integer> xs, List<Integer> ys, int x, int y) {
        int n = xs.size();
        if (n > 0 && xs.get(n - 1) == x && ys.get(n - 1) == y) return;
        xs.add(x);
        ys.add(y);
    }

    private static BufferedImage loadSkin(ShipRole role, Faction faction) {
        String roleKey = keyForRole(role);
        if (roleKey == null || roleKey.isEmpty()) roleKey = "frigate";
        String factionKey = keyForFaction(faction);
        String cacheKey = roleKey + "|" + factionKey;
        if (SKIN_CACHE.containsKey(cacheKey)) return SKIN_CACHE.get(cacheKey);
        if (SKIN_MISS.contains(cacheKey)) return null;

        BufferedImage img = null;
        List<String> attempts = new ArrayList<>();
        if (faction != null) {
            attempts.add(roleKey + "_" + factionKey + "_albedo");
            attempts.add(roleKey + "_" + factionKey);
            attempts.add("default_" + factionKey + "_albedo");
            attempts.add("default_" + factionKey);
        }
        attempts.add(roleKey + "_ally_albedo");
        attempts.add(roleKey + "_ally");
        attempts.add(roleKey + "_albedo");
        attempts.add(roleKey);
        attempts.add("default_ally_albedo");
        attempts.add("default_ally");
        attempts.add("default_albedo");
        attempts.add("default");
        for (String key : attempts) {
            img = loadRoleSkin(key);
            if (img != null) break;
        }

        if (img != null) {
            SKIN_CACHE.put(cacheKey, img);
            return img;
        }
        SKIN_MISS.add(cacheKey);
        return null;
    }

    private static BufferedImage loadRoleSkin(String key) {
        BufferedImage resource = loadBundledSkin(key);
        if (resource != null) return resource;
        for (File root : SKIN_ROOTS) {
            File f = new File(root, key + ".png");
            try {
                if (f.isFile()) return AssetLoadGuard.read(f, "silhouette");
            } catch (IOException ignored) {}
        }
        return null;
    }

    private static BufferedImage loadBundledSkin(String key) {
        if (key == null || key.isBlank()) return null;
        String[] paths = {
                "/" + SKIN_RESOURCE_DIR + "/" + key + ".png",
                "/" + SKIN_DIR + "/" + key + ".png"
        };
        for (String path : paths) {
            try (InputStream in = ShipHullSilhouette.class.getResourceAsStream(path)) {
                if (in == null) continue;
                BufferedImage img = AssetLoadGuard.read(in, "silhouette", path);
                if (img != null) return img;
            } catch (IOException ignored) {}
        }
        return null;
    }

    private static String keyForRole(ShipRole role) {
        if (role == null) return "frigate";
        if (role == ShipRole.ARTILLERY_SHIP) return "patrol";
        if (role == ShipRole.ELITE_REINFORCEMENTS_TITAN) return "elite_supership_command_titan";
        return role.name().toLowerCase(Locale.ROOT);
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

    private static List<File> resolveSkinRoots(String relativeDir) {
        List<File> roots = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        addRootCandidate(new File(relativeDir), roots, seen);
        addAncestorCandidates(new File(System.getProperty("user.dir", ".")), relativeDir, 8, roots, seen);

        return roots;
    }

    private static void addAncestorCandidates(File start, String relativeDir, int maxDepth,
                                              List<File> roots, Set<String> seen) {
        File current = start;
        for (int i = 0; i <= maxDepth && current != null; i++) {
            addRootCandidate(new File(current, relativeDir), roots, seen);
            current = current.getParentFile();
        }
    }

    private static void addRootCandidate(File dir, List<File> roots, Set<String> seen) {
        if (dir == null) return;
        String key;
        try {
            key = dir.getCanonicalPath();
        } catch (IOException e) {
            key = dir.getAbsolutePath();
        }
        if (seen.contains(key)) return;
        seen.add(key);
        roots.add(dir);
    }

    public static final class VisualBounds {
        public final double minX;
        public final double minY;
        public final double maxX;
        public final double maxY;

        private VisualBounds(double minX, double minY, double maxX, double maxY) {
            this.minX = Math.min(minX, maxX);
            this.minY = Math.min(minY, maxY);
            this.maxX = Math.max(minX, maxX);
            this.maxY = Math.max(minY, maxY);
        }
    }

    public static final class VisualColumn {
        public final double localX;
        public final double topY;
        public final double bottomY;
        public final double thickness;
        public final double balancedHalfSpan;

        private VisualColumn(double localX, double topY, double bottomY) {
            this.localX = localX;
            this.topY = Math.min(topY, bottomY);
            this.bottomY = Math.max(topY, bottomY);
            this.thickness = Math.max(0.0, this.bottomY - this.topY);
            this.balancedHalfSpan = Math.min(Math.abs(this.topY), Math.abs(this.bottomY));
        }
    }

    private static Polygon fallbackPolygon(ShipRole role, int r) {
        return switch (role) {
            case FIGHTER, DRONE, PD_CRAFT -> poly(
                    new int[]{r + 6, -r + 1, -r, -r + 1},
                    new int[]{0, -r / 2, 0, r / 2});
            case BOMBER -> poly(
                    new int[]{r + 7, r - 4, -r + 1, -r, -r + 1, r - 4},
                    new int[]{0, -r / 2, -r / 3, 0, r / 3, r / 2});
            case ARTILLERY_SHIP -> poly(
                    new int[]{r + 7, r - 2, -r, -r + 6, -r, r - 2},
                    new int[]{0, -r / 2, -r / 3, 0, r / 3, r / 2});
            case MISSILE_BOAT -> poly(
                    new int[]{r + 6, r - 8, -r, -r + 10, -r, r - 8},
                    new int[]{0, -r / 2, -r / 2, 0, r / 2, r / 2});
            case CIWS_CORVETTE -> poly(
                    new int[]{r + 6, r - 6, -r, -r + 6, -r, r - 6},
                    new int[]{0, -r / 2, -r / 3, 0, r / 3, r / 2});
            case PATROL -> poly(
                    new int[]{r + 7, r - 2, -r, -r + 6, -r, r - 2},
                    new int[]{0, -r / 2, -r / 3, 0, r / 3, r / 2});
            case PICKET -> poly(
                    new int[]{r + 9, r - 4, -r, -r + 10, -r, r - 4},
                    new int[]{0, -r / 2, -r / 2, 0, r / 2, r / 2});
            case STEALTH_SHIP -> poly(
                    new int[]{r + 10, r - 4, -r + 2, -r, -r + 2, r - 4},
                    new int[]{0, -r / 3, -r / 2, 0, r / 2, r / 3});
            case LIGHT_CRUISER -> poly(
                    new int[]{r + 10, r - 6, -r + 4, -r, -r + 6, -r, -r + 4, r - 6},
                    new int[]{0, -r / 2, -r / 2, -r / 5, 0, r / 5, r / 2, r / 2});
            case MEDIUM_CRUISER, CRUISER -> poly(
                    new int[]{r + 12, r - 7, r - 12, -r + 2, -r, -r + 10, -r, -r + 2, r - 12, r - 7},
                    new int[]{0, -r / 2, -r / 2, -r / 2, -r / 6, 0, r / 6, r / 2, r / 2, r / 2});
            case BATTLECRUISER -> poly(
                    new int[]{r + 14, r - 6, r - 14, -r + 2, -r, -r + 12, -r, -r + 2, r - 14, r - 6},
                    new int[]{0, -r / 2, -r / 2, -r / 2, -r / 4, 0, r / 4, r / 2, r / 2, r / 2});
            case BATTLESHIP -> poly(
                    new int[]{r + 16, r - 8, r - 18, -r + 2, -r, -r + 14, -r, -r + 2, r - 18, r - 8},
                    new int[]{0, -r / 2, -r / 2, -r / 2, -r / 3, 0, r / 3, r / 2, r / 2, r / 2});
            case DREADNOUGHT, SUPERSHIP,
                 TRANSPORT_TITAN, BULWARK_TITAN, CARRIER_SUPPORT_TITAN, VANGUARD_TITAN,
                 INTERDICTION_TITAN, COMMAND_INTEL_TITAN, BOARDING_RECOVERY_TITAN,
                 ARTILLERY_TITAN, SHIELD_BASTION_TITAN, FLEET_TELEPORTER_TITAN,
                 ELITE_SUPERSHIP_COMMAND_TITAN, ELITE_REINFORCEMENTS_TITAN,
                 MOBILE_STATION_TITAN, HYPERWEAPON_TITAN,
                 MOTHERSHIP -> poly(
                    new int[]{r + 18, r - 10, r - 22, -r + 2, -r, -r + 16, -r, -r + 2, r - 22, r - 10},
                    new int[]{0, -r / 2, -r / 2, -r / 2, -r / 3, 0, r / 3, r / 2, r / 2, r / 2});
            case CARRIER, DRONE_CARRIER, TRANSPORT, HAULER -> poly(
                    new int[]{r + 8, r - 8, -r, -r + 14, -r, r - 8},
                    new int[]{0, -r, -r, 0, r, r});
            case MINER -> poly(
                    new int[]{r + 5, r - 7, -r + 6, -r, -r + 6, r - 7},
                    new int[]{0, -r / 2, -r / 2, 0, r / 2, r / 2});
            case BASE, STATIC_TURRET -> poly(
                    new int[]{0, r, 0, -r},
                    new int[]{-r, 0, r, 0});
            default -> poly(
                    new int[]{r + 8, r - 6, -r, -r + 8, -r, r - 6},
                    new int[]{0, -r / 2, -r / 2, 0, r / 2, r / 2});
        };
    }

    private static Polygon poly(int[] xs, int[] ys) {
        return new Polygon(xs, ys, Math.min(xs.length, ys.length));
    }
}
