package emul.java.util.concurrent;

/** A latch on TeaVM's monitors, which park a green thread. */
public class TCountDownLatch {

    private long count;

    public TCountDownLatch(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count < 0");
        }
        this.count = count;
    }

    public synchronized void await() throws InterruptedException {
        while (count > 0) {
            wait();
        }
    }

    public synchronized void countDown() {
        if (count > 0 && --count == 0) {
            notifyAll();
        }
    }

    public synchronized long getCount() {
        return count;
    }
}
