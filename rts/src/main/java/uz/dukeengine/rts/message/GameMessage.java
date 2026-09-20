package uz.dukeengine.rts.message;

import java.util.List;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.message.Command;
import uz.dukeengine.core.thing.ObjectId;

/**
 * The RTS command set, ported from SAGE's {@code GameMessage} (the
 * {@code MSG_DO_*} command family).
 *
 * <p>SAGE used a single {@code GameMessage} class with a {@code Type} enum and a
 * loosely-typed argument list. Here it is a <strong>sealed</strong> hierarchy of
 * records, one per command, so the compiler enforces exhaustive dispatch: adding
 * a new command type forces every {@code switch} over messages to handle it,
 * exactly the property SAGE's enum-based code had to maintain by hand.
 *
 * <p>These extend the engine's genre-neutral {@link Command}: {@code core} only
 * queues and ships them, while this module gives them meaning. A non-RTS game
 * built on the engine declares its own sealed set the same way.
 *
 * <p>Every field is deterministic data (object ids and coordinates, never live
 * references), because each peer applies the identical commands on the identical
 * frame.
 */
public sealed interface GameMessage extends Command
        permits GameMessage.MoveTo, GameMessage.AttackObject, GameMessage.StopMoving,
                GameMessage.QueueProduction, GameMessage.SetRallyPoint {

    /** Order the given units to move to a destination. */
    record MoveTo(int playerIndex, List<ObjectId> units, Coord3D destination) implements GameMessage {
        public MoveTo {
            units = List.copyOf(units);
        }
    }

    /** Order the given units to attack a target object. */
    record AttackObject(int playerIndex, List<ObjectId> units, ObjectId target) implements GameMessage {
        public AttackObject {
            units = List.copyOf(units);
        }
    }

    /** Order the given units to halt. */
    record StopMoving(int playerIndex, List<ObjectId> units) implements GameMessage {
        public StopMoving {
            units = List.copyOf(units);
        }
    }

    /**
     * Ask a production structure to queue one unit of {@code unitTemplate}.
     * The template name must not contain the wire separators {@code , | ; :}.
     */
    record QueueProduction(int playerIndex, ObjectId factory, String unitTemplate) implements GameMessage {
    }

    /** Point a production structure's finished units at a rally position. */
    record SetRallyPoint(int playerIndex, ObjectId factory, Coord3D point) implements GameMessage {
    }
}
