import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Section 26 content-pack, scenario-editor backend, and local community state. */
public final class CommunityContentSystem {
    public enum DefinitionKind { HULLS, WEAPONS, FACTIONS, STATION_MODULES, MISSIONS, DIALOGUE }
    public enum EditorElementType { FLEET_SHIP, OBJECTIVE, TRIGGER, HAZARD, REINFORCEMENT, EVENT }
    public enum ShareCodeKind { DOCTRINE, CUSTOM_BATTLE, CHALLENGE, CAMPAIGN_LEGACY }

    public static final class DefinitionRow {
        public final String source;
        public final int row;
        public final Map<String, String> fields;

        DefinitionRow(String source, int row, Map<String, String> fields) {
            this.source = source;
            this.row = row;
            this.fields = Map.copyOf(fields);
        }
    }

    public static final class ContentPack {
        public String id = "core-kepler";
        public int schemaVersion = 1;
        public final Map<DefinitionKind, String> definitions = new LinkedHashMap<>();
        public final Map<DefinitionKind, List<DefinitionRow>> loadedDefinitions = new EnumMap<>(DefinitionKind.class);
        public final List<String> dependencies = new ArrayList<>();
        public final List<String> validationErrors = new ArrayList<>();
        public boolean migrationHelpers = true;
        public boolean hotReloadEnabled = true;
        public int loadOrder;
        public boolean enabled = true;
    }

    public static final class EditorElement {
        public final String id;
        public final EditorElementType type;
        public String label;
        public int x;
        public int y;

        EditorElement(String id, EditorElementType type, String label, int x, int y) {
            this.id = id;
            this.type = type;
            this.label = label;
            this.x = x;
            this.y = y;
        }
    }

    public static final class Scenario {
        public String id = "kepler-relief";
        public String title = "Kepler Relief Corridor";
        public String description = "Escort relief ships through a contested low-orbit corridor.";
        public String thumbnail = "assets/scenarios/kepler-relief.png";
        public long deterministicSeed = 7301L;
        public final List<EditorElement> elements = new ArrayList<>();
        public final List<String> victoryConditions = new ArrayList<>();
        public final List<String> failureConditions = new ArrayList<>();
        public final List<String> timeline = new ArrayList<>();
        public boolean visualTemplateEditor = true;
        public boolean dragDropFleetComposition = true;
        public boolean testPlayAvailable = true;
        public int testPlayLaunches;
    }

    public static final class CommunityState {
        public String fleetDoctrineCode = "DOC-ESCORT-35-RESCUE";
        public String customBattleCode = "BAT-KEPLER-7301";
        public String challengeCode = "CHL-IRON-FLEET";
        public String campaignLegacyCode = "LEG-KEPLER-000";
        public final List<String> compatibilityDiagnostics = new ArrayList<>();
        public final List<String> perSavePackManifest = new ArrayList<>();
        public final List<String> featuredScenarios = new ArrayList<>();
        public final Map<String, Integer> localRatings = new LinkedHashMap<>();
        public final Map<String, String> localNotes = new LinkedHashMap<>();
        public boolean safeMode;
        public boolean replayValidation = true;
    }

    public static final class State {
        public final ContentPack contentPack = new ContentPack();
        public final Scenario scenario = new Scenario();
        public final CommunityState community = new CommunityState();
    }

    private CommunityContentSystem() {}

    public static State bootstrap(long seed) {
        State state = new State();
        state.contentPack.definitions.put(DefinitionKind.HULLS, "config/content-pack/hulls.csv");
        state.contentPack.definitions.put(DefinitionKind.WEAPONS, "config/content-pack/weapons.csv");
        state.contentPack.definitions.put(DefinitionKind.FACTIONS, "config/content-pack/factions.csv");
        state.contentPack.definitions.put(DefinitionKind.STATION_MODULES, "config/content-pack/station-modules.csv");
        state.contentPack.definitions.put(DefinitionKind.MISSIONS, "config/content-pack/missions.csv");
        state.contentPack.definitions.put(DefinitionKind.DIALOGUE, "config/content-pack/dialogue.csv");
        state.contentPack.dependencies.add("base-game>=1");

        addElement(state, "ship-flag", EditorElementType.FLEET_SHIP, "Blue Flagship", 24, 48);
        addElement(state, "objective-relay", EditorElementType.OBJECTIVE, "Secure Moon Relay", 62, 30);
        addElement(state, "trigger-reinforce", EditorElementType.TRIGGER, "Convoy below 45%", 70, 56);
        addElement(state, "hazard-debris", EditorElementType.HAZARD, "Dense Debris", 45, 40);
        addElement(state, "reinforcement-reserve", EditorElementType.REINFORCEMENT, "Reserve Escort", 18, 65);
        addElement(state, "event-quarantine", EditorElementType.EVENT, "Quarantine warning", 80, 20);
        state.scenario.victoryConditions.add("Relief convoy reaches the habitat docks.");
        state.scenario.failureConditions.add("All relief transports are destroyed.");
        state.scenario.timeline.add("06:12 relief convoy enters low orbit.");
        state.scenario.timeline.add("06:18 Red reinforcements arrive if relay remains hostile.");
        state.community.compatibilityDiagnostics.add("core-kepler: compatible with base-game>=1");
        state.community.perSavePackManifest.add("core-kepler@1");
        state.community.featuredScenarios.add("Kepler Relief Corridor");
        loadContentPack(state, Path.of("."));
        return state;
    }

    public static void addElement(State state, String id, EditorElementType type, String label, int x, int y) {
        if (state == null || id == null || type == null || label == null) return;
        state.scenario.elements.add(new EditorElement(id, type, label, x, y));
    }

    public static boolean moveElement(State state, String id, int x, int y) {
        if (state == null || id == null) return false;
        for (EditorElement element : state.scenario.elements) {
            if (!id.equals(element.id)) continue;
            element.x = x;
            element.y = y;
            return true;
        }
        return false;
    }

    public static List<String> validateContentPack(State state) {
        if (state == null) return List.of("content-pack: state is missing");
        state.contentPack.validationErrors.clear();
        for (DefinitionKind kind : DefinitionKind.values()) {
            String file = state.contentPack.definitions.get(kind);
            if (file == null || file.isBlank()) {
                state.contentPack.validationErrors.add("manifest.properties: definitions." + kind.name().toLowerCase() + " is required");
            }
        }
        if (state.contentPack.schemaVersion < 1) {
            state.contentPack.validationErrors.add("manifest.properties: schemaVersion must be at least 1");
        }
        return List.copyOf(state.contentPack.validationErrors);
    }

    public static List<String> loadContentPack(State state, Path root) {
        if (state == null) return List.of("content-pack: state is missing");
        Path resolvedRoot = (root == null) ? Path.of(".") : root;
        state.contentPack.validationErrors.clear();
        state.contentPack.loadedDefinitions.clear();
        for (DefinitionKind kind : DefinitionKind.values()) {
            String configuredPath = state.contentPack.definitions.get(kind);
            if (configuredPath == null || configuredPath.isBlank()) {
                state.contentPack.validationErrors.add("manifest.properties: definitions."
                        + kind.name().toLowerCase() + " is required");
                continue;
            }
            loadDefinitionFile(state, resolvedRoot.resolve(configuredPath).normalize(), kind);
        }
        if (state.contentPack.schemaVersion < 1) {
            state.contentPack.validationErrors.add("manifest.properties: schemaVersion must be at least 1");
        }
        return List.copyOf(state.contentPack.validationErrors);
    }

    public static List<String> hotReload(State state, Path root) {
        if (state == null) return List.of("content-pack: state is missing");
        if (!state.contentPack.hotReloadEnabled) return List.of("content-pack: hot reload is disabled");
        return loadContentPack(state, root);
    }

    public static void configurePack(State state, boolean enabled, int loadOrder) {
        if (state == null) return;
        state.contentPack.enabled = enabled;
        state.contentPack.loadOrder = Math.max(0, loadOrder);
        refreshPerSaveManifest(state);
    }

    public static boolean migratePack(State state, int targetSchemaVersion) {
        if (state == null || !state.contentPack.migrationHelpers || targetSchemaVersion < state.contentPack.schemaVersion) {
            return false;
        }
        state.contentPack.schemaVersion = targetSchemaVersion;
        refreshPerSaveManifest(state);
        return true;
    }

    public static List<String> resolveDependencies(State state, List<String> installedPacks) {
        if (state == null) return List.of("content-pack: state is missing");
        List<String> installed = (installedPacks == null) ? List.of() : installedPacks;
        List<String> missing = new ArrayList<>();
        for (String dependency : state.contentPack.dependencies) {
            if (!installed.contains(dependency)) missing.add(dependency);
        }
        state.community.compatibilityDiagnostics.removeIf(line -> line.startsWith("missing dependency: "));
        for (String dependency : missing) state.community.compatibilityDiagnostics.add("missing dependency: " + dependency);
        return List.copyOf(missing);
    }

    public static boolean validateSavedManifest(State state, List<String> installedPacks, boolean replay) {
        if (state == null) return false;
        List<String> installed = (installedPacks == null) ? List.of() : installedPacks;
        for (String required : state.community.perSavePackManifest) {
            if (installed.contains(required)) continue;
            state.community.compatibilityDiagnostics.add((replay ? "replay" : "save") + " blocked: missing " + required);
            return false;
        }
        state.community.compatibilityDiagnostics.add((replay ? "replay" : "save") + " manifest compatible");
        return true;
    }

    private static void refreshPerSaveManifest(State state) {
        state.community.perSavePackManifest.clear();
        if (state.contentPack.enabled) {
            state.community.perSavePackManifest.add(state.contentPack.id + "@" + state.contentPack.schemaVersion);
        }
    }

    private static void loadDefinitionFile(State state, Path file, DefinitionKind kind) {
        if (!Files.isRegularFile(file)) {
            state.contentPack.validationErrors.add(file + ": file is missing");
            return;
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (lines.isEmpty() || lines.get(0).isBlank()) {
                state.contentPack.validationErrors.add(file + ":1: header is required");
                return;
            }
            String[] headers = cells(lines.get(0));
            List<String> identifierFields = (kind == DefinitionKind.DIALOGUE) ? List.of("pool", "key") : List.of("id");
            for (String required : identifierFields) requireColumn(state, file, headers, required);
            List<DefinitionRow> loaded = new ArrayList<>();
            for (int index = 1; index < lines.size(); index++) {
                if (lines.get(index).isBlank()) continue;
                String[] values = cells(lines.get(index));
                if (values.length != headers.length) {
                    state.contentPack.validationErrors.add(file + ":" + (index + 1)
                            + ": expected " + headers.length + " fields but found " + values.length);
                    continue;
                }
                Map<String, String> fields = new LinkedHashMap<>();
                for (int field = 0; field < headers.length; field++) {
                    fields.put(headers[field], values[field]);
                }
                boolean identifierMissing = false;
                for (String identifier : identifierFields) {
                    if (!fields.getOrDefault(identifier, "").isBlank()) continue;
                    state.contentPack.validationErrors.add(file + ":" + (index + 1) + ": " + identifier + " is required");
                    identifierMissing = true;
                }
                if (identifierMissing) continue;
                loaded.add(new DefinitionRow(file.toString(), index + 1, fields));
            }
            state.contentPack.loadedDefinitions.put(kind, List.copyOf(loaded));
        } catch (Exception exception) {
            state.contentPack.validationErrors.add(file + ": unable to read: " + exception.getMessage());
        }
    }

    private static void requireColumn(State state, Path file, String[] headers, String required) {
        for (String header : headers) if (required.equals(header)) return;
        state.contentPack.validationErrors.add(file + ":1: missing required field '" + required + "'");
    }

    private static String[] cells(String line) {
        return line.split(",", -1);
    }

    public static String exportScenarioPack(State state) {
        if (state == null) return "";
        return state.scenario.id + "|" + state.scenario.deterministicSeed + "|" + state.scenario.elements.size()
                + "|" + state.scenario.title + "|" + String.join(";", state.contentPack.dependencies);
    }

    public static boolean importScenarioPack(State state, String encoded) {
        return importScenarioPack(state, encoded, List.of("base-game>=1"));
    }

    public static boolean importScenarioPack(State state, String encoded, List<String> installedPacks) {
        if (state == null || encoded == null) return false;
        String[] fields = encoded.split("\\|", -1);
        if ((fields.length != 4 && fields.length != 5) || fields[0].isBlank() || fields[3].isBlank()) {
            if (state != null) state.community.compatibilityDiagnostics.add("scenario import: malformed pack");
            return false;
        }
        long seed = decimal(fields[1], Long.MIN_VALUE);
        int elements = number(fields[2], -1);
        if (seed == Long.MIN_VALUE || elements < 0) {
            state.community.compatibilityDiagnostics.add("scenario import: invalid seed or element count");
            return false;
        }
        if (fields.length == 5 && !fields[4].isBlank()) {
            List<String> installed = (installedPacks == null) ? List.of() : installedPacks;
            for (String dependency : fields[4].split(";")) {
                if (installed.contains(dependency)) continue;
                state.community.compatibilityDiagnostics.add("scenario import: missing dependency " + dependency);
                return false;
            }
        }
        state.scenario.id = fields[0].trim();
        state.scenario.deterministicSeed = seed;
        state.scenario.title = fields[3].trim();
        state.community.compatibilityDiagnostics.add("scenario import: " + state.scenario.id + " validated");
        return true;
    }

    public static String exportShareCode(State state, ShareCodeKind kind) {
        if (state == null || kind == null) return "";
        return switch (kind) {
            case DOCTRINE -> state.community.fleetDoctrineCode;
            case CUSTOM_BATTLE -> state.community.customBattleCode;
            case CHALLENGE -> state.community.challengeCode;
            case CAMPAIGN_LEGACY -> state.community.campaignLegacyCode;
        };
    }

    public static boolean importShareCode(State state, ShareCodeKind kind, String code) {
        if (state == null || kind == null || code == null || code.isBlank()) return false;
        String normalized = code.trim().toUpperCase();
        String prefix = switch (kind) {
            case DOCTRINE -> "DOC-";
            case CUSTOM_BATTLE -> "BAT-";
            case CHALLENGE -> "CHL-";
            case CAMPAIGN_LEGACY -> "LEG-";
        };
        if (!normalized.startsWith(prefix) || normalized.length() <= prefix.length()) {
            state.community.compatibilityDiagnostics.add("share code: invalid " + kind.name().toLowerCase());
            return false;
        }
        switch (kind) {
            case DOCTRINE -> state.community.fleetDoctrineCode = normalized;
            case CUSTOM_BATTLE -> state.community.customBattleCode = normalized;
            case CHALLENGE -> state.community.challengeCode = normalized;
            case CAMPAIGN_LEGACY -> state.community.campaignLegacyCode = normalized;
        }
        return true;
    }

    public static void launchTestPlay(State state) {
        if (state != null && state.scenario.testPlayAvailable) state.scenario.testPlayLaunches++;
    }

    public static void setSafeMode(State state, boolean safeMode) {
        if (state == null) return;
        state.community.safeMode = safeMode;
        state.contentPack.enabled = !safeMode;
        refreshPerSaveManifest(state);
    }

    public static void rateScenario(State state, String scenarioId, int rating, String note) {
        if (state == null || scenarioId == null || scenarioId.isBlank()) return;
        state.community.localRatings.put(scenarioId, Math.max(1, Math.min(5, rating)));
        state.community.localNotes.put(scenarioId, (note == null) ? "" : note.trim());
    }

    public static List<String> commandBoardLines(State state) {
        if (state == null) return List.of("Community content data unavailable.");
        return List.of(
                "Content pack " + state.contentPack.id + "@" + state.contentPack.schemaVersion + "  |  Definitions " + state.contentPack.definitions.size()
                        + "  |  Loaded rows " + loadedRowCount(state) + "  |  Hot reload " + state.contentPack.hotReloadEnabled,
                "Scenario " + state.scenario.title + "  |  Canvas elements " + state.scenario.elements.size()
                        + "  |  Seed " + state.scenario.deterministicSeed,
                "Safe mode " + state.community.safeMode + "  |  Featured " + state.community.featuredScenarios.size()
                        + "  |  Ratings " + state.community.localRatings.size()
        );
    }

    private static int loadedRowCount(State state) {
        int count = 0;
        for (List<DefinitionRow> rows : state.contentPack.loadedDefinitions.values()) count += rows.size();
        return count;
    }

    public static String serialize(State state) {
        if (state == null) return "";
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String note = state.community.localNotes.getOrDefault(state.scenario.id, "");
        return state.contentPack.schemaVersion + "," + state.contentPack.loadOrder + "," + state.contentPack.enabled
                + "|" + state.scenario.deterministicSeed + "," + state.scenario.testPlayLaunches
                + "|" + state.community.safeMode + "," + state.community.localRatings.getOrDefault(state.scenario.id, 0)
                + "|" + encoder.encodeToString(note.getBytes(StandardCharsets.UTF_8))
                + "|" + encoder.encodeToString(String.join(";", state.community.perSavePackManifest).getBytes(StandardCharsets.UTF_8));
    }

    public static State restore(String raw, long seed) {
        State state = bootstrap(seed);
        if (raw == null || raw.isBlank()) return state;
        String[] parts = raw.split("\\|", -1);
        if (parts.length < 4) return state;
        String[] pack = parts[0].split(",", -1);
        if (pack.length >= 3) {
            state.contentPack.schemaVersion = Math.max(1, number(pack[0], 1));
            state.contentPack.loadOrder = Math.max(0, number(pack[1], 0));
            state.contentPack.enabled = Boolean.parseBoolean(pack[2]);
        }
        String[] scenario = parts[1].split(",", -1);
        if (scenario.length >= 2) {
            state.scenario.deterministicSeed = decimal(scenario[0], 7301L);
            state.scenario.testPlayLaunches = Math.max(0, number(scenario[1], 0));
        }
        String[] community = parts[2].split(",", -1);
        if (community.length >= 2) {
            state.community.safeMode = Boolean.parseBoolean(community[0]);
            int rating = number(community[1], 0);
            if (rating > 0) state.community.localRatings.put(state.scenario.id, Math.max(1, Math.min(5, rating)));
        }
        try {
            state.community.localNotes.put(state.scenario.id,
                    new String(Base64.getUrlDecoder().decode(parts[3]), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException ignored) {
            state.community.localNotes.put(state.scenario.id, "");
        }
        if (parts.length >= 5) {
            try {
                String manifest = new String(Base64.getUrlDecoder().decode(parts[4]), StandardCharsets.UTF_8);
                state.community.perSavePackManifest.clear();
                if (!manifest.isBlank()) state.community.perSavePackManifest.addAll(List.of(manifest.split(";")));
            } catch (IllegalArgumentException ignored) {
                refreshPerSaveManifest(state);
            }
        }
        return state;
    }

    private static int number(String value, int fallback) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException ex) { return fallback; }
    }

    private static long decimal(String value, long fallback) {
        try { return Long.parseLong(value); }
        catch (NumberFormatException ex) { return fallback; }
    }
}
