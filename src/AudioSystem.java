import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;

public final class AudioSystem {
    private static final Random RNG = new Random();
    private static final WeakHashMap<GameContext, RuntimeState> STATE = new WeakHashMap<>();
    private static final ExecutorService PLAYBACK_EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "game-audio");
        t.setDaemon(true);
        return t;
    });
    private static final List<Clip> ACTIVE_CLIPS = Collections.synchronizedList(new ArrayList<>());

    private static Clip ambientClip;

    private AudioSystem() {}

    private enum VoiceCue {
        CAPTAIN_COMBAT_START("captain", "combat_start", "All stations, battle posture.", 6.0, 3),

        HELM_INTERCEPT("helm", "intercept", "Intercept course set.", 4.0, 1),
        HELM_EVASIVE("helm", "evasive", "Executing evasive pattern.", 4.0, 2),
        HELM_RTB("helm", "rtb", "Returning to base vector.", 5.0, 2),

        TACTICAL_TARGET_LOCK("tactical", "target_lock", "Target lock confirmed.", 1.8, 1),
        TACTICAL_TARGET_LOST("tactical", "target_lost", "Target lock lost.", 1.8, 1),
        TACTICAL_MISSILES_INBOUND("tactical", "missiles_inbound", "Missiles inbound.", 2.8, 3),

        ENGINEERING_SHIELDS_LOW("engineering", "shields_low", "Shield integrity critical.", 4.5, 3),
        ENGINEERING_REACTOR_HIT("engineering", "reactor_hit", "Reactor section damaged.", 4.5, 3),
        ENGINEERING_REPAIRS_STARTED("engineering", "repairs_started", "Damage control underway.", 4.5, 2),

        SCIENCE_NEW_CONTACT("science", "new_contact", "New hostile contact detected.", 4.0, 2),
        SCIENCE_JAMMED("science", "jammed", "Sensors are being jammed.", 5.5, 2),
        SCIENCE_SCAN_COMPLETE("science", "scan_complete", "Scan complete.", 4.0, 1);

        final String role;
        final String eventId;
        final String caption;
        final double cooldownSec;
        final int priority;

        VoiceCue(String role, String eventId, String caption, double cooldownSec, int priority) {
            this.role = role;
            this.eventId = eventId;
            this.caption = caption;
            this.cooldownSec = cooldownSec;
            this.priority = priority;
        }

        String roleLabel() {
            return role.toUpperCase(Locale.US);
        }
    }

    private enum SfxCue {
        UI_OPEN("ui", "open", 760.0, 70, -18.0, 0.05),
        UI_CLOSE("ui", "close", 520.0, 70, -18.5, 0.05),

        WEAPON_PRIMARY("weapons", "primary_fire", 160.0, 52, -15.0, 0.04),
        WEAPON_SECONDARY("weapons", "secondary_fire", 100.0, 120, -13.0, 0.08),
        WEAPON_WAVE("weapons", "wave_fire", 64.0, 260, -10.0, 0.75),

        IMPACT_SHIELD("impacts", "shield_hit", 420.0, 92, -13.0, 0.05),
        IMPACT_HULL("impacts", "hull_hit", 210.0, 95, -12.0, 0.05),
        IMPACT_EXPLOSION("impacts", "explosion", 84.0, 210, -9.0, 0.24);

        final String folder;
        final String eventId;
        final double fallbackHz;
        final int fallbackMs;
        final double gainDb;
        final double cooldownSec;

        SfxCue(String folder, String eventId, double fallbackHz, int fallbackMs, double gainDb, double cooldownSec) {
            this.folder = folder;
            this.eventId = eventId;
            this.fallbackHz = fallbackHz;
            this.fallbackMs = fallbackMs;
            this.gainDb = gainDb;
            this.cooldownSec = cooldownSec;
        }
    }

    private static final class RuntimeState {
        Ship lastLockedTarget;
        boolean hadCombatContact;
        boolean missilesInbound;
        boolean lastScienceJamming;
        boolean lastRepairsActive;
        int hostileContactCount;
        double lastShieldFrac;
        double lastReactorFrac;
        double lastHp;
        double lastShield;
        int lastExplosionCount;
        GameContext.HelmMode lastHelmMode;
        GameContext.CaptainDirective lastCaptainDirective;

        final EnumMap<VoiceCue, Double> voiceCooldownUntil = new EnumMap<>(VoiceCue.class);
        final EnumMap<SfxCue, Double> sfxCooldownUntil = new EnumMap<>(SfxCue.class);

        int activeVoicePriority = 0;
        double voicePriorityUntilSec = 0.0;

        static RuntimeState seed(GameContext ctx) {
            RuntimeState s = new RuntimeState();
            if (ctx != null && ctx.player != null) {
                s.lastLockedTarget = ctx.lockedTarget;
                s.hadCombatContact = countHostiles(ctx) > 0;
                s.missilesInbound = hasMissilesInbound(ctx);
                s.lastScienceJamming = ctx.scienceJamming;
                s.lastRepairsActive = repairsActive(ctx);
                s.hostileContactCount = countHostiles(ctx);
                s.lastShieldFrac = shieldFrac(ctx.player);
                s.lastReactorFrac = reactorFrac(ctx.player);
                s.lastHp = ctx.player.hp;
                s.lastShield = ctx.player.shield;
                s.lastExplosionCount = explosionCount();
                s.lastHelmMode = ctx.helmMode;
                s.lastCaptainDirective = ctx.captainDirective;
            }
            return s;
        }
    }

    private static final class AssetLibrary {
        private static final File ROOT_AUDIO = new File("assets/audio");
        private static final File ROOT_VOICE = new File("assets/voice");
        private static final Map<String, List<File>> CACHE = new HashMap<>();

        private AssetLibrary() {}

        static File pickVoice(String role, String eventId) {
            if (role == null || eventId == null) return null;
            String key = "voice/" + role.toLowerCase(Locale.US) + "/" + eventId.toLowerCase(Locale.US);
            List<File> files = CACHE.computeIfAbsent(key, k -> scan(new File(ROOT_VOICE, role), eventId));
            if (files.isEmpty()) return null;
            return files.get(RNG.nextInt(files.size()));
        }

        static File pickSfx(String folder, String eventId) {
            if (folder == null || eventId == null) return null;
            String key = "audio/" + folder.toLowerCase(Locale.US) + "/" + eventId.toLowerCase(Locale.US);
            List<File> files = CACHE.computeIfAbsent(key, k -> scan(new File(ROOT_AUDIO, folder), eventId));
            if (files.isEmpty()) return null;
            return files.get(RNG.nextInt(files.size()));
        }

        private static List<File> scan(File dir, String eventId) {
            if (dir == null || !dir.isDirectory()) return List.of();
            String prefix = eventId.toLowerCase(Locale.US);
            File[] matches = dir.listFiles(f -> {
                if (f == null || !f.isFile()) return false;
                String n = f.getName().toLowerCase(Locale.US);
                if (!n.endsWith(".wav")) return false;
                return n.equals(prefix + ".wav") || n.startsWith(prefix + "_");
            });
            if (matches == null || matches.length == 0) return List.of();
            List<File> out = new ArrayList<>();
            Collections.addAll(out, matches);
            out.sort(Comparator.comparing(File::getName));
            return out;
        }
    }

    public static void update(GameContext ctx, double dt) {
        if (ctx == null) return;

        if (ctx.voiceCaptionT > 0.0) {
            ctx.voiceCaptionT = Math.max(0.0, ctx.voiceCaptionT - Math.max(0.0, dt));
            if (ctx.voiceCaptionT <= 0.0) ctx.voiceCaption = "";
        }

        RuntimeState st = stateFor(ctx);
        double now = nowSec();

        ensureAmbientLoop(ctx, st, now);

        if (ctx.player == null || !ctx.player.alive || ctx.player.dying || ctx.player.hp <= 0) return;

        processVoiceSignals(ctx, st, now);
        processImpactSignals(ctx, st, now);
        applyAmbientMix(ctx);

        st.lastHp = ctx.player.hp;
        st.lastShield = ctx.player.shield;
        st.lastExplosionCount = explosionCount();
    }

    public static void onUiOpen(GameContext ctx) {
        triggerSfx(ctx, SfxCue.UI_OPEN);
    }

    public static void onUiClose(GameContext ctx) {
        triggerSfx(ctx, SfxCue.UI_CLOSE);
    }

    public static void onWeaponPrimary(GameContext ctx) {
        triggerSfx(ctx, SfxCue.WEAPON_PRIMARY);
    }

    public static void onWeaponSecondary(GameContext ctx) {
        triggerSfx(ctx, SfxCue.WEAPON_SECONDARY);
    }

    public static void onWeaponWave(GameContext ctx) {
        triggerSfx(ctx, SfxCue.WEAPON_WAVE);
    }

    private static RuntimeState stateFor(GameContext ctx) {
        return STATE.computeIfAbsent(ctx, RuntimeState::seed);
    }

    private static void processVoiceSignals(GameContext ctx, RuntimeState st, double now) {
        int hostiles = countHostiles(ctx);
        if (!st.hadCombatContact && hostiles > 0) {
            emitVoice(ctx, st, VoiceCue.CAPTAIN_COMBAT_START, now);
        }

        Ship currentLock = validLockedTarget(ctx) ? ctx.lockedTarget : null;
        boolean lockGained = (st.lastLockedTarget == null && currentLock != null);
        boolean lockLost = (st.lastLockedTarget != null && currentLock == null);
        if (lockGained) {
            emitVoice(ctx, st, VoiceCue.TACTICAL_TARGET_LOCK, now);
            emitVoice(ctx, st, VoiceCue.SCIENCE_SCAN_COMPLETE, now);
        } else if (lockLost) {
            emitVoice(ctx, st, VoiceCue.TACTICAL_TARGET_LOST, now);
        }

        boolean missilesNow = hasMissilesInbound(ctx);
        if (missilesNow && !st.missilesInbound) {
            emitVoice(ctx, st, VoiceCue.TACTICAL_MISSILES_INBOUND, now);
        }

        double shieldNow = shieldFrac(ctx.player);
        if (shieldNow <= 0.28 && st.lastShieldFrac > 0.28) {
            emitVoice(ctx, st, VoiceCue.ENGINEERING_SHIELDS_LOW, now);
        }

        double reactorNow = reactorFrac(ctx.player);
        if (reactorNow < st.lastReactorFrac - 0.08 || (reactorNow < 0.55 && st.lastReactorFrac >= 0.55)) {
            emitVoice(ctx, st, VoiceCue.ENGINEERING_REACTOR_HIT, now);
        }

        boolean repairsNow = repairsActive(ctx);
        if (repairsNow && !st.lastRepairsActive) {
            emitVoice(ctx, st, VoiceCue.ENGINEERING_REPAIRS_STARTED, now);
        }

        if (ctx.scienceJamming && !st.lastScienceJamming) {
            emitVoice(ctx, st, VoiceCue.SCIENCE_JAMMED, now);
        }

        if (hostiles > st.hostileContactCount) {
            emitVoice(ctx, st, VoiceCue.SCIENCE_NEW_CONTACT, now);
        }

        if (ctx.helmMode != st.lastHelmMode) {
            if (ctx.helmMode == GameContext.HelmMode.INTERCEPT) {
                emitVoice(ctx, st, VoiceCue.HELM_INTERCEPT, now);
            } else if (ctx.helmMode == GameContext.HelmMode.EVASIVE) {
                emitVoice(ctx, st, VoiceCue.HELM_EVASIVE, now);
            }
        }

        if (ctx.captainDirective != st.lastCaptainDirective && ctx.captainDirective == GameContext.CaptainDirective.RTB) {
            emitVoice(ctx, st, VoiceCue.HELM_RTB, now);
        }

        st.hadCombatContact = hostiles > 0;
        st.hostileContactCount = hostiles;
        st.lastLockedTarget = currentLock;
        st.missilesInbound = missilesNow;
        st.lastScienceJamming = ctx.scienceJamming;
        st.lastRepairsActive = repairsNow;
        st.lastShieldFrac = shieldNow;
        st.lastReactorFrac = reactorNow;
        st.lastHelmMode = ctx.helmMode;
        st.lastCaptainDirective = ctx.captainDirective;
    }

    private static void processImpactSignals(GameContext ctx, RuntimeState st, double now) {
        if (ctx == null || ctx.player == null) return;

        if (ctx.player.shield < st.lastShield - 0.18) {
            triggerSfx(ctx, st, SfxCue.IMPACT_SHIELD, now);
        }
        if (ctx.player.hp < st.lastHp) {
            triggerSfx(ctx, st, SfxCue.IMPACT_HULL, now);
        }

        int explosionsNow = explosionCount();
        if (explosionsNow > st.lastExplosionCount) {
            triggerSfx(ctx, st, SfxCue.IMPACT_EXPLOSION, now);
        }
    }

    private static void emitVoice(GameContext ctx, RuntimeState st, VoiceCue cue, double now) {
        if (ctx == null || st == null || cue == null) return;

        Double cd = st.voiceCooldownUntil.get(cue);
        if (cd != null && now < cd) return;
        if (now < st.voicePriorityUntilSec && cue.priority < st.activeVoicePriority) return;

        st.voiceCooldownUntil.put(cue, now + Math.max(0.25, cue.cooldownSec));
        st.activeVoicePriority = cue.priority;
        st.voicePriorityUntilSec = now + 0.9;

        File wav = AssetLibrary.pickVoice(cue.role, cue.eventId);
        if (wav != null) {
            playFileAsync(wav, false, -12.0);
        } else {
            double roleTone = switch (cue.role) {
                case "captain" -> 230.0;
                case "helm" -> 340.0;
                case "tactical" -> 270.0;
                case "engineering" -> 180.0;
                case "science" -> 300.0;
                default -> 260.0;
            };
            playToneAsync(roleTone, 100, -20.0, false);
        }

        ctx.voiceCaption = cue.roleLabel() + ": " + cue.caption;
        ctx.voiceCaptionT = 1.8;
    }

    private static void triggerSfx(GameContext ctx, SfxCue cue) {
        RuntimeState st = (ctx == null) ? null : stateFor(ctx);
        triggerSfx(ctx, st, cue, nowSec());
    }

    private static void triggerSfx(GameContext ctx, RuntimeState st, SfxCue cue, double now) {
        if (cue == null || st == null) return;

        Double cd = st.sfxCooldownUntil.get(cue);
        if (cd != null && now < cd) return;
        st.sfxCooldownUntil.put(cue, now + Math.max(0.02, cue.cooldownSec));

        File wav = AssetLibrary.pickSfx(cue.folder, cue.eventId);
        if (wav != null) {
            playFileAsync(wav, false, cue.gainDb);
        } else {
            playToneAsync(cue.fallbackHz, cue.fallbackMs, cue.gainDb, false);
        }
    }

    private static void ensureAmbientLoop(GameContext ctx, RuntimeState st, double now) {
        if (ctx == null || st == null) return;
        if (ambientClip != null && ambientClip.isOpen()) {
            if (!ambientClip.isRunning()) ambientClip.loop(Clip.LOOP_CONTINUOUSLY);
            return;
        }

        File ambientFile = AssetLibrary.pickSfx("ambient", "bridge_ambient");
        if (ambientFile != null) {
            ambientClip = createClipFromFile(ambientFile, -26.0);
        } else {
            ambientClip = createToneClip(58.0, 8000, -30.0, true);
        }

        if (ambientClip != null) {
            ambientClip.loop(Clip.LOOP_CONTINUOUSLY);
            ambientClip.start();
        }
    }

    private static void applyAmbientMix(GameContext ctx) {
        if (ambientClip == null || !ambientClip.isOpen()) return;
        double target = -26.0;
        if (countHostiles(ctx) > 0) target = -23.5;
        if (ctx.voiceCaptionT > 0.0) target -= 4.5;
        applyGain(ambientClip, target);
    }

    private static void playFileAsync(File wav, boolean loop, double gainDb) {
        if (wav == null || !wav.isFile()) return;
        PLAYBACK_EXEC.execute(() -> {
            Clip clip = createClipFromFile(wav, gainDb);
            if (clip == null) return;
            if (loop) {
                clip.loop(Clip.LOOP_CONTINUOUSLY);
            }
            clip.start();
        });
    }

    private static void playToneAsync(double hz, int ms, double gainDb, boolean loop) {
        PLAYBACK_EXEC.execute(() -> {
            Clip clip = createToneClip(hz, ms, gainDb, loop);
            if (clip == null) return;
            if (loop) {
                clip.loop(Clip.LOOP_CONTINUOUSLY);
            }
            clip.start();
        });
    }

    private static Clip createClipFromFile(File wav, double gainDb) {
        try (AudioInputStream stream = javax.sound.sampled.AudioSystem.getAudioInputStream(wav)) {
            Clip clip = javax.sound.sampled.AudioSystem.getClip();
            installClipLifecycle(clip);
            clip.open(stream);
            applyGain(clip, gainDb);
            ACTIVE_CLIPS.add(clip);
            return clip;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Clip createToneClip(double hz, int ms, double gainDb, boolean loopIntent) {
        try {
            int sampleRate = 44100;
            int frames = Math.max(1, (int) Math.round(sampleRate * (ms / 1000.0)));
            byte[] data = new byte[frames * 2];
            double amp = loopIntent ? 0.10 : 0.28;
            for (int i = 0; i < frames; i++) {
                double t = i / (double) sampleRate;
                double wave = Math.sin(2.0 * Math.PI * hz * t);
                wave += 0.35 * Math.sin(2.0 * Math.PI * hz * 2.0 * t + 0.2);
                wave += 0.15 * Math.sin(2.0 * Math.PI * hz * 0.5 * t + 0.6);
                wave /= 1.5;
                short s = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(wave * amp * Short.MAX_VALUE)));
                data[i * 2] = (byte) (s & 0xff);
                data[i * 2 + 1] = (byte) ((s >> 8) & 0xff);
            }

            AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
            AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(data), format, frames);
            Clip clip = javax.sound.sampled.AudioSystem.getClip();
            installClipLifecycle(clip);
            clip.open(stream);
            applyGain(clip, gainDb);
            ACTIVE_CLIPS.add(clip);
            stream.close();
            return clip;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void installClipLifecycle(Clip clip) {
        if (clip == null) return;
        clip.addLineListener(event -> {
            if (event.getType() == LineEvent.Type.STOP) {
                if (clip != ambientClip) {
                    clip.close();
                }
            } else if (event.getType() == LineEvent.Type.CLOSE) {
                ACTIVE_CLIPS.remove(clip);
            }
        });
    }

    private static void applyGain(Clip clip, double gainDb) {
        if (clip == null) return;
        try {
            if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) return;
            FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            float clamped = (float) Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), gainDb));
            gain.setValue(clamped);
        } catch (Throwable ignored) {
            // Optional; some mixers do not expose gain controls.
        }
    }

    private static boolean validLockedTarget(GameContext ctx) {
        if (ctx == null || ctx.player == null || ctx.lockedTarget == null) return false;
        Ship t = ctx.lockedTarget;
        if (!t.alive || t.dying || t.hp <= 0) return false;
        if (t.faction == null || t.faction.isFriendlyTo(ctx.player.faction)) return false;
        return TargetingSystem.isDetectableToObserver(ctx.player, t);
    }

    private static int countHostiles(GameContext ctx) {
        if (ctx == null || ctx.player == null || ctx.ships == null) return 0;
        double maxRange = 1800.0 * Math.max(0.20, ctx.player.sensorRangeMultiplier());
        double maxRange2 = maxRange * maxRange;
        int count = 0;
        for (Ship s : ctx.ships) {
            if (s == null || s == ctx.player) continue;
            if (!s.alive || s.dying || s.hp <= 0) continue;
            if (s.faction == null || s.faction.isFriendlyTo(ctx.player.faction)) continue;
            if (!TargetingSystem.isDetectableToObserver(ctx.player, s)) continue;
            double d2 = GameMath.dist2(s.x, s.y, ctx.player.x, ctx.player.y);
            if (d2 <= maxRange2) count++;
        }
        return count;
    }

    private static boolean hasMissilesInbound(GameContext ctx) {
        if (ctx == null || ctx.player == null || ctx.projectiles == null) return false;
        for (Projectile p : ctx.projectiles) {
            if (!(p instanceof Missile m)) continue;
            if (!p.alive) continue;
            if (p.faction != null && p.faction.isFriendlyTo(ctx.player.faction)) continue;

            double d = Math.hypot(m.x - ctx.player.x, m.y - ctx.player.y);
            if (m.target == ctx.player && d <= 980.0) return true;
            if (d <= 460.0) return true;
        }
        return false;
    }

    private static boolean repairsActive(GameContext ctx) {
        if (ctx == null || ctx.player == null) return false;
        return ctx.engineeringMode == GameContext.EngineeringMode.DAMAGE_CONTROL
                || ctx.player.crewOrder == Ship.CrewOrder.DAMAGE_CONTROL;
    }

    private static double shieldFrac(Ship ship) {
        if (ship == null || ship.shieldMax <= 0.0) return 1.0;
        return Math.max(0.0, Math.min(1.0, ship.shield / Math.max(1e-9, ship.shieldMax)));
    }

    private static double reactorFrac(Ship ship) {
        if (ship == null) return 1.0;
        return Math.max(0.0, Math.min(1.0, ship.systemHealthFraction(Ship.InternalSystem.REACTOR_CORE)));
    }

    private static int explosionCount() {
        try {
            return (Explosion.active == null) ? 0 : Explosion.active.size();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static double nowSec() {
        return System.nanoTime() * 1e-9;
    }
}

