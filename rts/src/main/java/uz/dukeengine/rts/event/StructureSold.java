package uz.dukeengine.rts.event;

import uz.dukeengine.core.event.WorldEvent;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.ObjectId;

/**
 * A building its side sold is gone — taken down for its worth, not destroyed by anyone: the side has its refund, and
 * the building has left the world without dying.
 *
 * @param refund what the side got back for it
 */
public record StructureSold(int frame, ObjectId building, String templateName, int player, int refund,
        Coord3D where) implements WorldEvent {

    @Override
    public Coord3D where() {
        return where;
    }
}
