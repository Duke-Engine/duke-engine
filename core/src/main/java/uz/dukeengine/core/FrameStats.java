package uz.dukeengine.core;

import java.util.logging.Logger;

/**
 * A periodic log line of how long something took: {@code stats logic: 150 x in 5.0 s (30.0/s), avg 1.20 ms, max 5.30 ms}.
 * Off unless the system property {@code duke.stats} is the number of seconds between lines. Read from one thread only.
 */
public final class FrameStats {

    private static final Logger LOG = Logger.getLogger(FrameStats.class.getName());

    private final String name;
    private final long periodNanos;
    private long since = System.nanoTime();
    private long count;
    private long total;
    private long max;

    private FrameStats(String name, long periodNanos) {
        this.name = name;
        this.periodNanos = periodNanos;
    }

    /** The counter for {@code name}, or null while {@code duke.stats} is not set (the property is read now). */
    public static FrameStats of(String name) {
        int seconds = Integer.getInteger("duke.stats", 0);
        return seconds > 0 ? new FrameStats(name, seconds * 1_000_000_000L) : null;
    }

    /** One more {@code nanos} long; and the line, once the period has passed. */
    public void add(long nanos) {
        count++;
        total += nanos;
        max = Math.max(max, nanos);
        long now = System.nanoTime();
        if (now - since >= periodNanos) {
            double seconds = (now - since) / 1e9;
            LOG.info(String.format("stats %s: %d x in %.1f s (%.1f/s), avg %.2f ms, max %.2f ms", name, count, seconds,
                    count / seconds, total / 1e6 / count, max / 1e6));
            since = now;
            count = 0;
            total = 0;
            max = 0;
        }
    }
}
