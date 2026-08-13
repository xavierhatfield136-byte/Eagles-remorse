import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

public final class CustomShipImageProcessor {
    public static final int MAX_SOURCE_WIDTH = 2048;
    public static final int MAX_SOURCE_HEIGHT = 2048;
    public static final long MAX_SOURCE_BYTES = 10L * 1024L * 1024L;
    public static final int THUMBNAIL_MAX_SIZE = 256;

    public ProcessedImage processPng(Path sourcePng,
                                     CustomShipDefinition definition,
                                     CustomShipRegistry registry) throws IOException {
        if (sourcePng == null) throw new IllegalArgumentException("Source PNG path is required");
        if (definition == null) throw new IllegalArgumentException("Custom ship definition is required");
        if (registry == null) throw new IllegalArgumentException("Custom ship registry is required");
        if (!isPngPath(sourcePng)) throw new IllegalArgumentException("Custom ship imports must be PNG files");
        if (!Files.isRegularFile(sourcePng)) throw new IllegalArgumentException("Custom ship source PNG does not exist");
        long size = Files.size(sourcePng);
        if (size > MAX_SOURCE_BYTES) throw new IllegalArgumentException("Custom ship PNG exceeds maximum file size");

        BufferedImage decoded = ImageIO.read(sourcePng.toFile());
        if (decoded == null) throw new IllegalArgumentException("Custom ship PNG could not be decoded");
        if (decoded.getWidth() <= 0 || decoded.getHeight() <= 0) {
            throw new IllegalArgumentException("Custom ship PNG has invalid dimensions");
        }
        if (decoded.getWidth() > MAX_SOURCE_WIDTH || decoded.getHeight() > MAX_SOURCE_HEIGHT) {
            throw new IllegalArgumentException("Custom ship PNG exceeds maximum dimensions");
        }

        Bounds bounds = alphaBounds(decoded);
        if (bounds == null) throw new IllegalArgumentException("Custom ship PNG has no visible hull pixels");

        BufferedImage hull = cropToArgb(decoded, bounds);
        BufferedImage thumbnail = thumbnail(hull);

        registry.ensureRoot();
        Path folder = registry.folderFor(definition.id);
        Files.createDirectories(folder);
        Path hullPath = registry.resolveContentPath(definition, definition.hullImagePath);
        Path thumbnailPath = registry.resolveContentPath(definition, definition.thumbnailImagePath);
        writePngAtomically(hull, hullPath);
        writePngAtomically(thumbnail, thumbnailPath);

        return new ProcessedImage(
                hull.getWidth(),
                hull.getHeight(),
                thumbnail.getWidth(),
                thumbnail.getHeight(),
                bounds.minX,
                bounds.minY,
                bounds.maxX,
                bounds.maxY
        );
    }

    private static boolean isPngPath(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".png");
    }

    private static Bounds alphaBounds(BufferedImage image) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int alpha = (image.getRGB(x, y) >>> 24) & 0xff;
                if (alpha > 0) {
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
            }
        }
        return maxX < minX || maxY < minY ? null : new Bounds(minX, minY, maxX, maxY);
    }

    private static BufferedImage cropToArgb(BufferedImage source, Bounds bounds) {
        int width = bounds.maxX - bounds.minX + 1;
        int height = bounds.maxY - bounds.minY + 1;
        BufferedImage cropped = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = cropped.createGraphics();
        try {
            g.drawImage(source,
                    0,
                    0,
                    width,
                    height,
                    bounds.minX,
                    bounds.minY,
                    bounds.maxX + 1,
                    bounds.maxY + 1,
                    null);
        } finally {
            g.dispose();
        }
        return cropped;
    }

    private static BufferedImage thumbnail(BufferedImage source) {
        double scale = Math.min(1.0, THUMBNAIL_MAX_SIZE / (double) Math.max(source.getWidth(), source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage thumbnail = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = thumbnail.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return thumbnail;
    }

    private static void writePngAtomically(BufferedImage image, Path destination) throws IOException {
        Files.createDirectories(destination.getParent());
        Path temp = destination.resolveSibling(destination.getFileName() + ".tmp");
        if (!ImageIO.write(image, "png", temp.toFile())) {
            throw new IOException("No PNG writer is available");
        }
        try {
            Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public record ProcessedImage(
            int hullWidth,
            int hullHeight,
            int thumbnailWidth,
            int thumbnailHeight,
            int sourceMinX,
            int sourceMinY,
            int sourceMaxX,
            int sourceMaxY
    ) {}

    private record Bounds(int minX, int minY, int maxX, int maxY) {}
}
