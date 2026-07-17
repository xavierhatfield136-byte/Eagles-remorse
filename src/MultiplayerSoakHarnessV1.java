import java.time.Duration;

/** Repeatable loopback soak routine for V1 multiplayer stability checks. */
public final class MultiplayerSoakHarnessV1 {
    public static final Duration ROUTINE_LOOPBACK_SOAK = Duration.ofMinutes(10);
    public static final Duration MANUAL_RELEASE_SOAK = Duration.ofMinutes(30);
    public static final Duration PRE_RELEASE_MIN_SOAK = Duration.ofMinutes(60);
    public static final Duration PRE_RELEASE_MAX_SOAK = Duration.ofMinutes(120);

    public record SoakReport(int ticksRun,
                             int snapshotsReceived,
                             long latestHostTick,
                             long maxSnapshotGapTicks,
                             boolean connected,
                             boolean returnedToMenu,
                             String failureReason) {
        public boolean passed() {
            return failureReason == null || failureReason.isBlank();
        }
    }

    private final MultiplayerRulesV1.BattleSetup setup;

    public MultiplayerSoakHarnessV1(MultiplayerRulesV1.BattleSetup setup) {
        this.setup = setup == null
                ? MultiplayerRulesV1.defaultDuel(2026L, ShipRole.FRIGATE, ShipRole.FRIGATE)
                : setup;
    }

    public SoakReport runLoopbackTicks(int ticks) {
        int safeTicks = Math.max(1, ticks);
        MultiplayerLoopbackDuelHarness harness = new MultiplayerLoopbackDuelHarness(setup);
        MultiplayerProtocolV1.CompatibilityResult connection = harness.connect();
        if (!connection.accepted()) {
            return new SoakReport(0, 0, 0L, 0L, false, false, connection.reason());
        }
        harness.startMatch(0L);

        int snapshots = 0;
        long previousSnapshotTick = 0L;
        long latestSnapshotTick = 0L;
        long maxGap = 0L;
        MultiplayerPlayerSlotState client = harness.hostScenario().runtime().slots()
                .get(MultiplayerRulesV1.CLIENT_SLOT_ID);
        if (client == null || client.controlledShipId <= 0) {
            return new SoakReport(0, 0, 0L, 0L, true, false, "Missing client-controlled ship");
        }

        for (int tick = 1; tick <= safeTicks; tick++) {
            float turn = ((tick / 90) % 2 == 0) ? 0.18f : -0.18f;
            harness.sendClientInput(new MultiplayerCommandGate.PlayerInputFrame(
                    MultiplayerRulesV1.CLIENT_SLOT_ID,
                    client.controlledShipId,
                    tick,
                    tick,
                    0.65f,
                    turn,
                    Math.PI,
                    false,
                    false));
            harness.clientHeartbeat(tick);
            harness.hostTick(GameContext.DT, tick);

            MultiplayerBattleSnapshot snapshot = harness.clientView().latestSnapshot();
            if (snapshot == null) {
                return new SoakReport(tick, snapshots, latestSnapshotTick, maxGap,
                        true, false, "Missing client snapshot");
            }
            if (snapshot.hostTick() != latestSnapshotTick) {
                snapshots++;
                if (previousSnapshotTick > 0L) {
                    maxGap = Math.max(maxGap, snapshot.hostTick() - previousSnapshotTick);
                }
                previousSnapshotTick = snapshot.hostTick();
                latestSnapshotTick = snapshot.hostTick();
            }
            if (harness.clientTimedOut(tick)) {
                return new SoakReport(tick, snapshots, latestSnapshotTick, maxGap,
                        true, false, "Client timed out during soak");
            }
            if (harness.hostScenario().lastResult().ended()) {
                return new SoakReport(tick, snapshots, latestSnapshotTick, maxGap,
                        true, false, "Match ended early during movement soak");
            }
        }

        harness.exitToMenu();
        return new SoakReport(safeTicks, snapshots, latestSnapshotTick, maxGap,
                true, harness.clientView().returnedToMenu(), "");
    }

    public SoakReport runLoopbackFor(Duration duration) {
        long seconds = Math.max(1L, duration == null ? 1L : duration.toSeconds());
        long ticks = Math.min(Integer.MAX_VALUE, seconds * MultiplayerRulesV1.AUTHORITATIVE_TICK_RATE);
        return runLoopbackTicks((int) ticks);
    }
}
