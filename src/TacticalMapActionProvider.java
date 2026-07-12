import java.util.List;

interface TacticalMapActionProvider {
    void contribute(GameContext ctx, List<CampaignSystem.CampaignAction> actions);
}
