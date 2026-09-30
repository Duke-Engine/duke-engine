package uz.dukeengine.game;

import uz.dukeengine.core.view.CameraView;

/**
 * The camera as the game drives it, from its own code on the simulation thread — moved to a point over so many logic
 * frames, turned to face another on the way, its pitch, zoom and angle set — and stepped once a logic frame, so a
 * flight the game scripts is flown the same way every time, as the reference flies its shell map's
 * ({@code moveCameraTo(pos, ms, 0, true, 0, 0)}: linear, no easing). A picture and not part of the world: nothing in it
 * is checksummed, and the client only shows it.
 *
 * <p>From the game's first order the camera is the game's — the client shows it, and the player's own camera controls
 * wait — until {@link #release} gives it back. Until then, and after, it follows where the player looks, as the client
 * reports it, so a move starts from the view on the screen and {@link #current} says what is shown.
 *
 * <p>Its units are {@link CameraView}'s.
 */
public final class GameCamera {

    private float x;
    private float y;
    private float angle;
    private float pitch = Float.NaN;
    private float zoom = Float.NaN;
    private boolean driven;

    private float fromX;
    private float fromY;
    private float toX;
    private float toY;
    private int frames;
    private int done;

    /** A turn under way with the move: from which angle, by how much, starting at which step of it. */
    private boolean turning;
    private float turnFrom;
    private float turnBy;
    private int turnStart;

    /** Where the player is looking, as the client last said; taken up while the game is not driving. */
    private volatile CameraView seen;

    /**
     * Move to look at {@code (x, y)} over so many logic frames — at once for none — in a straight line at an even
     * pace, keeping pitch and zoom. A move already under way is replaced, from where it had got to.
     */
    public void moveTo(float x, float y, int frames) {
        take();
        fromX = this.x;
        fromY = this.y;
        toX = x;
        toY = y;
        this.frames = Math.max(0, frames);
        done = 0;
        turning = false;
        if (this.frames == 0) {
            this.x = x;
            this.y = y;
        }
    }

    /**
     * Turn while the move under way goes, ending it facing {@code (x, y)} from where the move ends — the shorter way
     * round, at an even pace. With no move under way it faces the point at once.
     */
    public void lookToward(float x, float y) {
        take();
        boolean moving = isMoving();
        float dx = x - (moving ? toX : this.x);
        float dy = y - (moving ? toY : this.y);
        if (dx == 0f && dy == 0f) {
            return;
        }
        // Facing toward smaller y at angle 0: the way the client's unturned camera looks.
        float facing = (float) StrictMath.atan2(-dx, -dy);
        if (!moving) {
            angle = facing;
            return;
        }
        turning = true;
        turnFrom = angle;
        turnBy = wrap(facing - angle);
        turnStart = done;
    }

    /** Which way it faces, in radians; see {@link CameraView}. */
    public void angle(float radians) {
        take();
        turning = false;
        angle = radians;
    }

    /** How steeply it looks down, in radians above the ground. */
    public void pitch(float radians) {
        take();
        pitch = radians;
    }

    /** How far back it stands from the point it looks at, in world units. */
    public void zoom(float distance) {
        take();
        zoom = distance;
    }

    /** Give the camera back to the player, where it is. */
    public void release() {
        driven = false;
        frames = 0;
        done = 0;
        turning = false;
    }

    /** Whether a move is under way. */
    public boolean isMoving() {
        return done < frames;
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
        if (done >= frames) {
            return;
        }
        done++;
        float along = done / (float) frames;
        x = fromX + (toX - fromX) * along;
        y = fromY + (toY - fromY) * along;
        if (turning) {
            angle = turnFrom + turnBy * ((done - turnStart) / (float) (frames - turnStart));
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
        }
    }

    /** An angle brought into the half turn either side of none. */
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
