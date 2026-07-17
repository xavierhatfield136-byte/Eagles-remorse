import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerTwoMachineRunbookHarnessTest {

    @Test
    void writeRunbookCreatesHostClientAuditScriptsAndTemplates() throws Exception {
        Path dir = Files.createTempDirectory("mp-two-machine-runbook").resolve("run");
        MultiplayerTwoMachineRunbookHarness.RunbookConfig config =
                new MultiplayerTwoMachineRunbookHarness.RunbookConfig(
                        dir, "192.168.1.10", "192.168.1.11", 46718, 60000,
                        "manual-lan-test", "QA", "test-build");

        MultiplayerTwoMachineRunbookHarness.RunbookPaths paths =
                MultiplayerTwoMachineRunbookHarness.writeRunbook(config);

        assertTrue(Files.isRegularFile(paths.readme()));
        assertTrue(Files.isRegularFile(paths.hostScript()));
        assertTrue(Files.isRegularFile(paths.clientScript()));
        assertTrue(Files.isRegularFile(paths.auditScript()));
        assertTrue(Files.isRegularFile(paths.interactiveManualReport()));
        assertTrue(Files.isRegularFile(paths.finalManualReport()));
        assertTrue(Files.isRegularFile(paths.twoMachineLog()));

        String host = Files.readString(paths.hostScript());
        assertTrue(host.contains("multiplayerTwoProcessAcceptance"));
        assertTrue(host.contains("multiplayerLanPreflight"));
        assertTrue(host.contains("multiplayerLanAcceptanceHost"));
        assertTrue(host.contains("-PmpPort=46718"));
        assertTrue(host.contains("-PmpHostAddress=192.168.1.10"));

        String client = Files.readString(paths.clientScript());
        assertTrue(client.contains("-PmpAddress=192.168.1.10:46718"));
        assertTrue(client.contains("-PmpClientAddress=192.168.1.11"));
        assertTrue(client.contains("multiplayerLanAcceptanceClient"));

        String audit = Files.readString(paths.auditScript());
        assertTrue(audit.contains("multiplayerTwoMachineReadiness"));
        assertTrue(audit.contains("multiplayerLanAcceptanceValidate"));
        assertTrue(audit.contains("multiplayerManualAcceptanceReport"));
        assertTrue(audit.contains("multiplayerTwoMachineAcceptanceLog"));
        assertTrue(audit.contains("multiplayerAcceptanceAudit"));
        assertTrue(audit.contains("multiplayerReleaseGate"));
        assertTrue(audit.contains("multiplayerEvidenceBundle"));
        assertTrue(audit.contains("readiness.txt"));
        assertTrue(audit.contains("two-machine-acceptance-log.md"));
        assertTrue(audit.contains("evidence-bundle.txt"));
        assertTrue(audit.contains("-PmpStrict=true"));

        String manual = Files.readString(paths.finalManualReport());
        assertTrue(manual.contains("scope=final-two-machine"));
        assertTrue(manual.contains("tester=QA"));
        assertTrue(manual.contains("build=test-build"));
        assertTrue(manual.contains("hostAddress=192.168.1.10:46718"));
        assertTrue(manual.contains("clientAddress=192.168.1.11"));
        assertTrue(manual.contains("twoProcessReport=" + dir.resolve("two-process.txt").toString().replace('\\', '/')));
        assertTrue(manual.contains("preflightReport=" + dir.resolve("preflight.txt").toString().replace('\\', '/')));
        assertTrue(manual.contains("hostReport=" + dir.resolve("host.txt").toString().replace('\\', '/')));
        assertTrue(manual.contains("clientReport=" + dir.resolve("client.txt").toString().replace('\\', '/')));

        String log = Files.readString(paths.twoMachineLog());
        assertTrue(log.contains("Host machine OS / CPU / RAM:"));
        assertTrue(log.contains("- [ ] Both machines show the same winner."));
    }

    @Test
    void mainWritesRunbookToRequestedDirectory() throws Exception {
        Path dir = Files.createTempDirectory("mp-two-machine-runbook-main").resolve("run");

        MultiplayerTwoMachineRunbookHarness.main(new String[]{
                "--dir=" + dir,
                "--host-address=192.168.1.20",
                "--client-address=192.168.1.21",
                "--port=46719",
                "--tester=QA",
                "--build=test-build"
        });

        assertTrue(Files.readString(dir.resolve("README.md")).contains("192.168.1.20:46719"));
        assertTrue(Files.readString(dir.resolve("README.md")).contains("192.168.1.21"));
        assertTrue(Files.readString(dir.resolve("README.md")).contains("release gate"));
        assertTrue(Files.readString(dir.resolve("README.md")).contains("readiness evidence"));
        assertTrue(Files.readString(dir.resolve("README.md")).contains("two-machine log"));
        assertTrue(Files.readString(dir.resolve("README.md")).contains("evidence bundle"));
        assertTrue(Files.readString(dir.resolve("client-acceptance.ps1")).contains("-PmpAddress=192.168.1.20:46719"));
        assertTrue(Files.readString(dir.resolve("client-acceptance.ps1")).contains("-PmpClientAddress=192.168.1.21"));
    }
}
