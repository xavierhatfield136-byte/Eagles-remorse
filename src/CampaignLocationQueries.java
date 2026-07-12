import java.util.List;

final class CampaignLocationQueries {
    private CampaignLocationQueries() {}

    static List<CampaignSystem.CampaignLocation> mainCampaignLocations(GameContext ctx) {
        return legacyMainCampaignLocations(ctx);
    }

    static List<CampaignSystem.CampaignLocation> campaignAreasOfInterest(GameContext ctx) {
        return legacyCampaignAreasOfInterest(ctx);
    }

    static CampaignSystem.CampaignLocation currentCampaignLocation(GameContext ctx) {
        return legacyCurrentCampaignLocation(ctx);
    }

    static CampaignSystem.CampaignLocation selectedCampaignLocation(GameContext ctx) {
        return legacySelectedCampaignLocation(ctx);
    }

    static List<CampaignSystem.CampaignLocation> legacyMainCampaignLocations(GameContext ctx) {
        CampaignSystem.ensureStrategicOvermapReady(ctx);
        CampaignSystem.CampaignState st = CampaignSystem.state(ctx);
        if (st == null || st.galaxyMainPois.isEmpty()) return List.of();
        return List.copyOf(st.galaxyMainPois);
    }

    static List<CampaignSystem.CampaignLocation> legacyCampaignAreasOfInterest(GameContext ctx) {
        CampaignSystem.ensureStrategicOvermapReady(ctx);
        CampaignSystem.CampaignState st = CampaignSystem.state(ctx);
        if (st == null || st.galaxyAreasOfInterest.isEmpty()) return List.of();
        return List.copyOf(st.galaxyAreasOfInterest);
    }

    static CampaignSystem.CampaignLocation legacyCurrentCampaignLocation(GameContext ctx) {
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

    static CampaignSystem.CampaignLocation legacySelectedCampaignLocation(GameContext ctx) {
        CampaignSystem.ensureStrategicOvermapReady(ctx);
        CampaignSystem.CampaignState st = CampaignSystem.state(ctx);
        if (st == null) return null;
        CampaignSystem.CampaignLocation selected = CampaignSystem.campaignLocationById(st, st.selectedGalaxyLocationId);
        if (selected != null) return selected;
        if (CampaignSystem.hasSelectedFreeTravelTarget(ctx)) return selected;
        if (CampaignSystem.hasSelectedCampaignContactTarget(ctx)) return null;
        return CampaignSystem.nearestDockingRangeCampaignLocation(st);
    }
}
