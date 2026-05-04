import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class ShipPartLibrary {
    private static final String PART_DIR = "assets/ship_parts";
    private static final String PART_RESOURCE_DIR = "/ship_parts/";
    private static final Map<String, PartSet> CACHE = new HashMap<>();
    private static final Map<String, PartSprite> IMAGE_CACHE = new HashMap<>();
    private static final Set<String> IMAGE_MISS_CACHE = new HashSet<>();
    private static Map<String, double[]> DAMAGE_FOCUS_CACHE = null;
    private static boolean cachesPrewarmed = false;

    enum Variant {
        NORMAL,
        DAMAGED,
        CRITICAL,
        DESTROYED
    }

    private ShipPartLibrary() {}

    static boolean hasParts(ShipRole role, Faction faction) {
        return getSet(role, faction, Variant.NORMAL).hasParts();
    }

    static boolean hasDestroyedParts(ShipRole role, Faction faction) {
        return getSet(role, faction, Variant.DESTROYED).hasParts();
    }

    static PartSet getSet(ShipRole role, Faction faction) {
        return getSet(role, faction, Variant.NORMAL);
    }

    static PartSet getSet(ShipRole role, Faction faction, Variant variant) {
        Variant resolvedVariant = (variant == null) ? Variant.NORMAL : variant;
        if (usesDeathOnlyMultipart(role)
                && resolvedVariant != Variant.DESTROYED) {
            // Stations and titan-scale hulls stay on the authored ship skin path while alive.
            // Their multipart section sprites are reserved for the death handoff only.
            return new PartSet(List.of(), (variant == null) ? Variant.NORMAL : variant);
        }
        String roleKey = keyForRole(role);
        String factionKey = keyForFaction(faction);
        String cacheKey = roleKey + "|" + factionKey + "|" + resolvedVariant.name();
        PartSet cached = CACHE.get(cacheKey);
        if (cached != null) return cached;

        List<PartSprite> parts = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            String idx = String.format(Locale.US, "%02d", i);
            PartSprite sprite = loadVariant(roleKey, factionKey, idx, resolvedVariant);
            if (sprite == null) break;
            parts.add(sprite);
        }

        if (parts.isEmpty() && resolvedVariant != Variant.NORMAL) {
            return getSet(role, faction, Variant.NORMAL);
        }

        PartSet set = new PartSet(parts, resolvedVariant);
        CACHE.put(cacheKey, set);
        return set;
    }

    static synchronized void prewarmCaches() {
        if (cachesPrewarmed) return;
        damageFocusManifest();
        for (ShipRole role : ShipRole.values()) {
            for (Faction faction : Faction.values()) {
                getSet(role, faction, Variant.NORMAL);
                getSet(role, faction, Variant.DAMAGED);
                getSet(role, faction, Variant.CRITICAL);
                getSet(role, faction, Variant.DESTROYED);
            }
            getSet(role, null, Variant.NORMAL);
            getSet(role, null, Variant.DAMAGED);
            getSet(role, null, Variant.CRITICAL);
            getSet(role, null, Variant.DESTROYED);
        }
        cachesPrewarmed = true;
    }

    private static PartSprite loadVariant(String roleKey, String factionKey, String idx, Variant variant) {
        String[] attempts = variantAttempts(roleKey, factionKey, idx, variant);
        for (String key : attempts) {
            PartSprite sprite = loadImage(key);
            if (sprite != null) return sprite;
        }
        return null;
    }

    private static String[] variantAttempts(String roleKey, String factionKey, String idx, Variant variant) {
        Variant resolvedVariant = (variant == null) ? Variant.NORMAL : variant;
        if (resolvedVariant == Variant.DAMAGED) {
            return new String[] {
                    roleKey + "_" + factionKey + "_damaged_part_" + idx,
                    roleKey + "_damaged_" + factionKey + "_part_" + idx,
                    roleKey + "_damaged_part_" + idx,
                    "default_" + factionKey + "_damaged_part_" + idx,
                    "default_damaged_part_" + idx
            };
        }
        if (resolvedVariant == Variant.CRITICAL) {
            return new String[] {
                    roleKey + "_" + factionKey + "_critical_part_" + idx,
                    roleKey + "_critical_" + factionKey + "_part_" + idx,
                    roleKey + "_critical_part_" + idx,
                    "default_" + factionKey + "_critical_part_" + idx,
                    "default_critical_part_" + idx,
                    roleKey + "_" + factionKey + "_damaged_part_" + idx,
                    roleKey + "_damaged_" + factionKey + "_part_" + idx,
                    roleKey + "_damaged_part_" + idx,
                    "default_" + factionKey + "_damaged_part_" + idx,
                    "default_damaged_part_" + idx
            };
        }
        if (resolvedVariant == Variant.DESTROYED) {
            return new String[] {
                    roleKey + "_" + factionKey + "_destroyed_part_" + idx,
                    roleKey + "_" + factionKey + "_wreck_part_" + idx,
                    roleKey + "_destroyed_" + factionKey + "_part_" + idx,
                    roleKey + "_wreck_" + factionKey + "_part_" + idx,
                    roleKey + "_destroyed_part_" + idx,
                    roleKey + "_wreck_part_" + idx,
                    "default_" + factionKey + "_destroyed_part_" + idx,
                    "default_" + factionKey + "_wreck_part_" + idx,
                    "default_destroyed_part_" + idx,
                    "default_wreck_part_" + idx,
                    roleKey + "_" + factionKey + "_critical_part_" + idx,
                    roleKey + "_critical_" + factionKey + "_part_" + idx,
                    roleKey + "_critical_part_" + idx,
                    "default_" + factionKey + "_critical_part_" + idx,
                    "default_critical_part_" + idx,
                    roleKey + "_" + factionKey + "_damaged_part_" + idx,
                    roleKey + "_damaged_" + factionKey + "_part_" + idx,
                    roleKey + "_damaged_part_" + idx,
                    "default_" + factionKey + "_damaged_part_" + idx,
                    "default_damaged_part_" + idx
            };
        }
        return new String[] {
                roleKey + "_" + factionKey + "_part_" + idx,
                roleKey + "_part_" + idx,
                "default_" + factionKey + "_part_" + idx,
                "default_part_" + idx
        };
    }

    private static PartSprite loadImage(String key) {
        if (key == null || key.isBlank()) return null;
        PartSprite cached = IMAGE_CACHE.get(key);
        if (cached != null) return cached;
        if (IMAGE_MISS_CACHE.contains(key)) return null;

        BufferedImage img = null;
        File file = new File(PART_DIR, key + ".png");
        try {
            if (file.isFile()) img = ImageIO.read(file);
        } catch (IOException ignored) {}

        if (img == null) {
            try (InputStream in = ShipPartLibrary.class.getResourceAsStream(PART_RESOURCE_DIR + key + ".png")) {
                if (in != null) img = ImageIO.read(in);
            } catch (IOException ignored) {}
        }

        if (img == null) {
            IMAGE_MISS_CACHE.add(key);
            return null;
        }

        PartSprite sprite = trimSprite(img, key);
        if (sprite != null) {
            IMAGE_CACHE.put(key, sprite);
        } else {
            IMAGE_MISS_CACHE.add(key);
        }
        return sprite;
    }

    private static PartSprite trimSprite(BufferedImage src, String key) {
        if (src == null) return null;
        int w = src.getWidth();
        int h = src.getHeight();
        int minX = w;
        int minY = h;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int a = (src.getRGB(x, y) >>> 24) & 0xFF;
                if (a <= 0) continue;
                if (x < minX) minX = x;
                if (y < minY) minY = y;
                if (x > maxX) maxX = x;
                if (y > maxY) maxY = y;
            }
        }
        if (maxX < minX || maxY < minY) return null;

        int trimW = Math.max(1, maxX - minX + 1);
        int trimH = Math.max(1, maxY - minY + 1);
        BufferedImage trimmed = new BufferedImage(trimW, trimH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = trimmed.createGraphics();
        try {
            g.drawImage(src, 0, 0, trimW, trimH, minX, minY, maxX + 1, maxY + 1, null);
        } finally {
            g.dispose();
        }

        double centerX = minX + trimW * 0.5;
        double centerY = minY + trimH * 0.5;
        double offsetXNorm = (centerX - w * 0.5) / w;
        double offsetYNorm = (centerY - h * 0.5) / h;
        double widthNorm = trimW / (double) w;
        double heightNorm = trimH / (double) h;
        double[] focus = damageFocusFor(key);
        double focusXNorm = (focus == null || focus.length < 2) ? 0.0 : focus[0];
        double focusYNorm = (focus == null || focus.length < 2) ? 0.0 : focus[1];
        return new PartSprite(trimmed, offsetXNorm, offsetYNorm, widthNorm, heightNorm, focusXNorm, focusYNorm);
    }

    private static double[] damageFocusFor(String key) {
        if (key == null || key.isBlank()) return null;
        return damageFocusManifest().get(key + ".png");
    }

    private static Map<String, double[]> damageFocusManifest() {
        if (DAMAGE_FOCUS_CACHE != null) return DAMAGE_FOCUS_CACHE;
        Map<String, double[]> map = new HashMap<>();
        Path file = Path.of(PART_DIR, "damage_focus_manifest.txt");
        if (Files.isRegularFile(file)) {
            try {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    parseDamageFocusLine(map, line);
                }
            } catch (IOException ignored) {}
        } else {
            try (InputStream in = ShipPartLibrary.class.getResourceAsStream(PART_RESOURCE_DIR + "damage_focus_manifest.txt")) {
                if (in != null) {
                    for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                        parseDamageFocusLine(map, line);
                    }
                }
            } catch (IOException ignored) {}
        }
        DAMAGE_FOCUS_CACHE = map;
        return DAMAGE_FOCUS_CACHE;
    }

    private static void parseDamageFocusLine(Map<String, double[]> map, String line) {
        if (map == null || line == null) return;
        String trimmed = line.trim();
        if (trimmed.isEmpty()) return;
        String[] parts = trimmed.split("\\|");
        if (parts.length < 3) return;
        try {
            double x = Double.parseDouble(parts[1].trim());
            double y = Double.parseDouble(parts[2].trim());
            map.put(parts[0].trim(), new double[]{x, y});
        } catch (NumberFormatException ignored) {}
    }

    private static String keyForRole(ShipRole role) {
        if (role == null) return "frigate";
        if (role == ShipRole.ARTILLERY_SHIP) return "patrol";
        if (role == ShipRole.STATIC_TURRET) return "base";
        if (role == ShipRole.ELITE_REINFORCEMENTS_TITAN) return "elite_supership_command_titan";
        return role.name().toLowerCase(Locale.ROOT);
    }

    private static boolean usesDeathOnlyMultipart(ShipRole role) {
        if (role == null) return false;
        return role == ShipRole.BASE
                || role == ShipRole.STATIC_TURRET
                || role.isTitanOrMothership();
    }

    private static String keyForFaction(Faction faction) {
        if (faction == null) return "ally";
        return switch (faction) {
            case PLAYER, ALLY -> "ally";
            case ENEMY -> "enemy";
            case TEAM_C -> "team_c";
            case TEAM_D -> "team_d";
        };
    }

    static final class PartSet {
        final List<PartSprite> parts;
        final Variant variant;

        PartSet(List<PartSprite> parts, Variant variant) {
            this.parts = (parts == null) ? List.of() : List.copyOf(parts);
            this.variant = (variant == null) ? Variant.NORMAL : variant;
        }

        boolean hasParts() {
            return !parts.isEmpty();
        }

        boolean usesBakedDamageVisuals() {
            return variant == Variant.DESTROYED;
        }
    }

    static final class PartSprite {
        final BufferedImage image;
        final double offsetXNorm;
        final double offsetYNorm;
        final double widthNorm;
        final double heightNorm;
        final double focusXNorm;
        final double focusYNorm;

        PartSprite(BufferedImage image, double offsetXNorm, double offsetYNorm,
                   double widthNorm, double heightNorm, double focusXNorm, double focusYNorm) {
            this.image = image;
            this.offsetXNorm = offsetXNorm;
            this.offsetYNorm = offsetYNorm;
            this.widthNorm = widthNorm;
            this.heightNorm = heightNorm;
            this.focusXNorm = focusXNorm;
            this.focusYNorm = focusYNorm;
        }
    }
}
