import app.state.AssetLoadGuard;
import app.state.PerfTelemetry;
import app.state.SpriteAtlasRegistry;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
