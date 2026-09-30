package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.Color;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.combat.message.CombatOrder;

/** A game's own order travels as the engine's orders do: to every machine, on a frame boundary, into the replay. */
class GameOrderTest {

    private static final int PORT = 17803;
    private static final ObjectId FIRST = new ObjectId(1);
    private static final ObjectId SECOND = new ObjectId(2);

    private static DukeGame match() {
        var game = DukeGame.create("order-test").loadUnits(uz.dukeengine.rts.RtsFlavour.STARTER_UNITS).map(80, 40);
        var one = game.addPlayer("One", Color.BLUE);
        var two = game.addPlayer("Two", Color.RED);
        game.enemies(one, two);
        game.spawn("Rifleman", one, 100, 100);
        game.spawn("Rifleman", one, 100, 160);
        game.spawn("Rifleman", two, 700, 100);
        return game;
    }

    private static uz.dukeengine.combat.message.GameOrder order(int player, String word, ObjectId target) {
        return new uz.dukeengine.combat.message.GameOrder(player, word, List.of(), new Coord3D(300f, 200f, 0f), target, 42L);
    }

    /** What a machine heard: which order, on which frame, and whether the move posted beside it had been applied. */
    private static void listen(DukeGame game, List<String> heard) {
        game.onOrder(order -> {
            var unit = game.getLogic().findObject(order.target());
            heard.add(order.word() + " @" + game.getLogic().getFrame() + " moving=" + unit.getLocomotor().isMoving()
                    + " " + order.place() + " " + order.number());
        });
    }

    @Test
    void twoMachinesHearItOnTheSameFrameInTheOrderItWasGivenBesideAMove() throws Exception {
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

        var host = match().multiplayer(hosted.get());
        var guest = match().multiplayer(joined);
        var hostHeard = new CopyOnWriteArrayList<String>();
        var guestHeard = new CopyOnWriteArrayList<String>();
        listen(host, hostHeard);
        listen(guest, guestHeard);
        host.runHeadless(0);
        guest.runHeadless(0);

        int me = host.getLocalPlayerIndex();
        // A move, then the order: the order finds its unit already moving.
        host.postCommand(new CombatOrder.MoveTo(me, List.of(FIRST), new Coord3D(400f, 100f, 0f)));
        host.postCommand(order(me, "after the move", FIRST));
        // The order, then a move: the order finds its unit standing.
        host.postCommand(order(me, "before the move", SECOND));
        host.postCommand(new CombatOrder.MoveTo(me, List.of(SECOND), new Coord3D(400f, 160f, 0f)));
        long giveUp = System.nanoTime() + 20_000_000_000L;
        while ((hostHeard.size() < 2 || guestHeard.size() < 2) && System.nanoTime() < giveUp) {
            host.runHeadless(1);
            guest.runHeadless(1);
            Thread.sleep(2);
        }

        assertEquals(2, hostHeard.size(), () -> "heard " + hostHeard);
        assertEquals(hostHeard, guestHeard, "the same orders, on the same frame, in the same order, on both");
        assertEquals(true, hostHeard.get(0).contains("after the move") && hostHeard.get(0).contains("moving=true"));
        assertEquals(true, hostHeard.get(1).contains("before the move") && hostHeard.get(1).contains("moving=false"));
        hosted.get().close();
        joined.close();
    }

    @Test
    void aReplayHearsItAgainOnTheSameFrame() {
        var heard = new CopyOnWriteArrayList<String>();
        var recorded = match().recordReplay();
        listen(recorded, heard);
        recorded.runHeadless(10);
        recorded.postCommand(order(1, "cash hack", FIRST));
        recorded.runHeadless(20);

        var heardAgain = new CopyOnWriteArrayList<String>();
        var replayed = match().playReplay(recorded.getReplayText());
        listen(replayed, heardAgain);
        replayed.runHeadless(30);

        assertEquals(1, heard.size());
        assertEquals(heard, heardAgain, "the recording holds it, and plays it on the frame it was taken on");
    }

    @Test
    void aloneItIsHeardOnTheNextFrame() {
        var frames = new CopyOnWriteArrayList<Integer>();
        var game = match();
        game.onOrder(order -> frames.add(game.getLogic().getFrame()));
        game.runHeadless(3);
        int postedAt = game.getLogic().getFrame();

        game.postCommand(order(1, "spy vision", FIRST));
        game.runHeadless(5);

        assertEquals(List.of(postedAt + 1), frames);
    }
}
