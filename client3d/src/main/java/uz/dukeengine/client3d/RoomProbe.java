package uz.dukeengine.client3d;

import com.jme3.math.Vector3f;
import uz.dukeengine.render.RoomProbes;

/**
 * A room of the game's scene lit by a probe of its own: see {@link Duke3D#roomProbe}. From any thread; what it asks is done
 * on the render thread, in the order asked. With {@link Visuals#physicallyBased} off there is no probe, and it does nothing.
 */
public final class RoomProbe {

    private final Duke3D client;
    final Vector3f centre;
    final Vector3f halfExtents;
    final boolean enclosed;
    /** The room once the app has placed it: on the render thread only. Null with no physically based look. */
    RoomProbes.Room room;

    RoomProbe(Duke3D client, Vector3f centre, Vector3f halfExtents, boolean enclosed) {
        if (!(halfExtents.x > 0f && halfExtents.y > 0f && halfExtents.z > 0f)) {
            throw new IllegalArgumentException("a room probe needs half extents above 0: " + halfExtents);
        }
        this.client = client;
        this.centre = centre.clone();
        this.halfExtents = halfExtents.clone();
        this.enclosed = enclosed;
    }

    /** Baked again at the next frame: what is in the room, or lights it, has changed. */
    public void rebake() {
        client.later(app -> {
            if (room != null) {
                room.rebake();
            }
        });
    }

    /** No longer lights the scene. */
    public void remove() {
        client.later(app -> {
            if (room != null) {
                room.remove();
                room = null;
            }
        });
    }
}
