package emul.java.util.concurrent;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public interface TExecutorService extends Executor {
    void shutdown();

    List<Runnable> shutdownNow();

    boolean isShutdown();

    boolean isTerminated();

    boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException;
}
