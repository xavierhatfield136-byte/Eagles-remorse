import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignSectorOneDurationTest {

    @Test
    void firstCampaignMissionUsesTwoHundredSecondWindow() throws Exception {
        GameContext ctx = initializedCampaignContext();

        startSector(ctx, 1);

        assertEquals(200.0, ctx.campaign.objectiveGoal, 0.01);
        assertEquals(200.0, ctx.campaign.sectorTimeLimit, 0.01);
    }

    @Test
    void firstCampaignMissionAutoWinsAtTwoHundredSeconds() throws Exception {
        GameContext ctx = initializedCampaignContext();

        startSector(ctx, 1);
        ctx.campaign.introSequenceActive = false;
        ctx.campaign.sectorElapsed = 200.0 - (GameContext.DT * 0.5);
        ctx.campaign.objectiveProgress = 0.0;

        CampaignSystem.update(ctx, GameContext.DT);

        assertFalse(ctx.gameOver, "sector one should not fail on the 200-second mark");
        assertTrue(ctx.campaign.awaitingFleetHubChoice || ctx.campaign.pendingEpisodeSector == 2,
                "sector one should complete and queue the next campaign transition");
        assertEquals(200.0, ctx.campaign.objectiveProgress, 0.01);
    }

    private static GameContext initializedCampaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static void startSector(GameContext ctx, int sector) throws Exception {
        Method startSector = CampaignSystem.class.getDeclaredMethod("startSector", GameContext.class, int.class);
        startSector.setAccessible(true);
        startSector.invoke(null, ctx, sector);
    }
}
