final class CampaignRuntime {
    private static final CampaignRuntime DEFAULT = new CampaignRuntime();

    private CampaignRuntime() {}

    static void update(GameContext ctx, double dt) {
        DEFAULT.updateCampaign(ctx, dt);
    }

    private void updateCampaign(GameContext ctx, double dt) {
        CampaignSystem.legacyUpdate(ctx, dt);
    }
}
