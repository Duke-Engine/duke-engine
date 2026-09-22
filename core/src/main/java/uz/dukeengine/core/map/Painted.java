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
     * <p>Walked in its own order wherever the answer depends on it, so it is a map that has one — the
     * {@code Binder} reads a block's entries into a {@code LinkedHashMap}, which is the order they were
     * written in.
     */
    Map<String, String> palette();
}
