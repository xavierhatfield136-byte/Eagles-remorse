import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.awt.Canvas;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StrategicMapWaypointSelectionTest {

    @Test
    void sameSectorMapClickKeepsExactClickedPoint() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.RESOURCE_RUSH, 9000, 6000, true, 1234L, false));
        ctx.player = new Player(ShipRole.FRIGATE, 1500.0, 3000.0);
        ctx.player.faction = Faction.ALLY;
        BattlefieldSectorSystem.ensureLoadedSector(ctx);

        BattlefieldSectorSystem.SectorDefinition loaded = BattlefieldSectorSystem.loadedSector(ctx);
        assertNotNull(loaded);

        int viewW = 1280;
        int viewH = 720;
        Rectangle rect = Renderer.getStrategicMapInnerRect(viewW, viewH);
        double clickedWorldX = 4200.0;
        double clickedWorldY = 2150.0;
        int clickX = rect.x + (int) Math.round((clickedWorldX / ctx.WORLD_W) * rect.width);
        int clickY = rect.y + (int) Math.round((clickedWorldY / ctx.WORLD_H) * rect.height);
        double expectedX = GameMath.clamp(((clickX - rect.x) / (double) rect.width) * ctx.WORLD_W, 0, ctx.WORLD_W);
        double expectedY = GameMath.clamp(((clickY - rect.y) / (double) rect.height) * ctx.WORLD_H, 0, ctx.WORLD_H);

        MouseEvent click = new MouseEvent(
                new Canvas(),
                MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(),
                0,
                clickX,
                clickY,
                1,
                false,
                MouseEvent.BUTTON1
        );

        UISystem.handleMapClick(ctx, click, viewW, viewH);

        assertEquals(loaded.id, BattlefieldSectorSystem.selectedSector(ctx).id);
        assertEquals(expectedX, ctx.ui.waypointX, 1e-6);
        assertEquals(expectedY, ctx.ui.waypointY, 1e-6);
    }
}
