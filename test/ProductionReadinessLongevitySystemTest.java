import app.config.GameConfig;
import app.config.GameMode;
import app.persistence.CampaignCheckpointStore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionReadinessLongevitySystemTest {
    @Test
    void presentationPipelineDeclaresVisualAndAudioCoverage() {
        ProductionReadinessLongevitySystem.State state = ProductionReadinessLongevitySystem.bootstrap(91L);

        assertTrue(state.art.factionHullSkins && state.art.damageStages && state.art.destroyedMultipartVariants);
        assertTrue(state.art.factionTurretSkins && state.art.enginePlumes && state.art.shieldImpacts);
        assertTrue(state.art.factionMissileTrails && state.art.stationModules && state.art.environmentalProps);
        assertTrue(state.art.officerPortraitSets && state.art.zoomReadableMapIcons);
        assertFalse(state.art.uiGuidelines.isBlank());
        assertTrue(state.art.regressionScreenshots.size() >= 5);

        assertTrue(state.audio.factionWeaponIdentities && state.audio.layeredEngines && state.audio.differentiatedImpacts);
        assertTrue(state.audio.stationAmbience && state.audio.mapAmbience && state.audio.battleIntensityMusic);
        assertTrue(state.audio.lowResourceWarnings && state.audio.incomingStrikeWarnings && state.audio.jammingRadioDistortion);
        assertTrue(state.audio.voiceCooldownPriority && state.audio.dynamicAlertDucking && state.audio.accessibilityCaptions);
    }

    @Test
    void longevityModelTracksSlotsRotationRecoverySeedsReplayStatsAndMods() {
        ProductionReadinessLongevitySystem.State state = ProductionReadinessLongevitySystem.bootstrap(92L);

        assertTrue(state.longevity.slots.size() >= 3);
        assertTrue(state.longevity.autosaveRotation >= 3);
        assertFalse(state.longevity.migrationFixtures.isEmpty());
        assertFalse(state.longevity.battleReplayFiles.isEmpty());
        assertFalse(state.longevity.campaignEventLog.isEmpty());
        assertFalse(state.longevity.postCampaignStatistics.isEmpty());
        assertFalse(state.longevity.newGamePlusModifiers.isEmpty());
        assertFalse(state.longevity.challengeSeeds.isEmpty());
        assertFalse(state.longevity.scheduledScenarioSeeds.isEmpty());
        assertFalse(state.longevity.customScenarioOptions.isEmpty());
        assertFalse(state.longevity.sharedSeed.isBlank());
        ProductionReadinessLongevitySystem.recoverCorruptSlot(state, "slot-1");
        assertTrue(state.longevity.slots.get(0).corruptRecovered);
        ProductionReadinessLongevitySystem.appendCampaignEvent(state, "03:15 checkpoint recovered");
        assertTrue(state.longevity.campaignEventLog.get(state.longevity.campaignEventLog.size() - 1).contains("recovered"));
    }

    @Test
    void architectureModelDeclaresBoundariesTypedIdsInvariantsValidatorsAndBudgets() {
        ProductionReadinessLongevitySystem.State state = ProductionReadinessLongevitySystem.bootstrap(93L);

        assertTrue(state.architecture.ownershipBoundaries.contains("campaign simulation"));
        assertTrue(state.architecture.ownershipBoundaries.contains("tactical simulation"));
        assertTrue(state.architecture.ownershipBoundaries.contains("UI projection"));
        assertTrue(state.architecture.ownershipBoundaries.contains("persistence"));
        assertTrue(state.architecture.ownershipBoundaries.contains("presentation"));
        assertTrue(state.architecture.transitionApis.size() >= 6);
        assertEquals(6, state.architecture.typedIds.size());
        assertTrue(state.architecture.invariants.contains("stale references"));
        assertTrue(state.architecture.invariants.contains("duplicate ownership"));
        assertTrue(state.architecture.invariants.contains("impossible overlays"));
        assertFalse(state.architecture.structuredEvents.isEmpty());
        assertTrue(state.architecture.deterministicSimulation);
        assertTrue(state.architecture.headlessCampaignPlayback && state.architecture.headlessTacticalPlayback);
        assertFalse(state.architecture.validators.isEmpty());
        assertFalse(state.architecture.assetReports.isEmpty());
        assertTrue(state.architecture.automatedScreenshotCapture);
        assertTrue(state.architecture.performanceBudgets.get("frame-ms") <= 16);
        assertTrue(Files.isRegularFile(Path.of(state.architecture.saveSchemaDiffDoc)));
        assertTrue(Files.isRegularFile(Path.of(state.architecture.balanceExport)));
        assertTrue(Files.isRegularFile(Path.of(state.longevity.modCatalogPath)));
    }

    @Test
    void testingMatrixCoversSmokePermutationsLongRunsContinuityAndFuzz() {
        ProductionReadinessLongevitySystem.State state = ProductionReadinessLongevitySystem.bootstrap(94L);

        assertEquals(9, state.testing.smokeScenarios.size());
        assertTrue(state.testing.permutationSuites.contains("overlay state"));
        assertTrue(state.testing.permutationSuites.contains("hotkey context"));
        assertTrue(state.testing.permutationSuites.contains("faction hostility"));
        assertTrue(state.testing.longRunSuites.contains("fleet director"));
        assertTrue(state.testing.longRunSuites.contains("economy"));
        assertTrue(state.testing.longRunSuites.contains("route risk forecast"));
        assertTrue(state.testing.continuitySuites.contains("encounter families"));
        assertTrue(state.testing.continuitySuites.contains("persistent ship casualty reconciliation"));
        assertTrue(state.testing.continuitySuites.contains("strike families"));
        assertTrue(state.testing.continuitySuites.contains("sensor certainty decay"));
        assertTrue(state.testing.regressionChecks.contains("accessibility screenshots"));
        assertFalse(state.testing.compatibilityFixtures.isEmpty());
        assertTrue(state.testing.randomizedCampaignTransitionFuzz);
    }

    @Test
    void serializerAndCampaignCheckpointPreserveLongevityPreferences() throws Exception {
        ProductionReadinessLongevitySystem.State direct = ProductionReadinessLongevitySystem.bootstrap(95L);
        direct.longevity.autosaveRotation = 6;
        direct.longevity.sharedSeed = "ER-WEEKLY-42";
        direct.longevity.slots.get(0).metadata = "Lunar siege checkpoint";
        ProductionReadinessLongevitySystem.State directRestored =
                ProductionReadinessLongevitySystem.restore(ProductionReadinessLongevitySystem.serialize(direct), 95L);
        assertEquals(6, directRestored.longevity.autosaveRotation);
        assertEquals("ER-WEEKLY-42", directRestored.longevity.sharedSeed);
        assertEquals("Lunar siege checkpoint", directRestored.longevity.slots.get(0).metadata);

        GameContext source = campaignContext();
        source.campaign.productionReadiness.longevity.autosaveRotation = 5;
        source.campaign.productionReadiness.longevity.sharedSeed = "ER-SHARED-77";
        CampaignCheckpointStore.Checkpoint checkpoint = captureCheckpoint(source, 4);
        GameContext restored = campaignContext();
        assertTrue(applyCheckpoint(restored, checkpoint));
        assertEquals(5, restored.campaign.productionReadiness.longevity.autosaveRotation);
        assertEquals("ER-SHARED-77", restored.campaign.productionReadiness.longevity.sharedSeed);
        assertTrue(CampaignSystem.campaignProductionReadinessLines(restored).stream()
                .anyMatch(line -> line.contains("Save slots")));
    }

    private static GameContext campaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 55L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static CampaignCheckpointStore.Checkpoint captureCheckpoint(GameContext ctx, int nextSector) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "captureCheckpoint", GameContext.class, CampaignSystem.CampaignState.class, int.class);
        method.setAccessible(true);
        return (CampaignCheckpointStore.Checkpoint) method.invoke(null, ctx, ctx.campaign, nextSector);
    }

    private static boolean applyCheckpoint(GameContext ctx, CampaignCheckpointStore.Checkpoint checkpoint) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "applyCheckpoint", GameContext.class, CampaignSystem.CampaignState.class, CampaignCheckpointStore.Checkpoint.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, ctx, ctx.campaign, checkpoint);
    }
}
