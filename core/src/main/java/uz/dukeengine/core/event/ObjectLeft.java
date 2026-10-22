package uz.dukeengine.core.event;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.ObjectId;

/**
 * A thing come out of another that held it — the reference's {@code OpenContain::onRemoving}, where a container plays
 * its exit sound and what fell out its falling sound. Posted beside {@link ObjectCreated} {@code UNLOADED}. A moment,
 * not a state.
 *
 * @param passenger what came out
 * @param hold      what held it
 * @param how       how it came out
 * @param where     where the hold stands, for fog to filter by
 */
public record ObjectLeft(int frame, ObjectId passenger, ObjectId hold, Passage how, Coord3D where)
        implements WorldEvent {
}
