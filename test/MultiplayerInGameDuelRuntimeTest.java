import app.config.MultiplayerLaunchConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerInGameDuelRuntimeTest {

    @Test
    void hostRuntimeRejectsSimulationMutationFromNonOwnerThread() throws Exception {
        GameContext ctx = MultiplayerPlayableDuelContextFactory.create(
                MultiplayerLaunchConfig.host(46717, "127.0.0.1"));
        MultiplayerBattleRuntime runtime = ctx.multiplayerBattleRuntime;
        Thread owner = Thread.currentThread();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread remoteThread = new Thread(() -> {
            try {
                runtime.planFrame(GameContext.DT);
            } catch (Throwable ex) {
                failure.set(ex);
            }
        }, "mp-test-remote-mutation");
        remoteThread.start();
        remoteThread.join(1_000);

        assertEquals(owner, runtime.threadGuard().owner());
        assertTrue(failure.get() instanceof IllegalStateException);
        assertTrue(failure.get().getMessage().contains("authoritative simulation thread"));
    }

    @Test
    void hostAppliesAcceptedRemoteInputToClientOwnedShipOnly() {
        GameContext ctx = MultiplayerPlayableDuelContextFactory.create(
                MultiplayerLaunchConfig.host(46717, "127.0.0.1"));
        Ship remote = ctx.ships.stream()
                .filter(ship -> ship != ctx.player)
                .findFirst()
                .orElseThrow();
        double startAngle = remote.angle;
        double startVx = remote.vx;

        MultiplayerCommandGate.PlayerInputFrame frame = new MultiplayerCommandGate.PlayerInputFrame(
                MultiplayerRulesV1.CLIENT_SLOT_ID,
                remote.id,
                1L,
                1L,
                1.0f,
                1.0f,
                0.0,
                false,
                false).withIdentity(ctx.multiplayerMatchId, ctx.multiplayerSessionNonce,
                MultiplayerProtocolV1.playerIdForSlot(MultiplayerRulesV1.CLIENT_SLOT_ID));

        MultiplayerCommandGate.CommandResult result =
                ctx.multiplayerBattleRuntime.acceptInput(frame, 1L);
        assertTrue(result.accepted(), result.reason());

        MultiplayerInGameDuelInputApplier.applyAcceptedInput(ctx, frame, GameContext.DT);
        MultiplayerBattleSnapshot snapshot = ctx.multiplayerBattleRuntime.snapshot(2L);

        assertNotEquals(startAngle, remote.angle);
        assertNotEquals(startVx, remote.vx);
        assertEquals(0.0, ctx.player.vx);
        assertEquals(1L, snapshot.lastProcessedInputSequence());
    }

    @Test
    void hostAuthoritativeDirectFireAppliesDamageAndDeath() {
        GameContext ctx = MultiplayerPlayableDuelContextFactory.create(
                MultiplayerLaunchConfig.host(46717, "127.0.0.1"));
        Ship remote = ctx.ships.stream()
                .filter(ship -> ship != ctx.player)
                .findFirst()
                .orElseThrow();
        remote.x = ctx.player.x + 120.0;
        remote.y = ctx.player.y;
        int startingHp = ctx.player.hp;

        MultiplayerCommandGate.PlayerInputFrame frame = new MultiplayerCommandGate.PlayerInputFrame(
                MultiplayerRulesV1.CLIENT_SLOT_ID,
                remote.id,
                1L,
                1L,
                0.0f,
                0.0f,
                0.0,
                true,
                false).withIdentity(ctx.multiplayerMatchId, ctx.multiplayerSessionNonce,
                MultiplayerProtocolV1.playerIdForSlot(MultiplayerRulesV1.CLIENT_SLOT_ID));

        MultiplayerInGameDuelInputApplier.applyAcceptedInput(ctx, frame, GameContext.DT);

        assertTrue(ctx.player.hp < startingHp || !ctx.player.alive);
        assertTrue(!ctx.player.alive);
    }

    @Test
    void hostAndClientSlotsCanFightThroughAuthoritativeInputFrames() {
        GameContext ctx = MultiplayerPlayableDuelContextFactory.create(
                MultiplayerLaunchConfig.host(46717, "127.0.0.1"));
        Ship clientShip = ctx.ships.stream()
                .filter(ship -> ship != ctx.player)
                .findFirst()
                .orElseThrow();
        clientShip.x = ctx.player.x + 120.0;
        clientShip.y = ctx.player.y;

        MultiplayerCommandGate.PlayerInputFrame hostFire = new MultiplayerCommandGate.PlayerInputFrame(
                MultiplayerRulesV1.HOST_SLOT_ID,
                ctx.player.id,
                1L,
                1L,
                0.0f,
                0.0f,
                0.0,
                true,
                false).withIdentity(ctx.multiplayerMatchId, ctx.multiplayerSessionNonce,
                MultiplayerProtocolV1.playerIdForSlot(MultiplayerRulesV1.HOST_SLOT_ID));
        assertTrue(ctx.multiplayerBattleRuntime.acceptInput(hostFire, 1L).accepted());

        MultiplayerInGameDuelInputApplier.applyAcceptedInput(ctx, hostFire, GameContext.DT);
        assertTrue(!clientShip.alive);

        clientShip.hp = clientShip.hpMax;
        clientShip.alive = true;
        clientShip.dying = false;
        ctx.player.hp = ctx.player.hpMax;
        ctx.player.alive = true;
        ctx.player.dying = false;

        MultiplayerCommandGate.PlayerInputFrame clientFire = new MultiplayerCommandGate.PlayerInputFrame(
                MultiplayerRulesV1.CLIENT_SLOT_ID,
                clientShip.id,
                2L,
                2L,
                0.0f,
                0.0f,
                0.0,
                true,
                false).withIdentity(ctx.multiplayerMatchId, ctx.multiplayerSessionNonce,
                MultiplayerProtocolV1.playerIdForSlot(MultiplayerRulesV1.CLIENT_SLOT_ID));
        assertTrue(ctx.multiplayerBattleRuntime.acceptInput(clientFire, 2L).accepted());

        MultiplayerInGameDuelInputApplier.applyAcceptedInput(ctx, clientFire, GameContext.DT);
        assertTrue(!ctx.player.alive);
    }

    @Test
    void clientPresentationCannotApplyDirectFireDamageLocally() {
        GameContext ctx = MultiplayerPlayableDuelContextFactory.create(
                MultiplayerLaunchConfig.client("127.0.0.1:46717", ""));
        Ship target = ctx.ships.stream()
                .filter(ship -> ship != ctx.player && ship.faction != null
                        && ctx.player.faction != null && !ctx.player.faction.isFriendlyTo(ship.faction))
                .findFirst()
                .orElseThrow();
        target.x = ctx.player.x + 120.0;
        target.y = ctx.player.y;
        int startingHp = target.hp;
        double startingAngle = ctx.player.angle;

        MultiplayerCommandGate.PlayerInputFrame frame = new MultiplayerCommandGate.PlayerInputFrame(
                MultiplayerRulesV1.CLIENT_SLOT_ID,
                ctx.player.id,
                1L,
                1L,
                1.0f,
                1.0f,
                0.0,
                true,
                false).withIdentity(ctx.multiplayerMatchId, ctx.multiplayerSessionNonce,
                MultiplayerProtocolV1.playerIdForSlot(MultiplayerRulesV1.CLIENT_SLOT_ID));

        MultiplayerInGameDuelInputApplier.applyAcceptedInput(ctx, frame, GameContext.DT);

        assertEquals(startingHp, target.hp);
        assertEquals(startingAngle, ctx.player.angle);
    }

    @Test
    void slotOwnershipBindsSpawnedEntityIdsEvenWhenDisplayNamesMatch() {
        MultiplayerRulesV1.BattleSetup setup = new MultiplayerRulesV1.BattleSetup(
                99L,
                MultiplayerRulesV1.DEFAULT_ARENA_ID,
                new MultiplayerRulesV1.PlayerSlot(
                        MultiplayerRulesV1.HOST_SLOT_ID, Faction.ALLY, ShipRole.FRIGATE, "Pilot"),
                new MultiplayerRulesV1.PlayerSlot(
                        MultiplayerRulesV1.CLIENT_SLOT_ID, Faction.ENEMY, ShipRole.FRIGATE, "Pilot"),
                MultiplayerRulesV1.VictoryRule.ELIMINATION,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false);

        MultiplayerBattleRuntime runtime = MultiplayerBattleRuntime.createAuthoritative(setup, false);
        int hostShipId = runtime.slots().get(MultiplayerRulesV1.HOST_SLOT_ID).controlledShipId;
        int clientShipId = runtime.slots().get(MultiplayerRulesV1.CLIENT_SLOT_ID).controlledShipId;

        assertNotEquals(hostShipId, clientShipId);
        assertTrue(hostShipId > 0);
        assertTrue(clientShipId > 0);
    }

    @Test
    void aiSupportProfileSpawnsHostOwnedSupportShipsAndReplicatesWithinBudget() {
        MultiplayerRulesV1.BattleSetup setup = aiSupportSetup();

        MultiplayerBattleRuntime runtime = MultiplayerBattleRuntime.createAuthoritative(setup, false);
        GameContext ctx = runtime.context();
        MultiplayerBattleSnapshot snapshot = runtime.snapshot(2L);

        assertEquals(4, ctx.ships.size());
        assertEquals(2, runtime.slots().size());
        assertEquals(4, snapshot.ships().size());
        assertTrue(snapshot.ships().size() <= MultiplayerReplicationV1.MAX_REPLICATED_SHIPS_V1);
        assertEquals(1, countShips(ctx, Faction.ALLY, ShipRole.CIWS_CORVETTE));
        assertEquals(1, countShips(ctx, Faction.ENEMY, ShipRole.CIWS_CORVETTE));
        for (Ship ship : ctx.ships) {
            if (ship.role == ShipRole.CIWS_CORVETTE) {
                assertFalse(ctx.multiplayerPlayerControlledShipIds.contains(ship.id));
            }
        }
    }

    @Test
    void supportedV1EliminationMatchesPublishNoActiveObjectiveSummary() {
        MissionLaunchSpec spec = CustomMissionCatalog.resolveV1Duel(
                17L, 3600, 2200, ShipRole.FRIGATE, ShipRole.FRIGATE);
        MultiplayerRulesV1.BattleSetup setup =
                MultiplayerHostLaunchAdapter.toBattleSetup(spec, "Host", "Client");
        MultiplayerBattleRuntime runtime =
                MultiplayerBattleRuntime.createAuthoritative(setup, false, MultiplayerRulesV1.HOST_SLOT_ID,
                        spec.worldW(), spec.worldH());

        MultiplayerBattleSnapshot snapshot = runtime.snapshot(1L);

        assertEquals("elimination", spec.objectiveType());
        assertEquals(MultiplayerBattleSnapshot.ObjectiveSummarySnapshot.none(), snapshot.objectiveSummary());
    }

    @Test
    void aiSupportShipsDoNotPreventPlayerEliminationVictory() {
        MultiplayerBattleRuntime runtime = MultiplayerBattleRuntime.createAuthoritative(aiSupportSetup(), false);
        GameContext ctx = runtime.context();
        int clientShipId = runtime.slots().get(MultiplayerRulesV1.CLIENT_SLOT_ID).controlledShipId;
        Ship client = ctx.ships.stream()
                .filter(ship -> ship.id == clientShipId)
                .findFirst()
                .orElseThrow();
        client.hp = 0;
        client.alive = false;

        MultiplayerDuelVictoryEvaluator.MatchResult result =
                MultiplayerDuelVictoryEvaluator.evaluate(ctx, runtime.slots());

        assertTrue(result.ended());
        assertEquals(Faction.ALLY.teamId(), result.winningTeamId());
        assertEquals("Elimination victory", result.reason());
    }


    @Test
    void hostRejectsClientInputForHostShip() {
        GameContext ctx = MultiplayerPlayableDuelContextFactory.create(
                MultiplayerLaunchConfig.host(46717, "127.0.0.1"));
        MultiplayerCommandGate.PlayerInputFrame stolenShipFrame = new MultiplayerCommandGate.PlayerInputFrame(
                MultiplayerRulesV1.CLIENT_SLOT_ID,
                ctx.player.id,
                1L,
                1L,
                1.0f,
                0.0f,
                0.0,
                false,
                false).withIdentity(ctx.multiplayerMatchId, ctx.multiplayerSessionNonce,
                MultiplayerProtocolV1.playerIdForSlot(MultiplayerRulesV1.CLIENT_SLOT_ID));

        MultiplayerCommandGate.CommandResult result =
                ctx.multiplayerBattleRuntime.acceptInput(stolenShipFrame, 1L);

        assertTrue(!result.accepted());
        assertEquals("Player does not own this ship", result.reason());
    }

    @Test
    void hostRejectsInputForDestroyedPlayerShip() {
        GameContext ctx = MultiplayerPlayableDuelContextFactory.create(
                MultiplayerLaunchConfig.host(46717, "127.0.0.1"));
        Ship remote = ctx.ships.stream()
                .filter(ship -> ship != ctx.player)
                .findFirst()
                .orElseThrow();
        remote.hp = 0;
        remote.alive = false;
        remote.dying = false;
        MultiplayerCommandGate.PlayerInputFrame destroyedShipFrame = new MultiplayerCommandGate.PlayerInputFrame(
                MultiplayerRulesV1.CLIENT_SLOT_ID,
                remote.id,
                1L,
                1L,
                1.0f,
                0.0f,
                0.0,
                false,
                false).withIdentity(ctx.multiplayerMatchId, ctx.multiplayerSessionNonce,
                MultiplayerProtocolV1.playerIdForSlot(MultiplayerRulesV1.CLIENT_SLOT_ID));

        MultiplayerCommandGate.CommandResult result =
                ctx.multiplayerBattleRuntime.acceptInput(destroyedShipFrame, 1L);

        assertTrue(!result.accepted());
        assertEquals("Player ship is destroyed", result.reason());
    }

    @Test
    void destroyedClientPresentationPlayerCanOnlySpectateLocally() {
        GameContext ctx = MultiplayerPlayableDuelContextFactory.create(
                MultiplayerLaunchConfig.client("127.0.0.1:46717", ""));
        ctx.multiplayerInGameSession = MultiplayerInGameDuelSession.connectedForTests("Connected");
        ctx.player.hp = 0;
        ctx.player.alive = false;
        ctx.cameraPanRight = true;
        double startOffset = ctx.cameraOffsetX;

        CameraSystem.updateManualPan(ctx, 1.0);

        assertFalse(GameSimulationRuntime.clientPresentationInputAllowed(ctx));
        assertTrue(ctx.cameraOffsetX > startOffset);
        assertEquals(GameState.RUNNING, ctx.state);
    }

    @Test
    void hostRejectsInputAfterMatchEnd() {
        GameContext ctx = MultiplayerPlayableDuelContextFactory.create(
                MultiplayerLaunchConfig.host(46717, "127.0.0.1"));
        Ship remote = ctx.ships.stream()
                .filter(ship -> ship != ctx.player)
                .findFirst()
                .orElseThrow();
        ctx.gameOver = true;
        ctx.state = GameState.GAME_OVER;
        MultiplayerCommandGate.PlayerInputFrame lateFrame = new MultiplayerCommandGate.PlayerInputFrame(
                MultiplayerRulesV1.CLIENT_SLOT_ID,
                remote.id,
                1L,
                1L,
                1.0f,
                0.0f,
                0.0,
                false,
                false).withIdentity(ctx.multiplayerMatchId, ctx.multiplayerSessionNonce,
                MultiplayerProtocolV1.playerIdForSlot(MultiplayerRulesV1.CLIENT_SLOT_ID));

        MultiplayerCommandGate.CommandResult result =
                ctx.multiplayerBattleRuntime.acceptInput(lateFrame, 1L);

        assertTrue(!result.accepted());
        assertEquals("Match is over", result.reason());
    }

    @Test
    void clientSnapshotMapperUsesHostNetworkIdsForPresentationState() {
        GameContext ctx = MultiplayerPlayableDuelContextFactory.create(
                MultiplayerLaunchConfig.client("127.0.0.1:46717", ""));
        int localPlayerId = ctx.player.id;

        MultiplayerBattleSnapshot snapshot = new MultiplayerBattleSnapshot(
                12L,
                List.of(
                        new MultiplayerBattleSnapshot.ShipSnapshot(
                                900,
                                ShipRole.FRIGATE,
                                Faction.ALLY,
                                100.0,
                                200.0,
                                1.0,
                                2.0,
                                0.25,
                                80,
                                10.0,
                                true),
                        new MultiplayerBattleSnapshot.ShipSnapshot(
                                901,
                                ShipRole.FRIGATE,
                                Faction.ENEMY,
                                3100.0,
                                1400.0,
                                -1.0,
                                -2.0,
                                Math.PI,
                                55,
                                5.0,
                                true)),
                List.of(
                        new MultiplayerBattleSnapshot.SlotSnapshot(
                                MultiplayerRulesV1.HOST_SLOT_ID,
                                Faction.ALLY.teamId(),
                                900,
                                MultiplayerRulesV1.PlayerRole.DIRECT_SHIP,
                                MultiplayerRulesV1.ConnectionState.CONNECTED,
                                "Host"),
                        new MultiplayerBattleSnapshot.SlotSnapshot(
                                MultiplayerRulesV1.CLIENT_SLOT_ID,
                                Faction.ENEMY.teamId(),
                                901,
                                MultiplayerRulesV1.PlayerRole.DIRECT_SHIP,
                                MultiplayerRulesV1.ConnectionState.CONNECTED,
                                "Client")));

        assertTrue(MultiplayerInGameDuelSnapshotApplier.apply(ctx, snapshot));

        assertNotEquals(901, localPlayerId);
        assertEquals(901, ctx.multiplayerLocalNetworkShipId);
        assertEquals(localPlayerId, ctx.multiplayerNetworkShipIdToLocalShipId.get(901));
        assertEquals(MultiplayerProtocolV1.playerIdForSlot(MultiplayerRulesV1.CLIENT_SLOT_ID),
                ctx.multiplayerLocalPlayerId);
        assertNotEquals(ctx.multiplayerMatchId, ctx.multiplayerSessionNonce);
        assertEquals(3100.0, ctx.player.x);
        assertEquals(1400.0, ctx.player.y);
        assertEquals(55, ctx.player.hp);
    }

    @Test
    void clientSnapshotAppliesAiSupportShipsWithoutMarkingThemPlayerControlled() {
        GameContext ctx = MultiplayerPlayableDuelContextFactory.create(
                MultiplayerLaunchConfig.client("127.0.0.1:46717", ""));

        MultiplayerBattleSnapshot snapshot = new MultiplayerBattleSnapshot(
                18L,
                List.of(
                        new MultiplayerBattleSnapshot.ShipSnapshot(
                                900, ShipRole.FRIGATE, Faction.ALLY,
                                100.0, 200.0, 0.0, 0.0, 0.0,
                                80, 10.0, true),
                        new MultiplayerBattleSnapshot.ShipSnapshot(
                                901, ShipRole.FRIGATE, Faction.ENEMY,
                                3100.0, 1400.0, 0.0, 0.0, Math.PI,
                                55, 5.0, true),
                        new MultiplayerBattleSnapshot.ShipSnapshot(
                                902, ShipRole.CIWS_CORVETTE, Faction.ALLY,
                                900.0, 260.0, 0.0, 0.0, 0.0,
                                40, 0.0, true),
                        new MultiplayerBattleSnapshot.ShipSnapshot(
                                903, ShipRole.CIWS_CORVETTE, Faction.ENEMY,
                                2800.0, 1540.0, 0.0, 0.0, Math.PI,
                                40, 0.0, true)),
                List.of(
                        new MultiplayerBattleSnapshot.SlotSnapshot(
                                MultiplayerRulesV1.HOST_SLOT_ID,
                                Faction.ALLY.teamId(),
                                900,
                                MultiplayerRulesV1.PlayerRole.DIRECT_SHIP,
                                MultiplayerRulesV1.ConnectionState.CONNECTED,
                                "Host"),
                        new MultiplayerBattleSnapshot.SlotSnapshot(
                                MultiplayerRulesV1.CLIENT_SLOT_ID,
                                Faction.ENEMY.teamId(),
                                901,
                                MultiplayerRulesV1.PlayerRole.DIRECT_SHIP,
                                MultiplayerRulesV1.ConnectionState.CONNECTED,
                                "Client")));

        assertTrue(MultiplayerInGameDuelSnapshotApplier.apply(ctx, snapshot));

        assertEquals(4, ctx.ships.size());
        assertEquals(1, countShips(ctx, Faction.ALLY, ShipRole.CIWS_CORVETTE));
        assertEquals(1, countShips(ctx, Faction.ENEMY, ShipRole.CIWS_CORVETTE));
        Integer allySupportLocalId = ctx.multiplayerNetworkShipIdToLocalShipId.get(902);
        Integer enemySupportLocalId = ctx.multiplayerNetworkShipIdToLocalShipId.get(903);
        assertNotNull(allySupportLocalId);
        assertNotNull(enemySupportLocalId);
        assertFalse(ctx.multiplayerPlayerControlledShipIds.contains(allySupportLocalId));
        assertFalse(ctx.multiplayerPlayerControlledShipIds.contains(enemySupportLocalId));
    }

    @Test
    void clientSnapshotApplicationRejectsSecondOwnerPath() throws Exception {
        GameContext ctx = MultiplayerPlayableDuelContextFactory.create(
                MultiplayerLaunchConfig.client("127.0.0.1:46717", ""));
        MultiplayerBattleSnapshot snapshot = new MultiplayerBattleSnapshot(
                12L,
                List.of(
                        new MultiplayerBattleSnapshot.ShipSnapshot(
                                900,
                                ShipRole.FRIGATE,
                                Faction.ALLY,
                                100.0,
                                200.0,
                                1.0,
                                2.0,
                                0.25,
                                80,
                                10.0,
                                true)),
                List.of(
                        new MultiplayerBattleSnapshot.SlotSnapshot(
                                MultiplayerRulesV1.HOST_SLOT_ID,
                                Faction.ALLY.teamId(),
                                900,
                                MultiplayerRulesV1.PlayerRole.DIRECT_SHIP,
                                MultiplayerRulesV1.ConnectionState.CONNECTED,
                                "Host")));

        assertTrue(MultiplayerInGameDuelSnapshotApplier.apply(ctx, snapshot));
        assertEquals(Thread.currentThread(), ctx.multiplayerClientSnapshotThreadGuard.owner());
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread remoteThread = new Thread(() -> {
            try {
                MultiplayerInGameDuelSnapshotApplier.apply(ctx, snapshot);
            } catch (Throwable ex) {
                failure.set(ex);
            }
        }, "mp-test-snapshot-applier");
        remoteThread.start();
        remoteThread.join(1_000);

        assertTrue(failure.get() instanceof IllegalStateException);
        assertTrue(failure.get().getMessage().contains("client snapshot application path"));
    }

    @Test
    void hostVictoryCoordinatorPublishesEliminationOnce() {
        GameContext ctx = MultiplayerPlayableDuelContextFactory.create(
                MultiplayerLaunchConfig.host(46717, "127.0.0.1"));
        Ship remote = ctx.ships.stream()
                .filter(ship -> ship != ctx.player)
                .findFirst()
                .orElseThrow();
        remote.hp = 0;
        remote.alive = false;
        remote.dying = false;

        MultiplayerInGameVictoryCoordinator.HostVictory victory =
                MultiplayerInGameVictoryCoordinator.evaluateHostVictory(ctx, 12L, 5L);

        assertNotNull(victory);
        assertTrue(ctx.gameOver);
        assertEquals(GameState.GAME_OVER, ctx.state);
        assertEquals("Elimination victory", ctx.gameOverText);
        assertEquals(MultiplayerReplicationV1.EventType.VICTORY_DECLARED, victory.event().type());
        assertEquals(12L, victory.event().hostTick());
        assertEquals(5L, victory.event().eventSequence());
        assertEquals("Elimination victory", victory.event().detail());

        assertNull(MultiplayerInGameVictoryCoordinator.evaluateHostVictory(ctx, 13L, 6L));
    }

    @Test
    void clientVictoryEventEndsPresentationContext() {
        GameContext ctx = MultiplayerPlayableDuelContextFactory.create(
                MultiplayerLaunchConfig.client("127.0.0.1:46717", ""));
        MultiplayerReplicationV1.AuthoritativeEvent event =
                new MultiplayerReplicationV1.AuthoritativeEvent(
                        MultiplayerReplicationV1.EventType.VICTORY_DECLARED,
                        null,
                        4L,
                        30L,
                        MultiplayerRulesV1.HOST_SLOT_ID,
                        0,
                        "Elimination victory");

        assertTrue(MultiplayerInGameVictoryCoordinator.applyClientEvent(ctx, event));

        assertTrue(ctx.gameOver);
        assertEquals(GameState.GAME_OVER, ctx.state);
        assertEquals("Elimination victory", ctx.gameOverText);
        assertEquals("Elimination victory", ctx.eventBanner);
        assertTrue(ctx.eventBannerT > 0.0);
    }

    @Test
    void hostRuntimeEvaluatesEliminationVictoryDuringAuthoritativeTick() {
        GameContext ctx = MultiplayerPlayableDuelContextFactory.create(
                MultiplayerLaunchConfig.host(46717, "127.0.0.1"));
        Ship remote = ctx.ships.stream()
                .filter(ship -> ship != ctx.player)
                .findFirst()
                .orElseThrow();
        remote.hp = 0;
        remote.alive = false;
        remote.dying = false;

        GameSimulationRuntime runtime = new GameSimulationRuntime(ctx);
        runtime.advanceFrame(
                System.nanoTime() + 100_000_000L,
                new InputSnapshot(false, false, false, false, false, 400.0, 300.0),
                800,
                600,
                1.0);

        assertTrue(ctx.gameOver);
        assertEquals(GameState.GAME_OVER, ctx.state);
        assertEquals("Elimination victory", ctx.gameOverText);
    }

    @Test
    void multiplayerRuntimeIgnoresLocalPauseAndZeroTimeScale() {
        GameContext ctx = MultiplayerPlayableDuelContextFactory.create(
                MultiplayerLaunchConfig.host(46717, "127.0.0.1"));
        Ship remote = ctx.ships.stream()
                .filter(ship -> ship != ctx.player)
                .findFirst()
                .orElseThrow();
        remote.hp = 0;
        remote.alive = false;
        remote.dying = false;
        ctx.state = GameState.PAUSED;

        GameSimulationRuntime runtime = new GameSimulationRuntime(ctx);
        runtime.advanceFrame(
                System.nanoTime() + 100_000_000L,
                new InputSnapshot(false, false, false, false, false, 400.0, 300.0),
                800,
                600,
                0.0);

        assertTrue(ctx.gameOver);
        assertEquals(GameState.GAME_OVER, ctx.state);
        assertEquals("Elimination victory", ctx.gameOverText);
    }

    private static MultiplayerRulesV1.BattleSetup aiSupportSetup() {
        return new MultiplayerRulesV1.BattleSetup(
                99L,
                MultiplayerRulesV1.DEFAULT_ARENA_ID,
                new MultiplayerRulesV1.PlayerSlot(
                        MultiplayerRulesV1.HOST_SLOT_ID, Faction.ALLY, ShipRole.FRIGATE, "Host"),
                new MultiplayerRulesV1.PlayerSlot(
                        MultiplayerRulesV1.CLIENT_SLOT_ID, Faction.ENEMY, ShipRole.FRIGATE, "Client"),
                MultiplayerRulesV1.VictoryRule.ELIMINATION,
                false,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false);
    }

    private static long countShips(GameContext ctx, Faction faction, ShipRole role) {
        return ctx.ships.stream()
                .filter(ship -> ship != null && ship.faction == faction && ship.role == role)
                .count();
    }
}
