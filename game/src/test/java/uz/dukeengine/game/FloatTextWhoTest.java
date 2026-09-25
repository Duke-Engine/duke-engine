package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.event.TextFloated;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;

/** A floated text that says who sees it: the players named, or the owner of what it is about and whoever sees that. */
class FloatTextWhoTest {

    /** An American rifleman hidden from China, beside a Chinese one who would otherwise see him; shown to {@code local}. */
    private static DukeGame match(int local) {
        var game = DukeGame.create("income").loadUnits(DukeGame.STARTER_UNITS).map(70, 45);
        var usa = game.addPlayer("USA", Color.BLUE);
        var china = game.addPlayer("China", Color.RED);
        game.enemies(usa, china).localPlayer(local == 1 ? usa : china);
        game.spawn("Rifleman", usa, 50f, 50f);
        game.spawn("Rifleman", china, 70f, 50f);
        game.runHeadless(1);
        hider(game).addModule(new ConcealedInViewTest.Stealth(hider(game)));
        game.runHeadless(1);
        return game;
    }

    private static GameObject hider(DukeGame game) {
        return game.getLogic().getObjects().stream().filter(o -> o.getPlayerIndex() == 1).findFirst().orElseThrow();
    }

    private static List<String> texts(DukeGame game) {
        return game.getSnapshot().events().stream().filter(TextFloated.class::isInstance)
                .map(event -> ((TextFloated) event).text()).toList();
    }

    @Test
    void aTextAboutAThingHiddenAndUndetectedReachesItsOwnerAndNotItsEnemy() {
        var mine = match(1);
        mine.floatTextAbout(hider(mine).getId(), "$10", 0xE6FFFFFF);
        mine.runHeadless(1);
        assertEquals(List.of("$10"), texts(mine), "its owner sees its income");

        var theirs = match(2);
        theirs.floatTextAbout(hider(theirs).getId(), "$10", 0xE6FFFFFF);
        theirs.floatText("seen", 50f, 50f, 0f, 0xE6FFFFFF); // the point itself is in their sight
        theirs.runHeadless(1);
        assertEquals(List.of("seen"), texts(theirs), "the enemy is told nothing of where it stands");
    }

    @Test
    void aTextForNamedPlayersReachesThemAloneWhereverTheyLook() {
        var theirs = match(2);
        theirs.runOnSimThread(() -> {
            var logic = theirs.getLogic();
            logic.post(TextFloated.to(logic.getFrame(), new Coord3D(3000f, 3000f, 0f), "for China", 0xE6FFFFFF,
                    List.of(2)));
            logic.post(TextFloated.to(logic.getFrame(), new Coord3D(50f, 50f, 0f), "for the USA", 0xE6FFFFFF,
                    List.of(1)));
        });
        theirs.runHeadless(1);

        assertTrue(texts(theirs).contains("for China"), "far in the fog, but theirs");
        assertTrue(!texts(theirs).contains("for the USA"), "in their sight, but not theirs");
    }
}
