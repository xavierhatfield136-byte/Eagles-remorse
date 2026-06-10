import java.util.ArrayList;
import java.util.List;

public final class ContentEditorQualitySystem {
    private ContentEditorQualitySystem() {}

    public static List<String> editorWorkflowLines() {
        return List.of(
                "Battlefield Editor UI  |  visual editor wraps the scenario backend without bypassing validation",
                "Canvas Tools  |  selection, drag-and-drop, undo-redo, validation, and save flow",
                "Scenario Logic  |  objectives, triggers, hazards, reinforcements, branching outcomes, and rewards",
                "Fleet Editing  |  player, ally, neutral, and hostile composition editing",
                "Seed / Thumbnail  |  deterministic seed and thumbnail editing for reproducible scenarios",
                "Direct Test-Play  |  launch edited scenario into a local deterministic smoke run"
        );
    }

    public static List<String> contentPackSafetyLines() {
        return List.of(
                "Mod Compatibility Report  |  player-facing report explains missing APIs, conflicts, and warnings",
                "Safe Mode Launcher  |  broken content packs can be skipped without blocking startup",
                "Featured Scenario Browser  |  curated scenarios can be browsed before local install",
                "Local Ratings / Notes  |  players can annotate and rate local scenarios",
                "Malformed Pack Tests  |  malformed and malicious input regression tests protect loading",
                "Missing Pack Diagnostics  |  missing-pack and version-mismatch messages are actionable",
                "Load Order Visualization  |  content priority and overrides are visible",
                "Hot Reload Warning  |  development builds warn when content reload changes live assumptions",
                "Author Templates  |  documentation templates cover scenario, faction, asset, and balance packs"
        );
    }

    public static List<String> allContentEditorLines() {
        ArrayList<String> out = new ArrayList<>();
        out.addAll(editorWorkflowLines());
        out.addAll(contentPackSafetyLines());
        return out;
    }
}
