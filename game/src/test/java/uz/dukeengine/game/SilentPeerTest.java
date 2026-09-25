package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * A peer that stops sending with its connection still open: waited for, and said so, then taken out by the host at
 * the limit the game set, or on its word, and the rest play on — the reference's DisconnectManager.
 */
class SilentPeerTest {

    private static final int TWO_PORT = 17831;
    private static final int THREE_PORT = 17832;

    @Test
    @Timeout(60)
    void aGuestThatGoesQuietIsWaitedForThenTakenOutAtTheHostsLimit() throws Exception {
        var sessions = HostLeavesTest.sessions(TWO_PORT, 2);
        var host = HostLeavesTest.watched(2, sessions.get(0));
        var guest = HostLeavesTest.watched(2, sessions.get(1));
        sessions.get(0).setSilenceLimit(Duration.ofSeconds(2));
        long giveUp = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
        HostLeavesTest.stepUntil(List.of(host, guest), 300, giveUp);
        int guestStoppedAt = guest.frame(); // its machine hangs: stepped no more, its socket open

        int lastFrame = host.frame();
        int stalledAt = -1;
        long stalledSince = -1;
        long toldAfterMillis = -1;
        while (host.frame() < guestStoppedAt + 90 && System.nanoTime() < giveUp) {
            host.game().runHeadless(1);
            if (host.frame() != lastFrame) {
                lastFrame = host.frame();
                stalledSince = -1;
                continue;
            }
            if (stalledSince < 0) {
                stalledSince = System.nanoTime();
                stalledAt = Math.max(stalledAt, host.frame());
            }
            if (toldAfterMillis < 0 && sessions.get(0).waitingFor().stream().anyMatch(w -> w.player() == 2)) {
                toldAfterMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - stalledSince);
            }
            Thread.sleep(2);
        }

        assertTrue(toldAfterMillis >= 0 && toldAfterMillis < 1000,
                "told it waits for the guest within a second of stopping: " + toldAfterMillis);
        assertTrue(host.frame() >= guestStoppedAt + 90, "and ran on once it took the guest out: " + host.frame());
        assertNotNull(host.leftAt().get(2), "told the guest left");
        assertTrue(host.leftAt().get(2) > stalledAt, "from a frame nobody had run: " + host.leftAt().get(2)
                + " past " + stalledAt);
        assertTrue(sessions.get(0).waitingFor().isEmpty(), "waiting for nobody now");
        sessions.forEach(MultiplayerSession::close);
    }

    @Test
    @Timeout(60)
    void threePlayersTheHostTakesOneOutOnTheGamesWordAndTheOtherTwoRunOnAlike() throws Exception {
        var sessions = HostLeavesTest.sessions(THREE_PORT, 3);
        var host = HostLeavesTest.watched(3, sessions.get(0));
        var two = HostLeavesTest.watched(3, sessions.get(1));
        var three = HostLeavesTest.watched(3, sessions.get(2));
        long giveUp = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
        HostLeavesTest.stepUntil(List.of(host, two, three), 60, giveUp);

        sessions.get(0).takeOut(3); // the vote on the waiting screen
        HostLeavesTest.stepUntil(List.of(host, two), 660, giveUp);
        three.game().runHeadless(5);

        assertTrue(host.frame() >= 660 && two.frame() >= 660, "the other two ran on");
        for (int frame = 1; frame < 660; frame++) {
            assertEquals(host.sums().get(frame), two.sums().get(frame), "the worlds parted at frame " + frame);
        }
        assertNotNull(host.leftAt().get(3), "told the third left");
        assertEquals(host.leftAt(), two.leftAt(), "on the same frame on both");
        assertTrue(sessions.get(2).isConnectionLost(), "and the third, told it was taken out, stopped");
        sessions.forEach(MultiplayerSession::close);
    }
}
