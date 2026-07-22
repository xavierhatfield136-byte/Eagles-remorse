/** Single-player/local battle authority can directly mutate the simulation. */
public final class LocalBattleAuthority implements BattleAuthority {
    public static final LocalBattleAuthority INSTANCE = new LocalBattleAuthority();

    private LocalBattleAuthority() {}

    @Override
    public Decision evaluate(BattleAuthorityOperation operation) {
        return new Decision(true, "Local battle owns simulation state");
    }
}
