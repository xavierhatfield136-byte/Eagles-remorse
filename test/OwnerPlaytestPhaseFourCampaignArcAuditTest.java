import app.config.ExperienceSettings;
import app.config.GameConfig;
import app.config.GameMode;
import app.persistence.CampaignCheckpointStore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerPlaytestPhaseFourCampaignArcAuditTest {
    @Test
    void campaignArcReadoutDefinesDistinctPhasesFactionIdentitiesProblemsAndLengthTarget() {
        GameContext early = campaign(67001L);
        GameContext middle = campaign(67002L);
        middle.campaign.completedMainMissions = 10;
        middle.campaign.earthProgress = 0.42;
        GameContext late = campaign(67003L);
        late.campaign.completedMainMissions = 19;
        late.campaign.earthProgress = 0.78;
        late.campaign.earthOperationStage = 2;

        String earlyLines = String.join("\n", CampaignArcSummarySystem.campaignArcIdentityLines(early));
        String middleLines = String.join("\n", CampaignArcSummarySystem.campaignArcIdentityLines(middle));
        String lateLines = String.join("\n", CampaignArcSummarySystem.campaignArcIdentityLines(late));

        assertTrue(earlyLines.contains("Campaign Phase: EARLY"));
        assertTrue(middleLines.contains("Campaign Phase: MIDDLE"));
        assertTrue(lateLines.contains("Campaign Phase: LATE"));
        assertTrue(earlyLines.contains("Green identity"));
        assertTrue(earlyLines.contains("Bright Yellow identity"));
        assertTrue(earlyLines.contains("Dark Yellow identity"));
        assertTrue(earlyLines.contains("Red identity"));
        assertTrue(earlyLines.contains("Strategic Problems: early survival"));
        assertTrue(earlyLines.contains("Optional Choices Matter"));
        assertTrue(earlyLines.contains("Standard Target Length: 3-5 hours"));
    }

    @Test
    void mainObjectiveRouteGuidanceAndEarthReadinessAreAlwaysVisible() {
        GameContext ctx = campaign(67004L);
        ctx.campaign.campaignFuel = 12;
        ctx.campaign.campaignSupplies = 8;

        String guidance = String.join("\n", CampaignArcSummarySystem.mainObjectiveGuidanceLines(ctx));

        assertTrue(guidance.contains("Main Objective: Open the route to Earth"));
        assertTrue(guidance.contains("Immediate Step: resupply"));
        assertTrue(guidance.contains("Route Guidance: move north through selected route markers"));
        assertTrue(guidance.contains("Earth Readiness:"));
        assertTrue(guidance.contains("Earth Lock Conditions:"));
        assertTrue(guidance.contains("Exploration Rule: optional sites"));
    }

    @Test
    void difficultyPresetsExposeDifferentRulesAndStandardRemainsDefault() {
        assertEquals(ExperienceSettings.Preset.STANDARD, ExperienceSettings.defaults().preset);

        for (ExperienceSettings.Preset preset : List.of(
                ExperienceSettings.Preset.RELAXED,
                ExperienceSettings.Preset.STANDARD,
                ExperienceSettings.Preset.TACTICAL_ONLY,
                ExperienceSettings.Preset.COMMAND_ONLY,
                ExperienceSettings.Preset.IRON_COMMAND)) {
            String rulebook = String.join("\n", CampaignArcSummarySystem.difficultyPresetRuleLines(preset));
            assertTrue(rulebook.contains("Preset: "));
            assertTrue(rulebook.contains("Structure: "));
            assertTrue(rulebook.contains("Combat lethality: x") || rulebook.contains("Enemy armor systems"));
            assertTrue(rulebook.contains("Strategic pressure: x") || preset == ExperienceSettings.Preset.COMMAND_ONLY);
            assertTrue(rulebook.contains("Travel attrition: x") || preset == ExperienceSettings.Preset.COMMAND_ONLY);
        }

        assertTrue(String.join("\n", CampaignArcSummarySystem.difficultyPresetRuleLines(ExperienceSettings.Preset.TACTICAL_ONLY))
                .contains("suppresses strategic pressure and route attrition"));
        assertTrue(String.join("\n", CampaignArcSummarySystem.difficultyPresetRuleLines(ExperienceSettings.Preset.COMMAND_ONLY))
                .contains("campaign command pressure"));

        GameContext pauseSummary = campaign(67005L);
        String pauseLines = String.join("\n", CampaignSystem.campaignDifficultyModifierLines(pauseSummary));
        assertTrue(pauseLines.contains("Combat lethality"));
        assertTrue(pauseLines.contains("Travel attrition"));
    }

    @Test
    void endingSummariesUseStateInputsCreateVariantsAndSurviveCheckpointRestore() throws Exception {
        GameContext coalition = campaign(67006L);
        coalition.campaign.greenContractFavor = 8;
        coalition.campaign.yellowLiberationFavor = 8;
        coalition.campaign.bossDropsCollected = 3;
        coalition.campaign.completedMainMissions = 22;
        coalition.campaign.earthProgress = 0.92;
        coalition.campaign.earthOperationStage = 3;
        coalition.campaign.campaignMemoryFlags.add("LEDGER|allied_battles_joined|4");
        coalition.campaign.unlockAuxGunGranted = true;
        coalition.campaign.unlockMissileTierGranted = 2;
        coalition.campaign.unlockCiwsGranted = true;
        coalition.campaign.unlockHullGranted = true;
        completeSomeSites(coalition, 8);

        GameContext costly = campaign(67007L);
        costly.campaign.greenContractFavor = 4;
        costly.campaign.yellowLiberationFavor = 3;
        costly.campaign.completedMainMissions = 13;
        costly.campaign.earthProgress = 0.56;
        completeSomeSites(costly, 4);

        GameContext desperate = campaign(67008L);
        desperate.campaign.greenContractFavor = 0;
        desperate.campaign.yellowLiberationFavor = 0;
        desperate.campaign.campaignMemoryFlags.add("LEDGER|territory_abandoned|5");
        desperate.campaign.objectiveAssetLosses = 4;

        String coalitionEnding = String.join("\n", CampaignArcSummarySystem.campaignEndingSummaryLines(coalition));
        String costlyEnding = String.join("\n", CampaignArcSummarySystem.campaignEndingSummaryLines(costly));
        String desperateEnding = String.join("\n", CampaignArcSummarySystem.campaignEndingSummaryLines(desperate));

        assertTrue(coalitionEnding.contains("Ending Family: Coalition Restoration"));
        assertFalse(costlyEnding.contains("Coalition Restoration"));
        assertFalse(desperateEnding.contains("Coalition Restoration"));
        assertTrue(coalitionEnding.contains("Allies: Green favor 8"));
        assertTrue(coalitionEnding.contains("Territory/Sites: completed sites"));
        assertTrue(coalitionEnding.contains("Fleet Record:"));
        assertTrue(coalitionEnding.contains("Operations: completed board missions"));
        assertTrue(coalitionEnding.contains("Earth Readiness:"));
        assertTrue(coalitionEnding.contains("Persistence: unlocks aux=true missileTier=2 ciws=true hull=true"));

        CampaignCheckpointStore.Checkpoint checkpoint = captureCheckpoint(coalition, 24);
        GameContext restored = campaign(67009L);
        assertTrue(applyCheckpoint(restored, checkpoint));
        String restoredEnding = String.join("\n", CampaignArcSummarySystem.campaignEndingSummaryLines(restored));
        assertTrue(restoredEnding.contains("Persistence: unlocks aux=true missileTier=2 ciws=true hull=true"));
        assertTrue(restoredEnding.contains("Yellow favor 8"));
    }

    private static GameContext campaign(long seed) {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, seed, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static void completeSomeSites(GameContext ctx, int count) {
        int marked = 0;
        for (CampaignSystem.CampaignLocation location : CampaignSystem.mainCampaignLocations(ctx)) {
            if (location == null) continue;
            location.completed = true;
            marked++;
            if (marked >= count) return;
        }
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
