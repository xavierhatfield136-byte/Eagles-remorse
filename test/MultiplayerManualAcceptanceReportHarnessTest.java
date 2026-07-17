import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerManualAcceptanceReportHarnessTest {

    @Test
    void templateWritesIncompleteEvidenceFileForManualCompletion() throws Exception {
        Path report = Files.createTempDirectory("mp-manual-template").resolve("interactive.txt");

        MultiplayerManualAcceptanceReportHarness.main(new String[]{
                "--mode=template",
                "--scope=interactive-two-process",
                "--report=" + report
        });

        String text = Files.readString(report);
        assertTrue(text.contains("passed=false"));
        assertTrue(text.contains("scope=interactive-two-process"));
        assertTrue(text.contains("check.victoryParity=false"));
    }

    @Test
    void validateRejectsUnfinishedTemplate() throws Exception {
        Path report = Files.createTempDirectory("mp-manual-template-bad").resolve("final.txt");
        MultiplayerManualAcceptanceReportHarness.writeTemplate(report, "final-two-machine", true);

        assertThrows(IllegalStateException.class, () ->
                MultiplayerManualAcceptanceReportHarness.main(new String[]{
                        "--mode=validate",
                        "--scope=final-two-machine",
                        "--report=" + report,
                        "--real-lan=true"
                }));
    }

    @Test
    void validateRejectsCompletedReportWithMissingLinkedEvidence() throws Exception {
        Path report = Files.createTempDirectory("mp-manual-template-linked").resolve("interactive.txt");
        Files.writeString(report, String.join(System.lineSeparator(),
                "passed=true",
                "scope=interactive-two-process",
                "tester=QA",
                "build=test-build",
                "date=2026-07-17",
                "hostAddress=127.0.0.1:46717",
                "clientAddress=127.0.0.1",
                "twoProcessReport=missing-two-process.txt",
                "check.hostJoinReady=true",
                "check.shipControl=true",
                "check.inputReplication=true",
                "check.snapshotParity=true",
                "check.victoryParity=true",
                "check.clientDisconnectForfeit=true",
                "check.hostDisconnectReturnToMenu=true",
                "check.campaignUnaffected=true",
                ""));

        assertThrows(IllegalStateException.class, () ->
                MultiplayerManualAcceptanceReportHarness.main(new String[]{
                        "--mode=validate",
                        "--scope=interactive-two-process",
                        "--report=" + report
                }));
    }

    @Test
    void templateUsesRealLanPlaceholdersForFinalTwoMachineScope() {
        String text = MultiplayerManualAcceptanceReportHarness.templateText("final-two-machine", true);

        assertTrue(text.contains("hostAddress=<host-lan-ip>:46717"));
        assertFalse(text.contains("hostAddress=127.0.0.1:46717"));
    }
}
