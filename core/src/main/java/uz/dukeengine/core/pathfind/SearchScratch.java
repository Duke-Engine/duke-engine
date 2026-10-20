package uz.dukeengine.core.pathfind;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * A search's arrays, kept on its thread from one search to the next rather than made for each — five arrays the
 * size of the map a search, and many searches a frame — and made fresh for each search by a stamp rather than by
 * clearing them: a cell whose stamp is not this search's has no score and no way there, and is not closed. On a map
 * larger than {@link #denseCells} they hold only the cells the search has touched, found by the cell: the same
 * answers to every question, and as much memory as the search needed rather than five times the world's cells.
 */
final class SearchScratch {

    private static final ThreadLocal<SearchScratch> SCRATCH = ThreadLocal.withInitial(SearchScratch::new);

    /** The most cells a search's arrays are made the size of the map for — {@link #denseUpTo} for a test. */
    private static int denseCells = 1 << 21;

    private int[] g = new int[0];
    private int[] f = new int[0];
    private int[] came = new int[0];
    private int[] written = new int[0];
    private int[] shut = new int[0];
    private int stamp;
    private boolean busy;
    /** On a large map: each touched cell's place in the arrays, by an open table; null for arrays by the cell. */
    private int[] table;
    private int[] cellOf = new int[0];
    private int used;
    final PriorityQueue<Integer> open = new PriorityQueue<>((a, b) -> {
        int byF = Integer.compare(fOf(a), fOf(b));
        return byF != 0 ? byF : Integer.compare(a, b); // deterministic tie-break
    });

    /**
     * Hold a search's arrays by the cell up to {@code cells} cells, and past them only for the cells it touches: for a
     * test to hold the two alike on a small grid. Returns what it was.
     */
    static int denseUpTo(int cells) {
        int was = denseCells;
        denseCells = cells;
        return was;
    }

    /** This thread's scratch, fresh and room for {@code cells}; arrays of its own for a search inside another's. */
    static SearchScratch take(int cells) {
        var scratch = SCRATCH.get();
        if (scratch.busy) {
            scratch = new SearchScratch();
        }
        scratch.busy = true;
        scratch.open.clear();
        if (cells > denseCells) {
            scratch.sparse();
            return scratch;
        }
        if (scratch.table != null) {
            // The arrays held a large map's cells by their place in the table: nothing in them is a cell's.
            scratch.table = null;
            Arrays.fill(scratch.written, 0);
            Arrays.fill(scratch.shut, 0);
            scratch.stamp = 0;
        }
        if (scratch.g.length < cells) {
            scratch.g = new int[cells];
            scratch.f = new int[cells];
            scratch.came = new int[cells];
            scratch.written = new int[cells];
            scratch.shut = new int[cells];
            scratch.stamp = 0;
        }
        if (scratch.stamp == Integer.MAX_VALUE) {
            Arrays.fill(scratch.written, 0);
            Arrays.fill(scratch.shut, 0);
            scratch.stamp = 0;
        }
        scratch.stamp++;
        return scratch;
    }

    /** Arrays for the cells this search touches alone: emptied, and kept as large as the last search's. */
    private void sparse() {
        if (table == null || table.length < 1024) {
            table = new int[1024];
        } else {
            Arrays.fill(table, 0);
        }
        used = 0;
        stamp = 1;
    }

    void release() {
        busy = false;
    }

    /** Where the cell's scores are kept: the cell itself, or its place in the table — -1 for none and not made. */
    private int at(int cell, boolean make) {
        if (table == null) {
            return cell;
        }
        int mask = table.length - 1;
        int bucket = (cell * 0x9E3779B1) >>> 7 & mask;
        while (true) {
            int slot = table[bucket] - 1;
            if (slot < 0) {
                return make ? place(cell, bucket) : -1;
            }
            if (cellOf[slot] == cell) {
                return slot;
            }
            bucket = (bucket + 1) & mask;
        }
    }

    private int place(int cell, int bucket) {
        int slot = used++;
        if (slot == cellOf.length) {
            int grown = Math.max(1024, slot * 2);
            cellOf = Arrays.copyOf(cellOf, grown);
            g = Arrays.copyOf(g, grown);
            f = Arrays.copyOf(f, grown);
            came = Arrays.copyOf(came, grown);
            written = Arrays.copyOf(written, grown);
            shut = Arrays.copyOf(shut, grown);
        }
        cellOf[slot] = cell;
        written[slot] = 0;
        shut[slot] = 0;
        table[bucket] = slot + 1;
        if (used * 2 > table.length) {
            var old = table;
            table = new int[old.length * 2];
            int mask = table.length - 1;
            for (int entry : old) {
                if (entry == 0) {
                    continue;
                }
                int b = (cellOf[entry - 1] * 0x9E3779B1) >>> 7 & mask;
                while (table[b] != 0) {
                    b = (b + 1) & mask;
                }
                table[b] = entry;
            }
        }
        return slot;
    }

    private int fOf(int cell) {
        int at = at(cell, false);
        return at < 0 ? Integer.MAX_VALUE : f[at];
    }

    int g(int cell) {
        int at = at(cell, false);
        return at >= 0 && written[at] == stamp ? g[at] : Integer.MAX_VALUE;
    }

    int came(int cell) {
        int at = at(cell, false);
        return at >= 0 && written[at] == stamp ? came[at] : -1;
    }

    void set(int cell, int cost, int estimate, int from) {
        int at = at(cell, true);
        written[at] = stamp;
        g[at] = cost;
        f[at] = estimate;
        came[at] = from;
    }

    boolean closed(int cell) {
        int at = at(cell, false);
        return at >= 0 && shut[at] == stamp;
    }

    void close(int cell) {
        shut[at(cell, true)] = stamp;
    }

    /** The cells of the way the search came to {@code end} by, after {@code start}, in order. */
    List<Integer> cellsTo(int end, int start) {
        var cells = new ArrayList<Integer>();
        for (int cell = end; cell != -1 && cell != start; cell = came(cell)) {
            cells.add(cell);
        }
        Collections.reverse(cells);
        return cells;
    }
}
