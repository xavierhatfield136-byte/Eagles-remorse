import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Section 5 strategic-campaign model layered over the live campaign simulation. */
public final class StrategicCampaignExpansionSystem {
    public enum RegionRule { SHELTERED, CONTESTED_BELT, GRAVITY_WELL, ORBITAL, DEEP_SPACE_ANOMALY }
    public enum LaneType { TRAVEL_LANE, JUMP_POINT, HIDDEN_ROUTE, BLOCKADE_CHOKEPOINT }
    public enum InstallationType { HUB, FORWARD_BASE, RESOURCE_BELT, POPULATION_CENTER, ORBITAL_PLATFORM }
    public enum DirectorAction { RAID, DEFEND, LOGISTICS, RESEARCH, DIPLOMACY, MAJOR_OFFENSIVE, FEINT, MISINFORMATION }
    public enum DirectorPersonality { METHODICAL, OPPORTUNIST, ATTRITIONIST, DIPLOMAT, ROGUE_AI, PIRATE_BROKER }
    public enum Intervention { JOIN, IGNORE, STRIKE, OBSERVE, EVACUATE, BLOCKADE_RUN, RELIEF, PURSUIT, SURRENDER }
    public enum TaskOrder { HOLD, ROUTE, PATROL_LOOP, ESCORT, GARRISON, CONVOY, REPAIR_RESUPPLY, SCOUT, AMBUSH, BLOCKADE }
    public enum RulesOfEngagement { CONSERVE_FORCE, DEFENSIVE, BALANCED, AGGRESSIVE }
    public enum MapOverlay { LOGISTICS, SENSORS, CONTROL, DANGER, TRADE, HOSTILE_ROUTES, FACILITIES, MISSIONS, FLEETS, ROUTES, INTEL }

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
        public final List<String> participants = new ArrayList<>();
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

        BattleReport(String id) {
            this.id = id;
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
        public final List<StarSystem> systems = new ArrayList<>();
        public final List<TravelLane> lanes = new ArrayList<>();
        public final List<Installation> installations = new ArrayList<>();
        public final Map<String, FactionDirector> directors = new LinkedHashMap<>();
        public final List<BattleReport> battleReports = new ArrayList<>();
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
        state.installations.add(new Installation("southern-hub", InstallationType.HUB, "shelter", "Green"));
        state.installations.add(new Installation("frontier-fob", InstallationType.FORWARD_BASE, "frontier", "Blue"));
        state.installations.add(new Installation("belt-claims", InstallationType.RESOURCE_BELT, "frontier", "Contested"));
        state.installations.add(new Installation("lunar-colonies", InstallationType.POPULATION_CENTER, "well", "Yellow"));
        state.installations.add(new Installation("sol-platform", InstallationType.ORBITAL_PLATFORM, "sol", "Red"));
        addDirector(state, "Blue", DirectorPersonality.METHODICAL, "Stabilize theaters and liberate Sol");
        addDirector(state, "Red", DirectorPersonality.ATTRITIONIST, "Hold Sol and exhaust coalition logistics");
        addDirector(state, "Green", DirectorPersonality.DIPLOMAT, "Preserve trade hubs and civilian routes");
        addDirector(state, "Yellow", DirectorPersonality.OPPORTUNIST, "Survive, trade, and reclaim orbital access");
        addDirector(state, "Rogue AI", DirectorPersonality.ROGUE_AI, "Escalate without normal political constraints");
        addDirector(state, "Pirates", DirectorPersonality.PIRATE_BROKER, "Exploit exposed convoys and wreck fields");
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
        if (state != null) state.battleReports.add(report);
        return report;
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
        return List.of(
                "Systems " + state.systems.size() + "  |  Lanes " + state.lanes.size() + "  |  Front " + state.frontLinePosition,
                "Directors " + state.directors.size() + "  |  Task Groups " + state.taskGroups.size() + "  |  Reports " + state.battleReports.size(),
                "Overlays " + state.overlays.size() + "  |  " + state.doctrineSeason
        );
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
        return state.frontLinePosition + "|" + enc(encoder, state.doctrineSeason) + "|" + groups;
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
        return state;
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
