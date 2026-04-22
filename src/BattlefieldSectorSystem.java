import app.config.GameMode;
import app.config.GameConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

public final class BattlefieldSectorSystem {
    private static final int RESOURCE_RUSH_COLUMNS = 3;
    private static final int RESOURCE_RUSH_ROWS = 1;
    private static final int FOUR_TEAM_GRID_COLUMNS = 3;
    private static final int FOUR_TEAM_GRID_ROWS = 3;
    private static final int INTER_SECTOR_GAP = 2200;

    private static final List<SectorDefinition> RESOURCE_RUSH_SECTORS = List.of(
            new SectorDefinition("blue-home", "BLUE HOME", 0, 0, 1, 1, Faction.ALLY),
            new SectorDefinition("central-front", "CENTRAL FRONT", 1, 0, 1, 1, null),
            new SectorDefinition("red-home", "RED HOME", 2, 0, 1, 1, Faction.ENEMY)
    );

    private static final List<SectorDefinition> FOUR_TEAM_SECTORS = List.of(
            new SectorDefinition("blue-orbit", "BLUE ORBIT", 0, 0, 1, 1, Faction.ALLY),
            new SectorDefinition("north-link", "NORTH LINK", 1, 0, 1, 1, null),
            new SectorDefinition("red-orbit", "RED ORBIT", 2, 0, 1, 1, Faction.ENEMY),
            new SectorDefinition("west-link", "WEST LINK", 0, 1, 1, 1, null),
            new SectorDefinition("central-warzone", "CENTRAL WARZONE", 1, 1, 1, 1, null),
            new SectorDefinition("east-link", "EAST LINK", 2, 1, 1, 1, null),
            new SectorDefinition("green-orbit", "GREEN ORBIT", 0, 2, 1, 1, Faction.TEAM_C),
            new SectorDefinition("south-link", "SOUTH LINK", 1, 2, 1, 1, null),
            new SectorDefinition("yellow-orbit", "YELLOW ORBIT", 2, 2, 1, 1, Faction.TEAM_D)
    );
    private static final Map<String, List<String>> RESOURCE_RUSH_ADJACENCY = Map.of(
            "blue-home", List.of("central-front"),
            "central-front", List.of("blue-home", "red-home"),
            "red-home", List.of("central-front")
    );
    private static final Map<String, List<String>> FOUR_TEAM_ADJACENCY = Map.of(
            "blue-orbit", List.of("north-link", "west-link"),
            "north-link", List.of("blue-orbit", "central-warzone", "red-orbit"),
            "red-orbit", List.of("east-link", "north-link"),
            "west-link", List.of("blue-orbit", "central-warzone", "green-orbit"),
            "central-warzone", List.of("north-link", "east-link", "south-link", "west-link"),
            "east-link", List.of("red-orbit", "central-warzone", "yellow-orbit"),
            "green-orbit", List.of("west-link", "south-link"),
            "south-link", List.of("green-orbit", "central-warzone", "yellow-orbit"),
            "yellow-orbit", List.of("east-link", "south-link")
    );
    private static final double SNAPSHOT_CACHE_WINDOW_SECONDS = 0.25;
    private static final WeakHashMap<GameContext, SnapshotCache> SNAPSHOT_CACHE = new WeakHashMap<>();

    private BattlefieldSectorSystem() {}

    private static final class SnapshotCache {
        double bucketStart;
        int shipCount;
        List<SectorSnapshot> snapshots = Collections.emptyList();
    }

    public enum ControlState {
        EMPTY,
        CONTROLLED,
        CONTESTED
    }

    public static final class SectorDefinition {
        public final String id;
        public final String label;
        public final double minXFrac;
        public final double minYFrac;
        public final double maxXFrac;
        public final double maxYFrac;
        public final Faction anchorFaction;
        public final int gridColumn;
        public final int gridRow;
        public final int columnSpan;
        public final int rowSpan;

        SectorDefinition(String id,
                         String label,
                         int gridColumn,
                         int gridRow,
                         int columnSpan,
                         int rowSpan,
                         Faction anchorFaction) {
            this.id = (id == null || id.isBlank()) ? "sector" : id.trim();
            this.label = (label == null || label.isBlank()) ? this.id.toUpperCase(Locale.US) : label.trim();
            this.gridColumn = Math.max(0, gridColumn);
            this.gridRow = Math.max(0, gridRow);
            this.columnSpan = Math.max(1, columnSpan);
            this.rowSpan = Math.max(1, rowSpan);
            int cols = Math.max(1, defaultGridColumns(anchorFaction, this.id));
            int rows = Math.max(1, defaultGridRows(anchorFaction, this.id));
            this.minXFrac = Math.max(0.0, Math.min(1.0, this.gridColumn / (double) cols));
            this.minYFrac = Math.max(0.0, Math.min(1.0, this.gridRow / (double) rows));
            this.maxXFrac = Math.max(this.minXFrac, Math.min(1.0, (this.gridColumn + this.columnSpan) / (double) cols));
            this.maxYFrac = Math.max(this.minYFrac, Math.min(1.0, (this.gridRow + this.rowSpan) / (double) rows));
            this.anchorFaction = anchorFaction;
        }

        public boolean containsNormalized(double nx, double ny) {
            return false;
        }

        public boolean containsWorld(GameContext ctx, double wx, double wy) {
            if (ctx == null || ctx.WORLD_W <= 0 || ctx.WORLD_H <= 0) return false;
            double minX = minWorldX(ctx);
            double minY = minWorldY(ctx);
            double maxX = maxWorldX(ctx);
            double maxY = maxWorldY(ctx);
            boolean xInside = wx >= minX && (wx < maxX || maxX >= ctx.WORLD_W - 1e-6);
            boolean yInside = wy >= minY && (wy < maxY || maxY >= ctx.WORLD_H - 1e-6);
            return xInside && yInside;
        }

        public double centerX(GameContext ctx) {
            return (minWorldX(ctx) + maxWorldX(ctx)) * 0.5;
        }

        public double centerY(GameContext ctx) {
            return (minWorldY(ctx) + maxWorldY(ctx)) * 0.5;
        }

        public double minWorldX(GameContext ctx) {
            double width = sectorWidth(ctx);
            double gap = interSectorGap(ctx);
            return gridColumn * (width + gap);
        }

        public double minWorldY(GameContext ctx) {
            double height = sectorHeight(ctx);
            double gap = interSectorGap(ctx);
            return gridRow * (height + gap);
        }

        public double maxWorldX(GameContext ctx) {
            double width = sectorWidth(ctx);
            double gap = interSectorGap(ctx);
            return minWorldX(ctx) + columnSpan * width + Math.max(0, columnSpan - 1) * gap;
        }

        public double maxWorldY(GameContext ctx) {
            double height = sectorHeight(ctx);
            double gap = interSectorGap(ctx);
            return minWorldY(ctx) + rowSpan * height + Math.max(0, rowSpan - 1) * gap;
        }

        public double widthWorld(GameContext ctx) {
            return Math.max(1.0, maxWorldX(ctx) - minWorldX(ctx));
        }

        public double heightWorld(GameContext ctx) {
            return Math.max(1.0, maxWorldY(ctx) - minWorldY(ctx));
        }
    }

    public static int recommendedWorldWidth(GameConfig config) {
        if (config == null) return 5000;
        long sectorWidth = Math.max(1, config.worldW);
        return switch (config.mode) {
            case RESOURCE_RUSH -> safeDimension(sectorWidth * RESOURCE_RUSH_COLUMNS
                    + (long) interSectorGap(config) * (RESOURCE_RUSH_COLUMNS - 1L));
            case FOUR_TEAM_DOMINATION -> safeDimension(sectorWidth * FOUR_TEAM_GRID_COLUMNS
                    + (long) interSectorGap(config) * (FOUR_TEAM_GRID_COLUMNS - 1L));
            default -> safeDimension(sectorWidth);
        };
    }

    public static int recommendedWorldHeight(GameConfig config) {
        if (config == null) return 5000;
        long sectorHeight = Math.max(1, config.worldH);
        return switch (config.mode) {
            case FOUR_TEAM_DOMINATION -> safeDimension(sectorHeight * FOUR_TEAM_GRID_ROWS
                    + (long) interSectorGap(config) * (FOUR_TEAM_GRID_ROWS - 1L));
            default -> safeDimension(sectorHeight);
        };
    }

    public static final class SectorSnapshot {
        public final SectorDefinition sector;
        public final ControlState controlState;
        public final Faction dominantFaction;
        public final int bluePresence;
        public final int redPresence;
        public final int greenPresence;
        public final int yellowPresence;
        public final int occupiedTeams;
        public final int dominantPresence;
        public final int secondaryPresence;

        SectorSnapshot(SectorDefinition sector,
                       ControlState controlState,
                       Faction dominantFaction,
                       int bluePresence,
                       int redPresence,
                       int greenPresence,
                       int yellowPresence,
                       int occupiedTeams,
                       int dominantPresence,
                       int secondaryPresence) {
            this.sector = sector;
            this.controlState = controlState == null ? ControlState.EMPTY : controlState;
            this.dominantFaction = dominantFaction;
            this.bluePresence = Math.max(0, bluePresence);
            this.redPresence = Math.max(0, redPresence);
            this.greenPresence = Math.max(0, greenPresence);
            this.yellowPresence = Math.max(0, yellowPresence);
            this.occupiedTeams = Math.max(0, occupiedTeams);
            this.dominantPresence = Math.max(0, dominantPresence);
            this.secondaryPresence = Math.max(0, secondaryPresence);
        }

        public int presenceForTeamId(int teamId) {
            return switch (teamId) {
                case 0 -> bluePresence;
                case 1 -> redPresence;
                case 2 -> greenPresence;
                case 3 -> yellowPresence;
                default -> 0;
            };
        }
    }

    public static boolean isEnabled(GameContext ctx) {
        if (ctx == null || ctx.config == null) return false;
        return ctx.config.mode == GameMode.RESOURCE_RUSH
                || ctx.config.mode == GameMode.FOUR_TEAM_DOMINATION;
    }

    public static List<SectorDefinition> definitions(GameContext ctx) {
        if (!isEnabled(ctx) || ctx == null || ctx.config == null) return Collections.emptyList();
        return switch (ctx.config.mode) {
            case RESOURCE_RUSH -> RESOURCE_RUSH_SECTORS;
            case FOUR_TEAM_DOMINATION -> FOUR_TEAM_SECTORS;
            default -> Collections.emptyList();
        };
    }

    public static SectorDefinition findSector(GameContext ctx, String sectorId) {
        if (sectorId == null || sectorId.isBlank()) return null;
        for (SectorDefinition sector : definitions(ctx)) {
            if (sector.id.equalsIgnoreCase(sectorId.trim())) {
                return sector;
            }
        }
        return null;
    }

    public static SectorDefinition sectorAtNormalized(GameContext ctx, double nx, double ny) {
        if (ctx == null || !Double.isFinite(nx) || !Double.isFinite(ny)) return null;
        double worldX = nx * Math.max(1.0, ctx.WORLD_W);
        double worldY = ny * Math.max(1.0, ctx.WORLD_H);
        for (SectorDefinition sector : definitions(ctx)) {
            if (sector.containsWorld(ctx, worldX, worldY)) {
                return sector;
            }
        }
        return null;
    }

    public static SectorDefinition sectorAt(GameContext ctx, double worldX, double worldY) {
        if (!isEnabled(ctx) || ctx == null) return null;
        return sectorAtNormalized(ctx, worldX / Math.max(1.0, ctx.WORLD_W), worldY / Math.max(1.0, ctx.WORLD_H));
    }

    public static SectorDefinition currentSector(GameContext ctx) {
        if (ctx == null || ctx.player == null) return null;
        return sectorAt(ctx, ctx.player.x, ctx.player.y);
    }

    public static SectorDefinition loadedSector(GameContext ctx) {
        if (ctx == null || ctx.ui == null) return null;
        SectorDefinition loaded = findSector(ctx, ctx.ui.loadedSectorId);
        return (loaded != null) ? loaded : currentSector(ctx);
    }

    public static SectorDefinition selectedSector(GameContext ctx) {
        if (ctx == null || ctx.ui == null) return null;
        return findSector(ctx, ctx.ui.selectedSectorId);
    }

    public static void clearSelection(GameContext ctx) {
        if (ctx == null || ctx.ui == null) return;
        ctx.ui.selectedSectorId = "";
    }

    public static void ensureSelection(GameContext ctx) {
        if (!isEnabled(ctx)) {
            clearSelection(ctx);
            return;
        }
        if (selectedSector(ctx) != null) return;
        SectorDefinition current = currentSector(ctx);
        if (current != null) {
            selectSector(ctx, current.id);
        }
    }

    public static void ensureLoadedSector(GameContext ctx) {
        if (ctx == null || ctx.ui == null) return;
        if (!isEnabled(ctx)) {
            ctx.ui.loadedSectorId = "";
            return;
        }
        SectorDefinition loaded = loadedSector(ctx);
        if (loaded != null) {
            ctx.ui.loadedSectorId = loaded.id;
            return;
        }
        SectorDefinition current = currentSector(ctx);
        ctx.ui.loadedSectorId = (current == null) ? "" : current.id;
    }

    public static void selectSector(GameContext ctx, String sectorId) {
        if (ctx == null || ctx.ui == null) return;
        SectorDefinition sector = findSector(ctx, sectorId);
        ctx.ui.selectedSectorId = (sector == null) ? "" : sector.id;
    }

    public static void selectCurrentSector(GameContext ctx) {
        SectorDefinition current = currentSector(ctx);
        if (current != null) {
            selectSector(ctx, current.id);
        }
    }

    public static void setLoadedSector(GameContext ctx, String sectorId) {
        if (ctx == null || ctx.ui == null) return;
        SectorDefinition sector = findSector(ctx, sectorId);
        ctx.ui.loadedSectorId = (sector == null) ? "" : sector.id;
    }

    public static void invalidateSnapshots(GameContext ctx) {
        if (ctx == null) return;
        SNAPSHOT_CACHE.remove(ctx);
    }

    public static List<SectorSnapshot> snapshots(GameContext ctx) {
        List<SectorDefinition> sectors = definitions(ctx);
        if (sectors.isEmpty()) return Collections.emptyList();

        double bucket = snapshotBucketStart(ctx);
        int shipCount = (ctx == null || ctx.ships == null) ? 0 : ctx.ships.size();
        SnapshotCache cache = SNAPSHOT_CACHE.get(ctx);
        if (cache != null
                && Math.abs(cache.bucketStart - bucket) <= 1e-9
                && cache.shipCount == shipCount) {
            return cache.snapshots;
        }

        ArrayList<SectorSnapshot> out = new ArrayList<>(sectors.size());
        for (SectorDefinition sector : sectors) {
            int[] presenceByTeam = new int[4];
            if (ctx != null && ctx.ships != null) {
                for (Ship ship : ctx.ships) {
                    if (ship == null || !ship.alive || ship.dying || ship.hp <= 0) continue;
                    Faction faction = ship.faction;
                    if (faction == null) continue;
                    int teamId = faction.teamId();
                    if (teamId < 0 || teamId >= presenceByTeam.length) continue;
                    if (!sector.containsWorld(ctx, ship.x, ship.y)) continue;
                    presenceByTeam[teamId] += presenceWeight(ship);
                }
            }
            for (int teamId = 0; teamId < presenceByTeam.length; teamId++) {
                presenceByTeam[teamId] += OffSectorSimulationSystem.collapsedPresence(ctx, sector.id, teamId);
            }

            int topTeam = -1;
            int topPresence = 0;
            int secondPresence = 0;
            int occupiedTeams = 0;
            for (int teamId = 0; teamId < presenceByTeam.length; teamId++) {
                int presence = presenceByTeam[teamId];
                if (presence <= 0) continue;
                occupiedTeams++;
                if (presence > topPresence) {
                    secondPresence = topPresence;
                    topPresence = presence;
                    topTeam = teamId;
                } else if (presence > secondPresence) {
                    secondPresence = presence;
                }
            }

            ControlState state;
            Faction dominantFaction = topTeam >= 0 ? Faction.forTeamId(topTeam) : null;
            if (occupiedTeams <= 0) {
                state = ControlState.EMPTY;
            } else if (occupiedTeams == 1 || topPresence >= secondPresence * 2) {
                state = ControlState.CONTROLLED;
            } else {
                state = ControlState.CONTESTED;
            }

            out.add(new SectorSnapshot(
                    sector,
                    state,
                    dominantFaction,
                    presenceByTeam[0],
                    presenceByTeam[1],
                    presenceByTeam[2],
                    presenceByTeam[3],
                    occupiedTeams,
                    topPresence,
                    secondPresence
            ));
        }
        SnapshotCache next = new SnapshotCache();
        next.bucketStart = bucket;
        next.shipCount = shipCount;
        next.snapshots = Collections.unmodifiableList(out);
        SNAPSHOT_CACHE.put(ctx, next);
        return next.snapshots;
    }

    public static SectorSnapshot snapshotForSector(GameContext ctx, String sectorId) {
        if (sectorId == null || sectorId.isBlank()) return null;
        for (SectorSnapshot snapshot : snapshots(ctx)) {
            if (snapshot != null && snapshot.sector != null && snapshot.sector.id.equalsIgnoreCase(sectorId.trim())) {
                return snapshot;
            }
        }
        return null;
    }

    public static SectorSnapshot currentSectorSnapshot(GameContext ctx) {
        SectorDefinition current = currentSector(ctx);
        return current == null ? null : snapshotForSector(ctx, current.id);
    }

    public static SectorSnapshot selectedSectorSnapshot(GameContext ctx) {
        SectorDefinition selected = selectedSector(ctx);
        return selected == null ? null : snapshotForSector(ctx, selected.id);
    }

    public static String absoluteStatusLabel(SectorSnapshot snapshot) {
        if (snapshot == null) return "";
        if (snapshot.controlState == ControlState.EMPTY) return "UNCOMMITTED";
        if (snapshot.controlState == ControlState.CONTESTED) return "CONTESTED";
        if (snapshot.dominantFaction == null) return "CONTROLLED";
        return snapshot.dominantFaction.teamName().toUpperCase(Locale.US) + " CONTROL";
    }

    public static String relativeStatusLabel(GameContext ctx, SectorSnapshot snapshot) {
        if (snapshot == null) return "";
        if (snapshot.controlState == ControlState.EMPTY) return "Empty";
        if (snapshot.controlState == ControlState.CONTESTED) return "Contested";
        Faction playerFaction = (ctx != null && ctx.player != null && ctx.player.faction != null)
                ? ctx.player.faction
                : Faction.ALLY;
        if (snapshot.dominantFaction != null && snapshot.dominantFaction.isFriendlyTo(playerFaction)) {
            return "Friendly";
        }
        return "Hostile";
    }

    public static String currentSectorLine(GameContext ctx) {
        if (!isEnabled(ctx)) return "";
        SectorSnapshot current = currentSectorSnapshot(ctx);
        if (current == null || current.sector == null) return "";
        StringBuilder out = new StringBuilder("Sector: ");
        out.append(current.sector.label);
        String status = relativeStatusLabel(ctx, current);
        if (!status.isBlank()) {
            out.append("   ").append(status);
        }
        SectorDefinition selected = selectedSector(ctx);
        if (selected != null && !selected.id.equalsIgnoreCase(current.sector.id)) {
            out.append("   Target: ").append(selected.label);
        }
        return out.toString();
    }

    static SectorDefinition homeSector(GameContext ctx, Faction faction) {
        if (!isEnabled(ctx) || faction == null) return null;
        Ship base = TeamSystem.getBaseForTeam(ctx, faction);
        SectorDefinition baseSector = (base == null) ? null : sectorAt(ctx, base.x, base.y);
        if (baseSector != null) return baseSector;
        for (SectorDefinition sector : definitions(ctx)) {
            if (sector.anchorFaction != null && sector.anchorFaction.teamId() == faction.teamId()) {
                return sector;
            }
        }
        return null;
    }

    static List<SectorDefinition> adjacentSectors(GameContext ctx, SectorDefinition sector) {
        if (ctx == null || sector == null) return Collections.emptyList();
        List<String> ids = adjacencyIds(ctx, sector.id);
        if (ids.isEmpty()) return Collections.emptyList();
        ArrayList<SectorDefinition> out = new ArrayList<>(ids.size());
        for (String id : ids) {
            SectorDefinition adjacent = findSector(ctx, id);
            if (adjacent != null) out.add(adjacent);
        }
        return out;
    }

    static int hopDistance(GameContext ctx, SectorDefinition from, SectorDefinition to) {
        if (ctx == null || from == null || to == null) return -1;
        if (from.id.equalsIgnoreCase(to.id)) return 0;
        Map<String, Integer> distances = new HashMap<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(from.id);
        distances.put(from.id, 0);

        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            int distance = distances.getOrDefault(id, 0);
            for (String nextId : adjacencyIds(ctx, id)) {
                if (distances.containsKey(nextId)) continue;
                distances.put(nextId, distance + 1);
                if (nextId.equalsIgnoreCase(to.id)) return distance + 1;
                queue.addLast(nextId);
            }
        }
        return -1;
    }

    static boolean isFriendlyControl(SectorSnapshot snapshot, Faction faction) {
        if (snapshot == null || faction == null) return false;
        return snapshot.controlState == ControlState.CONTROLLED
                && snapshot.dominantFaction != null
                && snapshot.dominantFaction.isFriendlyTo(faction);
    }

    static boolean isThreatenedForFaction(SectorSnapshot snapshot, Faction faction) {
        if (snapshot == null || faction == null) return false;
        if (snapshot.controlState == ControlState.CONTESTED) return snapshot.occupiedTeams > 0;
        if (snapshot.controlState == ControlState.EMPTY) return false;
        return snapshot.dominantFaction != null && !snapshot.dominantFaction.isFriendlyTo(faction);
    }

    static SectorDefinition objectiveSector(GameContext ctx, Faction faction, double fromX, double fromY) {
        if (!isEnabled(ctx) || faction == null) return sectorAt(ctx, fromX, fromY);

        SectorDefinition home = homeSector(ctx, faction);
        SectorSnapshot homeSnapshot = (home == null) ? null : snapshotForSector(ctx, home.id);
        if (isThreatenedForFaction(homeSnapshot, faction)) return home;

        SectorDefinition selected = selectedObjectiveSector(ctx, faction);
        if (selected != null) return selected;

        if (ctx == null || ctx.config == null) return home;
        return switch (ctx.config.mode) {
            case RESOURCE_RUSH -> objectiveSectorResourceRush(ctx, faction, fromX, fromY);
            case FOUR_TEAM_DOMINATION -> objectiveSectorFourTeam(ctx, faction, fromX, fromY);
            default -> home;
        };
    }

    static SectorDefinition navigationSector(GameContext ctx, Faction faction, double fromX, double fromY) {
        if (!isEnabled(ctx) || faction == null) return sectorAt(ctx, fromX, fromY);
        SectorDefinition current = sectorAt(ctx, fromX, fromY);
        if (current == null) current = homeSector(ctx, faction);
        SectorDefinition objective = objectiveSector(ctx, faction, fromX, fromY);
        if (objective == null) return current;
        if (current == null || current.id.equalsIgnoreCase(objective.id)) return objective;
        SectorDefinition next = nextSectorToward(ctx, current, objective);
        return (next == null) ? objective : next;
    }

    static double[] navigationPoint(GameContext ctx, Faction faction, double fromX, double fromY, int seed) {
        SectorDefinition sector = navigationSector(ctx, faction, fromX, fromY);
        if (ctx == null || sector == null) return null;
        double spanX = sector.widthWorld(ctx);
        double spanY = sector.heightWorld(ctx);
        double jitterX = Math.max(70.0, Math.min(280.0, spanX * 0.14));
        double jitterY = Math.max(70.0, Math.min(280.0, spanY * 0.14));
        long bucket = (long) Math.floor(Math.max(0.0, ctx.battleElapsed) * 1.25);
        double ox = (stableNoise(seed * 31L + bucket * 17L) - 0.5) * jitterX * 2.0;
        double oy = (stableNoise(seed * 47L + bucket * 29L) - 0.5) * jitterY * 2.0;
        return clampPointToSector(ctx, sector, sector.centerX(ctx) + ox, sector.centerY(ctx) + oy, 70.0);
    }

    static SectorDefinition nextWarpHop(GameContext ctx, SectorDefinition from, SectorDefinition goal) {
        if (ctx == null || from == null) return goal;
        if (goal == null) return from;
        if (from.id.equalsIgnoreCase(goal.id)) return goal;
        SectorDefinition next = nextSectorToward(ctx, from, goal);
        return (next == null) ? goal : next;
    }

    static double[] warpArrivalPoint(GameContext ctx,
                                     SectorDefinition from,
                                     SectorDefinition to,
                                     UiState.TacticalSectorScalePreset scalePreset) {
        if (ctx == null || to == null) return null;
        double padding = sectorWarpPadding(ctx, to, scalePreset);
        if (from == null || from.id.equalsIgnoreCase(to.id)) {
            return clampPointToSector(ctx, to, to.centerX(ctx), to.centerY(ctx), padding);
        }

        double dx = to.centerX(ctx) - from.centerX(ctx);
        double dy = to.centerY(ctx) - from.centerY(ctx);
        double len = Math.hypot(dx, dy);
        if (len <= 1e-6) {
            return clampPointToSector(ctx, to, to.centerX(ctx), to.centerY(ctx), padding);
        }

        double nx = dx / len;
        double ny = dy / len;
        double spanX = to.widthWorld(ctx);
        double spanY = to.heightWorld(ctx);
        double offsetX = Math.max(0.0, spanX * 0.5 - padding);
        double offsetY = Math.max(0.0, spanY * 0.5 - padding);
        double arrivalX = to.centerX(ctx) - nx * offsetX;
        double arrivalY = to.centerY(ctx) - ny * offsetY;
        return clampPointToSector(ctx, to, arrivalX, arrivalY, padding);
    }

    static double sectorTravelZoom(UiState.TacticalSectorScalePreset scalePreset) {
        UiState.TacticalSectorScalePreset preset =
                (scalePreset == null) ? UiState.TacticalSectorScalePreset.STANDARD : scalePreset;
        return GameContext.DEFAULT_ZOOM * preset.zoomMultiplier();
    }

    static double[] stagingPoint(GameContext ctx, Faction faction) {
        if (!isEnabled(ctx) || ctx == null || faction == null) return null;
        SectorDefinition home = homeSector(ctx, faction);
        if (home == null) return null;

        Ship base = TeamSystem.getBaseForTeam(ctx, faction);
        double anchorX = (base == null) ? home.centerX(ctx) : base.x;
        double anchorY = (base == null) ? home.centerY(ctx) : base.y;

        SectorDefinition next = navigationSector(ctx, faction, anchorX, anchorY);
        double goalX = (next == null) ? home.centerX(ctx) : next.centerX(ctx);
        double goalY = (next == null) ? home.centerY(ctx) : next.centerY(ctx);

        double dx = goalX - anchorX;
        double dy = goalY - anchorY;
        double len = Math.hypot(dx, dy);
        if (len <= 1e-6) {
            dx = ctx.WORLD_W * 0.5 - anchorX;
            dy = ctx.WORLD_H * 0.5 - anchorY;
            len = Math.hypot(dx, dy);
        }
        if (len <= 1e-6) {
            dx = (faction.teamId() == 1 || faction.teamId() == 3) ? -1.0 : 1.0;
            dy = ((faction.teamId() & 1) == 0) ? -0.35 : 0.35;
            len = Math.hypot(dx, dy);
        }

        double spanX = home.widthWorld(ctx);
        double spanY = home.heightWorld(ctx);
        double forward = Math.min(360.0, Math.max(180.0, Math.min(spanX, spanY) * 0.24));
        double lateral = Math.min(180.0, Math.max(70.0, Math.min(spanX, spanY) * 0.10));
        double nx = dx / len;
        double ny = dy / len;
        double side = ((faction.teamId() & 1) == 0) ? -1.0 : 1.0;
        double px = anchorX + nx * forward - ny * lateral * side;
        double py = anchorY + ny * forward + nx * lateral * side;
        return clampPointToSector(ctx, home, px, py, 80.0);
    }

    static int presenceWeight(Ship ship) {
        if (ship == null) return 0;
        return presenceWeightForRole(ship.role);
    }

    static int presenceWeightForRole(ShipRole role) {
        if (role == null) return 0;
        return switch (role) {
            case BASE, MOTHERSHIP -> 8;
            case TRANSPORT_TITAN, BULWARK_TITAN, CARRIER_SUPPORT_TITAN, VANGUARD_TITAN,
                    INTERDICTION_TITAN, COMMAND_INTEL_TITAN, BOARDING_RECOVERY_TITAN,
                    ARTILLERY_TITAN, SHIELD_BASTION_TITAN, FLEET_TELEPORTER_TITAN,
                    ELITE_SUPERSHIP_COMMAND_TITAN, ELITE_REINFORCEMENTS_TITAN,
                    MOBILE_STATION_TITAN, HYPERWEAPON_TITAN -> 5;
            case SUPERSHIP, DREADNOUGHT, BATTLESHIP, BATTLECRUISER, CARRIER, DRONE_CARRIER -> 3;
            case LIGHT_CRUISER, MEDIUM_CRUISER, CRUISER, STEALTH_SHIP,
                    ARTILLERY_SHIP, MISSILE_BOAT, CIWS_CORVETTE, FRIGATE, HAULER, TRANSPORT -> 2;
            default -> 1;
        };
    }

    private static double snapshotBucketStart(GameContext ctx) {
        double battleElapsed = (ctx == null) ? 0.0 : Math.max(0.0, ctx.battleElapsed);
        return Math.floor(battleElapsed / SNAPSHOT_CACHE_WINDOW_SECONDS) * SNAPSHOT_CACHE_WINDOW_SECONDS;
    }

    private static List<String> adjacencyIds(GameContext ctx, String sectorId) {
        if (sectorId == null || sectorId.isBlank() || ctx == null || ctx.config == null) return Collections.emptyList();
        Map<String, List<String>> adjacency = switch (ctx.config.mode) {
            case RESOURCE_RUSH -> RESOURCE_RUSH_ADJACENCY;
            case FOUR_TEAM_DOMINATION -> FOUR_TEAM_ADJACENCY;
            default -> Collections.emptyMap();
        };
        return adjacency.getOrDefault(sectorId, Collections.emptyList());
    }

    private static SectorDefinition selectedObjectiveSector(GameContext ctx, Faction faction) {
        if (ctx == null || faction == null || ctx.player == null || ctx.player.faction == null) return null;
        SectorDefinition selected = selectedSector(ctx);
        if (selected == null) return null;
        return faction.isFriendlyTo(ctx.player.faction) ? selected : null;
    }

    private static SectorDefinition objectiveSectorResourceRush(GameContext ctx, Faction faction, double fromX, double fromY) {
        SectorDefinition center = findSector(ctx, "central-front");
        if (center == null) return homeSector(ctx, faction);
        SectorSnapshot centerSnapshot = snapshotForSector(ctx, center.id);
        if (!isFriendlyControl(centerSnapshot, faction)) return center;
        SectorDefinition hostileHome = nearestHostileHomeSector(ctx, faction, fromX, fromY);
        return (hostileHome == null) ? center : hostileHome;
    }

    private static SectorDefinition objectiveSectorFourTeam(GameContext ctx, Faction faction, double fromX, double fromY) {
        SectorDefinition center = findSector(ctx, "central-warzone");
        if (center != null) {
            SectorSnapshot centerSnapshot = snapshotForSector(ctx, center.id);
            if (!isFriendlyControl(centerSnapshot, faction)) return center;
        }
        SectorDefinition hostileHome = nearestHostileHomeSector(ctx, faction, fromX, fromY);
        if (hostileHome != null) return hostileHome;
        return (center == null) ? homeSector(ctx, faction) : center;
    }

    private static SectorDefinition nearestHostileHomeSector(GameContext ctx, Faction faction, double fromX, double fromY) {
        SectorDefinition origin = sectorAt(ctx, fromX, fromY);
        if (origin == null) origin = homeSector(ctx, faction);

        SectorDefinition best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (SectorDefinition sector : definitions(ctx)) {
            if (sector.anchorFaction == null || sector.anchorFaction.isFriendlyTo(faction)) continue;
            SectorSnapshot snapshot = snapshotForSector(ctx, sector.id);
            double score = 0.0;
            if (isFriendlyControl(snapshot, faction)) score += 1000.0;
            int hops = hopDistance(ctx, origin, sector);
            if (hops >= 0) score += hops * 120.0;
            score += Math.hypot(sector.centerX(ctx) - fromX, sector.centerY(ctx) - fromY) * 0.01;
            if (score < bestScore) {
                bestScore = score;
                best = sector;
            }
        }
        return best;
    }

    private static SectorDefinition nextSectorToward(GameContext ctx, SectorDefinition from, SectorDefinition to) {
        if (ctx == null || from == null || to == null) return null;
        if (from.id.equalsIgnoreCase(to.id)) return to;

        Map<String, String> parent = new HashMap<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(from.id);
        parent.put(from.id, null);

        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            if (id.equalsIgnoreCase(to.id)) break;
            for (String nextId : adjacencyIds(ctx, id)) {
                if (parent.containsKey(nextId)) continue;
                parent.put(nextId, id);
                queue.addLast(nextId);
            }
        }

        if (!parent.containsKey(to.id)) return null;
        String step = to.id;
        while (true) {
            String prev = parent.get(step);
            if (prev == null) return null;
            if (prev.equalsIgnoreCase(from.id)) return findSector(ctx, step);
            step = prev;
        }
    }

    private static double sectorWarpPadding(GameContext ctx,
                                            SectorDefinition sector,
                                            UiState.TacticalSectorScalePreset scalePreset) {
        if (ctx == null || sector == null) return 80.0;
        UiState.TacticalSectorScalePreset preset =
                (scalePreset == null) ? UiState.TacticalSectorScalePreset.STANDARD : scalePreset;
        double spanX = sector.widthWorld(ctx);
        double spanY = sector.heightWorld(ctx);
        double minSpan = Math.max(120.0, Math.min(spanX, spanY));
        return switch (preset) {
            case COMPACT -> Math.max(70.0, minSpan * 0.26);
            case EXPANDED -> Math.max(40.0, minSpan * 0.12);
            default -> Math.max(56.0, minSpan * 0.18);
        };
    }

    static double[] clampToLoadedSectorBounds(GameContext ctx,
                                              SectorDefinition sector,
                                              UiState.TacticalSectorScalePreset scalePreset,
                                              double x,
                                              double y) {
        return clampPointToSector(ctx, sector, x, y, 0.0);
    }

    private static double[] clampPointToSector(GameContext ctx, SectorDefinition sector, double x, double y, double padding) {
        if (ctx == null || sector == null) return new double[]{x, y};
        double minX = sector.minWorldX(ctx) + padding;
        double maxX = sector.maxWorldX(ctx) - padding;
        double minY = sector.minWorldY(ctx) + padding;
        double maxY = sector.maxWorldY(ctx) - padding;
        if (maxX < minX) {
            double midX = sector.centerX(ctx);
            minX = midX;
            maxX = midX;
        }
        if (maxY < minY) {
            double midY = sector.centerY(ctx);
            minY = midY;
            maxY = midY;
        }
        return new double[]{
                GameMath.clamp(x, minX, maxX),
                GameMath.clamp(y, minY, maxY)
        };
    }

    private static double stableNoise(long seed) {
        double value = Math.sin(seed * 12.9898 + 78.233) * 43758.5453123;
        return value - Math.floor(value);
    }

    private static int interSectorGap(GameContext ctx) {
        return interSectorGap((ctx == null) ? null : ctx.config);
    }

    static int interSectorGap(GameConfig config) {
        return INTER_SECTOR_GAP;
    }

    private static double sectorWidth(GameContext ctx) {
        if (ctx == null || ctx.config == null) return 1.0;
        return Math.max(1.0, ctx.config.worldW);
    }

    private static double sectorHeight(GameContext ctx) {
        if (ctx == null || ctx.config == null) return 1.0;
        return Math.max(1.0, ctx.config.worldH);
    }

    private static int defaultGridColumns(Faction anchorFaction, String sectorId) {
        if (sectorId != null && (sectorId.contains("home") || sectorId.contains("front"))) {
            return RESOURCE_RUSH_COLUMNS;
        }
        return FOUR_TEAM_GRID_COLUMNS;
    }

    private static int defaultGridRows(Faction anchorFaction, String sectorId) {
        if (sectorId != null && (sectorId.contains("home") || sectorId.contains("front"))) {
            return RESOURCE_RUSH_ROWS;
        }
        return FOUR_TEAM_GRID_ROWS;
    }

    private static int safeDimension(long value) {
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, value));
    }
}
