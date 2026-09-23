package uz.dukeengine.client3d;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import uz.dukeengine.core.math.Coord3D;

/**
 * Putting a thing down with an armed button: where the press went down, and which way the thing faces.
 *
 * <p>Until the press it follows the cursor, facing the way its button says. A press fixes where it stands;
 * dragging before letting go turns it to face along the drag — from the spot the press went down on to
 * wherever the cursor now is — and letting go puts it down there, facing that way. A press with no drag
 * keeps the button's facing. That is the reference game's own gesture, and it is the only way a player
 * can choose which way a building's door faces.
 *
 * <p>A drag of a few pixels is not a drag: a hand is never quite still, and a building that swung round
 * because the mouse trembled on the click would be a building put down facing somewhere nobody chose.
 *
 * <p>Pure, and so checked without a window: the client feeds it the cursor and reads back where the ghost
 * stands and which way it turns.
 */
final class PlacementDrag {

    /** How far the cursor must travel, in pixels, before a press is a drag rather than an unsteady hand. */
    static final float DRAG_PIXELS = 6f;

    /** Where, and which way, a thing was put down. */
    record Placed(Coord3D place, float facing) {
    }

    private Coord3D hover;
    private Coord3D pressedAt;
    private float pressX;
    private float pressY;
    private boolean dragging;
    private float facing;

    PlacementDrag(float facing) {
        this.facing = facing;
    }

    /** The cursor over the ground — {@code ground} in the simulation's coordinates, or null off the map. */
    void cursor(float screenX, float screenY, Coord3D ground) {
        if (pressedAt == null) {
            hover = ground;
            return;
        }
        float dx = screenX - pressX;
        float dy = screenY - pressY;
        if (!dragging && dx * dx + dy * dy >= DRAG_PIXELS * DRAG_PIXELS) {
            dragging = true;
        }
        if (dragging && ground != null) {
            float across = ground.x() - pressedAt.x();
            float along = ground.y() - pressedAt.y();
            if (across * across + along * along > 0.0001f) {
                // The simulation's degrees: 0 along +x, turning toward +y, as every orientation in it is.
                facing = (float) Math.toDegrees(Math.atan2(along, across));
            }
        }
    }

    /** The press went down, on the ground at {@code ground}, or off it with null. */
    void press(float screenX, float screenY, Coord3D ground) {
        if (ground == null) {
            return;
        }
        pressedAt = ground;
        pressX = screenX;
        pressY = screenY;
        dragging = false;
    }

    /**
     * Let go: where the press went down and which way it faces now — or null if no press landed. The facing
     * is kept for a next try, if this one is not sent; the press is not.
     */
    Placed release() {
        if (pressedAt == null) {
            return null;
        }
        var placed = new Placed(pressedAt, facing);
        pressedAt = null;
        dragging = false;
        return placed;
    }

    /** Where the ghost stands: where the press went down, or under the cursor before one. */
    Coord3D where() {
        return pressedAt != null ? pressedAt : hover;
    }

    float facing() {
        return facing;
    }

    boolean pressed() {
        return pressedAt != null;
    }

    boolean dragging() {
        return dragging;
    }

    /**
     * The turn a thing facing {@code degrees} in the simulation is drawn with — the same the client gives a
     * unit, whose orientation is the same angle in radians, turned the other way about the up axis because
     * the scene's second ground axis is the simulation's y.
     */
    static Quaternion turnedTo(float degrees) {
        return new Quaternion().fromAngles(0f, -degrees * FastMath.DEG_TO_RAD, 0f);
    }
}
