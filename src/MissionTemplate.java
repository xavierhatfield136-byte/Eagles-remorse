import java.util.List;

public record MissionTemplate(int worldW,
                              int worldH,
                              List<MissionSlotSpec> rosterTemplate,
                              String objectiveType,
                              MultiplayerRulesV1.VictoryRule victoryRule,
                              String seedPolicy) {
    public MissionTemplate {
        worldW = clampWorldSize(worldW);
        worldH = clampWorldSize(worldH);
        rosterTemplate = rosterTemplate == null ? List.of() : List.copyOf(rosterTemplate);
        objectiveType = clean(objectiveType, "elimination");
        if (victoryRule == null) victoryRule = MultiplayerRulesV1.VictoryRule.ELIMINATION;
        seedPolicy = clean(seedPolicy, "locked");
    }

    private static int clampWorldSize(int value) {
        return Math.max(1800, Math.min(60000, value));
    }

    private static String clean(String value, String fallback) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}
