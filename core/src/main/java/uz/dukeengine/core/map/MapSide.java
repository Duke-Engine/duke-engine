package uz.dukeengine.core.map;

/**
 * One of the sides a map is drawn for. {@link Peopled#players} is how many; this is who.
 *
 * <p>A count is enough to fill a lobby and no more. It cannot say that the third slot is the civilians who own
 * the trees, or that this map's two sides are an army and a militia rather than two of the same — which is a
 * thing the place decides, not the game's rules, and so belongs to the map.
 */
public interface MapSide {

    /** What the map calls it, which is what anything standing on the map names when it says whose it is. */
    String name();

    /** Which of the game's factions it plays, by that game's own word for them. */
    String faction();

    /** Whether a person takes it: the rest are the game's to fill, or to leave standing as scenery. */
    boolean human();
}
