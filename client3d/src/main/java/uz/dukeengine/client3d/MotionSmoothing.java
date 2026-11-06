package uz.dukeengine.client3d;

import com.jme3.math.Vector3f;
import java.util.HashMap;
import java.util.Map;

/**
 * Where things are drawn between the simulation's steps. The simulation moves a thing thirty times a second and the
 * window draws faster; a thing drawn where the last step put it stands still for the frames in between and then jumps,
 * which beside a smooth camera is a shudder. Each thing is drawn on its way from where it was drawn when a step arrived
 * to where that step put it, over the time steps have been taking to arrive — a step behind the simulation, never
 * ahead of it, so what it shows has happened.
 */
final class MotionSmoothing {

    /** The shortest and longest a step is taken to take: a window faster than any screen, and a stalled one. */
    private static final float SHORTEST = 1f / 120f;
    private static final float LONGEST = 0.1f;
    /** A step further than this is a jump — a blink, a spawn — and is drawn at once. */
    static final float JUMP = 24f;

    private static final class Track {
        private final Vector3f from = new Vector3f();
        private final Vector3f to = new Vector3f();
        private final Vector3f shown = new Vector3f();
        private int frame;
        private float since;
        private float took = 1f / 30f;
    }

    private final Map<Integer, Track> tracks = new HashMap<>();

    /**
     * Where thing {@code id} is drawn at {@code now} (seconds), the simulation's frame being {@code frame} and the step
     * having put it at {@code placed}.
     */
    Vector3f at(int id, int frame, Vector3f placed, float now) {
        var track = tracks.get(id);
        if (track == null) {
            track = new Track();
            track.from.set(placed);
            track.to.set(placed);
            track.shown.set(placed);
            track.frame = frame;
            track.since = now;
            tracks.put(id, track);
            return placed.clone();
        }
        if (frame != track.frame) {
            boolean jumped = placed.distance(track.to) > JUMP;
            track.took = Math.clamp(now - track.since, SHORTEST, LONGEST);
            track.from.set(jumped ? placed : track.shown);
            track.to.set(placed);
            track.frame = frame;
            track.since = now;
        }
        float along = Math.clamp((now - track.since) / track.took, 0f, 1f);
        track.shown.set(track.from).interpolateLocal(track.to, along);
        return track.shown.clone();
    }

    /** A thing gone from the world. */
    void forget(int id) {
        tracks.remove(id);
    }
}
