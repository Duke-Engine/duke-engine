package uz.dukeengine.core.math;

/**
 * The simulation's own random numbers — SAGE's {@code GameLogicRandomValue}, kept apart from anything a client
 * draws, so that whatever the world decides by chance it decides the same way on every machine.
 *
 * <p><b>Why its own and not {@code java.util.Random}.</b> Two things a lock-step world needs and that class does
 * not give. Its state cannot be read back, so a saved game could not carry on drawing where it stopped; and it
 * is shared across threads by design, when a draw anywhere but the simulation thread is exactly the mistake to
 * make impossible to overlook. This is SplitMix64: one {@code long} of state, integer arithmetic only, so the
 * same seed gives the same sequence on every JVM, and the state is one number a save can write down.
 *
 * <p>Draw only from the simulation thread, and only for what the simulation decides — a weapon's delay between
 * shots, a spread, a chance. Anything a player merely sees or hears (sparks, a sound's take) draws from the
 * client's own, which never touches this one: a spark that consumed a number here would move every later
 * draw on the machine that drew it, and nowhere else.
 */
public final class LogicRandom {

    private long state;

    public LogicRandom(long seed) {
        this.state = seed;
    }

    /** Where the sequence stands, for a save to write down. */
    public long state() {
        return state;
    }

    /** Carry on from a state {@link #state} gave — a loaded game drawing what the saved one would have. */
    public void restore(long saved) {
        this.state = saved;
    }

    /** The next 64 bits. */
    public long nextLong() {
        long z = state += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * A whole number from {@code min} to {@code max}, both included — the reference's own form, since what it
     * draws is a range written in data: a delay of 3 to 5 frames may be 5. A range written backwards is read
     * the right way round, and one of a single value draws nothing.
     */
    public int nextInt(int min, int max) {
        int low = Math.min(min, max);
        int high = Math.max(min, max);
        if (low == high) {
            return low; // nothing to decide, so nothing drawn: the sequence is left for what is
        }
        long span = (long) high - low + 1;
        return (int) (low + Long.remainderUnsigned(nextLong(), span));
    }

    /** A number from 0 up to, but not including, 1. */
    public float nextFloat() {
        return (nextLong() >>> 40) * 0x1.0p-24f;
    }
}
