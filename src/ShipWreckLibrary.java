import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ShipWreckLibrary {
    private static final String WRECK_DIR = "assets/ship_wrecks";
    private static final String WRECK_RESOURCE_DIR = "/ship_wrecks/";
    private static final List<File> WRECK_ROOTS = resolveWreckRoots(WRECK_DIR);
    private static final Map<String, WreckSet> CACHE = new HashMap<>();
    private static final Map<String, BufferedImage> IMAGE_CACHE = new HashMap<>();

    private ShipWreckLibrary() {}

    static WreckSet getSet(ShipRole role, Faction faction) {
        String roleKey = keyForRole(role);
        String factionKey = keyForFaction(faction);
        String key = roleKey + "|" + factionKey;
        WreckSet cached = CACHE.get(key);
        if (cached != null) return cached;

        List<BufferedImage> chunks = loadSeries(roleKey, factionKey, "chunk");
        List<BufferedImage> breaches = loadSeries(roleKey, factionKey, "breach");
        WreckSet set = new WreckSet(chunks, breaches);
        CACHE.put(key, set);
        return set;
    }

    private static List<BufferedImage> loadSeries(String roleKey, String factionKey, String seriesKey) {
        List<BufferedImage> out = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            String idx = String.format(Locale.US, "%02d", i);
            BufferedImage img = loadVariant(roleKey, factionKey, seriesKey, idx);
            if (img == null) break;
            out.add(img);
        }
        return out;
    }

    private static BufferedImage loadVariant(String roleKey, String factionKey, String seriesKey, String idx) {
        String[] attempts = new String[] {
                roleKey + "_" + factionKey + "_" + seriesKey + "_" + idx,
                roleKey + "_" + seriesKey + "_" + idx,
                "default_" + factionKey + "_" + seriesKey + "_" + idx,
                "default_" + seriesKey + "_" + idx
        };
        for (String key : attempts) {
            BufferedImage img = loadImage(key);
            if (img != null) return img;
        }
        return null;
    }

    private static BufferedImage loadImage(String key) {
        if (key == null || key.isBlank()) return null;
        BufferedImage cached = IMAGE_CACHE.get(key);
        if (cached != null) return cached;

        BufferedImage img = null;
        File file = new File(WRECK_DIR, key + ".png");
        try {
            if (file.isFile()) {
                img = ImageIO.read(file);
            }
        } catch (IOException ignored) {}

        if (img == null) {
            try (InputStream in = ShipWreckLibrary.class.getResourceAsStream(WRECK_RESOURCE_DIR + key + ".png")) {
                if (in != null) img = ImageIO.read(in);
            } catch (IOException ignored) {}
        }

        if (img != null) IMAGE_CACHE.put(key, img);
        return img;
    }

    private static List<File> resolveWreckRoots(String relativeDir) {
        List<File> roots = new ArrayList<>();
        File dir = new File(relativeDir);
        if (dir.isDirectory()) roots.add(dir);
        return roots;
    }

    private static String keyForRole(ShipRole role) {
        if (role == null) return "frigate";
        if (role == ShipRole.ARTILLERY_SHIP) return "patrol";
        if (role == ShipRole.STATIC_TURRET) return "base";
        if (role == ShipRole.ELITE_REINFORCEMENTS_TITAN) return "elite_supership_command_titan";
        return role.name().toLowerCase(Locale.ROOT);
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

    static final class WreckSet {
        final List<BufferedImage> chunks;
        final List<BufferedImage> breaches;

        WreckSet(List<BufferedImage> chunks, List<BufferedImage> breaches) {
            this.chunks = (chunks == null) ? List.of() : List.copyOf(chunks);
            this.breaches = (breaches == null) ? List.of() : List.copyOf(breaches);
        }

        boolean hasAny() {
            return !chunks.isEmpty() || !breaches.isEmpty();
        }
    }
}
