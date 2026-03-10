import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Locale;
import java.util.Random;

/**
 * Generates procedural SFX designed to avoid retro placeholder humming.
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
        double[] signal = new double[frames];
        Random rng = new Random(seed(spec, variant));
        switch (spec.category()) {
            case UI -> synthUi(signal, sampleRate, spec, variant, rng);
            case WEAPON -> synthWeapon(signal, sampleRate, spec, variant, rng);
            case IMPACT -> synthImpact(signal, sampleRate, spec, variant, rng);
            case HAZARD -> synthHazard(signal, sampleRate, spec, variant, rng);
            case SUBSYSTEM -> synthSubsystem(signal, sampleRate, spec, variant, rng);
            case AMBIENCE -> synthAmbience(signal, sampleRate, spec, variant, rng);
        }
        normalize(signal, spec.category() == SfxManifest.Category.AMBIENCE ? 0.40 : 0.74);
        byte[] data = toPcm16(signal);

        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        try (AudioInputStream ais = new AudioInputStream(new ByteArrayInputStream(data), format, frames)) {
            javax.sound.sampled.AudioSystem.write(ais, AudioFileFormat.Type.WAVE, out);
        }
    }

    private static int durationMs(SfxManifest.EventSpec spec, int variant) {
        return switch (spec.category()) {
            case UI -> 95 + variant * 14;
            case WEAPON -> 140 + variant * 22;
            case IMPACT -> 180 + variant * 28;
            case HAZARD -> 340 + variant * 40;
            case SUBSYSTEM -> 460 + variant * 55;
            case AMBIENCE -> 10000;
        };
    }

    private static void synthUi(double[] signal, int sampleRate, SfxManifest.EventSpec spec, int variant, Random rng) {
        double phase = 0.0;
        boolean open = spec.eventId().contains("open");
        double f0 = open ? 760.0 : 660.0;
        double f1 = open ? 1820.0 : 340.0;
        double lp = 0.0;
        int frames = signal.length;
        for (int i = 0; i < frames; i++) {
            double t = i / (double) sampleRate;
            double x = i / (double) Math.max(1, frames - 1);
            double freq = lerp(f0, f1, Math.pow(x, 0.55));
            phase += (2.0 * Math.PI * freq) / sampleRate;
            double noise = randSigned(rng);
            lp += 0.21 * (noise - lp);
            double click = (i < sampleRate * 0.012)
                    ? (1.0 - i / (sampleRate * 0.012)) * (0.85 * noise + 0.15 * Math.sin(2.0 * Math.PI * 2200.0 * t))
                    : 0.0;
            double tone = 0.58 * Math.sin(phase) + 0.18 * Math.sin(phase * 2.4 + 0.2 * variant);
            double env = expEnv(x, 0.0, 0.86, 18.0);
            signal[i] = env * (tone + 0.24 * lp) + click * 0.5;
        }
    }

    private static void synthWeapon(double[] signal, int sampleRate, SfxManifest.EventSpec spec, int variant, Random rng) {
        double phase = 0.0;
        int frames = signal.length;
        boolean wave = spec.eventId().contains("wave");
        boolean secondary = spec.eventId().contains("secondary");
        double fStart = wave ? 220.0 : (secondary ? 160.0 : 280.0);
        double fEnd = wave ? 72.0 : (secondary ? 110.0 : 160.0);
        double lp = 0.0;
        double hpState = 0.0;
        for (int i = 0; i < frames; i++) {
            double t = i / (double) sampleRate;
            double x = i / (double) Math.max(1, frames - 1);
            double freq = lerp(fStart, fEnd, Math.pow(x, wave ? 0.78 : 0.64));
            phase += (2.0 * Math.PI * freq) / sampleRate;
            double noise = randSigned(rng);
            lp += (wave ? 0.06 : 0.10) * (noise - lp);
            hpState += 0.35 * (noise - hpState);
            double hp = noise - hpState;
            double body = 0.55 * Math.sin(phase) + 0.21 * Math.sin(phase * 1.97 + 0.31 * variant);
            double transientEnv = Math.exp(-x * (wave ? 8.0 : 15.0));
            double burst = transientEnv * (0.55 * hp + 0.23 * Math.sin(2.0 * Math.PI * (900.0 - 420.0 * x) * t));
            double env = expEnv(x, 0.0, 0.92, wave ? 5.3 : 8.4);
            signal[i] = env * (body + 0.52 * lp) + burst;
        }
    }

    private static void synthImpact(double[] signal, int sampleRate, SfxManifest.EventSpec spec, int variant, Random rng) {
        int frames = signal.length;
        String eventId = spec.eventId().toLowerCase(Locale.US);
        boolean explosion = spec.eventId().contains("explosion");
        boolean shield = spec.eventId().contains("shield");
        boolean hull = eventId.contains("hull");
        boolean beam = eventId.contains("beam");
        boolean energy = eventId.contains("energy");
        boolean explosive = eventId.contains("explosive");
        boolean kinetic = eventId.contains("kinetic");

        double phase = 0.0;
        double phaseRingA = 0.0;
        double phaseRingB = 0.0;
        double lpLow = 0.0;
        double lpMid = 0.0;
        double lpFast = 0.0;
        double hpState = 0.0;
        for (int i = 0; i < frames; i++) {
            double x = i / (double) Math.max(1, frames - 1);
            double t = i / (double) sampleRate;
            double noise = randSigned(rng);
            hpState += 0.22 * (noise - hpState);
            double hp = noise - hpState;
            lpLow += 0.028 * (noise - lpLow);
            lpMid += 0.11 * (noise - lpMid);
            lpFast += 0.20 * (noise - lpFast);
            double debris = 0.55 * lpMid + 0.28 * (noise - lpMid);

            if (explosion) {
                double freq = Math.max(34.0, 74.0 * (1.0 - 0.72 * x));
                phase += (2.0 * Math.PI * freq) / sampleRate;
                double thump = Math.sin(phase) * Math.exp(-x * 4.2);
                double env = expEnv(x, 0.0, 0.95, 4.0);
                signal[i] = env * (0.64 * thump + 0.58 * debris + 0.35 * lpLow);
                continue;
            }

            if (shield) {
                double freq = Math.max(40.0, 160.0 * (1.0 - 0.70 * x));
                phase += (2.0 * Math.PI * freq) / sampleRate;
                double thump = Math.sin(phase) * Math.exp(-x * 7.8);
                double ring = 0.30 * Math.sin(2.0 * Math.PI * (400.0 - 260.0 * x) * t) * Math.exp(-x * 11.0);
                double env = expEnv(x, 0.0, 0.95, 6.8);
                signal[i] = env * (0.64 * thump + 0.58 * debris + ring + 0.35 * lpLow);
                continue;
            }

            if (hull) {
                double impactFreq = beam ? 120.0 : 96.0;
                phase += (2.0 * Math.PI * Math.max(42.0, impactFreq * (1.0 - 0.67 * x))) / sampleRate;
                double lowBody = Math.sin(phase) * Math.exp(-x * (beam ? 9.2 : 7.5));

                double chirpStart = beam ? 2220.0 : (energy ? 1980.0 : 1780.0);
                double chirpEnd = beam ? 760.0 : (energy ? 640.0 : 520.0);
                double chirpFreq = lerp(chirpStart, chirpEnd, Math.pow(x, 0.58));
                double bandCrackle = lpFast - lpLow;
                double crack = (0.62 * bandCrackle + 0.20 * Math.sin(2.0 * Math.PI * chirpFreq * t + variant * 0.33) + 0.18 * lpMid)
                        * Math.exp(-x * (beam ? 17.0 : 14.2));

                double ringAFreq = beam ? 930.0 : (kinetic ? 760.0 : 820.0);
                double ringBFreq = beam ? 620.0 : (explosive ? 540.0 : 580.0);
                phaseRingA += (2.0 * Math.PI * Math.max(110.0, ringAFreq * (1.0 - 0.78 * x))) / sampleRate;
                phaseRingB += (2.0 * Math.PI * Math.max(95.0, ringBFreq * (1.0 - 0.65 * x))) / sampleRate;
                double ring = 0.52 * Math.sin(phaseRingA + 0.2 * variant) * Math.exp(-x * (beam ? 6.2 : 5.8))
                        + 0.35 * Math.sin(phaseRingB + 0.4) * Math.exp(-x * (beam ? 4.8 : 4.5));

                double grit = (0.58 * lpMid) * Math.exp(-x * 5.8);
                double env = expEnv(x, 0.0, 0.96, beam ? 3.4 : 4.2);
                signal[i] = env * (0.44 * crack + 0.56 * ring + 0.31 * lowBody + 0.26 * grit);
                continue;
            }

            double freq = Math.max(42.0, 92.0 * (1.0 - 0.70 * x));
            phase += (2.0 * Math.PI * freq) / sampleRate;
            double thump = Math.sin(phase) * Math.exp(-x * 7.4);
            double env = expEnv(x, 0.0, 0.95, 6.5);
            signal[i] = env * (0.60 * thump + 0.50 * debris + 0.18 * hp);
        }
    }

    private static void synthHazard(double[] signal, int sampleRate, SfxManifest.EventSpec spec, int variant, Random rng) {
        int frames = signal.length;
        boolean suppression = spec.eventId().contains("suppression");
        boolean spread = spec.eventId().contains("spread");
        double lp = 0.0;
        double hpState = 0.0;
        double pop = 0.0;
        for (int i = 0; i < frames; i++) {
            double x = i / (double) Math.max(1, frames - 1);
            double noise = randSigned(rng);
            lp += 0.08 * (noise - lp);
            hpState += 0.42 * (noise - hpState);
            double hp = noise - hpState;
            if (rng.nextDouble() < (spread ? 0.015 : 0.010)) {
                pop = 0.65 + rng.nextDouble() * 0.4;
            }
            pop *= spread ? 0.972 : 0.962;
            double crackle = pop * (0.65 * hp + 0.35 * randSigned(rng));
            double air = suppression ? 0.65 * hp : 0.35 * hp;
            double roar = suppression ? 0.25 * lp : 0.62 * lp;
            double env = suppression ? expEnv(x, 0.0, 0.98, 3.8) : expEnv(x, 0.0, 0.98, 5.1);
            signal[i] = env * (roar + air + crackle);
        }
    }

    private static void synthSubsystem(double[] signal, int sampleRate, SfxManifest.EventSpec spec, int variant, Random rng) {
        int frames = signal.length;
        double phase = 0.0;
        double lp = 0.0;
        boolean reactor = spec.eventId().contains("reactor");
        boolean shields = spec.eventId().contains("shields");
        double base = reactor ? 190.0 : (shields ? 260.0 : 230.0);
        double rate = reactor ? 2.5 : 2.0;
        for (int i = 0; i < frames; i++) {
            double t = i / (double) sampleRate;
            double x = i / (double) Math.max(1, frames - 1);
            double pulse = Math.pow(Math.max(0.0, Math.sin(2.0 * Math.PI * rate * t + variant * 0.33)), 2.2);
            double freq = base + 180.0 * pulse;
            phase += (2.0 * Math.PI * freq) / sampleRate;
            double tone = (0.56 * Math.sin(phase) + 0.24 * Math.sin(phase * 1.5 + 0.4)) * pulse;
            double noise = randSigned(rng);
            lp += 0.18 * (noise - lp);
            double thunk = Math.exp(-x * 18.0) * (0.42 * lp);
            double env = expEnv(x, 0.0, 0.94, 3.5);
            signal[i] = env * (tone + 0.42 * lp) + thunk;
        }
    }

    private static void synthAmbience(double[] signal, int sampleRate, SfxManifest.EventSpec spec, int variant, Random rng) {
        int frames = signal.length;
        double phaseA = 0.0;
        double phaseB = 0.0;
        double phaseC = 0.0;
        double phaseAir = 0.0;
        boolean engine = spec.eventId().contains("engine");
        boolean station = spec.eventId().contains("station");
        double pulsePhase = variant * 0.7 + Math.abs(spec.eventId().hashCode() % 17);
        for (int i = 0; i < frames; i++) {
            double t = i / (double) sampleRate;
            double x = i / (double) Math.max(1, frames - 1);

            double slowMotion = 0.65 + 0.35 * Math.sin(2.0 * Math.PI * (engine ? 0.22 : 0.11) * t + pulsePhase);
            double fA = engine ? 42.0 : (station ? 34.0 : 30.0);
            double fB = engine ? 68.0 : (station ? 56.0 : 48.0);
            double fC = engine ? 102.0 : (station ? 86.0 : 74.0);
            double fAir = engine ? 144.0 : (station ? 132.0 : 120.0);
            phaseA += (2.0 * Math.PI * fA) / sampleRate;
            phaseB += (2.0 * Math.PI * fB) / sampleRate;
            phaseC += (2.0 * Math.PI * fC) / sampleRate;
            phaseAir += (2.0 * Math.PI * fAir) / sampleRate;

            double tonalBed = 0.58 * Math.sin(phaseA + 0.13 * variant)
                    + 0.28 * Math.sin(phaseB + 0.41)
                    + 0.14 * Math.sin(phaseC + 0.93);
            double enginePulse = engine
                    ? 0.16 * Math.sin(2.0 * Math.PI * 0.54 * t + pulsePhase)
                    + 0.07 * Math.sin(2.0 * Math.PI * 1.08 * t + 0.5 * pulsePhase)
                    : 0.0;
            double airyTone = (station ? 0.020 : 0.014) * Math.sin(phaseAir + 0.5 * Math.sin(2.0 * Math.PI * 0.05 * t + 0.2));
            signal[i] = tonalBed * (0.24 + 0.22 * slowMotion) + enginePulse + airyTone;

            // Ensure loop seam has matching endpoints without flattening the whole tail.
            if (i == frames - 1) {
                double diff = signal[i] - signal[0];
                for (int j = 0; j < frames; j++) {
                    double ramp = j / (double) Math.max(1, frames - 1);
                    signal[j] -= diff * ramp;
                }
            }

            // Very light edge smoothing for click-free looping.
            double edge = Math.min(1.0, Math.min(x / 0.01, (1.0 - x) / 0.01));
            signal[i] *= Math.max(0.0, edge);
        }
    }

    private static long seed(SfxManifest.EventSpec spec, int variant) {
        long h = 1469598103934665603L;
        h ^= (spec == null ? 0 : spec.eventId().hashCode());
        h *= 1099511628211L;
        h ^= (spec == null ? 0 : spec.folder().hashCode());
        h *= 1099511628211L;
        h ^= (long) variant * 0x9E3779B97F4A7C15L;
        h ^= ((long) (spec == null ? 0 : spec.category().ordinal()) << 33);
        return h;
    }

    private static void normalize(double[] signal, double peakTarget) {
        if (signal == null || signal.length == 0) return;
        double peak = 0.0;
        for (double v : signal) {
            peak = Math.max(peak, Math.abs(v));
        }
        if (peak < 1e-8) return;
        double gain = peakTarget / peak;
        for (int i = 0; i < signal.length; i++) {
            signal[i] = Math.tanh(signal[i] * gain * 1.15);
        }
    }

    private static byte[] toPcm16(double[] signal) {
        byte[] data = new byte[Math.max(0, signal.length * 2)];
        for (int i = 0; i < signal.length; i++) {
            double clamped = Math.max(-1.0, Math.min(1.0, signal[i]));
            short s = (short) Math.round(clamped * Short.MAX_VALUE);
            data[i * 2] = (byte) (s & 0xFF);
            data[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
        }
        return data;
    }

    private static double expEnv(double x, double attackStart, double releaseStart, double releaseCurve) {
        double attack = smoothStep(attackStart, 0.07, x);
        double releasePos = Math.max(0.0, (x - releaseStart) / Math.max(1e-6, 1.0 - releaseStart));
        double release = Math.exp(-releasePos * releaseCurve);
        return Math.max(0.0, Math.min(1.0, attack * release));
    }

    private static double smoothStep(double edge0, double edge1, double x) {
        double t = (x - edge0) / Math.max(1e-6, edge1 - edge0);
        t = Math.max(0.0, Math.min(1.0, t));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double randSigned(Random rng) {
        return rng.nextDouble() * 2.0 - 1.0;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * Math.max(0.0, Math.min(1.0, t));
    }
}
