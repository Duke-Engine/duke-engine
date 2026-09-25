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
}
