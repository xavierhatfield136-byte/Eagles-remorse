import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Section 6 campaign economy layered over tactical mining, salvage, and hub services. */
public final class EconomyLogisticsIndustrySystem {
    public enum Resource { FUEL, AMMUNITION, REPAIR_MATERIALS, PROVISIONS, SPECIALIST_COMPONENTS }
    public enum InstallationQuality { OUTPOST, STANDARD, INDUSTRIAL, FLEET_YARD }
    public enum Formation { TIGHT, CRUISE, DISPERSED }
    public enum RouteType { PATROL_LANE, JUMP_POINT, HIDDEN_ROUTE, BLOCKADED }
    public enum OreGrade { LOW, STANDARD, HIGH, STRATEGIC, VOLATILE }
    public enum SalvageMethod { QUICK_SALVAGE, CAREFUL_RECOVERY, TOW }
    public enum ContractType { ESCORT, RESCUE, BOUNTY, SURVEY, SALVAGE, SMUGGLING }

    public static final class LogisticsLedger {
        public final EnumMap<Resource, Integer> stores = new EnumMap<>(Resource.class);
        public final EnumMap<Resource, Integer> cargoAllocation = new EnumMap<>(Resource.class);
        public int cargoCapacity = 500;
        public boolean emergencyRationing;
        public int maintenanceDebt;
        public int sparePartsShortage;
        public int crewFatigue;
        public int readinessPercent = 88;
        public boolean isolatedBaseConvoyDependency;
        public int blockadeStarvation;
        public int blackMarketProcurement;
        public int salvageProcessingHours;
    }

    public static final class MiningClaim {
        public final String id;
        public final OreGrade grade;
        public final String rareMaterial;
        public final String owner;
        public boolean factionPermission;
        public boolean surveyed;
        public boolean volatileDeposit;
        public int miningDrones;
        public boolean specializedMiningFleet;
        public int refineryThroughput;
        public boolean contestedContract;

        MiningClaim(String id, OreGrade grade, String rareMaterial, String owner) {
            this.id = id;
            this.grade = grade;
            this.rareMaterial = rareMaterial;
            this.owner = owner;
        }
    }

    public static final class WreckRecovery {
        public final String id;
        public SalvageMethod method = SalvageMethod.CAREFUL_RECOVERY;
        public boolean blackBoxRecovered;
        public int survivorsRecovered;
        public boolean hazardous;
        public boolean illegalSalvage;
        public int reputationDelta;
        public int processingHours;

        WreckRecovery(String id) {
            this.id = id;
        }
    }

    public static final class RegionalMarket {
        public final String id;
        public final EnumMap<Resource, Integer> prices = new EnumMap<>(Resource.class);
        public final List<String> factionInventory = new ArrayList<>();
        public final List<String> shortages = new ArrayList<>();
        public int supplyShockPercent;
        public int tradeRouteInvestment;
        public boolean hullInsuranceAvailable;

        RegionalMarket(String id) {
            this.id = id;
        }
    }

    public static final class Contract {
        public final String id;
        public final ContractType type;
        public String terms;
        public int deadlineHours;
        public int collateral;
        public int reputationStake;
        public String chainId;
        public int competingBidders;
        public boolean negotiated;

        Contract(String id, ContractType type, String terms) {
            this.id = id;
            this.type = type;
            this.terms = terms;
        }
    }

    public static final class State {
        public final LogisticsLedger logistics = new LogisticsLedger();
        public final List<MiningClaim> miningClaims = new ArrayList<>();
        public final List<WreckRecovery> wreckRecoveries = new ArrayList<>();
        public final Map<String, RegionalMarket> markets = new LinkedHashMap<>();
        public final List<Contract> contractBoard = new ArrayList<>();
        public int ammunitionForecastHours = 18;
        public String readinessCurve = "Rested 88 -> repaired 96 -> fleet-yard 100";
        public double campaignHours;
        public int aiDeploymentReserve = 360;
        public int aiDeploymentsPaid;
    }

    private EconomyLogisticsIndustrySystem() {}

    public static State bootstrap(long seed) {
        State state = new State();
        putStores(state.logistics, 140, 125, 95, 110, 28);
        state.logistics.cargoAllocation.put(Resource.FUEL, 150);
        state.logistics.cargoAllocation.put(Resource.AMMUNITION, 125);
        state.logistics.cargoAllocation.put(Resource.REPAIR_MATERIALS, 100);
        state.logistics.cargoAllocation.put(Resource.PROVISIONS, 90);
        state.logistics.cargoAllocation.put(Resource.SPECIALIST_COMPONENTS, 35);

        MiningClaim frontier = claim("frontier-belt", OreGrade.HIGH, "titanium lattice", "Contested", true, true, 8, true, 32, true);
        MiningClaim echo = claim("echo-vein", OreGrade.VOLATILE, "quantum catalyst", "Unclaimed", false, true, 3, false, 12, false);
        state.miningClaims.add(frontier);
        state.miningClaims.add(echo);

        RegionalMarket shelter = market("southern-shelter", 8, 9, 11, 7, 21);
        shelter.factionInventory.addAll(List.of("Green relief stores", "Yellow cargo pods", "Blue repair tenders"));
        shelter.shortages.add("specialist components");
        shelter.hullInsuranceAvailable = true;
        state.markets.put(shelter.id, shelter);
        RegionalMarket lunar = market("lunar-blockade", 18, 16, 22, 14, 34);
        lunar.factionInventory.addAll(List.of("Red ration crates", "broker salvage lots"));
        lunar.shortages.addAll(List.of("fuel", "repair materials"));
        lunar.supplyShockPercent = 35;
        state.markets.put(lunar.id, lunar);

        for (ContractType type : ContractType.values()) {
            Contract contract = new Contract("contract-" + type.name().toLowerCase(Locale.US), type,
                    type.name().toLowerCase(Locale.US) + " terms open for negotiation");
            contract.deadlineHours = 24 + type.ordinal() * 4;
            contract.collateral = 100 + type.ordinal() * 25;
            contract.reputationStake = 4 + type.ordinal();
            contract.chainId = (type.ordinal() < 3) ? "shelter-relief" : "frontier-opportunity";
            contract.competingBidders = 1 + (type.ordinal() % 3);
            state.contractBoard.add(contract);
        }
        return state;
    }

    private static void putStores(LogisticsLedger ledger, int fuel, int ammo, int repair, int provisions, int components) {
        ledger.stores.put(Resource.FUEL, fuel);
        ledger.stores.put(Resource.AMMUNITION, ammo);
        ledger.stores.put(Resource.REPAIR_MATERIALS, repair);
        ledger.stores.put(Resource.PROVISIONS, provisions);
        ledger.stores.put(Resource.SPECIALIST_COMPONENTS, components);
    }

    private static MiningClaim claim(String id, OreGrade grade, String rare, String owner, boolean permission,
                                     boolean volatileDeposit, int drones, boolean fleet, int throughput, boolean contested) {
        MiningClaim claim = new MiningClaim(id, grade, rare, owner);
        claim.factionPermission = permission;
        claim.volatileDeposit = volatileDeposit;
        claim.miningDrones = drones;
        claim.specializedMiningFleet = fleet;
        claim.refineryThroughput = throughput;
        claim.contestedContract = contested;
        return claim;
    }

    private static RegionalMarket market(String id, int fuel, int ammo, int repair, int provisions, int components) {
        RegionalMarket market = new RegionalMarket(id);
        market.prices.put(Resource.FUEL, fuel);
        market.prices.put(Resource.AMMUNITION, ammo);
        market.prices.put(Resource.REPAIR_MATERIALS, repair);
        market.prices.put(Resource.PROVISIONS, provisions);
        market.prices.put(Resource.SPECIALIST_COMPONENTS, components);
        return market;
    }

    public static int routeFuelCost(State state, Formation formation, int speedPercent, RouteType routeType) {
        int base = 8 + Math.max(0, speedPercent - 60) / 10;
        if (formation == Formation.TIGHT) base += 2;
        if (formation == Formation.DISPERSED) base += 1;
        if (routeType == RouteType.JUMP_POINT) base += 3;
        if (routeType == RouteType.HIDDEN_ROUTE) base = Math.max(1, base - 2);
        if (routeType == RouteType.BLOCKADED) base += 6;
        return base;
    }

    public static void consumeRoute(State state, Formation formation, int speedPercent, RouteType routeType) {
        if (state == null) return;
        int fuel = routeFuelCost(state, formation, speedPercent, routeType);
        addStore(state.logistics, Resource.FUEL, -fuel);
        addStore(state.logistics, Resource.PROVISIONS, state.logistics.emergencyRationing ? -1 : -3);
        state.logistics.crewFatigue = clamp(state.logistics.crewFatigue + ((speedPercent > 80) ? 7 : 3), 0, 100);
        state.logistics.readinessPercent = clamp(state.logistics.readinessPercent - 2, 0, 100);
        if (routeType == RouteType.BLOCKADED) state.logistics.blockadeStarvation += 4;
    }

    public static void resupply(State state, InstallationQuality quality) {
        if (state == null || quality == null) return;
        int rate = 8 + quality.ordinal() * 7;
        for (Resource resource : Resource.values()) addStore(state.logistics, resource, rate);
        state.logistics.maintenanceDebt = Math.max(0, state.logistics.maintenanceDebt - rate);
        state.logistics.sparePartsShortage = Math.max(0, state.logistics.sparePartsShortage - rate / 2);
        state.logistics.crewFatigue = Math.max(0, state.logistics.crewFatigue - rate);
        state.logistics.readinessPercent = clamp(state.logistics.readinessPercent + rate / 2, 0, 100);
    }

    public static void reflectLiveStores(State state, int fuel, int ammunition, int repairMaterials,
                                         int provisions, int specialistComponents) {
        if (state == null) return;
        putStores(state.logistics, Math.max(0, fuel), Math.max(0, ammunition), Math.max(0, repairMaterials),
                Math.max(0, provisions), Math.max(0, specialistComponents));
    }

    public static void recordLiveTravelAttrition(State state, int fuelCost, int supplyCost, int ammunitionCost,
                                                  boolean highPressure) {
        if (state == null) return;
        LogisticsLedger ledger = state.logistics;
        int burden = Math.max(0, fuelCost) + Math.max(0, supplyCost) + Math.max(0, ammunitionCost);
        if (burden <= 0) return;
        ledger.crewFatigue = clamp(ledger.crewFatigue + (highPressure ? 3 : 1), 0, 100);
        ledger.readinessPercent = clamp(ledger.readinessPercent - (highPressure ? 2 : 1), 0, 100);
        if (highPressure) ledger.maintenanceDebt += 1;
    }

    public static void recordLiveDockingRecovery(State state, InstallationQuality quality) {
        if (state == null || quality == null) return;
        LogisticsLedger ledger = state.logistics;
        int recovery = 4 + quality.ordinal() * 3;
        ledger.crewFatigue = Math.max(0, ledger.crewFatigue - recovery);
        ledger.readinessPercent = clamp(ledger.readinessPercent + Math.max(1, recovery / 2), 0, 100);
        ledger.maintenanceDebt = Math.max(0, ledger.maintenanceDebt - Math.max(1, recovery / 3));
    }

    public static WreckRecovery recoverWreck(State state, String id, SalvageMethod method, boolean illegal) {
        WreckRecovery wreck = new WreckRecovery(id);
        wreck.method = method;
        wreck.blackBoxRecovered = method != SalvageMethod.QUICK_SALVAGE;
        wreck.survivorsRecovered = (method == SalvageMethod.CAREFUL_RECOVERY) ? 7 : 2;
        wreck.hazardous = true;
        wreck.illegalSalvage = illegal;
        wreck.reputationDelta = illegal ? -8 : 3;
        wreck.processingHours = switch (method) {
            case QUICK_SALVAGE -> 3;
            case CAREFUL_RECOVERY -> 12;
            case TOW -> 24;
        };
        if (state != null) {
            state.wreckRecoveries.add(wreck);
            state.logistics.salvageProcessingHours += wreck.processingHours;
        }
        return wreck;
    }

    public static void applySupplyShock(State state, String marketId, int percent) {
        RegionalMarket market = (state == null) ? null : state.markets.get(marketId);
        if (market == null) return;
        market.supplyShockPercent = clamp(percent, 0, 100);
        market.prices.replaceAll((resource, price) -> Math.max(1, price + price * market.supplyShockPercent / 100));
    }

    public static void advanceCampaignTime(State state, double elapsedSeconds, int regionalPressure) {
        if (state == null || elapsedSeconds <= 0.0) return;
        state.campaignHours += elapsedSeconds / 3600.0;
        if (state.campaignHours < 1.0) return;
        int hours = (int) Math.floor(state.campaignHours);
        state.campaignHours -= hours;
        int pressure = clamp(regionalPressure, 0, 100);
        for (RegionalMarket market : state.markets.values()) {
            int recovery = Math.max(1, 5 - pressure / 25);
            market.supplyShockPercent = clamp(market.supplyShockPercent - recovery * hours, 0, 100);
            if (market.supplyShockPercent >= 25 && !market.shortages.contains("route disruption")) {
                market.shortages.add("route disruption");
            } else if (market.supplyShockPercent < 18) {
                market.shortages.remove("route disruption");
            }
        }
        for (Contract contract : state.contractBoard) {
            contract.deadlineHours = Math.max(0, contract.deadlineHours - hours);
        }
        state.aiDeploymentReserve = clamp(state.aiDeploymentReserve + Math.max(1, 4 - pressure / 30) * hours, 0, 360);
    }

    public static boolean payForAiDeployment(State state, int hullCount) {
        if (state == null) return true;
        int cost = Math.max(6, 6 + Math.max(1, hullCount) * 2);
        if (state.aiDeploymentReserve < cost) return false;
        state.aiDeploymentReserve -= cost;
        state.aiDeploymentsPaid++;
        return true;
    }

    public static List<String> shortageWarningLines(State state) {
        if (state == null) return List.of();
        ArrayList<String> out = new ArrayList<>();
        for (RegionalMarket market : state.markets.values()) {
            if (market.shortages.isEmpty() && market.supplyShockPercent < 20) continue;
            out.add("SHORTAGE WARNING  |  " + market.id + "  |  shock " + market.supplyShockPercent
                    + "%  |  Recover through resupply, salvage sale, or a safer route");
        }
        if (state.aiDeploymentReserve < 48) {
            out.add("HOSTILE LOGISTICS THINNING  |  Reinforcement reserve " + state.aiDeploymentReserve);
        }
        return out;
    }

    public static boolean surveyClaim(State state, String id) {
        if (state == null) return false;
        for (MiningClaim claim : state.miningClaims) {
            if (!claim.id.equals(id)) continue;
            claim.surveyed = true;
            return true;
        }
        return false;
    }

    public static boolean negotiateContract(State state, String id, String terms, int deadlineHours) {
        if (state == null || id == null || terms == null || terms.isBlank()) return false;
        for (Contract contract : state.contractBoard) {
            if (!contract.id.equals(id)) continue;
            contract.terms = terms.trim();
            contract.deadlineHours = Math.max(1, deadlineHours);
            contract.negotiated = true;
            return true;
        }
        return false;
    }

    public static List<String> commandBoardLines(State state) {
        if (state == null) return List.of("Economy data unavailable.");
        LogisticsLedger ledger = state.logistics;
        return List.of(
                "Stores Fuel " + store(ledger, Resource.FUEL) + "  |  Ammo " + store(ledger, Resource.AMMUNITION)
                        + "  |  Repair " + store(ledger, Resource.REPAIR_MATERIALS),
                "Readiness " + ledger.readinessPercent + "%  |  Fatigue " + ledger.crewFatigue
                        + "  |  Maintenance debt " + ledger.maintenanceDebt,
                "Markets " + state.markets.size() + "  |  Claims " + state.miningClaims.size()
                        + "  |  Contracts " + state.contractBoard.size(),
                "AI reserve " + state.aiDeploymentReserve + "  |  Paid deployments " + state.aiDeploymentsPaid
        );
    }

    public static String serialize(State state) {
        if (state == null) return "";
        LogisticsLedger ledger = state.logistics;
        return store(ledger, Resource.FUEL) + "," + store(ledger, Resource.AMMUNITION) + ","
                + store(ledger, Resource.REPAIR_MATERIALS) + "," + store(ledger, Resource.PROVISIONS) + ","
                + store(ledger, Resource.SPECIALIST_COMPONENTS) + "," + ledger.cargoCapacity + ","
                + ledger.emergencyRationing + "," + ledger.maintenanceDebt + "," + ledger.sparePartsShortage + ","
                + ledger.crewFatigue + "," + ledger.readinessPercent + "," + ledger.isolatedBaseConvoyDependency + ","
                + ledger.blockadeStarvation + "," + ledger.blackMarketProcurement + "," + ledger.salvageProcessingHours
                + "," + state.campaignHours + "," + state.aiDeploymentReserve + "," + state.aiDeploymentsPaid;
    }

    public static State restore(String raw, long seed) {
        State state = bootstrap(seed);
        if (raw == null || raw.isBlank()) return state;
        String[] f = raw.split(",", -1);
        if (f.length < 15) return state;
        putStores(state.logistics, number(f[0], 140), number(f[1], 125), number(f[2], 95),
                number(f[3], 110), number(f[4], 28));
        state.logistics.cargoCapacity = Math.max(0, number(f[5], 500));
        state.logistics.emergencyRationing = Boolean.parseBoolean(f[6]);
        state.logistics.maintenanceDebt = Math.max(0, number(f[7], 0));
        state.logistics.sparePartsShortage = Math.max(0, number(f[8], 0));
        state.logistics.crewFatigue = clamp(number(f[9], 0), 0, 100);
        state.logistics.readinessPercent = clamp(number(f[10], 88), 0, 100);
        state.logistics.isolatedBaseConvoyDependency = Boolean.parseBoolean(f[11]);
        state.logistics.blockadeStarvation = Math.max(0, number(f[12], 0));
        state.logistics.blackMarketProcurement = Math.max(0, number(f[13], 0));
        state.logistics.salvageProcessingHours = Math.max(0, number(f[14], 0));
        if (f.length >= 18) {
            state.campaignHours = Math.max(0.0, decimal(f[15], 0.0));
            state.aiDeploymentReserve = clamp(number(f[16], 360), 0, 360);
            state.aiDeploymentsPaid = Math.max(0, number(f[17], 0));
        }
        return state;
    }

    private static int store(LogisticsLedger ledger, Resource resource) {
        return ledger.stores.getOrDefault(resource, 0);
    }

    private static void addStore(LogisticsLedger ledger, Resource resource, int delta) {
        ledger.stores.put(resource, Math.max(0, store(ledger, resource) + delta));
    }

    private static int number(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double decimal(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
