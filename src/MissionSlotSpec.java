public record MissionSlotSpec(int slotId,
                              int teamId,
                              ShipRole defaultHull,
                              ShipDefinitionRef definitionRef,
                              MissionSlotControlMode controlMode,
                              boolean required,
                              String spawnAnchorId) {
    public MissionSlotSpec(int slotId,
                           int teamId,
                           ShipRole defaultHull,
                           MissionSlotControlMode controlMode,
                           boolean required,
                           String spawnAnchorId) {
        this(slotId, teamId, defaultHull, ShipDefinitionRef.builtin(defaultHull), controlMode, required, spawnAnchorId);
    }

    public MissionSlotSpec {
        slotId = Math.max(1, slotId);
        teamId = Math.max(0, teamId);
        if (defaultHull == null) defaultHull = ShipRole.FRIGATE;
        if (definitionRef == null) definitionRef = ShipDefinitionRef.builtin(defaultHull);
        if (controlMode == null) controlMode = MissionSlotControlMode.PLAYER_REQUIRED;
        spawnAnchorId = (spawnAnchorId == null || spawnAnchorId.isBlank())
                ? "slot-" + slotId
                : spawnAnchorId.trim();
    }
}
