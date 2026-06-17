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
    void showcaseSpawnsOneFactionBlockWithReadableNames() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.SHOWCASE, 5000, 5000, true, 1234L, false,
                Faction.TEAM_C.teamId()));
        SpawnSystem.initWorld(ctx);

        assertNotNull(ctx.player);
        assertEquals("Showcase Camera", ctx.player.name);
        assertEquals(0, ctx.teamBases.size());
        assertNull(ctx.allyBase);
        assertNull(ctx.enemyBase);
        assertEquals(Faction.TEAM_C, ctx.command.showcaseFaction);

        int expectedPerFaction = ShipRole.values().length;
        for (Faction faction : Faction.fourTeamFactions()) {
            long factionShipCount = ctx.ships.stream()
                    .filter(s -> s != null && s.faction == faction)
                    .count();
            int expected = faction == Faction.TEAM_C ? expectedPerFaction : 0;
            assertEquals(expected, factionShipCount, "unexpected showcase count for " + faction);

            if (faction == Faction.TEAM_C) {
                assertTrue(ctx.ships.stream().anyMatch(s -> s != null && s.faction == faction && s.role == ShipRole.BASE));
                assertTrue(ctx.ships.stream().anyMatch(s -> s != null && s.faction == faction && s.role == ShipRole.MOTHERSHIP));
                assertFalse(ctx.ships.stream().anyMatch(s -> s != null
                        && s.faction == faction
                        && (s.name == null || !s.name.startsWith(faction.teamName() + " "))));
            }
        }
    }

    @Test
    void showcaseOpeningCameraRevealsSelectedFactionBlock() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.SHOWCASE, 5000, 5000, true, 1234L, false));
        SpawnSystem.initWorld(ctx);
        CameraSystem.update(ctx, 1280, 720);

        double viewW = CameraSystem.worldViewWidth(ctx, 1280);
        double viewH = CameraSystem.worldViewHeight(ctx, 720);
        assertTrue(ctx.zoom < 0.5, "showcase should start zoomed out enough for the whole gallery");

        long visible = ctx.ships.stream()
                .filter(s -> s != null && s.faction == Faction.ALLY)
                .filter(s -> s.x >= ctx.camX && s.x <= ctx.camX + viewW)
                .filter(s -> s.y >= ctx.camY && s.y <= ctx.camY + viewH)
                .count();
        assertEquals(ShipRole.values().length, visible, "opening showcase camera should reveal selected faction");

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
        assertTrue(ctx.perf.drawnShips >= ShipRole.values().length,
                "showcase tactical render should draw selected faction block");
    }

    @Test
    void showcaseSwitchingTeamsReplacesPriorShips() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.SHOWCASE, 5000, 5000, true, 1234L, false));
        SpawnSystem.initWorld(ctx);

        int blueCount = ctx.ships.size();
        SpawnSystem.loadShowcaseTeam(ctx, Faction.ENEMY);

        assertEquals(blueCount, ctx.ships.size(), "switching showcase teams should not duplicate ships");
        assertEquals(Faction.ENEMY, ctx.command.showcaseFaction);
        assertEquals(ShipRole.values().length, ctx.ships.stream()
                .filter(s -> s != null && s.faction == Faction.ENEMY)
                .count());
        assertEquals(0, ctx.ships.stream()
                .filter(s -> s != null && s.faction == Faction.ALLY)
                .count());
    }
}
