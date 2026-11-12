package emul.java.util.concurrent;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Pools that run each task at once, on the caller's thread.
 *
 * <p>shortcut: a browser has one real thread, so a pool buys no speed, only a frame that waits once for the work
 * (reading art, baking a level, painting a sky); give it green threads if a long task must not stall a frame.
 */
public final class TExecutors {

    private TExecutors() {
    }

    public static TExecutorService newFixedThreadPool(int threads, TThreadFactory factory) {
        return new AtOnce();
    }

    public static TExecutorService newSingleThreadExecutor(TThreadFactory factory) {
        return new AtOnce();
    }

    private static final class AtOnce implements TExecutorService {
        private boolean shut;

        @Override
        public void execute(Runnable task) {
            if (shut) {
                throw new IllegalStateException("shut down");
            }
            task.run();
        }

        @Override
        public void shutdown() {
            shut = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shut = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shut;
        }

        @Override
        public boolean isTerminated() {
            return shut;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }
}
