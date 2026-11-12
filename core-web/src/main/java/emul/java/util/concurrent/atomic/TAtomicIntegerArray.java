package emul.java.util.concurrent.atomic;

/** An int array; a browser's one thread at a time makes every access atomic. Gson's adapters name it. */
public class TAtomicIntegerArray {

    private final int[] values;

    public TAtomicIntegerArray(int length) {
        values = new int[length];
    }

    public TAtomicIntegerArray(int[] array) {
        values = array.clone();
    }

    public final int length() {
        return values.length;
    }

    public final int get(int i) {
        return values[i];
    }

    public final void set(int i, int value) {
        values[i] = value;
    }

    public final int getAndSet(int i, int value) {
        int old = values[i];
        values[i] = value;
        return old;
    }

    public final boolean compareAndSet(int i, int expected, int value) {
        if (values[i] != expected) {
            return false;
        }
        values[i] = value;
        return true;
    }

    public final int getAndAdd(int i, int delta) {
        int old = values[i];
        values[i] += delta;
        return old;
    }

    public final int addAndGet(int i, int delta) {
        return values[i] += delta;
    }

    public final int incrementAndGet(int i) {
        return ++values[i];
    }

    public final int decrementAndGet(int i) {
        return --values[i];
    }

    @Override
    public String toString() {
        return java.util.Arrays.toString(values);
    }
}
