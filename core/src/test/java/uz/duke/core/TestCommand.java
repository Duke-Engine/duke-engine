package uz.duke.core;

import java.util.List;
import uz.duke.core.math.Coord3D;
import uz.duke.core.message.Command;
import uz.duke.core.thing.ObjectId;

/**
 * A minimal command set for exercising the engine's own command pipeline.
 *
 * <p>It exists because {@code core} deliberately ships no commands: a game
 * declares its own sealed hierarchy of {@link Command}s (the RTS module's
 * {@code GameMessage} is the real one). This is that same pattern, shrunk to
 * what the engine's tests need — which also keeps core's tests free of any
 * dependency on a genre.
 */
public sealed interface TestCommand extends Command {

    /** Send units to a destination. */
    record Move(int playerIndex, List<ObjectId> units, Coord3D destination) implements TestCommand {
        public Move {
            units = List.copyOf(units);
        }
    }

    /** Order units to stop. */
    record Halt(int playerIndex, List<ObjectId> units) implements TestCommand {
        public Halt {
            units = List.copyOf(units);
        }
    }

    /** A payload-free command, for order and plumbing assertions. */
    record Ping(int playerIndex, String tag) implements TestCommand {
    }
}
