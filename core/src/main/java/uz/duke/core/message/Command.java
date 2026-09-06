package uz.duke.core.message;

/**
 * A player order that mutates the simulation — the unit of input the engine
 * queues, ships over the wire and applies on a frame boundary.
 *
 * <p>The engine deliberately does not know what commands a game has. It only
 * needs to queue them ({@link MessageStream}), ship them ({@code CommandPacket})
 * and hand them back on the frame they belong to; interpreting them is the
 * game's job. A game therefore declares its own command set, ideally as a
 * <strong>sealed</strong> hierarchy of records so its dispatch is exhaustive —
 * see {@code uz.duke.rts.message.GameMessage} for the RTS one.
 *
 * <p>Commands must carry only deterministic data (ids, coordinates, never live
 * object references): every peer applies the identical command list on the
 * identical frame, and that is the whole basis of lock-step and replays.
 */
public interface Command {

    /** The player who issued this command. */
    int playerIndex();
}
