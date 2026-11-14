package uz.dukeengine.render;

import com.jme3.asset.AssetManager;
import com.jme3.light.LightProbe;
import com.jme3.math.Vector3f;
import com.jme3.renderer.RenderManager;
import com.jme3.scene.Node;
import java.util.ArrayList;
import java.util.List;

/**
 * The rooms of a scene, each lit by a box probe of its own ({@link LocalProbes}), and when they are baked. The sky's probe is
 * the light everywhere no room is: outside, and past a room's feathered faces. Inside a room its probe takes over (the
 * narrowest first, weight 1 inside, the sky 0), as {@code LightProbes.glsllib} blends them.
 *
 * <p>A room asked to be baked ({@link #place}, {@link Room#rebake}, {@link #relight}) is baked at the next {@link #update}:
 * many asks in a frame are one bake. Once baked it lights the scene from the root it was given. A room that is
 * {@code enclosed} is baked with no other probe on the root, the sky's either: what lights it is its own lamps and what
 * glows in it, not a sky its walls keep out. One that is not is baked as {@link LocalProbes#bake} bakes.
 */
public final class RoomProbes {

    /** How a room's probe is baked, given the root and whether the room is enclosed: what the tests put in for the GPU. */
    interface Baker {
        double bake(Node root, LightProbe probe, boolean enclosed);
    }

    private final Node root;
    private final Baker baker;
    private final List<Room> rooms = new ArrayList<>();

    public RoomProbes(AssetManager assets, RenderManager renderManager, Node root) {
        this(root, (scene, probe, enclosed) -> bake(assets, renderManager, scene, probe, enclosed));
    }

    RoomProbes(Node root, Baker baker) {
        this.root = root;
        this.baker = baker;
    }

    /**
     * A room whose inner faces are {@code halfExtents} from {@code centre} along the world axes, baked at the next
     * {@link #update} and lighting the scene from then on.
     */
    public Room place(Vector3f centre, Vector3f halfExtents, boolean enclosed) {
        var room = new Room(LocalProbes.box(centre, halfExtents), enclosed);
        rooms.add(room);
        return room;
    }

    /** Every room baked again at the next {@link #update}: when a lamp comes on or goes off, or the world is another. */
    public void relight() {
        rooms.forEach(room -> room.due = true);
    }

    /**
     * Bakes the rooms asked for since the last call. Once a frame, on the render thread, outside the frame's drawing.
     *
     * @return how long the bakes took, in milliseconds (0 with nothing to bake)
     */
    public double update() {
        double ms = 0;
        for (var room : rooms) {
            if (room.due) {
                room.due = false;
                ms += baker.bake(root, room.probe, room.enclosed);
                if (!room.lights) {
                    root.addLight(room.probe);
                    room.lights = true;
                }
            }
        }
        return ms;
    }

    /** How many rooms there are. */
    public int size() {
        return rooms.size();
    }

    /**
     * {@link LocalProbes#bake}, with the root's other probes (the sky's among them) taken away for the bake when the room is
     * enclosed.
     */
    static double bake(AssetManager assets, RenderManager renderManager, Node root, LightProbe probe, boolean enclosed) {
        var others = new ArrayList<LightProbe>();
        if (enclosed) {
            for (var light : root.getLocalLightList()) {
                if (light instanceof LightProbe other && other != probe) {
                    others.add(other);
                }
            }
            others.forEach(root::removeLight);
        }
        try {
            return LocalProbes.bake(assets, renderManager, root, probe);
        } finally {
            others.forEach(root::addLight);
        }
    }

    /** One room: what a game keeps to bake it again or take it away. */
    public final class Room {

        final LightProbe probe;
        private final boolean enclosed;
        boolean due = true;
        private boolean lights;

        private Room(LightProbe probe, boolean enclosed) {
            this.probe = probe;
            this.enclosed = enclosed;
        }

        /** Baked again at the next {@link #update}: what is in the room, or lights it, has changed. */
        public void rebake() {
            if (rooms.contains(this)) {
                due = true;
            }
        }

        /** No longer lights the scene, nor is baked again. */
        public void remove() {
            rooms.remove(this);
            due = false;
            if (lights) {
                root.removeLight(probe);
                lights = false;
            }
        }

        public boolean enclosed() {
            return enclosed;
        }
    }
}
