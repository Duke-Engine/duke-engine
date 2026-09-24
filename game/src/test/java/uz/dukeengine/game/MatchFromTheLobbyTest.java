package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * A match a game builds itself, after its own lobby: the host's settings reach every guest exactly as written, each
 * machine plays the seat the session gave it — and a machine that only watches sees everything and orders nothing.
 */
class MatchFromTheLobbyTest {

    private static final int PORT = 17791;

    /** What a game's lobby writes of its slots: anything at all, spaces and line breaks included. */
    private static final String SETTINGS = "M=maps/alpine assault;S=HHard AI,0,-1,2:|Player One,1,3,-1:\nseed 42";

    /** The match a game builds from the settings its lobby agreed on. */
    private static DukeGame match() {
        var game = DukeGame.create("lobby-test").loadUnits(DukeGame.STARTER_UNITS);
        var first = game.addPlayer("One", Color.BLUE);
        var second = game.addPlayer("Two", Color.RED);
        game.enemies(first, second);
        game.spawn("Rifleman", first, 0, 0);
        game.spawn("Rifleman", second, 300, 0);
        return game;
    }

    @Test
    void theHostsSettingsReachTheGuestWholeAndEachMachinePlaysItsSeat() throws Exception {
        var hosted = new AtomicReference<MultiplayerSession>();
        var failed = new AtomicReference<Exception>();
        var host = new Thread(() -> {
            try (var server = new ServerSocket(PORT)) {
                hosted.set(MultiplayerSession.host(server, 2, SETTINGS, null));
            } catch (Exception e) {
                failed.set(e);
            }
        }, "test-host");
        host.start();
        Thread.sleep(200);
        var joined = MultiplayerSession.join("127.0.0.1", PORT);
        host.join(5000);
        assertNotNull(hosted.get(), () -> "the host failed: " + failed.get());

        assertEquals(SETTINGS, joined.getScenarioSpec(), "as the host wrote it, spaces and line breaks and all");
        var hostMatch = match().multiplayer(hosted.get());
        var guestMatch = match().multiplayer(joined);
        hostMatch.runHeadless(0);
        guestMatch.runHeadless(0);
        assertEquals(1, hostMatch.getLocalPlayerIndex());
        assertEquals(2, guestMatch.getLocalPlayerIndex());
        hosted.get().close();
        joined.close();
    }

    @Test
    void aMachineThatWatchesSeesEveryoneThroughNobodysFog() {
        var game = DukeGame.create("observer-test").loadUnits(DukeGame.STARTER_UNITS);
        var first = game.addPlayer("One", Color.BLUE);
        var second = game.addPlayer("Two", Color.RED);
        game.enemies(first, second).observe();
        game.spawn("Rifleman", first, 0, 0);
        game.spawn("Rifleman", second, 3000, 3000);

        game.runHeadless(2);

        assertEquals(-1, game.getLocalPlayerIndex(), "nobody's seat");
        assertEquals(2, game.getSnapshot().units().size(), "both, however far apart");
        assertTrue(game.getSnapshot().frame() > 0, "and the world goes on without anyone playing it");
    }
}
