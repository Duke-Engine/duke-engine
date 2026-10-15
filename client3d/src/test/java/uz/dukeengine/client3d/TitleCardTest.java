package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.game.DukeGame;

/** A title card: shown, drawn over a darkened screen with its fade, and reported gone after its time. */
class TitleCardTest {

    private static CanvasFrame frame() {
        return new CanvasFrame(800, 600, new CanvasText(path -> null), Map.<String, int[]>of()::get);
    }

    @Test
    void aCardIsDrawnForItsSecondsAndTheClientReportsItGone() {
        var game = DukeGame.create("card-test");
        game.titleCard("1-qism tugadi", "Davomi bor", 0.5f, 1f, 0.5f);
        var wanted = game.titleCardShowing();
        assertNotNull(wanted);
        assertTrue(wanted.takesInput(), "the player's keys go to nothing while it shows");

        var show = new TitleCardShow(wanted);
        int frames = 0;
        boolean up = true;
        while (up) {
            var canvas = frame();
            up = show.paint(canvas, 1f / 30f);
            if (up) {
                frames++;
                assertFalse(canvas.triangles().isEmpty(), "drawn while it is up");
            }
        }
        game.titleCardGone(show.card());

        assertEquals(59, frames, 1, "two seconds of frames");
        assertNull(game.titleCardShowing(), "gone");
    }

    @Test
    void itFadesInHoldsAndFadesOut() {
        var card = new uz.dukeengine.game.TitleCard("A", "", 1f, 2f, 1f, false);
        assertEquals(0.5f, card.shownAt(0.5f), 1e-6f);
        assertEquals(1f, card.shownAt(2f), 1e-6f);
        assertEquals(0.5f, card.shownAt(3.5f), 1e-6f);
        assertFalse(card.takesInput());
    }
}
