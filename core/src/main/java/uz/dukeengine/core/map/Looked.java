package uz.dukeengine.core.map;

/**
 * A map whose cells wear looks of their own — a wood beside a cave on one floor — each the name of a look the game
 * registered with its client, and a cell that names none wearing the map's own.
 *
 * <p>For the renderer only, like {@link Painted}: nothing in the logic path may ask this. Which look a cell wears is
 * a matter of what is drawn there, and a world laid out from a seed lays out the same looks on every machine without
 * the simulation knowing any of them.
 */
public interface Looked extends MapTemplate {

    /** The look cell ({@code cx}, {@code cy}) wears, or null for the map's own. */
    String lookAt(int cx, int cy);
}
