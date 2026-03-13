/**
 * Enemy archetype intel and practical counter guidance for HUD/UX.
 * This is intentionally static so campaign/modes can surface consistent hints.
 */
public final class EnemyArchetypeIntel {
    private EnemyArchetypeIntel() {}

    public static String archetypeLabel(ShipRole role) {
        if (role == null) return "";
        return switch (role) {
            case PATROL -> "Skirmisher Patrol";
            case PICKET -> "Long-Range Picket";
            case STEALTH_SHIP -> "Stealth Raider";
            case FIGHTER -> "Interceptor";
            case BOMBER -> "Strike Bomber";
            case PD_CRAFT -> "PD Escort Frigate";
            case DRONE -> "Missile Drone";
            case FRIGATE -> "Line Frigate";
            case MISSILE_BOAT -> "Siege Missile Boat";
            case CIWS_CORVETTE -> "CIWS Corvette";
            case LIGHT_CRUISER -> "Light Cruiser";
            case MEDIUM_CRUISER -> "Assault Cruiser";
            case CRUISER -> "Guided Missile Cruiser";
            case BATTLECRUISER -> "Battlecruiser";
            case BATTLESHIP -> "Battleship";
            case DREADNOUGHT -> "Dreadnought";
            case SUPERSHIP -> "Wave-Motion Supership";
            case CARRIER, DRONE_CARRIER -> "Carrier";
            case TRANSPORT -> "Support Transport";
            case MINER -> "Logistics Miner";
            case HAULER -> "Supply Hauler";
            case STATIC_TURRET -> "Defense Node";
            case BASE -> "Stronghold";
        };
    }

    public static String counterHint(ShipRole role) {
        if (role == null) return "";
        return switch (role) {
            case PATROL -> "Focus with guns before it flanks.";
            case PICKET -> "Close range and break sight with asteroids.";
            case STEALTH_SHIP -> "Force reveal with sustained fire and keep lock cycling.";
            case FIGHTER -> "Let CIWS and wide gun arcs thin them.";
            case BOMBER -> "Prioritize immediately to prevent missile salvos.";
            case PD_CRAFT -> "Use direct gun DPS and split missile salvos across the escort screen.";
            case DRONE -> "Use CIWS + fast guns before missile swarms saturate.";
            case FRIGATE -> "Orbit mid-range and concentrate fire.";
            case MISSILE_BOAT -> "Rush under CIWS cover and break target lock.";
            case CIWS_CORVETTE -> "Lead with guns/beam bolts, not missiles.";
            case LIGHT_CRUISER -> "Kite near max range while stripping shield.";
            case MEDIUM_CRUISER -> "Clear escorts first, then dump missiles.";
            case CRUISER -> "Close quickly under cover; do not stay at long missile range.";
            case BATTLECRUISER -> "Stay off the bow and dodge broadside arcs.";
            case BATTLESHIP -> "Flank and sustain pressure; avoid frontal duel.";
            case DREADNOUGHT -> "Phase fight: clear adds, then burst windows.";
            case SUPERSHIP -> "Break line-of-sight on charge and spread before wave fire.";
            case CARRIER, DRONE_CARRIER -> "Kill carrier hull to collapse wing pressure.";
            case TRANSPORT -> "Snipe early to remove repair aura.";
            case MINER, HAULER -> "Pick off to starve enemy economy.";
            case STATIC_TURRET -> "Strafe perpendicular and outrange with missiles.";
            case BASE -> "Capture map control first, then siege as a group.";
        };
    }

    public static int archetypeCount() {
        // Number of role entries with explicit counter mapping.
        return 22;
    }
}
