package uz.dukeengine.client3d;

import com.jme3.app.DetailedProfiler;
import com.jme3.profile.AppStep;
import com.jme3.profile.SpStep;
import com.jme3.profile.VpStep;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Where a frame's time goes on the card as well as on the processor: jME's {@link DetailedProfiler}, which times each
 * step of the frame (each view, bucket and filter) with a GL timer query and reads it back frames later, logged every
 * {@code duke.stats} seconds as the costliest steps and the frame's whole card time. A frame that takes
 * {@link #SLOW_NANOS} or more from one start to the next is logged too, its processor time by step: what a hitch was.
 * Off unless {@code duke.gpu} is true too: the queries cost a little themselves.
 */
final class GpuStats extends DetailedProfiler {

    private static final Logger LOG = Logger.getLogger(GpuStats.class.getName());
    /** How many of the costliest steps each line names. */
    private static final int SHOWN = 14;
    /** A frame this long, start to start, is a hitch: its steps are logged. */
    static final long SLOW_NANOS = 25_000_000L;
    /** How many of a hitch's steps are named. */
    private static final int HITCH_SHOWN = 6;

    private final long periodNanos;
    private long since = System.nanoTime();
    /** This frame's processor time so far by step, and the step under way and since when. */
    private final Map<String, Long> spent = new HashMap<>();
    private String step = "BeginFrame";
    private long stepFrom = System.nanoTime();
    private long frameFrom = -1;

    private GpuStats(long periodNanos) {
        this.periodNanos = periodNanos;
    }

    /** The profiler asked for by {@code duke.gpu} and {@code duke.stats}, or null (the properties are read now). */
    static GpuStats asked() {
        int seconds = Integer.getInteger("duke.stats", 0);
        return seconds > 0 && Boolean.getBoolean("duke.gpu") ? new GpuStats(seconds * 1_000_000_000L) : null;
    }

    @Override
    public void appStep(AppStep appStep) {
        long now = System.nanoTime();
        if (appStep == AppStep.BeginFrame) {
            // What came after the last frame's end (the swap, the wait for the card) is that frame's too.
            mark("AfterEndFrame", now);
            if (frameFrom >= 0 && now - frameFrom >= SLOW_NANOS) {
                LOG.info(hitch(now - frameFrom, spent));
            }
            spent.clear();
            frameFrom = now;
        } else {
            mark(appStep.name(), now);
        }
        super.appStep(appStep);
        // At the frame's end its steps are all in: read before the next frame begins and clears them.
        if (appStep == AppStep.EndFrame && now - since >= periodNanos && getStats() != null) {
            since = now;
            LOG.info(line(getStats()));
        }
    }

    @Override
    public void appSubStep(String... additionalInfo) {
        mark(step.split(":")[0] + ":" + String.join("/", additionalInfo), System.nanoTime());
        super.appSubStep(additionalInfo);
    }

    @Override
    public void vpStep(VpStep vpStep, ViewPort vp, RenderQueue.Bucket bucket) {
        mark(vp.getName() + "/" + (bucket == null ? vpStep.name() : bucket.name()), System.nanoTime());
        super.vpStep(vpStep, vp, bucket);
    }

    @Override
    public void spStep(SpStep spStep, String... additionalInfo) {
        mark(String.join("/", additionalInfo), System.nanoTime());
        super.spStep(spStep, additionalInfo);
    }

    /** The step under way ends {@code now}, its time counted, and {@code next} starts. */
    private void mark(String next, long now) {
        spent.merge(step, now - stepFrom, Long::sum);
        step = next;
        stepFrom = now;
    }

    /** {@code stats hitch: 41.2 ms | Render 30.10 | ...}: a slow frame's costliest steps on the processor, in ms. */
    static String hitch(long frameNanos, Map<String, Long> spent) {
        var steps = new ArrayList<>(spent.entrySet());
        steps.sort(Map.Entry.<String, Long>comparingByValue().reversed());
        var out = new StringBuilder(String.format("stats hitch: %.1f ms", frameNanos / 1e6));
        for (int i = 0; i < Math.min(HITCH_SHOWN, steps.size()); i++) {
            out.append(String.format(" | %s %.2f", steps.get(i).getKey(), steps.get(i).getValue() / 1e6));
        }
        return out.toString();
    }

    /** {@code stats gpu: frame 9.80 ms | Render/Main/Opaque 3.20/0.40 (gpu/cpu ms) | ...}, costliest on the card first. */
    static String line(Map<String, StatLine> stats) {
        double gpu = 0;
        var steps = new ArrayList<Map.Entry<String, StatLine>>(stats.entrySet());
        for (var s : steps) {
            // The steps follow one another, each query stopped as the next starts: their sum is the frame's.
            gpu += s.getValue().getAverageGpu();
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
