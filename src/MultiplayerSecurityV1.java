import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** V1 crash-prevention and trust-boundary checks for multiplayer messages and commands. */
public final class MultiplayerSecurityV1 {
    private final ArrayList<SuspiciousCommand> suspiciousCommands = new ArrayList<>();

    public record SuspiciousCommand(int slotId, long sequence, String reason) {
        public SuspiciousCommand {
            slotId = Math.max(0, slotId);
            sequence = Math.max(-1L, sequence);
            reason = (reason == null || reason.isBlank()) ? "Suspicious multiplayer command" : reason.trim();
        }
    }

    public record SecureInput(MultiplayerCommandGate.PlayerInputFrame frame,
                              boolean malformed,
                              String reason) {
        public SecureInput {
            reason = (reason == null || reason.isBlank())
                    ? (malformed ? "Malformed input" : "Accepted")
                    : reason.trim();
        }
    }

    public List<SuspiciousCommand> suspiciousCommands() {
        return List.copyOf(suspiciousCommands);
    }

    public MultiplayerProtocolV1.ProtocolValidation validatePayload(byte[] payload) {
        return MultiplayerProtocolV1.validatePayloadBytes(payload);
    }

    public SecureInput sanitizeInput(MultiplayerCommandGate.PlayerInputFrame frame) {
        if (frame == null) {
            log(0, -1L, "Missing input frame");
            return new SecureInput(null, true, "Missing input frame");
        }
        if (!Double.isFinite(frame.aimAngle())) {
            log(frame.slotId(), frame.sequence(), "Malformed aim angle");
            return new SecureInput(null, true, "Malformed aim angle");
        }
        float thrust = clampUnit(frame.thrust());
        float turn = clampUnit(frame.turn());
        double aim = normalizeAngle(frame.aimAngle());
        MultiplayerCommandGate.PlayerInputFrame sanitized =
                new MultiplayerCommandGate.PlayerInputFrame(
                        Math.max(0, frame.slotId()),
                        Math.max(0, frame.controlledShipId()),
                        Math.max(0L, frame.sequence()),
                        Math.max(0L, frame.clientTick()),
                        thrust,
                        turn,
                        aim,
                        frame.primaryHeld(),
                        frame.secondaryHeld());
        boolean clamped = Math.abs(thrust - frame.thrust()) > 1e-6f
                || Math.abs(turn - frame.turn()) > 1e-6f
                || Math.abs(aim - frame.aimAngle()) > 1e-6;
        if (clamped) {
            log(frame.slotId(), frame.sequence(), "Clamped out-of-bounds input");
        }
        return new SecureInput(sanitized, false, clamped ? "Clamped input" : "Accepted");
    }

    public MultiplayerCommandGate.CommandResult validateSanitizedInput(
            MultiplayerCommandGate gate,
            MultiplayerCommandGate.PlayerInputFrame frame,
            long hostTick) {
        if (gate == null) {
            log(frame == null ? 0 : frame.slotId(), frame == null ? -1L : frame.sequence(),
                    "Missing command gate");
            return new MultiplayerCommandGate.CommandResult(false, "Missing command gate", -1L);
        }
        SecureInput secure = sanitizeInput(frame);
        if (secure.malformed()) {
            return new MultiplayerCommandGate.CommandResult(false, secure.reason(),
                    frame == null ? -1L : frame.sequence());
        }
        MultiplayerCommandGate.CommandResult result = gate.validateInputFrame(secure.frame(), hostTick);
        if (!result.accepted()) log(secure.frame().slotId(), secure.frame().sequence(), result.reason());
        return result;
    }

    public MultiplayerCommandGate.CommandResult validateDiscreteCommand(
            MultiplayerCommandGate gate,
            MultiplayerCommandGate.DiscreteCommand command) {
        if (gate == null) {
            log(command == null ? 0 : command.slotId(), command == null ? -1L : command.sequence(),
                    "Missing command gate");
            return new MultiplayerCommandGate.CommandResult(false, "Missing command gate", -1L);
        }
        MultiplayerCommandGate.CommandResult result = gate.validateDiscreteCommand(command);
        if (!result.accepted()) {
            log(command == null ? 0 : command.slotId(), command == null ? -1L : command.sequence(),
                    result.reason());
        }
        return result;
    }

    public boolean exposesForbiddenLocalData(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        return lower.contains("password")
                || lower.contains("token")
                || lower.contains("config/")
                || lower.contains("config\\")
                || lower.contains(".sav")
                || lower.contains("developer")
                || lower.contains("devtools")
                || lower.matches(".*[a-z]:\\\\.*");
    }

    public String redactAddressForLog(String address) {
        String text = address == null ? "" : address.trim();
        if (text.isEmpty()) return "";
        String host = text;
        int slash = host.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < host.length()) host = host.substring(slash + 1);
        int colon = host.lastIndexOf(':');
        String port = "";
        if (colon > 0 && host.indexOf(':') == colon) {
            port = host.substring(colon);
            host = host.substring(0, colon);
        }
        if (host.equals("127.0.0.1") || host.equalsIgnoreCase("localhost")) return host + port;
        if (host.startsWith("10.") || host.startsWith("192.168.") || private172(host)) {
            return "private-lan-address" + port;
        }
        return "remote-address" + port;
    }

    private void log(int slotId, long sequence, String reason) {
        suspiciousCommands.add(new SuspiciousCommand(slotId, sequence, reason));
    }

    private static float clampUnit(float value) {
        if (!Float.isFinite(value)) return 0.0f;
        if (value > 1.0f) return 1.0f;
        if (value < -1.0f) return -1.0f;
        return value;
    }

    private static double normalizeAngle(double angle) {
        double twoPi = Math.PI * 2.0;
        double normalized = angle % twoPi;
        if (normalized < 0.0) normalized += twoPi;
        return normalized;
    }

    private static boolean private172(String host) {
        if (!host.startsWith("172.")) return false;
        String[] parts = host.split("\\.");
        if (parts.length < 2) return false;
        try {
            int second = Integer.parseInt(parts[1]);
            return second >= 16 && second <= 31;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
