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

    public static String label(String action) {
        Binding binding = BINDINGS.get(action);
        return (binding == null) ? "" : binding.label();
    }

    public static List<Binding> bindings() {
        return List.copyOf(BINDINGS.values());
    }

    public static synchronized boolean remapKeyboard(String action, KeyStroke stroke) {
        Binding previous = BINDINGS.get(action);
        if (previous == null || stroke == null) return false;
        BINDINGS.put(action, new Binding(previous.scope(), action, stroke, keyLabel(stroke)));
        persist();
        return true;
    }

    public static synchronized boolean remapMouse(String action, int button) {
        if (!DEFAULT_MOUSE.containsKey(action) || button <= 0) return false;
        MOUSE.put(action, button);
        persist();
        return true;
    }

    public static synchronized boolean remapController(String action, String button) {
        if (!DEFAULT_CONTROLLER.containsKey(action) || button == null || button.isBlank()) return false;
        CONTROLLER.put(action, button.trim().toUpperCase(Locale.US));
        persist();
        return true;
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

    public static int mouseButton(String action) { return MOUSE.getOrDefault(action, -1); }
    public static String controllerButton(String action) { return CONTROLLER.getOrDefault(action, ""); }
    public static String glyph(String action) {
        return "CONTROLLER".equals(lastInputDevice) ? controllerButton(action) : label(action);
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
            rows.add("WASD move  " + glyph("primaryDown") + "/LMB fire");
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
        GameContext.HudDetail mode = (detail == null) ? GameContext.HudDetail.FULL : detail;
        List<String> rows = new ArrayList<>();
        if (mode == GameContext.HudDetail.MINIMAL) {
            rows.add("HELP surface stores combat, navigation, and overlay hotkeys so the live HUD can stay focused.");
            rows.add("META: " + label("escape") + " pause/resume");
            return rows;
        }

        rows.add("COMBAT: " + label("primaryDown") + "/LMB fire | " + label("secondaryDown")
                + "/RMB secondary | " + label("lockUnderMouse") + "/MMB lock | " + label("toggleTacticalView") + " tactical");
        rows.add("NAV: WASD move | arrows pan | " + label("toggleShop") + " fleet management | "
                + label("toggleBaseMenu") + " command upgrades | " + label("toggleMap") + " map | "
                + label("cycleHudDetail") + " HUD");
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
            if (stroke != null) out.put(binding.action(), new Binding(binding.scope(), binding.action(), stroke, keyLabel(stroke)));
        }
        return out;
    }

    private static Map<String, Integer> defaultMouseBindings() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("primaryDown", 1);
        map.put("lockUnderMouse", 2);
        map.put("secondaryDown", 3);
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

    private static void add(Map<String, Binding> bindings, Scope scope, String action,
                            int keyCode, int modifiers, boolean onRelease, String label) {
        bindings.put(action, new Binding(scope, action, KeyStroke.getKeyStroke(keyCode, modifiers, onRelease), label));
    }
}
