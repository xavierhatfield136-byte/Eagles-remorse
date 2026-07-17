import java.util.ArrayList;
import java.util.List;

/** Client-side loopback view that displays only host snapshots and authoritative events. */
public final class MultiplayerClientSnapshotView {
    private MultiplayerBattleSnapshot latestSnapshot;
    private MultiplayerProtocolV1.InputAck latestInputAck;
    private final List<MultiplayerReplicationV1.AuthoritativeEvent> events = new ArrayList<>();
    private boolean matchEnded;
    private boolean returnedToMenu;

    public void receive(MultiplayerLoopbackTransport.Message message) {
        if (message == null) return;
        if (message.snapshot() != null) {
            latestSnapshot = message.snapshot();
        }
        if (message.inputAck() != null) {
            latestInputAck = message.inputAck();
        }
        if (message.event() != null) {
            events.add(message.event());
            if (message.event().type() == MultiplayerReplicationV1.EventType.VICTORY_DECLARED) {
                matchEnded = true;
            }
        }
        if (message.kind() == MultiplayerProtocolV1.MessageKind.DISCONNECT_NOTICE) {
            returnedToMenu = true;
        }
    }

    public MultiplayerBattleSnapshot latestSnapshot() {
        return latestSnapshot;
    }

    public MultiplayerProtocolV1.InputAck latestInputAck() {
        return latestInputAck;
    }

    public List<MultiplayerReplicationV1.AuthoritativeEvent> events() {
        return List.copyOf(events);
    }

    public boolean matchEnded() {
        return matchEnded;
    }

    public boolean returnedToMenu() {
        return returnedToMenu;
    }

    public int latestHpForShip(int shipId) {
        if (latestSnapshot == null) return -1;
        for (MultiplayerBattleSnapshot.ShipSnapshot ship : latestSnapshot.ships()) {
            if (ship.shipId() == shipId) return ship.hp();
        }
        return -1;
    }
}
