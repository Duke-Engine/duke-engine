package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.SelectionBox.Candidate;

/** What a box drawn across the screen picks up, and what it leaves alone — the reference's box. */
class SelectionBoxTest {

    private static Candidate own(int id, float x, float y) {
        return new Candidate(id, x, y, true, true);
    }

    private static Candidate ownBuilding(int id, float x, float y) {
        return new Candidate(id, x, y, true, true, true);
    }

    private static Candidate enemy(int id, float x, float y) {
        return new Candidate(id, x, y, false, true);
    }

    /** The selection a box over all of {@code things} leaves, from {@code selected}. */
    private static List<Integer> box(List<Candidate> things, List<Integer> selected, boolean hisUnits, boolean add) {
        return SelectionBox.after(SelectionBox.inside(0f, 0f, 500f, 500f, things), selected, hisUnits, add);
    }

    @Test
    void aBoxTakesWhatMayBeSelectedInsideIt() {
        var found = SelectionBox.inside(100f, 100f, 300f, 300f, List.of(
                own(1, 150f, 150f),
                own(2, 290f, 110f),
                own(3, 400f, 200f),   // right of the box
                own(4, 200f, 350f),   // above it
                new Candidate(5, 200f, 200f, true, false))); // not to be selected

        assertEquals(List.of(1, 2), found.stream().map(Candidate::id).toList());
    }

    /** Boxes are dragged in every direction; the corners sort themselves out. */
    @Test
    void aBoxDraggedBackwardsIsTheSameBox() {
        var units = List.of(own(1, 150f, 150f), own(2, 400f, 400f));

        assertEquals(SelectionBox.inside(100f, 100f, 300f, 300f, units),
                SelectionBox.inside(300f, 300f, 100f, 100f, units));
    }

    @Test
    void overTwoTanksAndTheirFactoryItTakesTheTanks() {
        assertEquals(List.of(1, 2), box(List.of(own(1, 100f, 100f), own(2, 150f, 100f), ownBuilding(3, 200f, 200f)),
                List.of(), true, false));
    }

    @Test
    void overTheFactoryAloneItTakesTheFactory() {
        assertEquals(List.of(3), box(List.of(ownBuilding(3, 200f, 200f)), List.of(1), true, false));
    }

    @Test
    void overTwoFactoriesItTakesNothingAndTheOldSelectionIsGone() {
        assertEquals(List.of(), box(List.of(ownBuilding(3, 200f, 200f), ownBuilding(4, 300f, 200f)), List.of(1, 2),
                true, false));
    }

    @Test
    void overEmptyGroundItKeepsTheSelection() {
        assertEquals(List.of(1, 2), box(List.of(own(9, 900f, 900f)), List.of(1, 2), true, false));
    }

    @Test
    void overOneEnemyTankAloneItTakesThatTankAndOverTwoItChangesNothing() {
        assertEquals(List.of(7), box(List.of(enemy(7, 100f, 100f)), List.of(1), true, false));
        assertEquals(List.of(1), box(List.of(enemy(7, 100f, 100f), enemy(8, 200f, 100f)), List.of(1), true, false));
    }

    /**
     * Dragging over the dungeon must never hand the player a skeleton while his own are in the box too: selection is
     * how orders are addressed.
     */
    @Test
    void withHisOwnInItABoxNeverTakesTheEnemy() {
        assertEquals(List.of(2), box(List.of(enemy(1, 100f, 100f), own(2, 200f, 200f), enemy(3, 300f, 300f)),
                List.of(), true, false));
    }

    @Test
    void withTheAddKeyItAddsToHisUnitsAndReplacesASelectionHoldingABuildingOrAnotherSidesThing() {
        var tanks = List.of(own(4, 100f, 100f), own(5, 150f, 100f));

        assertEquals(List.of(1, 2, 4, 5), box(tanks, List.of(1, 2), true, true), "added to his units");
        assertEquals(List.of(4, 5), box(tanks, List.of(3), false, true), "a building selected is replaced");
    }

    /**
     * The distinction that keeps ordinary clicking alive: a hand never drags exactly zero pixels, so without a
     * threshold every click would become a tiny empty box.
     */
    @Test
    void aPressMovedLessThanTheDragDistanceIsAClick() {
        assertFalse(SelectionBox.isDrag(200f, 200f, 202f, 201f, 5f), "a shaky hand is a click");
        assertTrue(SelectionBox.isDrag(200f, 200f, 198f, 260f, 5f), "a real sweep, in either axis, is a drag");

        assertFalse(SelectionBox.isDrag(200f, 200f, 220f, 200f, 25f), "with the drag distance at 25, 20 is a click");
        assertTrue(SelectionBox.isDrag(200f, 200f, 225f, 200f, 25f), "and 25 a box");
    }
}
