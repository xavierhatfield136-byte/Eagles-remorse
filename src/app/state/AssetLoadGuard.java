package app.state;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tracks image decode and disk access so gameplay-time asset faults are visible.
 */
public final class AssetLoadGuard {
    private static boolean gameplayBegun = false;
    private static boolean renderedFrame = false;
    private static int decodeCount = 0;
    private static int decodeDuringFrameCount = 0;
    private static int gameplayDiskLoadCount = 0;
    private static long decodeNs = 0L;
    private static String lastWarning = "";
    private static final Map<String, Long> IMAGE_BYTES = new BoundedCache<>(1024);

    private AssetLoadGuard() {}

    public static synchronized void markGameplayBegun() {
        gameplayBegun = true;
    }

    public static synchronized void beginRenderedFrame() {
        renderedFrame = true;
    }

    public static synchronized void endRenderedFrame() {
        renderedFrame = false;
    }

    public static BufferedImage read(File file, String category) throws IOException {
        if (file == null) return null;
        long start = System.nanoTime();
        BufferedImage image = ImageIO.read(file);
        record(category, file.getPath(), image, true, System.nanoTime() - start);
        return image;
    }

    public static BufferedImage read(InputStream in, String category, String label) throws IOException {
        if (in == null) return null;
        long start = System.nanoTime();
        BufferedImage image = ImageIO.read(in);
        record(category, label, image, false, System.nanoTime() - start);
        return image;
    }

    private static synchronized void record(String category, String label, BufferedImage image,
                                            boolean disk, long elapsedNs) {
        if (image == null) return;
        decodeCount++;
        decodeNs += Math.max(0L, elapsedNs);
        String safeCategory = (category == null || category.isBlank()) ? "image" : category.trim();
        String safeLabel = (label == null || label.isBlank()) ? "unknown" : label.trim();
        IMAGE_BYTES.put(safeCategory + ":" + safeLabel, estimatedBytes(image));
        SpriteAtlasRegistry.register(safeCategory, safeLabel, image);
        if (renderedFrame) {
            decodeDuringFrameCount++;
            lastWarning = "IMAGE DECODE DURING RENDER: " + safeLabel;
        } else if (gameplayBegun && disk) {
            gameplayDiskLoadCount++;
            lastWarning = "DISK ASSET LOAD AFTER GAMEPLAY START: " + safeLabel;
        }
    }

    public static synchronized int decodeCount() { return decodeCount; }
    public static synchronized int decodeDuringFrameCount() { return decodeDuringFrameCount; }
    public static synchronized int gameplayDiskLoadCount() { return gameplayDiskLoadCount; }
    public static synchronized double decodeMs() { return decodeNs / 1_000_000.0; }
    public static synchronized long spriteMemoryBytes() {
        long bytes = 0L;
        for (long value : IMAGE_BYTES.values()) bytes += Math.max(0L, value);
        return bytes;
    }
    public static synchronized int trackedImageCount() { return IMAGE_BYTES.size(); }
    public static synchronized String lastWarning() { return lastWarning; }

    public static synchronized void resetForTest() {
        gameplayBegun = false;
        renderedFrame = false;
        decodeCount = 0;
        decodeDuringFrameCount = 0;
        gameplayDiskLoadCount = 0;
        decodeNs = 0L;
        lastWarning = "";
        IMAGE_BYTES.clear();
    }

    private static long estimatedBytes(BufferedImage image) {
        if (image == null) return 0L;
        return (long) image.getWidth() * image.getHeight() * 4L;
    }
}
