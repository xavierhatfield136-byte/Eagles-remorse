import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerManualAcceptanceTemplateSetHarnessTest {

    @Test
    void writesInteractiveAndFinalManualTemplates() throws Exception {
        Path dir = Files.createTempDirectory("mp-manual-template-set");
        Path interactive = dir.resolve("interactive.txt");
        Path finalReport = dir.resolve("final.txt");

        MultiplayerManualAcceptanceTemplateSetHarness.main(new String[]{
                "--interactive-report=" + interactive,
                "--final-report=" + finalReport
        });

        String interactiveText = Files.readString(interactive);
        String finalText = Files.readString(finalReport);
        assertTrue(interactiveText.contains("scope=interactive-two-process"));
        assertTrue(interactiveText.contains("hostAddress=127.0.0.1:46717"));
        assertTrue(finalText.contains("scope=final-two-machine"));
        assertTrue(finalText.contains("hostAddress=<host-lan-ip>:46717"));
        assertTrue(finalText.contains("clientAddress=<client-lan-ip-or-machine-name>"));
    }
}
