import org.junit.jupiter.api.Test;
import app.config.GameConfig;
import app.config.GameMode;
import app.persistence.CampaignCheckpointStore;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategicCampaignExpansionSystemTest {
    @Test
    void territoryGraphProvidesStableAdjacencyAndRejectsSkippedInvasions() {
        StrategicCampaignExpansionSystem.State state = StrategicCampaignExpansionSystem.bootstrap(12L);

        assertTrue(StrategicCampaignExpansionSystem.validateTerritoryGraph(state).isEmpty());
        assertEquals(List.of("frontier"),
                StrategicCampaignExpansionSystem.adjacentTerritoryIds(state, "shelter"));
        assertFalse(StrategicCampaignExpansionSystem.isLegalInvasionTarget(state, "TEAM_C", "frontier"),
                "Green must not invade allied Bright Yellow territory");
        assertTrue(StrategicCampaignExpansionSystem.isLegalInvasionTarget(state, "BRIGHT_YELLOW", "well"));
        assertFalse(StrategicCampaignExpansionSystem.isLegalInvasionTarget(state, "TEAM_C", "well"),
                "a faction must not skip the intervening frontier territory");
        assertFalse(StrategicCampaignExpansionSystem.isLegalInvasionTarget(state, "TEAM_C", "sol"),
                "a faction must not jump to the top of the map");
        assertTrue(StrategicCampaignExpansionSystem.debugTerritoryLine(state, "shelter").contains("legal targets"));
    }

    @Test
    void territoryGraphOwnerControllerAndVersionRoundTrip() {
        StrategicCampaignExpansionSystem.State state = StrategicCampaignExpansionSystem.bootstrap(13L);
        StrategicCampaignExpansionSystem.Territory frontier =
                StrategicCampaignExpansionSystem.territory(state, "frontier");
        frontier.owner = "TEAM_C";
        frontier.controller = "ENEMY";

        StrategicCampaignExpansionSystem.State restored =
                StrategicCampaignExpansionSystem.restore(StrategicCampaignExpansionSystem.serialize(state), 13L);

        assertEquals(StrategicCampaignExpansionSystem.TERRITORY_GRAPH_VERSION, restored.territoryGraphVersion);
        assertEquals("TEAM_C", StrategicCampaignExpansionSystem.territory(restored, "frontier").owner);
        assertEquals("ENEMY", StrategicCampaignExpansionSystem.territory(restored, "frontier").controller);
        assertEquals(StrategicCampaignExpansionSystem.adjacentTerritoryIds(state, "frontier"),
                StrategicCampaignExpansionSystem.adjacentTerritoryIds(restored, "frontier"));
        assertTrue(StrategicCampaignExpansionSystem.validateTerritoryGraph(restored).isEmpty());
    }

    @Test
    void legalOperationsShareAdjacencyRulesAndRaidsNeverTransferControl() {
        StrategicCampaignExpansionSystem.State state = StrategicCampaignExpansionSystem.bootstrap(14L);

        StrategicCampaignExpansionSystem.OperationLegality skipped =
                StrategicCampaignExpansionSystem.operationLegality(state,
                        StrategicCampaignExpansionSystem.OperationType.INVASION, "BRIGHT_YELLOW", "frontier", "sol");
        assertFalse(skipped.legal());
        assertTrue(skipped.reason().contains("not adjacent"));

        StrategicCampaignExpansionSystem.StrategicOperation raid =
                StrategicCampaignExpansionSystem.startOperation(state,
                        StrategicCampaignExpansionSystem.OperationType.RAID, "BRIGHT_YELLOW", "frontier", "well");
        assertTrue(raid != null);
        String originalController = StrategicCampaignExpansionSystem.territory(state, "well").controller;
        assertTrue(StrategicCampaignExpansionSystem.completeOperation(state, raid.id, true));
        assertEquals(originalController, StrategicCampaignExpansionSystem.territory(state, "well").controller,
                "a successful raid must not transfer territory control");

        StrategicCampaignExpansionSystem.StrategicOperation invasion =
                StrategicCampaignExpansionSystem.startOperation(state,
                        StrategicCampaignExpansionSystem.OperationType.INVASION, "BRIGHT_YELLOW", "frontier", "well");
        assertTrue(invasion != null);
        assertTrue(StrategicCampaignExpansionSystem.completeOperation(state, invasion.id, true));
        assertEquals(StrategicCampaignExpansionSystem.TerritoryControlState.CONTESTED,
                StrategicCampaignExpansionSystem.territory(state, "well").controlState);
        assertEquals(originalController, StrategicCampaignExpansionSystem.territory(state, "well").controller,
                "one invasion victory must not instantly flip control");
    }

    @Test
    void interceptedAndStalematedOperationsSpendFleetAndSupplyWithoutChangingControl() {
        StrategicCampaignExpansionSystem.State state = StrategicCampaignExpansionSystem.bootstrap(141L);
        StrategicCampaignExpansionSystem.Territory origin =
                StrategicCampaignExpansionSystem.territory(state, "frontier");
        int strength = origin.friendlyFleetStrength;
        int ammunition = origin.ammunition;
        String controller = StrategicCampaignExpansionSystem.territory(state, "well").controller;
        StrategicCampaignExpansionSystem.StrategicOperation invasion =
                StrategicCampaignExpansionSystem.startOperation(state,
                        StrategicCampaignExpansionSystem.OperationType.INVASION,
                        "BRIGHT_YELLOW", "frontier", "well");
        assertTrue(invasion != null);
        assertTrue(StrategicCampaignExpansionSystem.resolveOperation(state, invasion.id,
                StrategicCampaignExpansionSystem.OperationResolution.INTERCEPTED, 12, 18));
        assertEquals(StrategicCampaignExpansionSystem.OperationStatus.INTERCEPTED, invasion.status);
        assertEquals(strength - 12, origin.friendlyFleetStrength);
        assertEquals(ammunition - 18, origin.ammunition);
        assertEquals(controller, StrategicCampaignExpansionSystem.territory(state, "well").controller);

        StrategicCampaignExpansionSystem.State restored =
                StrategicCampaignExpansionSystem.restore(StrategicCampaignExpansionSystem.serialize(state), 141L);
        assertEquals(12, restored.operations.get(0).fleetLosses);
        assertEquals(18, restored.operations.get(0).supplySpent);
    }

    @Test
    void strategicOperationsRoundTripWithTerritoryState() {
        StrategicCampaignExpansionSystem.State state = StrategicCampaignExpansionSystem.bootstrap(15L);
        StrategicCampaignExpansionSystem.StrategicOperation operation =
                StrategicCampaignExpansionSystem.startOperation(state,
                        StrategicCampaignExpansionSystem.OperationType.INVASION, "BRIGHT_YELLOW", "frontier", "well");
        operation.progress = 37;

        StrategicCampaignExpansionSystem.State restored =
                StrategicCampaignExpansionSystem.restore(StrategicCampaignExpansionSystem.serialize(state), 15L);

        assertEquals(1, restored.operations.size());
        assertEquals(StrategicCampaignExpansionSystem.OperationType.INVASION, restored.operations.get(0).type);
        assertEquals(StrategicCampaignExpansionSystem.OperationStatus.ACTIVE, restored.operations.get(0).status);
        assertEquals(37, restored.operations.get(0).progress);
        assertEquals("well", restored.operations.get(0).targetTerritoryId);
    }

    @Test
    void commonOperationModelCarriesIntentCommitmentTimingOutcomeAndConsequences() {
        StrategicCampaignExpansionSystem.State state = StrategicCampaignExpansionSystem.bootstrap(150L);
        StrategicCampaignExpansionSystem.StrategicOperation sabotage =
                StrategicCampaignExpansionSystem.startOperation(state,
                        StrategicCampaignExpansionSystem.OperationType.SABOTAGE,
                        "BRIGHT_YELLOW", "frontier", "well");
        assertTrue(sabotage != null);
        StrategicCampaignExpansionSystem.configureOperation(sabotage, "TEAM_C", "Disable the lunar relay",
                "fleet-voss", 22, 48, 4);
        int ownerInfrastructure = StrategicCampaignExpansionSystem.territory(state, "well").infrastructure;
        String originalController = StrategicCampaignExpansionSystem.territory(state, "well").controller;
        for (int i = 0; i < 4; i++) {
            StrategicCampaignExpansionSystem.advanceOperation(state, sabotage.id, 80, 80, i == 1);
        }
        if (sabotage.status == StrategicCampaignExpansionSystem.OperationStatus.ACTIVE) {
            StrategicCampaignExpansionSystem.completeOperation(state, sabotage.id, true);
        }
        assertEquals("TEAM_C", sabotage.sponsor);
        assertEquals("fleet-voss", sabotage.fleetId);
        assertFalse(sabotage.intent.isBlank());
        assertFalse(sabotage.stakes.isBlank());
        assertFalse(sabotage.outcome.isBlank());
        assertFalse(sabotage.consequence.isBlank());
        assertTrue(StrategicCampaignExpansionSystem.territory(state, "well").infrastructure < ownerInfrastructure);
        assertEquals(originalController, StrategicCampaignExpansionSystem.territory(state, "well").controller,
                "sabotage must never transfer ownership");

        StrategicCampaignExpansionSystem.StrategicOperation convoy =
                StrategicCampaignExpansionSystem.startOperation(state,
                        StrategicCampaignExpansionSystem.OperationType.CONVOY,
                        "BRIGHT_YELLOW", "frontier", "shelter");
        assertTrue(convoy != null, "friendly destinations must accept convoy operations");
        assertFalse(StrategicCampaignExpansionSystem.operationLegality(state,
                StrategicCampaignExpansionSystem.OperationType.CONVOY,
                "BRIGHT_YELLOW", "frontier", "well").legal());

        StrategicCampaignExpansionSystem.State restored =
                StrategicCampaignExpansionSystem.restore(StrategicCampaignExpansionSystem.serialize(state), 150L);
        StrategicCampaignExpansionSystem.StrategicOperation restoredSabotage =
                StrategicCampaignExpansionSystem.operation(restored, sabotage.id);
        assertEquals("TEAM_C", restoredSabotage.sponsor);
        assertEquals("Disable the lunar relay", restoredSabotage.objective);
        assertEquals(22, restoredSabotage.supplyCommitment);
        assertEquals(4, restoredSabotage.durationTicks);
    }

    @Test
    void beachheadExceptionsAreExplicitCostlySuppliedVulnerableAndNarrow() {
        StrategicCampaignExpansionSystem.State state = StrategicCampaignExpansionSystem.bootstrap(151L);
        assertTrue(StrategicCampaignExpansionSystem.createBeachhead(state, "BRIGHT_YELLOW", "well",
                StrategicCampaignExpansionSystem.BeachheadAuthorization.SPECIALIZED_ASSAULT,
                20, false, 20, 40, 30, 5, 60) == null,
                "ordinary low-cost AI action must not create a deep beachhead");

        StrategicCampaignExpansionSystem.Beachhead beachhead =
                StrategicCampaignExpansionSystem.createBeachhead(state, "BRIGHT_YELLOW", "well",
                        StrategicCampaignExpansionSystem.BeachheadAuthorization.SPECIALIZED_ASSAULT,
                        100, true, 20, 50, 30, 5, 60);
        assertTrue(beachhead != null && beachhead.canStageInvasion());
        assertTrue(StrategicCampaignExpansionSystem.operationLegality(state,
                StrategicCampaignExpansionSystem.OperationType.INVASION,
                "BRIGHT_YELLOW", "well", "sol").legal(),
                "a supplied authorized beachhead may stage only along its adjacent edge");
        assertFalse(StrategicCampaignExpansionSystem.operationLegality(state,
                StrategicCampaignExpansionSystem.OperationType.INVASION,
                "BRIGHT_YELLOW", "well", "shelter").legal(),
                "a beachhead must not become arbitrary map teleportation");

        StrategicCampaignExpansionSystem.advanceBeachhead(state, beachhead.id, 0, 0, false);
        StrategicCampaignExpansionSystem.advanceBeachhead(state, beachhead.id, 0, 0, false);
        assertEquals(StrategicCampaignExpansionSystem.BeachheadStatus.ISOLATED, beachhead.status);
        assertFalse(StrategicCampaignExpansionSystem.operationLegality(state,
                StrategicCampaignExpansionSystem.OperationType.INVASION,
                "BRIGHT_YELLOW", "well", "sol").legal());

        StrategicCampaignExpansionSystem.State restored =
                StrategicCampaignExpansionSystem.restore(StrategicCampaignExpansionSystem.serialize(state), 151L);
        assertEquals(1, restored.beachheads.size());
        assertEquals(StrategicCampaignExpansionSystem.BeachheadStatus.ISOLATED, restored.beachheads.get(0).status);
    }

    @Test
    void yellowCivilWarTurnUsesSharedRulesTracksSponsorsAndSupportsDefection() {
        StrategicCampaignExpansionSystem.State state = StrategicCampaignExpansionSystem.bootstrap(152L);
        StrategicCampaignExpansionSystem.StrategicOperation operation =
                StrategicCampaignExpansionSystem.advanceYellowCivilWar(state, 10, 6, false);
        assertTrue(operation != null);
        assertTrue(operation.faction.equals("BRIGHT_YELLOW") || operation.faction.equals("DARK_YELLOW"));
        assertTrue(operation.sponsor.equals("BLUE_GREEN_COALITION") || operation.sponsor.equals("RED"));
        assertEquals(10, state.brightCoalitionAid);
        assertEquals(6, state.darkRedAid);
        assertTrue(state.brightPoliticalObligation > 0 && state.darkPoliticalObligation > 0);

        StrategicCampaignExpansionSystem.Territory frontier =
                StrategicCampaignExpansionSystem.territory(state, "frontier");
        frontier.legitimacy = 20;
        frontier.resistance = 80;
        assertTrue(StrategicCampaignExpansionSystem.defectYellowTerritory(state, "frontier",
                "DARK_YELLOW", 30, 60));
        assertEquals("DARK_YELLOW", frontier.controller);
        assertEquals(StrategicCampaignExpansionSystem.TerritoryControlState.CONTESTED, frontier.controlState);

        StrategicCampaignExpansionSystem.State restored =
                StrategicCampaignExpansionSystem.restore(StrategicCampaignExpansionSystem.serialize(state), 152L);
        assertEquals(10, restored.brightCoalitionAid);
        assertEquals(6, restored.darkRedAid);
        assertTrue(restored.sharedHullIntelConfusion >= 35);
        assertFalse(StrategicCampaignExpansionSystem.civilWarResolution(restored).endingConsequence().isBlank());
    }

    @Test
    void yellowCivilWarStartsOnAdjacentFrontAndReachesPersistedSystemicOutcome() {
        StrategicCampaignExpansionSystem.State state = StrategicCampaignExpansionSystem.bootstrap(16L);
        assertTrue(StrategicCampaignExpansionSystem.operationLegality(state,
                StrategicCampaignExpansionSystem.OperationType.INVASION,
                "BRIGHT_YELLOW", "frontier", "well").legal());

        for (int i = 0; i < 2; i++) {
            StrategicCampaignExpansionSystem.StrategicOperation invasion =
                    StrategicCampaignExpansionSystem.startOperation(state,
                            StrategicCampaignExpansionSystem.OperationType.INVASION,
                            "BRIGHT_YELLOW", "frontier", "well");
            assertTrue(invasion != null);
            assertTrue(StrategicCampaignExpansionSystem.completeOperation(state, invasion.id, true));
        }
        assertEquals(StrategicCampaignExpansionSystem.TerritoryControlState.OCCUPIED,
                StrategicCampaignExpansionSystem.territory(state, "well").controlState);
        assertEquals(StrategicCampaignExpansionSystem.CivilWarOutcome.BRIGHT_YELLOW_VICTORY,
                StrategicCampaignExpansionSystem.evaluateCivilWarOutcome(state));

        StrategicCampaignExpansionSystem.State restored =
                StrategicCampaignExpansionSystem.restore(StrategicCampaignExpansionSystem.serialize(state), 16L);
        assertEquals(StrategicCampaignExpansionSystem.CivilWarOutcome.BRIGHT_YELLOW_VICTORY,
                restored.civilWarOutcome);
        assertEquals("BRIGHT_YELLOW", StrategicCampaignExpansionSystem.territory(restored, "well").controller);
    }

    @Test
    void isolationPreventsOnwardInvasionAndPressureReasonsRemainInspectable() {
        StrategicCampaignExpansionSystem.State state = StrategicCampaignExpansionSystem.bootstrap(17L);
        StrategicCampaignExpansionSystem.Territory frontier =
                StrategicCampaignExpansionSystem.territory(state, "frontier");
        frontier.supplySource = false;
        StrategicCampaignExpansionSystem.territory(state, "shelter").supplySource = false;
        StrategicCampaignExpansionSystem.recalculateSupply(state, "BRIGHT_YELLOW");

        assertEquals(StrategicCampaignExpansionSystem.SupplyState.ISOLATED, frontier.supplyState);
        StrategicCampaignExpansionSystem.OperationLegality blocked =
                StrategicCampaignExpansionSystem.operationLegality(state,
                        StrategicCampaignExpansionSystem.OperationType.INVASION,
                        "BRIGHT_YELLOW", "frontier", "well");
        assertFalse(blocked.legal());
        assertTrue(blocked.reason().contains("isolated"));
        StrategicCampaignExpansionSystem.DirectorScore score =
                StrategicCampaignExpansionSystem.scoreTerritoryDecision(state, "BRIGHT_YELLOW", "frontier");
        assertEquals("DEFEND", score.action());
        assertTrue(score.decisiveFactors().stream().anyMatch(value -> value.contains("isolated")));

        frontier.supplySource = true;
        StrategicCampaignExpansionSystem.recalculateSupply(state, "BRIGHT_YELLOW");
        assertEquals(StrategicCampaignExpansionSystem.SupplyState.SUPPLIED, frontier.supplyState);
        assertTrue(StrategicCampaignExpansionSystem.operationLegality(state,
                StrategicCampaignExpansionSystem.OperationType.INVASION,
                "BRIGHT_YELLOW", "frontier", "well").legal());
    }

    @Test
    void settlementPartitionAndCollapseOutcomesAreStateDrivenAndPersisted() {
        StrategicCampaignExpansionSystem.State settlement = StrategicCampaignExpansionSystem.bootstrap(18L);
        settlement.civilWarCeasefire = true;
        assertEquals(StrategicCampaignExpansionSystem.CivilWarOutcome.NEGOTIATED_SETTLEMENT,
                StrategicCampaignExpansionSystem.evaluateCivilWarOutcome(settlement));

        StrategicCampaignExpansionSystem.State partition = StrategicCampaignExpansionSystem.bootstrap(19L);
        partition.civilWarElapsedTicks = 100;
        assertEquals(StrategicCampaignExpansionSystem.CivilWarOutcome.PARTITION,
                StrategicCampaignExpansionSystem.evaluateCivilWarOutcome(partition));

        StrategicCampaignExpansionSystem.State collapse = StrategicCampaignExpansionSystem.bootstrap(20L);
        collapse.brightYellowExhaustion = 95;
        collapse.darkYellowExhaustion = 92;
        assertEquals(StrategicCampaignExpansionSystem.CivilWarOutcome.MUTUAL_COLLAPSE,
                StrategicCampaignExpansionSystem.evaluateCivilWarOutcome(collapse));
        StrategicCampaignExpansionSystem.State restored =
                StrategicCampaignExpansionSystem.restore(StrategicCampaignExpansionSystem.serialize(collapse), 20L);
        assertEquals(StrategicCampaignExpansionSystem.CivilWarOutcome.MUTUAL_COLLAPSE,
                restored.civilWarOutcome);
        assertEquals(95, restored.brightYellowExhaustion);
    }

    @Test
    void factualWarLedgerIsBoundedSearchableAndPersistent() {
        StrategicCampaignExpansionSystem.State state = StrategicCampaignExpansionSystem.bootstrap(21L);
        StrategicCampaignExpansionSystem.recordWarEvent(state, "evt-1", 4, "territory",
                "Frontier contested", "Bright and Dark Yellow fleets met at the frontier",
                "Control remains disputed", true);
        StrategicCampaignExpansionSystem.recordWarEvent(state, "evt-2", 5, "station",
                "Relay damaged", "The lunar relay lost primary power",
                "Sensor coverage reduced", false);

        assertEquals(1, StrategicCampaignExpansionSystem.warHistoryLines(state, 10, "relay").size());
        StrategicCampaignExpansionSystem.State restored =
                StrategicCampaignExpansionSystem.restore(StrategicCampaignExpansionSystem.serialize(state), 21L);
        assertEquals(2, restored.warEvents.size());
        assertTrue(StrategicCampaignExpansionSystem.warHistoryLines(restored, 1, "").get(0)
                .contains("Relay damaged"));
    }

    @Test
    void battleGeographyPersistsObjectivesCasualtiesWreckHazardsSurvivorsAndOccupation() {
        StrategicCampaignExpansionSystem.State state = StrategicCampaignExpansionSystem.bootstrap(211L);
        StrategicCampaignExpansionSystem.BattleReport report =
                StrategicCampaignExpansionSystem.recordBattle(state, "lunar-ambush", "Bright Fleet", "Dark Fleet");
        StrategicCampaignExpansionSystem.configureBattleReport(state, report, "well", 44,
                List.of("Hold relay", "Extract convoy"), 63, "Bright tactical withdrawal",
                90, 70, 8, "DARK_YELLOW");
        assertTrue(StrategicCampaignExpansionSystem.revisitBattleSite(state, report.id, 20, 3, "BRIGHT_YELLOW"));
        assertEquals(70, report.salvageRemaining);
        assertEquals(5, report.survivorWindowTicks);
        assertTrue(StrategicCampaignExpansionSystem.battleSiteLines(state, "well").get(0).contains("casualties 63"));

        StrategicCampaignExpansionSystem.State restored =
                StrategicCampaignExpansionSystem.restore(StrategicCampaignExpansionSystem.serialize(state), 211L);
        StrategicCampaignExpansionSystem.BattleReport saved = restored.battleReports.stream()
                .filter(candidate -> candidate.id.equals(report.id)).findFirst().orElseThrow();
        assertEquals("well", saved.locationId);
        assertEquals(List.of("Hold relay", "Extract convoy"), saved.objectives);
        assertEquals("BRIGHT_YELLOW", saved.occupiedBy);
        assertEquals(70, saved.salvageRemaining);
    }

    @Test
    void rivalCommanderSurvivesDefeatRecoversAdaptsAndPersists() {
        StrategicCampaignExpansionSystem.State state = StrategicCampaignExpansionSystem.bootstrap(22L);
        StrategicCampaignExpansionSystem.RivalCommander commander = state.commanders.get("cmd-dark-rook");
        assertTrue(commander != null);

        assertTrue(StrategicCampaignExpansionSystem.recordCommanderEncounter(state, commander.id,
                false, true, "carrier screen"));
        assertEquals(StrategicCampaignExpansionSystem.CommanderStatus.RETREATING, commander.status);
        assertTrue(StrategicCampaignExpansionSystem.recoverCommander(state, commander.id, "dyc-resolute-ii"));
        assertTrue(StrategicCampaignExpansionSystem.recordCommanderEncounter(state, commander.id,
                true, false, "carrier screen"));
        assertEquals(1, commander.adaptationLevel);
        assertEquals(2, commander.encountersWithPlayer);

        StrategicCampaignExpansionSystem.State restored =
                StrategicCampaignExpansionSystem.restore(StrategicCampaignExpansionSystem.serialize(state), 22L);
        StrategicCampaignExpansionSystem.RivalCommander restoredCommander = restored.commanders.get(commander.id);
        assertEquals(StrategicCampaignExpansionSystem.CommanderStatus.ACTIVE, restoredCommander.status);
        assertEquals("dyc-resolute-ii", restoredCommander.flagshipId);
        assertEquals(1, restoredCommander.victories);
        assertEquals(1, restoredCommander.defeats);
        assertEquals(1, restoredCommander.adaptationLevel);
        assertFalse(restoredCommander.encounterMemories.isEmpty());
        assertFalse(restoredCommander.currentCountermeasure.equals("none"));
    }

    @Test
    void commanderLifecycleAssignmentPromotionCaptureDefectionAndDeathAreSafeAndRecorded() {
        StrategicCampaignExpansionSystem.State state = StrategicCampaignExpansionSystem.bootstrap(220L);
        StrategicCampaignExpansionSystem.RivalCommander commander =
                StrategicCampaignExpansionSystem.addCommander(state, "cmd-test", "Captain Ilex", "Captain",
                        "BRIGHT_YELLOW", "bright-flagship", "cautious");
        commander.aggression = 35;
        commander.caution = 75;
        assertTrue(StrategicCampaignExpansionSystem.assignCommanderToFleet(state, commander.id,
                "bright-frigate-7", "BRIGHT_YELLOW"));
        assertTrue(StrategicCampaignExpansionSystem.recordCommanderEncounter(state, commander.id,
                true, false, "missile saturation"));
        assertFalse(StrategicCampaignExpansionSystem.commanderAdaptation(commander).countermeasure().isBlank());
        assertTrue(StrategicCampaignExpansionSystem.promoteCommander(state, commander.id, "Commodore"));
        assertTrue(StrategicCampaignExpansionSystem.injureCommander(state, commander.id, 15));
        assertTrue(StrategicCampaignExpansionSystem.demoteCommander(state, commander.id, "Captain", "political dispute"));
        commander.rank = "Commodore";
        assertTrue(StrategicCampaignExpansionSystem.commanderOperationModifier(commander, "defend")
                > StrategicCampaignExpansionSystem.commanderOperationModifier(null, "defend"));

        assertTrue(StrategicCampaignExpansionSystem.changeCommanderStatus(state, commander.id,
                StrategicCampaignExpansionSystem.CommanderStatus.CAPTURED, "flagship disabled"));
        commander.status = StrategicCampaignExpansionSystem.CommanderStatus.ACTIVE;
        assertTrue(StrategicCampaignExpansionSystem.changeCommanderStatus(state, commander.id,
                StrategicCampaignExpansionSystem.CommanderStatus.DEFECTED, "political reversal"));
        commander.status = StrategicCampaignExpansionSystem.CommanderStatus.ACTIVE;
        assertTrue(StrategicCampaignExpansionSystem.changeCommanderStatus(state, commander.id,
                StrategicCampaignExpansionSystem.CommanderStatus.DEAD, "lost during evacuation"));
        assertFalse(StrategicCampaignExpansionSystem.changeCommanderStatus(state, commander.id,
                StrategicCampaignExpansionSystem.CommanderStatus.ACTIVE, "invalid resurrection"));
        assertTrue(state.warEvents.stream().anyMatch(event -> event.category.equals("commander")));

        StrategicCampaignExpansionSystem.State restored =
                StrategicCampaignExpansionSystem.restore(StrategicCampaignExpansionSystem.serialize(state), 220L);
        StrategicCampaignExpansionSystem.RivalCommander saved = restored.commanders.get(commander.id);
        assertEquals(StrategicCampaignExpansionSystem.CommanderStatus.DEAD, saved.status);
        assertEquals("Commodore", saved.rank);
        assertFalse(saved.serviceHistory.isEmpty());
        assertEquals(0, saved.strategicAuthority);
    }

    @Test
    void commanderPoliticsCanNegotiateExchangeDefectSeekRevengeAndOpposeDirector() {
        StrategicCampaignExpansionSystem.State state = StrategicCampaignExpansionSystem.bootstrap(221L);
        StrategicCampaignExpansionSystem.RivalCommander commander = state.commanders.get("cmd-dark-rook");
        commander.warExhaustion = 60;
        commander.caution = 80;
        commander.aggression = 80;
        commander.loyalty = 20;
        commander.defeats = 2;
        assertTrue(StrategicCampaignExpansionSystem.commanderDiplomaticAction(state, commander.id,
                StrategicCampaignExpansionSystem.CommanderDiplomaticAction.NEGOTIATE, "Bright Yellow"));
        assertTrue(StrategicCampaignExpansionSystem.commanderDiplomaticAction(state, commander.id,
                StrategicCampaignExpansionSystem.CommanderDiplomaticAction.PRISONER_EXCHANGE, "Blue"));
        assertTrue(StrategicCampaignExpansionSystem.commanderDiplomaticAction(state, commander.id,
                StrategicCampaignExpansionSystem.CommanderDiplomaticAction.REVENGE, "player flagship"));
        assertTrue(StrategicCampaignExpansionSystem.commanderConflictsWithDirector(state, commander.id));
        assertTrue(StrategicCampaignExpansionSystem.commanderDiplomaticAction(state, commander.id,
                StrategicCampaignExpansionSystem.CommanderDiplomaticAction.DEFECT, "Bright Yellow"));
        assertEquals(StrategicCampaignExpansionSystem.CommanderStatus.DEFECTED, commander.status);
    }

    @Test
    void richTerritoryAndRouteMetadataRoundTripWithoutDuplicatingTheGraph() {
        StrategicCampaignExpansionSystem.State state = StrategicCampaignExpansionSystem.bootstrap(23L);
        StrategicCampaignExpansionSystem.Territory frontier =
                StrategicCampaignExpansionSystem.territory(state, "frontier");
        frontier.description = "Disputed civil-war trade and defense corridor";
        frontier.legitimacy = 64;
        frontier.resistance = 22;
        frontier.mineOutput = 47;
        frontier.tags.add("disputed");
        frontier.supportsConstruction = true;
        StrategicCampaignExpansionSystem.TravelLane lane = state.lanes.stream()
                .filter(candidate -> candidate.from.equals("frontier") && candidate.to.equals("well"))
                .findFirst().orElseThrow();
        lane.directed = true;
        lane.travelCost = 19;
        lane.supplyCost = 14;
        lane.transitRisk = 38;
        lane.routeCapacity = 55;
        lane.civilianTravelAllowed = false;

        assertTrue(StrategicCampaignExpansionSystem.adjacentTerritoryIds(state, "frontier").contains("well"));
        assertFalse(StrategicCampaignExpansionSystem.adjacentTerritoryIds(state, "well").contains("frontier"));
        StrategicCampaignExpansionSystem.State restored =
                StrategicCampaignExpansionSystem.restore(StrategicCampaignExpansionSystem.serialize(state), 23L);
        StrategicCampaignExpansionSystem.Territory restoredFrontier =
                StrategicCampaignExpansionSystem.territory(restored, "frontier");
        assertEquals(frontier.description, restoredFrontier.description);
        assertEquals(64, restoredFrontier.legitimacy);
        assertEquals(47, restoredFrontier.mineOutput);
        assertTrue(restoredFrontier.tags.contains("disputed"));
        StrategicCampaignExpansionSystem.TravelLane restoredLane = restored.lanes.stream()
                .filter(candidate -> candidate.from.equals("frontier") && candidate.to.equals("well"))
                .findFirst().orElseThrow();
        assertTrue(restoredLane.directed);
        assertEquals(55, restoredLane.routeCapacity);
        assertFalse(restoredLane.civilianTravelAllowed);
        assertTrue(StrategicCampaignExpansionSystem.validateTerritoryGraph(restored).isEmpty());
    }

    @Test
    void frontPressureSeparatesAttackOpportunityFromDefensiveUrgencyAndExplainsFactors() {
        StrategicCampaignExpansionSystem.State state = StrategicCampaignExpansionSystem.bootstrap(231L);
        StrategicCampaignExpansionSystem.Territory frontier =
                StrategicCampaignExpansionSystem.territory(state, "frontier");
        frontier.friendlyFleetStrength = 25;
        frontier.enemyFleetStrength = 120;
        frontier.fleetReadiness = 35;
        frontier.fleetDamage = 55;
        frontier.ammunition = 30;
        frontier.reinforcementTime = 80;
        frontier.infrastructure = 30;
        frontier.shipyardCapacity = 10;
        frontier.morale = 35;
        frontier.legitimacy = 40;
        frontier.resistance = 70;
        frontier.recentEconomicDisruption = 60;
        frontier.recentCivilianConsequences = 45;
        frontier.notableCommanderId = "cmd-dark-rook";
        frontier.doctrineMatchup = -30;
        StrategicCampaignExpansionSystem.FrontPressureBreakdown breakdown =
                StrategicCampaignExpansionSystem.frontPressureBreakdown(state, "frontier");
        assertTrue(breakdown.defensiveUrgency() > breakdown.attackOpportunity());
        assertFalse(breakdown.decisiveFactors().isEmpty());
        assertTrue(StrategicCampaignExpansionSystem.debugTerritoryLine(state, "frontier").contains("pressure"));

        StrategicCampaignExpansionSystem.State restored =
                StrategicCampaignExpansionSystem.restore(StrategicCampaignExpansionSystem.serialize(state), 231L);
        assertEquals(120, StrategicCampaignExpansionSystem.territory(restored, "frontier").enemyFleetStrength);
        assertEquals("cmd-dark-rook", StrategicCampaignExpansionSystem.territory(restored, "frontier").notableCommanderId);
    }

    @Test
    void directorsUseLimitedIntelLegalTargetsBudgetsDeterministicScoresAndPersistPlans() {
        StrategicCampaignExpansionSystem.State first = StrategicCampaignExpansionSystem.bootstrap(232L);
        StrategicCampaignExpansionSystem.State second = StrategicCampaignExpansionSystem.bootstrap(232L);
        first.directors.get("Bright Yellow").intelligenceCoverage = 100;
        second.directors.get("Bright Yellow").intelligenceCoverage = 100;
        StrategicCampaignExpansionSystem.DirectorPlan a =
                StrategicCampaignExpansionSystem.planDirectorTurn(first, "BRIGHT_YELLOW");
        StrategicCampaignExpansionSystem.DirectorPlan b =
                StrategicCampaignExpansionSystem.planDirectorTurn(second, "BRIGHT_YELLOW");
        assertEquals(a.operationType(), b.operationType());
        assertEquals(a.originId(), b.originId());
        assertEquals(a.targetId(), b.targetId());
        assertEquals(a.score(), b.score());
        assertFalse(a.decisiveFactors().isEmpty());
        if (a.operationType() == StrategicCampaignExpansionSystem.OperationType.RAID
                || a.operationType() == StrategicCampaignExpansionSystem.OperationType.INVASION) {
            assertTrue(StrategicCampaignExpansionSystem.operationLegality(first, a.operationType(),
                    a.faction(), a.originId(), a.targetId()).legal());
        }
        StrategicCampaignExpansionSystem.StrategicOperation committed =
                StrategicCampaignExpansionSystem.commitDirectorPlan(first, a, "bright-director-fleet");
        assertTrue(committed != null);

        StrategicCampaignExpansionSystem.State restored =
                StrategicCampaignExpansionSystem.restore(StrategicCampaignExpansionSystem.serialize(first), 232L);
        assertFalse(restored.directors.get("Bright Yellow").committedPlan.isBlank());
        assertEquals(first.directors.get("Bright Yellow").resourceBudget,
                restored.directors.get("Bright Yellow").resourceBudget);
    }

    @Test
    void randomizedGraphsNeverExposeNonAdjacentCaptureTargets() {
        Random random = new Random(233L);
        for (int sample = 0; sample < 100; sample++) {
            StrategicCampaignExpansionSystem.State state = new StrategicCampaignExpansionSystem.State();
            int count = 5 + random.nextInt(10);
            for (int i = 0; i < count; i++) {
                String faction = i < 2 ? "TEAM_C" : (i % 2 == 0 ? "ENEMY" : "DARK_YELLOW");
                StrategicCampaignExpansionSystem.Territory territory =
                        new StrategicCampaignExpansionSystem.Territory("r" + i, "Random " + i, faction, faction);
                territory.locationIds.add(territory.id.value());
                territory.supportsInvasionStaging = true;
                state.territories.add(territory);
                if (i > 0) state.lanes.add(new StrategicCampaignExpansionSystem.TravelLane(
                        "r" + (i - 1), "r" + i,
                        StrategicCampaignExpansionSystem.LaneType.TRAVEL_LANE, true));
            }
            for (int i = 0; i < count; i++) for (int j = i + 2; j < count; j++) {
                if (random.nextDouble() < 0.12) state.lanes.add(new StrategicCampaignExpansionSystem.TravelLane(
                        "r" + i, "r" + j, StrategicCampaignExpansionSystem.LaneType.JUMP_POINT, true));
            }
            for (String target : StrategicCampaignExpansionSystem.legalInvasionTargetIds(state, "TEAM_C")) {
                boolean hasLegalAdjacentOrigin = state.territories.stream()
                        .filter(territory -> "TEAM_C".equals(territory.controller))
                        .anyMatch(territory -> StrategicCampaignExpansionSystem.adjacentTerritoryIds(
                                state, territory.id.value()).contains(target));
                assertTrue(hasLegalAdjacentOrigin, "fuzz sample exposed skipped target " + target);
            }
        }
    }

    @Test
    void headlessMultiYearSoakDetectsNoIllegalCaptureOrRunawayFaction() {
        StrategicCampaignExpansionSystem.StrategicSoakReport report =
                StrategicCampaignExpansionSystem.runHeadlessStrategicSoak(234L, 1200);
        assertTrue(report.passed(), report.diagnostics().toString());
        assertEquals(0, report.illegalCaptures());
        assertTrue(report.operationsResolved() > 0);
    }

    @Test
    void completedOperationHistoryIsBoundedUnderHighChurn() {
        StrategicCampaignExpansionSystem.State state = StrategicCampaignExpansionSystem.bootstrap(235L);
        for (int i = 0; i < 700; i++) {
            StrategicCampaignExpansionSystem.StrategicOperation raid =
                    StrategicCampaignExpansionSystem.startOperation(state,
                            StrategicCampaignExpansionSystem.OperationType.RAID,
                            "BRIGHT_YELLOW", "frontier", "well");
            assertTrue(raid != null);
            StrategicCampaignExpansionSystem.resolveOperation(state, raid.id,
                    i % 3 == 0 ? StrategicCampaignExpansionSystem.OperationResolution.INTERCEPTED
                            : StrategicCampaignExpansionSystem.OperationResolution.SUCCESS,
                    0, 0);
        }
        assertTrue(state.operations.size() <= 512);
        assertTrue(state.warEvents.size() <= 512);
    }

    @Test
    void pathAndSupplyQueriesRespectBlockadesDirectionCapacityAndAlliedAccess() {
        StrategicCampaignExpansionSystem.State state = StrategicCampaignExpansionSystem.bootstrap(24L);
        StrategicCampaignExpansionSystem.Territory well = StrategicCampaignExpansionSystem.territory(state, "well");
        well.owner = "TEAM_C";
        well.controller = "TEAM_C";
        well.supplySource = false;
        StrategicCampaignExpansionSystem.Territory frontier = StrategicCampaignExpansionSystem.territory(state, "frontier");
        frontier.supplySource = false;
        StrategicCampaignExpansionSystem.TravelLane choke = state.lanes.stream()
                .filter(candidate -> candidate.from.equals("frontier") && candidate.to.equals("well"))
                .findFirst().orElseThrow();

        StrategicCampaignExpansionSystem.recalculateSupply(state, "TEAM_C");
        assertEquals(StrategicCampaignExpansionSystem.SupplyState.SUPPLIED, well.supplyState,
                "Green supply may traverse allied Bright Yellow territory");
        assertTrue(StrategicCampaignExpansionSystem.findTerritoryPath(state, "TEAM_C", "shelter", "well", true).reachable());

        choke.blockaded = true;
        StrategicCampaignExpansionSystem.recalculateSupply(state, "TEAM_C");
        assertEquals(StrategicCampaignExpansionSystem.SupplyState.ISOLATED, well.supplyState);
        assertFalse(StrategicCampaignExpansionSystem.findTerritoryPath(state, "TEAM_C", "shelter", "well", true).reachable());
        assertTrue(well.supplyReason.contains("No allied route"));

        choke.blockaded = false;
        choke.routeCapacity = 5;
        choke.supplyCost = 10;
        StrategicCampaignExpansionSystem.recalculateSupply(state, "TEAM_C");
        assertEquals(StrategicCampaignExpansionSystem.SupplyState.ISOLATED, well.supplyState);
    }

    @Test
    void controlLifecycleSupportsPressureContestOccupationCounterattackAndIntegration() {
        StrategicCampaignExpansionSystem.State state = StrategicCampaignExpansionSystem.bootstrap(25L);
        StrategicCampaignExpansionSystem.Territory well = StrategicCampaignExpansionSystem.territory(state, "well");
        int initialInfrastructure = well.infrastructure;

        StrategicCampaignExpansionSystem.updateTerritoryControl(state, "well", "BRIGHT_YELLOW",
                new StrategicCampaignExpansionSystem.ControlFactors(100, 70, 50, 0, 0, 50));
        assertTrue(well.controlState == StrategicCampaignExpansionSystem.TerritoryControlState.PRESSURED
                || well.controlState == StrategicCampaignExpansionSystem.TerritoryControlState.CONTESTED);
        while (well.controlState != StrategicCampaignExpansionSystem.TerritoryControlState.OCCUPIED) {
            StrategicCampaignExpansionSystem.updateTerritoryControl(state, "well", "BRIGHT_YELLOW",
                    new StrategicCampaignExpansionSystem.ControlFactors(100, 70, 50, 0, 0, 50));
        }
        assertEquals("DARK_YELLOW", well.owner);
        assertEquals("BRIGHT_YELLOW", well.controller);
        assertFalse(well.supportsInvasionStaging, "occupation cannot immediately stage another invasion");
        assertTrue(well.infrastructure < initialInfrastructure);

        for (int i = 0; i < 5 && !well.controller.equals(well.owner); i++) {
            StrategicCampaignExpansionSystem.updateTerritoryControl(state, "well", "DARK_YELLOW",
                    new StrategicCampaignExpansionSystem.ControlFactors(100, 100, 60, 60, 60, 60));
        }
        assertEquals("DARK_YELLOW", well.controller, "a successful counterattack restores the political owner");

        // Reoccupy, then require supplied consolidation to reach integration.
        for (int i = 0; i < 5 && well.controlState != StrategicCampaignExpansionSystem.TerritoryControlState.OCCUPIED; i++) {
            StrategicCampaignExpansionSystem.updateTerritoryControl(state, "well", "BRIGHT_YELLOW",
                    new StrategicCampaignExpansionSystem.ControlFactors(100, 100, 60, 0, 0, 60));
        }
        well.supplyState = StrategicCampaignExpansionSystem.SupplyState.SUPPLIED;
        assertTrue(StrategicCampaignExpansionSystem.advanceOccupationIntegration(state, "well", 100));
        assertEquals(StrategicCampaignExpansionSystem.TerritoryControlState.INTEGRATED, well.controlState);
        assertEquals("BRIGHT_YELLOW", well.owner);
        assertTrue(state.warEvents.stream().anyMatch(event -> event.category.equals("territory")));
    }

    @Test
    void occupiedOrContestedOriginsCannotLaunchFurtherInvasions() {
        StrategicCampaignExpansionSystem.State state = StrategicCampaignExpansionSystem.bootstrap(26L);
        StrategicCampaignExpansionSystem.Territory frontier = StrategicCampaignExpansionSystem.territory(state, "frontier");
        frontier.controlState = StrategicCampaignExpansionSystem.TerritoryControlState.CONTESTED;
        assertFalse(StrategicCampaignExpansionSystem.operationLegality(state,
                StrategicCampaignExpansionSystem.OperationType.INVASION,
                "BRIGHT_YELLOW", "frontier", "well").legal());
        frontier.controlState = StrategicCampaignExpansionSystem.TerritoryControlState.OCCUPIED;
        assertFalse(StrategicCampaignExpansionSystem.operationLegality(state,
                StrategicCampaignExpansionSystem.OperationType.RAID,
                "BRIGHT_YELLOW", "frontier", "well").legal());
    }

    @Test
    void supplyGradesAffectReadinessAndEmergencyMechanismsExpire() {
        StrategicCampaignExpansionSystem.State state = StrategicCampaignExpansionSystem.bootstrap(27L);
        StrategicCampaignExpansionSystem.Territory well = StrategicCampaignExpansionSystem.territory(state, "well");
        well.controller = "TEAM_C";
        well.owner = "TEAM_C";
        well.supplySource = false;
        StrategicCampaignExpansionSystem.Territory frontier = StrategicCampaignExpansionSystem.territory(state, "frontier");
        frontier.supplySource = false;
        StrategicCampaignExpansionSystem.TravelLane choke = state.lanes.stream()
                .filter(lane -> lane.from.equals("frontier") && lane.to.equals("well"))
                .findFirst().orElseThrow();
        choke.routeCapacity = 45;
        choke.supplyCost = 20;

        StrategicCampaignExpansionSystem.recalculateSupply(state, "TEAM_C");
        assertEquals(StrategicCampaignExpansionSystem.SupplyState.STRAINED, well.supplyState);
        StrategicCampaignExpansionSystem.SupplyEffects strained = StrategicCampaignExpansionSystem.supplyEffects(well);
        assertTrue(strained.repair() < 1.0 && strained.invasionReadiness() > 0.0);

        choke.routeCapacity = 25;
        StrategicCampaignExpansionSystem.recalculateSupply(state, "TEAM_C");
        assertEquals(StrategicCampaignExpansionSystem.SupplyState.UNDERSUPPLIED, well.supplyState);
        assertTrue(StrategicCampaignExpansionSystem.supplyEffects(well).construction() < strained.construction());

        choke.blockaded = true;
        StrategicCampaignExpansionSystem.recalculateSupply(state, "TEAM_C");
        assertEquals(StrategicCampaignExpansionSystem.SupplyState.ISOLATED, well.supplyState);
        assertTrue(StrategicCampaignExpansionSystem.applyEmergencySupply(state, "well",
                StrategicCampaignExpansionSystem.EmergencySupplyType.RELIEF));
        assertEquals(StrategicCampaignExpansionSystem.SupplyState.STRAINED, well.supplyState);
        assertTrue(well.supplyReason.contains("Emergency RELIEF"));

        StrategicCampaignExpansionSystem.State restored =
                StrategicCampaignExpansionSystem.restore(StrategicCampaignExpansionSystem.serialize(state), 27L);
        assertEquals(StrategicCampaignExpansionSystem.EmergencySupplyType.RELIEF,
                StrategicCampaignExpansionSystem.territory(restored, "well").emergencySupplyType);
        for (int i = 0; i < 7; i++) StrategicCampaignExpansionSystem.advanceSupplyTick(restored);
        assertEquals(StrategicCampaignExpansionSystem.SupplyState.ISOLATED,
                StrategicCampaignExpansionSystem.territory(restored, "well").supplyState);
    }

    @Test
    void everyControlAndSupplyStateRoundTrips() {
        StrategicCampaignExpansionSystem.State state = StrategicCampaignExpansionSystem.bootstrap(28L);
        for (int i = 0; i < state.territories.size(); i++) {
            StrategicCampaignExpansionSystem.Territory territory = state.territories.get(i);
            territory.controlState = StrategicCampaignExpansionSystem.TerritoryControlState.values()[i];
            territory.supplyState = StrategicCampaignExpansionSystem.SupplyState.values()[i];
            territory.controlProgress = i * 40;
            territory.infrastructure = 100 - i * 15;
        }
        StrategicCampaignExpansionSystem.State restored =
                StrategicCampaignExpansionSystem.restore(StrategicCampaignExpansionSystem.serialize(state), 28L);
        for (int i = 0; i < restored.territories.size(); i++) {
            assertEquals(StrategicCampaignExpansionSystem.TerritoryControlState.values()[i], restored.territories.get(i).controlState);
            assertEquals(StrategicCampaignExpansionSystem.SupplyState.values()[i], restored.territories.get(i).supplyState);
            assertEquals(100 - i * 15, restored.territories.get(i).infrastructure);
        }
    }

    @Test
    void alliedTransitDoesNotGrantCaptureRightsAndGraphValidatorFindsDisconnectedComponents() {
        StrategicCampaignExpansionSystem.State state = StrategicCampaignExpansionSystem.bootstrap(29L);
        assertTrue(StrategicCampaignExpansionSystem.findTerritoryPath(
                state, "TEAM_C", "shelter", "frontier", false).reachable());
        assertFalse(StrategicCampaignExpansionSystem.operationLegality(state,
                StrategicCampaignExpansionSystem.OperationType.INVASION,
                "TEAM_C", "shelter", "frontier").legal());

        StrategicCampaignExpansionSystem.TravelLane hidden = state.lanes.stream()
                .filter(lane -> lane.to.equals("anomaly"))
                .findFirst().orElseThrow();
        state.lanes.remove(hidden);
        assertTrue(StrategicCampaignExpansionSystem.validateTerritoryGraph(state).stream()
                .anyMatch(issue -> issue.contains("disconnected territory anomaly")));
    }

    @Test
    void cyclicGraphWithMultipleSupplySourcesRecalculatesDeterministically() {
        StrategicCampaignExpansionSystem.State state = StrategicCampaignExpansionSystem.bootstrap(30L);
        StrategicCampaignExpansionSystem.Territory well = StrategicCampaignExpansionSystem.territory(state, "well");
        well.owner = "TEAM_C";
        well.controller = "TEAM_C";
        well.supplySource = true;
        state.lanes.add(new StrategicCampaignExpansionSystem.TravelLane(
                "well", "shelter", StrategicCampaignExpansionSystem.LaneType.JUMP_POINT, true));

        StrategicCampaignExpansionSystem.recalculateSupply(state, "TEAM_C");
        List<StrategicCampaignExpansionSystem.SupplyState> first = state.territories.stream()
                .map(territory -> territory.supplyState).toList();
        StrategicCampaignExpansionSystem.recalculateSupply(state, "TEAM_C");
        List<StrategicCampaignExpansionSystem.SupplyState> second = state.territories.stream()
                .map(territory -> territory.supplyState).toList();

        assertEquals(first, second);
        assertEquals(StrategicCampaignExpansionSystem.SupplyState.SUPPLIED, well.supplyState);
        assertTrue(StrategicCampaignExpansionSystem.validateTerritoryGraph(state).isEmpty());
    }

    @Test
    void threeTerritoryAcceptanceHarnessOpensAndClosesFrontierLegally() {
        List<String> evidence = TerritoryFrontAcceptanceHarness.run();
        assertTrue(evidence.stream().anyMatch(line -> line.startsWith("PASS")));
    }
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
