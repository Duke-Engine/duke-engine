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
    /**
     * How much of each step's own time goes into the time steps are taken to take: steps arrive unevenly (Windows
     * sleeps in 15.6 ms ticks, so a sixtieth and a twentieth apart), and drawn at the last one's pace a thing ran
     * ahead, stood, and ran ahead again — a shudder beside a smooth camera.
     */
    private static final float SETTLE = 0.1f;
    /** A step is drawn over a little longer than steps take, so one that comes late finds the thing still on its way. */
    private static final float SLACK = 1.6f;

    private static final class Track {
        private final Vector3f from = new Vector3f();
        private final Vector3f to = new Vector3f();
        private final Vector3f shown = new Vector3f();
        private int frame;
        private float since;
        private float drawn;
        private float took = 1f / 30f;
        private float along = 1f;
        private float fromFacing = Float.NaN;
        private float toFacing;
        private float shownFacing;
        private int facingFrame;
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
            track.drawn = now;
            tracks.put(id, track);
            return placed.clone();
        }
        if (frame != track.frame) {
            boolean jumped = placed.distance(track.to) > JUMP;
            track.took += (Math.clamp(now - track.since, SHORTEST, LONGEST) - track.took) * SETTLE;
            track.from.set(jumped ? placed : track.shown);
            track.to.set(placed);
            track.frame = frame;
            // Come since it was last drawn: begun there, so the frame it arrives on moves it on rather than standing.
            track.since = track.drawn;
        }
        track.drawn = now;
        track.along = Math.clamp((now - track.since) / (track.took * SLACK), 0f, 1f);
        track.shown.set(track.from).interpolateLocal(track.to, track.along);
        return track.shown.clone();
    }

    /**
     * The way thing {@code id} is drawn facing (radians), the step {@link #at} last took for it having turned it to
     * {@code facing}: on its way there the short way round, as its place is — a turn drawn thirty times a second is a
     * flicker as the camera swings round him.
     */
    float facing(int id, float facing) {
        var track = tracks.get(id);
        if (track == null) {
            return facing;
        }
        if (Float.isNaN(track.fromFacing)) {
            track.fromFacing = facing;
            track.shownFacing = facing;
            track.facingFrame = track.frame;
        }
        if (track.facingFrame != track.frame || track.along >= 1f) {
            track.fromFacing = track.shownFacing;
            track.facingFrame = track.frame;
        }
        track.toFacing = facing;
        float turn = track.toFacing - track.fromFacing;
        turn = (float) Math.atan2(Math.sin(turn), Math.cos(turn));
        float shown = track.fromFacing + turn * track.along;
        track.shownFacing = (float) Math.atan2(Math.sin(shown), Math.cos(shown));
        return track.shownFacing;
    }

    /** A thing gone from the world. */
    void forget(int id) {
        tracks.remove(id);
    }
}
