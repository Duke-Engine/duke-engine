package uz.dukeengine.client3d.rpg;

/**
 * The bar's measures in the pixels the design was drawn at, for the parts that more than one of its pieces lay
 * out against, and the one test of whether a point is in a square.
 */
final class PanelMetrics {

    private PanelMetrics() {
    }

    static final float PAD = 10f;
    /** The tallest thing in the bar — the minimap — and so the bar's own height. */
    static final float BAND = 172f;
    /** The gold heading over a block — "NARSALAR", "MAHORAT". */
    static final float HEADING_SIZE = 12f;
    static final float HEADING_GAP = 6f;

    /**
     * The portrait, which is taller than it is wide.
     *
     * <p>A square read as a picture of a square thing. Head and shoulders are a
     * standing shape, and the badge that hangs under it wants the height.
     */
    static final float PORTRAIT = 126f;
    /**
     * ★ SHORTER THAN IT WAS, because two bars moved in under it.
     *
     * <p>What is left of him now hangs off his own face rather than off the far
     * side of his name: portrait, health, mana, one block the width of the
     * portrait. They are the three things about the man himself, and they used to
     * be split between two columns for no reason but that the bars were wide.
     */
    static final float PORTRAIT_HEIGHT = 128f;

    /**
     * The column the portrait stands in, which is wider than the portrait.
     *
     * <p>The design's: a frame of a hundred and twenty-six in a column of a
     * hundred and fifty, with the two gauges filling the column rather than the
     * frame. The bars being wider than the face is what makes them read as the
     * block's own rather than as part of the picture.
     */
    static final float PORTRAIT_COLUMN = 150f;

    /**
     * ★ WIDER THAN IT WAS, and the figures underneath are why.
     *
     * <p>Four of them across, each on ONE line — a drawing, a word, a number and
     * what is lent. Measured in the font this client actually ships: "Zarba 34
     * +6" is sixty-six pixels at the design's size and eighty-one with its
     * drawing and the gap, so four of them want three hundred and forty-five. The
     * design gets away with less because it is set in a condensed face and this
     * is not; the width is the honest way to pay for that, since shrinking the
     * lettering instead would make the one line nobody can enlarge unreadable.
     */
    static final float VITALS_WIDTH = 424f;

    /**
     * The experience bar, which is now the widest thing on the band.
     *
     * <p>It was ten pixels of trim under two bars that mattered more. It is
     * thirty, alone across the column, because it is the only bar on the panel
     * that is about the WHOLE RUN: health and mana come back, and this never goes
     * backwards. It is also the only one with room for lettering at both ends,
     * which is what let the level badge under the portrait go — the level was
     * being said twice, and this is the place the eye already is.
     */
    static final float XP_HEIGHT = 30f;
    /**
     * Everything in the column above the block of figures: his name, what he is, the gap
     * under that, and the experience bar. The block has what is left of the band.
     */
    static final float ABOVE_THE_BLOCK = 27f + 18f + 12f + XP_HEIGHT;

    /**
     * The key a socket carries when it belongs to nobody.
     *
     * <p>The bar's sockets are furniture and what goes in them is data — the same
     * arrangement his bag has always had, where six sockets are drawn whether he
     * is carrying six things or none. A row that appeared and vanished with the
     * selection would make the bar a different shape every time the player
     * clicked, which is the one thing a bar must not be.
     */
    static final char BLANK = '\0';

    /**
     * Whether a point is inside a square, in the design's own pixels.
     *
     * <p>Its own method because it is the part that can be wrong without looking
     * wrong: a slot that answers to clicks an inch from where it is drawn is a
     * bug nobody sees until they try to cast something.
     */
    static boolean hits(float x, float y, float left, float bottom, float size) {
        return x >= left && x <= left + size && y >= bottom && y <= bottom + size;
    }
}
