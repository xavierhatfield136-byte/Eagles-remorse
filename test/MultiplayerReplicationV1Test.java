import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerReplicationV1Test {

    @Test
    void shipReplicationStateCarriesAuthoritativeFields() {
        MultiplayerEntityIdAllocator allocator = new MultiplayerEntityIdAllocator();
        MultiplayerEntityIdAllocator.NetworkEntityId id = allocator.allocate();

        MultiplayerReplicationV1.ShipState state = new MultiplayerReplicationV1.ShipState(
                id, 42, ShipRole.CRUISER, Faction.ENEMY,
                100.5, 200.25, 3.0, -2.0, Math.PI,
                88, 12.5, true, false,
                Set.of(MultiplayerReplicationV1.MajorStatusEffect.SHIELD_DOWN));

        assertEquals(id, state.networkId());
        assertEquals(42, state.localShipId());
        assertEquals(ShipRole.CRUISER, state.role());
        assertEquals(Faction.ENEMY, state.faction());
        assertEquals(88, state.hp());
        assertEquals(12.5, state.shield(), 1e-9);
        assertTrue(state.majorStatusEffects().contains(MultiplayerReplicationV1.MajorStatusEffect.SHIELD_DOWN));
        assertTrue(MultiplayerReplicationV1.shouldApplyShipState(allocator, state));
    }

    @Test
    void playerControlAndMatchStateReplicateSlotMappingVictoryAndTimer() {
        MultiplayerEntityIdAllocator.NetworkEntityId shipId =
                new MultiplayerEntityIdAllocator.NetworkEntityId(7, 1);
        MultiplayerReplicationV1.PlayerControlState control =
                new MultiplayerReplicationV1.PlayerControlState(2, 1, shipId, " Red Pilot ");
        MultiplayerReplicationV1.MatchState match =
                new MultiplayerReplicationV1.MatchState(120L, 2.0, true, 0, "Elimination victory");

        assertEquals(2, control.slotId());
        assertEquals(1, control.teamId());
        assertEquals(shipId, control.controlledShipId());
        assertEquals("Red Pilot", control.displayName());
        assertEquals(120L, match.hostTick());
        assertTrue(match.ended());
        assertEquals(0, match.winningTeamId());
        assertEquals("Elimination victory", match.victoryReason());
    }

    @Test
    void projectileKindsChooseFullStateEventsOrCosmeticEvents() {
        assertEquals(MultiplayerReplicationV1.ProjectileReplicationMode.FULL_PERSISTENT_STATE,
                MultiplayerReplicationV1.modeForProjectile(MultiplayerReplicationV1.ProjectileKind.MISSILE));
        assertEquals(MultiplayerReplicationV1.ProjectileReplicationMode.FULL_PERSISTENT_STATE,
                MultiplayerReplicationV1.modeForProjectile(MultiplayerReplicationV1.ProjectileKind.TORPEDO));
        assertEquals(MultiplayerReplicationV1.ProjectileReplicationMode.FULL_PERSISTENT_STATE,
                MultiplayerReplicationV1.modeForProjectile(MultiplayerReplicationV1.ProjectileKind.MINE));
        assertEquals(MultiplayerReplicationV1.ProjectileReplicationMode.FIRE_AND_HIT_EVENTS,
                MultiplayerReplicationV1.modeForProjectile(MultiplayerReplicationV1.ProjectileKind.HITSCAN));
        assertEquals(MultiplayerReplicationV1.ProjectileReplicationMode.COSMETIC_FIRE_EVENT,
                MultiplayerReplicationV1.modeForProjectile(MultiplayerReplicationV1.ProjectileKind.POINT_DEFENSE_PELLET));
        assertTrue(MultiplayerReplicationV1.fullyReplicatedProjectileKinds()
                .contains(MultiplayerReplicationV1.ProjectileKind.SLOW_MAJOR_WEAPON));
    }

    @Test
    void authoritativeEventsCoverV1ReplicationSurface() {
        for (MultiplayerReplicationV1.EventType type : MultiplayerReplicationV1.EventType.values()) {
            MultiplayerReplicationV1.AuthoritativeEvent event =
                    new MultiplayerReplicationV1.AuthoritativeEvent(type,
                            new MultiplayerEntityIdAllocator.NetworkEntityId(1, 1),
                            10L, 20L, 1, 2, type.name());

            assertTrue(MultiplayerReplicationV1.isReliableAuthoritativeEvent(event));
        }

        assertTrue(MultiplayerReplicationV1.eventRequiresEntity(MultiplayerReplicationV1.EventType.SHIP_SPAWNED));
        assertTrue(MultiplayerReplicationV1.eventRequiresEntity(MultiplayerReplicationV1.EventType.HIT_CONFIRMED));
        assertFalse(MultiplayerReplicationV1.eventRequiresEntity(MultiplayerReplicationV1.EventType.VICTORY_DECLARED));
        assertFalse(MultiplayerReplicationV1.eventRequiresEntity(MultiplayerReplicationV1.EventType.PLAYER_DISCONNECTED));
    }

    @Test
    void reliableLifecycleEventsDefineCreationDestructionOwnershipAndMatchResult() {
        assertEquals(MultiplayerReplicationV1.EventType.SHIP_SPAWNED,
                MultiplayerReplicationV1.reliableLifecycleEventFor(
                        MultiplayerReplicationV1.ReliableLifecycleEventPurpose.CREATION));
        assertEquals(MultiplayerReplicationV1.EventType.SHIP_DESTROYED,
                MultiplayerReplicationV1.reliableLifecycleEventFor(
                        MultiplayerReplicationV1.ReliableLifecycleEventPurpose.DESTRUCTION));
        assertEquals(MultiplayerReplicationV1.EventType.CONTROL_OWNERSHIP_CHANGED,
                MultiplayerReplicationV1.reliableLifecycleEventFor(
                        MultiplayerReplicationV1.ReliableLifecycleEventPurpose.OWNERSHIP_CHANGE));
        assertEquals(MultiplayerReplicationV1.EventType.VICTORY_DECLARED,
                MultiplayerReplicationV1.reliableLifecycleEventFor(
                        MultiplayerReplicationV1.ReliableLifecycleEventPurpose.MATCH_RESULT));

        Set<MultiplayerReplicationV1.EventType> lifecycleEvents =
                MultiplayerReplicationV1.reliableLifecycleEvents();
        assertEquals(MultiplayerReplicationV1.ReliableLifecycleEventPurpose.values().length,
                lifecycleEvents.size());
        for (MultiplayerReplicationV1.EventType type : lifecycleEvents) {
            MultiplayerReplicationV1.AuthoritativeEvent event =
                    new MultiplayerReplicationV1.AuthoritativeEvent(type,
                            type == MultiplayerReplicationV1.EventType.VICTORY_DECLARED
                                    ? null
                                    : new MultiplayerEntityIdAllocator.NetworkEntityId(1, 1),
                            11L, 22L, 1, 2, type.name());

            assertTrue(MultiplayerReplicationV1.isReliableLifecycleEvent(type));
            assertTrue(MultiplayerReplicationV1.isReliableAuthoritativeEvent(event));
        }

        assertFalse(MultiplayerReplicationV1.isReliableLifecycleEvent(
                MultiplayerReplicationV1.EventType.WEAPON_FIRED));
    }

    @Test
    void delayedShipSnapshotsForDestroyedEntitiesAreIgnored() {
        MultiplayerEntityIdAllocator allocator = new MultiplayerEntityIdAllocator();
        MultiplayerEntityIdAllocator.NetworkEntityId id = allocator.allocate();
        MultiplayerReplicationV1.ShipState liveState = shipState(id);

        assertTrue(MultiplayerReplicationV1.shouldApplyShipState(allocator, liveState));

        allocator.despawn(id, MultiplayerEntityIdAllocator.EntityKind.SHIP,
                MultiplayerEntityIdAllocator.DespawnReason.DESTROYED, 44L);

        assertFalse(MultiplayerReplicationV1.shouldApplyShipState(allocator, liveState),
                "late snapshots for a destroyed entity must be ignored");
    }

    @Test
    void spawnDespawnAndGenerationMismatchHandlingAreExplicit() {
        MultiplayerEntityIdAllocator allocator = new MultiplayerEntityIdAllocator();
        MultiplayerEntityIdAllocator.SpawnEvent spawn =
                allocator.spawn(MultiplayerEntityIdAllocator.EntityKind.SHIP, 3L);
        MultiplayerEntityIdAllocator.NetworkEntityId wrongGeneration =
                new MultiplayerEntityIdAllocator.NetworkEntityId(spawn.id().index(), spawn.id().generation() + 1);
        MultiplayerReplicationV1.ShipState wrongGenerationState = shipState(wrongGeneration);

        assertFalse(MultiplayerReplicationV1.shouldApplyShipState(allocator, wrongGenerationState),
                "generation mismatch must not apply to the live entity");

        MultiplayerEntityIdAllocator.DespawnEvent despawn =
                allocator.despawn(spawn.id(), MultiplayerEntityIdAllocator.EntityKind.SHIP,
                        MultiplayerEntityIdAllocator.DespawnReason.DESTROYED, 8L);

        assertEquals(spawn.id(), despawn.id());
        assertEquals(MultiplayerEntityIdAllocator.DespawnReason.DESTROYED, despawn.reason());
        assertFalse(MultiplayerReplicationV1.shouldApplyShipState(allocator, shipState(spawn.id())));
    }

    @Test
    void persistentProjectileStateRequiresNetworkIdentity() {
        assertThrows(IllegalArgumentException.class, () -> new MultiplayerReplicationV1.ProjectileState(
                null, MultiplayerReplicationV1.ProjectileKind.MISSILE, Faction.ALLY,
                0.0, 0.0, 1.0, 1.0, 0.0, 4, 120, true));
    }

    private static MultiplayerReplicationV1.ShipState shipState(
            MultiplayerEntityIdAllocator.NetworkEntityId id) {
        return new MultiplayerReplicationV1.ShipState(
                id, 1, ShipRole.FRIGATE, Faction.ALLY,
                0.0, 0.0, 0.0, 0.0, 0.0,
                100, 20.0, true, false, Set.of());
    }
}
