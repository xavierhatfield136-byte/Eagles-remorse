import java.util.List;

public record MissionLaunchSpec(String missionId,
                                int missionRevision,
                                long seed,
                                int worldW,
                                int worldH,
                                List<MissionSlotSpec> resolvedRosters,
                                List<MissionSlotSpec> playerSlots,
                                String rulesProfileId,
                                String objectiveType,
                                MultiplayerRulesV1.VictoryRule victoryRule) {
    public MissionLaunchSpec(String missionId,
                             int missionRevision,
                             long seed,
                             int worldW,
                             int worldH,
                             List<MissionSlotSpec> resolvedRosters,
                             List<MissionSlotSpec> playerSlots,
                             String rulesProfileId) {
        this(missionId, missionRevision, seed, worldW, worldH, resolvedRosters, playerSlots, rulesProfileId,
                "elimination", MultiplayerRulesV1.VictoryRule.ELIMINATION);
    }

    public MissionLaunchSpec {
        missionId = clean(missionId, "core:unknown");
        missionRevision = Math.max(1, missionRevision);
        resolvedRosters = resolvedRosters == null ? List.of() : List.copyOf(resolvedRosters);
        playerSlots = playerSlots == null ? List.of() : List.copyOf(playerSlots);
        rulesProfileId = clean(rulesProfileId, "multiplayer:v1");
        objectiveType = clean(objectiveType, "elimination");
        if (victoryRule == null) victoryRule = MultiplayerRulesV1.VictoryRule.ELIMINATION;
    }

    private static String clean(String value, String fallback) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}
