package uz.duke.client3d;

import java.util.Arrays;
import java.util.Locale;

/**
 * One block of the hero's bar standing on its own rather than in the bar, and where: a corner or an edge of the
 * window, and how far in from it.
 *
 * <p>Written as a line — {@code Minimap TopRight 12 12} — which is how the game's file says it. The two numbers
 * are always measured <em>inwards</em> from what the anchor names, so the same pair means the same distance at
 * every corner; at {@code Middle} they move it right and up from the centre of the screen. They are in the
 * pixels the bar is designed at, so a block keeps its place on the screen however the window is scaled.
 *
 * @param block  which block of the bar it is
 * @param anchor the corner or edge of the window it is hung from
 * @param x      how far in from that side, across
 * @param y      and up or down
 */
public record PanelPlace(PanelBlock block, PanelAnchor anchor, float x, float y) {

    /** {@code Minimap TopRight 12 12}: the block, where it hangs, and how far in — the last three all optional. */
    public static PanelPlace of(String line) {
        var words = line.trim().split("\\s+");
        var block = named(PanelBlock.values(), words[0]);
        if (block == null) {
            throw new IllegalArgumentException("a block of the bar is one of " + Arrays.toString(PanelBlock.values())
                    + ", not '" + words[0] + "'");
        }
        var anchor = words.length < 2 ? PanelAnchor.TOP_LEFT : named(PanelAnchor.values(), words[1]);
        if (anchor == null) {
            throw new IllegalArgumentException("a place on the screen is one of " + Arrays.toString(PanelAnchor.values())
                    + ", not '" + words[1] + "'");
        }
        return new PanelPlace(block, anchor, number(words, 2, line), number(words, 3, line));
    }

    /**
     * The constant of that name, written as the file likes: {@code TopRight}, {@code top-right} and
     * {@code TOP_RIGHT} are one place, because a data file is not Java and should not have to look like it.
     */
    private static <T extends Enum<T>> T named(T[] constants, String word) {
        var wanted = plain(word);
        for (var constant : constants) {
            if (plain(constant.name()).equals(wanted)) {
                return constant;
            }
        }
        return null;
    }

    private static String plain(String word) {
        return word.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

    private static float number(String[] words, int at, String line) {
        if (at >= words.length) {
            return 0f;
        }
        try {
            return Float.parseFloat(words[at]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + line + "' ends in how far in it stands, which is a number");
        }
    }
}
