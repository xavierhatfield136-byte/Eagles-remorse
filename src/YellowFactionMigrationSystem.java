import java.util.Locale;

/** Backward-compatible migration and content alias policy for the retired monolithic Yellow faction. */
public final class YellowFactionMigrationSystem {
    public record ShipSnapshot(String id, String name, String factionId, String hullId,
                               int hullDamage, int armorDamage, int cargo,
                               String commander, String serviceHistory, String missionState) {}
    public record ContentReference(String requestedId, Faction faction, boolean migrated,
                                   String warning) {}

    private YellowFactionMigrationSystem() {}

    public static ShipSnapshot migrateShip(ShipSnapshot legacy) {
        if (legacy == null) return null;
        ContentReference faction = resolveContentFaction(legacy.factionId(), legacy.id());
        return new ShipSnapshot(legacy.id(), legacy.name(), faction.faction().name(), legacy.hullId(),
                legacy.hullDamage(), legacy.armorDamage(), legacy.cargo(), legacy.commander(),
                legacy.serviceHistory(), legacy.missionState());
    }

    public static ContentReference resolveContentFaction(String requestedId, String stableKey) {
        String value = requestedId == null ? "" : requestedId.trim();
        if (value.equalsIgnoreCase("yellow") || value.equalsIgnoreCase("team_d")) {
            Faction successor = brightFor(stableKey) ? Faction.BRIGHT_YELLOW : Faction.DARK_YELLOW;
            return new ContentReference(value, successor, true,
                    "Legacy faction '" + value + "' was migrated to " + successor.teamName()
                            + "; update the content pack to BRIGHT_YELLOW or DARK_YELLOW.");
        }
        try {
            return new ContentReference(value, Faction.valueOf(value.toUpperCase(Locale.US)), false, "");
        } catch (IllegalArgumentException ignored) {
            return new ContentReference(value, Faction.BRIGHT_YELLOW, true,
                    "Unknown faction '" + value + "'; choose an explicit stable faction ID. "
                            + "Bright Yellow was used as a recoverable fallback.");
        }
    }

    public static String migrateObjectiveText(String objective) {
        if (objective == null || objective.isBlank()) return objective == null ? "" : objective;
        return objective.replaceAll("(?i)\\bYellow faction\\b", "Yellow successor factions")
                .replaceAll("(?i)\\bYellow forces\\b", "Bright Yellow or Dark Orange-Yellow forces")
                .replaceAll("(?i)\\bYellow territory\\b", "Bright Yellow or Dark Orange-Yellow territory");
    }

    public static String presentationLabel(Faction faction, String objectName, String context) {
        Faction safe = faction == null ? Faction.BRIGHT_YELLOW : faction;
        String name = objectName == null || objectName.isBlank() ? "Unnamed asset" : objectName.trim();
        String view = context == null || context.isBlank() ? "record" : context.trim();
        return safe.teamName() + " [" + safe.transponderPrefix() + "/" + safe.insigniaKey() + "]"
                + " | " + view + " | " + name;
    }

    private static boolean brightFor(String stableKey) {
        String key = stableKey == null ? "" : stableKey;
        int number = -1;
        for (int i = key.length() - 1; i >= 0; i--) {
            if (!Character.isDigit(key.charAt(i))) {
                if (i < key.length() - 1) {
                    try { number = Integer.parseInt(key.substring(i + 1)); } catch (NumberFormatException ignored) {}
                }
                break;
            }
            if (i == 0) {
                try { number = Integer.parseInt(key); } catch (NumberFormatException ignored) {}
            }
        }
        return number >= 0 ? (number & 1) == 1 : Math.floorMod(key.hashCode(), 2) == 0;
    }
}
