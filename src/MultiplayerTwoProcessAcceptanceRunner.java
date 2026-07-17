import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.TimeUnit;

/** Standalone report-producing launcher for the V1 two-process loopback acceptance duel. */
public final class MultiplayerTwoProcessAcceptanceRunner {
    public record Report(boolean passed,
                         int hostExitCode,
                         int clientExitCode,
                         boolean hostOk,
                         boolean clientOk,
                         boolean victoryObserved,
                         long durationMs,
                         String reportPath,
                         String failureReason) {
        public Report {
            hostExitCode = Math.max(-1, hostExitCode);
            clientExitCode = Math.max(-1, clientExitCode);
            durationMs = Math.max(0L, durationMs);
            reportPath = clean(reportPath);
            failureReason = clean(failureReason);
        }

        public String toText(String hostOutput, String clientOutput, String hostDone, String clientDone) {
            return String.join(System.lineSeparator(),
                    "passed=" + passed,
                    "hostExitCode=" + hostExitCode,
                    "clientExitCode=" + clientExitCode,
                    "hostOk=" + hostOk,
                    "clientOk=" + clientOk,
                    "victoryObserved=" + victoryObserved,
                    "durationMs=" + durationMs,
                    "reportPath=" + reportPath,
                    "failureReason=" + failureReason,
                    "hostDone=" + clean(hostDone),
                    "clientDone=" + clean(clientDone),
                    "hostOutput=" + oneLine(hostOutput),
                    "clientOutput=" + oneLine(clientOutput),
                    "timestamp=" + Instant.now());
        }
    }

    private MultiplayerTwoProcessAcceptanceRunner() {}

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parseArgs(args);
        Path reportPath = Path.of(options.getOrDefault(
                "report", "build/reports/multiplayer-two-process-acceptance.txt"));
        int timeoutMs = parseInt(options.getOrDefault("timeout-ms", "15000"));
        Report report = run(reportPath, timeoutMs);
        System.out.println("passed=" + report.passed());
        System.out.println("reportPath=" + report.reportPath());
        if (!report.passed()) {
            throw new IllegalStateException(report.failureReason());
        }
    }

    public static Report run(Path reportPath, int timeoutMs) throws Exception {
        long start = System.nanoTime();
        Path coordDir = Files.createTempDirectory("mp-two-process-acceptance");
        Path absoluteReport = reportPath.toAbsolutePath().normalize();
        String hostOutput = "";
        String clientOutput = "";
        String hostDone = "";
        String clientDone = "";
        int hostExit = -1;
        int clientExit = -1;
        boolean timedOut = false;
        Process host = null;
        Process client = null;
        try {
            String javaExe = Path.of(System.getProperty("java.home"), "bin",
                    isWindows() ? "java.exe" : "java").toString();
            String classPath = System.getProperty("java.class.path");
            host = new ProcessBuilder(List.of(
                    javaExe, "-cp", classPath,
                    "MultiplayerTwoProcessSmokeHarness", "host", coordDir.toString()))
                    .redirectErrorStream(true)
                    .start();
            client = new ProcessBuilder(List.of(
                    javaExe, "-cp", classPath,
                    "MultiplayerTwoProcessSmokeHarness", "client", coordDir.toString()))
                    .redirectErrorStream(true)
                    .start();

            int waitMs = Math.max(1, timeoutMs);
            boolean clientDoneProcess = client.waitFor(waitMs, TimeUnit.MILLISECONDS);
            boolean hostDoneProcess = host.waitFor(waitMs, TimeUnit.MILLISECONDS);
            timedOut = !clientDoneProcess || !hostDoneProcess;
            if (timedOut) {
                if (!clientDoneProcess) client.destroyForcibly();
                if (!hostDoneProcess) host.destroyForcibly();
            }
            clientOutput = readAll(client.getInputStream());
            hostOutput = readAll(host.getInputStream());
            hostExit = hostDoneProcess ? host.exitValue() : -1;
            clientExit = clientDoneProcess ? client.exitValue() : -1;
            if (Files.isRegularFile(coordDir.resolve("host.done"))) {
                hostDone = Files.readString(coordDir.resolve("host.done"), StandardCharsets.UTF_8);
            }
            if (Files.isRegularFile(coordDir.resolve("client.done"))) {
                clientDone = Files.readString(coordDir.resolve("client.done"), StandardCharsets.UTF_8);
            }
        } finally {
            if (host != null && host.isAlive()) host.destroyForcibly();
            if (client != null && client.isAlive()) client.destroyForcibly();
        }

        boolean hostOk = hostExit == 0 && hostOutput.contains("HOST_OK") && hostDone.startsWith("HOST_OK");
        boolean clientOk = clientExit == 0 && clientOutput.contains("CLIENT_OK") && clientDone.startsWith("CLIENT_OK");
        boolean victory = hostDone.contains("Elimination victory") && clientDone.contains("Elimination victory");
        String failure = "";
        if (timedOut) failure = "Two-process acceptance timed out";
        else if (!hostOk) failure = "Host process did not complete acceptance";
        else if (!clientOk) failure = "Client process did not complete acceptance";
        else if (!victory) failure = "Two-process acceptance did not observe victory";
        boolean passed = failure.isBlank();
        long durationMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
        Report report = new Report(passed, hostExit, clientExit, hostOk, clientOk, victory,
                durationMs, absoluteReport.toString(), failure);
        writeReport(absoluteReport, report, hostOutput, clientOutput, hostDone, clientDone);
        return report;
    }

    private static void writeReport(Path path, Report report, String hostOutput,
                                    String clientOutput, String hostDone, String clientDone) throws Exception {
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(path, report.toText(hostOutput, clientOutput, hostDone, clientDone)
                + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    private static String readAll(InputStream input) throws Exception {
        return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static Map<String, String> parseArgs(String[] args) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        for (String arg : args) {
            String text = arg == null ? "" : arg.trim();
            if (!text.startsWith("--")) continue;
            int eq = text.indexOf('=');
            if (eq > 2) out.put(text.substring(2, eq), text.substring(eq + 1));
            else out.put(text.substring(2), "true");
        }
        return out;
    }

    private static int parseInt(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid integer: " + text);
        }
    }

    private static String clean(String text) {
        return text == null ? "" : text.trim();
    }

    private static String oneLine(String text) {
        return clean(text).replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
