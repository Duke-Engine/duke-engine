package uz.duke.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.duke.core.TestCommand;
import uz.duke.core.math.Coord3D;
import uz.duke.core.message.Command;
import uz.duke.core.thing.ObjectId;

class LockstepSchedulerTest {

    private LockstepScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new LockstepScheduler(List.of(1, 2));
        scheduler.init();
    }

    private static Command move(int player, int unit) {
        return new TestCommand.Move(player, List.of(new ObjectId(unit)), new Coord3D(0, 0, 0));
    }

    @Test
    void frameNotReadyUntilAllPlayersSubmit() {
        assertFalse(scheduler.isFrameReady(5));
        scheduler.submit(5, 1, List.of(move(1, 10)));
        assertFalse(scheduler.isFrameReady(5)); // still waiting on player 2
        scheduler.submit(5, 2, List.of());
        assertTrue(scheduler.isFrameReady(5));
    }

    @Test
    void emptySubmissionCountsAsReported() {
        scheduler.submit(3, 1, List.of());
        scheduler.submit(3, 2, List.of());
        assertTrue(scheduler.isFrameReady(3));
        assertTrue(scheduler.takeCommands(3).isEmpty());
    }

    @Test
    void commandsDrainInAscendingPlayerOrder() {
        // Submit player 2 first, then player 1; draining must still be 1 before 2.
        scheduler.submit(7, 2, List.of(move(2, 20)));
        scheduler.submit(7, 1, List.of(move(1, 10)));

        var drained = scheduler.takeCommands(7);
        assertEquals(2, drained.size());
        assertEquals(1, drained.get(0).playerIndex());
        assertEquals(2, drained.get(1).playerIndex());
    }

    @Test
    void takeRemovesTheFrame() {
        scheduler.submit(1, 1, List.of());
        scheduler.submit(1, 2, List.of());
        scheduler.takeCommands(1);
        assertFalse(scheduler.isFrameReady(1)); // consumed
    }

    @Test
    void unknownPlayerRejected() {
        assertThrows(IllegalArgumentException.class, () -> scheduler.submit(1, 99, List.of()));
    }

    @Test
    void framesAreIndependent() {
        scheduler.submit(1, 1, List.of());
        scheduler.submit(2, 1, List.of());
        scheduler.submit(2, 2, List.of());
        assertFalse(scheduler.isFrameReady(1)); // player 2 missing for frame 1
        assertTrue(scheduler.isFrameReady(2));
    }
}
