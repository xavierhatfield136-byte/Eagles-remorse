/** Client presentation session. It is intentionally non-authoritative in V1. */
public final class MultiplayerClientBattleSession implements MultiplayerBattleSession {
    private final MultiplayerRulesV1.BattleSetup setup;

    public MultiplayerClientBattleSession(MultiplayerRulesV1.BattleSetup setup) {
        MultiplayerRulesV1.ValidationResult validation = MultiplayerRulesV1.validate(setup);
        if (!validation.accepted()) {
            throw new IllegalArgumentException(validation.message());
        }
        this.setup = setup;
    }

    @Override
    public Kind kind() {
        return Kind.MULTIPLAYER_CLIENT;
    }

    @Override
    public boolean authoritative() {
        return false;
    }

    @Override
    public MultiplayerBattleRuntime runtime() {
        return null;
    }

    public MultiplayerRulesV1.BattleSetup setup() {
        return setup;
    }
}
