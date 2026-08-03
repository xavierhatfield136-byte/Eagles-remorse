import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Properties;
import java.util.Locale;

/**
 * Canonical keyboard-control catalog for registered gameplay actions and rendered help text.
 */
public final class HotkeyRegistry {
    public enum Scope {
        GLOBAL,
        TACTICAL,
        OVERMAP,
        MODAL,
        SHOP,
        FLEET_EDITOR
    }

    public record Binding(Scope scope, String action, KeyStroke stroke, String label) {}
    public record RemapResult(boolean accepted, String message) {}

    private static final Map<String, Binding> DEFAULT_BINDINGS = buildBindings();
    private static final Map<String, Binding> BINDINGS = loadBindings();
    private static final Map<String, Integer> DEFAULT_MOUSE = defaultMouseBindings();
    private static final Map<String, Integer> MOUSE = loadIntBindings("mouse.", DEFAULT_MOUSE);
    private static final Map<String, String> DEFAULT_CONTROLLER = defaultControllerBindings();
    private static final Map<String, String> CONTROLLER = loadStringBindings("controller.", DEFAULT_CONTROLLER);
    private static String lastInputDevice = "KEYBOARD";

    private HotkeyRegistry() {}

    public static KeyStroke stroke(String action) {
        Binding binding = BINDINGS.get(action);
        if (binding == null) throw new IllegalArgumentException("Unknown hotkey action: " + action);
        return binding.stroke();
    }

    public static boolean matches(String action, KeyEvent event) {
        if (event == null) return false;
        Binding binding = BINDINGS.get(action);
        if (binding == null || binding.stroke() == null) return false;
        KeyStroke stroke = binding.stroke();
        return stroke.getKeyCode() == event.getKeyCode()
                && normalizedKeyModifiers(stroke.getModifiers()) == normalizedKeyModifiers(event.getModifiersEx());
    }

    public static String label(String action) {
        Binding binding = BINDINGS.get(action);
        return (binding == null) ? "" : binding.label();
    }

    public static List<Binding> bindings() {
        return List.copyOf(BINDINGS.values());
    }

    public static List<Binding> defaultBindings() {
        return List.copyOf(DEFAULT_BINDINGS.values());
    }

    public static synchronized boolean remapKeyboard(String action, KeyStroke stroke) {
        return remapKeyboardDetailed(action, stroke).accepted();
    }

    public static synchronized RemapResult remapKeyboardDetailed(String action, KeyStroke stroke) {
        Binding previous = BINDINGS.get(action);
        RemapResult validation = validateKeyboardCandidate(BINDINGS, previous, stroke);
        if (!validation.accepted()) return validation;
        BINDINGS.put(action, new Binding(previous.scope(), action, stroke, keyLabel(stroke)));
        persist();
        return new RemapResult(true, action + " rebound to " + keyLabel(stroke));
    }

    public static synchronized boolean remapMouse(String action, int button) {
        return remapMouseDetailed(action, button).accepted();
    }

    public static synchronized RemapResult remapMouseDetailed(String action, int button) {
        if (!DEFAULT_MOUSE.containsKey(action)) return new RemapResult(false, "Unknown mouse action: " + action);
        if (button <= 0) return new RemapResult(false, "Mouse binding must use a real button.");
        for (Map.Entry<String, Integer> entry : MOUSE.entrySet()) {
            if (!entry.getKey().equals(action) && entry.getValue() == button) {
                return new RemapResult(false, "Mouse button " + button + " is already assigned to " + entry.getKey() + ".");
            }
        }
        MOUSE.put(action, button);
        persist();
        return new RemapResult(true, action + " rebound to mouse button " + button);
    }

    public static synchronized RemapResult replaceAllSerialized(Map<String, String> keyboard,
                                                                Map<String, Integer> mouse) {
        Map<String, Binding> keyboardCandidate = new LinkedHashMap<>(DEFAULT_BINDINGS);
        if (keyboard != null) {
            for (Binding binding : DEFAULT_BINDINGS.values()) {
                String raw = keyboard.get(binding.action());
                if (raw == null || raw.isBlank()) continue;
                KeyStroke stroke = KeyStroke.getKeyStroke(raw);
                if (stroke == null) return new RemapResult(false, "Keyboard binding must use a real key.");
                keyboardCandidate.put(binding.action(),
                        new Binding(binding.scope(), binding.action(), stroke, keyLabel(stroke)));
            }
        }
        RemapResult keyboardValidation = validateKeyboardCandidateSet(keyboardCandidate);
        if (!keyboardValidation.accepted()) return keyboardValidation;

        Map<String, Integer> mouseCandidate = new LinkedHashMap<>(DEFAULT_MOUSE);
        if (mouse != null) {
            for (Map.Entry<String, Integer> entry : mouse.entrySet()) {
                String action = entry.getKey();
                if (!DEFAULT_MOUSE.containsKey(action)) {
                    return new RemapResult(false, "Unknown mouse action: " + action);
                }
                Integer button = entry.getValue();
                if (button == null || button <= 0) {
                    return new RemapResult(false, "Mouse binding must use a real button.");
                }
                mouseCandidate.put(action, button);
            }
        }
        RemapResult mouseValidation = validateMouseCandidate(mouseCandidate);
        if (!mouseValidation.accepted()) return mouseValidation;

        BINDINGS.clear();
        BINDINGS.putAll(keyboardCandidate);
        MOUSE.clear();
        MOUSE.putAll(mouseCandidate);
        persist();
        return new RemapResult(true, "Controls saved.");
    }

    public static synchronized boolean remapController(String action, String button) {
        return remapControllerDetailed(action, button).accepted();
    }

    public static synchronized RemapResult remapControllerDetailed(String action, String button) {
        if (!DEFAULT_CONTROLLER.containsKey(action)) return new RemapResult(false, "Unknown controller action: " + action);
        if (button == null || button.isBlank()) return new RemapResult(false, "Controller binding must use a real button.");
        String normalized = button.trim().toUpperCase(Locale.US);
        for (Map.Entry<String, String> entry : CONTROLLER.entrySet()) {
            if (!entry.getKey().equals(action) && normalized.equalsIgnoreCase(entry.getValue())) {
                return new RemapResult(false, normalized + " is already assigned to " + entry.getKey() + ".");
            }
        }
        CONTROLLER.put(action, normalized);
        persist();
        return new RemapResult(true, action + " rebound to " + normalized);
    }

    public static synchronized void restoreDefaults(Scope scope) {
        for (Binding binding : DEFAULT_BINDINGS.values()) {
            if (binding.scope() == scope) BINDINGS.put(binding.action(), binding);
        }
        if (scope == Scope.TACTICAL) {
            MOUSE.clear();
            MOUSE.putAll(DEFAULT_MOUSE);
            CONTROLLER.clear();
            CONTROLLER.putAll(DEFAULT_CONTROLLER);
        }
        persist();
    }

    public static synchronized void restoreAllDefaults() {
        BINDINGS.clear();
        BINDINGS.putAll(DEFAULT_BINDINGS);
        MOUSE.clear();
        MOUSE.putAll(DEFAULT_MOUSE);
        CONTROLLER.clear();
        CONTROLLER.putAll(DEFAULT_CONTROLLER);
        persist();
    }

    public static int mouseButton(String action) { return MOUSE.getOrDefault(action, -1); }
    public static int defaultMouseButton(String action) { return DEFAULT_MOUSE.getOrDefault(action, -1); }
    public static List<String> mouseActionIds() { return List.copyOf(DEFAULT_MOUSE.keySet()); }
    public static String controllerButton(String action) { return CONTROLLER.getOrDefault(action, ""); }
    public static String glyph(String action) {
        return "CONTROLLER".equals(lastInputDevice) ? controllerButton(action) : label(action);
    }
    public static String movementLabel() {
        return label("moveForward") + "/" + label("moveBackward") + "/" + label("turnLeft") + "/" + label("turnRight");
    }
    public static String movementGlyphLabel() {
        return glyphOrKeyboard("moveForward") + "/" + glyphOrKeyboard("moveBackward") + "/"
                + glyphOrKeyboard("turnLeft") + "/" + glyphOrKeyboard("turnRight");
    }
    public static void noteKeyboardInput() { lastInputDevice = "KEYBOARD"; }
    public static void noteMouseInput() { lastInputDevice = "MOUSE"; }
    public static void noteControllerInput() { lastInputDevice = "CONTROLLER"; }
    public static String lastInputDevice() { return lastInputDevice; }

    public static List<Binding> search(String query) {
        String needle = (query == null) ? "" : query.trim().toLowerCase(Locale.US);
        List<Binding> out = new ArrayList<>();
        for (Binding binding : BINDINGS.values()) {
            if (needle.isBlank() || binding.action().toLowerCase(Locale.US).contains(needle)
                    || binding.label().toLowerCase(Locale.US).contains(needle)
                    || binding.scope().name().toLowerCase(Locale.US).contains(needle)) out.add(binding);
        }
        return out;
    }

    public static List<String> currentContextLegend(GameContext ctx) {
        List<String> rows = new ArrayList<>();
        rows.add(glyph("escape") + " back");
        if (ctx == null || ctx.ui == null) return rows;
        if (ctx.ui.powerManagementOpen) rows.add("1-6 bus  F1-F4 preset");
        else if (ctx.ui.crewStationsOpen) rows.add("F1-F5 station  A automation");
        else if (ctx.ui.flightDeckOpen) rows.add("F1-F5 slot  [/] focus");
        else if (ctx.ui.shopOpen) rows.add(glyph("toggleShop") + " close  arrows browse");
        else if (ctx.ui.mapOpen) rows.add("LMB waypoint  RMB ping  Ctrl +/- zoom");
        else {
            rows.add(movementGlyphLabel() + " move  " + glyph("primaryDown") + "/LMB fire all");
            rows.add(glyph("toggleMap") + " map  " + glyph("toggleCrewStations") + " crew");
        }
        return rows;
    }

    public static List<String> conflictWarnings() {
        List<String> out = new ArrayList<>(duplicateUnqualifiedBindings());
        Map<Integer, String> mouseOwners = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : MOUSE.entrySet()) {
            String previous = mouseOwners.putIfAbsent(entry.getValue(), entry.getKey());
            if (previous != null) out.add("MOUSE" + entry.getValue() + ": " + previous + " / " + entry.getKey());
        }
        return out;
    }

    public static List<String> requiredActionIds() {
        return List.copyOf(DEFAULT_BINDINGS.keySet());
    }

    public static String actionDescription(String action) {
        if (action == null) return "";
        return switch (action) {
            case "moveForward" -> "Thrust the player ship forward along its current heading.";
            case "moveBackward" -> "Reverse thrust the player ship.";
            case "turnLeft" -> "Rotate the player ship left.";
            case "turnRight" -> "Rotate the player ship right.";
            case "toggleShop" -> "Open or close fleet management and shop screens.";
            case "escape" -> "Pause, close menus, or return from game-over screens.";
            case "toggleBaseMenu" -> "Open or close base upgrades and command actions.";
            case "togglePowerManagement" -> "Open or close ship power management.";
            case "toggleCrewStations" -> "Open or close crew station command controls.";
            case "toggleFlightDeck" -> "Open or close carrier flight deck controls.";
            case "lockUnderMouse" -> "Lock the target nearest the cursor.";
            case "cycleCommIntent" -> "Cycle quick communication intent.";
            case "hailContact" -> "Hail the selected or locked contact.";
            case "cycleLeft" -> "Cycle the locked target backward.";
            case "cycleRight" -> "Cycle the locked target forward.";
            case "toggleMap", "toggleMapUp" -> "Open, close, or toggle the map according to the active input mode.";
            case "cycleHudDetail" -> "Keep the HUD in full visibility mode.";
            case "toggleTacticalView" -> "Switch tactical camera presentation.";
            case "cycleXrayFilter" -> "Cycle x-ray room inspection filters.";
            case "clearXrayFocus" -> "Clear the focused x-ray room.";
            case "pingAtCursor" -> "Drop a tactical or strategic ping at the cursor.";
            case "setWaypoint" -> "Set a waypoint at the cursor.";
            case "zoomIn" -> "Zoom the active map or tactical camera in.";
            case "zoomOut" -> "Zoom the active map or tactical camera out.";
            case "zoomReset" -> "Reset the active map or tactical camera zoom.";
            case "toggleTurretAuto" -> "Toggle turret auto-lock.";
            case "fullscreen" -> "Toggle fullscreen.";
            case "miningDown", "miningUp" -> "Start or stop mining according to the active input mode.";
            case "shieldOvercharge" -> "Trigger shield overcharge.";
            case "superweapon" -> "Fire or charge the superweapon when available.";
            case "carrierLaunch" -> "Launch carrier craft.";
            case "carrierRecall" -> "Recall carrier craft.";
            case "carrierMode" -> "Cycle carrier wing behavior.";
            case "carrierAutoLaunch" -> "Toggle carrier auto-launch.";
            case "battlefieldWarp", "teleportToBase" -> "Start or cancel battlefield warp.";
            case "cyclePowerPreset" -> "Cycle player ship power preset.";
            case "cycleCrewOrder" -> "Cycle player ship crew order.";
            case "toggleEmergencyThrust" -> "Toggle emergency thrust mode.";
            case "toMenu" -> "Return to the main menu.";
            case "overlayDiagnostics" -> "Print overlay diagnostics for debugging.";
            case "toggleControlsScreen" -> "Open or close the in-game controls reference.";
            case "skipOnboardingBeat" -> "Skip the current tutorial or onboarding beat.";
            case "toggleTutorialArchive" -> "Open or close tutorial archive text.";
            case "toggleTacticalOrders" -> "Open or close the tactical orders overlay.";
            case "cycleTacticalOrder" -> "Cycle the active tactical order.";
            case "toggleTacticalPause" -> "Pause or resume tactical command time when allowed.";
            case "cycleSupportMode" -> "Cycle tactical support mode.";
            case "activateSupportMode" -> "Activate the selected support mode at the cursor.";
            case "toggleOrientationHold" -> "Toggle tactical orientation hold.";
            case "toggleBulkheads" -> "Toggle bulkhead control.";
            case "weaponOverdrive" -> "Toggle weapon overdrive.";
            case "cyclePointDefensePriority" -> "Cycle point-defense priority.";
            case "cycleTacticalDoctrine" -> "Cycle tactical doctrine.";
            case "cycleTacticalGroup" -> "Cycle tactical group.";
            case "scuttleDisabledShip" -> "Scuttle a nearby disabled ship.";
            case "primaryDown", "primaryUp" -> "Start or stop primary fire.";
            case "secondaryDown", "secondaryUp" -> "Start or stop missile-focus fire.";
            case "modalConfirm" -> "Confirm modal prompts.";
            case "shopClose" -> "Close shop screens.";
            case "fleetClose" -> "Close fleet editor screens.";
            default -> humanizeAction(action);
        };
    }

    public static List<String> duplicateUnqualifiedBindings() {
        Map<String, String> owners = new LinkedHashMap<>();
        List<String> duplicates = new ArrayList<>();
        for (Binding binding : BINDINGS.values()) {
            if (binding.stroke().getModifiers() != 0 || binding.stroke().isOnKeyRelease()) continue;
            String qualifiedStroke = binding.scope().name() + ":" + binding.stroke();
            String previous = owners.putIfAbsent(qualifiedStroke, binding.action());
            if (previous != null && !previous.equals(binding.action())) {
                duplicates.add(binding.label() + ": " + previous + " / " + binding.action());
            }
        }
        return duplicates;
    }

    public static List<String> hudHelpRows(boolean carrier, GameContext.HudDetail detail) {
        List<String> rows = new ArrayList<>();

        rows.add("COMBAT: " + label("primaryDown") + "/LMB fire all | "
                + label("secondaryDown") + " missile focus | RMB comms | "
                + label("lockUnderMouse") + "/MMB lock | " + label("toggleTacticalView") + " FPS view");
        rows.add("NAV: " + movementLabel() + " move | arrows pan | " + label("toggleShop") + " fleet management | "
                + label("toggleBaseMenu") + " command upgrades | " + label("toggleMap") + " map");
        rows.add("SYSTEMS: " + label("togglePowerManagement") + " power | " + label("toggleBaseMenu") + " base | "
                + label("pingAtCursor") + " ping | " + label("setWaypoint") + " waypoint");
        rows.add("COMMS: " + label("cycleCommIntent") + " cycle intent | " + label("hailContact") + " hail target");
        rows.add("SPECIAL: " + label("miningDown") + " mine | " + label("shieldOvercharge") + " overcharge | "
                + label("toggleEmergencyThrust") + " thrust | " + label("cyclePowerPreset") + " preset | "
                + label("toggleTurretAuto") + " auto-lock");
        rows.add("EXTRAS: " + label("superweapon") + " superweapon | " + label("cycleXrayFilter") + " xray filter | "
                + label("clearXrayFocus") + " xray clear | " + label("battlefieldWarp") + "/BKSP warp | Ctrl +/-/0 zoom");
        if (carrier) {
            rows.add("CARRIER: " + label("carrierLaunch") + " launch | " + label("carrierRecall") + " recall | "
                    + label("carrierMode") + " mode | " + label("carrierAutoLaunch") + " auto-launch");
        }
        rows.add("META: " + label("escape") + " pause/resume | " + label("fullscreen") + " fullscreen");
        return rows;
    }

    public static String renderedHelpText(boolean carrier, GameContext.HudDetail detail) {
        return String.join("\n", hudHelpRows(carrier, detail));
    }

    public static Set<Scope> scopes() {
        Set<Scope> scopes = new LinkedHashSet<>();
        for (Binding binding : BINDINGS.values()) scopes.add(binding.scope());
        return Collections.unmodifiableSet(scopes);
    }

    private static Map<String, Binding> buildBindings() {
        Map<String, Binding> bindings = new LinkedHashMap<>();
        add(bindings, Scope.TACTICAL, "moveForward", KeyEvent.VK_W, 0, false, "W");
        add(bindings, Scope.TACTICAL, "moveBackward", KeyEvent.VK_S, 0, false, "S");
        add(bindings, Scope.TACTICAL, "turnLeft", KeyEvent.VK_A, 0, false, "A");
        add(bindings, Scope.TACTICAL, "turnRight", KeyEvent.VK_D, 0, false, "D");
        add(bindings, Scope.GLOBAL, "toggleShop", KeyEvent.VK_TAB, 0, false, "TAB");
        add(bindings, Scope.GLOBAL, "escape", KeyEvent.VK_ESCAPE, 0, false, "ESC");
        add(bindings, Scope.GLOBAL, "toggleBaseMenu", KeyEvent.VK_B, 0, false, "B");
        add(bindings, Scope.GLOBAL, "togglePowerManagement", KeyEvent.VK_O, 0, false, "O");
        add(bindings, Scope.GLOBAL, "toggleCrewStations", KeyEvent.VK_H, 0, false, "H");
        add(bindings, Scope.GLOBAL, "toggleFlightDeck", KeyEvent.VK_SLASH, 0, false, "/");
        add(bindings, Scope.TACTICAL, "lockUnderMouse", KeyEvent.VK_L, 0, false, "L");
        add(bindings, Scope.TACTICAL, "cycleCommIntent", KeyEvent.VK_I, 0, false, "I");
        add(bindings, Scope.TACTICAL, "hailContact", KeyEvent.VK_K, 0, false, "K");
        add(bindings, Scope.TACTICAL, "cycleLeft", KeyEvent.VK_OPEN_BRACKET, 0, false, "[");
        add(bindings, Scope.TACTICAL, "cycleRight", KeyEvent.VK_CLOSE_BRACKET, 0, false, "]");
        add(bindings, Scope.OVERMAP, "toggleMap", KeyEvent.VK_M, 0, false, "M");
        add(bindings, Scope.OVERMAP, "toggleMapUp", KeyEvent.VK_M, 0, true, "M");
        add(bindings, Scope.GLOBAL, "cycleHudDetail", KeyEvent.VK_N, 0, false, "N");
        add(bindings, Scope.TACTICAL, "toggleTacticalView", KeyEvent.VK_J, 0, false, "J");
        add(bindings, Scope.TACTICAL, "cycleXrayFilter", KeyEvent.VK_BACK_QUOTE, 0, false, "`");
        add(bindings, Scope.TACTICAL, "clearXrayFocus", KeyEvent.VK_QUOTE, 0, false, "'");
        add(bindings, Scope.OVERMAP, "pingAtCursor", KeyEvent.VK_P, 0, false, "P");
        add(bindings, Scope.OVERMAP, "setWaypoint", KeyEvent.VK_G, 0, false, "G");
        add(bindings, Scope.GLOBAL, "zoomIn", KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK, false, "Ctrl++");
        add(bindings, Scope.GLOBAL, "zoomOut", KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK, false, "Ctrl+-");
        add(bindings, Scope.GLOBAL, "zoomReset", KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK, false, "Ctrl+0");
        add(bindings, Scope.TACTICAL, "toggleTurretAuto", KeyEvent.VK_T, 0, false, "T");
        add(bindings, Scope.GLOBAL, "fullscreen", KeyEvent.VK_ENTER, InputEvent.ALT_DOWN_MASK, false, "Alt+Enter");
        add(bindings, Scope.TACTICAL, "miningDown", KeyEvent.VK_F, 0, false, "F");
        add(bindings, Scope.TACTICAL, "miningUp", KeyEvent.VK_F, 0, true, "F");
        add(bindings, Scope.TACTICAL, "shieldOvercharge", KeyEvent.VK_E, 0, false, "E");
        add(bindings, Scope.TACTICAL, "superweapon", KeyEvent.VK_X, 0, false, "X");
        add(bindings, Scope.TACTICAL, "carrierLaunch", KeyEvent.VK_C, 0, false, "C");
        add(bindings, Scope.TACTICAL, "carrierRecall", KeyEvent.VK_R, 0, false, "R");
        add(bindings, Scope.TACTICAL, "carrierMode", KeyEvent.VK_V, 0, false, "V");
        add(bindings, Scope.TACTICAL, "carrierAutoLaunch", KeyEvent.VK_Z, 0, false, "Z");
        add(bindings, Scope.TACTICAL, "battlefieldWarp", KeyEvent.VK_MINUS, 0, false, "-");
        add(bindings, Scope.TACTICAL, "teleportToBase", KeyEvent.VK_BACK_SPACE, 0, false, "BKSP");
        add(bindings, Scope.TACTICAL, "cyclePowerPreset", KeyEvent.VK_Y, 0, false, "Y");
        add(bindings, Scope.TACTICAL, "cycleCrewOrder", KeyEvent.VK_U, 0, false, "U");
        add(bindings, Scope.TACTICAL, "toggleEmergencyThrust", KeyEvent.VK_SEMICOLON, 0, false, ";");
        add(bindings, Scope.GLOBAL, "toMenu", KeyEvent.VK_F10, 0, false, "F10");
        add(bindings, Scope.GLOBAL, "overlayDiagnostics", KeyEvent.VK_F6, InputEvent.CTRL_DOWN_MASK, false, "Ctrl+F6");
        add(bindings, Scope.GLOBAL, "toggleControlsScreen", KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK, false, "Ctrl+H");
        add(bindings, Scope.GLOBAL, "skipOnboardingBeat", KeyEvent.VK_F1, InputEvent.CTRL_DOWN_MASK, false, "Ctrl+F1");
        add(bindings, Scope.GLOBAL, "toggleTutorialArchive", KeyEvent.VK_F2, InputEvent.CTRL_DOWN_MASK, false, "Ctrl+F2");
        add(bindings, Scope.TACTICAL, "toggleTacticalOrders", KeyEvent.VK_F3, InputEvent.CTRL_DOWN_MASK, false, "Ctrl+F3");
        add(bindings, Scope.TACTICAL, "cycleTacticalOrder", KeyEvent.VK_Q, 0, false, "Q");
        add(bindings, Scope.TACTICAL, "toggleTacticalPause", KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK, false, "Ctrl+P");
        add(bindings, Scope.TACTICAL, "cycleSupportMode", KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK, false, "Ctrl+T");
        add(bindings, Scope.TACTICAL, "activateSupportMode", KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK, false, "Ctrl+R");
        add(bindings, Scope.TACTICAL, "toggleOrientationHold", KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK, false, "Ctrl+O");
        add(bindings, Scope.TACTICAL, "toggleBulkheads", KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK, false, "Ctrl+B");
        add(bindings, Scope.TACTICAL, "weaponOverdrive", KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK, false, "Ctrl+X");
        add(bindings, Scope.TACTICAL, "cyclePointDefensePriority", KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK, false, "Ctrl+D");
        add(bindings, Scope.TACTICAL, "cycleTacticalDoctrine", KeyEvent.VK_J, InputEvent.CTRL_DOWN_MASK, false, "Ctrl+J");
        add(bindings, Scope.TACTICAL, "cycleTacticalGroup", KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK, false, "Ctrl+K");
        add(bindings, Scope.TACTICAL, "scuttleDisabledShip", KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK, false, "Ctrl+S");
        add(bindings, Scope.TACTICAL, "primaryDown", KeyEvent.VK_SPACE, 0, false, "SPACE");
        add(bindings, Scope.TACTICAL, "primaryUp", KeyEvent.VK_SPACE, 0, true, "SPACE");
        add(bindings, Scope.TACTICAL, "secondaryDown", KeyEvent.VK_SHIFT, 0, false, "SHIFT");
        add(bindings, Scope.TACTICAL, "secondaryUp", KeyEvent.VK_SHIFT, 0, true, "SHIFT");
        add(bindings, Scope.MODAL, "modalConfirm", KeyEvent.VK_ENTER, 0, false, "ENTER");
        add(bindings, Scope.SHOP, "shopClose", KeyEvent.VK_TAB, 0, false, "TAB");
        add(bindings, Scope.FLEET_EDITOR, "fleetClose", KeyEvent.VK_TAB, 0, false, "TAB");
        return bindings;
    }

    private static Map<String, Binding> loadBindings() {
        Map<String, Binding> out = new LinkedHashMap<>(DEFAULT_BINDINGS);
        Properties props = ControlSettingsStore.load();
        for (Binding binding : DEFAULT_BINDINGS.values()) {
            String raw = props.getProperty("keyboard." + binding.action());
            if (raw == null || raw.isBlank()) continue;
            KeyStroke stroke = KeyStroke.getKeyStroke(raw);
            if (stroke == null) continue;
            Binding candidate = new Binding(binding.scope(), binding.action(), stroke, keyLabel(stroke));
            out.put(binding.action(), candidate);
        }
        return validateKeyboardCandidateSet(out).accepted() ? out : new LinkedHashMap<>(DEFAULT_BINDINGS);
    }

    private static RemapResult validateKeyboardCandidate(Map<String, Binding> active,
                                                         Binding previous,
                                                         KeyStroke stroke) {
        if (previous == null) return new RemapResult(false, "Unknown keyboard action.");
        if (stroke == null) return new RemapResult(false, "Keyboard binding must use a real key.");
        for (Binding other : active.values()) {
            if (other.action().equals(previous.action())) continue;
            if (other.scope() == previous.scope() && other.stroke().equals(stroke)) {
                return new RemapResult(false, keyLabel(stroke) + " is already assigned to "
                        + other.action() + " in " + previous.scope().name() + ".");
            }
        }
        return new RemapResult(true, "OK");
    }

    private static RemapResult validateKeyboardCandidateSet(Map<String, Binding> candidate) {
        Map<String, String> owners = new LinkedHashMap<>();
        for (Binding binding : candidate.values()) {
            if (binding == null || binding.stroke() == null) {
                return new RemapResult(false, "Keyboard binding must use a real key.");
            }
            String key = binding.scope().name() + ":" + binding.stroke();
            String previous = owners.putIfAbsent(key, binding.action());
            if (previous != null && !previous.equals(binding.action())) {
                return new RemapResult(false, keyLabel(binding.stroke()) + " is already assigned to "
                        + previous + " and " + binding.action() + " in " + binding.scope().name() + ".");
            }
        }
        return new RemapResult(true, "OK");
    }

    private static RemapResult validateMouseCandidate(Map<String, Integer> candidate) {
        Map<Integer, String> owners = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : candidate.entrySet()) {
            Integer button = entry.getValue();
            if (button == null || button <= 0) {
                return new RemapResult(false, "Mouse binding must use a real button.");
            }
            String previous = owners.putIfAbsent(button, entry.getKey());
            if (previous != null && !previous.equals(entry.getKey())) {
                return new RemapResult(false, "Mouse button " + button + " is already assigned to "
                        + previous + " and " + entry.getKey() + ".");
            }
        }
        return new RemapResult(true, "OK");
    }

    private static Map<String, Integer> defaultMouseBindings() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("primaryDown", 1);
        map.put("lockUnderMouse", 2);
        return map;
    }

    private static Map<String, String> defaultControllerBindings() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("primaryDown", "RT");
        map.put("secondaryDown", "LT");
        map.put("lockUnderMouse", "RS");
        map.put("toggleMap", "VIEW");
        map.put("toggleCrewStations", "MENU");
        map.put("shieldOvercharge", "Y");
        map.put("carrierLaunch", "A");
        map.put("battlefieldWarp", "B");
        return map;
    }

    private static Map<String, Integer> loadIntBindings(String prefix, Map<String, Integer> defaults) {
        Map<String, Integer> out = new LinkedHashMap<>(defaults);
        Properties props = ControlSettingsStore.load();
        for (String action : defaults.keySet()) {
            try { out.put(action, Integer.parseInt(props.getProperty(prefix + action, String.valueOf(defaults.get(action))))); }
            catch (NumberFormatException ignored) {}
        }
        if ("mouse.".equals(prefix) && !validateMouseCandidate(out).accepted()) {
            return new LinkedHashMap<>(defaults);
        }
        return out;
    }

    private static Map<String, String> loadStringBindings(String prefix, Map<String, String> defaults) {
        Map<String, String> out = new LinkedHashMap<>(defaults);
        Properties props = ControlSettingsStore.load();
        for (String action : defaults.keySet()) out.put(action, props.getProperty(prefix + action, defaults.get(action)));
        return out;
    }

    private static synchronized void persist() {
        Properties props = new Properties();
        for (Binding binding : BINDINGS.values()) props.setProperty("keyboard." + binding.action(), binding.stroke().toString());
        for (Map.Entry<String, Integer> entry : MOUSE.entrySet()) props.setProperty("mouse." + entry.getKey(), String.valueOf(entry.getValue()));
        for (Map.Entry<String, String> entry : CONTROLLER.entrySet()) props.setProperty("controller." + entry.getKey(), entry.getValue());
        ControlSettingsStore.save(props);
    }

    private static String keyLabel(KeyStroke stroke) {
        if (stroke == null) return "";
        String raw = stroke.toString().replace("pressed ", "").replace("released ", "");
        return raw.replace("ctrl ", "Ctrl+").replace("alt ", "Alt+").replace("shift ", "Shift+").toUpperCase(Locale.US);
    }

    private static String glyphOrKeyboard(String action) {
        String glyph = glyph(action);
        return (glyph == null || glyph.isBlank()) ? label(action) : glyph;
    }

    private static int normalizedKeyModifiers(int modifiers) {
        return modifiers & (InputEvent.SHIFT_DOWN_MASK
                | InputEvent.CTRL_DOWN_MASK
                | InputEvent.ALT_DOWN_MASK
                | InputEvent.META_DOWN_MASK
                | InputEvent.ALT_GRAPH_DOWN_MASK);
    }

    public static boolean sameKeyCode(String action, KeyEvent event) {
        if (event == null) return false;
        Binding binding = BINDINGS.get(action);
        return binding != null && binding.stroke() != null && binding.stroke().getKeyCode() == event.getKeyCode();
    }

    private static String humanizeAction(String action) {
        if (action == null || action.isBlank()) return "";
        StringBuilder out = new StringBuilder();
        char[] chars = action.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char ch = chars[i];
            if (i > 0 && Character.isUpperCase(ch)) out.append(' ');
            out.append(i == 0 ? Character.toUpperCase(ch) : ch);
        }
        return out.toString();
    }

    private static void add(Map<String, Binding> bindings, Scope scope, String action,
                            int keyCode, int modifiers, boolean onRelease, String label) {
        bindings.put(action, new Binding(scope, action, KeyStroke.getKeyStroke(keyCode, modifiers, onRelease), label));
    }
}
