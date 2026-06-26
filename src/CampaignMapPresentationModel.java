import java.util.ArrayList;
import java.util.List;

/** Immutable campaign-map read models prepared outside Renderer. */
public final class CampaignMapPresentationModel {
    public record SidebarContent(
            UiState.CampaignCommandTab tab,
            String primaryHeader,
            List<String> primaryLines,
            String secondaryHeader,
            List<String> secondaryLines) {
        public SidebarContent {
            primaryLines = List.copyOf(primaryLines == null ? List.of() : primaryLines);
            secondaryLines = List.copyOf(secondaryLines == null ? List.of() : secondaryLines);
        }
    }

    public record ResourceBoard(
            int fuel,
            int supplies,
            int ammo,
            int salvage,
            int ore,
            List<String> trendLines,
            List<String> warningLines) {
        public ResourceBoard {
            trendLines = List.copyOf(trendLines == null ? List.of() : trendLines);
            warningLines = List.copyOf(warningLines == null ? List.of() : warningLines);
        }
    }

    private CampaignMapPresentationModel() {}

    public static SidebarContent sidebar(GameContext ctx) {
        UiState.CampaignCommandTab tab = visibleTab(ctx);
        CampaignSystem.CampaignLocation selected = CampaignSystem.selectedCampaignLocation(ctx);
        return switch (tab) {
            case FLEET -> new SidebarContent(
                    tab, "FLEET MANAGER", CampaignSystem.campaignFleetManagerLines(ctx),
                    "READINESS / DETACHMENTS",
                    joined(CampaignSystem.campaignFleetConditionLines(ctx),
                            CampaignSystem.campaignFleetDetachmentLines(ctx)));
            case RESOURCES -> new SidebarContent(
                    tab, "RESOURCE BOARD", CampaignSystem.campaignResourceManagerLines(ctx),
                    "LOGISTICS / ROUTE",
                    joined(CampaignSystem.campaignResourceTrendLines(ctx),
                            CampaignSystem.campaignResourceWarningLines(ctx)));
            default -> new SidebarContent(
                    tab, "WAR ROOM", CampaignSystem.campaignWarRoomLines(ctx),
                    selected == null ? "SELECTED COURSE" : selected.name.toUpperCase(java.util.Locale.US),
                    CampaignSystem.selectedLocationSidebarLines(ctx));
        };
    }

    public static ResourceBoard resources(GameContext ctx) {
        return new ResourceBoard(
                CampaignSystem.campaignFuel(ctx),
                CampaignSystem.campaignSupplies(ctx),
                CampaignSystem.campaignAmmo(ctx),
                CampaignSystem.campaignSalvageStock(ctx),
                CampaignSystem.currentCampaignOre(ctx),
                CampaignSystem.campaignResourceTrendLines(ctx),
                CampaignSystem.campaignResourceWarningLines(ctx));
    }

    private static UiState.CampaignCommandTab visibleTab(GameContext ctx) {
        UiState.CampaignCommandTab tab = ctx == null || ctx.ui == null
                ? UiState.CampaignCommandTab.NAV
                : ctx.ui.campaignCommandTab;
        return tab == UiState.CampaignCommandTab.STRIKES ? UiState.CampaignCommandTab.NAV : tab;
    }

    private static List<String> joined(List<String> first, List<String> second) {
        ArrayList<String> out = new ArrayList<>();
        if (first != null) out.addAll(first);
        if (second != null) out.addAll(second);
        return List.copyOf(out);
    }
}
