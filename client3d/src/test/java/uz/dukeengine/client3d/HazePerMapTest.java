package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import uz.dukeengine.game.Colour;
import uz.dukeengine.game.DukeGame;

/** The haze and the world picture, each for the maps a game names: an open world's, and none for a descent's floors. */
class HazePerMapTest {

    private static final Object WORLD = "the open world";
    private static final Object FLOOR = "a floor of the descent";

    @Test
    void aHazyWorldThenAnUnhazedFloor() {
        var visuals = Visuals.create().haze(40f, 48f, map -> map == WORLD);

        assertTrue(visuals.hazes(WORLD), "the world fades at its edge");
        assertFalse(visuals.hazes(FLOOR), "the floor drawn whole to its walls");
        assertTrue(Visuals.create().haze(40f, 48f).hazes(FLOOR), "one pair of numbers hazes every map");
        assertFalse(Visuals.create().hazes(WORLD), "no haze, as ever");
    }

    @Test
    void theWorldPictureIsHandedOnlyForTheMapsTheGameNames() {
        var game = DukeGame.create("world-picture-test").loadUnits(uz.dukeengine.rts.RtsFlavour.STARTER_UNITS)
                .map(20, 20);
        game.addPlayer("Alone", Colour.BLUE);
        game.boot();
        var app = new DukeRtsApp(game, Visuals.create(), Shell.standard(), null, null, null, null, null, null, null);
        var handed = new ArrayList<Picture>();

        app.worldPicture(64, _ -> false, handed::add);
        assertEquals(0, handed.size(), "a map it says no to: nothing handed");

        app.worldPicture(64, _ -> true, handed::add);
        assertEquals(1, handed.size(), "a map it names: its picture");
    }
}
