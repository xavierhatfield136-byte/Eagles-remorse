package app.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Small helper for keeping compiled artifacts out of the source tree.
 */
public final class SourceTreeHygiene {
    private SourceTreeHygiene() {}

    public static int purgeDefaultSourceTreeArtifacts() {
        return purgeCompiledArtifacts(Paths.get("src"));
    }

    public static int purgeCompiledArtifacts(Path root) {
        if (root == null || !Files.isDirectory(root)) return 0;

        List<Path> strayArtifacts = new ArrayList<>();
        try (var walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(SourceTreeHygiene::isCompiledArtifact)
                    .forEach(strayArtifacts::add);
        } catch (IOException ex) {
            System.err.println("[hygiene] scan_failed root=" + root.toAbsolutePath() + " error=" + ex.getMessage());
            return 0;
        }

        strayArtifacts.sort(Comparator.reverseOrder());
        int deleted = 0;
        for (Path artifact : strayArtifacts) {
            try {
                if (Files.deleteIfExists(artifact)) deleted++;
            } catch (IOException ex) {
                System.err.println("[hygiene] delete_failed path=" + artifact.toAbsolutePath() + " error=" + ex.getMessage());
            }
        }
        if (deleted > 0) {
            System.out.println("[hygiene] removed " + deleted + " compiled artifact(s) from " + root.toAbsolutePath());
        }
        return deleted;
    }

    static boolean isCompiledArtifact(Path path) {
        if (path == null) return false;
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.US);
        return name.endsWith(".class");
    }
}
