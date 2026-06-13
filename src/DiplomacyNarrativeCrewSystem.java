import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Section 7 campaign relationship, reactive-story, and persistent bridge-crew model. */
public final class DiplomacyNarrativeCrewSystem {
    public enum ReputationGroup { MILITARY, CIVILIAN, INDUSTRIAL, POLITICAL }
    public enum PrisonerChoice { RELEASE, EXCHANGE, DETAIN, ABANDON }
    public enum ArrivalState { EARLY, ON_TIME, LATE, OVERPREPARED, DEPLETED }
    public enum CrewStation { CAPTAIN, HELM, TACTICAL, ENGINEERING, SCIENCE }
    public enum Specialty { COMMAND, NAVIGATION, GUNNERY, DAMAGE_CONTROL, ANALYSIS }

    public static final class ReputationChange {
        public final ReputationGroup group;
        public final int delta;
        public final String reason;

        ReputationChange(ReputationGroup group, int delta, String reason) {
            this.group = group;
            this.delta = delta;
            this.reason = reason;
        }
    }

    public static final class RelationshipState {
        public final EnumMap<ReputationGroup, Integer> reputation = new EnumMap<>(ReputationGroup.class);
        public final List<ReputationChange> visibleReasons = new ArrayList<>();
        public final List<String> favors = new ArrayList<>();
        public final List<String> obligations = new ArrayList<>();
        public final List<String> factionRequests = new ArrayList<>();
        public final List<String> negotiationScenes = new ArrayList<>();
        public boolean ceasefire;
        public boolean temporaryAlliance;
        public int betrayalRiskPercent;
        public int roeConsequences;
        public int civilianCollateralConsequences;
        public PrisonerChoice prisonerChoice = PrisonerChoice.EXCHANGE;
        public boolean salvageRightsDispute;
        public final List<String> diplomaticMissions = new ArrayList<>();
    }

    public static final class NpcCaptain {
        public final String id;
        public final String name;
        public final List<String> encounterMemories = new ArrayList<>();
        public boolean rivalCommander;
        public int rescueReturns;
        public int revengeArcStage;

        NpcCaptain(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static final class Officer {
        public final CrewStation station;
        public String name;
        public String portrait;
        public Specialty specialty;
        public String opinion;
        public int trust;
        public int stress;
        public boolean casualtyReplacement;
        public String tacticalRecommendation;
        public final List<String> captainLogEntries = new ArrayList<>();
        public boolean voicedBriefingAvailable;

        Officer(CrewStation station, String name, String portrait, Specialty specialty, String opinion) {
            this.station = station;
            this.name = name;
            this.portrait = portrait;
            this.specialty = specialty;
            this.opinion = opinion;
        }
    }

    public static final class State {
        public final RelationshipState relationships = new RelationshipState();
        public final Map<String, NpcCaptain> npcCaptains = new LinkedHashMap<>();
        public final EnumMap<CrewStation, Officer> officers = new EnumMap<>(CrewStation.class);
        public final List<String> newsBulletins = new ArrayList<>();
        public final List<String> crewCommentary = new ArrayList<>();
        public final List<String> officerDisagreements = new ArrayList<>();
        public final List<String> dynamicMissionBriefings = new ArrayList<>();
        public final List<String> authoredStoryBeats = new ArrayList<>();
        public final List<String> epilogueTimeline = new ArrayList<>();
        public ArrivalState lastArrivalState = ArrivalState.ON_TIME;
        public String campaignEnding = "War state unresolved";
        public int banterFrequencyPercent = 55;
        public boolean quietMode;
    }

    private DiplomacyNarrativeCrewSystem() {}

    public static State bootstrap(long seed) {
        State state = new State();
        for (ReputationGroup group : ReputationGroup.values()) state.relationships.reputation.put(group, 50);
        state.relationships.ceasefire = false;
        state.relationships.temporaryAlliance = false;
        state.relationships.betrayalRiskPercent = 0;
        state.relationships.salvageRightsDispute = false;

        addCaptain(state, "voss", "Captain Nadi Voss", false);
        addCaptain(state, "rook", "Captain Sera Rook", true);
        addCaptain(state, "marr", "Broker-Captain Ilya Marr", false);
        addOfficer(state, CrewStation.CAPTAIN, "Nadi Voss", "captain.png", Specialty.COMMAND, "Preserve the fleet before chasing glory.");
        addOfficer(state, CrewStation.HELM, "Mira Hale", "helm.png", Specialty.NAVIGATION, "Use the hidden route while the lane is quiet.");
        addOfficer(state, CrewStation.TACTICAL, "Sera Rook", "tactical.png", Specialty.GUNNERY, "Press the flank before Red regroups.");
        addOfficer(state, CrewStation.ENGINEERING, "Tomas Vale", "engineering.png", Specialty.DAMAGE_CONTROL, "Refit before the next jump.");
        addOfficer(state, CrewStation.SCIENCE, "Ilya Marr", "science.png", Specialty.ANALYSIS, "Survey the anomaly before committing.");
        return state;
    }

    private static void addCaptain(State state, String id, String name, boolean rival) {
        NpcCaptain captain = new NpcCaptain(id, name);
        captain.rivalCommander = rival;
        state.npcCaptains.put(id, captain);
    }

    private static void addOfficer(State state, CrewStation station, String name, String portrait,
                                   Specialty specialty, String opinion) {
        Officer officer = new Officer(station, name, portrait, specialty, opinion);
        officer.trust = 58;
        officer.stress = 12;
        officer.tacticalRecommendation = opinion;
        officer.voicedBriefingAvailable = true;
        state.officers.put(station, officer);
    }

    public static void changeReputation(State state, ReputationGroup group, int delta, String reason) {
        if (state == null || group == null || reason == null || reason.isBlank()) return;
        RelationshipState relationships = state.relationships;
        relationships.reputation.put(group, clamp(relationships.reputation.getOrDefault(group, 50) + delta, 0, 100));
        relationships.visibleReasons.add(new ReputationChange(group, delta, reason.trim()));
    }

    public static void rememberEncounter(State state, String captainId, String memory) {
        NpcCaptain captain = (state == null) ? null : state.npcCaptains.get(captainId);
        if (captain == null || memory == null || memory.isBlank()) return;
        captain.encounterMemories.add(memory.trim());
    }

    public static void recordRescueReturn(State state, String captainId) {
        NpcCaptain captain = (state == null) ? null : state.npcCaptains.get(captainId);
        if (captain != null) captain.rescueReturns++;
    }

    public static void advanceRevengeArc(State state, String captainId) {
        NpcCaptain captain = (state == null) ? null : state.npcCaptains.get(captainId);
        if (captain != null) captain.revengeArcStage++;
    }

    public static void recordDecision(State state, String summary, ArrivalState arrival, int officerStress) {
        if (state == null || summary == null || summary.isBlank()) return;
        state.lastArrivalState = (arrival == null) ? ArrivalState.ON_TIME : arrival;
        state.crewCommentary.add(summary.trim() + " [" + state.lastArrivalState + "]");
        for (Officer officer : state.officers.values()) {
            officer.stress = clamp(officer.stress + officerStress, 0, 100);
            officer.captainLogEntries.add(summary.trim());
        }
        if (officerStress > 0) {
            Officer tactical = state.officers.get(CrewStation.TACTICAL);
            Officer engineering = state.officers.get(CrewStation.ENGINEERING);
            if (tactical != null && engineering != null) {
                state.officerDisagreements.add(tactical.name + " favors action; " + engineering.name + " requests caution.");
            }
        }
    }

    public static void replaceOfficer(State state, CrewStation station, String name, String portrait) {
        Officer officer = (state == null) ? null : state.officers.get(station);
        if (officer == null || name == null || name.isBlank()) return;
        officer.name = name.trim();
        officer.portrait = (portrait == null) ? "" : portrait.trim();
        officer.casualtyReplacement = true;
        officer.trust = 30;
        officer.stress = 35;
    }

    public static void resolveEnding(State state, int warScore, int allyCount, int losses, String doctrine) {
        if (state == null) return;
        if (warScore >= 75 && allyCount >= 2 && losses <= 3) state.campaignEnding = "Coalition restoration";
        else if (warScore >= 55) state.campaignEnding = "Hard-won armistice";
        else state.campaignEnding = "Evacuation under pressure";
        state.epilogueTimeline.add("Final doctrine: " + ((doctrine == null) ? "unrecorded" : doctrine));
        state.epilogueTimeline.add("Ending: " + state.campaignEnding);
    }

    public static List<String> commandBoardLines(State state) {
        if (state == null) return List.of("Diplomacy data unavailable.");
        RelationshipState rel = state.relationships;
        return List.of(
                "Reputation Mil " + reputation(rel, ReputationGroup.MILITARY) + "  |  Civ "
                        + reputation(rel, ReputationGroup.CIVILIAN) + "  |  Ind "
                        + reputation(rel, ReputationGroup.INDUSTRIAL) + "  |  Pol "
                        + reputation(rel, ReputationGroup.POLITICAL),
                "Captains " + state.npcCaptains.size() + "  |  Officers " + state.officers.size()
                        + "  |  Bulletins " + state.newsBulletins.size(),
                "Crew chatter " + (state.quietMode ? "quiet" : state.banterFrequencyPercent + "%")
                        + "  |  Ending " + state.campaignEnding
        );
    }

    public static String serialize(State state) {
        if (state == null) return "";
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        StringBuilder officers = new StringBuilder();
        for (Officer officer : state.officers.values()) {
            if (!officers.isEmpty()) officers.append(';');
            officers.append(officer.station).append(':').append(text(enc, officer.name)).append(':')
                    .append(text(enc, officer.portrait)).append(':').append(officer.specialty).append(':')
                    .append(officer.trust).append(':').append(officer.stress).append(':')
                    .append(officer.casualtyReplacement);
        }
        RelationshipState rel = state.relationships;
        return reputation(rel, ReputationGroup.MILITARY) + "," + reputation(rel, ReputationGroup.CIVILIAN) + ","
                + reputation(rel, ReputationGroup.INDUSTRIAL) + "," + reputation(rel, ReputationGroup.POLITICAL)
                + "|" + state.banterFrequencyPercent + "|" + state.quietMode + "|" + text(enc, state.campaignEnding)
                + "|" + officers;
    }

    public static State restore(String raw, long seed) {
        State state = bootstrap(seed);
        if (raw == null || raw.isBlank()) return state;
        String[] parts = raw.split("\\|", -1);
        if (parts.length < 5) return state;
        String[] rep = parts[0].split(",", -1);
        if (rep.length >= 4) {
            state.relationships.reputation.put(ReputationGroup.MILITARY, clamp(number(rep[0], 50), 0, 100));
            state.relationships.reputation.put(ReputationGroup.CIVILIAN, clamp(number(rep[1], 50), 0, 100));
            state.relationships.reputation.put(ReputationGroup.INDUSTRIAL, clamp(number(rep[2], 50), 0, 100));
            state.relationships.reputation.put(ReputationGroup.POLITICAL, clamp(number(rep[3], 50), 0, 100));
        }
        state.banterFrequencyPercent = clamp(number(parts[1], 55), 0, 100);
        state.quietMode = Boolean.parseBoolean(parts[2]);
        state.campaignEnding = decoded(parts[3], state.campaignEnding);
        for (String item : parts[4].split(";")) {
            String[] f = item.split(":", -1);
            if (f.length < 7) continue;
            CrewStation station = enumValue(f[0], CrewStation.CAPTAIN);
            Officer officer = state.officers.get(station);
            if (officer == null) continue;
            officer.name = decoded(f[1], officer.name);
            officer.portrait = decoded(f[2], officer.portrait);
            officer.specialty = enumValue(f[3], officer.specialty);
            officer.trust = clamp(number(f[4], officer.trust), 0, 100);
            officer.stress = clamp(number(f[5], officer.stress), 0, 100);
            officer.casualtyReplacement = Boolean.parseBoolean(f[6]);
        }
        return state;
    }

    private static int reputation(RelationshipState state, ReputationGroup group) {
        return state.reputation.getOrDefault(group, 50);
    }

    private static String text(Base64.Encoder encoder, String value) {
        return encoder.encodeToString(((value == null) ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decoded(String value, String fallback) {
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

    private static <T extends Enum<T>> T enumValue(String value, T fallback) {
        try {
            return Enum.valueOf(fallback.getDeclaringClass(), value);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
