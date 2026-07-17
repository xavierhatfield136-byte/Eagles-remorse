import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerSessionBoundaryTest {

    @Test
    void hostAndSinglePlayerSessionsShareAuthoritativeRuntimeType() {
        MultiplayerRulesV1.BattleSetup setup =
                MultiplayerRulesV1.defaultDuel(500L, ShipRole.FRIGATE, ShipRole.CRUISER);

        MultiplayerBattleSession single = new SinglePlayerCustomBattleSession(setup);
        MultiplayerBattleSession host = new MultiplayerHostBattleSession(setup);
        MultiplayerBattleSession client = new MultiplayerClientBattleSession(setup);

        assertTrue(single.authoritative());
        assertTrue(host.authoritative());
        assertFalse(client.authoritative());
        assertEquals(MultiplayerBattleSession.Kind.SINGLE_PLAYER_CUSTOM_BATTLE, single.kind());
        assertEquals(MultiplayerBattleSession.Kind.MULTIPLAYER_HOST, host.kind());
        assertEquals(MultiplayerBattleSession.Kind.MULTIPLAYER_CLIENT, client.kind());
        assertSame(single.runtime().getClass(), host.runtime().getClass(),
                "authoritative sessions should use the same runtime shell instead of duplicated combat logic");
        assertNull(client.runtime(), "client sessions are presentation-only until snapshots are implemented");
    }

    @Test
    void hostSessionStartsFreshBattleOnlyContextWithoutCampaignState() {
        MultiplayerRulesV1.BattleSetup setup =
                MultiplayerRulesV1.defaultDuel(501L, ShipRole.FRIGATE, ShipRole.CRUISER);

        MultiplayerHostBattleSession host = new MultiplayerHostBattleSession(setup);
        GameContext ctx = host.runtime().context();

        assertEquals(GameMode.CUSTOM_BATTLES, ctx.config.mode);
        assertFalse(ctx.config.resumeCampaign);
        assertNull(ctx.campaign);
        assertEquals(2, ctx.ships.size(), "V1 duel should start with exactly two player-slot ships and no AI fill");
        assertNotNull(ctx.player);
        assertEquals(Faction.ALLY, ctx.ships.get(0).faction);
        assertEquals(Faction.ENEMY, ctx.ships.get(1).faction);
        assertTrue(ctx.teamBases.isEmpty(), "V1 duel should not silently import custom-battle bases");
    }

    @Test
    void sharedRuntimeAdvancesFixedTicksThroughOneRunner() {
        MultiplayerRulesV1.BattleSetup setup =
                MultiplayerRulesV1.defaultDuel(502L, ShipRole.FRIGATE, ShipRole.CRUISER);
        MultiplayerHostBattleSession host = new MultiplayerHostBattleSession(setup);
        AtomicInteger ticks = new AtomicInteger();

        MultiplayerFixedStepClock.StepPlan plan = host.runtime().advanceFrame(1.0 / 10.0,
                (ctx, tickSeconds, hostTick) -> ticks.incrementAndGet());

        assertEquals(MultiplayerRulesV1.MAX_CATCH_UP_TICKS_PER_FRAME, plan.ticksToRun());
        assertEquals(plan.ticksToRun(), ticks.get());
    }

    @Test
    void runtimePublishesImmutableSnapshot() {
        MultiplayerRulesV1.BattleSetup setup =
                MultiplayerRulesV1.defaultDuel(503L, ShipRole.FRIGATE, ShipRole.CRUISER);
        MultiplayerHostBattleSession host = new MultiplayerHostBattleSession(setup);

        MultiplayerBattleSnapshot snapshot = host.runtime().snapshot(42L);

        assertEquals(42L, snapshot.hostTick());
        assertEquals(2, snapshot.ships().size());
        assertEquals(2, snapshot.slots().size());
        try {
            snapshot.ships().clear();
            throw new AssertionError("snapshot ship list should be immutable");
        } catch (UnsupportedOperationException expected) {
            assertTrue(true);
        }
    }

    @Test
    void singlePlayerSessionAcceptsLocalInputThroughCommandGate() {
        MultiplayerRulesV1.BattleSetup setup =
                MultiplayerRulesV1.defaultDuel(504L, ShipRole.FRIGATE, ShipRole.CRUISER);
        SinglePlayerCustomBattleSession session = new SinglePlayerCustomBattleSession(setup);

        MultiplayerCommandGate.CommandResult result = session.runtime().acceptLocalInput(
                new InputSnapshot(true, false, false, true, false, 10.0, 0.0),
                1L, 1L, true, false);

        assertTrue(result.accepted(), result.reason());
        assertEquals(1L, result.authoritativeTick());
    }

    @Test
    void playerSlotStateSurvivesControlledShipLoss() {
        MultiplayerPlayerSlotState slot = new MultiplayerPlayerSlotState(
                MultiplayerRulesV1.HOST_SLOT_ID,
                Faction.ALLY.teamId(),
                99,
                MultiplayerRulesV1.PlayerRole.DIRECT_SHIP,
                MultiplayerRulesV1.ConnectionState.CONNECTED,
                "Host");

        slot.clearControlledShip();

        assertEquals(MultiplayerRulesV1.HOST_SLOT_ID, slot.slotId);
        assertFalse(slot.hasControlledShip());
        assertTrue(slot.connected());
    }

    @Test
    void guardrailsKeepCampaignActionsAndPresentationStateOutOfSync() {
        assertFalse(MultiplayerBattleGuardrails.campaignActionsAllowed());
        assertFalse(MultiplayerBattleGuardrails.campaignUiAllowed());
        for (MultiplayerBattleGuardrails.LocalPresentationState state
                : MultiplayerBattleGuardrails.LocalPresentationState.values()) {
            assertFalse(MultiplayerBattleGuardrails.synchronizedOverNetwork(state));
        }
    }
}
