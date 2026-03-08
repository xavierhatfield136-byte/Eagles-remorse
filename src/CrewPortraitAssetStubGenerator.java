import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;

/**
 * Generates a complete portrait asset set:
 * - 5 base portraits
 * - 3 alternates per role
 */
public final class CrewPortraitAssetStubGenerator {
    private static final String[] ROLES = {"captain", "helm", "tactical", "engineering", "science"};

    private CrewPortraitAssetStubGenerator() {}

    public static void main(String[] args) throws Exception {
        boolean overwrite = false;
        for (String arg : args) {
            if (arg == null) continue;
            if ("--overwrite".equalsIgnoreCase(arg.trim())) overwrite = true;
        }

        File root = new File("assets/crew_portraits");
        if (!root.exists() && !root.mkdirs()) {
            throw new IllegalStateException("failed to create portrait directory: " + root.getAbsolutePath());
        }

        int created = 0;
        int skipped = 0;
        for (String role : ROLES) {
            for (int v = 0; v < 4; v++) {
                String name = (v == 0)
                        ? role + ".png"
                        : role + "_alt_0" + v + ".png";
                File out = new File(root, name);
                if (out.isFile() && !overwrite) {
                    skipped++;
                    continue;
                }
                BufferedImage img = renderPortrait(role, v);
                ImageIO.write(img, "png", out);
                created++;
            }
        }

        System.out.println("[portrait-gen] created=" + created + " skipped=" + skipped);
    }

    private static BufferedImage renderPortrait(String role, int variant) {
        int w = 512;
        int h = 512;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        Color accent = accent(role, variant);
        Color deep = mix(accent, new Color(8, 12, 18), 0.70);
        g.setPaint(new GradientPaint(0, 0, deep, w, h, mix(accent, Color.BLACK, 0.82)));
        g.fillRect(0, 0, w, h);

        g.setColor(new Color(240, 248, 255, 28));
        for (int i = 0; i < 10; i++) {
            int y = 36 + i * 44;
            g.drawLine(24, y, w - 24, y);
        }

        int panelW = 292;
        int panelH = 286;
        int panelX = (w - panelW) / 2;
        int panelY = 170;
        g.setColor(new Color(12, 16, 24, 188));
        g.fill(new RoundRectangle2D.Double(panelX, panelY, panelW, panelH, 28, 28));
        g.setColor(new Color(210, 232, 255, 58));
        g.draw(new RoundRectangle2D.Double(panelX, panelY, panelW, panelH, 28, 28));

        // Head/torso silhouette with simple variant shifts for readability.
        int headR = 68 + (variant % 2) * 4;
        int headX = w / 2 - headR;
        int headY = 88 + variant * 3;
        g.setColor(new Color(40, 48, 60, 232));
        g.fillOval(headX, headY, headR * 2, headR * 2);

        // High-contrast facial plate to improve HUD readability at 64x64.
        g.setColor(new Color(222, 230, 240, 238));
        g.fillOval(headX + 18, headY + 18, headR * 2 - 36, headR * 2 - 34);
        g.setColor(new Color(24, 28, 36, 230));
        g.fillRoundRect(headX + 24, headY + 42, headR * 2 - 48, 20, 10, 10);
        g.setColor(new Color(250, 255, 255, 238));
        g.fillRoundRect(headX + 34, headY + 46, 26, 8, 4, 4);
        g.fillRoundRect(headX + 78, headY + 46, 26, 8, 4, 4);

        g.setColor(new Color(56, 68, 86, 238));
        g.fillRoundRect(164 - variant * 2, 220 + variant * 2, 184 + variant * 4, 214, 72, 72);
        g.setColor(new Color(18, 22, 30, 220));
        g.fillRoundRect(204, 252, 104, 156, 28, 28);
        g.setColor(new Color(232, 240, 250, 210));
        g.fillRoundRect(176, 274, 44, 132, 20, 20);
        g.fillRoundRect(292, 274, 44, 132, 20, 20);

        g.setColor(new Color(235, 245, 255, 210));
        g.fillOval(headX + 20, headY + 26, 22, 10);
        g.fillOval(headX + 94, headY + 26, 22, 10);

        g.setColor(accent);
        g.fillRoundRect(216, 292, 80, 12, 8, 8);
        g.fillRoundRect(214, 318, 84, 10, 6, 6);

        g.setFont(new Font("Consolas", Font.BOLD, 28));
        g.setColor(new Color(245, 250, 255, 230));
        String roleText = role.toUpperCase(Locale.US);
        int tw = g.getFontMetrics().stringWidth(roleText);
        g.drawString(roleText, (w - tw) / 2, 468);

        g.setFont(new Font("Consolas", Font.PLAIN, 16));
        g.setColor(new Color(180, 220, 255, 170));
        String alt = (variant == 0) ? "BASE" : ("ALT " + variant);
        int aw = g.getFontMetrics().stringWidth(alt);
        g.drawString(alt, (w - aw) / 2, 492);

        g.dispose();
        return img;
    }

    private static Color accent(String role, int variant) {
        Color base = switch (role) {
            case "captain" -> new Color(255, 210, 120);
            case "helm" -> new Color(130, 214, 255);
            case "tactical" -> new Color(255, 160, 128);
            case "engineering" -> new Color(150, 244, 170);
            case "science" -> new Color(196, 166, 255);
            default -> new Color(170, 200, 255);
        };
        double t = Math.min(0.28, variant * 0.09);
        return mix(base, Color.WHITE, t);
    }

    private static Color mix(Color a, Color b, double t) {
        double k = Math.max(0.0, Math.min(1.0, t));
        int r = (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * k);
        int g = (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * k);
        int bl = (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * k);
        return new Color(MathUtil.clamp(r, 0, 255), MathUtil.clamp(g, 0, 255), MathUtil.clamp(bl, 0, 255));
    }
}
