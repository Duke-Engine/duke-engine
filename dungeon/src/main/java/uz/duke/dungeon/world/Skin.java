package uz.duke.dungeon.world;

/**
 * One painted edge of the hero panel: which part, which picture, and how. Every one of them is
 * optional — a part nobody names keeps the carved look it always had.
 *
 * <p>The client's own {@code PanelSkin} is what this becomes — see {@code Main} — so the name is
 * one of the names it answers to, and nothing here decides where a frame goes, only what it is
 * made of.
 *
 * @param name  the part of the panel: Minimap, Portrait, Slot, Gauge, Chip, Divider…
 * @param inset how many pixels of the picture are corner, measured off the file. Wrong and the
 *     corner is stretched or the edge is not
 * @param scale how many panel pixels one picture pixel becomes — the same file laid on lightly for
 *     a socket and heavily for a bar
 */
public record Skin(String name, String texture, float inset, float scale, int tint) {

    /** What a block leaves out. */
    public static final Skin DEFAULTS = new Skin("", "", 0f, 1f, 0xFFFFFF);

    public java.awt.Color awtTint() {
        return new java.awt.Color(tint);
    }
}
