import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class FleetClassifier {
    private FleetClassifier() {}

    public static final class FleetProfile {
        public final FleetLevel level;
        public final int shipCount;
        public final int escortCount;
        public final int lineCount;
        public final int capitalCount;
        public final int titanCount;
        public final int civilianCount;
        public final double combatStrength;

        FleetProfile(FleetLevel level,
                     int shipCount,
                     int escortCount,
                     int lineCount,
                     int capitalCount,
                     int titanCount,
                     int civilianCount,
                     double combatStrength) {
            this.level = level == null ? FleetLevel.LEVEL_1_LIGHT : level;
            this.shipCount = Math.max(0, shipCount);
            this.escortCount = Math.max(0, escortCount);
            this.lineCount = Math.max(0, lineCount);
            this.capitalCount = Math.max(0, capitalCount);
            this.titanCount = Math.max(0, titanCount);
            this.civilianCount = Math.max(0, civilianCount);
            this.combatStrength = Math.max(0.0, combatStrength);
        }

        public String intelligenceLabel() {
            return level.label();
        }

        public String compactReadout() {
            return level.shortLabel() + "  |  ships " + shipCount
                    + "  line " + lineCount
                    + "  capitals " + capitalCount
                    + "  titans " + titanCount;
        }
    }

    public static FleetProfile classifyShips(Collection<? extends Ship> ships) {
        ArrayList<ShipRole> roles = new ArrayList<>();
        if (ships != null) {
            for (Ship ship : ships) {
                if (ship == null || !ship.alive || ship.dying || ship.hp <= 0 || ship.role == null) continue;
                roles.add(ship.role);
            }
        }
        return classifyRoles(roles);
    }

    public static FleetProfile classifyRoles(Collection<ShipRole> roles) {
        int ships = 0;
        int escorts = 0;
        int line = 0;
        int capitals = 0;
        int titans = 0;
        int civilians = 0;
        double strength = 0.0;

        if (roles != null) {
            for (ShipRole role : roles) {
                if (role == null) continue;
                ships++;
                ShopHullCategory category = ShopHullCategory.forRole(role);
                if (role.isTitanOrMothership()) {
                    titans++;
                } else if (category == ShopHullCategory.CAPITAL) {
                    capitals++;
                } else if (category == ShopHullCategory.LINE) {
                    line++;
                } else {
                    escorts++;
                }
                if (isCivilianDefault(role)) civilians++;
                strength += roleCombatStrength(role);
            }
        }

        FleetLevel level = classifyLevel(ships, line, capitals, titans);
        return new FleetProfile(level, ships, escorts, line, capitals, titans, civilians, strength);
    }

    public static FleetProfile classifyRoles(ShipRole... roles) {
        if (roles == null) return classifyRoles(List.of());
        return classifyRoles(List.of(roles));
    }

    public static double roleCombatStrength(ShipRole role) {
        if (role == null) return 0.0;
        RoleStats.Stats stats = RoleStats.get(role);
        double base = stats.bountyValue;
        if (role.isTitanOrMothership()) base += 1800.0;
        else if (ShopHullCategory.forRole(role) == ShopHullCategory.CAPITAL) base += 650.0;
        else if (ShopHullCategory.forRole(role) == ShopHullCategory.LINE) base += 140.0;
        if (isCivilianDefault(role)) base *= 0.45;
        return Math.max(1.0, base);
    }

    public static boolean isCivilianDefault(ShipRole role) {
        return role == ShipRole.MINER
                || role == ShipRole.HAULER
                || role == ShipRole.TRANSPORT;
    }

    public static boolean isMilitaryJoinableByDefault(ShipRole role) {
        return role != null
                && !isCivilianDefault(role)
                && role != ShipRole.BASE
                && role != ShipRole.STATIC_TURRET
                && role != ShipRole.FIGHTER
                && role != ShipRole.BOMBER
                && role != ShipRole.DRONE
                && role != ShipRole.PD_CRAFT;
    }

    private static FleetLevel classifyLevel(int ships, int line, int capitals, int titans) {
        if (ships <= 0) return FleetLevel.LEVEL_1_LIGHT;

        boolean manyCapitals = capitals >= 6 || capitals >= 4 && ships >= 32;
        boolean severalTitans = titans >= 3;
        if (ships >= 40 && manyCapitals && severalTitans) {
            return FleetLevel.LEVEL_5_GRAND_FLEET;
        }

        if (titans >= 1 && ships >= 30) return FleetLevel.LEVEL_4_TITAN_TASK_FORCE;
        if (titans >= 2) return FleetLevel.LEVEL_4_TITAN_TASK_FORCE;
        if (titans >= 1 && capitals >= 2) return FleetLevel.LEVEL_4_TITAN_TASK_FORCE;

        if (titans >= 1) return FleetLevel.LEVEL_3_LARGE_CAPITAL;
        if (capitals >= 1) return FleetLevel.LEVEL_3_LARGE_CAPITAL;
        if (ships >= 21) return FleetLevel.LEVEL_3_LARGE_CAPITAL;

        if (ships <= 5 && line <= 1) return FleetLevel.LEVEL_1_LIGHT;
        return FleetLevel.LEVEL_2_MEDIUM;
    }
}
