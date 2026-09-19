package uz.duke.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import uz.duke.core.GameLogic;
import uz.duke.core.TestCommand;
import uz.duke.core.math.Coord3D;
import uz.duke.core.message.Command;
import uz.duke.core.module.ActiveBody;
import uz.duke.core.module.ModuleFactory;
import uz.duke.core.module.MoveUpdate;
import uz.duke.core.thing.ObjectId;
import uz.duke.core.thing.ThingFactory;
import uz.duke.core.thing.ThingTemplate;

/**
 * Several peers, one world: they exchange nothing but commands, and stay
 * bit-identical — including when one of them walks out.
 */
class LockstepGateTest {

    private static final ThingTemplate RUNNER = ThingTemplate.named("Runner")
            .module(new ActiveBody.Data(100f))
            .module(new MoveUpdate.Data(12f))
            .build();

    /** One player's simulation, applying the commands the gate hands it. */
    static final class PeerLogic extends GameLogic {

        /** Set to make this peer quietly miss the next order it is given. */
        boolean dropNextCommand;

        PeerLogic() {
            super(newFactory());
        }

        private static ThingFactory newFactory() {
            var factory = new ThingFactory(ModuleFactory.withDefaults());
            factory.addTemplate(RUNNER);
            return factory;
        }

        @Override
        protected void onCommand(Command command) {
            if (dropNextCommand) {
                dropNextCommand = false;
                return;
            }
            if (command instanceof TestCommand.Move move) {
                for (var id : move.units()) {
                    var unit = findObject(id);
                    if (unit != null) {
                        unit.findModule(MoveUpdate.class).moveTo(move.destination());
                    }
                }
            }
        }

        @Override
        protected void simulate() {
        }
    }

    /**
     * A stand-in for the network: every peer is wired to it, and a peer can be
     * cut off to model a machine that has gone away.
     */
    static final class Switchboard {
        private final List<Consumer<NetMessage>> listeners = new ArrayList<>();
        private final List<IntConsumer> hostLossListeners = new ArrayList<>();

        Transport portFor(int player) {
            return new Transport() {
                @Override
                public void send(NetMessage message) {
                    for (var listener : List.copyOf(listeners)) {
                        listener.accept(message);
                    }
                }

                @Override
                public void subscribe(Consumer<NetMessage> listener) {
                    listeners.add(listener);
                }

                @Override
                public void onLinkLost(IntConsumer listener) {
                    if (player == 1) {
                        hostLossListeners.add(listener); // only the host is told about guests
                    }
                }
            };
        }

        /** The named peer's machine has gone; the host's link to it drops. */
        void cut(int player) {
            for (var listener : hostLossListeners) {
                listener.accept(player);
            }
        }
    }

    /** One peer: its own world, its own gate, its own view of everyone's input. */
    private static final class Peer {
        final int index;
        final PeerLogic logic = new PeerLogic();
        final LockstepGate gate;
        final List<Integer> departed = new ArrayList<>();
        final List<Desync> desyncs = new ArrayList<>();

        /** What this peer computed the world to be, frame by frame. */
        final java.util.Map<Integer, Long> checksums = new java.util.HashMap<>();

        Peer(int index, int playerCount, Switchboard board) {
            this.index = index;
            logic.init();
            var scheduler = new LockstepScheduler(IntStream.rangeClosed(1, playerCount).boxed().toList());
            scheduler.init();
            this.gate = new LockstepGate(scheduler, board.portFor(index), index, 3, index == 1);
            this.gate.onPlayerLeft(departed::add);
            this.gate.onDesync(desyncs::add);
            // Every peer simulates the whole world, not just its own corner of it:
            // unit N belongs to player N, and exists identically on every machine.
            for (int player = 1; player <= playerCount; player++) {
                logic.spawn(RUNNER, new Coord3D(10f * player, 10f, 0f), player);
            }
        }

        /** One turn of the engine loop: step if the gate allows it. */
        boolean step() {
            if (!gate.beforeStep(logic)) {
                return false;
            }
            logic.update();
            checksums.put(logic.getFrame(), logic.checksum());
            return true;
        }
    }

    private static List<Peer> peers(int count, Switchboard board) {
        var peers = new ArrayList<Peer>();
        for (int index = 1; index <= count; index++) {
            peers.add(new Peer(index, count, board));
        }
        return peers;
    }

    /** Advance everyone one turn, in a fixed order. Some may stall; that is the point. */
    private static void stepAll(List<Peer> peers) {
        for (var peer : peers) {
            peer.step();
        }
    }

    /**
     * Compare frame by frame rather than turn by turn.
     *
     * <p>Peers do not run in step with each other in wall-clock time — one stalls
     * while another runs ahead by a frame. What lock-step promises is narrower and
     * stronger: whenever two peers have simulated <em>the same frame</em>, they
     * computed the same world.
     */
    private static void assertIdentical(List<Peer> peers, String when) {
        var reference = peers.get(0);
        int compared = 0;
        for (var entry : reference.checksums.entrySet()) {
            for (var peer : peers) {
                var theirs = peer.checksums.get(entry.getKey());
                if (theirs == null) {
                    continue; // they have not reached that frame yet
                }
                assertEquals(entry.getValue(), theirs,
                        "peer " + peer.index + " computed a different world for frame "
                                + entry.getKey() + " " + when);
                compared++;
            }
        }
        assertTrue(compared > 0, "nothing was actually compared " + when);
    }

    @Test
    void threePeersStayBitIdentical() {
        var board = new Switchboard();
        var peers = peers(3, board);

        for (int turn = 0; turn < 120; turn++) {
            if (turn % 17 == 0) {
                // Each peer orders its own unit somewhere different.
                for (var peer : peers) {
                    peer.gate.issueLocal(new TestCommand.Move(peer.index,
                            List.of(new ObjectId(peer.index)), new Coord3D(200f + turn, 40f * peer.index, 0f)));
                }
            }
            stepAll(peers);
        }
        assertIdentical(peers, "during a three-player game");

        for (var peer : peers) {
            assertTrue(peer.logic.getFrame() > 100,
                    "peer " + peer.index + " barely moved: frame " + peer.logic.getFrame());
        }
    }

    @Test
    void peersThatAgreeNeverCryDesyncAndKeepRunning() {
        var board = new Switchboard();
        var peers = peers(3, board);

        for (int turn = 0; turn < 150; turn++) {
            stepAll(peers);
        }

        assertTrue(peers.get(0).logic.getFrame() > LockstepGate.CHECKSUM_INTERVAL * 3,
                "the game must run long enough to have been checked several times");
        for (var peer : peers) {
            assertNull(peer.gate.getDesync(), "peer " + peer.index + " cried wolf");
            assertTrue(peer.desyncs.isEmpty());
            assertEquals(SessionState.RUNNING, peer.gate.getState());
        }
    }

    @Test
    void aPeerRunningADifferentWorldIsCaught() {
        var board = new Switchboard();
        var peers = peers(3, board);
        // Peer 2's world quietly gains something nobody else has. This is the shape
        // every real desync takes: the same commands, a different world.
        peers.get(1).logic.spawn(RUNNER, new Coord3D(999f, 999f, 0f), 2);

        for (int turn = 0; turn < 150; turn++) {
            stepAll(peers);
        }

        for (var peer : peers) {
            var desync = peer.gate.getDesync();
            assertNotNull(desync, "peer " + peer.index + " never noticed it was alone");
            assertEquals(0, desync.frame() % LockstepGate.CHECKSUM_INTERVAL,
                    "divergence is reported on a checked frame");
            assertTrue(desync.localChecksum() != desync.otherChecksum(),
                    "a report must actually name two different worlds");
            assertEquals(peer.index, desync.localPlayer());
        }
    }

    @Test
    void aDesyncIsReportedOnceAndTheGameStops() {
        var board = new Switchboard();
        var peers = peers(2, board);
        peers.get(1).logic.spawn(RUNNER, new Coord3D(999f, 999f, 0f), 2);

        for (int turn = 0; turn < 300; turn++) {
            stepAll(peers);
        }

        for (var peer : peers) {
            assertEquals(SessionState.DESYNCED, peer.gate.getState());
            assertEquals(1, peer.desyncs.size(),
                    "divergence compounds, so repeating it says nothing new");
        }
    }

    @Test
    void oneMissedCommandEndsTheGameForEveryone() {
        var board = new Switchboard();
        var peers = peers(2, board);
        // Player 2's machine quietly loses one order. Everything else about it is
        // right, which is precisely what makes this the hard case: nothing is
        // obviously broken, the two worlds simply stop being the same.
        peers.get(1).logic.dropNextCommand = true;
        peers.get(0).gate.issueLocal(new TestCommand.Move(1,
                List.of(new ObjectId(1)), new Coord3D(400f, 200f, 0f)));

        for (int turn = 0; turn < 200; turn++) {
            stepAll(peers);
        }

        var stoppedAt = new java.util.HashMap<Integer, Integer>();
        for (var peer : peers) {
            assertEquals(SessionState.DESYNCED, peer.gate.getState(),
                    "peer " + peer.index + " should have stopped");
            assertNotNull(peer.gate.getDesync());
            assertEquals(LockstepGate.CHECKSUM_INTERVAL, peer.gate.getDesync().frame(),
                    "caught at the first check after the worlds parted");
            stoppedAt.put(peer.index, peer.logic.getFrame());
        }

        // Peers can come to rest a frame apart: a disagreement can only be noticed
        // once the other side's hash has arrived, by which time one of them may
        // have taken one more step. That is harmless — the game is over either
        // way — so what matters is that neither of them takes another.
        for (int turn = 0; turn < 100; turn++) {
            stepAll(peers);
        }
        for (var peer : peers) {
            assertEquals(stoppedAt.get(peer.index), peer.logic.getFrame(),
                    "a stopped game must not creep forward");
            assertFalse(peer.gate.beforeStep(peer.logic));
        }
    }

    @Test
    void aPeerThatSawNothingWrongStopsBecauseTheHostSaysSo() {
        var scheduler = new LockstepScheduler(List.of(1, 2));
        scheduler.init();
        var listeners = new ArrayList<Consumer<NetMessage>>();
        var transport = new Transport() {
            @Override
            public void send(NetMessage message) {
            }

            @Override
            public void subscribe(Consumer<NetMessage> listener) {
                listeners.add(listener);
            }
        };
        var logic = new PeerLogic();
        logic.init();
        var gate = new LockstepGate(scheduler, transport, 2, 3, false);
        var reported = new ArrayList<Desync>();
        gate.onDesync(reported::add);

        assertEquals(SessionState.RUNNING, gate.getState());
        listeners.forEach(l -> l.accept(new SessionHalted(90, 3, 111L, 222L)));

        assertEquals(SessionState.DESYNCED, gate.getState(),
                "a guest cannot check every peer itself; the host's word is enough");
        assertFalse(gate.beforeStep(logic));
        assertEquals(1, reported.size(), "and the player is told, so the screen is not just frozen");
        assertEquals(90, reported.get(0).frame());
    }

    @Test
    void theOthersPlayOnWhenSomeoneWalksOut() {
        var board = new Switchboard();
        var peers = peers(3, board);
        for (int turn = 0; turn < 20; turn++) {
            stepAll(peers);
        }
        int frameAtDeparture = peers.get(0).logic.getFrame();

        // Player 3's machine is gone. It stops stepping; the host notices the link.
        var remaining = List.of(peers.get(0), peers.get(1));
        board.cut(3);

        for (int turn = 0; turn < 60; turn++) {
            stepAll(remaining);
        }

        assertTrue(peers.get(0).logic.getFrame() > frameAtDeparture + 20,
                "the game must carry on rather than stall for a player who will never answer");
        assertIdentical(remaining, "after a player left");
        for (var peer : remaining) {
            assertEquals(List.of(3), peer.departed, "both were told, once, who left");
        }
    }

    @Test
    void everyoneDropsThePlayerOnTheSameFrame() {
        var board = new Switchboard();
        var peers = peers(3, board);
        for (int turn = 0; turn < 20; turn++) {
            stepAll(peers);
        }

        board.cut(3);
        var remaining = List.of(peers.get(0), peers.get(1));
        for (int turn = 0; turn < 60; turn++) {
            stepAll(remaining);
        }
        assertIdentical(remaining, "after the drop");
    }

    @Test
    void aGuestThatLosesTheHostStopsRatherThanDrifting() {
        var board = new Switchboard();
        var guestScheduler = new LockstepScheduler(List.of(1, 2));
        guestScheduler.init();
        var lossListeners = new ArrayList<IntConsumer>();
        var transport = new Transport() {
            @Override
            public void send(NetMessage message) {
            }

            @Override
            public void subscribe(Consumer<NetMessage> listener) {
            }

            @Override
            public void onLinkLost(IntConsumer listener) {
                lossListeners.add(listener);
            }
        };
        var logic = new PeerLogic();
        logic.init();
        var gate = new LockstepGate(guestScheduler, transport, 2, 3, false);

        assertFalse(gate.isConnectionLost());
        lossListeners.forEach(listener -> listener.accept(1)); // the host vanished
        gate.beforeStep(logic);

        assertTrue(gate.isConnectionLost(),
                "a guest cannot decide the game's membership, so it must say it is cut off");
        assertFalse(gate.beforeStep(logic), "and it must never step again");
    }
}
