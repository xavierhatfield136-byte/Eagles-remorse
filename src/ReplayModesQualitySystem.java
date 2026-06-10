import java.util.ArrayList;
import java.util.List;

public final class ReplayModesQualitySystem {
    private ReplayModesQualitySystem() {}

    public static List<String> replayAndStatsLines(GameContext ctx) {
        return List.of(
                "Deterministic Replay  |  record seed, inputs, campaign state, and tactical events for playback",
                "Cinematic Replay Camera  |  follows kills, rescues, objectives, detonations, and retreat beats after replay exists",
                "Campaign Event Log  |  post-campaign statistics summarize routes, allies, losses, economy, and doctrine",
                "After-Action Export  |  report export packages review notes, timeline, stats, and share code"
        );
    }

    public static List<String> modeAndStartLines(GameContext ctx) {
        return List.of(
                "Scenario Launch  |  custom scenario flow validates seed, faction, map, objectives, and loadout",
                "Challenge Mode  |  scoring, curated rotations, mutators, and completion badges",
                "New Game Plus  |  locked until base campaign balance is stable; keeps meta without trivializing first run",
                "Skirmish Fleet Builder  |  standalone fleet composition, doctrine, and test battle setup",
                "Faction Start  |  faction-specific campaign openings with different resources and obligations",
                "Branching Chapter  |  major alliances or betrayals alter later chapter setup"
        );
    }

    public static List<String> sharingAndArchiveLines(GameContext ctx) {
        return List.of(
                "Historical Scenario  |  completed campaign events can generate replayable history setups",
                "Shareable Summary  |  reproducible share codes include seed, route, doctrine, outcomes, and mods",
                "Async Campaign Sharing  |  hand off campaign state and reports without live co-op dependency",
                "Spectator Mode  |  autonomous spectator follows campaign and battle drama",
                "Balanced Unlocks  |  metagame rewards preserve first-campaign challenge"
        );
    }

    public static List<String> allReplayModeLines(GameContext ctx) {
        ArrayList<String> out = new ArrayList<>();
        out.addAll(replayAndStatsLines(ctx));
        out.addAll(modeAndStartLines(ctx));
        out.addAll(sharingAndArchiveLines(ctx));
        return out;
    }
}
