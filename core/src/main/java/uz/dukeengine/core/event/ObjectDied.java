package uz.dukeengine.core.event;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.ObjectId;

/**
 * An object's health reached zero and it left the world.
 *
 * <p>Posted as the object is reaped, which is the last moment anything can be
 * known about it. That is why the event carries a copy of what a client needs —
 * what it was, whose it was, where it stood — rather than just an id: by the
 * time this is read, {@code findObject(id)} returns null.
 *
 * <p>Objects removed for other reasons (a script clearing the map, say) do not
 * produce this. It means "destroyed", so it is safe to hang an explosion on.
 */
public record ObjectDied(
        int frame,
        ObjectId object,
        String templateName,
        int playerIndex,
        Coord3D position) implements WorldEvent {

    @Override
    public Coord3D where() {
        return position;
    }
}
