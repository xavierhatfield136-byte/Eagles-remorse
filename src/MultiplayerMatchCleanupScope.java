import java.util.ArrayList;
import java.util.List;

/** Idempotent match-exit cleanup for queues, buffers, listeners, sockets, and helper threads. */
public final class MultiplayerMatchCleanupScope implements AutoCloseable {
    private final MultiplayerNetworkCommandQueue commandQueue;
    private final ArrayList<MultiplayerBattleSnapshot> snapshotBuffer = new ArrayList<>();
    private final ArrayList<Runnable> listeners = new ArrayList<>();
    private final ArrayList<AutoCloseable> closeables = new ArrayList<>();
    private final ArrayList<Thread> backgroundThreads = new ArrayList<>();
    private boolean closed;

    public MultiplayerMatchCleanupScope(MultiplayerNetworkCommandQueue commandQueue) {
        this.commandQueue = commandQueue;
    }

    public void addSnapshot(MultiplayerBattleSnapshot snapshot) {
        if (!closed && snapshot != null) snapshotBuffer.add(snapshot);
    }

    public void addListener(Runnable listener) {
        if (!closed && listener != null) listeners.add(listener);
    }

    public void addCloseable(AutoCloseable closeable) {
        if (!closed && closeable != null) closeables.add(closeable);
    }

    public void addBackgroundThread(Thread thread) {
        if (!closed && thread != null) backgroundThreads.add(thread);
    }

    public int snapshotBufferSize() {
        return snapshotBuffer.size();
    }

    public int listenerCount() {
        return listeners.size();
    }

    public int closeableCount() {
        return closeables.size();
    }

    public int backgroundThreadCount() {
        return backgroundThreads.size();
    }

    public boolean closed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (commandQueue != null) {
            commandQueue.drainInputs();
            commandQueue.drainCommands();
        }
        snapshotBuffer.clear();
        listeners.clear();
        List<AutoCloseable> resources = List.copyOf(closeables);
        closeables.clear();
        for (AutoCloseable closeable : resources) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // Match exit cleanup is best-effort; callers should already have logged the disconnect reason.
            }
        }
        for (Thread thread : backgroundThreads) {
            if (thread != null && thread.isAlive()) {
                thread.interrupt();
            }
        }
        backgroundThreads.clear();
    }
}
