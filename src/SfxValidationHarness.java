import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Validates manifest coverage and clipping risk for SFX assets.
 */
public final class SfxValidationHarness {
    private SfxValidationHarness() {}

    public static void main(String[] args) throws Exception {
        boolean strict = false;
        for (String arg : args) {
            if (arg != null && "--strict".equalsIgnoreCase(arg.trim())) strict = true;
        }

        SfxManifest.CoverageReport coverage = SfxManifest.coverage();
        System.out.println("[sfx-validate] rows=" + coverage.rows().size()
                + " ok=" + coverage.okCount() + " fail=" + coverage.failCount());

        List<String> issues = new ArrayList<>();
        for (SfxManifest.CoverageRow row : coverage.rows()) {
            System.out.println("[sfx-validate] " + row.spec().eventId()
                    + " required=" + row.spec().requiredVariants()
                    + " assets=" + row.assetVariants()
                    + " status=" + (row.ok() ? "OK" : "MISSING"));
            if (!row.ok()) {
                issues.add("missing variants: " + row.spec().eventId());
            }
        }

        int clipped = 0;
        int analyzed = 0;
        for (SfxManifest.CoverageRow row : coverage.rows()) {
            SfxManifest.EventSpec spec = row.spec();
            File dir = new File("assets/audio", spec.folder());
            File[] files = dir.listFiles(f -> f != null && f.isFile()
                    && f.getName().toLowerCase(Locale.US).startsWith(spec.filePrefix().toLowerCase(Locale.US))
                    && f.getName().toLowerCase(Locale.US).endsWith(".wav"));
            if (files == null) continue;
            for (File wav : files) {
                analyzed++;
                double peak = peakAmplitude(wav);
                if (peak >= 0.995) {
                    clipped++;
                    issues.add("clipping risk: " + wav.getPath() + " peak=" + fmt(peak));
                }
            }
        }

        System.out.println("[sfx-validate] analyzedWavs=" + analyzed + " clipped=" + clipped);

        if (issues.isEmpty()) {
            System.out.println("[sfx-validate] issues: none");
            return;
        }

        System.out.println("[sfx-validate] issues:");
        for (String issue : issues) {
            System.out.println(" - " + issue);
        }
        if (strict) {
            System.exit(2);
        }
    }

    private static double peakAmplitude(File wav) {
        if (wav == null || !wav.isFile()) return 0.0;
        try (AudioInputStream in = javax.sound.sampled.AudioSystem.getAudioInputStream(wav)) {
            AudioFormat fmt = in.getFormat();
            if (fmt.getSampleSizeInBits() != 16) return 0.0;
            byte[] buf = in.readAllBytes();
            if (buf.length < 2) return 0.0;
            boolean big = fmt.isBigEndian();
            int channels = Math.max(1, fmt.getChannels());
            double peak = 0.0;
            for (int i = 0; i + 1 < buf.length; i += 2 * channels) {
                for (int ch = 0; ch < channels; ch++) {
                    int idx = i + ch * 2;
                    if (idx + 1 >= buf.length) break;
                    int lo = buf[idx] & 0xFF;
                    int hi = buf[idx + 1];
                    short s = big ? (short) ((lo << 8) | (hi & 0xFF)) : (short) ((hi << 8) | lo);
                    double a = Math.abs(s / (double) Short.MAX_VALUE);
                    if (a > peak) peak = a;
                }
            }
            return peak;
        } catch (Throwable ignored) {
            return 0.0;
        }
    }

    private static String fmt(double v) {
        return String.format(Locale.US, "%.4f", v);
    }
}
