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

    @Test
    void withTheAddKeyAClickOnOneOfHisSelectedTakesItOutSilentlyAndOnAFourthAddsItWithItsVoice() {
        var three = List.of(1, 2, 3);
        var out = SelectionBox.click(new SelectionBox.Candidate(2, 0f, 0f, true, true), three, true);
        assertEquals(List.of(1, 3), out.selection(), "two left");
        assertEquals(-1, out.tookIn(), "with no voice");

        var in = SelectionBox.click(new SelectionBox.Candidate(4, 0f, 0f, true, true), three, true);
        assertEquals(List.of(1, 2, 3, 4), in.selection());
        assertEquals(4, in.tookIn(), "with its voice");

        var plain = SelectionBox.click(new SelectionBox.Candidate(2, 0f, 0f, true, true), three, false);
        assertEquals(List.of(2), plain.selection(), "without the key, it alone, as ever");

        var box = SelectionBox.after(List.of(new SelectionBox.Candidate(1, 0f, 0f, true, true),
                new SelectionBox.Candidate(2, 0f, 0f, true, true), new SelectionBox.Candidate(3, 0f, 0f, true, true)),
                three, true, true);
        assertEquals(three, box, "a box with the key over the three adds, as now");
    }

    @Test
    void theBoxNamedTwoPixelsIn9933FF33IsOutlinedSo() {
        var corners = DragBox.outline(100f, 100f, 200f, 150f, 2f);
        float left = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        float bottom = Float.MAX_VALUE;
        float top = -Float.MAX_VALUE;
        for (int at = 0; at < corners.length; at += 3) {
            left = Math.min(left, corners[at]);
            right = Math.max(right, corners[at]);
            bottom = Math.min(bottom, corners[at + 1]);
            top = Math.max(top, corners[at + 1]);
        }
        assertEquals(99f, left);
        assertEquals(201f, right);
        assertEquals(99f, bottom, "two pixels wide about its edges");
        assertEquals(151f, top);

        var material = new com.jme3.material.Material(new com.jme3.asset.DesktopAssetManager(true),
                "Common/MatDefs/Misc/Unshaded.j3md");
        new DragBox(new Visuals.DragBoxLook(2f, 0x9933FF33), material);
        var colour = (com.jme3.math.ColorRGBA) material.getParamValue("Color");
        assertEquals(0.2f, colour.r, 1e-6f);
        assertEquals(1f, colour.g, 1e-6f);
        assertEquals(0.2f, colour.b, 1e-6f);
        assertEquals(0.6f, colour.a, 1e-6f, "at 0.6 alpha");
    }

    @Test
    void theRingUnderSelectedThingsIsDrawnUnlessTheGameSwitchesItOff() {
        assertTrue(Visuals.create().drawsSelectionRings(), "as now");
        assertFalse(Visuals.create().selectionRings(false).drawsSelectionRings());
    }
}
