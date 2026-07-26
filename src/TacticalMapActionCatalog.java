import java.util.ArrayList;
import java.util.List;

final class TacticalMapActionCatalog {
    private static final List<TacticalMapActionProvider> PROVIDERS = List.of(
            (ctx, actions) -> actions.addAll(CampaignSystem.legacyTacticalMapVisibleActions(ctx))
    );

    private TacticalMapActionCatalog() {}

    static List<CampaignSystem.CampaignAction> visibleActions(GameContext ctx) {
        ArrayList<CampaignSystem.CampaignAction> actions = new ArrayList<>();
        for (TacticalMapActionProvider provider : PROVIDERS) {
            provider.contribute(ctx, actions);
        }
        return List.copyOf(actions);
    }
}
