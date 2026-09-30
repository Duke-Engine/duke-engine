package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.network.CommandPacket;
import uz.dukeengine.core.network.LineChannel;
import uz.dukeengine.core.network.NetMessage;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.combat.message.CombatOrder;

/** A relay that holds no seat: three guests through it, their leaving decided by it, and what it heard a replay. */
class MultiplayerRelayTest {

    private record Seated(MultiplayerRelay relay, List<MultiplayerSession> sessions, List<LineChannel> guestEnds,
            List<NetMessage> heard, Thread pumping) {

        void close() {
            pumping.interrupt();
            sessions.forEach(MultiplayerSession::close);
            relay.close();
        }
    }

    private static Seated seated(int players) throws Exception {
        var relay = new MultiplayerRelay(players, "");
        var heard = new CopyOnWriteArrayList<NetMessage>();
        relay.onHeard(heard::add);
        var sessions = new ConcurrentHashMap<Integer, MultiplayerSession>();
        var failed = new AtomicReference<Exception>();
        var ends = new ArrayList<LineChannel>();
        var joining = new ArrayList<Thread>();
        for (int i = 0; i < players; i++) {
            var pair = LineChannel.pair();
            ends.add(pair[1]);
            var join = new Thread(() -> {
                try {
                    var session = MultiplayerSession.join(pair[1]);
                    sessions.put(session.getLocalPlayerIndex(), session);
                } catch (Exception e) {
                    failed.set(e);
                }
            }, "relay-guest-" + i);
            join.start();
            joining.add(join);
            assertEquals(i + 1, relay.admit(pair[0]), "seats in turn, every one a guest's");
        }
        for (var join : joining) {
            join.join(10_000);
        }
        assertEquals(players, sessions.size(), () -> "a guest failed: " + failed.get());
        var pumping = new Thread(() -> {
            while (!relay.isOver() && !Thread.currentThread().isInterrupted()) {
                relay.pump();
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "relay");
        pumping.setDaemon(true);
        pumping.start();
        return new Seated(relay, List.copyOf(new TreeMap<>(sessions).values()), ends, heard, pumping);
    }

    @Test
    @Timeout(90)
    void threeGuestsThroughTheRelayPlay300FramesAlikeAndWhatItHeardReplaysToTheSameWorld() throws Exception {
        var seated = seated(3);
        var worlds = new ArrayList<HostLeavesTest.Watched>();
        for (var session : seated.sessions()) {
            worlds.add(HostLeavesTest.watched(3, session));
        }
        assertEquals(List.of(1, 2, 3), seated.sessions().stream().map(MultiplayerSession::getLocalPlayerIndex).toList(),
                "seats 1 to 3 are the guests'");
        HostLeavesTest.stepUntil(worlds, 40, System.nanoTime() + 30_000_000_000L);
        worlds.get(1).game().postCommand(new CombatOrder.MoveTo(2, List.of(new ObjectId(2)),
                new Coord3D(400f, 300f, 0f)));
        HostLeavesTest.stepUntil(worlds, 300, System.nanoTime() + 60_000_000_000L);
        for (int frame = 0; frame < 300; frame++) {
            var sum = worlds.get(0).sums().get(frame);
            assertNotNull(sum, "frame " + frame + " summed");
            assertEquals(sum, worlds.get(1).sums().get(frame), "frame " + frame);
            assertEquals(sum, worlds.get(2).sums().get(frame), "frame " + frame);
        }

        var replay = DukeGame.create("host-leaves").loadUnits(uz.dukeengine.rts.RtsFlavour.STARTER_UNITS);
        var replayed = new TreeMap<Integer, Long>();
        replay.onTick(g -> replayed.put(g.getLogic().getFrame(), g.getLogic().checksum()));
        for (int i = 1; i <= 3; i++) {
            replay.spawn("Rifleman", replay.addPlayer("P" + i, Color.BLUE), 200f * i, 0f);
        }
        replay.runHeadless(0);
        var byFrame = new TreeMap<Integer, TreeMap<Integer, CommandPacket>>();
        for (var message : seated.heard()) {
            if (message instanceof CommandPacket packet) {
                byFrame.computeIfAbsent(packet.frame(), f -> new TreeMap<>()).put(packet.playerIndex(), packet);
            }
        }
        assertTrue(byFrame.values().stream().flatMap(packets -> packets.values().stream())
                .anyMatch(packet -> !packet.commands().isEmpty()), "the move among what it heard");
        while (replay.getLogic().getFrame() < 300) {
            var packets = byFrame.get(replay.getLogic().getFrame());
            if (packets != null) {
                packets.values().forEach(packet -> packet.commands().forEach(replay.getLogic()::issueCommand));
            }
            replay.runHeadless(1);
        }
        for (int frame = 0; frame < 300; frame++) {
            assertEquals(worlds.get(0).sums().get(frame), replayed.get(frame), "replayed, frame " + frame);
        }
        seated.close();
    }

    @Test
    @Timeout(90)
    void aGuestsChannelClosedAtFrame150IsToldGoneToEveryOtherOnOneFrame() throws Exception {
        var seated = seated(3);
        var over = new AtomicReference<Boolean>(false);
        seated.relay().onOver(() -> over.set(true));
        var worlds = new ArrayList<HostLeavesTest.Watched>();
        for (var session : seated.sessions()) {
            worlds.add(HostLeavesTest.watched(3, session));
        }
        HostLeavesTest.stepUntil(worlds, 150, System.nanoTime() + 40_000_000_000L);
        seated.guestEnds().get(2).close();
        var staying = worlds.subList(0, 2);
        long giveUp = System.nanoTime() + 30_000_000_000L;
        while (staying.stream().anyMatch(world -> !world.leftAt().containsKey(3)) && System.nanoTime() < giveUp) {
            staying.forEach(world -> world.game().runHeadless(1));
            Thread.sleep(1);
        }
        assertNotNull(staying.get(0).leftAt().get(3), "told seat 3 is gone");
        assertEquals(staying.get(0).leftAt().get(3), staying.get(1).leftAt().get(3), "on the same frame");
        seated.guestEnds().get(0).close();
        seated.guestEnds().get(1).close();
        long end = System.nanoTime() + 10_000_000_000L;
        while (!over.get() && System.nanoTime() < end) {
            Thread.sleep(5);
        }
        assertTrue(over.get(), "every seat gone: the game is over for the relay");
        seated.close();
    }
}
