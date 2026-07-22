import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerEvidenceBundleHarnessTest {

    @AfterEach
    void clearFlagOverride() {
        System.clearProperty("game.feature.multiplayer_custom_battle");
        System.clearProperty("game.feature.multiplayer_custom_missions");
    }

    @Test
    void evidenceFilesRecordExistenceSizeAndSha256() throws Exception {
        Path dir = Files.createTempDirectory("mp-evidence-files");
        Path report = dir.resolve("report.txt");
        Files.writeString(report, "passed=true\n");

        List<MultiplayerEvidenceBundleHarness.EvidenceFile> files =
                MultiplayerEvidenceBundleHarness.evidenceFiles(
                        report, dir.resolve("missing-preflight.txt"), report,
                        report, report, report, report, report, report);

        assertTrue(files.get(0).exists());
        assertEquals(Files.size(report), files.get(0).bytes());
        assertEquals(sha256("passed=true\n"), files.get(0).sha256());
        assertTrue(files.stream().anyMatch(file -> !file.exists()));
    }

    @Test
    void mainWritesBundleWithArtifactsAndGateStatus() throws Exception {
        System.setProperty("game.feature.multiplayer_custom_battle", "false");
        System.setProperty("game.feature.multiplayer_custom_missions", "false");
        Path dir = Files.createTempDirectory("mp-evidence-bundle");
        Path twoProcess = dir.resolve("two-process.txt");
        Path preflight = dir.resolve("preflight.txt");
        Path host = dir.resolve("host.txt");
        Path client = dir.resolve("client.txt");
        Path interactive = dir.resolve("interactive.txt");
        Path finalManual = dir.resolve("final.txt");
        Path audit = dir.resolve("audit.txt");
        Path readiness = dir.resolve("readiness.txt");
        Path twoMachineLog = dir.resolve("two-machine-log.md");
        Path bundle = dir.resolve("bundle.txt");
        Files.writeString(twoProcess, passingTwoProcessReport());
        Files.writeString(preflight, passingPreflightReport());
        Files.writeString(host, passingLanReport("host"));
        Files.writeString(client, passingLanReport("client"));
        Files.writeString(interactive, incompleteManualReport("interactive-two-process"));
        Files.writeString(finalManual, incompleteManualReport("final-two-machine"));
        Files.writeString(audit, "complete=false\n");
        Files.writeString(readiness, "passed=true\n");
        Files.writeString(twoMachineLog, "accepted=true\n");

        MultiplayerEvidenceBundleHarness.main(new String[]{
                "--two-process-report=" + twoProcess,
                "--preflight-report=" + preflight,
                "--host-report=" + host,
                "--client-report=" + client,
                "--interactive-report=" + interactive,
                "--final-report=" + finalManual,
                "--audit-report=" + audit,
                "--readiness-report=" + readiness,
                "--two-machine-log=" + twoMachineLog,
                "--report=" + bundle
        });

        String text = Files.readString(bundle);
        MultiplayerProtocolV1.CompatibilityFingerprint fingerprint =
                MultiplayerProtocolV1.localFingerprint();
        MultiplayerProtocolV1.ContentManifest manifest = fingerprint.manifest();
        assertTrue(text.contains("protocolVersion=" + fingerprint.protocolVersion()));
        assertTrue(text.contains("gameBuild=" + fingerprint.gameBuild()));
        assertTrue(text.contains("manifest.rulesHash=" + manifest.rulesHash()));
        assertTrue(text.contains("manifest.requiredAssetsHash=" + manifest.requiredAssetsHash()));
        assertTrue(text.contains("artifact.1.name=twoProcessReport"));
        assertTrue(text.contains("artifact.8.name=twoMachineReadinessReport"));
        assertTrue(text.contains("artifact.9.name=twoMachineAcceptanceLog"));
        assertTrue(text.contains("artifact.1.sha256=" + sha256(passingTwoProcessReport())));
        assertTrue(text.contains("featureEnabled=false"));
        assertTrue(text.contains("releaseAllowed=true"));
        assertTrue(text.contains("gate.1.name=automated two-process TCP duel"));
        assertTrue(text.contains("missingGates="));
    }

    private static String passingTwoProcessReport() {
        return String.join(System.lineSeparator(),
                "passed=true",
                "hostOk=true",
                "clientOk=true",
                "victoryObserved=true",
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

    private static String incompleteManualReport(String scope) {
        return String.join(System.lineSeparator(),
                "passed=false",
                "scope=" + scope,
                "tester=",
                "build=test-build",
                "date=2026-07-17",
                "");
    }

    private static String sha256(String text) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
