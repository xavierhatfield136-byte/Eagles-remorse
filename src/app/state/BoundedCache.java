package app.state;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small access-ordered cache used for decoded image and generated sprite libraries.
 */
public final class BoundedCache<K, V> extends LinkedHashMap<K, V> {
    private final int maxEntries;
    private int evictionCount = 0;

    public BoundedCache(int maxEntries) {
        super(16, 0.75f, true);
        this.maxEntries = Math.max(1, maxEntries);
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        boolean remove = size() > maxEntries;
        if (remove) evictionCount++;
        return remove;
    }

    public int maxEntries() { return maxEntries; }
    public int evictionCount() { return evictionCount; }
}
