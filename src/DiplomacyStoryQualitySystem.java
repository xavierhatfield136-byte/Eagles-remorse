import java.util.ArrayList;
import java.util.List;

public final class DiplomacyStoryQualitySystem {
    private DiplomacyStoryQualitySystem() {}

    public static List<String> reputationAndFavorLines(GameContext ctx) {
        CampaignSystem.CampaignState st = ctx == null ? null : ctx.campaign;
        int green = st == null ? 0 : st.greenContractFavor;
        int yellow = st == null ? 0 : st.yellowLiberationFavor;
        return List.of(
                "Reputation Reason  |  rescue, collateral, trade promises, prisoner treatment, and relief timing change standing",
                "Favors / Obligations  |  green " + green + " yellow " + yellow + " owed support calls can be spent or called in",
                "Negotiation Scene  |  trade, passage, repairs, prisoners, and salvage claims expose terms before commitment"
        );
    }

    public static List<String> npcMemoryAndArcLines(GameContext ctx) {
        return List.of(
                "Recurring Captain  |  named NPC captains remember rescues, retreats, betrayals, and shared battles",
                "Rival Commander  |  enemy leaders adapt to doctrine, strike tempo, and civilian-protection habits",
                "Rescue Return  |  saved crews can return with aid; abandoned crews can seed revenge arcs",
                "Faction Bulletin  |  campaign events produce news about shortages, victories, losses, and alliances"
        );
    }

    public static List<String> crewAndOfficerLines(GameContext ctx) {
        CampaignSystem.CampaignState st = ctx == null ? null : ctx.campaign;
        int strain = st == null ? 0 : (int) Math.round(st.fleetStrain);
        return List.of(
                "Crew Commentary  |  major victories, losses, shortages, and betrayals trigger bridge chatter",
                "Bridge Disagreement  |  officers can disagree during prisoner, salvage, repair, and desperate-deal choices",
                "Officer State  |  stress " + strain + "% trust shifts with success, losses, and kept promises",
                "Captain Log  |  optional milestone entries summarize route, allies, losses, and doctrine"
        );
    }

    public static List<String> consequenceAndEndingLines(GameContext ctx) {
        return List.of(
                "Memorial Entry  |  destroyed friendly ships create memorial records and service-history scars",
                "Prisoner Choice  |  release, ransom, exchange, recruit, or abandon prisoners with diplomatic consequences",
                "Civilian Collateral  |  convoy losses and station damage change trust, prices, and later support",
                "Temporary Alliance  |  ceasefires can open passage but create obligations and betrayal risk",
                "Betrayal Risk  |  desperate black-market or enemy deals can backfire later",
                "Ending Slide  |  allies, losses, doctrine, rescued civilians, and liberated worlds alter the ending"
        );
    }

    public static List<String> narrativePresentationLines(GameContext ctx) {
        return List.of(
                "Narrative Pool  |  authored lines use cooldowns so repeated text is reduced",
                "Quiet Mode  |  concise presentation keeps bulletins, logs, and crew chatter available without interruption"
        );
    }

    public static List<String> allStoryQualityLines(GameContext ctx) {
        ArrayList<String> out = new ArrayList<>();
        out.addAll(reputationAndFavorLines(ctx));
        out.addAll(npcMemoryAndArcLines(ctx));
        out.addAll(crewAndOfficerLines(ctx));
        out.addAll(consequenceAndEndingLines(ctx));
        out.addAll(narrativePresentationLines(ctx));
        return out;
    }
}
