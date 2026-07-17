import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class MultiplayerTwoProcessSmokeHarnessTest {

    @Test
    void launchesSeparateHostAndClientProcessesForLoopbackDuel() throws Exception {
        Path coordDir = Files.createTempDirectory("mp-two-process-smoke");
        String javaExe = Path.of(System.getProperty("java.home"), "bin",
                isWindows() ? "java.exe" : "java").toString();
        String classPath = System.getProperty("java.class.path");

        Process host = new ProcessBuilder(List.of(
                javaExe, "-cp", classPath,
                "MultiplayerTwoProcessSmokeHarness", "host", coordDir.toString()))
                .redirectErrorStream(true)
                .start();
        Process client = new ProcessBuilder(List.of(
                javaExe, "-cp", classPath,
                "MultiplayerTwoProcessSmokeHarness", "client", coordDir.toString()))
                .redirectErrorStream(true)
                .start();

        boolean clientDone = client.waitFor(10, TimeUnit.SECONDS);
        boolean hostDone = host.waitFor(10, TimeUnit.SECONDS);

        String clientOut = readAll(client.getInputStream());
        String hostOut = readAll(host.getInputStream());
        if (!clientDone || !hostDone) {
            client.destroyForcibly();
            host.destroyForcibly();
            fail("two-process smoke timed out\nhost=" + hostOut + "\nclient=" + clientOut);
        }

        assertEquals(0, host.exitValue(), hostOut);
        assertEquals(0, client.exitValue(), clientOut);
        assertTrue(hostOut.contains("HOST_OK"), hostOut);
        assertTrue(clientOut.contains("CLIENT_OK"), clientOut);
        String hostDoneFile = Files.readString(coordDir.resolve("host.done"), StandardCharsets.UTF_8);
        String clientDoneFile = Files.readString(coordDir.resolve("client.done"), StandardCharsets.UTF_8);
        assertTrue(hostDoneFile.startsWith("HOST_OK"));
        assertTrue(clientDoneFile.startsWith("CLIENT_OK"));
        assertTrue(hostDoneFile.contains("Elimination victory"));
        assertTrue(clientDoneFile.contains("Elimination victory"));
    }

    @Test
    void twoProcessSmokeCompletesWithinAcceptanceBudget() {
        assertTrue(Duration.ofSeconds(10).toMillis() >= 10_000L);
    }

    private static String readAll(InputStream input) throws Exception {
        return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
