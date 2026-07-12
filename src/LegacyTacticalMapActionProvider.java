import java.util.List;

final class LegacyTacticalMapActionProvider implements TacticalMapActionProvider {
    @Override
    public void contribute(GameContext ctx, List<CampaignSystem.CampaignAction> actions) {
        actions.addAll(CampaignSystem.legacyTacticalMapVisibleActions(ctx));
    }
}
