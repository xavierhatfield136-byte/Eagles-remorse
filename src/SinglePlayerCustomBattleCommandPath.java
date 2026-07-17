import app.config.GameMode;

/**
 * Routes live single-player custom-battle controls through the same command gate
 * shape used by multiplayer host/client input.
 */
public final class SinglePlayerCustomBattleCommandPath {
    public static final class RoutedInput {
        public final InputSnapshot movementInput;
        public final MultiplayerCommandGate.PlayerInputFrame frame;
        public final MultiplayerCommandGate.CommandResult result;
        public final boolean primaryHeldForTick;
        public final boolean secondaryHeldForTick;

        private RoutedInput(InputSnapshot movementInput,
                            MultiplayerCommandGate.PlayerInputFrame frame,
                            MultiplayerCommandGate.CommandResult result,
                            boolean primaryHeldForTick,
                            boolean secondaryHeldForTick) {
            this.movementInput = movementInput;
            this.frame = frame;
            this.result = result;
            this.primaryHeldForTick = primaryHeldForTick;
            this.secondaryHeldForTick = secondaryHeldForTick;
        }

        public boolean accepted() {
            return result == null || result.accepted();
        }
    }

    private final MultiplayerCommandGate commandGate = new MultiplayerCommandGate();
    private int registeredShipId = 0;
    private long nextSequence = 1L;
    private MultiplayerCommandGate.CommandResult lastResult =
            new MultiplayerCommandGate.CommandResult(true, "No command routed", -1L);

    public RoutedInput route(GameContext ctx, InputSnapshot input, double cursorWorldX, double cursorWorldY) {
        return routeWithSequence(ctx, input, cursorWorldX, cursorWorldY, nextSequence, nextSequence, true);
    }

    RoutedInput routeWithSequenceForTests(GameContext ctx, InputSnapshot input, double cursorWorldX,
                                          double cursorWorldY, long sequence, long hostTick) {
        return routeWithSequence(ctx, input, cursorWorldX, cursorWorldY, sequence, hostTick, false);
    }

    public MultiplayerCommandGate.CommandResult lastResult() {
        return lastResult;
    }

    private RoutedInput routeWithSequence(GameContext ctx, InputSnapshot input, double cursorWorldX,
                                          double cursorWorldY, long sequence, long hostTick,
                                          boolean advanceAutomaticSequence) {
        InputSnapshot safeInput = safeInput(input);
        if (!isCustomBattle(ctx) || ctx.player == null) {
            lastResult = new MultiplayerCommandGate.CommandResult(true, "Bypassed outside custom battle", sequence);
            return new RoutedInput(safeInput, null, lastResult,
                    ctx != null && ctx.firingPrimaryManual,
                    ctx != null && ctx.firingSecondaryManual);
        }

        ensureRegistered(ctx.player);
        double aimAngle = Math.atan2(cursorWorldY - ctx.player.y, cursorWorldX - ctx.player.x);
        MultiplayerCommandGate.PlayerInputFrame frame = MultiplayerInputFrameAdapter.fromLocalInput(
                MultiplayerRulesV1.HOST_SLOT_ID,
                ctx.player.id,
                sequence,
                hostTick,
                safeInput,
                aimAngle,
                ctx.firingPrimaryManual,
                ctx.firingSecondaryManual);
        MultiplayerCommandGate.CommandResult result = commandGate.validateInputFrame(frame, hostTick);
        lastResult = result;
        if (advanceAutomaticSequence) {
            nextSequence = Math.max(nextSequence + 1L, sequence + 1L);
        }
        if (!result.accepted()) {
            return new RoutedInput(neutralMovementInput(safeInput), frame, result, false, false);
        }
        return new RoutedInput(inputFromAcceptedFrame(safeInput, frame), frame, result,
                frame.primaryHeld(), frame.secondaryHeld());
    }

    private void ensureRegistered(Player player) {
        if (player == null || player.id <= 0 || player.id == registeredShipId) return;
        registeredShipId = player.id;
        commandGate.registerSlot(new MultiplayerCommandGate.SlotOwnership(
                MultiplayerRulesV1.HOST_SLOT_ID, registeredShipId, true, true));
    }

    private static boolean isCustomBattle(GameContext ctx) {
        return ctx != null && ctx.config != null && ctx.config.mode == GameMode.CUSTOM_BATTLES;
    }

    private static InputSnapshot safeInput(InputSnapshot input) {
        if (input == null) return new InputSnapshot(false, false, false, false, false, 0.0, 0.0);
        double mouseX = Double.isFinite(input.mouseX) ? input.mouseX : 0.0;
        double mouseY = Double.isFinite(input.mouseY) ? input.mouseY : 0.0;
        return new InputSnapshot(input.up, input.down, input.left, input.right, input.boost, mouseX, mouseY);
    }

    private static InputSnapshot inputFromAcceptedFrame(InputSnapshot input,
                                                        MultiplayerCommandGate.PlayerInputFrame frame) {
        return new InputSnapshot(
                frame.thrust() > 1e-6f,
                frame.thrust() < -1e-6f,
                frame.turn() < -1e-6f,
                frame.turn() > 1e-6f,
                input.boost,
                input.mouseX,
                input.mouseY);
    }

    private static InputSnapshot neutralMovementInput(InputSnapshot input) {
        return new InputSnapshot(false, false, false, false, input.boost, input.mouseX, input.mouseY);
    }
}
