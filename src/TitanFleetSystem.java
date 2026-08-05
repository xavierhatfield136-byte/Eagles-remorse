import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class TitanFleetSystem {
    public static final int MOTHERSHIP_TITAN_CAP = 8;

    public enum PurchaseResult {
        PURCHASED(true, "TITAN PURCHASED"),
        INVALID_SELECTION(false, "INVALID TITAN SELECTION"),
        NOT_IN_CAMPAIGN(false, "TITANS ARE CAMPAIGN-ONLY"),
        TITAN_CAP_REACHED(false, "MOTHERSHIP TITAN CAP REACHED"),
        TITAN_TYPE_ALREADY_OWNED(false, "TITAN TYPE ALREADY IN FLEET"),
        NOT_YET_AVAILABLE(false, "TITAN NOT YET AVAILABLE"),
        NOT_ENOUGH_CREDITS(false, "NOT ENOUGH CREDITS");

        private final boolean success;
        private final String bannerText;

        PurchaseResult(boolean success, String bannerText) {
            this.success = success;
            this.bannerText = bannerText;
        }

        public boolean success() {
            return success;
        }

        public String bannerText() {
            return bannerText;
        }
    }

    private TitanFleetSystem() {}

    public static int mothershipTitanCap() {
        return MOTHERSHIP_TITAN_CAP;
    }

    public static boolean isCampaignTitanFleetAvailable(GameContext ctx) {
        CampaignSystem.CampaignState st = state(ctx);
        return st != null && st.enabled;
    }

    public static List<TitanArchetype> ownedTitans(GameContext ctx) {
        CampaignSystem.CampaignState st = state(ctx);
        if (st == null || st.ownedTitans.isEmpty()) return List.of();
        return Collections.unmodifiableList(new ArrayList<>(st.ownedTitans));
    }

    public static int ownedTitanCount(GameContext ctx) {
        CampaignSystem.CampaignState st = state(ctx);
        return (st == null) ? 0 : st.ownedTitans.size();
    }

    public static int remainingTitanSlots(GameContext ctx) {
        CampaignSystem.CampaignState st = state(ctx);
        if (st == null) return TitanArchetype.values().length;
        int ownedUnique = 0;
        for (TitanArchetype archetype : TitanArchetype.values()) {
            if (st.ownedTitans.contains(archetype)) ownedUnique++;
        }
        return Math.max(0, TitanArchetype.values().length - ownedUnique);
    }

    public static int totalStandardShipCommandCapacity(GameContext ctx) {
        CampaignSystem.CampaignState st = state(ctx);
        if (st == null) return 0;
        int total = 0;
        for (TitanArchetype archetype : st.ownedTitans) {
            if (archetype == null) continue;
            total += archetype.standardShipCommandCapacity();
        }
        return total;
    }

    public static int totalEliteSupershipCommandCapacity(GameContext ctx) {
        CampaignSystem.CampaignState st = state(ctx);
        if (st == null) return 0;
        int total = 0;
        for (TitanArchetype archetype : st.ownedTitans) {
            if (archetype == null) continue;
            total += archetype.eliteSupershipCommandCapacity();
        }
        return total;
    }

    public static int totalCommandHullCapacity(GameContext ctx) {
        return totalStandardShipCommandCapacity(ctx) + totalEliteSupershipCommandCapacity(ctx);
    }

    public static List<TitanArchetype> availableTitanArchetypes(GameContext ctx) {
        CampaignSystem.CampaignState st = state(ctx);
        int sector = (st == null) ? 1 : Math.max(1, st.sector);
        ArrayList<TitanArchetype> out = new ArrayList<>();
        for (TitanArchetype archetype : TitanArchetype.values()) {
            if (archetype.isAvailableInSector(sector)) {
                out.add(archetype);
            }
        }
        return out;
    }

    public static TitanArchetype nextLockedArchetype(GameContext ctx) {
        CampaignSystem.CampaignState st = state(ctx);
        int sector = (st == null) ? 1 : Math.max(1, st.sector);
        for (TitanArchetype archetype : TitanArchetype.values()) {
            if (!archetype.isAvailableInSector(sector)) {
                return archetype;
            }
        }
        return null;
    }

    public static PurchaseResult purchaseTitan(GameContext ctx, TitanArchetype archetype) {
        PurchaseResult result = evaluatePurchase(ctx, archetype);
        if (!result.success()) {
            if (ctx != null) {
                EventSystem.showBanner(ctx, result.bannerText(), 1.4);
            }
            return result;
        }

        CampaignSystem.CampaignState st = state(ctx);
        if (!st.ownedTitans.contains(archetype)) {
            st.ownedTitans.add(archetype);
        }
        ctx.credits -= archetype.costCredits();
        if (ctx.player != null) {
            ctx.player.cargoMax = Math.max(ctx.player.cargoMax, 10_000);
        }
        EventSystem.showBanner(
                ctx,
                "TITAN ONLINE: " + archetype.displayName().toUpperCase(Locale.US),
                1.4);
        return PurchaseResult.PURCHASED;
    }

    public static String serializeOwnedTitans(List<TitanArchetype> titans) {
        if (titans == null || titans.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        ArrayList<TitanArchetype> seen = new ArrayList<>();
        for (TitanArchetype archetype : titans) {
            if (archetype == null) continue;
            if (seen.contains(archetype)) continue;
            seen.add(archetype);
            if (sb.length() > 0) sb.append(',');
            sb.append(archetype.name());
        }
        return sb.toString();
    }

    public static void restoreOwnedTitans(CampaignSystem.CampaignState state, String raw) {
        if (state == null) return;
        state.ownedTitans.clear();
        if (raw == null || raw.isBlank()) return;
        String[] parts = raw.split(",");
        for (String part : parts) {
            TitanArchetype archetype = TitanArchetype.fromSerializedName(part);
            if (archetype != null && !state.ownedTitans.contains(archetype)) {
                state.ownedTitans.add(archetype);
            }
        }
    }

    private static PurchaseResult evaluatePurchase(GameContext ctx, TitanArchetype archetype) {
        if (archetype == null) return PurchaseResult.INVALID_SELECTION;
        CampaignSystem.CampaignState st = state(ctx);
        if (st == null || !st.enabled) return PurchaseResult.NOT_IN_CAMPAIGN;
        if (st.ownedTitans.contains(archetype)) return PurchaseResult.TITAN_TYPE_ALREADY_OWNED;
        if (!archetype.isAvailableInSector(Math.max(1, st.sector))) return PurchaseResult.NOT_YET_AVAILABLE;
        if (ctx == null || ctx.credits < archetype.costCredits()) return PurchaseResult.NOT_ENOUGH_CREDITS;
        return PurchaseResult.PURCHASED;
    }

    private static CampaignSystem.CampaignState state(GameContext ctx) {
        return (ctx == null) ? null : ctx.campaign;
    }
}
