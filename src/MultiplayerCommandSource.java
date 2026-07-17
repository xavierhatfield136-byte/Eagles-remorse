import java.util.List;

/** Source of queued commands for the Phase 4 multiple-command-source harness. */
public interface MultiplayerCommandSource {
    List<MultiplayerCommandGate.PlayerInputFrame> inputFrames(long hostTick);

    default List<MultiplayerCommandGate.DiscreteCommand> discreteCommands(long hostTick) {
        return List.of();
    }
}
