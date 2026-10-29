package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** The words of the world a thing is drawn without reach its view while the game gives them, and leave with them. */
class WithoutWorldTest {

    @Test
    void theWordsAreInTheViewUntilTheyAreSetToNothing() {
        var game = DukeGame.create("withoutworld").loadUnits(uz.dukeengine.rts.RtsFlavour.STARTER_UNITS).map(40, 40);
        var me = game.addPlayer("Me", Colour.BLUE);
        game.localPlayer(me).spawn("Rifleman", me, 100f, 100f);
        game.runHeadless(1);
        var thing = game.getLogic().getObjects().getFirst();
        assertTrue(game.getSnapshot().units().getFirst().withoutWorld().isEmpty(), "none until given");

        thing.setWithoutWorld(Set.of("NIGHT"));
        game.runHeadless(1);
        assertEquals(Set.of("NIGHT"), game.getSnapshot().units().getFirst().withoutWorld());

        thing.setWithoutWorld(null);
        game.runHeadless(1);
        assertTrue(game.getSnapshot().units().getFirst().withoutWorld().isEmpty(), "gone when taken away");
    }
}
