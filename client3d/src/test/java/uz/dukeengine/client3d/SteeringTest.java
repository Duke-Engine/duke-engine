package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The player's mouse on the camera, worked out with no window. */
class SteeringTest {

    @Test
    void aMiddleDragTurnsTheViewByItsAnglePerPixelAndIsNoClick() {
        var steering = new Steering();
        steering.middleDown(400f, 300f, 10f);

        assertEquals(0.5f, steering.turn(450f, 0.01f), 1e-6f, "fifty pixels at the reference's 0.01");
        assertEquals(-0.2f, steering.turn(430f, 0.01f), 1e-6f, "and back twenty from there");

        assertFalse(steering.middleUp(430f, 300f, 10.5f), "a drag, not a click");
        assertEquals(0f, steering.turn(500f, 0.01f), "let go, nothing turns");
    }

    @Test
    void aMiddleClickHardlyMovesAndIsSoonLetGo() {
        var steering = new Steering();
        steering.middleDown(400f, 300f, 10f);
        assertTrue(steering.middleUp(403f, 305f, 10.1f), "five pixels, a tenth of a second: a click");

        steering.middleDown(400f, 300f, 10f);
        assertFalse(steering.middleUp(406f, 300f, 10.1f), "six pixels is a drag");

        steering.middleDown(400f, 300f, 10f);
        assertFalse(steering.middleUp(400f, 300f, 10.2f), "held past five frames at thirty a second is not a click");

        assertFalse(steering.middleUp(400f, 300f, 10.2f), "and nothing let go that never went down");
    }

    // ---- the keys and the window's edges ----

    private static final EdgeScroll THREE = new EdgeScroll(3f, 100f);

    /** The pointer at {@code (x, y)} of an 800 by 600 window, counted from its top left, no key held. */
    private static Steering.Hands pointer(float x, float y, boolean inWindow) {
        return new Steering.Hands(false, false, false, false, x, y, inWindow, 800, 600);
    }

    private static Steering.Hands keys(boolean left, boolean right, boolean up, boolean down) {
        return new Steering.Hands(left, right, up, down, 400f, 300f, true, 800, 600);
    }

    @Test
    void theWindowsBottomEdgeScrollsUnderTheGamesBarAndItsEdgeAboveTheBarDoesNot() {
        var steering = new Steering();

        var aboveTheBar = steering.scroll(pointer(400f, 478f, true), 1f / 30f, 699f, 532f, THREE);
        var underIt = steering.scroll(pointer(400f, 599f, true), 1f / 30f, 699f, 532f, THREE);

        assertEquals(0f, aboveTheBar.y, 0f, "478 of 600, where the world's part ends: nothing");
        assertEquals(532f / 30f, underIt.y, 1e-4f, "599, the window's own edge, over the bar: down");
        assertEquals(0f, underIt.x, 0f);
    }

    @Test
    void aPointerThatLeftTheWindowByItsRightEdgeScrollsNothing() {
        var steering = new Steering();

        assertEquals(0f, steering.scroll(pointer(799f, 300f, false), 1f / 30f, 699f, 532f, THREE).x, 0f,
                "last seen at the right edge, but gone");
        assertEquals(699f / 30f, steering.scroll(pointer(799f, 300f, true), 1f / 30f, 699f, 532f, THREE).x, 1e-4f,
                "while it is there, right");
    }

    @Test
    void aPanKeyHeldForASecondAt699MovesTheView699WhateverTheZoom() {
        var camera = new CameraFocus();
        camera.frame(new CameraFrame(37.5f, 50f, 197f, 509f, 509f, 16.4f, 0.01f, 699f, 532f));
        var steering = new Steering();
        for (float distance : new float[] {509f, 197f}) {
            camera.restore(new CameraFocus.View(0f, 0f, 0f, distance));
            float moved = 0f;
            for (int frame = 0; frame < 30; frame++) {
                moved += steering.scroll(keys(false, true, false, false), 1f / 30f, camera.panAcross(),
                        camera.panAlong(), EdgeScroll.NONE).x;
            }
            assertEquals(699f, moved, 1e-2f, "at " + distance);
        }
    }

    @Test
    void heldPanKeysShowTheScrollPointerTheWayTheViewGoes() {
        var steering = new Steering();
        var up = steering.scroll(keys(false, false, true, false), 1f / 30f, 699f, 532f, EdgeScroll.NONE);
        var right = steering.scroll(keys(false, true, false, false), 1f / 30f, 699f, 532f, EdgeScroll.NONE);

        assertEquals("Scroll-N", Cursors.situationFor(scrolling(Cursors.scrollDirection(up.x, up.y))));
        assertEquals("Scroll-E", Cursors.situationFor(scrolling(Cursors.scrollDirection(right.x, right.y))));
        assertNull(Cursors.scrollDirection(0f, 0f), "and none still");
    }

    private static Cursors.Over scrolling(String way) {
        return new Cursors.Over(true, null, true, false, false, false, true, true, null, way);
    }

    // ---- the right button held ----

    /** 14 world units a second across for each pixel, 10.6 along, the anchor half the window behind at most. */
    private static final RightDrag REFERENCE = new RightDrag(14f, 10.6f, 0.5f, 25f, 250f, 25f);

    private static float across(Steering steering, float x, float y, int frames) {
        float moved = 0f;
        for (int frame = 0; frame < frames; frame++) {
            moved += steering.scroll(pointer(x, y, true), 1f / 30f, 699f, 532f, EdgeScroll.NONE).x;
        }
        return moved;
    }

    @Test
    void theRightButtonDownAndThePointerHeldAHundredRightForASecondScrolls1400AndIsNoClick() {
        var steering = new Steering();
        steering.rightDown(400f, 300f, 20f, 0f, 0f, REFERENCE);

        assertEquals(1400f, across(steering, 500f, 300f, 30), 0.1f, "a still pointer away from the anchor keeps going");

        assertFalse(steering.rightUp(500f, 300f, 21f, 1400f, 0f), "a drag: the selection is kept");
        assertEquals(0f, across(steering, 500f, 300f, 1), 0f, "let go, the scrolling stops");
    }

    @Test
    void downAndUpWithinTenPixelsAndATenthOfASecondIsAClickAndTheViewHasNotMoved() {
        var steering = new Steering();
        steering.rightDown(400f, 300f, 20f, 50f, 60f, REFERENCE);
        assertEquals(0f, across(steering, 400f, 300f, 2), 0f, "held where it went down: no scrolling");

        assertTrue(steering.rightUp(408f, 305f, 20.1f, 50f, 60f), "a click: the selection is let go");

        steering.rightDown(400f, 300f, 20f, 50f, 60f, REFERENCE);
        assertFalse(steering.rightUp(400f, 300f, 20.3f, 50f, 60f), "too slow for a click");
        steering.rightDown(400f, 300f, 20f, 50f, 60f, REFERENCE);
        assertFalse(steering.rightUp(426f, 300f, 20.1f, 50f, 60f), "too far");
        steering.rightDown(400f, 300f, 20f, 50f, 60f, REFERENCE);
        assertFalse(steering.rightUp(400f, 300f, 20.1f, 80f, 60f), "the view moved 30 meanwhile");
    }

    @Test
    void theAnchorNeverLagsMoreThanHalfTheWindowBehindThePointer() {
        var steering = new Steering();
        steering.rightDown(400f, 300f, 20f, 0f, 0f, REFERENCE);

        assertEquals(14f * 400f / 30f, across(steering, 1300f, 300f, 1), 1e-3f,
                "900 away, the anchor dragged to 400 behind: half of 800");
        assertEquals(-14f * 400f / 30f, across(steering, 0f, 300f, 1), 1e-3f, "and the other way");
    }

    @Test
    void aReleaseTheGameTookStillEndsTheDrag() {
        var steering = new Steering();
        steering.rightDown(400f, 300f, 20f, 0f, 0f, REFERENCE);

        steering.stillHeld(false, true);

        assertEquals(0f, across(steering, 500f, 300f, 1), 0f, "no view left scrolling on its own");
    }
}
