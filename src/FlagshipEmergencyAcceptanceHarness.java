/** Deterministic manual/automated acceptance scenario for simultaneous flagship emergencies. */
public final class FlagshipEmergencyAcceptanceHarness {
    public record Report(boolean passed, double fireBefore, double fireAfter, int casualties,
                         int repairPartsSpent, String summary) {}
    private FlagshipEmergencyAcceptanceHarness() {}

    public static Report run(Ship ship) {
        FlagshipOperationsSystem.State state = FlagshipOperationsSystem.bootstrap();
        FlagshipOperationsSystem.syncFromShip(state, ship);
        if (state.compartments.isEmpty()) return new Report(false, 0, 0, 0, 0, "No schematic rooms");
        String first = state.compartments.keySet().iterator().next();
        String second = state.compartments.keySet().stream().skip(1).findFirst().orElse(first);
        FlagshipOperationsSystem.setEmergency(state, first, 1.8, true, true);
        FlagshipOperationsSystem.setHazards(state, first, 1.2, 0.9, 0.7);
        FlagshipOperationsSystem.setEmergency(state, second, 0.7, false, true);
        state.powerGeneration = 35;
        state.medicalCapacity = 1;
        state.compartments.get(first).injuries = 8;
        double fireBefore = state.compartments.get(first).fire;
        FlagshipOperationsSystem.assignTeam(state, "dc-1", first, FlagshipOperationsSystem.TeamOrder.CONTAIN_FIRE);
        FlagshipOperationsSystem.assignTeam(state, "dc-2", first, FlagshipOperationsSystem.TeamOrder.EVACUATE);
        FlagshipOperationsSystem.assignTeam(state, "dc-3", second, FlagshipOperationsSystem.TeamOrder.RESTORE_SYSTEM);
        for (int i = 0; i < 20; i++) {
            FlagshipOperationsSystem.update(state, 0.5);
            FlagshipOperationsSystem.applyToShip(state, ship);
        }
        double fireAfter = state.compartments.get(first).fire;
        int partsBefore = state.repairParts;
        int repairSpent = FlagshipOperationsSystem.reconcileCampaignRepairs(state, ship, 10);
        boolean passed = fireAfter < fireBefore && state.casualties >= 8 && repairSpent > 0
                && state.repairParts == partsBefore - repairSpent
                && !FlagshipOperationsSystem.criticalWarnings(state).isEmpty();
        return new Report(passed, fireBefore, fireAfter, state.casualties, repairSpent,
                "fire " + fireBefore + "->" + fireAfter + ", casualties " + state.casualties
                        + ", campaign repair parts " + repairSpent);
    }
}
