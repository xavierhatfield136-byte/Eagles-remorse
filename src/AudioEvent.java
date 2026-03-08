/**
 * Structured audio dispatch event used by cooldown and mix telemetry.
 */
public final class AudioEvent {
    public final String eventId;
    public final int priority;
    public final String cooldownKey;
    public final long variantSeed;
    public final String duckingClass;
    public final long timestamp;

    public AudioEvent(String eventId,
                      int priority,
                      String cooldownKey,
                      long variantSeed,
                      String duckingClass,
                      long timestamp) {
        this.eventId = (eventId == null || eventId.isBlank()) ? "unknown" : eventId;
        this.priority = priority;
        this.cooldownKey = (cooldownKey == null || cooldownKey.isBlank()) ? this.eventId : cooldownKey;
        this.variantSeed = variantSeed;
        this.duckingClass = (duckingClass == null || duckingClass.isBlank()) ? "default" : duckingClass;
        this.timestamp = timestamp;
    }
}
