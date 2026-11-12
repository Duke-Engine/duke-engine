package emul.java.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** The browser's SplittableRandom rolls what the JDK's does, seed for seed. */
class TSplittableRandomTest {

    @Test
    void everyRollIsTheJdks() {
        for (long seed : new long[] {0, 1, -7, 0x5DEECE66DL, Long.MIN_VALUE}) {
            var jdk = new SplittableRandom(seed);
            var ours = new TSplittableRandom(seed);
            for (int i = 0; i < 2000; i++) {
                assertEquals(jdk.nextInt(), ours.nextInt());
                assertEquals(jdk.nextLong(), ours.nextLong());
                assertEquals(jdk.nextInt(1 + i), ours.nextInt(1 + i));
                assertEquals(jdk.nextInt(-i, 3 * i + 1), ours.nextInt(-i, 3 * i + 1));
                assertEquals(jdk.nextDouble(), ours.nextDouble());
                assertEquals(jdk.nextDouble(2.5), ours.nextDouble(2.5));
                assertEquals(jdk.nextDouble(-1, 4), ours.nextDouble(-1, 4));
                assertEquals(jdk.nextFloat(), ours.nextFloat());
                assertEquals(jdk.nextFloat(3f), ours.nextFloat(3f));
                assertEquals(jdk.nextBoolean(), ours.nextBoolean());
            }
            var jdkSplit = jdk.split();
            var ourSplit = ours.split();
            for (int i = 0; i < 100; i++) {
                assertEquals(jdkSplit.nextLong(), ourSplit.nextLong());
            }
        }
    }
}
