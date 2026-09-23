package uz.dukeengine.core.map;

import java.util.Map;
import uz.dukeengine.core.data.Paint;

/**
 * A map that says what its ground looks like: the rows marked {@link Paint} are one character a cell, and this
 * is what each of those characters is.
 *
 * <p>Two halves because they are two shapes. The rows are text a cell wide, read like the {@code @Grid} and the
 * {@code @Relief} beside them and drawn by an editor the same way; the palette is a handful of names, one line
 * of the file. A map of a quarter of a million cells names perhaps a dozen textures.
 *
 * <p>For the renderer only. Nothing in the logic path may ask this — see {@link Paint}.
 */
public interface Painted extends MapTemplate {

    /**
     * What each character of the paint rows stands for: a texture name the game's own art knows.
     *
     * <p>Whatever is written here is handed to the client as it stands. The engine does not put a folder in
     * front of it, take a suffix off it or look it up in anything — a game's art is laid out the way that
     * game likes, and the line in the file is the thing that is loaded. The one distinction drawn is whether
     * the value is a colour rather than a picture, so that a game with no art at all still gets painted
     * ground.
     *
     * <p>Walked in its own order wherever the answer depends on it, so it is a map that has one — the
     * {@code Binder} reads a block's entries into a {@code LinkedHashMap}, which is the order they were
     * written in.
     */
    Map<String, String> palette();

    /**
     * How much ground one copy of a picture covers, in cells, by the same keys as the {@link #palette}.
     *
     * <p>Not one number for the map and not a constant in the engine: in a real game a roadway repeats every
     * two cells and a field of grass every ten, and a picture laid at the wrong size is the difference
     * between ground and a pattern. A key this does not name covers one cell, which is the right answer for
     * a colour and a fair one for a picture.
     */
    default Map<String, Float> coverage() {
        return Map.of();
    }
}
