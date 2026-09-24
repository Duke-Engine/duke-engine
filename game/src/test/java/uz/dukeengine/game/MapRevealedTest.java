package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;

/** A beaten player watches the end: the whole map revealed mid-match, and this machine a watcher. */
class MapRevealedTest {

    private static final int PORT = 17807;
    private static final Coord3D ONES_CORNER = new Coord3D(100f, 100f, 0f);
    private static final Coord3D TWOS_CORNER = new Coord3D(900f, 500f, 0f);

    /** Two sides in far corners, the first revealed the whole map at frame 20; the world's sum kept at 40. */
    private static DukeGame match(AtomicLong sumAt40, boolean reveal, boolean oneIsLocal) {
        var game = DukeGame.create("reveal-test").loadUnits(DukeGame.STARTER_UNITS).map(80, 50);
        var one = game.addPlayer("One", Color.BLUE);
        var two = game.addPlayer("Two", Color.RED);
        game.enemies(one, two);
        if (oneIsLocal) {
            game.localPlayer(one);
        }
        game.spawn("Rifleman", one, ONES_CORNER.x(), ONES_CORNER.y());
        game.spawn("Rifleman", two, TWOS_CORNER.x(), TWOS_CORNER.y());
        game.onTick(g -> {
            int frame = g.getLogic().getFrame();
            if (reveal && frame == 20) {
                g.revealMapTo(one);
            }
            if (frame == 40) {
                sumAt40.set(g.getLogic().checksum());
            }
        });
        return game;
    }

    @Test
    void theFirstPlayersMapIsRevealedTheFrameAfterTheCallAndNobodyElses() {
        var game = match(new AtomicLong(), true, true);
        game.runHeadless(20);
        assertFalse(game.getLogic().canSee(1, TWOS_CORNER), "fog: the far corner is dark");
        assertEquals(1, game.getSnapshot().units().size(), "and the enemy in it is not shown");

        game.runHeadless(1); // frame 20: the call

        assertTrue(game.getLogic().canSee(1, TWOS_CORNER), "revealed, the frame after the call");
        assertTrue(game.getSnapshot().revealed());
        assertEquals(2, game.getSnapshot().units().size(), "the enemy far away is shown");
        assertFalse(game.getLogic().canSee(2, ONES_CORNER), "the other player's fog is as it was");
    }

    @Test
    void aWatcherHasNoPlayerOfItsOwnSeesEverythingAndTheMatchGoesOn() {
        var game = match(new AtomicLong(), false, true);
        game.runHeadless(5);
        assertEquals(1, game.getLocalPlayerIndex());

        game.watch();
        game.runHeadless(5);

        assertEquals(-1, game.getLocalPlayerIndex(), "nothing of its own to select or order");
        assertTrue(game.isWatching());
        assertTrue(game.getSnapshot().revealed());
        assertEquals(2, game.getSnapshot().units().size(), "everything seen, through nobody's fog");
        assertEquals(10, game.getLogic().getFrame(), "and the match goes on");
    }

    @Test
    void theChecksumMatchesOnTwoMachines() throws Exception {
        var hosted = new AtomicReference<MultiplayerSession>();
        var failed = new AtomicReference<Exception>();
        var hosting = new Thread(() -> {
            try (var server = new ServerSocket(PORT)) {
                hosted.set(MultiplayerSession.host(server, 2, "", null));
            } catch (Exception e) {
                failed.set(e);
            }
        }, "test-host");
        hosting.start();
        Thread.sleep(200);
        var joined = MultiplayerSession.join("127.0.0.1", PORT);
        hosting.join(5000);
        assertNotNull(hosted.get(), () -> "the host failed: " + failed.get());

        var hostSum = new AtomicLong();
        var guestSum = new AtomicLong();
        var host = match(hostSum, true, false).multiplayer(hosted.get());
        var guest = match(guestSum, true, false).multiplayer(joined);
        long giveUp = System.nanoTime() + 20_000_000_000L;
        while ((hostSum.get() == 0 || guestSum.get() == 0) && System.nanoTime() < giveUp) {
            host.runHeadless(1);
            guest.runHeadless(1);
            Thread.sleep(1);
        }
        hosted.get().close();
        joined.close();

        var unrevealed = new AtomicLong();
        match(unrevealed, false, false).runHeadless(41);

        assertEquals(hostSum.get(), guestSum.get(), "both machines revealed it on the same frame");
        assertNotEquals(unrevealed.get(), hostSum.get(), "and it is in the sum, so a machine that did not would be caught");
    }
}
