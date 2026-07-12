import java.util.List;

final class LegacyCampaignActionProvider implements CampaignActionProvider {
    @Override
    public void contribute(GameContext ctx, List<CampaignSystem.CampaignAction> actions) {
        actions.addAll(CampaignSystem.legacyCampaignVisibleActions(ctx));
    }
}
