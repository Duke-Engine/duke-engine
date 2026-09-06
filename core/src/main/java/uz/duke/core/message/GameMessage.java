package uz.duke.core.message;

import java.util.List;
import uz.duke.core.math.Coord3D;
import uz.duke.core.thing.ObjectId;

/**
 * A player command that mutates the simulation, ported from SAGE's
 * {@code GameMessage} (the {@code MSG_DO_*} command family).
 *
 * <p>SAGE used a single {@code GameMessage} class with a {@code Type} enum and a
 * loosely-typed argument list. Here it is a <strong>sealed</strong> hierarchy of
 * records, one per command, so the compiler enforces exhaustive dispatch: adding
 * a new command type forces every {@code switch} over messages to handle it,
 * exactly the property SAGE's enum-based code had to maintain by hand.
 *
 * <p>These are the messages that travel over the wire in lock-step: every peer
 * must apply the same commands at the same frame to stay in sync, so each
 * carries the issuing {@code playerIndex} and only deterministic data (object
 * ids and coordinates, never live references).
 */
public sealed interface GameMessage
        permits GameMessage.MoveTo, GameMessage.AttackObject, GameMessage.StopMoving,
                GameMessage.QueueProduction, GameMessage.SetRallyPoint {

    /** The player who issued this command. */
    int playerIndex();

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
