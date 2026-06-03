import app.config.GameConfig;
import app.config.GameMode;
import app.persistence.CampaignCheckpointStore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepCampaignSimulationSystemTest {
    @Test
    void bootstrapCoversLivingLocationsPersonnelOperationsEnvironmentPoliticsRecoveryAndLegacy() {
        DeepCampaignSimulationSystem.State state = DeepCampaignSimulationSystem.bootstrap(201L);

        assertEquals(DeepCampaignSimulationSystem.StationModule.values().length, state.station.modules.size());
        assertTrue(state.station.constructionBarge && state.station.smugglerDock && state.station.improvisedRepairYard);
        assertEquals(DeepCampaignSimulationSystem.WreckPurpose.values().length, state.location.wreckFieldUses.size());
        assertEquals(DeepCampaignSimulationSystem.OrbitalLayer.values().length, state.orbit.layers.size());
        assertFalse(state.officers.isEmpty());
        assertFalse(state.culture.traditions.isEmpty());
        assertTrue(state.civilians.organizations.size() >= 6);
        assertTrue(state.operation.phases.size() >= 3 && state.operation.rehearsedWithIncompleteIntel);
        assertFalse(state.intelligence.sources.isEmpty());
        assertFalse(state.espionage.agents.isEmpty());
        assertEquals(10, state.environment.hazards.size());
        assertEquals(5, state.politics.factionLogistics.size());
        assertTrue(state.politics.neutralPowers.size() >= 10);
        assertEquals(DeepCampaignSimulationSystem.RecoveryType.values().length, state.crisisRecovery.recoveryOptions.size());
        assertEquals(DeepCampaignSimulationSystem.EndgameType.values().length, state.legacy.endgames.size());
        assertEquals(DeepCampaignSimulationSystem.ChallengeType.values().length, state.legacy.challenges.size());
    }

    @Test
    void deepCampaignTransitionsPersistThroughDirectSerializer() {
        DeepCampaignSimulationSystem.State state = DeepCampaignSimulationSystem.bootstrap(202L);
        DeepCampaignSimulationSystem.setModuleStatus(state, DeepCampaignSimulationSystem.StationModule.DOCKS,
                DeepCampaignSimulationSystem.ModuleStatus.DISABLED);
        DeepCampaignSimulationSystem.advanceConstruction(state, 44);
        DeepCampaignSimulationSystem.resolveBattle(state, 5, true);
        DeepCampaignSimulationSystem.deployOfficer(state, "Lt. Mara Venn", 38);
        DeepCampaignSimulationSystem.comparePlanToOutcome(state, "Relay secured after reserve commitment");
        DeepCampaignSimulationSystem.debriefIntelligence(state, "Kepler/Red/Day 14: reserve route confirmed.");
        DeepCampaignSimulationSystem.triggerCrisis(state, DeepCampaignSimulationSystem.CrisisType.FUEL, "Ration escorts");
        state.crisisRecovery.continueResistance = true;
        state.environment.policy = DeepCampaignSimulationSystem.ResourcePolicy.RATIONING;
        state.legacy.playerNotes = "Protect Kepler habitat.";
        DeepCampaignSimulationSystem.resolveEndgame(state, DeepCampaignSimulationSystem.EndgameType.COALITION, 810);

        DeepCampaignSimulationSystem.State restored =
                DeepCampaignSimulationSystem.restore(DeepCampaignSimulationSystem.serialize(state), 202L);
        assertEquals(DeepCampaignSimulationSystem.ModuleStatus.DISABLED,
                restored.station.modules.get(DeepCampaignSimulationSystem.StationModule.DOCKS));
        assertEquals(44, restored.station.constructionPercent);
        assertTrue(restored.station.memorial && restored.location.visibleScars);
        assertEquals(38, restored.officers.get(0).fatiguePercent);
        assertEquals(DeepCampaignSimulationSystem.ResourcePolicy.RATIONING, restored.environment.policy);
        assertTrue(restored.crisisRecovery.continueResistance);
        assertFalse(restored.crisisRecovery.crises.isEmpty());
        assertEquals(810, restored.legacy.score);
        assertEquals("Protect Kepler habitat.", restored.legacy.playerNotes);
    }

    @Test
    void campaignCheckpointPreservesDeepCampaignAndProvidesReadout() throws Exception {
        GameContext source = campaignContext();
        DeepCampaignSimulationSystem.advanceConstruction(source.campaign.deepCampaignExpansion, 67);
        source.campaign.deepCampaignExpansion.station.emergencyShutdown = true;
        DeepCampaignSimulationSystem.triggerCrisis(source.campaign.deepCampaignExpansion,
                DeepCampaignSimulationSystem.CrisisType.EPIDEMIC, "Quarantine low orbit");
        CampaignCheckpointStore.Checkpoint checkpoint = captureCheckpoint(source, 5);

        GameContext restored = campaignContext();
        assertTrue(applyCheckpoint(restored, checkpoint));
        assertEquals(67, restored.campaign.deepCampaignExpansion.station.constructionPercent);
        assertTrue(restored.campaign.deepCampaignExpansion.station.emergencyShutdown);
        assertFalse(restored.campaign.deepCampaignExpansion.crisisRecovery.crises.isEmpty());
        assertTrue(CampaignSystem.campaignDeepSimulationLines(restored).stream().anyMatch(line -> line.contains("Station")));
    }

    @Test
    void liveStationDamageServiceLossAndRepairPersist() {
        DeepCampaignSimulationSystem.State state = DeepCampaignSimulationSystem.bootstrap(203L);
        DeepCampaignSimulationSystem.applyLiveStationDamage(state, 3, true, true);
        assertEquals(DeepCampaignSimulationSystem.ModuleStatus.CAPTURED,
                state.station.modules.get(DeepCampaignSimulationSystem.StationModule.DOCKS));
        assertTrue(state.location.visibleScars && state.station.memorial);
        int damagedServices = state.location.servicesPercent;
        DeepCampaignSimulationSystem.applyLiveStationService(state, "REPAIR", "Kepler Yard");
        assertEquals(DeepCampaignSimulationSystem.ModuleStatus.CAPTURED,
                state.station.modules.get(DeepCampaignSimulationSystem.StationModule.DOCKS));
        assertTrue(state.location.servicesPercent > damagedServices);
        DeepCampaignSimulationSystem.State restored =
                DeepCampaignSimulationSystem.restore(DeepCampaignSimulationSystem.serialize(state), 203L);
        assertEquals(state.location.servicesPercent, restored.location.servicesPercent);
        assertTrue(restored.location.reconstructionProject);
    }

    @Test
    void liveCampaignServicesAndLongRunTicksEvolveDeepStateFromBootstrap() throws Exception {
        GameContext ctx = campaignContext();
        CampaignSystem.CampaignState st = ctx.campaign;
        String bootstrapSnapshot = DeepCampaignSimulationSystem.serialize(st.deepCampaignExpansion);

        CampaignSystem.CampaignLocation shipyard = firstLocationWithService(ctx, CampaignSystem.HubService.SHIPYARD);
        assertTrue(shipyard != null, "campaign map should expose a live shipyard service");
        st.selectedGalaxyLocationId = shipyard.id;
        st.dockedGalaxyLocationId = shipyard.id;
        st.currentGalaxyLocationId = shipyard.id;
        ctx.credits = 20_000;
        st.campaignSalvage = 80;
        CampaignSystem.grantCampaignOre(ctx, 1_000);
        assertTrue(CampaignSystem.openSelectedHubService(ctx, CampaignSystem.HubService.SHIPYARD));
        assertTrue(CampaignSystem.confirmSelectedHubService(ctx));
        assertTrue(st.deepCampaignExpansion.location.history.stream()
                .anyMatch(line -> line.contains("SHIPYARD service completed")));

        CampaignSystem.CampaignLocation intel = firstLocationWithService(ctx, CampaignSystem.HubService.INTEL);
        assertTrue(intel != null, "campaign map should expose a live intel service");
        st.selectedGalaxyLocationId = intel.id;
        st.dockedGalaxyLocationId = intel.id;
        st.currentGalaxyLocationId = intel.id;
        ctx.credits = 20_000;
        assertTrue(CampaignSystem.openSelectedHubService(ctx, CampaignSystem.HubService.INTEL));
        assertTrue(CampaignSystem.confirmSelectedHubService(ctx));

        for (int i = 0; i < 900; i++) {
            CampaignSystem.update(ctx, 1.0 / 30.0);
        }

        String evolvedSnapshot = DeepCampaignSimulationSystem.serialize(st.deepCampaignExpansion);
        assertFalse(bootstrapSnapshot.equals(evolvedSnapshot));
        assertTrue(st.deepCampaignExpansion.station.constructionPercent >= 8);
        assertTrue(st.deepCampaignExpansion.station.orbitalRelayCoveragePercent > 80);
        assertTrue(CampaignSystem.campaignDeepSimulationLines(ctx).stream()
                .anyMatch(line -> line.contains("Construction")));

        CampaignCheckpointStore.Checkpoint checkpoint = captureCheckpoint(ctx, st.sector + 1);
        GameContext restored = campaignContext();
        assertTrue(applyCheckpoint(restored, checkpoint));
        assertEquals(evolvedSnapshot, DeepCampaignSimulationSystem.serialize(restored.campaign.deepCampaignExpansion));
    }

    private static GameContext campaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 55L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static CampaignSystem.CampaignLocation firstLocationWithService(GameContext ctx, CampaignSystem.HubService service) {
        List<CampaignSystem.CampaignLocation> locations = CampaignSystem.mainCampaignLocations(ctx);
        for (CampaignSystem.CampaignLocation location : locations) {
            if (location != null && location.services.contains(service)) return location;
        }
        return null;
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
