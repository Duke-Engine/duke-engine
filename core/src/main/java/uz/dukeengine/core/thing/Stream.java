package uz.dukeengine.core.thing;

import java.util.List;

/**
 * A stream the simulation keeps things riding — see {@link World#rideStream}: its name, and the things riding it in
 * the order they started, {@link #GAP} where the game said its aim changed.
 */
public record Stream(String name, List<ObjectId> riders) {

    /** Where a stream is broken: its ribbon is not drawn across. */
    public static final ObjectId GAP = ObjectId.INVALID;

    public Stream {
        riders = List.copyOf(riders);
    }
}
