import java.util.ArrayList;
import java.util.List;

/** Executable Phase 6 architecture inventory for owner-playtest stabilization extractions. */
public final class PhaseSixArchitectureAuditSystem {
    public record ExtractionRecord(String module,
                                   String activeSlice,
                                   String responsibility,
                                   String authorityImpact,
                                   List<String> publicCallSites,
                                   List<String> characterizationTests) {
        public ExtractionRecord {
            publicCallSites = List.copyOf(publicCallSites == null ? List.of() : publicCallSites);
            characterizationTests = List.copyOf(characterizationTests == null ? List.of() : characterizationTests);
        }

        public boolean namesActiveSlice() {
            return activeSlice != null && !activeSlice.isBlank();
        }

        public boolean hasCharacterizationCoverage() {
            return !characterizationTests.isEmpty();
        }

        public boolean introducesParallelAuthority() {
            String lower = authorityImpact == null ? "" : authorityImpact.toLowerCase(java.util.Locale.US);
            return lower.contains("parallel authority") || lower.contains("second authority");
        }
    }

    public record DeferredExtraction(String target,
                                     String reason,
                                     String requiredProtectionBeforeMove) {}

    private PhaseSixArchitectureAuditSystem() {}

    public static List<ExtractionRecord> completedStabilizationExtractions() {
        return List.of(
                new ExtractionRecord(
                        "CampaignEconomySystem",
                        "Phase 3 / Vertical Slice 3 resource-legibility work",
                        "Authoritative strategic economy ledger/readout for credits, ore, fuel, supplies, ammo, and repair materials.",
                        "Read-only/resource presentation adapter; CampaignSystem remains the mutating campaign authority and EconomySystem remains tactical mining authority.",
                        List.of(
                                "CampaignSystem.campaignAuthoritativeEconomyLedgerLines",
                                "CampaignSystem.campaignResourceManagerLines",
                                "CampaignMapPresentationModel.resources"),
                        List.of(
                                "OwnerPlaytestVerticalSliceThreeTest",
                                "CampaignEconomyBalanceAuditTest")),
                new ExtractionRecord(
                        "CampaignDifficultySystem",
                        "Phase 4 difficulty visibility and Phase 6 bloat control",
                        "Difficulty modifier presentation, telemetry wording, and runtime-consumer audit.",
                        "Read-only difficulty presentation; GameContext/ExperienceSettings/CampaignSystem remain runtime authorities.",
                        List.of(
                                "CampaignSystem.campaignDifficultyTelemetryLines",
                                "CampaignSystem.campaignDifficultyModifierLines",
                                "CampaignSystem.campaignDifficultyRuntimeConsumerAuditLines"),
                        List.of(
                                "CampaignDifficultyRuntimeAuditTest",
                                "CampaignDifficultyOutcomeSeparationTest")),
                new ExtractionRecord(
                        "CampaignArcSummarySystem",
                        "Phase 4 campaign arc/objective/ending clarity",
                        "Campaign phase identity, main-objective guidance, preset summaries, and choice-aware ending summaries.",
                        "Read-only summary layer; Earth readiness still comes from CampaignSystem.campaignFinalBattleReadiness.",
                        List.of(
                                "OwnerPlaytestPhaseFourCampaignArcAuditTest",
                                "PhaseFiveTacticalCleanupSystem.strategicTopFoldLines"),
                        List.of(
                                "OwnerPlaytestPhaseFourCampaignArcAuditTest")),
                new ExtractionRecord(
                        "CampaignMapPresentationModel",
                        "Phase 6 read-only map presentation boundary",
                        "Immutable sidebar/resource projection consumed by Renderer instead of rebuilding those panels inline.",
                        "Read-only projection; it exposes copied lists and does not mutate fleet, resource, operation, or contact state.",
                        List.of(
                                "Renderer.drawGalaxyCommandSidebar",
                                "Renderer.drawCampaignResourceBoard"),
                        List.of(
                                "CampaignStrategicUiReadabilityTest")),
                new ExtractionRecord(
                        "PhaseFiveTacticalCleanupSystem",
                        "Phase 5 tactical/control/UI presentation cleanup",
                        "Reserve-control readouts/actions, top-hint preference, crew automation explanation, role-balance measurements, and art baseline contracts.",
                        "Mostly read-only presentation; deploy/recall toggles only the existing reserve-request state and does not spawn a second reinforcement authority.",
                        List.of(
                                "OwnerPlaytestPhaseFiveTacticalCleanupAuditTest",
                                "PhaseFiveTacticalCleanupSystem.strategicTopFoldLines"),
                        List.of(
                                "OwnerPlaytestPhaseFiveTacticalCleanupAuditTest",
                                "AISystemSmallCraftRangeTest",
                                "AISystemEscortFormationTest",
                                "TitanGeometryRegressionTest"))
        );
    }

    public static List<DeferredExtraction> deferredBroadExtractions() {
        return List.of(
                new DeferredExtraction(
                        "CampaignFleetSimulationSystem",
                        "Fleet lifecycle is a live authority path with persistence, intel, search groups, and operation membership.",
                        "Fleet identity/save-load/intel/operation characterization plus a small adapter seam for one lifecycle transition."),
                new DeferredExtraction(
                        "CampaignIntelSystem",
                        "Contact precision, stale intel, strike gates, and checkpoint persistence are intertwined with campaign force state.",
                        "Public call-site inventory, save-schema fixture, and paired stale/exact contact tests."),
                new DeferredExtraction(
                        "CampaignOperationSystem / CampaignTerritorySystem",
                        "Operation legality and territory ownership must retain one authority path; a partial split risks fake captures.",
                        "Operation lifecycle tests from muster through legal outcome plus no-transfer-without-cause tests."),
                new DeferredExtraction(
                        "CampaignTravelSystem / CampaignStrikeSystem",
                        "Travel, posture, route risk, strike preflight, and consequence logic are player-facing balance surfaces.",
                        "Route/posture/strike characterization tests and save/load inventory checks."),
                new DeferredExtraction(
                        "Renderer / AISystem / Ship decomposition",
                        "These are broad renderer, tactical steering, and serialized identity refactors rather than one-slice stabilization work.",
                        "Screenshot baselines, tactical deterministic scenarios, and explicit serialized identity compatibility tests.")
        );
    }

    public static List<String> guardrailLines() {
        ArrayList<String> out = new ArrayList<>();
        out.add("Scope Freeze: no broad decomposition while earlier release-blocking phases remain incomplete.");
        out.add("Extraction Rule: every stabilization-time extraction must name the active slice it enables.");
        out.add("No Cleanup-Only Moves: defer aesthetic renames, package reshuffles, and arbitrary partial classes until owner acceptance.");
        out.add("Authority Rule: do not introduce parallel fleet, operation, territory, resource, or contact authorities.");
        out.add("Projection Rule: rendering/UI projection modules are read-only and return immutable/copy-backed views.");
        out.add("Compatibility Rule: save formats, stable IDs, enum names, and serialized field semantics stay compatible.");
        out.add("Step Rule: each extraction batch gets a compile-and-targeted-test gate before further movement.");
        return out;
    }

    public static List<String> validationErrors() {
        ArrayList<String> errors = new ArrayList<>();
        for (ExtractionRecord record : completedStabilizationExtractions()) {
            if (!record.namesActiveSlice()) errors.add(record.module + " missing active slice");
            if (record.publicCallSites.isEmpty()) errors.add(record.module + " missing public/static call-site inventory");
            if (!record.hasCharacterizationCoverage()) errors.add(record.module + " missing characterization tests");
            if (record.introducesParallelAuthority()) errors.add(record.module + " reports parallel authority risk");
        }
        for (String line : guardrailLines()) {
            if (line == null || line.isBlank()) errors.add("blank guardrail line");
        }
        return List.copyOf(errors);
    }
}
