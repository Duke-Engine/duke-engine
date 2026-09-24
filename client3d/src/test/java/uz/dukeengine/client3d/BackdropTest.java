package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.rts.message.GameMessage;

/** The match behind a front end: nobody plays it, it takes no orders, and made again it plays the same way. */
class BackdropTest {

    /** Art with nothing to read. */
    private static final MatchLoad.Art NOTHING = new MatchLoad.Art() {
        @Override
        public boolean step() {
            return true;
        }

        @Override
        public float done() {
            return 1f;
        }
    };

    /** The game's recipe: two sides in a fight, and one soldier standing well away from it. */
    private static DukeGame recipe() {
        var game = DukeGame.create("backdrop-test").loadUnits(DukeGame.STARTER_UNITS).map(80, 40).randomSeed(7);
        var west = game.addPlayer("West", Color.BLUE);
        var east = game.addPlayer("East", Color.RED);
        game.enemies(west, east);
        game.spawn("Rifleman", west, 700, 350);
        game.spawn("Rifleman", west, 100, 100);
        game.spawn("Tank", east, 160, 100);
        game.spawn("Rifleman", east, 150, 130);
        return game;
    }

    private static void waitFor(BooleanSupplier condition, String what) throws Exception {
        long giveUp = System.nanoTime() + 20_000_000_000L;
        while (!condition.getAsBoolean()) {
            assertTrue(System.nanoTime() < giveUp, "waited too long for " + what);
            Thread.sleep(5);
        }
    }

    private static DukeGame started(Backdrop backdrop) throws Exception {
        var running = new DukeGame[1];
        waitFor(() -> (running[0] = backdrop.frame()) != null, "the backdrop to start");
        return running[0];
    }

    @Test
    void aBackdropAdvancesWithNobodyPlayingItAndTakesNoOrders() throws Exception {
        var backdrop = new Backdrop(BackdropTest::recipe, built -> NOTHING);
        var running = started(backdrop);
        try {
            assertEquals(-1, running.getLocalPlayerIndex(), "watched: nobody's seat");
            var loner = running.getLogic().findObject(new ObjectId(1));
            var stood = loner.getPosition();

            // An order as the client would send one for whoever is at the keys: nobody is.
            running.postCommand(new GameMessage.MoveTo(running.getLocalPlayerIndex(), List.of(new ObjectId(1)),
                    new Coord3D(100f, 350f, 0f)));
            waitFor(() -> running.getSnapshot().frame() > 30, "thirty frames");

            assertEquals(stood, loner.getPosition(), "the order moved nothing");
            assertEquals(4, running.getSnapshot().units().size(), "and everything is seen, through nobody's fog");
        } finally {
            backdrop.stop();
        }
        int stoppedAt = running.getLogic().getFrame();
        Thread.sleep(100);
        assertEquals(stoppedAt, running.getLogic().getFrame(), "torn down: not a frame more");

        var again = started(backdrop);
        backdrop.stop();
        assertNotSame(running, again, "and when the front end comes back, a fresh one");
    }

    @Test
    void aBackdropMadeAgainPlaysTheSameWay() {
        var first = recipe().observe();
        var second = recipe().observe();

        first.runHeadless(100);
        second.runHeadless(100);

        assertEquals(100, first.getLogic().getFrame());
        assertNotNull(first.getLogic().findObject(new ObjectId(3)));
        assertEquals(first.getLogic().checksum(), second.getLogic().checksum(), "the same seed, the same world at 100");
    }
}
