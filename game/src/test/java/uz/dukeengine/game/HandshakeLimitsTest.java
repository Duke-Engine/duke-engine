package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** A lobby handshake that waits no longer than it is told: for a host that never answers, a guest that never says. */
class HandshakeLimitsTest {

    @Test
    @Timeout(10)
    void aJoinToAHostThatNeverAnswersGivesUpAtItsLimit() throws Exception {
        try (var server = new ServerSocket(0)) { // takes the connection, and says nothing
            long started = System.nanoTime();

            assertThrows(IOException.class,
                    () -> MultiplayerSession.join("127.0.0.1", server.getLocalPort(), Duration.ofSeconds(2)));

            long took = (System.nanoTime() - started) / 1_000_000;
            assertTrue(took >= 1900 && took < 3000, "gave up after " + took + " ms");
        }
    }

    @Test
    @Timeout(20)
    void aHostWithALimitLetsASilentGuestGoAndSeatsTheNext() throws Exception {
        try (var server = new ServerSocket(0)) {
            var hosted = new AtomicReference<MultiplayerSession>();
            var failed = new AtomicReference<Exception>();
            var hosting = new Thread(() -> {
                try {
                    hosted.set(MultiplayerSession.host(server, 2, "", null, Duration.ofSeconds(1)));
                } catch (Exception e) {
                    failed.set(e);
                }
            }, "test-host");
            hosting.start();
            try (var silent = new Socket("127.0.0.1", server.getLocalPort())) { // connects, and never says DUKE-JOIN
                Thread.sleep(100);
                var guest = MultiplayerSession.join("127.0.0.1", server.getLocalPort(), Duration.ofSeconds(5));
                hosting.join(10_000);

                assertNotNull(hosted.get(), () -> "the host failed: " + failed.get());
                assertEquals(2, guest.getLocalPlayerIndex(), "the next to come took the seat");
                assertTrue(silent.isConnected());
                guest.close();
                hosted.get().close();
            }
        }
    }
}
