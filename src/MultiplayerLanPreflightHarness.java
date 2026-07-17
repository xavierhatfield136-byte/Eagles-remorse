import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** LAN host preflight for the V1 two-machine multiplayer acceptance run. */
public final class MultiplayerLanPreflightHarness {
    public record PreflightReport(int port,
                                  boolean portBindable,
                                  List<String> candidateAddresses,
                                  List<String> warnings) {
        public PreflightReport {
            port = Math.max(0, port);
            candidateAddresses = candidateAddresses == null ? List.of() : List.copyOf(candidateAddresses);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }

        public boolean passed() {
            return portBindable && !candidateAddresses.isEmpty();
        }

        public String toText() {
            ArrayList<String> lines = new ArrayList<>();
            lines.add("passed=" + passed());
            lines.add("port=" + port);
            lines.add("portBindable=" + portBindable);
            lines.add("candidateAddressCount=" + candidateAddresses.size());
            for (int i = 0; i < candidateAddresses.size(); i++) {
                lines.add("candidateAddress." + (i + 1) + "=" + candidateAddresses.get(i));
            }
            lines.add("warningCount=" + warnings.size());
            for (int i = 0; i < warnings.size(); i++) {
                lines.add("warning." + (i + 1) + "=" + warnings.get(i));
            }
            lines.add("timestamp=" + Instant.now());
            return String.join(System.lineSeparator(), lines);
        }
    }

    private MultiplayerLanPreflightHarness() {}

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parseArgs(args);
        int port = parseInt(options.getOrDefault("port", String.valueOf(MultiplayerLanTransportV1.DEFAULT_PORT)));
        Path reportPath = Path.of(options.getOrDefault(
                "report", "build/reports/multiplayer-lan-preflight.txt"));
        boolean strict = Boolean.parseBoolean(options.getOrDefault("strict", "false"));

        PreflightReport report = run(port);
        writeReport(reportPath, report);
        System.out.println(report.toText());
        if (strict && !report.passed()) {
            throw new IllegalStateException("LAN preflight failed");
        }
    }

    public static PreflightReport run(int port) {
        return evaluate(port, candidateLanAddresses(), canBindPort(port));
    }

    public static PreflightReport evaluate(int port, List<String> candidateAddresses, boolean portBindable) {
        ArrayList<String> warnings = new ArrayList<>();
        if (candidateAddresses == null || candidateAddresses.isEmpty()) {
            warnings.add("No non-loopback IPv4 LAN address was found; two-machine clients need the host LAN IP.");
        }
        if (!portBindable) {
            warnings.add("Port " + Math.max(0, port) + " could not be bound; close the conflicting process or choose another port.");
        }
        warnings.add("Firewall rules may still block inbound TCP even when local bind succeeds.");
        warnings.add("V1 supports direct LAN/manual address only; NAT traversal, relay, discovery, and internet hosting are unsupported.");
        return new PreflightReport(port, portBindable,
                candidateAddresses == null ? List.of() : candidateAddresses, warnings);
    }

    public static List<String> candidateLanAddresses() {
        ArrayList<String> addresses = new ArrayList<>();
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            interfaces.sort((a, b) -> safeName(a).compareToIgnoreCase(safeName(b)));
            for (NetworkInterface network : interfaces) {
                if (network == null || !network.isUp() || network.isLoopback() || network.isVirtual()) continue;
                for (InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (!(address instanceof Inet4Address)) continue;
                    if (address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isAnyLocalAddress()) continue;
                    addresses.add(address.getHostAddress());
                }
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return List.copyOf(addresses);
    }

    public static boolean canBindPort(int port) {
        if (port <= 0 || port > 65_535) return false;
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress("0.0.0.0", port));
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static void writeReport(Path path, PreflightReport report) throws Exception {
        Path absolute = path.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(absolute, report.toText() + System.lineSeparator(), StandardCharsets.UTF_8);
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

    private static String safeName(NetworkInterface network) {
        if (network == null || network.getName() == null) return "";
        return network.getName();
    }
}
