import app.config.GameConfig;
import app.config.GameMode;
import app.persistence.CampaignCheckpointStore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationsInformationCommandSystemTest {
    @Test
    void missionCatalogCoversFamiliesBattlefieldIdentityAndProceduralComposition() {
        OperationsInformationCommandSystem.State state = OperationsInformationCommandSystem.bootstrap(81L);

        assertEquals(OperationsInformationCommandSystem.MissionFamily.values().length, state.missionTemplates.size());
        assertEquals(20, state.missionTemplates.size());
        assertTrue(state.missionTemplates.stream().anyMatch(template -> template.routeDecision));
        assertTrue(state.missionTemplates.stream().anyMatch(template -> template.stages > 1));
        assertEquals(OperationsInformationCommandSystem.BattlefieldType.values().length, state.battlefields.size());
        assertTrue(state.battlefields.stream().allMatch(template -> !template.factionArchitecture.isBlank()
                && template.civilianTrafficLanes && template.destructibleInfrastructure
                && template.neutralCollateralStructures && !template.hazards.isEmpty()
                && !template.persistentBattleScar.isBlank() && !template.audioAmbience.isBlank()));
        assertTrue(state.battlefields.stream().flatMap(template -> template.hazards.stream())
                .distinct().count() >= OperationsInformationCommandSystem.SpaceHazard.values().length);
        OperationsInformationCommandSystem.CompositionRule rule = state.compositionRules.get(0);
        assertFalse(rule.representedCampaignForce.isBlank());
        assertFalse(rule.doctrineReinforcements.isBlank());
        assertFalse(rule.factionFormation.isBlank());
        assertFalse(rule.hiddenStatInflation);
        assertTrue(rule.environmentalCompatibilityChecked);
        assertTrue(rule.civilianPresenceRule);
        assertEquals(81L, rule.deterministicSeed);
    }

    @Test
    void informationWarfareTracksModesSignaturesUncertaintyRelaysAndAdaptation() {
        OperationsInformationCommandSystem.State state = OperationsInformationCommandSystem.bootstrap(82L);

        assertEquals(OperationsInformationCommandSystem.SensorMode.PASSIVE, state.intelligence.sensorProfile.mode);
        assertFalse(state.intelligence.sensorProfile.hullSignature.isBlank());
        assertTrue(state.intelligence.sensorProfile.silentRunningPenalty > 0);
        assertTrue(state.intelligence.contacts.stream().anyMatch(contact ->
                contact.classification == OperationsInformationCommandSystem.ContactClassification.FALSE_POSITIVE));
        assertTrue(state.intelligence.contacts.stream().anyMatch(contact -> contact.merged && contact.split
                && contact.communicationsIntercepted && contact.jammingCone && contact.jammingAreaEffect));
        assertTrue(state.intelligence.contacts.stream().anyMatch(contact ->
                contact.classification == OperationsInformationCommandSystem.ContactClassification.DECOY_FLEET));
        assertTrue(state.intelligence.contacts.stream().anyMatch(contact ->
                contact.classification == OperationsInformationCommandSystem.ContactClassification.SPOOFED_TRANSPONDER));
        assertFalse(state.intelligence.relayPlacementDecisions.isEmpty());
        assertFalse(state.intelligence.scoutPatrolRoutes.isEmpty());
        assertFalse(state.intelligence.stealthApproachRoutes.isEmpty());
        assertFalse(state.intelligence.counterIntelligenceActions.isEmpty());
        assertFalse(state.intelligence.enemyAdaptations.isEmpty());
        OperationsInformationCommandSystem.activateSensors(state);
        assertEquals(OperationsInformationCommandSystem.SensorMode.ACTIVE, state.intelligence.sensorProfile.mode);
        assertTrue(state.intelligence.sensorProfile.emissionRiskPercent > 0);
        OperationsInformationCommandSystem.recordElectronicAttack(state);
        assertEquals(1, state.intelligence.electronicAttackStrikes);
        OperationsInformationCommandSystem.engageSilentRunning(state);
        assertEquals(OperationsInformationCommandSystem.SensorMode.PASSIVE, state.intelligence.sensorProfile.mode);
    }

    @Test
    void strikePackagesExposePreparationRequirementsSupportCounterplayAndReports() {
        OperationsInformationCommandSystem.State state = OperationsInformationCommandSystem.bootstrap(83L);
        OperationsInformationCommandSystem.StrikePackage strike = state.strikePackages.get(0);

        assertTrue(strike.preparationHours > 0);
        assertFalse(strike.launchPlatformRequirement.isBlank());
        assertTrue(strike.interceptionRiskPercent > 0);
        assertTrue(strike.targetQualityThreshold > 0);
        assertTrue(strike.collateralEstimate > 0);
        assertTrue(strike.decoyTargetRiskPercent > 0);
        assertTrue(strike.support.containsAll(List.of(OperationsInformationCommandSystem.SupportType.values())));
        assertTrue(strike.enemyCounterStrike);
        assertTrue(strike.strikeDefenseInstallation);
        assertFalse(strike.afterActionImagery.isBlank());
        assertFalse(strike.afterActionReport.isBlank());
    }

    @Test
    void liveAlphaMissionMatrixCoversRequiredFamiliesWithVisibleConsequences() {
        OperationsInformationCommandSystem.State state = OperationsInformationCommandSystem.bootstrap(86L);
        OperationsInformationCommandSystem.refreshLiveAlphaMissions(state, 4, 3, 2, 1, true);

        assertEquals(13, state.liveAlphaMissions.size());
        assertTrue(state.liveAlphaMissions.stream().allMatch(mission -> !mission.objective.isBlank()
                && !mission.reward.isBlank() && !mission.failure.isBlank() && !mission.aftermath.isBlank()
                && !mission.provenance.isBlank() && mission.warning.contains("Visible warning")));
        assertTrue(state.liveAlphaMissions.stream().anyMatch(mission ->
                mission.family == OperationsInformationCommandSystem.MissionFamily.STATION_EVACUATION));
        assertTrue(state.liveAlphaMissions.stream().anyMatch(mission ->
                mission.family == OperationsInformationCommandSystem.MissionFamily.DISABLED_SHIP_TOW));
    }

    @Test
    void commandExperienceExposesNavigationPlanningHudWarningsAndScreenshotControls() {
        OperationsInformationCommandSystem.State state = OperationsInformationCommandSystem.bootstrap(84L);
        OperationsInformationCommandSystem.CommandExperience command = state.command;

        assertEquals(OperationsInformationCommandSystem.Screen.values().length, command.screenHierarchy.size());
        assertFalse(command.breadcrumbs.isEmpty());
        assertTrue(command.consistentBackBehavior);
        assertFalse(command.notificationInbox.isEmpty());
        assertEquals(OperationsInformationCommandSystem.WarningCategory.values().length, command.warningFilters.size());
        assertFalse(command.operationsLog.isEmpty());
        assertFalse(command.mapBookmarks.isEmpty());
        assertFalse(command.pinnedContacts.isEmpty());
        assertFalse(command.visibleAutomationRules.isEmpty());
        assertTrue(command.routePreview.fuel > 0 && command.routePreview.hours > 0
                && command.routePreview.dangerPercent > 0 && command.routePreview.likelyContacts > 0);
        assertTrue(command.shieldFacing && command.subsystemDamagePriority && command.alliedOrderStatus
                && command.formationVisualization && command.missileWarnings && command.incomingStrikeWarnings
                && command.collisionAlerts && command.offscreenThreatIndicators);
        command.compareDestinations = true;
        command.compareFleets = true;
        command.compareContracts = true;
        command.pauseAndPlan = true;
        command.screenshotMode = true;
        OperationsInformationCommandSystem.bookmark(state, "Lunar Well");
        OperationsInformationCommandSystem.logOperation(state, "02:14", "Pinned convoy contact");
        assertTrue(command.compareDestinations && command.compareFleets && command.compareContracts);
        assertTrue(command.pauseAndPlan && command.screenshotMode);
        assertTrue(command.operationsLog.get(command.operationsLog.size() - 1).startsWith("02:14"));
    }

    @Test
    void serializerAndCheckpointPreserveOperationsPreferences() throws Exception {
        OperationsInformationCommandSystem.State direct = OperationsInformationCommandSystem.bootstrap(85L);
        OperationsInformationCommandSystem.activateSensors(direct);
        OperationsInformationCommandSystem.recordElectronicAttack(direct);
        direct.command.panelMode = OperationsInformationCommandSystem.PanelMode.EXPANDED;
        direct.command.densityPreset = OperationsInformationCommandSystem.DensityPreset.DENSE;
        direct.command.hudPreset = OperationsInformationCommandSystem.HudPreset.ACCESSIBILITY;
        direct.command.pauseAndPlan = true;
        direct.command.mapSearch = "lunar";
        OperationsInformationCommandSystem.State directRestored =
                OperationsInformationCommandSystem.restore(OperationsInformationCommandSystem.serialize(direct), 85L);
        assertEquals(OperationsInformationCommandSystem.SensorMode.ACTIVE, directRestored.intelligence.sensorProfile.mode);
        assertEquals(1, directRestored.intelligence.electronicAttackStrikes);
        assertEquals(OperationsInformationCommandSystem.PanelMode.EXPANDED, directRestored.command.panelMode);
        assertEquals("lunar", directRestored.command.mapSearch);

        GameContext source = campaignContext();
        source.campaign.operationsExpansion.command.screenshotMode = true;
        source.campaign.operationsExpansion.command.targetCardScalePercent = 145;
        source.campaign.operationsExpansion.command.mapSearch = "frontier";
        OperationsInformationCommandSystem.activateSensors(source.campaign.operationsExpansion);
        CampaignCheckpointStore.Checkpoint checkpoint = captureCheckpoint(source, 4);
        GameContext restored = campaignContext();
        assertTrue(applyCheckpoint(restored, checkpoint));
        assertTrue(restored.campaign.operationsExpansion.command.screenshotMode);
        assertEquals(145, restored.campaign.operationsExpansion.command.targetCardScalePercent);
        assertEquals("frontier", restored.campaign.operationsExpansion.command.mapSearch);
        assertEquals(OperationsInformationCommandSystem.SensorMode.ACTIVE,
                restored.campaign.operationsExpansion.intelligence.sensorProfile.mode);
        assertTrue(CampaignSystem.campaignOperationsExpansionLines(restored).stream()
                .anyMatch(line -> line.contains("Mission families")));
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
