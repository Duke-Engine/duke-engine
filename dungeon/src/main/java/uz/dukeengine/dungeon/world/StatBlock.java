package uz.dukeengine.dungeon.world;

/**
 * How the block under the hero's experience bar is drawn: how big each kind of socket is, how many
 * rows the block keeps room for, and the colours its words and pictures are set in.
 *
 * <p>Handed to the client as its {@code StatLook}, field for field. Nothing here says what is IN
 * the block — that is the status line's — only what it looks like.
 */
public record StatBlock(float figureIcon, float primaryIcon, float attributeIcon,
        float iconShare, float rowGap, float gapUnderBar, float figureColumn,
        float primaryColumn, int figureRows, int attributeRows, float figureText,
        float attributeText, float primaryText, int labelColour, int valueColour,
        int primaryColour, int attributeColour, int gainColour, int frameColour,
        int figureTint, int primaryTint, int attributeTint) {

    /** What a block leaves out. */
    public static final StatBlock DEFAULTS = new StatBlock(30f, 44f, 24f, 0.8f, 2f, 6f, 150f, 74f, 2, 3, 12f, 11f,
            14f, 0x8B8171, 0xD9CFBA, 0xF0D48A, 0xC9A24B, 0x7FBF6A, 0xF0D48A, 0xC9A24B, 0xFFFFFF, 0xC9A24B);
}
