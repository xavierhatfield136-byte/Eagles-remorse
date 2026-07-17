import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Allocates network entity identities with generation data so delayed packets cannot target a new entity
 * that happens to reuse an older local index.
 */
public final class MultiplayerEntityIdAllocator {
    public enum EntityKind {
        SHIP,
        PROJECTILE,
        HAZARD,
        OBJECTIVE
    }

    public enum DespawnReason {
        DESTROYED,
        EXPIRED,
        MATCH_ENDED,
        DISCONNECTED,
        RULE_REMOVED
    }

    public record NetworkEntityId(int index, int generation) {
        public NetworkEntityId {
            if (index <= 0) throw new IllegalArgumentException("Network entity index must be positive");
            if (generation <= 0) throw new IllegalArgumentException("Network entity generation must be positive");
        }
    }

    public record SpawnEvent(NetworkEntityId id, EntityKind kind, long hostTick) {
        public SpawnEvent {
            if (id == null) throw new IllegalArgumentException("Spawn event requires an entity id");
            if (kind == null) kind = EntityKind.SHIP;
            hostTick = Math.max(0L, hostTick);
        }
    }

    public record DespawnEvent(NetworkEntityId id, EntityKind kind, DespawnReason reason, long hostTick) {
        public DespawnEvent {
            if (id == null) throw new IllegalArgumentException("Despawn event requires an entity id");
            if (kind == null) kind = EntityKind.SHIP;
            if (reason == null) reason = DespawnReason.RULE_REMOVED;
            hostTick = Math.max(0L, hostTick);
        }
    }

    private int nextIndex = 1;
    private final Set<Integer> retiredIndexes = new HashSet<>();
    private final Map<Integer, Integer> generationsByIndex = new HashMap<>();

    public NetworkEntityId allocate() {
        NetworkEntityId id = new NetworkEntityId(nextIndex++, 1);
        generationsByIndex.put(id.index(), id.generation());
        return id;
    }

    public SpawnEvent spawn(EntityKind kind, long hostTick) {
        return new SpawnEvent(allocate(), kind, hostTick);
    }

    public void retire(NetworkEntityId id) {
        if (id == null) return;
        retiredIndexes.add(id.index());
    }

    public DespawnEvent despawn(NetworkEntityId id, EntityKind kind, DespawnReason reason, long hostTick) {
        retire(id);
        return new DespawnEvent(id, kind, reason, hostTick);
    }

    public boolean acceptsUpdate(NetworkEntityId id) {
        if (id == null) return false;
        Integer liveGeneration = generationsByIndex.get(id.index());
        return id.index() > 0
                && id.generation() > 0
                && liveGeneration != null
                && liveGeneration == id.generation()
                && !retiredIndexes.contains(id.index());
    }

    public boolean isRetired(NetworkEntityId id) {
        return id != null && retiredIndexes.contains(id.index());
    }

    public int allocatedCount() {
        return Math.max(0, nextIndex - 1);
    }
}
