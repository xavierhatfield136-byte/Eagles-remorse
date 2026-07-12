import java.util.List;

interface CampaignActionProvider {
    void contribute(GameContext ctx, List<CampaignSystem.CampaignAction> actions);
}
