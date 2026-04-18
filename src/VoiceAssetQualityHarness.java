import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Validates voice WAV assets for format consistency and basic loudness/clipping health.
 */
public final class VoiceAssetQualityHarness {
    private static final File ROOT = new File("assets/voice");
    private static final String[] ROLES = {"captain", "helm", "tactical", "engineering", "science"};
    private static final Pattern VALID_FILE = Pattern.compile("^[a-z0-9_]+_[0-9]{2}\\.wav$");

    private static final double MIN_DURATION_SEC = 0.35;
    private static final double MAX_DURATION_SEC = 4.25;
    private static final double MAX_AMBIENT_DURATION_SEC = 7.25;
    private static final double PEAK_WARN_DBFS = -1.0;
    private static final double RMS_MIN_DBFS = -30.0;
    private static final double RMS_MAX_DBFS = -10.0;
    private static final double ROLE_RMS_STDDEV_WARN_DB = 3.75;

    private VoiceAssetQualityHarness() {}

    private record VoiceAnalysis(
            String fileName,
            double durationSec,
            float sampleRateHz,
            int channels,
            double peakDbfs,
            double rmsDbfs,
            int clippedSamples,
            int sampleCount) {}

    private record VoiceIssue(String code, String detail) {}

    public static void main(String[] args) throws Exception {
        boolean strict = false;
        if (args != null) {
            for (String arg : args) {
                if (arg == null) continue;
                if ("--strict".equalsIgnoreCase(arg.trim())) strict = true;
            }
        }

        List<VoiceIssue> issues = new ArrayList<>();
        int checked = 0;
        Map<String, AudioSystem.VoiceEventSpec> matrix = new HashMap<>();
        for (AudioSystem.VoiceEventSpec spec : AudioSystem.voiceEventMatrix()) {
            if (spec == null) continue;
            matrix.put(spec.role() + "/" + spec.eventId(), spec);
        }

        for (String role : ROLES) {
            File dir = new File(ROOT, role);
            if (!dir.isDirectory()) {
                issues.add(new VoiceIssue("role_dir_missing", role + " folder missing."));
                continue;
            }

            File[] files = dir.listFiles(f -> f != null && f.isFile() && f.getName().toLowerCase(Locale.US).endsWith(".wav"));
            if (files == null || files.length == 0) {
                issues.add(new VoiceIssue("role_empty", role + " has no WAV assets."));
                continue;
            }
            Arrays.sort(files, (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.getName(), b.getName()));

            List<Double> roleRms = new ArrayList<>();
            for (File wav : files) {
                String name = wav.getName().toLowerCase(Locale.US);
                if (!VALID_FILE.matcher(name).matches()) {
                    issues.add(new VoiceIssue("naming", role + "/" + name + " does not match <event_id>_<nn>.wav"));
                    continue;
                }

                String eventId = extractEventId(name);
                AudioSystem.VoiceEventSpec spec = matrix.get(role + "/" + eventId);
                if (spec == null) {
                    // Ignore auxiliary or archival lines that are not part of the active runtime voice matrix.
                    continue;
                }

                VoiceAnalysis a;
                try {
                    a = analyze(wav);
                } catch (Throwable e) {
                    issues.add(new VoiceIssue("read", role + "/" + name + " failed to decode: " + e.getClass().getSimpleName()));
                    continue;
                }

                checked++;
                roleRms.add(a.rmsDbfs);
                System.out.println("[voice-quality] " + role + "/" + a.fileName()
                        + " dur=" + fmt(a.durationSec()) + "s"
                        + " sr=" + Math.round(a.sampleRateHz())
                        + "Hz ch=" + a.channels()
                        + " peak=" + fmt(a.peakDbfs()) + "dBFS"
                        + " rms=" + fmt(a.rmsDbfs()) + "dBFS"
                        + " clip=" + a.clippedSamples());

                if (a.channels() != 1) {
                    issues.add(new VoiceIssue("channels", role + "/" + name + " expected mono, found " + a.channels()));
                }
                int sr = Math.round(a.sampleRateHz());
                if (sr != 44100 && sr != 48000) {
                    issues.add(new VoiceIssue("sample_rate", role + "/" + name + " expected 44100 or 48000 Hz, found " + sr));
                }
                double maxDuration = allowedMaxDurationSec(spec);
                if (a.durationSec() < MIN_DURATION_SEC || a.durationSec() > maxDuration) {
                    issues.add(new VoiceIssue("duration", role + "/" + name + " duration out of range: " + fmt(a.durationSec()) + "s"));
                }
                if (a.clippedSamples() > 0) {
                    issues.add(new VoiceIssue("clipping", role + "/" + name + " has clipped samples: " + a.clippedSamples()));
                }
                if (a.peakDbfs() > PEAK_WARN_DBFS) {
                    issues.add(new VoiceIssue("peak", role + "/" + name + " peak too hot: " + fmt(a.peakDbfs()) + " dBFS"));
                }
                if (a.rmsDbfs() < RMS_MIN_DBFS || a.rmsDbfs() > RMS_MAX_DBFS) {
                    issues.add(new VoiceIssue("rms", role + "/" + name + " RMS out of range: " + fmt(a.rmsDbfs()) + " dBFS"));
                }
            }

            if (roleRms.size() >= 2) {
                double sd = stddev(roleRms);
                System.out.println("[voice-quality] " + role + " rms-stddev=" + fmt(sd) + "dB");
                if (sd > ROLE_RMS_STDDEV_WARN_DB) {
                    issues.add(new VoiceIssue("level_spread",
                            role + " RMS spread is high (" + fmt(sd) + " dB); normalize role lines closer."));
                }
            }
        }

        System.out.println("[voice-quality] checked files: " + checked);
        if (issues.isEmpty()) {
            System.out.println("[voice-quality] issues: none");
            return;
        }

        System.out.println("[voice-quality] issues:");
        for (VoiceIssue issue : issues) {
            System.out.println(" - [" + issue.code() + "] " + issue.detail());
        }
        if (strict) {
            System.exit(2);
        }
    }

    private static VoiceAnalysis analyze(File wav) throws Exception {
        try (AudioInputStream in = javax.sound.sampled.AudioSystem.getAudioInputStream(wav)) {
            AudioFormat src = in.getFormat();
            AudioFormat pcm = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    src.getSampleRate(),
                    16,
                    src.getChannels(),
                    src.getChannels() * 2,
                    src.getSampleRate(),
                    false
            );
            try (AudioInputStream pcmIn = javax.sound.sampled.AudioSystem.getAudioInputStream(pcm, in)) {
                byte[] bytes = pcmIn.readAllBytes();
                if (bytes.length < 2) {
                    return new VoiceAnalysis(wav.getName(), 0.0, pcm.getSampleRate(), pcm.getChannels(),
                            -120.0, -120.0, 0, 0);
                }

                int frameSize = Math.max(2, pcm.getFrameSize());
                int frameCount = bytes.length / frameSize;
                double durationSec = frameCount / Math.max(1.0, pcm.getSampleRate());

                double peak = 0.0;
                double sumSq = 0.0;
                int clipped = 0;
                int sampleCount = 0;

                for (int i = 0; i + 1 < bytes.length; i += 2) {
                    int sample = (short) (((bytes[i + 1] & 0xFF) << 8) | (bytes[i] & 0xFF));
                    double n = sample / 32768.0;
                    double abs = Math.abs(n);
                    if (abs > peak) peak = abs;
                    sumSq += n * n;
                    if (Math.abs(sample) >= 32767) clipped++;
                    sampleCount++;
                }

                double rms = (sampleCount <= 0) ? 0.0 : Math.sqrt(sumSq / sampleCount);
                return new VoiceAnalysis(
                        wav.getName(),
                        durationSec,
                        pcm.getSampleRate(),
                        pcm.getChannels(),
                        toDbfs(peak),
                        toDbfs(rms),
                        clipped,
                        sampleCount
                );
            }
        }
    }

    private static double stddev(List<Double> values) {
        if (values == null || values.size() < 2) return 0.0;
        double sum = 0.0;
        for (double v : values) sum += v;
        double mean = sum / values.size();
        double var = 0.0;
        for (double v : values) {
            double d = v - mean;
            var += d * d;
        }
        var /= values.size();
        return Math.sqrt(Math.max(0.0, var));
    }

    private static double toDbfs(double amplitude) {
        double a = Math.max(1e-9, amplitude);
        return 20.0 * Math.log10(a);
    }

    private static String extractEventId(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        String stem = dot >= 0 ? fileName.substring(0, dot) : fileName;
        int us = stem.lastIndexOf('_');
        if (us <= 0) return stem;
        return stem.substring(0, us);
    }

    private static double allowedMaxDurationSec(AudioSystem.VoiceEventSpec spec) {
        if (spec == null || spec.eventId() == null) return MAX_DURATION_SEC;
        String id = spec.eventId().toLowerCase(Locale.US);
        if ("banter".equals(id) || id.contains("ambient")) {
            return MAX_AMBIENT_DURATION_SEC;
        }
        return MAX_DURATION_SEC;
    }

    private static String fmt(double v) {
        return String.format(Locale.US, "%.2f", v);
    }
}
