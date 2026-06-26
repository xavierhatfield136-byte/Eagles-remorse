import app.config.ExperienceSettings;
import app.config.GameConfig;
import app.config.GameMode;
import app.state.PerfTelemetry;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignPhaseNinePerformanceScaleTest {
    @Test
    void battleScaleContractIsFiniteReadableAndTargeted() {
        assertTrue(PerformanceBattleScale.validationErrors().isEmpty(),
                String.join("; ", PerformanceBattleScale.validationErrors()));

        assertEquals(14, PerformanceBattleScale.ORDINARY_SHIPS_PER_SIDE);
        assertEquals(100, PerformanceBattleScale.MAJOR_SHIPS_PER_SIDE);
        assertEquals(50, PerformanceBattleScale.LARGEST_SUPPORTED_SHIPS_PER_SIDE);
        assertEquals(100, PerformanceBattleScale.LARGEST_SUPPORTED_TOTAL_SHIPS);
        assertEquals(160, PerformanceBattleScale.STRESS_TEST_SHIPS_PER_SIDE);
        assertEquals(320, PerformanceBattleScale.STRESS_TEST_TOTAL_SHIPS);
        assertTrue(PerformanceBattleScale.PLAYER_FACING_READABLE_ONSCREEN_SHIPS
                < PerformanceBattleScale.STRESS_TEST_TOTAL_SHIPS);
        assertEquals(60, PerformanceBattleScale.ORDINARY_TARGET_FPS);
        assertEquals(30, PerformanceBattleScale.LARGEST_SUPPORTED_FPS_FLOOR);

        String contract = String.join("\n", PerformanceBattleScale.contractLines());
        assertTrue(contract.contains("No unlimited promise"));
        assertTrue(contract.contains("1280x720"));
        assertTrue(contract.contains("1920x1080"));
        assertTrue(contract.contains("Tactical FPS View"));
    }

    @Test
    void stressScenarioCoverageMatchesChecklist() {
        assertEquals(100, PerformanceBattleScale.Scenario.MAJOR_100_PER_SIDE.shipsPerSide);
        assertEquals(50, PerformanceBattleScale.Scenario.LARGEST_SUPPORTED.shipsPerSide);
        assertEquals(160, PerformanceBattleScale.Scenario.STRESS_160_PER_SIDE.shipsPerSide);
        assertTrue(PerformanceBattleScale.Scenario.CAPITAL_HEAVY.capitalShipsTotal >= 30);
        assertTrue(PerformanceBattleScale.Scenario.TITAN_HEAVY.titanShipsTotal >= 4);
        assertTrue(PerformanceBattleScale.Scenario.MISSILE_HEAVY.projectileTarget
                >= PerformanceBattleScale.PROJECTILE_PRESSURE_TARGET);
        assertTrue(PerformanceBattleScale.Scenario.WRECK_HEAVY.wreckTarget
                >= PerformanceBattleScale.WRECK_PRESSURE_TARGET);
        assertEquals(PerformanceBattleScale.Scenario.REPEATED_TACTICAL_ENTRIES,
                PerformanceBattleScale.scenario("repeated-tactical-entries"));
        assertEquals(PerformanceBattleScale.Scenario.LONG_STRATEGIC_CAMPAIGN,
                PerformanceBattleScale.scenario("long-strategic-campaign"));
        assertEquals(PerformanceBattleScale.Scenario.LARGE_CAMPAIGN_SAVE_LOAD,
                PerformanceBattleScale.scenario("large-campaign-save-load"));
    }

    @Test
    void visualDetailSettingCanExplicitlyBoundWork() {
        PerformanceGuardrails.resetForTest();
        ExperienceSettings settings = ExperienceSettings.defaults();
        settings.visualDetail = ExperienceSettings.VisualDetail.LOW;
        GameContext ctx = new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 5000, 5000, true, 9L, false)
                .withExperience(settings));
        ctx.perf.frameMs = 8.0;
        ctx.perf.renderMs = 8.0;

        PerformanceGuardrails.update(ctx);

        assertEquals(ExperienceSettings.VisualDetail.LOW, PerformanceGuardrails.requestedVisualDetail());
        assertEquals(PerformanceGuardrails.VisualQuality.LOW, PerformanceGuardrails.quality());
        assertTrue(ctx.perf.visualQuality.contains("MANUAL"));
        assertTrue(PerformanceGuardrails.projectileTrailStride() >= 3);
    }

    @Test
    void adaptiveGuardrailStillDegradesUnderSustainedPressure() {
        PerformanceGuardrails.resetForTest();
        PerfTelemetry perf = new PerfTelemetry();
        perf.frameMs = 28.0;
        perf.renderMs = 24.0;

        for (int i = 0; i < 100; i++) PerformanceGuardrails.update(perf);

        assertTrue(PerformanceGuardrails.quality().ordinal()
                >= PerformanceGuardrails.VisualQuality.MEDIUM.ordinal());
        assertTrue(perf.visualQuality.contains("AUTO"));
    }

    @Test
    void guardrailHarnessWritesPhaseNineMeasurementReport() throws Exception {
        Path report = Path.of("build", "test-results", "phase9-harness-test.json");
        Files.deleteIfExists(report);

        PerformanceGuardrailHarness.main(new String[]{
                "--ticks=60",
                "--scenario=ordinary",
                "--viewport=1280x720",
                "--visual-detail=low",
                "--report=" + report
        });

        assertTrue(Files.exists(report));
        String json = Files.readString(report);
        assertTrue(json.contains("\"scenario\": \"ordinary\""));
        assertTrue(json.contains("\"viewport\": \"1280x720\""));
        assertTrue(json.contains("\"avgUpdateMs\""));
        assertTrue(json.contains("\"avgRenderMs\""));
        assertTrue(json.contains("\"avgAiMs\""));
        assertTrue(json.contains("\"avgProjectilePhysicsMs\""));
        assertTrue(json.contains("\"avgCampaignMapMs\""));
        assertTrue(json.contains("\"peakHeapMb\""));
        assertTrue(json.contains("\"gcCollections\""));
        assertTrue(json.contains("\"pass\": true"));
        assertFalse(json.contains("\"frameDecodes\": 1"));
    }
}
