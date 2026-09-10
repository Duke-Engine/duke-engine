package uz.duke.client3d;

/**
 * The lettering a game wants its menus set in.
 *
 * <p>The shapes are the client's — a slab, a lit line, a bar — because they are
 * the same in every game it draws. The letters are not: a dungeon wants carved
 * Roman capitals and a skirmish game does not, and a client that picked one for
 * all four would be making a game's decision for it.
 *
 * <p>Paths to bitmap fonts, which is what jME can draw. A game with none named
 * gets the client's own, which is what every game had.
 *
 * @param titleFont  the heading and the lines themselves — a display face
 * @param rowFont    the small print underneath: hints, versions, subtitles
 */
public record MenuStyle(String titleFont, String rowFont) {

    /** The client's own lettering, for a game that has not asked for any. */
    public static final MenuStyle PLAIN = new MenuStyle(null, null);
}
