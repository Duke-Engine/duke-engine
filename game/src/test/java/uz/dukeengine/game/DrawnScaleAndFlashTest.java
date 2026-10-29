package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.view.UnitView;

/** A thing drawn at the size the game steps it to, and flashed in a colour that eases out (R23.5). */
class DrawnScaleAndFlashTest {

    private static UnitView viewOf(DukeGame game) {
        return game.getSnapshot().units().getFirst();
    }

    private static DukeGame gameWithOne() {
        var game = DukeGame.create("drawn").loadUnits(uz.dukeengine.rts.RtsFlavour.STARTER_UNITS).map(40, 40);
        var me = game.addPlayer("Me", Colour.BLUE);
        game.localPlayer(me).spawn("Rifleman", me, 100f, 100f);
        game.runHeadless(1);
        return game;
    }

    @Test
    void aScaleSteppedFromNothingToWholeOverThirtyFramesIsDrawnAtThatScaleEachFrame() {
        var game = gameWithOne();
        var thing = game.getLogic().getObjects().getFirst();
        assertEquals(1f, viewOf(game).scale(), "its look's size until told");

        for (int frame = 0; frame <= 30; frame++) {
            thing.setDrawnScale(frame / 30f);
            game.runHeadless(1);
            assertEquals(frame / 30f, viewOf(game).scale(), 1e-6f);
        }
    }

    @Test
    void aFadeInOfTenFramesGoesFromNothingToWholeByTheTenth() {
        var game = gameWithOne();
        var thing = game.getLogic().getObjects().getFirst();
        assertEquals(1f, viewOf(game).opacity(), "whole until told");

        thing.fadeIn(10);
        assertEquals(0f, thing.getDrawnOpacity(), "nothing the frame it begins");
        for (int frame = 1; frame <= 10; frame++) {
            game.runHeadless(1);
            assertEquals(frame / 10f, viewOf(game).opacity(), 1e-6f);
        }
        game.runHeadless(5);
        assertEquals(1f, viewOf(game).opacity(), "and whole after");
    }

    @Test
    void aRedFlashOfTwelveFramesTintsRedEasesOutAndIsClearByTheTwelfth() {
        var game = gameWithOne();
        var thing = game.getLogic().getObjects().getFirst();
        assertEquals(0, viewOf(game).flash(), "no flash until told");

        thing.flash(0xFFFF0000, 12);
        int last = 256;
        for (int frame = 1; frame <= 12; frame++) {
            game.runHeadless(1);
            int flash = viewOf(game).flash();
            int alpha = flash >>> 24;
            assertTrue(alpha <= last, "easing out: " + alpha + " after " + last);
            last = alpha;
            if (alpha > 0) {
                assertEquals(0xFF0000, flash & 0xFFFFFF, "red while it shows");
            }
        }
        assertEquals(0, viewOf(game).flash(), "clear by the twelfth frame");
    }
}
