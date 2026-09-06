package uz.duke.core.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.duke.core.math.Coord3D;
import uz.duke.core.thing.ObjectId;

class MessageStreamTest {

    @Test
    void propagatesInArrivalOrderThenEmpties() {
        var stream = new MessageStream();
        stream.init();
        stream.appendMessage(new GameMessage.StopMoving(1, List.of(new ObjectId(1))));
        stream.appendMessage(new GameMessage.MoveTo(1, List.of(new ObjectId(2)), new Coord3D(5, 0, 0)));
        stream.appendMessage(new GameMessage.AttackObject(2, List.of(new ObjectId(3)), new ObjectId(9)));
        assertEquals(3, stream.size());

        var received = new ArrayList<GameMessage>();
        stream.propagate(received::add);

        assertEquals(3, received.size());
        assertTrue(received.get(0) instanceof GameMessage.StopMoving);
        assertTrue(received.get(1) instanceof GameMessage.MoveTo);
        assertTrue(received.get(2) instanceof GameMessage.AttackObject);
        assertTrue(stream.isEmpty());
    }

    @Test
    void resetClearsPending() {
        var stream = new MessageStream();
        stream.init();
        stream.appendMessage(new GameMessage.StopMoving(1, List.of(new ObjectId(1))));
        stream.reset();
        assertTrue(stream.isEmpty());
    }

    @Test
    void messageArgumentsAreDefensivelyCopied() {
        var units = new ArrayList<ObjectId>();
        units.add(new ObjectId(1));
        var move = new GameMessage.MoveTo(1, units, new Coord3D(0, 0, 0));
        units.add(new ObjectId(2)); // mutate the source list after construction
        assertEquals(1, move.units().size());
    }
}
