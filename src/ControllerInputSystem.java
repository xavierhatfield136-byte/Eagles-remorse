import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Adapter-friendly controller action state. A platform adapter can feed button events here.
 */
public final class ControllerInputSystem {
    private static final Set<String> PRESSED = new LinkedHashSet<>();

    private ControllerInputSystem() {}

    public static void setButtonPressed(String button, boolean pressed) {
        if (button == null || button.isBlank()) return;
        String normalized = button.trim().toUpperCase(java.util.Locale.US);
        if (pressed) PRESSED.add(normalized);
        else PRESSED.remove(normalized);
        HotkeyRegistry.noteControllerInput();
    }

    public static boolean isActionPressed(String action) {
        String button = HotkeyRegistry.controllerButton(action);
        return !button.isBlank() && PRESSED.contains(button);
    }

    public static void clear() { PRESSED.clear(); }
}
