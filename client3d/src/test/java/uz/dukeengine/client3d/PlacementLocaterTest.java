package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.jme3.scene.Node;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;

/**
 * The anchor and the arrow of a placement drag ({@code W3DInGameUI::drawPlaceAngle}, {@code Locater01} and {@code
 * Locater02}): the anchor where the press went down, the arrow there once the cursor is dragged away, turned along it.
 */
class PlacementLocaterTest {

    private static final Coord3D PRESSED = new Coord3D(100f, 200f, 0f);

    private static PlacementLocater locater(Node parent) {
        return new PlacementLocater(parent, new Node("anchor"), new Node("arrow"));
    }

    @Test
    void nothingIsLaidBeforeAPressAndTheAnchorStandsWhereItWentDown() {
        var parent = new Node("markers");
        var locater = locater(parent);
        var drag = new PlacementDrag(45f);
        drag.cursor(400f, 300f, PRESSED);
        locater.show(drag, (_, _) -> 3f);
        assertNull(locater.showing(), "the ghost follows the cursor; the marks wait for a press");

        drag.press(400f, 300f, PRESSED);
        drag.cursor(402f, 301f, new Coord3D(101f, 200f, 0f)); // a trembling hand is not a drag
        locater.show(drag, (_, _) -> 3f);

        assertEquals("anchor", locater.showing());
        var at = parent.getChild("placement locater").getLocalTranslation();
        assertEquals(100f, at.x, 1e-4f);
        assertEquals(3f, at.y, 1e-4f, "on the ground there");
        assertEquals(200f, at.z, 1e-4f, "the scene's second ground axis is the simulation's y");
    }

    @Test
    void draggedAwayTheArrowStandsAtTheSamePointTurnedAlongTheDrag() {
        var parent = new Node("markers");
        var locater = locater(parent);
        var drag = new PlacementDrag(45f);
        drag.press(400f, 300f, PRESSED);
        drag.cursor(400f, 250f, new Coord3D(100f, 260f, 0f)); // toward +y: facing 90
        locater.show(drag, (_, _) -> 0f);

        assertEquals("arrow", locater.showing());
        var node = parent.getChild("placement locater");
        assertEquals(100f, node.getLocalTranslation().x, 1e-4f, "still where the press went down");
        assertEquals(200f, node.getLocalTranslation().z, 1e-4f);
        assertEquals(PlacementDrag.turnedTo(90f), node.getLocalRotation(), "turned as the thing placed is");
    }

    @Test
    void letGoItIsTakenOffTheGroundAndAModelThatWillNotLoadIsSimplyNotDrawn() {
        var parent = new Node("markers");
        var locater = new PlacementLocater(parent, new Node("anchor"), null);
        var drag = new PlacementDrag(0f);
        drag.press(400f, 300f, PRESSED);
        drag.cursor(440f, 300f, new Coord3D(140f, 200f, 0f));
        locater.show(drag, (_, _) -> 0f);
        assertNull(locater.showing(), "dragged, and the arrow was not named");

        drag.release();
        locater.show(drag, (_, _) -> 0f);
        assertNull(locater.showing());

        locater.remove();
        assertNull(parent.getChild("placement locater"));
    }

    @Test
    void theGameNamesNoneByDefault() {
        assertNull(Visuals.create().getLocaters());
        var named = new Visuals.LocaterLook("a.glb", "b.glb");
        assertEquals(named, Visuals.create().locaters(named).getLocaters());
    }
}
