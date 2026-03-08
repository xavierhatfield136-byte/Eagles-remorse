import java.util.ArrayList;
import java.util.List;

/**
 * Verifies voice event matrix coverage and required variant counts.
 */
public final class VoiceCoverageHarness {
    private VoiceCoverageHarness() {}

    public static void main(String[] args) {
        boolean strict = false;
        if (args != null) {
            for (String arg : args) {
                if (arg == null) continue;
                if ("--strict".equalsIgnoreCase(arg.trim())) strict = true;
            }
        }

        List<AudioSystem.VoiceEventSpec> matrix = AudioSystem.voiceEventMatrix();
        List<String> missing = new ArrayList<>();
        int highFreqCovered = 0;
        int highFreqTotal = 0;

        System.out.println("[voice-coverage] rows=" + matrix.size());
        for (AudioSystem.VoiceEventSpec row : matrix) {
            boolean highFrequency = row.requiredVariants() >= 2;
            if (highFrequency) highFreqTotal++;

            boolean ok = row.assetVariants() >= row.requiredVariants();
            if (ok && highFrequency) highFreqCovered++;

            System.out.println("[voice-coverage] " + row.role() + "/" + row.eventId()
                    + " priority=" + row.priority()
                    + " cooldown=" + fmt(row.cooldownSec())
                    + " requiredVariants=" + row.requiredVariants()
                    + " assets=" + row.assetVariants()
                    + " captions=" + row.captionVariants()
                    + " status=" + (ok ? "OK" : "MISSING"));

            if (!ok) {
                missing.add(row.role() + "/" + row.eventId()
                        + " needs " + row.requiredVariants()
                        + " variants, found " + row.assetVariants());
            }
        }

        System.out.println("[voice-coverage] high-frequency coverage: " + highFreqCovered + "/" + highFreqTotal);

        if (missing.isEmpty()) {
            System.out.println("[voice-coverage] issues: none");
            return;
        }

        System.out.println("[voice-coverage] issues:");
        for (String issue : missing) {
            System.out.println(" - " + issue);
        }

        if (strict) {
            System.exit(2);
        }
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.US, "%.2f", v);
    }
}
