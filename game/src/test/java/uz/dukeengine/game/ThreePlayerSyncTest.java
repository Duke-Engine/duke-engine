package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
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
        Thread.sleep(100); // keep the join order deterministic: player 2, then 3
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
        assertEquals(1, host.getLocalPlayerIndex());
        assertEquals(2, guestTwo.getLocalPlayerIndex());
        assertEquals(3, guestThree.getLocalPlayerIndex());

        // Guests 2 and 3 have no connection to each other; both orders must still
        // reach both worlds, by way of the host.
        guestTwo.postCommand(new GameMessage.MoveTo(2, List.of(new ObjectId(2)), new Coord3D(250f, 60f, 0f)));
        guestThree.postCommand(new GameMessage.MoveTo(3, List.of(new ObjectId(3)), new Coord3D(60f, 250f, 0f)));

        // Frames, not attempts. A lock-step game that is waiting on a peer takes a
        // turn of runHeadless and advances nothing — which is correct, and which
        // means a loop counting its own turns is really counting how fast the
        // machine is. On a slow one the four hundred turns are spent waiting, ten
        // frames pass, the ordered unit has not gone anywhere, and the failure
        // lands on an assertion about movement that had nothing to do with it.
        int comparableFrames = 0;
        long giveUp = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        while (host.getLogic().getFrame() < 400 && System.nanoTime() < giveUp) {
            int wasAt = host.getLogic().getFrame();
            for (var game : games) {
                game.runHeadless(1);
                comparableFrames += compareAllOnSameFrame(games);
            }
            if (host.getLogic().getFrame() == wasAt) {
                // Held for a peer. Spinning on it would be a poll loop racing the
                // network and losing; there is nothing to do here but wait.
                Thread.sleep(1);
            }
        }
        assertTrue(host.getLogic().getFrame() >= 400,
                "the worlds never got through 400 frames: " + host.getLogic().getFrame());
        assertTrue(comparableFrames > 100,
                "the three must actually run in lock-step, got " + comparableFrames + " comparisons");

        // What guest 2 did is visible, identically, in guest 3's world — and they
        // never exchanged a byte.
        var inTwo = guestTwo.getLogic().findObject(new ObjectId(2)).getPosition();
        var inThree = guestThree.getLogic().findObject(new ObjectId(2)).getPosition();
        assertTrue(inTwo.x() < 390f, "guest 2's own unit moved");
        assertEquals(inTwo.x(), inThree.x(), 1e-6f);
        assertEquals(inTwo.y(), inThree.y(), 1e-6f);
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
