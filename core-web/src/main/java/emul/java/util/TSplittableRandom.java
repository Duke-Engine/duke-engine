package emul.java.util;

import java.util.random.RandomGenerator;

/**
 * The JDK's SplitMix64, written out step for step: a seeded roll gives the browser the number the desktop gets.
 * {@code TSplittableRandomTest} holds it to the JDK's.
 */
public final class TSplittableRandom implements RandomGenerator {

    private static final long GOLDEN_GAMMA = 0x9e3779b97f4a7c15L;
    private static long unseeded = mix64(System.currentTimeMillis()) ^ mix64(System.nanoTime());

    private long seed;
    private final long gamma;

    private TSplittableRandom(long seed, long gamma) {
        this.seed = seed;
        this.gamma = gamma;
    }

    public TSplittableRandom(long seed) {
        this(seed, GOLDEN_GAMMA);
    }

    public TSplittableRandom() {
        this(unseeded += 2 * GOLDEN_GAMMA);
    }

    public TSplittableRandom split() {
        return new TSplittableRandom(nextLong(), mixGamma(nextSeed()));
    }

    private long nextSeed() {
        return seed += gamma;
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    private static int mix32(long z) {
        z = (z ^ (z >>> 33)) * 0x62a9d9ed799705f5L;
        return (int) (((z ^ (z >>> 28)) * 0xcb24d0a5c88c35b3L) >>> 32);
    }

    private static long mixGamma(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        z = (z ^ (z >>> 33)) | 1L;
        int n = Long.bitCount(z ^ (z >>> 1));
        return (n < 24) ? z ^ 0xaaaaaaaaaaaaaaaaL : z;
    }

    @Override
    public int nextInt() {
        return mix32(nextSeed());
    }

    @Override
    public long nextLong() {
        return mix64(nextSeed());
    }

    @Override
    public boolean nextBoolean() {
        return nextInt() < 0;
    }

    @Override
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        int r = nextInt();
        int m = bound - 1;
        if ((bound & m) == 0) {
            r &= m;
        } else {
            for (int u = r >>> 1; u + m - (r = u % bound) < 0; u = nextInt() >>> 1) {
                // rejected: a roll past the last whole multiple of bound
            }
        }
        return r;
    }

    @Override
    public int nextInt(int origin, int bound) {
        if (origin >= bound) {
            throw new IllegalArgumentException("bound must be greater than origin");
        }
        int r = nextInt();
        int n = bound - origin;
        int m = n - 1;
        if ((n & m) == 0) {
            r = (r & m) + origin;
        } else if (n > 0) {
            for (int u = r >>> 1; u + m - (r = u % n) < 0; u = nextInt() >>> 1) {
                // rejected, as in nextInt(bound)
            }
            r += origin;
        } else {
            while (r < origin || r >= bound) {
                r = nextInt();
            }
        }
        return r;
    }

    @Override
    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    @Override
    public double nextDouble(double bound) {
        if (!(bound > 0 && bound < Double.POSITIVE_INFINITY)) {
            throw new IllegalArgumentException("bound must be finite and positive");
        }
        double r = nextDouble() * bound;
        return r < bound ? r : Math.nextDown(bound);
    }

    @Override
    public double nextDouble(double origin, double bound) {
        if (!(origin < bound && bound - origin < Double.POSITIVE_INFINITY)) {
            throw new IllegalArgumentException("bound must be greater than origin");
        }
        double r = nextDouble() * (bound - origin) + origin;
        return r < bound ? r : Math.nextAfter(bound, origin);
    }

    @Override
    public float nextFloat() {
        return (nextInt() >>> 8) * 0x1.0p-24f;
    }

    @Override
    public float nextFloat(float bound) {
        if (!(bound > 0 && bound < Float.POSITIVE_INFINITY)) {
            throw new IllegalArgumentException("bound must be finite and positive");
        }
        float r = nextFloat() * bound;
        return r < bound ? r : Math.nextDown(bound);
    }

    @Override
    public float nextFloat(float origin, float bound) {
        if (!(origin < bound && bound - origin < Float.POSITIVE_INFINITY)) {
            throw new IllegalArgumentException("bound must be greater than origin");
        }
        float r = nextFloat() * (bound - origin) + origin;
        return r < bound ? r : Math.nextAfter(bound, origin);
    }
}
