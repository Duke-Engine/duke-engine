package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.event.TextFloated;

/** A text floated from the game reaches the player's client only where the player can see the point. */
class FloatTextTest {

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

    @Test
    void aTextFloatedWhereThePlayerSeesReachesHisClientAndOneInTheFogDoesNot() {
        var game = DukeGame.create("Floating").loadUnits(UNITS).map(80, 80);
        var me = game.addPlayer("Me", Color.CYAN);
        var them = game.addPlayer("Them", Color.RED);
        game.enemies(me, them).localPlayer(me).spawn("Derrick", me, 100f, 100f).spawn("Derrick", them, 700f, 700f);
        game.runHeadless(1);

        game.floatText("$200", 100f, 100f, 12f, 0xE6FFFFFF);
        game.runHeadless(1);
        var seen = game.getSnapshot().events().stream().filter(TextFloated.class::isInstance)
                .map(TextFloated.class::cast).toList();
        assertEquals(1, seen.size(), "over my own derrick");
        assertEquals("$200", seen.getFirst().text());
        assertEquals(0xE6FFFFFF, seen.getFirst().argb());

        game.floatText("$200", 700f, 700f, 12f, 0xE6FFFFFF);
        game.runHeadless(1);
        assertTrue(game.getSnapshot().events().stream().noneMatch(TextFloated.class::isInstance),
                "over theirs, in my fog: nothing");
    }
}
