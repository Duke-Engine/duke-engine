package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.Color;
import org.junit.jupiter.api.Test;

/** The words over a thing reach its view while the game gives them, and leave with them. */
class CaptionTest {

    @Test
    void aCaptionIsInTheViewUntilItIsSetToNothing() {
        var game = DukeGame.create("caption").loadUnits(uz.dukeengine.rts.RtsFlavour.STARTER_UNITS).map(40, 40);
        var me = game.addPlayer("Me", Color.BLUE);
        game.localPlayer(me).spawn("Rifleman", me, 100f, 100f);
        game.runHeadless(1);
        var thing = game.getLogic().getObjects().getFirst();
        assertNull(game.getSnapshot().units().getFirst().caption(), "none until given");

        thing.setCaption("Rally");
        game.runHeadless(1);
        assertEquals("Rally", game.getSnapshot().units().getFirst().caption());

        thing.setCaption(null);
        game.runHeadless(1);
        assertNull(game.getSnapshot().units().getFirst().caption(), "gone when taken away");
    }
}
