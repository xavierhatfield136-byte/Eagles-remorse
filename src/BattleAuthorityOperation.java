/** Simulation operations that must be owned by the correct battle authority. */
public enum BattleAuthorityOperation {
    MOVEMENT_INPUT,
    TARGET_SELECTION,
    WEAPON_FIRE,
    ABILITY_ACTIVATION,
    ORDER_ISSUANCE,
    PAUSE_TIME_SCALE,
    SPAWNING,
    DAMAGE_APPLICATION,
    VICTORY_EVALUATION,
    ENTITY_DELETION,
    CLIENT_COMMAND_SUBMISSION,
    CLIENT_PRESENTATION_UPDATE,
    LOCAL_SPECTATOR_CAMERA;

    public static BattleAuthorityOperation forDiscreteCommand(MultiplayerCommandGate.DiscreteCommandType type) {
        return switch (type == null ? MultiplayerCommandGate.DiscreteCommandType.LOBBY_CHANGE : type) {
            case SELECT_TARGET -> TARGET_SELECTION;
            case ACTIVATE_ABILITY, ACTIVATE_SUPERWEAPON, BATTLEFIELD_WARP -> ABILITY_ACTIVATION;
            case FLEET_ORDER, FORMATION, ESCORT_ASSIGNMENT -> ORDER_ISSUANCE;
            case REQUEST_RESPAWN -> SPAWNING;
            case PAUSE -> PAUSE_TIME_SCALE;
            case READY, LOBBY_CHANGE, RECONNECT -> CLIENT_COMMAND_SUBMISSION;
        };
    }
}
