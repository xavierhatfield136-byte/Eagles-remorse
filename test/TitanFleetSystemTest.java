import app.config.GameConfig;
import app.config.GameMode;
import app.persistence.CampaignCheckpointStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

class TitanFleetSystemTest {

    @Test
    void purchaseTitanDeductsCreditsAndAddsCommandCapacity() {
        GameContext ctx = campaignContext(1, 6000);

        TitanFleetSystem.PurchaseResult result = TitanFleetSystem.purchaseTitan(ctx, TitanArchetype.BULWARK);

        assertEquals(TitanFleetSystem.PurchaseResult.PURCHASED, result);
        assertEquals(800, ctx.credits);
        assertEquals(1, TitanFleetSystem.ownedTitanCount(ctx));
        assertEquals(10, TitanFleetSystem.totalStandardShipCommandCapacity(ctx));
        assertEquals(0, TitanFleetSystem.totalEliteSupershipCommandCapacity(ctx));
    }

    @Test
    void purchaseTitanBlocksArchetypesBeforeTheirSectorBand() {
        GameContext ctx = campaignContext(4, 8000);

        TitanFleetSystem.PurchaseResult result = TitanFleetSystem.purchaseTitan(ctx, TitanArchetype.COMMAND_INTEL);

        assertEquals(TitanFleetSystem.PurchaseResult.NOT_YET_AVAILABLE, result);
        assertEquals(8000, ctx.credits);
        assertEquals(0, TitanFleetSystem.ownedTitanCount(ctx));
    }

    @Test
    void purchaseTitanRejectsDuplicateTypesWithoutTotalSlotLimit() {
        GameContext ctx = campaignContext(12, 100_000);

        for (TitanArchetype archetype : TitanArchetype.values()) {
            assertEquals(TitanFleetSystem.PurchaseResult.PURCHASED,
                    TitanFleetSystem.purchaseTitan(ctx, archetype));
        }

        TitanFleetSystem.PurchaseResult duplicate = TitanFleetSystem.purchaseTitan(ctx, TitanArchetype.TRANSPORT);

        assertEquals(TitanFleetSystem.PurchaseResult.TITAN_TYPE_ALREADY_OWNED, duplicate);
        assertEquals(TitanArchetype.values().length, TitanFleetSystem.ownedTitanCount(ctx));
        assertEquals(126, TitanFleetSystem.totalStandardShipCommandCapacity(ctx));
        assertEquals(0, TitanFleetSystem.remainingTitanSlots(ctx));
    }

    @Test
    void titanRosterSerializesAndRestoresCleanly() {
        List<TitanArchetype> expected = List.of(
                TitanArchetype.BULWARK,
                TitanArchetype.ELITE_SUPERSHIP_COMMAND,
                TitanArchetype.SHIELD_BASTION);

        String raw = TitanFleetSystem.serializeOwnedTitans(expected);
        CampaignSystem.CampaignState restored = new CampaignSystem.CampaignState();
        restored.enabled = true;
        TitanFleetSystem.restoreOwnedTitans(restored, raw);

        assertIterableEquals(expected, restored.ownedTitans);
        assertEquals(20, TitanFleetSystem.totalStandardShipCommandCapacity(campaignContext(restored, 12, 0)));
        assertEquals(5, TitanFleetSystem.totalEliteSupershipCommandCapacity(campaignContext(restored, 12, 0)));
    }

    @Test
    void titanRosterRestoreDeduplicatesLegacyDuplicateTypes() {
        CampaignSystem.CampaignState restored = new CampaignSystem.CampaignState();
        restored.enabled = true;

        TitanFleetSystem.restoreOwnedTitans(restored, "TRANSPORT,TRANSPORT,BULWARK");

        assertIterableEquals(List.of(TitanArchetype.TRANSPORT, TitanArchetype.BULWARK), restored.ownedTitans);
        assertEquals("TRANSPORT,BULWARK", TitanFleetSystem.serializeOwnedTitans(restored.ownedTitans));
    }

    @Test
    void eliteReinforcementsTitanAddsStandardCapacityWithoutEliteWing() {
        GameContext ctx = campaignContext(12, 10_000);

        TitanFleetSystem.PurchaseResult result = TitanFleetSystem.purchaseTitan(ctx, TitanArchetype.ELITE_REINFORCEMENTS);

        assertEquals(TitanFleetSystem.PurchaseResult.PURCHASED, result);
        assertEquals(6, TitanFleetSystem.totalStandardShipCommandCapacity(ctx));
        assertEquals(0, TitanFleetSystem.totalEliteSupershipCommandCapacity(ctx));
    }

    @Test
    void checkpointSummaryIncludesTitanCount() {
        CampaignCheckpointStore.Checkpoint cp = new CampaignCheckpointStore.Checkpoint();
        cp.nextSector = 6;
        cp.playerRoleName = "BATTLECRUISER";
        cp.branchRoute = "BALANCED";
        cp.ownedTitans = "BULWARK,COMMAND_INTEL,SHIELD_BASTION";
        cp.persistentBlueFleet = "1,FRIGATE,false,Qmx1ZSBHdWFyZCBPbmU";
        cp.normalize();

        assertEquals("Sector 6  |  BATTLECRUISER  |  Doctrine BALANCED  |  Titans 3  |  Fleet 1", cp.menuSummary());
    }

    private static GameContext campaignContext(int sector, int credits) {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        CampaignSystem.CampaignState st = new CampaignSystem.CampaignState();
        st.enabled = true;
        st.sector = sector;
        ctx.campaign = st;
        ctx.credits = credits;
        return ctx;
    }

    private static GameContext campaignContext(CampaignSystem.CampaignState st, int sector, int credits) {
        GameContext ctx = campaignContext(sector, credits);
        st.sector = sector;
        ctx.campaign = st;
        return ctx;
    }
}
