package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import uz.dukeengine.core.network.LineChannel;

/** A network game over channels of lines a game opens itself — a WebSocket through a gateway — played as over TCP. */
class LineChannelSessionTest {

    /** A host and a guest joined through an in-memory pair of line channels. */
    private static List<MultiplayerSession> joined(LineChannel[] ends) throws Exception {
        var hosted = new AtomicReference<MultiplayerSession>();
        var failed = new AtomicReference<Exception>();
        var hosting = new Thread(() -> {
            try {
                hosted.set(MultiplayerSession.host(() -> ends[0], 2, "map=Plains", null));
            } catch (Exception e) {
                failed.set(e);
            }
        }, "channel-host");
        hosting.start();
        var guest = MultiplayerSession.join(ends[1]);
        hosting.join(10_000);
        assertNotNull(hosted.get(), () -> "the host failed: " + failed.get());
        assertEquals("map=Plains", guest.getScenarioSpec(), "the host's settings, whole");
        assertEquals(2, guest.getLocalPlayerIndex());
        return List.of(hosted.get(), guest);
    }

    @Test
    @Timeout(60)
    void twoSessionsOverAPairOfLineChannelsPlay300FramesWithEqualChecksums() throws Exception {
        var sessions = joined(LineChannel.pair());
        var worlds = List.of(HostLeavesTest.watched(2, sessions.get(0)), HostLeavesTest.watched(2, sessions.get(1)));
        HostLeavesTest.stepUntil(worlds, 300, System.nanoTime() + 40_000_000_000L);
        for (int frame = 1; frame <= 300; frame++) {
            assertEquals(worlds.get(0).sums().get(frame), worlds.get(1).sums().get(frame), "frame " + frame);
        }
        sessions.forEach(MultiplayerSession::close);
    }

    @Test
    @Timeout(60)
    void aChannelClosedMidGameIsALostLinkToldAsASocketsCloseIs() throws Exception {
        var ends = LineChannel.pair();
        var sessions = joined(ends);
        var host = HostLeavesTest.watched(2, sessions.get(0));
        var guest = HostLeavesTest.watched(2, sessions.get(1));
        HostLeavesTest.stepUntil(List.of(host, guest), 150, System.nanoTime() + 30_000_000_000L);
        ends[1].close();
        long giveUp = System.nanoTime() + 20_000_000_000L;
        while ((host.leftAt().isEmpty() || !sessions.get(1).isConnectionLost()) && System.nanoTime() < giveUp) {
            host.game().runHeadless(1);
            guest.game().runHeadless(1);
            Thread.sleep(1);
        }
        assertTrue(host.leftAt().containsKey(2), "the host told the guest has left");
        assertTrue(sessions.get(1).isConnectionLost(), "and the guest that it is cut off");
        sessions.forEach(MultiplayerSession::close);
    }
}
