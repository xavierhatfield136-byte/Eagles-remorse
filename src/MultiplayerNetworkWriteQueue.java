import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Single-owner network write lane so UI handlers never block on socket writes. */
public final class MultiplayerNetworkWriteQueue implements AutoCloseable {
    @FunctionalInterface
    public interface WriteTask {
        void run() throws IOException;
    }

    private final ExecutorService executor;
    private final AtomicReference<Thread> writerThread = new AtomicReference<>();
    private final AtomicReference<Throwable> lastFailure = new AtomicReference<>();
    private volatile boolean closed;

    public MultiplayerNetworkWriteQueue(String threadName) {
        String safeName = (threadName == null || threadName.isBlank()) ? "mp-network-write" : threadName.trim();
        executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, safeName);
            thread.setDaemon(true);
            writerThread.compareAndSet(null, thread);
            return thread;
        });
    }

    public CompletableFuture<Boolean> submit(WriteTask task) {
        if (closed || task == null) return CompletableFuture.completedFuture(false);
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    task.run();
                    result.complete(true);
                } catch (Throwable ex) {
                    lastFailure.set(ex);
                    result.complete(false);
                }
            });
        } catch (RuntimeException ex) {
            lastFailure.set(ex);
            result.complete(false);
        }
        return result;
    }

    public boolean submitAndWait(WriteTask task, long timeoutMs) {
        try {
            return submit(task).get(Math.max(1L, timeoutMs), TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
            lastFailure.set(ex);
            return false;
        }
    }

    public Thread writerThread() {
        return writerThread.get();
    }

    public Throwable lastFailure() {
        return lastFailure.get();
    }

    public boolean closed() {
        return closed;
    }

    @Override
    public void close() {
        closed = true;
        executor.shutdownNow();
    }
}
