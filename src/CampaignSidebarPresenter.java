import java.util.List;
import java.util.ArrayList;

final class CampaignSidebarPresenter {
    private CampaignSidebarPresenter() {}

    static List<String> summaryLines(GameContext ctx) {
        return legacySummaryLines(ctx);
    }

    static List<String> legacySummaryLines(GameContext ctx) {
        CampaignSystem.CampaignState st = CampaignSystem.state(ctx);
        if (ctx == null || st == null) return List.of("Campaign data unavailable.");
        CampaignSystem.CampaignLocation current = CampaignSystem.currentCampaignLocation(ctx);
        CampaignSystem.CampaignTravelState travel = st.galaxyTravel;
        ArrayList<String> out = new ArrayList<>();
        out.add("Position: " + ((current == null) ? "In transit" : current.name));
        out.add("Destination: " + CampaignSystem.selectedStrategicDestinationLabel(ctx));
        out.add("Travel: " + CampaignSystem.galaxyTravelSidebarReadout(ctx, travel));
        out.add("Hunt Status: " + CampaignSystem.huntedStatusReadout(ctx));
        out.add("Enemy Alert: " + CampaignSystem.enemyAlertReadout(ctx) + "  |  " + CampaignSystem.enemyAlertRegionReadout(ctx));
        out.add("Alert / Pressure: " + CampaignSystem.enemyAlertReadout(ctx) + "  |  " + CampaignSystem.theaterPressureReadout(ctx));
        out.add("Selected Region: " + CampaignSystem.campaignSelectedTheaterLabel(st));
        out.add("Earth Gate: " + (CampaignSystem.earthPhaseUnlocked(st)
                ? "UNLOCKED"
                : ("LOCKED " + CampaignSystem.stabilizedTheaterCount(st)
                + "/" + CampaignSystem.earthPhaseMinStabilizedTheatersRequired())));
        return out;
    }
}
