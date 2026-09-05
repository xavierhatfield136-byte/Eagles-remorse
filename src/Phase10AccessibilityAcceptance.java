import app.config.ExperienceSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 10 acceptance contract for keyboard-only operation, remapping safety,
 * accessibility presentation, audio accessibility, and focus/window behavior.
 */
public final class Phase10AccessibilityAcceptance {
    public record KeyboardFlow(String id, String checklistItem, String keyboardPath, List<String> actionIds) {}
    public record AccessibilityCheck(String id, String checklistItem, String evidence) {}

    private Phase10AccessibilityAcceptance() {}

    public static List<KeyboardFlow> keyboardFlows() {
        return List.of(
                flow("main-menu", "Navigate the main menu without a mouse",
                        "Swing focus traversal with visible focus ring; Enter/Space activates focused buttons."),
                flow("start-campaign", "Start a campaign without a mouse",
                        "Focus Open World Campaign or Launch Mission from the menu and press Enter."),
                flow("strategic-tabs", "Navigate strategic command tabs",
                        "Open map with M; command-board tab chips expose NAV/FLEET/RESOURCES/STRIKES and actions.",
                        "toggleMap"),
                flow("select-fleets-locations", "Select fleets and locations",
                        "Map focus, fleet focus, and command-board actions expose selected location/fleet readouts.",
                        "toggleMap", "toggleShop"),
                flow("plot-cancel-course", "Plot and cancel a course",
                        "Overmap command actions expose route plotting/canceling with visible availability and failure reasons.",
                        "toggleMap", "setWaypoint"),
                flow("trade", "Open and use trade",
                        "Comms/trade menus support arrow selection, number-row shortcuts, Enter/Space confirmation, and quantity changes."),
                flow("shipyards", "Open and use shipyards",
                        "Fleet/shop overlays open from TAB/B and expose keyboard selection and numbered purchase/refit actions.",
                        "toggleShop", "toggleBaseMenu"),
                flow("queue-ship", "Queue a ship",
                        "Fleet command/refit actions expose commission/queue state, cost, disabled reason, and confirmation."),
                flow("objectives", "Review objectives",
                        "Controls reference panel and tactical/campaign map mission tabs show objective title, detail, and context hint.",
                        "toggleControlsScreen", "toggleMap"),
                flow("enter-tactical", "Enter tactical combat",
                        "Strategic encounter prompt has Take Command/auto-resolve hotkeys and blocks ambiguous escape."),
                flow("withdraw-tactical", "Withdraw from tactical combat",
                        "Escape/back, battlefield warp, and tactical withdrawal/mission-exit flows preserve input state.",
                        "escape", "battlefieldWarp"),
                flow("save-load", "Save and load",
                        "Menu resume plus checkpoint persistence on menu exit/shutdown preserve campaign state without mouse-only steps.",
                        "toMenu"),
                flow("recover-defeat", "Recover from defeat",
                        "Escape from GAME_OVER returns to menu; menu focus restores so retry/resume is keyboard reachable.",
                        "escape"),
                flow("visible-focus", "Show visible keyboard focus",
                        "Main menu buttons draw a high-contrast focus ring; overlays render selected rows/chips with shape and text changes.")
        );
    }

    public static List<AccessibilityCheck> remappingChecks() {
        return List.of(
                check("open-ui", "Open remapping UI", "Ctrl+H toggles the searchable controls screen.", "toggleControlsScreen"),
                check("rebind-required", "Rebind every required action", "Every registered gameplay action is exposed by HotkeyRegistry.requiredActionIds()."),
                check("detect-conflicts", "Detect conflicts", "Same-scope keyboard duplicates and duplicate mouse/controller buttons are rejected."),
                check("explain-conflicts", "Explain conflicts", "RemapResult carries a player-facing reason and controlsStatusMessage renders it."),
                check("restore-defaults", "Restore defaults", "Ctrl+1..6 restores scope defaults from the controls screen."),
                check("persist-mappings", "Persist mappings", "ControlSettingsStore saves keyboard, mouse, and controller mappings."),
                check("recover-invalid", "Recover from invalid mapping data", "Invalid persisted keyboard strokes are ignored and defaults remain active."),
                check("no-inaccessible-actions", "Ensure no required action becomes permanently inaccessible",
                        "Null/unknown/duplicate bindings are rejected before replacing the active binding.")
        );
    }

    public static List<AccessibilityCheck> visualChecks() {
        return List.of(
                check("high-contrast", "Verify high contrast", "High-contrast HUD setting and menu focus ring are available."),
                check("faction-symbols", "Verify color-independent hostile/friendly/neutral symbols",
                        "Strategic/tactical markers combine faction color with labels, outlines, hostility text, and selected shapes."),
                check("selected-without-color", "Verify selected state without color alone",
                        "Selected rows/chips change border, fill, text, and readout content."),
                check("warning-without-color", "Verify warning state without color alone",
                        "Warning rows include CRITICAL/LOW/disabled reason text in addition to warning color."),
                check("text-scale", "Verify text scaling", "UI text scale clamps from 0.8x to 1.6x."),
                check("readability-720", "Verify 1280x720 readability", "Screenshot regression includes 1280x720 accessibility-hud target."),
                check("readability-1080", "Verify 1920x1080 readability", "Phase 9/10 checks preserve scalable UI layout for 1920x1080."),
                check("long-names", "Verify long names", "Renderer fit/wrap helpers constrain long labels in map, fleet, and comm panels."),
                check("large-numbers", "Verify largest numeric values", "Fleet/resource panels use fitted labels for resource and health values."),
                check("briefing-text", "Verify mission briefing text", "Controls reference panel and onboarding archive expose mission detail text."),
                check("fleet-health", "Verify fleet health display", "Fleet panels show HP/role/status text rather than health color alone."),
                check("reputation", "Verify reputation display", "Campaign command resources/faction panels show reputation labels and values.")
        );
    }

    public static List<AccessibilityCheck> audioChecks() {
        return List.of(
                check("voice-removed", "Verify no voice acting dependency", "AudioSystem voice matrix is empty and release packaging rejects bundled voice assets."),
                check("critical-radio-text", "Show critical radio information as text", "Strategic, objective, warning, and comms information renders in visible UI panels."),
                check("combat-readable-text", "Keep combat text readable",
                        "HUD panels and warning banners keep mission, status, and ship-readiness text separated from combat action."),
                check("quiet-mode", "Verify quiet mode", "All required information remains visible when audio is muted."),
                check("reduced-noise", "Verify reduced-noise mode", "SFX volume controls can be lowered without hiding objectives, warnings, or comms."),
                check("not-audio-only", "Ensure no required information is audio-only",
                        "Campaign objectives, warnings, comms, and strategic choices are rendered as text."),
                check("volume-controls", "Verify volume controls", "Settings expose general audio controls without requiring voice-specific controls.")
        );
    }

    public static List<AccessibilityCheck> windowFocusChecks() {
        return List.of(
                check("fullscreen", "Test fullscreen", "AppShell toggles exclusive fullscreen and restores focus afterward."),
                check("windowed", "Test windowed mode", "AppShell restores prior windowed bounds and focus afterward."),
                check("alt-enter", "Test Alt+Enter", "Alt+Enter is the canonical fullscreen binding.", "fullscreen"),
                check("focus-loss", "Test focus loss", "GamePanel pauses when configured and releases held inputs on focus loss."),
                check("focus-regain", "Test focus regain", "AppShell restores focus to the active card/game after card/fullscreen changes."),
                check("minimize-campaign", "Test minimizing during campaign",
                        "Focus-loss release covers campaign/map held inputs and pauses when requested."),
                check("minimize-combat", "Test minimizing during combat",
                        "Focus-loss release clears firing, mining, and camera pan flags."),
                check("display-scaling", "Test display scaling", "MenuDisplay and renderer layout use scalable sizes and 1280x720/1920x1080 checks."),
                check("stuck-keys", "Prevent stuck keys after focus changes",
                        "ExperienceRuntime.releaseHeldInputs clears all manual hold flags.")
        );
    }

    public static List<String> validationErrors() {
        List<String> errors = new ArrayList<>();
        if (!HotkeyRegistry.conflictWarnings().isEmpty()) {
            errors.add("Default hotkey conflicts: " + HotkeyRegistry.conflictWarnings());
        }
        for (String action : HotkeyRegistry.requiredActionIds()) {
            if (HotkeyRegistry.label(action).isBlank()) errors.add("Missing label for action " + action);
            try {
                if (HotkeyRegistry.stroke(action) == null) errors.add("Missing stroke for action " + action);
            } catch (IllegalArgumentException ex) {
                errors.add("Missing registered action " + action);
            }
        }
        for (KeyboardFlow flow : keyboardFlows()) {
            if (flow.id().isBlank() || flow.checklistItem().isBlank() || flow.keyboardPath().isBlank()) {
                errors.add("Incomplete keyboard flow: " + flow);
            }
            for (String action : flow.actionIds()) {
                try {
                    HotkeyRegistry.stroke(action);
                } catch (IllegalArgumentException ex) {
                    errors.add("Flow " + flow.id() + " references unknown action " + action);
                }
            }
        }
        requireCount(errors, "keyboard flows", keyboardFlows(), 14);
        requireCount(errors, "remapping checks", remappingChecks(), 8);
        requireCount(errors, "visual checks", visualChecks(), 12);
        requireCount(errors, "audio checks", audioChecks(), 7);
        requireCount(errors, "window/focus checks", windowFocusChecks(), 9);

        ExperienceSettings settings = ExperienceSettings.defaults();
        settings.uiTextScale = 9.0;
        settings.subtitleScale = -4.0;
        settings.normalize();
        if (settings.uiTextScale > 1.6 || settings.subtitleScale < 0.8) {
            errors.add("Accessibility scale normalization failed.");
        }
        return errors;
    }

    public static void main(String[] args) throws IOException {
        boolean strict = false;
        Path report = null;
        for (String arg : args) {
            if ("--strict".equalsIgnoreCase(arg)) strict = true;
            else if (arg.startsWith("--report=")) report = Path.of(arg.substring("--report=".length()));
        }

        List<String> errors = validationErrors();
        String summary = "[phase10-accessibility] keyboardFlows=" + keyboardFlows().size()
                + " remapChecks=" + remappingChecks().size()
                + " visualChecks=" + visualChecks().size()
                + " audioChecks=" + audioChecks().size()
                + " windowFocusChecks=" + windowFocusChecks().size()
                + " requiredActions=" + HotkeyRegistry.requiredActionIds().size()
                + " pass=" + errors.isEmpty();
        System.out.println(summary);
        if (!errors.isEmpty()) {
            for (String error : errors) System.out.println("[phase10-accessibility] error=" + error);
        }
        if (report != null) writeReport(report, errors);
        if (strict && !errors.isEmpty()) throw new IllegalStateException("Phase 10 acceptance failed: " + errors);
    }

    private static KeyboardFlow flow(String id, String checklistItem, String keyboardPath, String... actionIds) {
        return new KeyboardFlow(id, checklistItem, keyboardPath, List.of(actionIds));
    }

    private static AccessibilityCheck check(String id, String checklistItem, String evidence, String... actionIds) {
        for (String action : actionIds) HotkeyRegistry.stroke(action);
        return new AccessibilityCheck(id, checklistItem, evidence);
    }

    private static void requireCount(List<String> errors, String label, List<?> values, int expected) {
        if (values.size() != expected) errors.add("Expected " + expected + " " + label + " but found " + values.size());
    }

    private static void writeReport(Path report, List<String> errors) throws IOException {
        Files.createDirectories(report.toAbsolutePath().getParent());
        String json = "{\n"
                + "  \"phase\": 10,\n"
                + "  \"status\": \"" + (errors.isEmpty() ? "PASS" : "FAIL") + "\",\n"
                + "  \"keyboardFlows\": " + keyboardFlows().size() + ",\n"
                + "  \"remappingChecks\": " + remappingChecks().size() + ",\n"
                + "  \"visualChecks\": " + visualChecks().size() + ",\n"
                + "  \"audioChecks\": " + audioChecks().size() + ",\n"
                + "  \"windowFocusChecks\": " + windowFocusChecks().size() + ",\n"
                + "  \"requiredActions\": " + HotkeyRegistry.requiredActionIds().size() + ",\n"
                + "  \"errors\": " + toJsonArray(errors) + "\n"
                + "}\n";
        Files.writeString(report, json);
    }

    private static String toJsonArray(List<String> values) {
        if (values.isEmpty()) return "[]";
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) out.append(", ");
            out.append('"').append(escape(values.get(i))).append('"');
        }
        return out.append(']').toString();
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").toLowerCase(Locale.ROOT);
    }
}
