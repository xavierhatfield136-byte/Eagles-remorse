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
        GameContext ctx = campaignContext(1, 5000);

        TitanFleetSystem.PurchaseResult result = TitanFleetSystem.purchaseTitan(ctx, TitanArchetype.BULWARK);

        assertEquals(TitanFleetSystem.PurchaseResult.PURCHASED, result);
        assertEquals(3200, ctx.credits);
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
    void purchaseTitanHonorsMothershipCap() {
        GameContext ctx = campaignContext(12, 30000);

        for (int i = 0; i < TitanFleetSystem.mothershipTitanCap(); i++) {
            assertEquals(TitanFleetSystem.PurchaseResult.PURCHASED,
                    TitanFleetSystem.purchaseTitan(ctx, TitanArchetype.TRANSPORT));
        }

        TitanFleetSystem.PurchaseResult extra = TitanFleetSystem.purchaseTitan(ctx, TitanArchetype.TRANSPORT);

        assertEquals(TitanFleetSystem.PurchaseResult.TITAN_CAP_REACHED, extra);
        assertEquals(8, TitanFleetSystem.ownedTitanCount(ctx));
        assertEquals(80, TitanFleetSystem.totalStandardShipCommandCapacity(ctx));
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
    void checkpointSummaryIncludesTitanCount() {
        CampaignCheckpointStore.Checkpoint cp = new CampaignCheckpointStore.Checkpoint();
        cp.nextSector = 6;
        cp.playerRoleName = "BATTLECRUISER";
        cp.branchRoute = "BALANCED";
        cp.ownedTitans = "BULWARK,COMMAND_INTEL,SHIELD_BASTION";
        cp.normalize();

        assertEquals("Sector 6  |  BATTLECRUISER  |  Route BALANCED  |  Titans 3/8", cp.menuSummary());
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
