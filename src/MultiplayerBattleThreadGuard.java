/**
 * Debug-friendly ownership guard for authoritative battle state. Network callbacks should enqueue work
 * and let the owner thread mutate battle state.
 */
public final class MultiplayerBattleThreadGuard {
    private final Thread owner;
    private final String ownerDescription;

    public MultiplayerBattleThreadGuard() {
        this("authoritative simulation thread");
    }

    public MultiplayerBattleThreadGuard(String ownerDescription) {
        this.owner = Thread.currentThread();
        this.ownerDescription = (ownerDescription == null || ownerDescription.isBlank())
                ? "authoritative simulation thread"
                : ownerDescription.trim();
    }

    public Thread owner() {
        return owner;
    }

    public boolean isOwnerThread() {
        return Thread.currentThread() == owner;
    }

    public void assertOwnerThread(String action) {
        if (!isOwnerThread()) {
            String label = (action == null || action.isBlank()) ? "battle-state mutation" : action.trim();
            throw new IllegalStateException(label + " must run on the " + ownerDescription);
        }
    }
}
