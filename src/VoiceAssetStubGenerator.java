import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * Generates placeholder WAV variants for voice events when authored lines are missing.
 */
public final class VoiceAssetStubGenerator {
    private VoiceAssetStubGenerator() {}

    public static void main(String[] args) throws Exception {
        boolean overwrite = false;
        for (String arg : args) {
            if (arg == null) continue;
            if ("--overwrite".equalsIgnoreCase(arg.trim())) overwrite = true;
        }

        List<AudioSystem.VoiceEventSpec> matrix = AudioSystem.voiceEventMatrix();
        int created = 0;
        int skipped = 0;

        for (AudioSystem.VoiceEventSpec row : matrix) {
            int required = Math.max(1, row.requiredVariants());
            File roleDir = new File("assets/voice/" + row.role());
            if (!roleDir.exists() && !roleDir.mkdirs()) {
                System.out.println("[voice-gen] failed mkdir: " + roleDir.getAbsolutePath());
                continue;
            }

            for (int variant = 1; variant <= required; variant++) {
                String fileName = row.eventId() + "_" + String.format(Locale.US, "%02d", variant) + ".wav";
                File out = new File(roleDir, fileName);
                if (out.isFile() && !overwrite) {
                    skipped++;
                    continue;
                }
                writeToneStub(out, row.role(), row.eventId(), variant, row.priority());
                created++;
            }
        }

        System.out.println("[voice-gen] created=" + created + " skipped=" + skipped);
    }

    private static void writeToneStub(File out, String role, String eventId, int variant, int priority) throws Exception {
        int sampleRate = 44100;
        int ms = 380 + variant * 45 + Math.max(0, 3 - priority) * 20;
        int frames = Math.max(1, (int) Math.round(sampleRate * (ms / 1000.0)));

        double base = switch (role) {
            case "captain" -> 220.0;
            case "helm" -> 300.0;
            case "tactical" -> 260.0;
            case "engineering" -> 185.0;
            case "science" -> 335.0;
            default -> 250.0;
        };
        int h = Math.abs((role + ":" + eventId).hashCode());
        double tone = base + (h % 70) + variant * 18.0;

        byte[] data = new byte[frames * 2];
        for (int i = 0; i < frames; i++) {
            double t = i / (double) sampleRate;
            double env = envelope(i, frames);
            double w = Math.sin(2.0 * Math.PI * tone * t);
            w += 0.33 * Math.sin(2.0 * Math.PI * tone * 1.9 * t + 0.2 * variant);
            w += 0.11 * Math.sin(2.0 * Math.PI * tone * 0.52 * t + 0.4);
            w /= 1.44;
            short s = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE,
                    Math.round(w * env * 0.26 * Short.MAX_VALUE)));
            data[i * 2] = (byte) (s & 0xFF);
            data[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
        }

        AudioFormat fmt = new AudioFormat(sampleRate, 16, 1, true, false);
        try (AudioInputStream ais = new AudioInputStream(new ByteArrayInputStream(data), fmt, frames)) {
            javax.sound.sampled.AudioSystem.write(ais, AudioFileFormat.Type.WAVE, out);
        }
    }

    private static double envelope(int i, int frames) {
        double t = i / (double) Math.max(1, frames - 1);
        double attack = Math.min(1.0, t / 0.06);
        double release = Math.min(1.0, (1.0 - t) / 0.10);
        return Math.max(0.0, Math.min(attack, release));
    }
}
