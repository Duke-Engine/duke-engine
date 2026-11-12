package emul.java.util.concurrent;

import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** Only the part the engine uses: a value made on an executor, asked whether it is done, then joined. */
public class TCompletableFuture<T> {

    private boolean done;
    private T value;
    private Throwable failure;

    public static <U> TCompletableFuture<U> supplyAsync(Supplier<U> supplier, Executor executor) {
        var future = new TCompletableFuture<U>();
        executor.execute(() -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public synchronized boolean complete(T result) {
        if (done) {
            return false;
        }
        value = result;
        done = true;
        notifyAll();
        return true;
    }

    public synchronized boolean completeExceptionally(Throwable e) {
        if (done) {
            return false;
        }
        failure = e;
        done = true;
        notifyAll();
        return true;
    }

    public synchronized boolean isDone() {
        return done;
    }

    public synchronized T join() {
        while (!done) {
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        if (failure != null) {
            throw failure instanceof RuntimeException run ? run : new IllegalStateException(failure);
        }
        return value;
    }
}
