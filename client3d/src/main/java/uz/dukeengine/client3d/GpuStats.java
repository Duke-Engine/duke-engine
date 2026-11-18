package uz.dukeengine.client3d;

import com.jme3.app.DetailedProfiler;
import com.jme3.profile.AppStep;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Where a frame's time goes on the card as well as on the processor: jME's {@link DetailedProfiler}, which times each
 * step of the frame (each view, bucket and filter) with a GL timer query and reads it back frames later, logged every
 * {@code duke.stats} seconds as the costliest steps and the frame's whole card time. Off unless {@code duke.gpu} is
 * true too: the queries cost a little themselves.
 */
final class GpuStats extends DetailedProfiler {

    private static final Logger LOG = Logger.getLogger(GpuStats.class.getName());
    /** How many of the costliest steps each line names. */
    private static final int SHOWN = 14;

    private final long periodNanos;
    private long since = System.nanoTime();

    private GpuStats(long periodNanos) {
        this.periodNanos = periodNanos;
    }

    /** The profiler asked for by {@code duke.gpu} and {@code duke.stats}, or null (the properties are read now). */
    static GpuStats asked() {
        int seconds = Integer.getInteger("duke.stats", 0);
        return seconds > 0 && Boolean.getBoolean("duke.gpu") ? new GpuStats(seconds * 1_000_000_000L) : null;
    }

    @Override
    public void appStep(AppStep step) {
        super.appStep(step);
        long now = System.nanoTime();
        // At the frame's end its steps are all in: read before the next frame begins and clears them.
        if (step == AppStep.EndFrame && now - since >= periodNanos && getStats() != null) {
            since = now;
            LOG.info(line(getStats()));
        }
    }

    /** {@code stats gpu: frame 9.80 ms | Render/Main/Opaque 3.20/0.40 (gpu/cpu ms) | ...}, costliest on the card first. */
    static String line(Map<String, StatLine> stats) {
        double gpu = 0;
        var steps = new ArrayList<Map.Entry<String, StatLine>>(stats.entrySet());
        for (var step : steps) {
            // The steps follow one another, each query stopped as the next starts: their sum is the frame's.
            gpu += step.getValue().getAverageGpu();
        }
        steps.sort(Comparator.comparingDouble((Map.Entry<String, StatLine> s) -> s.getValue().getAverageGpu()).reversed());
        var out = new StringBuilder(String.format("stats gpu: frame %.2f ms", gpu / 1e6));
        for (int i = 0; i < Math.min(SHOWN, steps.size()); i++) {
            var s = steps.get(i);
            out.append(String.format(" | %s %.2f/%.2f", s.getKey(), s.getValue().getAverageGpu() / 1e6,
                    s.getValue().getAverageCpu() / 1e6));
        }
        return out.toString();
    }
}
