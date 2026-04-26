import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EntityQueryIndex {
    private static final double SHIP_CELL_SIZE = 320.0;
    private static final double MISSILE_CELL_SIZE = 192.0;

    private final Map<Long, ArrayList<Ship>> shipCells = new HashMap<>();
    private final Map<Long, ArrayList<Missile>> missileCells = new HashMap<>();
    private final Map<Integer, Ship> shipById = new HashMap<>();
    private double maxShipBroadPhaseRadius = 0.0;

    public void rebuild(GameContext ctx) {
        shipCells.clear();
        missileCells.clear();
        shipById.clear();
        maxShipBroadPhaseRadius = 0.0;
        if (ctx == null) return;

        for (Ship ship : ctx.ships) {
            if (ship == null || !ship.alive || ship.dying || ship.hp <= 0) continue;
            shipById.put(ship.id, ship);
            maxShipBroadPhaseRadius = Math.max(maxShipBroadPhaseRadius, HullGeometry.broadPhaseRadius(ship));
            bucket(shipCells, SHIP_CELL_SIZE, ship.x, ship.y).add(ship);
        }

        for (Projectile projectile : ctx.projectiles) {
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
                for (Ship ship : bucket) {
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
                for (Ship ship : bucket) {
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
                for (Missile missile : bucket) {
                    if (missile == null) continue;
                    if (GameMath.dist2(missile.x, missile.y, x, y) <= r2) {
                        out.add(missile);
                    }
                }
            }
        }
        return out;
    }

    private static <T> ArrayList<T> bucket(Map<Long, ArrayList<T>> buckets, double cellSize, double x, double y) {
        long key = key(cell(x, cellSize), cell(y, cellSize));
        return buckets.computeIfAbsent(key, ignored -> new ArrayList<>());
    }

    private static int cell(double value, double cellSize) {
        return (int) Math.floor(value / Math.max(1.0, cellSize));
    }

    private static long key(int cellX, int cellY) {
        return (((long) cellX) << 32) ^ (cellY & 0xffffffffL);
    }
}
