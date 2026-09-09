package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Where things land on the minimap, and where the camera's outline goes. */
class MinimapProjectionTest {

    /** A 500×360 world (50×36 cells of 10) drawn into a 190px minimap. */
    private static MinimapProjection projection() {
        return new MinimapProjection(500f, 360f, 190f);
    }

    @Test
    void theWorldFitsInsideTheMinimap() {
        var minimap = projection();

        assertEquals(190f, minimap.widthPixels(), 0.001f, "the long side sets the scale");
        assertEquals(190f * 360f / 500f, minimap.heightPixels(), 0.001f,
                "and the short side keeps the world's proportions");
    }

    /**
     * The map's y grows downward and the screen's grows upward, so the top-left of
     * the map has to come out at the top-left of the minimap — which is a high
     * pixel y, not a low one.
     */
    @Test
    void theVerticalAxisIsFlipped() {
        var minimap = projection();

        var topLeft = minimap.toMinimap(0f, 0f);
        assertEquals(0f, topLeft.x(), 0.001f);
        assertEquals(minimap.heightPixels(), topLeft.y(), 0.001f,
                "the top of the map belongs at the top of the minimap");

        var bottomRight = minimap.toMinimap(500f, 360f);
        assertEquals(minimap.widthPixels(), bottomRight.x(), 0.001f);
        assertEquals(0f, bottomRight.y(), 0.001f);
    }

    @Test
    void aPointSurvivesTheRoundTrip() {
        var minimap = projection();
        var point = minimap.toMinimap(215f, 140f);

        assertEquals(215f, minimap.toWorldX(point.x()), 0.01f);
        assertEquals(140f, minimap.toWorldY(point.y()), 0.01f);
    }

    @Test
    void onlyPointsOverTheMinimapCount() {
        var minimap = projection();

        assertTrue(minimap.contains(10f, 10f));
        assertFalse(minimap.contains(-1f, 10f), "left of the minimap is not on it");
        assertFalse(minimap.contains(10f, minimap.heightPixels() + 1f), "nor is above it");
    }

    /** The camera's footprint tracks the camera: pan it, and the outline moves with it. */
    @Test
    void theViewportOutlineFollowsTheCamera() {
        var minimap = projection();

        var looking = minimap.viewportOutline(
                new float[] {100f, 200f, 200f, 100f},
                new float[] {100f, 100f, 200f, 200f});
        var panned = minimap.viewportOutline(
                new float[] {200f, 300f, 300f, 200f},
                new float[] {100f, 100f, 200f, 200f});

        assertEquals(4, looking.length);
        for (int corner = 0; corner < 4; corner++) {
            assertEquals(100f * minimap.scale(), panned[corner].x() - looking[corner].x(), 0.01f,
                    "panning east should slide the whole outline east by the same amount");
            assertEquals(looking[corner].y(), panned[corner].y(), 0.01f,
                    "and not move it vertically at all");
        }
    }

    /** Zooming out covers more ground, and the outline grows to say so. */
    @Test
    void theViewportOutlineGrowsWhenTheCameraPullsBack() {
        var minimap = projection();

        var close = minimap.viewportOutline(
                new float[] {200f, 260f, 260f, 200f},
                new float[] {150f, 150f, 210f, 210f});
        var far = minimap.viewportOutline(
                new float[] {170f, 290f, 290f, 170f},
                new float[] {120f, 120f, 240f, 240f});

        assertTrue(far[1].x() - far[0].x() > close[1].x() - close[0].x(),
                "a wider view should draw a wider outline");
    }

    /**
     * A camera at the edge of the world sees past it, but the outline still has to
     * stay in its box rather than draw over the rest of the screen.
     */
    @Test
    void anOutlineRunningOffTheMapIsClampedToTheMinimap() {
        var minimap = projection();

        var outline = minimap.viewportOutline(
                new float[] {-400f, 900f, 900f, -400f},
                new float[] {-300f, -300f, 700f, 700f});

        for (var corner : outline) {
            assertTrue(minimap.contains(corner.x(), corner.y()),
                    "a clamped corner should sit on the minimap, got " + corner);
        }
        assertEquals(0f, outline[0].x(), 0.001f, "off the west edge flattens against it");
        assertEquals(minimap.widthPixels(), outline[1].x(), 0.001f);
    }
}
