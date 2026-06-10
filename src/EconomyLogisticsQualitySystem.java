import java.util.ArrayList;
import java.util.List;

public final class EconomyLogisticsQualitySystem {
    private EconomyLogisticsQualitySystem() {}

    public static List<String> economyDecisionLines(GameContext ctx) {
        CampaignSystem.CampaignState st = ctx == null ? null : ctx.campaign;
        int ore = st == null ? 0 : Math.max(0, st.oreLedger.storedOre);
        int salvage = st == null ? 0 : Math.max(0, st.campaignSalvage);
        int fuel = st == null ? 0 : Math.max(0, st.campaignFuel);
        int supplies = st == null ? 0 : Math.max(0, st.campaignSupplies);
        int ammo = st == null ? 0 : Math.max(0, st.campaignAmmo);
        return List.of(
                "Split Resources  |  ore " + ore + " salvage " + salvage + " fuel " + fuel + " supplies " + supplies + " ammo " + ammo,
                "Cargo Allocation  |  ore builds hulls, salvage pays refits, fuel buys reach, supplies buy repairs, ammo buys strikes",
                "Trade Substitute  |  diplomacy, favors, contracts, and passage deals can replace mining grind"
        );
    }

    public static List<String> marketAndContractLines(GameContext ctx) {
        CampaignSystem.CampaignState st = ctx == null ? null : ctx.campaign;
        int favor = st == null ? 0 : Math.max(0, st.greenContractFavor + st.yellowLiberationFavor);
        return List.of(
                "Market Screen  |  prices move with shortages, blockades, local trade health, and reputation",
                "Shortage Reason  |  fuel rises under blockade; supplies rise after battle damage; ammo rises after strike tempo",
                "Contract Board  |  escort, rescue, bounty, survey, salvage, relief, and convoy work available",
                "Contract Stakes  |  deadlines, collateral, reputation, and forfeited deposits are visible before acceptance",
                "Rival Bidders  |  high-value salvage attracts scavengers and competing brokers",
                "Owed Support  |  favors " + favor + " can discount repairs, open passage, or call escorts"
        );
    }

    public static List<String> shipyardAndMaintenanceLines(GameContext ctx) {
        CampaignSystem.CampaignState st = ctx == null ? null : ctx.campaign;
        int strain = st == null ? 0 : (int) Math.round(st.fleetStrain);
        return List.of(
                "Hull Insurance  |  expensive hulls can be insured for partial replacement after loss",
                "Maintenance Debt  |  ignored strain " + strain + "% raises repair cost and breakdown risk",
                "Spare Parts  |  shortages force hard choices between weapons, engines, armor, and civilian repairs",
                "Field Repairs  |  fast unreliable repairs restore sortie readiness but add failure risk",
                "Shipyard Region  |  southern yards favor repairs, frontier yards favor refits, Earthward yards favor military hulls",
                "Construction Queue  |  hull time, ore, salvage, rare materials, and delivery ETA shown together",
                "Refit Template  |  saved fleet loadouts can restore known weapon, escort, and logistics packages",
                "Black Market  |  procurement trades reputation and reliability for scarce hulls or weapons",
                "Rare Materials  |  specific cores unlock special hulls, weapons, and station repairs"
        );
    }

    public static List<String> blockadeAndReserveLines(GameContext ctx) {
        CampaignSystem.CampaignState st = ctx == null ? null : ctx.campaign;
        int alert = st == null ? 0 : (int) Math.round(st.enemyAlertLevel);
        int aiReserve = Math.max(0, 100 - alert / 2);
        return List.of(
                "Convoy Dependency  |  isolated bases need convoy lanes or services degrade",
                "Blockade Starvation  |  relief missions restore fuel, supplies, market health, and civilian trust",
                "Salvage Processing  |  big wreck hauls convert over time instead of instant cash",
                "AI Resource Reserve  |  visible hostile reserve " + aiReserve + "% explains pressure spikes",
                "Relief Alternative  |  trade and diplomacy can open supplies without another mining route"
        );
    }

    public static List<String> allEconomyQualityLines(GameContext ctx) {
        ArrayList<String> out = new ArrayList<>();
        out.addAll(economyDecisionLines(ctx));
        out.addAll(marketAndContractLines(ctx));
        out.addAll(shipyardAndMaintenanceLines(ctx));
        out.addAll(blockadeAndReserveLines(ctx));
        return out;
    }
}
