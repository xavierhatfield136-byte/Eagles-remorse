import app.config.GameConfig;
import app.config.GameMode;
import app.persistence.CampaignCheckpointStore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CampaignPhaseSixReputationAidTest {

    @Test
    void reputationBoardShowsValuesTiersThresholdsBenefitsAndReasonedHistory() {
        GameContext ctx = initializedCampaignContext(6101L);
        ctx.credits = 1000;

        assertTrue(CampaignSystem.executeCampaignAidTransfer(ctx, Faction.TEAM_C,
                CampaignSystem.CampaignAidType.CREDITS, 250, 0, "green-credit-1", true));

        String board = String.join("\n", CampaignSystem.campaignWarSupportReadoutLines(ctx));
        assertTrue(board.contains("Green reputation 1"));
        assertTrue(board.contains("Unknown fleet"));
        assertTrue(board.contains("Next Green threshold"));
        assertTrue(board.contains("unlocked") || board.contains("support") || board.contains("discounts"));
        assertTrue(board.contains("because Blue sent 250 credits"));
        assertTrue(String.join("\n", CampaignSystem.campaignReputationHistoryLines(ctx, 4))
                .contains("Green +1"));
    }

    @Test
    void oreCreditsAndIntelligenceAidHaveCostsEffectsAndDuplicateProtection() {
        GameContext ctx = initializedCampaignContext(6102L);
        ctx.credits = 2000;
        CampaignSystem.grantCampaignOre(ctx, 100);
        ctx.campaign.campaignIntelLevel = 80.0;

        String previews = String.join("\n", CampaignSystem.campaignAidTransferLines(ctx, Faction.TEAM_D, 0));
        assertTrue(previews.contains("cost 20 ore"));
        assertTrue(previews.contains("cost 250 credits"));
        assertTrue(previews.contains("cost 10 intelligence"));
        assertTrue(previews.contains("reputation +"));
        assertTrue(previews.contains("effect"));

        int oreBefore = CampaignSystem.currentCampaignOre(ctx);
        assertTrue(CampaignSystem.executeCampaignAidTransfer(ctx, Faction.TEAM_D,
                CampaignSystem.CampaignAidType.ORE, 40, 0, "yellow-ore-1", true));
        assertEquals(oreBefore - 40, CampaignSystem.currentCampaignOre(ctx));
        int favorAfterOre = ctx.campaign.yellowLiberationFavor;
        assertFalse(CampaignSystem.executeCampaignAidTransfer(ctx, Faction.TEAM_D,
                CampaignSystem.CampaignAidType.ORE, 40, 0, "yellow-ore-1", true));
        assertEquals(favorAfterOre, ctx.campaign.yellowLiberationFavor);

        assertTrue(CampaignSystem.executeCampaignAidTransfer(ctx, Faction.TEAM_C,
                CampaignSystem.CampaignAidType.CREDITS, 500, 0, "green-credit-2", true));
        assertTrue(CampaignSystem.executeCampaignAidTransfer(ctx, Faction.TEAM_D,
                CampaignSystem.CampaignAidType.INTELLIGENCE, 20, 0, "yellow-intel-1", true));
        assertTrue(ctx.campaign.campaignBlueYellowAlliance);
        assertTrue(String.join("\n", CampaignSystem.campaignBehaviorLedgerLines(ctx)).contains("Yellow ore 40"));
        assertTrue(String.join("\n", CampaignSystem.campaignBehaviorLedgerLines(ctx)).contains("Green ore 0 credits 500"));
    }

    @Test
    void persistentShipTransferRequiresConfirmationMovesInventoryAndPreservesOrigin() throws Exception {
        GameContext ctx = initializedCampaignContext(6103L);
        List<CampaignSystem.CampaignFleetRosterEntry> roster = CampaignSystem.campaignFleetRosterEntries(ctx);
        assertFalse(roster.isEmpty());
        int slot = roster.get(0).slotId;
        int blueBefore = roster.size();

        assertFalse(CampaignSystem.executeCampaignAidTransfer(ctx, Faction.TEAM_D,
                CampaignSystem.CampaignAidType.SHIP, 1, slot, "yellow-ship-1", false));
        assertEquals(blueBefore, CampaignSystem.campaignFleetRosterEntries(ctx).size());

        assertTrue(CampaignSystem.executeCampaignAidTransfer(ctx, Faction.TEAM_D,
                CampaignSystem.CampaignAidType.SHIP, 1, slot, "yellow-ship-1", true));
        assertEquals(blueBefore - 1, CampaignSystem.campaignFleetRosterEntries(ctx).size());
        assertTrue(forceNameExists(ctx.campaign, "Yellow Aid Detachment"));
        String memory = String.join("\n", CampaignSystem.campaignMemoryFlagLines(ctx, 12));
        assertTrue(memory.contains("original hull identity"));
        assertTrue(memory.contains("Blue inventory to Bright Yellow"));
        assertTrue(String.join("\n", CampaignSystem.campaignBehaviorLedgerLines(ctx)).contains("ships 1"));
    }

    @Test
    void aidReputationAllianceAndHistorySurviveCheckpointRestore() throws Exception {
        GameContext source = initializedCampaignContext(6104L);
        source.credits = 1000;
        assertTrue(CampaignSystem.executeCampaignAidTransfer(source, Faction.TEAM_D,
                CampaignSystem.CampaignAidType.CREDITS, 750, 0, "yellow-credit-save", true));
        assertTrue(source.campaign.campaignBlueYellowAlliance);
        CampaignCheckpointStore.Checkpoint checkpoint = captureCheckpoint(source);

        GameContext restored = initializedCampaignContext(9999L);
        assertTrue(applyCheckpoint(restored, checkpoint));
        assertEquals(source.campaign.yellowLiberationFavor, restored.campaign.yellowLiberationFavor);
        assertTrue(restored.campaign.campaignBlueYellowAlliance);
        assertTrue(String.join("\n", CampaignSystem.campaignReputationHistoryLines(restored, 4))
                .contains("yellow-credit-save") || String.join("\n", CampaignSystem.campaignCaptainLogLines(restored, 4))
                .contains("750 credits"));
        assertTrue(String.join("\n", CampaignSystem.campaignBehaviorLedgerLines(restored))
                .contains("Yellow ore 0 credits 750"));
    }

    @Test
    void yellowLiberationBoardTargetsRealAssetsAndHighTrustChangesAlignment() {
        GameContext ctx = initializedCampaignContext(6105L);
        List<CampaignSystem.CampaignMissionBoardEntry> missions =
                CampaignSystem.campaignFactionMissionBoard(ctx, Faction.TEAM_D);
        assertFalse(missions.isEmpty());
        assertTrue(missions.stream().allMatch(entry -> !entry.targetLocationId.isBlank()));

        ctx.credits = 1000;
        assertTrue(CampaignSystem.executeCampaignAidTransfer(ctx, Faction.TEAM_D,
                CampaignSystem.CampaignAidType.CREDITS, 750, 0, "yellow-liberation-1", true));
        assertEquals(CampaignSystem.YellowAlignment.LIBERATED_FRIENDLY,
                CampaignSystem.yellowAlignment(ctx));
        String late = String.join("\n", CampaignSystem.campaignLateCampaignReadinessLines(ctx));
        assertTrue(late.contains("LIBERATED_FRIENDLY"));
    }

    @Test
    void reportsCharactersAndEndingReferenceRecordedBehaviorWithoutDialogueSprawl() {
        GameContext ctx = initializedCampaignContext(6106L);
        ctx.credits = 1000;
        assertTrue(CampaignSystem.executeCampaignAidTransfer(ctx, Faction.TEAM_C,
                CampaignSystem.CampaignAidType.CREDITS, 500, 0, "green-ending-1", true));

        String characters = String.join("\n", CampaignSystem.campaignFactionResponseLines(ctx));
        assertTrue(characters.contains("Captain Voss ["));
        assertTrue(characters.contains("Broker Marr ["));
        assertTrue(characters.contains("Commander Rook ["));
        assertTrue(characters.contains("quiet mode"));
        assertTrue(characters.contains("captions"));
        assertTrue(characters.contains("placeholder voices are not release content"));

        String ending = String.join("\n", CampaignSystem.campaignEndingMemoryLines(ctx));
        assertTrue(ending.contains("Because you aided Green"));
        assertTrue(ending.contains("The record remembers"));
        assertTrue(ending.contains("Reputation history"));
        assertTrue(String.join("\n", CampaignSystem.campaignFinalBattleReadiness(ctx).lines)
                .contains("Because you aided Green"));
    }

    private static GameContext initializedCampaignContext(long seed) {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, seed, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static boolean forceNameExists(CampaignSystem.CampaignState state, String fragment) throws Exception {
        Field field = CampaignSystem.CampaignState.class.getDeclaredField("campaignForces");
        field.setAccessible(true);
        for (Object force : (List<?>) field.get(state)) {
            Field name = force.getClass().getDeclaredField("name");
            name.setAccessible(true);
            if (name.get(force).toString().contains(fragment)) return true;
        }
        return false;
    }

    private static CampaignCheckpointStore.Checkpoint captureCheckpoint(GameContext ctx) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "captureCheckpoint", GameContext.class, CampaignSystem.CampaignState.class, int.class);
        method.setAccessible(true);
        return (CampaignCheckpointStore.Checkpoint) method.invoke(null, ctx, ctx.campaign, 2);
    }

    private static boolean applyCheckpoint(GameContext ctx, CampaignCheckpointStore.Checkpoint checkpoint) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "applyCheckpoint", GameContext.class, CampaignSystem.CampaignState.class, CampaignCheckpointStore.Checkpoint.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, ctx, ctx.campaign, checkpoint);
    }
}
