package uz.dukeengine.rts.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.network.CommandPacket;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.rts.message.GameMessage;

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
    void aMoveThatIsThePlayersClickSaysSo() {
        assertRoundTrips(new CommandPacket(10, 1, List.of(
                new GameMessage.MoveTo(1, List.of(new ObjectId(3), new ObjectId(5)), new Coord3D(12.5f, -3.25f, 0f),
                        true))));
    }

    @Test
    void attackAndStopRoundTrip() {
        assertRoundTrips(new CommandPacket(4, 2, List.of(
                new GameMessage.AttackObject(2, List.of(new ObjectId(9)), new ObjectId(1)),
                new GameMessage.StopMoving(2, List.of(new ObjectId(9), new ObjectId(10))))));
    }

    @Test
    void anAttackSaysItsSourceAndTheSlotItLocksTo() {
        assertRoundTrips(new CommandPacket(4, 2, List.of(
                new GameMessage.AttackObject(2, List.of(new ObjectId(9)), new ObjectId(1), false,
                        uz.dukeengine.rts.message.OrderSource.GAME, -1),
                new GameMessage.AttackObject(2, List.of(new ObjectId(9)), new ObjectId(1), true,
                        uz.dukeengine.rts.message.OrderSource.PLAYER, 1))));
    }

    @Test
    void researchAndCallingOffAQueuedThingRoundTrip() {
        assertRoundTrips(new CommandPacket(12, 1, List.of(
                new GameMessage.QueueResearch(1, new ObjectId(4), "Upgrade_Armour"),
                new GameMessage.CancelProduction(1, new ObjectId(4), 2))));
    }

    @Test
    void theBarsEverydayOrdersRoundTrip() {
        assertRoundTrips(new CommandPacket(20, 1, List.of(
                new GameMessage.Sell(1, new ObjectId(7)),
                new GameMessage.AttackMove(1, List.of(new ObjectId(2), new ObjectId(3)), new Coord3D(40f, 60.5f, 0f)),
                new GameMessage.Guard(1, List.of(new ObjectId(2)), new Coord3D(10f, 20f, 0f), null,
                        GameMessage.Guard.Mode.WITHOUT_PURSUIT),
                new GameMessage.Guard(1, List.of(new ObjectId(3)), null, new ObjectId(9),
                        GameMessage.Guard.Mode.FLYING_ONLY),
                new GameMessage.Evacuate(1, new ObjectId(11)),
                new GameMessage.ExitContainer(1, new ObjectId(12)))));
    }

    @Test
    void aGamesOwnOrderRoundTripsWhateverItsWordHolds() {
        assertRoundTrips(new CommandPacket(30, 2, List.of(
                new GameMessage.GameOrder(2, "SPECIAL_POWER:Nuke|at,here;now дўст", List.of(new ObjectId(4)),
                        new Coord3D(120.5f, 80f, 0f), null, 7L),
                new GameMessage.GameOrder(2, "PURCHASE_SCIENCE", List.of(), null, null, Long.MIN_VALUE),
                new GameMessage.GameOrder(2, "", List.of(new ObjectId(1), new ObjectId(2)), null, new ObjectId(9),
                        -1L),
                new GameMessage.MoveTo(2, List.of(new ObjectId(1)), new Coord3D(5f, 6f, 0f)))));
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
