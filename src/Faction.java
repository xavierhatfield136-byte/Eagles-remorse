public enum Faction {
    PLAYER,
    ALLY,
    ENEMY;

    public boolean isFriendlyTo(Faction other) {
        if (this == ENEMY) return other == ENEMY;
        return other != ENEMY;
    }
}
