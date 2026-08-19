import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void mothershipInMissionModeDoesNotGetCombatHudStrikePanelClickTargets() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 9000, 6000, true, 3344L, false));
        SpawnSystem.initWorld(ctx);

        assertNotNull(ctx.player);
        assertEquals(ShipRole.MOTHERSHIP, ctx.player.role);

        assertFalse(anyCombatHudStrikeClickTarget(ctx, 1920, 1080),
                "combat HUD should not expose strike panel click targets");
    }

    @Test
    void nonMothershipMissionPlayerDoesNotGetCombatHudStrikePanel() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 9000, 6000, true, 7788L, false));
        SpawnSystem.initWorld(ctx);
        ctx.player = new Player(ShipRole.FRIGATE, 1200.0, 1200.0);

        assertFalse(anyCombatHudStrikeClickTarget(ctx, 1920, 1080),
                "non-mothership combat HUD should not expose strike panel click targets");
    }

    @Test
    void mothershipHudNuclearLaunchTargetIsRemoved() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 9000, 6000, true, 2468L, false));
        SpawnSystem.initWorld(ctx);

        assertNotNull(ctx.player);
        assertEquals(ShipRole.MOTHERSHIP, ctx.player.role);
        Ship hostile = ctx.ships.stream()
                .filter(ship -> ship != null && ship.alive && ship.faction != null)
                .filter(ship -> !ship.faction.isFriendlyTo(ctx.player.faction))
                .findFirst()
                .orElse(null);
        assertNotNull(hostile);
        ctx.lockedTarget = hostile;

        ctx.campaign.strategicAtomicCharges = 1;
        ctx.campaign.campaignAmmo = 120;
        ctx.campaign.campaignFuel = 120;
        ctx.campaign.campaignSupplies = 120;

        assertFalse(anyCombatHudStrikeClickTarget(ctx, 1920, 1080),
                "removed combat HUD should not expose nuclear strike launch targets");
        assertEquals(1, ctx.campaign.strategicAtomicCharges);
    }

    private static boolean anyCombatHudStrikeClickTarget(GameContext ctx, int viewW, int viewH) {
        for (int y = 0; y < viewH; y += 4) {
            for (int x = 0; x < viewW; x += 4) {
                Renderer.HudPanelClickTarget target = Renderer.hudPanelClickTargetAt(ctx, viewW, viewH, x, y);
                if (target != null && target.kind != null && target.kind.name().startsWith("STRIKE_")) return true;
            }
        }
        return false;
    }
}
