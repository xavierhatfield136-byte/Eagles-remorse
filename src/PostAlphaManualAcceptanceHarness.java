import java.util.ArrayList;
import java.util.List;

/** Repeatable acceptance scripts whose reports can be captured alongside a manual playthrough. */
public final class PostAlphaManualAcceptanceHarness {
    public record OutcomeEvidence(StrategicCampaignExpansionSystem.CivilWarOutcome expected,
                                  StrategicCampaignExpansionSystem.CivilWarOutcome actual,
                                  boolean persisted, String consequence) {}
    public record RivalEvidence(boolean survived, int encounters, int adaptationLevel,
                                String countermeasure, List<String> memories) {}
    private PostAlphaManualAcceptanceHarness() {}

    public static List<OutcomeEvidence> civilWarOutcomeFamilies() {
        ArrayList<OutcomeEvidence> evidence = new ArrayList<>();
        for (StrategicCampaignExpansionSystem.CivilWarOutcome outcome
                : StrategicCampaignExpansionSystem.CivilWarOutcome.values()) {
            if (outcome == StrategicCampaignExpansionSystem.CivilWarOutcome.ONGOING) continue;
            StrategicCampaignExpansionSystem.State state = StrategicCampaignExpansionSystem.bootstrap(10_000L + outcome.ordinal());
            configureOutcome(state, outcome);
            StrategicCampaignExpansionSystem.CivilWarOutcome actual =
                    StrategicCampaignExpansionSystem.evaluateCivilWarOutcome(state);
            StrategicCampaignExpansionSystem.State restored = StrategicCampaignExpansionSystem.restore(
                    StrategicCampaignExpansionSystem.serialize(state), 10_000L + outcome.ordinal());
            evidence.add(new OutcomeEvidence(outcome, actual, restored.civilWarOutcome == actual,
                    StrategicCampaignExpansionSystem.civilWarResolution(restored).endingConsequence()));
        }
        return List.copyOf(evidence);
    }

    private static void configureOutcome(StrategicCampaignExpansionSystem.State state,
                                         StrategicCampaignExpansionSystem.CivilWarOutcome outcome) {
        List<StrategicCampaignExpansionSystem.Territory> homelands = state.territories.stream()
                .filter(t -> t.yellowHomeland).toList();
        switch (outcome) {
            case BRIGHT_YELLOW_VICTORY -> homelands.forEach(t -> t.controller = "BRIGHT_YELLOW");
            case DARK_YELLOW_VICTORY -> homelands.forEach(t -> t.controller = "DARK_YELLOW");
            case NEGOTIATED_SETTLEMENT -> state.civilWarCeasefire = true;
            case PARTITION -> state.civilWarElapsedTicks = 100;
            case MUTUAL_COLLAPSE -> { state.brightYellowExhaustion = 95; state.darkYellowExhaustion = 95; }
            case BRIGHT_COALITION_PROTECTORATE -> state.brightPoliticalObligation = 85;
            case DARK_RED_PROTECTORATE -> state.darkPoliticalObligation = 85;
            case FOREIGN_OCCUPATION -> homelands.forEach(t -> t.controller = "ENEMY");
            case ONGOING -> { }
        }
    }

    public static RivalEvidence rivalThreeEncounterScenario() {
        StrategicCampaignExpansionSystem.State state = StrategicCampaignExpansionSystem.bootstrap(10_100L);
        StrategicCampaignExpansionSystem.RivalCommander rival = state.commanders.values().stream().findFirst().orElseThrow();
        StrategicCampaignExpansionSystem.recordCommanderEncounter(state, rival.id, true, true, "missile saturation");
        StrategicCampaignExpansionSystem.changeCommanderStatus(state, rival.id,
                StrategicCampaignExpansionSystem.CommanderStatus.ACTIVE, "returned after repairs");
        StrategicCampaignExpansionSystem.recordCommanderEncounter(state, rival.id, false, true, "missile saturation");
        StrategicCampaignExpansionSystem.changeCommanderStatus(state, rival.id,
                StrategicCampaignExpansionSystem.CommanderStatus.ACTIVE, "reassigned with counter-screen");
        StrategicCampaignExpansionSystem.recordCommanderEncounter(state, rival.id, true, false, "missile saturation");
        StrategicCampaignExpansionSystem.State restored = StrategicCampaignExpansionSystem.restore(
                StrategicCampaignExpansionSystem.serialize(state), 10_100L);
        StrategicCampaignExpansionSystem.RivalCommander saved = restored.commanders.get(rival.id);
        return new RivalEvidence(saved.status != StrategicCampaignExpansionSystem.CommanderStatus.DEAD,
                saved.encountersWithPlayer, saved.adaptationLevel, saved.currentCountermeasure,
                List.copyOf(saved.encounterMemories));
    }
}
