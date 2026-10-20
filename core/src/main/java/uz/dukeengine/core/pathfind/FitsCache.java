package uz.dukeengine.core.pathfind;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * What a body fits at on a grid, kept for each passage and clearance asked — see {@link Fits} — and caught up with what
 * changed on the grid since each was last asked ({@link PathGrid#changesSince}).
 */
final class FitsCache {

    /**
     * What a body of one clearance fits at, cell by cell, for one passage — 0 not yet asked, 1 it fits, 2 it does not —
     * each cell worked out once and kept until the grid says the ground near it changed: a search on a fine grid asks
     * the same cells' circles again and again, and a cell's answer is a byte, not a scan.
     */
    static final class Fits {
        final long key;
        final float clearance;
        final byte[] cells;
        int serial;

        Fits(long key, float clearance, int cells, int serial) {
            this.key = key;
            this.clearance = clearance;
            this.cells = new byte[cells];
            this.serial = serial;
        }
    }

    private final Map<Long, Fits> byClearance = new HashMap<>();
    private Fits last;

    /**
     * The fits of a body of {@code clearance} on the passage of {@code root} that enters {@code surfaces}, caught up
     * with what changed since they were last asked; the one asked last kept at hand, as a search asks one clearance cell
     * after cell.
     */
    Fits of(PathGrid root, int surfaces, float clearance) {
        long key = (long) surfaces << 32 | (Float.floatToIntBits(clearance) & 0xFFFFFFFFL);
        var fits = last;
        if (fits == null || fits.key != key) {
            fits = byClearance.computeIfAbsent(key,
                    k -> new Fits(k, clearance, root.getWidth() * root.getHeight(), root.changeSerial()));
            last = fits;
        }
        if (fits.serial != root.changeSerial()) {
            forget(root, fits);
        }
        return fits;
    }

    /** What changed since {@code fits} were caught up forgotten, as far round each change as a body's fit could feel it. */
    private static void forget(PathGrid root, Fits fits) {
        var changed = root.changesSince(fits.serial);
        fits.serial = root.changeSerial();
        if (changed == null) {
            Arrays.fill(fits.cells, (byte) 0);
            return;
        }
        int width = root.getWidth();
        int margin = (int) Math.ceil((fits.clearance + root.widestCircle()) / root.getCellSize()) + 1;
        for (var box : changed) {
            int minX = Math.max(0, box[0] - margin);
            int maxX = Math.min(width - 1, box[2] + margin);
            int minY = Math.max(0, box[1] - margin);
            int maxY = Math.min(root.getHeight() - 1, box[3] + margin);
            for (int cy = minY; cy <= maxY; cy++) {
                Arrays.fill(fits.cells, cy * width + minX, cy * width + maxX + 1, (byte) 0);
            }
        }
    }
}
