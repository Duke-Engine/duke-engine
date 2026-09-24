package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.game.DukeGame;

/**
 * A match loading while the window draws: the game told how far it has got, rising to 100 with its canvas painted
 * after every figure, and a start the game holds kept until the game lets it go.
 */
class MatchLoadTest {

    /** Art that takes five frames to read. */
    private static final class FiveFrames implements MatchLoad.Art {
        int steps;

        @Override
        public boolean step() {
            return ++steps >= 5;
        }

        @Override
        public float done() {
            return steps / 5f;
        }
    }

    private static DukeGame match() {
        var game = DukeGame.create("load-test").loadUnits(DukeGame.STARTER_UNITS).map(40, 30);
        var one = game.addPlayer("One", Color.BLUE);
        var two = game.addPlayer("Two", Color.RED);
        game.enemies(one, two);
        game.spawn("Rifleman", one, 50, 50);
        game.spawn("Rifleman", two, 250, 50);
        return game;
    }

    /** Frames of the window, a painted canvas after each, until the load is ready or time runs out. */
    private static void drawUntil(MatchLoad load, List<String> happened, int percentAtLeast) throws Exception {
        long giveUp = System.nanoTime() + 20_000_000_000L;
        while (load.percent() < percentAtLeast && System.nanoTime() < giveUp) {
            load.frame();
            happened.add("painted");
            Thread.sleep(1);
        }
    }

    @Test
    void theGameIsToldRisingFiguresEndingAt100WithItsCanvasPaintedAfterEach() throws Exception {
        var happened = new ArrayList<String>();
        var told = new ArrayList<Integer>();
        var load = new MatchLoad(match(), built -> new FiveFrames(), percent -> {
            told.add(percent);
            happened.add("told " + percent);
        }, false);

        drawUntil(load, happened, 100);

        assertTrue(load.ready(), "loaded, and nothing holds it");
        assertEquals(100, told.getLast());
        for (int at = 1; at < told.size(); at++) {
            assertTrue(told.get(at) > told.get(at - 1), "rising: " + told);
        }
        assertTrue(told.stream().anyMatch(percent -> percent >= MatchLoad.BUILT && percent < 100),
                "the art's part told too: " + told);
        for (int at = 0; at < happened.size(); at++) {
            if (happened.get(at).startsWith("told")) {
                assertEquals("painted", happened.get(at + 1), "the canvas painted after every figure: " + happened);
            }
        }
    }

    @Test
    void aStartTheGameHoldsWaitsAt100UntilItIsLetGo() throws Exception {
        var game = match();
        var load = new MatchLoad(game, built -> new FiveFrames(), percent -> { }, true);

        drawUntil(load, new ArrayList<>(), 100);
        for (int frame = 0; frame < 10; frame++) {
            load.frame();
        }

        assertEquals(100, load.percent());
        assertFalse(load.ready(), "held at 100");
        assertEquals(0, game.getLogic().getFrame(), "and not one frame of the match stepped");
        load.release();
        assertTrue(load.ready(), "let go, it starts");
    }
}
