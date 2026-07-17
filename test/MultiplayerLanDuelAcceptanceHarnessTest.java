import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerLanDuelAcceptanceHarnessTest {

    @Test
    void lanAcceptanceHarnessRunsDuelOverManualAddress() throws Exception {
        int port = freeLoopbackPort();
        CompletableFuture<MultiplayerLanDuelAcceptanceHarness.AcceptanceReport> host =
                CompletableFuture.supplyAsync(() -> MultiplayerLanDuelAcceptanceHarness.runHost(
                        port, true, "acceptance-cli-test", 5_000));

        Thread.sleep(100L);
        MultiplayerLanDuelAcceptanceHarness.AcceptanceReport client =
                MultiplayerLanDuelAcceptanceHarness.runClient(
                        new MultiplayerLanTransportV1.DirectAddress("127.0.0.1", port),
                        "acceptance-cli-test",
                        5_000);
        MultiplayerLanDuelAcceptanceHarness.AcceptanceReport hostReport =
                host.get(5, TimeUnit.SECONDS);

        assertTrue(hostReport.passed(), hostReport.failureReason());
        assertTrue(client.passed(), client.failureReason());
        assertEquals("Elimination victory", hostReport.result());
        assertEquals("Elimination victory", client.result());
        assertTrue(client.inputAcked());
        assertTrue(client.snapshotsReceived() >= 2);
        assertTrue(hostReport.returnedToMenu());
    }

    @Test
    void acceptanceReportIsCopyPasteFriendlyForManualLog() {
        MultiplayerLanDuelAcceptanceHarness.AcceptanceReport report =
                new MultiplayerLanDuelAcceptanceHarness.AcceptanceReport(
                        true, "client", "127.0.0.1:46717", "conn-1",
                        "Elimination victory", 2, true, true, "");

        String text = report.toText();

        assertTrue(text.contains("passed=true"));
        assertTrue(text.contains("role=client"));
        assertTrue(text.contains("localEndpoint="));
        assertTrue(text.contains("remoteEndpoint="));
        assertTrue(text.contains("result=Elimination victory"));
        assertTrue(text.contains("snapshotsReceived=2"));
    }

    @Test
    void validateModeAcceptsPassingHostAndClientReports() throws Exception {
        Path dir = Files.createTempDirectory("mp-lan-validate-mode");
        Path host = dir.resolve("host.txt");
        Path client = dir.resolve("client.txt");
        String hostReport = String.join(System.lineSeparator(),
                "passed=true",
                "role=host",
                "address=192.168.1.10:46717",
                "localEndpoint=192.168.1.10:46717",
                "remoteEndpoint=192.168.1.11:52000",
                "connectionId=conn-1",
                "result=Elimination victory",
                "snapshotsReceived=2",
                "inputAcked=true",
                "returnedToMenu=true",
                "");
        String clientReport = String.join(System.lineSeparator(),
                "passed=true",
                "role=client",
                "address=192.168.1.10:46717",
                "localEndpoint=192.168.1.11:52000",
                "remoteEndpoint=192.168.1.10:46717",
                "connectionId=conn-1",
                "result=Elimination victory",
                "snapshotsReceived=2",
                "inputAcked=true",
                "returnedToMenu=true",
                "");
        Files.writeString(host, hostReport);
        Files.writeString(client, clientReport);

        MultiplayerLanDuelAcceptanceHarness.main(new String[]{
                "validate",
                "--host-report=" + host,
                "--client-report=" + client
        });
    }

    private static int freeLoopbackPort() throws Exception {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
