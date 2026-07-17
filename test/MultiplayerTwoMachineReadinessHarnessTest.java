import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerTwoMachineReadinessHarnessTest {

    @Test
    void inspectReportsMissingRunbookAndTemplates() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        Path dir = Files.createTempDirectory("mp-readiness-missing");

        MultiplayerTwoMachineReadinessHarness.ReadinessReport report =
                MultiplayerTwoMachineReadinessHarness.inspect(
                        port,
                        dir.resolve("runbook"),
                        dir.resolve("two-process.txt"),
                        dir.resolve("interactive.txt"),
                        dir.resolve("final.txt"));

        assertFalse(report.passed());
        String text = report.toText();
        assertTrue(text.contains("checkCount=9"));
        assertTrue(text.contains("runbook README"));
        assertTrue(text.contains("two-process acceptance report"));
    }

    @Test
    void mainWritesReviewableReadinessReport() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        Path dir = Files.createTempDirectory("mp-readiness-main");
        Path runbook = dir.resolve("runbook");
        Files.createDirectories(runbook);
        Files.writeString(runbook.resolve("README.md"), "readme");
        Files.writeString(runbook.resolve("host-acceptance.ps1"), "host");
        Files.writeString(runbook.resolve("client-acceptance.ps1"), "client");
        Files.writeString(runbook.resolve("audit-acceptance.ps1"), "audit");
        Path twoProcess = dir.resolve("two-process.txt");
        Path interactive = dir.resolve("interactive.txt");
        Path finalReport = dir.resolve("final.txt");
        Files.writeString(twoProcess, "passed=true");
        Files.writeString(interactive, "passed=false");
        Files.writeString(finalReport, "passed=false");
        Path output = dir.resolve("readiness.txt");

        MultiplayerTwoMachineReadinessHarness.main(new String[]{
                "--port=" + port,
                "--runbook-dir=" + runbook,
                "--two-process-report=" + twoProcess,
                "--interactive-report=" + interactive,
                "--final-report=" + finalReport,
                "--report=" + output
        });

        String text = Files.readString(output);
        assertTrue(text.contains("port=" + port));
        assertTrue(text.contains("host port bindable"));
        assertTrue(text.contains("final two-machine manual template"));
    }
}
