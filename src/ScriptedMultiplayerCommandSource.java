import java.util.ArrayList;
import java.util.List;

/** Scripted command source used to prove ownership and command routing before real transport exists. */
public final class ScriptedMultiplayerCommandSource implements MultiplayerCommandSource {
    private final int slotId;
    private final int controlledShipId;
    private final float thrust;
    private final float turn;
    private final boolean primaryHeld;
    private long sequence = 0L;

    public ScriptedMultiplayerCommandSource(int slotId, int controlledShipId,
                                            float thrust, float turn, boolean primaryHeld) {
        this.slotId = slotId;
        this.controlledShipId = controlledShipId;
        this.thrust = clampUnit(thrust);
        this.turn = clampUnit(turn);
        this.primaryHeld = primaryHeld;
    }

    @Override
    public List<MultiplayerCommandGate.PlayerInputFrame> inputFrames(long hostTick) {
        sequence++;
        ArrayList<MultiplayerCommandGate.PlayerInputFrame> out = new ArrayList<>(1);
        out.add(new MultiplayerCommandGate.PlayerInputFrame(
                slotId,
                controlledShipId,
                sequence,
                hostTick,
                thrust,
                turn,
                0.0,
                primaryHeld,
                false));
        return out;
    }

    private static float clampUnit(float value) {
        if (!Float.isFinite(value)) return 0.0f;
        if (value > 1.0f) return 1.0f;
        if (value < -1.0f) return -1.0f;
        return value;
    }
}
