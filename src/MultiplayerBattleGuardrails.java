/** Guardrails that keep V1 multiplayer custom battles separated from campaign and local presentation state. */
public final class MultiplayerBattleGuardrails {
    public enum LocalPresentationState {
        CAMERA,
        HOVER,
        LOCAL_MENU,
        INPUT_HINT,
        COSMETIC_EFFECT,
        DEBUG_OVERLAY
    }

    private MultiplayerBattleGuardrails() {}

    public static boolean campaignActionsAllowed() {
        return false;
    }

    public static boolean campaignUiAllowed() {
        return false;
    }

    public static boolean synchronizedOverNetwork(LocalPresentationState state) {
        return false;
    }
}
