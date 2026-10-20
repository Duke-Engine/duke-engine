package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A game of one hero: no drag box, and a creature's live portrait drawn in a frame of the game's own canvas. */
class SingleHeroTest {

    @Test
    void aGameWhoseDragSelectsNothingHasNoBoxHoweverFarThePressMoves() {
        var visuals = Visuals.create();
        assertTrue(Orders.isBox(visuals, 200f, 200f, 400f, 400f), "a sweep is a box, as in any RTS");

        visuals.dragSelects(false);
        assertFalse(Orders.isBox(visuals, 200f, 200f, 400f, 400f), "a click where the button is let go");
        assertFalse(Orders.isBox(visuals, 0f, 0f, 4000f, 4000f));
    }

    @Test
    void aPortraitDrawnOnTheCanvasIsAskedForAndLaidAsALivePicture() {
        var frame = new CanvasFrame(100, 100, new CanvasText(_ -> null), Map.<String, int[]>of()::get);

        frame.drawPortrait(7, 10f, 10f, 50f, 60f);
        frame.drawPortrait(4, 0f, 0f, 5f, 5f);
        frame.drawPortrait(7, 60f, 10f, 90f, 60f);

        assertEquals(List.of(7, 4), List.copyOf(frame.portraitsAsked()), "each once, in the order asked");
        assertEquals(6, frame.triangles().size(), "a quad each");
        assertEquals(new CanvasFrame.Live(7), frame.triangles().getFirst().source());
    }
}
