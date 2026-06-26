import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ScreenshotRegressionHarnessTest {
    @Test
    void productionScreenshotTargetsRenderAndMatchBaselines() throws Exception {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("win"),
                "approved visual signatures use Windows Java2D font rasterization");
        ScreenshotRegressionHarness.Result result = ScreenshotRegressionHarness.run(
                ScreenshotRegressionHarness.DEFAULT_BASELINE,
                Path.of("build", "reports", "test-visual-regression"),
                true,
                false);

        assertTrue(result.passed(), () -> String.join("\n", result.errors()));
        Set<String> targets = result.captures().stream()
                .map(ScreenshotRegressionHarness.Capture::target)
                .collect(Collectors.toSet());
        assertEquals(Set.of("campaign-map", "fleet-board", "strike-tab", "tactical-hud", "accessibility-hud"), targets);
        assertTrue(result.captures().stream().allMatch(capture -> capture.opaquePixels() > 300_000));
        assertTrue(result.captures().stream().allMatch(capture -> capture.colorBuckets() >= 18));
    }
}
