import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Release-readiness helpers for V1 multiplayer diagnostics and acceptance gates. */
public final class MultiplayerReleaseReadinessV1 {
    private MultiplayerReleaseReadinessV1() {}

    public record DebugInfo(int protocolVersion,
                            String gameBuild,
                            MultiplayerProtocolV1.ContentManifest manifest,
                            boolean featureFlagEnabled) {}

    public record FailedConnectTelemetry(boolean enabled,
                                         String matchId,
                                         String reason,
                                         String redactedRemoteAddress,
                                         int protocolVersion,
                                         String gameBuild) {
        public FailedConnectTelemetry {
            matchId = clean(matchId);
            reason = clean(reason);
            redactedRemoteAddress = clean(redactedRemoteAddress);
            gameBuild = clean(gameBuild);
        }
    }

    public record TwoMachineEvidence(boolean accepted,
                                     String reason,
                                     String hostAddress,
                                     String clientAddress,
                                     String hostLocalEndpoint,
                                     String hostRemoteEndpoint,
                                     String clientLocalEndpoint,
                                     String clientRemoteEndpoint,
                                     String hostConnectionId,
                                     String clientConnectionId) {
        public TwoMachineEvidence {
            reason = clean(reason);
            hostAddress = clean(hostAddress);
            clientAddress = clean(clientAddress);
            hostLocalEndpoint = clean(hostLocalEndpoint);
            hostRemoteEndpoint = clean(hostRemoteEndpoint);
            clientLocalEndpoint = clean(clientLocalEndpoint);
            clientRemoteEndpoint = clean(clientRemoteEndpoint);
            hostConnectionId = clean(hostConnectionId);
            clientConnectionId = clean(clientConnectionId);
        }
    }

    public record AcceptanceGate(String name,
                                 boolean proven,
                                 boolean externalManual,
                                 String reason) {
        public AcceptanceGate {
            name = clean(name);
            reason = clean(reason);
        }
    }

    public record LanPreflightEvidence(boolean accepted,
                                       String reason,
                                       int port,
                                       List<String> candidateAddresses) {
        public LanPreflightEvidence {
            reason = clean(reason);
            candidateAddresses = candidateAddresses == null ? List.of() : List.copyOf(candidateAddresses);
        }
    }

    public record ManualAcceptanceEvidence(boolean accepted,
                                           String reason,
                                           String scope,
                                           List<String> missingChecks) {
        public ManualAcceptanceEvidence {
            reason = clean(reason);
            scope = clean(scope);
            missingChecks = missingChecks == null ? List.of() : List.copyOf(missingChecks);
        }
    }

    public record AcceptanceAudit(List<AcceptanceGate> gates) {
        public AcceptanceAudit {
            gates = gates == null ? List.of() : List.copyOf(gates);
        }

        public boolean complete() {
            for (AcceptanceGate gate : gates) {
                if (gate != null && !gate.proven()) return false;
            }
            return !gates.isEmpty();
        }

        public List<AcceptanceGate> missingGates() {
            ArrayList<AcceptanceGate> out = new ArrayList<>();
            for (AcceptanceGate gate : gates) {
                if (gate != null && !gate.proven()) out.add(gate);
            }
            return List.copyOf(out);
        }
    }

    public record MultiplayerReleaseGate(boolean allowed,
                                         boolean featureEnabled,
                                         boolean acceptanceComplete,
                                         String reason,
                                         List<AcceptanceGate> gates) {
        public MultiplayerReleaseGate {
            reason = clean(reason);
            gates = gates == null ? List.of() : List.copyOf(gates);
        }
    }

    public static DebugInfo debugInfo() {
        MultiplayerProtocolV1.CompatibilityFingerprint fingerprint =
                MultiplayerProtocolV1.localFingerprint();
        return new DebugInfo(fingerprint.protocolVersion(), fingerprint.gameBuild(),
                fingerprint.manifest(), MultiplayerRulesV1.entryPointEnabled());
    }

    public static String stateHash(MultiplayerBattleSnapshot snapshot) {
        byte[] bytes = MultiplayerSerializationV1.encodeSnapshot(snapshot);
        long hash = 0xcbf29ce484222325L;
        for (byte b : bytes) {
            hash ^= (b & 0xff);
            hash *= 0x100000001b3L;
        }
        return Long.toUnsignedString(hash, 16);
    }

    public static boolean reconstructedStateHashMatches(MultiplayerBattleSnapshot snapshot) {
        byte[] encoded = MultiplayerSerializationV1.encodeSnapshot(snapshot);
        MultiplayerBattleSnapshot decoded = MultiplayerSerializationV1.decodeSnapshot(encoded);
        return stateHash(snapshot).equals(stateHash(decoded));
    }

    public static FailedConnectTelemetry failedConnectTelemetry(boolean telemetryEnabled,
                                                               String matchId,
                                                               String reason,
                                                               String remoteAddress) {
        MultiplayerSecurityV1 security = new MultiplayerSecurityV1();
        MultiplayerProtocolV1.CompatibilityFingerprint fingerprint =
                MultiplayerProtocolV1.localFingerprint();
        return new FailedConnectTelemetry(
                telemetryEnabled,
                matchId,
                reason,
                security.redactAddressForLog(remoteAddress),
                fingerprint.protocolVersion(),
                fingerprint.gameBuild());
    }

    public static List<String> knownLimitations() {
        ArrayList<String> out = new ArrayList<>();
        out.add("Custom-battle multiplayer only; campaign multiplayer is unsupported.");
        out.add("Two-player duel: one directly controlled ship per player.");
        out.add("Direct LAN/manual address only; no discovery, relay, NAT traversal, or internet hosting claim.");
        out.add("No reconnect, host migration, mid-match join, respawn, pause, same-team co-op, AI fleets, fog-of-war replication, superweapons, or battlefield warp in V1.");
        return List.copyOf(out);
    }

    public static boolean featureFlagDisablesEntryPoints() {
        return !MultiplayerRulesV1.entryPointEnabled();
    }

    public static boolean crashSafeCleanup(MultiplayerMatchCleanupScope scope) {
        if (scope == null) return false;
        scope.close();
        return scope.closed();
    }

    public static TwoMachineEvidence validateTwoMachineCliEvidence(Path hostReport, Path clientReport) {
        try {
            Map<String, String> host = readReport(hostReport);
            Map<String, String> client = readReport(clientReport);
            if (!"true".equalsIgnoreCase(host.getOrDefault("passed", ""))) {
                return evidence(false, "Host report did not pass", host, client);
            }
            if (!"true".equalsIgnoreCase(client.getOrDefault("passed", ""))) {
                return evidence(false, "Client report did not pass", host, client);
            }
            if (!"host".equalsIgnoreCase(host.getOrDefault("role", ""))) {
                return evidence(false, "Host report role is not host", host, client);
            }
            if (!"client".equalsIgnoreCase(client.getOrDefault("role", ""))) {
                return evidence(false, "Client report role is not client", host, client);
            }
            if (!host.getOrDefault("result", "").contains("Elimination victory")
                    || !client.getOrDefault("result", "").contains("Elimination victory")) {
                return evidence(false, "Reports do not both show elimination victory", host, client);
            }
            if (!"true".equalsIgnoreCase(client.getOrDefault("inputAcked", ""))) {
                return evidence(false, "Client report did not acknowledge input", host, client);
            }
            if (!"true".equalsIgnoreCase(host.getOrDefault("returnedToMenu", ""))
                    || !"true".equalsIgnoreCase(client.getOrDefault("returnedToMenu", ""))) {
                return evidence(false, "Reports do not show clean return-to-menu/disconnect", host, client);
            }
            if (parseNonNegativeInt(client.getOrDefault("snapshotsReceived", "0")) < 2) {
                return evidence(false, "Client report has too few snapshots", host, client);
            }
            if (host.getOrDefault("address", "").isBlank()
                    || client.getOrDefault("address", "").isBlank()) {
                return evidence(false, "Reports are missing LAN address evidence", host, client);
            }
            String hostLocal = firstUsableEndpoint(host.get("localEndpoint"), host.get("address"));
            String hostRemote = host.getOrDefault("remoteEndpoint", "");
            String clientLocal = client.getOrDefault("localEndpoint", "");
            String clientRemote = firstNonBlank(client.get("remoteEndpoint"), client.get("address"));
            if (isLoopbackEvidenceAddress(hostLocal)
                    || isLoopbackEvidenceAddress(hostRemote)
                    || isLoopbackEvidenceAddress(clientLocal)
                    || isLoopbackEvidenceAddress(clientRemote)) {
                return evidence(false, "Loopback reports do not prove a two-machine LAN pass", host, client);
            }
            if (hostRemote.isBlank() || clientLocal.isBlank()) {
                return evidence(false, "Reports are missing socket endpoint evidence", host, client);
            }
            if (!endpointHost(hostRemote).equals(endpointHost(clientLocal))) {
                return evidence(false, "Host-observed client endpoint does not match client local endpoint", host, client);
            }
            return evidence(true, "Two-machine CLI evidence accepted", host, client);
        } catch (Exception ex) {
            return new TwoMachineEvidence(false,
                    ex.getMessage() == null ? "Missing or malformed evidence report" : ex.getMessage(),
                    "", "", "", "", "", "", "", "");
        }
    }

    public static AcceptanceGate validateTwoProcessReport(Path twoProcessReport) {
        try {
            Map<String, String> report = readReport(twoProcessReport);
            if (!"true".equalsIgnoreCase(report.getOrDefault("passed", ""))) {
                return new AcceptanceGate("automated two-process TCP duel", false, false,
                        "Two-process report did not pass");
            }
            if (!"true".equalsIgnoreCase(report.getOrDefault("hostOk", ""))) {
                return new AcceptanceGate("automated two-process TCP duel", false, false,
                        "Host process did not complete");
            }
            if (!"true".equalsIgnoreCase(report.getOrDefault("clientOk", ""))) {
                return new AcceptanceGate("automated two-process TCP duel", false, false,
                        "Client process did not complete");
            }
            if (!"true".equalsIgnoreCase(report.getOrDefault("victoryObserved", ""))) {
                return new AcceptanceGate("automated two-process TCP duel", false, false,
                        "Victory was not observed by both processes");
            }
            return new AcceptanceGate("automated two-process TCP duel", true, false,
                    "Two-process report accepted");
        } catch (Exception ex) {
            return new AcceptanceGate("automated two-process TCP duel", false, false,
                    ex.getMessage() == null ? "Missing or malformed two-process report" : ex.getMessage());
        }
    }

    public static LanPreflightEvidence validateLanPreflightReport(Path preflightReport) {
        try {
            Map<String, String> report = readReport(preflightReport);
            int port = parseNonNegativeInt(report.getOrDefault("port", "0"));
            List<String> addresses = numberedValues(report, "candidateAddress.");
            if (!"true".equalsIgnoreCase(report.getOrDefault("passed", ""))) {
                return new LanPreflightEvidence(false, "Preflight report did not pass", port, addresses);
            }
            if (!"true".equalsIgnoreCase(report.getOrDefault("portBindable", ""))) {
                return new LanPreflightEvidence(false, "Preflight report says the host port is not bindable", port, addresses);
            }
            if (port <= 0 || port > 65_535) {
                return new LanPreflightEvidence(false, "Preflight report has an invalid host port", port, addresses);
            }
            if (addresses.isEmpty()) {
                return new LanPreflightEvidence(false, "Preflight report has no candidate LAN address", port, addresses);
            }
            for (String address : addresses) {
                if (isLoopbackEvidenceAddress(address)) {
                    return new LanPreflightEvidence(false, "Preflight report includes loopback address evidence", port, addresses);
                }
            }
            return new LanPreflightEvidence(true, "LAN host preflight accepted", port, addresses);
        } catch (Exception ex) {
            return new LanPreflightEvidence(false,
                    ex.getMessage() == null ? "Missing or malformed preflight report" : ex.getMessage(),
                    0, List.of());
        }
    }

    public static ManualAcceptanceEvidence validateManualAcceptanceReport(Path reportPath,
                                                                         String expectedScope,
                                                                         boolean requireRealLanAddresses) {
        try {
            Map<String, String> report = readReport(reportPath);
            String scope = report.getOrDefault("scope", "");
            ArrayList<String> missing = new ArrayList<>();
            boolean passed = "true".equalsIgnoreCase(report.getOrDefault("passed", ""));
            boolean scopeMatches = clean(expectedScope).equalsIgnoreCase(clean(scope));
            if (!passed) missing.add("passed");
            if (!scopeMatches) missing.add("scope");
            requireNonBlank(report, missing, "tester");
            requireNonBlank(report, missing, "build");
            requireNonBlank(report, missing, "date");
            for (String check : requiredManualAcceptanceChecks()) {
                if (!"true".equalsIgnoreCase(report.getOrDefault(check, ""))) {
                    missing.add(check);
                }
            }
            if (requireRealLanAddresses) {
                String hostAddress = report.getOrDefault("hostAddress", "");
                String clientAddress = report.getOrDefault("clientAddress", "");
                if (isLoopbackEvidenceAddress(hostAddress)) missing.add("hostAddress.realLan");
                if (isLoopbackEvidenceAddress(clientAddress)) missing.add("clientAddress.realLan");
            }
            validateLinkedManualEvidence(reportPath, report, missing, requireRealLanAddresses);
            if (!missing.isEmpty()) {
                String reason = !passed
                        ? "Manual report did not pass"
                        : !scopeMatches
                        ? "Manual report scope mismatch"
                        : "Manual report is missing required evidence";
                return manualEvidence(false, reason, scope, missing);
            }
            return manualEvidence(true, "Manual report accepted", scope, List.of());
        } catch (Exception ex) {
            return manualEvidence(false,
                    ex.getMessage() == null ? "Missing or malformed manual acceptance report" : ex.getMessage(),
                    "", List.of());
        }
    }

    public static List<String> requiredManualAcceptanceChecks() {
        return List.of(
                "check.hostJoinReady",
                "check.shipControl",
                "check.inputReplication",
                "check.snapshotParity",
                "check.victoryParity",
                "check.clientDisconnectForfeit",
                "check.hostDisconnectReturnToMenu",
                "check.campaignUnaffected");
    }

    public static AcceptanceAudit auditAcceptanceEvidence(Path twoProcessReport,
                                                          Path lanPreflightReport,
                                                          Path twoMachineHostReport,
                                                          Path twoMachineClientReport,
                                                          Path interactiveManualReport,
                                                          Path finalTwoMachineManualReport) {
        return auditAcceptanceEvidence(
                twoProcessReport,
                lanPreflightReport,
                twoMachineHostReport,
                twoMachineClientReport,
                interactiveManualReport,
                finalTwoMachineManualReport,
                Path.of("build/reports/multiplayer-two-machine-acceptance-log.md"));
    }

    public static AcceptanceAudit auditAcceptanceEvidence(Path twoProcessReport,
                                                          Path lanPreflightReport,
                                                          Path twoMachineHostReport,
                                                          Path twoMachineClientReport,
                                                          Path interactiveManualReport,
                                                          Path finalTwoMachineManualReport,
                                                          Path twoMachineAcceptanceLog) {
        return auditAcceptanceEvidence(
                twoProcessReport,
                lanPreflightReport,
                twoMachineHostReport,
                twoMachineClientReport,
                interactiveManualReport,
                finalTwoMachineManualReport,
                twoMachineAcceptanceLog,
                Path.of("build/reports/multiplayer-two-machine-readiness.txt"));
    }

    public static AcceptanceAudit auditAcceptanceEvidence(Path twoProcessReport,
                                                          Path lanPreflightReport,
                                                          Path twoMachineHostReport,
                                                          Path twoMachineClientReport,
                                                          Path interactiveManualReport,
                                                          Path finalTwoMachineManualReport,
                                                          Path twoMachineAcceptanceLog,
                                                          Path twoMachineReadinessReport) {
        ArrayList<AcceptanceGate> gates = baseEvidenceGates(
                twoProcessReport, lanPreflightReport, twoMachineHostReport, twoMachineClientReport);

        gates.add(validateTwoMachineReadinessReport(twoMachineReadinessReport));

        ManualAcceptanceEvidence interactive =
                validateManualAcceptanceReport(interactiveManualReport, "interactive-two-process", false);
        gates.add(new AcceptanceGate(
                "interactive two-process manual acceptance",
                interactive.accepted(),
                true,
                interactive.reason()));

        ManualAcceptanceEvidence finalTwoMachine =
                validateManualAcceptanceReport(finalTwoMachineManualReport, "final-two-machine", true);
        gates.add(new AcceptanceGate(
                "final two-machine manual acceptance",
                finalTwoMachine.accepted(),
                true,
                finalTwoMachine.reason()));

        gates.add(validateTwoMachineAcceptanceLog(twoMachineAcceptanceLog));
        return new AcceptanceAudit(gates);
    }

    public static MultiplayerReleaseGate validateMultiplayerReleaseGate(Path twoProcessReport,
                                                                        Path lanPreflightReport,
                                                                        Path twoMachineHostReport,
                                                                        Path twoMachineClientReport,
                                                                        Path interactiveManualReport,
                                                                        Path finalTwoMachineManualReport) {
        return validateMultiplayerReleaseGate(
                twoProcessReport,
                lanPreflightReport,
                twoMachineHostReport,
                twoMachineClientReport,
                interactiveManualReport,
                finalTwoMachineManualReport,
                Path.of("build/reports/multiplayer-two-machine-acceptance-log.md"));
    }

    public static MultiplayerReleaseGate validateMultiplayerReleaseGate(Path twoProcessReport,
                                                                        Path lanPreflightReport,
                                                                        Path twoMachineHostReport,
                                                                        Path twoMachineClientReport,
                                                                        Path interactiveManualReport,
                                                                        Path finalTwoMachineManualReport,
                                                                        Path twoMachineAcceptanceLog) {
        return validateMultiplayerReleaseGate(
                twoProcessReport,
                lanPreflightReport,
                twoMachineHostReport,
                twoMachineClientReport,
                interactiveManualReport,
                finalTwoMachineManualReport,
                twoMachineAcceptanceLog,
                Path.of("build/reports/multiplayer-two-machine-readiness.txt"));
    }

    public static MultiplayerReleaseGate validateMultiplayerReleaseGate(Path twoProcessReport,
                                                                        Path lanPreflightReport,
                                                                        Path twoMachineHostReport,
                                                                        Path twoMachineClientReport,
                                                                        Path interactiveManualReport,
                                                                        Path finalTwoMachineManualReport,
                                                                        Path twoMachineAcceptanceLog,
                                                                        Path twoMachineReadinessReport) {
        boolean enabled = MultiplayerRulesV1.entryPointEnabled();
        AcceptanceAudit audit = auditAcceptanceEvidence(
                twoProcessReport,
                lanPreflightReport,
                twoMachineHostReport,
                twoMachineClientReport,
                interactiveManualReport,
                finalTwoMachineManualReport,
                twoMachineAcceptanceLog,
                twoMachineReadinessReport);
        if (!enabled) {
            return new MultiplayerReleaseGate(true, false, audit.complete(),
                    "Multiplayer custom battle entry point is disabled",
                    audit.gates());
        }
        if (audit.complete()) {
            return new MultiplayerReleaseGate(true, true, true,
                    "Multiplayer custom battle entry point is enabled with complete acceptance evidence",
                    audit.gates());
        }
        return new MultiplayerReleaseGate(false, true, false,
                "Multiplayer custom battle entry point is enabled without complete acceptance evidence",
                audit.gates());
    }

    private static ArrayList<AcceptanceGate> baseEvidenceGates(Path twoProcessReport,
                                                               Path lanPreflightReport,
                                                               Path twoMachineHostReport,
                                                               Path twoMachineClientReport) {
        ArrayList<AcceptanceGate> gates = new ArrayList<>();
        gates.add(validateTwoProcessReport(twoProcessReport));

        LanPreflightEvidence preflight = validateLanPreflightReport(lanPreflightReport);
        gates.add(new AcceptanceGate(
                "LAN host preflight",
                preflight.accepted(),
                false,
                preflight.reason()));

        TwoMachineEvidence twoMachine = validateTwoMachineCliEvidence(twoMachineHostReport, twoMachineClientReport);
        gates.add(new AcceptanceGate(
                "first real two-machine LAN CLI pass",
                twoMachine.accepted(),
                true,
                twoMachine.reason()));
        return gates;
    }

    public static AcceptanceGate validateTwoMachineAcceptanceLog(Path twoMachineAcceptanceLog) {
        try {
            MultiplayerTwoMachineAcceptanceLogHarness.LogValidation validation =
                    MultiplayerTwoMachineAcceptanceLogHarness.validate(twoMachineAcceptanceLog);
            if (validation.accepted()) {
                return new AcceptanceGate(
                        "two-machine machine/build/network acceptance log",
                        true,
                        true,
                        "Two-machine acceptance log accepted");
            }
            return new AcceptanceGate(
                    "two-machine machine/build/network acceptance log",
                    false,
                    true,
                    "Two-machine acceptance log incomplete: " + validation.missing());
        } catch (Exception ex) {
            return new AcceptanceGate(
                    "two-machine machine/build/network acceptance log",
                    false,
                    true,
                    ex.getMessage() == null ? "Missing or malformed two-machine acceptance log" : ex.getMessage());
        }
    }

    public static AcceptanceGate validateTwoMachineReadinessReport(Path twoMachineReadinessReport) {
        try {
            Map<String, String> report = readReport(twoMachineReadinessReport);
            if (!"true".equalsIgnoreCase(report.getOrDefault("passed", ""))) {
                return new AcceptanceGate(
                        "local two-machine readiness report",
                        false,
                        false,
                        "Readiness report did not pass");
            }
            int checkCount = parseNonNegativeInt(report.getOrDefault("checkCount", "0"));
            if (checkCount <= 0) {
                return new AcceptanceGate(
                        "local two-machine readiness report",
                        false,
                        false,
                        "Readiness report has no checks");
            }
            for (int i = 1; i <= checkCount; i++) {
                if (!"true".equalsIgnoreCase(report.getOrDefault("check." + i + ".passed", ""))) {
                    String name = report.getOrDefault("check." + i + ".name", "check " + i);
                    return new AcceptanceGate(
                            "local two-machine readiness report",
                            false,
                            false,
                            "Readiness check failed: " + name);
                }
            }
            return new AcceptanceGate(
                    "local two-machine readiness report",
                    true,
                    false,
                    "Readiness report accepted");
        } catch (Exception ex) {
            return new AcceptanceGate(
                    "local two-machine readiness report",
                    false,
                    false,
                    ex.getMessage() == null ? "Missing or malformed readiness report" : ex.getMessage());
        }
    }

    private static String clean(String text) {
        return text == null ? "" : text.trim();
    }

    private static TwoMachineEvidence evidence(boolean accepted, String reason,
                                               Map<String, String> host,
                                               Map<String, String> client) {
        return new TwoMachineEvidence(
                accepted,
                reason,
                host == null ? "" : host.getOrDefault("address", ""),
                client == null ? "" : client.getOrDefault("address", ""),
                host == null ? "" : host.getOrDefault("localEndpoint", ""),
                host == null ? "" : host.getOrDefault("remoteEndpoint", ""),
                client == null ? "" : client.getOrDefault("localEndpoint", ""),
                client == null ? "" : client.getOrDefault("remoteEndpoint", ""),
                host == null ? "" : host.getOrDefault("connectionId", ""),
                client == null ? "" : client.getOrDefault("connectionId", ""));
    }

    private static ManualAcceptanceEvidence manualEvidence(boolean accepted,
                                                           String reason,
                                                           String scope,
                                                           List<String> missingChecks) {
        return new ManualAcceptanceEvidence(accepted, reason, scope, missingChecks);
    }

    private static void requireNonBlank(Map<String, String> report, List<String> missing, String key) {
        if (clean(report.getOrDefault(key, "")).isBlank()) missing.add(key);
    }

    private static void validateLinkedManualEvidence(Path manualReport,
                                                     Map<String, String> report,
                                                     List<String> missing,
                                                     boolean requireRealLanEvidence) {
        Path twoProcess = linkedReportPath(manualReport, report, missing, "twoProcessReport");
        if (twoProcess != null && !validateTwoProcessReport(twoProcess).proven()) {
            missing.add("twoProcessReport.accepted");
        }
        if (!requireRealLanEvidence) return;

        Path preflight = linkedReportPath(manualReport, report, missing, "preflightReport");
        if (preflight != null && !validateLanPreflightReport(preflight).accepted()) {
            missing.add("preflightReport.accepted");
        }
        Path host = linkedReportPath(manualReport, report, missing, "hostReport");
        Path client = linkedReportPath(manualReport, report, missing, "clientReport");
        if (host != null && client != null && !validateTwoMachineCliEvidence(host, client).accepted()) {
            missing.add("hostClientReports.acceptedRealLan");
        }
    }

    private static Path linkedReportPath(Path manualReport,
                                         Map<String, String> report,
                                         List<String> missing,
                                         String key) {
        String value = clean(report.getOrDefault(key, ""));
        if (value.isBlank()) {
            missing.add(key);
            return null;
        }
        Path direct = Path.of(value);
        if (direct.isAbsolute() || Files.isRegularFile(direct)) return direct;
        if (manualReport != null && manualReport.getParent() != null) {
            Path siblingRelative = manualReport.getParent().resolve(direct).normalize();
            if (Files.isRegularFile(siblingRelative)) return siblingRelative;
        }
        return direct;
    }

    private static Map<String, String> readReport(Path path) throws Exception {
        if (path == null || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Missing evidence report: " + path);
        }
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            out.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
        }
        return out;
    }

    private static int parseNonNegativeInt(String text) {
        try {
            return Math.max(0, Integer.parseInt(text.trim()));
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private static List<String> numberedValues(Map<String, String> report, String prefix) {
        ArrayList<String> out = new ArrayList<>();
        for (int i = 1; ; i++) {
            String value = report.get(prefix + i);
            if (value == null) break;
            String cleaned = clean(value);
            if (!cleaned.isBlank()) out.add(cleaned);
        }
        return List.copyOf(out);
    }

    private static boolean isLoopbackEvidenceAddress(String address) {
        String text = endpointHost(address);
        if (text.isBlank()) return true;
        if (text.startsWith("127.")) return true;
        if (text.startsWith("localhost")) return true;
        if (text.startsWith("[::1]") || text.startsWith("::1")) return true;
        return text.startsWith("0.0.0.0");
    }

    private static String firstNonBlank(String first, String second) {
        String value = clean(first);
        return value.isBlank() ? clean(second) : value;
    }

    private static String firstUsableEndpoint(String first, String second) {
        String value = clean(first);
        return value.isBlank() || endpointHost(value).startsWith("0.0.0.0") ? clean(second) : value;
    }

    private static String endpointHost(String endpoint) {
        String text = clean(endpoint).toLowerCase(java.util.Locale.ROOT);
        if (text.startsWith("/")) text = text.substring(1);
        if (text.startsWith("[")) {
            int close = text.indexOf(']');
            return close > 0 ? text.substring(0, close + 1) : text;
        }
        int colon = text.lastIndexOf(':');
        if (colon > 0 && text.indexOf(':') == colon) {
            return text.substring(0, colon).trim();
        }
        return text;
    }

    @SuppressWarnings("unused")
    private static byte[] utf8(String text) {
        return clean(text).getBytes(StandardCharsets.UTF_8);
    }
}
