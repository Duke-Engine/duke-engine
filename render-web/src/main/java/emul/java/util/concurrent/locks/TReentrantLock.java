package emul.java.util.concurrent.locks;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/** A lock on TeaVM's green threads, kept on this object's monitor. */
public class TReentrantLock implements Lock {
    private Thread owner;
    private int holds;

    public synchronized void lock() {
        Thread me = Thread.currentThread();
        while (owner != null && owner != me) {
            try {
                wait();
            } catch (InterruptedException e) {
                me.interrupt();
            }
        }
        owner = me;
        holds++;
    }

    public synchronized boolean tryLock() {
        Thread me = Thread.currentThread();
        if (owner != null && owner != me) {
            return false;
        }
        owner = me;
        holds++;
        return true;
    }

    public synchronized void unlock() {
        if (owner != Thread.currentThread()) {
            throw new IllegalMonitorStateException();
        }
        if (--holds == 0) {
            owner = null;
            notifyAll();
        }
    }

    public void lockInterruptibly() {
        lock();
    }

    public boolean tryLock(long time, TimeUnit unit) {
        return tryLock();
    }

    public boolean isHeldByCurrentThread() {
        return owner == Thread.currentThread();
    }

    public Condition newCondition() {
        return new Cond(this);
    }

    // static with an Object parameter: TeaVM renames TReentrantLock in callers but not in a nested constructor.
    private static final class Cond implements Condition {
        private final TReentrantLock lock;

        Cond(Object lock) {
            this.lock = (TReentrantLock) lock;
        }

        private int signals;

        public void await() throws InterruptedException {
            await(0, TimeUnit.MILLISECONDS);
        }

        public boolean await(long time, TimeUnit unit) throws InterruptedException {
            long millis = unit.toMillis(time);
            int held;
            synchronized (lock) {
                held = lock.holds;
                lock.holds = 0;
                lock.owner = null;
                lock.notifyAll();
            }
            boolean signalled;
            synchronized (this) {
                int seen = signals;
                if (millis > 0) {
                    wait(millis);
                } else {
                    while (signals == seen) {
                        wait();
                    }
                }
                signalled = signals != seen;
            }
            lock.lock();
            synchronized (lock) {
                lock.holds = held;
            }
            return signalled;
        }

        public void awaitUninterruptibly() {
            try {
                await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        public long awaitNanos(long nanos) throws InterruptedException {
            return await(Math.max(1, nanos / 1_000_000), TimeUnit.MILLISECONDS) ? 1 : 0;
        }

        public boolean awaitUntil(java.util.Date deadline) throws InterruptedException {
            return await(Math.max(1, deadline.getTime() - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
        }

        public synchronized void signal() {
            signals++;
            notify();
        }

        public synchronized void signalAll() {
            signals++;
            notifyAll();
        }
    }
}
