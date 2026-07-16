import java.util.Map;

/**
 * Fleet UI/presentation synchronization helpers used by AISystem.
 */
public final class FleetPresentationSync {
    private FleetPresentationSync() {}

    public static <V> void syncMap(Map<Integer, V> destination, Map<Integer, V> source) {
        if (destination == null) return;
        if (source == null || source.isEmpty()) {
            destination.clear();
            return;
        }
        destination.entrySet().removeIf(e -> e == null || !source.containsKey(e.getKey()));
        for (Map.Entry<Integer, V> entry : source.entrySet()) {
            if (entry == null || entry.getKey() == null) continue;
            V value = entry.getValue();
            if (value == null) destination.remove(entry.getKey());
            else destination.put(entry.getKey(), value);
        }
    }
}
