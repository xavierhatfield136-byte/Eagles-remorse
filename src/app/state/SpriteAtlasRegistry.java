package app.state;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Compact atlas builder for decoded sprite families.
 */
public final class SpriteAtlasRegistry {
    public record Region(int x, int y, int width, int height) {}
    public record Atlas(BufferedImage image, Map<String, Region> regions) {}

    private static final Map<String, Map<String, BufferedImage>> IMAGES = new BoundedCache<>(32);
    private static final Map<String, Atlas> ATLASES = new BoundedCache<>(32);

    private SpriteAtlasRegistry() {}

    public static synchronized void register(String category, String key, BufferedImage image) {
        if (image == null || key == null || key.isBlank()) return;
        String safeCategory = (category == null || category.isBlank()) ? "image" : category.trim();
        IMAGES.computeIfAbsent(safeCategory, ignored -> new BoundedCache<>(256)).put(key, image);
        ATLASES.remove(safeCategory);
    }

    public static synchronized Atlas atlas(String category) {
        String safeCategory = (category == null || category.isBlank()) ? "image" : category.trim();
        Atlas cached = ATLASES.get(safeCategory);
        if (cached != null) return cached;
        Map<String, BufferedImage> images = IMAGES.get(safeCategory);
        if (images == null || images.isEmpty()) return null;

        int maxWidth = 1024;
        int x = 0, y = 0, rowH = 0, usedW = 1;
        Map<String, Region> regions = new LinkedHashMap<>();
        for (Map.Entry<String, BufferedImage> entry : images.entrySet()) {
            BufferedImage image = entry.getValue();
            if (x > 0 && x + image.getWidth() > maxWidth) {
                x = 0;
                y += rowH;
                rowH = 0;
            }
            regions.put(entry.getKey(), new Region(x, y, image.getWidth(), image.getHeight()));
            x += image.getWidth();
            usedW = Math.max(usedW, x);
            rowH = Math.max(rowH, image.getHeight());
        }
        BufferedImage atlasImage = new BufferedImage(usedW, Math.max(1, y + rowH), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = atlasImage.createGraphics();
        try {
            for (Map.Entry<String, BufferedImage> entry : images.entrySet()) {
                Region region = regions.get(entry.getKey());
                g2.drawImage(entry.getValue(), region.x(), region.y(), null);
            }
        } finally {
            g2.dispose();
        }
        Atlas atlas = new Atlas(atlasImage, Map.copyOf(regions));
        ATLASES.put(safeCategory, atlas);
        return atlas;
    }

    public static synchronized int atlasCount() { return ATLASES.size(); }
    public static synchronized void clearForTest() { IMAGES.clear(); ATLASES.clear(); }
}
