package uz.dukeengine.core.module;

import uz.dukeengine.core.event.ThingLanded;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;

/**
 * A thing moved by the story over so many frames — a leap, a knock-back's hop, a fall from the sky — from where it
 * stands to a point: along a straight line, over an arc so high at its middle, or straight down onto the point from a
 * height. No legs carry it: it takes no orders while it goes (its legs are stopped each frame) and no other mover
 * moves it. It comes down on the point on its last frame, posts a {@link ThingLanded} for the picture, and tells the
 * story on the simulation's thread ({@link #onLanded}). In the world and so in the checksum, by where it stands.
 */
public final class ScriptedMove extends UpdateModule {

    private final Coord3D from;
    private final Coord3D to;
    private final int frames;
    private final float arc;
    private Runnable landed = () -> { };
    private int frame;
    private boolean done;

    private ScriptedMove(GameObject owner, Coord3D from, Coord3D to, int frames, float arc) {
        super(owner);
        this.from = from;
        this.to = to;
        this.frames = Math.max(1, frames);
        this.arc = arc;
    }

    /**
     * Move {@code thing} to {@code to} over {@code frames}, along an arc {@code arcHeight} high at its middle — 0 for a
     * slide along the ground. A move it was already on is given up from where it had got to.
     */
    public static ScriptedMove along(GameObject thing, Coord3D to, int frames, float arcHeight) {
        return put(thing, thing.getPosition(), to, frames, arcHeight);
    }

    /** Drop {@code thing} straight down onto {@code to} from {@code height} above it, over {@code frames}. */
    public static ScriptedMove fall(GameObject thing, Coord3D to, float height, int frames) {
        var above = new Coord3D(to.x(), to.y(), groundAt(thing, to) + height);
        thing.setPosition(above);
        return put(thing, above, to, frames, 0f);
    }

    private static ScriptedMove put(GameObject thing, Coord3D from, Coord3D to, int frames, float arc) {
        for (var module : java.util.List.copyOf(thing.getModules())) {
            if (module instanceof ScriptedMove old) {
                thing.removeModule(old);
            }
        }
        var move = new ScriptedMove(thing, from, to, frames, arc);
        thing.addModule(move);
        return move;
    }

    /** What the story does when it comes down: run once, on the simulation's thread, on its last frame. */
    public ScriptedMove onLanded(Runnable landed) {
        this.landed = landed == null ? () -> { } : landed;
        return this;
    }

    /** Whether it has come down. */
    public boolean isDone() {
        return done;
    }

    /** Its legs are not its own until it has come down. */
    @Override
    public boolean keepsBusy() {
        return !done;
    }

    @Override
    public void update() {
        if (done) {
            return;
        }
        var owner = getOwner();
        var legs = owner.getLocomotor();
        if (legs != null && legs.isMoving()) {
            legs.stop();
        }
        frame++;
        float t = frame / (float) frames;
        float x = from.x() + (to.x() - from.x()) * t;
        float y = from.y() + (to.y() - from.y()) * t;
        var flat = new Coord3D(x, y, 0f);
        // On the ground under it, or along the line from where it began to where it is going, and the arc over that.
        float ground = from.z() > groundAt(owner, from) + 0.001f || to.z() > groundAt(owner, to) + 0.001f
                ? from.z() + (to.z() - from.z()) * t : groundAt(owner, flat);
        float z = ground + 4f * arc * t * (1f - t);
        if (frame >= frames) {
            owner.setPosition(to);
            done = true;
            var world = owner.getWorld();
            if (world != null) {
                world.post(new ThingLanded(world.getFrame(), owner.getId(), to));
            }
            landed.run();
            return;
        }
        owner.setPosition(new Coord3D(x, y, z));
    }

    private static float groundAt(GameObject thing, Coord3D at) {
        var world = thing.getWorld();
        return world == null ? 0f : world.groundHeight(at);
    }
}
