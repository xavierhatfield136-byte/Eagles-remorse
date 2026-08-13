public record FactionCapabilities(
        boolean playable,
        boolean selectableInCustomBattle,
        boolean participatesInCampaign,
        boolean ownsTerritory,
        boolean canTrade,
        boolean canUseCustomHulls
) {
    public static FactionCapabilities forFaction(Faction faction) {
        if (faction == Faction.TEAM_E) {
            return new FactionCapabilities(true, true, false, false, false, true);
        }
        return new FactionCapabilities(true, true, true, true, true, false);
    }

    public static boolean participatesInCampaign(Faction faction) {
        return forFaction(faction).participatesInCampaign();
    }

    public static boolean canUseCustomHulls(Faction faction) {
        return forFaction(faction).canUseCustomHulls();
    }
}
