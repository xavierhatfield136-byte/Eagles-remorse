import java.util.List;

/** Immutable host snapshot suitable for render/network publication. */
public record MultiplayerBattleSnapshot(long hostTick,
                                        List<ShipSnapshot> ships,
                                        List<SlotSnapshot> slots) {
    public MultiplayerBattleSnapshot {
        ships = (ships == null) ? List.of() : List.copyOf(ships);
        slots = (slots == null) ? List.of() : List.copyOf(slots);
    }

    public record ShipSnapshot(int shipId, ShipRole role, Faction faction,
                               double x, double y, double vx, double vy,
                               double angle, int hp, double shield, boolean alive) {}

    public record SlotSnapshot(int slotId, int teamId, int controlledShipId,
                               MultiplayerRulesV1.PlayerRole role,
                               MultiplayerRulesV1.ConnectionState connectionState,
                               String displayName) {}
}
