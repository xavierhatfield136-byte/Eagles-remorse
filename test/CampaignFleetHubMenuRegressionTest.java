import app.config.GameConfig;
import app.config.GameMode;
import app.persistence.CampaignCheckpointStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignFleetHubMenuRegressionTest {

    @AfterEach
    void cleanupCheckpoint() {
        CampaignCheckpointStore.clear();
    }

    @Test
    void campaignOpsFleetHubUnlocksFleetHangarBetweenSectors() {
        GameContext ctx = campaignContext(GameMode.CAMPAIGN_OPS);
        ctx.campaign.sector = 2;
        ctx.campaign.awaitingEpisodeLaunch = true;
        ctx.campaign.pendingEpisodeSector = 3;
        ctx.state = GameState.FLEET;

        assertTrue(CampaignSystem.isFleetHubSession(ctx));
        assertTrue(CampaignSystem.usesPersistentFleetShop(ctx));

        UISystem.toggleShop(ctx);
        assertTrue(ctx.ui.shopOpen);
        assertEquals(GameState.SHOP, ctx.state);

        UISystem.toggleShop(ctx);
        assertFalse(ctx.ui.shopOpen);
        assertEquals(GameState.FLEET, ctx.state);
    }

    @Test
    void menuExitCheckpointCapturesCurrentSectorOreForResume() {
        GameContext ctx = campaignContext(GameMode.CAMPAIGN_OPS);
        ctx.campaign.sector = 4;
        ctx.player.cargo = 137;
        ctx.credits = 980;

        assertTrue(CampaignSystem.persistCheckpointForMenuExit(ctx));

        CampaignCheckpointStore.Checkpoint checkpoint = CampaignCheckpointStore.load();
        assertNotNull(checkpoint);
        assertEquals(4, checkpoint.nextSector);
        assertEquals(137, checkpoint.cargo);
        assertEquals(GameMode.CAMPAIGN_OPS, checkpoint.toGameConfig().mode);
        assertEquals(GameMode.FLEET, checkpoint.toGameConfig(GameMode.FLEET).mode);
    }

    @Test
    void menuExitCheckpointKeepsPendingSectorWhenLeavingFleetHub() {
        GameContext ctx = campaignContext(GameMode.CAMPAIGN_OPS);
        ctx.campaign.sector = 4;
        ctx.campaign.awaitingEpisodeLaunch = true;
        ctx.campaign.pendingEpisodeSector = 5;
        ctx.state = GameState.FLEET;
        ctx.player.cargo = 212;

        assertTrue(CampaignSystem.persistCheckpointForMenuExit(ctx));

        CampaignCheckpointStore.Checkpoint checkpoint = CampaignCheckpointStore.load();
        assertNotNull(checkpoint);
        assertEquals(5, checkpoint.nextSector);
        assertEquals(212, checkpoint.cargo);
        assertTrue(checkpoint.toGameConfig(GameMode.FLEET).resumeCampaign);
    }

    private static GameContext campaignContext(GameMode mode) {
        GameContext ctx = new GameContext(new GameConfig(mode, 5000, 5000, true, 1234L, false));
        CampaignSystem.CampaignState st = new CampaignSystem.CampaignState();
        st.enabled = true;
        ctx.campaign = st;

        ctx.player = new Player(ShipRole.MOTHERSHIP, 2500.0, 2500.0);
        ctx.player.faction = Faction.ALLY;
        ctx.player.cargoMax = 500;
        ctx.ships.add(ctx.player);
        ctx.baseUpgrades.put(ctx.player, new BaseUpgrades().bindTo(ctx.player));
        return ctx;
    }
}
