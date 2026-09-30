package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.thing.ObjectStatus;

/** A thing made unselectable for a while: every way of selecting passes it by, until it is unmarked. */
class UnselectableTest {

    @Test
    void markedItCannotBePickedAndUnmarkedItCanAgain() {
        var game = DukeGame.create("drones").loadUnits(uz.dukeengine.rts.RtsFlavour.STARTER_UNITS).map(40, 40);
        var you = game.addPlayer("You", Color.BLUE);
        game.localPlayer(you);
        game.spawn("Rifleman", you, 50f, 50f);
        game.runHeadless(1);
        var drone = game.getLogic().getObjects().getFirst();
        assertTrue(selectable(game), "a rifleman may be selected");

        drone.setStatus(ObjectStatus.UNSELECTABLE); // serving its master
        game.runHeadless(1);
        assertFalse(selectable(game), "a click, a box, a double click, the pointer and a game's own pick pass it by");

        drone.clearStatus(ObjectStatus.UNSELECTABLE);
        game.runHeadless(1);
        assertTrue(selectable(game));
    }

    private static boolean selectable(DukeGame game) {
        return game.getSnapshot().units().getFirst().selectable();
    }
}
