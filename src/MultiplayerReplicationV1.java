import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Phase 6 replication records and validation rules for authoritative V1 battle state. */
public final class MultiplayerReplicationV1 {
    public static final int TARGET_SNAPSHOT_BYTES = 12 * 1024;
    public static final int PEAK_SNAPSHOT_BYTES = MultiplayerProtocolV1.MAX_MESSAGE_BYTES;
    public static final int TARGET_BYTES_PER_SECOND_PER_CLIENT = 96 * 1024;
    public static final int MAX_REPLICATED_SHIPS_V1 = 2;
    public static final int MAX_REPLICATED_PROJECTILES_V1 = 128;

    private MultiplayerReplicationV1() {}

    public enum MajorStatusEffect {
        DYING,
        DESTROYED,
        SHIELD_DOWN,
        ENGINE_DISABLED,
        WEAPONS_DISABLED,
        CLOAKED,
        DESTABILIZED
    }

    public enum EventType {
        SHIP_SPAWNED,
        SHIP_DESPAWNED,
        SHIP_DESTROYED,
        WEAPON_FIRED,
        HIT_CONFIRMED,
        EXPLOSION_OCCURRED,
        OBJECTIVE_COMPLETED,
        PLAYER_DISCONNECTED,
        VICTORY_DECLARED,
        CONTROL_OWNERSHIP_CHANGED
    }

    public enum ProjectileKind {
        MISSILE,
        TORPEDO,
        MINE,
        SLOW_MAJOR_WEAPON,
        HITSCAN,
        INSTANT_BEAM,
        RAPID_TRACER,
        POINT_DEFENSE_PELLET,
        COSMETIC_ONLY
    }

    public enum ProjectileReplicationMode {
        FULL_PERSISTENT_STATE,
        FIRE_AND_HIT_EVENTS,
        COSMETIC_FIRE_EVENT
    }

    public record ShipState(MultiplayerEntityIdAllocator.NetworkEntityId networkId,
                            int localShipId,
                            ShipRole role,
                            Faction faction,
                            double x,
                            double y,
                            double vx,
                            double vy,
                            double angle,
                            int hp,
                            double shield,
                            boolean alive,
                            boolean dying,
                            Set<MajorStatusEffect> majorStatusEffects) {
        public ShipState {
            if (networkId == null) throw new IllegalArgumentException("Ship replication requires a network id");
            localShipId = Math.max(0, localShipId);
            if (role == null) role = ShipRole.FRIGATE;
            if (faction == null) faction = Faction.ALLY;
            x = finiteOrZero(x);
            y = finiteOrZero(y);
            vx = finiteOrZero(vx);
            vy = finiteOrZero(vy);
            angle = finiteOrZero(angle);
            hp = Math.max(0, hp);
            shield = Math.max(0.0, finiteOrZero(shield));
            majorStatusEffects = majorStatusEffects == null
                    ? Set.of()
                    : Set.copyOf(majorStatusEffects);
        }
    }

    public record PlayerControlState(int slotId,
                                     int teamId,
                                     MultiplayerEntityIdAllocator.NetworkEntityId controlledShipId,
                                     String displayName) {
        public PlayerControlState {
            slotId = Math.max(0, slotId);
            teamId = Math.max(0, teamId);
            displayName = displayName == null ? "" : displayName.trim();
        }
    }

    public record MatchState(long hostTick,
                             double matchSeconds,
                             boolean ended,
                             int winningTeamId,
                             String victoryReason) {
        public MatchState {
            hostTick = Math.max(0L, hostTick);
            matchSeconds = Math.max(0.0, finiteOrZero(matchSeconds));
            winningTeamId = Math.max(-1, winningTeamId);
            victoryReason = (victoryReason == null || victoryReason.isBlank())
                    ? (ended ? "Match ended" : "In progress")
                    : victoryReason.trim();
        }
    }

    public record AuthoritativeEvent(EventType type,
                                     MultiplayerEntityIdAllocator.NetworkEntityId entityId,
                                     long eventSequence,
                                     long hostTick,
                                     int sourceSlotId,
                                     int targetSlotId,
                                     String detail) {
        public AuthoritativeEvent {
            if (type == null) type = EventType.HIT_CONFIRMED;
            eventSequence = Math.max(0L, eventSequence);
            hostTick = Math.max(0L, hostTick);
            sourceSlotId = Math.max(0, sourceSlotId);
            targetSlotId = Math.max(0, targetSlotId);
            detail = detail == null ? "" : detail.trim();
        }
    }

    public record ProjectileState(MultiplayerEntityIdAllocator.NetworkEntityId networkId,
                                  ProjectileKind kind,
                                  Faction faction,
                                  double x,
                                  double y,
                                  double vx,
                                  double vy,
                                  double angle,
                                  int damage,
                                  int life,
                                  boolean alive) {
        public ProjectileState {
            if (networkId == null) throw new IllegalArgumentException("Projectile replication requires a network id");
            if (kind == null) kind = ProjectileKind.MISSILE;
            if (faction == null) faction = Faction.ENEMY;
            x = finiteOrZero(x);
            y = finiteOrZero(y);
            vx = finiteOrZero(vx);
            vy = finiteOrZero(vy);
            angle = finiteOrZero(angle);
            damage = Math.max(0, damage);
            life = Math.max(0, life);
        }
    }

    public record ReplicationSnapshot(MultiplayerProtocolV1.SnapshotHeader header,
                                      List<ShipState> ships,
                                      List<PlayerControlState> playerControls,
                                      List<ProjectileState> persistentProjectiles,
                                      MatchState matchState) {
        public ReplicationSnapshot {
            if (header == null) header = new MultiplayerProtocolV1.SnapshotHeader(0L, 0L, 0L);
            ships = ships == null ? List.of() : List.copyOf(ships);
            playerControls = playerControls == null ? List.of() : List.copyOf(playerControls);
            persistentProjectiles = persistentProjectiles == null ? List.of() : List.copyOf(persistentProjectiles);
            if (matchState == null) matchState = new MatchState(header.hostTick(), 0.0, false, -1, "In progress");
        }
    }

    public static ProjectileReplicationMode modeForProjectile(ProjectileKind kind) {
        return switch (kind == null ? ProjectileKind.MISSILE : kind) {
            case MISSILE, TORPEDO, MINE, SLOW_MAJOR_WEAPON -> ProjectileReplicationMode.FULL_PERSISTENT_STATE;
            case HITSCAN, INSTANT_BEAM -> ProjectileReplicationMode.FIRE_AND_HIT_EVENTS;
            case RAPID_TRACER, POINT_DEFENSE_PELLET, COSMETIC_ONLY -> ProjectileReplicationMode.COSMETIC_FIRE_EVENT;
        };
    }

    public static boolean shouldApplyShipState(MultiplayerEntityIdAllocator allocator, ShipState state) {
        return state != null && MultiplayerProtocolV1.acceptsEntityUpdate(allocator, state.networkId());
    }

    public static boolean shouldApplyProjectileState(MultiplayerEntityIdAllocator allocator, ProjectileState state) {
        return state != null
                && modeForProjectile(state.kind()) == ProjectileReplicationMode.FULL_PERSISTENT_STATE
                && MultiplayerProtocolV1.acceptsEntityUpdate(allocator, state.networkId());
    }

    public static boolean isReliableAuthoritativeEvent(AuthoritativeEvent event) {
        return event != null;
    }

    public static boolean eventRequiresEntity(EventType type) {
        return switch (type == null ? EventType.HIT_CONFIRMED : type) {
            case SHIP_SPAWNED, SHIP_DESPAWNED, SHIP_DESTROYED,
                    WEAPON_FIRED, HIT_CONFIRMED, EXPLOSION_OCCURRED,
                    CONTROL_OWNERSHIP_CHANGED -> true;
            case OBJECTIVE_COMPLETED, PLAYER_DISCONNECTED, VICTORY_DECLARED -> false;
        };
    }

    public static Set<ProjectileKind> fullyReplicatedProjectileKinds() {
        return EnumSet.of(ProjectileKind.MISSILE, ProjectileKind.TORPEDO,
                ProjectileKind.MINE, ProjectileKind.SLOW_MAJOR_WEAPON);
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }
}
