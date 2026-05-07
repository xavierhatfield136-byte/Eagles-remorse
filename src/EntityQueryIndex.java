import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EntityQueryIndex {
    private static final double SHIP_CELL_SIZE = 320.0;
    private static final double MISSILE_CELL_SIZE = 192.0;

    private final LongBucketTable<Ship> shipCells = new LongBucketTable<>();
    private final LongBucketTable<Missile> missileCells = new LongBucketTable<>();
    private final Map<Integer, Ship> shipById = new HashMap<>();
    private double maxShipBroadPhaseRadius = 0.0;

    public void rebuild(GameContext ctx) {
        shipCells.clear();
        missileCells.clear();
        shipById.clear();
        maxShipBroadPhaseRadius = 0.0;
        if (ctx == null) return;

        for (int i = 0; i < ctx.ships.size(); i++) {
            Ship ship = ctx.ships.get(i);
            if (ship == null || !ship.alive || ship.dying || ship.hp <= 0) continue;
            shipById.put(ship.id, ship);
            maxShipBroadPhaseRadius = Math.max(maxShipBroadPhaseRadius, HullGeometry.broadPhaseRadius(ship));
            bucket(shipCells, SHIP_CELL_SIZE, ship.x, ship.y).add(ship);
        }

        for (int i = 0; i < ctx.projectiles.size(); i++) {
            Projectile projectile = ctx.projectiles.get(i);
            if (!(projectile instanceof Missile missile) || !missile.alive) continue;
            bucket(missileCells, MISSILE_CELL_SIZE, missile.x, missile.y).add(missile);
        }
    }

    public Ship findShipById(int id) {
        if (id <= 0) return null;
        return shipById.get(id);
    }

    public double maxShipBroadPhaseRadius() {
        return maxShipBroadPhaseRadius;
    }

    public List<Ship> collectAliveShipsNear(double x, double y, double radius, List<Ship> out) {
        out.clear();
        if (shipCells.isEmpty()) return out;
        double r = Math.max(0.0, radius);
        double r2 = r * r;
        int minCellX = cell(x - r, SHIP_CELL_SIZE);
        int maxCellX = cell(x + r, SHIP_CELL_SIZE);
        int minCellY = cell(y - r, SHIP_CELL_SIZE);
        int maxCellY = cell(y + r, SHIP_CELL_SIZE);
        for (int cy = minCellY; cy <= maxCellY; cy++) {
            for (int cx = minCellX; cx <= maxCellX; cx++) {
                ArrayList<Ship> bucket = shipCells.get(key(cx, cy));
                if (bucket == null || bucket.isEmpty()) continue;
                for (int i = 0; i < bucket.size(); i++) {
                    Ship ship = bucket.get(i);
                    if (ship == null) continue;
                    if (GameMath.dist2(ship.x, ship.y, x, y) <= r2) {
                        out.add(ship);
                    }
                }
            }
        }
        return out;
    }

    public List<Ship> collectHostileShipsNear(Faction perspective, double x, double y, double radius, List<Ship> out) {
        out.clear();
        if (shipCells.isEmpty() || perspective == null) return out;
        double r = Math.max(0.0, radius);
        double r2 = r * r;
        int minCellX = cell(x - r, SHIP_CELL_SIZE);
        int maxCellX = cell(x + r, SHIP_CELL_SIZE);
        int minCellY = cell(y - r, SHIP_CELL_SIZE);
        int maxCellY = cell(y + r, SHIP_CELL_SIZE);
        for (int cy = minCellY; cy <= maxCellY; cy++) {
            for (int cx = minCellX; cx <= maxCellX; cx++) {
                ArrayList<Ship> bucket = shipCells.get(key(cx, cy));
                if (bucket == null || bucket.isEmpty()) continue;
                for (int i = 0; i < bucket.size(); i++) {
                    Ship ship = bucket.get(i);
                    if (ship == null || ship.faction == null) continue;
                    if (perspective.isFriendlyTo(ship.faction)) continue;
                    if (GameMath.dist2(ship.x, ship.y, x, y) <= r2) {
                        out.add(ship);
                    }
                }
            }
        }
        return out;
    }

    public List<Missile> collectMissilesNear(double x, double y, double radius, List<Missile> out) {
        out.clear();
        if (missileCells.isEmpty()) return out;
        double r = Math.max(0.0, radius);
        double r2 = r * r;
        int minCellX = cell(x - r, MISSILE_CELL_SIZE);
        int maxCellX = cell(x + r, MISSILE_CELL_SIZE);
        int minCellY = cell(y - r, MISSILE_CELL_SIZE);
        int maxCellY = cell(y + r, MISSILE_CELL_SIZE);
        for (int cy = minCellY; cy <= maxCellY; cy++) {
            for (int cx = minCellX; cx <= maxCellX; cx++) {
                ArrayList<Missile> bucket = missileCells.get(key(cx, cy));
                if (bucket == null || bucket.isEmpty()) continue;
                for (int i = 0; i < bucket.size(); i++) {
                    Missile missile = bucket.get(i);
                    if (missile == null) continue;
                    if (GameMath.dist2(missile.x, missile.y, x, y) <= r2) {
                        out.add(missile);
                    }
                }
            }
        }
        return out;
    }

    private static <T> ArrayList<T> bucket(LongBucketTable<T> buckets, double cellSize, double x, double y) {
        long key = key(cell(x, cellSize), cell(y, cellSize));
        return buckets.getOrCreate(key);
    }

    private static int cell(double value, double cellSize) {
        return (int) Math.floor(value / Math.max(1.0, cellSize));
    }

    private static long key(int cellX, int cellY) {
        return (((long) cellX) << 32) ^ (cellY & 0xffffffffL);
    }

    private static final class LongBucketTable<T> {
        private static final float LOAD_FACTOR = 0.70f;
        private long[] keys = new long[256];
        private Object[] values = new Object[256];
        private boolean[] used = new boolean[256];
        private int mask = keys.length - 1;
        private int size = 0;

        void clear() {
            if (size <= 0) return;
            used = new boolean[used.length];
            values = new Object[values.length];
            size = 0;
        }

        boolean isEmpty() {
            return size <= 0;
        }

        @SuppressWarnings("unchecked")
        ArrayList<T> get(long key) {
            int slot = findSlot(key);
            if (slot < 0) return null;
            return (ArrayList<T>) values[slot];
        }

        @SuppressWarnings("unchecked")
        ArrayList<T> getOrCreate(long key) {
            if ((size + 1) > (int) (keys.length * LOAD_FACTOR)) {
                resize(keys.length << 1);
            }
            int slot = insertSlot(key);
            Object current = values[slot];
            if (current == null) {
                ArrayList<T> created = new ArrayList<>();
                values[slot] = created;
                if (!used[slot]) {
                    used[slot] = true;
                    keys[slot] = key;
                    size++;
                }
                return created;
            }
            if (!used[slot]) {
                used[slot] = true;
                keys[slot] = key;
                size++;
            }
            return (ArrayList<T>) current;
        }

        private int findSlot(long key) {
            int idx = mix(key) & mask;
            while (used[idx]) {
                if (keys[idx] == key) return idx;
                idx = (idx + 1) & mask;
            }
            return -1;
        }

        private int insertSlot(long key) {
            int idx = mix(key) & mask;
            while (used[idx]) {
                if (keys[idx] == key) return idx;
                idx = (idx + 1) & mask;
            }
            return idx;
        }

        private void resize(int newCapacity) {
            long[] oldKeys = keys;
            Object[] oldValues = values;
            boolean[] oldUsed = used;
            keys = new long[newCapacity];
            values = new Object[newCapacity];
            used = new boolean[newCapacity];
            mask = newCapacity - 1;
            size = 0;
            for (int i = 0; i < oldKeys.length; i++) {
                if (!oldUsed[i] || oldValues[i] == null) continue;
                long key = oldKeys[i];
                int idx = insertSlot(key);
                keys[idx] = key;
                values[idx] = oldValues[i];
                used[idx] = true;
                size++;
            }
        }

        private static int mix(long key) {
            long z = key;
            z ^= (z >>> 33);
            z *= 0xff51afd7ed558ccdL;
            z ^= (z >>> 33);
            z *= 0xc4ceb9fe1a85ec53L;
            z ^= (z >>> 33);
            return (int) z;
        }
    }
}
