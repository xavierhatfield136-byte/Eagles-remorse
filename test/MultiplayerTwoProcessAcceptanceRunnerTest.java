import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerTwoProcessAcceptanceRunnerTest {

    @Test
    void runnerLaunchesTwoJvmDuelAndWritesReport() throws Exception {
        Path reportPath = Files.createTempDirectory("mp-two-process-runner")
                .resolve("report.txt");

        MultiplayerTwoProcessAcceptanceRunner.Report report =
                MultiplayerTwoProcessAcceptanceRunner.run(reportPath, 15_000);

        String text = Files.readString(reportPath);
        assertTrue(report.passed(), report.failureReason());
        assertTrue(text.contains("passed=true"));
        assertTrue(text.contains("hostOk=true"));
        assertTrue(text.contains("clientOk=true"));
        assertTrue(text.contains("victoryObserved=true"));
    }
}
