import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerPerformanceV1Test {

    @Test
    void snapshotGenerationStaysWithinSmokeBudget() {
        MultiplayerBattleRuntime runtime = MultiplayerBattleRuntime.createAuthoritative(
                MultiplayerRulesV1.defaultDuel(555L, ShipRole.FRIGATE, ShipRole.FRIGATE),
                true);

        assertTimeout(Duration.ofSeconds(2), () -> {
            for (int i = 0; i < 2_000; i++) {
                MultiplayerBattleSnapshot snapshot = runtime.snapshot(i);
                byte[] encoded = MultiplayerSerializationV1.encodeSnapshot(snapshot);
                assertTrue(MultiplayerNetworkConditionsV1.snapshotWithinPeakBudget(encoded.length));
            }
        });
    }

    @Test
    void clientInterpolationStaysWithinSmokeBudget() {
        MultiplayerBattleRuntime runtime = MultiplayerBattleRuntime.createAuthoritative(
                MultiplayerRulesV1.defaultDuel(556L, ShipRole.FRIGATE, ShipRole.FRIGATE),
                true);
        MultiplayerClientPresentationV1 presentation =
                new MultiplayerClientPresentationV1(MultiplayerRulesV1.CLIENT_SLOT_ID);

        assertTimeout(Duration.ofSeconds(2), () -> {
            for (int i = 0; i < 2_000; i++) {
                presentation.receiveSnapshot(runtime.snapshot(i));
                MultiplayerClientPresentationV1.RenderState state = presentation.render();
                assertTrue(state.ships().size() <= MultiplayerNetworkConditionsV1.MAX_V1_SUPPORTED_ENTITIES);
            }
        });
    }
}
