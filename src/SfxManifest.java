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
            event("super.blue.charge", "weapons", "super_blue_charge", Category.WEAPON, 3, 8.50, -10.0, 1, true),
            event("super.blue.fire", "weapons", "super_blue_fire", Category.WEAPON, 3, 0.90, -9.0, 1, true),
            event("hyper.blue.charge", "weapons", "hyper_blue_charge", Category.WEAPON, 3, 8.50, -9.5, 1, true),
            event("hyper.blue.fire", "weapons", "hyper_blue_fire", Category.WEAPON, 3, 0.90, -8.8, 1, true),
            event("super.red.charge", "weapons", "super_red_charge", Category.WEAPON, 3, 8.50, -10.0, 1, true),
            event("super.red.fire", "weapons", "super_red_fire", Category.WEAPON, 3, 0.90, -9.0, 1, true),
            event("hyper.red.charge", "weapons", "hyper_red_charge", Category.WEAPON, 3, 8.50, -9.5, 1, true),
            event("hyper.red.fire", "weapons", "hyper_red_fire", Category.WEAPON, 3, 0.90, -8.8, 1, true),
            event("super.green.charge", "weapons", "super_green_charge", Category.WEAPON, 3, 8.50, -10.0, 1, true),
            event("super.green.fire", "weapons", "super_green_fire", Category.WEAPON, 3, 0.90, -9.0, 1, true),
            event("hyper.green.charge", "weapons", "hyper_green_charge", Category.WEAPON, 3, 8.50, -9.5, 1, true),
            event("hyper.green.fire", "weapons", "hyper_green_fire", Category.WEAPON, 3, 0.90, -8.8, 1, true),
            event("super.yellow.charge", "weapons", "super_yellow_charge", Category.WEAPON, 3, 8.50, -10.0, 1, true),
            event("super.yellow.fire", "weapons", "super_yellow_fire", Category.WEAPON, 3, 0.90, -9.0, 1, true),
            event("hyper.yellow.charge", "weapons", "hyper_yellow_charge", Category.WEAPON, 3, 8.50, -9.5, 1, true),
            event("hyper.yellow.fire", "weapons", "hyper_yellow_fire", Category.WEAPON, 3, 0.90, -8.8, 1, true),
            event("weapon.blue.small_fire", "weapons", "weapon_blue_small_fire", Category.WEAPON, 1, 0.04, -14.5, 2, true),
            event("weapon.blue.medium_fire", "weapons", "weapon_blue_medium_fire", Category.WEAPON, 2, 0.06, -13.5, 2, true),
            event("weapon.blue.capital_fire", "weapons", "weapon_blue_capital_fire", Category.WEAPON, 3, 0.12, -12.5, 2, true),
            event("weapon.red.small_fire", "weapons", "weapon_red_small_fire", Category.WEAPON, 1, 0.04, -14.5, 2, true),
            event("weapon.red.medium_fire", "weapons", "weapon_red_medium_fire", Category.WEAPON, 2, 0.06, -13.5, 2, true),
            event("weapon.red.capital_fire", "weapons", "weapon_red_capital_fire", Category.WEAPON, 3, 0.12, -12.5, 2, true),
            event("weapon.green.small_fire", "weapons", "weapon_green_small_fire", Category.WEAPON, 1, 0.04, -14.5, 2, true),
            event("weapon.green.medium_fire", "weapons", "weapon_green_medium_fire", Category.WEAPON, 2, 0.06, -13.5, 2, true),
            event("weapon.green.capital_fire", "weapons", "weapon_green_capital_fire", Category.WEAPON, 3, 0.12, -12.5, 2, true),
            event("weapon.yellow.small_fire", "weapons", "weapon_yellow_small_fire", Category.WEAPON, 1, 0.04, -14.5, 2, true),
            event("weapon.yellow.medium_fire", "weapons", "weapon_yellow_medium_fire", Category.WEAPON, 2, 0.06, -13.5, 2, true),
            event("weapon.yellow.capital_fire", "weapons", "weapon_yellow_capital_fire", Category.WEAPON, 3, 0.12, -12.5, 2, true),
            event("weapon.missile_launch", "weapons", "missile_launch", Category.WEAPON, 2, 0.10, -12.0, 2, true),
            event("weapon.torpedo_launch", "weapons", "torpedo_launch", Category.WEAPON, 3, 0.14, -11.0, 2, true),
            event("weapon.ciws_fire", "weapons", "ciws_fire", Category.WEAPON, 1, 0.06, -21.0, 2, true),
            event("flight.launch", "weapons", "flight_launch", Category.WEAPON, 2, 0.18, -11.0, 1, true),
            event("warp.charge_start", "weapons", "warp_charge_start", Category.WEAPON, 2, 0.45, -10.5, 1, true),
            event("warp.spool_up", "weapons", "warp_spool_up", Category.WEAPON, 2, 0.45, -10.5, 1, true),
            event("warp.exit", "weapons", "warp_exit", Category.WEAPON, 3, 0.40, -9.5, 1, true),

            // Impacts by damage class
            event("impact.shield.kinetic", "impacts", "shield_kinetic", Category.IMPACT, 2, 0.05, -13.0, 2, true),
            event("impact.shield.energy", "impacts", "shield_energy", Category.IMPACT, 2, 0.05, -13.0, 2, true),
            event("impact.shield.beam", "impacts", "shield_beam", Category.IMPACT, 2, 0.05, -13.0, 2, true),
            event("impact.shield.explosive", "impacts", "shield_explosive", Category.IMPACT, 2, 0.05, -13.0, 2, true),
            event("impact.shield.damage", "impacts", "shield_damage", Category.IMPACT, 2, 0.12, -12.0, 2, true),
            event("impact.hull.kinetic", "impacts", "hull_kinetic", Category.IMPACT, 2, 0.05, -10.5, 2, true),
            event("impact.hull.energy", "impacts", "hull_energy", Category.IMPACT, 2, 0.05, -12.0, 2, true),
            event("impact.hull.beam", "impacts", "hull_beam", Category.IMPACT, 2, 0.05, -12.0, 2, true),
            event("impact.hull.explosive", "impacts", "hull_explosive", Category.IMPACT, 2, 0.05, -12.0, 2, true),
            event("impact.hull.damage", "impacts", "hull_damage", Category.IMPACT, 2, 0.14, -11.0, 2, true),
            event("impact.explosion", "impacts", "explosion", Category.IMPACT, 3, 0.24, -9.0, 2, true),
            event("impact.ship_death_major", "impacts", "ship_death_major", Category.IMPACT, 3, 0.42, -8.0, 1, true),

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
