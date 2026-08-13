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

public final class CustomWeaponAssetProcessor {
    public static final int MAX_SOURCE_WIDTH = 1024;
    public static final int MAX_SOURCE_HEIGHT = 1024;
    public static final long MAX_SOURCE_BYTES = 4L * 1024L * 1024L;
    public static final int THUMBNAIL_MAX_SIZE = 192;

    public ProcessedWeaponAssets processPngs(Path turretPng,
                                             Path projectilePng,
                                             CustomWeaponDefinition definition,
                                             CustomWeaponRegistry registry) throws IOException {
        if (definition == null) throw new IllegalArgumentException("Custom weapon definition is required");
        if (registry == null) throw new IllegalArgumentException("Custom weapon registry is required");
        BufferedImage turret = readRequiredPng(turretPng, "turret");
        BufferedImage projectile = readRequiredPng(projectilePng, "projectile");
        BufferedImage normalizedTurret = cropVisible(turret, "turret");
        BufferedImage normalizedProjectile = cropVisible(projectile, "projectile");
        BufferedImage thumbnail = thumbnail(normalizedTurret, normalizedProjectile);

        registry.ensureRoot();
        Path folder = registry.folderFor(definition.id);
        Files.createDirectories(folder);
        writePngAtomically(normalizedTurret, registry.resolveContentPath(definition, definition.turretAsset));
        writePngAtomically(normalizedProjectile, registry.resolveContentPath(definition, definition.projectileAsset));
        writePngAtomically(thumbnail, registry.resolveContentPath(definition, definition.thumbnailAsset));

        return new ProcessedWeaponAssets(
                normalizedTurret.getWidth(),
                normalizedTurret.getHeight(),
                normalizedProjectile.getWidth(),
                normalizedProjectile.getHeight(),
                thumbnail.getWidth(),
                thumbnail.getHeight()
        );
    }

    private static BufferedImage readRequiredPng(Path path, String label) throws IOException {
        if (path == null) throw new IllegalArgumentException("Custom weapon " + label + " PNG path is required");
        if (!isPngPath(path)) throw new IllegalArgumentException("Custom weapon " + label + " import must be a PNG file");
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("Custom weapon " + label + " PNG does not exist");
        long size = Files.size(path);
        if (size > MAX_SOURCE_BYTES) throw new IllegalArgumentException("Custom weapon " + label + " PNG exceeds maximum file size");
        BufferedImage decoded = ImageIO.read(path.toFile());
        if (decoded == null) throw new IllegalArgumentException("Custom weapon " + label + " PNG could not be decoded");
        if (decoded.getWidth() <= 0 || decoded.getHeight() <= 0) {
            throw new IllegalArgumentException("Custom weapon " + label + " PNG has invalid dimensions");
        }
        if (decoded.getWidth() > MAX_SOURCE_WIDTH || decoded.getHeight() > MAX_SOURCE_HEIGHT) {
            throw new IllegalArgumentException("Custom weapon " + label + " PNG exceeds maximum dimensions");
        }
        return decoded;
    }

    private static boolean isPngPath(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".png");
    }

    private static BufferedImage cropVisible(BufferedImage source, String label) {
        int minX = source.getWidth();
        int minY = source.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int alpha = (source.getRGB(x, y) >>> 24) & 0xff;
                if (alpha > 0) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < minX || maxY < minY) {
            throw new IllegalArgumentException("Custom weapon " + label + " PNG has no visible pixels");
        }
        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        BufferedImage cropped = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = cropped.createGraphics();
        try {
            g.drawImage(source, 0, 0, width, height, minX, minY, maxX + 1, maxY + 1, null);
        } finally {
            g.dispose();
        }
        return cropped;
    }

    private static BufferedImage thumbnail(BufferedImage turret, BufferedImage projectile) {
        int sourceW = turret.getWidth() + projectile.getWidth() + 12;
        int sourceH = Math.max(turret.getHeight(), projectile.getHeight());
        BufferedImage combined = new BufferedImage(Math.max(1, sourceW), Math.max(1, sourceH), BufferedImage.TYPE_INT_ARGB);
        Graphics2D cg = combined.createGraphics();
        try {
            cg.drawImage(turret, 0, (sourceH - turret.getHeight()) / 2, null);
            cg.drawImage(projectile, turret.getWidth() + 12, (sourceH - projectile.getHeight()) / 2, null);
        } finally {
            cg.dispose();
        }
        double scale = Math.min(1.0, THUMBNAIL_MAX_SIZE / (double) Math.max(combined.getWidth(), combined.getHeight()));
        int width = Math.max(1, (int) Math.round(combined.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(combined.getHeight() * scale));
        BufferedImage thumbnail = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = thumbnail.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(combined, 0, 0, width, height, null);
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

    public record ProcessedWeaponAssets(
            int turretWidth,
            int turretHeight,
            int projectileWidth,
            int projectileHeight,
            int thumbnailWidth,
            int thumbnailHeight
    ) {}
}
