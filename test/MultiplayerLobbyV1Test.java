import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerLobbyV1Test {

    @Test
    void menuEntryModelDefinesHostJoinAddressNamesTeamsAndHullSelection() {
        MultiplayerLobbyV1.MenuEntryModel model = MultiplayerLobbyV1.menuEntryModel();

        assertEquals(MultiplayerRulesV1.entryPointEnabled(), model.enabled());
        assertEquals("Host Battle", model.hostBattleLabel());
        assertEquals("Join Battle", model.joinBattleLabel());
        assertTrue(model.directAddressPlaceholder().contains(":"));
        assertEquals("Host", model.defaultHostName());
        assertEquals("Client", model.defaultClientName());
        assertEquals(Faction.ALLY, model.hostTeam());
        assertEquals(Faction.ENEMY, model.clientTeam());
        assertTrue(model.hullSelectionEnabled());
    }

    @Test
    void hostOwnedLobbyCreatesOpposingTeamV1Setup() {
        MultiplayerLobbyV1 lobby = lobby();
        lobby.hostSetSeed(12345L);
        MultiplayerRulesV1.BattleSetup setup = lobby.currentSetup();

        assertEquals(12345L, setup.seed());
        assertEquals(MultiplayerRulesV1.HOST_SLOT_ID, setup.hostSlot().slotId());
        assertEquals(MultiplayerRulesV1.CLIENT_SLOT_ID, setup.clientSlot().slotId());
        assertEquals(Faction.ALLY, setup.hostSlot().team());
        assertEquals(Faction.ENEMY, setup.clientSlot().team());
        assertEquals(ShipRole.FRIGATE, setup.hostSlot().hull());
        assertEquals(ShipRole.CRUISER, setup.clientSlot().hull());
        assertTrue(MultiplayerRulesV1.validate(setup).accepted());
    }

    @Test
    void hostConfigChangesIncrementRevisionAndClearReadyStates() {
        MultiplayerLobbyV1 lobby = lobby();
        assertTrue(lobby.setReady(MultiplayerRulesV1.HOST_SLOT_ID, true).accepted());
        assertTrue(lobby.setReady(MultiplayerRulesV1.CLIENT_SLOT_ID, true).accepted());
        assertEquals(MultiplayerLobbyV1.LobbyState.READY, lobby.state());

        long beforeRevision = lobby.revision();
        MultiplayerLobbyV1.LobbyResult result =
                lobby.hostSetHull(MultiplayerRulesV1.CLIENT_SLOT_ID, ShipRole.BATTLECRUISER);

        assertTrue(result.accepted());
        assertEquals(beforeRevision + 1, lobby.revision());
        assertFalse(lobby.hostReady());
        assertFalse(lobby.clientReady());
        assertEquals(MultiplayerLobbyV1.LobbyState.IN_LOBBY, lobby.state());
        assertEquals(ShipRole.BATTLECRUISER, lobby.currentSetup().clientSlot().hull());
    }

    @Test
    void clientCannotChangeHostAuthoritativeLobbySettings() {
        MultiplayerLobbyV1 lobby = lobby();

        MultiplayerLobbyV1.LobbyResult result = lobby.clientSetHull(ShipRole.BATTLESHIP);

        assertFalse(result.accepted());
        assertTrue(result.reason().contains("host-authoritative"));
        assertEquals(ShipRole.CRUISER, lobby.currentSetup().clientSlot().hull());
    }

    @Test
    void readyCountdownLoadingAndMatchStartSynchronizationLockConfiguration() {
        MultiplayerLobbyV1 lobby = lobby();
        lobby.hostSetSeed(777L);
        assertTrue(lobby.setReady(MultiplayerRulesV1.HOST_SLOT_ID, true).accepted());
        assertTrue(lobby.setReady(MultiplayerRulesV1.CLIENT_SLOT_ID, true).accepted());

        MultiplayerLobbyV1.LobbyResult lock = lobby.lockForCountdown();

        assertTrue(lock.accepted());
        assertTrue(lobby.locked());
        assertEquals(MultiplayerLobbyV1.LobbyState.COUNTDOWN, lobby.state());
        assertFalse(lobby.hostSetHull(MultiplayerRulesV1.HOST_SLOT_ID, ShipRole.BATTLESHIP).accepted());
        assertFalse(lobby.setReady(MultiplayerRulesV1.HOST_SLOT_ID, false).accepted());

        MultiplayerLobbyV1.MatchStartSync sync = lobby.startLoading();

        assertEquals(MultiplayerLobbyV1.LobbyState.LOADING, lobby.state());
        assertEquals(lobby.revision(), sync.lobbyRevision());
        assertEquals(777L, sync.setup().seed());
        assertEquals(ShipRole.FRIGATE, sync.setup().hostSlot().hull());
        assertEquals(MultiplayerProtocolV1.localFingerprint(), sync.fingerprint());

        MultiplayerRulesV1.BattleSetup started = lobby.startMatch();

        assertEquals(MultiplayerLobbyV1.LobbyState.IN_MATCH, lobby.state());
        assertEquals(sync.setup(), started);
    }

    @Test
    void joinRequestsAreRejectedAfterMatchIsLockedOrActive() {
        MultiplayerLobbyV1 lobby = lobby();
        lobby.setReady(MultiplayerRulesV1.HOST_SLOT_ID, true);
        lobby.setReady(MultiplayerRulesV1.CLIENT_SLOT_ID, true);
        lobby.lockForCountdown();

        MultiplayerLobbyV1.LobbyResult lockedJoin = lobby.join("Late Client");

        assertFalse(lockedJoin.accepted());
        assertEquals("Match already in progress", lockedJoin.reason());

        lobby.startLoading();
        lobby.startMatch();

        MultiplayerLobbyV1.LobbyResult activeJoin = lobby.join("Later Client");

        assertFalse(activeJoin.accepted());
        assertEquals("Match already in progress", activeJoin.reason());
    }

    @Test
    void directConnectAddressIsParsedAndMaterialChangesClearReady() {
        MultiplayerLobbyV1 lobby = lobby();
        lobby.setReady(MultiplayerRulesV1.HOST_SLOT_ID, true);
        lobby.setReady(MultiplayerRulesV1.CLIENT_SLOT_ID, true);

        MultiplayerLobbyV1.LobbyResult result = lobby.setDirectAddress("192.168.1.40:48000");

        assertTrue(result.accepted());
        assertEquals("192.168.1.40:48000", lobby.directAddress().toString());
        assertFalse(lobby.hostReady());
        assertFalse(lobby.clientReady());
    }

    @Test
    void loadingAndMatchStartRequireLockedCountdown() {
        MultiplayerLobbyV1 lobby = lobby();

        assertThrows(IllegalStateException.class, lobby::startLoading);
        assertThrows(IllegalStateException.class, lobby::startMatch);
    }

    private static MultiplayerLobbyV1 lobby() {
        return new MultiplayerLobbyV1("Host", "Client", ShipRole.FRIGATE, ShipRole.CRUISER);
    }
}
