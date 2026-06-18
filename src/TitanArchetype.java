public enum TitanArchetype {
    TRANSPORT(
            "Transport Titan",
            4800,
            TitanAvailability.EARLY,
            10,
            0,
            "Logistics, recovery, cargo survival",
            "+25% salvage, +20% resupply, +1 emergency extraction",
            "escort cruisers",
            "point-defense frigates",
            "line destroyers",
            "repair frigates",
            "sensor ship",
            "interdiction escort"),
    BULWARK(
            "Bulwark Titan",
            5200,
            TitanAvailability.EARLY,
            10,
            0,
            "Frontline defense, Mothership guard",
            "+20% shield durability near the Mothership, +15% point-defense accuracy",
            "heavy line cruisers",
            "shield frigates",
            "point-defense destroyers",
            "missile escorts",
            "support carrier"),
    CARRIER_SUPPORT(
            "Carrier Support Titan",
            5400,
            TitanAvailability.EARLY_MID,
            10,
            0,
            "Small Craft repair and rearm support",
            "-30% Small Craft repair/rearm time, +1 upgrade tier for deployed wings",
            "light carriers",
            "escort frigates",
            "interceptor destroyers",
            "repair frigates",
            "fleet tender",
            "screen cruiser"),
    VANGUARD(
            "Vanguard Titan",
            5600,
            TitanAvailability.EARLY_MID,
            10,
            0,
            "Fast reserve, breach response",
            "+25% deployment speed, faster intercept and rapid-reaction orders",
            "fast cruisers",
            "missile destroyers",
            "interceptor frigates",
            "recon ship",
            "support escort"),
    INTERDICTION(
            "Interdiction Titan",
            6000,
            TitanAvailability.MID,
            10,
            0,
            "Trap, slow, isolate",
            "Suppresses enemy retreat/warp behavior and improves pursuit quality",
            "fast cruisers",
            "electronic-warfare frigates",
            "pursuit destroyers",
            "interception escorts",
            "sensor ship",
            "strike carrier"),
    COMMAND_INTEL(
            "Command / Intel Titan",
            6200,
            TitanAvailability.MID,
            10,
            0,
            "Sensor command, targeting coordination",
            "+30% sensor reach, +20% target-lock quality, +15% long-range firing accuracy",
            "sensor cruisers",
            "command frigates",
            "line destroyers",
            "point-defense escorts",
            "artillery cruiser",
            "signals ship"),
    BOARDING_RECOVERY(
            "Boarding / Recovery Titan",
            6200,
            TitanAvailability.MID,
            10,
            0,
            "Liberation, capture, recovery",
            "+25% disablement efficiency, +20% liberation success against yellow ships",
            "interdiction cruisers",
            "boarding frigates",
            "disablement destroyers",
            "repair escorts",
            "salvage escorts",
            "support carrier"),
    ARTILLERY(
            "Artillery Titan",
            6400,
            TitanAvailability.MID,
            10,
            0,
            "Long-range bombardment",
            "+25% artillery range, +15% long-range firing-solution accuracy",
            "artillery cruisers",
            "sensor frigates",
            "point-defense escorts",
            "line destroyers",
            "shield support ship"),
    SHIELD_BASTION(
            "Shield Bastion Titan",
            6700,
            TitanAvailability.MID_LATE,
            10,
            0,
            "Defensive shield anchoring",
            "Projects layered shield coverage and reduces alpha-strike spike damage",
            "shield frigates",
            "point-defense destroyers",
            "heavy line cruisers",
            "repair escorts",
            "command escort"),
    FLEET_TELEPORTER(
            "Fleet Teleporter Titan",
            7000,
            TitanAvailability.MID_LATE,
            10,
            0,
            "Reposition, redeploy, recover",
            "Redeploys allied deployments and rescues isolated ships behind the line",
            "guard cruisers",
            "fast destroyers",
            "repair frigates",
            "sensor escorts",
            "command escorts",
            "shield frigate",
            "supply ship"),
    ELITE_SUPERSHIP_COMMAND(
            "Elite Supership Command Titan",
            7800,
            TitanAvailability.LATE,
            0,
            5,
            "Elite strike command",
            "Coordinates a 4-5 Supership wing with stronger focus-fire lethality",
            "elite Superships",
            "recon escort",
            "support command escort"),
    ELITE_REINFORCEMENTS(
            "Elite Reinforcements Titan",
            8000,
            TitanAvailability.LATE,
            6,
            0,
            "Shock task-group command",
            "Auto-commissions a battleship-led honor guard with line escorts and boosts attached capital screens",
            "honor-guard battleship",
            "elite battlecruiser",
            "screen frigate",
            "ciws escort",
            "assault reserve"),
    MOBILE_STATION(
            "Mobile Station Titan",
            8200,
            TitanAvailability.LATE,
            10,
            0,
            "Rear-base support, field services",
            "Creates a durable service node for repair, resupply, and local defense",
            "repair frigates",
            "ammo tenders",
            "point-defense destroyers",
            "shield frigates",
            "salvage ship",
            "escort carrier"),
    HYPERWEAPON(
            "Hyperweapon Titan",
            9000,
            TitanAvailability.LATE,
            10,
            0,
            "Strategic finisher",
            "Carries a battle-defining weapon with major payoff against marked targets",
            "shield support ships",
            "point-defense frigates",
            "artillery cruisers",
            "sensor ships",
            "repair escort",
            "interdiction escort");

    private final String displayName;
    private final int costCredits;
    private final TitanAvailability availability;
    private final int standardShipCommandCapacity;
    private final int eliteSupershipCommandCapacity;
    private final String roleLabel;
    private final String commandBonusSummary;
    private final String[] preferredDeploymentRoles;

    TitanArchetype(String displayName,
                   int costCredits,
                   TitanAvailability availability,
                   int standardShipCommandCapacity,
                   int eliteSupershipCommandCapacity,
                   String roleLabel,
                   String commandBonusSummary,
                   String... preferredDeploymentRoles) {
        this.displayName = (displayName == null || displayName.isBlank()) ? name() : displayName;
        this.costCredits = Math.max(0, costCredits);
        this.availability = (availability == null) ? TitanAvailability.EARLY : availability;
        this.standardShipCommandCapacity = Math.max(0, standardShipCommandCapacity);
        this.eliteSupershipCommandCapacity = Math.max(0, eliteSupershipCommandCapacity);
        this.roleLabel = (roleLabel == null || roleLabel.isBlank()) ? "Fleet role" : roleLabel;
        this.commandBonusSummary = (commandBonusSummary == null || commandBonusSummary.isBlank())
                ? "No command bonus."
                : commandBonusSummary;
        this.preferredDeploymentRoles = (preferredDeploymentRoles == null) ? new String[0] : preferredDeploymentRoles.clone();
    }

    public String displayName() {
        return displayName;
    }

    public int costCredits() {
        return costCredits;
    }

    public TitanAvailability availability() {
        return availability;
    }

    public int standardShipCommandCapacity() {
        return standardShipCommandCapacity;
    }

    public int eliteSupershipCommandCapacity() {
        return eliteSupershipCommandCapacity;
    }

    public int totalCommandHullCapacity() {
        return standardShipCommandCapacity + eliteSupershipCommandCapacity;
    }

    public boolean commandsEliteSupershipWing() {
        return eliteSupershipCommandCapacity > 0;
    }

    public String roleLabel() {
        return roleLabel;
    }

    public String commandBonusSummary() {
        return commandBonusSummary;
    }

    public String[] preferredDeploymentRoles() {
        return preferredDeploymentRoles.clone();
    }

    public boolean isAvailableInSector(int sector) {
        return availability.isAvailableInSector(sector);
    }

    public ShipRole shipRole() {
        return switch (this) {
            case TRANSPORT -> ShipRole.TRANSPORT_TITAN;
            case BULWARK -> ShipRole.BULWARK_TITAN;
            case CARRIER_SUPPORT -> ShipRole.CARRIER_SUPPORT_TITAN;
            case VANGUARD -> ShipRole.VANGUARD_TITAN;
            case INTERDICTION -> ShipRole.INTERDICTION_TITAN;
            case COMMAND_INTEL -> ShipRole.COMMAND_INTEL_TITAN;
            case BOARDING_RECOVERY -> ShipRole.BOARDING_RECOVERY_TITAN;
            case ARTILLERY -> ShipRole.ARTILLERY_TITAN;
            case SHIELD_BASTION -> ShipRole.SHIELD_BASTION_TITAN;
            case FLEET_TELEPORTER -> ShipRole.FLEET_TELEPORTER_TITAN;
            case ELITE_SUPERSHIP_COMMAND -> ShipRole.ELITE_SUPERSHIP_COMMAND_TITAN;
            case ELITE_REINFORCEMENTS -> ShipRole.ELITE_REINFORCEMENTS_TITAN;
            case MOBILE_STATION -> ShipRole.MOBILE_STATION_TITAN;
            case HYPERWEAPON -> ShipRole.HYPERWEAPON_TITAN;
        };
    }

    public static TitanArchetype fromShipRole(ShipRole role) {
        if (role == null) return null;
        return switch (role) {
            case TRANSPORT_TITAN -> TRANSPORT;
            case BULWARK_TITAN -> BULWARK;
            case CARRIER_SUPPORT_TITAN -> CARRIER_SUPPORT;
            case VANGUARD_TITAN -> VANGUARD;
            case INTERDICTION_TITAN -> INTERDICTION;
            case COMMAND_INTEL_TITAN -> COMMAND_INTEL;
            case BOARDING_RECOVERY_TITAN -> BOARDING_RECOVERY;
            case ARTILLERY_TITAN -> ARTILLERY;
            case SHIELD_BASTION_TITAN -> SHIELD_BASTION;
            case FLEET_TELEPORTER_TITAN -> FLEET_TELEPORTER;
            case ELITE_SUPERSHIP_COMMAND_TITAN -> ELITE_SUPERSHIP_COMMAND;
            case ELITE_REINFORCEMENTS_TITAN -> ELITE_REINFORCEMENTS;
            case MOBILE_STATION_TITAN -> MOBILE_STATION;
            case HYPERWEAPON_TITAN -> HYPERWEAPON;
            default -> null;
        };
    }

    public static TitanArchetype fromSerializedName(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        try {
            return TitanArchetype.valueOf(trimmed);
        } catch (IllegalArgumentException ignored) {
            for (TitanArchetype archetype : values()) {
                if (archetype.displayName.equalsIgnoreCase(trimmed)) {
                    return archetype;
                }
            }
            return null;
        }
    }

    @Override
    public String toString() {
        return displayName + " [" + availability + ", $" + costCredits + ", cmd " + totalCommandHullCapacity() + "]";
    }
}
