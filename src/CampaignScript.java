interface CampaignScript {
    default void onStart(GameContext ctx, CampaignSystem.CampaignState state) {}

    default void update(GameContext ctx, CampaignSystem.CampaignState state, double dt) {}

    default void onComplete(GameContext ctx, CampaignSystem.CampaignState state) {}
}
