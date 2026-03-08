import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Verifies minimum voice line counts per role from assets/voice/*.
 */
public final class VoiceRoleLineCountHarness {
    private static final String[] ROLES = {"captain", "helm", "tactical", "engineering", "science"};

    private VoiceRoleLineCountHarness() {}

    public static void main(String[] args) {
        boolean strict = false;
        int minLines = 12;
        for (String arg : args) {
            if (arg == null || arg.isBlank()) continue;
            String a = arg.trim();
            if ("--strict".equalsIgnoreCase(a)) strict = true;
            if (a.startsWith("--min-lines=")) minLines = Math.max(1, parseInt(a.substring("--min-lines=".length()), minLines));
        }

        Map<String, Integer> counts = new TreeMap<>();
        List<String> failures = new ArrayList<>();
        int total = 0;

        for (String role : ROLES) {
            File dir = new File("assets/voice/" + role);
            int c = 0;
            File[] files = dir.listFiles(f -> f != null && f.isFile() && f.getName().toLowerCase(Locale.US).endsWith(".wav"));
            if (files != null) c = files.length;
            counts.put(role, c);
            total += c;
            if (c < minLines) failures.add(role + " has " + c + " (< " + minLines + ")");
        }

        System.out.println("[voice-role-count] minLines=" + minLines + " totalFiles=" + total);
        for (String role : ROLES) {
            System.out.println("[voice-role-count] role=" + role + " lines=" + counts.getOrDefault(role, 0));
        }

        if (failures.isEmpty()) {
            System.out.println("[voice-role-count] checks: PASS");
            return;
        }

        System.out.println("[voice-role-count] checks: FAIL");
        for (String f : failures) System.out.println(" - " + f);
        if (strict) System.exit(2);
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
