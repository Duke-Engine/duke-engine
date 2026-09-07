package uz.duke.core.message;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Deque;
import uz.duke.core.SubsystemInterface;

/**
 * A FIFO queue of pending commands, ported from SAGE's {@code MessageStream}.
 *
 * <p>Commands are appended as they are issued (by input handling, the AI, or the
 * network) and drained once per frame, in arrival order, to a
 * {@link CommandHandler}. The strict FIFO order is what keeps command
 * application deterministic across peers — a hard requirement for lock-step.
 *
 * <p>SAGE's stream also runs an ordered chain of input translators; that
 * client-side concern is intentionally omitted here. This is the
 * simulation-facing command queue.
 */
public final class MessageStream extends SubsystemInterface {

    private final Deque<Command> queue = new ArrayDeque<>();

    @Override
    public void init() {
        queue.clear();
    }

    @Override
    public void reset() {
        queue.clear();
    }

    @Override
    public void update() {
        // Draining is driven explicitly by the logic via propagate(); nothing
        // to do on the generic per-frame tick.
    }

    /** Add a command to the end of the stream. */
    public void appendMessage(Command command) {
        queue.addLast(command);
    }

    public int size() {
        return queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /** Throw away everything queued, unapplied. */
    public void clear() {
        queue.clear();
    }

    /**
     * Everything queued, in the order it will be applied, without draining it —
     * for a recorder that needs to see a frame's input before the frame consumes it.
     */
    public List<Command> peekAll() {
        return List.copyOf(queue);
    }

    /**
     * Drain every queued command to {@code handler} in arrival order, leaving
     * the stream empty. Commands appended <em>by</em> the handler are not drained
     * in this pass; they wait for the next one.
     */
    public void propagate(CommandHandler handler) {
        int count = queue.size();
        for (int i = 0; i < count; i++) {
            handler.handle(queue.removeFirst());
        }
    }
}
