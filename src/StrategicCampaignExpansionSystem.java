import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Section 5 strategic-campaign model layered over the live campaign simulation. */
public final class StrategicCampaignExpansionSystem {
    public static final int TERRITORY_GRAPH_VERSION = 2;
    public enum RegionRule { SHELTERED, CONTESTED_BELT, GRAVITY_WELL, ORBITAL, DEEP_SPACE_ANOMALY }
    public enum LaneType { TRAVEL_LANE, JUMP_POINT, HIDDEN_ROUTE, BLOCKADE_CHOKEPOINT }
    public enum InstallationType { HUB, FORWARD_BASE, RESOURCE_BELT, POPULATION_CENTER, ORBITAL_PLATFORM }
    public enum DirectorAction { RAID, DEFEND, LOGISTICS, RESEARCH, DIPLOMACY, MAJOR_OFFENSIVE, FEINT, MISINFORMATION }
    public enum DirectorPersonality { METHODICAL, OPPORTUNIST, ATTRITIONIST, DIPLOMAT, ROGUE_AI, PIRATE_BROKER }
    public enum Intervention { JOIN, IGNORE, STRIKE, OBSERVE, EVACUATE, BLOCKADE_RUN, RELIEF, PURSUIT, SURRENDER }
    public enum TaskOrder { HOLD, ROUTE, PATROL_LOOP, ESCORT, GARRISON, CONVOY, REPAIR_RESUPPLY, SCOUT, AMBUSH, BLOCKADE }
    public enum RulesOfEngagement { CONSERVE_FORCE, DEFENSIVE, BALANCED, AGGRESSIVE }
    public enum MapOverlay { LOGISTICS, SENSORS, CONTROL, DANGER, TRADE, HOSTILE_ROUTES, FACILITIES, MISSIONS, FLEETS, ROUTES, INTEL }
    public enum OperationType {
        RAID, INVASION, DEFENSIVE_REINFORCEMENT, RELIEF, CONSOLIDATION,
        WITHDRAWAL, EVACUATION, CONVOY, INTERDICTION, RECONNAISSANCE,
        SABOTAGE, COUNTER_SABOTAGE
    }
    public enum OperationStatus { PLANNED, ACTIVE, SUCCEEDED, FAILED, ABORTED, INTERCEPTED, STALEMATE, SURRENDERED, ENCIRCLED, WITHDRAWN }
    public enum TerritoryControlState { SECURE, PRESSURED, CONTESTED, OCCUPIED, INTEGRATED }
    public enum CivilWarOutcome {
        ONGOING, BRIGHT_YELLOW_VICTORY, DARK_YELLOW_VICTORY,
        NEGOTIATED_SETTLEMENT, PARTITION, MUTUAL_COLLAPSE,
        BRIGHT_COALITION_PROTECTORATE, DARK_RED_PROTECTORATE, FOREIGN_OCCUPATION
    }
    public enum SupplyState { SUPPLIED, STRAINED, UNDERSUPPLIED, ISOLATED, COLLAPSING }
    public enum EmergencySupplyType { AIRLIFT, CONVOY, SMUGGLING, RELIEF }
    public enum BeachheadAuthorization { AUTHORED_SCENARIO, SPECIALIZED_ASSAULT, FACTION_ABILITY }
    public enum BeachheadStatus { ACTIVE, REINFORCED, EXPANDED, ISOLATED, EVACUATING, EVACUATED, EXPIRED, COLLAPSED, DESTROYED }
    public enum OperationResolution { SUCCESS, FAILURE, INTERCEPTED, ABORTED, STALEMATE, WITHDRAWAL, ENCIRCLEMENT, SURRENDER }
    public enum CivilWarMissionType {
        DISPUTED_STATION, CONVOY_CORRIDOR, PRISONER_EXCHANGE, DEFECTOR_ESCORT,
        CEASEFIRE_MONITORING, SALVAGE_RIGHTS, IDENTITY_VERIFICATION,
        CIVILIAN_EVACUATION, COALITION_INTERVENTION
    }
    public enum CivilWarMissionStatus { OFFERED, ACTIVE, SUCCEEDED, FAILED, EXPIRED }
    public enum IdentityIncidentType { FALSE_FLAG, TRANSPONDER_CONFUSION, CAPTURED_SHIP, DISPUTED_LOYALTY }
    public enum RaidTarget { FLEET, STATION, SUPPLY, PRODUCTION, MORALE, SENSORS, INTELLIGENCE }

    public record DirectorScore(String action, int score, List<String> decisiveFactors) {}
    public record DirectorPlan(String faction, OperationType operationType, String originId, String targetId,
                               int score, List<String> decisiveFactors, List<String> rejectedAlternatives) {}
    public record PathResult(List<String> territoryIds, String status) {
        public boolean reachable() { return territoryIds != null && !territoryIds.isEmpty(); }
    }
    public record ControlFactors(int fleetPresence, int stationControl, int supplySupport,
                                 int moraleSupport, int resistance, int recentBattleMomentum) {}
    public record SupplyEffects(double repair, double ammunition, double reinforcement,
                                double construction, double morale, double invasionReadiness) {}
    public record FrontPressureBreakdown(int fleetBalance, int fleetCondition, int logistics,
                                         int stationDefense, int civicControl, int economy,
                                         int recentEvents, int commander, int attackOpportunity,
                                         int defensiveUrgency, List<String> decisiveFactors) {}
    public record CivilWarResolution(CivilWarOutcome outcome, String territorialConsequence,
                                     String allianceConsequence, String fleetConsequence,
                                     String economicConsequence, String endingConsequence) {}
    public record StrategicSoakReport(int ticks, int operationsResolved, int illegalCaptures,
                                      int deadlockTicks, int ownershipChurn, boolean runawayFaction,
                                      List<String> diagnostics) {
        public boolean passed() { return illegalCaptures == 0 && !runawayFaction; }
    }

    public static final class WarEvent {
        public final String id;
        public final int tick;
        public final String category;
        public final String title;
        public final String detail;
        public final String consequence;
        public final boolean major;

        WarEvent(String id, int tick, String category, String title, String detail, String consequence, boolean major) {
            this.id = normalized(id, "war-event");
            this.tick = Math.max(0, tick);
            this.category = normalized(category, "campaign");
            this.title = normalized(title, "Campaign event");
            this.detail = normalized(detail, "No detail recorded");
            this.consequence = normalized(consequence, "No consequence recorded");
            this.major = major;
        }
    }

    public enum CommanderStatus { ACTIVE, RETREATING, RECOVERING, CAPTURED, DEAD, DEFECTED, RETIRED }
    public enum CommanderDiplomaticAction { NEGOTIATE, SURRENDER, TEMPORARY_COOPERATION, PRISONER_EXCHANGE, DEFECT, REVENGE }
    public record CommanderAdaptation(String compositionChange, String approach, String targeting,
                                      int retreatThreshold, String countermeasure) {}

    public static final class RivalCommander {
        public final String id;
        public String name;
        public String rank;
        public String faction;
        public String flagshipId;
        public String doctrine;
        public CommanderStatus status = CommanderStatus.ACTIVE;
        public int victories;
        public int defeats;
        public int retreats;
        public int encountersWithPlayer;
        public int adaptationLevel;
        public String lastObservedPlayerDoctrine = "unknown";
        public final List<String> traits = new ArrayList<>();
        public final List<String> serviceHistory = new ArrayList<>();
        public final List<String> encounterMemories = new ArrayList<>();
        public int confidence = 55;
        public int caution = 50;
        public int aggression = 50;
        public int loyalty = 70;
        public int politicalStanding = 50;
        public int warExhaustion;
        public int injuries;
        public int resources;
        public int strategicAuthority = 1;
        public String currentCountermeasure = "none";

        RivalCommander(String id, String name, String rank, String faction, String flagshipId, String doctrine) {
            this.id = normalized(id, "commander");
            this.name = normalized(name, "Unknown Commander");
            this.rank = normalized(rank, "Captain");
            this.faction = normalized(faction, "NEUTRAL");
            this.flagshipId = normalized(flagshipId, "unassigned");
            this.doctrine = normalized(doctrine, "balanced");
            this.traits.add(this.doctrine);
        }
    }

    public record OperationLegality(boolean legal, String reason) {
        static OperationLegality allowed() { return new OperationLegality(true, "Legal adjacent target"); }
        static OperationLegality denied(String reason) { return new OperationLegality(false, reason); }
    }

    public static final class StrategicOperation {
        public final String id;
        public final OperationType type;
        public final String faction;
        public String sponsor;
        public final String originTerritoryId;
        public final String targetTerritoryId;
        public String objective;
        public String fleetId;
        public int supplyCommitment;
        public int readinessRequired;
        public int durationTicks;
        public int elapsedTicks;
        public int detectionChance;
        public String withdrawalBehavior;
        public String intent;
        public String stakes;
        public String outcome;
        public String consequence;
        public int fleetLosses;
        public int supplySpent;
        public RaidTarget raidTarget = RaidTarget.SUPPLY;
        public OperationStatus status;
        public int progress;

        StrategicOperation(String id, OperationType type, String faction, String originTerritoryId,
                           String targetTerritoryId, OperationStatus status, int progress) {
            this.id = normalized(id, "operation");
            this.type = type == null ? OperationType.RAID : type;
            this.faction = normalized(faction, "NEUTRAL");
            this.sponsor = this.faction;
            this.originTerritoryId = normalized(originTerritoryId, "unknown-origin");
            this.targetTerritoryId = normalized(targetTerritoryId, "unknown-target");
            this.objective = defaultOperationObjective(this.type);
            this.fleetId = "unassigned";
            this.supplyCommitment = defaultSupplyCommitment(this.type);
            this.readinessRequired = defaultReadinessRequirement(this.type);
            this.durationTicks = defaultOperationDuration(this.type);
            this.detectionChance = defaultDetectionChance(this.type);
            this.withdrawalBehavior = defaultWithdrawalBehavior(this.type);
            this.intent = defaultOperationIntent(this.type);
            this.stakes = defaultOperationStakes(this.type);
            this.outcome = "Pending";
            this.consequence = "No consequences applied";
            this.status = status == null ? OperationStatus.PLANNED : status;
            this.progress = clamp(progress, 0, 100);
        }
    }

    public static final class Beachhead {
        public final String id;
        public final String sponsor;
        public final String targetTerritoryId;
        public final BeachheadAuthorization authorization;
        public int supplyRequirement;
        public int supplyStored;
        public int capacity;
        public int durationTicks;
        public int ageTicks;
        public int vulnerability;
        public BeachheadStatus status = BeachheadStatus.ACTIVE;

        Beachhead(String id, String sponsor, String targetTerritoryId, BeachheadAuthorization authorization,
                  int supplyRequirement, int supplyStored, int capacity, int durationTicks, int vulnerability) {
            this.id = normalized(id, "beachhead");
            this.sponsor = normalized(sponsor, "NEUTRAL");
            this.targetTerritoryId = normalized(targetTerritoryId, "unknown-target");
            this.authorization = authorization == null ? BeachheadAuthorization.AUTHORED_SCENARIO : authorization;
            this.supplyRequirement = Math.max(1, supplyRequirement);
            this.supplyStored = Math.max(0, supplyStored);
            this.capacity = Math.max(1, capacity);
            this.durationTicks = Math.max(1, durationTicks);
            this.vulnerability = clamp(vulnerability, 0, 100);
        }

        public boolean supplied() { return supplyStored >= supplyRequirement; }
        public boolean canStageInvasion() {
            return (status == BeachheadStatus.ACTIVE || status == BeachheadStatus.REINFORCED || status == BeachheadStatus.EXPANDED)
                    && supplied() && capacity > 0;
        }
    }

    /** Stable value object used anywhere a campaign territory crosses a subsystem boundary. */
    public record TerritoryId(String value) {
        public TerritoryId {
            value = (value == null) ? "" : value.trim();
            if (value.isEmpty()) throw new IllegalArgumentException("Territory id cannot be blank");
        }

        @Override public String toString() { return value; }
    }

    public record FactionId(String value) { public FactionId { value = requireId(value, "faction"); } }
    public record RouteId(String value) { public RouteId { value = requireId(value, "route"); } }
    public record OperationId(String value) { public OperationId { value = requireId(value, "operation"); } }
    public record CommanderId(String value) { public CommanderId { value = requireId(value, "commander"); } }
    public record FleetId(String value) { public FleetId { value = requireId(value, "fleet"); } }
    public record StationId(String value) { public StationId { value = requireId(value, "station"); } }
    public record BeachheadId(String value) { public BeachheadId { value = requireId(value, "beachhead"); } }
    public record CampaignId(String value) { public CampaignId { value = requireId(value, "campaign"); } }
    public record HistoricalEventId(String value) { public HistoricalEventId { value = requireId(value, "historical event"); } }

    /** Authoritative strategic metadata for one campaign territory. */
    public static final class Territory {
        public final TerritoryId id;
        public final String name;
        public String description = "";
        public String region = "frontier";
        public double centerX;
        public double centerY;
        public double minX;
        public double minY;
        public double maxX;
        public double maxY;
        public String owner;
        public String controller;
        public TerritoryControlState controlState = TerritoryControlState.SECURE;
        public int controlProgress;
        public boolean yellowHomeland;
        public boolean supplySource;
        public SupplyState supplyState = SupplyState.SUPPLIED;
        public int frontPressure;
        public int legitimacy = 70;
        public int resistance;
        public int morale = 70;
        public int infrastructure = 100;
        public int defensiveReadiness = 50;
        public int mineOutput;
        public int shipyardCapacity;
        public int repairCapacity;
        public int sensorCoverage = 50;
        public int strategicValue = 1;
        public int friendlyFleetStrength = 50;
        public int enemyFleetStrength;
        public int fleetReadiness = 75;
        public int fleetDamage;
        public int ammunition = 75;
        public int reinforcementTime = 20;
        public int recentEconomicDisruption;
        public int recentBattleMomentum;
        public int recentCivilianConsequences;
        public String notableCommanderId = "";
        public int doctrineMatchup;
        public final List<String> locationIds = new ArrayList<>();
        public final LinkedHashSet<String> tags = new LinkedHashSet<>();
        public boolean supportsBasing;
        public boolean supportsRepairs;
        public boolean supportsConstruction;
        public boolean supportsReinforcement;
        public boolean supportsInvasionStaging;
        public List<String> supplyPath = List.of();
        public String supplyReason = "Local supply source";
        public int emergencySupplyTicks;
        public EmergencySupplyType emergencySupplyType;

        Territory(String id, String name, String owner, String controller) {
            this.id = new TerritoryId(id);
            this.name = normalized(name, this.id.value());
            this.owner = normalized(owner, "NEUTRAL");
            this.controller = normalized(controller, this.owner);
        }
    }

    public static final class StarSystem {
        public final String id;
        public final String name;
        public final RegionRule rule;
        public boolean explored;

        StarSystem(String id, String name, RegionRule rule, boolean explored) {
            this.id = id;
            this.name = name;
            this.rule = rule;
            this.explored = explored;
        }
    }

    public static final class TravelLane {
        public final String from;
        public final String to;
        public final LaneType type;
        public boolean discovered;
        public boolean directed;
        public int travelCost = 10;
        public int supplyCost = 10;
        public int transitRisk;
        public boolean blockaded;
        public int routeCapacity = 100;
        public boolean civilianTravelAllowed = true;
        public boolean militaryInvasionAllowed = true;
        public String requiredAccess = "";
        public boolean requiresTechnology;
        public boolean requiresIntelligence;
        public boolean infrastructureOperational = true;

        TravelLane(String from, String to, LaneType type, boolean discovered) {
            this.from = from;
            this.to = to;
            this.type = type;
            this.discovered = discovered;
        }
    }

    public static final class Installation {
        public final String id;
        public final InstallationType type;
        public final String systemId;
        public String owner;

        Installation(String id, InstallationType type, String systemId, String owner) {
            this.id = id;
            this.type = type;
            this.systemId = systemId;
            this.owner = owner;
        }
    }

    public static final class FactionDirector {
        public final String faction;
        public final DirectorPersonality personality;
        public final List<DirectorAction> objectiveQueue = new ArrayList<>();
        public final List<String> constructionQueue = new ArrayList<>();
        public int resourceBudget;
        public int intelligenceCoverage;
        public int exhaustion;
        public int politicalPressure;
        public int mistakes;
        public int recoveries;
        public String victoryCondition;
        public int homelandReserve = 25;
        public String committedPlan = "";
        public final List<String> rejectedAlternatives = new ArrayList<>();

        FactionDirector(String faction, DirectorPersonality personality, int resourceBudget,
                        int intelligenceCoverage, String victoryCondition) {
            this.faction = faction;
            this.personality = personality;
            this.resourceBudget = resourceBudget;
            this.intelligenceCoverage = intelligenceCoverage;
            this.victoryCondition = victoryCondition;
        }
    }

    public static final class BattleReport {
        public final String id;
        public String locationId = "unknown";
        public int tick;
        public final List<String> participants = new ArrayList<>();
        public final List<String> objectives = new ArrayList<>();
        public final List<String> fronts = new ArrayList<>();
        public final List<Intervention> interventions = new ArrayList<>();
        public int reinforcementWindowSec;
        public int losses;
        public int prisoners;
        public boolean delayedIntel;
        public boolean conflictingRumors;
        public boolean wreckFieldVisitAvailable;
        public boolean rescueAvailable;
        public boolean salvageRightsDisputed;
        public String outcome = "unresolved";
        public int salvageRemaining = 100;
        public int hazardLevel = 25;
        public int survivorWindowTicks = 10;
        public String occupiedBy = "NEUTRAL";
        public String battleScar = "wreck field";

        BattleReport(String id) {
            this.id = id;
        }
    }

    public static final class CivilWarMission {
        public final String id;
        public final CivilWarMissionType type;
        public final String territoryId;
        public String title;
        public String objective;
        public String sponsor;
        public String opposingFaction;
        public CivilWarMissionStatus status = CivilWarMissionStatus.OFFERED;
        public int collateralLimit;
        public int legitimacyAtStake;
        public int humanitarianLives;
        public boolean identityVerificationRequired;
        public String outcome = "Pending player action";

        CivilWarMission(String id, CivilWarMissionType type, String territoryId) {
            this.id = id;
            this.type = type;
            this.territoryId = territoryId;
        }
    }

    public static final class IdentityIncident {
        public final String id;
        public final IdentityIncidentType type;
        public final String territoryId;
        public String presentedTransponder;
        public String verifiedAllegiance;
        public String evidence;
        public boolean verified;
        public boolean hostile;

        IdentityIncident(String id, IdentityIncidentType type, String territoryId) {
            this.id = id;
            this.type = type;
            this.territoryId = territoryId;
        }
    }

    public static final class TaskGroup {
        public final String id;
        public String name;
        public final List<Integer> shipSlots = new ArrayList<>();
        public TaskOrder order = TaskOrder.HOLD;
        public RulesOfEngagement rulesOfEngagement = RulesOfEngagement.BALANCED;
        public int automaticRetreatThreshold = 35;
        public String delegatedCaptain = "";
        public String route = "";
        public int etaHours;
        public int riskPercent;

        TaskGroup(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static final class State {
        public int territoryGraphVersion = TERRITORY_GRAPH_VERSION;
        public final List<Territory> territories = new ArrayList<>();
        public final List<StrategicOperation> operations = new ArrayList<>();
        public int nextOperationId = 1;
        public final List<Beachhead> beachheads = new ArrayList<>();
        public int nextBeachheadId = 1;
        public CivilWarOutcome civilWarOutcome = CivilWarOutcome.ONGOING;
        public int civilWarElapsedTicks;
        public int brightYellowExhaustion;
        public int darkYellowExhaustion;
        public boolean civilWarCeasefire;
        public int brightCoalitionAid;
        public int darkRedAid;
        public int brightPoliticalObligation;
        public int darkPoliticalObligation;
        public int sharedHullIntelConfusion = 25;
        public final List<StarSystem> systems = new ArrayList<>();
        public final List<TravelLane> lanes = new ArrayList<>();
        public final List<Installation> installations = new ArrayList<>();
        public final Map<String, FactionDirector> directors = new LinkedHashMap<>();
        public final List<BattleReport> battleReports = new ArrayList<>();
        public final List<CivilWarMission> civilWarMissions = new ArrayList<>();
        public int nextCivilWarMissionId = 1;
        public final List<IdentityIncident> identityIncidents = new ArrayList<>();
        public int nextIdentityIncidentId = 1;
        public final List<WarEvent> warEvents = new ArrayList<>();
        public final Map<String, RivalCommander> commanders = new LinkedHashMap<>();
        public final List<TaskGroup> taskGroups = new ArrayList<>();
        public final List<MapOverlay> overlays = new ArrayList<>();
        public int frontLinePosition = 42;
        public String neutralActorStatus = "Neutral leagues fragmented";
        public String pirateLeaderAgenda = "Broker clans follow salvage-rich battles";
        public String doctrineSeason = "Opening patrol doctrine";
    }

    private StrategicCampaignExpansionSystem() {}

    public static State bootstrap(long seed) {
        State state = new State();
        state.systems.add(new StarSystem("sol", "Sol Defense Net", RegionRule.ORBITAL, true));
        state.systems.add(new StarSystem("frontier", "Frontier Belt", RegionRule.CONTESTED_BELT, true));
        state.systems.add(new StarSystem("shelter", "Southern Shelter", RegionRule.SHELTERED, true));
        state.systems.add(new StarSystem("well", "Lunar Gravity Well", RegionRule.GRAVITY_WELL, false));
        state.systems.add(new StarSystem("anomaly", "Deep-Space Echo", RegionRule.DEEP_SPACE_ANOMALY, false));
        state.lanes.add(new TravelLane("shelter", "frontier", LaneType.TRAVEL_LANE, true));
        state.lanes.add(new TravelLane("frontier", "well", LaneType.JUMP_POINT, true));
        state.lanes.add(new TravelLane("well", "sol", LaneType.BLOCKADE_CHOKEPOINT, true));
        state.lanes.add(new TravelLane("frontier", "anomaly", LaneType.HIDDEN_ROUTE, false));
        state.territories.add(new Territory("shelter", "Southern Shelter", "TEAM_C", "TEAM_C"));
        state.territories.add(new Territory("frontier", "Bright Yellow Frontier", "BRIGHT_YELLOW", "BRIGHT_YELLOW"));
        state.territories.add(new Territory("well", "Dark Yellow Lunar Hold", "DARK_YELLOW", "DARK_YELLOW"));
        state.territories.get(1).yellowHomeland = true;
        state.territories.get(2).yellowHomeland = true;
        state.territories.add(new Territory("sol", "Sol Defense Net", "ENEMY", "ENEMY"));
        state.territories.add(new Territory("anomaly", "Deep-Space Echo", "NEUTRAL", "NEUTRAL"));
        for (int i = 0; i < state.territories.size(); i++) {
            Territory territory = state.territories.get(i);
            territory.region = switch (territory.id.value()) {
                case "shelter" -> "southern";
                case "frontier" -> "frontier";
                case "well" -> "lunar";
                case "sol" -> "earth";
                default -> "deep-space";
            };
            territory.description = territory.name + " strategic territory";
            territory.centerX = i * 1000.0;
            territory.centerY = i * 700.0;
            territory.minX = territory.centerX - 400.0;
            territory.maxX = territory.centerX + 400.0;
            territory.minY = territory.centerY - 300.0;
            territory.maxY = territory.centerY + 300.0;
            territory.locationIds.add(territory.id.value());
            territory.tags.add(territory.yellowHomeland ? "homeland" : "frontier");
            territory.supplySource = !"NEUTRAL".equals(territory.controller);
            territory.supportsBasing = territory.supplySource;
            territory.supportsRepairs = territory.supplySource;
            territory.supportsConstruction = territory.id.value().equals("shelter") || territory.id.value().equals("sol");
            territory.supportsReinforcement = territory.supplySource;
            territory.supportsInvasionStaging = territory.supplySource;
            territory.shipyardCapacity = territory.supportsConstruction ? 70 : 0;
            territory.repairCapacity = territory.supportsRepairs ? 60 : 0;
        }
        state.installations.add(new Installation("southern-hub", InstallationType.HUB, "shelter", "Green"));
        state.installations.add(new Installation("frontier-fob", InstallationType.FORWARD_BASE, "frontier", "Blue"));
        state.installations.add(new Installation("belt-claims", InstallationType.RESOURCE_BELT, "frontier", "Contested"));
        state.installations.add(new Installation("lunar-colonies", InstallationType.POPULATION_CENTER, "well", "Yellow"));
        state.installations.add(new Installation("sol-platform", InstallationType.ORBITAL_PLATFORM, "sol", "Red"));
        addDirector(state, "Blue", DirectorPersonality.METHODICAL, "Stabilize theaters and liberate Sol");
        addDirector(state, "Red", DirectorPersonality.ATTRITIONIST, "Hold Sol and exhaust coalition logistics");
        addDirector(state, "Green", DirectorPersonality.DIPLOMAT, "Preserve trade hubs and civilian routes");
        addDirector(state, "Bright Yellow", DirectorPersonality.DIPLOMAT, "Survive with the Blue-Green coalition and restore legitimate Yellow government");
        addDirector(state, "Dark Orange-Yellow", DirectorPersonality.OPPORTUNIST, "Win the Yellow succession war with Red support");
        addDirector(state, "Rogue AI", DirectorPersonality.ROGUE_AI, "Escalate without normal political constraints");
        addDirector(state, "Pirates", DirectorPersonality.PIRATE_BROKER, "Exploit exposed convoys and wreck fields");
        addCommander(state, "cmd-dark-rook", "Marshal Veyra Rook", "Marshal", "DARK_YELLOW",
                "dyc-resolute", "missile pressure and disciplined withdrawal");
        TaskGroup flagship = new TaskGroup("tg-flag", "Blue Flag Group");
        flagship.order = TaskOrder.ROUTE;
        flagship.delegatedCaptain = "Flag bridge";
        flagship.route = "Southern Shelter -> Frontier Belt";
        flagship.etaHours = 8;
        flagship.riskPercent = 24;
        state.taskGroups.add(flagship);
        state.overlays.addAll(List.of(MapOverlay.values()));
        return state;
    }

    private static void addDirector(State state, String faction, DirectorPersonality personality, String victory) {
        FactionDirector director = new FactionDirector(faction, personality, 100, 55, victory);
        director.objectiveQueue.addAll(List.of(DirectorAction.DEFEND, DirectorAction.LOGISTICS, DirectorAction.RAID,
                DirectorAction.RESEARCH, DirectorAction.DIPLOMACY, DirectorAction.FEINT,
                DirectorAction.MISINFORMATION, DirectorAction.MAJOR_OFFENSIVE));
        director.constructionQueue.addAll(List.of("escort screen", "logistics tender"));
        state.directors.put(faction, director);
    }

    public static BattleReport recordBattle(State state, String id, String... participants) {
        BattleReport report = new BattleReport(id);
        report.participants.addAll(List.of(participants));
        report.fronts.addAll(List.of("center", "reserve", "flank", "retreat corridor"));
        report.interventions.addAll(List.of(Intervention.values()));
        report.reinforcementWindowSec = 90;
        report.delayedIntel = true;
        report.conflictingRumors = true;
        report.wreckFieldVisitAvailable = true;
        report.rescueAvailable = true;
        report.prisoners = 3;
        report.salvageRightsDisputed = true;
        if (state != null) {
            state.battleReports.add(report);
            while (state.battleReports.size() > 256) state.battleReports.remove(0);
            recordWarEvent(state, "battle-" + id, state.civilWarElapsedTicks, "battle",
                    "Battle recorded: " + id, "Participants " + report.participants,
                    "Wreck, rescue, prisoner, and salvage consequences recorded", true);
        }
        return report;
    }

    public static BattleReport configureBattleReport(State state, BattleReport report, String locationId,
                                                      int tick, List<String> objectives, int casualties,
                                                      String outcome, int salvage, int hazards,
                                                      int survivorWindowTicks, String occupiedBy) {
        if (report == null) return null;
        report.locationId = normalized(locationId, "unknown");
        report.tick = Math.max(0, tick);
        report.objectives.clear();
        if (objectives != null) report.objectives.addAll(objectives.stream().filter(item -> item != null && !item.isBlank()).toList());
        report.losses = Math.max(0, casualties);
        report.outcome = normalized(outcome, "unresolved");
        report.salvageRemaining = clamp(salvage, 0, 100);
        report.hazardLevel = clamp(hazards, 0, 100);
        report.survivorWindowTicks = Math.max(0, survivorWindowTicks);
        report.occupiedBy = normalized(occupiedBy, "NEUTRAL");
        report.battleScar = report.losses >= 50 ? "major wreck field and debris scar" : "recent battle wreckage";
        recordWarEvent(state, "battle-fact-" + report.id, report.tick, "battle",
                "Battle at " + report.locationId, "Participants " + report.participants + "; objectives " + report.objectives
                        + "; outcome " + report.outcome + "; casualties " + report.losses,
                "Salvage " + report.salvageRemaining + ", hazard " + report.hazardLevel
                        + ", survivor window " + report.survivorWindowTicks, true);
        return report;
    }

    public static boolean revisitBattleSite(State state, String battleId, int salvageRecovered,
                                            int ticksElapsed, String occupier) {
        if (state == null || battleId == null) return false;
        for (BattleReport report : state.battleReports) {
            if (report == null || !report.id.equals(battleId)) continue;
            report.salvageRemaining = Math.max(0, report.salvageRemaining - Math.max(0, salvageRecovered));
            report.survivorWindowTicks = Math.max(0, report.survivorWindowTicks - Math.max(0, ticksElapsed));
            if (occupier != null && !occupier.isBlank()) report.occupiedBy = occupier.trim();
            report.hazardLevel = Math.max(0, report.hazardLevel - Math.max(0, ticksElapsed / 2));
            return true;
        }
        return false;
    }

    public static List<String> battleSiteLines(State state, String locationFilter) {
        if (state == null) return List.of();
        String filter = locationFilter == null ? "" : locationFilter.toLowerCase(Locale.ROOT);
        ArrayList<String> lines = new ArrayList<>();
        for (BattleReport report : state.battleReports) {
            if (report == null || (!filter.isBlank() && !report.locationId.toLowerCase(Locale.ROOT).contains(filter))) continue;
            lines.add("T" + report.tick + " | " + report.locationId + " | " + report.outcome + " | participants "
                    + report.participants + " | casualties " + report.losses + " | " + report.battleScar
                    + " | salvage " + report.salvageRemaining + " | survivors " + report.survivorWindowTicks
                    + " | occupied " + report.occupiedBy);
        }
        return List.copyOf(lines);
    }

    public static void recordWarEvent(State state, String id, int tick, String category, String title,
                                      String detail, String consequence, boolean major) {
        if (state == null) return;
        WarEvent event = new WarEvent(id, tick, category, title, detail, consequence, major);
        for (int i = 0; i < state.warEvents.size(); i++) {
            if (state.warEvents.get(i).id.equals(event.id)) {
                state.warEvents.set(i, event);
                return;
            }
        }
        state.warEvents.add(event);
        while (state.warEvents.size() > 512) state.warEvents.remove(0);
    }

    public static RivalCommander addCommander(State state, String id, String name, String rank, String faction,
                                               String flagshipId, String doctrine) {
        RivalCommander commander = new RivalCommander(id, name, rank, faction, flagshipId, doctrine);
        if (state != null) state.commanders.put(commander.id, commander);
        return commander;
    }

    public static boolean recordCommanderEncounter(State state, String commanderId, boolean commanderWon,
                                                    boolean commanderRetreated, String observedPlayerDoctrine) {
        RivalCommander commander = state == null ? null : state.commanders.get(commanderId);
        if (commander == null || commander.status == CommanderStatus.DEAD) return false;
        commander.encountersWithPlayer++;
        if (commanderWon) {
            commander.victories++;
            commander.confidence = clamp(commander.confidence + 8, 0, 100);
            commander.resources += 10;
            commander.politicalStanding = clamp(commander.politicalStanding + 5, 0, 100);
        } else {
            commander.defeats++;
            commander.confidence = clamp(commander.confidence - 8, 0, 100);
            commander.caution = clamp(commander.caution + 6, 0, 100);
            commander.warExhaustion = clamp(commander.warExhaustion + 8, 0, 100);
        }
        if (commanderRetreated) {
            commander.retreats++;
            commander.status = CommanderStatus.RETREATING;
        }
        if (observedPlayerDoctrine != null && !observedPlayerDoctrine.isBlank()) {
            String observed = observedPlayerDoctrine.trim();
            if (observed.equals(commander.lastObservedPlayerDoctrine)) {
                commander.adaptationLevel = clamp(commander.adaptationLevel + 1, 0, 3);
            }
            commander.lastObservedPlayerDoctrine = observed;
            commander.currentCountermeasure = countermeasureFor(observed, commander.adaptationLevel);
        }
        String memory = "Encounter " + commander.encountersWithPlayer + ": "
                + (commanderWon ? "victory" : "defeat") + (commanderRetreated ? ", retreated" : "")
                + ", observed " + commander.lastObservedPlayerDoctrine;
        commander.encounterMemories.add(memory);
        commander.serviceHistory.add(memory);
        while (commander.encounterMemories.size() > 32) commander.encounterMemories.remove(0);
        while (commander.serviceHistory.size() > 128) commander.serviceHistory.remove(0);
        recordWarEvent(state, "commander-encounter-" + commander.id + "-" + commander.encountersWithPlayer,
                state.civilWarElapsedTicks, "commander", commander.name + " encountered",
                (commanderWon ? "Commander victory" : "Commander defeat")
                        + (commanderRetreated ? " with successful retreat" : ""),
                "Adaptation level " + commander.adaptationLevel, true);
        return true;
    }

    public static boolean changeCommanderStatus(State state, String commanderId, CommanderStatus status,
                                                String reason) {
        RivalCommander commander = state == null ? null : state.commanders.get(commanderId);
        if (commander == null || status == null || commander.status == CommanderStatus.DEAD) return false;
        commander.status = status;
        if (status == CommanderStatus.CAPTURED) commander.flagshipId = "captured";
        if (status == CommanderStatus.DEAD || status == CommanderStatus.RETIRED) commander.strategicAuthority = 0;
        if (status == CommanderStatus.DEFECTED) commander.loyalty = 0;
        String event = status + ": " + normalized(reason, "status changed");
        commander.serviceHistory.add(event);
        recordWarEvent(state, "commander-status-" + commander.id + "-" + state.warEvents.size(),
                state.civilWarElapsedTicks, "commander", commander.name + " " + status.name().toLowerCase(Locale.ROOT),
                event, "Flagship and faction assignment require reconciliation", true);
        return true;
    }

    public static boolean promoteCommander(State state, String commanderId, String newRank) {
        RivalCommander commander = state == null ? null : state.commanders.get(commanderId);
        if (commander == null || commander.status != CommanderStatus.ACTIVE || commander.victories < 1) return false;
        commander.rank = normalized(newRank, commander.rank);
        commander.strategicAuthority = clamp(commander.strategicAuthority + 1, 0, 5);
        commander.resources += 20;
        commander.serviceHistory.add("Promoted to " + commander.rank);
        return true;
    }

    public static boolean demoteCommander(State state, String commanderId, String newRank, String reason) {
        RivalCommander commander = state == null ? null : state.commanders.get(commanderId);
        if (commander == null || commander.status == CommanderStatus.DEAD) return false;
        commander.rank = normalized(newRank, commander.rank);
        commander.strategicAuthority = Math.max(0, commander.strategicAuthority - 1);
        commander.politicalStanding = Math.max(0, commander.politicalStanding - 15);
        commander.serviceHistory.add("Demoted to " + commander.rank + ": " + normalized(reason, "political decision"));
        return true;
    }

    public static boolean injureCommander(State state, String commanderId, int severity) {
        RivalCommander commander = state == null ? null : state.commanders.get(commanderId);
        if (commander == null || commander.status == CommanderStatus.DEAD) return false;
        commander.injuries = clamp(commander.injuries + Math.max(1, severity), 0, 100);
        if (commander.injuries >= 40) commander.status = CommanderStatus.RECOVERING;
        commander.serviceHistory.add("Injured severity " + severity);
        return true;
    }

    public static CommanderAdaptation commanderAdaptation(RivalCommander commander) {
        if (commander == null) return new CommanderAdaptation("none", "standard", "standard", 35, "none");
        int level = clamp(commander.adaptationLevel, 0, 3);
        String doctrine = commander.lastObservedPlayerDoctrine.toLowerCase(Locale.ROOT);
        String composition = doctrine.contains("carrier") ? "add interceptors and long-range pickets"
                : doctrine.contains("missile") ? "add point-defense escorts" : "preserve mixed fleet composition";
        String approach = level == 0 ? "observe" : (commander.caution > commander.aggression ? "screened cautious approach" : "multi-axis pressure");
        String targeting = doctrine.contains("armor") ? "propulsion and logistics" : doctrine.contains("carrier") ? "flight deck and screen" : "command and weapons";
        return new CommanderAdaptation(composition, approach, targeting,
                clamp(30 + commander.caution / 3 + level * 5, 30, 75), commander.currentCountermeasure);
    }

    public static boolean commanderDiplomaticAction(State state, String commanderId,
                                                     CommanderDiplomaticAction action, String counterpart) {
        RivalCommander commander = state == null ? null : state.commanders.get(commanderId);
        if (commander == null || action == null || commander.status == CommanderStatus.DEAD) return false;
        boolean allowed = switch (action) {
            case NEGOTIATE -> commander.warExhaustion >= 35 || commander.caution >= 60;
            case SURRENDER -> commander.confidence <= 25 || commander.status == CommanderStatus.CAPTURED;
            case TEMPORARY_COOPERATION -> commander.politicalStanding >= 30 && commander.loyalty <= 75;
            case PRISONER_EXCHANGE -> commander.status == CommanderStatus.CAPTURED || commander.caution >= 50;
            case DEFECT -> commander.loyalty <= 25;
            case REVENGE -> commander.defeats >= 1 && commander.aggression >= 50;
        };
        if (!allowed) return false;
        if (action == CommanderDiplomaticAction.DEFECT) commander.status = CommanderStatus.DEFECTED;
        if (action == CommanderDiplomaticAction.SURRENDER) commander.status = CommanderStatus.CAPTURED;
        String fact = action + " with " + normalized(counterpart, "unknown counterpart");
        commander.serviceHistory.add(fact);
        recordWarEvent(state, "commander-politics-" + commander.id + "-" + state.warEvents.size(),
                state.civilWarElapsedTicks, "commander", commander.name + " chose " + action.name().toLowerCase(Locale.ROOT),
                fact, "Political commander state changed", true);
        return true;
    }

    public static boolean commanderConflictsWithDirector(State state, String commanderId) {
        RivalCommander commander = state == null ? null : state.commanders.get(commanderId);
        FactionDirector director = commander == null ? null : directorForFaction(state, commander.faction);
        if (commander == null || director == null || commander.status != CommanderStatus.ACTIVE) return false;
        boolean conflict = commander.loyalty < 40 || (commander.aggression >= 75 && director.personality == DirectorPersonality.DIPLOMAT)
                || (commander.caution >= 75 && director.personality == DirectorPersonality.ATTRITIONIST);
        if (conflict) recordWarEvent(state, "commander-director-conflict-" + commander.id + "-" + state.warEvents.size(),
                state.civilWarElapsedTicks, "commander", commander.name + " disputed faction orders",
                "Commander doctrine " + commander.doctrine + " versus director " + director.personality,
                "Operation commitment delayed or altered", false);
        return conflict;
    }

    public static int commanderOperationModifier(RivalCommander commander, String action) {
        if (commander == null || commander.status != CommanderStatus.ACTIVE) return 0;
        String normalizedAction = action == null ? "" : action.toLowerCase(Locale.ROOT);
        int modifier = commander.strategicAuthority * 3 + commander.confidence / 10 - commander.warExhaustion / 10;
        if (normalizedAction.contains("attack") || normalizedAction.contains("raid")) modifier += commander.aggression / 10;
        if (normalizedAction.contains("defend") || normalizedAction.contains("withdraw")) modifier += commander.caution / 10;
        return clamp(modifier, -20, 35);
    }

    public static boolean assignCommanderToFleet(State state, String commanderId, String flagshipId,
                                                  String faction) {
        RivalCommander commander = state == null ? null : state.commanders.get(commanderId);
        if (commander == null || commander.status != CommanderStatus.ACTIVE || flagshipId == null || flagshipId.isBlank()) return false;
        if (faction != null && !faction.isBlank() && !commander.faction.equals(faction)) return false;
        for (RivalCommander other : state.commanders.values()) {
            if (other != commander && flagshipId.equals(other.flagshipId) && other.status == CommanderStatus.ACTIVE) return false;
        }
        commander.flagshipId = flagshipId.trim();
        return true;
    }

    private static String countermeasureFor(String doctrine, int adaptationLevel) {
        if (adaptationLevel <= 0) return "observe and verify";
        String text = doctrine == null ? "" : doctrine.toLowerCase(Locale.ROOT);
        if (text.contains("missile")) return "layered point defense and dispersed approach";
        if (text.contains("carrier")) return "long-range interception and deck suppression";
        if (text.contains("armor") || text.contains("brawl")) return "standoff fire and withdrawal threshold " + (45 + adaptationLevel * 5);
        return "cautious mixed counter-screen level " + adaptationLevel;
    }

    public static boolean recoverCommander(State state, String commanderId, String replacementFlagshipId) {
        RivalCommander commander = state == null ? null : state.commanders.get(commanderId);
        if (commander == null || (commander.status != CommanderStatus.RETREATING
                && commander.status != CommanderStatus.RECOVERING)) return false;
        commander.flagshipId = normalized(replacementFlagshipId, commander.flagshipId);
        commander.status = CommanderStatus.ACTIVE;
        return true;
    }

    public static List<String> warHistoryLines(State state, int maxLines, String filter) {
        if (state == null || maxLines <= 0) return List.of();
        String needle = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        ArrayList<String> lines = new ArrayList<>();
        for (int i = state.warEvents.size() - 1; i >= 0 && lines.size() < maxLines; i--) {
            WarEvent event = state.warEvents.get(i);
            String line = "T" + event.tick + "  |  " + event.category.toUpperCase(Locale.ROOT) + "  |  "
                    + event.title + "  |  " + event.detail + "  |  " + event.consequence;
            if (needle.isEmpty() || line.toLowerCase(Locale.ROOT).contains(needle)) lines.add(line);
        }
        return List.copyOf(lines);
    }

    public static TaskGroup createTaskGroup(State state, String id, String name, int... shipSlots) {
        TaskGroup group = new TaskGroup(id, name);
        for (int slot : shipSlots) group.shipSlots.add(slot);
        if (state != null) state.taskGroups.add(group);
        return group;
    }

    public static void moveFrontLine(State state, int delta) {
        if (state != null) state.frontLinePosition = clamp(state.frontLinePosition + delta, 0, 100);
    }

    public static boolean discoverHiddenRoute(State state, String from, String to) {
        if (state == null) return false;
        for (TravelLane lane : state.lanes) {
            if (lane.type != LaneType.HIDDEN_ROUTE) continue;
            if (!lane.from.equals(from) || !lane.to.equals(to)) continue;
            lane.discovered = true;
            return true;
        }
        return false;
    }

    public static boolean transferInstallation(State state, String installationId, String owner) {
        if (state == null || installationId == null || owner == null || owner.isBlank()) return false;
        for (Installation site : state.installations) {
            if (!site.id.equals(installationId)) continue;
            site.owner = owner.trim();
            return true;
        }
        return false;
    }

    public static void recordDirectorMistake(State state, String faction) {
        FactionDirector director = (state == null) ? null : state.directors.get(faction);
        if (director == null) return;
        director.mistakes++;
        director.exhaustion = clamp(director.exhaustion + 8, 0, 100);
        director.politicalPressure = clamp(director.politicalPressure + 6, 0, 100);
    }

    public static void recordDirectorRecovery(State state, String faction) {
        FactionDirector director = (state == null) ? null : state.directors.get(faction);
        if (director == null) return;
        director.recoveries++;
        director.exhaustion = clamp(director.exhaustion - 5, 0, 100);
        director.politicalPressure = clamp(director.politicalPressure - 4, 0, 100);
    }

    public static List<String> commandBoardLines(State state) {
        if (state == null) return List.of("Strategic expansion data unavailable.");
        ArrayList<String> lines = new ArrayList<>(List.of(
                "Territories " + state.territories.size() + "  |  Graph v" + state.territoryGraphVersion
                        + "  |  Validation " + (validateTerritoryGraph(state).isEmpty() ? "OK" : "FAILED"),
                "Systems " + state.systems.size() + "  |  Lanes " + state.lanes.size() + "  |  Front " + state.frontLinePosition,
                "Directors " + state.directors.size() + "  |  Task Groups " + state.taskGroups.size() + "  |  Reports " + state.battleReports.size(),
                "Overlays " + state.overlays.size() + "  |  " + state.doctrineSeason
        ));
        if (!state.territories.isEmpty()) lines.add(debugTerritoryLine(state, state.territories.get(0).id.value()));
        return List.copyOf(lines);
    }

    public static Territory territory(State state, String territoryId) {
        if (state == null || territoryId == null) return null;
        String key = territoryId.trim();
        for (Territory territory : state.territories) {
            if (territory != null && territory.id.value().equals(key)) return territory;
        }
        return null;
    }

    /** Canonical outbound adjacency query. Hidden lanes become usable only after discovery. */
    public static List<String> adjacentTerritoryIds(State state, String territoryId) {
        if (state == null || territoryId == null || territory(state, territoryId) == null) return List.of();
        LinkedHashSet<String> adjacent = new LinkedHashSet<>();
        for (TravelLane lane : state.lanes) {
            if (lane == null || !lane.discovered) continue;
            if (lane.from.equals(territoryId) && territory(state, lane.to) != null) adjacent.add(lane.to);
            if (!lane.directed && lane.to.equals(territoryId) && territory(state, lane.from) != null) adjacent.add(lane.from);
        }
        ArrayList<String> sorted = new ArrayList<>(adjacent);
        Collections.sort(sorted);
        return List.copyOf(sorted);
    }

    /** Minimal Expansion 1A query: hostile territories adjacent to any territory controlled by the faction. */
    public static List<String> legalInvasionTargetIds(State state, String faction) {
        if (state == null || faction == null || faction.isBlank()) return List.of();
        String actor = faction.trim();
        LinkedHashSet<String> legal = new LinkedHashSet<>();
        for (Territory origin : state.territories) {
            if (origin == null || !actor.equals(origin.controller)) continue;
            for (String targetId : adjacentTerritoryIds(state, origin.id.value())) {
                if (operationLegality(state, OperationType.INVASION, actor, origin.id.value(), targetId).legal()) legal.add(targetId);
            }
        }
        ArrayList<String> sorted = new ArrayList<>(legal);
        Collections.sort(sorted);
        return List.copyOf(sorted);
    }

    public static boolean isLegalInvasionTarget(State state, String faction, String targetId) {
        return targetId != null && legalInvasionTargetIds(state, faction).contains(targetId.trim());
    }

    private static boolean isCaptureOperation(OperationType type) { return type == OperationType.INVASION; }
    private static boolean isDefensiveOperation(OperationType type) {
        return type == OperationType.DEFENSIVE_REINFORCEMENT || type == OperationType.RELIEF
                || type == OperationType.CONSOLIDATION || type == OperationType.COUNTER_SABOTAGE;
    }
    private static boolean isMovementToFriendlyOperation(OperationType type) {
        return isDefensiveOperation(type) || type == OperationType.WITHDRAWAL
                || type == OperationType.EVACUATION || type == OperationType.CONVOY;
    }
    private static String defaultOperationObjective(OperationType type) {
        return switch (type) {
            case RAID -> "Disrupt a selected strategic asset and withdraw";
            case INVASION -> "Capture stations, routes, control points, or defeat the local fleet to accumulate control";
            case DEFENSIVE_REINFORCEMENT -> "Strengthen local defense before hostile action resolves";
            case RELIEF -> "Restore supply and prevent collapse";
            case CONSOLIDATION -> "Stabilize newly occupied control";
            case WITHDRAWAL -> "Extract the committed fleet to friendly territory";
            case EVACUATION -> "Remove threatened personnel and strategic material";
            case CONVOY -> "Deliver supply to the destination";
            case INTERDICTION -> "Reduce hostile route capacity";
            case RECONNAISSANCE -> "Improve intelligence without capturing territory";
            case SABOTAGE -> "Damage infrastructure without transferring ownership";
            case COUNTER_SABOTAGE -> "Protect or restore threatened infrastructure";
        };
    }
    private static int defaultSupplyCommitment(OperationType type) {
        return switch (type) {
            case INVASION -> 60; case RELIEF, DEFENSIVE_REINFORCEMENT -> 40;
            case CONVOY, CONSOLIDATION -> 30; case RAID, INTERDICTION, SABOTAGE -> 20;
            default -> 10;
        };
    }
    private static int defaultReadinessRequirement(OperationType type) {
        return switch (type) {
            case INVASION -> 65; case RAID, INTERDICTION, DEFENSIVE_REINFORCEMENT -> 50;
            case RELIEF, CONSOLIDATION, CONVOY -> 40; default -> 25;
        };
    }
    private static int defaultOperationDuration(OperationType type) {
        return switch (type) {
            case INVASION -> 8; case CONSOLIDATION, RELIEF -> 6;
            case RAID, SABOTAGE, INTERDICTION -> 4; default -> 3;
        };
    }
    private static int defaultDetectionChance(OperationType type) {
        return switch (type) {
            case RECONNAISSANCE -> 25; case SABOTAGE -> 35; case RAID, INTERDICTION -> 60;
            case INVASION, DEFENSIVE_REINFORCEMENT, RELIEF -> 100; default -> 70;
        };
    }
    private static String defaultWithdrawalBehavior(OperationType type) {
        return switch (type) {
            case RAID, SABOTAGE, RECONNAISSANCE, INTERDICTION -> "Withdraw to origin after objective or interception";
            case WITHDRAWAL, EVACUATION -> "Continue to destination unless route becomes illegal";
            default -> "Hold commitment until success, abort order, or logistical failure";
        };
    }
    private static String defaultOperationIntent(OperationType type) {
        return type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
    private static String defaultOperationStakes(OperationType type) {
        return isCaptureOperation(type) ? "Territorial control, fleet losses, supply, and legitimacy"
                : "Fleet exposure, time, supply, and local strategic effects; no direct ownership transfer";
    }

    public static OperationLegality operationLegality(State state, OperationType type, String faction,
                                                       String originId, String targetId) {
        if (state == null) return OperationLegality.denied("Strategic territory state is unavailable");
        if (type == null) return OperationLegality.denied("Operation type is missing");
        if (faction == null || faction.isBlank()) return OperationLegality.denied("Acting faction is missing");
        Territory origin = territory(state, originId);
        if (origin == null) return OperationLegality.denied("Origin territory does not exist");
        Territory target = territory(state, targetId);
        if (target == null) return OperationLegality.denied("Target territory does not exist");
        String actor = faction.trim();
        Beachhead stagingBeachhead = activeBeachhead(state, actor, origin.id.value());
        boolean usesBeachhead = isCaptureOperation(type) && stagingBeachhead != null;
        if (!actor.equals(origin.controller) && !usesBeachhead) {
            return OperationLegality.denied("Origin is not controlled by " + actor + " and has no valid supplied beachhead");
        }
        if (usesBeachhead && !stagingBeachhead.canStageInvasion()) {
            return OperationLegality.denied("Beachhead is unsupplied, exhausted, or inactive");
        }
        if (isCaptureOperation(type)
                && (origin.supplyState == SupplyState.ISOLATED || origin.supplyState == SupplyState.COLLAPSING)) {
            return OperationLegality.denied("Origin is isolated from faction supply and cannot launch an invasion");
        }
        if (!usesBeachhead && (origin.controlState == TerritoryControlState.OCCUPIED || origin.controlState == TerritoryControlState.CONTESTED)) {
            return OperationLegality.denied("Origin is not stable enough to launch outward operations");
        }
        if (isCaptureOperation(type) && !origin.supportsInvasionStaging && !usesBeachhead) {
            return OperationLegality.denied("Origin lacks invasion-staging capacity");
        }
        if (isCaptureOperation(type) && (origin.friendlyFleetStrength <= 0 || origin.fleetReadiness < 65)) {
            return OperationLegality.denied("Origin lacks an invasion-capable fleet at minimum readiness");
        }
        if (isCaptureOperation(type) && (origin.ammunition < 60 || supplyEffects(origin).invasionReadiness() <= 0.0)) {
            return OperationLegality.denied("Origin lacks sufficient strategic supply reserves");
        }
        if (isCaptureOperation(type)) {
            for (StrategicOperation active : state.operations) {
                if (active != null && active.type == OperationType.INVASION && active.status == OperationStatus.ACTIVE
                        && active.targetTerritoryId.equals(target.id.value())) {
                    return OperationLegality.denied("Another invasion already owns the target control transition");
                }
            }
        }
        boolean alliedTarget = strategicAllies(actor, target.controller);
        if (isMovementToFriendlyOperation(type) && !alliedTarget) {
            return OperationLegality.denied("Operation requires a friendly or allied destination");
        }
        if (!isMovementToFriendlyOperation(type) && alliedTarget) {
            return OperationLegality.denied("Hostile operation cannot target an allied faction");
        }
        if (isDefensiveOperation(type) && origin.id.equals(target.id)) {
            return OperationLegality.allowed();
        }
        if (!adjacentTerritoryIds(state, origin.id.value()).contains(target.id.value())) {
            return OperationLegality.denied("Target is not adjacent to the controlled origin territory");
        }
        TravelLane lane = outboundLane(state, origin.id.value(), target.id.value());
        if (lane == null) return OperationLegality.denied("No usable route connects origin and target");
        if (lane.blockaded) return OperationLegality.denied("Route is blockaded");
        if (!lane.infrastructureOperational) return OperationLegality.denied("Route infrastructure is offline");
        if ((isCaptureOperation(type) || type == OperationType.RAID || type == OperationType.INTERDICTION
                || type == OperationType.SABOTAGE) && !lane.militaryInvasionAllowed) {
            return OperationLegality.denied("Route permits travel but not this military operation");
        }
        if (lane.requiresTechnology) return OperationLegality.denied("Required route technology is unavailable");
        if (lane.requiresIntelligence && !lane.discovered) return OperationLegality.denied("Route intelligence is insufficient");
        if (!lane.requiredAccess.isBlank() && !lane.requiredAccess.equals(actor)) {
            return OperationLegality.denied("Route requires " + lane.requiredAccess + " access rights");
        }
        if (lane.routeCapacity <= 0) return OperationLegality.denied("Route has no available military capacity");
        return OperationLegality.allowed();
    }

    private static TravelLane outboundLane(State state, String from, String to) {
        if (state == null) return null;
        for (TravelLane lane : state.lanes) {
            if (lane == null || !lane.discovered) continue;
            if (lane.from.equals(from) && lane.to.equals(to)) return lane;
            if (!lane.directed && lane.to.equals(from) && lane.from.equals(to)) return lane;
        }
        return null;
    }

    public static PathResult findTerritoryPath(State state, String faction, String fromId, String toId,
                                                boolean requireSupplyRoute) {
        if (state == null || territory(state, fromId) == null || territory(state, toId) == null) {
            return new PathResult(List.of(), "Missing endpoint territory");
        }
        ArrayList<String> queue = new ArrayList<>();
        Map<String, String> previous = new LinkedHashMap<>();
        queue.add(fromId);
        previous.put(fromId, "");
        for (int i = 0; i < queue.size(); i++) {
            String current = queue.get(i);
            if (current.equals(toId)) break;
            for (String next : adjacentTerritoryIds(state, current)) {
                if (previous.containsKey(next)) continue;
                TravelLane lane = outboundLane(state, current, next);
                Territory destination = territory(state, next);
                if (lane == null || destination == null || lane.blockaded || !lane.infrastructureOperational) continue;
                if (requireSupplyRoute && (lane.routeCapacity <= 0 || lane.supplyCost > lane.routeCapacity)) continue;
                if (faction != null && !faction.isBlank() && !strategicAllies(faction, destination.controller)
                        && !next.equals(toId)) continue;
                previous.put(next, current);
                queue.add(next);
            }
        }
        if (!previous.containsKey(toId)) return new PathResult(List.of(), requireSupplyRoute
                ? "Blocked or unsupplied route" : "No permitted connected route");
        ArrayList<String> path = new ArrayList<>();
        for (String cursor = toId; !cursor.isEmpty(); cursor = previous.getOrDefault(cursor, "")) path.add(cursor);
        Collections.reverse(path);
        boolean alliedAccess = false;
        boolean hostileDestination = false;
        if (faction != null && !faction.isBlank()) {
            for (int i = 1; i < path.size(); i++) {
                Territory step = territory(state, path.get(i));
                if (step == null) continue;
                if (i == path.size() - 1 && !strategicAllies(faction, step.controller)) hostileDestination = true;
                else if (!faction.equals(step.controller) && strategicAllies(faction, step.controller)) alliedAccess = true;
            }
        }
        String status = (requireSupplyRoute ? "Supplied route" : "Travel route")
                + (alliedAccess ? " using allied access" : " through owned territory")
                + (hostileDestination ? " to hostile destination" : "");
        return new PathResult(List.copyOf(path), status);
    }

    private static boolean strategicAllies(String first, String second) {
        if (first == null || second == null) return false;
        if (first.equals(second)) return true;
        boolean firstBlueCoalition = first.equals("PLAYER") || first.equals("ALLY") || first.equals("TEAM_C") || first.equals("BRIGHT_YELLOW");
        boolean secondBlueCoalition = second.equals("PLAYER") || second.equals("ALLY") || second.equals("TEAM_C") || second.equals("BRIGHT_YELLOW");
        if (firstBlueCoalition && secondBlueCoalition) return true;
        return (first.equals("ENEMY") && second.equals("DARK_YELLOW"))
                || (first.equals("DARK_YELLOW") && second.equals("ENEMY"));
    }

    public static StrategicOperation startOperation(State state, OperationType type, String faction,
                                                     String originId, String targetId) {
        OperationLegality legality = operationLegality(state, type, faction, originId, targetId);
        if (!legality.legal()) return null;
        long activeCount = state.operations.stream().filter(operation -> operation != null
                && (operation.status == OperationStatus.ACTIVE || operation.status == OperationStatus.PLANNED)).count();
        if (activeCount >= 128) return null;
        StrategicOperation operation = new StrategicOperation("op-" + state.nextOperationId++, type, faction,
                originId, targetId, OperationStatus.ACTIVE, 0);
        state.operations.add(operation);
        while (state.operations.size() > 512) {
            int removable = -1;
            for (int i = 0; i < state.operations.size(); i++) {
                OperationStatus status = state.operations.get(i).status;
                if (status != OperationStatus.ACTIVE && status != OperationStatus.PLANNED) { removable = i; break; }
            }
            if (removable < 0) break;
            state.operations.remove(removable);
        }
        recordWarEvent(state, "operation-start-" + operation.id, state.civilWarElapsedTicks, "operation",
                operation.intent + " started", faction + " from " + originId + " to " + targetId,
                operation.stakes, type == OperationType.INVASION);
        return operation;
    }

    public static StrategicOperation configureOperation(StrategicOperation operation, String sponsor,
                                                         String objective, String fleetId,
                                                         int supplyCommitment, int readinessRequired,
                                                         int durationTicks) {
        if (operation == null) return null;
        operation.sponsor = normalized(sponsor, operation.faction);
        operation.objective = normalized(objective, defaultOperationObjective(operation.type));
        operation.fleetId = normalized(fleetId, "unassigned");
        operation.supplyCommitment = Math.max(0, supplyCommitment);
        operation.readinessRequired = clamp(readinessRequired, 0, 100);
        operation.durationTicks = Math.max(1, durationTicks);
        return operation;
    }

    public static StrategicOperation configureRaidTarget(StrategicOperation operation, RaidTarget target) {
        if (operation == null || operation.type != OperationType.RAID) return operation;
        operation.raidTarget = target == null ? RaidTarget.SUPPLY : target;
        operation.objective = "Raid " + operation.raidTarget.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return operation;
    }

    /** Advances a committed operation while preserving a defender intervention window. */
    public static OperationStatus advanceOperation(State state, String operationId, int readiness,
                                                   int availableSupply, boolean defenderIntervened) {
        StrategicOperation operation = operation(state, operationId);
        if (operation == null || operation.status != OperationStatus.ACTIVE) return operation == null ? null : operation.status;
        operation.elapsedTicks++;
        if (readiness < operation.readinessRequired || availableSupply < operation.supplyCommitment) {
            operation.progress = Math.max(0, operation.progress - 8);
            if (operation.elapsedTicks >= operation.durationTicks) {
                operation.status = OperationStatus.FAILED;
                operation.outcome = "Commitment failed from insufficient readiness or supply";
                operation.consequence = "Fleet time and logistical commitment were lost";
            }
            return operation.status;
        }
        int step = Math.max(5, 100 / Math.max(1, operation.durationTicks));
        if (defenderIntervened) {
            step = Math.max(2, step / 2);
            operation.consequence = "Defender intervention increased time and fleet exposure";
        }
        operation.progress = clamp(operation.progress + step, 0, 100);
        if (operation.progress >= 100) completeOperation(state, operation.id, true);
        return operation.status;
    }

    public static boolean abortOperation(State state, String operationId, String reason) {
        StrategicOperation operation = operation(state, operationId);
        if (operation == null || (operation.status != OperationStatus.ACTIVE && operation.status != OperationStatus.PLANNED)) return false;
        if (!resolveOperation(state, operationId, OperationResolution.ABORTED, 2, 5)) return false;
        operation.outcome = "Aborted";
        operation.consequence = normalized(reason, "Fleet withdrew before completing its objective");
        recordWarEvent(state, "operation-end-" + operation.id, state.civilWarElapsedTicks, "operation",
                operation.intent + " aborted", operation.objective, operation.consequence, false);
        return true;
    }

    public static StrategicOperation operation(State state, String operationId) {
        if (state == null || operationId == null) return null;
        for (StrategicOperation operation : state.operations) if (operation != null && operation.id.equals(operationId)) return operation;
        return null;
    }

    public static Beachhead createBeachhead(State state, String sponsor, String targetTerritoryId,
                                             BeachheadAuthorization authorization, int strategicCost,
                                             boolean specializedForce, int supplyRequirement,
                                             int initialSupply, int capacity, int durationTicks,
                                             int vulnerability) {
        if (state == null || sponsor == null || sponsor.isBlank() || territory(state, targetTerritoryId) == null) return null;
        if (authorization == null) return null;
        if (authorization != BeachheadAuthorization.AUTHORED_SCENARIO && strategicCost < 80 && !specializedForce) return null;
        if (activeBeachhead(state, sponsor, targetTerritoryId) != null) return null;
        Beachhead beachhead = new Beachhead("bh-" + state.nextBeachheadId++, sponsor, targetTerritoryId,
                authorization, supplyRequirement, initialSupply, capacity, durationTicks, vulnerability);
        if (!beachhead.supplied()) beachhead.status = BeachheadStatus.ISOLATED;
        state.beachheads.add(beachhead);
        while (state.beachheads.size() > 64) state.beachheads.remove(0);
        recordWarEvent(state, "beachhead-start-" + beachhead.id, state.civilWarElapsedTicks, "beachhead",
                sponsor + " established a beachhead", "Target " + targetTerritoryId + " via " + authorization,
                "Capacity " + beachhead.capacity + ", duration " + beachhead.durationTicks
                        + ", vulnerability " + beachhead.vulnerability, true);
        return beachhead;
    }

    public static Beachhead activeBeachhead(State state, String sponsor, String territoryId) {
        if (state == null || sponsor == null || territoryId == null) return null;
        for (Beachhead beachhead : state.beachheads) {
            if (beachhead != null && beachhead.sponsor.equals(sponsor) && beachhead.targetTerritoryId.equals(territoryId)
                    && (beachhead.status == BeachheadStatus.ACTIVE || beachhead.status == BeachheadStatus.REINFORCED
                    || beachhead.status == BeachheadStatus.EXPANDED || beachhead.status == BeachheadStatus.ISOLATED)) return beachhead;
        }
        return null;
    }

    public static BeachheadStatus advanceBeachhead(State state, String beachheadId, int deliveredSupply,
                                                    int defenderDamage, boolean evacuate) {
        if (state == null || beachheadId == null) return null;
        for (Beachhead beachhead : state.beachheads) {
            if (beachhead == null || !beachhead.id.equals(beachheadId)) continue;
            if (beachhead.status == BeachheadStatus.EXPIRED || beachhead.status == BeachheadStatus.COLLAPSED
                    || beachhead.status == BeachheadStatus.DESTROYED || beachhead.status == BeachheadStatus.EVACUATED) return beachhead.status;
            beachhead.ageTicks++;
            beachhead.supplyStored = Math.max(0, beachhead.supplyStored + deliveredSupply - beachhead.supplyRequirement);
            beachhead.capacity = Math.max(0, beachhead.capacity - Math.max(0, defenderDamage));
            if (evacuate) beachhead.status = BeachheadStatus.EVACUATING;
            else if (beachhead.capacity <= 0) beachhead.status = BeachheadStatus.DESTROYED;
            else if (beachhead.ageTicks >= beachhead.durationTicks) beachhead.status = BeachheadStatus.EXPIRED;
            else if (!beachhead.supplied() && beachhead.ageTicks >= 3) beachhead.status = BeachheadStatus.COLLAPSED;
            else if (!beachhead.supplied()) beachhead.status = BeachheadStatus.ISOLATED;
            else beachhead.status = BeachheadStatus.ACTIVE;
            if (beachhead.status != BeachheadStatus.ACTIVE && beachhead.status != BeachheadStatus.ISOLATED) {
                recordWarEvent(state, "beachhead-end-" + beachhead.id, state.civilWarElapsedTicks, "beachhead",
                        "Beachhead " + beachhead.status.name().toLowerCase(Locale.ROOT),
                        beachhead.sponsor + " at " + beachhead.targetTerritoryId,
                        "Remaining capacity " + beachhead.capacity + ", supply " + beachhead.supplyStored, true);
            }
            return beachhead.status;
        }
        return null;
    }

    public static boolean reinforceBeachhead(State state, String beachheadId, int supply, int capacity) {
        Beachhead beachhead = beachheadById(state, beachheadId);
        if (beachhead == null || beachhead.status == BeachheadStatus.DESTROYED || beachhead.status == BeachheadStatus.COLLAPSED) return false;
        beachhead.supplyStored += Math.max(0, supply);
        beachhead.capacity += Math.max(0, capacity);
        beachhead.status = beachhead.supplied() ? BeachheadStatus.REINFORCED : BeachheadStatus.ISOLATED;
        return true;
    }

    public static boolean expandBeachhead(State state, String beachheadId, int strategicCost) {
        Beachhead beachhead = beachheadById(state, beachheadId);
        if (beachhead == null || !beachhead.canStageInvasion() || strategicCost < 50) return false;
        beachhead.capacity += strategicCost / 2;
        beachhead.durationTicks += Math.max(1, strategicCost / 10);
        beachhead.vulnerability = clamp(beachhead.vulnerability + 10, 0, 100);
        beachhead.status = BeachheadStatus.EXPANDED;
        return true;
    }

    public static boolean evacuateBeachhead(State state, String beachheadId) {
        Beachhead beachhead = beachheadById(state, beachheadId);
        if (beachhead == null || beachhead.status == BeachheadStatus.DESTROYED) return false;
        beachhead.status = BeachheadStatus.EVACUATED;
        beachhead.capacity = 0;
        beachhead.supplyStored = 0;
        return true;
    }

    private static Beachhead beachheadById(State state, String beachheadId) {
        if (state == null || beachheadId == null) return null;
        for (Beachhead beachhead : state.beachheads) if (beachhead != null && beachhead.id.equals(beachheadId)) return beachhead;
        return null;
    }

    /** Resolves the minimal operation without allowing raids to transfer territorial ownership. */
    public static boolean completeOperation(State state, String operationId, boolean success) {
        return resolveOperation(state, operationId, success ? OperationResolution.SUCCESS : OperationResolution.FAILURE,
                success ? 5 : 15, success ? 10 : 20);
    }

    public static boolean resolveOperation(State state, String operationId, OperationResolution resolution,
                                           int fleetLosses, int supplySpent) {
        if (state == null || operationId == null) return false;
        for (StrategicOperation operation : state.operations) {
            if (operation == null || !operation.id.equals(operationId)) continue;
            OperationResolution result = resolution == null ? OperationResolution.FAILURE : resolution;
            boolean success = result == OperationResolution.SUCCESS;
            if (success) operation.progress = 100;
            operation.status = switch (result) {
                case SUCCESS -> OperationStatus.SUCCEEDED;
                case FAILURE -> OperationStatus.FAILED;
                case INTERCEPTED -> OperationStatus.INTERCEPTED;
                case ABORTED -> OperationStatus.ABORTED;
                case STALEMATE -> OperationStatus.STALEMATE;
                case WITHDRAWAL -> OperationStatus.WITHDRAWN;
                case ENCIRCLEMENT -> OperationStatus.ENCIRCLED;
                case SURRENDER -> OperationStatus.SURRENDERED;
            };
            operation.fleetLosses = Math.max(0, fleetLosses);
            operation.supplySpent = Math.max(0, supplySpent);
            operation.outcome = result.name().toLowerCase(Locale.ROOT).replace('_', ' ');
            operation.consequence = success ? "Strategic effects applied" : "Control unchanged; fleet and supply costs still applied";
            Territory origin = territory(state, operation.originTerritoryId);
            if (origin != null) {
                origin.friendlyFleetStrength = Math.max(0, origin.friendlyFleetStrength - operation.fleetLosses);
                origin.ammunition = Math.max(0, origin.ammunition - operation.supplySpent);
                origin.fleetReadiness = Math.max(0, origin.fleetReadiness - Math.max(1, operation.supplySpent / 4));
            }
            if (success && operation.type == OperationType.INVASION) {
                Territory target = territory(state, operation.targetTerritoryId);
                if (target != null) {
                    applyControlDelta(state, target, operation.faction, 50, "successful invasion operation");
                    evaluateCivilWarOutcome(state);
                }
            } else if (success && (operation.type == OperationType.RAID || operation.type == OperationType.SABOTAGE
                    || operation.type == OperationType.INTERDICTION)) {
                Territory target = territory(state, operation.targetTerritoryId);
                if (target != null) {
                    if (operation.type == OperationType.RAID) applyRaidEffect(target, operation.raidTarget);
                    else target.infrastructure = Math.max(0, target.infrastructure - 18);
                    if (operation.type != OperationType.RAID || operation.raidTarget != RaidTarget.MORALE) {
                        target.morale = Math.max(0, target.morale - 6);
                    }
                    target.frontPressure = clamp(target.frontPressure + 8, 0, 100);
                    operation.consequence = (operation.type == OperationType.RAID
                            ? operation.raidTarget.name().toLowerCase(Locale.ROOT) : "infrastructure")
                            + ", morale, and pressure changed; ownership was unchanged";
                }
            } else if (success && operation.type == OperationType.RELIEF) {
                Territory target = territory(state, operation.targetTerritoryId);
                if (target != null) applyEmergencySupply(state, target.id.value(), EmergencySupplyType.RELIEF);
            } else if (success && operation.type == OperationType.CONSOLIDATION) {
                Territory target = territory(state, operation.targetTerritoryId);
                if (target != null && operation.faction.equals(target.controller)) {
                    advanceOccupationIntegration(state, target.id.value(), 200);
                }
            }
            recordWarEvent(state, "operation-end-" + operation.id, state.civilWarElapsedTicks, "operation",
                    operation.intent + " " + operation.status.name().toLowerCase(Locale.ROOT),
                    operation.objective, operation.consequence, operation.type == OperationType.INVASION);
            return true;
        }
        return false;
    }

    private static void applyRaidEffect(Territory target, RaidTarget raidTarget) {
        RaidTarget type = raidTarget == null ? RaidTarget.SUPPLY : raidTarget;
        switch (type) {
            case FLEET -> {
                target.friendlyFleetStrength = Math.max(0, target.friendlyFleetStrength
                        - StrategicTuning.integer("raid.fleet.strengthDamage", 18));
                target.fleetReadiness = Math.max(0, target.fleetReadiness
                        - StrategicTuning.integer("raid.fleet.readinessDamage", 12));
            }
            case STATION -> {
                target.defensiveReadiness = Math.max(0, target.defensiveReadiness
                        - StrategicTuning.integer("raid.station.defenseDamage", 14));
                target.infrastructure = Math.max(0, target.infrastructure
                        - StrategicTuning.integer("raid.station.infrastructureDamage", 8));
            }
            case SUPPLY -> {
                target.ammunition = Math.max(0, target.ammunition
                        - StrategicTuning.integer("raid.supply.ammunitionDamage", 20));
                target.emergencySupplyTicks = 0;
            }
            case PRODUCTION -> {
                target.shipyardCapacity = Math.max(0, target.shipyardCapacity
                        - StrategicTuning.integer("raid.production.shipyardDamage", 15));
                target.mineOutput = Math.max(0, target.mineOutput
                        - StrategicTuning.integer("raid.production.mineDamage", 12));
            }
            case MORALE -> target.morale = Math.max(0, target.morale
                    - StrategicTuning.integer("raid.morale.damage", 18));
            case SENSORS -> target.sensorCoverage = Math.max(0, target.sensorCoverage
                    - StrategicTuning.integer("raid.sensors.damage", 22));
            case INTELLIGENCE -> {
                target.sensorCoverage = Math.max(0, target.sensorCoverage
                        - StrategicTuning.integer("raid.intelligence.sensorDamage", 10));
                target.recentBattleMomentum = Math.max(-100, target.recentBattleMomentum
                        - StrategicTuning.integer("raid.intelligence.momentumDamage", 15));
            }
        }
    }

    public static TerritoryControlState updateTerritoryControl(State state, String territoryId, String faction,
                                                               ControlFactors factors) {
        Territory territory = territory(state, territoryId);
        if (state == null || territory == null || faction == null || faction.isBlank()) return null;
        ControlFactors f = factors == null ? new ControlFactors(0, 0, 0, 0, 0, 0) : factors;
        int offense = Math.max(0, f.fleetPresence) + Math.max(0, f.stationControl)
                + Math.max(0, f.supplySupport) + Math.max(0, f.recentBattleMomentum);
        int defense = territory.defensiveReadiness / 4 + Math.max(0, f.moraleSupport) / 2
                + Math.max(0, f.resistance) / 2;
        if (territory.tags.contains("capital") || territory.tags.contains("homeworld")) defense += 18;
        if (territory.shipyardCapacity > 0) defense += 8;
        int delta = clamp((offense - defense) / 4, -20, 35);
        if (delta > 0) applyControlDelta(state, territory, faction, delta, "live strategic pressure");
        else if (delta < 0 || offense == 0) {
            String defendingFaction = territory.controller;
            applyControlDelta(state, territory, defendingFaction, Math.max(2, -delta), "control recovery");
        }
        return territory.controlState;
    }

    private static void applyControlDelta(State state, Territory territory, String faction, int amount, String reason) {
        if (state == null || territory == null || faction == null || faction.isBlank() || amount <= 0) return;
        TerritoryControlState before = territory.controlState;
        String beforeController = territory.controller;
        if (faction.equals(territory.controller)) {
            if (territory.controlState == TerritoryControlState.OCCUPIED) {
                territory.controlProgress = clamp(territory.controlProgress + amount, 100, 200);
            } else {
                territory.controlProgress = Math.max(0, territory.controlProgress - amount);
                territory.controlState = territory.controlProgress == 0
                        ? TerritoryControlState.SECURE
                        : (territory.controlProgress < 50 ? TerritoryControlState.PRESSURED : TerritoryControlState.CONTESTED);
            }
        } else if (territory.controlState == TerritoryControlState.OCCUPIED && faction.equals(territory.owner)) {
            territory.controlProgress = Math.max(0, territory.controlProgress - amount);
            if (territory.controlProgress == 0) {
                territory.controller = territory.owner;
                territory.controlState = TerritoryControlState.PRESSURED;
            }
        } else {
            territory.controlProgress = clamp(territory.controlProgress + amount, 0, 100);
            territory.controlState = territory.controlProgress < 50
                    ? TerritoryControlState.PRESSURED : TerritoryControlState.CONTESTED;
            if (territory.controlProgress >= 100) {
                territory.controller = faction;
                territory.controlState = TerritoryControlState.OCCUPIED;
                territory.controlProgress = 100;
            }
        }
        if (before != territory.controlState || !beforeController.equals(territory.controller)) {
            applyControlStateConsequences(territory, before, territory.controlState);
            recordWarEvent(state, "control-" + territory.id + "-" + state.warEvents.size(),
                    state.civilWarElapsedTicks, "territory", territory.name + " control changed",
                    before + "/" + beforeController + " -> " + territory.controlState + "/" + territory.controller,
                    reason, true);
        }
    }

    private static void applyControlStateConsequences(Territory territory, TerritoryControlState before,
                                                       TerritoryControlState after) {
        switch (after) {
            case SECURE -> {
                territory.morale = clamp(territory.morale + 4, 0, 100);
                territory.legitimacy = clamp(territory.legitimacy + 3, 0, 100);
            }
            case PRESSURED -> territory.morale = clamp(territory.morale - 3, 0, 100);
            case CONTESTED -> {
                territory.morale = clamp(territory.morale - 6, 0, 100);
                territory.infrastructure = clamp(territory.infrastructure - 3, 0, 100);
            }
            case OCCUPIED -> {
                territory.legitimacy = clamp(territory.legitimacy - 18, 0, 100);
                territory.resistance = clamp(territory.resistance + 25, 0, 100);
                territory.infrastructure = clamp(territory.infrastructure - 10, 0, 100);
                territory.supportsConstruction = false;
                territory.supportsInvasionStaging = false;
            }
            case INTEGRATED -> {
                territory.legitimacy = clamp(territory.legitimacy + 12, 0, 100);
                territory.resistance = clamp(territory.resistance - 15, 0, 100);
                territory.supportsInvasionStaging = territory.infrastructure >= 40;
            }
        }
    }

    public static List<CivilWarMission> generateCivilWarMissionDeck(State state, String territoryId) {
        if (state == null) return List.of();
        Territory territory = territory(state, territoryId);
        if (territory == null || !territory.yellowHomeland) return List.of();
        ArrayList<CivilWarMission> generated = new ArrayList<>();
        for (CivilWarMissionType type : CivilWarMissionType.values()) {
            generated.add(createCivilWarMission(state, type, territoryId));
        }
        return List.copyOf(generated);
    }

    public static CivilWarMission createCivilWarMission(State state, CivilWarMissionType type, String territoryId) {
        Territory territory = state == null ? null : territory(state, territoryId);
        if (state == null || type == null || territory == null || !territory.yellowHomeland) return null;
        CivilWarMission mission = new CivilWarMission(
                "yellow-mission-" + state.nextCivilWarMissionId++, type, territoryId);
        mission.sponsor = "BRIGHT_YELLOW".equals(territory.controller) ? "BLUE_GREEN_COALITION" : "RED";
        mission.opposingFaction = "BRIGHT_YELLOW".equals(territory.controller) ? "DARK_YELLOW" : "BRIGHT_YELLOW";
        mission.collateralLimit = 20;
        mission.legitimacyAtStake = 12;
        mission.humanitarianLives = 0;
        switch (type) {
            case DISPUTED_STATION -> {
                mission.title = "Defend the Disputed Station";
                mission.objective = "Secure command, communications, and docking control without destroying the station";
                mission.collateralLimit = 15;
            }
            case CONVOY_CORRIDOR -> {
                mission.title = "Open the Humanitarian Corridor";
                mission.objective = "Escort relief ships while interdicting only positively identified combatants";
                mission.humanitarianLives = 2400;
                mission.collateralLimit = 5;
            }
            case PRISONER_EXCHANGE -> {
                mission.title = "Prisoner Exchange at the Frontier";
                mission.objective = "Verify manifests, protect the exchange, and extract detainees if the truce fails";
                mission.humanitarianLives = 180;
                mission.identityVerificationRequired = true;
            }
            case DEFECTOR_ESCORT -> {
                mission.title = "Escort the Defector Flotilla";
                mission.objective = "Confirm allegiance and bring defecting Yellow ships through rival pursuit";
                mission.identityVerificationRequired = true;
            }
            case CEASEFIRE_MONITORING -> {
                mission.title = "Monitor the Yellow Ceasefire";
                mission.objective = "Record weapons fire, preserve evidence, and investigate violations before retaliating";
                mission.collateralLimit = 0;
                mission.identityVerificationRequired = true;
            }
            case SALVAGE_RIGHTS -> {
                mission.title = "Arbitrate the Shared-Hull Wreck Field";
                mission.objective = "Identify wreck ownership and hold rival salvagers outside the disputed perimeter";
                mission.identityVerificationRequired = true;
            }
            case IDENTITY_VERIFICATION -> {
                mission.title = "Verify Yellow Transponders";
                mission.objective = "Correlate drive signatures, service records, and current orders before assigning allegiance";
                mission.identityVerificationRequired = true;
            }
            case CIVILIAN_EVACUATION -> {
                mission.title = "Evacuate the Divided Population";
                mission.objective = "Move civilians to their chosen destination while keeping both armed factions separated";
                mission.humanitarianLives = 5200;
                mission.collateralLimit = 0;
            }
            case COALITION_INTERVENTION -> {
                mission.title = "Coalition Limited Intervention";
                mission.objective = "Protect the local government under strict collateral and legitimacy constraints";
                mission.collateralLimit = 8;
                mission.legitimacyAtStake = 25;
            }
        }
        state.civilWarMissions.add(mission);
        recordWarEvent(state, mission.id, state.civilWarElapsedTicks, "civil-war-mission",
                mission.title, mission.objective,
                "Mission offered; collateral limit " + mission.collateralLimit + "% and legitimacy at stake "
                        + mission.legitimacyAtStake, false);
        return mission;
    }

    public static boolean resolveCivilWarMission(State state, String missionId, boolean success,
                                                  int collateralPercent, boolean identityVerified) {
        if (state == null || missionId == null) return false;
        CivilWarMission mission = state.civilWarMissions.stream()
                .filter(item -> item != null && missionId.equals(item.id)).findFirst().orElse(null);
        if (mission == null || mission.status == CivilWarMissionStatus.SUCCEEDED
                || mission.status == CivilWarMissionStatus.FAILED) return false;
        Territory territory = territory(state, mission.territoryId);
        boolean withinCollateral = collateralPercent <= mission.collateralLimit;
        boolean identificationSatisfied = !mission.identityVerificationRequired || identityVerified;
        boolean achieved = success && withinCollateral && identificationSatisfied;
        mission.status = achieved ? CivilWarMissionStatus.SUCCEEDED : CivilWarMissionStatus.FAILED;
        mission.outcome = achieved
                ? "Objective completed within political and humanitarian constraints"
                : (!withinCollateral ? "Excess collateral damaged local legitimacy"
                : (!identificationSatisfied ? "Allegiance could not be verified" : "Mission objective failed"));
        if (territory != null) {
            int legitimacyDelta = achieved ? mission.legitimacyAtStake : -mission.legitimacyAtStake;
            territory.legitimacy = clamp(territory.legitimacy + legitimacyDelta, 0, 100);
            territory.morale = clamp(territory.morale + (achieved ? 6 : -8), 0, 100);
            territory.recentCivilianConsequences = clamp(territory.recentCivilianConsequences
                    + (withinCollateral ? -5 : Math.max(5, collateralPercent)), 0, 100);
        }
        if ("BLUE_GREEN_COALITION".equals(mission.sponsor)) {
            state.brightYellowExhaustion = clamp(state.brightYellowExhaustion + (achieved ? -2 : 5), 0, 100);
            state.brightPoliticalObligation = clamp(state.brightPoliticalObligation + (achieved ? 1 : 4), 0, 100);
        } else {
            state.darkYellowExhaustion = clamp(state.darkYellowExhaustion + (achieved ? -2 : 5), 0, 100);
            state.darkPoliticalObligation = clamp(state.darkPoliticalObligation + (achieved ? 1 : 4), 0, 100);
        }
        recordWarEvent(state, mission.id + "-resolved", state.civilWarElapsedTicks, "civil-war-mission",
                mission.title + (achieved ? " succeeded" : " failed"), mission.outcome,
                "Legitimacy, morale, exhaustion, and sponsor obligations updated", true);
        return achieved;
    }

    public static IdentityIncident createIdentityIncident(State state, IdentityIncidentType type,
                                                           String territoryId, boolean hostile) {
        Territory territory = state == null ? null : territory(state, territoryId);
        if (state == null || type == null || territory == null || !territory.yellowHomeland) return null;
        IdentityIncident incident = new IdentityIncident(
                "yellow-identity-" + state.nextIdentityIncidentId++, type, territoryId);
        incident.presentedTransponder = type == IdentityIncidentType.FALSE_FLAG
                ? territory.controller : ("BRIGHT_YELLOW".equals(territory.controller) ? "DARK_YELLOW" : "BRIGHT_YELLOW");
        incident.verifiedAllegiance = hostile
                ? ("BRIGHT_YELLOW".equals(territory.controller) ? "DARK_YELLOW" : "BRIGHT_YELLOW")
                : territory.controller;
        incident.evidence = switch (type) {
            case FALSE_FLAG -> "Transponder claim conflicts with drive signature and current fire-control lock";
            case TRANSPONDER_CONFUSION -> "Both contacts use legacy Yellow codes; service serials distinguish them";
            case CAPTURED_SHIP -> "Hull registry is unchanged but command cipher changed after capture";
            case DISPUTED_LOYALTY -> "Crew oath, destination, and sponsor orders conflict; allegiance remains provisional";
        };
        incident.hostile = hostile;
        state.identityIncidents.add(incident);
        state.sharedHullIntelConfusion = clamp(state.sharedHullIntelConfusion + 5, 0, 100);
        recordWarEvent(state, incident.id, state.civilWarElapsedTicks, "identity-incident",
                type.name().replace('_', ' '), incident.evidence,
                "Do not authorize fire from hull color or silhouette alone", true);
        return incident;
    }

    public static boolean verifyIdentityIncident(State state, String incidentId, String evidenceCode) {
        if (state == null || incidentId == null || evidenceCode == null || evidenceCode.isBlank()) return false;
        IdentityIncident incident = state.identityIncidents.stream()
                .filter(item -> item != null && incidentId.equals(item.id)).findFirst().orElse(null);
        if (incident == null) return false;
        incident.verified = true;
        state.sharedHullIntelConfusion = clamp(state.sharedHullIntelConfusion - 10, 0, 100);
        recordWarEvent(state, incident.id + "-verified", state.civilWarElapsedTicks, "identity-incident",
                "Allegiance verified: " + incident.verifiedAllegiance,
                "Evidence " + evidenceCode + " resolved " + incident.type.name().toLowerCase(Locale.ROOT),
                incident.hostile ? "Hostile identification authorized" : "Contact protected from friendly fire", true);
        return true;
    }

    public static CivilWarOutcome evaluateCivilWarOutcome(State state) {
        if (state == null) return CivilWarOutcome.ONGOING;
        int bright = 0;
        int dark = 0;
        int homelands = 0;
        for (Territory territory : state.territories) {
            if (territory == null || !territory.yellowHomeland) continue;
            homelands++;
            if ("BRIGHT_YELLOW".equals(territory.controller)) bright++;
            if ("DARK_YELLOW".equals(territory.controller)) dark++;
        }
        if (state.brightYellowExhaustion >= 90 && state.darkYellowExhaustion >= 90) {
            state.civilWarOutcome = CivilWarOutcome.MUTUAL_COLLAPSE;
        } else if (state.civilWarCeasefire && bright > 0 && dark > 0) {
            state.civilWarOutcome = CivilWarOutcome.NEGOTIATED_SETTLEMENT;
        } else if (state.brightPoliticalObligation >= 80 && bright > 0 && bright < homelands) {
            state.civilWarOutcome = CivilWarOutcome.BRIGHT_COALITION_PROTECTORATE;
        } else if (state.darkPoliticalObligation >= 80 && dark > 0 && dark < homelands) {
            state.civilWarOutcome = CivilWarOutcome.DARK_RED_PROTECTORATE;
        } else if (homelands > 0 && bright == 0 && dark == 0) {
            state.civilWarOutcome = CivilWarOutcome.FOREIGN_OCCUPATION;
        } else if (state.civilWarElapsedTicks >= 100 && bright > 0 && dark > 0) {
            state.civilWarOutcome = CivilWarOutcome.PARTITION;
        } else if (homelands > 0 && bright == homelands) state.civilWarOutcome = CivilWarOutcome.BRIGHT_YELLOW_VICTORY;
        else if (homelands > 0 && dark == homelands) state.civilWarOutcome = CivilWarOutcome.DARK_YELLOW_VICTORY;
        else state.civilWarOutcome = CivilWarOutcome.ONGOING;
        return state.civilWarOutcome;
    }

    public static StrategicOperation advanceYellowCivilWar(State state, int brightCoalitionAid,
                                                            int darkRedAid, boolean ceasefireRequested) {
        if (state == null || state.civilWarOutcome != CivilWarOutcome.ONGOING) return null;
        state.civilWarElapsedTicks++;
        commitCivilWarAid(state, "BRIGHT_YELLOW", brightCoalitionAid);
        commitCivilWarAid(state, "DARK_YELLOW", darkRedAid);
        if (ceasefireRequested && state.brightYellowExhaustion >= 45 && state.darkYellowExhaustion >= 45) {
            state.civilWarCeasefire = true;
            recordWarEvent(state, "yellow-ceasefire-" + state.civilWarElapsedTicks, state.civilWarElapsedTicks,
                    "civil-war", "Yellow ceasefire accepted", "Both successors accepted monitored separation",
                    "Active attacks pause; negotiated settlement evaluation begins", true);
            evaluateCivilWarOutcome(state);
            return null;
        }
        if (state.civilWarCeasefire) return null;
        for (StrategicOperation operation : state.operations) {
            if (operation != null && operation.status == OperationStatus.ACTIVE
                    && (operation.faction.equals("BRIGHT_YELLOW") || operation.faction.equals("DARK_YELLOW"))) return operation;
        }
        String attacker = (state.civilWarElapsedTicks + state.brightCoalitionAid - state.darkRedAid) % 2 == 0
                ? "BRIGHT_YELLOW" : "DARK_YELLOW";
        String defender = attacker.equals("BRIGHT_YELLOW") ? "DARK_YELLOW" : "BRIGHT_YELLOW";
        if (state.civilWarElapsedTicks % 6 == 0 && state.identityIncidents.size() < 20) {
            Territory incidentTerritory = state.territories.stream()
                    .filter(item -> item != null && item.yellowHomeland && attacker.equals(item.controller))
                    .findFirst().orElse(null);
            if (incidentTerritory != null) {
                IdentityIncidentType[] types = IdentityIncidentType.values();
                createIdentityIncident(state, types[(state.civilWarElapsedTicks / 6 - 1) % types.length],
                        incidentTerritory.id.value(), true);
            }
        }
        for (Territory origin : state.territories) {
            if (origin == null || !attacker.equals(origin.controller)) continue;
            if (state.civilWarElapsedTicks % 5 == 0) {
                StrategicOperation defense = startOperation(state, OperationType.DEFENSIVE_REINFORCEMENT,
                        attacker, origin.id.value(), origin.id.value());
                if (defense != null) {
                    configureOperation(defense, attacker.equals("BRIGHT_YELLOW") ? "BLUE_GREEN_COALITION" : "RED",
                            "Defend the disputed Yellow frontier", "yellow-defense-" + state.civilWarElapsedTicks,
                            40, 50, 3);
                    return defense;
                }
            }
            for (String targetId : adjacentTerritoryIds(state, origin.id.value())) {
                Territory target = territory(state, targetId);
                if (target == null || !defender.equals(target.controller)) continue;
                OperationType type = state.civilWarElapsedTicks % 3 == 0 ? OperationType.INVASION : OperationType.RAID;
                StrategicOperation operation = startOperation(state, type, attacker, origin.id.value(), targetId);
                if (operation != null) {
                    configureOperation(operation, attacker.equals("BRIGHT_YELLOW") ? "BLUE_GREEN_COALITION" : "RED",
                            type == OperationType.INVASION ? "Seize disputed Yellow control points" : "Pressure the rival supply frontier",
                            "yellow-front-" + state.civilWarElapsedTicks, type == OperationType.INVASION ? 60 : 20,
                            type == OperationType.INVASION ? 65 : 50, type == OperationType.INVASION ? 8 : 4);
                    state.sharedHullIntelConfusion = clamp(state.sharedHullIntelConfusion + 2, 0, 100);
                    return operation;
                }
            }
        }
        evaluateCivilWarOutcome(state);
        return null;
    }

    public static void commitCivilWarAid(State state, String recipient, int amount) {
        if (state == null || amount <= 0) return;
        if ("BRIGHT_YELLOW".equals(recipient)) {
            state.brightCoalitionAid = clamp(state.brightCoalitionAid + amount, 0, 10_000);
            state.brightPoliticalObligation = clamp(state.brightPoliticalObligation + Math.max(1, amount / 5), 0, 100);
        } else if ("DARK_YELLOW".equals(recipient)) {
            state.darkRedAid = clamp(state.darkRedAid + amount, 0, 10_000);
            state.darkPoliticalObligation = clamp(state.darkPoliticalObligation + Math.max(1, amount / 5), 0, 100);
        }
    }

    public static boolean defectYellowTerritory(State state, String territoryId, String newFaction,
                                                int legitimacyThreshold, int resistanceThreshold) {
        Territory territory = territory(state, territoryId);
        if (state == null || territory == null || !territory.yellowHomeland) return false;
        if (!("BRIGHT_YELLOW".equals(newFaction) || "DARK_YELLOW".equals(newFaction))) return false;
        if (newFaction.equals(territory.controller)) return false;
        if (territory.legitimacy > legitimacyThreshold || territory.resistance < resistanceThreshold) return false;
        String previous = territory.controller;
        territory.controller = newFaction;
        territory.controlState = TerritoryControlState.CONTESTED;
        territory.controlProgress = 50;
        recordWarEvent(state, "yellow-defection-" + territory.id + "-" + state.civilWarElapsedTicks,
                state.civilWarElapsedTicks, "civil-war", territory.name + " defected",
                previous + " control changed to " + newFaction,
                "Shared hulls and disputed loyalty increase local identification uncertainty", true);
        state.sharedHullIntelConfusion = clamp(state.sharedHullIntelConfusion + 10, 0, 100);
        return true;
    }

    public static CivilWarResolution civilWarResolution(State state) {
        CivilWarOutcome outcome = state == null ? CivilWarOutcome.ONGOING : state.civilWarOutcome;
        return switch (outcome) {
            case BRIGHT_YELLOW_VICTORY -> new CivilWarResolution(outcome, "Bright Yellow reunifies the Yellow homelands",
                    "Blue and Green coalition alignment becomes durable", "Dark formations disarm, defect, or withdraw",
                    "Trade lanes reopen under coalition guarantees", "Bright reunification ending family");
            case DARK_YELLOW_VICTORY -> new CivilWarResolution(outcome, "Dark Orange-Yellow controls the Yellow homelands",
                    "Red gains a dependent allied bloc", "Bright remnants evacuate or continue resistance",
                    "Industry is redirected toward the Red war effort", "Dark domination ending family");
            case NEGOTIATED_SETTLEMENT -> new CivilWarResolution(outcome, "Shared institutions return under negotiated autonomy",
                    "Both foreign coalitions accept monitored guarantees", "Front-line fleets form a joint security screen",
                    "Civil trade and reconstruction resume", "Negotiated reunification ending family");
            case PARTITION -> new CivilWarResolution(outcome, "The frontier hardens into a long-term partition",
                    "Bright remains coalition-aligned and Dark remains Red-aligned", "Separate fleets guard a permanent border",
                    "Duplicated industry and checkpoints reduce output", "Partition ending family");
            case MUTUAL_COLLAPSE -> new CivilWarResolution(outcome, "Central authority fragments across local territories",
                    "Foreign sponsors compete for successor enclaves", "Fleets splinter into local commands",
                    "Supply and civilian services collapse", "Fragmentation ending family");
            case BRIGHT_COALITION_PROTECTORATE -> new CivilWarResolution(outcome, "Bright territory survives under coalition protection",
                    "Blue and Green assume formal guarantees", "Coalition fleets secure critical nodes",
                    "Aid sustains reconstruction with political obligations", "Coalition protectorate ending family");
            case DARK_RED_PROTECTORATE -> new CivilWarResolution(outcome, "Dark territory survives as a Red protectorate",
                    "Red controls security and foreign policy", "Dark fleets become auxiliary commands",
                    "Red requisitions strategic production", "Red protectorate ending family");
            case FOREIGN_OCCUPATION -> new CivilWarResolution(outcome, "Foreign forces occupy the divided homeland",
                    "Yellow sovereignty is suspended", "Remaining fleets are interned or dispersed",
                    "Occupation authorities control industry and relief", "Foreign occupation ending family");
            case ONGOING -> new CivilWarResolution(outcome, "Frontier remains contested", "Alliances remain conditional",
                    "Both fleets remain active", "Trade and supply remain disrupted", "No ending resolved");
        };
    }

    public static StrategicSoakReport runHeadlessStrategicSoak(long seed, int ticks) {
        State state = bootstrap(seed);
        int resolved = 0;
        int illegal = 0;
        int deadlock = 0;
        int churn = 0;
        ArrayList<String> diagnostics = new ArrayList<>();
        Map<String, String> previousControllers = new LinkedHashMap<>();
        for (Territory territory : state.territories) previousControllers.put(territory.id.value(), territory.controller);
        for (int tick = 0; tick < Math.max(1, ticks); tick++) {
            StrategicOperation operation = advanceYellowCivilWar(state, tick % 5 == 0 ? 2 : 0,
                    tick % 7 == 0 ? 2 : 0, tick > 120 && tick % 40 == 0);
            if (operation == null) {
                if (state.civilWarOutcome == CivilWarOutcome.ONGOING) deadlock++;
            } else if (operation.status == OperationStatus.ACTIVE) {
                Territory origin = territory(state, operation.originTerritoryId);
                boolean legal = origin != null && operation.faction.equals(origin.controller)
                        && ((isDefensiveOperation(operation.type)
                                && operation.originTerritoryId.equals(operation.targetTerritoryId))
                        || adjacentTerritoryIds(state, operation.originTerritoryId).contains(operation.targetTerritoryId));
                if (!legal) {
                    illegal++;
                    diagnostics.add("Illegal active operation " + operation.id + ": invalid origin or non-adjacent target");
                    abortOperation(state, operation.id, "invalid origin or non-adjacent target");
                } else {
                    resolveOperation(state, operation.id, tick % 4 == 0 ? OperationResolution.FAILURE : OperationResolution.SUCCESS,
                            3 + tick % 5, 5 + tick % 8);
                    resolved++;
                }
            }
            for (Territory territory : state.territories) {
                String before = previousControllers.put(territory.id.value(), territory.controller);
                if (before != null && !before.equals(territory.controller)) churn++;
            }
        }
        Map<String, Integer> ownership = new LinkedHashMap<>();
        for (Territory territory : state.territories) ownership.merge(territory.controller, 1, Integer::sum);
        int max = ownership.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        boolean runaway = state.territories.size() >= 5 && max >= Math.ceil(state.territories.size() * 0.9);
        if (deadlock > 50 && state.civilWarOutcome == CivilWarOutcome.ONGOING) diagnostics.add("Prolonged unresolved deadlock " + deadlock);
        return new StrategicSoakReport(Math.max(1, ticks), resolved, illegal, deadlock, churn, runaway,
                List.copyOf(diagnostics));
    }

    public static void recalculateSupply(State state, String faction) {
        if (state == null || faction == null || faction.isBlank()) return;
        String actor = faction.trim();
        LinkedHashSet<String> reached = new LinkedHashSet<>();
        ArrayList<String> queue = new ArrayList<>();
        Map<String, String> previous = new LinkedHashMap<>();
        Map<String, Integer> bottleneck = new LinkedHashMap<>();
        Map<String, Integer> totalCost = new LinkedHashMap<>();
        for (Territory territory : state.territories) {
            if (territory != null && strategicAllies(actor, territory.controller)
                    && (territory.supplySource || territory.emergencySupplyTicks > 0)) {
                reached.add(territory.id.value());
                queue.add(territory.id.value());
                previous.put(territory.id.value(), "");
                bottleneck.put(territory.id.value(), territory.emergencySupplyTicks > 0 ? 45 : 100);
                totalCost.put(territory.id.value(), 0);
            }
        }
        for (int i = 0; i < queue.size(); i++) {
            String current = queue.get(i);
            for (String adjacent : adjacentTerritoryIds(state, current)) {
                Territory next = territory(state, adjacent);
                TravelLane lane = outboundLane(state, current, adjacent);
                if (next == null || lane == null || !strategicAllies(actor, next.controller)
                        || lane.blockaded || !lane.infrastructureOperational || lane.routeCapacity <= 0
                        || lane.supplyCost > lane.routeCapacity) continue;
                int nextBottleneck = Math.min(bottleneck.getOrDefault(current, 100), lane.routeCapacity);
                int nextCost = totalCost.getOrDefault(current, 0) + lane.supplyCost;
                int nextQuality = nextBottleneck * 1000 - nextCost;
                int oldQuality = bottleneck.getOrDefault(adjacent, -1) * 1000 - totalCost.getOrDefault(adjacent, 0);
                if (reached.contains(adjacent) && nextQuality <= oldQuality) continue;
                reached.add(adjacent);
                previous.put(adjacent, current);
                bottleneck.put(adjacent, nextBottleneck);
                totalCost.put(adjacent, nextCost);
                queue.add(adjacent);
            }
        }
        for (Territory territory : state.territories) {
            if (territory == null || !actor.equals(territory.controller)) continue;
            if (reached.contains(territory.id.value())) {
                int capacity = bottleneck.getOrDefault(territory.id.value(), 100);
                int cost = totalCost.getOrDefault(territory.id.value(), 0);
                territory.supplyState = capacity >= 70 && cost <= 30 ? SupplyState.SUPPLIED
                        : (capacity >= 35 ? SupplyState.STRAINED : SupplyState.UNDERSUPPLIED);
                ArrayList<String> path = new ArrayList<>();
                for (String cursor = territory.id.value(); !cursor.isEmpty(); cursor = previous.getOrDefault(cursor, "")) path.add(cursor);
                Collections.reverse(path);
                territory.supplyPath = List.copyOf(path);
                territory.supplyReason = (territory.emergencySupplyTicks > 0
                        ? "Emergency " + territory.emergencySupplyType + " support; " : "")
                        + (path.size() <= 1 ? "local supply source" : "connected through " + String.join(" -> ", path))
                        + " (capacity " + capacity + ", cost " + cost + ")";
            } else {
                territory.supplyState = territory.infrastructure <= 20 ? SupplyState.COLLAPSING : SupplyState.ISOLATED;
                territory.supplyPath = List.of();
                territory.supplyReason = "No allied route reaches an active supply source";
            }
        }
    }

    public static SupplyEffects supplyEffects(Territory territory) {
        SupplyState state = territory == null ? SupplyState.COLLAPSING : territory.supplyState;
        return switch (state) {
            case SUPPLIED -> new SupplyEffects(1.0, 1.0, 1.0, 1.0, 1.0, 1.0);
            case STRAINED -> new SupplyEffects(0.85, 0.88, 0.82, 0.78, 0.92, 0.88);
            case UNDERSUPPLIED -> new SupplyEffects(0.60, 0.62, 0.55, 0.45, 0.75, 0.60);
            case ISOLATED -> new SupplyEffects(0.30, 0.35, 0.10, 0.0, 0.52, 0.0);
            case COLLAPSING -> new SupplyEffects(0.10, 0.15, 0.0, 0.0, 0.30, 0.0);
        };
    }

    public static boolean applyEmergencySupply(State state, String territoryId, EmergencySupplyType type) {
        Territory territory = territory(state, territoryId);
        if (territory == null || type == null) return false;
        territory.emergencySupplyType = type;
        territory.emergencySupplyTicks = switch (type) {
            case AIRLIFT -> 2;
            case SMUGGLING -> 3;
            case CONVOY -> 5;
            case RELIEF -> 7;
        };
        recalculateSupply(state, territory.controller);
        return true;
    }

    public static void advanceSupplyTick(State state) {
        if (state == null) return;
        LinkedHashSet<String> affectedFactions = new LinkedHashSet<>();
        for (Territory territory : state.territories) {
            if (territory == null || territory.emergencySupplyTicks <= 0) continue;
            territory.emergencySupplyTicks--;
            affectedFactions.add(territory.controller);
            if (territory.emergencySupplyTicks == 0) territory.emergencySupplyType = null;
        }
        for (String faction : affectedFactions) recalculateSupply(state, faction);
    }

    public static int updateFrontPressure(State state, String territoryId) {
        Territory territory = territory(state, territoryId);
        if (territory == null) return 0;
        FrontPressureBreakdown breakdown = frontPressureBreakdown(state, territoryId);
        territory.frontPressure = breakdown.defensiveUrgency();
        return territory.frontPressure;
    }

    public static FrontPressureBreakdown frontPressureBreakdown(State state, String territoryId) {
        Territory territory = territory(state, territoryId);
        if (territory == null) return new FrontPressureBreakdown(0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                List.of("Territory unavailable"));
        int adjacentThreat = 0;
        int routeVulnerability = 0;
        for (String adjacentId : adjacentTerritoryIds(state, territoryId)) {
            Territory adjacent = territory(state, adjacentId);
            if (adjacent != null && !strategicAllies(territory.controller, adjacent.controller)) adjacentThreat += 8;
            TravelLane lane = outboundLane(state, territoryId, adjacentId);
            if (lane != null) routeVulnerability += lane.blockaded ? 20 : lane.transitRisk / 10;
        }
        int fleetBalance = clamp((territory.enemyFleetStrength - territory.friendlyFleetStrength) / 5 + adjacentThreat, -20, 20);
        int fleetCondition = clamp((100 - territory.fleetReadiness + territory.fleetDamage
                + (100 - territory.ammunition) + territory.reinforcementTime) / 20, 0, 20);
        SupplyEffects effects = supplyEffects(territory);
        int logistics = clamp((int) Math.round((1.0 - effects.invasionReadiness()) * 20.0) + routeVulnerability, 0, 20);
        int stationDefense = clamp((100 - territory.infrastructure + 100 - territory.defensiveReadiness
                + 100 - territory.shipyardCapacity) / 18, 0, 20);
        int civicControl = clamp((100 - territory.morale + 100 - territory.legitimacy + territory.resistance
                + (territory.controlState == TerritoryControlState.CONTESTED ? 30 : 0)) / 18, 0, 20);
        int economy = clamp((100 - territory.infrastructure + territory.recentEconomicDisruption
                + Math.max(0, 30 - territory.mineOutput)) / 10, 0, 20);
        int recentEvents = clamp((-territory.recentBattleMomentum + territory.recentCivilianConsequences) / 5, -20, 20);
        RivalCommander commander = state == null ? null : state.commanders.get(territory.notableCommanderId);
        int commanderFactor = clamp(-commanderOperationModifier(commander, "defend") - territory.doctrineMatchup / 5, -20, 20);
        int defensiveUrgency = clamp(20 + Math.max(0, fleetBalance) + fleetCondition + logistics
                + stationDefense + civicControl + Math.max(0, recentEvents) + Math.max(0, commanderFactor), 0, 100);
        int attackOpportunity = clamp(50 - Math.max(0, fleetBalance) - fleetCondition - logistics
                + territory.recentBattleMomentum / 5 + territory.doctrineMatchup / 5
                + commanderOperationModifier(commander, "attack"), 0, 100);
        ArrayList<String> factors = new ArrayList<>();
        if (fleetBalance >= 8) factors.add("enemy fleet advantage " + fleetBalance);
        if (fleetCondition >= 8) factors.add("fleet readiness/damage/ammunition " + fleetCondition);
        if (logistics >= 8) factors.add("supply and route vulnerability " + logistics);
        if (stationDefense >= 8) factors.add("station and shipyard weakness " + stationDefense);
        if (civicControl >= 8) factors.add("morale, legitimacy, resistance, and control " + civicControl);
        if (economy >= 8) factors.add("economic disruption " + economy);
        if (recentEvents >= 5) factors.add("recent battle/civilian consequences " + recentEvents);
        if (commander != null) factors.add("commander " + commander.name + " / doctrine matchup " + territory.doctrineMatchup);
        if (factors.isEmpty()) factors.add("no dominant pressure factor");
        return new FrontPressureBreakdown(fleetBalance, fleetCondition, logistics, stationDefense, civicControl,
                economy, recentEvents, commanderFactor, attackOpportunity, defensiveUrgency, List.copyOf(factors));
    }

    public static DirectorScore scoreTerritoryDecision(State state, String faction, String territoryId) {
        Territory territory = territory(state, territoryId);
        if (territory == null) return new DirectorScore("HOLD", 0, List.of("Territory unavailable"));
        FrontPressureBreakdown breakdown = frontPressureBreakdown(state, territoryId);
        int pressure = breakdown.defensiveUrgency();
        territory.frontPressure = pressure;
        ArrayList<String> factors = new ArrayList<>(breakdown.decisiveFactors());
        if (territory.supplyState == SupplyState.ISOLATED) factors.add("isolated from supply");
        if (territory.controlState == TerritoryControlState.CONTESTED) factors.add("control is contested");
        if (pressure >= 50) factors.add("high front pressure " + pressure);
        boolean own = faction != null && faction.equals(territory.controller);
        String action = own ? (pressure >= 40 ? "DEFEND" : "CONSOLIDATE") : "ATTACK";
        int score = own ? pressure : breakdown.attackOpportunity();
        if (factors.isEmpty()) factors.add("stable local conditions");
        return new DirectorScore(action, score, List.copyOf(factors));
    }

    public static DirectorPlan planDirectorTurn(State state, String faction) {
        FactionDirector director = directorForFaction(state, faction);
        if (director == null) return new DirectorPlan(normalized(faction, "NEUTRAL"), null, "", "", 0,
                List.of("Director unavailable"), List.of());
        ArrayList<DirectorPlan> candidates = new ArrayList<>();
        ArrayList<String> rejected = new ArrayList<>();
        for (Territory origin : state.territories) {
            if (origin == null || !faction.equals(origin.controller)) continue;
            FrontPressureBreakdown ownPressure = frontPressureBreakdown(state, origin.id.value());
            int defenseScore = ownPressure.defensiveUrgency() + director.politicalPressure / 5;
            OperationType defensiveType = origin.supplyState == SupplyState.ISOLATED
                    ? OperationType.RELIEF : (origin.controlState == TerritoryControlState.OCCUPIED
                    ? OperationType.CONSOLIDATION : OperationType.DEFENSIVE_REINFORCEMENT);
            candidates.add(new DirectorPlan(faction, defensiveType, origin.id.value(), origin.id.value(),
                    clamp(defenseScore, 0, 100), ownPressure.decisiveFactors(), List.of()));
            for (String targetId : adjacentTerritoryIds(state, origin.id.value())) {
                Territory target = territory(state, targetId);
                if (target == null || strategicAllies(faction, target.controller)) continue;
                int intelRoll = Math.floorMod((faction + ":" + targetId).hashCode(), 100);
                if (intelRoll > director.intelligenceCoverage) {
                    rejected.add(targetId + " rejected: insufficient credible intelligence");
                    continue;
                }
                for (OperationType type : List.of(OperationType.RAID, OperationType.INVASION)) {
                    OperationLegality legality = operationLegality(state, type, faction, origin.id.value(), targetId);
                    if (!legality.legal()) {
                        rejected.add(type + " " + targetId + " rejected: " + legality.reason());
                        continue;
                    }
                    FrontPressureBreakdown targetPressure = frontPressureBreakdown(state, targetId);
                    int cost = defaultSupplyCommitment(type);
                    if (director.resourceBudget - director.homelandReserve < cost) {
                        rejected.add(type + " " + targetId + " rejected: budget reserved for homeland defense");
                        continue;
                    }
                    int score = targetPressure.attackOpportunity() - director.exhaustion / 4
                            - director.politicalPressure / 8;
                    if (director.personality == DirectorPersonality.OPPORTUNIST && type == OperationType.RAID) score += 10;
                    if (director.personality == DirectorPersonality.ATTRITIONIST && type == OperationType.INVASION) score += 8;
                    if (origin.enemyFleetStrength > origin.friendlyFleetStrength * 2) score -= 30;
                    candidates.add(new DirectorPlan(faction, type, origin.id.value(), targetId, clamp(score, 0, 100),
                            targetPressure.decisiveFactors(), List.of()));
                }
            }
        }
        candidates.sort((a, b) -> {
            int score = Integer.compare(b.score(), a.score());
            if (score != 0) return score;
            int target = a.targetId().compareTo(b.targetId());
            if (target != 0) return target;
            return String.valueOf(a.operationType()).compareTo(String.valueOf(b.operationType()));
        });
        DirectorPlan chosen = candidates.isEmpty()
                ? new DirectorPlan(faction, OperationType.WITHDRAWAL, "", "", 0,
                List.of("No legal or affordable operation"), List.of()) : candidates.get(0);
        director.rejectedAlternatives.clear();
        director.rejectedAlternatives.addAll(rejected.stream().limit(32).toList());
        director.committedPlan = chosen.operationType() + ":" + chosen.originId() + ":" + chosen.targetId()
                + ":score=" + chosen.score() + ":factors=" + chosen.decisiveFactors();
        return new DirectorPlan(chosen.faction(), chosen.operationType(), chosen.originId(), chosen.targetId(),
                chosen.score(), chosen.decisiveFactors(), List.copyOf(director.rejectedAlternatives));
    }

    public static StrategicOperation commitDirectorPlan(State state, DirectorPlan plan, String fleetId) {
        if (state == null || plan == null || plan.operationType() == null || plan.originId().isBlank()) return null;
        FactionDirector director = directorForFaction(state, plan.faction());
        if (director == null) return null;
        StrategicOperation operation = startOperation(state, plan.operationType(), plan.faction(),
                plan.originId(), plan.targetId().isBlank() ? plan.originId() : plan.targetId());
        if (operation == null) return null;
        configureOperation(operation, plan.faction(), defaultOperationObjective(plan.operationType()), fleetId,
                defaultSupplyCommitment(plan.operationType()), defaultReadinessRequirement(plan.operationType()),
                defaultOperationDuration(plan.operationType()));
        director.resourceBudget = Math.max(0, director.resourceBudget - operation.supplyCommitment);
        return operation;
    }

    public static boolean requestAlliedDirectorSupport(State state, String requester, String ally, String territoryId) {
        if (state == null || !strategicAllies(requester, ally)) return false;
        FactionDirector alliedDirector = directorForFaction(state, ally);
        Territory territory = territory(state, territoryId);
        if (alliedDirector == null || territory == null) return false;
        boolean threatenedAtHome = state.territories.stream().filter(item -> item != null && ally.equals(item.controller))
                .anyMatch(item -> frontPressureBreakdown(state, item.id.value()).defensiveUrgency() >= 65);
        if (threatenedAtHome || alliedDirector.resourceBudget <= alliedDirector.homelandReserve) {
            alliedDirector.rejectedAlternatives.add("Support request from " + requester + " refused: own front threatened");
            return false;
        }
        alliedDirector.resourceBudget -= 10;
        recordWarEvent(state, "allied-support-" + ally + "-" + state.civilWarElapsedTicks,
                state.civilWarElapsedTicks, "director", ally + " accepted support request",
                "Requester " + requester + " at " + territoryId, "Independent allied budget committed", false);
        return true;
    }

    private static FactionDirector directorForFaction(State state, String faction) {
        if (state == null || faction == null) return null;
        FactionDirector exact = state.directors.get(faction);
        if (exact != null) return exact;
        String alias = switch (faction) {
            case "PLAYER", "ALLY" -> "Blue";
            case "ENEMY" -> "Red";
            case "TEAM_C" -> "Green";
            case "BRIGHT_YELLOW" -> "Bright Yellow";
            case "DARK_YELLOW" -> "Dark Orange-Yellow";
            default -> faction;
        };
        return state.directors.get(alias);
    }

    public static boolean advanceOccupationIntegration(State state, String territoryId, int amount) {
        Territory territory = territory(state, territoryId);
        if (territory == null || territory.controlState != TerritoryControlState.OCCUPIED
                || territory.supplyState == SupplyState.ISOLATED || territory.supplyState == SupplyState.COLLAPSING) return false;
        territory.controlProgress = clamp(territory.controlProgress + Math.max(0, amount), 0, 200);
        if (territory.controlProgress >= 200) {
            TerritoryControlState before = territory.controlState;
            territory.owner = territory.controller;
            territory.controlState = TerritoryControlState.INTEGRATED;
            applyControlStateConsequences(territory, before, territory.controlState);
            recordWarEvent(state, "integration-" + territory.id + "-" + state.warEvents.size(),
                    state.civilWarElapsedTicks, "territory", territory.name + " integrated",
                    "Political owner now matches military controller " + territory.controller,
                    "Occupation persisted with a functioning supply connection", true);
        }
        return true;
    }

    public static List<String> validateTerritoryGraph(State state) {
        if (state == null) return List.of("territory state missing");
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        LinkedHashSet<String> assignedLocations = new LinkedHashSet<>();
        ArrayList<String> issues = new ArrayList<>();
        LinkedHashSet<String> validFactions = new LinkedHashSet<>(List.of(
                "PLAYER", "ALLY", "ENEMY", "TEAM_C", "TEAM_D", "BRIGHT_YELLOW", "DARK_YELLOW", "NEUTRAL"));
        if (state.territoryGraphVersion <= 0) issues.add("invalid graph version");
        for (Territory territory : state.territories) {
            if (territory == null) {
                issues.add("null territory");
                continue;
            }
            if (!ids.add(territory.id.value())) issues.add("duplicate territory " + territory.id.value());
            if (territory.owner.isBlank()) issues.add("blank owner " + territory.id.value());
            if (territory.controller.isBlank()) issues.add("blank controller " + territory.id.value());
            if (!validFactions.contains(territory.owner)) issues.add("invalid owner " + territory.id.value() + "=" + territory.owner);
            if (!validFactions.contains(territory.controller)) issues.add("invalid controller " + territory.id.value() + "=" + territory.controller);
            if (territory.locationIds.isEmpty()) issues.add("territory has no assigned location " + territory.id.value());
            for (String locationId : territory.locationIds) {
                if (locationId == null || locationId.isBlank()) issues.add("blank location assignment " + territory.id.value());
                else if (!assignedLocations.add(locationId)) issues.add("location assigned to multiple territories " + locationId);
            }
            if (territory.maxX < territory.minX || territory.maxY < territory.minY) issues.add("invalid territory bounds " + territory.id.value());
        }
        LinkedHashSet<String> edges = new LinkedHashSet<>();
        for (TravelLane lane : state.lanes) {
            if (lane == null) {
                issues.add("null lane");
                continue;
            }
            if (lane.from.equals(lane.to)) issues.add("self lane " + lane.from);
            if (!ids.contains(lane.from)) issues.add("dangling lane origin " + lane.from);
            if (!ids.contains(lane.to)) issues.add("dangling lane target " + lane.to);
            String edge = lane.directed ? lane.from + ">" + lane.to
                    : (lane.from.compareTo(lane.to) <= 0 ? lane.from + "|" + lane.to : lane.to + "|" + lane.from);
            if (!edges.add(edge)) issues.add("duplicate lane " + edge);
            if (lane.travelCost < 0 || lane.supplyCost < 0 || lane.routeCapacity < 0) issues.add("invalid lane cost/capacity " + edge);
        }
        if (!ids.isEmpty()) {
            LinkedHashSet<String> connected = new LinkedHashSet<>();
            ArrayList<String> queue = new ArrayList<>();
            String first = ids.iterator().next();
            connected.add(first);
            queue.add(first);
            for (int i = 0; i < queue.size(); i++) {
                String current = queue.get(i);
                for (TravelLane lane : state.lanes) {
                    if (lane == null) continue;
                    String next = lane.from.equals(current) ? lane.to : (lane.to.equals(current) ? lane.from : "");
                    if (!next.isEmpty() && ids.contains(next) && connected.add(next)) queue.add(next);
                }
            }
            for (String id : ids) if (!connected.contains(id)) issues.add("disconnected territory " + id);
        }
        for (Territory territory : state.territories) {
            if (territory != null && territory.tags.contains("homeland")
                    && state.lanes.stream().noneMatch(lane -> lane != null
                    && (territory.id.value().equals(lane.from) || territory.id.value().equals(lane.to)))) {
                issues.add("homeland has no viable connection " + territory.id.value());
            }
        }
        return List.copyOf(issues);
    }

    public static String debugTerritoryLine(State state, String territoryId) {
        Territory territory = territory(state, territoryId);
        if (territory == null) return "Territory " + territoryId + " unavailable";
        FrontPressureBreakdown pressure = frontPressureBreakdown(state, territoryId);
        return "Territory " + territory.id + "  |  owner " + territory.owner + "  |  controller "
                + territory.controller + "  |  state " + territory.controlState + "  |  supply " + territory.supplyState
                + "  |  pressure " + pressure.defensiveUrgency() + " / attack " + pressure.attackOpportunity()
                + " " + pressure.decisiveFactors() + "  |  adjacent " + adjacentTerritoryIds(state, territory.id.value())
                + "  |  legal targets " + legalInvasionTargetIds(state, territory.controller);
    }

    public static String serialize(State state) {
        if (state == null) return "";
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        StringBuilder groups = new StringBuilder();
        for (TaskGroup group : state.taskGroups) {
            if (!groups.isEmpty()) groups.append(';');
            groups.append(enc(encoder, group.id)).append(':').append(enc(encoder, group.name)).append(':')
                    .append(group.order).append(':').append(group.rulesOfEngagement).append(':')
                    .append(group.automaticRetreatThreshold).append(':').append(enc(encoder, group.delegatedCaptain))
                    .append(':').append(enc(encoder, group.route)).append(':').append(group.etaHours).append(':').append(group.riskPercent);
        }
        StringBuilder territories = new StringBuilder();
        for (Territory territory : state.territories) {
            if (!territories.isEmpty()) territories.append(';');
            territories.append(enc(encoder, territory.id.value())).append(':')
                    .append(enc(encoder, territory.name)).append(':').append(enc(encoder, territory.owner)).append(':')
                    .append(enc(encoder, territory.controller)).append(':').append(territory.controlState).append(':')
                    .append(territory.controlProgress).append(':').append(territory.yellowHomeland).append(':')
                    .append(territory.supplySource).append(':').append(territory.supplyState).append(':')
                    .append(territory.frontPressure).append(':').append(enc(encoder, territory.description)).append(':')
                    .append(enc(encoder, territory.region)).append(':').append(territory.centerX).append(':')
                    .append(territory.centerY).append(':').append(territory.minX).append(':').append(territory.minY).append(':')
                    .append(territory.maxX).append(':').append(territory.maxY).append(':').append(territory.legitimacy).append(':')
                    .append(territory.resistance).append(':').append(territory.morale).append(':').append(territory.infrastructure).append(':')
                    .append(territory.defensiveReadiness).append(':').append(territory.mineOutput).append(':')
                    .append(territory.shipyardCapacity).append(':').append(territory.repairCapacity).append(':')
                    .append(territory.sensorCoverage).append(':').append(territory.strategicValue).append(':')
                    .append(enc(encoder, String.join(",", territory.locationIds))).append(':')
                    .append(enc(encoder, String.join(",", territory.tags))).append(':').append(territory.supportsBasing).append(':')
                    .append(territory.supportsRepairs).append(':').append(territory.supportsConstruction).append(':')
                    .append(territory.supportsReinforcement).append(':').append(territory.supportsInvasionStaging).append(':')
                    .append(enc(encoder, territory.supplyReason)).append(':')
                    .append(enc(encoder, String.join(",", territory.supplyPath))).append(':')
                    .append(territory.emergencySupplyTicks).append(':')
                    .append(territory.emergencySupplyType == null ? "NONE" : territory.emergencySupplyType.name()).append(':')
                    .append(territory.friendlyFleetStrength).append(':').append(territory.enemyFleetStrength).append(':')
                    .append(territory.fleetReadiness).append(':').append(territory.fleetDamage).append(':')
                    .append(territory.ammunition).append(':').append(territory.reinforcementTime).append(':')
                    .append(territory.recentEconomicDisruption).append(':').append(territory.recentBattleMomentum).append(':')
                    .append(territory.recentCivilianConsequences).append(':').append(enc(encoder, territory.notableCommanderId)).append(':')
                    .append(territory.doctrineMatchup);
        }
        StringBuilder lanes = new StringBuilder();
        for (TravelLane lane : state.lanes) {
            if (!lanes.isEmpty()) lanes.append(';');
            lanes.append(enc(encoder, lane.from)).append(':').append(enc(encoder, lane.to)).append(':')
                    .append(lane.type).append(':').append(lane.discovered).append(':').append(lane.directed).append(':')
                    .append(lane.travelCost).append(':').append(lane.supplyCost).append(':').append(lane.transitRisk).append(':')
                    .append(lane.blockaded).append(':').append(lane.routeCapacity).append(':')
                    .append(lane.civilianTravelAllowed).append(':').append(lane.militaryInvasionAllowed).append(':')
                    .append(enc(encoder, lane.requiredAccess)).append(':').append(lane.requiresTechnology).append(':')
                    .append(lane.requiresIntelligence).append(':').append(lane.infrastructureOperational);
        }
        StringBuilder operations = new StringBuilder();
        for (StrategicOperation operation : state.operations) {
            if (!operations.isEmpty()) operations.append(';');
            operations.append(enc(encoder, operation.id)).append(':').append(operation.type).append(':')
                    .append(enc(encoder, operation.faction)).append(':').append(enc(encoder, operation.originTerritoryId))
                    .append(':').append(enc(encoder, operation.targetTerritoryId)).append(':')
                    .append(operation.status).append(':').append(operation.progress).append(':')
                    .append(enc(encoder, operation.sponsor)).append(':').append(enc(encoder, operation.objective)).append(':')
                    .append(enc(encoder, operation.fleetId)).append(':').append(operation.supplyCommitment).append(':')
                    .append(operation.readinessRequired).append(':').append(operation.durationTicks).append(':')
                    .append(operation.elapsedTicks).append(':').append(operation.detectionChance).append(':')
                    .append(enc(encoder, operation.withdrawalBehavior)).append(':').append(enc(encoder, operation.intent)).append(':')
                    .append(enc(encoder, operation.stakes)).append(':').append(enc(encoder, operation.outcome)).append(':')
                    .append(enc(encoder, operation.consequence)).append(':').append(operation.fleetLosses).append(':')
                    .append(operation.supplySpent).append(':').append(operation.raidTarget);
        }
        StringBuilder history = new StringBuilder();
        for (WarEvent event : state.warEvents) {
            if (!history.isEmpty()) history.append(';');
            history.append(enc(encoder, event.id)).append(':').append(event.tick).append(':')
                    .append(enc(encoder, event.category)).append(':').append(enc(encoder, event.title)).append(':')
                    .append(enc(encoder, event.detail)).append(':').append(enc(encoder, event.consequence)).append(':')
                    .append(event.major);
        }
        StringBuilder commanders = new StringBuilder();
        for (RivalCommander commander : state.commanders.values()) {
            if (!commanders.isEmpty()) commanders.append(';');
            commanders.append(enc(encoder, commander.id)).append(':').append(enc(encoder, commander.name)).append(':')
                    .append(enc(encoder, commander.rank)).append(':').append(enc(encoder, commander.faction)).append(':')
                    .append(enc(encoder, commander.flagshipId)).append(':').append(enc(encoder, commander.doctrine)).append(':')
                    .append(commander.status).append(':').append(commander.victories).append(':').append(commander.defeats)
                    .append(':').append(commander.retreats).append(':').append(commander.encountersWithPlayer).append(':')
                    .append(commander.adaptationLevel).append(':').append(enc(encoder, commander.lastObservedPlayerDoctrine)).append(':')
                    .append(enc(encoder, String.join("\u001f", commander.traits))).append(':')
                    .append(enc(encoder, String.join("\u001f", commander.serviceHistory))).append(':')
                    .append(enc(encoder, String.join("\u001f", commander.encounterMemories))).append(':')
                    .append(commander.confidence).append(':').append(commander.caution).append(':')
                    .append(commander.aggression).append(':').append(commander.loyalty).append(':')
                    .append(commander.politicalStanding).append(':').append(commander.warExhaustion).append(':')
                    .append(commander.injuries).append(':').append(commander.resources).append(':')
                    .append(commander.strategicAuthority).append(':').append(enc(encoder, commander.currentCountermeasure));
        }
        StringBuilder beachheads = new StringBuilder();
        for (Beachhead beachhead : state.beachheads) {
            if (!beachheads.isEmpty()) beachheads.append(';');
            beachheads.append(enc(encoder, beachhead.id)).append(':').append(enc(encoder, beachhead.sponsor)).append(':')
                    .append(enc(encoder, beachhead.targetTerritoryId)).append(':').append(beachhead.authorization).append(':')
                    .append(beachhead.supplyRequirement).append(':').append(beachhead.supplyStored).append(':')
                    .append(beachhead.capacity).append(':').append(beachhead.durationTicks).append(':')
                    .append(beachhead.ageTicks).append(':').append(beachhead.vulnerability).append(':').append(beachhead.status);
        }
        StringBuilder battles = new StringBuilder();
        for (BattleReport report : state.battleReports) {
            if (!battles.isEmpty()) battles.append(';');
            battles.append(enc(encoder, report.id)).append(':').append(enc(encoder, report.locationId)).append(':')
                    .append(report.tick).append(':').append(enc(encoder, String.join("\u001f", report.participants))).append(':')
                    .append(enc(encoder, String.join("\u001f", report.objectives))).append(':').append(report.losses).append(':')
                    .append(enc(encoder, report.outcome)).append(':').append(report.salvageRemaining).append(':')
                    .append(report.hazardLevel).append(':').append(report.survivorWindowTicks).append(':')
                    .append(enc(encoder, report.occupiedBy)).append(':').append(enc(encoder, report.battleScar)).append(':')
                    .append(report.prisoners).append(':').append(report.wreckFieldVisitAvailable).append(':')
                    .append(report.rescueAvailable).append(':').append(report.salvageRightsDisputed);
        }
        StringBuilder directors = new StringBuilder();
        for (FactionDirector director : state.directors.values()) {
            if (!directors.isEmpty()) directors.append(';');
            directors.append(enc(encoder, director.faction)).append(':').append(director.personality).append(':')
                    .append(director.resourceBudget).append(':').append(director.intelligenceCoverage).append(':')
                    .append(director.exhaustion).append(':').append(director.politicalPressure).append(':')
                    .append(director.mistakes).append(':').append(director.recoveries).append(':')
                    .append(enc(encoder, director.victoryCondition)).append(':').append(director.homelandReserve).append(':')
                    .append(enc(encoder, director.committedPlan)).append(':')
                    .append(enc(encoder, String.join("\u001f", director.rejectedAlternatives)));
        }
        StringBuilder civilMissions = new StringBuilder();
        for (CivilWarMission mission : state.civilWarMissions) {
            if (!civilMissions.isEmpty()) civilMissions.append(';');
            civilMissions.append(enc(encoder, mission.id)).append(':').append(mission.type).append(':')
                    .append(enc(encoder, mission.territoryId)).append(':').append(enc(encoder, mission.title)).append(':')
                    .append(enc(encoder, mission.objective)).append(':').append(enc(encoder, mission.sponsor)).append(':')
                    .append(enc(encoder, mission.opposingFaction)).append(':').append(mission.status).append(':')
                    .append(mission.collateralLimit).append(':').append(mission.legitimacyAtStake).append(':')
                    .append(mission.humanitarianLives).append(':').append(mission.identityVerificationRequired).append(':')
                    .append(enc(encoder, mission.outcome));
        }
        StringBuilder identityIncidents = new StringBuilder();
        for (IdentityIncident incident : state.identityIncidents) {
            if (!identityIncidents.isEmpty()) identityIncidents.append(';');
            identityIncidents.append(enc(encoder, incident.id)).append(':').append(incident.type).append(':')
                    .append(enc(encoder, incident.territoryId)).append(':')
                    .append(enc(encoder, incident.presentedTransponder)).append(':')
                    .append(enc(encoder, incident.verifiedAllegiance)).append(':')
                    .append(enc(encoder, incident.evidence)).append(':').append(incident.verified).append(':')
                    .append(incident.hostile);
        }
        return state.frontLinePosition + "|" + enc(encoder, state.doctrineSeason) + "|" + groups + "|"
                + state.territoryGraphVersion + "|" + territories + "|" + lanes + "|"
                + state.nextOperationId + "|" + operations + "|" + state.civilWarOutcome + "|"
                + state.civilWarElapsedTicks + ":" + state.brightYellowExhaustion + ":"
                + state.darkYellowExhaustion + ":" + state.civilWarCeasefire + ":"
                + state.brightCoalitionAid + ":" + state.darkRedAid + ":"
                + state.brightPoliticalObligation + ":" + state.darkPoliticalObligation + ":"
                + state.sharedHullIntelConfusion + "|" + history + "|" + commanders
                + "|" + state.nextBeachheadId + ":" + beachheads + "|" + battles + "|" + directors
                + "|" + state.nextCivilWarMissionId + ":" + civilMissions
                + "|" + state.nextIdentityIncidentId + ":" + identityIncidents;
    }

    public static State restore(String raw, long seed) {
        State state = bootstrap(seed);
        if (raw == null || raw.isBlank()) return state;
        String[] parts = raw.split("\\|", -1);
        if (parts.length < 3) return state;
        state.frontLinePosition = clamp(parseInt(parts[0], state.frontLinePosition), 0, 100);
        state.doctrineSeason = dec(parts[1], state.doctrineSeason);
        state.taskGroups.clear();
        for (String groupRaw : parts[2].split(";")) {
            String[] fields = groupRaw.split(":", -1);
            if (fields.length < 9) continue;
            TaskGroup group = new TaskGroup(dec(fields[0], "tg"), dec(fields[1], "Task Group"));
            group.order = parseEnum(fields[2], TaskOrder.HOLD);
            group.rulesOfEngagement = parseEnum(fields[3], RulesOfEngagement.BALANCED);
            group.automaticRetreatThreshold = clamp(parseInt(fields[4], 35), 0, 100);
            group.delegatedCaptain = dec(fields[5], "");
            group.route = dec(fields[6], "");
            group.etaHours = Math.max(0, parseInt(fields[7], 0));
            group.riskPercent = clamp(parseInt(fields[8], 0), 0, 100);
            state.taskGroups.add(group);
        }
        if (parts.length >= 6) {
            state.territoryGraphVersion = Math.max(1, parseInt(parts[3], TERRITORY_GRAPH_VERSION));
            state.territories.clear();
            for (String territoryRaw : parts[4].split(";")) {
                if (territoryRaw.isBlank()) continue;
                String[] fields = territoryRaw.split(":", -1);
                if (fields.length < 4) continue;
                String id = dec(fields[0], "");
                if (id.isBlank()) continue;
                Territory territory = new Territory(id, dec(fields[1], id),
                        migrateLegacyYellowId(dec(fields[2], "NEUTRAL"), id),
                        migrateLegacyYellowId(dec(fields[3], "NEUTRAL"), id));
                if (fields.length >= 7) {
                    territory.controlState = parseEnum(fields[4], TerritoryControlState.SECURE);
                    territory.controlProgress = clamp(parseInt(fields[5], 0), 0, 200);
                    territory.yellowHomeland = Boolean.parseBoolean(fields[6]);
                }
                if (fields.length >= 10) {
                    territory.supplySource = Boolean.parseBoolean(fields[7]);
                    territory.supplyState = parseEnum(fields[8], SupplyState.SUPPLIED);
                    territory.frontPressure = clamp(parseInt(fields[9], 0), 0, 100);
                }
                if (fields.length >= 37) {
                    territory.description = dec(fields[10], "");
                    territory.region = dec(fields[11], "frontier");
                    territory.centerX = parseDouble(fields[12], 0.0);
                    territory.centerY = parseDouble(fields[13], 0.0);
                    territory.minX = parseDouble(fields[14], territory.centerX);
                    territory.minY = parseDouble(fields[15], territory.centerY);
                    territory.maxX = parseDouble(fields[16], territory.centerX);
                    territory.maxY = parseDouble(fields[17], territory.centerY);
                    territory.legitimacy = clamp(parseInt(fields[18], 70), 0, 100);
                    territory.resistance = clamp(parseInt(fields[19], 0), 0, 100);
                    territory.morale = clamp(parseInt(fields[20], 70), 0, 100);
                    territory.infrastructure = clamp(parseInt(fields[21], 100), 0, 100);
                    territory.defensiveReadiness = clamp(parseInt(fields[22], 50), 0, 100);
                    territory.mineOutput = Math.max(0, parseInt(fields[23], 0));
                    territory.shipyardCapacity = clamp(parseInt(fields[24], 0), 0, 100);
                    territory.repairCapacity = clamp(parseInt(fields[25], 0), 0, 100);
                    territory.sensorCoverage = clamp(parseInt(fields[26], 50), 0, 100);
                    territory.strategicValue = Math.max(0, parseInt(fields[27], 1));
                    addCsv(territory.locationIds, dec(fields[28], ""));
                    addCsv(territory.tags, dec(fields[29], ""));
                    territory.supportsBasing = Boolean.parseBoolean(fields[30]);
                    territory.supportsRepairs = Boolean.parseBoolean(fields[31]);
                    territory.supportsConstruction = Boolean.parseBoolean(fields[32]);
                    territory.supportsReinforcement = Boolean.parseBoolean(fields[33]);
                    territory.supportsInvasionStaging = Boolean.parseBoolean(fields[34]);
                    territory.supplyReason = dec(fields[35], "Local supply source");
                    ArrayList<String> path = new ArrayList<>();
                    addCsv(path, dec(fields[36], ""));
                    territory.supplyPath = List.copyOf(path);
                    if (fields.length >= 39) {
                        territory.emergencySupplyTicks = Math.max(0, parseInt(fields[37], 0));
                        territory.emergencySupplyType = "NONE".equals(fields[38])
                                ? null : parseEnum(fields[38], EmergencySupplyType.RELIEF);
                    }
                    if (fields.length >= 50) {
                        territory.friendlyFleetStrength = clamp(parseInt(fields[39], 50), 0, 500);
                        territory.enemyFleetStrength = clamp(parseInt(fields[40], 0), 0, 500);
                        territory.fleetReadiness = clamp(parseInt(fields[41], 75), 0, 100);
                        territory.fleetDamage = clamp(parseInt(fields[42], 0), 0, 100);
                        territory.ammunition = clamp(parseInt(fields[43], 75), 0, 100);
                        territory.reinforcementTime = Math.max(0, parseInt(fields[44], 20));
                        territory.recentEconomicDisruption = clamp(parseInt(fields[45], 0), 0, 100);
                        territory.recentBattleMomentum = clamp(parseInt(fields[46], 0), -100, 100);
                        territory.recentCivilianConsequences = clamp(parseInt(fields[47], 0), 0, 100);
                        territory.notableCommanderId = dec(fields[48], "");
                        territory.doctrineMatchup = clamp(parseInt(fields[49], 0), -100, 100);
                    }
                } else if (territory.locationIds.isEmpty()) {
                    territory.locationIds.add(territory.id.value());
                }
                state.territories.add(territory);
            }
            state.lanes.clear();
            for (String laneRaw : parts[5].split(";")) {
                if (laneRaw.isBlank()) continue;
                String[] fields = laneRaw.split(":", -1);
                if (fields.length < 4) continue;
                String from = dec(fields[0], "");
                String to = dec(fields[1], "");
                if (from.isBlank() || to.isBlank()) continue;
                TravelLane lane = new TravelLane(from, to, parseEnum(fields[2], LaneType.TRAVEL_LANE),
                        Boolean.parseBoolean(fields[3]));
                if (fields.length >= 16) {
                    lane.directed = Boolean.parseBoolean(fields[4]);
                    lane.travelCost = Math.max(0, parseInt(fields[5], 10));
                    lane.supplyCost = Math.max(0, parseInt(fields[6], 10));
                    lane.transitRisk = clamp(parseInt(fields[7], 0), 0, 100);
                    lane.blockaded = Boolean.parseBoolean(fields[8]);
                    lane.routeCapacity = Math.max(0, parseInt(fields[9], 100));
                    lane.civilianTravelAllowed = Boolean.parseBoolean(fields[10]);
                    lane.militaryInvasionAllowed = Boolean.parseBoolean(fields[11]);
                    lane.requiredAccess = dec(fields[12], "");
                    lane.requiresTechnology = Boolean.parseBoolean(fields[13]);
                    lane.requiresIntelligence = Boolean.parseBoolean(fields[14]);
                    lane.infrastructureOperational = Boolean.parseBoolean(fields[15]);
                }
                state.lanes.add(lane);
            }
        }
        if (parts.length >= 8) {
            state.nextOperationId = Math.max(1, parseInt(parts[6], 1));
            state.operations.clear();
            for (String operationRaw : parts[7].split(";")) {
                if (operationRaw.isBlank()) continue;
                String[] fields = operationRaw.split(":", -1);
                if (fields.length < 7) continue;
                StrategicOperation operation = new StrategicOperation(dec(fields[0], "operation"),
                        parseEnum(fields[1], OperationType.RAID), dec(fields[2], "NEUTRAL"),
                        dec(fields[3], "unknown-origin"), dec(fields[4], "unknown-target"),
                        parseEnum(fields[5], OperationStatus.PLANNED), parseInt(fields[6], 0));
                if (fields.length >= 20) {
                    operation.sponsor = dec(fields[7], operation.faction);
                    operation.objective = dec(fields[8], defaultOperationObjective(operation.type));
                    operation.fleetId = dec(fields[9], "unassigned");
                    operation.supplyCommitment = Math.max(0, parseInt(fields[10], defaultSupplyCommitment(operation.type)));
                    operation.readinessRequired = clamp(parseInt(fields[11], defaultReadinessRequirement(operation.type)), 0, 100);
                    operation.durationTicks = Math.max(1, parseInt(fields[12], defaultOperationDuration(operation.type)));
                    operation.elapsedTicks = Math.max(0, parseInt(fields[13], 0));
                    operation.detectionChance = clamp(parseInt(fields[14], defaultDetectionChance(operation.type)), 0, 100);
                    operation.withdrawalBehavior = dec(fields[15], defaultWithdrawalBehavior(operation.type));
                    operation.intent = dec(fields[16], defaultOperationIntent(operation.type));
                    operation.stakes = dec(fields[17], defaultOperationStakes(operation.type));
                    operation.outcome = dec(fields[18], "Pending");
                    operation.consequence = dec(fields[19], "No consequences applied");
                    if (fields.length >= 22) {
                        operation.fleetLosses = Math.max(0, parseInt(fields[20], 0));
                        operation.supplySpent = Math.max(0, parseInt(fields[21], 0));
                        if (fields.length >= 23) operation.raidTarget = parseEnum(fields[22], RaidTarget.SUPPLY);
                    }
                }
                state.operations.add(operation);
            }
        }
        if (parts.length >= 9) state.civilWarOutcome = parseEnum(parts[8], CivilWarOutcome.ONGOING);
        if (parts.length >= 10) {
            String[] civil = parts[9].split(":", -1);
            if (civil.length >= 4) {
                state.civilWarElapsedTicks = Math.max(0, parseInt(civil[0], 0));
                state.brightYellowExhaustion = clamp(parseInt(civil[1], 0), 0, 100);
                state.darkYellowExhaustion = clamp(parseInt(civil[2], 0), 0, 100);
                state.civilWarCeasefire = Boolean.parseBoolean(civil[3]);
                if (civil.length >= 9) {
                    state.brightCoalitionAid = Math.max(0, parseInt(civil[4], 0));
                    state.darkRedAid = Math.max(0, parseInt(civil[5], 0));
                    state.brightPoliticalObligation = clamp(parseInt(civil[6], 0), 0, 100);
                    state.darkPoliticalObligation = clamp(parseInt(civil[7], 0), 0, 100);
                    state.sharedHullIntelConfusion = clamp(parseInt(civil[8], 25), 0, 100);
                }
            }
        }
        if (parts.length >= 11) {
            state.warEvents.clear();
            for (String eventRaw : parts[10].split(";")) {
                if (eventRaw.isBlank()) continue;
                String[] fields = eventRaw.split(":", -1);
                if (fields.length < 7) continue;
                recordWarEvent(state, dec(fields[0], "war-event"), parseInt(fields[1], 0),
                        dec(fields[2], "campaign"), dec(fields[3], "Campaign event"),
                        dec(fields[4], "No detail recorded"), dec(fields[5], "No consequence recorded"),
                        Boolean.parseBoolean(fields[6]));
            }
        }
        if (parts.length >= 12) {
            state.commanders.clear();
            for (String commanderRaw : parts[11].split(";")) {
                if (commanderRaw.isBlank()) continue;
                String[] fields = commanderRaw.split(":", -1);
                if (fields.length < 13) continue;
                RivalCommander commander = addCommander(state, dec(fields[0], "commander"),
                        dec(fields[1], "Unknown Commander"), dec(fields[2], "Captain"),
                        dec(fields[3], "NEUTRAL"), dec(fields[4], "unassigned"), dec(fields[5], "balanced"));
                commander.status = parseEnum(fields[6], CommanderStatus.ACTIVE);
                commander.victories = Math.max(0, parseInt(fields[7], 0));
                commander.defeats = Math.max(0, parseInt(fields[8], 0));
                commander.retreats = Math.max(0, parseInt(fields[9], 0));
                commander.encountersWithPlayer = Math.max(0, parseInt(fields[10], 0));
                commander.adaptationLevel = clamp(parseInt(fields[11], 0), 0, 3);
                commander.lastObservedPlayerDoctrine = dec(fields[12], "unknown");
                if (fields.length >= 26) {
                    commander.traits.clear(); addSeparated(commander.traits, dec(fields[13], ""));
                    commander.serviceHistory.clear(); addSeparated(commander.serviceHistory, dec(fields[14], ""));
                    commander.encounterMemories.clear(); addSeparated(commander.encounterMemories, dec(fields[15], ""));
                    commander.confidence = clamp(parseInt(fields[16], 55), 0, 100);
                    commander.caution = clamp(parseInt(fields[17], 50), 0, 100);
                    commander.aggression = clamp(parseInt(fields[18], 50), 0, 100);
                    commander.loyalty = clamp(parseInt(fields[19], 70), 0, 100);
                    commander.politicalStanding = clamp(parseInt(fields[20], 50), 0, 100);
                    commander.warExhaustion = clamp(parseInt(fields[21], 0), 0, 100);
                    commander.injuries = Math.max(0, parseInt(fields[22], 0));
                    commander.resources = Math.max(0, parseInt(fields[23], 0));
                    commander.strategicAuthority = clamp(parseInt(fields[24], 1), 0, 5);
                    commander.currentCountermeasure = dec(fields[25], "none");
                }
            }
        }
        if (parts.length >= 13) {
            String[] beachheadSection = parts[12].split(":", 2);
            state.nextBeachheadId = Math.max(1, parseInt(beachheadSection[0], 1));
            state.beachheads.clear();
            if (beachheadSection.length == 2) {
                for (String beachheadRaw : beachheadSection[1].split(";")) {
                    if (beachheadRaw.isBlank()) continue;
                    String[] fields = beachheadRaw.split(":", -1);
                    if (fields.length < 11) continue;
                    Beachhead beachhead = new Beachhead(dec(fields[0], "beachhead"), dec(fields[1], "NEUTRAL"),
                            dec(fields[2], "unknown-target"), parseEnum(fields[3], BeachheadAuthorization.AUTHORED_SCENARIO),
                            parseInt(fields[4], 1), parseInt(fields[5], 0), parseInt(fields[6], 1),
                            parseInt(fields[7], 1), parseInt(fields[9], 50));
                    beachhead.ageTicks = Math.max(0, parseInt(fields[8], 0));
                    beachhead.status = parseEnum(fields[10], BeachheadStatus.ACTIVE);
                    state.beachheads.add(beachhead);
                }
            }
        }
        if (parts.length >= 14) {
            state.battleReports.clear();
            for (String battleRaw : parts[13].split(";")) {
                if (battleRaw.isBlank()) continue;
                String[] fields = battleRaw.split(":", -1);
                if (fields.length < 16) continue;
                BattleReport report = new BattleReport(dec(fields[0], "battle"));
                report.locationId = dec(fields[1], "unknown");
                report.tick = Math.max(0, parseInt(fields[2], 0));
                addSeparated(report.participants, dec(fields[3], ""));
                addSeparated(report.objectives, dec(fields[4], ""));
                report.losses = Math.max(0, parseInt(fields[5], 0));
                report.outcome = dec(fields[6], "unresolved");
                report.salvageRemaining = clamp(parseInt(fields[7], 100), 0, 100);
                report.hazardLevel = clamp(parseInt(fields[8], 25), 0, 100);
                report.survivorWindowTicks = Math.max(0, parseInt(fields[9], 10));
                report.occupiedBy = dec(fields[10], "NEUTRAL");
                report.battleScar = dec(fields[11], "wreck field");
                report.prisoners = Math.max(0, parseInt(fields[12], 0));
                report.wreckFieldVisitAvailable = Boolean.parseBoolean(fields[13]);
                report.rescueAvailable = Boolean.parseBoolean(fields[14]);
                report.salvageRightsDisputed = Boolean.parseBoolean(fields[15]);
                state.battleReports.add(report);
            }
        }
        if (parts.length >= 15) {
            state.directors.clear();
            for (String directorRaw : parts[14].split(";")) {
                if (directorRaw.isBlank()) continue;
                String[] fields = directorRaw.split(":", -1);
                if (fields.length < 12) continue;
                String faction = dec(fields[0], "NEUTRAL");
                FactionDirector director = new FactionDirector(faction,
                        parseEnum(fields[1], DirectorPersonality.METHODICAL), parseInt(fields[2], 100),
                        clamp(parseInt(fields[3], 55), 0, 100), dec(fields[8], "Survive"));
                director.exhaustion = clamp(parseInt(fields[4], 0), 0, 100);
                director.politicalPressure = clamp(parseInt(fields[5], 0), 0, 100);
                director.mistakes = Math.max(0, parseInt(fields[6], 0));
                director.recoveries = Math.max(0, parseInt(fields[7], 0));
                director.homelandReserve = Math.max(0, parseInt(fields[9], 25));
                director.committedPlan = dec(fields[10], "");
                addSeparated(director.rejectedAlternatives, dec(fields[11], ""));
                state.directors.put(faction, director);
            }
        }
        if (parts.length >= 16) {
            String[] section = parts[15].split(":", 2);
            state.nextCivilWarMissionId = Math.max(1, parseInt(section[0], 1));
            state.civilWarMissions.clear();
            if (section.length == 2) {
                for (String rawMission : section[1].split(";")) {
                    if (rawMission.isBlank()) continue;
                    String[] fields = rawMission.split(":", -1);
                    if (fields.length < 13) continue;
                    CivilWarMission mission = new CivilWarMission(dec(fields[0], "yellow-mission"),
                            parseEnum(fields[1], CivilWarMissionType.DISPUTED_STATION), dec(fields[2], "frontier"));
                    mission.title = dec(fields[3], "Civil-war mission");
                    mission.objective = dec(fields[4], "Resolve the local crisis");
                    mission.sponsor = dec(fields[5], "NEUTRAL");
                    mission.opposingFaction = dec(fields[6], "NEUTRAL");
                    mission.status = parseEnum(fields[7], CivilWarMissionStatus.OFFERED);
                    mission.collateralLimit = clamp(parseInt(fields[8], 20), 0, 100);
                    mission.legitimacyAtStake = clamp(parseInt(fields[9], 10), 0, 100);
                    mission.humanitarianLives = Math.max(0, parseInt(fields[10], 0));
                    mission.identityVerificationRequired = Boolean.parseBoolean(fields[11]);
                    mission.outcome = dec(fields[12], "Pending player action");
                    state.civilWarMissions.add(mission);
                }
            }
        }
        if (parts.length >= 17) {
            String[] section = parts[16].split(":", 2);
            state.nextIdentityIncidentId = Math.max(1, parseInt(section[0], 1));
            state.identityIncidents.clear();
            if (section.length == 2) {
                for (String rawIncident : section[1].split(";")) {
                    if (rawIncident.isBlank()) continue;
                    String[] fields = rawIncident.split(":", -1);
                    if (fields.length < 8) continue;
                    IdentityIncident incident = new IdentityIncident(dec(fields[0], "yellow-identity"),
                            parseEnum(fields[1], IdentityIncidentType.TRANSPONDER_CONFUSION),
                            dec(fields[2], "frontier"));
                    incident.presentedTransponder = dec(fields[3], "UNKNOWN");
                    incident.verifiedAllegiance = dec(fields[4], "UNKNOWN");
                    incident.evidence = dec(fields[5], "No evidence recorded");
                    incident.verified = Boolean.parseBoolean(fields[6]);
                    incident.hostile = Boolean.parseBoolean(fields[7]);
                    state.identityIncidents.add(incident);
                }
            }
        }
        return state;
    }

    private static String normalized(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    private static String requireId(String value, String kind) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(kind + " id cannot be blank");
        return normalized;
    }

    private static String migrateLegacyYellowId(String value, String stableKey) {
        if (!"TEAM_D".equals(value) && !"Yellow".equalsIgnoreCase(value)) return value;
        return legacyYellowBright(stableKey) ? "BRIGHT_YELLOW" : "DARK_YELLOW";
    }

    private static boolean legacyYellowBright(String stableKey) {
        String key = stableKey == null ? "" : stableKey;
        int number = -1;
        for (int i = key.length() - 1; i >= 0; i--) {
            if (!Character.isDigit(key.charAt(i))) {
                if (i < key.length() - 1) number = parseInt(key.substring(i + 1), -1);
                break;
            }
            if (i == 0) number = parseInt(key, -1);
        }
        return number >= 0 ? (number & 1) == 1 : Math.floorMod(key.hashCode(), 2) == 0;
    }

    private static String enc(Base64.Encoder encoder, String value) {
        return encoder.encodeToString(((value == null) ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String dec(String value, String fallback) {
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static void addCsv(java.util.Collection<String> target, String csv) {
        if (target == null || csv == null || csv.isBlank()) return;
        for (String value : csv.split(",")) if (!value.isBlank()) target.add(value.trim());
    }

    private static void addSeparated(java.util.Collection<String> target, String value) {
        if (target == null || value == null || value.isBlank()) return;
        for (String item : value.split("\u001f", -1)) if (!item.isBlank()) target.add(item);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static <T extends Enum<T>> T parseEnum(String value, T fallback) {
        try {
            return Enum.valueOf(fallback.getDeclaringClass(), value.toUpperCase(Locale.US));
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
