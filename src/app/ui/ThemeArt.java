package app.ui;

import javax.imageio.ImageIO;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight themed UI art registry. Missing files are expected while the art
 * pass is in progress, so every lookup simply returns null and lets callers
 * fall back to procedural UI drawing.
 */
public final class ThemeArt {
    public record FrameMetrics(int left, int top, int right, int bottom, int titleBaseline, int separatorY) {
        public Insets contentInsets() {
            return new Insets(top, left, bottom, right);
        }
    }

    public static final String MENU_MAIN_SHELL = "menu_main_shell";
    public static final String MENU_SECTION_PANEL = "menu_section_panel";
    public static final String MENU_INSET_PANEL = "menu_inset_panel";
    public static final String HUD_STANDARD_PANEL = "hud_standard_panel";
    public static final String HUD_ALERT_PANEL = "hud_alert_panel";
    public static final String HUD_STATUS_STRIP = "hud_status_strip";
    public static final String HUD_SPECIAL_FRAME = "hud_special_frame";
    public static final String HUD_RADAR_RING = "hud_radar_ring";

    private static final File UI_THEME_DIR = new File("assets/ui_theme");
    private static final BufferedImage MISSING = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    private static final Map<String, BufferedImage> CACHE = new ConcurrentHashMap<>();

    private ThemeArt() {
    }

    public static BufferedImage get(String slot) {
        if (slot == null || slot.isBlank()) return null;
        BufferedImage cached = CACHE.get(slot);
        if (cached != null) {
            return cached == MISSING ? null : cached;
        }

        BufferedImage image = load(slot);
        CACHE.put(slot, image == null ? MISSING : image);
        return image;
    }

    public static FrameMetrics metrics(String slot, int w, int h) {
        int width = Math.max(1, w);
        int height = Math.max(1, h);
        return switch (slot) {
            case MENU_MAIN_SHELL -> new FrameMetrics(
                    clamp((int) Math.round(width * 0.040), 42, 72),
                    clamp((int) Math.round(height * 0.090), 54, 92),
                    clamp((int) Math.round(width * 0.040), 42, 72),
                    clamp((int) Math.round(height * 0.055), 28, 54),
                    0, 0);
            case MENU_SECTION_PANEL -> new FrameMetrics(
                    clamp((int) Math.round(width * 0.032), 26, 46),
                    clamp((int) Math.round(height * 0.085), 40, 68),
                    clamp((int) Math.round(width * 0.032), 26, 46),
                    clamp((int) Math.round(height * 0.050), 18, 40),
                    0, 0);
            case MENU_INSET_PANEL -> new FrameMetrics(
                    clamp((int) Math.round(width * 0.024), 16, 28),
                    clamp((int) Math.round(height * 0.070), 22, 40),
                    clamp((int) Math.round(width * 0.024), 16, 28),
                    clamp((int) Math.round(height * 0.042), 12, 24),
                    0, 0);
            case HUD_ALERT_PANEL -> {
                int left = clamp((int) Math.round(width * 0.018), 18, 34);
                int top = clamp((int) Math.round(height * 0.155), 20, 54);
                yield new FrameMetrics(left, top, left, clamp((int) Math.round(height * 0.090), 12, 28),
                        clamp((int) Math.round(height * 0.110), 18, 34),
                        clamp((int) Math.round(height * 0.155), 24, 42));
            }
            case HUD_STATUS_STRIP -> {
                int left = clamp((int) Math.round(width * 0.018), 16, 28);
                int top = clamp((int) Math.round(height * 0.220), 14, 26);
                yield new FrameMetrics(left, top, left, clamp((int) Math.round(height * 0.180), 10, 22),
                        clamp((int) Math.round(height * 0.180), 16, 24),
                        clamp((int) Math.round(height * 0.280), 20, 32));
            }
            case HUD_SPECIAL_FRAME -> {
                int left = clamp((int) Math.round(width * 0.048), 24, 42);
                int top = clamp((int) Math.round(height * 0.120), 42, 74);
                yield new FrameMetrics(left, top, left, clamp((int) Math.round(height * 0.070), 18, 32),
                        clamp((int) Math.round(height * 0.072), 24, 38),
                        clamp((int) Math.round(height * 0.102), 34, 50));
            }
            case HUD_RADAR_RING -> {
                int inset = clamp((int) Math.round(Math.min(width, height) * 0.165), 44, 92);
                yield new FrameMetrics(inset, inset, inset, inset, 0, 0);
            }
            case HUD_STANDARD_PANEL -> {
                int left = clamp((int) Math.round(width * 0.022), 14, 28);
                int top = clamp((int) Math.round(height * 0.095), 28, 46);
                yield new FrameMetrics(left, top, left, clamp((int) Math.round(height * 0.055), 12, 22),
                        clamp((int) Math.round(height * 0.060), 18, 28),
                        clamp((int) Math.round(height * 0.090), 22, 36));
            }
            default -> new FrameMetrics(16, 16, 16, 16, 18, 26);
        };
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static BufferedImage load(String slot) {
        if (!UI_THEME_DIR.isDirectory()) return null;
        for (String candidate : candidates(slot)) {
            File file = new File(UI_THEME_DIR, candidate);
            if (!file.isFile()) continue;
            try {
                return ImageIO.read(file);
            } catch (IOException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String[] candidates(String slot) {
        return switch (slot) {
            case MENU_MAIN_SHELL -> new String[]{
                    "menu_main_shell.png",
                    "main_menu_shell.png",
                    "hud_core_shell.png"
            };
            case MENU_SECTION_PANEL -> new String[]{
                    "menu_section_panel.png",
                    "section_panel.png",
                    "hud_panel_feature_square.png"
            };
            case MENU_INSET_PANEL -> new String[]{
                    "menu_inset_panel.png",
                    "inset_panel.png",
                    "hud_standard_panel.png"
            };
            case HUD_STANDARD_PANEL -> new String[]{
                    "hud_standard_panel.png",
                    "hud_panel_feature_square.png",
                    "hud_panel_main.png"
            };
            case HUD_ALERT_PANEL -> new String[]{
                    "hud_alert_panel.png",
                    "hud_strip_alert.png",
                    "alert_panel.png"
            };
            case HUD_STATUS_STRIP -> new String[]{
                    "hud_status_strip.png",
                    "hud_strip_status.png",
                    "status_strip.png"
            };
            case HUD_SPECIAL_FRAME -> new String[]{
                    "hud_special_frame.png",
                    "hud_special_square.png",
                    "hud_system_frame.png"
            };
            case HUD_RADAR_RING -> new String[]{
                    "hud_radar_ring.png",
                    "radar_ring.png",
                    "hud_circular_housing.png"
            };
            default -> new String[]{slot + ".png"};
        };
    }
}
