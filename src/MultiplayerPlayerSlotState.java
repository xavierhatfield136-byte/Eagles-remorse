/** Stable player slot state. Slot identity outlives any controlled ship. */
public final class MultiplayerPlayerSlotState {
    public final int slotId;
    public final int teamId;
    public final MultiplayerRulesV1.PlayerRole role;
    public final String displayName;
    public MultiplayerRulesV1.ConnectionState connectionState;
    public int controlledShipId;

    public MultiplayerPlayerSlotState(int slotId, int teamId, int controlledShipId,
                                      MultiplayerRulesV1.PlayerRole role,
                                      MultiplayerRulesV1.ConnectionState connectionState,
                                      String displayName) {
        this.slotId = slotId;
        this.teamId = teamId;
        this.controlledShipId = Math.max(0, controlledShipId);
        this.role = (role == null) ? MultiplayerRulesV1.PlayerRole.DIRECT_SHIP : role;
        this.connectionState = (connectionState == null) ? MultiplayerRulesV1.ConnectionState.DISCONNECTED : connectionState;
        this.displayName = (displayName == null || displayName.isBlank())
                ? "Player " + Math.max(1, slotId)
                : displayName.trim();
    }

    public boolean connected() {
        return connectionState == MultiplayerRulesV1.ConnectionState.LOCAL
                || connectionState == MultiplayerRulesV1.ConnectionState.CONNECTED;
    }

    public boolean hasControlledShip() {
        return controlledShipId > 0;
    }

    public void clearControlledShip() {
        controlledShipId = 0;
    }
}
