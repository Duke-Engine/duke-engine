package uz.dukeengine.client3d;

/**
 * A mark the game puts on the minimap — a floor's key, a quest's target — at a point of the world; held to the
 * minimap's edge, pointing the way, while the point is off it.
 *
 * @param x       where in the world, across
 * @param y       where in the world, along
 * @param colour  packed RGB it is drawn in, the picture tinted by it
 * @param picture a picture from the resource root, or null for a plain diamond
 * @param blinks  whether it blinks, twice a second
 */
public record MinimapMark(float x, float y, int colour, String picture, boolean blinks) {

    /** A plain diamond that holds still. */
    public MinimapMark(float x, float y, int colour) {
        this(x, y, colour, null, false);
    }
}
