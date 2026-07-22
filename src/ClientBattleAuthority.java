/** Multiplayer client authority is limited to command submission and presentation-only state. */
public final class ClientBattleAuthority implements BattleAuthority {
    public static final ClientBattleAuthority INSTANCE = new ClientBattleAuthority();

    private ClientBattleAuthority() {}

    @Override
    public Decision evaluate(BattleAuthorityOperation operation) {
        return switch (operation == null ? BattleAuthorityOperation.DAMAGE_APPLICATION : operation) {
            case CLIENT_COMMAND_SUBMISSION, CLIENT_PRESENTATION_UPDATE, LOCAL_SPECTATOR_CAMERA ->
                    new Decision(true, "Client may submit commands and update presentation-only state");
            case MOVEMENT_INPUT, TARGET_SELECTION, WEAPON_FIRE, ABILITY_ACTIVATION, ORDER_ISSUANCE ->
                    new Decision(false, "Client must submit gameplay intent to the host");
            case PAUSE_TIME_SCALE, SPAWNING, DAMAGE_APPLICATION, VICTORY_EVALUATION, ENTITY_DELETION ->
                    new Decision(false, "Client cannot mutate authoritative simulation state");
        };
    }
}
