import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sections 12-15 production-readiness model over the game's existing asset and harness infrastructure. */
public final class ProductionReadinessLongevitySystem {
    public record TypedId(String kind, String value) {
        public TypedId {
            kind = clean(kind, "unknown");
            value = clean(value, "unset");
        }
    }

    public static final class ArtPipeline {
        public boolean factionHullSkins = true;
        public boolean damageStages = true;
        public boolean destroyedMultipartVariants = true;
        public boolean factionTurretSkins = true;
        public boolean enginePlumes = true;
        public boolean shieldImpacts = true;
        public boolean factionMissileTrails = true;
        public boolean stationModules = true;
        public boolean environmentalProps = true;
        public boolean officerPortraitSets = true;
        public boolean zoomReadableMapIcons = true;
        public String uiGuidelines = "8px spacing tokens, silhouette-first icons, high-contrast alert hierarchy";
        public final List<String> regressionScreenshots = new ArrayList<>();
    }

    public static final class AudioPipeline {
        public boolean factionWeaponIdentities = true;
        public boolean layeredEngines = true;
        public boolean differentiatedImpacts = true;
        public boolean stationAmbience = true;
        public boolean mapAmbience = true;
        public boolean battleIntensityMusic = true;
        public boolean lowResourceWarnings = true;
        public boolean incomingStrikeWarnings = true;
        public boolean jammingRadioDistortion = true;
        public boolean voiceCooldownPriority = true;
        public boolean dynamicAlertDucking = true;
        public boolean accessibilityCaptions = true;
    }

    public static final class SaveSlot {
        public final String id;
        public String label;
        public String metadata;
        public int schemaVersion;
        public long seed;
        public boolean corruptRecovered;

        SaveSlot(String id, String label, String metadata, int schemaVersion, long seed) {
            this.id = id;
            this.label = label;
            this.metadata = metadata;
            this.schemaVersion = schemaVersion;
            this.seed = seed;
        }
    }

    public static final class Longevity {
        public final List<SaveSlot> slots = new ArrayList<>();
        public int autosaveRotation = 3;
        public final List<String> migrationFixtures = new ArrayList<>();
        public final List<String> battleReplayFiles = new ArrayList<>();
        public final List<String> campaignEventLog = new ArrayList<>();
        public final List<String> postCampaignStatistics = new ArrayList<>();
        public final List<String> newGamePlusModifiers = new ArrayList<>();
        public final List<Long> challengeSeeds = new ArrayList<>();
        public final List<Long> scheduledScenarioSeeds = new ArrayList<>();
        public final List<String> customScenarioOptions = new ArrayList<>();
        public String sharedSeed = "ER-55-SHELTER";
        public String modCatalogPath = "config/mod_content_catalog.properties";
    }

    public static final class Architecture {
        public final List<String> ownershipBoundaries = new ArrayList<>();
        public final List<String> transitionApis = new ArrayList<>();
        public final List<TypedId> typedIds = new ArrayList<>();
        public final List<String> invariants = new ArrayList<>();
        public final List<String> structuredEvents = new ArrayList<>();
        public final List<String> scenarioFixtures = new ArrayList<>();
        public final List<String> validators = new ArrayList<>();
        public final List<String> assetReports = new ArrayList<>();
        public final Map<String, Integer> performanceBudgets = new LinkedHashMap<>();
        public String saveSchemaDiffDoc = "docs/CAMPAIGN_SAVE_SCHEMA.md";
        public String balanceExport = "config/balance_data_export.csv";
        public boolean deterministicSimulation = true;
        public boolean headlessCampaignPlayback = true;
        public boolean headlessTacticalPlayback = true;
        public boolean automatedScreenshotCapture = true;
    }

    public static final class TestingMatrix {
        public final List<String> smokeScenarios = new ArrayList<>();
        public final List<String> permutationSuites = new ArrayList<>();
        public final List<String> longRunSuites = new ArrayList<>();
        public final List<String> continuitySuites = new ArrayList<>();
        public final List<String> regressionChecks = new ArrayList<>();
        public final List<String> compatibilityFixtures = new ArrayList<>();
        public boolean randomizedCampaignTransitionFuzz = true;
    }

    public static final class State {
        public final ArtPipeline art = new ArtPipeline();
        public final AudioPipeline audio = new AudioPipeline();
        public final Longevity longevity = new Longevity();
        public final Architecture architecture = new Architecture();
        public final TestingMatrix testing = new TestingMatrix();
    }

    private ProductionReadinessLongevitySystem() {}

    public static State bootstrap(long seed) {
        State state = new State();
        state.art.regressionScreenshots.addAll(List.of("campaign-map", "fleet-board", "strike-tab", "tactical-hud", "accessibility-hud"));
        state.longevity.slots.add(new SaveSlot("slot-1", "Primary campaign", "Southern Shelter checkpoint", 8, seed));
        state.longevity.slots.add(new SaveSlot("slot-2", "Iron command", "Challenge reserve", 8, seed + 1));
        state.longevity.slots.add(new SaveSlot("slot-3", "Sandbox", "Custom scenario", 8, seed + 2));
        state.longevity.migrationFixtures.addAll(List.of("schema-v1", "schema-v4", "schema-v7", "schema-v8"));
        state.longevity.battleReplayFiles.add("replay-open-space-intercept.erreplay");
        state.longevity.campaignEventLog.add("00:00 campaign bootstrap seed=" + seed);
        state.longevity.postCampaignStatistics.addAll(List.of("battles", "losses", "rescues", "allies", "doctrine", "ending"));
        state.longevity.newGamePlusModifiers.addAll(List.of("veteran captains", "scarred hulls", "aggressive directors"));
        state.longevity.challengeSeeds.addAll(List.of(20260601L, 20260608L));
        state.longevity.scheduledScenarioSeeds.addAll(List.of(20260601L, 20260607L));
        state.longevity.customScenarioOptions.addAll(List.of("fleet", "factions", "location", "hazards", "difficulty", "seed"));

        state.architecture.ownershipBoundaries.addAll(List.of("campaign simulation", "tactical simulation", "UI projection", "persistence", "presentation"));
        state.architecture.transitionApis.addAll(List.of("travel", "checkpoint", "encounter", "strike", "fleet refit", "modal ownership"));
        state.architecture.typedIds.addAll(List.of(
                new TypedId("fleet", "fleet-blue-1"), new TypedId("battle", "battle-lunar-1"),
                new TypedId("location", "poi-05"), new TypedId("ship", "ship-101"),
                new TypedId("prompt", "prompt-intervention-1"), new TypedId("contract", "contract-escort")));
        state.architecture.invariants.addAll(List.of("stale references", "duplicate ownership", "impossible overlays"));
        state.architecture.structuredEvents.addAll(List.of("campaign.transition", "fleet.ownership", "strike.launch", "checkpoint.save"));
        state.architecture.scenarioFixtures.addAll(List.of("campaign-start", "mining-dock", "travel-intercept", "tactical-victory", "retreat-save-load"));
        state.architecture.validators.addAll(List.of("asset validation", "missing asset report", "duplicate asset report", "content authoring validator"));
        state.architecture.assetReports.addAll(List.of("missing-assets.json", "duplicate-assets.json", "visual-regression/index.json"));
        state.architecture.performanceBudgets.put("frame-ms", 16);
        state.architecture.performanceBudgets.put("heap-mb", 1024);
        state.architecture.performanceBudgets.put("ships", 320);

        state.testing.smokeScenarios.addAll(List.of("campaign start", "mining", "docking", "travel", "intercept", "tactical entry", "victory", "retreat", "save/load"));
        state.testing.permutationSuites.addAll(List.of("overlay state", "hotkey context", "faction hostility", "UI hitbox/render bounds"));
        state.testing.longRunSuites.addAll(List.of("fleet director", "economy", "route risk forecast", "memory usage", "frame time"));
        state.testing.continuitySuites.addAll(List.of("encounter families", "persistent ship casualty reconciliation", "strike families", "sensor certainty decay"));
        state.testing.regressionChecks.addAll(List.of("accessibility screenshots", "save compatibility", "randomized campaign transition fuzz"));
        state.testing.compatibilityFixtures.addAll(List.of("schema-v1.properties", "schema-v4.properties", "schema-v7.properties"));
        return state;
    }

    public static void appendCampaignEvent(State state, String event) {
        if (state != null && event != null && !event.isBlank()) state.longevity.campaignEventLog.add(event.trim());
    }

    public static void recoverCorruptSlot(State state, String slotId) {
        SaveSlot slot = slot(state, slotId);
        if (slot != null) {
            slot.corruptRecovered = true;
            slot.metadata = "Recovered from rotating autosave";
        }
    }

    public static List<String> commandBoardLines(State state) {
        if (state == null) return List.of("Production data unavailable.");
        return List.of(
                "Presentation screens " + state.art.regressionScreenshots.size() + "  |  Audio captions " + state.audio.accessibilityCaptions,
                "Save slots " + state.longevity.slots.size() + "  |  Autosaves " + state.longevity.autosaveRotation
                        + "  |  Seed " + state.longevity.sharedSeed,
                "Boundaries " + state.architecture.ownershipBoundaries.size() + "  |  Smoke scenarios " + state.testing.smokeScenarios.size()
        );
    }

    public static String serialize(State state) {
        if (state == null) return "";
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return state.longevity.autosaveRotation + "|" + enc(encoder, state.longevity.sharedSeed) + "|"
                + state.testing.randomizedCampaignTransitionFuzz + "|" + enc(encoder, state.longevity.slots.get(0).metadata);
    }

    public static State restore(String raw, long seed) {
        State state = bootstrap(seed);
        if (raw == null || raw.isBlank()) return state;
        String[] parts = raw.split("\\|", -1);
        if (parts.length < 4) return state;
        state.longevity.autosaveRotation = clamp(number(parts[0], 3), 1, 12);
        state.longevity.sharedSeed = dec(parts[1], state.longevity.sharedSeed);
        state.testing.randomizedCampaignTransitionFuzz = Boolean.parseBoolean(parts[2]);
        state.longevity.slots.get(0).metadata = dec(parts[3], state.longevity.slots.get(0).metadata);
        return state;
    }

    private static SaveSlot slot(State state, String slotId) {
        if (state == null || slotId == null) return null;
        for (SaveSlot slot : state.longevity.slots) if (slot.id.equals(slotId)) return slot;
        return null;
    }

    private static String clean(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    private static String enc(Base64.Encoder encoder, String value) {
        return encoder.encodeToString(clean(value, "").getBytes(StandardCharsets.UTF_8));
    }

    private static String dec(String value, String fallback) {
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static int number(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
