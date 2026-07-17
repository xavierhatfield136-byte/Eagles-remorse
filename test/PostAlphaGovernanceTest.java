import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostAlphaGovernanceTest {
    @Test
    void publicConfigurationKeepsTrackBPrototypeFlagsDisabled() {
        assertTrue(PostAlphaFeatureFlags.enabled(PostAlphaFeatureFlags.Feature.TERRITORY_FRONTS));
        assertTrue(PostAlphaFeatureFlags.enabled(PostAlphaFeatureFlags.Feature.YELLOW_SPLIT));
        assertFalse(PostAlphaFeatureFlags.enabled(PostAlphaFeatureFlags.Feature.WAR_MEMORY));
        assertFalse(PostAlphaFeatureFlags.enabled(PostAlphaFeatureFlags.Feature.RIVAL_COMMANDERS));
        assertFalse(PostAlphaFeatureFlags.enabled(PostAlphaFeatureFlags.Feature.FLAGSHIP_OPERATIONS));
        assertFalse(PostAlphaFeatureFlags.enabled(PostAlphaFeatureFlags.Feature.BOARDING_RESCUE));
        assertFalse(PostAlphaFeatureFlags.enabled(PostAlphaFeatureFlags.Feature.ALTERNATIVE_CAMPAIGNS));
        assertFalse(PostAlphaFeatureFlags.enabled(PostAlphaFeatureFlags.Feature.COOPERATIVE_COMMAND_PROTOTYPE));
        assertFalse(PostAlphaFeatureFlags.enabled(PostAlphaFeatureFlags.Feature.MULTIPLAYER_CUSTOM_BATTLE));
    }

    @Test
    void governanceAndTraceabilityArtifactsExist() {
        assertTrue(Files.isRegularFile(Path.of("docs", "POST_ALPHA_ARCHITECTURE_AND_GOVERNANCE.md")));
        assertTrue(Files.isRegularFile(Path.of("docs", "POST_ALPHA_IMPLEMENTATION_EVIDENCE_2026-06-29.md")));
    }
}
