import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Thread-safe handoff from network callbacks to the authoritative simulation thread. */
public final class MultiplayerNetworkCommandQueue {
    private final ArrayDeque<MultiplayerCommandGate.PlayerInputFrame> inputFrames = new ArrayDeque<>();
    private final ArrayDeque<MultiplayerCommandGate.DiscreteCommand> discreteCommands = new ArrayDeque<>();

    public synchronized void enqueueInput(MultiplayerCommandGate.PlayerInputFrame frame) {
        if (frame != null) inputFrames.addLast(frame);
    }

    public synchronized void enqueueCommand(MultiplayerCommandGate.DiscreteCommand command) {
        if (command != null) discreteCommands.addLast(command);
    }

    public synchronized List<MultiplayerCommandGate.PlayerInputFrame> drainInputs() {
        ArrayList<MultiplayerCommandGate.PlayerInputFrame> out = new ArrayList<>(inputFrames);
        inputFrames.clear();
        return out;
    }

    public synchronized List<MultiplayerCommandGate.DiscreteCommand> drainCommands() {
        ArrayList<MultiplayerCommandGate.DiscreteCommand> out = new ArrayList<>(discreteCommands);
        discreteCommands.clear();
        return out;
    }
}
