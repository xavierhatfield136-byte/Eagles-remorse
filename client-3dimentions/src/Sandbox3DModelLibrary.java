import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

final class Sandbox3DModelLibrary {
    private static final String MODEL_DIR_PROPERTY = "eagles.modelDir";
    private static final String MODEL_DIR_ENV = "EAGLES_3D_MODEL_DIR";
    private static final Path DEFAULT_DROPOFF = Path.of(
            "C:\\Users\\xhatf\\OneDrive\\Desktop\\3d models dropoff");

    private final Path modelDir;
    private final Map<Key, Entry> entries;
    private final Map<Key, GlbModel> loaded = new ConcurrentHashMap<>();
    private final int discoveredFiles;

    private Sandbox3DModelLibrary(Path modelDir, Map<Key, Entry> entries, int discoveredFiles) {
        this.modelDir = modelDir;
        this.entries = entries;
        this.discoveredFiles = discoveredFiles;
    }

    static Sandbox3DModelLibrary discoverDefault() {
        Path dir = resolveModelDir();
        if (dir == null || !Files.isDirectory(dir)) {
            return new Sandbox3DModelLibrary(dir, Map.of(), 0);
        }

        Map<Key, Entry> mapped = new HashMap<>();
        int count = 0;
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> files = stream
                    .filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.US).endsWith(".glb"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.US)))
                    .toList();
            count = files.size();
            for (Path file : files) {
                Entry entry = Entry.from(file);
                if (entry == null) continue;
                mapped.merge(entry.key, entry, Sandbox3DModelLibrary::choosePreferred);
            }
        } catch (Exception ignored) {
            return new Sandbox3DModelLibrary(dir, Map.of(), 0);
        }
        return new Sandbox3DModelLibrary(dir, Map.copyOf(mapped), count);
    }

    GlbModel modelFor(ShipRole role, Faction faction) {
        if (role == null || entries.isEmpty()) return null;
        Faction normalizedFaction = normalizeFaction(faction);
        Entry entry = entries.get(new Key(role, normalizedFaction));
        if (entry == null && normalizedFaction == Faction.PLAYER) {
            entry = entries.get(new Key(role, Faction.ALLY));
        }
        if (entry == null) return null;
        Entry selected = entry;
        return loaded.computeIfAbsent(selected.key, k -> GlbModel.load(selected.path));
    }

    String summary() {
        if (modelDir == null) return "GLB models: no model folder configured";
        if (entries.isEmpty()) return "GLB models: none mapped in " + modelDir;
        return "GLB models: " + entries.size() + " role/team mappings from " + discoveredFiles + " files";
    }

    private static Path resolveModelDir() {
        String configured = System.getProperty(MODEL_DIR_PROPERTY);
        if (configured == null || configured.isBlank()) configured = System.getenv(MODEL_DIR_ENV);
        if (configured != null && !configured.isBlank()) return Path.of(configured.trim());
        if (Files.isDirectory(DEFAULT_DROPOFF)) return DEFAULT_DROPOFF;
        Path localAssets = Path.of("assets", "ship_models");
        if (Files.isDirectory(localAssets)) return localAssets;
        return DEFAULT_DROPOFF;
    }

    private static Entry choosePreferred(Entry a, Entry b) {
        int scoreA = preferenceScore(a.path.getFileName().toString());
        int scoreB = preferenceScore(b.path.getFileName().toString());
        if (scoreA != scoreB) return scoreA > scoreB ? a : b;
        long sizeA = fileSize(a.path);
        long sizeB = fileSize(b.path);
        if (sizeA != sizeB) return sizeA < sizeB ? a : b;
        return a;
    }

    private static int preferenceScore(String name) {
        String n = normalizeName(name);
        int score = 0;
        if (n.contains("modern")) score += 8;
        if (n.contains(" copy ")) score -= 2;
        if (n.matches(".*\\(\\d+\\).*")) score -= 1;
        if (n.contains(" old ")) score -= 3;
        return score;
    }

    private static long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (Exception ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static Faction normalizeFaction(Faction faction) {
        if (faction == Faction.PLAYER) return Faction.ALLY;
        return faction == null ? Faction.ALLY : faction;
    }

    private record Key(ShipRole role, Faction faction) {}

    private static final class Entry {
        final Path path;
        final Key key;

        private Entry(Path path, Key key) {
            this.path = path;
            this.key = key;
        }

        static Entry from(Path path) {
            String name = normalizeName(path.getFileName().toString());
            Faction faction = factionFrom(name);
            ShipRole role = roleFrom(name);
            if (faction == null || role == null) return null;
            return new Entry(path, new Key(role, faction));
        }

        private static Faction factionFrom(String name) {
            if (name.contains(" blue ")) return Faction.ALLY;
            if (name.contains(" red ")) return Faction.ENEMY;
            if (name.contains(" green ")) return Faction.TEAM_C;
            if (name.contains(" yellow ")) return Faction.TEAM_D;
            return null;
        }

        private static ShipRole roleFrom(String name) {
            if (name.contains("command intel")) return ShipRole.COMMAND_INTEL_TITAN;
            if (name.contains("bulwark") || name.contains("tital")) return ShipRole.BULWARK_TITAN;
            if (name.contains("carrier titan") || name.contains("carrier support")) return ShipRole.CARRIER_SUPPORT_TITAN;
            if (name.contains("transport titan")) return ShipRole.TRANSPORT_TITAN;
            if (name.contains("drone carrier")) return ShipRole.DRONE_CARRIER;
            if (name.contains("ciws corvette")) return ShipRole.CIWS_CORVETTE;
            if (name.contains("missile boat") || name.contains("missile ship")) return ShipRole.MISSILE_BOAT;
            if (name.contains("light cruiser")) return ShipRole.LIGHT_CRUISER;
            if (name.contains("medium cruiser")) return ShipRole.MEDIUM_CRUISER;
            if (name.contains("battlecruiser")) return ShipRole.BATTLECRUISER;
            if (name.contains("battleship")) return ShipRole.BATTLESHIP;
            if (name.contains("dreadnaught") || name.contains("dreadnought")) return ShipRole.DREADNOUGHT;
            if (name.contains("mothership")) return ShipRole.MOTHERSHIP;
            if (name.contains("supership")) return ShipRole.SUPERSHIP;
            if (name.contains("stealth ship")) return ShipRole.STEALTH_SHIP;
            if (name.contains("pd craft") || name.contains("pf craft")) return ShipRole.PD_CRAFT;
            if (name.contains("mining ships") || name.contains(" miner ")) return ShipRole.MINER;
            if (name.contains("fighter")) return ShipRole.FIGHTER;
            if (name.contains("bomber")) return ShipRole.BOMBER;
            if (name.contains("drone")) return ShipRole.DRONE;
            if (name.contains("frigate")) return ShipRole.FRIGATE;
            if (name.contains("cruiser")) return ShipRole.CRUISER;
            if (name.contains("carrier")) return ShipRole.CARRIER;
            if (name.contains("transport")) return ShipRole.TRANSPORT;
            if (name.contains("hauler")) return ShipRole.HAULER;
            if (name.contains("patrol")) return ShipRole.PATROL;
            if (name.contains("picket")) return ShipRole.PICKET;
            if (name.contains(" base ")) return ShipRole.BASE;
            return null;
        }
    }

    private static String normalizeName(String raw) {
        String n = raw == null ? "" : raw.toLowerCase(Locale.US);
        n = n.replace(".glb", "");
        n = n.replace('+', ' ');
        n = n.replace('_', ' ');
        n = n.replace('-', ' ');
        n = n.replaceAll("[^a-z0-9() ]+", " ");
        n = n.replaceAll("\\s+", " ").trim();
        return " " + n + " ";
    }
}
