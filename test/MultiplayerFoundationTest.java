import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerFoundationTest {

    @Test
    void fixedStepClockAdvancesAtAuthoritativeTickRate() {
        MultiplayerFixedStepClock clock = new MultiplayerFixedStepClock(60, 5);

        MultiplayerFixedStepClock.StepPlan first = clock.planFrame(1.0 / 120.0);
        MultiplayerFixedStepClock.StepPlan second = clock.planFrame(1.0 / 120.0);

        assertEquals(0, first.ticksToRun());
        assertEquals(1, second.ticksToRun());
        assertEquals(0, second.firstTick());
        assertEquals(1, clock.nextTick());
    }

    @Test
    void fixedStepClockClampsCatchUpInsteadOfSpiraling() {
        MultiplayerFixedStepClock clock = new MultiplayerFixedStepClock(60, 5);

        MultiplayerFixedStepClock.StepPlan plan = clock.planFrame(1.0);

        assertEquals(5, plan.ticksToRun());
        assertTrue(plan.catchUpClamped());
        assertEquals(5, clock.nextTick());
        assertEquals(0.0, clock.accumulatorSeconds(), 1e-9);
    }

    @Test
    void entityAllocatorDoesNotReuseRetiredIndexes() {
        MultiplayerEntityIdAllocator allocator = new MultiplayerEntityIdAllocator();

        MultiplayerEntityIdAllocator.NetworkEntityId first = allocator.allocate();
        allocator.retire(first);
        MultiplayerEntityIdAllocator.NetworkEntityId second = allocator.allocate();

        assertEquals(1, first.index());
        assertEquals(2, second.index());
        assertTrue(allocator.isRetired(first));
        assertFalse(allocator.acceptsUpdate(first),
                "delayed updates for retired entity IDs should be rejected");
        assertTrue(allocator.acceptsUpdate(second));
    }

    @Test
    void entityLifecycleEventsCarryKindReasonAndTick() {
        MultiplayerEntityIdAllocator allocator = new MultiplayerEntityIdAllocator();

        MultiplayerEntityIdAllocator.SpawnEvent spawn =
                allocator.spawn(MultiplayerEntityIdAllocator.EntityKind.SHIP, 12L);
        MultiplayerEntityIdAllocator.DespawnEvent despawn =
                allocator.despawn(spawn.id(), MultiplayerEntityIdAllocator.EntityKind.SHIP,
                        MultiplayerEntityIdAllocator.DespawnReason.DESTROYED, 20L);

        assertEquals(MultiplayerEntityIdAllocator.EntityKind.SHIP, spawn.kind());
        assertEquals(12L, spawn.hostTick());
        assertEquals(spawn.id(), despawn.id());
        assertEquals(MultiplayerEntityIdAllocator.DespawnReason.DESTROYED, despawn.reason());
        assertEquals(20L, despawn.hostTick());
        assertFalse(allocator.acceptsUpdate(spawn.id()));
    }

    @Test
    void threadGuardAcceptsOwnerThread() {
        MultiplayerBattleThreadGuard guard = new MultiplayerBattleThreadGuard();

        guard.assertOwnerThread("test mutation");

        assertTrue(guard.isOwnerThread());
        assertEquals(Thread.currentThread(), guard.owner());
    }

    @Test
    void networkCommandQueueDrainsWithoutMutatingBattleState() {
        MultiplayerNetworkCommandQueue queue = new MultiplayerNetworkCommandQueue();
        MultiplayerCommandGate.PlayerInputFrame frame = new MultiplayerCommandGate.PlayerInputFrame(
                MultiplayerRulesV1.HOST_SLOT_ID, 101, 1L, 1L,
                0.0f, 0.0f, 0.0, false, false);

        queue.enqueueInput(frame);

        assertEquals(1, queue.drainInputs().size());
        assertTrue(queue.drainInputs().isEmpty());
        assertTrue(queue.drainCommands().isEmpty());
    }

    @Test
    void networkWriteQueueRunsWritesOffEventDispatchThread() throws Exception {
        MultiplayerNetworkWriteQueue queue = new MultiplayerNetworkWriteQueue("mp-write-test");
        AtomicReference<CompletableFuture<Boolean>> future = new AtomicReference<>();
        AtomicReference<Thread> writeThread = new AtomicReference<>();
        AtomicBoolean writeRanOnEdt = new AtomicBoolean(true);

        SwingUtilities.invokeAndWait(() -> future.set(queue.submit(() -> {
            writeThread.set(Thread.currentThread());
            writeRanOnEdt.set(SwingUtilities.isEventDispatchThread());
        })));

        assertTrue(future.get().get(1, TimeUnit.SECONDS));
        assertFalse(writeRanOnEdt.get());
        assertNotNull(writeThread.get());
        assertEquals("mp-write-test", writeThread.get().getName());
        assertEquals(writeThread.get(), queue.writerThread());

        queue.close();
        assertTrue(queue.closed());
    }
}
