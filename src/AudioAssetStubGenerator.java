import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Locale;

/**
 * Generates authored-placeholder SFX files for manifest coverage.
 */
public final class AudioAssetStubGenerator {
    private AudioAssetStubGenerator() {}

    public static void main(String[] args) throws Exception {
        boolean overwrite = false;
        for (String arg : args) {
            if (arg != null && "--overwrite".equalsIgnoreCase(arg.trim())) overwrite = true;
        }

        int created = 0;
        int skipped = 0;
        for (SfxManifest.EventSpec spec : SfxManifest.all()) {
            int required = Math.max(1, spec.requiredVariants());
            File folder = new File("assets/audio", spec.folder());
            if (!folder.exists() && !folder.mkdirs()) {
                System.out.println("[audio-gen] failed mkdir: " + folder.getAbsolutePath());
                continue;
            }
            for (int i = 1; i <= required; i++) {
                String variant = String.format(Locale.US, "%02d", i);
                File out = new File(folder, spec.filePrefix() + "_" + variant + ".wav");
                if (out.isFile() && !overwrite) {
                    skipped++;
                    continue;
                }
                writeSfxStub(out, spec, i);
                created++;
            }
        }
        System.out.println("[audio-gen] created=" + created + " skipped=" + skipped);
    }

    private static void writeSfxStub(File out, SfxManifest.EventSpec spec, int variant) throws Exception {
        int sampleRate = 44100;
        int ms = durationMs(spec, variant);
        int frames = Math.max(1, (int) Math.round(sampleRate * (ms / 1000.0)));
        byte[] data = new byte[frames * 2];

        double base = baseHz(spec.category());
        double tone = base + (Math.abs(spec.eventId().hashCode()) % 160) + variant * 13.0;
        double mod = 0.1 + (Math.abs(spec.eventId().hashCode()) % 7) * 0.03;

        for (int i = 0; i < frames; i++) {
            double t = i / (double) sampleRate;
            double env = envelope(i, frames);
            double w = Math.sin(2.0 * Math.PI * tone * t);
            w += 0.4 * Math.sin(2.0 * Math.PI * (tone * (1.8 + mod)) * t + 0.3 * variant);
            w += 0.2 * Math.sin(2.0 * Math.PI * (tone * 0.5) * t + 0.5);
            if (spec.category() == SfxManifest.Category.IMPACT || spec.category() == SfxManifest.Category.HAZARD) {
                w += ((Math.sin(2 * Math.PI * 37.0 * t) + Math.sin(2 * Math.PI * 53.0 * t)) * 0.08);
            }
            w /= 1.6;
            short s = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE,
                    Math.round(w * env * 0.24 * Short.MAX_VALUE)));
            data[i * 2] = (byte) (s & 0xFF);
            data[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
        }

        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        try (AudioInputStream ais = new AudioInputStream(new ByteArrayInputStream(data), format, frames)) {
            javax.sound.sampled.AudioSystem.write(ais, AudioFileFormat.Type.WAVE, out);
        }
    }

    private static int durationMs(SfxManifest.EventSpec spec, int variant) {
        return switch (spec.category()) {
            case UI -> 80 + variant * 10;
            case WEAPON -> 95 + variant * 14;
            case IMPACT -> 110 + variant * 14;
            case HAZARD -> 170 + variant * 18;
            case SUBSYSTEM -> 220 + variant * 24;
            case AMBIENCE -> 8000;
        };
    }

    private static double baseHz(SfxManifest.Category category) {
        return switch (category) {
            case UI -> 720.0;
            case WEAPON -> 190.0;
            case IMPACT -> 240.0;
            case HAZARD -> 150.0;
            case SUBSYSTEM -> 130.0;
            case AMBIENCE -> 58.0;
        };
    }

    private static double envelope(int i, int frames) {
        double t = i / (double) Math.max(1, frames - 1);
        double attack = Math.min(1.0, t / 0.04);
        double release = Math.min(1.0, (1.0 - t) / 0.18);
        return Math.max(0.0, Math.min(attack, release));
    }
}
