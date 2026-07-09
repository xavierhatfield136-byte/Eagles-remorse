import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerPlaytestPhaseSixArchitectureAuditTest {
    @Test
    void completedExtractionsNameSliceCallSitesAuthorityImpactAndCharacterizationTests() {
        List<PhaseSixArchitectureAuditSystem.ExtractionRecord> records =
                PhaseSixArchitectureAuditSystem.completedStabilizationExtractions();

        assertEquals(List.of(), PhaseSixArchitectureAuditSystem.validationErrors());
        assertTrue(records.size() >= 5);

        Set<String> modules = records.stream()
                .map(PhaseSixArchitectureAuditSystem.ExtractionRecord::module)
                .collect(Collectors.toSet());
        assertTrue(modules.contains("CampaignEconomySystem"));
        assertTrue(modules.contains("CampaignDifficultySystem"));
        assertTrue(modules.contains("CampaignArcSummarySystem"));
        assertTrue(modules.contains("CampaignMapPresentationModel"));
        assertTrue(modules.contains("PhaseFiveTacticalCleanupSystem"));

        for (PhaseSixArchitectureAuditSystem.ExtractionRecord record : records) {
            assertTrue(record.namesActiveSlice(), record.module());
            assertFalse(record.publicCallSites().isEmpty(), record.module());
            assertTrue(record.hasCharacterizationCoverage(), record.module());
            assertFalse(record.introducesParallelAuthority(), record.module());
        }
    }

    @Test
    void broadAuthorityExtractionsRemainExplicitlyDeferredUntilProtected() {
        List<PhaseSixArchitectureAuditSystem.DeferredExtraction> deferred =
                PhaseSixArchitectureAuditSystem.deferredBroadExtractions();
        String joined = deferred.stream()
                .map(item -> item.target() + " " + item.reason() + " " + item.requiredProtectionBeforeMove())
                .collect(Collectors.joining("\n"));

        assertTrue(joined.contains("CampaignFleetSimulationSystem"));
        assertTrue(joined.contains("CampaignIntelSystem"));
        assertTrue(joined.contains("CampaignOperationSystem"));
        assertTrue(joined.contains("CampaignTravelSystem"));
        assertTrue(joined.contains("Renderer / AISystem / Ship"));
        assertTrue(joined.contains("one authority path"));
        assertTrue(joined.contains("save-load"));
        assertTrue(joined.contains("Screenshot baselines"));
    }

    @Test
    void architectureGuardrailsForPhaseSixAreExecutableAndSpecific() {
        String guardrails = String.join("\n", PhaseSixArchitectureAuditSystem.guardrailLines());

        assertTrue(guardrails.contains("Scope Freeze"));
        assertTrue(guardrails.contains("active slice"));
        assertTrue(guardrails.contains("No Cleanup-Only Moves"));
        assertTrue(guardrails.contains("parallel fleet, operation, territory, resource, or contact authorities"));
        assertTrue(guardrails.contains("read-only"));
        assertTrue(guardrails.contains("save formats"));
        assertTrue(guardrails.contains("compile-and-targeted-test"));
    }

    @Test
    void readOnlyPresentationBoundaryIsUsedByRendererAndDoesNotOwnMutableAuthority() throws Exception {
        String model = Files.readString(Path.of("src", "CampaignMapPresentationModel.java"));
        String renderer = Files.readString(Path.of("src", "Renderer.java"));

        assertTrue(renderer.contains("CampaignMapPresentationModel.sidebar(ctx)"));
        assertTrue(renderer.contains("CampaignMapPresentationModel.resources(ctx)"));
        assertTrue(model.contains("List.copyOf"));
        assertFalse(model.contains("campaignForces.add"));
        assertFalse(model.contains("galaxySearchGroups.add"));
        assertFalse(model.contains("campaignFuel ="));
        assertFalse(model.contains("ownerFaction ="));
    }

    @Test
    void oversizedFileInventoryStillIdentifiesRemainingHighRiskSystems() throws Exception {
        assertTrue(lineCount(Path.of("src", "CampaignSystem.java")) > 40_000);
        assertTrue(lineCount(Path.of("src", "Renderer.java")) > 10_000);
        assertTrue(lineCount(Path.of("src", "Ship.java")) > 5_000);
        assertTrue(lineCount(Path.of("src", "AISystem.java")) > 4_000);
        assertTrue(Files.exists(Path.of("src", "CampaignEconomySystem.java")));
        assertTrue(Files.exists(Path.of("src", "CampaignDifficultySystem.java")));
        assertTrue(Files.exists(Path.of("src", "CampaignArcSummarySystem.java")));
        assertTrue(Files.exists(Path.of("src", "PhaseFiveTacticalCleanupSystem.java")));
    }

    private static long lineCount(Path path) throws Exception {
        try (java.util.stream.Stream<String> lines = Files.lines(path)) {
            return lines.count();
        }
    }
}
