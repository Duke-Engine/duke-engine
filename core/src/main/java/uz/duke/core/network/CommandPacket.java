package uz.duke.core.network;

import java.util.List;
import uz.duke.core.message.Command;

/**
 * One player's commands for one future frame — the unit of data exchanged over
 * the wire in lock-step, ported in spirit from SAGE's per-frame net command
 * messages.
 *
 * <p>Tiny by design: lock-step ships commands, never world state. An empty
 * command list is still sent so peers know the player has reported for that
 * frame.
 */
public record CommandPacket(int frame, int playerIndex, List<Command> commands) {

    public CommandPacket {
        commands = List.copyOf(commands);
    }
}
