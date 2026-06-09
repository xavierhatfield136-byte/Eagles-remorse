import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShowcaseSpawnLayoutTest {

    @Test
    void showcaseSpawnsFullFactionBlocksWithReadableNames() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.SHOWCASE, 5000, 5000, true, 1234L, false));
        SpawnSystem.initWorld(ctx);

        assertNotNull(ctx.player);
        assertEquals("Showcase Camera", ctx.player.name);
        assertEquals(0, ctx.teamBases.size());
        assertNull(ctx.allyBase);
        assertNull(ctx.enemyBase);

        int expectedPerFaction = ShipRole.values().length;
        for (Faction faction : Faction.fourTeamFactions()) {
            long factionShipCount = ctx.ships.stream()
                    .filter(s -> s != null && s.faction == faction)
                    .count();
            assertEquals(expectedPerFaction, factionShipCount, "unexpected showcase count for " + faction);

            assertTrue(ctx.ships.stream().anyMatch(s -> s != null && s.faction == faction && s.role == ShipRole.BASE));
            assertTrue(ctx.ships.stream().anyMatch(s -> s != null && s.faction == faction && s.role == ShipRole.MOTHERSHIP));
            assertFalse(ctx.ships.stream().anyMatch(s -> s != null
                    && s.faction == faction
                    && (s.name == null || !s.name.startsWith(faction.teamName() + " "))));
        }
    }

    @Test
    void showcaseOpeningCameraRevealsEveryFactionBlock() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.SHOWCASE, 5000, 5000, true, 1234L, false));
        SpawnSystem.initWorld(ctx);
        CameraSystem.update(ctx, 1280, 720);

        double viewW = CameraSystem.worldViewWidth(ctx, 1280);
        double viewH = CameraSystem.worldViewHeight(ctx, 720);
        assertTrue(ctx.zoom < 0.5, "showcase should start zoomed out enough for the whole gallery");

        for (Faction faction : Faction.fourTeamFactions()) {
            long visible = ctx.ships.stream()
                    .filter(s -> s != null && s.faction == faction)
                    .filter(s -> s.x >= ctx.camX && s.x <= ctx.camX + viewW)
                    .filter(s -> s.y >= ctx.camY && s.y <= ctx.camY + viewH)
                    .count();
            assertEquals(ShipRole.values().length, visible, "opening showcase camera should reveal " + faction.teamName());
        }

        assertEquals(ctx.ships.size(), GameRenderSystem.renderScopedShips(ctx, ctx.ships).size(),
                "showcase rendering should not hide factions behind battlefield sector scoping");

        ctx.ui.tacticalViewEnabled = true;
        BufferedImage image = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        try {
            GameRenderSystem.render(ctx, g2, 1280, 720);
        } finally {
            g2.dispose();
        }
        assertTrue(ctx.perf.drawnShips >= ShipRole.values().length * Faction.fourTeamFactions().length,
                "showcase tactical render should draw every faction block");
    }
}
