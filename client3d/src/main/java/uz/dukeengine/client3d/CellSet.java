package uz.dukeengine.client3d;

import java.util.Arrays;
import java.util.BitSet;

/**
 * Cells named once each, walked in the order they were first named and forgotten a cell at a time: what changed this
 * frame costs what changed, where a set of bits over the whole map is walked and cleared over the whole map, a world of
 * sixteen million cells a quarter of a million words each time. Its own words, not a {@link BitSet}'s, which looks down
 * through every word below whenever its highest one empties.
 */
final class CellSet {

    private long[] words = new long[0];
    private int[] cells = new int[64];
    private int count;

    /** Name {@code cell}; whether it was not named yet. */
    boolean add(int cell) {
        int word = cell >>> 6;
        if (word >= words.length) {
            words = Arrays.copyOf(words, Math.max(word + 1, words.length * 2));
        }
        long bit = 1L << cell;
        if ((words[word] & bit) != 0) {
            return false;
        }
        words[word] |= bit;
        if (count == cells.length) {
            cells = Arrays.copyOf(cells, count * 2);
        }
        cells[count++] = cell;
        return true;
    }

    boolean contains(int cell) {
        int word = cell >>> 6;
        return word < words.length && (words[word] & 1L << cell) != 0;
    }

    int size() {
        return count;
    }

    boolean isEmpty() {
        return count == 0;
    }

    /** The {@code i}th cell named. */
    int get(int i) {
        return cells[i];
    }

    /** Forget every cell, each on its own. */
    void clear() {
        for (int i = 0; i < count; i++) {
            words[cells[i] >>> 6] = 0L;
        }
        count = 0;
    }

    /** The cells as bits, indexed by cell: a copy, for a question asked now and then. */
    BitSet bits() {
        return BitSet.valueOf(words);
    }
}
