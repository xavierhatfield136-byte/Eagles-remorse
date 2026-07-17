/** Converts local input into the same compact input frame shape used by multiplayer clients. */
public final class MultiplayerInputFrameAdapter {
    private MultiplayerInputFrameAdapter() {}

    public static MultiplayerCommandGate.PlayerInputFrame fromLocalInput(
            int slotId,
            int controlledShipId,
            long sequence,
            long clientTick,
            InputSnapshot input,
            boolean primaryHeld,
            boolean secondaryHeld) {
        return fromLocalInput(slotId, controlledShipId, sequence, clientTick, input,
                localAimAngle(input), primaryHeld, secondaryHeld);
    }

    public static MultiplayerCommandGate.PlayerInputFrame fromLocalInput(
            int slotId,
            int controlledShipId,
            long sequence,
            long clientTick,
            InputSnapshot input,
            double aimAngle,
            boolean primaryHeld,
            boolean secondaryHeld) {
        float thrust = 0.0f;
        float turn = 0.0f;
        if (input != null) {
            if (input.up) thrust += 1.0f;
            if (input.down) thrust -= 1.0f;
            if (input.left) turn -= 1.0f;
            if (input.right) turn += 1.0f;
        }
        return new MultiplayerCommandGate.PlayerInputFrame(
                slotId,
                controlledShipId,
                sequence,
                clientTick,
                clampUnit(thrust),
                clampUnit(turn),
                finiteOrZero(aimAngle),
                primaryHeld,
                secondaryHeld);
    }

    private static double localAimAngle(InputSnapshot input) {
        if (input == null) return 0.0;
        return Math.atan2(input.mouseY, input.mouseX);
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static float clampUnit(float value) {
        if (!Float.isFinite(value)) return 0.0f;
        if (value > 1.0f) return 1.0f;
        if (value < -1.0f) return -1.0f;
        return value;
    }
}
