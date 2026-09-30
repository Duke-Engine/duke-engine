package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Loading, told: the engine's own steps as a match is built, and each machine's figure heard by the others. */
class LoadProgressTest {

    private static final int PORT = 17793;

    private static DukeGame match() {
        var game = DukeGame.create("progress-test").loadUnits(uz.dukeengine.rts.RtsFlavour.STARTER_UNITS);
        var one = game.addPlayer("One", Color.BLUE);
        var two = game.addPlayer("Two", Color.RED);
        game.enemies(one, two);
        game.spawn("Rifleman", one, 0, 0);
        game.spawn("Rifleman", two, 300, 0);
        return game;
    }

    @Test
    void buildingAMatchSaysRisingStepsEndingAt100() {
        var steps = new ArrayList<Integer>();

        match().boot(steps::add);

        assertEquals(100, steps.getLast());
        for (int at = 1; at < steps.size(); at++) {
            assertTrue(steps.get(at) > steps.get(at - 1), "rising: " + steps);
        }
        assertTrue(steps.size() >= 5, "the engine's own steps, not just the end: " + steps);
    }

    @Test
    void inATwoMachineSessionEachHearsTheOthersProgress() throws Exception {
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
        var hostHeard = new ArrayList<String>();
        var guestHeard = new ArrayList<String>();
        host.onPeerLoadProgress((player, percent) -> hostHeard.add(player.getName() + " " + percent));
        guest.onPeerLoadProgress((player, percent) -> guestHeard.add(player.getName() + " " + percent));
        host.boot();
        guest.boot();

        long giveUp = System.nanoTime() + 20_000_000_000L;
        while ((!hostHeard.contains("Two 30") || !guestHeard.contains("One 55")) && System.nanoTime() < giveUp) {
            host.loadProgress(55);
            guest.loadProgress(30);
            Thread.sleep(5);
        }

        assertTrue(hostHeard.contains("Two 30"), "the host heard the guest: " + hostHeard);
        assertTrue(guestHeard.contains("One 55"), "the guest heard the host: " + guestHeard);
        assertEquals(List.of("Two 30"), hostHeard.stream().distinct().toList(), "and only the other's: " + hostHeard);
        hosted.get().close();
        joined.close();
    }
}
