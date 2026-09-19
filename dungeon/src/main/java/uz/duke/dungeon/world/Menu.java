package uz.duke.dungeon.world;

/**
 * The lettering the menus are set in.
 *
 * @param titleFont the font a menu title is drawn in, or null for the engine's own lettering
 * @param rowFont   the font a menu row is drawn in, or null for the engine's own
 */
public record Menu(String titleFont, String rowFont) {

    /** What a block leaves out. */
    public static final Menu DEFAULTS = new Menu(null, null);

    public Menu {
        titleFont = titleFont == null || titleFont.isBlank() ? null : titleFont;
        rowFont = rowFont == null || rowFont.isBlank() ? null : rowFont;
    }
}
