import app.config.MultiplayerLaunchConfig;
import app.config.MultiplayerMissionChoice;

/** Gate A bridge from the menu multiplayer launch request into a real playable GamePanel context. */
public final class MultiplayerPlayableDuelContextFactory {
    private MultiplayerPlayableDuelContextFactory() {}

    public static GameContext create(MultiplayerLaunchConfig launch) {
        MissionLaunchSpec spec = resolveMissionSpec(launch, System.nanoTime());
        MultiplayerRulesV1.BattleSetup setup = MultiplayerHostLaunchAdapter.toBattleSetup(
                spec,
                launch == null ? "Host" : launch.hostPlayerName,
                launch == null ? "Client" : launch.clientPlayerName);
        int localSlotId = launch != null && !launch.host()
                ? MultiplayerRulesV1.CLIENT_SLOT_ID
                : MultiplayerRulesV1.HOST_SLOT_ID;
        MultiplayerBattleRuntime runtime = MultiplayerBattleRuntime.createAuthoritative(
                setup, false, localSlotId, spec.worldW(), spec.worldH());
        GameContext ctx = runtime.context();
        ctx.multiplayerBattle = true;
        ctx.multiplayerBattleRuntime = runtime;
        String matchId = launch == null ? "in-game-multiplayer" : launch.matchId;
        String sessionNonce = MultiplayerProtocolV1.sessionNonceForMatch(matchId);
        runtime.configureMatchIdentity(matchId, sessionNonce);
        ctx.multiplayerLocalPlayerId = MultiplayerProtocolV1.playerIdForSlot(localSlotId);
        EventSystem.showBanner(ctx, launch != null && !launch.host()
                ? "MULTIPLAYER CLIENT DUEL READY"
                : "MULTIPLAYER HOST DUEL READY", 1.8);
        return ctx;
    }

    static MissionLaunchSpec resolveMissionSpec(MultiplayerLaunchConfig launch, long seed) {
        String missionId = launch == null ? MultiplayerMissionChoice.DEFAULT_MISSION_ID : launch.missionId;
        long resolvedSeed = launch != null && launch.missionSeed > 0L ? launch.missionSeed : seed;
        MultiplayerMissionChoice choice = MultiplayerMissionChoice.fromMissionId(missionId);
        MissionTemplate template = CustomMissionCatalog.templateFor(choice.missionId());
        int worldW = launch != null && launch.missionWorldW > 0 ? launch.missionWorldW : template.worldW();
        int worldH = launch != null && launch.missionWorldH > 0 ? launch.missionWorldH : template.worldH();
        return CustomMissionCatalog.resolveMultiplayerMission(choice.missionId(), resolvedSeed, worldW, worldH);
    }
}
