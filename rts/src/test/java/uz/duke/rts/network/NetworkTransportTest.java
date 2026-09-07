package uz.duke.rts.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import uz.duke.core.math.Coord3D;
import uz.duke.core.network.CommandPacket;
import uz.duke.core.network.FrameChecksum;
import uz.duke.core.network.HostTransport;
import uz.duke.core.network.LoopbackTransport;
import uz.duke.core.network.NetMessage;
import uz.duke.core.network.PeerLeft;
import uz.duke.core.network.SocketTransport;
import uz.duke.core.thing.ObjectId;
import uz.duke.rts.message.GameMessage;

class NetworkTransportTest {

    private static CommandPacket packetFrom(int player) {
        return new CommandPacket(3, player, List.of(
                new GameMessage.MoveTo(player, List.of(new ObjectId(1)), new Coord3D(9f, 0f, 0f))));
    }

    /** Spin the pump until {@code check} holds, or give up after five seconds. */
    private static void pumpUntil(java.util.function.BooleanSupplier check, Runnable... pumps) {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (!check.getAsBoolean() && System.nanoTime() < deadline) {
            for (var pump : pumps) {
                pump.run();
            }
            Thread.onSpinWait();
        }
    }

    @Test
    void loopbackFansOutToAllSubscribers() {
        var transport = new LoopbackTransport();
        var a = new ArrayList<NetMessage>();
        var b = new ArrayList<NetMessage>();
        transport.subscribe(a::add);
        transport.subscribe(b::add);

        var packet = packetFrom(1);
        transport.send(packet);

        assertEquals(List.of(packet), a);
        assertEquals(List.of(packet), b);
    }

    @Test
    @Timeout(15)
    void messagesTravelOverTcp() throws Exception {
        try (var server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            var accepted = new java.util.concurrent.atomic.AtomicReference<SocketTransport>();
            var acceptThread = new Thread(() -> {
                try {
                    accepted.set(SocketTransport.accept(server, CommandCodec.INSTANCE));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            acceptThread.start();

            try (var client = SocketTransport.connect("localhost", port, CommandCodec.INSTANCE)) {
                acceptThread.join(5000);
                var far = accepted.get();
                assertNotNull(far);
                try {
                    var received = new CopyOnWriteArrayList<NetMessage>();
                    far.subscribe(received::add);
                    var localEcho = new CopyOnWriteArrayList<NetMessage>();
                    client.subscribe(localEcho::add);

                    var packet = packetFrom(1);
                    client.send(packet);
                    assertEquals(1, localEcho.size(), "the sender hears its own message immediately");

                    pumpUntil(() -> !received.isEmpty(), far::pump);
                    assertEquals(List.of(packet), received);

                    // Control messages ride the same connection as commands.
                    client.send(new PeerLeft(2, 90));
                    pumpUntil(() -> received.size() > 1, far::pump);
                    assertEquals(new PeerLeft(2, 90), received.get(1));

                    // Checksums carry a full long, negatives included.
                    var sum = new FrameChecksum(120, 3, -5512490980485146533L);
                    client.send(sum);
                    pumpUntil(() -> received.size() > 2, far::pump);
                    assertEquals(sum, received.get(2));
                } finally {
                    far.close();
                }
            }
        }
    }

    @Test
    @Timeout(20)
    void theHostPassesEveryGuestsMessagesOnToTheOthers() throws Exception {
        try (var server = new ServerSocket(0);
                var host = new HostTransport(CommandCodec.INSTANCE)) {
            int port = server.getLocalPort();
            var two = connectGuest(server, host, port, 2);
            var three = connectGuest(server, host, port, 3);
            try (two.transport; three.transport) {
                var atHost = new CopyOnWriteArrayList<NetMessage>();
                var atTwo = new CopyOnWriteArrayList<NetMessage>();
                var atThree = new CopyOnWriteArrayList<NetMessage>();
                host.subscribe(atHost::add);
                two.transport.subscribe(atTwo::add);
                three.transport.subscribe(atThree::add);

                var fromTwo = packetFrom(2);
                two.transport.send(fromTwo);

                pumpUntil(() -> !atThree.isEmpty(),
                        host::pump, two.transport::pump, three.transport::pump);

                assertEquals(List.of(fromTwo), atHost, "the host hears it");
                assertEquals(List.of(fromTwo), atThree, "and passes it on to the other guest");
                assertEquals(List.of(fromTwo), atTwo, "the sender heard its own echo");
            }
        }
    }

    @Test
    @Timeout(20)
    void aGuestsLastMessageArrivesBeforeNewsOfItsDeparture() throws Exception {
        try (var server = new ServerSocket(0);
                var host = new HostTransport(CommandCodec.INSTANCE)) {
            int port = server.getLocalPort();
            var guest = connectGuest(server, host, port, 2);

            var order = new CopyOnWriteArrayList<String>();
            host.subscribe(message -> order.add("message"));
            host.onLinkLost(index -> order.add("lost:" + index));

            guest.transport.send(packetFrom(2));
            guest.transport.close(); // ragequit, right after acting

            pumpUntil(() -> order.contains("lost:2"), host::pump);

            assertEquals(List.of("message", "lost:2"), order,
                    "a disconnection must never overtake the packets that preceded it");
            assertEquals(0, host.getGuestCount());
        }
    }

    private record Guest(SocketTransport transport) {
    }

    /** Connect one guest and hand its socket to the host, as the lobby would. */
    private static Guest connectGuest(ServerSocket server, HostTransport host, int port, int index)
            throws Exception {
        var accepted = new java.util.concurrent.atomic.AtomicReference<Socket>();
        var counted = new AtomicInteger();
        var acceptThread = new Thread(() -> {
            try {
                accepted.set(server.accept());
                counted.incrementAndGet();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        acceptThread.start();
        var guest = SocketTransport.connect("localhost", port, CommandCodec.INSTANCE);
        acceptThread.join(5000);
        assertTrue(counted.get() == 1, "the host should have accepted the guest");
        host.addGuest(index, accepted.get());
        return new Guest(guest);
    }
}
