import java.util.ArrayList;
import java.util.List;

public final class UiAccessibilityQualitySystem {
    private UiAccessibilityQualitySystem() {}

    public static List<String> overlayAndActionLines(GameContext ctx) {
        return List.of(
                "Overlay Audit  |  1280x720 and 1920x1080 layout checks use RendererHudLayoutTest and screenshotRegression",
                "Action Strip  |  compact what-can-I-do-now commands for map, fleet, tactical, paused, game-over, and hub states",
                "Disabled Reason  |  every unavailable action explains missing target, stores, range, state, or irreversible risk",
                "Command Filters  |  roster dumps filter by fleet, resource, strike, intel, damage, and route state",
                "Dense Calm Boards  |  fleet, resource, and strike boards group essentials before details"
        );
    }

    public static List<String> inputAccessibilityLines(GameContext ctx) {
        return List.of(
                "Controls Search  |  search field locates bindings, controller actions, accessibility toggles, and overlays",
                "Keyboard Smoke  |  major flows have keyboard-only launch, map, fleet, combat, pause, and exit checks",
                "Controller Polish  |  nested overlays expose focus order, back behavior, and primary/secondary actions",
                "Tooltip Delay  |  delay presets support instant, standard, and patient reading modes"
        );
    }

    public static List<String> readabilityAndWarningLines(GameContext ctx) {
        return List.of(
                "High Contrast Projectiles  |  optional palette separates friendly, hostile, missile, beam, and point-defense fire",
                "Reduced Noise Audio  |  preset lowers chatter, repeated alerts, and noncritical ambience",
                "Caption Priority  |  critical warnings override chatter; missed lines go to recent messages",
                "Warning Hierarchy  |  advisory, risk, critical, and irreversible messages use distinct language",
                "High-DPI Map Icons  |  important contacts use larger scalable icon tiers",
                "Recent Messages  |  missed warnings and voice captions remain reviewable"
        );
    }

    public static List<String> polishAndConsistencyLines(GameContext ctx) {
        return List.of(
                "Irreversible Confirm  |  confirmations reserved for spend-all, abandon, scuttle, atomic, and surrender actions",
                "Resource Language  |  fuel, ammo, repairs, salvage, intel, and reputation use consistent labels and colors",
                "Release Notes  |  in-game campaign notes explain what changed this run",
                "Visual Regression  |  screenshot-driven checks cover remaining crowded screens"
        );
    }

    public static List<String> allUiAccessibilityLines(GameContext ctx) {
        ArrayList<String> out = new ArrayList<>();
        out.addAll(overlayAndActionLines(ctx));
        out.addAll(inputAccessibilityLines(ctx));
        out.addAll(readabilityAndWarningLines(ctx));
        out.addAll(polishAndConsistencyLines(ctx));
        return out;
    }
}
