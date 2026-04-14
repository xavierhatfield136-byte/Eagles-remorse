import java.util.Locale;

/**
 * Central identity definitions for faction-themed passives and faction-role bonus stats.
 *
 * Role bonuses are resolved dynamically from both faction and role so the same hull class can
 * play differently across Blue, Red, Green, and Yellow fleets.
 */
public final class ShipIdentityRegistry {

    private ShipIdentityRegistry() {}

    public enum IdentityStat {
        NONE,
        WEAPON_DAMAGE,
        WEAPON_CYCLE,
        SENSOR_RANGE,
        SHIELD_REGEN,
        MOBILITY,
        CIWS_RANGE,
        MISSILE_DAMAGE,
        MISSILE_CYCLE,
        STRIKE_CRAFT,
        SUPPORT_FIELD,
        MINING_YIELD,
        SUPERWEAPON_RECHARGE,
        WARP_CHARGE
    }

    public enum FactionTraitId {
        NONE,
        COMMAND_NET,
        KINETIC_MOMENTUM,
        AEGIS_LATTICE,
        VIPER_ASSAULT
    }

    public static final class FactionTrait {
        public final FactionTraitId id;
        public final String name;
        public final String description;

        private FactionTrait(FactionTraitId id, String name, String description) {
            this.id = id;
            this.name = name;
            this.description = description;
        }
    }

    public static final class RoleBonus {
        public final IdentityStat stat;
        public final double multiplier;
        public final String name;
        public final String description;

        private RoleBonus(IdentityStat stat, double multiplier, String name, String description) {
            this.stat = stat;
            this.multiplier = multiplier;
            this.name = name;
            this.description = description;
        }
    }

    private static final class BonusSpec {
        final IdentityStat stat;
        final double multiplier;
        final String name;

        private BonusSpec(IdentityStat stat, double multiplier, String name) {
            this.stat = stat;
            this.multiplier = multiplier;
            this.name = name;
        }
    }

    private static final FactionTrait NONE_TRAIT = new FactionTrait(
            FactionTraitId.NONE,
            "No Faction Trait",
            "No faction-themed passive is configured."
    );
    private static final FactionTrait BLUE_COMMAND_NET = new FactionTrait(
            FactionTraitId.COMMAND_NET,
            "Command Net",
            "Stable, low-chaos ships coordinate better and recharge fleet-control systems faster."
    );
    private static final FactionTrait RED_KINETIC_MOMENTUM = new FactionTrait(
            FactionTraitId.KINETIC_MOMENTUM,
            "Kinetic Momentum",
            "Recent weapon fire feeds pressure back into the ship, improving offensive tempo."
    );
    private static final FactionTrait GREEN_AEGIS_LATTICE = new FactionTrait(
            FactionTraitId.AEGIS_LATTICE,
            "Aegis Lattice",
            "Intact shield-strip geometry strengthens shielding, sensors, and precision fire control."
    );
    private static final FactionTrait YELLOW_VIPER_ASSAULT = new FactionTrait(
            FactionTraitId.VIPER_ASSAULT,
            "Viper Assault",
            "Hull-first assault groups keep salvos coming and spool for jumps under pressure."
    );

    private static final RoleBonus NO_ROLE_BONUS = new RoleBonus(
            IdentityStat.NONE,
            1.0,
            "No Role Bonus",
            "No role-specific bonus is configured."
    );

    public static FactionTrait factionTraitFor(Faction faction) {
        if (faction == null) return NONE_TRAIT;
        return switch (faction) {
            case PLAYER, ALLY -> BLUE_COMMAND_NET;
            case ENEMY -> RED_KINETIC_MOMENTUM;
            case TEAM_C -> GREEN_AEGIS_LATTICE;
            case TEAM_D -> YELLOW_VIPER_ASSAULT;
        };
    }

    public static RoleBonus roleBonusFor(Faction faction, ShipRole role) {
        if (role == null) return NO_ROLE_BONUS;
        Faction key = normalizeFaction(faction);
        if (key == null) return NO_ROLE_BONUS;

        return switch (role) {
            case PATROL -> matrixBonus(key, role,
                    spec(IdentityStat.SENSOR_RANGE, 1.16, "Recon Uplink"),
                    spec(IdentityStat.WEAPON_CYCLE, 1.15, "Skirmish Pattern"),
                    spec(IdentityStat.SHIELD_REGEN, 1.14, "Screen Lattice"),
                    spec(IdentityStat.MOBILITY, 1.16, "Raider Burst"));
            case PICKET -> matrixBonus(key, role,
                    spec(IdentityStat.WARP_CHARGE, 1.16, "Interception Spool"),
                    spec(IdentityStat.WEAPON_DAMAGE, 1.14, "Breaker Volley"),
                    spec(IdentityStat.CIWS_RANGE, 1.18, "Barrier Net"),
                    spec(IdentityStat.MOBILITY, 1.15, "Cutoff Thrusters"));
            case STEALTH_SHIP -> matrixBonus(key, role,
                    spec(IdentityStat.SENSOR_RANGE, 1.14, "Ghost Sensors"),
                    spec(IdentityStat.WEAPON_DAMAGE, 1.16, "Execution Strike"),
                    spec(IdentityStat.SHIELD_REGEN, 1.14, "Phase Veil"),
                    spec(IdentityStat.WARP_CHARGE, 1.16, "Raid Spool"));
            case FIGHTER -> matrixBonus(key, role,
                    spec(IdentityStat.WEAPON_CYCLE, 1.14, "Vector Control"),
                    spec(IdentityStat.MOBILITY, 1.16, "Dogfight Burn"),
                    spec(IdentityStat.SHIELD_REGEN, 1.14, "Deflection Loop"),
                    spec(IdentityStat.WEAPON_DAMAGE, 1.15, "Viper Snap"));
            case BOMBER -> matrixBonus(key, role,
                    spec(IdentityStat.MISSILE_DAMAGE, 1.18, "Lock Solutions"),
                    spec(IdentityStat.MOBILITY, 1.14, "Attack Run Thrusters"),
                    spec(IdentityStat.SHIELD_REGEN, 1.14, "Escort Envelope"),
                    spec(IdentityStat.MISSILE_CYCLE, 1.18, "Salvo Feed"));
            case PD_CRAFT -> matrixBonus(key, role,
                    spec(IdentityStat.CIWS_RANGE, 1.22, "Escort Screen"),
                    spec(IdentityStat.MOBILITY, 1.14, "Cut-In Jets"),
                    spec(IdentityStat.SHIELD_REGEN, 1.14, "Guardian Lattice"),
                    spec(IdentityStat.WEAPON_CYCLE, 1.14, "Flak Rush"));
            case DRONE -> matrixBonus(key, role,
                    spec(IdentityStat.SENSOR_RANGE, 1.15, "Relay Eyes"),
                    spec(IdentityStat.WEAPON_CYCLE, 1.16, "Swarm Pressure"),
                    spec(IdentityStat.MOBILITY, 1.15, "Vector Weave"),
                    spec(IdentityStat.MISSILE_DAMAGE, 1.16, "Sting Payload"));
            case FRIGATE -> matrixBonus(key, role,
                    spec(IdentityStat.SHIELD_REGEN, 1.10, "Line Shielding"),
                    spec(IdentityStat.WEAPON_DAMAGE, 1.14, "Impact Drill"),
                    spec(IdentityStat.SENSOR_RANGE, 1.14, "Fire-Control Relay"),
                    spec(IdentityStat.MISSILE_CYCLE, 1.16, "Volley Feeds"));
            case ARTILLERY_SHIP -> matrixBonus(key, role,
                    spec(IdentityStat.SENSOR_RANGE, 1.16, "Fire-Control Relay"),
                    spec(IdentityStat.WEAPON_DAMAGE, 1.18, "Overpressure Cannon"),
                    spec(IdentityStat.WEAPON_CYCLE, 1.14, "Beam Stabilizers"),
                    spec(IdentityStat.MOBILITY, 1.14, "Shoot-and-Scoot Thrusters"));
            case MISSILE_BOAT -> matrixBonus(key, role,
                    spec(IdentityStat.SENSOR_RANGE, 1.14, "Target Data Link"),
                    spec(IdentityStat.MISSILE_DAMAGE, 1.18, "Breaker Salvos"),
                    spec(IdentityStat.SHIELD_REGEN, 1.14, "Stand-Off Screens"),
                    spec(IdentityStat.MISSILE_CYCLE, 1.20, "Rack Cycling"));
            case CIWS_CORVETTE -> matrixBonus(key, role,
                    spec(IdentityStat.SENSOR_RANGE, 1.14, "Threat Grid"),
                    spec(IdentityStat.MOBILITY, 1.14, "Charge Intercepts"),
                    spec(IdentityStat.CIWS_RANGE, 1.20, "Aegis Mesh"),
                    spec(IdentityStat.MISSILE_DAMAGE, 1.14, "Counterstrike Pods"));
            case LIGHT_CRUISER -> matrixBonus(key, role,
                    spec(IdentityStat.WEAPON_CYCLE, 1.10, "Gunnery Links"),
                    spec(IdentityStat.WEAPON_DAMAGE, 1.12, "Impact Broadside"),
                    spec(IdentityStat.SHIELD_REGEN, 1.12, "Shield Spine"),
                    spec(IdentityStat.MISSILE_CYCLE, 1.16, "Salvo Deck"));
            case MEDIUM_CRUISER -> matrixBonus(key, role,
                    spec(IdentityStat.SHIELD_REGEN, 1.12, "Bluewater Screens"),
                    spec(IdentityStat.WEAPON_CYCLE, 1.12, "Relentless Fire"),
                    spec(IdentityStat.SENSOR_RANGE, 1.14, "Beam Ranging"),
                    spec(IdentityStat.MISSILE_DAMAGE, 1.16, "Assault Magazine"));
            case CRUISER -> matrixBonus(key, role,
                    spec(IdentityStat.SENSOR_RANGE, 1.14, "Missile Plotting"),
                    spec(IdentityStat.WEAPON_CYCLE, 1.12, "Pressure Battery"),
                    spec(IdentityStat.SHIELD_REGEN, 1.12, "Aegis Pickets"),
                    spec(IdentityStat.MISSILE_DAMAGE, 1.16, "Siege Rack"));
            case BATTLECRUISER -> matrixBonus(key, role,
                    spec(IdentityStat.MOBILITY, 1.12, "Pursuit Drive"),
                    spec(IdentityStat.WEAPON_DAMAGE, 1.14, "Ramming Barrage"),
                    spec(IdentityStat.SENSOR_RANGE, 1.14, "Long-Range Fire Control"),
                    spec(IdentityStat.WARP_CHARGE, 1.14, "Breakthrough Spool"));
            case BATTLESHIP -> matrixBonus(key, role,
                    spec(IdentityStat.WEAPON_DAMAGE, 1.14, "Siege Broadside"),
                    spec(IdentityStat.WEAPON_CYCLE, 1.12, "Rolling Salvos"),
                    spec(IdentityStat.SHIELD_REGEN, 1.14, "Bulwark Lattice"),
                    spec(IdentityStat.MISSILE_DAMAGE, 1.16, "Storm Battery"));
            case DREADNOUGHT -> matrixBonus(key, role,
                    spec(IdentityStat.SHIELD_REGEN, 1.14, "Citadel Reactors"),
                    spec(IdentityStat.WEAPON_DAMAGE, 1.14, "Linebreaker Guns"),
                    spec(IdentityStat.SENSOR_RANGE, 1.14, "Capital Ranging Web"),
                    spec(IdentityStat.MISSILE_CYCLE, 1.16, "Warhead Feed"));
            case SUPERSHIP -> matrixBonus(key, role,
                    spec(IdentityStat.SUPERWEAPON_RECHARGE, 1.14, "Capacitor Banks"),
                    spec(IdentityStat.WEAPON_DAMAGE, 1.14, "Catastrophic Impact"),
                    spec(IdentityStat.SHIELD_REGEN, 1.14, "Lattice Bastion"),
                    spec(IdentityStat.MISSILE_CYCLE, 1.18, "Apocalypse Salvos"));
            case TRANSPORT_TITAN -> matrixBonus(key, role,
                    spec(IdentityStat.SUPPORT_FIELD, 1.28, "Fleet Sustainment"),
                    spec(IdentityStat.SHIELD_REGEN, 1.16, "Convoy Shield Spine"),
                    spec(IdentityStat.WARP_CHARGE, 1.16, "Extraction Corridor"),
                    spec(IdentityStat.MOBILITY, 1.12, "Heavy Transfer Drives"));
            case BULWARK_TITAN -> matrixBonus(key, role,
                    spec(IdentityStat.SHIELD_REGEN, 1.18, "Guard Bastion"),
                    spec(IdentityStat.CIWS_RANGE, 1.18, "Guardian Mesh"),
                    spec(IdentityStat.WEAPON_DAMAGE, 1.14, "Linebreaker Ram"),
                    spec(IdentityStat.SUPPORT_FIELD, 1.16, "Covering Wall"));
            case CARRIER_SUPPORT_TITAN -> matrixBonus(key, role,
                    spec(IdentityStat.STRIKE_CRAFT, 1.24, "Flight Control Nexus"),
                    spec(IdentityStat.SUPPORT_FIELD, 1.20, "Service Grid"),
                    spec(IdentityStat.SHIELD_REGEN, 1.14, "Hangar Screens"),
                    spec(IdentityStat.MISSILE_CYCLE, 1.16, "Deck Feed"));
            case VANGUARD_TITAN -> matrixBonus(key, role,
                    spec(IdentityStat.MOBILITY, 1.16, "Gap-Runner Drives"),
                    spec(IdentityStat.WEAPON_DAMAGE, 1.14, "Rapid Breach Guns"),
                    spec(IdentityStat.WARP_CHARGE, 1.16, "Reaction Spool"),
                    spec(IdentityStat.SENSOR_RANGE, 1.14, "Forward Scout Net"));
            case INTERDICTION_TITAN -> matrixBonus(key, role,
                    spec(IdentityStat.WARP_CHARGE, 1.16, "Pursuit Lock"),
                    spec(IdentityStat.SENSOR_RANGE, 1.16, "Trap Ranging"),
                    spec(IdentityStat.MISSILE_DAMAGE, 1.16, "Snare Payload"),
                    spec(IdentityStat.MOBILITY, 1.12, "Pincer Thrusters"));
            case COMMAND_INTEL_TITAN -> matrixBonus(key, role,
                    spec(IdentityStat.SENSOR_RANGE, 1.20, "Command Web"),
                    spec(IdentityStat.WEAPON_CYCLE, 1.14, "Fire Control Relay"),
                    spec(IdentityStat.SUPPORT_FIELD, 1.16, "Command Bus"),
                    spec(IdentityStat.SHIELD_REGEN, 1.14, "Protected Flag Bridge"));
            case BOARDING_RECOVERY_TITAN -> matrixBonus(key, role,
                    spec(IdentityStat.SUPPORT_FIELD, 1.20, "Recovery Lanes"),
                    spec(IdentityStat.WEAPON_DAMAGE, 1.14, "Disablement Batteries"),
                    spec(IdentityStat.WARP_CHARGE, 1.14, "Extraction Pull"),
                    spec(IdentityStat.SHIELD_REGEN, 1.14, "Assault Screens"));
            case ARTILLERY_TITAN -> matrixBonus(key, role,
                    spec(IdentityStat.SENSOR_RANGE, 1.18, "Ranging Cathedral"),
                    spec(IdentityStat.WEAPON_DAMAGE, 1.16, "Siege Spine"),
                    spec(IdentityStat.WEAPON_CYCLE, 1.12, "Fire Solution Loop"),
                    spec(IdentityStat.MISSILE_CYCLE, 1.16, "Auxiliary Salvos"));
            case SHIELD_BASTION_TITAN -> matrixBonus(key, role,
                    spec(IdentityStat.SHIELD_REGEN, 1.20, "Layered Aegis"),
                    spec(IdentityStat.SUPPORT_FIELD, 1.18, "Shelter Field"),
                    spec(IdentityStat.CIWS_RANGE, 1.18, "Ward Lattice"),
                    spec(IdentityStat.WEAPON_CYCLE, 1.12, "Shielded Battery"));
            case FLEET_TELEPORTER_TITAN -> matrixBonus(key, role,
                    spec(IdentityStat.WARP_CHARGE, 1.20, "Transit Corridor"),
                    spec(IdentityStat.MOBILITY, 1.16, "Phase Drives"),
                    spec(IdentityStat.SUPPORT_FIELD, 1.16, "Recovery Anchor"),
                    spec(IdentityStat.SENSOR_RANGE, 1.14, "Transit Plotting"));
            case ELITE_SUPERSHIP_COMMAND_TITAN -> matrixBonus(key, role,
                    spec(IdentityStat.WEAPON_DAMAGE, 1.18, "Supership Command Net"),
                    spec(IdentityStat.SUPPORT_FIELD, 1.18, "Kill Chain Bridge"),
                    spec(IdentityStat.SENSOR_RANGE, 1.16, "Aegis Target Choir"),
                    spec(IdentityStat.WARP_CHARGE, 1.16, "Viper Spearhead"));
            case ELITE_REINFORCEMENTS_TITAN -> matrixBonus(key, role,
                    spec(IdentityStat.WEAPON_DAMAGE, 1.16, "Strike Coordination"),
                    spec(IdentityStat.SENSOR_RANGE, 1.16, "Command Targeting"),
                    spec(IdentityStat.SHIELD_REGEN, 1.14, "Honor Guard Screens"),
                    spec(IdentityStat.MOBILITY, 1.14, "Assault Screen Drives"));
            case MOBILE_STATION_TITAN -> matrixBonus(key, role,
                    spec(IdentityStat.SUPPORT_FIELD, 1.30, "Dockyard Spine"),
                    spec(IdentityStat.STRIKE_CRAFT, 1.18, "Reserve Decks"),
                    spec(IdentityStat.SHIELD_REGEN, 1.18, "Service Bastion"),
                    spec(IdentityStat.WARP_CHARGE, 1.12, "Harbor Relocation"));
            case HYPERWEAPON_TITAN -> matrixBonus(key, role,
                    spec(IdentityStat.SUPERWEAPON_RECHARGE, 1.18, "Overcharge Lattice"),
                    spec(IdentityStat.WEAPON_DAMAGE, 1.16, "Termination Spine"),
                    spec(IdentityStat.SENSOR_RANGE, 1.16, "Execution Plot"),
                    spec(IdentityStat.SHIELD_REGEN, 1.14, "Containment Screens"));
            case MOTHERSHIP -> matrixBonus(key, role,
                    spec(IdentityStat.SUPPORT_FIELD, 1.34, "Fleet Command Harbor"),
                    spec(IdentityStat.STRIKE_CRAFT, 1.22, "Grand Flight Control"),
                    spec(IdentityStat.SHIELD_REGEN, 1.20, "Citadel Lattice"),
                    spec(IdentityStat.WEAPON_DAMAGE, 1.14, "Citadel Batteries"));
            case CARRIER -> matrixBonus(key, role,
                    spec(IdentityStat.STRIKE_CRAFT, 1.18, "Sortie Control"),
                    spec(IdentityStat.WEAPON_CYCLE, 1.12, "Aggression Deck"),
                    spec(IdentityStat.SHIELD_REGEN, 1.12, "Shield Hangars"),
                    spec(IdentityStat.MISSILE_CYCLE, 1.14, "Assault Launch Deck"));
            case DRONE_CARRIER -> matrixBonus(key, role,
                    spec(IdentityStat.STRIKE_CRAFT, 1.20, "Swarm Coordination"),
                    spec(IdentityStat.MOBILITY, 1.12, "Advance Carrier Drives"),
                    spec(IdentityStat.SENSOR_RANGE, 1.14, "Drone Guidance Web"),
                    spec(IdentityStat.MISSILE_DAMAGE, 1.14, "Warhead Swarm"));
            case TRANSPORT -> matrixBonus(key, role,
                    spec(IdentityStat.SUPPORT_FIELD, 1.22, "Fleet Logistics"),
                    spec(IdentityStat.MOBILITY, 1.12, "Hot-Zone Courier"),
                    spec(IdentityStat.SHIELD_REGEN, 1.12, "Convoy Screens"),
                    spec(IdentityStat.WARP_CHARGE, 1.14, "Breakout Spool"));
            case MINER -> matrixBonus(key, role,
                    spec(IdentityStat.MINING_YIELD, 1.20, "Ore Processor"),
                    spec(IdentityStat.MOBILITY, 1.12, "Rush Loader"),
                    spec(IdentityStat.SHIELD_REGEN, 1.12, "Protected Rig"),
                    spec(IdentityStat.WARP_CHARGE, 1.14, "Extraction Dash"));
            case HAULER -> matrixBonus(key, role,
                    spec(IdentityStat.WARP_CHARGE, 1.12, "Courier Spool"),
                    spec(IdentityStat.MOBILITY, 1.12, "Fast Transfer Drives"),
                    spec(IdentityStat.SHIELD_REGEN, 1.12, "Shielded Hold"),
                    spec(IdentityStat.SENSOR_RANGE, 1.12, "Route Hunter"));
            case BASE -> matrixBonus(key, role,
                    spec(IdentityStat.SUPPORT_FIELD, 1.28, "Dockyard Grid"),
                    spec(IdentityStat.WEAPON_DAMAGE, 1.14, "Siege Bastion"),
                    spec(IdentityStat.SHIELD_REGEN, 1.16, "Fortress Lattice"),
                    spec(IdentityStat.MISSILE_CYCLE, 1.18, "Barrage Citadel"));
            case STATIC_TURRET -> matrixBonus(key, role,
                    spec(IdentityStat.SENSOR_RANGE, 1.14, "Tracking Relay"),
                    spec(IdentityStat.WEAPON_DAMAGE, 1.14, "Impact Emplacement"),
                    spec(IdentityStat.SHIELD_REGEN, 1.12, "Shielded Hardpoint"),
                    spec(IdentityStat.MISSILE_DAMAGE, 1.14, "Warhead Nest"));
        };
    }

    public static RoleBonus roleBonusFor(ShipRole role) {
        return roleBonusFor(Faction.ALLY, role);
    }

    private static Faction normalizeFaction(Faction faction) {
        if (faction == null) return null;
        return switch (faction) {
            case PLAYER, ALLY -> Faction.ALLY;
            case ENEMY -> Faction.ENEMY;
            case TEAM_C -> Faction.TEAM_C;
            case TEAM_D -> Faction.TEAM_D;
        };
    }

    private static BonusSpec spec(IdentityStat stat, double multiplier, String name) {
        return new BonusSpec(stat, Math.max(1.0, multiplier), name);
    }

    private static RoleBonus matrixBonus(Faction faction, ShipRole role,
                                         BonusSpec blue,
                                         BonusSpec red,
                                         BonusSpec green,
                                         BonusSpec yellow) {
        BonusSpec selected = switch (faction) {
            case ALLY, PLAYER -> blue;
            case ENEMY -> red;
            case TEAM_C -> green;
            case TEAM_D -> yellow;
        };
        return new RoleBonus(
                selected.stat,
                selected.multiplier,
                selected.name,
                describeRoleBonus(faction, role, selected.stat, selected.name)
        );
    }

    private static String describeRoleBonus(Faction faction, ShipRole role, IdentityStat stat, String name) {
        String team = (faction == null) ? "Fleet" : faction.teamName();
        return team + " " + prettyRole(role) + " specialize in "
                + statPhrase(stat) + " through " + name.toLowerCase(Locale.US) + ".";
    }

    private static String prettyRole(ShipRole role) {
        if (role == null) return "ships";
        if (role == ShipRole.ELITE_SUPERSHIP_COMMAND_TITAN) return "Elite Supership Command Titan";
        if (role == ShipRole.ELITE_REINFORCEMENTS_TITAN) return "Elite Reinforcements Titan";
        String raw = role.name().toLowerCase(Locale.US).replace('_', ' ');
        String[] parts = raw.split(" ");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) out.append(part.substring(1));
        }
        return out.toString();
    }

    private static String statPhrase(IdentityStat stat) {
        if (stat == null) return "undefined systems";
        return switch (stat) {
            case WEAPON_DAMAGE -> "weapon damage";
            case WEAPON_CYCLE -> "weapon cycle rate";
            case SENSOR_RANGE -> "sensor range";
            case SHIELD_REGEN -> "shield regeneration";
            case MOBILITY -> "mobility";
            case CIWS_RANGE -> "CIWS coverage";
            case MISSILE_DAMAGE -> "missile damage";
            case MISSILE_CYCLE -> "missile cycle rate";
            case STRIKE_CRAFT -> "strike-craft tempo";
            case SUPPORT_FIELD -> "support-field output";
            case MINING_YIELD -> "mining yield";
            case SUPERWEAPON_RECHARGE -> "superweapon recharge";
            case WARP_CHARGE -> "battlefield warp charge";
            case NONE -> "general performance";
        };
    }
}
