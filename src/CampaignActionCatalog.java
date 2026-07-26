import java.util.List;
import java.util.ArrayList;

final class CampaignActionCatalog {
    private static final List<CampaignActionProvider> PROVIDERS = List.of(
            (ctx, actions) -> actions.addAll(CampaignSystem.legacyCampaignVisibleActions(ctx))
    );

    private CampaignActionCatalog() {}

    static List<CampaignSystem.CampaignAction> visibleActions(GameContext ctx) {
        ArrayList<CampaignSystem.CampaignAction> actions = new ArrayList<>();
        for (CampaignActionProvider provider : PROVIDERS) {
            provider.contribute(ctx, actions);
        }
        return List.copyOf(actions);
    }
}
