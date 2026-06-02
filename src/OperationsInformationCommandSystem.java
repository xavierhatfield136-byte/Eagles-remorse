import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sections 8-11 campaign-facing mission, information-warfare, support, and command model. */
public final class OperationsInformationCommandSystem {
    public enum MissionFamily {
        CONVOY_ESCORT, CONVOY_INTERCEPTION, BLOCKADE_RUN, STATION_DEFENSE, STATION_EVACUATION,
        SEARCH_AND_RESCUE, SALVAGE_RACE, STEALTH_RECONNAISSANCE, AMBUSH_SETUP, MINEFIELD_CLEARANCE,
        BOARDING_CAPTURE, DISABLED_SHIP_TOW, PRISON_TRANSPORT_INTERCEPTION, DIPLOMATIC_ESCORT, SMUGGLING,
        MULTI_STAGE_PURSUIT, RETREAT_UNDER_PRESSURE, FLEET_RENDEZVOUS, TITAN_HUNT, ANOMALY_INVESTIGATION
    }

    public enum BattlefieldType { HUB, BELT, WRECK_FIELD, ORBITAL_LANE, DEEP_SPACE }
    public enum SpaceHazard { NAVIGATION, NEBULA, GRAVITY_ANOMALY, SOLAR_FLARE, ASTEROID_OCCLUSION, MINEFIELD, JUMP_POINT_TURBULENCE }
    public enum SensorMode { PASSIVE, ACTIVE }
    public enum ContactClassification { UNKNOWN, FALSE_POSITIVE, MERGED_RETURN, SPLIT_RETURN, DECOY_FLEET, SPOOFED_TRANSPONDER, IDENTIFIED_HOSTILE }
    public enum SupportType { RECONNAISSANCE, ELECTRONIC_WARFARE, MINE_LAYING, REPAIR_TENDER, EMERGENCY_EXTRACTION, RESERVE_FLEET, ORBITAL_BOMBARDMENT }
    public enum StrikePayload { TORPEDO, SORTIE, ATOMIC, ELECTRONIC_ATTACK }
    public enum Screen { MAP, FLEET, RESOURCES, CONTACTS, STRIKES }
    public enum PanelMode { COMPACT, EXPANDED }
    public enum DensityPreset { LOW, STANDARD, DENSE }
    public enum HudPreset { COMMAND, PILOTING, ACCESSIBILITY, SCREENSHOT }
    public enum WarningCategory { RESOURCE, CONTACT, STRIKE, COLLISION, MISSILE, MISSION }

    public static final class MissionTemplate {
        public final MissionFamily family;
        public boolean routeDecision;
        public int stages = 1;
        public String objective;

        MissionTemplate(MissionFamily family, String objective) {
            this.family = family;
            this.objective = objective;
        }
    }

    public static final class LiveMission {
        public final MissionFamily family;
        public final String objective;
        public final String reward;
        public final String failure;
        public final String aftermath;
        public final String provenance;
        public final String warning;

        LiveMission(MissionFamily family, String objective, String reward, String failure,
                    String aftermath, String provenance, String warning) {
            this.family = family;
            this.objective = objective;
            this.reward = reward;
            this.failure = failure;
            this.aftermath = aftermath;
            this.provenance = provenance;
            this.warning = warning;
        }
    }

    public static final class BattlefieldTemplate {
        public final BattlefieldType type;
        public String factionArchitecture;
        public boolean civilianTrafficLanes;
        public boolean destructibleInfrastructure;
        public boolean neutralCollateralStructures;
        public final List<SpaceHazard> hazards = new ArrayList<>();
        public String persistentBattleScar;
        public String audioAmbience;

        BattlefieldTemplate(BattlefieldType type, String factionArchitecture, String audioAmbience) {
            this.type = type;
            this.factionArchitecture = factionArchitecture;
            this.audioAmbience = audioAmbience;
        }
    }

    public static final class CompositionRule {
        public String representedCampaignForce;
        public String doctrineReinforcements;
        public String factionFormation;
        public int difficultyTier;
        public boolean hiddenStatInflation;
        public boolean environmentalCompatibilityChecked;
        public String objectiveSpawnLane;
        public boolean civilianPresenceRule;
        public String cleanupRule;
        public long deterministicSeed;
    }

    public static final class SensorProfile {
        public SensorMode mode = SensorMode.PASSIVE;
        public int emissionRiskPercent;
        public String hullSignature;
        public int speedSignature;
        public int damageSignature;
        public int weaponUseSignature;
        public int silentRunningPenalty;
    }

    public static final class Contact {
        public final String id;
        public ContactClassification classification;
        public int confidencePercent;
        public int ageMinutes;
        public boolean merged;
        public boolean split;
        public boolean communicationsIntercepted;
        public boolean jammingCone;
        public boolean jammingAreaEffect;

        Contact(String id, ContactClassification classification, int confidencePercent, int ageMinutes) {
            this.id = id;
            this.classification = classification;
            this.confidencePercent = confidencePercent;
            this.ageMinutes = ageMinutes;
        }
    }

    public static final class IntelligenceState {
        public final SensorProfile sensorProfile = new SensorProfile();
        public final List<Contact> contacts = new ArrayList<>();
        public final List<String> relayPlacementDecisions = new ArrayList<>();
        public final List<String> scoutPatrolRoutes = new ArrayList<>();
        public final List<String> stealthApproachRoutes = new ArrayList<>();
        public final List<String> counterIntelligenceActions = new ArrayList<>();
        public final List<String> enemyAdaptations = new ArrayList<>();
        public int electronicAttackStrikes;
    }

    public static final class StrikePackage {
        public final String id;
        public StrikePayload payload;
        public final List<SupportType> support = new ArrayList<>();
        public int preparationHours;
        public String launchPlatformRequirement;
        public int interceptionRiskPercent;
        public int targetQualityThreshold;
        public int collateralEstimate;
        public int decoyTargetRiskPercent;
        public boolean enemyCounterStrike;
        public boolean strikeDefenseInstallation;
        public String afterActionImagery;
        public String afterActionReport;

        StrikePackage(String id, StrikePayload payload) {
            this.id = id;
            this.payload = payload;
        }
    }

    public static final class RoutePreview {
        public int fuel;
        public int hours;
        public int dangerPercent;
        public int likelyContacts;
    }

    public static final class CommandExperience {
        public final List<Screen> screenHierarchy = new ArrayList<>();
        public final List<String> breadcrumbs = new ArrayList<>();
        public final List<String> notificationInbox = new ArrayList<>();
        public final List<String> operationsLog = new ArrayList<>();
        public final List<String> mapBookmarks = new ArrayList<>();
        public final List<String> pinnedContacts = new ArrayList<>();
        public final EnumMap<WarningCategory, Boolean> warningFilters = new EnumMap<>(WarningCategory.class);
        public final Map<String, String> visibleAutomationRules = new LinkedHashMap<>();
        public final RoutePreview routePreview = new RoutePreview();
        public PanelMode panelMode = PanelMode.COMPACT;
        public DensityPreset densityPreset = DensityPreset.STANDARD;
        public HudPreset hudPreset = HudPreset.COMMAND;
        public boolean consistentBackBehavior = true;
        public boolean compareDestinations;
        public boolean compareFleets;
        public boolean compareContracts;
        public boolean pauseAndPlan;
        public String mapSearch = "";
        public int targetCardScalePercent = 100;
        public boolean shieldFacing = true;
        public boolean subsystemDamagePriority = true;
        public boolean alliedOrderStatus = true;
        public boolean formationVisualization = true;
        public boolean missileWarnings = true;
        public boolean incomingStrikeWarnings = true;
        public boolean collisionAlerts = true;
        public boolean offscreenThreatIndicators = true;
        public int combatLogVerbosity = 2;
        public boolean screenshotMode;
    }

    public static final class State {
        public final List<MissionTemplate> missionTemplates = new ArrayList<>();
        public final List<LiveMission> liveAlphaMissions = new ArrayList<>();
        public final List<BattlefieldTemplate> battlefields = new ArrayList<>();
        public final List<CompositionRule> compositionRules = new ArrayList<>();
        public final IntelligenceState intelligence = new IntelligenceState();
        public final List<StrikePackage> strikePackages = new ArrayList<>();
        public final CommandExperience command = new CommandExperience();
    }

    private OperationsInformationCommandSystem() {}

    public static State bootstrap(long seed) {
        State state = new State();
        for (MissionFamily family : MissionFamily.values()) {
            MissionTemplate template = new MissionTemplate(family, family.name().replace('_', ' ').toLowerCase());
            template.routeDecision = family == MissionFamily.CONVOY_ESCORT || family == MissionFamily.BLOCKADE_RUN
                    || family == MissionFamily.SMUGGLING || family == MissionFamily.MULTI_STAGE_PURSUIT;
            template.stages = (family == MissionFamily.MULTI_STAGE_PURSUIT || family == MissionFamily.RETREAT_UNDER_PRESSURE) ? 3 : 1;
            state.missionTemplates.add(template);
        }
        addBattlefield(state, BattlefieldType.HUB, "faction hub architecture", "orbital harbor traffic", SpaceHazard.NAVIGATION);
        addBattlefield(state, BattlefieldType.BELT, "industrial claim platforms", "mining drones and rock impacts", SpaceHazard.ASTEROID_OCCLUSION, SpaceHazard.MINEFIELD);
        addBattlefield(state, BattlefieldType.WRECK_FIELD, "salvage gantries", "distant hull groans", SpaceHazard.NAVIGATION, SpaceHazard.NEBULA);
        addBattlefield(state, BattlefieldType.ORBITAL_LANE, "orbital defense lattice", "relay traffic and defense klaxons", SpaceHazard.GRAVITY_ANOMALY, SpaceHazard.SOLAR_FLARE);
        addBattlefield(state, BattlefieldType.DEEP_SPACE, "expedition beacons", "low engine resonance", SpaceHazard.JUMP_POINT_TURBULENCE, SpaceHazard.NEBULA);
        CompositionRule rule = new CompositionRule();
        rule.representedCampaignForce = "Represent live task-group roster";
        rule.doctrineReinforcements = "Doctrine-weighted reserve screen";
        rule.factionFormation = "Faction-specific wedge, screen, or artillery line";
        rule.difficultyTier = 2;
        rule.environmentalCompatibilityChecked = true;
        rule.objectiveSpawnLane = "Objective-aware approach corridor";
        rule.civilianPresenceRule = true;
        rule.cleanupRule = "Persist scars, salvage, survivors, and cleared hazards";
        rule.deterministicSeed = seed;
        state.compositionRules.add(rule);

        state.intelligence.sensorProfile.hullSignature = "Hull, speed, damage, and weapons weighted";
        state.intelligence.sensorProfile.silentRunningPenalty = 28;
        addContact(state, "ghost-1", ContactClassification.FALSE_POSITIVE, 18, 7);
        Contact merged = addContact(state, "convoy-shadow", ContactClassification.MERGED_RETURN, 52, 3);
        merged.merged = true;
        merged.split = true;
        merged.communicationsIntercepted = true;
        merged.jammingCone = true;
        merged.jammingAreaEffect = true;
        addContact(state, "decoy-wing", ContactClassification.DECOY_FLEET, 34, 5);
        addContact(state, "spoofed-runner", ContactClassification.SPOOFED_TRANSPONDER, 41, 2);
        state.intelligence.relayPlacementDecisions.add("Forward relay or protected rear relay");
        state.intelligence.scoutPatrolRoutes.add("Shelter -> Frontier -> Lunar loop");
        state.intelligence.stealthApproachRoutes.add("Hidden-route silent approach");
        state.intelligence.counterIntelligenceActions.add("Sweep spoofed transponders");
        state.intelligence.enemyAdaptations.add("Red widens search spacing after repeated active sweeps");

        StrikePackage strike = new StrikePackage("package-aegis", StrikePayload.TORPEDO);
        strike.support.addAll(List.of(SupportType.values()));
        strike.preparationHours = 4;
        strike.launchPlatformRequirement = "Carrier or forward launch platform in range";
        strike.interceptionRiskPercent = 26;
        strike.targetQualityThreshold = 65;
        strike.collateralEstimate = 12;
        strike.decoyTargetRiskPercent = 18;
        strike.enemyCounterStrike = true;
        strike.strikeDefenseInstallation = true;
        strike.afterActionImagery = "Sensor imagery archived";
        strike.afterActionReport = "Strike report pending battle-damage assessment";
        state.strikePackages.add(strike);

        state.command.screenHierarchy.addAll(List.of(Screen.values()));
        state.command.breadcrumbs.add("Map > Contacts > Strike Package");
        state.command.notificationInbox.add("New intelligence report");
        state.command.operationsLog.add("00:00 Operations board initialized");
        state.command.mapBookmarks.add("Southern Shelter");
        state.command.pinnedContacts.add("convoy-shadow");
        for (WarningCategory category : WarningCategory.values()) state.command.warningFilters.put(category, true);
        state.command.visibleAutomationRules.put("relief-group", "Retreat at 35%; preserve civilians");
        state.command.routePreview.fuel = 14;
        state.command.routePreview.hours = 8;
        state.command.routePreview.dangerPercent = 31;
        state.command.routePreview.likelyContacts = 2;
        return state;
    }

    public static void refreshLiveAlphaMissions(State state, int hostileForces, int supportHubs,
                                                int wreckFields, int damagedFriendlyShips,
                                                boolean blockadePressure) {
        if (state == null) return;
        state.liveAlphaMissions.clear();
        int hostile = Math.max(1, hostileForces);
        int hubs = Math.max(1, supportHubs);
        int wrecks = Math.max(1, wreckFields);
        int damaged = Math.max(1, damagedFriendlyShips);
        String hostileProvenance = hostile + " tracked hostile campaign force" + (hostile == 1 ? "" : "s");
        String hubProvenance = hubs + " visible support hub" + (hubs == 1 ? "" : "s");
        String wreckProvenance = wrecks + " persistent wreck field" + (wrecks == 1 ? "" : "s");
        addLiveMission(state, MissionFamily.CONVOY_ESCORT, "Escort relief traffic through a threatened lane",
                "credits and route stability", "convoy losses reduce regional supply", "traffic route remains visible", hubProvenance);
        addLiveMission(state, MissionFamily.CONVOY_INTERCEPTION, "Intercept hostile logistics before they reach the front",
                "salvage and reduced enemy supply", "hostile readiness improves", "wrecks remain on the route", hostileProvenance);
        addLiveMission(state, MissionFamily.BLOCKADE_RUN, "Break through the blockade corridor",
                "fuel access and route recovery", "shortages worsen", "lane control changes", blockadePressure ? hostileProvenance : hubProvenance);
        addLiveMission(state, MissionFamily.STATION_DEFENSE, "Defend station services from an incoming strike group",
                "open services and repairs", "station modules are disabled", "damage remains visible", hubProvenance);
        addLiveMission(state, MissionFamily.STATION_EVACUATION, "Hold evacuation lanes until transports clear",
                "civilian trust and recovery traffic", "refugees and service capacity are lost", "memorial state records losses", hubProvenance);
        addLiveMission(state, MissionFamily.SEARCH_AND_RESCUE, "Recover survivors before the distress window closes",
                "crew recovery and favor", "the signal becomes a grave field", "rescue outcome persists", damaged + " damaged or missing hull lead" + (damaged == 1 ? "" : "s"));
        addLiveMission(state, MissionFamily.SALVAGE_RACE, "Secure useful wreckage before rival crews arrive",
                "salvage and black-box intel", "easy recovery is stripped", "wreck field becomes spent", wreckProvenance);
        addLiveMission(state, MissionFamily.STEALTH_RECONNAISSANCE, "Identify a hostile route without committing the fleet",
                "contact confidence and strike quality", "enemy alert rises", "sensor picture ages normally", hostileProvenance);
        addLiveMission(state, MissionFamily.AMBUSH_SETUP, "Use a tracked hostile route to prepare an ambush",
                "first-engagement advantage", "enemy pressure continues", "route danger changes", hostileProvenance);
        addLiveMission(state, MissionFamily.MINEFIELD_CLEARANCE, "Clear mines from a logistics lane",
                "safer travel and support access", "lane remains hazardous", "cleared hazard state persists", hubProvenance);
        addLiveMission(state, MissionFamily.DISABLED_SHIP_TOW, "Tow a disabled hull back to support traffic",
                "recovered hull and rescue favor", "hull is lost", "recovery route remains visible", damaged + " damaged fleet hull" + (damaged == 1 ? "" : "s"));
        addLiveMission(state, MissionFamily.RETREAT_UNDER_PRESSURE, "Extract a depleted formation under pursuit",
                "preserved fleet strength", "additional hulls are lost", "retreat changes theater pressure", hostileProvenance);
        addLiveMission(state, MissionFamily.FLEET_RENDEZVOUS, "Reach allied support before the contact closes",
                "reinforcement and resupply", "support arrives too late", "support route remains on the map", hubProvenance);
    }

    private static void addLiveMission(State state, MissionFamily family, String objective, String reward,
                                       String failure, String aftermath, String provenance) {
        state.liveAlphaMissions.add(new LiveMission(family, objective, reward, failure, aftermath, provenance,
                "Visible warning: confirm contact confidence, support availability, and route risk before commitment."));
    }

    private static void addBattlefield(State state, BattlefieldType type, String architecture, String ambience, SpaceHazard... hazards) {
        BattlefieldTemplate battlefield = new BattlefieldTemplate(type, architecture, ambience);
        battlefield.civilianTrafficLanes = true;
        battlefield.destructibleInfrastructure = true;
        battlefield.neutralCollateralStructures = true;
        battlefield.hazards.addAll(List.of(hazards));
        battlefield.persistentBattleScar = "Revisit retains battle damage";
        state.battlefields.add(battlefield);
    }

    private static Contact addContact(State state, String id, ContactClassification classification, int confidence, int age) {
        Contact contact = new Contact(id, classification, confidence, age);
        state.intelligence.contacts.add(contact);
        return contact;
    }

    public static void activateSensors(State state) {
        if (state == null) return;
        state.intelligence.sensorProfile.mode = SensorMode.ACTIVE;
        state.intelligence.sensorProfile.emissionRiskPercent = 44;
    }

    public static void engageSilentRunning(State state) {
        if (state == null) return;
        state.intelligence.sensorProfile.mode = SensorMode.PASSIVE;
        state.intelligence.sensorProfile.emissionRiskPercent = 6;
        state.intelligence.sensorProfile.speedSignature = 0;
    }

    public static void recordElectronicAttack(State state) {
        if (state != null) state.intelligence.electronicAttackStrikes++;
    }

    public static void logOperation(State state, String timestamp, String detail) {
        if (state == null || detail == null || detail.isBlank()) return;
        state.command.operationsLog.add(((timestamp == null || timestamp.isBlank()) ? "00:00" : timestamp.trim()) + " " + detail.trim());
    }

    public static void bookmark(State state, String location) {
        if (state != null && location != null && !location.isBlank()) state.command.mapBookmarks.add(location.trim());
    }

    public static List<String> commandBoardLines(State state) {
        if (state == null) return List.of("Operations data unavailable.");
        return List.of(
                "Mission families " + state.missionTemplates.size() + "  |  Battlefields " + state.battlefields.size()
                        + "  |  Live alpha set " + state.liveAlphaMissions.size(),
                "Sensor mode " + state.intelligence.sensorProfile.mode + "  |  Contacts " + state.intelligence.contacts.size()
                        + "  |  EW strikes " + state.intelligence.electronicAttackStrikes,
                "Strike packages " + state.strikePackages.size() + "  |  Screens " + state.command.screenHierarchy.size()
                        + "  |  HUD " + state.command.hudPreset
        );
    }

    public static String serialize(State state) {
        if (state == null) return "";
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        CommandExperience command = state.command;
        SensorProfile sensors = state.intelligence.sensorProfile;
        return sensors.mode + "," + sensors.emissionRiskPercent + "," + state.intelligence.electronicAttackStrikes
                + "|" + command.panelMode + "," + command.densityPreset + "," + command.hudPreset + ","
                + command.pauseAndPlan + "," + command.targetCardScalePercent + "," + command.combatLogVerbosity + ","
                + command.screenshotMode + "|" + enc(encoder, command.mapSearch);
    }

    public static State restore(String raw, long seed) {
        State state = bootstrap(seed);
        if (raw == null || raw.isBlank()) return state;
        String[] parts = raw.split("\\|", -1);
        if (parts.length < 3) return state;
        String[] sensors = parts[0].split(",", -1);
        if (sensors.length >= 3) {
            state.intelligence.sensorProfile.mode = value(sensors[0], SensorMode.PASSIVE);
            state.intelligence.sensorProfile.emissionRiskPercent = clamp(number(sensors[1], 0), 0, 100);
            state.intelligence.electronicAttackStrikes = Math.max(0, number(sensors[2], 0));
        }
        String[] command = parts[1].split(",", -1);
        if (command.length >= 7) {
            state.command.panelMode = value(command[0], PanelMode.COMPACT);
            state.command.densityPreset = value(command[1], DensityPreset.STANDARD);
            state.command.hudPreset = value(command[2], HudPreset.COMMAND);
            state.command.pauseAndPlan = Boolean.parseBoolean(command[3]);
            state.command.targetCardScalePercent = clamp(number(command[4], 100), 50, 200);
            state.command.combatLogVerbosity = clamp(number(command[5], 2), 0, 3);
            state.command.screenshotMode = Boolean.parseBoolean(command[6]);
        }
        state.command.mapSearch = dec(parts[2], "");
        return state;
    }

    private static String enc(Base64.Encoder encoder, String text) {
        return encoder.encodeToString(((text == null) ? "" : text).getBytes(StandardCharsets.UTF_8));
    }

    private static String dec(String text, String fallback) {
        try {
            return new String(Base64.getUrlDecoder().decode(text), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static int number(String text, int fallback) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int clamp(int number, int min, int max) {
        return Math.max(min, Math.min(max, number));
    }

    private static <T extends Enum<T>> T value(String text, T fallback) {
        try {
            return Enum.valueOf(fallback.getDeclaringClass(), text);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
