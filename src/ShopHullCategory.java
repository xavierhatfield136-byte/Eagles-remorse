public enum ShopHullCategory {
    ESCORT("ESCORT", "Patrol hulls, escorts, and light utility craft"),
    LINE("LINE", "Frigates, cruisers, and logistics line hulls"),
    CAPITAL("CAPITAL", "Heavy capitals, carriers, and super-capitals"),
    TITAN("TITAN", "Titan command hulls and the Mothership");

    private final String label;
    private final String subtitle;

    ShopHullCategory(String label, String subtitle) {
        this.label = (label == null || label.isBlank()) ? name() : label;
        this.subtitle = (subtitle == null || subtitle.isBlank()) ? "Hull group" : subtitle;
    }

    public String label() {
        return label;
    }

    public String subtitle() {
        return subtitle;
    }

    public static ShopHullCategory forRole(ShipRole role) {
        if (role == null) return ESCORT;
        return switch (role) {
            case PATROL,
                    PICKET,
                    FRIGATE,
                    ARTILLERY_SHIP,
                    MISSILE_BOAT,
                    CIWS_CORVETTE,
                    MINER -> ESCORT;
            case LIGHT_CRUISER,
                    MEDIUM_CRUISER,
                    CRUISER,
                    BATTLECRUISER,
                    BATTLESHIP,
                    STEALTH_SHIP,
                    TRANSPORT,
                    HAULER -> LINE;
            case DREADNOUGHT,
                    CARRIER,
                    DRONE_CARRIER,
                    SUPERSHIP -> CAPITAL;
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
                    HYPERWEAPON_TITAN,
                    MOTHERSHIP -> TITAN;
            default -> ESCORT;
        };
    }
}
