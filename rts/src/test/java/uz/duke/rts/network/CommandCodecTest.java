package uz.duke.rts.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.duke.core.math.Coord3D;
import uz.duke.core.network.CommandPacket;
import uz.duke.core.thing.ObjectId;
import uz.duke.rts.message.GameMessage;

class CommandCodecTest {

    private static void assertRoundTrips(CommandPacket packet) {
        var decoded = CommandCodec.INSTANCE.decode(CommandCodec.INSTANCE.encode(packet));
        assertEquals(packet, decoded);
    }

    @Test
    void emptyPacketRoundTrips() {
        assertRoundTrips(new CommandPacket(7, 2, List.of()));
    }

    @Test
    void moveCommandRoundTrips() {
        assertRoundTrips(new CommandPacket(10, 1, List.of(
                new GameMessage.MoveTo(1, List.of(new ObjectId(3), new ObjectId(5)), new Coord3D(12.5f, -3.25f, 0f)))));
    }

    @Test
    void attackAndStopRoundTrip() {
        assertRoundTrips(new CommandPacket(4, 2, List.of(
                new GameMessage.AttackObject(2, List.of(new ObjectId(9)), new ObjectId(1)),
                new GameMessage.StopMoving(2, List.of(new ObjectId(9), new ObjectId(10))))));
    }

    @Test
    void floatBitsArePreserved() {
        float awkward = 0.1f + 0.2f; // not exactly representable
        var packet = new CommandPacket(1, 1, List.of(
                new GameMessage.MoveTo(1, List.of(new ObjectId(1)), new Coord3D(awkward, 0f, 0f))));
        var decoded = (GameMessage.MoveTo) CommandCodec.INSTANCE.decode(CommandCodec.INSTANCE.encode(packet)).commands().get(0);
        assertEquals(Float.floatToIntBits(awkward), Float.floatToIntBits(decoded.destination().x()));
    }
}
