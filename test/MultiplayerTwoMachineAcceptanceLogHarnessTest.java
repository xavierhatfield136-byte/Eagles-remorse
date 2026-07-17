import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerTwoMachineAcceptanceLogHarnessTest {

    @Test
    void templateIsIntentionallyIncomplete() throws Exception {
        Path log = Files.createTempDirectory("mp-two-machine-log").resolve("log.md");

        MultiplayerTwoMachineAcceptanceLogHarness.writeTemplate(log);

        String text = Files.readString(log);
        assertTrue(text.contains("Host machine OS / CPU / RAM:"));
        assertTrue(text.contains("- [ ] Both machines show the same winner."));
        assertFalse(MultiplayerTwoMachineAcceptanceLogHarness.validate(log).accepted());
    }

    @Test
    void validateAcceptsCompletedNonLoopbackLog() throws Exception {
        Path log = Files.createTempDirectory("mp-two-machine-log-complete").resolve("log.md");
        Files.writeString(log, completedLog(false));

        MultiplayerTwoMachineAcceptanceLogHarness.LogValidation validation =
                MultiplayerTwoMachineAcceptanceLogHarness.validate(log);

        assertTrue(validation.accepted(), validation.missing().toString());
    }

    @Test
    void validateRejectsLoopbackAndUncheckedItems() throws Exception {
        Path log = Files.createTempDirectory("mp-two-machine-log-loopback").resolve("log.md");
        Files.writeString(log, completedLog(true).replace("- [x] Both machines show the same winner.",
                "- [ ] Both machines show the same winner."));

        MultiplayerTwoMachineAcceptanceLogHarness.LogValidation validation =
                MultiplayerTwoMachineAcceptanceLogHarness.validate(log);

        assertFalse(validation.accepted());
        assertTrue(validation.missing().contains("loopback evidence"));
        assertTrue(validation.missing().stream().anyMatch(item -> item.startsWith("required pass unchecked")));
    }

    @Test
    void validateRejectsFailedResultFields() throws Exception {
        Path log = Files.createTempDirectory("mp-two-machine-log-failed-result").resolve("log.md");
        Files.writeString(log, completedLog(false)
                .replace("- Release gate result: allowed=true", "- Release gate result: allowed=false"));

        MultiplayerTwoMachineAcceptanceLogHarness.LogValidation validation =
                MultiplayerTwoMachineAcceptanceLogHarness.validate(log);

        assertFalse(validation.accepted());
        assertTrue(validation.missing().contains("Release gate result.success"));
    }

    @Test
    void mainValidateThrowsForIncompleteTemplate() throws Exception {
        Path log = Files.createTempDirectory("mp-two-machine-log-main").resolve("log.md");
        MultiplayerTwoMachineAcceptanceLogHarness.writeTemplate(log);

        assertThrows(IllegalStateException.class, () ->
                MultiplayerTwoMachineAcceptanceLogHarness.main(new String[]{
                        "--mode=validate",
                        "--log=" + log
                }));
    }

    private static String completedLog(boolean loopback) {
        String host = loopback ? "127.0.0.1:46717" : "192.168.1.10:46717";
        String client = loopback ? "127.0.0.1" : "192.168.1.11";
        return MultiplayerTwoMachineAcceptanceLogHarness.templateText()
                .replace("- Build/version:", "- Build/version: 1.0.1.2")
                .replace("- Commit or packaged build ID:", "- Commit or packaged build ID: local-test")
                .replace("- Host machine OS / CPU / RAM:", "- Host machine OS / CPU / RAM: Windows / CPU / 16GB")
                .replace("- Client machine OS / CPU / RAM:", "- Client machine OS / CPU / RAM: Windows / CPU / 16GB")
                .replace("- Network type:", "- Network type: wired")
                .replace("- Host LAN address and port:", "- Host LAN address and port: " + host)
                .replace("- Client LAN address:", "- Client LAN address: " + client)
                .replace("- Firewall rule created or confirmed:", "- Firewall rule created or confirmed: yes")
                .replace("- Two-machine runbook directory:", "- Two-machine runbook directory: build/reports/run")
                .replace("- Host preflight report path:", "- Host preflight report path: preflight.txt")
                .replace("- Host preflight candidate address used:", "- Host preflight candidate address used: " + host)
                .replace("- Host CLI report path:", "- Host CLI report path: host.txt")
                .replace("- Client CLI report path:", "- Client CLI report path: client.txt")
                .replace("- Host observed client endpoint:", "- Host observed client endpoint: " + client + ":52000")
                .replace("- Client reported local endpoint:", "- Client reported local endpoint: " + client + ":52000")
                .replace("- Final two-machine manual report path:", "- Final two-machine manual report path: final.txt")
                .replace("- Evidence validator result:", "- Evidence validator result: passed")
                .replace("- Manual report validator result:", "- Manual report validator result: passed")
                .replace("- Acceptance audit result:", "- Acceptance audit result: complete=true")
                .replace("- Release gate result:", "- Release gate result: allowed=true")
                .replace("- Evidence bundle report path:", "- Evidence bundle report path: evidence-bundle.txt")
                .replace("- Snapshot gap / perceived latency:", "- Snapshot gap / perceived latency: acceptable")
                .replace("- Disconnects or reconnect attempts:", "- Disconnects or reconnect attempts: none")
                .replace("- Errors or warnings:", "- Errors or warnings: none")
                .replace("- Memory/process growth:", "- Memory/process growth: none observed")
                .replace("- Follow-up defects filed:", "- Follow-up defects filed: none")
                .replace("- [ ]", "- [x]");
    }
}
