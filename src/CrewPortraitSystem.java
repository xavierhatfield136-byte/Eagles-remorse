import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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

public final class CrewPortraitSystem {
    private static final File ROOT = new File("assets/crew_portraits");
    private static final String[] CANDIDATE_SUFFIXES = {"", "_alt_01", "_alt_02", "_alt_03"};
    private static final String[] ROLE_KEYS = {"captain", "helm", "tactical", "engineering", "science"};
    private static final Pattern VALID_NAME =
            Pattern.compile("^(captain|helm|tactical|engineering|science)(?:_(alt_0[1-3]))?\\.png$");
    private static final int NORMALIZED_SIZE = 512;
    private static final int HUD_PREVIEW_SIZE = 64;
    private static final double HUD_READABILITY_MIN = 0.22;
    private static final Map<String, PortraitAsset> CACHE = new HashMap<>();
    public static final String STYLE_LOCK_PROMPT =
            "Stylized sci-fi bridge officer portrait, chest-up, clean cinematic lighting, realistic proportions, " +
                    "sharp facial detail, subtle uniform paneling, cool starship bridge background, " +
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

    public static String styleLockPrompt() {
        return STYLE_LOCK_PROMPT;
    }

    public static List<String> fallbackChainFor(GameContext.CrewStation station) {
        return fallbackChainForRole(roleKey(station));
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
                img = ImageIO.read(file);
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

    private static PortraitAsset loadPortrait(String role) {
        if (role == null || role.isBlank()) role = "captain";
        for (String name : fallbackChainForRole(role)) {
            File file = new File(ROOT, name);
            if (!file.isFile()) continue;
            try {
                BufferedImage img = ImageIO.read(file);
                if (img != null) {
                    return new PortraitAsset(normalizePortrait(img), true, name);
                }
            } catch (Throwable ignored) {
                // Fall through to placeholder.
            }
        }
        return new PortraitAsset(buildPlaceholder(role), false, role + " (placeholder)");
    }

    private static List<String> fallbackChainForRole(String role) {
        String normalizedRole = normalizeRole(role);
        List<String> chain = new ArrayList<>(CANDIDATE_SUFFIXES.length * 2);
        for (String suffix : CANDIDATE_SUFFIXES) {
            chain.add(normalizedRole + suffix + ".png");
        }
        if (!"captain".equals(normalizedRole)) {
            for (String suffix : CANDIDATE_SUFFIXES) {
                chain.add("captain" + suffix + ".png");
            }
        }
        return chain;
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


