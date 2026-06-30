import java.util.ArrayList;
import java.util.List;

/** Repeatable manual/CI acceptance scenario for adjacency-bound three-territory expansion. */
public final class TerritoryFrontAcceptanceHarness {
    private TerritoryFrontAcceptanceHarness() {}

    public static List<String> run() {
        StrategicCampaignExpansionSystem.State state = StrategicCampaignExpansionSystem.bootstrap(29062026L);
        ArrayList<String> evidence = new ArrayList<>();
        evidence.add(StrategicCampaignExpansionSystem.debugTerritoryLine(state, "frontier"));
        require(!StrategicCampaignExpansionSystem.isLegalInvasionTarget(state, "BRIGHT_YELLOW", "sol"),
                "Sol must not be legal before Lunar control");
        captureAndIntegrate(state, "BRIGHT_YELLOW", "frontier", "well");
        evidence.add(StrategicCampaignExpansionSystem.debugTerritoryLine(state, "well"));
        require(StrategicCampaignExpansionSystem.isLegalInvasionTarget(state, "BRIGHT_YELLOW", "sol"),
                "Sol must open after supplied Lunar integration");
        StrategicCampaignExpansionSystem.TravelLane lunarSol = state.lanes.stream()
                .filter(lane -> lane.from.equals("well") && lane.to.equals("sol"))
                .findFirst().orElseThrow();
        lunarSol.blockaded = true;
        require(!StrategicCampaignExpansionSystem.isLegalInvasionTarget(state, "BRIGHT_YELLOW", "sol"),
                "Blockading the connector must close Sol");
        evidence.add("PASS  |  frontier -> lunar integration opened Sol; blockade closed it again");
        return List.copyOf(evidence);
    }

    private static void captureAndIntegrate(StrategicCampaignExpansionSystem.State state, String faction,
                                            String origin, String target) {
        for (int i = 0; i < 2; i++) {
            StrategicCampaignExpansionSystem.StrategicOperation operation =
                    StrategicCampaignExpansionSystem.startOperation(state,
                            StrategicCampaignExpansionSystem.OperationType.INVASION, faction, origin, target);
            require(operation != null, "Expected legal invasion " + origin + " -> " + target);
            require(StrategicCampaignExpansionSystem.completeOperation(state, operation.id, true), "Operation completion failed");
        }
        StrategicCampaignExpansionSystem.Territory territory = StrategicCampaignExpansionSystem.territory(state, target);
        territory.supplyState = StrategicCampaignExpansionSystem.SupplyState.SUPPLIED;
        require(StrategicCampaignExpansionSystem.advanceOccupationIntegration(state, target, 100), "Integration failed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    public static void main(String[] args) {
        for (String line : run()) System.out.println(line);
    }
}
