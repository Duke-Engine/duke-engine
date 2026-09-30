package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.event.StripPlayed;
import uz.dukeengine.core.math.Coord3D;

/** A picture strip the simulation plays at a point: shown where the viewer sees the point, and nowhere else. */
class StripPlayedTest {

    @Test
    void aStripInSightIsShownOneInFogIsNotAndAHeadlessGameBreaksNothing() {
        var game = DukeGame.create("Strips").loadUnits(uz.dukeengine.rts.RtsFlavour.STARTER_UNITS).map(80, 80);
        var me = game.addPlayer("Me", Color.CYAN);
        game.localPlayer(me).spawn("Rifleman", me, 100f, 100f);
        game.runHeadless(1);

        game.runOnSimThread(() -> {
            game.getLogic().strip("LevelGained", new Coord3D(100f, 100f, 10f), 4f, 15f);
            game.getLogic().strip("MoneyPickUp", new Coord3D(700f, 700f, 0f), 4f, 15f);
        });
        game.runHeadless(1);

        var events = game.getSnapshot().events();
        assertTrue(events.stream().anyMatch(e -> e instanceof StripPlayed strip && strip.strip().equals("LevelGained")),
                "over his own rifleman");
        assertFalse(events.stream().anyMatch(e -> e instanceof StripPlayed strip
                && strip.strip().equals("MoneyPickUp")), "not in the fog");
    }
}
