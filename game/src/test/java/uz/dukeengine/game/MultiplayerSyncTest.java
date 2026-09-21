package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.core.thing.ObjectId;

/**
 * The multiplayer promise, end to end over real TCP on localhost: two complete
 * games (menus aside), each issuing its own player's commands, must stay
 * bit-identical frame for frame — the same guarantee the engine's lock-step
 * core proved, now at the {@link DukeGame} API level players actually use.
 */
class MultiplayerSyncTest {

    private static final int PORT = 17777;

    private static DukeGame newGame() {
        var game = DukeGame.create("mp-test").loadUnits(DukeGame.STARTER_UNITS);
        var host = game.addPlayer("Host", Color.BLUE);
        var guest = game.addPlayer("Guest", Color.RED);
        game.enemies(host, guest);
        game.money(host, 500).money(guest, 500);
        game.spawn("Rifleman", host, 0, 0);     // id 1, host's
        game.spawn("Rifleman", guest, 300, 0);  // id 2, guest's (far apart — no combat)
        return game;
    }

    @Test
    void twoGamesOverTcpStayBitIdentical() throws Exception {
        var host = newGame();
        var guest = newGame();

        var hostError = new AtomicReference<Exception>();
        var hostThread = new Thread(() -> {
            try {
                host.hostMultiplayer(PORT);
            } catch (Exception e) {
                hostError.set(e);
            }
        }, "test-host");
        hostThread.start();
        Thread.sleep(200); // let the server bind before the guest dials in
        guest.joinMultiplayer("127.0.0.1", PORT);
        hostThread.join(5000);
        assertTrue(host.isMultiplayer(), () -> "host failed: " + hostError.get());

        host.runHeadless(0);  // boot both worlds
        guest.runHeadless(0);
        assertEquals(1, host.getLocalPlayerIndex(), "host controls player 1");
        assertEquals(2, guest.getLocalPlayerIndex(), "guest controls player 2");

        // each machine orders ITS OWN unit around — commands cross the wire
        host.postCommand(new GameMessage.MoveTo(1, List.of(new ObjectId(1)), new Coord3D(150f, 40f, 0f)));
        guest.postCommand(new GameMessage.MoveTo(2, List.of(new ObjectId(2)), new Coord3D(200f, 80f, 0f)));

        // Interleaved stepping phase-shifts the two sims by up to a frame, so
        // compare after EACH half-step, whenever the frame counters line up.
        //
        // Counted in frames rather than in turns of this loop, and the difference
        // is not pedantry. A lock-step game waiting on its peer takes a turn of
        // runHeadless and advances nothing, which is exactly what it should do —
        // so a loop of three hundred turns measures how fast the machine is. On a
        // slow one the turns go on waiting, a handful of frames pass, and the
        // failure arrives as an assertion about a unit that has not moved yet.
        int comparableFrames = 0;
        // Two minutes, which is not a guess at how long this takes — on any machine
        // that is not thrashing it is under a second. It is the ceiling at which a
        // sync that will never come stops being a hung build. Thirty seconds was
        // not enough on a CI runner carrying three jobs, and a flaky test on the
        // release path is a tag that fails for no reason anybody can act on.
        long giveUp = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(120);
        while (frameOf(host) < 300 && frameOf(guest) < 300 && System.nanoTime() < giveUp) {
            int wasAt = frameOf(host) + frameOf(guest);
            host.runHeadless(1);
            comparableFrames += compareIfSameFrame(host, guest);
            guest.runHeadless(1);
            comparableFrames += compareIfSameFrame(host, guest);
            if (frameOf(host) + frameOf(guest) == wasAt) {
                // Both held for the other's orders. Spinning on that would be a
                // poll loop racing the network; there is nothing to do but wait.
                Thread.sleep(1);
            }
        }
        assertTrue(frameOf(host) >= 300 || frameOf(guest) >= 300,
                "the worlds never got through 300 frames: host at " + frameOf(host)
                        + ", guest at " + frameOf(guest));
        assertTrue(comparableFrames > 100, "the games must actually run in lock-step, got "
                + comparableFrames + " comparable frames");

        // both commands took effect in BOTH worlds identically
        var hostView = host.getLogic().findObject(new ObjectId(2));
        var guestView = guest.getLogic().findObject(new ObjectId(2));
        assertTrue(guestView.getPosition().x() < 290f, "guest's unit moved in its own world");
        assertEquals(guestView.getPosition().x(), hostView.getPosition().x(), 1e-6f,
                "and its position is bit-identical in the host's world");
    }

    private static int frameOf(DukeGame game) {
        return game.getLogic().getFrame();
    }

    /** Returns 1 and asserts checksum equality when both sims are on the same frame. */
    private static int compareIfSameFrame(DukeGame a, DukeGame b) {
        if (a.getLogic().getFrame() != b.getLogic().getFrame() || a.getLogic().getFrame() == 0) {
            return 0;
        }
        assertEquals(a.getLogic().checksum(), b.getLogic().checksum(),
                "worlds desynced at frame " + a.getLogic().getFrame());
        return 1;
    }
}
