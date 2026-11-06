package uz.dukeengine.game;

import java.util.List;
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
public final class GameCamera implements uz.dukeengine.core.save.Saveable {

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

        void save(uz.dukeengine.core.save.SaveOut out) {
            out.writeFloat(from).writeFloat(to).writeInt(frames).writeInt(done).writeEnum(ease);
        }

        void load(uz.dukeengine.core.save.SaveIn in) {
            from = in.readFloat();
            to = in.readFloat();
            frames = in.readInt();
            done = in.readInt();
            ease = in.readEnum(Ease.class);
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
    /** Whether the view the game drives is kept clear of standing scenery: see {@link #keepClear}. Not saved. */
    private boolean clear;

    private final Tween movingX = new Tween();
    private final Tween movingY = new Tween();
    private final Tween turning = new Tween();
    private final Tween tilting = new Tween();
    private final Tween zooming = new Tween();

    /** One point of a camera path: where it looks, which way it faces, how steeply, and how far back it stands. */
    public record Key(float x, float y, float angle, float pitch, float zoom) {
    }

    /** A path under way: its keys, its frames, how it eases, and how many frames are gone. */
    private List<Key> path = List.of();
    private int pathFrames;
    private int pathDone;
    private Ease pathEase = Ease.LINEAR;

    /**
     * Fly through {@code keys} in one move over {@code frames}: on a smooth curve through every key's point
     * (Catmull-Rom), its turn, pitch and zoom eased with it, the whole eased by {@code ease} — from the first key, which
     * it starts at, to the last, where it stays. Each key's span is an equal share of the frames, so it stands still
     * between none. Replaces any move under way; not checksummed, as the camera is not.
     */
    public void along(List<Key> keys, int frames, Ease ease) {
        take();
        movingX.stop();
        movingY.stop();
        turning.stop();
        tilting.stop();
        zooming.stop();
        if (keys == null || keys.isEmpty()) {
            return;
        }
        path = List.copyOf(keys);
        pathFrames = Math.max(1, frames);
        pathDone = 0;
        pathEase = ease == null ? Ease.LINEAR : ease;
        put(path.getFirst());
        if (path.size() == 1) {
            path = List.of();
        }
    }

    private void put(Key key) {
        x = key.x();
        y = key.y();
        angle = key.angle();
        pitch = key.pitch();
        zoom = key.zoom();
    }

    /** One frame along the path: the segment its eased share falls in, and the point on the curve there. */
    private void stepAlong() {
        pathDone++;
        int last = path.size() - 1;
        float u = pathEase.along(Math.min(1f, pathDone / (float) pathFrames)) * last;
        int at = Math.min(last - 1, (int) Math.floor(u));
        float t = u - at;
        var p0 = path.get(Math.max(0, at - 1));
        var p1 = path.get(at);
        var p2 = path.get(at + 1);
        var p3 = path.get(Math.min(last, at + 2));
        x = curve(p0.x(), p1.x(), p2.x(), p3.x(), t);
        y = curve(p0.y(), p1.y(), p2.y(), p3.y(), t);
        angle = p1.angle() + wrap(p2.angle() - p1.angle()) * t;
        pitch = curve(p0.pitch(), p1.pitch(), p2.pitch(), p3.pitch(), t);
        zoom = curve(p0.zoom(), p1.zoom(), p2.zoom(), p3.zoom(), t);
        if (pathDone >= pathFrames) {
            put(path.getLast());
            path = List.of();
        }
    }

    /** The Catmull-Rom curve through {@code b} and {@code c}, shaped by {@code a} and {@code d}, at {@code t}. */
    private static float curve(float a, float b, float c, float d, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return 0.5f * (2f * b + (c - a) * t + (2f * a - 5f * b + 4f * c - d) * t2 + (3f * b - a - 3f * c + d) * t3);
    }

    /** Where the player is looking, as the client last said; taken up while the game is not driving. */
    private volatile CameraView seen;

    /**
     * What a scene puts over the whole picture, for the client to draw — the reference's {@code CAMERA_FADE_*}, {@code
     * LETTERBOX_MODE} and {@code DISABLE_INPUT}.
     *
     * @param colour      the fade's colour, {@code 0xRRGGBB}
     * @param fade        how much of it covers the world, 0 none to 1 all, under the interface
     * @param letterbox   how far the bars are in, 0 out to 1 each at {@link #LETTERBOX_SHARE} of the height
     * @param inputLocked whether the player's picking, selecting, ordering and camera keys wait (never Esc, nor chat)
     */
    public record Screen(int colour, float fade, float letterbox, boolean inputLocked) {

        /** A bar's share of the height, all the way in. */
        public static final float LETTERBOX_SHARE = 0.12f;

        public static final Screen CLEAR = new Screen(0, 0f, 0f, false);
    }

    private final Tween fading = new Tween();
    private final Tween barring = new Tween();
    private int fadeColour;
    private float fade;
    private float letterbox;
    private boolean inputLocked;
    private volatile Screen screen = Screen.CLEAR;
    private uz.dukeengine.core.thing.ObjectId followed;
    private int catchUpFrames;

    /** Fade the world to {@code colour} ({@code 0xRRGGBB}) from {@code from} to {@code to} covered over so many frames. */
    public void fade(int colour, float from, float to, int frames) {
        fadeColour = colour & 0xFFFFFF;
        fading.start(Math.clamp(from, 0f, 1f), Math.clamp(to, 0f, 1f), frames, Ease.LINEAR);
        fade = frames <= 0 ? Math.clamp(to, 0f, 1f) : Math.clamp(from, 0f, 1f);
        publish();
    }

    /** Slide the bars at the top and bottom in, or out, over so many frames. */
    public void letterbox(boolean on, int frames) {
        barring.start(letterbox, on ? 1f : 0f, frames, Ease.IN_OUT);
        if (frames <= 0) {
            letterbox = on ? 1f : 0f;
        }
        publish();
    }

    /** Keep the player's hands off the world — picking, selecting, orders and camera keys, not Esc or chat — or not. */
    public void lockInput(boolean locked) {
        inputLocked = locked;
        publish();
    }

    /**
     * Keep {@code thing} in view each frame while the game has the camera, closing a share of the way a frame so it
     * catches up in about {@code frames}; null lets go, where it is.
     */
    public void follow(uz.dukeengine.core.thing.ObjectId thing, int frames) {
        if (thing != null) {
            take();
            movingX.stop();
            movingY.stop();
            path = List.of();
        }
        followed = thing;
        catchUpFrames = Math.max(1, frames);
    }

    /** What the client puts over the picture now; read from any thread. */
    public Screen screen() {
        return screen;
    }

    private void publish() {
        screen = new Screen(fadeColour, fade, letterbox, inputLocked);
    }

    private void stepScreen() {
        if (fading.going()) {
            fade = fading.step();
        }
        if (barring.going()) {
            letterbox = barring.step();
        }
        publish();
    }

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
        clear = false;
        followed = null;
        path = List.of();
        movingX.stop();
        movingY.stop();
        turning.stop();
        tilting.stop();
        zooming.stop();
    }

    /** Whether a move, a turn, a tilt or a zoom is under way. */
    public boolean isMoving() {
        return !path.isEmpty() || movingX.going() || turning.going() || tilting.going() || zooming.going();
    }

    /**
     * Keep the eye the game drives out of standing scenery: pulled in toward what it looks at, as the player's own camera
     * is where the client's scheme does that (a talk filmed close, in a court of walls), its gaze as it was; a wide
     * cut scene leaves it off. Off again at {@link #release}. Drawing only: nothing of the simulation reads it.
     */
    public void keepClear(boolean on) {
        clear = on;
    }

    /** See {@link #keepClear}. */
    public boolean keepsClear() {
        return clear;
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

    /** One logic frame, a followed thing found where {@code where} says (null for gone). */
    void step(java.util.function.Function<uz.dukeengine.core.thing.ObjectId, uz.dukeengine.core.math.Coord3D> where) {
        stepScreen();
        if (driven && followed != null) {
            var at = where.apply(followed);
            if (at == null) {
                followed = null;
            } else {
                float share = 1f / catchUpFrames;
                x += (at.x() - x) * share;
                y += (at.y() - y) * share;
            }
        }
        step();
    }

    /** One logic frame. */
    void step() {
        if (!driven) {
            adoptSeen();
            return;
        }
        if (!path.isEmpty()) {
            stepAlong();
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

    /**
     * Where it stands and every move, path, fade, bar and follow under way, so a scene loaded goes on as it was saved;
     * what the client last said it saw is not written, and is taken to be the camera's own until the client says again.
     */
    @Override
    public void save(uz.dukeengine.core.save.SaveOut out) {
        out.writeFloat(x).writeFloat(y).writeFloat(angle).writeFloat(pitch).writeFloat(zoom).writeBoolean(driven);
        for (var tween : List.of(movingX, movingY, turning, tilting, zooming, fading, barring)) {
            tween.save(out);
        }
        out.writeInt(path.size());
        for (var key : path) {
            out.writeFloat(key.x()).writeFloat(key.y()).writeFloat(key.angle()).writeFloat(key.pitch())
                    .writeFloat(key.zoom());
        }
        out.writeInt(pathFrames).writeInt(pathDone).writeEnum(pathEase);
        out.writeInt(fadeColour).writeFloat(fade).writeFloat(letterbox).writeBoolean(inputLocked);
        out.writeId(followed).writeInt(catchUpFrames);
    }

    @Override
    public void load(uz.dukeengine.core.save.SaveIn in) {
        x = in.readFloat();
        y = in.readFloat();
        angle = in.readFloat();
        pitch = in.readFloat();
        zoom = in.readFloat();
        driven = in.readBoolean();
        for (var tween : List.of(movingX, movingY, turning, tilting, zooming, fading, barring)) {
            tween.load(in);
        }
        var keys = new java.util.ArrayList<Key>();
        for (int count = in.readInt(); count > 0; count--) {
            keys.add(new Key(in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat()));
        }
        path = List.copyOf(keys);
        pathFrames = in.readInt();
        pathDone = in.readInt();
        pathEase = in.readEnum(Ease.class);
        fadeColour = in.readInt();
        fade = in.readFloat();
        letterbox = in.readFloat();
        inputLocked = in.readBoolean();
        followed = in.readId();
        catchUpFrames = in.readInt();
        seen = current();
        publish();
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
