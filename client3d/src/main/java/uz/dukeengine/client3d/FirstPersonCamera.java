package uz.dukeengine.client3d;

import com.jme3.collision.CollisionResults;
import com.jme3.font.BitmapText;
import com.jme3.math.FastMath;
import com.jme3.math.Ray;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.List;
import java.util.Optional;

/**
 * The player's own eyes, standing in the scene ({@link Duke3D#firstPerson}): the held mouse turns the view, W, A, S and
 * D walk it within the walls, a cross marks the middle of the window, and whatever is under the cross within reach is
 * what is pointed at. The RTS camera, its pan keys and the window's edges are off while it is on. Frozen, it lets the
 * mouse go and neither looks nor walks — for a screen of the game's own over the world.
 */
public final class FirstPersonCamera implements ControlScheme {

    private static final float MAX_PITCH = 89f * FastMath.DEG_TO_RAD;
    /** Near enough for a bench at arm's length; the client's own near plane is for a camera far over the map. */
    private static final float NEAR = 0.05f;
    private static final float FAR = 200f;
    /** How fast a walk gathers speed, m/s²: 3 m/s in a quarter second, so the first step is not a jump. */
    public static final float ACCEL = 12f;
    /** How fast it stops once the keys are let go, m/s²: a little quicker than it starts. */
    public static final float BRAKE = 18f;

    private FirstPerson config;
    private Vector3f eye;
    private float yaw;
    private float pitch;
    /** How fast the eye walks now, m/s along x and z. */
    private final Vector3f velocity = new Vector3f();
    private boolean frozen;
    private boolean running;
    private Context context;
    private BitmapText crosshair;

    /** Standing in the middle of its walls, looking along -z. */
    public FirstPersonCamera(FirstPerson config) {
        this.config = config;
        this.eye = new Vector3f((config.minX() + config.maxX()) / 2f, config.eyeHeight(),
                (config.minZ() + config.maxZ()) / 2f);
    }

    /** How fast the eye walks now, m/s along x and z (y 0): a body's legs follow it. */
    public Vector3f velocity() {
        return velocity.clone();
    }

    public Vector3f eye() {
        return eye.clone();
    }

    /** Which way it faces, radians: 0 along -z, rising to the left. */
    public float yaw() {
        return yaw;
    }

    /** How far it looks up, radians; down is below 0. */
    public float pitch() {
        return pitch;
    }

    public boolean frozen() {
        return frozen;
    }

    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
        if (frozen) {
            velocity.zero();
        }
    }

    /** Runs ({@link FirstPerson#run}) from now on in place of walking, or walks again: the game's own run key. */
    public void setRunning(boolean running) {
        this.running = running;
    }

    /** Walks round these footprints from now on in place of the ones it had: a door shut or opened, a thing moved. */
    public void setBlocked(List<FirstPerson.Footprint> footprints) {
        config = config.blocking(footprints);
    }

    /** Walks by these rules from now on, walls and all: the player has gone through to another place. */
    public void setWalk(FirstPerson walk) {
        config = walk;
    }

    /** Puts the eye at {@code eye} looking {@code yaw} across and {@code pitch} up: sitting down, a cut scene. */
    public void moveTo(Vector3f eye, float yaw, float pitch) {
        this.eye = eye.clone();
        velocity.zero();
        this.yaw = yaw;
        this.pitch = clampPitch(pitch);
    }

    /** Puts the eye at {@code eye} and keeps its walk going: a game's own nudge each frame (a lean, a tremble), not a jump. */
    public void nudge(Vector3f eye) {
        this.eye = eye.clone();
    }

    /** The nearest thing under the cross, within reach. */
    public Optional<Geometry> pointed(Node under) {
        var hits = new CollisionResults();
        under.collideWith(new Ray(eye.clone(), direction()), hits);
        var nearest = hits.getClosestCollision();
        if (nearest == null || nearest.getDistance() > config.reach()) {
            return Optional.empty();
        }
        return Optional.of(nearest.getGeometry());
    }

    /** Where a walk of {@code tpf} seconds takes the eye, kept within the walls and off the blocked footprints. */
    public static Vector3f step(Vector3f eye, float yaw, boolean fwd, boolean back, boolean left, boolean right,
            float speed, float tpf, FirstPerson bounds) {
        var w = wish(yaw, fwd, back, left, right, speed);
        return step(eye, w.x * tpf, w.z * tpf, bounds);
    }

    /** The speed the held keys ask for, m/s along x and z: {@code speed} whichever way they point, none for none. */
    static Vector3f wish(float yaw, boolean fwd, boolean back, boolean left, boolean right, float speed) {
        float sin = FastMath.sin(yaw);
        float cos = FastMath.cos(yaw);
        float x = 0f;
        float z = 0f;
        if (fwd) {
            x -= sin;
            z -= cos;
        }
        if (back) {
            x += sin;
            z += cos;
        }
        if (right) {
            x += cos;
            z -= sin;
        }
        if (left) {
            x -= cos;
            z += sin;
        }
        float length = FastMath.sqrt(x * x + z * z);
        float d = length == 0f ? 0f : speed / length;
        return new Vector3f(x * d, 0f, z * d);
    }

    /** Where a walk of {@code dx}, {@code dz} metres takes the eye, kept within the walls and off the blocked footprints. */
    public static Vector3f step(Vector3f eye, float dx, float dz, FirstPerson bounds) {
        // An axis at a time: a blocked x still lets z through, so a slanting walk slides along the footprint.
        float r = bounds.radius();
        float toX = FastMath.clamp(eye.x + dx, bounds.minX(), bounds.maxX());
        for (var f : bounds.blocked()) {
            if (f.acrossZ(eye.z, r)) {
                toX = stop(eye.x, toX, f.minX() - r, f.maxX() + r);
            }
        }
        float toZ = FastMath.clamp(eye.z + dz, bounds.minZ(), bounds.maxZ());
        for (var f : bounds.blocked()) {
            if (f.acrossX(toX, r)) {
                toZ = stop(eye.z, toZ, f.minZ() - r, f.maxZ() + r);
            }
        }
        return new Vector3f(toX, eye.y, toZ);
    }

    /**
     * The walking speed a frame of {@code tpf} seconds later: towards {@code wish} (m/s, x and z) by at most
     * {@link #ACCEL} a second while a key is held, {@link #BRAKE} once none is. A turn is a change of speed like any other.
     */
    public static Vector3f pace(Vector3f velocity, Vector3f wish, float tpf) {
        var change = wish.subtract(velocity);
        float most = (wish.lengthSquared() > 0f ? ACCEL : BRAKE) * tpf;
        float length = change.length();
        return length <= most ? wish.clone() : velocity.add(change.multLocal(most / length));
    }

    /** A walk from {@code from} to {@code to} on one axis, stopped at the edge of {@code [min, max]} it would cross. */
    private static float stop(float from, float to, float min, float max) {
        if (from <= min && to > min) {
            return min;
        }
        if (from >= max && to < max) {
            return max;
        }
        // Already inside — stood up there, or put there — it walks out freely.
        return to;
    }

    public static float clampPitch(float pitch) {
        return FastMath.clamp(pitch, -MAX_PITCH, MAX_PITCH);
    }

    @Override
    public void attach(Context context) {
        this.context = context;
        crosshair = new BitmapText(context.font());
        crosshair.setText("+");
        context.gui().attachChild(crosshair);
    }

    @Override
    public void frame(float tpf) {
        if (context == null) {
            return;
        }
        boolean playing = context.playing();
        context.holdMouse(playing && !frozen);
        // Frozen, the mouse is free and is its own pointer: a cross in the middle would point at nothing.
        crosshair.setCullHint(frozen ? Spatial.CullHint.Always : Spatial.CullHint.Inherit);
        if (!playing) {
            return;
        }
        float[] moved = context.mouseMoved();
        move(context.keyHeld('W'), context.keyHeld('S'), context.keyHeld('A'), context.keyHeld('D'), moved[0],
                moved[1], tpf);
    }

    /** The left button is the game's, through its scene: the client selects nothing by it. */
    @Override
    public boolean left(boolean pressed) {
        return true;
    }

    @Override
    public boolean rightOrders(boolean worded, boolean onAThing) {
        return false;
    }

    /** A frame of the hands: the mouse moved {@code dx} across and {@code dy} down, the walking keys as held. */
    void move(boolean fwd, boolean back, boolean left, boolean right, float dx, float dy, float tpf) {
        if (frozen) {
            return;
        }
        yaw -= dx * config.sensitivity();
        pitch = clampPitch(pitch - dy * config.sensitivity());
        // The keys ask for a speed; the eye gathers to it and slows from it (pace), and a wall it meets stops it.
        velocity.set(pace(velocity, wish(yaw, fwd, back, left, right, running ? config.run() : config.speed()), tpf));
        var to = step(eye, velocity.x * tpf, velocity.z * tpf, config);
        if (tpf > 0f) {
            velocity.set((to.x - eye.x) / tpf, 0f, (to.z - eye.z) / tpf);
        }
        eye = to;
    }

    /** The window's camera put at the eye, and the cross in the middle of the window. */
    void place(Camera cam) {
        if (cam.getFrustumNear() != NEAR) {
            cam.setFrustumPerspective(cam.getFov(), (float) cam.getWidth() / cam.getHeight(), NEAR, FAR);
        }
        cam.setLocation(eye);
        cam.lookAtDirection(direction(), Vector3f.UNIT_Y);
        if (crosshair != null) {
            crosshair.setLocalTranslation((cam.getWidth() - crosshair.getLineWidth()) / 2f,
                    (cam.getHeight() + crosshair.getLineHeight()) / 2f, 0f);
        }
    }

    private Vector3f direction() {
        float across = FastMath.cos(pitch);
        return new Vector3f(-FastMath.sin(yaw) * across, FastMath.sin(pitch), -FastMath.cos(yaw) * across);
    }
}
