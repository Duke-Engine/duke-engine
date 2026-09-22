package uz.dukeengine.core.map;

import java.util.List;

/**
 * A named piece of a map with a shape rather than a cell: a ring of points on the ground.
 *
 * <p>An interface rather than a record, for the reason every other seam here is one — the game keeps its areas
 * in its own record, read from its own block by its own word, and core only asks. A game's
 * {@code record Area(…) implements MapArea} answers without its file changing a line.
 *
 * <p>The one thing core cares about is {@link #water}. An area is otherwise the game's business — a no-build
 * zone, a trigger, a place a script names — but water is not decoration: a boat floats on it, infantry drown
 * in it, and the pathfinder is the wrong place to learn that from a texture. Of the 720 areas across Zero
 * Hour's 65 skirmish maps, 122 are water.
 */
public interface MapArea {

    /** What the map calls it, which is how a script or a script's game finds it again. */
    String name();

    /** Whether the ground inside it is water, standing at {@link #height}. */
    boolean water();

    /** How high its surface stands, in steps — the unit the {@code @Relief} is written in. */
    float height();

    /**
     * Its outline, {@code x, y, x, y, …} in cells: two numbers a corner, and the last corner joins the first.
     *
     * <p>Flat because that is how it is written, and core does not consume it. A reader that wants points
     * pairs them off; one that wants only to know where the water is never has to.
     */
    List<Float> points();
}
