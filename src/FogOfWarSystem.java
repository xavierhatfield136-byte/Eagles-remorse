import app.config.GameMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Coarse fog-of-war state for long-range sensor coverage.
 * Friendly ships reveal explored space; the strategic map then shades unknown sectors.
 */
public final class FogOfWarSystem {
    public static final double SENSOR_BASE_RANGE = 1800.0;
    private static final double CELL_SIZE = 160.0;
    private static final double CONTACT_GHOST_TTL_SECONDS = 5.5;
    private static final double CONTACT_GHOST_SAMPLE_DISTANCE = 28.0;
    private static final int CONTACT_GHOST_MAX_TRAIL_POINTS = 10;
    private FogOfWarSystem() {}

    public static final class State {
        private final int worldW;
        private final int worldH;
        private final int cols;
        private final int rows;
        private final int cellCount;
        private final double cellWorldW;
        private final double cellWorldH;
        private final boolean[] explored;
        private final boolean[] visible;
        private final LinkedHashMap<Integer, ContactGhost> contactGhosts;
        private int exploredCount;
        private int visibleCount;

        public State(int worldW, int worldH) {
            this.worldW = Math.max(1, worldW);
            this.worldH = Math.max(1, worldH);
            this.cols = Math.max(1, (int) Math.ceil(this.worldW / CELL_SIZE));
            this.rows = Math.max(1, (int) Math.ceil(this.worldH / CELL_SIZE));
            this.cellCount = this.cols * this.rows;
            this.cellWorldW = this.worldW / (double) this.cols;
            this.cellWorldH = this.worldH / (double) this.rows;
            this.explored = new boolean[cellCount];
            this.visible = new boolean[cellCount];
            this.contactGhosts = new LinkedHashMap<>();
        }

        public int cols() {
            return cols;
        }

        public int rows() {
            return rows;
        }

        public int totalCells() {
            return cellCount;
        }

        public double cellWorldWidth() {
            return cellWorldW;
        }

        public double cellWorldHeight() {
            return cellWorldH;
        }

        public int exploredCount() {
            return exploredCount;
        }

        public int visibleCount() {
            return visibleCount;
        }

        public double exploredFraction() {
            if (cellCount <= 0) return 0.0;
            return exploredCount / (double) cellCount;
        }

        public boolean isExploredCell(int col, int row) {
            int idx = index(col, row);
            return idx >= 0 && explored[idx];
        }

        public boolean isVisibleCell(int col, int row) {
            int idx = index(col, row);
            return idx >= 0 && visible[idx];
        }

        public boolean isExploredAtWorld(double worldX, double worldY) {
            int idx = indexForWorld(worldX, worldY);
            return idx >= 0 && explored[idx];
        }

        public boolean isVisibleAtWorld(double worldX, double worldY) {
            int idx = indexForWorld(worldX, worldY);
            return idx >= 0 && visible[idx];
        }

        public void reset() {
            Arrays.fill(explored, false);
            Arrays.fill(visible, false);
            contactGhosts.clear();
            exploredCount = 0;
            visibleCount = 0;
        }

        public ContactGhost contactGhost(int shipId) {
            return contactGhosts.get(shipId);
        }

        public int activeGhostCount() {
            return contactGhosts.size();
        }

        private void ageGhostContacts(double dt) {
            if (dt <= 0.0 || contactGhosts.isEmpty()) return;
            for (ContactGhost ghost : contactGhosts.values()) {
                if (ghost != null) ghost.age(dt);
            }
        }

        private void pruneExpiredGhostContacts() {
            if (contactGhosts.isEmpty()) return;
            contactGhosts.entrySet().removeIf(e -> e == null || e.getValue() == null || e.getValue().isExpired());
        }

        private void refreshGhostContact(Ship ship) {
            if (ship == null) return;
            ContactGhost ghost = contactGhosts.get(ship.id);
            if (ghost == null || ghost.faction != ship.faction || ghost.role != ship.role) {
                ghost = new ContactGhost(ship, CONTACT_GHOST_TTL_SECONDS);
                contactGhosts.put(ship.id, ghost);
            }
            ghost.refreshFromShip(ship);
        }

        private int indexForWorld(double worldX, double worldY) {
            if (!Double.isFinite(worldX) || !Double.isFinite(worldY)) return -1;
            double maxX = Math.nextDown(Math.max(1.0, worldW));
            double maxY = Math.nextDown(Math.max(1.0, worldH));
            double clampedX = Math.max(0.0, Math.min(worldX, maxX));
            double clampedY = Math.max(0.0, Math.min(worldY, maxY));
            int col = (int) Math.floor(clampedX / Math.max(1.0, cellWorldW));
            int row = (int) Math.floor(clampedY / Math.max(1.0, cellWorldH));
            return index(col, row);
        }

        private int index(int col, int row) {
            if (col < 0 || row < 0 || col >= cols || row >= rows) return -1;
            return row * cols + col;
        }
    }

    public static void reset(GameContext ctx) {
        if (ctx == null || ctx.fogOfWar == null) return;
        ctx.fogOfWar.reset();
    }

    public static void update(GameContext ctx) {
        if (ctx == null || ctx.fogOfWar == null || !isCombatFogEnabled(ctx)) return;
        State fog = ctx.fogOfWar;
        Arrays.fill(fog.visible, false);
        fog.visibleCount = 0;
        fog.ageGhostContacts(GameContext.DT);

        if (ctx.player == null || ctx.player.faction == null || ctx.ships == null) {
            fog.pruneExpiredGhostContacts();
            return;
        }
        Faction perspective = ctx.player.faction;
        for (Ship ship : ctx.ships) {
            if (!isRevealSource(ship, perspective)) continue;
            revealFromSource(fog, ship);
        }
        for (Ship ship : ctx.ships) {
            if (!isTrackableHostile(ship, perspective)) continue;
            if (fog.isVisibleAtWorld(ship.x, ship.y)) {
                fog.refreshGhostContact(ship);
            }
        }
        fog.pruneExpiredGhostContacts();
    }

    public static boolean isCombatFogEnabled(GameContext ctx) {
        return ctx != null
                && ctx.fogOfWar != null
                && ctx.config != null
                && ctx.config.mode != GameMode.SHOWCASE;
    }

    public static int countFriendlySensorSources(GameContext ctx) {
        if (ctx == null || ctx.player == null || ctx.player.faction == null || ctx.ships == null) return 0;
        Faction perspective = ctx.player.faction;
        int count = 0;
        for (Ship ship : ctx.ships) {
            if (isRevealSource(ship, perspective)) count++;
        }
        return count;
    }

    public static int countVisibleHostiles(GameContext ctx) {
        if (ctx == null || ctx.player == null || ctx.player.faction == null || ctx.ships == null || ctx.fogOfWar == null) {
            return 0;
        }
        Faction perspective = ctx.player.faction;
        int count = 0;
        for (Ship ship : ctx.ships) {
            if (!isTrackableHostile(ship, perspective)) continue;
            if (ctx.fogOfWar.isVisibleAtWorld(ship.x, ship.y)) count++;
        }
        return count;
    }

    public static int countLostContactGhosts(GameContext ctx) {
        if (ctx == null || ctx.player == null || ctx.player.faction == null || ctx.ships == null || ctx.fogOfWar == null) {
            return 0;
        }
        Faction perspective = ctx.player.faction;
        int count = 0;
        for (ContactGhost ghost : ctx.fogOfWar.contactGhosts.values()) {
            if (ghost == null || ghost.isExpired()) continue;
            Ship live = findShipById(ctx.ships, ghost.shipId);
            if (live != null && live.alive && !live.dying && live.hp > 0 && live.faction != null
                    && !live.faction.isFriendlyTo(perspective)
                    && ctx.fogOfWar.isVisibleAtWorld(live.x, live.y)) {
                continue;
            }
            count++;
        }
        return count;
    }

    public static String coverageSummary(GameContext ctx) {
        if (ctx == null || ctx.fogOfWar == null) return "";
        int contacts = countVisibleHostiles(ctx);
        int ghosts = countLostContactGhosts(ctx);
        int sources = countFriendlySensorSources(ctx);
        int mapped = (int) Math.round(ctx.fogOfWar.exploredFraction() * 100.0);
        String contactText = contacts + (contacts == 1 ? " live contact" : " live contacts");
        if (ghosts > 0) {
            contactText += " | " + ghosts + (ghosts == 1 ? " ghost trace" : " ghost traces");
        }
        return "SENSOR NET: " + contactText + " | " + sources + " sources | " + mapped + "% mapped";
    }

    public static boolean isVisibleToPerspective(State fog, Faction perspective, Ship ship) {
        if (ship == null || !ship.alive || ship.dying || ship.hp <= 0) return false;
        if (fog == null || perspective == null || ship.faction == null) return true;
        if (ship.faction.isFriendlyTo(perspective)) return true;
        return fog.isVisibleAtWorld(ship.x, ship.y);
    }

    private static boolean isRevealSource(Ship ship, Faction perspective) {
        return ship != null
                && ship.alive
                && !ship.dying
                && ship.hp > 0
                && ship.faction != null
                && perspective != null
                && ship.faction.isFriendlyTo(perspective);
    }

    private static boolean isTrackableHostile(Ship ship, Faction perspective) {
        return ship != null
                && ship.alive
                && !ship.dying
                && ship.hp > 0
                && ship.faction != null
                && perspective != null
                && !ship.faction.isFriendlyTo(perspective);
    }

    private static void revealFromSource(State fog, Ship source) {
        if (fog == null || source == null) return;
        double radius = SENSOR_BASE_RANGE * Math.max(0.16, source.sensorRangeMultiplier());
        if (!Double.isFinite(radius) || radius <= 0.0) return;
        revealCircle(fog, source.x, source.y, radius);
    }

    private static void revealCircle(State fog, double cx, double cy, double radius) {
        if (fog == null || radius <= 0.0 || !Double.isFinite(cx) || !Double.isFinite(cy)) return;
        double cellW = Math.max(1.0, fog.cellWorldWidth());
        double cellH = Math.max(1.0, fog.cellWorldHeight());
        double softenedRadius = radius + Math.max(cellW, cellH) * 0.45;
        double radiusSq = softenedRadius * softenedRadius;

        int minCol = clampCol((int) Math.floor((cx - softenedRadius) / cellW), fog.cols());
        int maxCol = clampCol((int) Math.floor((cx + softenedRadius) / cellW), fog.cols());
        int minRow = clampRow((int) Math.floor((cy - softenedRadius) / cellH), fog.rows());
        int maxRow = clampRow((int) Math.floor((cy + softenedRadius) / cellH), fog.rows());

        for (int row = minRow; row <= maxRow; row++) {
            double cellY = (row + 0.5) * cellH;
            double dy = cellY - cy;
            for (int col = minCol; col <= maxCol; col++) {
                double cellX = (col + 0.5) * cellW;
                double dx = cellX - cx;
                if (dx * dx + dy * dy > radiusSq) continue;
                revealCell(fog, col, row);
            }
        }
    }

    private static void revealCell(State fog, int col, int row) {
        if (fog == null) return;
        int idx = fog.index(col, row);
        if (idx < 0) return;
        if (!fog.visible[idx]) {
            fog.visible[idx] = true;
            fog.visibleCount++;
        }
        if (!fog.explored[idx]) {
            fog.explored[idx] = true;
            fog.exploredCount++;
        }
    }

    private static int clampCol(int col, int cols) {
        if (cols <= 0) return 0;
        if (col < 0) return 0;
        if (col >= cols) return cols - 1;
        return col;
    }

    private static int clampRow(int row, int rows) {
        if (rows <= 0) return 0;
        if (row < 0) return 0;
        if (row >= rows) return rows - 1;
        return row;
    }

    private static Ship findShipById(List<Ship> ships, int shipId) {
        if (ships == null) return null;
        for (Ship ship : ships) {
            if (ship != null && ship.id == shipId) return ship;
        }
        return null;
    }

    public static final class GhostTrailPoint {
        public final double x;
        public final double y;

        public GhostTrailPoint(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    public static final class ContactGhost {
        public final int shipId;
        public final Faction faction;
        public final ShipRole role;
        public final String label;
        public final double maxTtlSeconds;
        public double x;
        public double y;
        public double angle;
        public double radius;
        public double ttlSeconds;
        public final ArrayList<GhostTrailPoint> trail = new ArrayList<>();

        private ContactGhost(Ship ship, double ttlSeconds) {
            this.shipId = ship.id;
            this.faction = ship.faction;
            this.role = ship.role;
            this.label = ship.name;
            this.maxTtlSeconds = Math.max(0.5, ttlSeconds);
            this.ttlSeconds = this.maxTtlSeconds;
            refreshFromShip(ship);
        }

        public void refreshFromShip(Ship ship) {
            if (ship == null) return;
            x = ship.x;
            y = ship.y;
            angle = ship.angle;
            radius = Math.max(8.0, ship.radius);
            ttlSeconds = maxTtlSeconds;

            GhostTrailPoint last = trail.isEmpty() ? null : trail.get(trail.size() - 1);
            double sampleDistance = Math.max(CONTACT_GHOST_SAMPLE_DISTANCE, radius * 0.35);
            if (last == null || distance(last.x, last.y, x, y) >= sampleDistance) {
                trail.add(new GhostTrailPoint(x, y));
                if (trail.size() > CONTACT_GHOST_MAX_TRAIL_POINTS) {
                    trail.remove(0);
                }
            } else {
                trail.set(trail.size() - 1, new GhostTrailPoint(x, y));
            }
        }

        public void age(double dt) {
            if (dt <= 0.0) return;
            ttlSeconds = Math.max(0.0, ttlSeconds - dt);
        }

        public boolean isExpired() {
            return ttlSeconds <= 0.0;
        }

        public double fadeFraction() {
            if (maxTtlSeconds <= 0.0) return 0.0;
            return Math.max(0.0, Math.min(1.0, ttlSeconds / maxTtlSeconds));
        }

        public double renderCullRadius() {
            double base = Math.max(radius + 24.0, radius * 2.0);
            for (GhostTrailPoint point : trail) {
                if (point == null) continue;
                double dist = distance(x, y, point.x, point.y) + radius + 24.0;
                if (dist > base) base = dist;
            }
            return base;
        }

        private static double distance(double x1, double y1, double x2, double y2) {
            double dx = x2 - x1;
            double dy = y2 - y1;
            return Math.hypot(dx, dy);
        }
    }
}
