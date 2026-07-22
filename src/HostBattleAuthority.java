/** Multiplayer host authority owns authoritative simulation mutation and validation. */
public final class HostBattleAuthority implements BattleAuthority {
    public static final HostBattleAuthority INSTANCE = new HostBattleAuthority();

    private HostBattleAuthority() {}

    @Override
    public Decision evaluate(BattleAuthorityOperation operation) {
        return switch (operation == null ? BattleAuthorityOperation.CLIENT_COMMAND_SUBMISSION : operation) {
            case CLIENT_PRESENTATION_UPDATE, LOCAL_SPECTATOR_CAMERA ->
                    new Decision(false, "Host authority does not own client-only presentation state");
            case PAUSE_TIME_SCALE ->
                    new Decision(false, "Multiplayer matches run at fixed simulation speed");
            default -> new Decision(true, "Host owns authoritative battle state");
        };
    }
}
