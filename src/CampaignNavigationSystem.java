final class CampaignNavigationSystem {
    private CampaignNavigationSystem() {}

    static boolean startTravelToSelectedLocation(GameContext ctx) {
        return CampaignSystem.legacyStartTravelToSelectedLocation(ctx);
    }
}
