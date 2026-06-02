import app.config.GameConfig;
import app.config.GameMode;
import app.persistence.CampaignCheckpointStore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommunityContentSystemTest {
    @Test
    void externalDefinitionsManifestAndScenarioEditorBackendArePresent() {
        CommunityContentSystem.State state = CommunityContentSystem.bootstrap(301L);

        assertEquals(CommunityContentSystem.DefinitionKind.values().length, state.contentPack.definitions.size());
        assertTrue(CommunityContentSystem.validateContentPack(state).isEmpty());
        assertTrue(Files.isRegularFile(Path.of("config/content-pack/manifest.properties")));
        assertTrue(state.contentPack.definitions.values().stream().allMatch(path -> Files.isRegularFile(Path.of(path))));
        assertEquals(CommunityContentSystem.DefinitionKind.values().length, state.contentPack.loadedDefinitions.size());
        assertTrue(state.contentPack.loadedDefinitions.values().stream().flatMap(List::stream)
                .anyMatch(row -> "blue_flagship".equals(row.fields.get("id"))));
        assertTrue(state.scenario.visualTemplateEditor && state.scenario.dragDropFleetComposition);
        assertTrue(state.scenario.testPlayAvailable);
        assertTrue(state.scenario.elements.stream().anyMatch(element -> element.type == CommunityContentSystem.EditorElementType.OBJECTIVE));
        assertTrue(state.scenario.elements.stream().anyMatch(element -> element.type == CommunityContentSystem.EditorElementType.HAZARD));
        assertFalse(CommunityContentSystem.exportScenarioPack(state).isBlank());
        assertTrue(Files.isRegularFile(Path.of("docs/ADDITIONAL_CANDIDATE_EXTRACTION_PACKS.md")));
    }

    @Test
    void loaderReportsFileRowAndFieldDiagnosticsForMalformedDefinitions() throws Exception {
        Path root = Files.createTempDirectory("content-pack-validation");
        Path pack = Files.createDirectories(root.resolve("config/content-pack"));
        for (String name : List.of("hulls.csv", "weapons.csv", "factions.csv", "station-modules.csv", "missions.csv", "dialogue.csv")) {
            Files.writeString(pack.resolve(name), "id,value\nok,1\n");
        }
        Files.writeString(pack.resolve("hulls.csv"), "id,value\n,missing-id\nbad,row,shape\n");

        CommunityContentSystem.State state = CommunityContentSystem.bootstrap(303L);
        List<String> errors = CommunityContentSystem.loadContentPack(state, root);

        assertTrue(errors.stream().anyMatch(error -> error.contains("hulls.csv:2: id is required")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("hulls.csv:3: expected 2 fields but found 3")));
    }

    @Test
    void scenarioAndShareCodeImportsRoundTripAndRejectMalformedInput() {
        CommunityContentSystem.State state = CommunityContentSystem.bootstrap(304L);
        assertTrue(CommunityContentSystem.importScenarioPack(state,
                CommunityContentSystem.exportScenarioPack(state)));
        assertFalse(CommunityContentSystem.importScenarioPack(state, "broken|scenario"));

        for (CommunityContentSystem.ShareCodeKind kind : CommunityContentSystem.ShareCodeKind.values()) {
            String code = CommunityContentSystem.exportShareCode(state, kind);
            assertFalse(code.isBlank());
            assertTrue(CommunityContentSystem.importShareCode(state, kind, code.toLowerCase()));
            assertEquals(code, CommunityContentSystem.exportShareCode(state, kind));
            assertFalse(CommunityContentSystem.importShareCode(state, kind, "invalid"));
        }
    }

    @Test
    void packLifecycleControlsMigrationDependenciesHotReloadAndSavedManifestCompatibility() {
        CommunityContentSystem.State state = CommunityContentSystem.bootstrap(305L);
        assertTrue(CommunityContentSystem.resolveDependencies(state, List.of("base-game>=1")).isEmpty());
        assertFalse(CommunityContentSystem.resolveDependencies(state, List.of()).isEmpty());
        assertTrue(CommunityContentSystem.migratePack(state, 3));
        CommunityContentSystem.configurePack(state, true, 7);
        assertEquals(7, state.contentPack.loadOrder);
        assertEquals(List.of("core-kepler@3"), state.community.perSavePackManifest);
        assertTrue(CommunityContentSystem.hotReload(state, Path.of(".")).isEmpty());
        assertTrue(CommunityContentSystem.validateSavedManifest(state, List.of("core-kepler@3"), true));
        assertFalse(CommunityContentSystem.validateSavedManifest(state, List.of("core-kepler@2"), true));

        CommunityContentSystem.setSafeMode(state, true);
        assertTrue(state.community.perSavePackManifest.isEmpty());
    }

    @Test
    void scenarioImportReportsMissingDependenciesAndSaveSerializerKeepsManifest() {
        CommunityContentSystem.State state = CommunityContentSystem.bootstrap(306L);
        assertFalse(CommunityContentSystem.importScenarioPack(state,
                CommunityContentSystem.exportScenarioPack(state), List.of()));
        assertTrue(state.community.compatibilityDiagnostics.stream()
                .anyMatch(line -> line.contains("scenario import: missing dependency")));

        CommunityContentSystem.configurePack(state, true, 2);
        CommunityContentSystem.State restored =
                CommunityContentSystem.restore(CommunityContentSystem.serialize(state), 306L);
        assertEquals(List.of("core-kepler@1"), restored.community.perSavePackManifest);
    }

    @Test
    void editorCommunityPreferencesAndSafeModePersist() {
        CommunityContentSystem.State state = CommunityContentSystem.bootstrap(302L);
        assertTrue(CommunityContentSystem.moveElement(state, "objective-relay", 90, 15));
        CommunityContentSystem.launchTestPlay(state);
        CommunityContentSystem.setSafeMode(state, true);
        CommunityContentSystem.rateScenario(state, "kepler-relief", 5, "Good escort pressure.");

        CommunityContentSystem.State restored =
                CommunityContentSystem.restore(CommunityContentSystem.serialize(state), 302L);
        assertTrue(restored.community.safeMode);
        assertFalse(restored.contentPack.enabled);
        assertEquals(1, restored.scenario.testPlayLaunches);
        assertEquals(5, restored.community.localRatings.get("kepler-relief"));
        assertEquals("Good escort pressure.", restored.community.localNotes.get("kepler-relief"));
    }

    @Test
    void campaignCheckpointPreservesCommunityPreferencesAndProvidesReadout() throws Exception {
        GameContext source = campaignContext();
        source.campaign.communityContent.contentPack.loadOrder = 4;
        source.campaign.communityContent.scenario.deterministicSeed = 999L;
        CommunityContentSystem.rateScenario(source.campaign.communityContent, "kepler-relief", 4, "Replayable.");
        CampaignCheckpointStore.Checkpoint checkpoint = captureCheckpoint(source, 6);

        GameContext restored = campaignContext();
        assertTrue(applyCheckpoint(restored, checkpoint));
        assertEquals(4, restored.campaign.communityContent.contentPack.loadOrder);
        assertEquals(999L, restored.campaign.communityContent.scenario.deterministicSeed);
        assertEquals(4, restored.campaign.communityContent.community.localRatings.get("kepler-relief"));
        assertTrue(CampaignSystem.campaignCommunityContentLines(restored).stream().anyMatch(line -> line.contains("Content pack")));
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
