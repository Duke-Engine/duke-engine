package uz.duke.core.map;

/**
 * A map that says how big one of its cells is, in world units.
 *
 * <p>A map is drawn on a grid and played in world units, and the number between the two has to live somewhere.
 * SAGE put it in the engine — {@code MAP_XY_FACTOR 10.0f} — and so no Generals map could ever be drawn at
 * another scale, nor its heights carry more than a byte. It belongs to the place, so it is asked of the place;
 * a map that does not say gets the world's.
 */
public interface Scaled extends MapTemplate {

    /** How wide and deep one cell is, in world units; 0 or less leaves the world's own. */
    float cellSize();
}
