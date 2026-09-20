package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.SelectionBox.Candidate;

/** What a box drawn across the screen picks up, and what it leaves alone. */
class SelectionBoxTest {

    private static Candidate own(int id, float x, float y) {
        return new Candidate(id, x, y, true, true);
    }

    private static Candidate enemy(int id, float x, float y) {
        return new Candidate(id, x, y, false, true);
    }

    @Test
    void aBoxTakesTheUnitsInsideIt() {
        var found = SelectionBox.inside(100f, 100f, 300f, 300f, List.of(
                own(1, 150f, 150f),
                own(2, 290f, 110f),
                own(3, 400f, 200f),   // right of the box
                own(4, 200f, 350f))); // above it

        assertEquals(List.of(1, 2), found);
    }

    /**
     * Dragging over the dungeon must never hand the player a skeleton. Selection is
     * how orders are addressed, and an order to something that is not his is not an
     * order at all — better it never enters the selection.
     */
    @Test
    void aBoxNeverTakesTheEnemy() {
        var found = SelectionBox.inside(0f, 0f, 500f, 500f, List.of(
                enemy(1, 100f, 100f),
                own(2, 200f, 200f),
                enemy(3, 300f, 300f)));

        assertEquals(List.of(2), found, "only his own should come back");
    }

    @Test
    void unselectableThingsAreLeftOut() {
        var found = SelectionBox.inside(0f, 0f, 500f, 500f, List.of(
                new Candidate(1, 100f, 100f, true, false),
                own(2, 200f, 200f)));

        assertEquals(List.of(2), found);
    }

    /** Boxes are dragged in every direction; the corners sort themselves out. */
    @Test
    void aBoxDraggedBackwardsIsTheSameBox() {
        var units = List.of(own(1, 150f, 150f), own(2, 400f, 400f));

        var forwards = SelectionBox.inside(100f, 100f, 300f, 300f, units);
        var backwards = SelectionBox.inside(300f, 300f, 100f, 100f, units);

        assertEquals(forwards, backwards);
        assertEquals(List.of(1), forwards);
    }

    @Test
    void anEmptyBoxSelectsNothing() {
        var found = SelectionBox.inside(10f, 10f, 60f, 60f, List.of(own(1, 400f, 400f)));

        assertTrue(found.isEmpty(), "dragging over empty floor should clear the selection");
    }

    /**
     * The distinction that keeps ordinary clicking alive: a hand never drags
     * exactly zero pixels, so without a threshold every click would become a tiny
     * empty box and quietly select nothing.
     */
    @Test
    void aSmallWobbleIsAClickRatherThanADrag() {
        assertFalse(SelectionBox.isDrag(200f, 200f, 200f, 200f), "a still hand is a click");
        assertFalse(SelectionBox.isDrag(200f, 200f, 202f, 201f), "and so is a shaky one");

        assertTrue(SelectionBox.isDrag(200f, 200f, 260f, 205f), "but a real sweep is a drag");
        assertTrue(SelectionBox.isDrag(200f, 200f, 198f, 260f), "in either axis");
    }
}
