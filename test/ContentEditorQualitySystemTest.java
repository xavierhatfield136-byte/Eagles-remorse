import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentEditorQualitySystemTest {

    @Test
    void contentEditorLinesCoverEditorWorkflowAndPackSafety() {
        List<String> lines = ContentEditorQualitySystem.allContentEditorLines();

        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Battlefield Editor UI  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Canvas Tools  |  ")
                && line.contains("drag-and-drop") && line.contains("undo-redo")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Scenario Logic  |  ")
                && line.contains("objectives") && line.contains("branching outcomes")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Fleet Editing  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Seed / Thumbnail  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Direct Test-Play  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Mod Compatibility Report  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Safe Mode Launcher  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Featured Scenario Browser  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Local Ratings / Notes  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Malformed Pack Tests  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Missing Pack Diagnostics  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Load Order Visualization  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Hot Reload Warning  |  ")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Author Templates  |  ")));
    }
}
