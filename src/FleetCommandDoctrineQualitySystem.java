import java.util.ArrayList;
import java.util.List;

public final class FleetCommandDoctrineQualitySystem {
    private FleetCommandDoctrineQualitySystem() {}

    public static List<String> fleetGroupManagementLines(GameContext ctx) {
        int groups = groupCount(ctx);
        int ships = friendlyShipCount(ctx);
        return List.of(
                "Fleet Groups  |  create, rename, assign  |  groups " + groups + " ships " + ships,
                "Composition  |  drag ship cards between Flag, Screen, Strike, Reserve, and Salvage groups",
                "Assignment  |  selected hull can join flagship, detach group, or reserve rotation"
        );
    }

    public static List<String> savedDoctrineTemplateLines(GameContext ctx) {
        ArrayList<String> out = new ArrayList<>();
        for (StretchGoalsFleetDoctrineSystem.DoctrineTemplate template : StretchGoalsFleetDoctrineSystem.DoctrineTemplate.values()) {
            out.add("Saved Doctrine  |  " + template.name().replace('_', ' ')
                    + "  |  " + templateSummary(template));
        }
        return out;
    }

    public static List<String> doctrineRecommendationLines(GameContext ctx) {
        int carriers = countFriendlyRole(ctx, ShipRole.CARRIER) + countFriendlyRole(ctx, ShipRole.DRONE_CARRIER);
        int transports = countFriendlyRole(ctx, ShipRole.TRANSPORT) + countFriendlyRole(ctx, ShipRole.HAULER) + countFriendlyRole(ctx, ShipRole.TRANSPORT_TITAN);
        int missile = countFriendlyRole(ctx, ShipRole.MISSILE_BOAT) + countFriendlyRole(ctx, ShipRole.BOMBER);
        String recommendation = transports > 0 ? "CONVOY ESCORT"
                : (carriers > 0 ? "FLEET BATTLE"
                : (missile >= 2 ? "RAID" : "FLEET BATTLE"));
        return List.of(
                "Doctrine Recommendation  |  " + recommendation + "  |  carriers " + carriers
                        + " transports " + transports + " strike " + missile,
                "Captain Objection  |  aggressive captains object to HOLD FIRE; rescue captains object to abandon-disabled policies"
        );
    }

    public static List<String> doctrineImpactLines(GameContext ctx) {
        StretchGoalsFleetDoctrineSystem.StandingOrders orders = standingOrders(ctx);
        int ammo = orders != null && orders.conserveAmmunition ? -18 : 12;
        int retreat = orders == null ? 35 : orders.retreatThresholdPercent;
        int repair = orders != null && orders.rescueDisabledAllies ? 16 : -8;
        return List.of(
                "Doctrine Impact  |  ammo " + signed(ammo) + "%  retreat at " + retreat + "%  repair burden " + signed(repair) + "%",
                "Rules Of Engagement  |  conserve ammo " + yes(orders == null || orders.conserveAmmunition)
                        + " protect civilians " + yes(orders == null || orders.protectCivilianTraffic)
                        + " accept surrender " + yes(orders == null || orders.acceptSurrender),
                "Retreat / Rescue Policy  |  rescue disabled allies " + yes(orders == null || orders.rescueDisabledAllies)
                        + " scuttle compromised " + yes(orders != null && orders.scuttleCompromisedShips)
        );
    }

    public static List<String> commandBandwidthForecastLines(GameContext ctx) {
        StretchGoalsFleetDoctrineSystem.FleetCommandState fleet = fleet(ctx);
        int used = fleet == null ? 0 : fleet.bandwidthUsed;
        int cap = fleet == null ? 0 : fleet.bandwidthCapacity;
        int spare = Math.max(0, cap - used);
        return List.of(
                "Command Bandwidth Forecast  |  " + used + "/" + cap + " used  spare " + spare,
                "Command Training  |  upgrade signal drills to reduce order delay, garbling, and bandwidth spikes"
        );
    }

    public static List<String> commandConsequenceLines(GameContext ctx) {
        StretchGoalsFleetDoctrineSystem.FleetCommandState fleet = fleet(ctx);
        int panic = fleet == null ? 0 : fleet.panicPercent;
        int cohesion = fleet == null ? 0 : fleet.cohesionPercent;
        boolean collapsed = fleet != null && fleet.networkCollapsed;
        return List.of(
                "Command Consequence  |  relay loss cuts bandwidth and raises isolation",
                "Command Consequence  |  flagship loss " + (collapsed ? "collapses the net" : "forces fallback promotion"),
                "Command Consequence  |  panic " + panic + "% cohesion " + cohesion + "%",
                "Field Promotion  |  fallback escort can become acting flag after command casualties"
        );
    }

    public static List<String> reserveAndSpecialOrderLines(GameContext ctx) {
        return List.of(
                "Reserve Rotation  |  long battles can cycle damaged hulls into reserve and fresh escorts forward",
                "Special Order  |  screen artillery",
                "Special Order  |  escort carrier",
                "Special Order  |  guard salvage ship",
                "Special Order  |  cover retreat corridor"
        );
    }

    public static List<String> postBattleDoctrineReviewLines(GameContext ctx) {
        StretchGoalsFleetDoctrineSystem.StandingOrders orders = standingOrders(ctx);
        ArrayList<String> out = new ArrayList<>();
        out.add("Post-Battle Doctrine Review  |  orders worked, failed, or arrived late");
        if (orders != null && !orders.afterActionNotes.isEmpty()) {
            for (String note : orders.afterActionNotes) {
                out.add("Doctrine Note  |  " + note);
            }
        } else {
            out.add("Doctrine Note  |  no after-action notes recorded yet");
        }
        return out;
    }

    private static StretchGoalsFleetDoctrineSystem.FleetCommandState fleet(GameContext ctx) {
        if (ctx == null || ctx.campaign == null || ctx.campaign.fleetDoctrineExpansion == null) return null;
        return ctx.campaign.fleetDoctrineExpansion.fleet;
    }

    private static StretchGoalsFleetDoctrineSystem.StandingOrders standingOrders(GameContext ctx) {
        StretchGoalsFleetDoctrineSystem.FleetCommandState fleet = fleet(ctx);
        return fleet == null ? null : fleet.standingOrders;
    }

    private static int groupCount(GameContext ctx) {
        if (ctx == null || ctx.campaign == null || ctx.campaign.strategicDivisions == null) return 0;
        return ctx.campaign.strategicDivisions.size();
    }

    private static int friendlyShipCount(GameContext ctx) {
        if (ctx == null || ctx.ships == null) return 0;
        int count = 0;
        for (Ship ship : ctx.ships) {
            if (ship != null && ship.faction != Faction.ENEMY) count++;
        }
        return count;
    }

    private static int countFriendlyRole(GameContext ctx, ShipRole role) {
        if (ctx == null || ctx.ships == null || role == null) return 0;
        int count = 0;
        for (Ship ship : ctx.ships) {
            if (ship != null && ship.faction != Faction.ENEMY && ship.role == role) count++;
        }
        return count;
    }

    private static String templateSummary(StretchGoalsFleetDoctrineSystem.DoctrineTemplate template) {
        return switch (template) {
            case CONVOY_ESCORT -> "protect civilians, high retreat threshold, rescue enabled";
            case FLEET_BATTLE -> "balanced ammo, line cohesion, rescue enabled";
            case RAID -> "fast strike, lower rescue burden, higher ammo spend";
            case RESCUE -> "protect disabled ships and accept surrender";
            case BLOCKADE -> "hold routes, accept surrender, preserve capture value";
        };
    }

    private static String signed(int value) {
        return value >= 0 ? "+" + value : String.valueOf(value);
    }

    private static String yes(boolean value) {
        return value ? "yes" : "no";
    }
}
