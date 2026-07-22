import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        assertEquals(MultiplayerLobbyV1.LobbyState.READY_TO_START, lobby.state());

        long beforeRevision = lobby.revision();
        MultiplayerLobbyV1.LobbyResult result =
                lobby.hostSetHull(MultiplayerRulesV1.CLIENT_SLOT_ID, ShipRole.BATTLECRUISER);

        assertTrue(result.accepted());
        assertEquals(beforeRevision + 1, lobby.revision());
        assertFalse(lobby.hostReady());
        assertFalse(lobby.clientReady());
        assertEquals(MultiplayerLobbyV1.LobbyState.OPEN, lobby.state());
        assertEquals(ShipRole.BATTLECRUISER, lobby.currentSetup().clientSlot().hull());
    }

    @Test
    void missionConfigReplacementIncrementsRevisionOnceAndClearsReadyStates() {
        MultiplayerLobbyV1 lobby = lobby();
        lobby.setReady(MultiplayerRulesV1.HOST_SLOT_ID, true);
        lobby.setReady(MultiplayerRulesV1.CLIENT_SLOT_ID, true);
        long before = lobby.revision();

        MultiplayerLobbyV1.LobbyResult result = lobby.hostSetMissionConfig(
                new MultiplayerLobbyV1.LobbyMissionConfig(
                        CustomMissionCatalog.HEAVY_DUEL_ID,
                        9988L,
                        5200,
                        3200,
                        ShipRole.CRUISER,
                        ShipRole.BATTLECRUISER));

        assertTrue(result.accepted());
        assertEquals(before + 1, lobby.revision());
        assertEquals(CustomMissionCatalog.HEAVY_DUEL_ID, lobby.missionConfig().missionId());
        assertEquals(9988L, lobby.currentSetup().seed());
        assertEquals(5200, lobby.worldW());
        assertEquals(3200, lobby.worldH());
        assertEquals(ShipRole.CRUISER, lobby.currentSetup().hostSlot().hull());
        assertEquals(ShipRole.BATTLECRUISER, lobby.currentSetup().clientSlot().hull());
        assertFalse(lobby.hostReady());
        assertFalse(lobby.clientReady());

        MultiplayerLobbyV1.LobbyResult unchanged = lobby.hostSetMissionConfig(lobby.missionConfig());
        assertTrue(unchanged.accepted());
        assertEquals(before + 1, lobby.revision());
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
        assertEquals(MultiplayerLobbyV1.LobbyState.LOCKED, lobby.state());
        assertFalse(lobby.hostSetHull(MultiplayerRulesV1.HOST_SLOT_ID, ShipRole.BATTLESHIP).accepted());
        assertFalse(lobby.setReady(MultiplayerRulesV1.HOST_SLOT_ID, false).accepted());

        MultiplayerLobbyV1.MatchStartSync sync = lobby.startLoading();

        assertEquals(MultiplayerLobbyV1.LobbyState.LOADING, lobby.state());
        assertEquals(lobby.revision(), sync.lobbyRevision());
        assertEquals(lobby.lobbyId(), sync.identity().lobbyId());
        assertEquals(lobby.activeMatchId(), sync.identity().matchId());
        assertEquals(lobby.activeSessionNonce(), sync.identity().sessionNonce());
        assertEquals(lobby.revision(), sync.identity().lockedConfigRevision());
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
    void explicitLifecycleStatesGateJoinEditReadyAndReturn() {
        MultiplayerLobbyV1 lobby = lobby();

        assertEquals(MultiplayerLobbyV1.LobbyState.OPEN, lobby.state());
        assertTrue(lobby.join("Client").accepted());
        assertTrue(lobby.setReady(MultiplayerRulesV1.HOST_SLOT_ID, true, lobby.revision()).accepted());
        assertEquals(MultiplayerLobbyV1.LobbyState.OPEN, lobby.state());
        assertTrue(lobby.setReady(MultiplayerRulesV1.CLIENT_SLOT_ID, true, lobby.revision()).accepted());
        assertEquals(MultiplayerLobbyV1.LobbyState.READY_TO_START, lobby.state());
        assertFalse(lobby.join("Late").accepted());

        assertTrue(lobby.hostSetSeed(42L).accepted());
        assertEquals(MultiplayerLobbyV1.LobbyState.OPEN, lobby.state());
        assertFalse(lobby.hostReady());
        assertFalse(lobby.clientReady());

        lobby.setReady(MultiplayerRulesV1.HOST_SLOT_ID, true, lobby.revision());
        lobby.setReady(MultiplayerRulesV1.CLIENT_SLOT_ID, true, lobby.revision());
        assertTrue(lobby.lockForCountdown().accepted());
        assertEquals(MultiplayerLobbyV1.LobbyState.LOCKED, lobby.state());
        assertFalse(lobby.hostSetSeed(43L).accepted());
        assertFalse(lobby.setReady(MultiplayerRulesV1.HOST_SLOT_ID, false, lobby.revision()).accepted());
        assertFalse(lobby.join("Later").accepted());

        lobby.startLoading();
        assertEquals(MultiplayerLobbyV1.LobbyState.LOADING, lobby.state());
        lobby.startMatch();
        assertEquals(MultiplayerLobbyV1.LobbyState.IN_MATCH, lobby.state());
        lobby.endMatch();
        assertEquals(MultiplayerLobbyV1.LobbyState.POST_MATCH, lobby.state());
        lobby.returnToLobby();
        assertEquals(MultiplayerLobbyV1.LobbyState.OPEN, lobby.state());
        assertFalse(lobby.hostReady());
        assertFalse(lobby.clientReady());
        lobby.disconnect();
        assertEquals(MultiplayerLobbyV1.LobbyState.CLOSED, lobby.state());
    }

    @Test
    void disconnectIsAllowedFromEveryLifecycleStateAndClearsTransientMatchState() {
        for (MultiplayerLobbyV1.LobbyState target : new MultiplayerLobbyV1.LobbyState[]{
                MultiplayerLobbyV1.LobbyState.OPEN,
                MultiplayerLobbyV1.LobbyState.READY_TO_START,
                MultiplayerLobbyV1.LobbyState.LOCKED,
                MultiplayerLobbyV1.LobbyState.LOADING,
                MultiplayerLobbyV1.LobbyState.IN_MATCH,
                MultiplayerLobbyV1.LobbyState.POST_MATCH}) {
            MultiplayerLobbyV1 lobby = lobbyInState(target);

            lobby.disconnect();

            assertEquals(MultiplayerLobbyV1.LobbyState.CLOSED, lobby.state());
            assertFalse(lobby.locked(), target.name());
            assertFalse(lobby.clientConnected(), target.name());
            assertFalse(lobby.hostReady(), target.name());
            assertFalse(lobby.clientReady(), target.name());
            assertEquals("", lobby.activeMatchId(), target.name());
            assertEquals("", lobby.activeSessionNonce(), target.name());
        }
    }

    @Test
    void publicLobbyStateApiIsSerializedThroughTheLobbyMonitor() {
        for (Method method : MultiplayerLobbyV1.class.getDeclaredMethods()) {
            int modifiers = method.getModifiers();
            if (!Modifier.isPublic(modifiers) || Modifier.isStatic(modifiers) || method.isSynthetic()) continue;

            assertTrue(Modifier.isSynchronized(modifiers), method.getName());
        }
    }

    @Test
    void clientJoinAsPlayerSlotChangeClearsExistingReadiness() {
        MultiplayerLobbyV1 lobby = lobby();
        assertTrue(lobby.setReady(MultiplayerRulesV1.HOST_SLOT_ID, true, lobby.revision()).accepted());
        long before = lobby.revision();

        MultiplayerLobbyV1.LobbyResult result = lobby.join("Grace");

        assertTrue(result.accepted());
        assertEquals(before + 1, lobby.revision());
        assertFalse(lobby.hostReady());
        assertFalse(lobby.clientReady());
        assertEquals(-1L, lobby.hostReadyRevision());
        assertEquals(-1L, lobby.clientReadyRevision());
    }

    @Test
    void clientDisconnectAsPlayerSlotChangeClearsAllReadiness() {
        MultiplayerLobbyV1 lobby = lobby();
        assertTrue(lobby.join("Grace").accepted());
        assertTrue(lobby.setReady(MultiplayerRulesV1.HOST_SLOT_ID, true, lobby.revision()).accepted());
        assertTrue(lobby.setReady(MultiplayerRulesV1.CLIENT_SLOT_ID, true, lobby.revision()).accepted());
        long before = lobby.revision();

        MultiplayerLobbyV1.LobbyResult result = lobby.clientDisconnected("client left");

        assertTrue(result.accepted());
        assertEquals(before + 1, lobby.revision());
        assertFalse(lobby.hostReady());
        assertFalse(lobby.clientReady());
        assertEquals(-1L, lobby.hostReadyRevision());
        assertEquals(-1L, lobby.clientReadyRevision());
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
    void clientDisconnectClearsClientSlotReadinessAndConnectionState() {
        MultiplayerLobbyV1 lobby = lobby();
        assertTrue(lobby.join("Grace").accepted());
        assertTrue(lobby.clientConnected());
        lobby.setReady(MultiplayerRulesV1.HOST_SLOT_ID, true, lobby.revision());
        lobby.setReady(MultiplayerRulesV1.CLIENT_SLOT_ID, true, lobby.revision());
        long before = lobby.revision();

        MultiplayerLobbyV1.LobbyResult result = lobby.clientDisconnected("client left");

        assertTrue(result.accepted());
        assertEquals(before + 1, lobby.revision());
        assertFalse(lobby.clientConnected());
        assertFalse(lobby.hostReady());
        assertFalse(lobby.clientReady());
        assertEquals(-1L, lobby.hostReadyRevision());
        assertEquals(-1L, lobby.clientReadyRevision());
        assertEquals(MultiplayerLobbyV1.LobbyState.OPEN, lobby.state());
        assertTrue(logContains(lobby, "client_left"));
    }

    @Test
    void readyMessagesMustReferenceCurrentLobbyRevision() {
        MultiplayerLobbyV1 lobby = lobby();
        long staleRevision = lobby.revision();
        lobby.hostSetSeed(9001L);

        MultiplayerLobbyV1.LobbyResult stale =
                lobby.setReady(MultiplayerRulesV1.CLIENT_SLOT_ID, true, staleRevision);
        MultiplayerLobbyV1.LobbyResult current =
                lobby.setReady(MultiplayerRulesV1.CLIENT_SLOT_ID, true, lobby.revision());

        assertFalse(stale.accepted());
        assertTrue(stale.reason().contains("settings changed"));
        assertTrue(current.accepted());
        assertEquals(lobby.revision(), lobby.clientReadyRevision());
    }

    @Test
    void worldSizeChangesIncrementRevisionAndClearReady() {
        MultiplayerLobbyV1 lobby = lobby();
        lobby.setReady(MultiplayerRulesV1.HOST_SLOT_ID, true);
        lobby.setReady(MultiplayerRulesV1.CLIENT_SLOT_ID, true);
        long before = lobby.revision();

        MultiplayerLobbyV1.LobbyResult result = lobby.hostSetWorldSize(5000, 4200);

        assertTrue(result.accepted());
        assertEquals(before + 1, lobby.revision());
        assertEquals(5000, lobby.worldW());
        assertEquals(4200, lobby.worldH());
        assertFalse(lobby.hostReady());
        assertFalse(lobby.clientReady());
    }

    @Test
    void missionRevisionAndRulesProfileChangesClearReadinessButCosmeticTextDoesNot() {
        MultiplayerLobbyV1 lobby = lobby();
        lobby.setReady(MultiplayerRulesV1.HOST_SLOT_ID, true, lobby.revision());
        lobby.setReady(MultiplayerRulesV1.CLIENT_SLOT_ID, true, lobby.revision());
        long beforeRevision = lobby.revision();

        MultiplayerLobbyV1.LobbyResult missionRevision =
                lobby.hostSetMissionRevision(lobby.missionConfig().missionRevision() + 1);

        assertTrue(missionRevision.accepted());
        assertEquals(beforeRevision + 1, lobby.revision());
        assertFalse(lobby.hostReady());
        assertFalse(lobby.clientReady());

        lobby.setReady(MultiplayerRulesV1.HOST_SLOT_ID, true, lobby.revision());
        lobby.setReady(MultiplayerRulesV1.CLIENT_SLOT_ID, true, lobby.revision());
        long beforeRulesProfile = lobby.revision();

        MultiplayerLobbyV1.LobbyResult rulesProfile =
                lobby.hostSetRulesProfileId(MultiplayerRulesCustomMission.RULES_PROFILE_ID);

        assertTrue(rulesProfile.accepted());
        assertEquals(beforeRulesProfile + 1, lobby.revision());
        assertEquals(MultiplayerRulesCustomMission.RULES_PROFILE_ID,
                lobby.missionConfig().rulesProfileId());
        assertFalse(lobby.hostReady());
        assertFalse(lobby.clientReady());

        lobby.setReady(MultiplayerRulesV1.HOST_SLOT_ID, true, lobby.revision());
        lobby.setReady(MultiplayerRulesV1.CLIENT_SLOT_ID, true, lobby.revision());
        long beforeCosmetic = lobby.revision();

        MultiplayerLobbyV1.LobbyResult cosmetic =
                lobby.hostSetCosmeticDisplayText("Friendly lobby label");

        assertTrue(cosmetic.accepted());
        assertEquals(beforeCosmetic, lobby.revision());
        assertTrue(lobby.hostReady());
        assertTrue(lobby.clientReady());
        assertEquals("Friendly lobby label", lobby.missionConfig().cosmeticDisplayText());
    }

    @Test
    void assignedHullChangesClearReadinessForEitherPlayerSlot() {
        MultiplayerLobbyV1 lobby = lobby();
        lobby.setReady(MultiplayerRulesV1.HOST_SLOT_ID, true, lobby.revision());
        lobby.setReady(MultiplayerRulesV1.CLIENT_SLOT_ID, true, lobby.revision());

        MultiplayerLobbyV1.LobbyResult hostHull =
                lobby.hostSetHull(MultiplayerRulesV1.HOST_SLOT_ID, ShipRole.BATTLESHIP);

        assertTrue(hostHull.accepted());
        assertFalse(lobby.hostReady());
        assertFalse(lobby.clientReady());
        assertEquals(MultiplayerLobbyV1.LobbyState.OPEN, lobby.state());

        lobby.setReady(MultiplayerRulesV1.HOST_SLOT_ID, true, lobby.revision());
        lobby.setReady(MultiplayerRulesV1.CLIENT_SLOT_ID, true, lobby.revision());

        MultiplayerLobbyV1.LobbyResult clientHull =
                lobby.hostSetHull(MultiplayerRulesV1.CLIENT_SLOT_ID, ShipRole.BATTLECRUISER);

        assertTrue(clientHull.accepted());
        assertFalse(lobby.hostReady());
        assertFalse(lobby.clientReady());
        assertEquals(MultiplayerLobbyV1.LobbyState.OPEN, lobby.state());
    }

    @Test
    void lockCapturesExactDuelConfigRevisionAndWorldSize() {
        MultiplayerLobbyV1 lobby = lobby();
        lobby.hostSetWorldSize(5000, 4200);
        lobby.hostSetSeed(123456L);
        lobby.setReady(MultiplayerRulesV1.HOST_SLOT_ID, true, lobby.revision());
        lobby.setReady(MultiplayerRulesV1.CLIENT_SLOT_ID, true, lobby.revision());

        MultiplayerLobbyV1.LobbyResult result = lobby.lockForCountdown();

        assertTrue(result.accepted());
        assertTrue(lobby.locked());
        assertNotNull(lobby.lockedDuelConfig());
        assertEquals(lobby.revision(), lobby.lockedDuelConfig().lobbyRevision());
        assertEquals(5000, lobby.lockedDuelConfig().worldW());
        assertEquals(4200, lobby.lockedDuelConfig().worldH());
        assertEquals(123456L, lobby.lockedDuelConfig().setup().seed());
        assertEquals(lobby.missionConfig(), lobby.lockedDuelConfig().missionConfig());
    }

    @Test
    void lockingAssignsFreshMatchIdentityForEachMatch() {
        MultiplayerLobbyV1 lobby = lobby();
        lobby.setReady(MultiplayerRulesV1.HOST_SLOT_ID, true, lobby.revision());
        lobby.setReady(MultiplayerRulesV1.CLIENT_SLOT_ID, true, lobby.revision());
        assertTrue(lobby.lockForCountdown().accepted());
        String firstMatchId = lobby.lockedDuelConfig().matchId();
        String firstNonce = lobby.lockedDuelConfig().sessionNonce();

        assertTrue(firstMatchId.startsWith(lobby.lobbyId()));
        assertEquals(firstNonce, MultiplayerProtocolV1.sessionNonceForMatch(firstMatchId));

        lobby.endMatch();
        lobby.returnToLobby();
        lobby.setReady(MultiplayerRulesV1.HOST_SLOT_ID, true, lobby.revision());
        lobby.setReady(MultiplayerRulesV1.CLIENT_SLOT_ID, true, lobby.revision());
        assertTrue(lobby.lockForCountdown().accepted());

        assertFalse(firstMatchId.equals(lobby.lockedDuelConfig().matchId()));
        assertFalse(firstNonce.equals(lobby.lockedDuelConfig().sessionNonce()));
    }

    @Test
    void returnToLobbyAfterMatchClearsReadinessAndUnlocksMissionEditing() {
        MultiplayerLobbyV1 lobby = lobby();
        lobby.join("Client");
        lobby.setReady(MultiplayerRulesV1.HOST_SLOT_ID, true, lobby.revision());
        lobby.setReady(MultiplayerRulesV1.CLIENT_SLOT_ID, true, lobby.revision());
        assertTrue(lobby.lockForCountdown().accepted());
        lobby.startLoading();
        lobby.startMatch();
        lobby.endMatch();

        lobby.returnToLobby();
        MultiplayerLobbyV1.LobbyResult edit = lobby.hostSetSeed(55L);

        assertEquals(MultiplayerLobbyV1.LobbyState.OPEN, lobby.state());
        assertFalse(lobby.hostReady());
        assertFalse(lobby.clientReady());
        assertFalse(lobby.locked());
        assertTrue(edit.accepted(), edit.reason());
        assertEquals(55L, lobby.currentSetup().seed());
    }

    @Test
    void loadingAndMatchStartRequireLockedCountdown() {
        MultiplayerLobbyV1 lobby = lobby();

        assertThrows(IllegalStateException.class, lobby::startLoading);
        assertThrows(IllegalStateException.class, lobby::startMatch);
    }

    @Test
    void observabilityLogRecordsLobbyMatchAndCleanupEvents() {
        MultiplayerLobbyV1 lobby = lobby();
        lobby.join("Client");
        lobby.recordCompatibilityResult(true, "Compatible");
        lobby.recordCompatibilityResult(false, "Protocol mismatch");
        lobby.hostSetSeed(444L);
        lobby.setReady(MultiplayerRulesV1.HOST_SLOT_ID, true, lobby.revision());
        lobby.setReady(MultiplayerRulesV1.CLIENT_SLOT_ID, true, lobby.revision());
        assertTrue(lobby.lockForCountdown().accepted());
        lobby.startLoading();
        lobby.startMatch();
        lobby.endMatch();
        lobby.returnToLobby();
        lobby.disconnect();

        assertTrue(logContains(lobby, "lobby created"));
        assertTrue(logContains(lobby, "client joined"));
        assertTrue(logContains(lobby, "compatibility accepted"));
        assertTrue(logContains(lobby, "compatibility rejected"));
        assertTrue(logContains(lobby, "lobby revision changed"));
        assertTrue(logContains(lobby, "player ready"));
        assertTrue(logContains(lobby, "mission locked"));
        assertTrue(logContains(lobby, "match specification hash"));
        assertTrue(logContains(lobby, "player slot assignment"));
        assertTrue(logContains(lobby, "match started"));
        assertTrue(logContains(lobby, "match result"));
        assertTrue(logContains(lobby, "cleanup completed"));
        assertTrue(logContains(lobby, "disconnect reason"));
    }

    private static MultiplayerLobbyV1 lobby() {
        return new MultiplayerLobbyV1("Host", "Client", ShipRole.FRIGATE, ShipRole.CRUISER);
    }

    private static MultiplayerLobbyV1 lobbyInState(MultiplayerLobbyV1.LobbyState target) {
        MultiplayerLobbyV1 lobby = lobby();
        lobby.join("Client");
        if (target == MultiplayerLobbyV1.LobbyState.OPEN) return lobby;
        lobby.setReady(MultiplayerRulesV1.HOST_SLOT_ID, true, lobby.revision());
        lobby.setReady(MultiplayerRulesV1.CLIENT_SLOT_ID, true, lobby.revision());
        if (target == MultiplayerLobbyV1.LobbyState.READY_TO_START) return lobby;
        lobby.lockForCountdown();
        if (target == MultiplayerLobbyV1.LobbyState.LOCKED) return lobby;
        lobby.startLoading();
        if (target == MultiplayerLobbyV1.LobbyState.LOADING) return lobby;
        lobby.startMatch();
        if (target == MultiplayerLobbyV1.LobbyState.IN_MATCH) return lobby;
        lobby.endMatch();
        return lobby;
    }

    private static boolean logContains(MultiplayerLobbyV1 lobby, String text) {
        return lobby.observabilityLog().stream().anyMatch(line -> line.contains(text));
    }
}
