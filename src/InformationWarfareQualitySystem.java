import java.util.ArrayList;
import java.util.List;

public final class InformationWarfareQualitySystem {
    private InformationWarfareQualitySystem() {}

    public static List<String> intelligenceBoardLines(GameContext ctx) {
        CampaignSystem.CampaignState st = ctx == null ? null : ctx.campaign;
        int contacts = st == null ? 0 : Math.max(0, st.galaxySearchGroups.size());
        return List.of(
                "Intelligence Board  |  uncertain contacts " + contacts + " sorted by confidence, age, source, and risk",
                "Source Quality  |  scan, rumor, scout, captured manifest, debrief, relay, and agent report tracked separately",
                "Contact Decay  |  confidence fades over time unless refreshed by scan, scout, relay, or probe"
        );
    }

    public static List<String> deceptionAndPatternLines(GameContext ctx) {
        return List.of(
                "Decoy Operation  |  false contacts can waste time or strikes if trusted blindly",
                "Enemy Feint  |  repeated patterns reveal bait routes and fake retreats",
                "Officer Interpretation  |  stressed officers can be wrong about ambiguous contacts",
                "Intercepted Chatter  |  partial radio clues reveal intent, cargo, timing, or deception risk"
        );
    }

    public static List<String> scoutProbeAndDeadZoneLines(GameContext ctx) {
        return List.of(
                "Scouting Order  |  detached groups can verify contacts, routes, and station status",
                "Probe Launch  |  limited range, recovery risk, and one-shot contact refresh",
                "Dead Zone  |  command links and sensors degrade in shadow, debris, quarantine, and jamming regions"
        );
    }

    public static List<String> counterIntelAndAgentLines(GameContext ctx) {
        return List.of(
                "Counterintelligence Sweep  |  checks for false orders, spoofed relays, and compromised manifests",
                "Sabotage Incident  |  neglected security can damage supplies, repairs, morale, or command links",
                "Agent Mission  |  recruit, insert, and extract agents for manifests, routes, and prisoner intel",
                "Propaganda Event  |  misinformation can change reputation, market panic, or faction trust",
                "Intel Archive  |  stored patterns improve future prediction and reduce repeated surprises"
        );
    }

    public static List<String> allInformationWarfareLines(GameContext ctx) {
        ArrayList<String> out = new ArrayList<>();
        out.addAll(intelligenceBoardLines(ctx));
        out.addAll(deceptionAndPatternLines(ctx));
        out.addAll(scoutProbeAndDeadZoneLines(ctx));
        out.addAll(counterIntelAndAgentLines(ctx));
        return out;
    }
}
