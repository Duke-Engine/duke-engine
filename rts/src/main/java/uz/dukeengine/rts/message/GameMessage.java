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
                GameMessage.QueueProduction, GameMessage.SetRallyPoint, GameMessage.Construct,
                GameMessage.CancelConstruction, GameMessage.QueueResearch, GameMessage.CancelProduction,
                GameMessage.Sell, GameMessage.AttackMove, GameMessage.Guard, GameMessage.Evacuate,
                GameMessage.ExitContainer, GameMessage.GameOrder {

    /**
     * Order the given units to move to a destination, placed there as one group — see {@code GroupLayout}; {@code click}
     * says it is the player's own click, which gathers a group clicked in its middle.
     */
    record MoveTo(int playerIndex, List<ObjectId> units, Coord3D destination, boolean click) implements GameMessage {
        public MoveTo {
            units = List.copyOf(units);
        }

        /** An order the game's own code gives, not the player's click. */
        public MoveTo(int playerIndex, List<ObjectId> units, Coord3D destination) {
            this(playerIndex, units, destination, false);
        }
    }

    /**
     * Order the given units to attack a target object — {@code forced}, the player's forced attack, taken on a thing
     * passing itself off to their side as none of its targets ({@link uz.dukeengine.core.module.Disguise}).
     */
    record AttackObject(int playerIndex, List<ObjectId> units, ObjectId target, boolean forced)
            implements GameMessage {
        public AttackObject {
            units = List.copyOf(units);
        }

        /** An attack that is not forced, as every one was before one could be. */
        public AttackObject(int playerIndex, List<ObjectId> units, ObjectId target) {
            this(playerIndex, units, target, false);
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

    /**
     * Build {@code template} at {@code place}, facing {@code facing} degrees: the one order that puts a thing
     * <em>somewhere</em>. {@link QueueProduction} makes a unit come out of a factory, which is right for a
     * soldier and wrong for a building, because a building is not produced — it is placed.
     *
     * <p>The builder walks there; a site rises the frame it arrives and grows whole over the template's build
     * time while a builder stands beside it. The money is taken when the order is <em>accepted</em>, on the
     * frame it is applied — on every machine at once — not when a window sent it. A place the simulation
     * refuses costs nothing. The template name must not contain the wire separators {@code , | ; :}.
     */
    record Construct(int playerIndex, ObjectId builder, String template, Coord3D place, float facing)
            implements GameMessage {
    }

    /** Call off a site still going up: the share the game says comes back, and the site is gone. */
    record CancelConstruction(int playerIndex, ObjectId site) implements GameMessage {
    }

    /**
     * Research {@code upgrade} at {@code factory}: queued among its units, charged now, done its time later.
     * The name must not contain the wire separators {@code , | ; :}.
     */
    record QueueResearch(int playerIndex, ObjectId factory, String upgrade) implements GameMessage {
    }

    /** Call off the {@code index}-th thing a factory has queued, the first 0: its cost comes back in full. */
    record CancelProduction(int playerIndex, ObjectId factory, int index) implements GameMessage {
    }

    /** Sell a building: its queue paid back at once, and its worth once it has come down. */
    record Sell(int playerIndex, ObjectId building) implements GameMessage {
    }

    /** Move to a point, taking on every enemy that comes within sight on the way. */
    record AttackMove(int playerIndex, List<ObjectId> units, Coord3D destination) implements GameMessage {
        public AttackMove {
            units = List.copyOf(units);
        }
    }

    /**
     * Guard a point, or a thing — {@code place} or {@code target}, the other null; both null guards where each unit
     * stands.
     */
    record Guard(int playerIndex, List<ObjectId> units, Coord3D place, ObjectId target, Mode mode)
            implements GameMessage {
        public Guard {
            units = List.copyOf(units);
            mode = mode == null ? Mode.NORMAL : mode;
        }

        /** How it guards: taking enemies on and chasing them, only what its weapons reach, or only what flies. */
        public enum Mode { NORMAL, WITHOUT_PURSUIT, FLYING_ONLY }
    }

    /** Every passenger of a transport or a garrisoned building gets out. */
    record Evacuate(int playerIndex, ObjectId container) implements GameMessage {
    }

    /** One passenger gets out of whatever carries it. */
    record ExitContainer(int playerIndex, ObjectId passenger) implements GameMessage {
    }

    /**
     * An order the game defined — a special power fired at a place or a thing, a science bought with rank points: the
     * reference's {@code MSG_DO_SPECIAL_POWER_AT_LOCATION}, {@code MSG_PURCHASE_SCIENCE} and their kind. It travels as
     * every order here does: posted from any thread, sent to every machine, applied on a frame boundary in the order
     * it was given, written into a replay. The engine never reads {@code word} or {@code number}; the game hears the
     * order where it is applied ({@code RtsSimulation.onOrder}) and does what it means there. {@code units} may be
     * empty, and {@code place} and {@code target} null.
     */
    record GameOrder(int playerIndex, String word, List<ObjectId> units, Coord3D place, ObjectId target, long number)
            implements GameMessage {
        public GameOrder {
            java.util.Objects.requireNonNull(word, "word");
            units = units == null ? List.of() : List.copyOf(units);
        }
    }
}
