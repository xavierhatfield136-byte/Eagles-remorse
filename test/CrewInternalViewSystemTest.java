import org.junit.jupiter.api.Test;

import app.config.GameConfig;
import app.config.GameMode;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void combatHudNoLongerExposesInternalCrewRepairControls() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.SHOWCASE, 5000, 5000, true, 1234L, false));
        ctx.player = new Player(ShipRole.FRIGATE, 2500.0, 2500.0);
        ctx.player.faction = Faction.ALLY;
        ctx.state = GameState.RUNNING;
        int viewW = 1280;
        int viewH = 720;

        assertFalse(anyHudTargetNamed(ctx, viewW, viewH, "INTERNAL_VIEW", "CREW_PRIORITY_"),
                "combat HUD should not expose internal crew repair controls");
    }

    private static boolean anyHudTargetNamed(GameContext ctx, int viewW, int viewH, String exact, String prefix) {
        for (int y = 0; y < viewH; y += 2) {
            for (int x = 0; x < viewW; x += 2) {
                Renderer.HudPanelClickTarget target = Renderer.hudPanelClickTargetAt(ctx, viewW, viewH, x, y);
                if (target == null || target.kind == null) continue;
                String name = target.kind.name();
                if (name.equals(exact) || name.startsWith(prefix)) return true;
            }
        }
        return false;
    }
}
