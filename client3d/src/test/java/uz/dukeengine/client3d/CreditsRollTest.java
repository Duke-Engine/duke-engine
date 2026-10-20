package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import uz.dukeengine.game.CreditsRoll;
import uz.dukeengine.game.DukeGame;

/** The credits rolled up a darkened screen, told gone when they have rolled out or been skipped (E49). */
class CreditsRollTest {

    private static CanvasFrame frame() {
        return new CanvasFrame(800, 600, new CanvasText(_ -> null), Map.<String, int[]>of()::get);
    }

    private static java.util.List<CreditsRoll.Line> tenLines() {
        var lines = new ArrayList<CreditsRoll.Line>();
        lines.add(CreditsRoll.Line.heading("Yaratuvchilar"));
        for (int at = 1; at < 10; at++) {
            lines.add(CreditsRoll.Line.name("Ism " + at));
        }
        return lines;
    }

    @Test
    void tenLinesRollOverTwoSecondsAndAreReportedGone() {
        var game = DukeGame.create("credits-test");
        var ended = new AtomicInteger();
        game.creditsRoll(tenLines(), 2f, ended::incrementAndGet);
        var show = new CreditsRollShow(game.creditsShowing());

        int frames = 0;
        boolean drewAName = false;
        while (true) {
            var canvas = frame();
            if (!show.paint(canvas, 1f / 30f)) {
                break;
            }
            frames++;
            drewAName |= canvas.triangles().size() > 2; // the darkened screen, and lines over it
        }
        game.creditsGone(show.roll());

        assertEquals(59, frames, 1, "two seconds of frames");
        assertTrue(drewAName, "its lines drawn over the dark");
        assertNull(game.creditsShowing(), "gone");
        assertEquals(1, ended.get(), "the game told once");
    }

    @Test
    void escOrAClickSkipsTheRest() {
        var game = DukeGame.create("credits-skip");
        var ended = new AtomicInteger();
        game.creditsRoll(tenLines(), 20f, ended::incrementAndGet);
        var show = new CreditsRollShow(game.creditsShowing());
        assertTrue(show.paint(frame(), 1f / 30f));

        show.skip();
        assertFalse(show.paint(frame(), 1f / 30f), "over at the next frame");
        game.creditsGone(show.roll());
        assertEquals(1, ended.get());
    }
}
