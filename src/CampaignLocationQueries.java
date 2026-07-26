import java.util.List;

final class CampaignLocationQueries {
    private CampaignLocationQueries() {}

    static List<CampaignSystem.CampaignLocation> mainCampaignLocations(GameContext ctx) {
        CampaignSystem.ensureStrategicOvermapReady(ctx);
        CampaignSystem.CampaignState st = CampaignSystem.state(ctx);
        if (st == null || st.galaxyMainPois.isEmpty()) return List.of();
        return List.copyOf(st.galaxyMainPois);
    }

    static List<CampaignSystem.CampaignLocation> campaignAreasOfInterest(GameContext ctx) {
        CampaignSystem.ensureStrategicOvermapReady(ctx);
        CampaignSystem.CampaignState st = CampaignSystem.state(ctx);
        if (st == null || st.galaxyAreasOfInterest.isEmpty()) return List.of();
        return List.copyOf(st.galaxyAreasOfInterest);
    }

    static CampaignSystem.CampaignLocation currentCampaignLocation(GameContext ctx) {
        CampaignSystem.ensureStrategicOvermapReady(ctx);
        CampaignSystem.CampaignState st = CampaignSystem.state(ctx);
        if (st == null) return null;
        String dockedId = (st.dockedGalaxyLocationId == null) ? "" : st.dockedGalaxyLocationId;
        if (!dockedId.isBlank()) {
            CampaignSystem.CampaignLocation docked = CampaignSystem.campaignLocationById(st, dockedId);
            if (docked != null) return docked;
        }
        return CampaignSystem.campaignLocationById(st, st.currentGalaxyLocationId);
    }

    static CampaignSystem.CampaignLocation selectedCampaignLocation(GameContext ctx) {
        CampaignSystem.ensureStrategicOvermapReady(ctx);
        CampaignSystem.CampaignState st = CampaignSystem.state(ctx);
        if (st == null) return null;
        CampaignSystem.CampaignLocation selected = CampaignSystem.campaignLocationById(st, st.selectedGalaxyLocationId);
        if (selected != null) return selected;
        if (CampaignSystem.hasSelectedFreeTravelTarget(ctx)) return selected;
        if (CampaignSystem.hasSelectedCampaignContactTarget(ctx)
                && ctx != null && ctx.ui != null && ctx.ui.selectedCampaignContactTrackable) {
            return null;
        }
        CampaignSystem.CampaignLocation nearby = CampaignSystem.nearestDockingRangeCampaignLocation(st);
        if (nearby != null) return nearby;
        if (CampaignSystem.hasSelectedCampaignContactTarget(ctx)) return null;
        return null;
    }
}
