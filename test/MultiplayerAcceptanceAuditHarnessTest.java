import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerAcceptanceAuditHarnessTest {

    @Test
    void harnessWritesAuditWithMissingExternalManualGates() throws Exception {
        Path dir = Files.createTempDirectory("mp-acceptance-audit-harness");
        Path twoProcess = dir.resolve("two-process.txt");
        Path preflight = dir.resolve("preflight.txt");
        Path host = dir.resolve("host.txt");
        Path client = dir.resolve("client.txt");
        Path interactive = dir.resolve("interactive.txt");
        Path finalManual = dir.resolve("final.txt");
        Path twoMachineLog = dir.resolve("two-machine-log.md");
        Path readiness = dir.resolve("readiness.txt");
        Path report = dir.resolve("audit.txt");
        Files.writeString(twoProcess, String.join(System.lineSeparator(),
                "passed=true",
                "hostOk=true",
                "clientOk=true",
                "victoryObserved=true",
                ""));
        Files.writeString(preflight, passingPreflightReport());
        Files.writeString(host, passingLanReport("host"));
        Files.writeString(client, passingLanReport("client"));
        MultiplayerManualAcceptanceReportHarness.writeTemplate(interactive, "interactive-two-process", false);
        MultiplayerTwoMachineAcceptanceLogHarness.writeTemplate(twoMachineLog);
        Files.writeString(readiness, passingReadinessReport());

        MultiplayerAcceptanceAuditHarness.main(new String[]{
                "--two-process-report=" + twoProcess,
                "--preflight-report=" + preflight,
                "--host-report=" + host,
                "--client-report=" + client,
                "--interactive-report=" + interactive,
                "--final-report=" + finalManual,
                "--two-machine-log=" + twoMachineLog,
                "--readiness-report=" + readiness,
                "--report=" + report
        });

        String text = Files.readString(report);
        assertTrue(text.contains("complete=false"));
        assertTrue(text.contains("gate.1.proven=true"));
        assertTrue(text.contains("LAN host preflight"));
        assertTrue(text.contains("first real two-machine LAN CLI pass"));
        assertTrue(text.contains("local two-machine readiness report"));
        assertTrue(text.contains("interactive two-process manual acceptance"));
        assertTrue(text.contains("two-machine machine/build/network acceptance log"));
        assertFalse(text.contains("complete=true"));
    }

    private static String passingLanReport(String role) {
        boolean host = "host".equals(role);
        return String.join(System.lineSeparator(),
                "passed=true",
                "role=" + role,
                "address=192.168.1.10:46717",
                "localEndpoint=" + (host ? "192.168.1.10:46717" : "192.168.1.11:52000"),
                "remoteEndpoint=" + (host ? "192.168.1.11:52000" : "192.168.1.10:46717"),
                "connectionId=abc",
                "result=Elimination victory",
                "snapshotsReceived=2",
                "inputAcked=true",
                "returnedToMenu=true",
                "");
    }

    private static String passingPreflightReport() {
        return String.join(System.lineSeparator(),
                "passed=true",
                "port=46717",
                "portBindable=true",
                "candidateAddressCount=1",
                "candidateAddress.1=192.168.1.10",
                "");
    }

    private static String passingReadinessReport() {
        return String.join(System.lineSeparator(),
                "passed=true",
                "checkCount=2",
                "check.1.name=host port bindable",
                "check.1.passed=true",
                "check.2.name=candidate LAN address",
                "check.2.passed=true",
                "");
    }
}
