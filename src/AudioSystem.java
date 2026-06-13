import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final Map<String, Double> SFX_BURST_THROTTLE_UNTIL = new ConcurrentHashMap<>();
    private static final Map<String, Double> ONE_SHOT_ASSET_THROTTLE_UNTIL = new ConcurrentHashMap<>();
    private static final Map<String, byte[]> AUDIO_FILE_BYTES = new ConcurrentHashMap<>();
    private static final Map<String, byte[]> AUDIO_RESOURCE_BYTES = new ConcurrentHashMap<>();
    private static final boolean AUDIO_DISABLED = Boolean.getBoolean("codex.disableAudio");
    private static volatile boolean TELEMETRY_ONLY = AUDIO_DISABLED;

    private static volatile Clip ambientClip;
    private static volatile Clip greenWeaponLoopClip;
    private static volatile Clip warpSpoolLoopClip;
    private static final int MAX_ACTIVE_ONE_SHOTS = 16;
    private static final int MAX_ACTIVE_LOW_PRIORITY_ONE_SHOTS = 8;
    private static final double DUPLICATE_ONE_SHOT_SUPPRESS_SEC = 0.075;
    private static final double HOSTILE_CONTACT_REFRESH_SEC = 0.24;
    private static final double GREEN_WEAPON_LOOP_HOLD_SEC = 0.45;
    private static final double GREEN_SUPERWEAPON_FIRE_LOOP_HOLD_SEC = 5.2;
    private static final double WARP_LOOP_KEEPALIVE_SEC = 0.35;
    private static final boolean ALPHA_TEMPORARY_CREW_CHATTER_ENABLED = false;

    private AudioSystem() {}

    private enum VoiceCue {
        CAPTAIN_COMBAT_START("captain", "combat_start", 6.0, 3, 2,
                "All stations, prepare for combat.",
                "All hands, combat stations."),
        // NOTE: Using a new event id here intentionally avoids playing the legacy `combat_end` voice asset
        // (which included the removed wording). If/when new VO is recorded, drop it in as:
        // `assets/voice/captain/combat_end_clear_01.wav` (and variants).
        CAPTAIN_COMBAT_END("captain", "combat_end_clear", 6.0, 2, 1,
                "Area secure.",
                "Area secure. Maintain readiness."),
        CAPTAIN_ORDER_PUSH("captain", "order_push", 4.0, 2, 3,
                "Press the attack now.",
                "Advance and keep firing.",
                "Push forward. Do not let them regroup."),
        CAPTAIN_ORDER_RETREAT("captain", "order_retreat", 4.0, 3, 3,
                "Break contact and fall back.",
                "Disengage and regroup.",
                "Retreat vector now."),
        CAPTAIN_ORDER_ESCORT("captain", "order_escort", 4.0, 2, 3,
                "Escort the objective. Stay close.",
                "Form escort screen around the asset.",
                "Escort detail, tighten formation."),
        CAPTAIN_ORDER_DEFEND("captain", "order_defend", 4.0, 2, 3,
                "Hold the line. Defensive formation.",
                "Defensive stations. Hold position.",
                "Maintain defense coverage."),
        CAPTAIN_ORDER_MINE("captain", "order_mine", 4.5, 2, 3,
                "Mining group, proceed to extraction.",
                "Mining detail, move on the resource pocket.",
                "Cover the miners and hold the lane."),
        CAPTAIN_ORDER_REPAIR("captain", "order_repair", 4.5, 2, 3,
                "Damaged ships, peel off for repairs.",
                "Repair group, disengage and recover.",
                "Withdraw damaged hulls and begin damage control."),
        CAPTAIN_ORDER_RTB("captain", "order_rtb", 4.5, 2, 3,
                "All units, return to base.",
                "Return to base and prepare to dock.",
                "Base recovery pattern is now in effect."),

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
                "Scan complete."),

        // Phase 6: Fleet hub bridge chatter (low priority, quieter mix).
        CAPTAIN_FLEET_ORGANIZER_AMBIENT("captain", "fleet_organizer_ambient", 20.0, 1, 4, -18.0,
                "Fleet roster synced. Dock crews standing by.",
                "Hangar assignments updated.",
                "Contract board refreshed.",
                "Stores and hull plates accounted for."),
        CAPTAIN_XO_AMBIENT("captain", "xo_ambient", 22.0, 1, 4, -18.0,
                "XO confirms: crews ready to launch.",
                "Maintenance rotation complete.",
                "Damage teams are rested. For now.",
                "Command net is quiet. That's a gift."),
        TACTICAL_WEAPONRY_AMBIENT("tactical", "weaponry_ambient", 20.0, 1, 4, -18.5,
                "Weapon checks green across batteries.",
                "Magazines topped. Guidance links stable.",
                "Fire control reports tight tracking.",
                "CIWS arcs recalibrated."),
        ENGINEERING_STATION_AMBIENT("engineering", "station_ambient", 20.0, 1, 4, -18.5,
                "Reactor stable. Heat sinks cycling.",
                "Cooling loops holding pressure.",
                "Power buses balanced.",
                "We've got margin. Let's keep it."),
        HELM_FLIGHT_DECK_AMBIENT("helm", "flight_deck_ambient", 20.0, 1, 4, -18.5,
                "Flight crews standing by.",
                "Strike craft fuelled and armed.",
                "Launch rails cleared.",
                "Deck crew reports ready."),
        SCIENCE_CREW_AMBIENT("science", "crew_ambient", 20.0, 1, 4, -18.5,
                "Sensor net recalibrated.",
                "Comms traffic normalized.",
                "New navigation solutions uploaded.",
                "Fresh telemetry on the route."),

        CAPTAIN_BANTER("captain", "banter", 34.0, 1, 6, -17.0,
                "Keep the formation tight. The universe hates empty space.",
                "If you can hear me, you're still on the payroll.",
                "Let's not make engineering earn their keep today.",
                "Remember: escorts exist so we don't die heroically.",
                "Someone tell Tactical to stop naming missiles.",
                "If we lose this hull, we're walking home."),
        HELM_BANTER("helm", "banter", 34.0, 1, 6, -17.5,
                "If we could stop drifting into debris, I'd appreciate it.",
                "Thrusters respond... eventually.",
                "Next time we dock, I'm asking for a new nav console.",
                "I can thread this needle. Don't ask how.",
                "If you hear a scrape, no you didn't.",
                "Course is set. It's the universe that needs to cooperate."),
        TACTICAL_BANTER("tactical", "banter", 34.0, 1, 6, -17.5,
                "I am once again requesting we shoot from farther away.",
                "Missiles are expensive. Let's pretend that matters.",
                "Targeting solution is clean. Like my conscience.",
                "If it moves, it's hostile. If it doesn't, it's salvage.",
                "Guns charged. Please refrain from ramming.",
                "Fire control is ready. Try not to blink."),
        ENGINEERING_BANTER("engineering", "banter", 34.0, 1, 6, -17.5,
                "If you overload that bus again, I will find you.",
                "We can do miracles. Just not on schedule.",
                "Power is stable. Stop asking.",
                "I've got duct tape and faith. Pick one.",
                "Reactor's purring. Please don't wake it.",
                "If something sparks, it's probably fine."),
        SCIENCE_BANTER("science", "banter", 34.0, 1, 6, -17.5,
                "Sensors say 'bad idea'. Again.",
                "I can give you probabilities. Not guarantees.",
                "Signal interference is... charming.",
                "If you want certainty, stay docked.",
                "We are being watched. Statistically.",
                "New contact. Or old contact. Hard to say.");

        final String role;
        final String eventId;
        final String cooldownKey;
        final String[] captions;
        final double cooldownSec;
        final double gainDb;
        final int priority;
        final int requiredVariants;

        VoiceCue(String role, String eventId, double cooldownSec, int priority, int requiredVariants, String... captions) {
            this(role, eventId, cooldownSec, priority, requiredVariants, -12.0, captions);
        }

        VoiceCue(String role, String eventId, double cooldownSec, int priority, int requiredVariants, double gainDb, String... captions) {
            this.role = role;
            this.eventId = eventId;
            this.cooldownKey = role + "." + eventId;
            this.cooldownSec = cooldownSec;
            this.gainDb = gainDb;
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
        WEAPON_MISSILE_LAUNCH("weapon.missile_launch"),
        WEAPON_TORPEDO_LAUNCH("weapon.torpedo_launch"),
        WEAPON_CIWS_FIRE("weapon.ciws_fire"),
        FLIGHT_LAUNCH("flight.launch"),
        WARP_SPOOL_UP("warp.spool_up"),
        WARP_CHARGE_START("warp.charge_start"),
        WARP_EXIT("warp.exit"),
        IMPACT_EXPLOSION("impact.explosion"),
        IMPACT_SHIP_DEATH_MAJOR("impact.ship_death_major"),
        IMPACT_SHIELD_DAMAGE("impact.shield.damage"),
        IMPACT_HULL_DAMAGE("impact.hull.damage");

        final String eventId;

        SfxCue(String eventId) {
            this.eventId = eventId;
        }
    }

    private static final class RuntimeState {
        Ship lastLockedTarget;
        boolean hadCombatContact;
        boolean missilesInbound;
        boolean lastRepairsActive;
        int hostileContactCount;
        double hostileContactRefreshAtSec = 0.0;
        List<Ship> cachedVisibleHostiles = List.of();
        double scienceContactMemoryUntilSec = 0.0;
        double lastShieldFrac;
        double lastReactorFrac;
        double lastHp;
        double lastShield;
        int lastExplosionCount;
        int lastMajorDetonationCount;
        GameContext.HelmMode lastHelmMode;
        GameContext.CaptainDirective lastCaptainDirective;
        int lastFriendlyCommandShipId = -1;
        GameContext.FleetCommand lastFriendlyFleetCommand = null;
        GameContext.FleetFormation lastFriendlyFleetFormation = null;

        // Phase 6: Low-priority bridge chatter / banter in fleet hub.
        double fleetHubChatterNextSec = 0.0;
        VoiceCue fleetHubChatterFollowupCue = null;
        double fleetHubChatterFollowupAtSec = 0.0;

        final EnumMap<VoiceCue, Double> voiceCooldownUntil = new EnumMap<>(VoiceCue.class);
        final Map<String, Double> sfxCooldownUntil = new HashMap<>();
        final Map<String, Double> voiceDedupeUntil = new HashMap<>();
        final Map<String, Double> roleThrottleUntil = new HashMap<>();
        final Map<Integer, Double> scienceKnownContactsUntil = new HashMap<>();
        final Map<Integer, Boolean> lastSuperweaponChargingByShip = new HashMap<>();
        final Map<String, Integer> lastVariantByKey = new HashMap<>();
        final Map<String, Integer> lastSfxVariantByEvent = new HashMap<>();
        final Map<String, Integer> voiceDispatchByEvent = new HashMap<>();
        final Map<String, Integer> voiceDropByReason = new HashMap<>();
        final EnumMap<Ship.InternalSystem, Double> lastSystemFractions =
                new EnumMap<>(Ship.InternalSystem.class);
        final EnumMap<ShipRoomLayout.RoomId, Double> lastRoomFireIntensity =
                new EnumMap<>(ShipRoomLayout.RoomId.class);
        double greenWeaponLoopUntilSec = 0.0;
        String greenWeaponLoopEventId = "";
        double warpSpoolLoopUntilSec = 0.0;

        int voiceDispatchCount = 0;
        int voiceDropCount = 0;
        int activeVoicePriority = 0;
        double voicePriorityUntilSec = 0.0;

        static RuntimeState seed(GameContext ctx) {
            RuntimeState s = new RuntimeState();
            if (ctx != null && ctx.player != null) {
                s.lastLockedTarget = ctx.lockedTarget;
                List<Ship> visibleHostiles = visibleHostiles(ctx);
                double now = nowSec();
                s.cachedVisibleHostiles = List.copyOf(visibleHostiles);
                s.hostileContactRefreshAtSec = now + HOSTILE_CONTACT_REFRESH_SEC;
                s.hadCombatContact = !visibleHostiles.isEmpty();
                s.missilesInbound = hasMissilesInbound(ctx);
                s.lastRepairsActive = repairsActive(ctx);
                s.hostileContactCount = visibleHostiles.size();
                s.lastShieldFrac = shieldFrac(ctx.player);
                s.lastReactorFrac = reactorFrac(ctx.player);
                s.lastHp = ctx.player.hp;
                s.lastShield = ctx.player.shield;
                s.lastExplosionCount = explosionCountNearPlayer(ctx);
                s.lastMajorDetonationCount = majorDetonationCountNearPlayer(ctx);
                s.lastHelmMode = ctx.command.helmMode;
                s.lastCaptainDirective = ctx.command.captainDirective;
                Ship commandShip = friendlyCommandShip(ctx);
                if (commandShip != null) {
                    s.lastFriendlyCommandShipId = commandShip.id;
                    s.lastFriendlyFleetCommand = resolvedFleetCommand(ctx, commandShip);
                    s.lastFriendlyFleetFormation = resolvedFleetFormation(ctx, commandShip);
                }
                double seededUntil = now + 12.0;
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
                if (ctx.ships != null) {
                    for (Ship ship : ctx.ships) {
                        if (ship == null || !ship.hasSuperweapon) continue;
                        s.lastSuperweaponChargingByShip.put(ship.id, ship.isSuperweaponCharging());
                    }
                }
            }
            return s;
        }
    }

    private static final class AssetLibrary {
        private static final File ROOT_AUDIO = new File("assets/audio");
        private static final File ROOT_VOICE = new File("assets/voice");
        private static final Map<String, List<File>> FILE_CACHE = new ConcurrentHashMap<>();
        private static final Map<String, List<String>> BUNDLED_CACHE = new ConcurrentHashMap<>();
        private static final int MAX_RESOURCE_VARIANTS = 16;

        private AssetLibrary() {}

        static VoicePick pickVoice(String role, String eventId, int preferredVariantIndex) {
            if (role == null || eventId == null) return null;
            List<File> files = filesFor("voice", role, eventId, new File(ROOT_VOICE, role));
            if (!files.isEmpty()) {
                int idx = Math.floorMod(preferredVariantIndex, files.size());
                return new VoicePick(files.get(idx), null, idx, files.size());
            }
            List<String> bundled = bundledFor("voice", role, eventId);
            if (bundled.isEmpty()) return null;
            int idx = Math.floorMod(preferredVariantIndex, bundled.size());
            return new VoicePick(null, bundled.get(idx), idx, bundled.size());
        }

        static int voiceVariantCount(String role, String eventId) {
            if (role == null || eventId == null) return 0;
            List<File> files = filesFor("voice", role, eventId, new File(ROOT_VOICE, role));
            if (!files.isEmpty()) return files.size();
            return bundledFor("voice", role, eventId).size();
        }

        static int sfxVariantCount(SfxManifest.EventSpec spec) {
            if (spec == null) return 0;
            String folder = spec.folder();
            String eventPrefix = spec.filePrefix();
            List<File> files = filesFor("audio", folder, eventPrefix, new File(ROOT_AUDIO, folder));
            if (!files.isEmpty()) return files.size();
            return bundledFor("audio", folder, eventPrefix).size();
        }

        static SfxPick pickSfx(SfxManifest.EventSpec spec, int preferredVariantIndex) {
            if (spec == null) return null;
            String folder = spec.folder();
            String eventPrefix = spec.filePrefix();
            List<File> files = filesFor("audio", folder, eventPrefix, new File(ROOT_AUDIO, folder));
            if (!files.isEmpty()) {
                int idx = Math.floorMod(preferredVariantIndex, files.size());
                return new SfxPick(files.get(idx), null, idx, files.size());
            }
            List<String> bundled = bundledFor("audio", folder, eventPrefix);
            if (bundled.isEmpty()) return null;
            int idx = Math.floorMod(preferredVariantIndex, bundled.size());
            return new SfxPick(null, bundled.get(idx), idx, bundled.size());
        }

        private static List<File> filesFor(String root, String folder, String eventId, File dir) {
            String key = cacheKey(root, folder, eventId);
            return FILE_CACHE.computeIfAbsent(key, k -> scan(dir, eventId));
        }

        private static List<String> bundledFor(String root, String folder, String eventId) {
            String key = cacheKey(root, folder, eventId);
            return BUNDLED_CACHE.computeIfAbsent(key, k -> scanBundled(root, folder, eventId));
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

        private static String cacheKey(String root, String folder, String eventId) {
            String a = (root == null) ? "" : root.toLowerCase(Locale.US);
            String b = (folder == null) ? "" : folder.toLowerCase(Locale.US);
            String c = (eventId == null) ? "" : eventId.toLowerCase(Locale.US);
            return a + "/" + b + "/" + c;
        }

        private static boolean resourceExists(String path) {
            return path != null && AudioSystem.class.getResource(path) != null;
        }

        record VoicePick(File file, String resourcePath, int variantIndex, int variantCount) {}
        record SfxPick(File file, String resourcePath, int variantIndex, int variantCount) {}
    }

    public static void update(GameContext ctx, double dt) {
        if (ctx == null) return;

        if (ctx.ui.voiceCaptionT > 0.0) {
            ctx.ui.voiceCaptionT = Math.max(0.0, ctx.ui.voiceCaptionT - Math.max(0.0, dt));
                if (ctx.ui.voiceCaptionT <= 0.0) ctx.ui.clearVoiceCaption();
        }
        ctx.decayPortraitExpressions(dt);

        RuntimeState st = stateFor(ctx);
        double now = nowSec();

        ensureAmbientLoop(ctx, st, now);
        processLoopSignals(ctx, st, now);

        if (ctx.player == null || !ctx.player.alive || ctx.player.dying || ctx.player.hp <= 0) return;

        processVoiceSignals(ctx, st, now);
        processSuperweaponSignals(ctx, st, now);
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
        onWeaponPrimary(ctx, source, null);
    }

    public static void onWeaponPrimary(GameContext ctx, Ship source, List<Projectile> firedProjectiles) {
        if (containsTorpedo(firedProjectiles)) {
            onTorpedoLaunch(ctx, source);
            return;
        }
        if (containsMissile(firedProjectiles)) {
            onMissileLaunch(ctx, source);
            return;
        }
        String classifiedEvent = primaryWeaponEventId(source, firedProjectiles);
        RuntimeState st = stateFor(ctx);
        if (isGreenWeaponLoopEvent(classifiedEvent)) {
            touchGreenWeaponLoop(ctx, st, nowSec(), classifiedEvent, source, greenLoopHoldSeconds(classifiedEvent));
            return;
        }
        if (source == null) {
            triggerSfxEvent(ctx, st, classifiedEvent, nowSec());
            return;
        }
        triggerSfxEvent(ctx, st, classifiedEvent, nowSec(), source.x, source.y);
    }

    public static void onWeaponSecondary(GameContext ctx) {
        triggerSfx(ctx, SfxCue.WEAPON_SECONDARY);
    }

    public static void onWeaponSecondary(GameContext ctx, Ship source) {
        onWeaponSecondary(ctx, source, null);
    }

    public static void onWeaponSecondary(GameContext ctx, Ship source, List<Projectile> firedProjectiles) {
        if (containsTorpedo(firedProjectiles)) {
            onTorpedoLaunch(ctx, source);
            return;
        }
        if (containsMissile(firedProjectiles)) {
            onMissileLaunch(ctx, source);
            return;
        }
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

    public static void onSuperweaponFired(GameContext ctx, Ship source) {
        if (ctx == null || source == null) {
            onWeaponWave(ctx, source);
            return;
        }
        String eventId = superweaponFireEventId(source);
        if (eventId == null || SfxManifest.byId(eventId) == null) {
            onWeaponWave(ctx, source);
            return;
        }
        RuntimeState st = stateFor(ctx);
        if (isGreenWeaponLoopEvent(eventId)) {
            touchGreenWeaponLoop(ctx, st, nowSec(), eventId, source, greenLoopHoldSeconds(eventId));
            return;
        }
        triggerSfxEvent(ctx, st, eventId, nowSec(), source.x, source.y);
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

    public static void onFlightLaunch(GameContext ctx, Ship source) {
        if (source == null) {
            triggerSfx(ctx, SfxCue.FLIGHT_LAUNCH);
            return;
        }
        triggerSfx(ctx, SfxCue.FLIGHT_LAUNCH, source.x, source.y);
    }

    public static void onMissileLaunch(GameContext ctx, Ship source) {
        if (source == null) {
            triggerSfx(ctx, SfxCue.WEAPON_MISSILE_LAUNCH);
            return;
        }
        triggerSfx(ctx, SfxCue.WEAPON_MISSILE_LAUNCH, source.x, source.y);
    }

    public static void onTorpedoLaunch(GameContext ctx, Ship source) {
        if (source == null) {
            triggerSfx(ctx, SfxCue.WEAPON_TORPEDO_LAUNCH);
            return;
        }
        triggerSfx(ctx, SfxCue.WEAPON_TORPEDO_LAUNCH, source.x, source.y);
    }

    public static void onCiwsFire(GameContext ctx, Ship source) {
        if (source == null) {
            triggerSfx(ctx, SfxCue.WEAPON_CIWS_FIRE);
            return;
        }
        triggerSfx(ctx, SfxCue.WEAPON_CIWS_FIRE, source.x, source.y);
    }

    public static void onWarpChargeStart(GameContext ctx, Ship source) {
        RuntimeState st = stateFor(ctx);
        double now = nowSec();
        if (source == null) {
            triggerSfx(ctx, st, SfxCue.WARP_CHARGE_START, now);
            touchWarpSpoolLoop(ctx, st, now, Double.NaN, Double.NaN);
            return;
        }
        triggerSfx(ctx, st, SfxCue.WARP_CHARGE_START, now, source.x, source.y);
        touchWarpSpoolLoop(ctx, st, now, source.x, source.y);
    }

    public static void onWarpExit(GameContext ctx, Ship source) {
        stopWarpSpoolLoop();
        RuntimeState st = stateFor(ctx);
        if (st != null) st.warpSpoolLoopUntilSec = 0.0;
        if (source == null) {
            triggerSfx(ctx, SfxCue.WARP_EXIT);
            return;
        }
        triggerSfx(ctx, SfxCue.WARP_EXIT, source.x, source.y);
    }

    private static boolean isGreenWeaponLoopEvent(String eventId) {
        if (eventId == null || eventId.isBlank()) return false;
        if (eventId.startsWith("weapon.green.")) return true;
        return "super.green.fire".equals(eventId)
                || "hyper.green.fire".equals(eventId)
                || "super.green.charge".equals(eventId)
                || "hyper.green.charge".equals(eventId);
    }

    private static void touchGreenWeaponLoop(GameContext ctx, RuntimeState st, double now, String eventId, Ship source, double holdSec) {
        if (ctx == null || st == null || eventId == null || eventId.isBlank()) return;
        if (source != null && !shouldPlayWorldSfxAt(ctx, source.x, source.y)) return;
        double ttl = Math.max(0.10, holdSec);
        st.greenWeaponLoopUntilSec = Math.max(st.greenWeaponLoopUntilSec, now + ttl);
        String incomingFamily = greenLoopFamilyKey(eventId);
        String activeEventId = st.greenWeaponLoopEventId;
        String activeFamily = greenLoopFamilyKey(activeEventId);
        if (activeEventId == null || activeEventId.isBlank()) {
            st.greenWeaponLoopEventId = eventId;
        } else if ("superweapon.green".equals(activeFamily) && !"superweapon.green".equals(incomingFamily)
                && now < st.greenWeaponLoopUntilSec) {
            return;
        } else if (!incomingFamily.equals(activeFamily)) {
            stopGreenWeaponLoop(st);
            st.greenWeaponLoopEventId = eventId;
        }
        ensureGreenWeaponLoopRunning(ctx, st);
    }

    private static String greenLoopFamilyKey(String eventId) {
        if (eventId == null || eventId.isBlank()) return "";
        if (eventId.startsWith("weapon.green.")) return "weapon.green";
        if (eventId.startsWith("super.green.") || eventId.startsWith("hyper.green.")) return "superweapon.green";
        return eventId;
    }

    private static double greenLoopHoldSeconds(String eventId) {
        if (eventId == null || eventId.isBlank()) return GREEN_WEAPON_LOOP_HOLD_SEC;
        if ("super.green.fire".equals(eventId) || "hyper.green.fire".equals(eventId)) {
            return GREEN_SUPERWEAPON_FIRE_LOOP_HOLD_SEC;
        }
        return GREEN_WEAPON_LOOP_HOLD_SEC;
    }

    private static void touchWarpSpoolLoop(GameContext ctx, RuntimeState st, double now, double sourceX, double sourceY) {
        if (ctx == null || st == null) return;
        if (!shouldPlayWorldSfxAt(ctx, sourceX, sourceY)) return;
        st.warpSpoolLoopUntilSec = now + WARP_LOOP_KEEPALIVE_SEC;
        ensureWarpSpoolLoopRunning(ctx);
    }

    public static void onCommandShipFormationOrder(GameContext ctx, Ship commander, GameContext.FleetFormation formation) {
        // Intentionally silent: formation changes were spamming "form up" callouts and became noise.
    }

    public static void onCommandShipShipOrder(GameContext ctx, Ship commander, GameContext.FleetCommand command, Ship target) {
        if (ctx == null || commander == null || command == null || target == null) return;
        if (commander != ctx.player || !isPlayerFleetCommandShip(ctx)) return;
        RuntimeState st = stateFor(ctx);
        VoiceCue cue = voiceCueForFleetCommand(command);
        if (cue == null) return;
        emitCommandVoice(ctx, st, cue, nowSec(), commander,
                targetedFleetOrderCaption(command, target));
    }

    public static synchronized void setTelemetryOnly(boolean telemetryOnly) {
        TELEMETRY_ONLY = AUDIO_DISABLED || telemetryOnly;
        Clip clip = ambientClip;
        Clip greenClip = greenWeaponLoopClip;
        Clip warpClip = warpSpoolLoopClip;
        if (TELEMETRY_ONLY && clip != null) {
            try {
                clip.stop();
                clip.close();
            } catch (Throwable ignored) {
            }
            if (ambientClip == clip) {
                ambientClip = null;
            }
        }
        if (TELEMETRY_ONLY && greenClip != null) {
            try {
                greenClip.stop();
            } catch (Throwable ignored) {
            }
            if (greenWeaponLoopClip == greenClip) {
                greenWeaponLoopClip = null;
            }
        }
        if (TELEMETRY_ONLY && warpClip != null) {
            try {
                warpClip.stop();
            } catch (Throwable ignored) {
            }
            if (warpSpoolLoopClip == warpClip) {
                warpSpoolLoopClip = null;
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

    public static void playScriptedVoice(GameContext ctx, String role, String eventId,
                                         String speakerLabel, String caption, double captionSeconds) {
        if (ctx == null || role == null || role.isBlank() || eventId == null || eventId.isBlank()) return;
        RuntimeState st = stateFor(ctx);
        int variantCount = Math.max(1, AssetLibrary.voiceVariantCount(role, eventId));
        int variantIndex = chooseVariantIndex(st, "scripted." + role + "." + eventId, variantCount);
        AssetLibrary.VoicePick voicePick = AssetLibrary.pickVoice(role, eventId, variantIndex);

        double roleVol = voiceRoleVolume(ctx, role);
        double roleVolGainDb = 20.0 * Math.log10(Math.max(0.05, roleVol));
        if (voicePick != null && (voicePick.file() != null || voicePick.resourcePath() != null)) {
            playAssetAsync(voicePick.file(), voicePick.resourcePath(), false, -12.0 + roleVolGainDb);
            variantIndex = voicePick.variantIndex();
        }

        if (ctx.ui != null && ctx.ui.voiceCaptionsEnabled) {
            String resolvedSpeaker = (speakerLabel == null || speakerLabel.isBlank()) ? role.toUpperCase(Locale.US) : speakerLabel;
            String resolvedCaption = (caption == null || caption.isBlank()) ? eventId : caption;
            ctx.ui.voiceCaption = resolvedSpeaker + ": " + resolvedCaption;
            ctx.ui.voiceCaptionT = Math.max(1.6, captionSeconds);
        }
        GameContext.CrewStation station = stationForRole(role);
        if (station != null) {
            ctx.setPortraitExpression(station, 3, Math.max(1.3, captionSeconds));
        }
        st.voiceDispatchCount++;
        st.voiceDispatchByEvent.put("scripted." + eventId, st.voiceDispatchByEvent.getOrDefault("scripted." + eventId, 0) + 1);
    }

    public static boolean playContextBanter(GameContext ctx, String role, String eventId,
                                            String speakerLabel, String caption, double captionSeconds,
                                            double cooldownSec, int priority) {
        if (ctx == null || role == null || role.isBlank() || eventId == null || eventId.isBlank()) return false;
        RuntimeState st = stateFor(ctx);
        double now = nowSec();
        int clampedPriority = MathUtil.clamp(priority, 1, 3);
        String cooldownKey = "context." + role + "." + eventId;

        Double dedupe = st.voiceDedupeUntil.get(cooldownKey);
        if (dedupe != null && now < dedupe) return false;
        Double roleThrottle = st.roleThrottleUntil.get(role);
        if (roleThrottle != null && now < roleThrottle && clampedPriority < 3) return false;
        if (now < st.voicePriorityUntilSec && clampedPriority < st.activeVoicePriority) return false;

        double effectiveCooldown = Math.max(0.8, cooldownSec);
        st.voiceDedupeUntil.put(cooldownKey, now + effectiveCooldown);
        st.roleThrottleUntil.put(role, now + roleThrottleSeconds(clampedPriority));
        st.activeVoicePriority = clampedPriority;
        st.voicePriorityUntilSec = now + 0.9;

        playScriptedVoice(ctx, role, eventId, speakerLabel, caption, captionSeconds);
        return true;
    }

    private static RuntimeState stateFor(GameContext ctx) {
        return STATE.computeIfAbsent(ctx, RuntimeState::seed);
    }

    private static void processVoiceSignals(GameContext ctx, RuntimeState st, double now) {
        List<Ship> visibleHostiles = visibleHostilesCached(ctx, st, now);
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

        if (ctx.command.helmMode != st.lastHelmMode) {
            if (ctx.command.helmMode == GameContext.HelmMode.INTERCEPT) {
                emitVoice(ctx, st, VoiceCue.HELM_INTERCEPT, now);
            } else if (ctx.command.helmMode == GameContext.HelmMode.EVASIVE) {
                emitVoice(ctx, st, VoiceCue.HELM_EVASIVE, now);
            }
        }

        if (ctx.command.captainDirective != st.lastCaptainDirective) {
            if (ctx.command.captainDirective == GameContext.CaptainDirective.RTB) {
                emitVoice(ctx, st, VoiceCue.HELM_RTB, now);
            }
            emitVoiceForCaptainDirective(ctx, st, ctx.command.captainDirective, now);
        }
        processFriendlyCommandShipBroadcast(ctx, st, now);
        processFleetHubChatter(ctx, st, now, hostiles);

        st.hadCombatContact = hostiles > 0;
        st.hostileContactCount = hostiles;
        st.lastLockedTarget = currentLock;
        st.missilesInbound = missilesNow;
        st.lastRepairsActive = repairsNow;
        st.lastShieldFrac = shieldNow;
        st.lastReactorFrac = reactorNow;
        st.lastHelmMode = ctx.command.helmMode;
        st.lastCaptainDirective = ctx.command.captainDirective;
        Ship commandShip = friendlyCommandShip(ctx);
        if (commandShip == null) {
            st.lastFriendlyCommandShipId = -1;
            st.lastFriendlyFleetCommand = null;
            st.lastFriendlyFleetFormation = null;
        } else {
            st.lastFriendlyCommandShipId = commandShip.id;
            st.lastFriendlyFleetCommand = resolvedFleetCommand(ctx, commandShip);
            st.lastFriendlyFleetFormation = resolvedFleetFormation(ctx, commandShip);
        }
    }

    private static void processFriendlyCommandShipBroadcast(GameContext ctx, RuntimeState st, double now) {
        if (ctx == null || st == null || ctx.player == null || ctx.player.faction == null) return;
        Ship commandShip = friendlyCommandShip(ctx);
        if (commandShip == null || commandShip == ctx.player || commandShip.faction == null) return;
        GameContext.FleetCommand command = resolvedFleetCommand(ctx, commandShip);
        if (command == null) command = GameContext.FleetCommand.AUTO;

        if (st.lastFriendlyCommandShipId != commandShip.id) return;
        if (command == st.lastFriendlyFleetCommand) return;
        VoiceCue cue = voiceCueForFleetCommand(command);
        VoiceCue priorCue = voiceCueForFleetCommand(st.lastFriendlyFleetCommand);
        if (cue != null && cue == priorCue) return;
        if (cue == null) return;
        emitCommandVoice(ctx, st, cue, now, commandShip, null);
    }

    private static void processFleetHubChatter(GameContext ctx, RuntimeState st, double now, int visibleHostiles) {
        if (ctx == null || st == null) return;

        if (!ALPHA_TEMPORARY_CREW_CHATTER_ENABLED) {
            st.fleetHubChatterFollowupCue = null;
            st.fleetHubChatterFollowupAtSec = 0.0;
            st.fleetHubChatterNextSec = 0.0;
            return;
        }

        if (!CampaignSystem.isFleetHubSession(ctx)) {
            st.fleetHubChatterFollowupCue = null;
            st.fleetHubChatterFollowupAtSec = 0.0;
            st.fleetHubChatterNextSec = 0.0;
            return;
        }
        if (visibleHostiles > 0) return;

        if (st.fleetHubChatterFollowupCue != null && now >= st.fleetHubChatterFollowupAtSec) {
            VoiceCue cue = st.fleetHubChatterFollowupCue;
            st.fleetHubChatterFollowupCue = null;
            st.fleetHubChatterFollowupAtSec = 0.0;
            emitVoice(ctx, st, cue, now);
        }

        if (now < st.fleetHubChatterNextSec) return;
        if (ctx.ui == null) return;
        // Keep chatter out of the player's way: if captions are off, deprioritize chatter entirely.
        if (!ctx.ui.voiceCaptionsEnabled && RNG.nextDouble() < 0.80) {
            st.fleetHubChatterNextSec = now + 16.0 + RNG.nextDouble() * 12.0;
            return;
        }

        VoiceCue[] ambientPool = new VoiceCue[]{
                VoiceCue.CAPTAIN_FLEET_ORGANIZER_AMBIENT,
                VoiceCue.CAPTAIN_XO_AMBIENT,
                VoiceCue.TACTICAL_WEAPONRY_AMBIENT,
                VoiceCue.ENGINEERING_STATION_AMBIENT,
                VoiceCue.HELM_FLIGHT_DECK_AMBIENT,
                VoiceCue.SCIENCE_CREW_AMBIENT
        };
        VoiceCue[] banterPool = new VoiceCue[]{
                VoiceCue.CAPTAIN_BANTER,
                VoiceCue.HELM_BANTER,
                VoiceCue.TACTICAL_BANTER,
                VoiceCue.ENGINEERING_BANTER,
                VoiceCue.SCIENCE_BANTER
        };

        boolean doBanter = RNG.nextDouble() < 0.42;
        VoiceCue cue = doBanter
                ? banterPool[RNG.nextInt(banterPool.length)]
                : ambientPool[RNG.nextInt(ambientPool.length)];
        emitVoice(ctx, st, cue, now);

        if (doBanter && RNG.nextDouble() < 0.72) {
            VoiceCue reply = banterPool[RNG.nextInt(banterPool.length)];
            int guard = 0;
            while (reply != null && cue != null && reply.role != null && cue.role != null
                    && reply.role.equals(cue.role) && guard++ < 8) {
                reply = banterPool[RNG.nextInt(banterPool.length)];
            }
            st.fleetHubChatterFollowupCue = reply;
            st.fleetHubChatterFollowupAtSec = now + 2.0 + RNG.nextDouble() * 1.8;
        }

        st.fleetHubChatterNextSec = now + 14.0 + RNG.nextDouble() * 12.0;
    }

    private static void emitVoiceForCaptainDirective(GameContext ctx, RuntimeState st,
                                                     GameContext.CaptainDirective directive, double now) {
        if (ctx == null || st == null || directive == null) return;
        boolean commandAuthority = isPlayerFleetCommandShip(ctx);
        VoiceCue cue = voiceCueForCaptainDirective(directive, commandAuthority);
        if (cue == null) return;
        emitVoice(ctx, st, cue, now,
                commandAuthority ? commandSpeakerLabel(ctx.player) : null,
                null);
    }

    private static void emitCommandVoice(GameContext ctx, RuntimeState st, VoiceCue cue, double now,
                                         Ship commander, String captionOverride) {
        if (ctx == null || st == null || cue == null) return;
        emitVoice(ctx, st, cue, now, commandSpeakerLabel(commander), captionOverride);
    }

    private static VoiceCue voiceCueForCaptainDirective(GameContext.CaptainDirective directive, boolean commandAuthority) {
        if (directive == null) return null;
        return switch (directive) {
            case BALANCED -> null;
            case ATTACK -> VoiceCue.CAPTAIN_ORDER_PUSH;
            case DEFENSE, DEFEND -> VoiceCue.CAPTAIN_ORDER_DEFEND;
            case EMERGENCY -> VoiceCue.CAPTAIN_ORDER_RETREAT;
            case MINE -> commandAuthority ? VoiceCue.CAPTAIN_ORDER_MINE : null;
            case ESCORT -> VoiceCue.CAPTAIN_ORDER_ESCORT;
            case REPAIR -> commandAuthority ? VoiceCue.CAPTAIN_ORDER_REPAIR : VoiceCue.CAPTAIN_ORDER_RETREAT;
            case RTB -> commandAuthority ? VoiceCue.CAPTAIN_ORDER_RTB : VoiceCue.CAPTAIN_ORDER_RETREAT;
        };
    }

    private static VoiceCue voiceCueForFleetCommand(GameContext.FleetCommand command) {
        if (command == null) return null;
        return switch (command) {
            case AUTO, FORM_UP -> null;
            case ATTACK -> VoiceCue.CAPTAIN_ORDER_PUSH;
            case DEFEND -> VoiceCue.CAPTAIN_ORDER_DEFEND;
            case ESCORT -> VoiceCue.CAPTAIN_ORDER_ESCORT;
            case REPAIR -> VoiceCue.CAPTAIN_ORDER_REPAIR;
            case RTB -> VoiceCue.CAPTAIN_ORDER_RTB;
            case RETREAT -> VoiceCue.CAPTAIN_ORDER_RETREAT;
            case MINE -> VoiceCue.CAPTAIN_ORDER_MINE;
        };
    }

    private static String targetedFleetOrderCaption(GameContext.FleetCommand command, Ship target) {
        String shipLabel = (target == null) ? "UNIT" : "SHIP " + target.id;
        if (command == null) command = GameContext.FleetCommand.AUTO;
        return switch (command) {
            case ATTACK -> shipLabel + ", engage and press the attack.";
            case DEFEND -> shipLabel + ", hold the line and defend.";
            case ESCORT -> shipLabel + ", escort the objective.";
            case REPAIR -> shipLabel + ", peel off and begin repairs.";
            case RTB -> shipLabel + ", return to base.";
            case RETREAT -> shipLabel + ", break contact and fall back.";
            case MINE -> shipLabel + ", move to the mining pocket.";
            case AUTO, FORM_UP -> shipLabel + ", resume command formation.";
        };
    }

    private static Ship friendlyCommandShip(GameContext ctx) {
        if (ctx == null || ctx.player == null || ctx.player.faction == null || ctx.command.fleetCommandShips == null) return null;
        Ship direct = ctx.command.fleetCommandShips.get(ctx.player.faction);
        if (direct != null) return direct;
        for (Faction faction : Faction.fourTeamFactions()) {
            if (faction == null || !faction.isFriendlyTo(ctx.player.faction)) continue;
            Ship candidate = ctx.command.fleetCommandShips.get(faction);
            if (candidate != null) return candidate;
        }
        return null;
    }

    private static boolean isPlayerFleetCommandShip(GameContext ctx) {
        Ship commandShip = friendlyCommandShip(ctx);
        return commandShip != null && commandShip == ctx.player;
    }

    private static GameContext.FleetCommand resolvedFleetCommand(GameContext ctx, Ship commander) {
        if (ctx == null || commander == null || commander.faction == null) return null;
        GameContext.FleetCommand direct = ctx.command.fleetResolvedCommands.get(commander.faction);
        if (direct != null) return direct;
        for (Faction faction : Faction.fourTeamFactions()) {
            if (faction == null || !faction.isFriendlyTo(commander.faction)) continue;
            GameContext.FleetCommand fallback = ctx.command.fleetResolvedCommands.get(faction);
            if (fallback != null) return fallback;
        }
        return null;
    }

    private static GameContext.FleetFormation resolvedFleetFormation(GameContext ctx, Ship commander) {
        if (ctx == null || commander == null || commander.faction == null) return null;
        GameContext.FleetFormation direct = ctx.command.fleetResolvedFormations.get(commander.faction);
        if (direct != null) return direct;
        for (Faction faction : Faction.fourTeamFactions()) {
            if (faction == null || !faction.isFriendlyTo(commander.faction)) continue;
            GameContext.FleetFormation fallback = ctx.command.fleetResolvedFormations.get(faction);
            if (fallback != null) return fallback;
        }
        return null;
    }

    private static String commandSpeakerLabel(Ship commander) {
        if (commander != null && commander.faction != null && commander.faction.teamName() != null) {
            return commander.faction.teamName().toUpperCase(Locale.US) + " COMMAND";
        }
        return "COMMAND";
    }

    private static void pruneScienceContactMemory(RuntimeState st, double now) {
        if (st == null) return;
        if (st.scienceKnownContactsUntil.isEmpty()) return;
        st.scienceKnownContactsUntil.entrySet().removeIf(e -> e == null || e.getValue() == null || e.getValue() < now);
        if (st.scienceKnownContactsUntil.isEmpty()) {
            st.scienceContactMemoryUntilSec = 0.0;
        }
    }

    private static void processSuperweaponSignals(GameContext ctx, RuntimeState st, double now) {
        if (ctx == null || st == null || ctx.ships == null) return;
        Set<Integer> trackedIds = new HashSet<>();
        for (Ship ship : ctx.ships) {
            if (ship == null) continue;
            if (!ship.alive || ship.dying || ship.hp <= 0) continue;
            if (!ship.hasSuperweapon) continue;

            trackedIds.add(ship.id);
            boolean chargingNow = ship.isSuperweaponCharging();
            boolean chargingBefore = st.lastSuperweaponChargingByShip.getOrDefault(ship.id, false);
            String eventId = superweaponChargeEventId(ship);
            if (chargingNow && isGreenWeaponLoopEvent(eventId)) {
                touchGreenWeaponLoop(ctx, st, now, eventId, ship, greenLoopHoldSeconds(eventId));
            } else if (chargingNow && !chargingBefore) {
                triggerSfxEvent(ctx, st, eventId, now, ship.x, ship.y);
            }
            st.lastSuperweaponChargingByShip.put(ship.id, chargingNow);
        }
        st.lastSuperweaponChargingByShip.keySet().removeIf(id -> !trackedIds.contains(id));
    }

    private static void processLoopSignals(GameContext ctx, RuntimeState st, double now) {
        if (ctx == null || st == null) {
            stopGreenWeaponLoop(null);
            stopWarpSpoolLoop();
            return;
        }

        boolean warpCharging = false;
        if (ctx.ships != null) {
            for (Ship ship : ctx.ships) {
                if (ship == null || !ship.alive || ship.dying || ship.hp <= 0) continue;
                if (!ship.isWarpCharging()) continue;
                if (ctx.player != null && !shouldPlayWorldSfxAt(ctx, ship.x, ship.y)) continue;
                warpCharging = true;
                break;
            }
        }
        if (warpCharging) {
            touchWarpSpoolLoop(ctx, st, now, Double.NaN, Double.NaN);
        } else if (st.warpSpoolLoopUntilSec > 0.0 && now >= st.warpSpoolLoopUntilSec) {
            stopWarpSpoolLoop();
            st.warpSpoolLoopUntilSec = 0.0;
        }

        if (st.greenWeaponLoopUntilSec > 0.0 && now >= st.greenWeaponLoopUntilSec) {
            stopGreenWeaponLoop(st);
            st.greenWeaponLoopUntilSec = 0.0;
        }
    }

    private static void processImpactSignals(GameContext ctx, RuntimeState st, double now) {
        if (ctx == null || ctx.player == null) return;
        Ship player = ctx.player;

        double shieldLoss = Math.max(0.0, st.lastShield - player.shield);
        if (shieldLoss > Math.max(4.0, player.effectiveShieldCapacityMax() * 0.015)) {
            triggerSfx(ctx, st, SfxCue.IMPACT_SHIELD_DAMAGE, now);
        }

        double hullLoss = Math.max(0.0, st.lastHp - player.hp);
        if (hullLoss > Math.max(3.0, player.hpMax * 0.015)) {
            triggerSfx(ctx, st, SfxCue.IMPACT_HULL_DAMAGE, now);
        }

        int explosionsNow = explosionCountNearPlayer(ctx);
        if (explosionsNow > st.lastExplosionCount) {
            triggerSfx(ctx, st, SfxCue.IMPACT_EXPLOSION, now);
        }
        st.lastExplosionCount = explosionsNow;

        int majorDetonationsNow = majorDetonationCountNearPlayer(ctx);
        if (majorDetonationsNow > st.lastMajorDetonationCount) {
            triggerSfx(ctx, st, SfxCue.IMPACT_SHIP_DEATH_MAJOR, now);
        }
        st.lastMajorDetonationCount = majorDetonationsNow;
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
        emitVoice(ctx, st, cue, now, null, null);
    }

    private static void emitVoice(GameContext ctx, RuntimeState st, VoiceCue cue, double now,
                                  String speakerLabelOverride, String captionOverride) {
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
        if (captionOverride != null && !captionOverride.isBlank()) {
            caption = captionOverride;
        }

        double roleVol = voiceRoleVolume(ctx, cue.role);
        double roleVolGainDb = volumeToGainOffsetDb(roleVol);
        AssetLibrary.VoicePick voicePick = AssetLibrary.pickVoice(cue.role, cue.eventId, variantIndex);
        if (voicePick != null && (voicePick.file() != null || voicePick.resourcePath() != null)) {
            variantIndex = voicePick.variantIndex();
            playAssetAsync(voicePick.file(), voicePick.resourcePath(), false, cue.gainDb + roleVolGainDb);
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
            playToneAsync(variantTone, variantMs, (cue.gainDb - 8.0) + roleVolGainDb, false);
        }
        st.lastVariantByKey.put(cue.cooldownKey, variantIndex);
        applyPortraitExpression(ctx, cue, variantIndex);

        if (ctx.ui.voiceCaptionsEnabled) {
            String speaker = (speakerLabelOverride == null || speakerLabelOverride.isBlank())
                    ? cue.roleLabel()
                    : speakerLabelOverride;
            ctx.ui.voiceCaption = speaker + ": " + caption;
            ctx.ui.voiceCaptionT = 1.8;
        }
        logAudioEvent(ctx, new AudioEvent(
                "voice." + cue.eventId,
                cue.priority,
                cue.cooldownKey,
                variantIndex,
                "voice",
                eventTimestampNanos(now)
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
        if (isSfxBurstSuppressed(spec, now)) return;

        Double cd = st.sfxCooldownUntil.get(spec.eventId());
        if (cd != null && now < cd) return;
        st.sfxCooldownUntil.put(spec.eventId(), now + Math.max(0.02, spec.cooldownSec()));

        int variants = Math.max(1, AssetLibrary.sfxVariantCount(spec));
        int variant = chooseSfxVariantIndex(st, spec.eventId(), variants);
        AssetLibrary.SfxPick pick = AssetLibrary.pickSfx(spec, variant);
        boolean hasAsset = (pick != null && (pick.file() != null || pick.resourcePath() != null));
        if (hasAsset) {
            variant = pick.variantIndex();
            double gain = spec.gainDb() + sfxVoiceDuckingDb(ctx, spec.priority());
            playAssetAsync(pick.file(), pick.resourcePath(), false, gain, spec.priority());
        }
        st.lastSfxVariantByEvent.put(spec.eventId(), variant);

        logAudioEvent(ctx, new AudioEvent(
                "sfx." + spec.eventId(),
                spec.priority(),
                "sfx." + spec.eventId(),
                hasAsset ? variant : -1,
                hasAsset ? "sfx" : "sfx_missing",
                eventTimestampNanos(now)
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
        RuntimeState st = stateFor(ctx);
        Clip clip = ambientClip;
        if (clip == null || !clip.isOpen()) return;
        // Slightly louder ambience to better support the "crewed bridge" feel.
        double target = -24.0;
        if (countHostiles(ctx, st, nowSec()) > 0) target = -21.5;
        if (ctx.ui.voiceCaptionT > 0.0) target -= 4.5;
        applyGain(clip, target);
    }

    private static synchronized void ensureGreenWeaponLoopRunning(GameContext ctx, RuntimeState st) {
        if (ctx == null || st == null || TELEMETRY_ONLY) return;
        String eventId = st.greenWeaponLoopEventId;
        if (eventId == null || eventId.isBlank()) return;
        Clip clip = greenWeaponLoopClip;
        if (clip != null && clip.isOpen()) {
            if (!clip.isRunning()) clip.loop(Clip.LOOP_CONTINUOUSLY);
            return;
        }
        greenWeaponLoopClip = startEventLoopClip(ctx, eventId);
    }

    private static synchronized void ensureWarpSpoolLoopRunning(GameContext ctx) {
        if (ctx == null || TELEMETRY_ONLY) return;
        Clip clip = warpSpoolLoopClip;
        if (clip != null && clip.isOpen()) {
            if (!clip.isRunning()) clip.loop(Clip.LOOP_CONTINUOUSLY);
            return;
        }
        warpSpoolLoopClip = startEventLoopClip(ctx, SfxCue.WARP_SPOOL_UP.eventId);
    }

    private static synchronized void stopGreenWeaponLoop(RuntimeState st) {
        Clip clip = greenWeaponLoopClip;
        if (clip != null) {
            try {
                clip.stop();
            } catch (Throwable ignored) {
            }
        }
        greenWeaponLoopClip = null;
        if (st != null) st.greenWeaponLoopEventId = "";
    }

    private static synchronized void stopWarpSpoolLoop() {
        Clip clip = warpSpoolLoopClip;
        if (clip != null) {
            try {
                clip.stop();
            } catch (Throwable ignored) {
            }
        }
        warpSpoolLoopClip = null;
    }

    private static Clip startEventLoopClip(GameContext ctx, String eventId) {
        if (TELEMETRY_ONLY || eventId == null || eventId.isBlank()) return null;
        SfxManifest.EventSpec spec = SfxManifest.byId(eventId);
        if (spec == null) return null;
        int variantCount = Math.max(1, AssetLibrary.sfxVariantCount(spec));
        AssetLibrary.SfxPick pick = AssetLibrary.pickSfx(spec, RNG.nextInt(variantCount));
        if (pick == null || (pick.file() == null && (pick.resourcePath() == null || pick.resourcePath().isBlank()))) {
            return null;
        }
        double gainDb = spec.gainDb() + sfxVoiceDuckingDb(ctx, spec.priority());
        Clip clip = createClipFromAsset(pick.file(), pick.resourcePath(), gainDb);
        if (clip == null) return null;
        clip.loop(Clip.LOOP_CONTINUOUSLY);
        clip.start();
        return clip;
    }

    private static void playAssetAsync(File wav, String resourcePath, boolean loop, double gainDb) {
        playAssetAsync(wav, resourcePath, loop, gainDb, 3);
    }

    private static void playAssetAsync(File wav, String resourcePath, boolean loop, double gainDb, int priority) {
        if (TELEMETRY_ONLY) return;
        if ((wav == null || !wav.isFile()) && (resourcePath == null || resourcePath.isBlank())) return;
        if (!loop) {
            if (!canSpawnOneShotClip(priority)) return;
            if (isDuplicateOneShotSuppressed(wav, resourcePath)) return;
        }
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
        if (wav == null) return null;
        try {
            byte[] bytes = AUDIO_FILE_BYTES.computeIfAbsent(wav.getAbsolutePath(), path -> {
                try {
                    return Files.readAllBytes(wav.toPath());
                } catch (Throwable ignored) {
                    return null;
                }
            });
            if (bytes == null || bytes.length == 0) return null;
            try (AudioInputStream stream = javax.sound.sampled.AudioSystem.getAudioInputStream(new BufferedInputStream(new ByteArrayInputStream(bytes)))) {
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

    private static Clip createClipFromResource(String resourcePath, double gainDb) {
        if (resourcePath == null || resourcePath.isBlank()) return null;
        try {
            byte[] bytes = AUDIO_RESOURCE_BYTES.computeIfAbsent(resourcePath, path -> {
                try (InputStream raw = AudioSystem.class.getResourceAsStream(path)) {
                    return (raw == null) ? null : raw.readAllBytes();
                } catch (Throwable ignored) {
                    return null;
                }
            });
            if (bytes == null || bytes.length == 0) return null;
            try (AudioInputStream stream = javax.sound.sampled.AudioSystem.getAudioInputStream(new BufferedInputStream(new ByteArrayInputStream(bytes)))) {
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

    private static boolean canSpawnOneShotClip(int priority) {
        pruneInactiveClips();
        int active = ACTIVE_CLIPS.size();
        if (active >= MAX_ACTIVE_ONE_SHOTS) return false;
        if (active >= MAX_ACTIVE_LOW_PRIORITY_ONE_SHOTS) return priority >= 3;
        return true;
    }

    private static void pruneInactiveClips() {
        synchronized (ACTIVE_CLIPS) {
            ACTIVE_CLIPS.removeIf(clip -> clip == null || !clip.isOpen());
        }
    }

    private static boolean isSfxBurstSuppressed(SfxManifest.EventSpec spec, double now) {
        if (spec == null) return false;
        String eventId = spec.eventId();
        if (eventId == null || eventId.isBlank()) return false;
        double extraCooldown = 0.0;
        if (eventId.startsWith("impact.shield.")) {
            extraCooldown = 0.028;
        } else if (eventId.startsWith("impact.hull.")) {
            extraCooldown = 0.022;
        } else if (eventId.startsWith("impact.")) {
            extraCooldown = 0.018;
        }
        if (extraCooldown <= 1e-6) return false;
        Double until = SFX_BURST_THROTTLE_UNTIL.get(eventId);
        if (until != null && now < until) return true;
        SFX_BURST_THROTTLE_UNTIL.put(eventId, now + extraCooldown);
        return false;
    }

    private static boolean isDuplicateOneShotSuppressed(File wav, String resourcePath) {
        String key;
        if (wav != null && wav.isFile()) {
            key = "file:" + wav.getAbsolutePath();
        } else if (resourcePath != null && !resourcePath.isBlank()) {
            key = "res:" + resourcePath;
        } else {
            return false;
        }
        double now = System.nanoTime() * 1e-9;
        Double until = ONE_SHOT_ASSET_THROTTLE_UNTIL.get(key);
        if (until != null && now < until) return true;
        ONE_SHOT_ASSET_THROTTLE_UNTIL.put(key, now + DUPLICATE_ONE_SHOT_SUPPRESS_SEC);
        return false;
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
        return TargetingSystem.isDetectableToObserver(ctx, ctx.player, t);
    }

    private static int countHostiles(GameContext ctx, RuntimeState st, double now) {
        return visibleHostilesCached(ctx, st, now).size();
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
            if (!TargetingSystem.isDetectableToObserver(ctx, ctx.player, s)) continue;
            double d2 = GameMath.dist2(s.x, s.y, ctx.player.x, ctx.player.y);
            if (d2 <= maxRange2) out.add(s);
        }
        return out;
    }

    private static List<Ship> visibleHostilesCached(GameContext ctx, RuntimeState st, double now) {
        if (st == null) return visibleHostiles(ctx);
        if (ctx == null || ctx.player == null || !ctx.player.alive || ctx.player.dying || ctx.player.hp <= 0) {
            st.cachedVisibleHostiles = List.of();
            st.hostileContactCount = 0;
            st.hostileContactRefreshAtSec = now + HOSTILE_CONTACT_REFRESH_SEC;
            return st.cachedVisibleHostiles;
        }
        if (st.cachedVisibleHostiles != null
                && !st.cachedVisibleHostiles.isEmpty()
                && now < st.hostileContactRefreshAtSec) {
            return st.cachedVisibleHostiles;
        }
        List<Ship> refreshed = List.copyOf(visibleHostiles(ctx));
        st.cachedVisibleHostiles = refreshed;
        st.hostileContactCount = refreshed.size();
        st.hostileContactRefreshAtSec = now + HOSTILE_CONTACT_REFRESH_SEC;
        return refreshed;
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
        return ctx.command.engineeringMode == GameContext.EngineeringMode.DAMAGE_CONTROL
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

    private static int majorDetonationCountNearPlayer(GameContext ctx) {
        try {
            if (Explosion.active == null) return 0;
            if (ctx == null || ctx.player == null) return Explosion.active.size();
            int count = 0;
            for (Explosion e : Explosion.active) {
                if (e == null) continue;
                if (e.kind != Explosion.Kind.FINAL_DETONATION
                        && e.kind != Explosion.Kind.SUPERWEAPON_BLAST) {
                    continue;
                }
                if (GameMath.dist2(e.x, e.y, ctx.player.x, ctx.player.y) <= WORLD_SFX_HEARING_RADIUS2) {
                    count++;
                }
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
        if (ctx.ui.voiceCaptionT <= 0.0) return 0.0;
        if (sfxPriority >= 3) return -1.0;
        if (sfxPriority == 2) return -2.5;
        return -4.0;
    }

    private static String primaryWeaponEventId(Ship source, List<Projectile> firedProjectiles) {
        if (source == null) return SfxCue.WEAPON_PRIMARY.eventId;
        String color = factionColorKey(source.faction);
        String grade = weaponGradeKey(source, firedProjectiles);
        return "weapon." + color + "." + grade + "_fire";
    }

    private static String primaryWeaponEventId(Ship source) {
        return primaryWeaponEventId(source, null);
    }

    private static String superweaponChargeEventId(Ship source) {
        if (source == null) return null;
        String color = factionColorKey(source.faction);
        boolean hyper = source.role == ShipRole.HYPERWEAPON_TITAN;
        return (hyper ? "hyper." : "super.") + color + ".charge";
    }

    private static String superweaponFireEventId(Ship source) {
        if (source == null) return null;
        String color = factionColorKey(source.faction);
        boolean hyper = source.role == ShipRole.HYPERWEAPON_TITAN;
        return (hyper ? "hyper." : "super.") + color + ".fire";
    }

    private static String factionColorKey(Faction faction) {
        if (faction == null) return "red";
        return switch (faction) {
            case PLAYER, ALLY -> "blue";
            case ENEMY -> "red";
            case TEAM_C -> "green";
            case TEAM_D -> "yellow";
        };
    }

    private static String weaponGradeKey(Ship source, List<Projectile> firedProjectiles) {
        if (source == null) return "medium";
        if (source.faction == Faction.TEAM_C) {
            if (containsProjectileType(firedProjectiles, PhaserBeam.class)) {
                return (source.role != null && source.role.isTitanOrMothership()) ? "capital" : "medium";
            }
            if (containsProjectileType(firedProjectiles, EnergyBolt.class)) {
                return source.isSmallCraft() ? "small" : "medium";
            }
            if (containsProjectileType(firedProjectiles, Bullet.class)) {
                return "small";
            }
        }
        ShipRole role = source.role;
        if (role == null) return "medium";
        if (source.isSmallCraft()) return "small";
        int tier = SpawnSystem.requiredHangarTierForRole(role);
        if (tier >= 3 || role.isTitanOrMothership() || role.isCapitalCombatant()) return "capital";
        if (tier <= 0) return "small";
        return "medium";
    }

    private static String weaponGradeKey(Ship source) {
        return weaponGradeKey(source, null);
    }

    private static boolean containsProjectileType(List<Projectile> projectiles, Class<?> type) {
        if (projectiles == null || projectiles.isEmpty() || type == null) return false;
        for (Projectile p : projectiles) {
            if (p != null && type.isInstance(p)) return true;
        }
        return false;
    }

    private static boolean containsMissile(List<Projectile> projectiles) {
        if (projectiles == null || projectiles.isEmpty()) return false;
        for (Projectile p : projectiles) {
            if (p instanceof Missile) return true;
        }
        return false;
    }

    private static boolean containsTorpedo(List<Projectile> projectiles) {
        if (projectiles == null || projectiles.isEmpty()) return false;
        for (Projectile p : projectiles) {
            if (!(p instanceof Missile missile)) continue;
            if (missile.role == Turret.MissileRole.ANTI_HEAVY) return true;
            if (missile.strikeVisual == Missile.StrikeVisual.TORPEDO
                    || missile.strikeVisual == Missile.StrikeVisual.ATOMIC) {
                return true;
            }
        }
        return false;
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

    private static long eventTimestampNanos(double nowSec) {
        if (!Double.isFinite(nowSec) || nowSec <= 0.0) {
            return System.nanoTime();
        }
        return Math.max(0L, Math.round(nowSec * 1_000_000_000.0));
    }

    private static void logAudioEvent(GameContext ctx, AudioEvent event) {
        if (ctx == null || event == null) return;
        if (ctx.audioEvents.size() >= MAX_AUDIO_EVENT_LOG) {
            ctx.audioEvents.remove(0);
        }
        ctx.audioEvents.add(event);
    }
}
