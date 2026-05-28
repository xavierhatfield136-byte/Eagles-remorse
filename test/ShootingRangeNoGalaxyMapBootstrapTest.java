import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShootingRangeNoGalaxyMapBootstrapTest {
    @Test
    void shootingRangeTacticalStrikeBootstrapDoesNotEnableGalaxyMapMode() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.SHOOTING_RANGE, 5000, 5000, true, 42L, false));
        SpawnSystem.initWorld(ctx);

        assertNotNull(ctx.campaign, "shooting range should still have tactical strike state");
        assertTrue(ctx.campaign.enabled);
        assertFalse(CampaignSystem.isStrategicGalaxyMapMode(ctx),
                "shooting range must never auto-enter campaign galaxy map mode");
    }
}
