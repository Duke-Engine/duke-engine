package emul.java.util.concurrent;

public interface TThreadFactory {
    Thread newThread(Runnable task);
}
