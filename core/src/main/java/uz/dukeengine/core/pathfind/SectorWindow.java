package uz.dukeengine.core.pathfind;

import java.util.Arrays;

/**
 * A stretch's arrays: as many cells as its sectors hold, each sector given its place for the stretch — whatever the
 * world's size — and made fresh for each stretch by a stamp.
 */
final class SectorWindow {

    private static final ThreadLocal<SectorWindow> WINDOW = ThreadLocal.withInitial(SectorWindow::new);

    private int[] slotOf = new int[0];
    private int[] slotStamp = new int[0];
    private int[] g = new int[0];
    private int[] came = new int[0];
    private int[] written = new int[0];
    private int[] shut = new int[0];
    private int stamp;
    private int size;
    private int across;
    private boolean busy;
    final Sectors.LongHeap open = new Sectors.LongHeap();

    /** This thread's window over the sectors {@code kept}, fresh; one of its own for a stretch inside another's. */
    static SectorWindow over(Sectors sectors, int[] kept) {
        var window = WINDOW.get();
        if (window.busy) {
            window = new SectorWindow();
        }
        window.busy = true;
        window.open.clear();
        int count = sectors.across() * sectors.down();
        int area = sectors.size() * sectors.size();
        if (window.slotOf.length < count) {
            window.slotOf = new int[count];
            window.slotStamp = new int[count];
        }
        if (window.g.length < kept.length * area) {
            int cells = kept.length * area;
            window.g = new int[cells];
            window.came = new int[cells];
            window.written = new int[cells];
            window.shut = new int[cells];
            Arrays.fill(window.slotStamp, 0);
            window.stamp = 0;
        }
        if (window.stamp == Integer.MAX_VALUE) {
            Arrays.fill(window.written, 0);
            Arrays.fill(window.shut, 0);
            Arrays.fill(window.slotStamp, 0);
            window.stamp = 0;
        }
        window.stamp++;
        window.size = sectors.size();
        window.across = sectors.across();
        for (int slot = 0; slot < kept.length; slot++) {
            window.slotOf[kept[slot]] = slot;
            window.slotStamp[kept[slot]] = window.stamp;
        }
        return window;
    }

    void release() {
        busy = false;
    }

    /** The cell's place in the arrays, or -1 where its sector is not the stretch's. */
    int at(int cx, int cy) {
        int sector = (cy / size) * across + cx / size;
        if (slotStamp[sector] != stamp) {
            return -1;
        }
        return slotOf[sector] * size * size + (cy % size) * size + cx % size;
    }

    int g(int at) {
        return written[at] == stamp ? g[at] : Integer.MAX_VALUE;
    }

    int came(int at) {
        return written[at] == stamp ? came[at] : -1;
    }

    void set(int at, int cost, int from) {
        written[at] = stamp;
        g[at] = cost;
        came[at] = from;
    }

    boolean closed(int at) {
        return shut[at] == stamp;
    }

    void close(int at) {
        shut[at] = stamp;
    }
}
