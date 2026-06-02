import org.junit.jupiter.api.Test;
import app.config.GameConfig;
import app.config.GameMode;
import app.persistence.CampaignCheckpointStore;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategicCampaignExpansionSystemTest {
    @Test
    void theaterTopologyIncludesSystemsLanesInstallationsFogAndHiddenRoutes() {
        StrategicCampaignExpansionSystem.State state = StrategicCampaignExpansionSystem.bootstrap(77L);

        assertTrue(state.systems.size() >= 5);
        assertTrue(state.systems.stream().anyMatch(system -> !system.explored));
        assertTrue(state.systems.stream().anyMatch(system ->
                system.rule == StrategicCampaignExpansionSystem.RegionRule.DEEP_SPACE_ANOMALY));
        assertTrue(state.lanes.stream().anyMatch(lane ->
                lane.type == StrategicCampaignExpansionSystem.LaneType.JUMP_POINT));
        assertTrue(state.lanes.stream().anyMatch(lane ->
                lane.type == StrategicCampaignExpansionSystem.LaneType.BLOCKADE_CHOKEPOINT));
        assertTrue(state.lanes.stream().anyMatch(lane ->
                lane.type == StrategicCampaignExpansionSystem.LaneType.HIDDEN_ROUTE && !lane.discovered));
        assertTrue(state.installations.stream().anyMatch(site ->
                site.type == StrategicCampaignExpansionSystem.InstallationType.FORWARD_BASE));
        assertTrue(state.installations.stream().anyMatch(site ->
                site.type == StrategicCampaignExpansionSystem.InstallationType.RESOURCE_BELT));
        assertTrue(state.installations.stream().anyMatch(site ->
                site.type == StrategicCampaignExpansionSystem.InstallationType.POPULATION_CENTER));
        assertTrue(state.installations.stream().anyMatch(site ->
                site.type == StrategicCampaignExpansionSystem.InstallationType.ORBITAL_PLATFORM));
        int front = state.frontLinePosition;
        StrategicCampaignExpansionSystem.moveFrontLine(state, 7);
        assertEquals(front + 7, state.frontLinePosition);
        assertTrue(StrategicCampaignExpansionSystem.discoverHiddenRoute(state, "frontier", "anomaly"));
        assertTrue(StrategicCampaignExpansionSystem.transferInstallation(state, "frontier-fob", "Red"));
    }

    @Test
    void directorsHaveLimitedIntelBudgetsQueuesPoliticsAndAsymmetricActors() {
        StrategicCampaignExpansionSystem.State state = StrategicCampaignExpansionSystem.bootstrap(91L);

        assertTrue(state.directors.size() >= 6);
        assertTrue(state.directors.values().stream().allMatch(director ->
                director.intelligenceCoverage < 100 && director.resourceBudget > 0));
        assertTrue(state.directors.values().stream().allMatch(director ->
                director.objectiveQueue.contains(StrategicCampaignExpansionSystem.DirectorAction.RAID)
                        && director.objectiveQueue.contains(StrategicCampaignExpansionSystem.DirectorAction.DEFEND)
                        && director.objectiveQueue.contains(StrategicCampaignExpansionSystem.DirectorAction.LOGISTICS)
                        && director.objectiveQueue.contains(StrategicCampaignExpansionSystem.DirectorAction.RESEARCH)
                        && director.objectiveQueue.contains(StrategicCampaignExpansionSystem.DirectorAction.DIPLOMACY)
                        && director.objectiveQueue.contains(StrategicCampaignExpansionSystem.DirectorAction.FEINT)
                        && director.objectiveQueue.contains(StrategicCampaignExpansionSystem.DirectorAction.MISINFORMATION)
                        && director.objectiveQueue.contains(StrategicCampaignExpansionSystem.DirectorAction.MAJOR_OFFENSIVE)));
        assertTrue(state.directors.containsKey("Rogue AI"));
        assertTrue(state.directors.containsKey("Pirates"));
        assertTrue(state.directors.values().stream().allMatch(director -> !director.constructionQueue.isEmpty()));
        StrategicCampaignExpansionSystem.recordDirectorMistake(state, "Red");
        StrategicCampaignExpansionSystem.recordDirectorRecovery(state, "Red");
        assertEquals(1, state.directors.get("Red").mistakes);
        assertEquals(1, state.directors.get("Red").recoveries);
        assertFalse(state.neutralActorStatus.isBlank());
        assertFalse(state.pirateLeaderAgenda.isBlank());
    }

    @Test
    void multiFleetBattleReportsCoverFrontsInterventionsIntelAndAftermath() {
        StrategicCampaignExpansionSystem.State state = StrategicCampaignExpansionSystem.bootstrap(123L);
        StrategicCampaignExpansionSystem.BattleReport report =
                StrategicCampaignExpansionSystem.recordBattle(state, "battle-lunar", "Blue", "Red", "Yellow");

        assertEquals(3, report.participants.size());
        assertTrue(report.fronts.containsAll(List.of("center", "reserve", "flank", "retreat corridor")));
        assertTrue(report.interventions.containsAll(List.of(StrategicCampaignExpansionSystem.Intervention.values())));
        assertTrue(report.reinforcementWindowSec > 0);
        assertTrue(report.delayedIntel);
        assertTrue(report.conflictingRumors);
        assertTrue(report.wreckFieldVisitAvailable);
        assertTrue(report.rescueAvailable);
        assertTrue(report.prisoners > 0);
        assertTrue(report.salvageRightsDisputed);
    }

    @Test
    void taskGroupsExposeCompositionRoutingOrdersDelegationAndOverlayPlanning() {
        StrategicCampaignExpansionSystem.State state = StrategicCampaignExpansionSystem.bootstrap(42L);
        StrategicCampaignExpansionSystem.TaskGroup group =
                StrategicCampaignExpansionSystem.createTaskGroup(state, "tg-rescue", "Voss Relief Group", 2, 4, 7);
        group.order = StrategicCampaignExpansionSystem.TaskOrder.ESCORT;
        group.rulesOfEngagement = StrategicCampaignExpansionSystem.RulesOfEngagement.DEFENSIVE;
        group.automaticRetreatThreshold = 48;
        group.delegatedCaptain = "Captain Nadi Voss";
        group.route = "Frontier Belt -> Lunar Gravity Well";
        group.etaHours = 5;
        group.riskPercent = 38;

        assertEquals(3, group.shipSlots.size());
        assertEquals(StrategicCampaignExpansionSystem.TaskOrder.ESCORT, group.order);
        assertTrue(group.automaticRetreatThreshold > 0);
        assertFalse(group.delegatedCaptain.isBlank());
        assertFalse(group.route.isBlank());
        assertEquals(StrategicCampaignExpansionSystem.MapOverlay.values().length, state.overlays.size());
        assertTrue(StrategicCampaignExpansionSystem.commandBoardLines(state).stream()
                .anyMatch(line -> line.contains("Task Groups")));
    }

    @Test
    void taskGroupsPersistWithoutLosingStrategicPlanningFields() {
        StrategicCampaignExpansionSystem.State state = StrategicCampaignExpansionSystem.bootstrap(5L);
        StrategicCampaignExpansionSystem.TaskGroup group =
                StrategicCampaignExpansionSystem.createTaskGroup(state, "tg-blockade", "Lunar Blockade Group", 3, 8);
        group.order = StrategicCampaignExpansionSystem.TaskOrder.BLOCKADE;
        group.rulesOfEngagement = StrategicCampaignExpansionSystem.RulesOfEngagement.AGGRESSIVE;
        group.automaticRetreatThreshold = 27;
        group.delegatedCaptain = "Captain Sera Rook";
        group.route = "Lunar Gravity Well";
        group.etaHours = 3;
        group.riskPercent = 61;
        state.frontLinePosition = 68;
        state.doctrineSeason = "Lunar counteroffensive";

        StrategicCampaignExpansionSystem.State restored =
                StrategicCampaignExpansionSystem.restore(StrategicCampaignExpansionSystem.serialize(state), 5L);
        StrategicCampaignExpansionSystem.TaskGroup restoredGroup = restored.taskGroups.stream()
                .filter(candidate -> candidate.id.equals("tg-blockade"))
                .findFirst()
                .orElseThrow();

        assertEquals(68, restored.frontLinePosition);
        assertEquals("Lunar counteroffensive", restored.doctrineSeason);
        assertEquals(StrategicCampaignExpansionSystem.TaskOrder.BLOCKADE, restoredGroup.order);
        assertEquals(StrategicCampaignExpansionSystem.RulesOfEngagement.AGGRESSIVE, restoredGroup.rulesOfEngagement);
        assertEquals(27, restoredGroup.automaticRetreatThreshold);
        assertEquals("Captain Sera Rook", restoredGroup.delegatedCaptain);
        assertEquals(61, restoredGroup.riskPercent);
    }

    @Test
    void campaignCheckpointPersistsStrategicExpansionState() throws Exception {
        GameContext source = campaignContext();

        CampaignCheckpointStore.Checkpoint checkpoint = captureCheckpoint(source, 4);
        GameContext restored = campaignContext();
        assertTrue(applyCheckpoint(restored, checkpoint));

        assertFalse(restored.campaign.strategicExpansion.systems.isEmpty());
        assertFalse(restored.campaign.strategicExpansion.installations.isEmpty());
        assertTrue(restored.campaign.strategicExpansion.taskGroups.stream()
                .anyMatch(candidate -> candidate.id.startsWith("live-force-")));
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
