public enum TitanAvailability {
    EARLY("Early", 1),
    EARLY_MID("Early-Mid", 3),
    MID("Mid", 5),
    MID_LATE("Mid-Late", 7),
    LATE("Late", 9);

    private final String label;
    private final int minSector;

    TitanAvailability(String label, int minSector) {
        this.label = (label == null || label.isBlank()) ? "Unknown" : label;
        this.minSector = Math.max(1, minSector);
    }

    public String label() {
        return label;
    }

    public int minSector() {
        return minSector;
    }

    public boolean isAvailableInSector(int sector) {
        return sector >= minSector;
    }

    @Override
    public String toString() {
        return label;
    }
}
