package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.combat.message.CombatOrder;

/**
 * The match behind a front end: nobody plays it, it takes no orders, and made again it plays the same way; held back
 * while the game's movies play, and telling the game's load screen how far it has got.
 */
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
        var game = DukeGame.create("backdrop-test").loadUnits(uz.dukeengine.rts.RtsFlavour.STARTER_UNITS).map(80, 40).randomSeed(7);
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
        var backdrop = new Backdrop(BackdropTest::recipe, built -> NOTHING, percent -> { });
        var running = started(backdrop);
        try {
            assertEquals(-1, running.getLocalPlayerIndex(), "watched: nobody's seat");
            var loner = running.getLogic().findObject(new ObjectId(1));
            var stood = loner.getPosition();

            // An order as the client would send one for whoever is at the keys: nobody is.
            running.postCommand(new CombatOrder.MoveTo(running.getLocalPlayerIndex(), List.of(new ObjectId(1)),
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
    void heldItIsNotAskedForOrReadAndLetGoItIsMadeOnceAndToldUpTo100() throws Exception {
        var asked = new AtomicInteger();
        var read = new AtomicInteger();
        var told = new ArrayList<Integer>();
        var ranBy100 = new boolean[1];
        var self = new Backdrop[1];
        var backdrop = new Backdrop(() -> {
            asked.incrementAndGet();
            return recipe();
        }, built -> {
            read.incrementAndGet();
            return NOTHING;
        }, percent -> {
            told.add(percent);
            if (percent == 100) { // the world, at the moment the game hears 100
                var running = self[0].running();
                ranBy100[0] = running != null && running.getSnapshot().frame() > 0;
            }
        });
        self[0] = backdrop;

        backdrop.hold(true);
        for (int frame = 0; frame < 300; frame++) {
            assertNull(backdrop.frame());
        }
        assertEquals(0, asked.get(), "held: the recipe not asked over 300 frames");
        assertEquals(0, read.get(), "and nothing of it read: a movie played meanwhile has the machine to itself");
        assertTrue(told.isEmpty());

        backdrop.hold(false);
        var running = started(backdrop);
        try {
            waitFor(() -> {
                backdrop.frame();
                return told.contains(100);
            }, "100");
            assertEquals(1, asked.get(), "let go, made once");
            assertEquals(1, read.get());
            for (int i = 1; i < told.size(); i++) {
                assertTrue(told.get(i) > told.get(i - 1), "each figure once, rising: " + told);
            }
            assertEquals(100, told.getLast());
            assertTrue(ranBy100[0], "100 once a frame of it has run, not when it is merely started");
        } finally {
            backdrop.stop();
        }
        int stoppedAt = running.getLogic().getFrame();
        Thread.sleep(100);
        assertEquals(stoppedAt, running.getLogic().getFrame(), "a match started: the backdrop still stops");
    }

    @Test
    void heldAgainTheNextFrontEndMakesNoneUntilLetGo() throws Exception {
        var asked = new AtomicInteger();
        var backdrop = new Backdrop(() -> {
            asked.incrementAndGet();
            return recipe();
        }, built -> NOTHING, percent -> { });
        started(backdrop);
        backdrop.stop(); // a match starts

        backdrop.hold(true); // and before the front end comes back, the game holds it again
        for (int frame = 0; frame < 300; frame++) {
            assertNull(backdrop.frame());
        }
        assertEquals(1, asked.get());

        backdrop.hold(false);
        started(backdrop);
        backdrop.stop();
        assertEquals(2, asked.get(), "a fresh one once let go");
    }

    @Test
    void aRecipeThatMakesNoMatchStillTells100() {
        var told = new ArrayList<Integer>();
        var backdrop = new Backdrop(() -> null, built -> NOTHING, told::add);

        assertNull(backdrop.frame());
        assertNull(backdrop.frame());

        assertEquals(List.of(100), told, "none to be had: the load screen is not left waiting for ever");
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
