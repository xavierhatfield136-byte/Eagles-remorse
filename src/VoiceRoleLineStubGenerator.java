import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ensures each voice role has at least N line assets by generating additional
 * deterministic tone stubs for existing events.
 */
public final class VoiceRoleLineStubGenerator {
    private VoiceRoleLineStubGenerator() {}

    public static void main(String[] args) throws Exception {
        int minLines = 12;
        boolean overwrite = false;
        for (String arg : args) {
            if (arg == null || arg.isBlank()) continue;
            String a = arg.trim();
            if (a.startsWith("--min-lines=")) {
                minLines = Math.max(1, parseInt(a.substring("--min-lines=".length()), minLines));
            } else if ("--overwrite".equalsIgnoreCase(a)) {
                overwrite = true;
            }
        }

        List<AudioSystem.VoiceEventSpec> matrix = AudioSystem.voiceEventMatrix();
        Map<String, List<AudioSystem.VoiceEventSpec>> byRole = new HashMap<>();
        for (AudioSystem.VoiceEventSpec row : matrix) {
            byRole.computeIfAbsent(row.role(), k -> new ArrayList<>()).add(row);
        }

        int created = 0;
        int skipped = 0;
        for (Map.Entry<String, List<AudioSystem.VoiceEventSpec>> e : byRole.entrySet()) {
            String role = e.getKey();
            List<AudioSystem.VoiceEventSpec> events = e.getValue();
            if (events.isEmpty()) continue;

            File roleDir = new File("assets/voice/" + role);
            if (!roleDir.exists() && !roleDir.mkdirs()) {
                System.out.println("[voice-role-gen] failed mkdir: " + roleDir.getAbsolutePath());
                continue;
            }

            int count = countVoiceFiles(roleDir);
            if (count >= minLines && !overwrite) {
                continue;
            }

            events.sort(Comparator.comparing(AudioSystem.VoiceEventSpec::eventId));
            int cursor = 0;
            while (count < minLines) {
                AudioSystem.VoiceEventSpec spec = events.get(cursor % events.size());
                cursor++;
                int nextVariant = nextVariantIndex(roleDir, spec.eventId());
                File out = new File(roleDir, spec.eventId() + "_" + String.format(Locale.US, "%02d", nextVariant) + ".wav");
                if (out.isFile() && !overwrite) {
                    skipped++;
                    continue;
                }
                writeToneStub(out, role, spec.eventId(), nextVariant, spec.priority());
                created++;
                count++;
            }
        }

        System.out.println("[voice-role-gen] minLines=" + minLines + " created=" + created + " skipped=" + skipped);
    }

    private static int countVoiceFiles(File roleDir) {
        File[] files = roleDir.listFiles(f -> f != null && f.isFile()
                && f.getName().toLowerCase(Locale.US).endsWith(".wav"));
        return (files == null) ? 0 : files.length;
    }

    private static int nextVariantIndex(File roleDir, String eventId) {
        int max = 0;
        String prefix = eventId.toLowerCase(Locale.US) + "_";
        File[] files = roleDir.listFiles(f -> f != null && f.isFile()
                && f.getName().toLowerCase(Locale.US).startsWith(prefix)
                && f.getName().toLowerCase(Locale.US).endsWith(".wav"));
        if (files != null) {
            for (File f : files) {
                String n = f.getName().toLowerCase(Locale.US);
                int dot = n.lastIndexOf('.');
                int us = n.lastIndexOf('_');
                if (dot <= us || us < 0) continue;
                String raw = n.substring(us + 1, dot);
                int idx = parseInt(raw, 0);
                if (idx > max) max = idx;
            }
        }
        return Math.max(1, max + 1);
    }

    private static void writeToneStub(File out, String role, String eventId, int variant, int priority) throws Exception {
        int sampleRate = 44100;
        int ms = 360 + (variant % 7) * 30 + Math.max(0, 3 - priority) * 18;
        int frames = Math.max(1, (int) Math.round(sampleRate * (ms / 1000.0)));

        double base = switch (role) {
            case "captain" -> 220.0;
            case "helm" -> 300.0;
            case "tactical" -> 260.0;
            case "engineering" -> 185.0;
            case "science" -> 335.0;
            default -> 250.0;
        };
        int h = Math.abs((role + ":" + eventId + ":" + variant).hashCode());
        double tone = base + (h % 84) + variant * 7.0;

        byte[] data = new byte[frames * 2];
        for (int i = 0; i < frames; i++) {
            double t = i / (double) sampleRate;
            double env = envelope(i, frames);
            double w = Math.sin(2.0 * Math.PI * tone * t);
            w += 0.31 * Math.sin(2.0 * Math.PI * tone * 1.8 * t + 0.18 * variant);
            w += 0.12 * Math.sin(2.0 * Math.PI * tone * 0.50 * t + 0.42);
            w /= 1.43;
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

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
