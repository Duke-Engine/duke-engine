package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.rts.message.GameMessage;

/**
 * Three machines, one game, over real TCP on localhost.
 *
 * <p>Two players is a special case that hides the interesting part: with three,
 * two of them are not connected to each other at all and only ever hear about
 * one another through the host. This is the test that the relay actually holds
 * everyone to the same world.
 */
class ThreePlayerSyncTest {

    private static final int PORT = 17778;

    private static DukeGame newGame() {
        var game = DukeGame.create("mp3-test").loadUnits(DukeGame.STARTER_UNITS);
        var one = game.addPlayer("One", Color.BLUE);
        var two = game.addPlayer("Two", Color.RED);
        var three = game.addPlayer("Three", Color.GREEN);
        game.enemies(one, two).enemies(one, three).enemies(two, three);
        // Far apart, so nothing fights and the only motion is the ordered kind.
        game.spawn("Rifleman", one, 0, 0);      // id 1
        game.spawn("Rifleman", two, 400, 0);    // id 2
        game.spawn("Rifleman", three, 0, 400);  // id 3
        return game;
    }

    @Test
    @Timeout(60)
    void threeGamesOverTcpStayBitIdentical() throws Exception {
        var host = newGame();
        var guestTwo = newGame();
        var guestThree = newGame();

        var hostError = new AtomicReference<Exception>();
        var hostThread = new Thread(() -> {
            try {
                host.hostMultiplayer(PORT, 3, null);
            } catch (Exception e) {
                hostError.set(e);
            }
        }, "test-host-3");
        hostThread.start();
        Thread.sleep(200); // let the server bind

        // Each guest blocks in join() until the host has everyone, so they cannot
        // be joined one after the other on this thread.
        var joinErrors = new AtomicReference<Exception>();
        var joinTwo = new Thread(() -> {
            try {
                guestTwo.joinMultiplayer("127.0.0.1", PORT);
            } catch (Exception e) {
                joinErrors.set(e);
            }
        }, "test-join-2");
        joinTwo.start();
        Thread.sleep(100); // nudges guest 2 in first; the assertions below do not rely on it
        guestThree.joinMultiplayer("127.0.0.1", PORT);
        joinTwo.join(10_000);
        hostThread.join(10_000);

        assertTrue(host.isMultiplayer(), () -> "host failed: " + hostError.get());
        assertTrue(guestTwo.isMultiplayer(), () -> "guest 2 failed: " + joinErrors.get());
        assertTrue(guestThree.isMultiplayer());

        var games = List.of(host, guestTwo, guestThree);
        for (var game : games) {
            game.runHeadless(0); // boot every world
        }
        // Which guest got which index is the accept order's business, and the sleep
        // above is a nudge rather than a guarantee: under load the two can be taken
        // in either order, and this test used to fail on that with "expected 2 but
        // was 3" — a race in its own setup, dressed as a sync failure. What the
        // engine actually promises is that each world controls exactly one player,
        // so the test reads the indices it was given and uses them.
        int two = guestTwo.getLocalPlayerIndex();
        int three = guestThree.getLocalPlayerIndex();
        assertEquals(1, host.getLocalPlayerIndex(), "the host is always player 1");
        assertEquals(Set.of(1, 2, 3), Set.of(host.getLocalPlayerIndex(), two, three),
                "each world controls one player, and between them all three");

        // Guests 2 and 3 have no connection to each other; both orders must still
        // reach both worlds, by way of the host. A player's unit carries its number.
        guestTwo.postCommand(new GameMessage.MoveTo(two, List.of(new ObjectId(two)), new Coord3D(250f, 60f, 0f)));
        guestThree.postCommand(new GameMessage.MoveTo(three, List.of(new ObjectId(three)), new Coord3D(60f, 250f, 0f)));

        // Frames, not attempts. A lock-step game that is waiting on a peer takes a
        // turn of runHeadless and advances nothing — which is correct, and which
        // means a loop counting its own turns is really counting how fast the
        // machine is. On a slow one the four hundred turns are spent waiting, ten
        // frames pass, the ordered unit has not gone anywhere, and the failure
        // lands on an assertion about movement that had nothing to do with it.
        int comparableFrames = 0;
        // Inside the @Timeout above, on purpose: a deadline longer than the one
        // JUnit enforces never fires, and the failure arrives as a bare timeout
        // instead of the assertion below saying which world was where.
        long giveUp = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(45);
        while (games.stream().anyMatch(g -> g.getLogic().getFrame() < 400) && System.nanoTime() < giveUp) {
            int wasAt = games.stream().mapToInt(g -> g.getLogic().getFrame()).sum();
            for (var game : games) {
                game.runHeadless(1);
            }
            // Then bring the laggards up to the leader, because three worlds do not
            // stay level on their own. `runHeadless(1)` is one ATTEMPT, and one whose
            // peer input has not arrived advances nothing — so a single stall puts a
            // world a frame behind, and stepping them one each from there keeps the
            // gap exactly as it is. With three, all three must be on the same frame
            // for anything to be compared at all, so the drift bites sooner: this
            // test was down to 24 comparisons in four hundred frames.
            for (int i = 0; i < 32 && !allOnSameFrame(games) && System.nanoTime() < giveUp; i++) {
                int leader = games.stream().mapToInt(g -> g.getLogic().getFrame()).max().orElse(0);
                for (var game : games) {
                    if (game.getLogic().getFrame() < leader) {
                        game.runHeadless(1);
                    }
                }
            }
            comparableFrames += compareAllOnSameFrame(games);
            if (games.stream().mapToInt(g -> g.getLogic().getFrame()).sum() == wasAt) {
                // Held for a peer. Spinning on it would be a poll loop racing the
                // network and losing; there is nothing to do here but wait.
                Thread.sleep(1);
            }
        }
        assertTrue(games.stream().allMatch(g -> g.getLogic().getFrame() >= 400),
                "the worlds never got through 400 frames: "
                        + games.stream().map(g -> String.valueOf(g.getLogic().getFrame())).toList());
        assertTrue(comparableFrames > 100,
                "the three must actually run in lock-step, got " + comparableFrames + " comparisons");

        // What guest 2 did is visible, identically, in guest 3's world — and they
        // never exchanged a byte.
        var inTwo = guestTwo.getLogic().findObject(new ObjectId(two)).getPosition();
        var inThree = guestThree.getLogic().findObject(new ObjectId(two)).getPosition();
        assertTrue(inTwo.x() < 390f, "guest 2's own unit moved");
        assertEquals(inTwo.x(), inThree.x(), 1e-6f);
        assertEquals(inTwo.y(), inThree.y(), 1e-6f);
    }

    /** Whether every world is on the same frame, which is when there is anything to compare. */
    private static boolean allOnSameFrame(List<DukeGame> games) {
        int frame = games.get(0).getLogic().getFrame();
        return games.stream().allMatch(g -> g.getLogic().getFrame() == frame);
    }

    /** Assert equality across every pair of games that happen to be on the same frame. */
    private static int compareAllOnSameFrame(List<DukeGame> games) {
        var reference = games.get(0);
        int frame = reference.getLogic().getFrame();
        if (frame == 0) {
            return 0;
        }
        var matched = new ArrayList<DukeGame>();
        for (var game : games) {
            if (game.getLogic().getFrame() == frame) {
                matched.add(game);
            }
        }
        for (var game : matched) {
            assertEquals(reference.getLogic().checksum(), game.getLogic().checksum(),
                    "worlds desynced at frame " + frame);
        }
        return matched.size() > 1 ? 1 : 0;
    }
}
