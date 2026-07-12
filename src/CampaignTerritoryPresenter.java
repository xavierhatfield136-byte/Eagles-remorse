import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;

final class CampaignTerritoryPresenter {
    private CampaignTerritoryPresenter() {}

    static List<CampaignSystem.CampaignTerritoryOverlayView> overlayViews(GameContext ctx) {
        return legacyOverlayViews(ctx);
    }

    static List<String> detailLines(GameContext ctx, boolean expanded) {
        List<String> lines = CampaignSystem.campaignSelectedTerritoryLines(ctx);
        if (expanded || lines.size() <= 4) return lines;
        return List.copyOf(lines.subList(0, 4));
    }

    static List<CampaignSystem.CampaignTerritoryOverlayView> legacyOverlayViews(GameContext ctx) {
        CampaignSystem.CampaignState st = CampaignSystem.state(ctx);
        if (ctx == null || st == null) return List.of();
        CampaignSystem.synchronizeStrategicExpansionFromLive(st);
        ArrayList<CampaignSystem.CampaignTerritoryOverlayView> out = new ArrayList<>();
        for (StrategicCampaignExpansionSystem.Territory territory : st.strategicExpansion.territories) {
            if (territory == null) continue;
            Faction faction = CampaignSystem.factionFromStrategicId(territory.controller);
            boolean frontLine = StrategicCampaignExpansionSystem.adjacentTerritoryIds(st.strategicExpansion,
                            territory.id.value()).stream()
                    .map(id -> StrategicCampaignExpansionSystem.territory(st.strategicExpansion, id))
                    .filter(Objects::nonNull)
                    .anyMatch(adjacent -> !territory.controller.equals(adjacent.controller));
            boolean activeOperation = st.strategicExpansion.operations.stream().anyMatch(operation -> operation != null
                    && operation.status == StrategicCampaignExpansionSystem.OperationStatus.ACTIVE
                    && (operation.originTerritoryId.equals(territory.id.value())
                    || operation.targetTerritoryId.equals(territory.id.value())));
            boolean beachhead = st.strategicExpansion.beachheads.stream().anyMatch(item -> item != null
                    && item.targetTerritoryId.equals(territory.id.value())
                    && item.status != StrategicCampaignExpansionSystem.BeachheadStatus.DESTROYED
                    && item.status != StrategicCampaignExpansionSystem.BeachheadStatus.COLLAPSED
                    && item.status != StrategicCampaignExpansionSystem.BeachheadStatus.EXPIRED
                    && item.status != StrategicCampaignExpansionSystem.BeachheadStatus.EVACUATED);
            String insignia = faction == null ? "neutral" : faction.insigniaKey();
            String pattern = faction == null ? "neutral" : faction.mapPatternKey();
            String status = territory.name + " | " + territory.controller + " | " + territory.controlState
                    + " | " + territory.supplyState + " | pressure " + territory.frontPressure
                    + (territory.supplySource ? " | SOURCE" : "") + (frontLine ? " | FRONT" : "")
                    + (activeOperation ? " | OPERATION" : "") + (beachhead ? " | BEACHHEAD" : "");
            out.add(new CampaignSystem.CampaignTerritoryOverlayView(territory.id.value(), territory.name, territory.centerX,
                    territory.centerY, faction, insignia, pattern, territory.controlState, territory.supplyState,
                    territory.frontPressure, territory.supplySource, frontLine, activeOperation, beachhead, status));
        }
        out.sort(Comparator.comparing(CampaignSystem.CampaignTerritoryOverlayView::id));
        return List.copyOf(out);
    }

    static List<CampaignSystem.CampaignBattleScarView> battleScarViews(GameContext ctx) {
        CampaignSystem.CampaignState st = CampaignSystem.state(ctx);
        if (st == null || st.strategicExpansion == null) return List.of();
        ArrayList<CampaignSystem.CampaignBattleScarView> out = new ArrayList<>();
        for (StrategicCampaignExpansionSystem.BattleReport report : st.strategicExpansion.battleReports) {
            if (report == null) continue;
            CampaignSystem.CampaignLocation location = CampaignSystem.campaignLocationById(st, report.locationId);
            StrategicCampaignExpansionSystem.Territory territory = location == null
                    ? StrategicCampaignExpansionSystem.territory(st.strategicExpansion, report.locationId) : null;
            double x = location != null ? location.x : (territory == null ? Double.NaN : territory.centerX);
            double y = location != null ? location.y : (territory == null ? Double.NaN : territory.centerY);
            if (!Double.isFinite(x) || !Double.isFinite(y)) continue;
            out.add(new CampaignSystem.CampaignBattleScarView(report.id, report.locationId, x, y, report.battleScar,
                    report.outcome, report.losses, report.salvageRemaining, report.survivorWindowTicks));
        }
        out.sort(Comparator.comparing(CampaignSystem.CampaignBattleScarView::battleId));
        return List.copyOf(out);
    }
}
