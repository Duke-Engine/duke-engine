package uz.dukeengine.game;

import uz.dukeengine.core.view.CameraView;

/**
 * The camera as the game drives it, from its own code on the simulation thread — moved to a point over so many logic
 * frames, turned, tilted and drawn in or back on the way, each eased as the shot asks — and stepped once a logic frame,
 * so a flight the game scripts is flown the same way every time. Linear, as the reference flies its shell map's
 * ({@code moveCameraTo(pos, ms, 0, true, 0, 0)}), unless an {@link Ease} says otherwise. A picture and not part of the
 * world: nothing in it is checksummed, and the client only shows it.
 *
 * <p>From the game's first order the camera is the game's — the client shows it, and the player's own camera controls
 * wait — until {@link #release} gives it back. Until then, and after, it follows where the player looks, as the client
 * reports it, so a move starts from the view on the screen and {@link #current} says what is shown.
 *
 * <p>Its units are {@link CameraView}'s.
 */
public final class GameCamera {

    /** How a move gets from where it starts to where it ends over its frames. */
    public enum Ease {
        /** At an even pace. */
        LINEAR,
        /** Slow, and then quickening: setting off. */
        IN,
        /** Quick, and then settling: arriving. */
        OUT,
        /** Slow at both ends: a shot that sets off and settles. */
        IN_OUT;

        /** How far along at {@code t} of the way, both from 0 to 1. */
        float along(float t) {
            return switch (this) {
                case LINEAR -> t;
                case IN -> t * t;
                case OUT -> t * (2f - t);
                case IN_OUT -> t < 0.5f ? 2f * t * t : 1f - 2f * (1f - t) * (1f - t);
            };
        }
    }

    /** One figure of the camera on its way from one value to another over so many frames. */
    private static final class Tween {
        private float from;
        private float to;
        private int frames;
        private int done;
        private Ease ease = Ease.LINEAR;

        void start(float from, float to, int frames, Ease ease) {
            this.from = from;
            this.to = to;
            this.frames = Math.max(0, frames);
            this.done = 0;
            this.ease = ease == null ? Ease.LINEAR : ease;
        }

        boolean going() {
            return done < frames;
        }

        void stop() {
            frames = 0;
            done = 0;
        }

        /** One frame on; what the figure is now. */
        float step() {
            done++;
            return done >= frames ? to : from + (to - from) * ease.along(done / (float) frames);
        }
    }

    private float x;
    private float y;
    private float angle;
    private float pitch = Float.NaN;
    private float zoom = Float.NaN;
    private boolean driven;

    private final Tween movingX = new Tween();
    private final Tween movingY = new Tween();
    private final Tween turning = new Tween();
    private final Tween tilting = new Tween();
    private final Tween zooming = new Tween();

    /** Where the player is looking, as the client last said; taken up while the game is not driving. */
    private volatile CameraView seen;

    /**
     * Move to look at {@code (x, y)} over so many logic frames — at once for none — in a straight line at an even
     * pace, keeping pitch and zoom. A move already under way is replaced, from where it had got to.
     */
    public void moveTo(float x, float y, int frames) {
        moveTo(x, y, frames, Ease.LINEAR);
    }

    /** The same, eased as {@code ease} says. */
    public void moveTo(float x, float y, int frames, Ease ease) {
        take();
        turning.stop(); // a turn timed to the move it was given with is the old move's
        if (frames <= 0) {
            movingX.stop();
            movingY.stop();
            this.x = x;
            this.y = y;
            return;
        }
        movingX.start(this.x, x, frames, ease);
        movingY.start(this.y, y, frames, ease);
    }

    /**
     * Turn while the move under way goes, ending it facing {@code (x, y)} from where the move ends — the shorter way
     * round, at an even pace. With no move under way it faces the point at once.
     */
    public void lookToward(float x, float y) {
        take();
        boolean moving = movingX.going();
        float dx = x - (moving ? movingX.to : this.x);
        float dy = y - (moving ? movingY.to : this.y);
        if (dx == 0f && dy == 0f) {
            return;
        }
        // Facing toward smaller y at angle 0: the way the client's unturned camera looks.
        float facing = (float) StrictMath.atan2(-dx, -dy);
        if (!moving) {
            angle = facing;
            return;
        }
        turning.start(angle, angle + wrap(facing - angle), movingX.frames - movingX.done, Ease.LINEAR);
    }

    /** Turn to face {@code radians} over so many frames — the shorter way round — eased; at once for none. */
    public void turnTo(float radians, int frames, Ease ease) {
        take();
        if (frames <= 0) {
            angle(radians);
            return;
        }
        turning.start(angle, angle + wrap(radians - angle), frames, ease);
    }

    /** Tilt to look down {@code radians} above the ground over so many frames, eased; at once for none. */
    public void pitchTo(float radians, int frames, Ease ease) {
        take();
        if (frames <= 0 || Float.isNaN(pitch)) {
            tilting.stop();
            pitch = radians; // from nothing said there is nothing to tilt from
            return;
        }
        tilting.start(pitch, radians, frames, ease);
    }

    /** Draw in or back to stand {@code distance} from the point it looks at over so many frames, eased. */
    public void zoomTo(float distance, int frames, Ease ease) {
        take();
        if (frames <= 0 || Float.isNaN(zoom)) {
            zooming.stop();
            zoom = distance;
            return;
        }
        zooming.start(zoom, distance, frames, ease);
    }

    /** Which way it faces, in radians; see {@link CameraView}. */
    public void angle(float radians) {
        take();
        turning.stop();
        angle = radians;
    }

    /** How steeply it looks down, in radians above the ground. */
    public void pitch(float radians) {
        take();
        tilting.stop();
        pitch = radians;
    }

    /** How far back it stands from the point it looks at, in world units. */
    public void zoom(float distance) {
        take();
        zooming.stop();
        zoom = distance;
    }

    /** Give the camera back to the player, where it is. */
    public void release() {
        driven = false;
        movingX.stop();
        movingY.stop();
        turning.stop();
        tilting.stop();
        zooming.stop();
    }

    /** Whether a move, a turn, a tilt or a zoom is under way. */
    public boolean isMoving() {
        return movingX.going() || turning.going() || tilting.going() || zooming.going();
    }

    /** Whether the game has the camera. */
    public boolean isDriven() {
        return driven;
    }

    /** Where the camera is now. */
    public CameraView current() {
        return new CameraView(x, y, angle, pitch, zoom);
    }

    /** What the client is to show: the game's camera while the game has it, null while it is the player's. */
    CameraView shown() {
        return driven ? current() : null;
    }

    /** Where the player is looking, told by the client from its own thread. */
    void seen(CameraView view) {
        seen = view;
    }

    /** One logic frame. */
    void step() {
        if (!driven) {
            adoptSeen();
            return;
        }
        if (movingX.going()) {
            x = movingX.step();
            y = movingY.step();
        }
        if (turning.going()) {
            angle = turning.step();
        }
        if (tilting.going()) {
            pitch = tilting.step();
        }
        if (zooming.going()) {
            zoom = zooming.step();
        }
    }

    private void take() {
        if (!driven) {
            adoptSeen();
            driven = true;
        }
    }

    private void adoptSeen() {
        var view = seen;
        if (view != null) {
            x = view.x();
            y = view.y();
            angle = view.angle();
            if (!Float.isNaN(view.pitch())) {
                pitch = view.pitch();
            }
            if (!Float.isNaN(view.zoom())) {
                zoom = view.zoom();
            }
        }
    }

    private static float wrap(float radians) {
        float turn = (float) (2.0 * StrictMath.PI);
        float wrapped = radians % turn;
        if (wrapped > turn / 2f) {
            wrapped -= turn;
        } else if (wrapped < -turn / 2f) {
            wrapped += turn;
        }
        return wrapped;
    }
}
