package uz.dukeengine.core.pathfind;

import uz.dukeengine.core.math.Coord3D;

/**
 * The square of cells a ground mover covers: k cells a side from its bounding circle, as the reference's pathfinder
 * works it out ({@code Pathfinder::getRadiusAndCenter}) — its diameter in cells, raised to 2 when between 1 and 2,
 * {@code k = floor(diameter + 0.3)}, at least 1 and at most 5. An odd k is centred on the cell a point is in, an even
 * k on the cell corner nearest it. A radius of 7 covers 2 by 2; a box of radii 15 and 10 (a circle of 18.0), 3 by 3.
 *
 * @param x       the cell it is centred on — or, for an even k, the cell whose lower corner it is centred on
 * @param y       the same, along y
 * @param half    how many cells it reaches from there: {@code k / 2}
 * @param centred whether k is odd
 */
public record Block(int x, int y, int half, boolean centred) {

    /** The most a block reaches: the reference's {@code MAX_RADIUS}, 5 cells a side. */
    private static final int MOST_HALF = 2;

    /** The block a mover of {@code boundingRadius} covers standing at {@code (px, py)}, on cells of {@code cellSize}. */
    public static Block of(float boundingRadius, float cellSize, float px, float py) {
        return of(boundingRadius, cellSize, 1, px, py);
    }

    /**
     * The same on a grid walked {@code perMapCell} cells a side for each of the map's: the most it reaches is the
     * reference's five of the map's cells, that many more of its own.
     */
    public static Block of(float boundingRadius, float cellSize, int perMapCell, float px, float py) {
        float diameter = 2f * boundingRadius;
        if (diameter > cellSize && diameter < 2f * cellSize) {
            diameter = 2f * cellSize;
        }
        int cells = (int) Math.floor(diameter / cellSize + 0.3f);
        if (cells == 0) {
            cells = 1;
        }
        boolean centred = (cells & 1) == 1;
        int half = cells / 2;
        int mostHalf = MOST_HALF * Math.max(1, perMapCell);
        if (half > mostHalf) {
            half = mostHalf;
            centred = true;
        }
        return centred
                ? new Block((int) Math.floor(px / cellSize), (int) Math.floor(py / cellSize), half, true)
                : new Block((int) Math.floor(0.5f + px / cellSize), (int) Math.floor(0.5f + py / cellSize), half, false);
    }

    /** The same block moved to be centred on cell {@code (cx, cy)}. */
    public Block at(int cx, int cy) {
        return new Block(cx, cy, half, centred);
    }

    public int minX() {
        return x - half;
    }

    public int maxX() {
        return x + half - (centred ? 0 : 1);
    }

    public int minY() {
        return y - half;
    }

    public int maxY() {
        return y + half - (centred ? 0 : 1);
    }

    /** Whether the two share a cell. */
    public boolean overlaps(Block other) {
        return minX() <= other.maxX() && other.minX() <= maxX() && minY() <= other.maxY() && other.minY() <= maxY();
    }

    /**
     * Where a mover holding it stands, on the ground plane: its centre cell's middle, or — for an even block — just past
     * its corner, the reference's {@code (cell + 0.05) × cell size} ({@code adjustCoordToCell}).
     */
    public Coord3D point(float cellSize, float z) {
        return centred
                ? new Coord3D((x + 0.5f) * cellSize, (y + 0.5f) * cellSize, z)
                : new Coord3D((x + 0.05f) * cellSize, (y + 0.05f) * cellSize, z);
    }
}
