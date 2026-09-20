package uz.duke.dungeon.gen;

import uz.duke.core.pathfind.HeightMap;

/**
 * Gentle hills over a floor: a height drawn at every {@code size}-th corner, and the corners between eased from one
 * drawn height to the next, in whole numbers.
 *
 * <p>Never more than 15 steps, so never a cliff: SAGE calls a cell a cliff when its corners are 16 steps apart, and
 * no two heights here are. Which is what lets hills be laid over a floor that was checked walkable without it —
 * every step the floor allowed, it still allows.
 */
final class Hills {

    /** The highest a hill may rise: one step under a cliff. */
    static final int MOST = HeightMap.CLIFF_STEPS - 1;

    private Hills() {
    }

    /**
     * Hills over a floor of {@code width × height} cells, up to [most] steps high and about [size] cells across.
     *
     * <p>Drawn from a stream of their own, so a floor with hills is the same floor as without them, stone for stone and
     * monster for monster: only the ground under it rises and falls.
     */
    static HeightMap of(long seed, int width, int height, int most, int size) {
        var rng = new DeterministicRng(seed ^ 0x68696C6C73L);
        int columns = width + 1;
        int rows = height + 1;
        int across = columns / size + 2;
        int down = rows / size + 2;
        var drawn = new int[across * down];
        for (int i = 0; i < drawn.length; i++) {
            drawn[i] = rng.nextInt(most + 1);
        }
        var steps = new int[columns * rows];
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < columns; x++) {
                int gx = x / size;
                int gy = y / size;
                int tx = x % size;
                int ty = y % size;
                int top = drawn[gy * across + gx] * (size - tx) + drawn[gy * across + gx + 1] * tx;
                int bottom = drawn[(gy + 1) * across + gx] * (size - tx) + drawn[(gy + 1) * across + gx + 1] * tx;
                steps[y * columns + x] = (top * (size - ty) + bottom * ty) / (size * size);
            }
        }
        return new HeightMap(columns, rows, steps);
    }
}
