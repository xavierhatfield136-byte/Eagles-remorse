import javax.imageio.ImageIO;
import app.state.AssetLoadGuard;
import app.state.BoundedCache;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class ShipDamagePatchLibrary {
    private static final String PATCH_DIR = "assets/ship_damage_patches";
    private static final String PATCH_RESOURCE_DIR = "/ship_damage_patches/";
    private static final String[] FAMILIES = {"azure", "ember", "emerald", "amber"};
    private static final String[] VARIANTS = {"a", "b", "c"};
    private static final List<File> PATCH_ROOTS = resolvePatchRoots(PATCH_DIR);
    private static final Map<String, List<DamagePatch>> FAMILY_CACHE = new BoundedCache<>(16);

    private ShipDamagePatchLibrary() {}

    static Selection select(Faction faction, double localX, double localY, double severity, int sequence) {
        List<DamagePatch> patches = patchesForFamily(familyForFaction(faction));
        if (patches.isEmpty()) patches = fallbackPatches();
        if (patches.isEmpty()) return null;

        long seed = selectionSeed(faction, localX, localY, severity, sequence);
        int patchIndex = (int) Math.floorMod(seed, patches.size());
        int quarterTurns = (int) Math.floorMod(seed >>> 9, 4);
        boolean flipX = ((seed >>> 14) & 1L) == 0L;
        return new Selection(patches.get(patchIndex).image, quarterTurns, flipX);
    }

    static boolean hasAnyPatch() {
        return !fallbackPatches().isEmpty();
    }

    private static List<DamagePatch> fallbackPatches() {
        for (String family : FAMILIES) {
            List<DamagePatch> patches = patchesForFamily(family);
            if (!patches.isEmpty()) return patches;
        }
        return Collections.emptyList();
    }

    private static String familyForFaction(Faction faction) {
        if (faction == null) return "azure";
        return switch (faction) {
            case PLAYER, ALLY -> "azure";
            case ENEMY -> "ember";
            case TEAM_C -> "emerald";
            case TEAM_D -> "amber";
        };
    }

    private static List<DamagePatch> patchesForFamily(String family) {
        String safeFamily = (family == null || family.isBlank())
                ? "azure"
                : family.toLowerCase(Locale.ROOT);
        List<DamagePatch> cached = FAMILY_CACHE.get(safeFamily);
        if (cached != null) return cached;

        List<DamagePatch> patches = new ArrayList<>();
        for (String variant : VARIANTS) {
            String key = safeFamily + "_breach_" + variant;
            BufferedImage image = loadPatch(key);
            if (image != null) {
                patches.add(new DamagePatch(key, image));
            }
        }
        List<DamagePatch> immutable = Collections.unmodifiableList(patches);
        FAMILY_CACHE.put(safeFamily, immutable);
        return immutable;
    }

    private static BufferedImage loadPatch(String key) {
        if (key == null || key.isBlank()) return null;
        for (File root : PATCH_ROOTS) {
            File file = new File(root, key + ".png");
            try {
                if (file.isFile()) return AssetLoadGuard.read(file, "damage-patch");
            } catch (IOException ignored) {}
        }

        try (InputStream in = ShipDamagePatchLibrary.class.getResourceAsStream(PATCH_RESOURCE_DIR + key + ".png")) {
            if (in != null) return AssetLoadGuard.read(in, "damage-patch", key);
        } catch (IOException ignored) {}
        return null;
    }

    private static List<File> resolvePatchRoots(String relativeDir) {
        List<File> roots = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        addRootCandidate(new File(relativeDir), roots, seen);
        addAncestorCandidates(new File(System.getProperty("user.dir", ".")), relativeDir, 8, roots, seen);

        try {
            File codeSource = new File(ShipDamagePatchLibrary.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File start = codeSource.isDirectory() ? codeSource : codeSource.getParentFile();
            addAncestorCandidates(start, relativeDir, 8, roots, seen);
        } catch (Exception ignored) {}

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
        if (dir == null || !dir.isDirectory()) return;
        try {
            String canonical = dir.getCanonicalPath();
            if (seen.add(canonical)) roots.add(dir);
        } catch (IOException ignored) {}
    }

    private static long selectionSeed(Faction faction, double localX, double localY, double severity, int sequence) {
        long seed = 0x9E3779B97F4A7C15L;
        seed ^= ((long) Math.round(localX * 16.0)) * 0xBF58476D1CE4E5B9L;
        seed ^= Long.rotateLeft(((long) Math.round(localY * 16.0)) * 0x94D049BB133111EBL, 19);
        seed ^= Long.rotateLeft(((long) Math.round(severity * 1000.0)) * 0xD6E8FEB86659FD93L, 37);
        seed ^= ((long) sequence + 31L) * 0xA24BAED4963EE407L;
        seed ^= ((long) ((faction == null) ? 0 : faction.ordinal() + 1)) * 0x9FB21C651E98DF25L;
        seed ^= seed >>> 33;
        seed *= 0xff51afd7ed558ccdL;
        seed ^= seed >>> 33;
        seed *= 0xc4ceb9fe1a85ec53L;
        seed ^= seed >>> 33;
        return seed;
    }

    static final class Selection {
        final BufferedImage image;
        final int quarterTurns;
        final boolean flipX;

        Selection(BufferedImage image, int quarterTurns, boolean flipX) {
            this.image = image;
            this.quarterTurns = quarterTurns;
            this.flipX = flipX;
        }
    }

    private static final class DamagePatch {
        final String key;
        final BufferedImage image;

        DamagePatch(String key, BufferedImage image) {
            this.key = key;
            this.image = image;
        }
    }
}
