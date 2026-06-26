import app.state.AssetLoadGuard;
import app.state.PerfTelemetry;
import app.state.SpriteAtlasRegistry;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerformanceGuardrailsTest {
    @Test
    void guardedDecodeReportsRenderedFrameAndBuildsCompactAtlas() throws Exception {
        AssetLoadGuard.resetForTest();
        SpriteAtlasRegistry.clearForTest();
        Path imageFile = Files.createTempFile("asset-load-guard", ".png");
        ImageIO.write(new BufferedImage(8, 6, BufferedImage.TYPE_INT_ARGB), "png", imageFile.toFile());

        AssetLoadGuard.markGameplayBegun();
        AssetLoadGuard.beginRenderedFrame();
        try {
            assertNotNull(AssetLoadGuard.read(imageFile.toFile(), "test"));
        } finally {
            AssetLoadGuard.endRenderedFrame();
        }

        assertEquals(1, AssetLoadGuard.decodeDuringFrameCount());
        assertTrue(AssetLoadGuard.lastWarning().contains("IMAGE DECODE DURING RENDER"));
        assertNotNull(SpriteAtlasRegistry.atlas("test"));
        Files.deleteIfExists(imageFile);
    }

    @Test
    void sustainedFramePressureDegradesVisualQuality() {
        PerformanceGuardrails.resetForTest();
        PerfTelemetry perf = new PerfTelemetry();
        perf.frameMs = 28.0;
        perf.renderMs = 24.0;

        for (int i = 0; i < 100; i++) PerformanceGuardrails.update(perf);

        assertTrue(PerformanceGuardrails.quality().ordinal() >= PerformanceGuardrails.VisualQuality.MEDIUM.ordinal());
        assertTrue(PerformanceGuardrails.projectileTrailStride() >= 2);
    }

    @Test
    void visualQualityNeverReplacesShipsUnlessPlayerRequestsFpsView() {
        PerformanceGuardrails.resetForTest();
        PerfTelemetry perf = new PerfTelemetry();
        perf.frameMs = 28.0;
        perf.renderMs = 24.0;
        for (int i = 0; i < 270; i++) PerformanceGuardrails.update(perf);
        assertEquals(PerformanceGuardrails.VisualQuality.EMERGENCY, PerformanceGuardrails.quality());

        GameContext ctx = new GameContext(new app.config.GameConfig(app.config.GameMode.SHOWCASE, 6000, 6000, true, 42L, false));
        ctx.player = new Player(ShipRole.FRIGATE, 3000.0, 3000.0);
        ctx.player.angle = 0.0;
        ctx.player.faction = Faction.ALLY;

        assertFalse(Renderer.performanceTokenMode(ctx),
                "automatic quality degradation should keep normal ship/background rendering unless the player requests FPS view");

        ctx.ui.tacticalViewEnabled = true;
        assertTrue(Renderer.performanceTokenMode(ctx), "J/FPS view should manually enable performance ship tokens");

        Ship nearby = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 3700.0, 3000.0);
        Ship forward = new FleetShip(ShipRole.BATTLECRUISER, Faction.ENEMY, 5000.0, 3000.0);
        Ship distantBehind = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 3000.0, 800.0);

        assertFalse(Renderer.shouldDrawPerformanceToken(ctx, nearby),
                "nearby ships should stay readable in manual FPS view");
        assertFalse(Renderer.shouldDrawPerformanceToken(ctx, forward),
                "ships directly in front of the player should stay readable in manual FPS view");
        assertTrue(Renderer.shouldDrawPerformanceToken(ctx, distantBehind),
                "distant off-screen/behind ships may use low-render tokens");
    }
}
