package uz.duke.core.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.duke.core.TestCommand;
import uz.duke.core.math.Coord3D;
import uz.duke.core.thing.ObjectId;

class MessageStreamTest {

    @Test
    void propagatesInArrivalOrderThenEmpties() {
        var stream = new MessageStream();
        stream.init();
        stream.appendMessage(new TestCommand.Halt(1, List.of(new ObjectId(1))));
        stream.appendMessage(new TestCommand.Move(1, List.of(new ObjectId(2)), new Coord3D(5, 0, 0)));
        stream.appendMessage(new TestCommand.Ping(2, "hello"));
        assertEquals(3, stream.size());

        var received = new ArrayList<Command>();
        stream.propagate(received::add);

        assertEquals(3, received.size());
        assertTrue(received.get(0) instanceof TestCommand.Halt);
        assertTrue(received.get(1) instanceof TestCommand.Move);
        assertTrue(received.get(2) instanceof TestCommand.Ping);
        assertTrue(stream.isEmpty());
    }

    @Test
    void resetClearsPending() {
        var stream = new MessageStream();
        stream.init();
        stream.appendMessage(new TestCommand.Halt(1, List.of(new ObjectId(1))));
        stream.reset();
        assertTrue(stream.isEmpty());
    }

    @Test
    void commandArgumentsAreDefensivelyCopied() {
        var units = new ArrayList<ObjectId>();
        units.add(new ObjectId(1));
        var move = new TestCommand.Move(1, units, new Coord3D(0, 0, 0));
        units.add(new ObjectId(2)); // mutate the source list after construction
        assertEquals(1, move.units().size());
    }
}
