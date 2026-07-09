import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Player-facing campaign economy authority and audit readouts. */
public final class CampaignEconomySystem {
    private static final Pattern ROUTE_RESOURCE_PATTERN =
            Pattern.compile("Fuel (\\d+)\\s+Supplies (\\d+)\\s+Ammo (\\d+)");

    private CampaignEconomySystem() {}

    public static List<String> authoritativeLedgerLines(GameContext ctx) {
        CampaignSystem.CampaignState st = ctx == null ? null : ctx.campaign;
        if (ctx == null || st == null) return List.of("AUTHORITATIVE ECONOMY LEDGER  |  unavailable");
        ArrayList<String> out = new ArrayList<>();
        int[] routeUse = selectedRouteForecastResources(ctx);
        out.add("AUTHORITATIVE ECONOMY LEDGER  |  live campaign stores; no hidden parallel player resource pool");
        out.add(economyResourceLine("Credits", Math.max(0, ctx.credits), -1, expectedCreditUse(ctx),
                "contracts, ore sales, salvage sale, hub rewards"));
        out.add(economyResourceLine("Fleet Ore", CampaignSystem.currentCampaignOre(ctx),
                campaignFleetOreCapacity(ctx), expectedFleetOreUse(ctx), "mining, recovered cargo, salvage awards, trade purchases"));
        out.add(economyResourceLine("Yard Ore", knownYardOre(ctx), knownYardOreCapacity(ctx),
                expectedYardOreUse(ctx), "NPC miners and ore haulers"));
        out.add(economyResourceLine("Fuel", CampaignSystem.campaignFuel(ctx), campaignFuelCapacity(st),
                Math.max(routeUse[0], 6), "fuel hubs, trade, yellow leverage, contracts"));
        out.add(economyResourceLine("Supplies", CampaignSystem.campaignSupplies(ctx), campaignSupplyCapacity(st),
                Math.max(routeUse[1], 5), "supply hubs, trade, green support, contracts"));
        out.add(economyResourceLine("Ammo", CampaignSystem.campaignAmmo(ctx), campaignAmmoCapacity(st),
                Math.max(routeUse[2], 8), "supply hubs, strike rearm, salvage contracts"));
        out.add(economyResourceLine("Repair Materials", repairMaterialStock(ctx),
                repairMaterialCapacity(ctx), expectedRepairMaterialUse(ctx),
                "salvage, repair hubs, transport tenders"));
        out.add("Ledger Rule  |  travel, repair, refit, strategic strikes, and commissions spend the displayed stores");
        out.add("Recovery Choices  |  buy supplies/fuel, sell salvage, mine ore, salvage wrecks, divert to hub, use Logistics Conservation, accept partial repair, or retreat");
        out.add("Shortage Rule  |  Standard permits shortages but points to recovery; Iron multiplies attrition and recovery pressure");
        return out;
    }

    private static String economyResourceLine(String label, int current, int capacity, int expectedUse, String replenishment) {
        String cap = capacity <= 0 ? "uncapped" : String.valueOf(Math.max(0, capacity));
        return label + ": " + Math.max(0, current) + "/" + cap
                + "  |  expected use " + Math.max(0, expectedUse)
                + "  |  replenishment " + replenishment;
    }

    private static int expectedCreditUse(GameContext ctx) {
        CampaignSystem.CampaignLocation selected = selectedLocation(ctx);
        CampaignSystem.HubService service = selectedHubService(ctx);
        if (selected != null && service != null && isDockedAt(ctx, selected)) {
            return switch (service) {
                case REPAIR -> 130;
                case REFIT -> 110;
                case SHIPYARD -> 260;
                case SUPPLY -> 90;
                case STRIKE_REARM -> 420;
                case INTEL, FUEL -> 70;
                case TRADE, CONTRACTS, SALVAGE -> 0;
            };
        }
        return 70;
    }

    private static int expectedFleetOreUse(GameContext ctx) {
        CampaignSystem.CampaignLocation selected = selectedLocation(ctx);
        if (selected != null && selected.services.contains(CampaignSystem.HubService.SHIPYARD)) return 90;
        if (selected != null && selected.services.contains(CampaignSystem.HubService.STRIKE_REARM)) return 96;
        return CampaignSystem.currentCampaignOre(ctx) > 0 ? Math.min(80, CampaignSystem.currentCampaignOre(ctx)) : 0;
    }

    private static int expectedYardOreUse(GameContext ctx) {
        int yardOre = knownYardOre(ctx);
        return yardOre <= 0 ? 0 : Math.min(yardOre, 65);
    }

    private static int expectedRepairMaterialUse(GameContext ctx) {
        CampaignSystem.CampaignState st = ctx == null ? null : ctx.campaign;
        if (st == null) return 0;
        int damaged = 0;
        if (ctx.player != null && ctx.player.hpMax > 0 && ctx.player.hp < ctx.player.hpMax * 0.96) damaged++;
        for (Object entry : st.persistentBlueFleet) {
            if (entry == null || fleetEntryDestroyed(entry)) continue;
            if (fleetEntryDouble(entry, "hullConditionFrac", 1.0) < 0.96
                    || fleetEntryDouble(entry, "shieldConditionFrac", 1.0) < 0.90) {
                damaged++;
            }
        }
        return Math.max(1, damaged);
    }

    private static int[] selectedRouteForecastResources(GameContext ctx) {
        if (ctx == null || ctx.campaign == null) return new int[]{0, 0, 0};
        try {
            for (String line : CampaignSystem.selectedRouteAssessmentLines(ctx)) {
                if (line == null || !line.startsWith("Route Forecast:")) continue;
                Matcher matcher = ROUTE_RESOURCE_PATTERN.matcher(line);
                if (matcher.find()) {
                    return new int[]{
                            Integer.parseInt(matcher.group(1)),
                            Integer.parseInt(matcher.group(2)),
                            Integer.parseInt(matcher.group(3))
                    };
                }
            }
        } catch (RuntimeException ignored) {
            return new int[]{0, 0, 0};
        }
        return new int[]{0, 0, 0};
    }

    private static int campaignFleetOreCapacity(GameContext ctx) {
        CampaignSystem.CampaignState st = ctx == null ? null : ctx.campaign;
        int cap = 900;
        if (ctx != null && ctx.player != null) cap = Math.max(cap, ctx.player.cargoMax);
        if (st != null) cap += Math.max(0, st.persistentBlueFleet.size()) * 40;
        return cap;
    }

    private static int campaignFuelCapacity(CampaignSystem.CampaignState st) {
        return Math.max(160, 120 + Math.max(0, st == null ? 0 : st.persistentBlueFleet.size()) * 8);
    }

    private static int campaignSupplyCapacity(CampaignSystem.CampaignState st) {
        return Math.max(140, 105 + Math.max(0, st == null ? 0 : st.persistentBlueFleet.size()) * 6);
    }

    private static int campaignAmmoCapacity(CampaignSystem.CampaignState st) {
        return Math.max(160, 125 + Math.max(0, st == null ? 0 : st.persistentBlueFleet.size()) * 7);
    }

    private static int repairMaterialStock(GameContext ctx) {
        return CampaignSystem.campaignSalvageStock(ctx) + knownRepairHubStock(ctx);
    }

    private static int repairMaterialCapacity(GameContext ctx) {
        return Math.max(80, 80 + knownRepairHubCount(ctx) * 35);
    }

    private static int knownYardOre(GameContext ctx) {
        int total = 0;
        for (CampaignSystem.CampaignLocation location : CampaignSystem.mainCampaignLocations(ctx)) {
            if (location == null || location.destroyed || !location.discovered) continue;
            if (location.services.contains(CampaignSystem.HubService.SHIPYARD)
                    || location.facilityType == CampaignSystem.CampaignFacilityType.SHIPYARD) {
                total += Math.max(0, location.oreStockpile);
            }
        }
        return total;
    }

    private static int knownYardOreCapacity(GameContext ctx) {
        int cap = 0;
        for (CampaignSystem.CampaignLocation location : CampaignSystem.mainCampaignLocations(ctx)) {
            if (location == null || location.destroyed || !location.discovered) continue;
            if (location.services.contains(CampaignSystem.HubService.SHIPYARD)
                    || location.facilityType == CampaignSystem.CampaignFacilityType.SHIPYARD) {
                cap += Math.max(180, location.strategicValue * 140);
            }
        }
        return Math.max(180, cap);
    }

    private static int knownRepairHubStock(GameContext ctx) {
        int total = 0;
        for (CampaignSystem.CampaignLocation location : CampaignSystem.mainCampaignLocations(ctx)) {
            if (location == null || location.destroyed || !location.discovered) continue;
            if (location.services.contains(CampaignSystem.HubService.REPAIR)
                    || location.services.contains(CampaignSystem.HubService.REFIT)) {
                total += Math.max(0, location.repairSupplyStockpile);
            }
        }
        return total;
    }

    private static int knownRepairHubCount(GameContext ctx) {
        int count = 0;
        for (CampaignSystem.CampaignLocation location : CampaignSystem.mainCampaignLocations(ctx)) {
            if (location == null || location.destroyed || !location.discovered) continue;
            if (location.services.contains(CampaignSystem.HubService.REPAIR)
                    || location.services.contains(CampaignSystem.HubService.REFIT)) {
                count++;
            }
        }
        return count;
    }

    private static CampaignSystem.CampaignLocation selectedLocation(GameContext ctx) {
        CampaignSystem.CampaignState st = ctx == null ? null : ctx.campaign;
        if (st == null || st.selectedGalaxyLocationId == null || st.selectedGalaxyLocationId.isBlank()) return null;
        for (CampaignSystem.CampaignLocation location : CampaignSystem.mainCampaignLocations(ctx)) {
            if (location != null && st.selectedGalaxyLocationId.equals(location.id)) return location;
        }
        return null;
    }

    private static CampaignSystem.HubService selectedHubService(GameContext ctx) {
        if (ctx == null || ctx.ui == null || ctx.ui.campaignHubMenu == null || !ctx.ui.campaignHubMenu.active) return null;
        try {
            return CampaignSystem.HubService.valueOf(ctx.ui.campaignHubMenu.serviceId.toUpperCase(Locale.US));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean isDockedAt(GameContext ctx, CampaignSystem.CampaignLocation location) {
        CampaignSystem.CampaignState st = ctx == null ? null : ctx.campaign;
        return st != null && location != null && location.id != null && location.id.equals(st.dockedGalaxyLocationId);
    }

    private static boolean fleetEntryDestroyed(Object entry) {
        try {
            var field = entry.getClass().getDeclaredField("destroyed");
            field.setAccessible(true);
            return field.getBoolean(entry);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static double fleetEntryDouble(Object entry, String fieldName, double fallback) {
        try {
            var field = entry.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getDouble(entry);
        } catch (ReflectiveOperationException ignored) {
            return fallback;
        }
    }
}
