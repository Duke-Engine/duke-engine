package uz.dukeengine.core.event;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.ObjectId;

/**
 * A thing gone into another that holds it — the reference's {@code OpenContain::onContaining}, where a container plays
 * its enter sound. A moment, not a state.
 *
 * @param passenger what went in
 * @param hold      what holds it
 * @param how       how it went in
 * @param where     where the hold stands, for fog to filter by
 */
public record ObjectEntered(int frame, ObjectId passenger, ObjectId hold, Passage how, Coord3D where)
        implements WorldEvent {
}
