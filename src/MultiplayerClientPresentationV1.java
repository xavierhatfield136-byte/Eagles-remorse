import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Phase 8 client presentation model: snapshot interpolation, HUD data, markers, and authority guardrails. */
public final class MultiplayerClientPresentationV1 {
    public static final int INTERPOLATION_DELAY_TICKS =
            Math.max(1, MultiplayerRulesV1.AUTHORITATIVE_TICK_RATE / MultiplayerProtocolV1.SNAPSHOT_RATE_HZ);
    public static final int PRESENTATION_BUFFER_CAPACITY = 2;

    public enum ClientCapability {
        INTERPOLATION,
        LOCAL_CAMERA,
        COSMETIC_PARTICLES,
        SOUND,
        TEMPORARY_PREDICTED_MUZZLE_EFFECTS,
        AUTHORITATIVE_AI,
        AUTHORITATIVE_DAMAGE,
        AUTHORITATIVE_DEATH,
        AUTHORITATIVE_OBJECTIVE_COMPLETION,
        AUTHORITATIVE_SHIP_SPAWNING,
        AUTHORITATIVE_TARGET_VALIDITY,
        AUTHORITATIVE_VICTORY_EVALUATION
    }

    public record DebugMetrics(long latestReceivedHostTick,
                               long renderedHostTick,
                               int interpolationDelayTicks,
                               long snapshotGapTicks,
                               long extrapolationTicks,
                               double correctionMagnitude) {}

    public record RenderedShip(int shipId,
                               ShipRole role,
                               Faction faction,
                               double x,
                               double y,
                               double angle,
                               int hp,
                               double shield,
                               boolean alive) {}

    public record LocalHudState(int slotId,
                                int teamId,
                                int controlledShipId,
                                ShipRole hull,
                                int hp,
                                double shield,
                                boolean alive,
                                boolean matchEnded,
                                String matchResult) {}

    public record RemoteMarker(int slotId,
                               int teamId,
                               int controlledShipId,
                               String displayName) {}

    public record RenderState(List<RenderedShip> ships,
                              LocalHudState localHud,
                              List<RemoteMarker> remoteMarkers,
                              DebugMetrics debug,
                              boolean completeVisibility) {
        public RenderState {
            ships = ships == null ? List.of() : List.copyOf(ships);
            remoteMarkers = remoteMarkers == null ? List.of() : List.copyOf(remoteMarkers);
        }
    }

    private final int localSlotId;
    private final PresentationBuffer presentationBuffer = new PresentationBuffer(PRESENTATION_BUFFER_CAPACITY);
    private MultiplayerReplicationV1.AuthoritativeEvent victoryEvent;

    public MultiplayerClientPresentationV1(int localSlotId) {
        this.localSlotId = Math.max(0, localSlotId);
    }

    public void receiveSnapshot(MultiplayerBattleSnapshot snapshot) {
        presentationBuffer.add(snapshot);
    }

    public void receiveEvent(MultiplayerReplicationV1.AuthoritativeEvent event) {
        if (event != null && event.type() == MultiplayerReplicationV1.EventType.VICTORY_DECLARED) {
            victoryEvent = event;
        }
    }

    public RenderState render() {
        if (presentationBuffer.isEmpty()) {
            return new RenderState(List.of(), emptyHud(), List.of(),
                    new DebugMetrics(0L, 0L, INTERPOLATION_DELAY_TICKS, 0L, 0L, 0.0),
                    true);
        }

        MultiplayerBattleSnapshot newest = presentationBuffer.newest();
        MultiplayerBattleSnapshot oldest = presentationBuffer.oldest();
        long renderedTick = Math.max(0L, newest.hostTick() - INTERPOLATION_DELAY_TICKS);
        long gap = Math.max(0L, newest.hostTick() - oldest.hostTick());
        long extrapolation = renderedTick > newest.hostTick() ? renderedTick - newest.hostTick() : 0L;
        double t = interpolationFraction(oldest.hostTick(), newest.hostTick(), renderedTick);
        ArrayList<RenderedShip> ships = new ArrayList<>();
        for (MultiplayerBattleSnapshot.ShipSnapshot latestShip : newest.ships()) {
            MultiplayerBattleSnapshot.ShipSnapshot previousShip = findShip(oldest, latestShip.shipId());
            ships.add(renderShip(previousShip, latestShip, t));
        }

        return new RenderState(ships, localHud(newest), remoteMarkers(newest),
                new DebugMetrics(newest.hostTick(), renderedTick, INTERPOLATION_DELAY_TICKS,
                        gap, extrapolation, 0.0),
                true);
    }

    public static boolean canClientRun(ClientCapability capability) {
        return switch (capability == null ? ClientCapability.AUTHORITATIVE_DAMAGE : capability) {
            case INTERPOLATION, LOCAL_CAMERA, COSMETIC_PARTICLES, SOUND,
                    TEMPORARY_PREDICTED_MUZZLE_EFFECTS -> true;
            case AUTHORITATIVE_AI, AUTHORITATIVE_DAMAGE, AUTHORITATIVE_DEATH,
                    AUTHORITATIVE_OBJECTIVE_COMPLETION, AUTHORITATIVE_SHIP_SPAWNING,
                    AUTHORITATIVE_TARGET_VALIDITY, AUTHORITATIVE_VICTORY_EVALUATION -> false;
        };
    }

    public int bufferedSnapshotCountForTests() {
        return presentationBuffer.size();
    }

    public List<Long> bufferedSnapshotTicksForTests() {
        return presentationBuffer.hostTicks();
    }

    private static final class PresentationBuffer {
        private final int capacity;
        private final ArrayList<MultiplayerBattleSnapshot> snapshots;

        private PresentationBuffer(int capacity) {
            this.capacity = Math.max(1, capacity);
            this.snapshots = new ArrayList<>(this.capacity);
        }

        private void add(MultiplayerBattleSnapshot snapshot) {
            if (snapshot == null) return;
            snapshots.add(snapshot);
            snapshots.sort(Comparator.comparingLong(MultiplayerBattleSnapshot::hostTick));
            while (snapshots.size() > capacity) {
                snapshots.remove(0);
            }
        }

        private boolean isEmpty() {
            return snapshots.isEmpty();
        }

        private int size() {
            return snapshots.size();
        }

        private MultiplayerBattleSnapshot oldest() {
            return snapshots.get(0);
        }

        private MultiplayerBattleSnapshot newest() {
            return snapshots.get(snapshots.size() - 1);
        }

        private List<Long> hostTicks() {
            ArrayList<Long> ticks = new ArrayList<>(snapshots.size());
            for (MultiplayerBattleSnapshot snapshot : snapshots) {
                ticks.add(snapshot.hostTick());
            }
            return List.copyOf(ticks);
        }
    }

    private RenderedShip renderShip(MultiplayerBattleSnapshot.ShipSnapshot previous,
                                    MultiplayerBattleSnapshot.ShipSnapshot latest,
                                    double t) {
        if (previous == null || previous.shipId() != latest.shipId()) {
            return new RenderedShip(latest.shipId(), latest.role(), latest.faction(),
                    latest.x(), latest.y(), latest.angle(), latest.hp(), latest.shield(), latest.alive());
        }
        return new RenderedShip(latest.shipId(), latest.role(), latest.faction(),
                lerp(previous.x(), latest.x(), t),
                lerp(previous.y(), latest.y(), t),
                lerp(previous.angle(), latest.angle(), t),
                latest.hp(), latest.shield(), latest.alive());
    }

    private LocalHudState localHud(MultiplayerBattleSnapshot snapshot) {
        MultiplayerBattleSnapshot.SlotSnapshot slot = null;
        for (MultiplayerBattleSnapshot.SlotSnapshot s : snapshot.slots()) {
            if (s.slotId() == localSlotId) {
                slot = s;
                break;
            }
        }
        if (slot == null) return emptyHud();
        MultiplayerBattleSnapshot.ShipSnapshot ship = findShip(snapshot, slot.controlledShipId());
        String result = victoryEvent == null ? "In progress" : victoryEvent.detail();
        return new LocalHudState(slot.slotId(), slot.teamId(), slot.controlledShipId(),
                ship == null ? ShipRole.FRIGATE : ship.role(),
                ship == null ? 0 : ship.hp(),
                ship == null ? 0.0 : ship.shield(),
                ship != null && ship.alive(),
                victoryEvent != null,
                result);
    }

    private List<RemoteMarker> remoteMarkers(MultiplayerBattleSnapshot snapshot) {
        ArrayList<RemoteMarker> out = new ArrayList<>();
        for (MultiplayerBattleSnapshot.SlotSnapshot slot : snapshot.slots()) {
            if (slot.slotId() == localSlotId) continue;
            out.add(new RemoteMarker(slot.slotId(), slot.teamId(), slot.controlledShipId(), slot.displayName()));
        }
        return out;
    }

    private LocalHudState emptyHud() {
        return new LocalHudState(localSlotId, 0, 0, ShipRole.FRIGATE,
                0, 0.0, false, victoryEvent != null,
                victoryEvent == null ? "In progress" : victoryEvent.detail());
    }

    private static MultiplayerBattleSnapshot.ShipSnapshot findShip(MultiplayerBattleSnapshot snapshot, int shipId) {
        if (snapshot == null) return null;
        for (MultiplayerBattleSnapshot.ShipSnapshot ship : snapshot.ships()) {
            if (ship.shipId() == shipId) return ship;
        }
        return null;
    }

    private static double interpolationFraction(long startTick, long endTick, long renderTick) {
        if (endTick <= startTick) return 1.0;
        double t = (renderTick - startTick) / (double) (endTick - startTick);
        if (t < 0.0) return 0.0;
        if (t > 1.0) return 1.0;
        return t;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
