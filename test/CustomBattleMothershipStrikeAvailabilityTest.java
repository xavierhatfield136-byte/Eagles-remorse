import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.awt.Canvas;
import java.awt.Point;
import java.awt.event.MouseEvent;
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
    void mothershipInMissionModeGetsCombatHudStrikePanelClickTargets() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 9000, 6000, true, 3344L, false));
        SpawnSystem.initWorld(ctx);

        assertNotNull(ctx.player);
        assertEquals(ShipRole.MOTHERSHIP, ctx.player.role);

        int viewW = 1920;
        int viewH = 1080;
        boolean foundStrikeHudClickTarget = false;
        for (int y = 0; y < viewH && !foundStrikeHudClickTarget; y += 4) {
            for (int x = 0; x < viewW; x += 4) {
                Renderer.HudPanelClickTarget target = Renderer.hudPanelClickTargetAt(ctx, viewW, viewH, x, y);
                if (target == null) continue;
                if (target.kind == Renderer.HudPanelClickTarget.Kind.STRIKE_SELECT_TORPEDO
                        || target.kind == Renderer.HudPanelClickTarget.Kind.STRIKE_SELECT_AIRWING
                        || target.kind == Renderer.HudPanelClickTarget.Kind.STRIKE_SELECT_NUCLEAR
                        || target.kind == Renderer.HudPanelClickTarget.Kind.STRIKE_LAUNCH) {
                    foundStrikeHudClickTarget = true;
                    break;
                }
            }
        }
        assertTrue(foundStrikeHudClickTarget, "mothership combat HUD should expose strike panel click targets in mission modes");
    }

    @Test
    void nonMothershipMissionPlayerDoesNotGetCombatHudStrikePanel() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 9000, 6000, true, 7788L, false));
        SpawnSystem.initWorld(ctx);
        ctx.player = new Player(ShipRole.FRIGATE, 1200.0, 1200.0);

        int viewW = 1920;
        int viewH = 1080;
        boolean foundStrikeHudClickTarget = false;
        for (int y = 0; y < viewH && !foundStrikeHudClickTarget; y += 4) {
            for (int x = 0; x < viewW; x += 4) {
                Renderer.HudPanelClickTarget target = Renderer.hudPanelClickTargetAt(ctx, viewW, viewH, x, y);
                if (target == null) continue;
                if (target.kind == Renderer.HudPanelClickTarget.Kind.STRIKE_SELECT_TORPEDO
                        || target.kind == Renderer.HudPanelClickTarget.Kind.STRIKE_SELECT_AIRWING
                        || target.kind == Renderer.HudPanelClickTarget.Kind.STRIKE_SELECT_NUCLEAR
                        || target.kind == Renderer.HudPanelClickTarget.Kind.STRIKE_LAUNCH) {
                    foundStrikeHudClickTarget = true;
                    break;
                }
            }
        }
        assertFalse(foundStrikeHudClickTarget, "non-mothership combat HUD should not expose strike panel click targets");
    }

    @Test
    void mothershipHudNuclearLaunchDoesNotOpenBlockingConfirmOverlay() {
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

        int viewW = 1920;
        int viewH = 1080;
        Point selectNuclear = firstHudPointForKind(ctx, viewW, viewH, Renderer.HudPanelClickTarget.Kind.STRIKE_SELECT_NUCLEAR);
        Point launch = firstHudPointForKind(ctx, viewW, viewH, Renderer.HudPanelClickTarget.Kind.STRIKE_LAUNCH);
        assertNotNull(selectNuclear, "expected STRIKE_SELECT_NUCLEAR click target in mothership HUD");
        assertNotNull(launch, "expected STRIKE_LAUNCH click target in mothership HUD");

        assertTrue(UISystem.handleHudPanelClick(ctx, leftClick(selectNuclear.x, selectNuclear.y), viewW, viewH));
        int projectilesBefore = ctx.projectiles.size();
        assertTrue(UISystem.handleHudPanelClick(ctx, leftClick(launch.x, launch.y), viewW, viewH));

        assertFalse(ctx.ui.campaignActionConfirm.active,
                "combat HUD strike launch should fire immediately in mission modes, not open a blocking confirm overlay");
        assertEquals(0, ctx.campaign.strategicAtomicCharges);
        assertTrue(ctx.projectiles.size() > projectilesBefore, "nuclear strike should spawn an inbound strike object");
    }

    private static Point firstHudPointForKind(GameContext ctx, int viewW, int viewH, Renderer.HudPanelClickTarget.Kind kind) {
        for (int y = 0; y < viewH; y += 4) {
            for (int x = 0; x < viewW; x += 4) {
                Renderer.HudPanelClickTarget target = Renderer.hudPanelClickTargetAt(ctx, viewW, viewH, x, y);
                if (target != null && target.kind == kind) {
                    return new Point(x, y);
                }
            }
        }
        return null;
    }

    private static MouseEvent leftClick(int x, int y) {
        return new MouseEvent(
                new Canvas(),
                MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(),
                0,
                x,
                y,
                1,
                false,
                MouseEvent.BUTTON1
        );
    }
}
