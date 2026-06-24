import app.config.GameConfig;
import app.config.GameMode;
import app.persistence.CampaignCheckpointStore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignStructuredTelemetryTest {
    @Test
    void majorCampaignTransitionsEmitDurableStructuredTelemetry() throws Exception {
        CampaignCheckpointStore.clear();
        try {
            GameContext ctx = initializedCampaignContext();
            CampaignSystem.CampaignState st = ctx.campaign;

            assertTrue(CampaignSystem.selectCampaignFreeTravelTarget(ctx, 1400.0, 3400.0));
            assertTrue(CampaignSystem.startTravelToSelectedLocation(ctx));
            assertTelemetryContains(ctx, "event=campaign.transition.travel_start");

            assertTrue(saveCheckpoint(ctx, st, 4));
            assertTelemetryContains(ctx, "event=campaign.transition.checkpoint_save");

            CampaignCheckpointStore.Checkpoint checkpoint = CampaignCheckpointStore.load();
            GameContext restored = initializedCampaignContext();
            assertTrue(applyCheckpoint(restored, checkpoint));
            assertTelemetryContains(restored, "event=campaign.transition.travel_start");
            assertTelemetryContains(restored, "event=campaign.transition.checkpoint_restore");

            GameContext failure = initializedCampaignContext();
            failure.player.hp = 0;
            CampaignSystem.update(failure, 1.0 / 60.0);
            assertTrue(failure.gameOver);
            assertTelemetryContains(failure, "event=campaign.failure");
            assertTelemetryContains(failure, "reason=DEFEAT:_FLAGSHIP_LOST");
            assertTelemetryContains(failure, "banner=Campaign_command_ship_destroyed");
        } finally {
            CampaignCheckpointStore.clear();
        }
    }

    private static GameContext initializedCampaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static void assertTelemetryContains(GameContext ctx, String expected) {
        List<String> lines = CampaignSystem.campaignStructuredTelemetryLines(ctx);
        assertTrue(lines.stream().anyMatch(line -> line.contains(expected)),
                () -> "Expected telemetry containing '" + expected + "' in " + lines);
    }

    private static boolean saveCheckpoint(GameContext ctx, CampaignSystem.CampaignState st, int nextSector) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "saveCheckpoint", GameContext.class, CampaignSystem.CampaignState.class, int.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, ctx, st, nextSector);
    }

    private static boolean applyCheckpoint(GameContext ctx, CampaignCheckpointStore.Checkpoint checkpoint) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod(
                "applyCheckpoint", GameContext.class, CampaignSystem.CampaignState.class, CampaignCheckpointStore.Checkpoint.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, ctx, ctx.campaign, checkpoint);
    }
}
