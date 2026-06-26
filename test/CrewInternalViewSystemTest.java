import org.junit.jupiter.api.Test;

import app.config.GameConfig;
import app.config.GameMode;
import java.awt.Canvas;
import java.awt.event.MouseEvent;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrewInternalViewSystemTest {

    @Test
    void crewTeamsPathToPriorityRoomsAndSuppressFires() {
        Player player = new Player(ShipRole.FRIGATE, 0.0, 0.0);
        DoctrineRegistry.applyToShip(player);
        player.seedRoomFire(ShipRoomLayout.RoomId.REACTOR, 1.45);
        player.setCrewPriority(Ship.CrewPriority.FIRE_SUPPRESSION);

        double before = player.roomFireIntensity(ShipRoomLayout.RoomId.REACTOR);
        for (int i = 0; i < 360; i++) player.update(GameContext.DT);

        List<Ship.CrewTeamSnapshot> teams = player.crewTeamSnapshots();
        assertTrue(teams.stream().anyMatch(team -> team.targetRoom == ShipRoomLayout.RoomId.REACTOR
                || team.currentRoom == ShipRoomLayout.RoomId.REACTOR),
                "at least one crew team should route to the active reactor fire");
        assertTrue(player.roomFireIntensity(ShipRoomLayout.RoomId.REACTOR) < before,
                "crew teams stationed at the fire should reduce room fire intensity");
    }

    @Test
    void hudInternalViewAndCrewPriorityButtonsAreClickable() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.SHOWCASE, 5000, 5000, true, 1234L, false));
        ctx.player = new Player(ShipRole.FRIGATE, 2500.0, 2500.0);
        ctx.player.faction = Faction.ALLY;
        ctx.state = GameState.RUNNING;
        int viewW = 1280;
        int viewH = 720;

        PointLike internal = findHudTarget(ctx, viewW, viewH, Renderer.HudPanelClickTarget.Kind.INTERNAL_VIEW);
        PointLike fire = findHudTarget(ctx, viewW, viewH, Renderer.HudPanelClickTarget.Kind.CREW_PRIORITY_FIRE);
        PointLike battle = findHudTarget(ctx, viewW, viewH, Renderer.HudPanelClickTarget.Kind.CREW_PRIORITY_BATTLE);
        PointLike cancel = findHudTarget(ctx, viewW, viewH, Renderer.HudPanelClickTarget.Kind.CREW_PRIORITY_CANCEL);
        assertNotNull(internal, "internal view button should have a HUD click target");
        assertNotNull(fire, "fire suppression priority button should have a HUD click target");
        assertNotNull(battle, "battle stations priority button should have a HUD click target");
        assertNotNull(cancel, "cancel priority button should have a HUD click target");

        assertTrue(UISystem.handleHudPanelClick(ctx, mouse(internal.x, internal.y), viewW, viewH));
        assertTrue(ctx.ui.tacticalViewEnabled, "FPS view HUD button should toggle the tactical FPS render mode");

        assertTrue(UISystem.handleHudPanelClick(ctx, mouse(fire.x, fire.y), viewW, viewH));
        assertTrue(ctx.player.crewPriority() == Ship.CrewPriority.FIRE_SUPPRESSION,
                "fire button should set player crew priority");

        assertTrue(UISystem.handleHudPanelClick(ctx, mouse(battle.x, battle.y), viewW, viewH));
        assertTrue(ctx.player.crewPriority() == Ship.CrewPriority.BATTLE_STATIONS,
                "battle button should set player crew priority");
        assertTrue(ctx.player.crewOrder == Ship.CrewOrder.GUNNERY,
                "battle stations should put crew into gunnery posture");

        assertTrue(UISystem.handleHudPanelClick(ctx, mouse(cancel.x, cancel.y), viewW, viewH));
        assertTrue(ctx.player.crewPriority() == Ship.CrewPriority.AUTO_REPAIR,
                "cancel button should return crew priority to auto repair");
    }

    private static PointLike findHudTarget(GameContext ctx, int viewW, int viewH, Renderer.HudPanelClickTarget.Kind kind) {
        for (int y = 0; y < viewH; y += 2) {
            for (int x = 0; x < viewW; x += 2) {
                Renderer.HudPanelClickTarget target = Renderer.hudPanelClickTargetAt(ctx, viewW, viewH, x, y);
                if (target != null && target.kind == kind) return new PointLike(x, y);
            }
        }
        return null;
    }

    private static MouseEvent mouse(int x, int y) {
        return new MouseEvent(new Canvas(), MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(),
                0, x, y, 1, false, MouseEvent.BUTTON1);
    }

    private record PointLike(int x, int y) {}
}
