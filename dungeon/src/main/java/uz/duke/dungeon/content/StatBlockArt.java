package uz.duke.dungeon.content;

/**
 * How the block under the hero's experience bar is drawn, as {@code StatBlock}
 * spells it: how big each kind of socket is, how many rows the block keeps room for, and
 * the colours its words and pictures are set in.
 *
 * <p>Handed to the client as its {@code StatLook}, field for field. Nothing here says
 * what is IN the block — that is the status line's — only what it looks like.
 */
public record StatBlockArt(float figureIcon, float primaryIcon, float attributeIcon,
        float iconShare, float rowGap, float gapUnderBar, float figureColumn,
        float primaryColumn, int figureRows, int attributeRows, float figureText,
        float attributeText, float primaryText, int labelColour, int valueColour,
        int primaryColour, int attributeColour, int gainColour, int frameColour,
        int figureTint, int primaryTint, int attributeTint) {
}
