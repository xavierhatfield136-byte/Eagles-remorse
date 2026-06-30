import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Data-driven campaign perspectives that reuse the authoritative campaign simulation. */
public final class AlternativeCampaignSystem {
    public static final class Definition {
        public final String id;
        public final String name;
        public final Faction commandFaction;
        public final String objective;
        public final String victoryCondition;
        public final int startingFuel;
        public final int startingSupplies;
        public final int startingAmmo;
        public final List<String> rules = new ArrayList<>();
        public final List<String> alliances = new ArrayList<>();
        public final List<String> mechanics = new ArrayList<>();
        public final List<String> tutorialSteps = new ArrayList<>();
        public String startDescription = "";
        public String commandFantasy = "";
        public String defeatCondition = "Command can no longer meet its strategic objective";
        public String extensionPoint = "standard-territorial-simulation";
        public long seedSalt;
        public boolean conquestAllowed = true;
        public int durationTicks;
        public int reinforcementLimit = 20;
        public boolean releaseReady;

        Definition(String id, String name, Faction commandFaction, String objective, String victoryCondition,
                   int startingFuel, int startingSupplies, int startingAmmo) {
            this.id = id;
            this.name = name;
            this.commandFaction = commandFaction;
            this.objective = objective;
            this.victoryCondition = victoryCondition;
            this.startingFuel = startingFuel;
            this.startingSupplies = startingSupplies;
            this.startingAmmo = startingAmmo;
        }
    }

    public static final class State {
        public final Map<String, Definition> definitions = new LinkedHashMap<>();
        public String activeCampaignId = "blue-liberation";
        public boolean started;
        public boolean defeated;
        public boolean victorious;
        public String saveSlotId = "campaign-blue-liberation-1";
        public long deterministicSeed;
        public int objectiveProgress;
        public int survivalScore;
        public int delayScore;
        public int evacuationScore;
        public int preservedForceScore;
        public int politicalOutcomeScore;
        public int campaignTicks;
        public int civiliansDelivered;
        public int sortiesLaunched;
        public int craftRemaining = 12;
        public int deckCapacity = 4;
        public int salvageRecovered;
        public int reinforcementsUsed;
    }

    private AlternativeCampaignSystem() {}

    public static State bootstrap() {
        State state = new State();
        Definition blue = add(state, "blue-liberation", "Blue Liberation", Faction.PLAYER, "Liberate Earth through the territorial front", "Earth integrated by the coalition", 120, 90, 110);
        configure(blue, "Command the established coalition campaign", "Begin in the Southern Shelter with Green support",
                List.of("territorial conquest", "coalition diplomacy", "fleet preservation"), List.of("TEAM_C", "BRIGHT_YELLOW"),
                List.of("Inspect the legal frontier", "Commit a supplied operation"));
        Definition red = add(state, "red-offensive", "Red Offensive", Faction.ENEMY, "Hold Sol and break the coalition", "Coalition front collapses", 130, 105, 125);
        configure(red, "Command a pressured military hierarchy defending Sol", "Begin with Sol industry, strict command pressure, and long logistics",
                List.of("Red command pressure", "requisitions", "interdiction", "fortified defense"), List.of("DARK_YELLOW"),
                List.of("Review homeland reserves", "Interdict a legal adjacent coalition route"));
        Definition bright = add(state, "bright-yellow", "Bright Yellow Restoration", Faction.BRIGHT_YELLOW, "Survive the civil war and restore legitimate Yellow government", "Bright Yellow victory or negotiated reunification", 105, 105, 95);
        configure(bright, "Hold a divided homeland while managing coalition obligations", "Begin on the Bright frontier with Blue and Green aid",
                List.of("legitimacy", "relief", "reunification", "negotiated settlement", "coalition obligation"), List.of("PLAYER", "TEAM_C"),
                List.of("Identify Dark Yellow contacts", "Open a relief route", "Review aid obligations"));
        Definition dark = add(state, "dark-yellow", "Dark Orange-Yellow Ascendancy", Faction.DARK_YELLOW, "Win independence from rivals and Red dependency", "Dark Yellow victory or independent settlement", 110, 85, 120);
        configure(dark, "Balance military survival against dependency on Red", "Begin in the lunar hold with Red supplies and political pressure",
                List.of("domination", "independence", "settlement", "political reversal", "Red dependency"), List.of("ENEMY"),
                List.of("Review Red aid terms", "Secure a Yellow supply source", "Choose dependency or autonomy"));
        Definition convoy = add(state, "civilian-convoy", "Civilian Convoy", Faction.TEAM_C, "Move civilians and trade through the living front", "Required population reaches sanctuary", 145, 125, 45);
        configure(convoy, "Keep civilians alive while the territorial war moves around them", "Begin with vulnerable transports and limited defensive escorts",
                List.of("routing", "trade", "rescue", "negotiation", "concealment", "limited defense", "no conquest objective"), List.of("PLAYER", "BRIGHT_YELLOW"),
                List.of("Plot a low-risk route", "Request an escort", "Practice emergency rescue"));
        convoy.conquestAllowed = false;
        Definition carrier = add(state, "carrier-task-force", "Carrier Task Force", Faction.PLAYER, "Control the front through scouting and sorties", "Strategic carrier objectives completed", 115, 95, 130);
        configure(carrier, "Command a carrier screen rather than individual craft", "Begin with finite deck capacity, pilots, fuel, and screening escorts",
                List.of("sortie planning", "craft attrition", "deck capacity", "scouting", "screening", "logistics", "automated routine launches"), List.of("TEAM_C"),
                List.of("Plan a reconnaissance sortie", "Set deck reserves", "Assign the screen"));
        Definition scavenger = add(state, "scavenger", "Scavenger Wake", Faction.ALLY, "Survive by recovering the material history of the war", "Recovery target met without faction collapse", 90, 75, 65);
        configure(scavenger, "Follow the war's wreckage without surrendering political neutrality", "Begin near a depleted wreck field with rival recovery crews",
                List.of("salvage claims", "hazardous wrecks", "war-history opportunities", "rival crews", "neutrality"), List.of(),
                List.of("Inspect a recorded battle site", "File a salvage claim", "Avoid a faction incident"));
        Definition lastStand = add(state, "last-stand", "Last Stand", Faction.PLAYER, "Delay the offensive and evacuate what can be saved", "Evacuation and delay thresholds achieved", 70, 60, 80);
        configure(lastStand, "Trade time and ships for lives in a short pressured defense", "Begin encircled with limited reinforcement and evacuation windows",
                List.of("limited reinforcement", "meaningful sacrifice", "survival", "delay", "evacuation", "preserved forces", "political outcome"), List.of("TEAM_C"),
                List.of("Set evacuation priorities", "Choose a delaying position", "Preserve an exit force"));
        lastStand.durationTicks = 120;
        lastStand.reinforcementLimit = 3;
        blue.releaseReady = true;
        bright.releaseReady = true;
        dark.releaseReady = true;
        return state;
    }

    private static Definition add(State state, String id, String name, Faction faction, String objective,
                            String victory, int fuel, int supplies, int ammo) {
        Definition definition = new Definition(id, name, faction, objective, victory, fuel, supplies, ammo);
        definition.seedSalt = id.hashCode();
        state.definitions.put(id, definition);
        return definition;
    }

    private static void configure(Definition definition, String fantasy, String start, List<String> mechanics,
                                  List<String> alliances, List<String> tutorial) {
        definition.commandFantasy = fantasy;
        definition.startDescription = start;
        definition.mechanics.addAll(mechanics);
        definition.alliances.addAll(alliances);
        definition.tutorialSteps.addAll(tutorial);
        definition.rules.addAll(List.of("Use canonical territory adjacency", "Use shared operation legality",
                "Use shared supply and control simulation", "Do not fork tactical combat rules"));
    }

    public static boolean start(GameContext ctx, State state, String campaignId) {
        if (ctx == null || ctx.campaign == null || state == null) return false;
        Definition definition = state.definitions.get(campaignId);
        if (definition == null) return false;
        state.activeCampaignId = definition.id;
        state.started = true;
        state.defeated = false;
        state.victorious = false;
        state.objectiveProgress = 0;
        state.deterministicSeed = (ctx.config == null ? 0L : ctx.config.seed) ^ definition.seedSalt;
        state.saveSlotId = "campaign-" + definition.id + "-1";
        ctx.campaign.campaignFuel = definition.startingFuel;
        ctx.campaign.campaignSupplies = definition.startingSupplies;
        ctx.campaign.campaignAmmo = definition.startingAmmo;
        ctx.campaign.branchRoute = definition.commandFaction.isYellowLineage() ? "YELLOW" : "BALANCED";
        if (definition.commandFaction == Faction.BRIGHT_YELLOW) {
            StrategicCampaignExpansionSystem.commitCivilWarAid(ctx.campaign.strategicExpansion, "BRIGHT_YELLOW", 20);
        } else if (definition.commandFaction == Faction.DARK_YELLOW) {
            StrategicCampaignExpansionSystem.commitCivilWarAid(ctx.campaign.strategicExpansion, "DARK_YELLOW", 20);
        }
        return true;
    }

    public static StrategicCampaignExpansionSystem.StrategicOperation startVariantOperation(
            GameContext ctx, State state, StrategicCampaignExpansionSystem.OperationType type,
            String originId, String targetId, String fleetId) {
        Definition definition = activeDefinition(state);
        if (ctx == null || ctx.campaign == null || definition == null || !state.started) return null;
        if (!definition.conquestAllowed && type == StrategicCampaignExpansionSystem.OperationType.INVASION) return null;
        StrategicCampaignExpansionSystem.StrategicOperation operation = StrategicCampaignExpansionSystem.startOperation(
                ctx.campaign.strategicExpansion, type, definition.commandFaction.name(), originId, targetId);
        if (operation != null) StrategicCampaignExpansionSystem.configureOperation(operation, definition.commandFaction.name(),
                definition.objective, fleetId, type == StrategicCampaignExpansionSystem.OperationType.INVASION ? 60 : 20,
                type == StrategicCampaignExpansionSystem.OperationType.INVASION ? 65 : 45, 4);
        return operation;
    }

    public static boolean planCarrierSortie(State state, int craftCommitted, boolean scouting, boolean screened) {
        if (state == null || !"carrier-task-force".equals(state.activeCampaignId) || craftCommitted <= 0
                || craftCommitted > state.deckCapacity || craftCommitted > state.craftRemaining) return false;
        state.sortiesLaunched++;
        int attrition = (!screened ? 1 : 0) + (!scouting ? 1 : 0);
        state.craftRemaining = Math.max(0, state.craftRemaining - attrition);
        return true;
    }

    public static boolean deliverCivilianConvoy(State state, int civilians, int routeRisk, boolean concealed,
                                                 boolean negotiatedPassage, boolean escorted) {
        if (state == null || !"civilian-convoy".equals(state.activeCampaignId) || civilians <= 0) return false;
        int effectiveRisk = Math.max(0, routeRisk - (concealed ? 20 : 0) - (negotiatedPassage ? 20 : 0) - (escorted ? 15 : 0));
        state.civiliansDelivered += Math.max(0, civilians * (100 - Math.min(100, effectiveRisk)) / 100);
        updateOutcome(state, Math.min(100, state.civiliansDelivered / 10), false);
        return true;
    }

    public static int recoverScavengerHistory(State state, WarMemorySystem.State memory, int maxSites) {
        if (state == null || memory == null || !"scavenger".equals(state.activeCampaignId)) return 0;
        int opportunities = Math.min(Math.max(0, maxSites), WarMemorySystem.historicalScenarioSeeds(memory).size());
        state.salvageRecovered += opportunities * 10;
        return opportunities;
    }

    public static boolean requestLastStandReinforcement(State state) {
        Definition definition = activeDefinition(state);
        if (state == null || definition == null || !"last-stand".equals(definition.id)
                || state.reinforcementsUsed >= definition.reinforcementLimit) return false;
        state.reinforcementsUsed++;
        return true;
    }

    public static void advanceVariant(State state) {
        Definition definition = activeDefinition(state);
        if (state == null || definition == null || !state.started || state.defeated || state.victorious) return;
        state.campaignTicks++;
        if (definition.durationTicks > 0 && state.campaignTicks >= definition.durationTicks) {
            scoreLastStand(state, state.survivalScore, state.delayScore + 100, state.evacuationScore,
                    state.preservedForceScore, state.politicalOutcomeScore);
        }
    }

    public static List<String> validateDefinitions(State state) {
        ArrayList<String> errors = new ArrayList<>();
        if (state == null) return List.of("campaign state missing");
        for (Definition definition : state.definitions.values()) {
            if (definition.id == null || definition.id.isBlank()) errors.add("blank campaign id");
            if (definition.commandFaction == null) errors.add(definition.id + " missing command faction");
            if (definition.startingFuel <= 0 || definition.startingSupplies <= 0 || definition.startingAmmo <= 0) errors.add(definition.id + " has nonviable resources");
            if (definition.objective.isBlank() || definition.victoryCondition.isBlank() || definition.defeatCondition.isBlank()) errors.add(definition.id + " missing outcome definition");
            if (definition.rules.isEmpty() || definition.tutorialSteps.isEmpty()) errors.add(definition.id + " missing rules/tutorial");
        }
        return List.copyOf(errors);
    }

    public static void updateOutcome(State state, int objectiveProgress, boolean commandDestroyed) {
        if (state == null || !state.started) return;
        state.objectiveProgress = MathUtil.clamp(objectiveProgress, 0, 100);
        state.defeated = commandDestroyed;
        state.victorious = !state.defeated && state.objectiveProgress >= 100;
    }

    public static void scoreLastStand(State state, int survival, int delay, int evacuation,
                                      int preservedForces, int politicalOutcome) {
        if (state == null || !"last-stand".equals(state.activeCampaignId)) return;
        state.survivalScore = Math.max(0, survival);
        state.delayScore = Math.max(0, delay);
        state.evacuationScore = Math.max(0, evacuation);
        state.preservedForceScore = Math.max(0, preservedForces);
        state.politicalOutcomeScore = Math.max(0, politicalOutcome);
        updateOutcome(state, Math.min(100, (survival + delay + evacuation + preservedForces + politicalOutcome) / 5), false);
    }

    public static Definition activeDefinition(State state) {
        return state == null ? null : state.definitions.get(state.activeCampaignId);
    }

    public static String serialize(State state) {
        if (state == null) return "";
        String id = Base64.getUrlEncoder().withoutPadding().encodeToString(state.activeCampaignId.getBytes(StandardCharsets.UTF_8));
        return id + "|" + state.started + "|" + state.defeated + "|" + state.victorious + "|"
                + enc(state.saveSlotId) + "|" + state.deterministicSeed + "|" + state.objectiveProgress + "|"
                + state.survivalScore + ":" + state.delayScore + ":" + state.evacuationScore + ":"
                + state.preservedForceScore + ":" + state.politicalOutcomeScore + "|"
                + state.campaignTicks + ":" + state.civiliansDelivered + ":" + state.sortiesLaunched + ":"
                + state.craftRemaining + ":" + state.deckCapacity + ":" + state.salvageRecovered + ":" + state.reinforcementsUsed;
    }

    public static State restore(String raw) {
        State state = bootstrap();
        if (raw == null || raw.isBlank()) return state;
        String[] p = raw.split("\\|", -1);
        if (p.length < 4) return state;
        try {
            String id = new String(Base64.getUrlDecoder().decode(p[0]), StandardCharsets.UTF_8);
            if (state.definitions.containsKey(id)) state.activeCampaignId = id;
        } catch (Exception ignored) { }
        state.started = Boolean.parseBoolean(p[1]);
        state.defeated = Boolean.parseBoolean(p[2]);
        state.victorious = Boolean.parseBoolean(p[3]);
        if (p.length >= 8) {
            state.saveSlotId = dec(p[4], "campaign-" + state.activeCampaignId + "-1");
            state.deterministicSeed = longValue(p[5], 0L);
            state.objectiveProgress = MathUtil.clamp(integer(p[6], 0), 0, 100);
            String[] scores = p[7].split(":", -1);
            if (scores.length >= 5) {
                state.survivalScore = Math.max(0, integer(scores[0], 0));
                state.delayScore = Math.max(0, integer(scores[1], 0));
                state.evacuationScore = Math.max(0, integer(scores[2], 0));
                state.preservedForceScore = Math.max(0, integer(scores[3], 0));
                state.politicalOutcomeScore = Math.max(0, integer(scores[4], 0));
            }
        }
        if (p.length >= 9) {
            String[] f = p[8].split(":", -1);
            if (f.length >= 7) {
                state.campaignTicks = integer(f[0], 0); state.civiliansDelivered = integer(f[1], 0);
                state.sortiesLaunched = integer(f[2], 0); state.craftRemaining = integer(f[3], 12);
                state.deckCapacity = integer(f[4], 4); state.salvageRecovered = integer(f[5], 0);
                state.reinforcementsUsed = integer(f[6], 0);
            }
        }
        return state;
    }

    public static List<String> summaryLines(State state) {
        Definition definition = activeDefinition(state);
        if (definition == null) return List.of("Campaign definition unavailable");
        return List.of(definition.name + "  |  command " + definition.commandFaction.teamName(),
                "Fantasy: " + definition.commandFantasy, "Start: " + definition.startDescription,
                "Objective: " + definition.objective, "Victory: " + definition.victoryCondition,
                "Mechanics: " + definition.mechanics, "Alliances: " + definition.alliances,
                "Save identity: " + state.saveSlotId + "  seed " + state.deterministicSeed);
    }

    private static String enc(String value) { return Base64.getUrlEncoder().withoutPadding().encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)); }
    private static String dec(String value, String fallback) { try { return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8); } catch (Exception ignored) { return fallback; } }
    private static int integer(String value, int fallback) { try { return Integer.parseInt(value); } catch (Exception ignored) { return fallback; } }
    private static long longValue(String value, long fallback) { try { return Long.parseLong(value); } catch (Exception ignored) { return fallback; } }
}
