import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
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
    private static final int MAX_AUDIO_EVENT_LOG = 8192;
    private static final double WORLD_SFX_HEARING_RADIUS = 1400.0;
    private static final double WORLD_SFX_HEARING_RADIUS2 = WORLD_SFX_HEARING_RADIUS * WORLD_SFX_HEARING_RADIUS;
    private static final Random RNG = new Random();
    private static final WeakHashMap<GameContext, RuntimeState> STATE = new WeakHashMap<>();
    private static final Map<String, Double> VOICE_COOLDOWN_SEC_BY_KEY = new HashMap<>();
    private static final ExecutorService PLAYBACK_EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "game-audio");
        t.setDaemon(true);
        return t;
    });
    private static final List<Clip> ACTIVE_CLIPS = Collections.synchronizedList(new ArrayList<>());
    private static volatile boolean TELEMETRY_ONLY = false;

    private static volatile Clip ambientClip;

    private AudioSystem() {}

    private enum VoiceCue {
        CAPTAIN_COMBAT_START("captain", "combat_start", 6.0, 3, 2,
                "All stations, battle posture.",
                "All hands, combat stations."),
        CAPTAIN_COMBAT_END("captain", "combat_end", 6.0, 2, 1,
                "Area secure. Stand down from combat alert."),
        CAPTAIN_ORDER_PUSH("captain", "order_push", 4.0, 2, 3,
                "Press the attack now.",
                "Advance and maintain pressure.",
                "Push forward. Do not let them regroup."),
        CAPTAIN_ORDER_RETREAT("captain", "order_retreat", 4.0, 3, 3,
                "Break contact and fall back.",
                "Disengage and regroup.",
                "Retreat vector now."),
        CAPTAIN_ORDER_ESCORT("captain", "order_escort", 4.0, 2, 3,
                "Escort posture. Protect the objective.",
                "Form escort screen around the asset.",
                "Escort detail, tighten formation."),
        CAPTAIN_ORDER_DEFEND("captain", "order_defend", 4.0, 2, 3,
                "Hold the line. Defensive posture.",
                "Defensive stations. Hold position.",
                "Maintain defense coverage."),

        HELM_INTERCEPT("helm", "intercept", 4.0, 1, 3,
                "Intercept course set.",
                "Course laid in for intercept.",
                "Intercept vector confirmed."),
        HELM_EVASIVE("helm", "evasive", 4.0, 2, 3,
                "Executing evasive pattern.",
                "Evasive maneuvers underway.",
                "Evasive profile engaged."),
        HELM_RTB("helm", "rtb", 5.0, 2, 2,
                "Returning to base vector.",
                "Base return course plotted."),

        TACTICAL_TARGET_LOCK("tactical", "target_lock", 1.8, 1, 3,
                "Target lock confirmed.",
                "Lock acquired.",
                "Weapons lock solid."),
        TACTICAL_TARGET_LOST("tactical", "target_lost", 1.8, 1, 3,
                "Target lock lost.",
                "Lock broken.",
                "Contact lost from lock."),
        TACTICAL_MISSILES_INBOUND("tactical", "missiles_inbound", 2.8, 3, 3,
                "Missiles inbound.",
                "Incoming missiles, bearing update.",
                "Missile threat, impact window closing."),

        ENGINEERING_SHIELDS_LOW("engineering", "shields_low", 4.5, 3, 3,
                "Shield integrity critical.",
                "Shields are failing.",
                "Shield reserves below safe limits."),
        ENGINEERING_REACTOR_HIT("engineering", "reactor_hit", 4.5, 3, 3,
                "Reactor section damaged.",
                "Reactor taking damage.",
                "Reactor instability rising."),
        ENGINEERING_REPAIRS_STARTED("engineering", "repairs_started", 4.5, 2, 3,
                "Damage control underway.",
                "Repair teams are moving.",
                "Engineering teams commencing repairs."),
        ENGINEERING_REPAIRS_COMPLETED("engineering", "repairs_completed", 4.5, 2, 3,
                "Damage control complete.",
                "Repairs complete.",
                "Repair cycle finished."),

        SCIENCE_NEW_CONTACT("science", "new_contact", 4.0, 2, 2,
                "New hostile contact detected.",
                "Additional contact on sensors."),
        SCIENCE_JAMMED("science", "jammed", 5.5, 2, 2,
                "Sensors are being jammed.",
                "Electronic interference detected."),
        SCIENCE_SCAN_COMPLETE("science", "scan_complete", 4.0, 1, 1,
                "Scan complete.");

        final String role;
        final String eventId;
        final String cooldownKey;
        final String[] captions;
        final double cooldownSec;
        final int priority;
        final int requiredVariants;

        VoiceCue(String role, String eventId, double cooldownSec, int priority, int requiredVariants, String... captions) {
            this.role = role;
            this.eventId = eventId;
            this.cooldownKey = role + "." + eventId;
            this.cooldownSec = cooldownSec;
            this.priority = priority;
            this.requiredVariants = Math.max(1, requiredVariants);
            if (captions == null || captions.length == 0) {
                this.captions = new String[]{eventId};
            } else {
                this.captions = captions.clone();
            }
            VOICE_COOLDOWN_SEC_BY_KEY.put(this.cooldownKey, this.cooldownSec);
        }

        String roleLabel() {
            return role.toUpperCase(Locale.US);
        }

        String captionForVariant(int variantIndex) {
            if (captions.length == 0) return eventId;
            int idx = Math.floorMod(variantIndex, captions.length);
            return captions[idx];
        }

        int captionVariantCount() {
            return Math.max(1, captions.length);
        }
    }

    private enum SfxCue {
        UI_OPEN("ui.open"),
        UI_CLOSE("ui.close"),
        WEAPON_PRIMARY("weapon.primary_fire"),
        WEAPON_SECONDARY("weapon.secondary_fire"),
        WEAPON_WAVE("weapon.wave_fire"),
        IMPACT_EXPLOSION("impact.explosion");

        final String eventId;

        SfxCue(String eventId) {
            this.eventId = eventId;
        }
    }

    private static final class RuntimeState {
        Ship lastLockedTarget;
        boolean hadCombatContact;
        boolean missilesInbound;
        boolean lastScienceJamming;
        boolean lastRepairsActive;
        int hostileContactCount;
        double scienceContactMemoryUntilSec = 0.0;
        double lastShieldFrac;
        double lastReactorFrac;
        double lastHp;
        double lastShield;
        int lastExplosionCount;
        GameContext.HelmMode lastHelmMode;
        GameContext.CaptainDirective lastCaptainDirective;

        final EnumMap<VoiceCue, Double> voiceCooldownUntil = new EnumMap<>(VoiceCue.class);
        final Map<String, Double> sfxCooldownUntil = new HashMap<>();
        final Map<String, Double> voiceDedupeUntil = new HashMap<>();
        final Map<String, Double> roleThrottleUntil = new HashMap<>();
        final Map<Integer, Double> scienceKnownContactsUntil = new HashMap<>();
        final Map<String, Integer> lastVariantByKey = new HashMap<>();
        final Map<String, Integer> lastSfxVariantByEvent = new HashMap<>();
        final Map<String, Integer> voiceDispatchByEvent = new HashMap<>();
        final Map<String, Integer> voiceDropByReason = new HashMap<>();
        final EnumMap<Ship.InternalSystem, Double> lastSystemFractions =
                new EnumMap<>(Ship.InternalSystem.class);
        final EnumMap<ShipRoomLayout.RoomId, Double> lastRoomFireIntensity =
                new EnumMap<>(ShipRoomLayout.RoomId.class);

        int voiceDispatchCount = 0;
        int voiceDropCount = 0;
        int activeVoicePriority = 0;
        double voicePriorityUntilSec = 0.0;

        static RuntimeState seed(GameContext ctx) {
            RuntimeState s = new RuntimeState();
            if (ctx != null && ctx.player != null) {
                s.lastLockedTarget = ctx.lockedTarget;
                List<Ship> visibleHostiles = visibleHostiles(ctx);
                s.hadCombatContact = !visibleHostiles.isEmpty();
                s.missilesInbound = hasMissilesInbound(ctx);
                s.lastScienceJamming = ctx.scienceJamming;
                s.lastRepairsActive = repairsActive(ctx);
                s.hostileContactCount = visibleHostiles.size();
                s.lastShieldFrac = shieldFrac(ctx.player);
                s.lastReactorFrac = reactorFrac(ctx.player);
                s.lastHp = ctx.player.hp;
                s.lastShield = ctx.player.shield;
                s.lastExplosionCount = explosionCountNearPlayer(ctx);
                s.lastHelmMode = ctx.helmMode;
                s.lastCaptainDirective = ctx.captainDirective;
                double seededUntil = nowSec() + 12.0;
                for (Ship hostile : visibleHostiles) {
                    if (hostile == null) continue;
                    s.scienceKnownContactsUntil.put(hostile.id, seededUntil);
                }
                s.scienceContactMemoryUntilSec = seededUntil;
                for (Ship.InternalSystem system : Ship.InternalSystem.values()) {
                    s.lastSystemFractions.put(system, ctx.player.systemHealthFraction(system));
                }
                for (Ship.RoomStatus room : ctx.player.roomStatusSnapshot()) {
                    if (room == null || room.roomId == null) continue;
                    s.lastRoomFireIntensity.put(room.roomId, room.fireIntensity);
                }
            }
            return s;
        }
    }

    private static final class AssetLibrary {
        private static final File ROOT_AUDIO = new File("assets/audio");
        private static final File ROOT_VOICE = new File("assets/voice");
        private static final Map<String, List<File>> CACHE = new HashMap<>();
        private static final int MAX_RESOURCE_VARIANTS = 16;

        private AssetLibrary() {}

        static VoicePick pickVoice(String role, String eventId, int preferredVariantIndex) {
            if (role == null || eventId == null) return null;
            String key = "voice/" + role.toLowerCase(Locale.US) + "/" + eventId.toLowerCase(Locale.US);
            List<File> files = CACHE.computeIfAbsent(key, k -> scan(new File(ROOT_VOICE, role), eventId));
            if (!files.isEmpty()) {
                int idx = Math.floorMod(preferredVariantIndex, files.size());
                return new VoicePick(files.get(idx), bundledVariantPath("voice", role, eventId, idx), idx, files.size());
            }
            List<String> bundled = scanBundled("voice", role, eventId);
            if (bundled.isEmpty()) return null;
            int idx = Math.floorMod(preferredVariantIndex, bundled.size());
            return new VoicePick(null, bundled.get(idx), idx, bundled.size());
        }

        static int voiceVariantCount(String role, String eventId) {
            if (role == null || eventId == null) return 0;
            String key = "voice/" + role.toLowerCase(Locale.US) + "/" + eventId.toLowerCase(Locale.US);
            List<File> files = CACHE.computeIfAbsent(key, k -> scan(new File(ROOT_VOICE, role), eventId));
            if (!files.isEmpty()) return files.size();
            return scanBundled("voice", role, eventId).size();
        }

        static SfxPick pickSfx(SfxManifest.EventSpec spec, int preferredVariantIndex) {
            if (spec == null) return null;
            String folder = spec.folder();
            String eventPrefix = spec.filePrefix();
            String key = "audio/" + folder.toLowerCase(Locale.US) + "/" + eventPrefix.toLowerCase(Locale.US);
            List<File> files = CACHE.computeIfAbsent(key, k -> scan(new File(ROOT_AUDIO, folder), eventPrefix));
            if (!files.isEmpty()) {
                int idx = Math.floorMod(preferredVariantIndex, files.size());
                return new SfxPick(files.get(idx), bundledVariantPath("audio", folder, eventPrefix, idx), idx, files.size());
            }
            List<String> bundled = scanBundled("audio", folder, eventPrefix);
            if (bundled.isEmpty()) return null;
            int idx = Math.floorMod(preferredVariantIndex, bundled.size());
            return new SfxPick(null, bundled.get(idx), idx, bundled.size());
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

        private static List<String> scanBundled(String root, String folder, String eventId) {
            List<String> out = new ArrayList<>();
            String exact = "/" + root + "/" + folder + "/" + eventId.toLowerCase(Locale.US) + ".wav";
            if (resourceExists(exact)) out.add(exact);
            for (int i = 1; i <= MAX_RESOURCE_VARIANTS; i++) {
                String variant = String.format(Locale.US, "/%s/%s/%s_%02d.wav",
                        root, folder, eventId.toLowerCase(Locale.US), i);
                if (resourceExists(variant)) out.add(variant);
            }
            return out;
        }

        private static String bundledVariantPath(String root, String folder, String eventId, int variantIndex) {
            List<String> bundled = scanBundled(root, folder, eventId);
            if (bundled.isEmpty()) return null;
            int idx = Math.floorMod(variantIndex, bundled.size());
            return bundled.get(idx);
        }

        private static boolean resourceExists(String path) {
            return path != null && AudioSystem.class.getResource(path) != null;
        }

        record VoicePick(File file, String resourcePath, int variantIndex, int variantCount) {}
        record SfxPick(File file, String resourcePath, int variantIndex, int variantCount) {}
    }

    public static void update(GameContext ctx, double dt) {
        if (ctx == null) return;

        if (ctx.voiceCaptionT > 0.0) {
            ctx.voiceCaptionT = Math.max(0.0, ctx.voiceCaptionT - Math.max(0.0, dt));
            if (ctx.voiceCaptionT <= 0.0) ctx.voiceCaption = "";
        }
        ctx.decayPortraitExpressions(dt);

        RuntimeState st = stateFor(ctx);
        double now = nowSec();

        ensureAmbientLoop(ctx, st, now);

        if (ctx.player == null || !ctx.player.alive || ctx.player.dying || ctx.player.hp <= 0) return;

        processVoiceSignals(ctx, st, now);
        processImpactSignals(ctx, st, now);
        processHazardAndSubsystemSignals(ctx, st, now);
        applyAmbientMix(ctx);

        st.lastHp = ctx.player.hp;
        st.lastShield = ctx.player.shield;
        st.lastExplosionCount = explosionCountNearPlayer(ctx);
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

    public static void onWeaponPrimary(GameContext ctx, Ship source) {
        if (source == null) {
            triggerSfx(ctx, SfxCue.WEAPON_PRIMARY);
            return;
        }
        triggerSfx(ctx, SfxCue.WEAPON_PRIMARY, source.x, source.y);
    }

    public static void onWeaponSecondary(GameContext ctx) {
        triggerSfx(ctx, SfxCue.WEAPON_SECONDARY);
    }

    public static void onWeaponSecondary(GameContext ctx, Ship source) {
        if (source == null) {
            triggerSfx(ctx, SfxCue.WEAPON_SECONDARY);
            return;
        }
        triggerSfx(ctx, SfxCue.WEAPON_SECONDARY, source.x, source.y);
    }

    public static void onWeaponWave(GameContext ctx) {
        triggerSfx(ctx, SfxCue.WEAPON_WAVE);
    }

    public static void onWeaponWave(GameContext ctx, Ship source) {
        if (source == null) {
            triggerSfx(ctx, SfxCue.WEAPON_WAVE);
            return;
        }
        triggerSfx(ctx, SfxCue.WEAPON_WAVE, source.x, source.y);
    }

    public static void onShieldImpact(GameContext ctx, VFX.ImpactStyle style) {
        onShieldImpact(ctx, style, Double.NaN, Double.NaN);
    }

    public static void onShieldImpact(GameContext ctx, VFX.ImpactStyle style, double sourceX, double sourceY) {
        if (ctx == null) return;
        RuntimeState st = stateFor(ctx);
        triggerSfxEvent(ctx, st, shieldImpactEventId(style), nowSec(), sourceX, sourceY);
    }

    public static void onHullImpact(GameContext ctx, VFX.ImpactStyle style) {
        onHullImpact(ctx, style, Double.NaN, Double.NaN);
    }

    public static void onHullImpact(GameContext ctx, VFX.ImpactStyle style, double sourceX, double sourceY) {
        if (ctx == null) return;
        RuntimeState st = stateFor(ctx);
        triggerSfxEvent(ctx, st, hullImpactEventId(style), nowSec(), sourceX, sourceY);
    }

    public static void onExplosion(GameContext ctx) {
        onExplosion(ctx, Double.NaN, Double.NaN);
    }

    public static void onExplosion(GameContext ctx, double sourceX, double sourceY) {
        triggerSfx(ctx, SfxCue.IMPACT_EXPLOSION, sourceX, sourceY);
    }

    public static synchronized void setTelemetryOnly(boolean telemetryOnly) {
        TELEMETRY_ONLY = telemetryOnly;
        Clip clip = ambientClip;
        if (telemetryOnly && clip != null) {
            try {
                clip.stop();
                clip.close();
            } catch (Throwable ignored) {
            }
            if (ambientClip == clip) {
                ambientClip = null;
            }
        }
    }

    static double voiceCooldownSeconds(String cooldownKey) {
        if (cooldownKey == null) return 0.0;
        return VOICE_COOLDOWN_SEC_BY_KEY.getOrDefault(cooldownKey, 0.0);
    }

    public record VoiceEventSpec(
            String role,
            String eventId,
            int priority,
            double cooldownSec,
            int requiredVariants,
            int captionVariants,
            int assetVariants) {}

    public record VoiceTelemetrySnapshot(
            int dispatchCount,
            int dropCount,
            Map<String, Integer> dispatchByEvent,
            Map<String, Integer> dropsByReason) {}

    public static List<VoiceEventSpec> voiceEventMatrix() {
        List<VoiceEventSpec> out = new ArrayList<>();
        for (VoiceCue cue : VoiceCue.values()) {
            int assets = AssetLibrary.voiceVariantCount(cue.role, cue.eventId);
            out.add(new VoiceEventSpec(
                    cue.role,
                    cue.eventId,
                    cue.priority,
                    cue.cooldownSec,
                    cue.requiredVariants,
                    cue.captionVariantCount(),
                    assets
            ));
        }
        out.sort(Comparator.comparing(VoiceEventSpec::role).thenComparing(VoiceEventSpec::eventId));
        return out;
    }

    public static VoiceTelemetrySnapshot voiceTelemetry(GameContext ctx) {
        if (ctx == null) {
            return new VoiceTelemetrySnapshot(0, 0, Map.of(), Map.of());
        }
        RuntimeState st = stateFor(ctx);
        if (st == null) {
            return new VoiceTelemetrySnapshot(0, 0, Map.of(), Map.of());
        }
        return new VoiceTelemetrySnapshot(
                st.voiceDispatchCount,
                st.voiceDropCount,
                Collections.unmodifiableMap(new HashMap<>(st.voiceDispatchByEvent)),
                Collections.unmodifiableMap(new HashMap<>(st.voiceDropByReason))
        );
    }

    private static RuntimeState stateFor(GameContext ctx) {
        return STATE.computeIfAbsent(ctx, RuntimeState::seed);
    }

    private static void processVoiceSignals(GameContext ctx, RuntimeState st, double now) {
        List<Ship> visibleHostiles = visibleHostiles(ctx);
        int hostiles = visibleHostiles.size();
        if (!st.hadCombatContact && hostiles > 0) {
            emitVoice(ctx, st, VoiceCue.CAPTAIN_COMBAT_START, now);
        } else if (st.hadCombatContact && hostiles <= 0) {
            emitVoice(ctx, st, VoiceCue.CAPTAIN_COMBAT_END, now);
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
        } else if (!repairsNow && st.lastRepairsActive) {
            emitVoice(ctx, st, VoiceCue.ENGINEERING_REPAIRS_COMPLETED, now);
        }

        if (ctx.scienceJamming && !st.lastScienceJamming) {
            emitVoice(ctx, st, VoiceCue.SCIENCE_JAMMED, now);
        }

        pruneScienceContactMemory(st, now);
        int newlyDetectedContacts = 0;
        double memoryUntil = now + 12.0;
        for (Ship hostile : visibleHostiles) {
            if (hostile == null) continue;
            Double knownUntil = st.scienceKnownContactsUntil.get(hostile.id);
            if (knownUntil == null || knownUntil < now) {
                newlyDetectedContacts++;
            }
            st.scienceKnownContactsUntil.put(hostile.id, memoryUntil);
        }
        st.scienceContactMemoryUntilSec = memoryUntil;
        if (newlyDetectedContacts > 0) {
            emitVoice(ctx, st, VoiceCue.SCIENCE_NEW_CONTACT, now);
        }

        if (ctx.helmMode != st.lastHelmMode) {
            if (ctx.helmMode == GameContext.HelmMode.INTERCEPT) {
                emitVoice(ctx, st, VoiceCue.HELM_INTERCEPT, now);
            } else if (ctx.helmMode == GameContext.HelmMode.EVASIVE) {
                emitVoice(ctx, st, VoiceCue.HELM_EVASIVE, now);
            }
        }

        if (ctx.captainDirective != st.lastCaptainDirective) {
            if (ctx.captainDirective == GameContext.CaptainDirective.RTB) {
                emitVoice(ctx, st, VoiceCue.HELM_RTB, now);
                emitVoice(ctx, st, VoiceCue.CAPTAIN_ORDER_RETREAT, now);
            } else if (ctx.captainDirective == GameContext.CaptainDirective.ATTACK) {
                emitVoice(ctx, st, VoiceCue.CAPTAIN_ORDER_PUSH, now);
            } else if (ctx.captainDirective == GameContext.CaptainDirective.ESCORT) {
                emitVoice(ctx, st, VoiceCue.CAPTAIN_ORDER_ESCORT, now);
            } else if (ctx.captainDirective == GameContext.CaptainDirective.DEFEND) {
                emitVoice(ctx, st, VoiceCue.CAPTAIN_ORDER_DEFEND, now);
            } else if (ctx.captainDirective == GameContext.CaptainDirective.REPAIR
                    || ctx.captainDirective == GameContext.CaptainDirective.EMERGENCY) {
                emitVoice(ctx, st, VoiceCue.CAPTAIN_ORDER_RETREAT, now);
            }
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

    private static void pruneScienceContactMemory(RuntimeState st, double now) {
        if (st == null) return;
        if (st.scienceKnownContactsUntil.isEmpty()) return;
        st.scienceKnownContactsUntil.entrySet().removeIf(e -> e == null || e.getValue() == null || e.getValue() < now);
        if (st.scienceKnownContactsUntil.isEmpty()) {
            st.scienceContactMemoryUntilSec = 0.0;
        }
    }

    private static void processImpactSignals(GameContext ctx, RuntimeState st, double now) {
        if (ctx == null || ctx.player == null) return;

        int explosionsNow = explosionCountNearPlayer(ctx);
        if (explosionsNow > st.lastExplosionCount) {
            triggerSfx(ctx, st, SfxCue.IMPACT_EXPLOSION, now);
        }
        st.lastExplosionCount = explosionsNow;
    }

    private static void processHazardAndSubsystemSignals(GameContext ctx, RuntimeState st, double now) {
        if (ctx == null || st == null || ctx.player == null) return;
        Ship player = ctx.player;

        // Subsystem failure callouts/SFX.
        for (Ship.InternalSystem system : Ship.InternalSystem.values()) {
            double prev = st.lastSystemFractions.getOrDefault(system, 1.0);
            double curr = player.systemHealthFraction(system);
            if (prev > 1e-6 && curr <= 1e-6) {
                String ev = subsystemOfflineEventId(system);
                if (ev != null) triggerSfxEvent(ctx, st, ev, now);
            }
            st.lastSystemFractions.put(system, curr);
        }

        // Fire hazard lifecycle cues.
        List<Ship.RoomStatus> rooms = player.roomStatusSnapshot();
        for (Ship.RoomStatus room : rooms) {
            if (room == null || room.roomId == null) continue;
            double prev = st.lastRoomFireIntensity.getOrDefault(room.roomId, 0.0);
            double curr = Math.max(0.0, room.fireIntensity);

            if (prev <= 0.05 && curr > 0.05) {
                triggerSfxEvent(ctx, st, "hazard.fire_ignition", now);
            } else if (curr - prev > 0.35) {
                triggerSfxEvent(ctx, st, "hazard.fire_spread", now);
            } else if (prev > 0.25 && curr < prev - 0.20) {
                triggerSfxEvent(ctx, st, "hazard.fire_suppression", now);
            }
            st.lastRoomFireIntensity.put(room.roomId, curr);
        }
    }

    private static void emitVoice(GameContext ctx, RuntimeState st, VoiceCue cue, double now) {
        if (ctx == null || st == null || cue == null) return;

        Double cd = st.voiceCooldownUntil.get(cue);
        if (cd != null && now < cd) {
            noteVoiceDrop(st, cue, "cooldown");
            return;
        }
        Double dedupe = st.voiceDedupeUntil.get(cue.cooldownKey);
        if (dedupe != null && now < dedupe) {
            noteVoiceDrop(st, cue, "dedupe");
            return;
        }
        Double roleThrottle = st.roleThrottleUntil.get(cue.role);
        if (roleThrottle != null && now < roleThrottle && cue.priority < 3) {
            noteVoiceDrop(st, cue, "role_throttle");
            return;
        }
        if (now < st.voicePriorityUntilSec && cue.priority < st.activeVoicePriority) {
            noteVoiceDrop(st, cue, "priority_window");
            return;
        }

        st.voiceCooldownUntil.put(cue, now + Math.max(0.25, cue.cooldownSec));
        st.voiceDedupeUntil.put(cue.cooldownKey, now + Math.max(0.35, Math.min(2.4, cue.cooldownSec)));
        st.roleThrottleUntil.put(cue.role, now + roleThrottleSeconds(cue.priority));
        st.activeVoicePriority = cue.priority;
        st.voicePriorityUntilSec = now + 0.9;

        int fallbackVariantCount = cue.captionVariantCount();
        int variantIndex = chooseVariantIndex(st, cue.cooldownKey, fallbackVariantCount);
        String caption = cue.captionForVariant(variantIndex);

        double roleVol = voiceRoleVolume(ctx, cue.role);
        double roleVolGainDb = volumeToGainOffsetDb(roleVol);
        AssetLibrary.VoicePick voicePick = AssetLibrary.pickVoice(cue.role, cue.eventId, variantIndex);
        if (voicePick != null && (voicePick.file() != null || voicePick.resourcePath() != null)) {
            variantIndex = voicePick.variantIndex();
            playAssetAsync(voicePick.file(), voicePick.resourcePath(), false, -12.0 + roleVolGainDb);
        } else {
            double roleTone = switch (cue.role) {
                case "captain" -> 230.0;
                case "helm" -> 340.0;
                case "tactical" -> 270.0;
                case "engineering" -> 180.0;
                case "science" -> 300.0;
                default -> 260.0;
            };
            double variantTone = roleTone + variantIndex * 16.0 + RNG.nextDouble() * 4.0;
            int variantMs = 90 + variantIndex * 24;
            playToneAsync(variantTone, variantMs, -20.0 + roleVolGainDb, false);
        }
        st.lastVariantByKey.put(cue.cooldownKey, variantIndex);
        applyPortraitExpression(ctx, cue, variantIndex);

        if (ctx.voiceCaptionsEnabled) {
            ctx.voiceCaption = cue.roleLabel() + ": " + caption;
            ctx.voiceCaptionT = 1.8;
        }
        logAudioEvent(ctx, new AudioEvent(
                "voice." + cue.eventId,
                cue.priority,
                cue.cooldownKey,
                variantIndex,
                "voice",
                System.nanoTime()
        ));
        noteVoiceDispatch(st, cue);
    }

    private static void noteVoiceDrop(RuntimeState st, VoiceCue cue, String reason) {
        if (st == null || cue == null || reason == null) return;
        st.voiceDropCount++;
        String key = cue.eventId + ":" + reason;
        st.voiceDropByReason.put(key, st.voiceDropByReason.getOrDefault(key, 0) + 1);
    }

    private static void noteVoiceDispatch(RuntimeState st, VoiceCue cue) {
        if (st == null || cue == null) return;
        st.voiceDispatchCount++;
        st.voiceDispatchByEvent.put(cue.eventId, st.voiceDispatchByEvent.getOrDefault(cue.eventId, 0) + 1);
    }

    private static void triggerSfx(GameContext ctx, SfxCue cue) {
        RuntimeState st = (ctx == null) ? null : stateFor(ctx);
        triggerSfx(ctx, st, cue, nowSec());
    }

    private static void triggerSfx(GameContext ctx, SfxCue cue, double sourceX, double sourceY) {
        RuntimeState st = (ctx == null) ? null : stateFor(ctx);
        triggerSfx(ctx, st, cue, nowSec(), sourceX, sourceY);
    }

    private static void triggerSfx(GameContext ctx, RuntimeState st, SfxCue cue, double now) {
        if (cue == null || st == null) return;
        triggerSfxEvent(ctx, st, cue.eventId, now);
    }

    private static void triggerSfx(GameContext ctx, RuntimeState st, SfxCue cue, double now, double sourceX, double sourceY) {
        if (cue == null || st == null) return;
        triggerSfxEvent(ctx, st, cue.eventId, now, sourceX, sourceY);
    }

    private static void triggerSfxEvent(GameContext ctx, RuntimeState st, String eventId, double now) {
        triggerSfxEvent(ctx, st, eventId, now, Double.NaN, Double.NaN);
    }

    private static void triggerSfxEvent(GameContext ctx, RuntimeState st, String eventId, double now, double sourceX, double sourceY) {
        if (ctx == null || st == null || eventId == null || eventId.isBlank()) return;
        if (!shouldPlayWorldSfxAt(ctx, sourceX, sourceY)) return;
        SfxManifest.EventSpec spec = SfxManifest.byId(eventId);
        if (spec == null) return;

        Double cd = st.sfxCooldownUntil.get(spec.eventId());
        if (cd != null && now < cd) return;
        st.sfxCooldownUntil.put(spec.eventId(), now + Math.max(0.02, spec.cooldownSec()));

        int variants = Math.max(1, SfxManifest.variantCount(spec));
        int variant = chooseSfxVariantIndex(st, spec.eventId(), variants);
        AssetLibrary.SfxPick pick = AssetLibrary.pickSfx(spec, variant);
        boolean hasAsset = (pick != null && (pick.file() != null || pick.resourcePath() != null));
        if (hasAsset) {
            variant = pick.variantIndex();
            double gain = spec.gainDb() + sfxVoiceDuckingDb(ctx, spec.priority());
            playAssetAsync(pick.file(), pick.resourcePath(), false, gain);
        }
        st.lastSfxVariantByEvent.put(spec.eventId(), variant);

        logAudioEvent(ctx, new AudioEvent(
                "sfx." + spec.eventId(),
                spec.priority(),
                "sfx." + spec.eventId(),
                hasAsset ? variant : -1,
                hasAsset ? "sfx" : "sfx_missing",
                System.nanoTime()
        ));
    }

    private static synchronized void ensureAmbientLoop(GameContext ctx, RuntimeState st, double now) {
        if (ctx == null || st == null) return;
        if (TELEMETRY_ONLY) return;
        Clip clip = ambientClip;
        if (clip != null && clip.isOpen()) {
            if (!clip.isRunning()) clip.loop(Clip.LOOP_CONTINUOUSLY);
            return;
        }

        SfxManifest.EventSpec[] ambienceChoices = new SfxManifest.EventSpec[]{
                SfxManifest.byId("ambience.bridge_ambient"),
                SfxManifest.byId("ambience.engine_loop"),
                SfxManifest.byId("ambience.station_hum")
        };
        SfxManifest.EventSpec ambientSpec = ambienceChoices[RNG.nextInt(ambienceChoices.length)];
        AssetLibrary.SfxPick ambientPick = AssetLibrary.pickSfx(ambientSpec, RNG.nextInt(4));
        if (ambientPick != null && (ambientPick.file() != null || ambientPick.resourcePath() != null)) {
            ambientClip = createClipFromAsset(ambientPick.file(), ambientPick.resourcePath(),
                    ambientSpec == null ? -26.0 : ambientSpec.gainDb());
        } else {
            // No tone fallback for SFX/ambience; silence is preferred over placeholder beeps.
            ambientClip = null;
        }

        if (ambientClip != null) {
            ambientClip.loop(Clip.LOOP_CONTINUOUSLY);
            ambientClip.start();
        }
    }

    private static synchronized void applyAmbientMix(GameContext ctx) {
        Clip clip = ambientClip;
        if (clip == null || !clip.isOpen()) return;
        double target = -26.0;
        if (countHostiles(ctx) > 0) target = -23.5;
        if (ctx.voiceCaptionT > 0.0) target -= 4.5;
        applyGain(clip, target);
    }

    private static void playAssetAsync(File wav, String resourcePath, boolean loop, double gainDb) {
        if (TELEMETRY_ONLY) return;
        if ((wav == null || !wav.isFile()) && (resourcePath == null || resourcePath.isBlank())) return;
        PLAYBACK_EXEC.execute(() -> {
            Clip clip = createClipFromAsset(wav, resourcePath, gainDb);
            if (clip == null) return;
            if (loop) {
                clip.loop(Clip.LOOP_CONTINUOUSLY);
            }
            clip.start();
        });
    }

    private static void playToneAsync(double hz, int ms, double gainDb, boolean loop) {
        if (TELEMETRY_ONLY) return;
        PLAYBACK_EXEC.execute(() -> {
            Clip clip = createToneClip(hz, ms, gainDb, loop);
            if (clip == null) return;
            if (loop) {
                clip.loop(Clip.LOOP_CONTINUOUSLY);
            }
            clip.start();
        });
    }

    private static Clip createClipFromAsset(File wav, String resourcePath, double gainDb) {
        if (wav != null && wav.isFile()) {
            return createClipFromFile(wav, gainDb);
        }
        return createClipFromResource(resourcePath, gainDb);
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

    private static Clip createClipFromResource(String resourcePath, double gainDb) {
        if (resourcePath == null || resourcePath.isBlank()) return null;
        try (InputStream raw = AudioSystem.class.getResourceAsStream(resourcePath)) {
            if (raw == null) return null;
            try (AudioInputStream stream = javax.sound.sampled.AudioSystem.getAudioInputStream(new BufferedInputStream(raw))) {
                Clip clip = javax.sound.sampled.AudioSystem.getClip();
                installClipLifecycle(clip);
                clip.open(stream);
                applyGain(clip, gainDb);
                ACTIVE_CLIPS.add(clip);
                return clip;
            }
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
        return visibleHostiles(ctx).size();
    }

    private static List<Ship> visibleHostiles(GameContext ctx) {
        if (ctx == null || ctx.player == null || ctx.ships == null) return List.of();
        double maxRange = 1800.0 * Math.max(0.20, ctx.player.sensorRangeMultiplier());
        double maxRange2 = maxRange * maxRange;
        List<Ship> out = new ArrayList<>();
        for (Ship s : ctx.ships) {
            if (s == null || s == ctx.player) continue;
            if (!s.alive || s.dying || s.hp <= 0) continue;
            if (s.faction == null || s.faction.isFriendlyTo(ctx.player.faction)) continue;
            if (!TargetingSystem.isDetectableToObserver(ctx.player, s)) continue;
            double d2 = GameMath.dist2(s.x, s.y, ctx.player.x, ctx.player.y);
            if (d2 <= maxRange2) out.add(s);
        }
        return out;
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
        if (ship == null) return 1.0;
        double effectiveMax = ship.effectiveShieldCapacityMax();
        if (effectiveMax <= 0.0) return 1.0;
        return Math.max(0.0, Math.min(1.0, ship.shield / Math.max(1e-9, effectiveMax)));
    }

    private static double reactorFrac(Ship ship) {
        if (ship == null) return 1.0;
        return Math.max(0.0, Math.min(1.0, ship.systemHealthFraction(Ship.InternalSystem.REACTOR_CORE)));
    }

    private static int explosionCountNearPlayer(GameContext ctx) {
        try {
            if (Explosion.active == null) return 0;
            if (ctx == null || ctx.player == null) return Explosion.active.size();
            int count = 0;
            for (Explosion e : Explosion.active) {
                if (e == null) continue;
                if (GameMath.dist2(e.x, e.y, ctx.player.x, ctx.player.y) <= WORLD_SFX_HEARING_RADIUS2) count++;
            }
            return count;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static boolean shouldPlayWorldSfxAt(GameContext ctx, double sourceX, double sourceY) {
        if (!Double.isFinite(sourceX) || !Double.isFinite(sourceY)) return true;
        if (ctx == null || ctx.player == null) return true;
        return GameMath.dist2(sourceX, sourceY, ctx.player.x, ctx.player.y) <= WORLD_SFX_HEARING_RADIUS2;
    }

    private static double roleThrottleSeconds(int priority) {
        if (priority >= 3) return 0.15;
        if (priority == 2) return 0.38;
        return 0.65;
    }

    private static int chooseVariantIndex(RuntimeState st, String key, int variantCount) {
        if (variantCount <= 1 || st == null || key == null) return 0;
        int count = Math.max(1, variantCount);
        int last = st.lastVariantByKey.getOrDefault(key, -1);
        int idx = RNG.nextInt(count);
        if (idx == last) {
            idx = (idx + 1 + RNG.nextInt(Math.max(1, count - 1))) % count;
        }
        return idx;
    }

    private static int chooseSfxVariantIndex(RuntimeState st, String eventId, int variantCount) {
        if (variantCount <= 1 || st == null || eventId == null) return 0;
        int count = Math.max(1, variantCount);
        int last = st.lastSfxVariantByEvent.getOrDefault(eventId, -1);
        int idx = RNG.nextInt(count);
        if (idx == last) {
            idx = (idx + 1 + RNG.nextInt(Math.max(1, count - 1))) % count;
        }
        return idx;
    }

    private static double sfxVoiceDuckingDb(GameContext ctx, int sfxPriority) {
        if (ctx == null) return 0.0;
        if (ctx.voiceCaptionT <= 0.0) return 0.0;
        if (sfxPriority >= 3) return -1.0;
        if (sfxPriority == 2) return -2.5;
        return -4.0;
    }

    private static String shieldImpactEventId(VFX.ImpactStyle style) {
        if (style == null) return "impact.shield.kinetic";
        return switch (style) {
            case ENERGY -> "impact.shield.energy";
            case BEAM -> "impact.shield.beam";
            case EXPLOSIVE -> "impact.shield.explosive";
            default -> "impact.shield.kinetic";
        };
    }

    private static String hullImpactEventId(VFX.ImpactStyle style) {
        if (style == null) return "impact.hull.kinetic";
        return switch (style) {
            case ENERGY -> "impact.hull.energy";
            case BEAM -> "impact.hull.beam";
            case EXPLOSIVE -> "impact.hull.explosive";
            default -> "impact.hull.kinetic";
        };
    }

    private static String subsystemOfflineEventId(Ship.InternalSystem system) {
        if (system == null) return null;
        return switch (system) {
            case ENGINES, WARP_ENGINES -> "subsystem.engines_offline";
            case REACTOR_CORE -> "subsystem.reactor_offline";
            case SENSORS, BRIDGE -> "subsystem.sensors_offline";
            case WEAPONS, MAGAZINES -> "subsystem.weapons_offline";
            case SHIELDS -> "subsystem.shields_offline";
        };
    }

    private static double voiceRoleVolume(GameContext ctx, String role) {
        if (ctx == null) return 1.0;
        GameContext.CrewStation station = stationForRole(role);
        if (station == null) return 1.0;
        return ctx.voiceRoleVolume(station);
    }

    private static void applyPortraitExpression(GameContext ctx, VoiceCue cue, int variantIndex) {
        if (ctx == null || cue == null) return;
        GameContext.CrewStation station = stationForRole(cue.role);
        if (station == null) return;

        int priority = MathUtil.clamp(cue.priority, 1, 3);
        int variantBias = Math.floorMod(variantIndex, 2);
        int expression = MathUtil.clamp(priority + variantBias - 1, 1, 3);
        double holdSec = 1.15 + cue.priority * 0.28;
        ctx.setPortraitExpression(station, expression, holdSec);
    }

    private static GameContext.CrewStation stationForRole(String role) {
        if (role == null) return null;
        return switch (role) {
            case "captain" -> GameContext.CrewStation.CAPTAIN;
            case "helm" -> GameContext.CrewStation.HELM;
            case "tactical" -> GameContext.CrewStation.TACTICAL;
            case "engineering" -> GameContext.CrewStation.ENGINEERING;
            case "science" -> GameContext.CrewStation.SCIENCE;
            default -> null;
        };
    }

    private static double volumeToGainOffsetDb(double linearVolume) {
        double v = Math.max(0.0, Math.min(2.0, linearVolume));
        if (v <= 1e-4) return -80.0;
        return 20.0 * Math.log10(v);
    }

    private static double nowSec() {
        return System.nanoTime() * 1e-9;
    }

    private static void logAudioEvent(GameContext ctx, AudioEvent event) {
        if (ctx == null || event == null) return;
        if (ctx.audioEvents.size() >= MAX_AUDIO_EVENT_LOG) {
            ctx.audioEvents.remove(0);
        }
        ctx.audioEvents.add(event);
    }
}

