package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;

/** What the snapshot says of a thing's height, pitch and roll: what the simulation keeps, for the client to draw. */
class HeightInViewTest {

    @Test
    void aThingsHeightPitchAndRollReachTheSnapshot() {
        var game = DukeGame.create("height").loadUnits(uz.dukeengine.rts.RtsFlavour.STARTER_UNITS).map(70, 45);
        var you = game.addPlayer("You", Color.BLUE);
        game.localPlayer(you);
        game.spawn("Rifleman", you, 50f, 50f);
        game.runHeadless(1);
        var rifleman = game.getLogic().getObjects().getFirst();
        rifleman.setPosition(new Coord3D(50f, 50f, 100f));
        rifleman.setPitch(0.5f);
        rifleman.setRoll(-0.25f);
        rifleman.setKeepsOwnHeight(true);

        game.runHeadless(1);

        var view = game.getSnapshot().units().getFirst();
        assertEquals(100f, view.z(), 0f);
        assertEquals(0.5f, view.pitch(), 0f);
        assertEquals(-0.25f, view.roll(), 0f);
        assertTrue(view.ownHeight());
    }
}
