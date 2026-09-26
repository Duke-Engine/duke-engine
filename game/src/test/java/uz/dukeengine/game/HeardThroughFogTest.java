package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.event.SoundPlayed;
import uz.dukeengine.core.math.Coord3D;

/** What happens where the viewer does not see, carried for the sounds fog does not hide, and nothing else. */
class HeardThroughFogTest {

    private static DukeGame game(boolean hears) {
        var game = DukeGame.create("fog").loadUnits(DukeGame.STARTER_UNITS).map(60, 60).hearThroughFog(hears);
        var me = game.addPlayer("Me", Color.BLUE);
        var them = game.addPlayer("Them", Color.RED);
        game.enemies(me, them).localPlayer(me).spawn("Rifleman", me, 100f, 100f).spawn("Rifleman", them, 500f, 500f);
        game.runHeadless(1);
        return game;
    }

    @Test
    void aSoundWhereTheViewerDoesNotSeeIsCarriedApartFromWhatHeSeesAndSoIsTheThingThere() {
        var game = game(true);
        game.getLogic().sound("Nuke", new Coord3D(500f, 500f, 0f), 0);
        game.runHeadless(1);
        var frame = game.getSnapshot();
        assertTrue(frame.events().stream().noneMatch(SoundPlayed.class::isInstance), "not shown");
        assertEquals(1, frame.unseenEvents().stream().filter(SoundPlayed.class::isInstance).count(), "but carried");
        assertEquals(1, frame.hiddenUnits().size(), "and the rifleman there, out of sight");
        assertEquals(500f, frame.hiddenUnits().getFirst().x());
    }

    @Test
    void aGameThatDoesNotAskIsCarriedNothingOfIt() {
        var game = game(false);
        game.getLogic().sound("Nuke", new Coord3D(500f, 500f, 0f), 0);
        game.runHeadless(1);
        assertTrue(game.getSnapshot().unseenEvents().isEmpty());
        assertTrue(game.getSnapshot().hiddenUnits().isEmpty());
    }
}
