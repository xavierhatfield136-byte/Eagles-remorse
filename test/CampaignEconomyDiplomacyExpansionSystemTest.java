import app.config.GameConfig;
import app.config.GameMode;
import app.persistence.CampaignCheckpointStore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignEconomyDiplomacyExpansionSystemTest {
    @Test
    void logisticsModelTracksSplitStoresCargoReadinessRationingAndBlockadePressure() {
        EconomyLogisticsIndustrySystem.State state = EconomyLogisticsIndustrySystem.bootstrap(11L);
        EconomyLogisticsIndustrySystem.LogisticsLedger ledger = state.logistics;

        assertEquals(EconomyLogisticsIndustrySystem.Resource.values().length, ledger.stores.size());
        assertEquals(EconomyLogisticsIndustrySystem.Resource.values().length, ledger.cargoAllocation.size());
        assertTrue(ledger.cargoCapacity > 0);
        int fuelBefore = ledger.stores.get(EconomyLogisticsIndustrySystem.Resource.FUEL);
        ledger.emergencyRationing = true;
        EconomyLogisticsIndustrySystem.consumeRoute(state, EconomyLogisticsIndustrySystem.Formation.TIGHT,
                95, EconomyLogisticsIndustrySystem.RouteType.BLOCKADED);
        assertTrue(ledger.stores.get(EconomyLogisticsIndustrySystem.Resource.FUEL) < fuelBefore);
        assertTrue(ledger.blockadeStarvation > 0);
        assertTrue(ledger.crewFatigue > 0);
        ledger.maintenanceDebt = 20;
        ledger.sparePartsShortage = 12;
        EconomyLogisticsIndustrySystem.resupply(state, EconomyLogisticsIndustrySystem.InstallationQuality.FLEET_YARD);
        assertTrue(ledger.maintenanceDebt < 20);
        assertTrue(ledger.sparePartsShortage < 12);
        assertFalse(state.readinessCurve.isBlank());
    }

    @Test
    void miningSalvageMarketsAndContractBoardExposeSectionSixChoices() {
        EconomyLogisticsIndustrySystem.State state = EconomyLogisticsIndustrySystem.bootstrap(22L);

        assertTrue(state.miningClaims.stream().anyMatch(claim -> claim.grade == EconomyLogisticsIndustrySystem.OreGrade.HIGH));
        assertTrue(state.miningClaims.stream().anyMatch(claim -> claim.volatileDeposit));
        assertTrue(state.miningClaims.stream().anyMatch(claim -> !claim.rareMaterial.isBlank()));
        assertTrue(state.miningClaims.stream().anyMatch(claim -> claim.contestedContract && claim.miningDrones > 0
                && claim.specializedMiningFleet && claim.refineryThroughput > 0));
        assertTrue(EconomyLogisticsIndustrySystem.surveyClaim(state, "frontier-belt"));

        EconomyLogisticsIndustrySystem.WreckRecovery wreck = EconomyLogisticsIndustrySystem.recoverWreck(
                state, "wreck-7", EconomyLogisticsIndustrySystem.SalvageMethod.CAREFUL_RECOVERY, true);
        assertTrue(wreck.blackBoxRecovered);
        assertTrue(wreck.survivorsRecovered > 0);
        assertTrue(wreck.hazardous);
        assertTrue(wreck.reputationDelta < 0);
        assertTrue(state.logistics.salvageProcessingHours > 0);

        int marketFuel = state.markets.get("southern-shelter").prices.get(EconomyLogisticsIndustrySystem.Resource.FUEL);
        EconomyLogisticsIndustrySystem.applySupplyShock(state, "southern-shelter", 40);
        assertTrue(state.markets.get("southern-shelter").prices.get(EconomyLogisticsIndustrySystem.Resource.FUEL) > marketFuel);
        assertTrue(state.markets.values().stream().anyMatch(market -> market.hullInsuranceAvailable));
        assertEquals(EconomyLogisticsIndustrySystem.ContractType.values().length, state.contractBoard.size());
        assertTrue(state.contractBoard.stream().allMatch(contract -> contract.deadlineHours > 0
                && contract.collateral > 0 && contract.reputationStake > 0 && !contract.chainId.isBlank()
                && contract.competingBidders > 0));
        assertTrue(EconomyLogisticsIndustrySystem.negotiateContract(state, "contract-escort", "Priority escort lane", 12));
    }

    @Test
    void campaignClockMovesMarketsAndFiniteAiReservePaysForDeployments() {
        EconomyLogisticsIndustrySystem.State state = EconomyLogisticsIndustrySystem.bootstrap(23L);
        int shock = state.markets.get("lunar-blockade").supplyShockPercent;
        int deadline = state.contractBoard.get(0).deadlineHours;
        EconomyLogisticsIndustrySystem.advanceCampaignTime(state, 7200.0, 65);
        assertTrue(state.markets.get("lunar-blockade").supplyShockPercent < shock);
        assertTrue(state.contractBoard.get(0).deadlineHours < deadline);
        int reserve = state.aiDeploymentReserve;
        assertTrue(EconomyLogisticsIndustrySystem.payForAiDeployment(state, 3));
        assertTrue(state.aiDeploymentReserve < reserve);
        while (EconomyLogisticsIndustrySystem.payForAiDeployment(state, 4)) {
            // Exhaust the bounded reinforcement reserve.
        }
        assertTrue(state.aiDeploymentReserve < 14);
        assertFalse(EconomyLogisticsIndustrySystem.payForAiDeployment(state, 4));
    }

    @Test
    void diplomacyAndReactiveStoryTrackRelationshipsRecurringCaptainsAndEndings() {
        DiplomacyNarrativeCrewSystem.State state = DiplomacyNarrativeCrewSystem.bootstrap(33L);

        assertEquals(DiplomacyNarrativeCrewSystem.ReputationGroup.values().length, state.relationships.reputation.size());
        DiplomacyNarrativeCrewSystem.changeReputation(state, DiplomacyNarrativeCrewSystem.ReputationGroup.CIVILIAN,
                7, "Rescued lunar evacuees");
        assertEquals(57, state.relationships.reputation.get(DiplomacyNarrativeCrewSystem.ReputationGroup.CIVILIAN));
        assertFalse(state.relationships.visibleReasons.isEmpty());
        assertTrue(state.relationships.favors.isEmpty());
        assertTrue(state.relationships.obligations.isEmpty());
        assertTrue(state.relationships.factionRequests.isEmpty());
        assertTrue(state.relationships.negotiationScenes.isEmpty());
        assertFalse(state.relationships.ceasefire);
        assertFalse(state.relationships.temporaryAlliance);
        assertEquals(0, state.relationships.betrayalRiskPercent);
        assertFalse(state.relationships.salvageRightsDispute);
        assertTrue(state.relationships.diplomaticMissions.isEmpty());

        DiplomacyNarrativeCrewSystem.rememberEncounter(state, "rook", "Player broke the blockade.");
        DiplomacyNarrativeCrewSystem.recordRescueReturn(state, "voss");
        DiplomacyNarrativeCrewSystem.advanceRevengeArc(state, "rook");
        assertTrue(state.npcCaptains.get("rook").rivalCommander);
        assertEquals(1, state.npcCaptains.get("rook").encounterMemories.size());
        assertEquals(1, state.npcCaptains.get("voss").rescueReturns);
        assertEquals(1, state.npcCaptains.get("rook").revengeArcStage);
        assertTrue(state.newsBulletins.isEmpty());
        assertTrue(state.crewCommentary.isEmpty());
        assertTrue(state.dynamicMissionBriefings.isEmpty());
        assertTrue(state.authoredStoryBeats.isEmpty());
        DiplomacyNarrativeCrewSystem.resolveEnding(state, 80, 3, 2, "Rescue priority");
        assertEquals("Coalition restoration", state.campaignEnding);
        assertEquals(2, state.epilogueTimeline.size());
    }

    @Test
    void crewLayerTracksOfficersOpinionsStressReplacementLogsVoiceAndQuietMode() {
        DiplomacyNarrativeCrewSystem.State state = DiplomacyNarrativeCrewSystem.bootstrap(44L);

        assertEquals(DiplomacyNarrativeCrewSystem.CrewStation.values().length, state.officers.size());
        assertTrue(state.officers.values().stream().allMatch(officer -> !officer.name.isBlank()
                && !officer.portrait.isBlank() && officer.specialty != null && !officer.opinion.isBlank()
                && !officer.tacticalRecommendation.isBlank() && officer.voicedBriefingAvailable));
        DiplomacyNarrativeCrewSystem.recordDecision(state, "Captain chose relief over salvage",
                DiplomacyNarrativeCrewSystem.ArrivalState.DEPLETED, 9);
        assertEquals(DiplomacyNarrativeCrewSystem.ArrivalState.DEPLETED, state.lastArrivalState);
        assertTrue(state.officers.values().stream().allMatch(officer -> officer.stress >= 21
                && officer.captainLogEntries.size() == 1));
        assertFalse(state.officerDisagreements.isEmpty());
        DiplomacyNarrativeCrewSystem.replaceOfficer(state, DiplomacyNarrativeCrewSystem.CrewStation.ENGINEERING,
                "Ari Sol", "engineering_alt_01.png");
        assertTrue(state.officers.get(DiplomacyNarrativeCrewSystem.CrewStation.ENGINEERING).casualtyReplacement);
        state.banterFrequencyPercent = 0;
        state.quietMode = true;
        assertTrue(DiplomacyNarrativeCrewSystem.commandBoardLines(state).stream().anyMatch(line -> line.contains("quiet")));
    }

    @Test
    void serializersPreserveMutableEconomyAndCrewFields() {
        EconomyLogisticsIndustrySystem.State economy = EconomyLogisticsIndustrySystem.bootstrap(55L);
        economy.logistics.maintenanceDebt = 19;
        economy.logistics.blackMarketProcurement = 4;
        EconomyLogisticsIndustrySystem.State economyRestored =
                EconomyLogisticsIndustrySystem.restore(EconomyLogisticsIndustrySystem.serialize(economy), 55L);
        assertEquals(19, economyRestored.logistics.maintenanceDebt);
        assertEquals(4, economyRestored.logistics.blackMarketProcurement);

        DiplomacyNarrativeCrewSystem.State diplomacy = DiplomacyNarrativeCrewSystem.bootstrap(66L);
        DiplomacyNarrativeCrewSystem.changeReputation(diplomacy, DiplomacyNarrativeCrewSystem.ReputationGroup.MILITARY,
                13, "Held the line");
        diplomacy.quietMode = true;
        DiplomacyNarrativeCrewSystem.replaceOfficer(diplomacy, DiplomacyNarrativeCrewSystem.CrewStation.HELM,
                "Lio Park", "helm_alt_01.png");
        DiplomacyNarrativeCrewSystem.State diplomacyRestored =
                DiplomacyNarrativeCrewSystem.restore(DiplomacyNarrativeCrewSystem.serialize(diplomacy), 66L);
        assertEquals(63, diplomacyRestored.relationships.reputation.get(DiplomacyNarrativeCrewSystem.ReputationGroup.MILITARY));
        assertTrue(diplomacyRestored.quietMode);
        assertEquals("Lio Park", diplomacyRestored.officers.get(DiplomacyNarrativeCrewSystem.CrewStation.HELM).name);
    }

    @Test
    void campaignCheckpointPersistsEconomyAndDiplomacyExpansions() throws Exception {
        GameContext source = campaignContext();
        source.campaign.economyExpansion.logistics.maintenanceDebt = 27;
        source.campaign.economyExpansion.logistics.emergencyRationing = true;
        DiplomacyNarrativeCrewSystem.changeReputation(source.campaign.diplomacyNarrative,
                DiplomacyNarrativeCrewSystem.ReputationGroup.POLITICAL, -11, "Rejected summit terms");
        source.campaign.diplomacyNarrative.quietMode = true;

        CampaignCheckpointStore.Checkpoint checkpoint = captureCheckpoint(source, 4);
        GameContext restored = campaignContext();
        assertTrue(applyCheckpoint(restored, checkpoint));

        assertEquals(27, restored.campaign.economyExpansion.logistics.maintenanceDebt);
        assertTrue(restored.campaign.economyExpansion.logistics.emergencyRationing);
        assertEquals(39, restored.campaign.diplomacyNarrative.relationships.reputation
                .get(DiplomacyNarrativeCrewSystem.ReputationGroup.POLITICAL));
        assertTrue(restored.campaign.diplomacyNarrative.quietMode);
        assertTrue(CampaignSystem.campaignEconomyExpansionLines(restored).stream().anyMatch(line -> line.contains("Readiness")));
        assertTrue(CampaignSystem.campaignDiplomacyNarrativeLines(restored).stream().anyMatch(line -> line.contains("Reputation")));
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
