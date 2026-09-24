package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.network.ChatLine;

/** Players talking during a match: beside it, never in it. */
class ChatTest {

    private static final int PORT = 17797;

    private static DukeGame match() {
        var game = DukeGame.create("chat-test").loadUnits(DukeGame.STARTER_UNITS);
        var one = game.addPlayer("One", Color.BLUE);
        var two = game.addPlayer("Two", Color.RED);
        game.enemies(one, two);
        game.spawn("Rifleman", one, 0, 0);
        game.spawn("Rifleman", two, 300, 0);
        return game;
    }

    @Test
    void aLineToEveryoneReachesThePeerWithItsSenderAndOneToAlliesDoesNotReachAnEnemy() throws Exception {
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
        var hostHeard = new CopyOnWriteArrayList<ChatLine>();
        var guestHeard = new CopyOnWriteArrayList<ChatLine>();
        host.onChat(hostHeard::add);
        guest.onChat(guestHeard::add);
        host.runHeadless(0);
        guest.runHeadless(0);

        host.say("allies only", List.of(1));
        host.say("salom, дўстим", List.of(1, 2));
        long giveUp = System.nanoTime() + 20_000_000_000L;
        while (guestHeard.isEmpty() && System.nanoTime() < giveUp) {
            host.runHeadless(1);
            guest.runHeadless(1);
            Thread.sleep(2);
        }

        assertEquals(List.of(new ChatLine(1, List.of(1, 2), "salom, дўстим")), guestHeard,
                "to everyone: it arrives, from player 1, the Cyrillic intact — and the line to allies never did");
        assertEquals("allies only", hostHeard.getFirst().text(), "the sender hears its own lines");
        assertEquals(2, hostHeard.size());
        hosted.get().close();
        joined.close();
    }

    @Test
    void talkingChangesNothingInTheMatch() {
        var quiet = match();
        var chatty = match();
        var heard = new CopyOnWriteArrayList<ChatLine>();
        chatty.onChat(heard::add);
        quiet.runHeadless(1);
        chatty.runHeadless(1);

        for (int n = 0; n < 5; n++) {
            chatty.say("gg " + n, List.of(1, 2));
            quiet.runHeadless(20);
            chatty.runHeadless(20);
        }

        assertEquals(quiet.getLogic().checksum(), chatty.getLogic().checksum());
        assertEquals(quiet.getLogic().getFrame(), chatty.getLogic().getFrame());
        assertEquals(5, heard.size(), "alone, the sender hears its own");
        assertTrue(heard.stream().allMatch(line -> line.sender() == 1));
    }
}
