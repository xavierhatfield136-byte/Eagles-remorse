import app.config.GameConfig;
import app.config.GameMode;
import app.config.MultiplayerLaunchConfig;
import app.config.MultiplayerMissionChoice;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameLaunchRequestTest {

    @Test
    void legacyCustomBattleConfigResolvesSinglePlayerMissionSpecWithoutMultiplayerContext() {
        GameConfig config = new GameConfig(
                GameMode.CUSTOM_BATTLES,
                6200,
                4100,
                true,
                1234L,
                false,
                Faction.ALLY.teamId(),
                false,
                Faction.ENEMY.teamId(),
                "FRIGATE:2,CRUISER:1",
                "DESTROYER:2");

        GameLaunchRequest request = GameLaunchRequest.fromGameConfig(config);

        assertEquals(GameMode.CUSTOM_BATTLES, request.gameMode());
        assertEquals(config, request.legacyConfig());
        assertFalse(request.multiplayer());
        assertNull(request.multiplayerContext());
        assertNotNull(request.missionLaunchSpec());
        assertEquals(CustomMissionCatalog.CUSTOM_BATTLE_ID, request.missionLaunchSpec().missionId());
        assertEquals(1234L, request.missionLaunchSpec().seed());
        assertEquals(6200, request.missionLaunchSpec().worldW());
        assertEquals(4100, request.missionLaunchSpec().worldH());
    }

    @Test
    void multiplayerLaunchRequestCarriesResolvedSpecAndContextOutsideGameConfig() {
        MultiplayerLaunchConfig launch = MultiplayerLaunchConfig.host(46718, "192.168.1.20")
                .withMatchId("match-launch-request")
                .withMissionSettings(MultiplayerMissionChoice.HEAVY_DUEL.missionId(), 4242L, 7000, 4200);
        GameConfig config = new GameConfig(
                GameMode.CUSTOM_BATTLES,
                3600,
                2200,
                true,
                99L,
                false).withMultiplayerLaunch(launch);

        GameLaunchRequest request = GameLaunchRequest.fromGameConfig(config);

        assertTrue(request.multiplayer());
        assertEquals(GameMode.CUSTOM_BATTLES, request.gameMode());
        assertNotNull(request.multiplayerContext());
        assertEquals(launch, request.multiplayerContext().launchConfig());
        assertEquals("match-launch-request", request.multiplayerContext().matchId());
        assertEquals(MultiplayerProtocolV1.sessionNonceForMatch("match-launch-request"),
                request.multiplayerContext().sessionNonce());
        assertNotNull(request.missionLaunchSpec());
        assertEquals(MultiplayerMissionChoice.HEAVY_DUEL.missionId(), request.missionLaunchSpec().missionId());
        assertEquals(4242L, request.missionLaunchSpec().seed());
        assertEquals(7000, request.missionLaunchSpec().worldW());
        assertEquals(4200, request.missionLaunchSpec().worldH());
        assertEquals(MultiplayerRulesV1.RULES_PROFILE_ID, request.missionLaunchSpec().rulesProfileId());
    }
}
