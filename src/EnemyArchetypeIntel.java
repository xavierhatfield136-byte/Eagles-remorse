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
            case ARTILLERY_SHIP -> "Pocket Artillery Ship";
            case MISSILE_BOAT -> "Siege Missile Boat";
            case CIWS_CORVETTE -> "CIWS Corvette";
            case LIGHT_CRUISER -> "Light Cruiser";
            case MEDIUM_CRUISER -> "Assault Cruiser";
            case CRUISER -> "Guided Missile Cruiser";
            case BATTLECRUISER -> "Battlecruiser";
            case BATTLESHIP -> "Battleship";
            case DREADNOUGHT -> "Dreadnought";
            case SUPERSHIP -> "Supership";
            case TRANSPORT_TITAN -> "Transport Titan";
            case BULWARK_TITAN -> "Bulwark Titan";
            case CARRIER_SUPPORT_TITAN -> "Carrier Support Titan";
            case VANGUARD_TITAN -> "Vanguard Titan";
            case INTERDICTION_TITAN -> "Interdiction Titan";
            case COMMAND_INTEL_TITAN -> "Command / Intel Titan";
            case BOARDING_RECOVERY_TITAN -> "Boarding / Recovery Titan";
            case ARTILLERY_TITAN -> "Artillery Titan";
            case SHIELD_BASTION_TITAN -> "Shield Bastion Titan";
            case FLEET_TELEPORTER_TITAN -> "Fleet Teleporter Titan";
            case ELITE_SUPERSHIP_COMMAND_TITAN -> "Elite Supership Command Titan";
            case ELITE_REINFORCEMENTS_TITAN -> "Elite Reinforcements Titan";
            case MOBILE_STATION_TITAN -> "Mobile Station Titan";
            case HYPERWEAPON_TITAN -> "Hyperweapon Titan";
            case MOTHERSHIP -> "Mothership";
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
            case ARTILLERY_SHIP -> "Rush it or break line-of-sight before the spinal gun lands repeated hits.";
            case MISSILE_BOAT -> "Rush under CIWS cover and break target lock.";
            case CIWS_CORVETTE -> "Lead with guns/beam bolts, not missiles.";
            case LIGHT_CRUISER -> "Kite near max range while stripping shield.";
            case MEDIUM_CRUISER -> "Clear escorts first, then dump missiles.";
            case CRUISER -> "Close quickly under cover; do not stay at long missile range.";
            case BATTLECRUISER -> "Stay off the bow and dodge broadside arcs.";
            case BATTLESHIP -> "Flank and sustain pressure; avoid frontal duel.";
            case DREADNOUGHT -> "Phase fight: clear adds, then burst windows.";
            case SUPERSHIP -> "Break line-of-sight on charge and spread before superweapon fire.";
            case TRANSPORT_TITAN -> "Kill the repair spine early before the whole fleet stabilizes.";
            case BULWARK_TITAN -> "Do not front-duel it; peel escorts and attack from offset angles.";
            case CARRIER_SUPPORT_TITAN -> "Pressure the hull fast to collapse its strike-craft sustain.";
            case VANGUARD_TITAN -> "Bait the lunge, then punish while it turns back into line.";
            case INTERDICTION_TITAN -> "Stay grouped and avoid isolated warp attempts.";
            case COMMAND_INTEL_TITAN -> "Break sensor support first to weaken the whole enemy line.";
            case BOARDING_RECOVERY_TITAN -> "Keep shields up and deny it close disablement windows.";
            case ARTILLERY_TITAN -> "Use cover, force repositioning, and never sit on the same lane.";
            case SHIELD_BASTION_TITAN -> "Strip its escorts and grind it down after the shield wall falters.";
            case FLEET_TELEPORTER_TITAN -> "Track re-entry angles and punish it when the jump finishes.";
            case ELITE_SUPERSHIP_COMMAND_TITAN -> "Spread out so its strike group cannot focus-fire one target.";
            case ELITE_REINFORCEMENTS_TITAN -> "Break its honor guard first so the titan cannot keep the assault group stabilized.";
            case MOBILE_STATION_TITAN -> "Destroy the service hub before committing to a long fleet brawl.";
            case HYPERWEAPON_TITAN -> "Break line-of-sight and force the shot into overkill or empty space.";
            case MOTHERSHIP -> "Treat it like a mobile base: clear Titan cover, then collapse it from the flanks.";
            case CARRIER, DRONE_CARRIER -> "Kill carrier hull to collapse wing pressure.";
            case TRANSPORT -> "Snipe early to remove repair aura.";
            case MINER, HAULER -> "Pick off to starve enemy economy.";
            case STATIC_TURRET -> "Strafe perpendicular and outrange with missiles.";
            case BASE -> "Capture map control first, then siege as a group.";
        };
    }

    public static int archetypeCount() {
        // Number of role entries with explicit counter mapping.
        return ShipRole.values().length;
    }
}
