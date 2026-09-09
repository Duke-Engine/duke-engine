package uz.duke.client3d;

/**
 * The mapping between the world and the minimap overlay, and nothing else.
 *
 * <p>Kept apart from the scene it draws into because it is the part with answers
 * that can be wrong: a dot half a map out, a viewport box that drifts from what
 * the camera really sees. As plain arithmetic over floats it can be checked
 * directly, without a window, a GPU or a running game.
 *
 * <p>Two frames meet here and they disagree about which way is up. The map's y
 * grows downward — row 0 is the top — while screen y grows upward from the bottom
 * of the window. Every conversion flips, which is exactly the sort of thing worth
 * having a test for.
 */
final class MinimapProjection {

    /** A point on the minimap, in pixels from its bottom-left corner. */
    record Point(float x, float y) {
    }

    private final float worldWidth;
    private final float worldHeight;
    private final float scale;

    /** Fits a world of this size into a square {@code size} pixels on its longest side. */
    MinimapProjection(float worldWidth, float worldHeight, float size) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.scale = size / Math.max(worldWidth, worldHeight);
    }

    float scale() {
        return scale;
    }

    float widthPixels() {
        return worldWidth * scale;
    }

    float heightPixels() {
        return worldHeight * scale;
    }

    /** World position to a point on the minimap. */
    Point toMinimap(float worldX, float worldY) {
        return new Point(worldX * scale, (worldHeight - worldY) * scale);
    }

    /** A point on the minimap back to the world position it stands for. */
    float toWorldX(float minimapX) {
        return minimapX / scale;
    }

    float toWorldY(float minimapY) {
        return worldHeight - minimapY / scale;
    }

    boolean contains(float minimapX, float minimapY) {
        return minimapX >= 0 && minimapY >= 0
                && minimapX <= widthPixels() && minimapY <= heightPixels();
    }

    /**
     * The same point, pulled inside the minimap if it fell outside.
     *
     * <p>The camera can look past the edge of the map — at a corner, most of what
     * is on screen is off the map entirely. The viewport outline still has to stay
     * inside its little box rather than draw over the rest of the HUD, so corners
     * are clamped. A clamped outline reads correctly: it flattens against the edge
     * the camera has run up against.
     */
    Point clamp(Point point) {
        return new Point(
                Math.clamp(point.x(), 0f, widthPixels()),
                Math.clamp(point.y(), 0f, heightPixels()));
    }

    /**
     * The camera's footprint on the minimap: each world-space ground corner
     * converted and clamped, in the order given, ready to be drawn as a loop.
     *
     * <p>Takes the corners rather than the camera because where a perspective
     * camera actually meets the ground is the caller's problem — it needs the
     * projection matrix to answer. What the shape is once it gets here is this
     * class's problem.
     */
    Point[] viewportOutline(float[] worldXs, float[] worldYs) {
        if (worldXs.length != worldYs.length) {
            throw new IllegalArgumentException("a corner needs both an x and a y");
        }
        var outline = new Point[worldXs.length];
        for (int i = 0; i < worldXs.length; i++) {
            outline[i] = clamp(toMinimap(worldXs[i], worldYs[i]));
        }
        return outline;
    }
}
