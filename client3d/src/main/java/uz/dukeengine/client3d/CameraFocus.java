package uz.dukeengine.client3d;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import java.util.List;
import uz.dukeengine.core.view.UnitView;

/**
 * Where the camera is looking and how far back it stands.
 *
 * <p>An RTS camera is the player's, not the game's: it goes where he pans it and
 * stays there. But there are two moments when leaving it alone is wrong, because
 * the world has just changed underneath it — the first frame of a game, and the
 * first frame of a new run after dying. Both times the player's units are
 * somewhere he has never looked, and a camera obediently holding its old position
 * is showing him nothing.
 *
 * <p>So this is a one-shot request, not a follow: something asks for the camera to
 * be put on the player's units, and the next snapshot that actually contains one
 * satisfies the request and clears it. From then on the camera is his again. That
 * matters — a camera that kept following would take panning away, and looking
 * around while your hero stands still is most of how an RTS is played.
 *
 * <p>Held apart from the jME application because it is the part with answers that
 * can be wrong, and none of it needs a window: plain floats and a list of units.
 */
final class CameraFocus {

    /**
     * How far back the camera starts.
     *
     * <p>The camera sits about this far from the point it looks at, so it shows
     * roughly this much ground front to back and half again across. A dungeon room
     * is 50–90 units, which puts the hero's room on screen with its surroundings
     * around it — close enough to see what is happening, wide enough to see what is
     * coming.
     */
    static final float START_DISTANCE = 140f;

    private static final float NEAREST = 40f;
    private static final float FURTHEST = 400f;

    /** How fast a held turn turns it unless the game says: a quarter turn a second. */
    static final float TURN_SPEED = FastMath.HALF_PI;
    /** How far a held zoom takes it in a second unless the game says: halfway there. */
    static final float HELD_ZOOM = 0.5f;
    /** How many times a second the eye closes its share of the way. */
    private static final float EASE_STEPS = 30f;

    /** How steeply it looks down unless the game frames it: 0.82 up for 0.57 back, the slope it has always had. */
    static final float DEFAULT_PITCH = FastMath.atan2(0.82f, 0.57f);

    private float targetX;
    private float targetZ;
    private float distance = START_DISTANCE;
    /** Where the wheel and the zoom keys have set it, which the eye closes on; the same where it lands at once. */
    private float aimed = START_DISTANCE;
    private float turnSpeed = Float.NaN;
    private float zoomSpeed = Float.NaN;
    private float zoomEase = Float.NaN;
    private float pitch = DEFAULT_PITCH;
    private float nearest = NEAREST;
    private float furthest = FURTHEST;
    private float start = START_DISTANCE;
    private float wheelStep = Float.NaN;
    private float panAcross = Float.NaN;
    private float panAlong = Float.NaN;
    /** How far it is turned about the point it looks at, in radians; 0 is the way it starts. */
    private float yaw;
    private boolean wantsOwnUnit;
    private float groundWidth;
    private float groundHeight;
    /** The share of the view's height down to the point the edges are kept in by; NaN keeps the point on the map. */
    private float edgeShare = Float.NaN;
    /** The tangent of half the view's height, as the camera is laid into its part of the window; 0 before it is. */
    private float tanHalfHeight;
    /** How far in from each edge the point looked at is kept, as last worked out. */
    private float inset;

    /**
     * Framed as the game says — see {@link CameraFrame}: its pitch, how near and far it may come, where it starts, how
     * far a wheel notch takes it and how fast it pans, what the frame leaves NaN staying the client's own; and put back
     * to how it starts.
     */
    void frame(CameraFrame frame) {
        pitch = Float.isNaN(frame.pitch()) ? DEFAULT_PITCH : (float) Math.toRadians(frame.pitch());
        nearest = Float.isNaN(frame.nearest()) ? NEAREST : frame.nearest();
        furthest = Math.max(nearest, Float.isNaN(frame.furthest()) ? FURTHEST : frame.furthest());
        start = Math.clamp(Float.isNaN(frame.start()) ? START_DISTANCE : frame.start(), nearest, furthest);
        wheelStep = frame.wheelStep();
        panAcross = frame.panAcross();
        panAlong = frame.panAlong();
        turnSpeed = frame.turnSpeed();
        zoomSpeed = frame.zoomSpeed();
        zoomEase = frame.zoomEase();
        edgeShare = frame.edgeShare();
        resetView();
    }

    /** The view's shape as the camera is now laid: the tangent of half its height, which the edges are worked by. */
    void viewShape(float tanHalfHeight) {
        this.tanHalfHeight = tanHalfHeight;
        keepBackFromTheEdges();
    }

    /**
     * How far in from each edge of the map the point looked at is kept — the reference's {@code
     * W3DView::calcCameraConstraints}: the ground between the points under the view's middle and under a point {@code
     * share} of its height down, both on the level plane of the point looked at, for an eye {@code distance} back along
     * a line of sight {@code pitch} radians down.
     */
    static float inset(float pitch, float distance, float tanHalfHeight, float share) {
        float below = FastMath.atan((2f * share - 1f) * tanHalfHeight);
        float along = distance * FastMath.cos(pitch);
        float lower = pitch + below;
        if (lower >= FastMath.HALF_PI) {
            return along; // the lower point is under the eye or behind it
        }
        return along - distance * FastMath.sin(pitch) / FastMath.tan(lower);
    }

    /** The eye's distance was set: how far in the edges keep the point worked out again, from the eye as it stands. */
    private void keepBackFromTheEdges() {
        inset = Float.isNaN(edgeShare) || tanHalfHeight <= 0f ? 0f
                : Math.max(0f, inset(pitch, distance, tanHalfHeight, edgeShare));
        keepOnTheGround();
    }

    /** How far in from each edge of the map the point looked at is kept now. */
    float inset() {
        return inset;
    }

    /** Look here — used before there is a world, and by the minimap. */
    void lookAt(float worldX, float worldY) {
        this.targetX = worldX;
        this.targetZ = worldY;
        keepOnTheGround();
    }

    /**
     * The ground there is to look at: the map, in world units.
     *
     * <p>Without it a player who holds a pan key wanders off into black nothing
     * and has no way back — the map is behind him and there is no landmark
     * anywhere to say which way. The same rectangle the minimap draws, so what he
     * can look at and what the minimap shows are one thing rather than two.
     *
     * <p>The point that is kept inside is the one the camera looks at, not the
     * whole view, so the edge of the map can still be reached and seen with a
     * little void beyond it — which is how it should look. Zero means no map yet
     * and no limit; a demo that never says gets the old free camera.
     */
    void keepInside(float worldWidth, float worldHeight) {
        this.groundWidth = Math.max(0f, worldWidth);
        this.groundHeight = Math.max(0f, worldHeight);
        keepOnTheGround();
    }

    private void keepOnTheGround() {
        if (groundWidth <= 0f || groundHeight <= 0f) {
            return;
        }
        targetX = keptIn(targetX, groundWidth);
        targetZ = keptIn(targetZ, groundHeight);
    }

    /** Along one side of the map: at least the inset from either edge, or its middle where the two insets meet. */
    private float keptIn(float at, float side) {
        return inset * 2f >= side ? side / 2f : Math.clamp(at, inset, side - inset);
    }

    /**
     * Ask to be put on the player's units as soon as there are any.
     *
     * <p>Deferred rather than done now because the caller — a game starting, a new
     * dungeon being laid out — knows the world has changed before the simulation
     * has produced a snapshot showing it.
     */
    void requestOwnUnit() {
        wantsOwnUnit = true;
    }

    boolean isAwaitingOwnUnit() {
        return wantsOwnUnit;
    }

    /**
     * Satisfy a pending request if this snapshot has one of the player's units.
     * Does nothing at all when none is pending — the camera is the player's.
     *
     * @return whether the camera was moved
     */
    boolean focusOnOwnUnit(List<UnitView> units, int localPlayer) {
        if (!wantsOwnUnit) {
            return false;
        }
        for (var unit : units) {
            if (unit.playerIndex() == localPlayer) {
                lookAt(unit.x(), unit.y());
                wantsOwnUnit = false;
                return true;
            }
        }
        return false; // nothing of his on screen yet; keep asking
    }

    /** Pan by a world-space offset (the WASD keys). */
    void panBy(float dx, float dz) {
        targetX += dx;
        targetZ += dz;
        keepOnTheGround();
    }

    /** Zoom by a factor, kept between the nearest and furthest useful distances. */
    void zoomBy(float factor) {
        aimAt(aimed * factor);
    }

    /** One notch of the wheel, in or out: the frame's step along its line of sight, or the client's own 0.92 and 1.09. */
    void wheel(boolean in) {
        if (Float.isNaN(wheelStep)) {
            zoomBy(in ? 0.92f : 1.09f);
            return;
        }
        aimAt(aimed + (in ? -wheelStep : wheelStep));
    }

    /** The zoom keys held for {@code seconds}: the frame's speed along the line of sight, else halving or doubling. */
    void heldZoom(boolean in, float seconds) {
        if (Float.isNaN(zoomSpeed)) {
            zoomBy((float) Math.pow(in ? HELD_ZOOM : 1f / HELD_ZOOM, seconds));
            return;
        }
        aimAt(aimed + (in ? -zoomSpeed : zoomSpeed) * seconds);
    }

    /** The turn keys held for {@code seconds}, {@code way} 1 leftward and -1 rightward, at the frame's speed. */
    void heldTurn(int way, float seconds) {
        turnBy(way * (Float.isNaN(turnSpeed) ? TURN_SPEED : turnSpeed) * seconds);
    }

    /** Where the eye is to go, kept between the nearest and the furthest; there at once where it does not ease. */
    private void aimAt(float wanted) {
        aimed = Math.clamp(wanted, nearest, furthest);
        if (!eases()) {
            distance = aimed;
        }
        keepBackFromTheEdges();
    }

    private boolean eases() {
        return zoomEase > 0f && zoomEase < 1f; // NaN is neither
    }

    /**
     * {@code seconds} of the window gone: the eye closes the frame's share of the way to where the wheel and the keys
     * set it each thirtieth of a second — the reference's {@code W3DView::update}, a notch 83% done after 5 frames and
     * 97% after 10 — or is there already, where it does not ease.
     */
    void approach(float seconds) {
        distance = eases() ? aimed + (distance - aimed) * (float) Math.pow(1f - zoomEase, seconds * EASE_STEPS) : aimed;
    }

    /** Turn it about the point it looks at. */
    void turnBy(float radians) {
        yaw = (yaw + radians) % (2f * (float) Math.PI);
    }

    /** Back to how it starts: unturned, at its first distance, looking where it looks. */
    void resetView() {
        yaw = 0f;
        distance = start;
        aimed = start;
        keepBackFromTheEdges();
    }

    /** Where it looks, which way it is turned and how far back it stands: what a bookmark keeps. */
    record View(float x, float z, float yaw, float distance) {
    }

    View view() {
        return new View(targetX, targetZ, yaw, distance);
    }

    /** Back to a view that was kept. */
    void restore(View view) {
        targetX = view.x();
        targetZ = view.z();
        yaw = view.yaw();
        distance = Math.clamp(view.distance(), nearest, furthest);
        aimed = distance;
        keepBackFromTheEdges();
    }

    float yaw() {
        return yaw;
    }

    /** How fast panning should feel — further back, faster, so it takes the same time. */
    float panSpeed() {
        return distance * 0.9f;
    }

    /** How fast the keys pan it across the screen, in world units a second: the frame's whatever the zoom, else faster further back. */
    float panAcross() {
        return Float.isNaN(panAcross) ? panSpeed() : panAcross;
    }

    /** And up and down it, along the ground. */
    float panAlong() {
        return Float.isNaN(panAlong) ? panSpeed() : panAlong;
    }

    /** How steeply the player's camera looks down, in radians: the frame's, else the client's own. */
    float pitch() {
        return pitch;
    }

    /** Where the eye stands from the point it looks at: its distance back along its line of sight, turned, at {@code pitch}. */
    Vector3f eyeOffset(float pitch) {
        return new Quaternion().fromAngleAxis(yaw, Vector3f.UNIT_Y)
                .mult(new Vector3f(0f, distance * FastMath.sin(pitch), distance * FastMath.cos(pitch)));
    }

    float targetX() {
        return targetX;
    }

    float targetZ() {
        return targetZ;
    }

    float distance() {
        return distance;
    }
}
