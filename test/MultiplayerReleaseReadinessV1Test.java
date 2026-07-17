import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerReleaseReadinessV1Test {

    @Test
    void limitationsAndTroubleshootingDocsExist() {
        assertTrue(Files.isRegularFile(Path.of(
                "docs", "MULTIPLAYER_V1_LIMITATIONS_AND_TROUBLESHOOTING.md")));
        assertTrue(Files.isRegularFile(Path.of(
                "docs", "MULTIPLAYER_V1_MANUAL_ACCEPTANCE.md")));
    }

    @Test
    void debugInfoDisplaysProtocolVersionManifestBuildAndFeatureFlag() {
        MultiplayerReleaseReadinessV1.DebugInfo info =
                MultiplayerReleaseReadinessV1.debugInfo();

        assertEquals(MultiplayerProtocolV1.PROTOCOL_VERSION, info.protocolVersion());
        assertFalse(info.gameBuild().isBlank());
        assertNotNull(info.manifest());
        assertEquals(MultiplayerRulesV1.entryPointEnabled(), info.featureFlagEnabled());
    }

    @Test
    void stateHashValidatesSnapshotReconstruction() {
        MultiplayerBattleSnapshot snapshot = new MultiplayerBattleSnapshot(55L,
                List.of(new MultiplayerBattleSnapshot.ShipSnapshot(
                        101, ShipRole.FRIGATE, Faction.ALLY,
                        1.0, 2.0, 0.1, 0.2, 0.3, 44, 9.0, true)),
                List.of(new MultiplayerBattleSnapshot.SlotSnapshot(
                        MultiplayerRulesV1.HOST_SLOT_ID, Faction.ALLY.teamId(), 101,
                        MultiplayerRulesV1.PlayerRole.DIRECT_SHIP,
                        MultiplayerRulesV1.ConnectionState.LOCAL,
                        "Host")));

        assertFalse(MultiplayerReleaseReadinessV1.stateHash(snapshot).isBlank());
        assertTrue(MultiplayerReleaseReadinessV1.reconstructedStateHashMatches(snapshot));
    }

    @Test
    void failedConnectTelemetryIsOptInAndRedactsPrivateAddresses() {
        MultiplayerReleaseReadinessV1.FailedConnectTelemetry telemetry =
                MultiplayerReleaseReadinessV1.failedConnectTelemetry(
                        true,
                        "match-1",
                        "Connection refused",
                        "192.168.1.50:46717");

        assertTrue(telemetry.enabled());
        assertEquals("match-1", telemetry.matchId());
        assertEquals("Connection refused", telemetry.reason());
        assertEquals("private-lan-address:46717", telemetry.redactedRemoteAddress());
        assertEquals(MultiplayerProtocolV1.PROTOCOL_VERSION, telemetry.protocolVersion());
        assertFalse(telemetry.gameBuild().isBlank());
    }

    @Test
    void crashSafeCleanupClosesMatchScope() {
        MultiplayerMatchCleanupScope scope =
                new MultiplayerMatchCleanupScope(new MultiplayerNetworkCommandQueue());
        scope.addSnapshot(new MultiplayerBattleSnapshot(1L, null, null));

        assertTrue(MultiplayerReleaseReadinessV1.crashSafeCleanup(scope));
        assertTrue(scope.closed());
        assertEquals(0, scope.snapshotBufferSize());
    }

    @Test
    void knownLimitationsAndFeatureFlagGuardAreExposed() {
        assertTrue(MultiplayerReleaseReadinessV1.knownLimitations().stream()
                .anyMatch(line -> line.contains("campaign multiplayer is unsupported")));
        assertEquals(!MultiplayerRulesV1.entryPointEnabled(),
                MultiplayerReleaseReadinessV1.featureFlagDisablesEntryPoints());
    }

    @Test
    void twoMachineCliEvidenceRequiresPassingHostAndClientReports() throws Exception {
        Path dir = Files.createTempDirectory("mp-two-machine-evidence");
        Path host = dir.resolve("host.txt");
        Path client = dir.resolve("client.txt");
        Files.writeString(host, String.join(System.lineSeparator(),
                "passed=true",
                "role=host",
                "address=192.168.1.10:46717",
                "localEndpoint=192.168.1.10:46717",
                "remoteEndpoint=192.168.1.11:52000",
                "connectionId=abc",
                "result=Elimination victory",
                "snapshotsReceived=2",
                "inputAcked=true",
                "returnedToMenu=true",
                ""));
        Files.writeString(client, String.join(System.lineSeparator(),
                "passed=true",
                "role=client",
                "address=192.168.1.10:46717",
                "localEndpoint=192.168.1.11:52000",
                "remoteEndpoint=192.168.1.10:46717",
                "connectionId=abc",
                "result=Elimination victory",
                "snapshotsReceived=2",
                "inputAcked=true",
                "returnedToMenu=true",
                ""));

        MultiplayerReleaseReadinessV1.TwoMachineEvidence evidence =
                MultiplayerReleaseReadinessV1.validateTwoMachineCliEvidence(host, client);

        assertTrue(evidence.accepted(), evidence.reason());
        assertEquals("192.168.1.10:46717", evidence.hostAddress());
        assertEquals("192.168.1.10:46717", evidence.clientAddress());
    }

    @Test
    void twoMachineCliEvidenceRejectsWeakClientReport() throws Exception {
        Path dir = Files.createTempDirectory("mp-two-machine-evidence-bad");
        Path host = dir.resolve("host.txt");
        Path client = dir.resolve("client.txt");
        Files.writeString(host, String.join(System.lineSeparator(),
                "passed=true",
                "role=host",
                "address=192.168.1.10:46717",
                "localEndpoint=192.168.1.10:46717",
                "remoteEndpoint=192.168.1.11:52000",
                "connectionId=abc",
                "result=Elimination victory",
                "snapshotsReceived=2",
                "inputAcked=true",
                "returnedToMenu=true",
                ""));
        Files.writeString(client, String.join(System.lineSeparator(),
                "passed=true",
                "role=client",
                "address=192.168.1.10:46717",
                "localEndpoint=192.168.1.11:52000",
                "remoteEndpoint=192.168.1.10:46717",
                "connectionId=abc",
                "result=Elimination victory",
                "snapshotsReceived=1",
                "inputAcked=false",
                "returnedToMenu=true",
                ""));

        MultiplayerReleaseReadinessV1.TwoMachineEvidence evidence =
                MultiplayerReleaseReadinessV1.validateTwoMachineCliEvidence(host, client);

        assertFalse(evidence.accepted());
        assertTrue(evidence.reason().contains("acknowledge"));
    }

    @Test
    void twoMachineCliEvidenceRejectsLoopbackReports() throws Exception {
        Path dir = Files.createTempDirectory("mp-two-machine-loopback");
        Path host = dir.resolve("host.txt");
        Path client = dir.resolve("client.txt");
        Files.writeString(host, String.join(System.lineSeparator(),
                "passed=true",
                "role=host",
                "address=127.0.0.1:46717",
                "localEndpoint=127.0.0.1:46717",
                "remoteEndpoint=127.0.0.1:52000",
                "connectionId=abc",
                "result=Elimination victory",
                "snapshotsReceived=2",
                "inputAcked=true",
                "returnedToMenu=true",
                ""));
        Files.writeString(client, String.join(System.lineSeparator(),
                "passed=true",
                "role=client",
                "address=127.0.0.1:46717",
                "localEndpoint=127.0.0.1:52000",
                "remoteEndpoint=127.0.0.1:46717",
                "connectionId=abc",
                "result=Elimination victory",
                "snapshotsReceived=2",
                "inputAcked=true",
                "returnedToMenu=true",
                ""));

        MultiplayerReleaseReadinessV1.TwoMachineEvidence evidence =
                MultiplayerReleaseReadinessV1.validateTwoMachineCliEvidence(host, client);

        assertFalse(evidence.accepted());
        assertTrue(evidence.reason().contains("Loopback"));
    }

    @Test
    void lanPreflightEvidenceRequiresPassingBindableNonLoopbackReport() throws Exception {
        Path dir = Files.createTempDirectory("mp-lan-preflight-evidence");
        Path preflight = dir.resolve("preflight.txt");
        Files.writeString(preflight, String.join(System.lineSeparator(),
                "passed=true",
                "port=46717",
                "portBindable=true",
                "candidateAddressCount=1",
                "candidateAddress.1=192.168.1.10",
                ""));

        MultiplayerReleaseReadinessV1.LanPreflightEvidence evidence =
                MultiplayerReleaseReadinessV1.validateLanPreflightReport(preflight);

        assertTrue(evidence.accepted(), evidence.reason());
        assertEquals(46717, evidence.port());
        assertEquals(List.of("192.168.1.10"), evidence.candidateAddresses());
    }

    @Test
    void lanPreflightEvidenceRejectsLoopbackCandidateAddress() throws Exception {
        Path dir = Files.createTempDirectory("mp-lan-preflight-loopback");
        Path preflight = dir.resolve("preflight.txt");
        Files.writeString(preflight, String.join(System.lineSeparator(),
                "passed=true",
                "port=46717",
                "portBindable=true",
                "candidateAddressCount=1",
                "candidateAddress.1=127.0.0.1",
                ""));

        MultiplayerReleaseReadinessV1.LanPreflightEvidence evidence =
                MultiplayerReleaseReadinessV1.validateLanPreflightReport(preflight);

        assertFalse(evidence.accepted());
        assertTrue(evidence.reason().contains("loopback"));
    }

    @Test
    void manualAcceptanceEvidenceRequiresScopeAndAllRequiredChecks() throws Exception {
        Path dir = Files.createTempDirectory("mp-manual-acceptance");
        Path twoProcess = dir.resolve("two-process.txt");
        Path report = dir.resolve("interactive.txt");
        Files.writeString(twoProcess, passingTwoProcessReport());
        Files.writeString(report, passingManualReport("interactive-two-process", false,
                twoProcess, null, null, null));

        MultiplayerReleaseReadinessV1.ManualAcceptanceEvidence evidence =
                MultiplayerReleaseReadinessV1.validateManualAcceptanceReport(
                        report, "interactive-two-process", false);

        assertTrue(evidence.accepted(), evidence.reason());
        assertTrue(evidence.missingChecks().isEmpty());
    }

    @Test
    void manualAcceptanceEvidenceRejectsMissingLinkedTwoProcessReport() throws Exception {
        Path dir = Files.createTempDirectory("mp-manual-acceptance-linked-missing");
        Path report = dir.resolve("interactive.txt");
        Files.writeString(report, passingManualReport("interactive-two-process", false,
                dir.resolve("missing-two-process.txt"), null, null, null));

        MultiplayerReleaseReadinessV1.ManualAcceptanceEvidence evidence =
                MultiplayerReleaseReadinessV1.validateManualAcceptanceReport(
                        report, "interactive-two-process", false);

        assertFalse(evidence.accepted());
        assertTrue(evidence.missingChecks().contains("twoProcessReport.accepted"));
    }

    @Test
    void manualAcceptanceEvidenceRejectsIncompleteFinalTwoMachineReport() throws Exception {
        Path dir = Files.createTempDirectory("mp-manual-acceptance-bad");
        Path report = dir.resolve("final.txt");
        Files.writeString(report, String.join(System.lineSeparator(),
                "passed=true",
                "scope=final-two-machine",
                "tester=",
                "build=test-build",
                "date=2026-07-17",
                "hostAddress=127.0.0.1:46717",
                "clientAddress=127.0.0.1",
                "twoProcessReport=" + dir.resolve("missing-two-process.txt"),
                "check.hostJoinReady=true",
                ""));

        MultiplayerReleaseReadinessV1.ManualAcceptanceEvidence evidence =
                MultiplayerReleaseReadinessV1.validateManualAcceptanceReport(
                        report, "final-two-machine", true);

        assertFalse(evidence.accepted());
        assertTrue(evidence.missingChecks().contains("tester"));
        assertTrue(evidence.missingChecks().contains("hostAddress.realLan"));
        assertTrue(evidence.missingChecks().contains("check.shipControl"));
    }

    @Test
    void acceptanceAuditSeparatesAutomatedEvidenceFromExternalManualGates() throws Exception {
        Path dir = Files.createTempDirectory("mp-acceptance-audit");
        Path twoProcess = dir.resolve("two-process.txt");
        Path preflight = dir.resolve("preflight.txt");
        Path host = dir.resolve("host.txt");
        Path client = dir.resolve("client.txt");
        Path interactive = dir.resolve("interactive.txt");
        Path finalManual = dir.resolve("final.txt");
        Path twoMachineLog = dir.resolve("two-machine-log.md");
        Path readiness = dir.resolve("readiness.txt");
        Files.writeString(twoProcess, String.join(System.lineSeparator(),
                "passed=true",
                "hostOk=true",
                "clientOk=true",
                "victoryObserved=true",
                ""));
        Files.writeString(preflight, passingPreflightReport());
        Files.writeString(host, String.join(System.lineSeparator(),
                "passed=true",
                "role=host",
                "address=192.168.1.10:46717",
                "localEndpoint=192.168.1.10:46717",
                "remoteEndpoint=192.168.1.11:52000",
                "connectionId=abc",
                "result=Elimination victory",
                "snapshotsReceived=2",
                "inputAcked=true",
                "returnedToMenu=true",
                ""));
        Files.writeString(client, String.join(System.lineSeparator(),
                "passed=true",
                "role=client",
                "address=192.168.1.10:46717",
                "localEndpoint=192.168.1.11:52000",
                "remoteEndpoint=192.168.1.10:46717",
                "connectionId=abc",
                "result=Elimination victory",
                "snapshotsReceived=2",
                "inputAcked=true",
                "returnedToMenu=true",
                ""));
        Files.writeString(interactive, incompleteManualReport("interactive-two-process", false,
                twoProcess, null, null, null));
        MultiplayerManualAcceptanceReportHarness.writeTemplate(finalManual, "final-two-machine", true);
        MultiplayerTwoMachineAcceptanceLogHarness.writeTemplate(twoMachineLog);
        Files.writeString(readiness, passingReadinessReport());

        MultiplayerReleaseReadinessV1.AcceptanceAudit audit =
                MultiplayerReleaseReadinessV1.auditAcceptanceEvidence(
                        twoProcess, preflight, host, client, interactive, finalManual, twoMachineLog, readiness);

        assertFalse(audit.complete());
        assertEquals(3, audit.missingGates().size());
        assertTrue(audit.gates().get(0).proven(), audit.gates().get(0).reason());
        assertTrue(audit.gates().get(1).proven(), audit.gates().get(1).reason());
        assertTrue(audit.gates().get(2).proven(), audit.gates().get(2).reason());
        assertTrue(audit.gates().get(3).proven(), audit.gates().get(3).reason());
        assertTrue(audit.missingGates().stream().allMatch(MultiplayerReleaseReadinessV1.AcceptanceGate::externalManual));
    }

    @Test
    void readinessReportGateRequiresPassingChecks() throws Exception {
        Path dir = Files.createTempDirectory("mp-readiness-gate");
        Path readiness = dir.resolve("readiness.txt");
        Files.writeString(readiness, String.join(System.lineSeparator(),
                "passed=true",
                "checkCount=2",
                "check.1.name=host port bindable",
                "check.1.passed=true",
                "check.2.name=candidate LAN address",
                "check.2.passed=false",
                ""));

        MultiplayerReleaseReadinessV1.AcceptanceGate gate =
                MultiplayerReleaseReadinessV1.validateTwoMachineReadinessReport(readiness);

        assertFalse(gate.proven());
        assertTrue(gate.reason().contains("candidate LAN address"));
    }

    @Test
    void acceptanceAuditCompletesOnlyWhenManualGatesAreRecorded() throws Exception {
        Path dir = Files.createTempDirectory("mp-acceptance-audit-complete");
        Path twoProcess = dir.resolve("two-process.txt");
        Path preflight = dir.resolve("preflight.txt");
        Path host = dir.resolve("host.txt");
        Path client = dir.resolve("client.txt");
        Path interactive = dir.resolve("interactive.txt");
        Path finalManual = dir.resolve("final.txt");
        Path twoMachineLog = dir.resolve("two-machine-log.md");
        Path readiness = dir.resolve("readiness.txt");
        Files.writeString(twoProcess, String.join(System.lineSeparator(),
                "passed=true",
                "hostOk=true",
                "clientOk=true",
                "victoryObserved=true",
                ""));
        Files.writeString(preflight, passingPreflightReport());
        Files.writeString(host, String.join(System.lineSeparator(),
                "passed=true",
                "role=host",
                "address=192.168.1.10:46717",
                "localEndpoint=192.168.1.10:46717",
                "remoteEndpoint=192.168.1.11:52000",
                "connectionId=abc",
                "result=Elimination victory",
                "snapshotsReceived=2",
                "inputAcked=true",
                "returnedToMenu=true",
                ""));
        Files.writeString(client, String.join(System.lineSeparator(),
                "passed=true",
                "role=client",
                "address=192.168.1.10:46717",
                "localEndpoint=192.168.1.11:52000",
                "remoteEndpoint=192.168.1.10:46717",
                "connectionId=abc",
                "result=Elimination victory",
                "snapshotsReceived=2",
                "inputAcked=true",
                "returnedToMenu=true",
                ""));
        Files.writeString(interactive, passingManualReport("interactive-two-process", false,
                twoProcess, null, null, null));
        Files.writeString(finalManual, passingManualReport("final-two-machine", true,
                twoProcess, preflight, host, client));
        Files.writeString(twoMachineLog, passingTwoMachineLog());
        Files.writeString(readiness, passingReadinessReport());

        MultiplayerReleaseReadinessV1.AcceptanceAudit audit =
                MultiplayerReleaseReadinessV1.auditAcceptanceEvidence(
                        twoProcess, preflight, host, client, interactive, finalManual, twoMachineLog, readiness);

        assertTrue(audit.complete());
        assertTrue(audit.missingGates().isEmpty());
    }

    @Test
    void acceptanceAuditCompletesWithManualEvidenceReports() throws Exception {
        Path dir = Files.createTempDirectory("mp-acceptance-audit-manual-complete");
        Path twoProcess = dir.resolve("two-process.txt");
        Path preflight = dir.resolve("preflight.txt");
        Path host = dir.resolve("host.txt");
        Path client = dir.resolve("client.txt");
        Path interactive = dir.resolve("interactive.txt");
        Path finalManual = dir.resolve("final.txt");
        Path twoMachineLog = dir.resolve("two-machine-log.md");
        Path readiness = dir.resolve("readiness.txt");
        Files.writeString(twoProcess, String.join(System.lineSeparator(),
                "passed=true",
                "hostOk=true",
                "clientOk=true",
                "victoryObserved=true",
                ""));
        Files.writeString(preflight, passingPreflightReport());
        Files.writeString(host, passingLanReport("host"));
        Files.writeString(client, passingLanReport("client"));
        Files.writeString(interactive, passingManualReport("interactive-two-process", false,
                twoProcess, null, null, null));
        Files.writeString(finalManual, passingManualReport("final-two-machine", true,
                twoProcess, preflight, host, client));
        Files.writeString(twoMachineLog, passingTwoMachineLog());
        Files.writeString(readiness, passingReadinessReport());

        MultiplayerReleaseReadinessV1.AcceptanceAudit audit =
                MultiplayerReleaseReadinessV1.auditAcceptanceEvidence(
                        twoProcess, preflight, host, client, interactive, finalManual, twoMachineLog, readiness);

        assertTrue(audit.complete());
        assertTrue(audit.missingGates().isEmpty());
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

    private static String passingTwoProcessReport() {
        return String.join(System.lineSeparator(),
                "passed=true",
                "hostOk=true",
                "clientOk=true",
                "victoryObserved=true",
                "");
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

    private static String passingTwoMachineLog() {
        return MultiplayerTwoMachineAcceptanceLogHarness.templateText()
                .replace("- Build/version:", "- Build/version: 1.0.1.2")
                .replace("- Commit or packaged build ID:", "- Commit or packaged build ID: local-test")
                .replace("- Host machine OS / CPU / RAM:", "- Host machine OS / CPU / RAM: Windows / CPU / 16GB")
                .replace("- Client machine OS / CPU / RAM:", "- Client machine OS / CPU / RAM: Windows / CPU / 16GB")
                .replace("- Network type:", "- Network type: wired")
                .replace("- Host LAN address and port:", "- Host LAN address and port: 192.168.1.10:46717")
                .replace("- Client LAN address:", "- Client LAN address: 192.168.1.11")
                .replace("- Firewall rule created or confirmed:", "- Firewall rule created or confirmed: yes")
                .replace("- Two-machine runbook directory:", "- Two-machine runbook directory: build/reports/run")
                .replace("- Host preflight report path:", "- Host preflight report path: preflight.txt")
                .replace("- Host preflight candidate address used:", "- Host preflight candidate address used: 192.168.1.10")
                .replace("- Host CLI report path:", "- Host CLI report path: host.txt")
                .replace("- Client CLI report path:", "- Client CLI report path: client.txt")
                .replace("- Host observed client endpoint:", "- Host observed client endpoint: 192.168.1.11:52000")
                .replace("- Client reported local endpoint:", "- Client reported local endpoint: 192.168.1.11:52000")
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

    private static String passingReadinessReport() {
        return String.join(System.lineSeparator(),
                "passed=true",
                "checkCount=3",
                "check.1.name=host port bindable",
                "check.1.passed=true",
                "check.2.name=candidate LAN address",
                "check.2.passed=true",
                "check.3.name=runbook README",
                "check.3.passed=true",
                "");
    }

    private static String passingManualReport(String scope,
                                              boolean realLan,
                                              Path twoProcess,
                                              Path preflight,
                                              Path host,
                                              Path client) {
        return manualReport(scope, realLan, true, twoProcess, preflight, host, client);
    }

    private static String incompleteManualReport(String scope,
                                                 boolean realLan,
                                                 Path twoProcess,
                                                 Path preflight,
                                                 Path host,
                                                 Path client) {
        return manualReport(scope, realLan, false, twoProcess, preflight, host, client);
    }

    private static String manualReport(String scope,
                                       boolean realLan,
                                       boolean checksPassed,
                                       Path twoProcess,
                                       Path preflight,
                                       Path host,
                                       Path client) {
        ArrayList<String> lines = new ArrayList<>();
        lines.add("passed=" + checksPassed);
        lines.add("scope=" + scope);
        lines.add("tester=QA");
        lines.add("build=test-build");
        lines.add("date=2026-07-17");
        lines.add("hostAddress=" + (realLan ? "192.168.1.10:46717" : "127.0.0.1:46717"));
        lines.add("clientAddress=" + (realLan ? "192.168.1.11" : "127.0.0.1"));
        lines.add("twoProcessReport=" + (twoProcess == null ? "" : twoProcess));
        lines.add("preflightReport=" + (preflight == null ? "" : preflight));
        lines.add("hostReport=" + (host == null ? "" : host));
        lines.add("clientReport=" + (client == null ? "" : client));
        for (String check : MultiplayerReleaseReadinessV1.requiredManualAcceptanceChecks()) {
            lines.add(check + "=" + checksPassed);
        }
        lines.add("");
        return String.join(System.lineSeparator(), lines);
    }
}
