public enum ShipRole {
    // Scout / escort line
    PICKET,
    PATROL,
    STEALTH_SHIP,

    // Small craft
    FIGHTER,
    BOMBER,
    PD_CRAFT,
    DRONE,

    // Medium ships
    FRIGATE,
    ARTILLERY_SHIP,
    MISSILE_BOAT,
    CIWS_CORVETTE,
    LIGHT_CRUISER,
    MEDIUM_CRUISER,
    CRUISER,
    BATTLECRUISER,

    // Large ships
    BATTLESHIP,
    DREADNOUGHT,
    SUPERSHIP,
    TRANSPORT_TITAN,
    BULWARK_TITAN,
    CARRIER_SUPPORT_TITAN,
    VANGUARD_TITAN,
    INTERDICTION_TITAN,
    COMMAND_INTEL_TITAN,
    BOARDING_RECOVERY_TITAN,
    ARTILLERY_TITAN,
    SHIELD_BASTION_TITAN,
    FLEET_TELEPORTER_TITAN,
    ELITE_SUPERSHIP_COMMAND_TITAN,
    ELITE_REINFORCEMENTS_TITAN,
    MOBILE_STATION_TITAN,
    HYPERWEAPON_TITAN,
    MOTHERSHIP,
    CARRIER,
    DRONE_CARRIER,
    TRANSPORT,

    // Economy / logistics
    MINER,
    HAULER,

    // Structures
    BASE,
    STATIC_TURRET;

    public boolean isTitan() {
        return switch (this) {
            case TRANSPORT_TITAN,
                 BULWARK_TITAN,
                 CARRIER_SUPPORT_TITAN,
                 VANGUARD_TITAN,
                 INTERDICTION_TITAN,
                 COMMAND_INTEL_TITAN,
                 BOARDING_RECOVERY_TITAN,
                 ARTILLERY_TITAN,
                 SHIELD_BASTION_TITAN,
                 FLEET_TELEPORTER_TITAN,
                 ELITE_SUPERSHIP_COMMAND_TITAN,
                 ELITE_REINFORCEMENTS_TITAN,
                 MOBILE_STATION_TITAN,
                 HYPERWEAPON_TITAN -> true;
            default -> false;
        };
    }

    public boolean isMothership() {
        return this == MOTHERSHIP;
    }

    public boolean isTitanOrMothership() {
        return isTitan() || isMothership();
    }

    public boolean isCapitalCombatant() {
        return switch (this) {
            case LIGHT_CRUISER,
                 MEDIUM_CRUISER,
                 CRUISER,
                 BATTLECRUISER,
                 BATTLESHIP,
                 DREADNOUGHT,
                 SUPERSHIP,
                 TRANSPORT_TITAN,
                 BULWARK_TITAN,
                 CARRIER_SUPPORT_TITAN,
                 VANGUARD_TITAN,
                 INTERDICTION_TITAN,
                 COMMAND_INTEL_TITAN,
                 BOARDING_RECOVERY_TITAN,
                 ARTILLERY_TITAN,
                 SHIELD_BASTION_TITAN,
                 FLEET_TELEPORTER_TITAN,
                 ELITE_SUPERSHIP_COMMAND_TITAN,
                 ELITE_REINFORCEMENTS_TITAN,
                 MOBILE_STATION_TITAN,
                 HYPERWEAPON_TITAN,
                 MOTHERSHIP -> true;
            default -> false;
        };
    }

    public boolean isCarrierHull() {
        return switch (this) {
            case CARRIER,
                 DRONE_CARRIER,
                 CARRIER_SUPPORT_TITAN,
                 MOBILE_STATION_TITAN,
                 MOTHERSHIP -> true;
            default -> false;
        };
    }

    public boolean isSupportHull() {
        return switch (this) {
            case MINER,
                 HAULER,
                 TRANSPORT,
                 CARRIER,
                 DRONE_CARRIER,
                 TRANSPORT_TITAN,
                 CARRIER_SUPPORT_TITAN,
                 COMMAND_INTEL_TITAN,
                 BOARDING_RECOVERY_TITAN,
                 SHIELD_BASTION_TITAN,
                 MOBILE_STATION_TITAN,
                 MOTHERSHIP -> true;
            default -> false;
        };
    }

    public boolean isHeavyMissileThreat() {
        return switch (this) {
            case MISSILE_BOAT,
                 BOMBER,
                 STEALTH_SHIP,
                 CRUISER,
                 BATTLECRUISER,
                 BATTLESHIP,
                 DREADNOUGHT,
                 SUPERSHIP,
                 INTERDICTION_TITAN,
                 BOARDING_RECOVERY_TITAN,
                 ARTILLERY_TITAN,
                 FLEET_TELEPORTER_TITAN,
                 HYPERWEAPON_TITAN,
                 MOTHERSHIP -> true;
            default -> false;
        };
    }
}
