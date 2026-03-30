import app.support.SourceTreeHygiene;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceTreeHygieneTest {

    @Test
    void purgeCompiledArtifactsDeletesOnlyClassFiles() throws Exception {
        Path root = Files.createTempDirectory("source-tree-hygiene");
        Path nested = Files.createDirectories(root.resolve("nested"));
        Path classFile = Files.writeString(root.resolve("Main.class"), "stale");
        Path innerClassFile = Files.writeString(nested.resolve("GameRenderSystem$1.class"), "stale");
        Path sourceFile = Files.writeString(nested.resolve("Main.java"), "source");

        int deleted = SourceTreeHygiene.purgeCompiledArtifacts(root);

        assertEquals(2, deleted);
        assertFalse(Files.exists(classFile));
        assertFalse(Files.exists(innerClassFile));
        assertTrue(Files.exists(sourceFile));
    }

    @Test
    void purgeCompiledArtifactsIgnoresMissingRoot() {
        Path missing = Path.of("build", "tmp", "definitely-missing", "src");
        assertEquals(0, SourceTreeHygiene.purgeCompiledArtifacts(missing));
    }
}
