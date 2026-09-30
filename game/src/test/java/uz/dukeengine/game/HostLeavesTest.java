package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.combat.message.CombatOrder;

/** The host's machine gone mid-match, and the others playing on: the next player relays, and no frame is lost. */
class HostLeavesTest {

    private static final int THREE_PORT = 17811;
    private static final int TWO_PORT = 17812;
    private static final int CHOSEN_HOST_PORT = 17813;
    private static final int CHOSEN_GUEST_PORT = 17823;
    private static final int HOST_GOES_AT = 60;
    private static final int PLAYED_AFTER = 1800;

    /** One world: its sum every frame, and the frame each player was told gone on. */
    record Watched(DukeGame game, Map<Integer, Long> sums, Map<Integer, Integer> leftAt) {
        int frame() {
            return game.getLogic().getFrame();
        }
    }

    static Watched watched(int players, MultiplayerSession session) {
        var game = DukeGame.create("host-leaves").loadUnits(uz.dukeengine.rts.RtsFlavour.STARTER_UNITS);
        for (int i = 1; i <= players; i++) {
            game.spawn("Rifleman", game.addPlayer("P" + i, Color.BLUE), 200f * i, 0f); // unit i is player i's
        }
        var sums = new TreeMap<Integer, Long>();
        var leftAt = new ConcurrentHashMap<Integer, Integer>();
        game.onTick(g -> sums.put(g.getLogic().getFrame(), g.getLogic().checksum()));
        game.onPlayerLeft((g, who) -> leftAt.put(who.getIndex(), g.getLogic().getFrame()));
        game.multiplayer(session).runHeadless(0); // booted
        return new Watched(game, sums, leftAt);
    }

    static List<MultiplayerSession> sessions(int port, int players) throws Exception {
        var hosted = new AtomicReference<MultiplayerSession>();
        var failed = new AtomicReference<Exception>();
        var hosting = new Thread(() -> {
            try (var server = new ServerSocket(port)) {
                hosted.set(MultiplayerSession.host(server, players, "", null));
            } catch (Exception e) {
                failed.set(e);
            }
        }, "test-host");
        hosting.start();
        Thread.sleep(200);
        var guests = new ConcurrentHashMap<Integer, MultiplayerSession>();
        var joining = new ArrayList<Thread>();
        for (int i = 0; i < players - 1; i++) {
            var join = new Thread(() -> {
                try {
                    var session = MultiplayerSession.join("127.0.0.1", port);
                    guests.put(session.getLocalPlayerIndex(), session);
                } catch (Exception e) {
                    failed.set(e);
                }
            }, "test-join-" + i);
            join.start();
            joining.add(join);
        }
        for (var join : joining) {
            join.join(10_000);
        }
        hosting.join(10_000);
        assertNotNull(hosted.get(), () -> "the host failed: " + failed.get());
        assertEquals(players - 1, guests.size(), () -> "a guest failed: " + failed.get());
        var all = new ArrayList<MultiplayerSession>();
        all.add(hosted.get());
        new TreeMap<>(guests).values().forEach(all::add);
        return all;
    }

    /** Every world stepped until each is past {@code frame}, or the time is up. */
    static void stepUntil(List<Watched> worlds, int frame, long giveUp) throws InterruptedException {
        while (worlds.stream().anyMatch(w -> w.frame() < frame) && System.nanoTime() < giveUp) {
            int wasAt = worlds.stream().mapToInt(Watched::frame).sum();
            for (var world : worlds) {
                if (world.frame() < frame) {
                    world.game().runHeadless(1);
                }
            }
            if (worlds.stream().mapToInt(Watched::frame).sum() == wasAt) {
                Thread.sleep(1); // held for a peer, or finding the new relay
            }
        }
    }

    /**
     * The next in the seats joined listening on a port of the test's choosing, as a player opens one port in his
     * firewall: it listens there, and after the host has gone the third player reaches it there and both play on.
     */
    @Test
    @Timeout(60)
    void aGuestListeningOnAChosenPortIsReachedThereAfterTheHostHasGone() throws Exception {
        var hosted = new AtomicReference<MultiplayerSession>();
        var second = new AtomicReference<MultiplayerSession>();
        var third = new AtomicReference<MultiplayerSession>();
        var failed = new AtomicReference<Exception>();
        var hosting = new Thread(() -> {
            try (var server = new ServerSocket(CHOSEN_HOST_PORT)) {
                hosted.set(MultiplayerSession.host(server, 3, "", null));
            } catch (Exception e) {
                failed.set(e);
            }
        }, "test-host");
        hosting.start();
        Thread.sleep(200);
        var first = new Thread(() -> {
            try {
                second.set(MultiplayerSession.join("127.0.0.1", CHOSEN_HOST_PORT, java.time.Duration.ofSeconds(10),
                        CHOSEN_GUEST_PORT));
            } catch (Exception e) {
                failed.set(e);
            }
        }, "test-join-chosen");
        first.start();
        Thread.sleep(300); // in first: the next in the seats
        third.set(MultiplayerSession.join("127.0.0.1", CHOSEN_HOST_PORT));
        first.join(10_000);
        hosting.join(10_000);
        assertNotNull(second.get(), () -> "the chosen guest failed: " + failed.get());
        assertEquals(2, second.get().getLocalPlayerIndex(), "the next in the seats");
        org.junit.jupiter.api.Assertions.assertThrows(java.net.BindException.class,
                () -> new ServerSocket(CHOSEN_GUEST_PORT).close(), "it listens on the port chosen");

        var host = watched(3, hosted.get());
        var two = watched(3, second.get());
        var three = watched(3, third.get());
        long giveUp = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(45);
        stepUntil(List.of(host, two, three), HOST_GOES_AT, giveUp);
        hosted.get().close();
        int hostWasAt = host.frame();
        stepUntil(List.of(two, three), hostWasAt + 300, giveUp);

        assertTrue(two.frame() >= hostWasAt + 300 && three.frame() >= hostWasAt + 300,
                () -> "the match stopped: " + two.frame() + ", " + three.frame());
        assertFalse(second.get().isConnectionLost() || third.get().isConnectionLost(), "reached it there");
        second.get().close();
        third.get().close();
    }

    @Test
    @Timeout(90)
    void threePlayersTheNextInTheSeatsRelaysAndBothPlayOnInTheSameWorld() throws Exception {
        var sessions = sessions(THREE_PORT, 3);
        var host = watched(3, sessions.get(0));
        var two = watched(3, sessions.get(1));
        var three = watched(3, sessions.get(2));
        long giveUp = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(75);

        stepUntil(List.of(host, two, three), HOST_GOES_AT, giveUp);
        sessions.get(0).close(); // the host's machine gone; its world is never stepped again
        int hostWasAt = host.frame();

        var guests = List.of(two, three);
        stepUntil(guests, hostWasAt + 30, giveUp);
        two.game().postCommand(new CombatOrder.MoveTo(2, List.of(new ObjectId(2)), new Coord3D(400f, 150f, 0f)));
        three.game().postCommand(new CombatOrder.MoveTo(3, List.of(new ObjectId(3)), new Coord3D(600f, 150f, 0f)));
        stepUntil(guests, hostWasAt + PLAYED_AFTER, giveUp);

        assertTrue(guests.stream().allMatch(w -> w.frame() >= hostWasAt + PLAYED_AFTER),
                () -> "the match stopped: " + guests.stream().map(Watched::frame).toList());
        assertFalse(sessions.get(1).isConnectionLost());
        assertFalse(sessions.get(2).isConnectionLost());
        assertTrue(two.sums().size() >= hostWasAt + PLAYED_AFTER, "every frame summed: " + two.sums().size());
        for (int frame = 1; frame < hostWasAt + PLAYED_AFTER; frame++) {
            assertEquals(two.sums().get(frame), three.sums().get(frame), "the worlds parted at frame " + frame);
        }
        assertNotNull(two.leftAt().get(1), "told the host left");
        assertEquals(two.leftAt(), three.leftAt(), "on the same frame on both");
        assertTrue(two.leftAt().get(1) > hostWasAt - 3, "and not before any frame some peer ran with its orders");

        for (int unit = 2; unit <= 3; unit++) {
            var inTwo = two.game().getLogic().findObject(new ObjectId(unit)).getPosition();
            var inThree = three.game().getLogic().findObject(new ObjectId(unit)).getPosition();
            assertTrue(inTwo.y() > 100f, "player " + unit + "'s order, given after the host left, was carried out");
            assertEquals(inTwo, inThree);
        }
        sessions.forEach(MultiplayerSession::close);
    }

    @Test
    @Timeout(60)
    void twoPlayersTheGuestPlaysOnAloneAndIsToldTheHostLeft() throws Exception {
        var sessions = sessions(TWO_PORT, 2);
        var host = watched(2, sessions.get(0));
        var guest = watched(2, sessions.get(1));
        long giveUp = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(45);

        stepUntil(List.of(host, guest), HOST_GOES_AT, giveUp);
        sessions.get(0).close();
        stepUntil(List.of(guest), host.frame() + 300, giveUp);

        assertTrue(guest.frame() >= host.frame() + 300, "the guest's match goes on: frame " + guest.frame());
        assertNotNull(guest.leftAt().get(1), "and it is told the host left");
        sessions.get(1).close();
    }
}
