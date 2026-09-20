package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Whether a click lands on the slot it looks like it landed on.
 *
 * <p>The bar is drawn in the pixels the design was made at and then scaled to the
 * window, so a cursor has to make the trip the other way before it can be
 * compared with anything. That conversion is the part that can be wrong without
 * looking wrong — the slot is drawn in the right place and simply answers to
 * clicks somewhere else — and it is the part a player notices immediately,
 * because casting by mouse stops working.
 *
 * <p>The drawing itself needs a window and cannot be tested here; this is the
 * arithmetic underneath it.
 */
class PanelHitTest {

    @Test
    void aPointInsideTheSquareHits() {
        assertTrue(HeroPanel.hits(100f, 100f, 90f, 90f, 62f));
        assertTrue(HeroPanel.hits(90f, 90f, 90f, 90f, 62f), "the near corner counts");
        assertTrue(HeroPanel.hits(152f, 152f, 90f, 90f, 62f), "so does the far one");
    }

    @Test
    void aPointOutsideDoesNot() {
        assertFalse(HeroPanel.hits(89f, 100f, 90f, 90f, 62f));
        assertFalse(HeroPanel.hits(100f, 89f, 90f, 90f, 62f));
        assertFalse(HeroPanel.hits(153f, 100f, 90f, 90f, 62f));
        assertFalse(HeroPanel.hits(100f, 153f, 90f, 90f, 62f));
    }

    /**
     * A slot the width of a finger, drawn on a screen half the design's size,
     * still answers to a click in the middle of it.
     *
     * <p>Written the way the panel does it: the cursor is divided by the scale and
     * then compared with the design's own coordinates.
     */
    @Test
    void theScaleIsTakenOffTheCursorRatherThanAddedToTheSlot() {
        float scale = 0.5f;
        float slotLeft = 400f;
        float slotBottom = 40f;
        float size = 62f;
        // Drawn at 200..231 across and 20..51 up, so a click at 215,35 is on it.
        assertTrue(HeroPanel.hits(215f / scale, 35f / scale, slotLeft, slotBottom, size));
        assertFalse(HeroPanel.hits(190f / scale, 35f / scale, slotLeft, slotBottom, size),
                "a click to the left of the drawn slot must miss it");
    }
}
