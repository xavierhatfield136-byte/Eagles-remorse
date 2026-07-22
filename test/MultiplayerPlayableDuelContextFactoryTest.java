import app.config.MultiplayerLaunchConfig;
import app.config.MultiplayerMissionChoice;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerPlayableDuelContextFactoryTest {

    @Test
    void hostLaunchCreatesPreparedGamePanelContextWithoutWorldInitBloat() {
        GameContext ctx = MultiplayerPlayableDuelContextFactory.create(
                MultiplayerLaunchConfig.host(46717, "127.0.0.1"));

        assertTrue(ctx.multiplayerBattle);
        assertEquals(MultiplayerAuthorityMode.HOST, ctx.multiplayerAuthorityMode);
        assertEquals(MultiplayerRulesV1.HOST_SLOT_ID, ctx.multiplayerLocalSlotId);
        assertNotNull(ctx.player);
        assertEquals(Faction.ALLY, ctx.player.faction);
        assertEquals(2, ctx.ships.size());
        assertTrue(ctx.teamBases.isEmpty());
        assertEquals(2, ctx.multiplayerPlayerControlledShipIds.size());
        for (Ship ship : ctx.ships) {
            assertTrue(ctx.multiplayerPlayerControlledShipIds.contains(ship.id));
        }
    }

    @Test
    void clientLaunchUsesClientSlotAsLocalPresentationPlayer() {
        GameContext ctx = MultiplayerPlayableDuelContextFactory.create(
                MultiplayerLaunchConfig.client("127.0.0.1:46717", "")
                        .withPlayerName("Grace"));

        assertTrue(ctx.multiplayerBattle);
        assertEquals(MultiplayerAuthorityMode.CLIENT_PRESENTATION, ctx.multiplayerAuthorityMode);
        assertEquals(MultiplayerRulesV1.CLIENT_SLOT_ID, ctx.multiplayerLocalSlotId);
        assertNotNull(ctx.player);
        assertEquals(Faction.ENEMY, ctx.player.faction);
        assertEquals("Grace", ctx.player.name);
        assertEquals(2, ctx.ships.size());
        assertFalse(ctx.teamBases.containsKey(Faction.ALLY));
    }

    @Test
    void hostLaunchUsesPersistedHostDisplayName() {
        GameContext ctx = MultiplayerPlayableDuelContextFactory.create(
                MultiplayerLaunchConfig.host(46717, "127.0.0.1")
                        .withPlayerName("Ada"));

        assertEquals("Ada", ctx.player.name);
    }

    @Test
    void selectedHeavyDuelMissionChangesPlayableContextHullsAndMap() {
        GameContext ctx = MultiplayerPlayableDuelContextFactory.create(
                MultiplayerLaunchConfig.host(46717, "127.0.0.1")
                        .withMissionId(MultiplayerMissionChoice.HEAVY_DUEL.missionId()));

        assertEquals(5200, ctx.WORLD_W);
        assertEquals(3200, ctx.WORLD_H);
        assertEquals(ShipRole.CRUISER, ctx.ships.get(0).role);
        assertEquals(ShipRole.BATTLECRUISER, ctx.ships.get(1).role);
    }

    @Test
    void everyMultiplayerMissionChoiceCreatesPlayableHostContext() {
        for (MultiplayerMissionChoice choice : MultiplayerMissionChoice.values()) {
            GameContext ctx = MultiplayerPlayableDuelContextFactory.create(
                    MultiplayerLaunchConfig.host(46717, "127.0.0.1")
                            .withMissionId(choice.missionId()));

            assertTrue(ctx.multiplayerBattle, choice + " should create a multiplayer context");
            assertEquals(MultiplayerAuthorityMode.HOST, ctx.multiplayerAuthorityMode);
            assertEquals(2, ctx.ships.size(), choice + " should create the two V1 player ships");
            assertTrue(ctx.ships.stream().allMatch(ship -> ship.alive));
            assertEquals(2, ctx.multiplayerPlayerControlledShipIds.size());
        }
    }

    @Test
    void sameLockedMissionSpecProducesSameInitialSpawnState() {
        MultiplayerLaunchConfig launch = MultiplayerLaunchConfig.host(46717, "127.0.0.1")
                .withMissionSettings(MultiplayerMissionChoice.HEAVY_DUEL.missionId(),
                        4242L, 7000, 4200);

        GameContext first = MultiplayerPlayableDuelContextFactory.create(launch);
        GameContext second = MultiplayerPlayableDuelContextFactory.create(launch);

        assertEquals(first.ships.size(), second.ships.size());
        for (int i = 0; i < first.ships.size(); i++) {
            Ship a = first.ships.get(i);
            Ship b = second.ships.get(i);
            assertEquals(a.role, b.role);
            assertEquals(a.faction, b.faction);
            assertEquals(a.x, b.x, 1e-9);
            assertEquals(a.y, b.y, 1e-9);
            assertEquals(a.angle, b.angle, 1e-9);
            assertEquals(a.hp, b.hp);
            assertEquals(a.shield, b.shield, 1e-9);
        }
    }

    @Test
    void sameLockedMissionSpecProducesSameInitialLoadoutsAcrossHostAndClientPerspectives() {
        MultiplayerLaunchConfig hostLaunch = MultiplayerLaunchConfig.host(46717, "127.0.0.1")
                .withMissionSettings(MultiplayerMissionChoice.HEAVY_DUEL.missionId(),
                        4242L, 7000, 4200);
        MultiplayerLaunchConfig clientLaunch = MultiplayerLaunchConfig.client("127.0.0.1:46717", "")
                .withMissionSettings(MultiplayerMissionChoice.HEAVY_DUEL.missionId(),
                        4242L, 7000, 4200);

        GameContext host = MultiplayerPlayableDuelContextFactory.create(hostLaunch);
        GameContext client = MultiplayerPlayableDuelContextFactory.create(clientLaunch);

        assertEquivalentInitialLoadout(shipForFaction(host, Faction.ALLY), shipForFaction(client, Faction.ALLY));
        assertEquivalentInitialLoadout(shipForFaction(host, Faction.ENEMY), shipForFaction(client, Faction.ENEMY));
    }

    @Test
    void lockedMissionSettingsOverrideDefaultSeedAndWorldSize() {
        MissionLaunchSpec spec = MultiplayerPlayableDuelContextFactory.resolveMissionSpec(
                MultiplayerLaunchConfig.host(46717, "127.0.0.1")
                        .withMissionSettings(MultiplayerMissionChoice.HEAVY_DUEL.missionId(),
                                4242L, 7000, 4200),
                99L);

        assertEquals(MultiplayerMissionChoice.HEAVY_DUEL.missionId(), spec.missionId());
        assertEquals(4242L, spec.seed());
        assertEquals(7000, spec.worldW());
        assertEquals(4200, spec.worldH());
        assertEquals(ShipRole.CRUISER, spec.playerSlots().get(0).defaultHull());
        assertEquals(ShipRole.BATTLECRUISER, spec.playerSlots().get(1).defaultHull());
    }

    @Test
    void unknownMultiplayerMissionFallsBackToCustomBattleVariant() {
        MissionLaunchSpec spec = MultiplayerPlayableDuelContextFactory.resolveMissionSpec(
                MultiplayerLaunchConfig.host(46717, "127.0.0.1")
                        .withMissionId("missing:mission"),
                123L);

        assertEquals(CustomMissionCatalog.CUSTOM_BATTLE_ID, spec.missionId());
        assertEquals(ShipRole.FRIGATE, spec.playerSlots().get(0).defaultHull());
        assertEquals(ShipRole.FRIGATE, spec.playerSlots().get(1).defaultHull());
    }

    @Test
    void gamePanelCanMountPreparedMultiplayerContextWithoutRespawningCustomBattleFleets() {
        GameContext ctx = MultiplayerPlayableDuelContextFactory.create(
                MultiplayerLaunchConfig.host(46717, "127.0.0.1"));

        GamePanel panel = new GamePanel(ctx, () -> {}, () -> {});
        try {
            assertEquals(2, panel.ctx.ships.size());
            assertTrue(panel.ctx.multiplayerBattle);
            assertTrue(panel.ctx.teamBases.isEmpty());
        } finally {
            panel.shutdown();
        }
    }

    @Test
    void aiDoesNotTakeOverRemotePlayerSlotShipInPreparedDuelContext() {
        GameContext ctx = MultiplayerPlayableDuelContextFactory.create(
                MultiplayerLaunchConfig.host(46717, "127.0.0.1"));
        Ship remote = ctx.ships.stream()
                .filter(ship -> ship != ctx.player)
                .findFirst()
                .orElseThrow();
        double x = remote.x;
        double y = remote.y;
        double vx = remote.vx;
        double vy = remote.vy;

        AISystem.update(ctx, GameContext.DT);

        assertEquals(x, remote.x);
        assertEquals(y, remote.y);
        assertEquals(vx, remote.vx);
        assertEquals(vy, remote.vy);
    }

    @Test
    void clientLocalInputFramesUseClientSlotOwnership() {
        GameContext ctx = MultiplayerPlayableDuelContextFactory.create(
                MultiplayerLaunchConfig.client("127.0.0.1:46717", ""));
        SinglePlayerCustomBattleCommandPath path = new SinglePlayerCustomBattleCommandPath();

        SinglePlayerCustomBattleCommandPath.RoutedInput routed = path.routeWithSequenceForTests(
                ctx,
                new InputSnapshot(true, false, false, true, false, 640.0, 360.0),
                ctx.player.x + 100.0,
                ctx.player.y,
                1L,
                1L);

        assertTrue(routed.accepted(), routed.result.reason());
        assertEquals(MultiplayerRulesV1.CLIENT_SLOT_ID, routed.frame.slotId());
        assertEquals(ctx.player.id, routed.frame.controlledShipId());
    }

    @Test
    void clientPresentationRuntimeDoesNotRunAuthoritativeMovement() {
        GameContext ctx = MultiplayerPlayableDuelContextFactory.create(
                MultiplayerLaunchConfig.client("127.0.0.1:46717", ""));
        double x = ctx.player.x;
        double y = ctx.player.y;
        GameSimulationRuntime runtime = new GameSimulationRuntime(ctx);

        runtime.advanceFrame(
                System.nanoTime() + 1_000_000_000L,
                new InputSnapshot(true, false, false, true, false, 640.0, 360.0),
                1280,
                720,
                1.0);

        assertEquals(x, ctx.player.x);
        assertEquals(y, ctx.player.y);
    }

    private static Ship shipForFaction(GameContext ctx, Faction faction) {
        return ctx.ships.stream()
                .filter(ship -> ship != null && ship.faction == faction)
                .findFirst()
                .orElseThrow();
    }

    private static void assertEquivalentInitialLoadout(Ship expected, Ship actual) {
        assertEquals(expected.role, actual.role);
        assertEquals(expected.hpMax, actual.hpMax);
        assertEquals(expected.shieldMax, actual.shieldMax, 1e-9);
        assertEquals(expected.turrets.size(), actual.turrets.size());
        for (int i = 0; i < expected.turrets.size(); i++) {
            Turret a = expected.turrets.get(i);
            Turret b = actual.turrets.get(i);
            assertEquals(a.kind, b.kind);
            assertEquals(a.missileRole, b.missileRole);
            assertEquals(a.primary, b.primary);
            assertEquals(a.localX, b.localX, 1e-9);
            assertEquals(a.localY, b.localY, 1e-9);
            assertEquals(a.damage, b.damage);
            assertEquals(a.cooldown, b.cooldown, 1e-9);
            assertEquals(a.bulletSpeed, b.bulletSpeed, 1e-9);
            assertEquals(a.bulletLife, b.bulletLife);
            assertEquals(a.missileSpeed, b.missileSpeed, 1e-9);
            assertEquals(a.missileTurnRate, b.missileTurnRate, 1e-9);
            assertEquals(a.missileLife, b.missileLife);
        }
    }
}
