package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.event.EffectPlayed;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;

/** An effect the simulation plays at a place, or on a thing, reaches the clients that see it, on its frame. */
class EffectPlayedTest {

    private static final String UNITS = """
            Object
              Name = Derrick
              KindOf = [STRUCTURE]
              VisionRange = 150
              Geometry = Cylinder
                Radius = 8
                Height = 12
              End
              Modules = [
                ActiveBody
                  MaxHealth = 500
                End
              ]
            End
            """;

    private static DukeGame game() {
        var game = DukeGame.create("Effects").loadUnits(UNITS).map(80, 80);
        var me = game.addPlayer("Me", Color.CYAN);
        var them = game.addPlayer("Them", Color.RED);
        game.enemies(me, them).localPlayer(me).spawn("Derrick", me, 100f, 100f).spawn("Derrick", them, 700f, 700f);
        game.runHeadless(1);
        return game;
    }

    private static List<EffectPlayed> played(DukeGame game) {
        return game.getSnapshot().events().stream().filter(EffectPlayed.class::isInstance)
                .map(EffectPlayed.class::cast).toList();
    }

    @Test
    void anEffectWhereThePlayerSeesReachesHisClientOnItsFrameAndOneInTheFogDoesNot() {
        var game = game();

        game.effect("Pulse", 100f, 100f, 12f, 1.5f);
        game.runHeadless(1);
        var seen = played(game);
        assertEquals(1, seen.size(), "over my own derrick");
        // The snapshot counts the frames done; the effect carries the one it was played on, the last of them.
        assertEquals(new EffectPlayed(game.getSnapshot().frame() - 1, "Pulse", new Coord3D(100f, 100f, 12f), 1.5f,
                null), seen.getFirst(), "at its point, turned its way, in the picture of the frame it was played on");

        game.effect("Pulse", 700f, 700f, 12f, 0f);
        game.runHeadless(1);
        assertTrue(played(game).isEmpty(), "over theirs, in my fog: nothing");

        game.effect("NoSuchEffect", 100f, 100f, 0f, 0f);
        game.runHeadless(1);
        assertEquals("NoSuchEffect", played(game).getFirst().name(), "a name nobody drew is carried, and breaks nothing");
    }

    @Test
    void anEffectOnAThingRidesIt() {
        var game = game();
        GameObject mine = game.getLogic().getObjects().stream().filter(o -> o.getPlayerIndex() == 1).findFirst()
                .orElseThrow();

        game.runOnSimThread(() -> game.getLogic().effect("Burning", mine));
        game.runHeadless(1);

        var seen = played(game).getFirst();
        assertEquals(mine.getId(), seen.riding());
        assertEquals(mine.getPosition(), seen.where());
    }

    @Test
    void effectsTakeNoPartInTheChecksum() {
        var quiet = game();
        var busy = game();
        for (int frame = 0; frame < 30; frame++) {
            busy.effect("Pulse", 100f, 100f, 0f, frame);
            quiet.runHeadless(1);
            busy.runHeadless(1);
        }
        assertEquals(quiet.getLogic().checksum(), busy.getLogic().checksum());
    }
}
