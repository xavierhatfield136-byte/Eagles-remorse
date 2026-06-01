import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import app.state.AssetLoadGuard;
import app.state.BoundedCache;

public final class CrewPortraitSystem {
    private static final File ROOT = new File("assets/crew_portraits");
    private static final String[] CANDIDATE_SUFFIXES = {"", "_alt_01", "_alt_02", "_alt_03"};
    private static final int MAX_EXPRESSION_LEVEL = 3;
    private static final String[] ROLE_KEYS = {"captain", "helm", "tactical", "engineering", "science"};
    private static final Pattern VALID_NAME =
            Pattern.compile("^(captain|helm|tactical|engineering|science)(?:_(alt_0[1-3]))?\\.png$");
    private static final int NORMALIZED_SIZE = 512;
    private static final int HUD_PREVIEW_SIZE = 64;
    private static final double HUD_READABILITY_MIN = 0.22;
    private static final Map<String, PortraitAsset> CACHE = new BoundedCache<>(32);
    public static final String STYLE_LOCK_PROMPT =
            "Photorealistic solo portrait of one real human starship bridge officer, one person only, bareheaded with no helmet or headgear, head-and-shoulders chest-up framing, face centered and fully visible, natural skin texture, " +
                    "physically accurate face anatomy, realistic eye detail, cinematic practical lighting, " +
                    "subtle depth of field, sharp focus on face, clean futuristic bridge uniform with smooth technical fabric and minimal seam lines, no armor or tactical gear, simple uncluttered background with soft neutral gradient and faint starship tones, " +
                    "high readability at small UI sizes, no text, no logo, no watermark";

    private CrewPortraitSystem() {}

    public record PortraitAsset(BufferedImage image, boolean fromDisk, String sourceLabel) {}
    public record PortraitIssue(String code, String fileName, String detail) {}
    public record PortraitAudit(
            boolean baseComplete,
            int totalPortraits,
            Map<String, Integer> perRolePortraitCount,
            Map<String, Double> hudReadabilityScore,
            List<PortraitIssue> issues) {}

    public static PortraitAsset getPortrait(GameContext.CrewStation station) {
        return getPortrait(station, 0);
    }

    public static PortraitAsset getPortrait(GameContext.CrewStation station, int expressionLevel) {
        String role = roleKey(station);
        int expression = MathUtil.clamp(expressionLevel, 0, MAX_EXPRESSION_LEVEL);
        String cacheKey = role + "#" + expression;
        synchronized (CACHE) {
            PortraitAsset cached = CACHE.get(cacheKey);
            if (cached != null) return cached;
            PortraitAsset loaded = loadPortrait(role, expression);
            CACHE.put(cacheKey, loaded);
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

    public static String styleLockPrompt() {
        return STYLE_LOCK_PROMPT;
    }

    public static List<String> fallbackChainFor(GameContext.CrewStation station) {
        return fallbackChainForRole(roleKey(station), 0);
    }

    public static PortraitAudit auditLibrary() {
        List<PortraitIssue> issues = new ArrayList<>();
        Map<String, Integer> perRoleCount = new LinkedHashMap<>();
        Map<String, Double> readability = new LinkedHashMap<>();
        for (String role : ROLE_KEYS) perRoleCount.put(role, 0);

        File[] files = ROOT.listFiles(f -> f != null && f.isFile() && f.getName().toLowerCase(Locale.US).endsWith(".png"));
        if (files == null) files = new File[0];
        Arrays.sort(files, (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.getName(), b.getName()));

        for (File file : files) {
            String name = file.getName().toLowerCase(Locale.US);
            Matcher matcher = VALID_NAME.matcher(name);
            if (!matcher.matches()) {
                issues.add(new PortraitIssue("naming", name, "Invalid portrait filename pattern."));
                continue;
            }
            String role = matcher.group(1);
            perRoleCount.put(role, perRoleCount.getOrDefault(role, 0) + 1);

            BufferedImage img;
            try {
                img = AssetLoadGuard.read(file, "portrait");
            } catch (Throwable e) {
                issues.add(new PortraitIssue("read", name, "Failed to decode image: " + e.getClass().getSimpleName()));
                continue;
            }
            if (img == null) {
                issues.add(new PortraitIssue("read", name, "Image decoded as null."));
                continue;
            }

            if (img.getWidth() != img.getHeight()) {
                issues.add(new PortraitIssue("resolution", name, "Image must be square."));
            }
            if (img.getWidth() != 512 && img.getWidth() != 1024) {
                issues.add(new PortraitIssue("resolution", name, "Recommended size is 512x512 or 1024x1024."));
            }

            double opaqueCoverage = opaqueCoverage(img);
            if (opaqueCoverage < 0.92) {
                issues.add(new PortraitIssue("alpha", name, "Too much transparency for HUD portrait use."));
            }

            BufferedImage normalized = normalizePortrait(img);
            double readabilityScore = hudReadabilityScore(normalized);
            readability.put(name, readabilityScore);
            if (readabilityScore < HUD_READABILITY_MIN) {
                issues.add(new PortraitIssue("readability", name, "Low HUD readability score: " + fmt(readabilityScore)));
            }
        }

        boolean baseComplete = true;
        for (String role : ROLE_KEYS) {
            File base = new File(ROOT, role + ".png");
            if (!base.isFile()) {
                baseComplete = false;
                issues.add(new PortraitIssue("base_missing", role + ".png", "Required base portrait missing."));
            }

            int alternates = 0;
            for (int i = 1; i <= 3; i++) {
                File alt = new File(ROOT, role + "_alt_0" + i + ".png");
                if (alt.isFile()) alternates++;
            }
            if (alternates < 3) {
                issues.add(new PortraitIssue("alternates", role, "Expected 3 alternates, found " + alternates + "."));
            }
        }

        return new PortraitAudit(
                baseComplete,
                files.length,
                Collections.unmodifiableMap(new LinkedHashMap<>(perRoleCount)),
                Collections.unmodifiableMap(new LinkedHashMap<>(readability)),
                Collections.unmodifiableList(new ArrayList<>(issues))
        );
    }

    public static void writeHudPreviewSnapshots(Path outputDir) throws IOException {
        if (outputDir == null) return;
        Files.createDirectories(outputDir);
        for (String role : ROLE_KEYS) {
            PortraitAsset portrait = getPortrait(stationForRole(role));
            if (portrait == null || portrait.image() == null) continue;
            BufferedImage small = resize(portrait.image(), HUD_PREVIEW_SIZE, HUD_PREVIEW_SIZE);
            Path file = outputDir.resolve(role + "_hud_preview.png");
            ImageIO.write(small, "png", file.toFile());
        }
    }

    private static PortraitAsset loadPortrait(String role, int expressionLevel) {
        String normalizedRole = normalizeRole(role);
        int expression = MathUtil.clamp(expressionLevel, 0, MAX_EXPRESSION_LEVEL);
        return new PortraitAsset(buildProceduralPortrait(normalizedRole, expression), false, "procedural");
    }

    private static List<String> fallbackChainForRole(String role, int expressionLevel) {
        String normalizedRole = normalizeRole(role);
        List<String> chain = new ArrayList<>(CANDIDATE_SUFFIXES.length * 2 + 2);
        String preferred = expressionSuffix(expressionLevel);
        if (!preferred.isBlank()) {
            chain.add(normalizedRole + preferred + ".png");
        }
        for (String suffix : CANDIDATE_SUFFIXES) {
            String name = normalizedRole + suffix + ".png";
            if (!chain.contains(name)) chain.add(name);
        }
        if (!"captain".equals(normalizedRole)) {
            if (!preferred.isBlank()) {
                chain.add("captain" + preferred + ".png");
            }
            for (String suffix : CANDIDATE_SUFFIXES) {
                String name = "captain" + suffix + ".png";
                if (!chain.contains(name)) chain.add(name);
            }
        }
        return chain;
    }

    private static String expressionSuffix(int expressionLevel) {
        int level = MathUtil.clamp(expressionLevel, 0, MAX_EXPRESSION_LEVEL);
        if (level <= 0) return "";
        return "_alt_0" + level;
    }

    private static String normalizeRole(String role) {
        if (role == null || role.isBlank()) return "captain";
        String lower = role.toLowerCase(Locale.US);
        for (String candidate : ROLE_KEYS) {
            if (candidate.equals(lower)) return lower;
        }
        return "captain";
    }

    private static GameContext.CrewStation stationForRole(String role) {
        return switch (normalizeRole(role)) {
            case "helm" -> GameContext.CrewStation.HELM;
            case "tactical" -> GameContext.CrewStation.TACTICAL;
            case "engineering" -> GameContext.CrewStation.ENGINEERING;
            case "science" -> GameContext.CrewStation.SCIENCE;
            default -> GameContext.CrewStation.CAPTAIN;
        };
    }

    private static BufferedImage normalizePortrait(BufferedImage src) {
        if (src == null) return null;
        int target = NORMALIZED_SIZE;
        BufferedImage out = new BufferedImage(target, target, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        double sx = target / (double) Math.max(1, src.getWidth());
        double sy = target / (double) Math.max(1, src.getHeight());
        double scale = Math.max(sx, sy);
        int drawW = Math.max(1, (int) Math.round(src.getWidth() * scale));
        int drawH = Math.max(1, (int) Math.round(src.getHeight() * scale));
        int dx = (target - drawW) / 2;
        int dy = (target - drawH) / 2;

        g.setColor(new Color(8, 12, 18, 255));
        g.fillRect(0, 0, target, target);
        g.drawImage(src, dx, dy, drawW, drawH, null);
        g.dispose();
        return out;
    }

    private static BufferedImage resize(BufferedImage src, int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, width, height, null);
        g.dispose();
        return out;
    }

    private static double opaqueCoverage(BufferedImage image) {
        if (image == null) return 0.0;
        int w = image.getWidth();
        int h = image.getHeight();
        if (w <= 0 || h <= 0) return 0.0;
        int opaque = 0;
        int total = w * h;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int a = (image.getRGB(x, y) >>> 24) & 0xFF;
                if (a >= 250) opaque++;
            }
        }
        return opaque / (double) total;
    }

    private static double hudReadabilityScore(BufferedImage portrait) {
        if (portrait == null) return 0.0;
        BufferedImage sample = resize(portrait, HUD_PREVIEW_SIZE, HUD_PREVIEW_SIZE);
        int w = sample.getWidth();
        int h = sample.getHeight();
        if (w <= 0 || h <= 0) return 0.0;

        double sum = 0.0;
        double sumSq = 0.0;
        int count = 0;

        int x0 = (int) Math.round(w * 0.22);
        int x1 = (int) Math.round(w * 0.78);
        int y0 = (int) Math.round(h * 0.18);
        int y1 = (int) Math.round(h * 0.82);

        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int argb = sample.getRGB(x, y);
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                double luma = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0;
                sum += luma;
                sumSq += luma * luma;
                count++;
            }
        }
        if (count <= 0) return 0.0;
        double mean = sum / count;
        double variance = Math.max(0.0, sumSq / count - mean * mean);
        double contrast = Math.sqrt(variance);

        // Score in [0,1], tuned for low-res HUD icon legibility.
        return MathUtil.clamp((contrast - 0.07) / 0.26, 0.0, 1.0);
    }

    private static String fmt(double v) {
        return String.format(Locale.US, "%.3f", v);
    }

    private static BufferedImage buildProceduralPortrait(String role, int expressionLevel) {
        int w = 512;
        int h = 512;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        Color accent = accentForRole(role);
        Color dark = new Color(Math.max(0, accent.getRed() - 82), Math.max(0, accent.getGreen() - 82), Math.max(0, accent.getBlue() - 82), 255);
        g.setPaint(new GradientPaint(0, 0, dark, w, h, new Color(10, 14, 20, 255)));
        g.fillRect(0, 0, w, h);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.16f));
        g.setColor(new Color(185, 220, 255, 120));
        g.fillOval(-100, -60, 320, 220);
        g.fillOval(250, 284, 310, 220);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        g.setColor(new Color(255, 255, 255, 22));
        for (int i = 0; i < 11; i++) {
            int y = 34 + i * 42;
            g.drawLine(26, y, w - 26, y);
        }

        int cardX = 86;
        int cardY = 70;
        int cardW = 340;
        int cardH = 340;
        g.setColor(new Color(0, 0, 0, 112));
        g.fillRoundRect(cardX, cardY, cardW, cardH, 28, 28);
        g.setColor(new Color(255, 255, 255, 72));
        g.setStroke(new BasicStroke(2.0f));
        g.drawRoundRect(cardX, cardY, cardW, cardH, 28, 28);

        int cx = w / 2;
        int cy = 216;
        int haloR = 118;
        g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 42));
        g.fillOval(cx - haloR, cy - haloR, haloR * 2, haloR * 2);
        g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 118));
        g.setStroke(new BasicStroke(4.0f));
        g.drawOval(cx - 94, cy - 94, 188, 188);

        Polygon head = new Polygon(
                new int[]{cx - 72, cx - 38, cx + 38, cx + 72, cx + 52, cx - 52},
                new int[]{cy - 16, cy - 72, cy - 72, cy - 16, cy + 62, cy + 62},
                6
        );
        g.setColor(new Color(18, 22, 30, 235));
        g.fillPolygon(head);
        g.setColor(new Color(255, 255, 255, 58));
        g.drawPolygon(head);

        Polygon visor = new Polygon(
                new int[]{cx - 54, cx - 20, cx + 20, cx + 54, cx + 36, cx - 36},
                new int[]{cy - 6, cy - 34, cy - 34, cy - 6, cy + 18, cy + 18},
                6
        );
        int expressionBias = MathUtil.clamp(expressionLevel, 0, MAX_EXPRESSION_LEVEL);
        Color visorColor = switch (expressionBias) {
            case 1 -> new Color(255, 239, 160, 220);
            case 2 -> new Color(255, 170, 132, 228);
            case 3 -> new Color(255, 116, 116, 232);
            default -> new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 220);
        };
        g.setColor(visorColor);
        g.fillPolygon(visor);

        g.setStroke(new BasicStroke(3.0f));
        g.setColor(new Color(255, 255, 255, 85));
        g.drawLine(cx - 76, cy + 86, cx + 76, cy + 86);
        g.drawLine(cx - 58, cy + 104, cx + 58, cy + 104);

        drawRoleGlyph(g, role, accent, cx, cy + 6, expressionBias);
        drawFrameBrackets(g, accent, cardX, cardY, cardW, cardH);

        g.setColor(new Color(255, 255, 255, 210));
        g.setFont(new Font("Consolas", Font.BOLD, 28));
        String title = role.toUpperCase(Locale.US);
        int tw = g.getFontMetrics().stringWidth(title);
        g.drawString(title, (w - tw) / 2, h - 38);

        g.dispose();
        return img;
    }

    private static void drawRoleGlyph(Graphics2D g, String role, Color accent, int cx, int cy, int expressionBias) {
        g.setStroke(new BasicStroke(4.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(255, 255, 255, 190));
        switch (normalizeRole(role)) {
            case "captain" -> {
                g.drawOval(cx - 22, cy - 22, 44, 44);
                g.drawLine(cx, cy - 42, cx, cy + 42);
                g.drawLine(cx - 42, cy, cx + 42, cy);
            }
            case "helm" -> {
                g.drawArc(cx - 42, cy - 42, 84, 84, 200, 140);
                g.drawArc(cx - 42, cy - 42, 84, 84, 20, 140);
                g.drawLine(cx, cy - 52, cx, cy + 28);
            }
            case "tactical" -> {
                g.drawLine(cx - 44, cy, cx + 44, cy);
                g.drawLine(cx, cy - 44, cx, cy + 44);
                g.drawOval(cx - 16, cy - 16, 32, 32);
            }
            case "engineering" -> {
                g.drawRoundRect(cx - 34, cy - 24, 68, 48, 12, 12);
                g.drawLine(cx - 12, cy - 44, cx + 12, cy + 44);
                g.drawLine(cx + 12, cy - 44, cx - 12, cy + 44);
            }
            case "science" -> {
                g.drawOval(cx - 18, cy - 38, 36, 76);
                g.drawLine(cx - 30, cy - 10, cx + 30, cy - 10);
                g.drawLine(cx - 26, cy + 16, cx + 26, cy + 16);
            }
            default -> g.drawOval(cx - 18, cy - 18, 36, 36);
        }
        if (expressionBias > 0) {
            g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 150));
            g.setStroke(new BasicStroke(2.0f));
            int spread = 22 + expressionBias * 8;
            g.drawArc(cx - spread, cy - spread, spread * 2, spread * 2, 20, 50);
            g.drawArc(cx - spread, cy - spread, spread * 2, spread * 2, 110, 50);
        }
    }

    private static void drawFrameBrackets(Graphics2D g, Color accent, int x, int y, int w, int h) {
        g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 180));
        g.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int arm = 28;
        g.drawLine(x + 16, y + 16, x + 16 + arm, y + 16);
        g.drawLine(x + 16, y + 16, x + 16, y + 16 + arm);
        g.drawLine(x + w - 16, y + 16, x + w - 16 - arm, y + 16);
        g.drawLine(x + w - 16, y + 16, x + w - 16, y + 16 + arm);
        g.drawLine(x + 16, y + h - 16, x + 16 + arm, y + h - 16);
        g.drawLine(x + 16, y + h - 16, x + 16, y + h - 16 - arm);
        g.drawLine(x + w - 16, y + h - 16, x + w - 16 - arm, y + h - 16);
        g.drawLine(x + w - 16, y + h - 16, x + w - 16, y + h - 16 - arm);
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


