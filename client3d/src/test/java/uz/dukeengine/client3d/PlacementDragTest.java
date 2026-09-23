package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;

/**
 * Putting a thing down: the ghost faces its button's way, a press fixes where, and a drag before letting go
 * turns it to face along the drag — the reference game's gesture, and the only way to choose which way a
 * building's door faces.
 */
class PlacementDragTest {

    private static final Coord3D HERE = new Coord3D(100f, 200f, 0f);

    @Test
    void anArmedGhostFacesItsButtonsWay() {
        var drag = new PlacementDrag(45f);
        drag.cursor(400f, 300f, HERE);

        assertEquals(45f, drag.facing());
        assertEquals(HERE, drag.where(), "and follows the cursor until a press");

        var drawn = PlacementDrag.turnedTo(45f);
        var unit = new Quaternion().fromAngles(0f, -45f * FastMath.DEG_TO_RAD, 0f);
        assertEquals(unit, drawn, "turned the way a unit facing 45 is drawn");
    }

    @Test
    void aPressWithNoDragKeepsTheButtonsFacing() {
        var drag = new PlacementDrag(45f);
        drag.press(400f, 300f, HERE);
        drag.cursor(402f, 301f, new Coord3D(101f, 200f, 0f)); // a trembling hand is not a drag

        var placed = drag.release();
        assertEquals(HERE, placed.place());
        assertEquals(45f, placed.facing());
    }

    /**
     * Dragged east from where the press went down, it faces east — 0, along +x — and stands where the press
     * went down, not where the drag ended.
     */
    @Test
    void aPressDraggedEastFacesEastAndStaysWhereItWentDown() {
        var drag = new PlacementDrag(45f);
        drag.press(400f, 300f, HERE);
        drag.cursor(440f, 300f, new Coord3D(140f, 200f, 0f));

        assertTrue(drag.dragging());
        assertEquals(HERE, drag.where(), "the ghost stays where the press went down while it turns");
        var placed = drag.release();
        assertEquals(HERE, placed.place());
        assertEquals(0f, placed.facing(), 1e-4f, "east");
        assertFalse(drag.pressed());
    }

    /** Dragged toward +y it faces 90, as every orientation in the simulation turns. */
    @Test
    void aDragTowardTheSimulationsYFacesNinety() {
        var drag = new PlacementDrag(0f);
        drag.press(400f, 300f, HERE);
        drag.cursor(400f, 250f, new Coord3D(100f, 260f, 0f));

        assertEquals(90f, drag.release().facing(), 1e-4f);
    }

    /** A press off the map places nothing, and a release without a press sends nothing. */
    @Test
    void nothingIsPutDownWithoutAPressOnTheGround() {
        var drag = new PlacementDrag(0f);
        drag.press(400f, 300f, null);

        assertFalse(drag.pressed());
        assertNull(drag.release());
    }
}
