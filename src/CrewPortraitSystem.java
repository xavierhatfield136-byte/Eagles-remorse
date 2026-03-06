import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;

public final class CrewPortraitSystem {
    private static final File ROOT = new File("assets/crew_portraits");
    private static final String[] CANDIDATE_SUFFIXES = {"", "_alt_01", "_alt_02"};
    private static final Map<String, PortraitAsset> CACHE = new HashMap<>();

    private CrewPortraitSystem() {}

    public record PortraitAsset(BufferedImage image, boolean fromDisk, String sourceLabel) {}

    public static PortraitAsset getPortrait(GameContext.CrewStation station) {
        String role = roleKey(station);
        synchronized (CACHE) {
            PortraitAsset cached = CACHE.get(role);
            if (cached != null) return cached;
            PortraitAsset loaded = loadPortrait(role);
            CACHE.put(role, loaded);
            return loaded;
        }
    }

    public static String roleKey(GameContext.CrewStation station) {
        if (station == null) return "captain";
        return switch (station) {
            case CAPTAIN -> "captain";
            case HELM -> "helm";
            case TACTICAL -> "tactical";
            case ENGINEERING -> "engineering";
            case SCIENCE -> "science";
        };
    }

    private static PortraitAsset loadPortrait(String role) {
        if (role == null || role.isBlank()) role = "captain";
        for (String suffix : CANDIDATE_SUFFIXES) {
            String name = role + suffix + ".png";
            File file = new File(ROOT, name);
            if (!file.isFile()) continue;
            try {
                BufferedImage img = ImageIO.read(file);
                if (img != null) {
                    return new PortraitAsset(img, true, name);
                }
            } catch (Throwable ignored) {
                // Fall through to placeholder.
            }
        }
        return new PortraitAsset(buildPlaceholder(role), false, role + " (placeholder)");
    }

    private static BufferedImage buildPlaceholder(String role) {
        int w = 512;
        int h = 512;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color accent = accentForRole(role);
        Color dark = new Color(Math.max(0, accent.getRed() - 70), Math.max(0, accent.getGreen() - 70), Math.max(0, accent.getBlue() - 70), 255);
        g.setPaint(new GradientPaint(0, 0, dark, w, h, new Color(12, 16, 22, 255)));
        g.fillRect(0, 0, w, h);

        // Bridge-like haze layers.
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.22f));
        g.setColor(new Color(190, 230, 255, 140));
        g.fillOval(-120, -80, 360, 240);
        g.fillOval(260, 300, 320, 220);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        g.setColor(new Color(255, 255, 255, 30));
        for (int i = 0; i < 10; i++) {
            int y = 44 + i * 40;
            g.drawLine(28, y, w - 28, y);
        }

        // Simple officer silhouette.
        g.setColor(new Color(22, 24, 30, 230));
        g.fillRoundRect(124, 220, 264, 250, 70, 70);
        g.setColor(new Color(40, 44, 56, 230));
        g.fillOval(176, 104, 160, 170);
        g.setColor(new Color(66, 74, 92, 225));
        g.fillOval(194, 128, 124, 124);

        g.setColor(accent);
        g.fillRoundRect(216, 274, 80, 14, 8, 8);
        g.fillRoundRect(216, 302, 80, 10, 6, 6);

        g.setColor(new Color(255, 255, 255, 210));
        g.setFont(new Font("Consolas", Font.BOLD, 28));
        String title = role.toUpperCase(Locale.US);
        int tw = g.getFontMetrics().stringWidth(title);
        g.drawString(title, (w - tw) / 2, h - 38);

        g.dispose();
        return img;
    }

    private static Color accentForRole(String role) {
        if (role == null) return new Color(150, 190, 255);
        return switch (role.toLowerCase(Locale.US)) {
            case "captain" -> new Color(255, 214, 120);
            case "helm" -> new Color(130, 214, 255);
            case "tactical" -> new Color(255, 162, 130);
            case "engineering" -> new Color(150, 245, 170);
            case "science" -> new Color(196, 166, 255);
            default -> new Color(150, 190, 255);
        };
    }
}


