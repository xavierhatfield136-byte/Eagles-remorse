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

class StretchGoalsFleetDoctrineSystemTest {
    @Test
    void unsupportedStretchClaimsAreExplicitlyDeScopedToPostReleaseRoadmap() {
        StretchGoalsFleetDoctrineSystem.State state = StretchGoalsFleetDoctrineSystem.bootstrap(101L);

        assertTrue(state.stretch.cooperativeRoles.isEmpty());
        assertFalse(state.stretch.asynchronousCampaignSharing);
        assertFalse(state.stretch.skirmishFleetBuilder);
        assertTrue(state.stretch.editors.isEmpty());
        assertFalse(state.stretch.workshopStylePackaging);
        assertFalse(state.stretch.proceduralStarSystems);
        assertFalse(state.stretch.branchingCampaignChapters);
        assertTrue(state.stretch.factionCampaigns.isEmpty());
        assertFalse(state.stretch.balancedMetagameUnlocks);
        assertFalse(state.stretch.autonomousSpectatorMode);
        assertFalse(state.stretch.exportableAfterActionReports);
        assertFalse(state.stretch.cinematicReplayCamera);
        assertEquals(8, state.stretch.postReleaseRoadmap.size());
    }

    @Test
    void extractionCatalogMapsPlanningPacksToRealArtifacts() {
        StretchGoalsFleetDoctrineSystem.State state = StretchGoalsFleetDoctrineSystem.bootstrap(102L);

        assertEquals(13, state.extractionPacks.size());
        assertTrue(state.extractionPacks.stream().allMatch(pack -> !pack.title.isBlank() && !pack.artifact.isBlank()));
        assertTrue(state.extractionPacks.stream().allMatch(pack -> Files.isRegularFile(Path.of(pack.artifact))));
        assertTrue(Files.isRegularFile(Path.of("docs/CANDIDATE_EXTRACTION_PACKS.md")));
    }

    @Test
    void commandNetworkQueuesOrdersTradesBandwidthAndTransfersFlagAfterCollapse() {
        StretchGoalsFleetDoctrineSystem.State state = StretchGoalsFleetDoctrineSystem.bootstrap(103L);
        StretchGoalsFleetDoctrineSystem.FleetCommandState fleet = state.fleet;

        assertTrue(fleet.nodes.stream().anyMatch(node -> node.type == StretchGoalsFleetDoctrineSystem.NodeType.FLAGSHIP));
        assertTrue(fleet.nodes.stream().anyMatch(node -> node.type == StretchGoalsFleetDoctrineSystem.NodeType.RELAY
                && node.redundancyBonus > 0));
        assertTrue(fleet.nodes.stream().anyMatch(node -> node.type == StretchGoalsFleetDoctrineSystem.NodeType.FALLBACK));
        StretchGoalsFleetDoctrineSystem.setChannelMode(state, StretchGoalsFleetDoctrineSystem.ChannelMode.ENCRYPTED);
        assertEquals(6, fleet.bandwidthCapacity);
        StretchGoalsFleetDoctrineSystem.queueOrder(state, "Screen the convoy", true);
        assertEquals(1, fleet.orderQueue.size());
        assertTrue(fleet.orderQueue.get(0).captainInterpretation.contains("interprets"));
        fleet.commandLinkOverlay = true;
        assertTrue(fleet.commandLinkOverlay);
        StretchGoalsFleetDoctrineSystem.isolateFlagship(state);
        assertTrue(fleet.networkCollapsed);
        assertTrue(fleet.panicPercent > 0);
        assertTrue(fleet.isolationPenaltyPercent > 0);
        assertTrue(StretchGoalsFleetDoctrineSystem.transferFlag(state));
        assertFalse(fleet.networkCollapsed);
        assertTrue(fleet.rallyActions > 0);
        assertTrue(fleet.panicPercent < 14);
    }

    @Test
    void standingOrdersAndCohesionExposeDoctrineTradeoffsDisciplineAndReserveRotation() {
        StretchGoalsFleetDoctrineSystem.State state = StretchGoalsFleetDoctrineSystem.bootstrap(104L);
        StretchGoalsFleetDoctrineSystem.StandingOrders orders = state.fleet.standingOrders;

        assertTrue(orders.conserveAmmunition && orders.rescueDisabledAllies && orders.protectCivilianTraffic);
        assertTrue(orders.acceptSurrender && orders.preserveRareCapturedTechnology);
        assertTrue(orders.retreatThresholdPercent > 0);
        assertEquals(StretchGoalsFleetDoctrineSystem.DoctrineTemplate.CONVOY_ESCORT, orders.template);
        assertFalse(orders.captainExceptions.isEmpty());
        assertFalse(orders.afterActionNotes.isEmpty());
        assertFalse(state.fleet.preBattleReview.isBlank());
        assertTrue(state.fleet.squadronCrossfireBonusPercent > 0);
        assertEquals(StretchGoalsFleetDoctrineSystem.Discipline.MILITARY, state.fleet.discipline);

        int cohesion = state.fleet.cohesionPercent;
        StretchGoalsFleetDoctrineSystem.applyAggressiveBurn(state);
        StretchGoalsFleetDoctrineSystem.applyAggressiveBurn(state);
        assertTrue(state.fleet.cohesionPercent < cohesion);
        assertTrue(state.fleet.isolationPenaltyPercent > 0);
        assertTrue(state.fleet.panicPercent > 0);
        assertTrue(state.fleet.exhaustedReserveRotation);
        int damagedCohesion = state.fleet.cohesionPercent;
        int panic = state.fleet.panicPercent;
        StretchGoalsFleetDoctrineSystem.reformFormation(state, true);
        assertTrue(state.fleet.cohesionPercent > damagedCohesion);
        assertTrue(state.fleet.panicPercent < panic);
    }

    @Test
    void liveFleetProjectionRebuildsNodesAndResolvesAcknowledgedOrders() {
        StretchGoalsFleetDoctrineSystem.State state = StretchGoalsFleetDoctrineSystem.bootstrap(106L);
        StretchGoalsFleetDoctrineSystem.synchronizeLiveFleet(state, 4, 1, true, 2);
        assertEquals(4, state.fleet.nodes.size());
        assertTrue(state.fleet.nodes.stream().anyMatch(node ->
                node.type == StretchGoalsFleetDoctrineSystem.NodeType.RELAY));
        assertTrue(state.fleet.preBattleReview.contains("retreat at"));
        StretchGoalsFleetDoctrineSystem.queueOrder(state, "Protect disabled allies", false);
        StretchGoalsFleetDoctrineSystem.resolveQueuedOrders(state);
        assertTrue(state.fleet.doctrineAcknowledgment.contains("Protect disabled allies"));
        assertFalse(state.fleet.standingOrders.afterActionNotes.isEmpty());

        int bandwidth = state.fleet.bandwidthCapacity;
        StretchGoalsFleetDoctrineSystem.loseRelays(state, 1);
        assertTrue(state.fleet.bandwidthCapacity < bandwidth);
        assertTrue(state.fleet.nodes.stream().anyMatch(node ->
                node.type == StretchGoalsFleetDoctrineSystem.NodeType.RELAY && !node.operational));
        assertTrue(state.fleet.panicPercent > 0);

        StretchGoalsFleetDoctrineSystem.synchronizeLiveFleet(state, 1, 1, false, 0);
        assertTrue(state.fleet.networkCollapsed);
        assertTrue(state.fleet.exhaustedReserveRotation);

        StretchGoalsFleetDoctrineSystem.synchronizeLiveFleet(state, 4, 0, true, 2);
        assertFalse(state.fleet.networkCollapsed);
        assertTrue(state.fleet.rallyActions > 0);
    }

    @Test
    void serializerAndCampaignCheckpointPreserveFleetDoctrinePreferences() throws Exception {
        StretchGoalsFleetDoctrineSystem.State direct = StretchGoalsFleetDoctrineSystem.bootstrap(105L);
        StretchGoalsFleetDoctrineSystem.setChannelMode(direct, StretchGoalsFleetDoctrineSystem.ChannelMode.BURST_TRANSMISSION);
        direct.fleet.standingOrders.retreatThresholdPercent = 47;
        direct.fleet.standingOrders.pursueFleeingEnemies = true;
        direct.fleet.commandLinkOverlay = true;
        StretchGoalsFleetDoctrineSystem.applyAggressiveBurn(direct);
        StretchGoalsFleetDoctrineSystem.applyAggressiveBurn(direct);
        StretchGoalsFleetDoctrineSystem.isolateFlagship(direct);
        StretchGoalsFleetDoctrineSystem.transferFlag(direct);
        direct.fleet.doctrineAcknowledgment = "Order acknowledged: Rally on relay one.";
        StretchGoalsFleetDoctrineSystem.State directRestored =
                StretchGoalsFleetDoctrineSystem.restore(StretchGoalsFleetDoctrineSystem.serialize(direct), 105L);
        assertEquals(StretchGoalsFleetDoctrineSystem.ChannelMode.BURST_TRANSMISSION, directRestored.fleet.channelMode);
        assertEquals(47, directRestored.fleet.standingOrders.retreatThresholdPercent);
        assertTrue(directRestored.fleet.standingOrders.pursueFleeingEnemies);
        assertTrue(directRestored.fleet.commandLinkOverlay);
        assertEquals(direct.fleet.panicPercent, directRestored.fleet.panicPercent);
        assertEquals(direct.fleet.isolationPenaltyPercent, directRestored.fleet.isolationPenaltyPercent);
        assertEquals(direct.fleet.rallyActions, directRestored.fleet.rallyActions);
        assertEquals(direct.fleet.doctrineAcknowledgment, directRestored.fleet.doctrineAcknowledgment);

        GameContext source = campaignContext();
        source.campaign.fleetDoctrineExpansion.fleet.standingOrders.retreatThresholdPercent = 52;
        source.campaign.fleetDoctrineExpansion.fleet.discipline = StretchGoalsFleetDoctrineSystem.Discipline.MILITIA;
        StretchGoalsFleetDoctrineSystem.setChannelMode(source.campaign.fleetDoctrineExpansion,
                StretchGoalsFleetDoctrineSystem.ChannelMode.COURIER_DRONE);
        StretchGoalsFleetDoctrineSystem.isolateFlagship(source.campaign.fleetDoctrineExpansion);
        source.campaign.fleetDoctrineExpansion.fleet.doctrineAcknowledgment = "Order acknowledged: Emergency flag transfer.";
        CampaignCheckpointStore.Checkpoint checkpoint = captureCheckpoint(source, 4);
        GameContext restored = campaignContext();
        assertTrue(applyCheckpoint(restored, checkpoint));
        assertEquals(52, restored.campaign.fleetDoctrineExpansion.fleet.standingOrders.retreatThresholdPercent);
        assertEquals(StretchGoalsFleetDoctrineSystem.Discipline.MILITIA,
                restored.campaign.fleetDoctrineExpansion.fleet.discipline);
        assertEquals(StretchGoalsFleetDoctrineSystem.ChannelMode.COURIER_DRONE,
                restored.campaign.fleetDoctrineExpansion.fleet.channelMode);
        assertTrue(restored.campaign.fleetDoctrineExpansion.fleet.panicPercent > 0);
        assertTrue(restored.campaign.fleetDoctrineExpansion.fleet.networkCollapsed);
        assertTrue(restored.campaign.fleetDoctrineExpansion.fleet.doctrineAcknowledgment.contains("Emergency flag"));
        assertTrue(CampaignSystem.campaignFleetDoctrineExpansionLines(restored).stream()
                .anyMatch(line -> line.contains("Panic")));
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
