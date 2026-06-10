import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UiAccessibilityQualitySystemTest {

    @Test
    void uiAccessibilityLinesCoverLayoutActionsInputWarningsAndRegression() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 303L, false));
        SpawnSystem.initWorld(ctx);

        List<String> lines = UiAccessibilityQualitySystem.allUiAccessibilityLines(ctx);

        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Overlay Audit  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Action Strip  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Disabled Reason  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Command Filters  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Dense Calm Boards  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Controls Search  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Keyboard Smoke  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("High Contrast Projectiles  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Reduced Noise Audio  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Caption Priority  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Warning Hierarchy  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("High-DPI Map Icons  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Tooltip Delay  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Controller Polish  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Recent Messages  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Irreversible Confirm  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Resource Language  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Release Notes  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Visual Regression  |  ")));
    }
}
