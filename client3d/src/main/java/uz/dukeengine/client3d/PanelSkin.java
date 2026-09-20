package uz.dukeengine.client3d;

import java.awt.Color;
import java.util.Map;

/**
 * The painted edges a game wants its hero panel drawn with.
 *
 * <p>The shapes are the client's, as they are in {@link MenuStyle}: where the
 * minimap sits, how big a skill socket is, what a bar does when it empties. What
 * a game may say is what those edges are <em>painted</em> with — a file, how much
 * of it is corner, how heavy to lay it on, and what colour to lay it in.
 *
 * <p><b>An extra coat, never a foundation.</b> Every piece is optional and a
 * missing one is not an error: the panel draws the carved edge it has always
 * drawn, says so once in the log, and carries on. A game that names no skin at
 * all — every game but this one — gets exactly the panel it had before there were
 * any pictures. That is why the names below are a map rather than fields: a skin
 * with three of the six in it is a perfectly good skin.
 *
 * <p>The names are the contract between this client and any game that wants to
 * paint it, so they live here rather than being spelled twice.
 */
public record PanelSkin(Map<String, Piece> pieces) {

    /** The recess the minimap is sunk into. */
    public static final String MINIMAP = "Minimap";

    /** The portrait's frame. */
    public static final String PORTRAIT = "Portrait";

    /** A skill socket — the ultimate's larger one is the same picture, drawn bigger. */
    public static final String SLOT = "Slot";

    /** The trough a health or experience bar sits in. */
    public static final String GAUGE = "Gauge";

    /** The little square a power he has taken is shown in, and a figure's drawing. */
    public static final String CHIP = "Chip";

    /** One of the order buttons beside the map. */
    public static final String BUTTON = "Button";

    /** One socket of his bag. */
    public static final String ITEM = "Item";

    /**
     * The line between two sections of the bar.
     *
     * <p>The odd one out, and the one place the arithmetic differs: a divider is
     * an ornament of a fixed length rather than a frame, so it is drawn whole and
     * stood on end instead of being cut into nine. See
     * {@link NineSlice#quarterTurn}.
     */
    public static final String DIVIDER = "Divider";

    /**
     * The plaque a banner is written on — "you died", "you won", the next floor.
     *
     * <p>Three of it rather than one, because the three moments are not the same
     * news and the frame is the only thing on screen that can say which: a death
     * is an interruption, a floor is a door, and a win is an ending. A game that
     * names only {@code Banner} gets that one for all three, which is still better
     * than the bare lettering this replaced.
     */
    public static final String BANNER = "Banner";

    /** The plaque for an ending that went his way. */
    public static final String BANNER_WON = "BannerWon";

    /** And for one that did not. */
    public static final String BANNER_LOST = "BannerLost";

    /** For a game that has asked for nothing, which is every game but the dungeon. */
    public static final PanelSkin NONE = new PanelSkin(Map.of());

    /**
     * One painted piece.
     *
     * @param texture the picture, as the asset manager will be asked for it
     * @param inset   how many texels of it are corner and must not be stretched —
     *                read off the picture, because that is where the number is
     * @param scale   how many drawn units one texel becomes. The whole reason a
     *                bar and a socket can share a file: the same corner is laid on
     *                twice as heavily for the thing that is twenty times as wide
     * @param tint    what to paint it. The pictures are white, so this is the only
     *                thing that decides whether an edge is gold or bone
     */
    public record Piece(String texture, float inset, float scale, Color tint) {
    }

    public PanelSkin {
        // Insertion order kept, so what is loaded before a game starts is the same
        // list in the same order every run -- see Preload.
        pieces = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(pieces));
    }

    /** The piece of that name, or {@code null} — which means "draw it as before". */
    public Piece piece(String name) {
        return pieces.get(name);
    }
}
