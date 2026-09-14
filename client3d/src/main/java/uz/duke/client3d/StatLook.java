package uz.duke.client3d;

/**
 * How the block under the experience bar is drawn: the figures a fight is read by down
 * the left, his primary attribute large in the middle, and every attribute he has down
 * the right — the way Warcraft III lays a hero out.
 *
 * <p>The client knows where the three columns go and what a socket is made of. How big
 * each kind of picture is, how many rows the block keeps room for, and what colour each
 * word is set in are the game's, and are numbers in its data file. A game that asks for
 * nothing gets {@link #DEFAULT}.
 *
 * <p>A colour sets a word; a tint multiplies a picture, and white is a picture exactly as
 * it was drawn. Every one of them is packed RGB. Sizes are design pixels, the pixels the
 * whole panel is drawn in and scaled from.
 *
 * @param figureIcon      the socket beside a figure
 * @param primaryIcon     the framed socket his primary stands in
 * @param attributeIcon   the socket beside each attribute in the list
 * @param iconShare       how much of a socket its picture fills
 * @param rowGap          between one row and the next, in either list
 * @param gapUnderBar     between the experience bar and the top of the block
 * @param figureColumn    how wide the left column is
 * @param primaryColumn   how wide the middle one is; the right one has what is left
 * @param figureRows      how many figures the block keeps room for before they close up
 * @param attributeRows   and how many attributes
 * @param figureText      the size a figure's word and value are set in
 * @param attributeText   the size an attribute's are
 * @param primaryText     and the value under his primary
 * @param labelColour     a figure's word
 * @param valueColour     a figure's value
 * @param primaryColour   his primary's word and value, brighter than the rest
 * @param attributeColour every other attribute's word and value
 * @param gainColour      what he found, beside what it moved
 * @param frameColour     the ring round his primary's socket
 * @param figureTint      what a figure's picture is multiplied by
 * @param primaryTint     what his primary's pictures are
 * @param attributeTint   and every other attribute's
 */
public record StatLook(float figureIcon, float primaryIcon, float attributeIcon, float iconShare,
        float rowGap, float gapUnderBar, float figureColumn, float primaryColumn,
        int figureRows, int attributeRows, float figureText, float attributeText,
        float primaryText, int labelColour, int valueColour, int primaryColour,
        int attributeColour, int gainColour, int frameColour, int figureTint, int primaryTint,
        int attributeTint) {

    /** What a game that asks for nothing gets. */
    public static final StatLook DEFAULT = new StatLook(30f, 44f, 24f, 0.8f, 2f, 6f, 150f, 74f,
            2, 3, 12f, 11f, 14f, 0x8B8171, 0xD9CFBA, 0xF0D48A, 0xC9A24B, 0x7FBF6A, 0xF0D48A,
            0xC9A24B, 0xFFFFFF, 0xC9A24B);

    public StatLook {
        figureIcon = Math.clamp(figureIcon, 12f, 48f);
        primaryIcon = Math.clamp(primaryIcon, 16f, 64f);
        attributeIcon = Math.clamp(attributeIcon, 12f, 40f);
        iconShare = Math.clamp(iconShare, 0.3f, 1f);
        rowGap = Math.clamp(rowGap, 0f, 16f);
        gapUnderBar = Math.clamp(gapUnderBar, 0f, 24f);
        figureColumn = Math.clamp(figureColumn, figureIcon + 40f, 260f);
        primaryColumn = Math.clamp(primaryColumn, primaryIcon + 8f, 160f);
        figureRows = Math.clamp(figureRows, 1, 8);
        attributeRows = Math.clamp(attributeRows, 1, 8);
        figureText = Math.clamp(figureText, 7f, 20f);
        attributeText = Math.clamp(attributeText, 7f, 20f);
        primaryText = Math.clamp(primaryText, 7f, 24f);
    }
}
