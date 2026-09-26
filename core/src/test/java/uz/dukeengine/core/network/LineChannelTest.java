package uz.dukeengine.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A channel of lines, and a lock-step's link over one: its end told as a socket's is, after its last message. */
class LineChannelTest {

    @Test
    void aPairCarriesLinesInOrderAndItsEndOnceEitherEndCloses() {
        var ends = LineChannel.pair();
        ends[0].send("one");
        ends[0].send("two");
        ends[0].close();
        assertEquals("one", ends[1].receive());
        assertEquals("two", ends[1].receive());
        assertNull(ends[1].receive(), "then its end");
        assertFalse(ends[1].send("late"), "closed for both");
    }

    @Test
    void aLinkOverAClosedChannelIsLostAfterItsLastMessage() throws Exception {
        var ends = LineChannel.pair();
        var codec = new PacketCodec() {
            @Override
            public String encode(CommandPacket packet) {
                return "";
            }

            @Override
            public CommandPacket decode(String line) {
                return null;
            }
        };
        var link = SocketTransport.over(ends[1], codec, 1);
        var heard = new ArrayList<NetMessage>();
        var lost = new ArrayList<Integer>();
        link.subscribe(heard::add);
        link.onLinkLost(lost::add);
        ends[0].send(NetFraming.encode(new ChatLine(1, List.of(2), "hello"), codec));
        ends[0].close();
        long giveUp = System.nanoTime() + 5_000_000_000L;
        while (lost.isEmpty() && System.nanoTime() < giveUp) {
            link.pump();
            Thread.sleep(1);
        }
        assertEquals(1, heard.size(), "its last message first");
        assertEquals(List.of(1), lost, "then the lost link, once");
    }
}
