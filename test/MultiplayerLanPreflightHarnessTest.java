import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerLanPreflightHarnessTest {

    @Test
    void evaluateReportsMissingAddressAndPortProblemsSeparately() {
        MultiplayerLanPreflightHarness.PreflightReport report =
                MultiplayerLanPreflightHarness.evaluate(46717, List.of(), false);

        assertFalse(report.passed());
        assertTrue(report.toText().contains("candidateAddressCount=0"));
        assertTrue(report.toText().contains("could not be bound"));
        assertTrue(report.toText().contains("direct LAN/manual address only"));
    }

    @Test
    void evaluatePassesWithCandidateAddressAndBindablePort() {
        MultiplayerLanPreflightHarness.PreflightReport report =
                MultiplayerLanPreflightHarness.evaluate(46717, List.of("192.168.1.10"), true);

        assertTrue(report.passed());
        assertTrue(report.toText().contains("candidateAddress.1=192.168.1.10"));
    }

    @Test
    void runCanBindAnAvailablePortAndWriteReport() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        Path reportPath = Files.createTempDirectory("mp-lan-preflight").resolve("preflight.txt");

        MultiplayerLanPreflightHarness.main(new String[]{
                "--port=" + port,
                "--report=" + reportPath
        });

        String text = Files.readString(reportPath);
        assertTrue(text.contains("port=" + port));
        assertTrue(text.contains("portBindable=true"));
    }
}
