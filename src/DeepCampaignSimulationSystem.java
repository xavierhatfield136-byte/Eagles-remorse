import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sections 19-25 persistent deep-campaign simulation state. */
public final class DeepCampaignSimulationSystem {
    public enum StationModule { DOCKS, REACTORS, SENSORS, DEFENSE_GRID, REFINERY, HABITAT }
    public enum ModuleStatus { OPERATIONAL, DISABLED, REPAIRING, CAPTURED, DESTROYED }
    public enum WreckPurpose { SALVAGE_SITE, AMBUSH_SITE, HAZARD, MEMORIAL }
    public enum OrbitalLayer { LOW_ORBIT, HIGH_ORBIT, SHADOW_ZONE, MOON_RELAY, ELEVATOR, EVACUATION_CORRIDOR, QUARANTINE }
    public enum IntelReliability { LOW, MEDIUM, HIGH, CONFIRMED }
    public enum ResourcePolicy { CONSERVATION, EXTRACTION, RATIONING, HOARDING }
    public enum CrisisType { FUEL, AMMUNITION, REPAIR_MATERIALS, CREW_REPLACEMENTS, REFUGEES, EPIDEMIC, MUTINY, INTELLIGENCE_LEAK, SABOTAGE, FRONT_COLLAPSE, COALITION_SUMMIT, LEGITIMACY }
    public enum RecoveryType { WITHDRAWAL, REBUILD, LOAN, REACTIVATE_HULLS, CIVILIAN_REQUISITION, IMPROVISED_REPAIR, SALVAGE_EXPEDITION, PRISONER_EXCHANGE, HUMANITARIAN_CORRIDOR, COMEBACK, EPILOGUE, RESISTANCE }
    public enum EndgameType { FINAL_OFFENSIVE, EVACUATION, COALITION, ROGUE_AI, ECONOMIC_BLOCKADE, TITAN_RACE }
    public enum ChallengeType { ONE_FLEET, IRON_FLEET, CIVILIAN_PROTECTION, LOGISTICS_STARVATION, STEALTH_INTELLIGENCE, PIRATE_PRIVATEER, TITAN_RACE, SHATTERED_ALLIANCE, MONTHLY }

    public static final class Station {
        public final String name;
        public final Map<StationModule, ModuleStatus> modules = new EnumMap<>(StationModule.class);
        public int constructionPercent;
        public boolean constructionBarge;
        public boolean emergencyShutdown;
        public int evacuationCapacity = 800;
        public int garrisonReadinessPercent = 70;
        public String commander = "Commander Vale";
        public String commanderTrait = "Methodical";
        public String affiliation = "Civilian Reform Bloc";
        public int orbitalRelayCoveragePercent = 80;
        public boolean smugglerDock;
        public boolean improvisedRepairYard;
        public boolean abandoned;
        public int reclaimCost = 240;
        public boolean mobile;
        public String relocationOrder = "";
        public boolean memorial;

        Station(String name) {
            this.name = name;
            for (StationModule module : StationModule.values()) modules.put(module, ModuleStatus.OPERATIONAL);
        }
    }

    public static final class Location {
        public final String name;
        public final List<WreckPurpose> wreckFieldUses = new ArrayList<>();
        public final List<String> history = new ArrayList<>();
        public int wrecks;
        public int tradeHubGrowth;
        public int servicesPercent = 100;
        public int miningYieldPercent = 100;
        public boolean deeperDeposit;
        public boolean visibleScars;
        public boolean reconstructionProject;
        public int refugees;
        public boolean militaryCheckpoint;
        public String trafficSeason = "Convoy surge";
        public String beforeAfterSummary = "Stable trade route -> defended reconstruction corridor";

        Location(String name) { this.name = name; }
    }

    public static final class OrbitalEnvironment {
        public final List<OrbitalLayer> layers = new ArrayList<>();
        public int atmosphericDragPercent = 12;
        public int debrisDensityPercent = 28;
        public int transferEfficiencyPercent = 76;
        public int sensorShadowPercent = 35;
        public int solarPowerPenaltyPercent = 18;
        public boolean moonArtillery;
        public boolean reentryTransports = true;
        public boolean rescueCapsules = true;
        public int planetaryAllegiance = 54;
        public String presentation = "Low-orbit skybox, radio ambience, and orbital symbology";
    }

    public static final class Officer {
        public final String name;
        public final List<String> shipHistory = new ArrayList<>();
        public final List<String> commendations = new ArrayList<>();
        public final List<String> disciplinaryRecords = new ArrayList<>();
        public String assignment;
        public String promotionRecommendation = "";
        public String mentor = "";
        public int trainingHours;
        public int trainingCost;
        public int fatiguePercent;
        public int medicalLeaveDays;
        public String careerReturn = "";

        Officer(String name, String assignment) {
            this.name = name;
            this.assignment = assignment;
            this.shipHistory.add(assignment);
        }
    }

    public static final class FleetCulture {
        public final List<String> traditions = new ArrayList<>();
        public final List<String> rituals = new ArrayList<>();
        public final List<String> ceremonies = new ArrayList<>();
        public String motto = "Bring everyone home";
        public int moralePercent = 78;
        public int capturedShipFrictionPercent = 14;
        public int mixedCrewTrustPercent = 62;
        public String summary = "Protective command culture with a strong rescue tradition.";
    }

    public static final class CivilianLife {
        public final List<String> persistentCaptains = new ArrayList<>();
        public final List<String> organizations = new ArrayList<>();
        public final List<String> rumors = new ArrayList<>();
        public final List<String> casualtyReports = new ArrayList<>();
        public int publicPerception = 56;
    }

    public static final class OperationPlan {
        public final String name;
        public final List<String> phases = new ArrayList<>();
        public final Map<String, String> synchronizedDepartures = new LinkedHashMap<>();
        public final List<String> conditionalOrders = new ArrayList<>();
        public final Map<String, String> branchPlans = new LinkedHashMap<>();
        public final List<String> reserveTriggers = new ArrayList<>();
        public final List<String> notebook = new ArrayList<>();
        public final List<String> comparisons = new ArrayList<>();
        public String stagingArea;
        public String projection;
        public String expectedResponse;
        public boolean rehearsedWithIncompleteIntel;
        public String template;

        OperationPlan(String name) { this.name = name; }
    }

    public static final class Intelligence {
        public final Map<String, IntelReliability> sources = new LinkedHashMap<>();
        public final List<String> interceptedManifests = new ArrayList<>();
        public final List<String> conflictingReports = new ArrayList<>();
        public final List<String> orderOfBattle = new ArrayList<>();
        public final List<String> recommendations = new ArrayList<>();
        public final List<String> gaps = new ArrayList<>();
        public final List<String> enemyHistory = new ArrayList<>();
        public final List<String> detectedPatterns = new ArrayList<>();
        public final List<String> misinformation = new ArrayList<>();
        public final List<String> temporaryRoutes = new ArrayList<>();
        public final List<String> archive = new ArrayList<>();
        public int qualityPercent = 58;
    }

    public static final class Espionage {
        public final List<String> agents = new ArrayList<>();
        public final List<String> sabotageOperations = new ArrayList<>();
        public final List<String> deadDrops = new ArrayList<>();
        public final List<String> incidents = new ArrayList<>();
        public int loyaltyRiskPercent = 24;
        public boolean counterintelligenceSweep;
        public boolean compromisedOfficer;
        public boolean falseOrders;
        public boolean extractionMission;
        public boolean doubleAgent;
        public int propagandaEffect;
    }

    public static final class Environment {
        public final List<String> hazards = new ArrayList<>();
        public final Map<String, IntelReliability> hazardMapConfidence = new LinkedHashMap<>();
        public final List<String> doctrineChanges = new ArrayList<>();
        public final List<String> resourceEcology = new ArrayList<>();
        public ResourcePolicy policy = ResourcePolicy.CONSERVATION;
        public int overMiningRiskPercent = 18;
        public int surveyUncertaintyPercent = 34;
        public String economicForecast = "Rare-material survey may shift patrol demand.";
    }

    public static final class Politics {
        public final Map<String, String> factionLogistics = new LinkedHashMap<>();
        public final Map<String, String> factionRules = new LinkedHashMap<>();
        public final List<String> blocs = new ArrayList<>();
        public final Map<String, Integer> blocApproval = new LinkedHashMap<>();
        public final List<String> politicalEvents = new ArrayList<>();
        public final List<String> neutralPowers = new ArrayList<>();
        public String endingSlide = "Reform coalition preserved civilian oversight.";
    }

    public static final class CrisisRecovery {
        public final List<CrisisType> crises = new ArrayList<>();
        public final List<String> postmortems = new ArrayList<>();
        public final List<RecoveryType> recoveryOptions = new ArrayList<>();
        public boolean continueResistance;
    }

    public static final class Legacy {
        public final List<EndgameType> endgames = new ArrayList<>();
        public final List<ChallengeType> challenges = new ArrayList<>();
        public final List<String> chronicle = new ArrayList<>();
        public final List<String> hallOfRecords = new ArrayList<>();
        public final List<String> historicalScenarios = new ArrayList<>();
        public final List<String> cleanupOperations = new ArrayList<>();
        public String finalReview = "Maps, losses, rescues, and defining decisions archived.";
        public String fleetRosterExport = "Blue Fleet: 4 active hulls, 2 notable officers";
        public String playerNotes = "";
        public String shareCode = "";
        public int score;
    }

    public static final class State {
        public final Station station = new Station("Kepler Trade Spindle");
        public final Location location = new Location("Kepler Corridor");
        public final OrbitalEnvironment orbit = new OrbitalEnvironment();
        public final List<Officer> officers = new ArrayList<>();
        public final FleetCulture culture = new FleetCulture();
        public final CivilianLife civilians = new CivilianLife();
        public final OperationPlan operation = new OperationPlan("Operation Lantern");
        public final Intelligence intelligence = new Intelligence();
        public final Espionage espionage = new Espionage();
        public final Environment environment = new Environment();
        public final Politics politics = new Politics();
        public final CrisisRecovery crisisRecovery = new CrisisRecovery();
        public final Legacy legacy = new Legacy();
    }

    private DeepCampaignSimulationSystem() {}

    public static State bootstrap(long seed) {
        State state = new State();
        state.station.constructionBarge = true;
        state.station.smugglerDock = true;
        state.station.improvisedRepairYard = true;
        state.location.wrecks = 7;
        state.location.wreckFieldUses.addAll(List.of(WreckPurpose.values()));
        state.location.history.add("Kepler Corridor liberated after the relay battle.");
        state.location.reconstructionProject = true;
        state.location.refugees = 420;
        state.location.militaryCheckpoint = true;
        state.orbit.layers.addAll(List.of(OrbitalLayer.values()));
        state.orbit.moonArtillery = true;

        Officer officer = new Officer("Lt. Mara Venn", "Blue Flagship");
        officer.mentor = "Captain Rook";
        officer.promotionRecommendation = "Escort command after specialist training";
        officer.trainingHours = 40;
        officer.trainingCost = 18;
        officer.commendations.add("Kepler rescue ribbon");
        state.officers.add(officer);
        state.culture.traditions.add("Recover disabled allies before pursuit.");
        state.culture.rituals.add("Read the missing-ships roll before difficult sorties.");
        state.culture.ceremonies.add("Kepler remembrance day");
        state.civilians.persistentCaptains.add("Captain Ilya Moss: Kepler -> Hesper route");
        state.civilians.organizations.addAll(List.of("Free Traders Guild", "Kepler Mining Cooperative", "Beacon Rescue Service",
                "Frontline Correspondents", "Volunteer Auxiliary", "Grey Market Fixers"));
        state.civilians.rumors.add("Outdated: Red convoy expected near the moon relay.");
        state.civilians.casualtyReports.add("12 civilian casualties: habitat decompression after relay strike.");

        state.operation.phases.addAll(List.of("Stage escorts", "Secure relay", "Escort relief convoy"));
        state.operation.synchronizedDepartures.put("Blue Flagship", "06:00");
        state.operation.synchronizedDepartures.put("Relief Convoy", "06:12");
        state.operation.conditionalOrders.add("Engage only if escorts arrive.");
        state.operation.branchPlans.put("success", "Reconstruct relay");
        state.operation.branchPlans.put("stalemate", "Commit reserve");
        state.operation.branchPlans.put("retreat", "Withdraw through shadow zone");
        state.operation.reserveTriggers.add("Commit reserve if convoy armor falls below 45%.");
        state.operation.notebook.add("Risk: moon artillery estimate is stale.");
        state.operation.stagingArea = "Kepler L4";
        state.operation.projection = "Fuel 78%, ammunition 64%, repairs 82%, crew readiness 71%";
        state.operation.expectedResponse = "Red raiders likely reinforce after phase two.";
        state.operation.rehearsedWithIncompleteIntel = true;
        state.operation.template = "Relief corridor";

        state.intelligence.sources.put("Scout Wing Echo", IntelReliability.HIGH);
        state.intelligence.sources.put("Dockworker network", IntelReliability.MEDIUM);
        state.intelligence.interceptedManifests.add("Red convoy: 2 escorts, 3 tankers.");
        state.intelligence.conflictingReports.add("Echo reports moon artillery; dockworkers report decoys.");
        state.intelligence.orderOfBattle.add("Red raider group: 4-6 hulls, medium confidence.");
        state.intelligence.recommendations.add("Analyst: secure relay before convoy departure.");
        state.intelligence.gaps.add("Unknown patrol route in planetary shadow.");
        state.intelligence.enemyHistory.add("Raid timing clusters after refinery deliveries.");
        state.intelligence.detectedPatterns.add("Repeated attacks 18 hours after fuel arrivals.");
        state.intelligence.misinformation.add("Planted false route marker under review.");
        state.intelligence.temporaryRoutes.add("Captured nav route through debris lane.");
        state.intelligence.archive.add("Kepler/Red/Day 12: refinery raid debrief.");
        state.espionage.agents.add("Agent Morrow: embedded in Kepler shipyard");
        state.espionage.sabotageOperations.add("Disable Red fuel telemetry");
        state.espionage.deadDrops.add("Moon relay service locker 4B");

        state.environment.hazards.addAll(List.of("Moving radiation storm", "Uncertain solar flare", "Comet trail",
                "Drifting asteroid cluster", "Ion cloud", "Dense debris field", "Micro-meteor shower",
                "Magnetic anomaly", "Gravity well", "Volatile gas pocket"));
        state.environment.hazardMapConfidence.put("Kepler radiation storm, age 3h", IntelReliability.HIGH);
        state.environment.doctrineChanges.add("AI slows approach speed in dense debris.");
        state.environment.resourceEcology.addAll(List.of("Rich deposit attracts miners, pirates, patrols, and speculators",
                "Over-mining risks collapse and reduced yield", "Refinery pollution creates debris",
                "Rare materials occur near dangerous regions", "Cometary ice drifts between routes",
                "Depleted belts push factions outward", "Salvage boom follows major war"));

        state.politics.factionLogistics.put("Blue", "Convoy protection and relay redundancy");
        state.politics.factionLogistics.put("Green", "Distributed militia depots and conservation");
        state.politics.factionLogistics.put("Yellow", "Market contracts and mobile supply");
        state.politics.factionLogistics.put("Red", "Aggressive requisition and forward magazines");
        state.politics.factionLogistics.put("Rogue AI", "Autonomous fabrication and captured infrastructure");
        state.politics.factionRules.put("Blue", "Rescue priority, negotiated surrender, restrained salvage");
        state.politics.factionRules.put("Red", "Hardline prisoners, rapid salvage, high collateral tolerance");
        state.politics.blocs.addAll(List.of("Military Command", "Industrial Board", "Civilian Assembly", "Reform Caucus", "Intelligence Office"));
        for (String bloc : state.politics.blocs) state.politics.blocApproval.put(bloc, 50);
        state.politics.politicalEvents.addAll(List.of("Budget dispute affects dock construction", "Reformers reward rescue operation",
                "Procurement corruption investigation", "Hardliners resist ceasefire", "Defeat may trigger leadership change",
                "Schism can create hostile splinter faction"));
        state.politics.neutralPowers.addAll(List.of("Blackwake pirate haven", "Orion mercenary company", "Kepler defense league",
                "Quiet Light enclave", "Nomad market flotilla", "Scavenger clan", "Blockade smuggler", "Licensed privateer",
                "Captain-hunting bounty office", "Neutral coalition council"));

        state.crisisRecovery.recoveryOptions.addAll(List.of(RecoveryType.values()));
        state.legacy.endgames.addAll(List.of(EndgameType.values()));
        state.legacy.challenges.addAll(List.of(ChallengeType.values()));
        state.legacy.chronicle.add("Day 12: Kepler relay held and 420 refugees evacuated.");
        state.legacy.hallOfRecords.add("Blue Flagship and Lt. Mara Venn");
        state.legacy.historicalScenarios.add("The Kepler Relief Corridor");
        state.legacy.cleanupOperations.add("Post-victory mine clearance");
        state.legacy.shareCode = "CMP-" + Long.toUnsignedString(seed, 36).toUpperCase() + "-NORMAL-KEPLER";
        return state;
    }

    public static void setModuleStatus(State state, StationModule module, ModuleStatus status) {
        if (state == null || module == null || status == null) return;
        state.station.modules.put(module, status);
    }

    public static void advanceConstruction(State state, int percent) {
        if (state == null) return;
        state.station.constructionPercent = clamp(state.station.constructionPercent + percent, 0, 100);
    }

    public static void applyLiveStationService(State state, String service, String locationName) {
        if (state == null || service == null || service.isBlank()) return;
        String action = service.trim().toUpperCase();
        String place = (locationName == null || locationName.isBlank()) ? state.station.name : locationName.trim();
        switch (action) {
            case "REPAIR", "REFIT" -> {
                state.station.modules.replaceAll((module, status) ->
                        status == ModuleStatus.DISABLED || status == ModuleStatus.REPAIRING
                                ? ModuleStatus.OPERATIONAL : status);
                state.location.servicesPercent = clamp(state.location.servicesPercent + 12, 0, 100);
            }
            case "SHIPYARD" -> {
                state.location.reconstructionProject = true;
                advanceConstruction(state, 8);
            }
            case "SUPPLY", "FUEL", "TRADE", "SALVAGE" ->
                    state.location.servicesPercent = clamp(state.location.servicesPercent + 5, 0, 100);
            case "INTEL" -> state.station.orbitalRelayCoveragePercent =
                    clamp(state.station.orbitalRelayCoveragePercent + 8, 0, 100);
            default -> {
            }
        }
        state.location.history.add(action + " service completed at " + place + ".");
        trimHistory(state);
    }

    public static void applyLiveStationDamage(State state, int wrecks, boolean majorLoss, boolean captured) {
        if (state == null) return;
        resolveBattle(state, wrecks, majorLoss);
        state.location.servicesPercent = clamp(state.location.servicesPercent - (majorLoss ? 28 : 12), 0, 100);
        state.station.modules.put(StationModule.DOCKS, captured ? ModuleStatus.CAPTURED : ModuleStatus.REPAIRING);
        state.station.emergencyShutdown = majorLoss;
        state.location.reconstructionProject = true;
        state.location.history.add(captured ? "Station services captured after battle." : "Station repairs opened after battle damage.");
        trimHistory(state);
    }

    private static void trimHistory(State state) {
        while (state.location.history.size() > 12) state.location.history.remove(0);
    }

    public static void resolveBattle(State state, int newWrecks, boolean majorLoss) {
        if (state == null) return;
        state.location.wrecks += Math.max(0, newWrecks);
        state.location.visibleScars = true;
        state.location.history.add("Battle left " + Math.max(0, newWrecks) + " persistent wrecks.");
        if (majorLoss) {
            state.station.memorial = true;
            state.culture.ceremonies.add("Memorial ceremony for Kepler losses");
            state.culture.moralePercent = clamp(state.culture.moralePercent - 12, 0, 100);
        }
    }

    public static void deployOfficer(State state, String officerName, int fatigue) {
        if (state == null || officerName == null) return;
        for (Officer officer : state.officers) {
            if (officer.name.equals(officerName)) officer.fatiguePercent = clamp(officer.fatiguePercent + fatigue, 0, 100);
        }
    }

    public static void comparePlanToOutcome(State state, String outcome) {
        if (state == null || outcome == null || outcome.isBlank()) return;
        state.operation.comparisons.add("Plan '" + state.operation.name + "' vs outcome: " + outcome.trim());
    }

    public static void debriefIntelligence(State state, String archiveEntry) {
        if (state == null) return;
        state.intelligence.qualityPercent = clamp(state.intelligence.qualityPercent + 8, 0, 100);
        if (archiveEntry != null && !archiveEntry.isBlank()) state.intelligence.archive.add(archiveEntry.trim());
    }

    public static void triggerCrisis(State state, CrisisType type, String response) {
        if (state == null || type == null) return;
        state.crisisRecovery.crises.add(type);
        state.crisisRecovery.postmortems.add(type + ": " + ((response == null || response.isBlank()) ? "Response pending" : response.trim()));
    }

    public static void resolveEndgame(State state, EndgameType type, int score) {
        if (state == null || type == null) return;
        state.legacy.score = Math.max(0, score);
        state.legacy.chronicle.add("Endgame resolved: " + type + " score " + state.legacy.score);
    }

    public static List<String> commandBoardLines(State state) {
        if (state == null) return List.of("Deep campaign data unavailable.");
        return List.of(
                "Station " + state.station.name + "  |  Modules " + state.station.modules.size() + "  |  Construction " + state.station.constructionPercent + "%",
                "Services " + state.location.servicesPercent + "%  |  Wrecks " + state.location.wrecks
                        + "  |  Scars " + (state.location.visibleScars ? "visible" : "clear")
                        + "  |  Memorial " + (state.station.memorial ? "active" : "none"),
                "Officers " + state.officers.size() + "  |  Intel " + state.intelligence.qualityPercent + "%  |  Operation phases " + state.operation.phases.size(),
                "Hazards " + state.environment.hazards.size() + "  |  Blocs " + state.politics.blocs.size() + "  |  Crises " + state.crisisRecovery.crises.size(),
                "Endgames " + state.legacy.endgames.size() + "  |  Challenges " + state.legacy.challenges.size() + "  |  Share " + state.legacy.shareCode
        );
    }

    public static String serialize(State state) {
        if (state == null) return "";
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        Officer officer = state.officers.isEmpty() ? null : state.officers.get(0);
        return state.station.constructionPercent + "," + state.station.emergencyShutdown + "," + state.station.memorial
                + "," + state.station.modules.get(StationModule.DOCKS) + "," + state.location.wrecks + "," + state.location.visibleScars
                + "," + state.location.servicesPercent + "," + state.location.reconstructionProject
                + "|" + ((officer == null) ? 0 : officer.fatiguePercent) + "," + state.culture.moralePercent
                + "," + state.intelligence.qualityPercent + "," + state.environment.policy + "," + state.crisisRecovery.continueResistance
                + "|" + state.crisisRecovery.crises.size() + "," + state.legacy.score
                + "|" + enc(encoder, state.legacy.playerNotes);
    }

    public static State restore(String raw, long seed) {
        State state = bootstrap(seed);
        if (raw == null || raw.isBlank()) return state;
        String[] parts = raw.split("\\|", -1);
        if (parts.length < 4) return state;
        String[] station = parts[0].split(",", -1);
        if (station.length >= 6) {
            state.station.constructionPercent = clamp(number(station[0], 0), 0, 100);
            state.station.emergencyShutdown = Boolean.parseBoolean(station[1]);
            state.station.memorial = Boolean.parseBoolean(station[2]);
            state.station.modules.put(StationModule.DOCKS, value(station[3], ModuleStatus.OPERATIONAL));
            state.location.wrecks = Math.max(0, number(station[4], 7));
            state.location.visibleScars = Boolean.parseBoolean(station[5]);
            if (station.length >= 8) {
                state.location.servicesPercent = clamp(number(station[6], 100), 0, 100);
                state.location.reconstructionProject = Boolean.parseBoolean(station[7]);
            }
        }
        String[] campaign = parts[1].split(",", -1);
        if (campaign.length >= 5) {
            if (!state.officers.isEmpty()) state.officers.get(0).fatiguePercent = clamp(number(campaign[0], 0), 0, 100);
            state.culture.moralePercent = clamp(number(campaign[1], 78), 0, 100);
            state.intelligence.qualityPercent = clamp(number(campaign[2], 58), 0, 100);
            state.environment.policy = value(campaign[3], ResourcePolicy.CONSERVATION);
            state.crisisRecovery.continueResistance = Boolean.parseBoolean(campaign[4]);
        }
        String[] legacy = parts[2].split(",", -1);
        if (legacy.length >= 2) {
            int count = Math.max(0, number(legacy[0], 0));
            for (int i = 0; i < count; i++) state.crisisRecovery.crises.add(CrisisType.LEGITIMACY);
            state.legacy.score = Math.max(0, number(legacy[1], 0));
        }
        state.legacy.playerNotes = dec(parts[3], "");
        return state;
    }

    private static String enc(Base64.Encoder encoder, String value) {
        return encoder.encodeToString(((value == null) ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String dec(String value, String fallback) {
        try { return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8); }
        catch (IllegalArgumentException ex) { return fallback; }
    }

    private static int number(String value, int fallback) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException ex) { return fallback; }
    }

    private static <T extends Enum<T>> T value(String raw, T fallback) {
        try { return Enum.valueOf(fallback.getDeclaringClass(), raw); }
        catch (IllegalArgumentException ex) { return fallback; }
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
