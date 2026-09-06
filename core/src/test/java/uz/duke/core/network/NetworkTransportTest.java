package uz.duke.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import uz.duke.core.math.Coord3D;
import uz.duke.core.message.GameMessage;
import uz.duke.core.thing.ObjectId;

class NetworkTransportTest {

    private static CommandPacket samplePacket() {
        return new CommandPacket(3, 1, List.of(
                new GameMessage.MoveTo(1, List.of(new ObjectId(1)), new Coord3D(9f, 0f, 0f))));
    }

    @Test
    void loopbackFansOutToAllSubscribers() {
        var transport = new LoopbackTransport();
        var a = new ArrayList<CommandPacket>();
        var b = new ArrayList<CommandPacket>();
        transport.subscribe(a::add);
        transport.subscribe(b::add);

        var packet = samplePacket();
        transport.broadcast(packet);

        assertEquals(List.of(packet), a);
        assertEquals(List.of(packet), b);
    }

    @Test
    @Timeout(15)
    void packetsTravelOverTcp() throws Exception {
        try (var server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            var hostHolder = new AtomicReference<SocketTransport>();
            var acceptThread = new Thread(() -> {
                try {
                    hostHolder.set(SocketTransport.accept(server));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            acceptThread.start();

            try (var client = SocketTransport.connect("localhost", port)) {
                acceptThread.join(5000);
                var host = hostHolder.get();
                assertNotNull(host);
                try {
                    var received = new CopyOnWriteArrayList<CommandPacket>();
                    host.subscribe(received::add);
                    var localEcho = new CopyOnWriteArrayList<CommandPacket>();
                    client.subscribe(localEcho::add);

                    var packet = samplePacket();
                    client.broadcast(packet);

                    // The sender hears its own packet immediately (local echo).
                    assertEquals(1, localEcho.size());

                    // The peer receives it over the wire; drain on this thread via pump().
                    long deadline = System.nanoTime() + 5_000_000_000L;
                    while (received.isEmpty() && System.nanoTime() < deadline) {
                        host.pump();
                        Thread.onSpinWait();
                    }
                    assertTrue(received.size() == 1, "host should receive the packet over TCP");
                    assertEquals(packet, received.get(0));
                } finally {
                    host.close();
                }
            }
        }
    }
}
