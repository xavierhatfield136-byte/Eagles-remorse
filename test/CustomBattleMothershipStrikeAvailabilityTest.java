import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CustomBattleMothershipStrikeAvailabilityTest {
    @Test
    void customBattlesExposeTacticalStrikeActionsForMothership() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 9000, 6000, true, 1234L, false));
        SpawnSystem.initWorld(ctx);

        assertNotNull(ctx.player);
        assertEquals(ShipRole.MOTHERSHIP, ctx.player.role);
        assertNotNull(ctx.campaign);
        assertTrue(ctx.campaign.enabled);

        ctx.ui.tacticalMapTab = UiState.TacticalMapTab.STRIKES;
        List<CampaignSystem.CampaignAction> actions = CampaignSystem.tacticalMapVisibleActions(ctx);
        assertTrue(actions.stream().anyMatch(a -> a != null && "TACTICAL_TORPEDO_STRIKE".equals(a.id)));
        assertTrue(actions.stream().anyMatch(a -> a != null && "TACTICAL_CARRIER_SORTIE".equals(a.id)));
        assertTrue(actions.stream().anyMatch(a -> a != null && "TACTICAL_ATOMIC_STRIKE".equals(a.id)));
    }
}
