package uz.dukeengine.core.pathfind;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.core.math.Coord3D;

/**
 * A floor laid over the ground — the reference's bridge ({@code PathfindLayer}, {@code TerrainLogic} Bridge): a flat
 * quad, its height the plane through three of its corners, sloping end to end, with cells of its own over its bounding
 * box, walked on at its own height while the ground under it keeps its own. Its corners run round it, the first two
 * one end and the last two the other; the only ways on and off are its entries, the cells half a cell beyond each end,
 * each joined to the deck cell inside it.
 *
 * <p>Classified as the reference classifies a layer's cells ({@code PathfindLayer::classifyLayerMapCell}): a cell whose
 * four corners are all on the quad, edges included, is walkable, one only partly on it is not — except the cells the
 * two end lines cross, inset a cell from the sides, which are walkable so there is a way on. Plain arithmetic on the
 * corners, so every machine lays the same cells.
 */
public final class Deck {

    private final int floor;
    private final float[] cornerX = new float[4];
    private final float[] cornerY = new float[4];
    private final float[] cornerZ = new float[4];
    /** Its height: z = slopeX · x + slopeY · y + base. */
    private final float slopeX;
    private final float slopeY;
    private final float base;
    private final int minX;
    private final int minY;
    private final int maxX;
    private final int maxY;
    private final boolean[] walkable;
    /** Each entry's ground cell and the deck cell it joins, by grid index, in the order laid. */
    private final List<int[]> entries = new ArrayList<>();
    private volatile boolean open = true; // read by a client's pointer too

    Deck(int floor, Coord3D[] corners, PathGrid grid) {
        this.floor = floor;
        for (int i = 0; i < 4; i++) {
            cornerX[i] = corners[i].x();
            cornerY[i] = corners[i].y();
            cornerZ[i] = corners[i].z();
        }
        // The plane through the first three corners: its normal, the cross of two of its sides.
        float ax = cornerX[1] - cornerX[0];
        float ay = cornerY[1] - cornerY[0];
        float az = cornerZ[1] - cornerZ[0];
        float bx = cornerX[2] - cornerX[0];
        float by = cornerY[2] - cornerY[0];
        float bz = cornerZ[2] - cornerZ[0];
        float nx = ay * bz - az * by;
        float ny = az * bx - ax * bz;
        float nz = ax * by - ay * bx;
        if (nz == 0f) {
            slopeX = 0f;
            slopeY = 0f;
            base = (cornerZ[0] + cornerZ[1] + cornerZ[2] + cornerZ[3]) / 4f;
        } else {
            slopeX = -nx / nz;
            slopeY = -ny / nz;
            base = cornerZ[0] - slopeX * cornerX[0] - slopeY * cornerY[0];
        }
        float cell = grid.getCellSize();
        float lowX = Math.min(Math.min(cornerX[0], cornerX[1]), Math.min(cornerX[2], cornerX[3]));
        float lowY = Math.min(Math.min(cornerY[0], cornerY[1]), Math.min(cornerY[2], cornerY[3]));
        float highX = Math.max(Math.max(cornerX[0], cornerX[1]), Math.max(cornerX[2], cornerX[3]));
        float highY = Math.max(Math.max(cornerY[0], cornerY[1]), Math.max(cornerY[2], cornerY[3]));
        minX = Math.max(0, (int) Math.floor(lowX / cell) - 1);
        minY = Math.max(0, (int) Math.floor(lowY / cell) - 1);
        maxX = Math.min(grid.getWidth() - 1, (int) Math.floor(highX / cell) + 1);
        maxY = Math.min(grid.getHeight() - 1, (int) Math.floor(highY / cell) + 1);
        walkable = new boolean[Math.max(0, (maxX - minX + 1) * (maxY - minY + 1))];
        classify(grid, cell);
    }

    private void classify(PathGrid grid, float cell) {
        // Its axis, end to end, and its breadth, from the first end's two corners.
        float startX = (cornerX[0] + cornerX[1]) / 2f;
        float startY = (cornerY[0] + cornerY[1]) / 2f;
        float endX = (cornerX[2] + cornerX[3]) / 2f;
        float endY = (cornerY[2] + cornerY[3]) / 2f;
        float length = (float) Math.sqrt((endX - startX) * (endX - startX) + (endY - startY) * (endY - startY));
        if (length <= 0f) {
            return;
        }
        float ux = (endX - startX) / length;
        float uy = (endY - startY) / length;
        float vx = -uy;
        float vy = ux;
        float half = (float) Math.sqrt((cornerX[1] - cornerX[0]) * (cornerX[1] - cornerX[0])
                + (cornerY[1] - cornerY[0]) * (cornerY[1] - cornerY[0])) / 2f;
        float inset = half - cell;
        float reach = cell / 2f * (Math.abs(ux) + Math.abs(uy)); // how far a cell spans along the axis from its middle
        for (int cy = minY; cy <= maxY; cy++) {
            for (int cx = minX; cx <= maxX; cx++) {
                float x0 = cx * cell;
                float y0 = cy * cell;
                boolean whole = inside(x0, y0) && inside(x0 + cell, y0) && inside(x0 + cell, y0 + cell)
                        && inside(x0, y0 + cell);
                float mx = x0 + cell / 2f - startX;
                float my = y0 + cell / 2f - startY;
                float along = mx * ux + my * uy;
                float across = mx * vx + my * vy;
                boolean onAnEnd = Math.abs(across) <= inset
                        && (Math.abs(along) < reach || Math.abs(along - length) < reach); // straddling, not beside
                walkable[(cy - minY) * (maxX - minX + 1) + (cx - minX)] = whole || onAnEnd;
            }
        }
        entriesAt(grid, cell, startX, startY, -ux, -uy, vx, vy, inset);
        entriesAt(grid, cell, endX, endY, ux, uy, vx, vy, inset);
    }

    /** The entries of the end at ({@code mx}, {@code my}), {@code (ox, oy)} its way out: sampled across it. */
    private void entriesAt(PathGrid grid, float cell, float mx, float my, float ox, float oy, float vx, float vy,
            float inset) {
        int steps = Math.max(0, (int) Math.floor(inset * 2f / (cell / 2f)));
        for (int i = 0; i <= steps; i++) {
            float across = -inset + i * (cell / 2f);
            float gx = mx + ox * cell / 2f + vx * across;
            float gy = my + oy * cell / 2f + vy * across;
            float dx = mx - ox * cell / 2f + vx * across;
            float dy = my - oy * cell / 2f + vy * across;
            int groundX = (int) Math.floor(gx / cell);
            int groundY = (int) Math.floor(gy / cell);
            int deckX = (int) Math.floor(dx / cell);
            int deckY = (int) Math.floor(dy / cell);
            if (!grid.inBounds(groundX, groundY) || !walkable(deckX, deckY) || walkable(groundX, groundY)) {
                continue;
            }
            int ground = grid.index(groundX, groundY);
            int deck = grid.index(deckX, deckY);
            if (entries.stream().noneMatch(pair -> pair[0] == ground && pair[1] == deck)) {
                entries.add(new int[] {ground, deck});
            }
        }
    }

    /** Whether a point of the ground lies on the quad, edges included. */
    private boolean inside(float x, float y) {
        boolean positive = false;
        boolean negative = false;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            float cross = (cornerX[j] - cornerX[i]) * (y - cornerY[i]) - (cornerY[j] - cornerY[i]) * (x - cornerX[i]);
            if (cross > 1e-4f) {
                positive = true;
            } else if (cross < -1e-4f) {
                negative = true;
            }
        }
        return !(positive && negative);
    }

    /** Its floor: the number a thing on it is on, 1 on. */
    public int floor() {
        return floor;
    }

    public boolean isOpen() {
        return open;
    }

    void setOpen(boolean open) {
        this.open = open;
    }

    /** Whether cell ({@code cx}, {@code cy}) of the grid is one of its walkable cells. */
    public boolean walkable(int cx, int cy) {
        return cx >= minX && cx <= maxX && cy >= minY && cy <= maxY
                && walkable[(cy - minY) * (maxX - minX + 1) + (cx - minX)];
    }

    /** Its height over a point of the ground. */
    public float heightAt(float x, float y) {
        return slopeX * x + slopeY * y + base;
    }

    /** Whether a point of the ground lies on it. */
    public boolean covers(float x, float y) {
        return inside(x, y);
    }

    /** Its corners, as they were laid. */
    public Coord3D corner(int i) {
        return new Coord3D(cornerX[i], cornerY[i], cornerZ[i]);
    }

    /** Its entries: each a ground cell and the deck cell it joins, by grid index. */
    List<int[]> entries() {
        return entries;
    }

    int minX() {
        return minX;
    }

    int minY() {
        return minY;
    }

    int maxX() {
        return maxX;
    }

    int maxY() {
        return maxY;
    }
}
