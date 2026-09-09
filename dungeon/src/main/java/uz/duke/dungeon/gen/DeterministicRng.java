package uz.duke.dungeon.gen;

/**
 * A tiny seeded random source — the one the dungeon is built from.
 *
 * <p>Generation has to be a pure function of a seed: the same seed must draw the
 * same dungeon on every machine and every run, or the "same seed = same dungeon"
 * promise (and the tests that rest on it) fall apart. So this deliberately does
 * <em>not</em> use {@link java.util.Random} (whose contract is fixed but whose
 * role here would be easy to swap by accident), and above all never touches
 * {@code Math.random} or the wall clock.
 *
 * <p>The algorithm is xorshift64: three shifts and XORs, integer only, no
 * floating point and no trigonometry, so there is nothing a platform could round
 * differently. A zero seed would stick at zero, so it is nudged to a non-zero
 * constant.
 */
final class DeterministicRng {

    private long state;

    DeterministicRng(long seed) {
        this.state = seed == 0L ? 0x9E3779B97F4A7C15L : seed;
    }

    long nextLong() {
        long x = state;
        x ^= x << 13;
        x ^= x >>> 7;
        x ^= x << 17;
        state = x;
        return x;
    }

    /** A value in {@code [0, bound)}. */
    int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive: " + bound);
        }
        return (int) Math.floorMod(nextLong(), (long) bound);
    }

    /** A value in {@code [origin, boundInclusive]}. */
    int nextInt(int origin, int boundInclusive) {
        return origin + nextInt(boundInclusive - origin + 1);
    }

    /** Whether the next draw comes up heads. */
    boolean nextBoolean() {
        return (nextLong() & 1L) != 0L;
    }

    /** The seed one link along the chain — how one run picks the next run's dungeon. */
    static long advance(long seed) {
        return new DeterministicRng(seed).nextLong();
    }
}
