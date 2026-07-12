import java.util.List;
import java.util.ArrayList;

final class CampaignRoutePresenter {
    private CampaignRoutePresenter() {}

    static List<String> selectedRouteAssessmentLines(GameContext ctx) {
        return CampaignSystem.legacySelectedRouteAssessmentLines(ctx);
    }

    static List<String> selectedRouteForceWarningLines(GameContext ctx) {
        CampaignSystem.ensureStrategicOvermapReady(ctx);
        CampaignSystem.CampaignState st = CampaignSystem.state(ctx);
        CampaignSystem.CampaignLocation selected = CampaignSystem.selectedCampaignLocation(ctx);
        if (ctx == null || st == null || selected == null) return List.of();
        CampaignSystem.ensureGalaxyFleetPosition(st, CampaignSystem.currentCampaignLocation(ctx));
        return CampaignSystem.routeForceWarningLines(ctx, st.playerGalaxyX, st.playerGalaxyY, selected.x, selected.y);
    }

    static List<String> mapBookmarkLines(GameContext ctx) {
        CampaignSystem.CampaignState st = CampaignSystem.state(ctx);
        if (st == null || st.campaignMapBookmarks.isEmpty()) return List.of("Bookmarks: none");
        ArrayList<String> out = new ArrayList<>();
        out.add("Bookmarks: " + st.campaignMapBookmarks.size());
        int start = Math.max(0, Math.min(st.campaignMapBookmarks.size() - 1, st.selectedCampaignMapBookmarkIndex));
        for (int i = 0; i < st.campaignMapBookmarks.size() && out.size() < 6; i++) {
            int idx = (start + i) % st.campaignMapBookmarks.size();
            CampaignSystem.CampaignMapBookmark bookmark = st.campaignMapBookmarks.get(idx);
            String selected = idx == st.selectedCampaignMapBookmarkIndex ? "* " : "";
            out.add(selected + bookmark.category + ": " + bookmark.label
                    + "  X " + (int) Math.round(bookmark.x)
                    + " Y " + (int) Math.round(bookmark.y));
        }
        return out;
    }

    static List<String> routeQueueLines(GameContext ctx) {
        CampaignSystem.CampaignState st = CampaignSystem.state(ctx);
        if (st == null || st.campaignRouteQueue.isEmpty()) return List.of("Route Queue: none");
        ArrayList<String> out = new ArrayList<>();
        out.add("Route Queue: " + st.campaignRouteQueue.size() + " stop" + (st.campaignRouteQueue.size() == 1 ? "" : "s"));
        for (int i = 0; i < st.campaignRouteQueue.size() && out.size() < 5; i++) {
            CampaignSystem.CampaignRouteQueueStop stop = st.campaignRouteQueue.get(i);
            out.add((i + 1) + ". " + stop.category + ": " + stop.label
                    + "  |  " + stop.condition
                    + "  X " + (int) Math.round(stop.x)
                    + " Y " + (int) Math.round(stop.y));
        }
        return out;
    }
}
