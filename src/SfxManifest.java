import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Event-to-sound manifest for gameplay SFX coverage and validation.
 */
public final class SfxManifest {
    private static final File ROOT_AUDIO = new File("assets/audio");
    private static final int MAX_RESOURCE_VARIANTS = 16;

    public enum Category {
        WEAPON,
        IMPACT,
        HAZARD,
        SUBSYSTEM,
        UI,
        AMBIENCE
    }

    public record EventSpec(
            String eventId,
            String folder,
            String filePrefix,
            Category category,
            int priority,
            double cooldownSec,
            double gainDb,
            int requiredVariants,
            boolean coreLoop) {}

    public record CoverageRow(EventSpec spec, int assetVariants, boolean ok) {}
    public record CoverageReport(List<CoverageRow> rows, int okCount, int failCount) {}

    private static final List<EventSpec> EVENTS = List.of(
            // UI
            event("ui.open", "ui", "open", Category.UI, 1, 0.05, -18.0, 1, true),
            event("ui.close", "ui", "close", Category.UI, 1, 0.05, -18.5, 1, true),

            // Weapons
            event("weapon.primary_fire", "weapons", "primary_fire", Category.WEAPON, 1, 0.04, -15.0, 2, true),
            event("weapon.secondary_fire", "weapons", "secondary_fire", Category.WEAPON, 2, 0.08, -13.0, 2, true),
            event("weapon.wave_fire", "weapons", "wave_fire", Category.WEAPON, 3, 0.75, -10.0, 1, true),

            // Impacts by damage class
            event("impact.shield.kinetic", "impacts", "shield_kinetic", Category.IMPACT, 2, 0.05, -13.0, 2, true),
            event("impact.shield.energy", "impacts", "shield_energy", Category.IMPACT, 2, 0.05, -13.0, 2, true),
            event("impact.shield.beam", "impacts", "shield_beam", Category.IMPACT, 2, 0.05, -13.0, 2, true),
            event("impact.shield.explosive", "impacts", "shield_explosive", Category.IMPACT, 2, 0.05, -13.0, 2, true),
            event("impact.hull.kinetic", "impacts", "hull_kinetic", Category.IMPACT, 2, 0.05, -10.5, 2, true),
            event("impact.hull.energy", "impacts", "hull_energy", Category.IMPACT, 2, 0.05, -12.0, 2, true),
            event("impact.hull.beam", "impacts", "hull_beam", Category.IMPACT, 2, 0.05, -12.0, 2, true),
            event("impact.hull.explosive", "impacts", "hull_explosive", Category.IMPACT, 2, 0.05, -12.0, 2, true),
            event("impact.explosion", "impacts", "explosion", Category.IMPACT, 3, 0.24, -9.0, 2, true),

            // Hazards
            event("hazard.fire_ignition", "hazards", "fire_ignition", Category.HAZARD, 2, 0.25, -12.0, 2, true),
            event("hazard.fire_spread", "hazards", "fire_spread", Category.HAZARD, 2, 0.35, -12.0, 2, true),
            event("hazard.fire_suppression", "hazards", "fire_suppression", Category.HAZARD, 2, 0.30, -12.0, 2, true),

            // Subsystem failures
            event("subsystem.engines_offline", "subsystems", "engines_offline", Category.SUBSYSTEM, 2, 0.60, -11.0, 1, true),
            event("subsystem.reactor_offline", "subsystems", "reactor_offline", Category.SUBSYSTEM, 3, 0.80, -10.5, 1, true),
            event("subsystem.sensors_offline", "subsystems", "sensors_offline", Category.SUBSYSTEM, 2, 0.70, -11.5, 1, true),
            event("subsystem.weapons_offline", "subsystems", "weapons_offline", Category.SUBSYSTEM, 2, 0.70, -11.5, 1, true),
            event("subsystem.shields_offline", "subsystems", "shields_offline", Category.SUBSYSTEM, 2, 0.70, -11.5, 1, true),

            // Ambience
            event("ambience.bridge_ambient", "ambient", "bridge_ambient", Category.AMBIENCE, 0, 1.00, -26.0, 1, true),
            event("ambience.engine_loop", "ambient", "engine_loop", Category.AMBIENCE, 0, 1.00, -27.0, 1, true),
            event("ambience.station_hum", "ambient", "station_hum", Category.AMBIENCE, 0, 1.00, -27.5, 1, true)
    );

    private static final Map<String, EventSpec> BY_ID = indexById(EVENTS);
    private static final Map<String, Integer> VARIANT_COUNT_BY_EVENT_ID = indexVariantCounts(EVENTS);

    private SfxManifest() {}

    public static List<EventSpec> all() {
        return EVENTS;
    }

    public static EventSpec byId(String eventId) {
        if (eventId == null) return null;
        return BY_ID.get(eventId);
    }

    public static int variantCount(EventSpec spec) {
        if (spec == null) return 0;
        Integer cached = VARIANT_COUNT_BY_EVENT_ID.get(spec.eventId());
        if (cached != null) return cached;
        return VARIANT_COUNT_BY_EVENT_ID.computeIfAbsent(spec.eventId(), k -> countVariants(spec));
    }

    private static int countVariants(EventSpec spec) {
        if (spec == null) return 0;
        File dir = new File(ROOT_AUDIO, spec.folder());
        String prefix = spec.filePrefix().toLowerCase(Locale.US);
        if (dir.isDirectory()) {
            File[] matches = dir.listFiles(f -> {
                if (f == null || !f.isFile()) return false;
                String n = f.getName().toLowerCase(Locale.US);
                if (!n.endsWith(".wav")) return false;
                return n.equals(prefix + ".wav") || n.startsWith(prefix + "_");
            });
            if (matches != null && matches.length > 0) return matches.length;
        }
        return bundledVariantCount(spec.folder(), prefix);
    }

    private static int bundledVariantCount(String folder, String prefix) {
        if (folder == null || prefix == null || prefix.isBlank()) return 0;
        int count = 0;
        if (resourceExists("/audio/" + folder + "/" + prefix + ".wav")) count++;
        for (int i = 1; i <= MAX_RESOURCE_VARIANTS; i++) {
            String candidate = String.format(Locale.US, "/audio/%s/%s_%02d.wav", folder, prefix, i);
            if (resourceExists(candidate)) count++;
        }
        return count;
    }

    private static boolean resourceExists(String path) {
        return path != null && SfxManifest.class.getResource(path) != null;
    }

    public static CoverageReport coverage() {
        List<CoverageRow> rows = new ArrayList<>();
        int ok = 0;
        int fail = 0;
        for (EventSpec spec : EVENTS) {
            int count = variantCount(spec);
            boolean pass = count >= spec.requiredVariants();
            if (pass) ok++;
            else fail++;
            rows.add(new CoverageRow(spec, count, pass));
        }
        rows.sort(Comparator.comparing(r -> r.spec().eventId()));
        return new CoverageReport(Collections.unmodifiableList(rows), ok, fail);
    }

    private static EventSpec event(String eventId,
                                   String folder,
                                   String prefix,
                                   Category category,
                                   int priority,
                                   double cooldownSec,
                                   double gainDb,
                                   int requiredVariants,
                                   boolean coreLoop) {
        return new EventSpec(
                eventId,
                folder,
                prefix,
                category,
                priority,
                cooldownSec,
                gainDb,
                Math.max(1, requiredVariants),
                coreLoop
        );
    }

    private static Map<String, EventSpec> indexById(List<EventSpec> events) {
        Map<String, EventSpec> map = new HashMap<>();
        for (EventSpec spec : events) {
            map.put(spec.eventId(), spec);
        }
        return Collections.unmodifiableMap(map);
    }

    private static Map<String, Integer> indexVariantCounts(List<EventSpec> events) {
        Map<String, Integer> map = new ConcurrentHashMap<>();
        for (EventSpec spec : events) {
            if (spec == null || spec.eventId() == null || spec.eventId().isBlank()) continue;
            map.put(spec.eventId(), countVariants(spec));
        }
        return map;
    }
}
