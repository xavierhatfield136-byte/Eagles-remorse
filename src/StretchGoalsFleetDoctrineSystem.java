import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sections 16-18 stretch catalog and persistent fleet-command friction model. */
public final class StretchGoalsFleetDoctrineSystem {
    public enum SharedCommandRole { CAPTAIN, HELM, TACTICAL, ENGINEERING, SCIENCE, FLEET_COORDINATOR }
    public enum EditorType { SCENARIO, MISSION, FACTION }
    public enum CampaignFantasy { BLUE_LIBERATION, GREEN_DEFENSE, YELLOW_TRADE_SURVIVAL, RED_OFFENSIVE, ROGUE_AI_SURVIVAL }
    public enum NodeType { FLAGSHIP, RELAY, FALLBACK }
    public enum ChannelMode { STANDARD, ENCRYPTED, BURST_TRANSMISSION, COURIER_DRONE }
    public enum DoctrineTemplate { CONVOY_ESCORT, FLEET_BATTLE, RAID, RESCUE, BLOCKADE }
    public enum Discipline { MILITARY, MILITIA, PIRATE, CIVILIAN, AI }

    public static final class StretchCatalog {
        public final List<SharedCommandRole> cooperativeRoles = new ArrayList<>();
        public boolean asynchronousCampaignSharing;
        public boolean skirmishFleetBuilder;
        public final List<EditorType> editors = new ArrayList<>();
        public boolean workshopStylePackaging;
        public boolean proceduralStarSystems;
        public boolean branchingCampaignChapters;
        public final List<CampaignFantasy> factionCampaigns = new ArrayList<>();
        public boolean balancedMetagameUnlocks;
        public boolean autonomousSpectatorMode;
        public boolean exportableAfterActionReports;
        public boolean cinematicReplayCamera;
        public final List<String> postReleaseRoadmap = new ArrayList<>();
    }

    public static final class ExtractionPack {
        public final String title;
        public final String artifact;

        ExtractionPack(String title, String artifact) {
            this.title = title;
            this.artifact = artifact;
        }
    }

    public static final class CommandNode {
        public final String id;
        public final NodeType type;
        public String ship;
        public boolean operational = true;
        public int redundancyBonus;

        CommandNode(String id, NodeType type, String ship, int redundancyBonus) {
            this.id = id;
            this.type = type;
            this.ship = ship;
            this.redundancyBonus = redundancyBonus;
        }
    }

    public static final class QueuedOrder {
        public final String text;
        public final String captainInterpretation;

        QueuedOrder(String text, String captainInterpretation) {
            this.text = text;
            this.captainInterpretation = captainInterpretation;
        }
    }

    public static final class StandingOrders {
        public boolean conserveAmmunition = true;
        public int retreatThresholdPercent = 35;
        public boolean rescueDisabledAllies = true;
        public boolean protectCivilianTraffic = true;
        public boolean pursueFleeingEnemies;
        public boolean acceptSurrender = true;
        public boolean scuttleCompromisedShips;
        public boolean preserveRareCapturedTechnology = true;
        public DoctrineTemplate template = DoctrineTemplate.CONVOY_ESCORT;
        public final Map<String, String> captainExceptions = new LinkedHashMap<>();
        public final List<String> afterActionNotes = new ArrayList<>();
    }

    public static final class FleetCommandState {
        public final List<CommandNode> nodes = new ArrayList<>();
        public final List<QueuedOrder> orderQueue = new ArrayList<>();
        public final StandingOrders standingOrders = new StandingOrders();
        public ChannelMode channelMode = ChannelMode.STANDARD;
        public int bandwidthCapacity = 8;
        public int bandwidthUsed;
        public int cohesionPercent = 82;
        public int squadronCrossfireBonusPercent = 12;
        public int isolationPenaltyPercent;
        public int panicPercent;
        public int rallyActions;
        public Discipline discipline = Discipline.MILITARY;
        public boolean networkCollapsed;
        public boolean commandLinkOverlay;
        public boolean exhaustedReserveRotation;
        public String doctrineAcknowledgment = "Blue command: order received and plotted.";
        public String preBattleReview = "Escort posture: conserve ammunition, protect traffic, retreat at 35%.";
    }

    public static final class State {
        public final StretchCatalog stretch = new StretchCatalog();
        public final List<ExtractionPack> extractionPacks = new ArrayList<>();
        public final FleetCommandState fleet = new FleetCommandState();
    }

    private StretchGoalsFleetDoctrineSystem() {}

    public static State bootstrap(long seed) {
        State state = new State();
        state.stretch.postReleaseRoadmap.addAll(List.of(
                "Networked cooperative command roles",
                "Asynchronous campaign sharing",
                "Standalone skirmish fleet builder",
                "Visual scenario, mission, and faction editors",
                "Workshop-style content-pack distribution",
                "Procedural star-system generation",
                "Branching chapters and faction-specific campaigns",
                "Metagame unlocks, spectator mode, report export, and cinematic replay camera"
        ));

        addPack(state, "Stability and state-machine hardening checklist", "docs/extraction-packs/STABILITY_AND_STATE_MACHINE.md");
        addPack(state, "Performance budget and asset-lifetime checklist", "docs/extraction-packs/PERFORMANCE_AND_ASSET_LIFETIME.md");
        addPack(state, "First-hour onboarding redesign", "docs/FIRST_HOUR_EXPERIENCE.md");
        addPack(state, "Tactical fleet-orders implementation plan", "docs/TACTICAL_COMBAT_DEPTH.md");
        addPack(state, "Persistent ship history and captain system", "docs/extraction-packs/PERSISTENT_SHIPS_AND_CAPTAINS.md");
        addPack(state, "Multi-system strategic map expansion", "docs/STRATEGIC_CAMPAIGN_MAP_SPEC.md");
        addPack(state, "Economy, logistics, and market overhaul", "docs/extraction-packs/ECONOMY_LOGISTICS_MARKETS.md");
        addPack(state, "Diplomacy and reactive narrative roadmap", "docs/extraction-packs/DIPLOMACY_REACTIVE_NARRATIVE.md");
        addPack(state, "Tactical battlefield identity art plan", "docs/STRATEGIC_CAMPAIGN_FURNISHING_PLAN.md");
        addPack(state, "Accessibility and input-remapping checklist", "docs/extraction-packs/ACCESSIBILITY_AND_INPUT.md");
        addPack(state, "Architecture decomposition plan", "docs/extraction-packs/ARCHITECTURE_DECOMPOSITION.md");
        addPack(state, "Automated scenario and soak-test harness plan", "docs/PERFORMANCE_GUARDRAILS.md");
        addPack(state, "Section 27 deep-simulation and community design packs", "docs/extraction-packs/SECTION_27_DESIGN_PACKS.md");

        state.fleet.nodes.add(new CommandNode("node-flag", NodeType.FLAGSHIP, "Blue Flagship", 2));
        state.fleet.nodes.add(new CommandNode("node-relay", NodeType.RELAY, "Relay Cruiser", 3));
        state.fleet.nodes.add(new CommandNode("node-fallback", NodeType.FALLBACK, "Veteran Escort", 1));
        state.fleet.standingOrders.captainExceptions.put("Captain Rook", "Pursue fleeing enemies when target is Red command.");
        state.fleet.standingOrders.afterActionNotes.add("Civilian-protection order prevented unsafe pursuit.");
        return state;
    }

    private static void addPack(State state, String title, String artifact) {
        state.extractionPacks.add(new ExtractionPack(title, artifact));
    }

    public static void queueOrder(State state, String text, boolean vague) {
        if (state == null || text == null || text.isBlank()) return;
        FleetCommandState fleet = state.fleet;
        String interpretation = vague ? "Captain interprets pressure locally under stress." : "Exact order acknowledged.";
        fleet.orderQueue.add(new QueuedOrder(text.trim(), interpretation));
        fleet.bandwidthUsed = Math.min(fleet.bandwidthCapacity + 4, fleet.bandwidthUsed + 1);
    }

    public static void synchronizeLiveFleet(State state, int operationalShips, int damagedShips,
                                            boolean flagshipOperational, int relayCount) {
        if (state == null) return;
        FleetCommandState fleet = state.fleet;
        boolean wasCollapsed = fleet.networkCollapsed;
        int priorPanic = fleet.panicPercent;
        int priorRally = fleet.rallyActions;
        fleet.nodes.clear();
        fleet.nodes.add(new CommandNode("node-flag", NodeType.FLAGSHIP,
                flagshipOperational ? "Live Flagship" : "Flagship link lost", flagshipOperational ? 2 : 0));
        for (int i = 0; i < Math.max(0, relayCount); i++) {
            fleet.nodes.add(new CommandNode("node-relay-" + (i + 1), NodeType.RELAY, "Live Relay " + (i + 1), 2));
        }
        if (operationalShips > 1) {
            fleet.nodes.add(new CommandNode("node-fallback", NodeType.FALLBACK, "Live Escort Fallback", 1));
        }
        fleet.nodes.get(0).operational = flagshipOperational;
        fleet.networkCollapsed = !flagshipOperational && relayCount <= 0;
        int damagePenalty = Math.max(0, damagedShips) * 6;
        int redundancy = Math.max(0, relayCount) * 2 + (operationalShips > 1 ? 1 : 0);
        fleet.bandwidthCapacity = Math.max(1, channelCapacity(fleet.channelMode) + redundancy - damagePenalty / 4);
        fleet.cohesionPercent = clamp(82 - damagePenalty + redundancy, 0, 100);
        fleet.panicPercent = clamp(Math.max(priorPanic - (relayCount > 0 ? 3 : 0), fleet.networkCollapsed ? priorPanic + 12 : priorPanic), 0, 100);
        if (wasCollapsed && !fleet.networkCollapsed) {
            fleet.rallyActions = priorRally + 1;
            fleet.cohesionPercent = clamp(fleet.cohesionPercent + 8, 0, 100);
            fleet.panicPercent = clamp(fleet.panicPercent - 10, 0, 100);
        } else {
            fleet.rallyActions = priorRally;
        }
        fleet.isolationPenaltyPercent = fleet.networkCollapsed
                ? clamp(fleet.isolationPenaltyPercent + 10, 0, 100)
                : clamp(fleet.isolationPenaltyPercent - Math.max(1, relayCount), 0, 100);
        fleet.exhaustedReserveRotation = fleet.networkCollapsed || fleet.cohesionPercent < 55 || fleet.panicPercent >= 45;
        fleet.preBattleReview = fleet.standingOrders.template + " posture: bandwidth " + fleet.bandwidthUsed + "/"
                + fleet.bandwidthCapacity + ", retreat at " + fleet.standingOrders.retreatThresholdPercent
                + "%, rescue " + (fleet.standingOrders.rescueDisabledAllies ? "priority" : "optional")
                + ", surrender " + (fleet.standingOrders.acceptSurrender ? "accepted" : "refused") + ".";
    }

    public static void resolveQueuedOrders(State state) {
        if (state == null) return;
        FleetCommandState fleet = state.fleet;
        if (fleet.orderQueue.isEmpty()) return;
        QueuedOrder order = fleet.orderQueue.remove(0);
        fleet.bandwidthUsed = Math.max(0, fleet.bandwidthUsed - 1);
        fleet.doctrineAcknowledgment = "Order acknowledged: " + order.text + "  |  " + order.captainInterpretation;
        fleet.standingOrders.afterActionNotes.add(fleet.doctrineAcknowledgment);
        while (fleet.standingOrders.afterActionNotes.size() > 8) fleet.standingOrders.afterActionNotes.remove(0);
    }

    public static void setChannelMode(State state, ChannelMode mode) {
        if (state == null || mode == null) return;
        state.fleet.channelMode = mode;
        state.fleet.bandwidthCapacity = channelCapacity(mode);
    }

    private static int channelCapacity(ChannelMode mode) {
        return switch (mode == null ? ChannelMode.STANDARD : mode) {
            case STANDARD -> 8;
            case ENCRYPTED -> 6;
            case BURST_TRANSMISSION -> 4;
            case COURIER_DRONE -> 2;
        };
    }

    public static void isolateFlagship(State state) {
        if (state == null) return;
        for (CommandNode node : state.fleet.nodes) {
            if (node.type == NodeType.FLAGSHIP) node.operational = false;
        }
        state.fleet.networkCollapsed = true;
        state.fleet.cohesionPercent = Math.max(0, state.fleet.cohesionPercent - 24);
        state.fleet.isolationPenaltyPercent = Math.min(100, state.fleet.isolationPenaltyPercent + 18);
        state.fleet.panicPercent = Math.min(100, state.fleet.panicPercent + 14);
        state.fleet.exhaustedReserveRotation = state.fleet.networkCollapsed
                || state.fleet.cohesionPercent < 55
                || state.fleet.panicPercent >= 45;
    }

    public static void loseRelays(State state, int relaysLost) {
        if (state == null || relaysLost <= 0) return;
        FleetCommandState fleet = state.fleet;
        int remaining = relaysLost;
        for (CommandNode node : fleet.nodes) {
            if (remaining <= 0) break;
            if (node.type != NodeType.RELAY || !node.operational) continue;
            node.operational = false;
            remaining--;
        }
        int lost = relaysLost - remaining;
        if (lost <= 0) return;
        fleet.bandwidthCapacity = Math.max(1, fleet.bandwidthCapacity - lost * 2);
        fleet.cohesionPercent = Math.max(0, fleet.cohesionPercent - lost * 8);
        fleet.isolationPenaltyPercent = Math.min(100, fleet.isolationPenaltyPercent + lost * 7);
        fleet.panicPercent = Math.min(100, fleet.panicPercent + lost * 5);
        boolean flagshipOperational = fleet.nodes.stream()
                .anyMatch(node -> node.type == NodeType.FLAGSHIP && node.operational);
        boolean anyRelayOperational = fleet.nodes.stream()
                .anyMatch(node -> node.type == NodeType.RELAY && node.operational);
        fleet.networkCollapsed = !flagshipOperational && !anyRelayOperational;
        fleet.exhaustedReserveRotation = fleet.networkCollapsed || fleet.cohesionPercent < 55 || fleet.panicPercent >= 45;
    }

    public static boolean transferFlag(State state) {
        if (state == null) return false;
        for (CommandNode node : state.fleet.nodes) {
            if (node.type != NodeType.FALLBACK || !node.operational) continue;
            node.ship = node.ship + " (Acting Flag)";
            state.fleet.networkCollapsed = false;
            state.fleet.rallyActions++;
            state.fleet.cohesionPercent = Math.min(100, state.fleet.cohesionPercent + 14);
            state.fleet.panicPercent = Math.max(0, state.fleet.panicPercent - 12);
            state.fleet.isolationPenaltyPercent = Math.max(0, state.fleet.isolationPenaltyPercent - 10);
            state.fleet.exhaustedReserveRotation = state.fleet.networkCollapsed
                    || state.fleet.cohesionPercent < 55
                    || state.fleet.panicPercent >= 45;
            return true;
        }
        return false;
    }

    public static void applyAggressiveBurn(State state) {
        if (state == null) return;
        state.fleet.cohesionPercent = Math.max(0, state.fleet.cohesionPercent - 18);
        state.fleet.isolationPenaltyPercent = Math.min(100, state.fleet.isolationPenaltyPercent + 12);
        state.fleet.panicPercent = Math.min(100, state.fleet.panicPercent + 7);
        state.fleet.exhaustedReserveRotation = state.fleet.cohesionPercent < 55;
    }

    public static void reformFormation(State state, boolean veteranCrew) {
        if (state == null) return;
        state.fleet.cohesionPercent = Math.min(100, state.fleet.cohesionPercent + (veteranCrew ? 18 : 9));
        state.fleet.isolationPenaltyPercent = Math.max(0, state.fleet.isolationPenaltyPercent - 6);
        state.fleet.panicPercent = Math.max(0, state.fleet.panicPercent - (veteranCrew ? 10 : 5));
        state.fleet.exhaustedReserveRotation = state.fleet.networkCollapsed
                || state.fleet.cohesionPercent < 55
                || state.fleet.panicPercent >= 45;
    }

    public static List<String> commandBoardLines(State state) {
        if (state == null) return List.of("Fleet doctrine data unavailable.");
        FleetCommandState fleet = state.fleet;
        return List.of(
                "Release stretch claims de-scoped  |  Post-release roadmap " + state.stretch.postReleaseRoadmap.size()
                        + "  |  Extraction packs " + state.extractionPacks.size(),
                "Command nodes " + fleet.nodes.size() + "  |  Bandwidth " + fleet.bandwidthUsed + "/" + fleet.bandwidthCapacity
                        + "  |  Channel " + fleet.channelMode,
                "Cohesion " + fleet.cohesionPercent + "%  |  Discipline " + fleet.discipline
                        + "  |  Panic " + fleet.panicPercent + "%  |  Isolation " + fleet.isolationPenaltyPercent + "%",
                "Queued orders " + fleet.orderQueue.size() + "  |  Rally actions " + fleet.rallyActions
                        + "  |  Reserve rotation " + (fleet.exhaustedReserveRotation ? "strained" : "ready"),
                "Review " + fleet.preBattleReview,
                "Acknowledgment " + fleet.doctrineAcknowledgment
        );
    }

    public static String serialize(State state) {
        if (state == null) return "";
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        FleetCommandState fleet = state.fleet;
        StandingOrders orders = fleet.standingOrders;
        return fleet.channelMode + "," + fleet.bandwidthCapacity + "," + fleet.bandwidthUsed + "," + fleet.cohesionPercent
                + "," + fleet.discipline + "," + fleet.commandLinkOverlay + "," + fleet.exhaustedReserveRotation
                + "," + fleet.squadronCrossfireBonusPercent + "," + fleet.isolationPenaltyPercent + "," + fleet.panicPercent
                + "," + fleet.rallyActions + "," + fleet.networkCollapsed + "," + enc(encoder, fleet.doctrineAcknowledgment)
                + "|" + orders.retreatThresholdPercent + "," + orders.conserveAmmunition + "," + orders.rescueDisabledAllies
                + "," + orders.protectCivilianTraffic + "," + orders.pursueFleeingEnemies + "," + orders.acceptSurrender
                + "," + orders.scuttleCompromisedShips + "," + orders.preserveRareCapturedTechnology + "," + orders.template
                + "|" + enc(encoder, fleet.preBattleReview);
    }

    public static State restore(String raw, long seed) {
        State state = bootstrap(seed);
        if (raw == null || raw.isBlank()) return state;
        String[] parts = raw.split("\\|", -1);
        if (parts.length < 3) return state;
        String[] fleet = parts[0].split(",", -1);
        if (fleet.length >= 7) {
            state.fleet.channelMode = value(fleet[0], ChannelMode.STANDARD);
            state.fleet.bandwidthCapacity = Math.max(1, number(fleet[1], 8));
            state.fleet.bandwidthUsed = Math.max(0, number(fleet[2], 0));
            state.fleet.cohesionPercent = clamp(number(fleet[3], 82), 0, 100);
            state.fleet.discipline = value(fleet[4], Discipline.MILITARY);
            state.fleet.commandLinkOverlay = Boolean.parseBoolean(fleet[5]);
            state.fleet.exhaustedReserveRotation = Boolean.parseBoolean(fleet[6]);
            if (fleet.length >= 13) {
                state.fleet.squadronCrossfireBonusPercent = clamp(number(fleet[7], 12), 0, 100);
                state.fleet.isolationPenaltyPercent = clamp(number(fleet[8], 0), 0, 100);
                state.fleet.panicPercent = clamp(number(fleet[9], 0), 0, 100);
                state.fleet.rallyActions = Math.max(0, number(fleet[10], 0));
                state.fleet.networkCollapsed = Boolean.parseBoolean(fleet[11]);
                state.fleet.doctrineAcknowledgment = dec(fleet[12], state.fleet.doctrineAcknowledgment);
            }
        }
        String[] orders = parts[1].split(",", -1);
        if (orders.length >= 9) {
            StandingOrders standing = state.fleet.standingOrders;
            standing.retreatThresholdPercent = clamp(number(orders[0], 35), 0, 100);
            standing.conserveAmmunition = Boolean.parseBoolean(orders[1]);
            standing.rescueDisabledAllies = Boolean.parseBoolean(orders[2]);
            standing.protectCivilianTraffic = Boolean.parseBoolean(orders[3]);
            standing.pursueFleeingEnemies = Boolean.parseBoolean(orders[4]);
            standing.acceptSurrender = Boolean.parseBoolean(orders[5]);
            standing.scuttleCompromisedShips = Boolean.parseBoolean(orders[6]);
            standing.preserveRareCapturedTechnology = Boolean.parseBoolean(orders[7]);
            standing.template = value(orders[8], DoctrineTemplate.CONVOY_ESCORT);
        }
        state.fleet.preBattleReview = dec(parts[2], state.fleet.preBattleReview);
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

    private static <T extends Enum<T>> T value(String raw, T fallback) {
        try {
            return Enum.valueOf(fallback.getDeclaringClass(), raw);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
