package emul.java.util.concurrent;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
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

    public static ExecutorService newFixedThreadPool(int threads, TThreadFactory factory) {
        return new AtOnce();
    }

    public static ExecutorService newSingleThreadExecutor(TThreadFactory factory) {
        return new AtOnce();
    }

    /**
     * Names the JDK's {@code ExecutorService}, not {@link TExecutorService}: a nested class is not stood in, so its
     * references are not mapped, and the game's calls reach the JDK name. What it cannot do (futures) it refuses.
     */
    private static final class AtOnce implements ExecutorService {
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

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            throw new UnsupportedOperationException("no futures in a browser's pool");
        }

        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            throw new UnsupportedOperationException("no futures in a browser's pool");
        }

        @Override
        public Future<?> submit(Runnable task) {
            throw new UnsupportedOperationException("no futures in a browser's pool");
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
            throw new UnsupportedOperationException("no futures in a browser's pool");
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException("no futures in a browser's pool");
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks) {
            throw new UnsupportedOperationException("no futures in a browser's pool");
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException("no futures in a browser's pool");
        }
    }
}
