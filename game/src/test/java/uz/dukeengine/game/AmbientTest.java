package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** The loop the game gives one thing reaches its view while given, and leaves with it, the match's checksum untouched. */
class AmbientTest {

    @Test
    void theLoopIsInTheViewUntilItIsTakenAwayAndTheChecksumNeverHearsOfIt() {
        var game = DukeGame.create("ambient").loadUnits(uz.dukeengine.rts.RtsFlavour.STARTER_UNITS).map(40, 40);
        var me = game.addPlayer("Me", Colour.BLUE);
        game.localPlayer(me).spawn("Rifleman", me, 100f, 100f);
        game.runHeadless(1);
        var thing = game.getLogic().getObjects().getFirst();
        assertNull(game.getSnapshot().units().getFirst().ambient(), "its template's until given");
        long before = game.getLogic().checksum();

        thing.setAmbient("fountain.loud");
        assertEquals(before, game.getLogic().checksum(), "drawing only");
        game.runHeadless(1);
        assertEquals("fountain.loud", game.getSnapshot().units().getFirst().ambient());

        thing.setAmbient("");
        game.runHeadless(1);
        assertEquals("", game.getSnapshot().units().getFirst().ambient(), "silenced");

        thing.setAmbient(null);
        game.runHeadless(1);
        assertNull(game.getSnapshot().units().getFirst().ambient(), "its template's again");
    }
}
