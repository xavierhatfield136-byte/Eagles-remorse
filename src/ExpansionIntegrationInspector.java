import java.util.ArrayList;
import java.util.List;

/** Developer-facing audit readout for expansion systems that are modeled but not always live-integrated. */
public final class ExpansionIntegrationInspector {
    private ExpansionIntegrationInspector() {}

    public static List<String> lines(GameContext ctx) {
        CampaignSystem.CampaignState state = (ctx == null) ? null : ctx.campaign;
        if (state == null) return List.of("Expansion Inspector: campaign unavailable");

        int active = countActive(state);
        int economyEvents = (state.economyExpansion == null) ? 0
                : state.economyExpansion.logistics.crewFatigue + state.economyExpansion.logistics.maintenanceDebt;
        int diplomacyEvents = (state.diplomacyNarrative == null) ? 0
                : state.diplomacyNarrative.relationships.visibleReasons.size();
        int fleetArchiveEntries = CampaignSystem.campaignFleetArchiveLines(ctx, 999).size();

        ArrayList<String> out = new ArrayList<>();
        out.add("EXPANSION INSPECTOR  projection/model-only audit  active " + active
                + "/8  live authority remains in CampaignState");
        out.add("Observed events: ledger burden " + economyEvents + "  reputation reasons " + diplomacyEvents
                + "  fleet archive entries " + fleetArchiveEntries);
        out.add("PROJECTION ONLY: expansion resource stores, strategic summaries, operations templates, production catalogs");
        out.add("DEFERRED MODEL: doctrine queue, deep stations/hazards/politics, community editor/content loader");
        return out;
    }

    private static int countActive(CampaignSystem.CampaignState state) {
        int count = 0;
        if (state.strategicExpansion != null) count++;
        if (state.economyExpansion != null) count++;
        if (state.diplomacyNarrative != null) count++;
        if (state.operationsExpansion != null) count++;
        if (state.productionReadiness != null) count++;
        if (state.fleetDoctrineExpansion != null) count++;
        if (state.deepCampaignExpansion != null) count++;
        if (state.communityContent != null) count++;
        return count;
    }
}
