import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignDifficultyRuntimeAuditTest {

    @Test
    void everyDifficultyPresetFieldNamesALiveRuntimeConsumer() {
        Set<String> fields = CampaignDifficultySystem.runtimeConsumers().stream()
                .map(CampaignDifficultySystem.RuntimeConsumer::field)
                .collect(Collectors.toSet());

        assertEquals(Set.of(
                "commandComplexity",
                "combatLethality",
                "strategicPressure",
                "attrition",
                "tacticalOnly",
                "commandOnly",
                "ironCommand"
        ), fields);

        String audit = String.join("\n", CampaignDifficultySystem.runtimeConsumerAuditLines());
        assertTrue(audit.contains("GameContext constructor"));
        assertTrue(audit.contains("CollisionSystem damage resolution"));
        assertTrue(audit.contains("CampaignSystem travel forecast and resource deduction"));
        assertTrue(audit.contains("ExperienceRuntime and GameplayActions"));
    }

    @Test
    void campaignSystemExposesTheDifficultyConsumerAuditForPauseAndDebugReadouts() {
        List<String> lines = CampaignSystem.campaignDifficultyRuntimeConsumerAuditLines();

        assertEquals(CampaignDifficultySystem.runtimeConsumerAuditLines(), lines);
        assertTrue(String.join("\n", lines).contains("strategicPressure"));
        assertTrue(String.join("\n", lines).contains("ironCommand"));
    }
}
