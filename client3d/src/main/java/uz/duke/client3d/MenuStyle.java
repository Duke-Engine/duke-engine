package uz.duke.client3d;

import uz.duke.core.data.Group;

/**
 * The lettering a game wants its menus set in, and the colours they are cut from.
 *
 * <p>The shapes are the client's — a slab, a lit line, a bar — because they are
 * the same in every game it draws. The letters are not: a dungeon wants carved
 * Roman capitals and a skirmish game does not, and a client that picked one for
 * all four would be making a game's decision for it. Nor is the palette: the
 * menus of this dungeon are stone under torchlight, and another game's are not.
 *
 * <p>The fonts are paths to bitmap fonts, which is what jME can draw. A game with
 * none named gets the client's own, which is what every game had. Every colour is
 * packed {@code 0xRRGGBB}, and one left out is the dungeon's own.
 *
 * @param titleFont    the heading and the lines themselves — a display face
 * @param rowFont      the small print underneath: hints, versions, subtitles
 * @param stoneDeepColour  the dark a trough or an unlit cell is cut into
 * @param stoneColour      the face of a slab, at its foot
 * @param stoneLitColour   and at its top, where the light falls
 * @param stoneEdgeColour  the lit lip along a slab's top edge
 * @param slabFootColour   the shadow under it
 * @param torchColour      what a menu is lit by: the heading, a lit row, a filled bar
 * @param torchHotColour   the same, brighter, for the row the hand is on
 * @param boneColour       a word being read
 * @param bloodColour      a row that ends a run — quitting, giving up
 * @param muteColour       a word that is there to be seen and not read
 * @param gloomTopColour   the backdrop behind a menu, at its top and foot
 * @param gloomMiddleColour and across its middle, where the torches are
 * @param veilColour       what the world behind a menu is washed with
 * @param veilPercent      how heavily — 100 hides the world entirely
 * @param hintColour       the line under a heading
 * @param ruleColour       the rule either side of a row nothing is on
 * @param dangerLitColour  a dangerous row with the hand on it
 * @param rowColour        a row nothing is on
 * @param grooveColour     the line between one row of a page and the next
 * @param dimColour        a row that cannot be chosen now
 * @param quietColour      an arrow or a word that is off rather than dim
 * @param onTopColour      a setting that is on, at the top of its cell
 * @param onFootColour     and at its foot
 * @param fillTopColour    a slider's filled part, at its top
 * @param fillFootColour   and at its foot; the middle is the torch
 * @param footnoteColour   the build line at the very bottom of the first page
 */
public record MenuStyle(String titleFont, String rowFont,
        @Group("Stone") int stoneDeepColour, int stoneColour, int stoneLitColour, int stoneEdgeColour,
        int slabFootColour, int gloomTopColour, int gloomMiddleColour, int veilColour, int veilPercent,
        @Group("Colours") int torchColour, int torchHotColour, int boneColour, int bloodColour, int muteColour,
        int hintColour, int ruleColour, int dangerLitColour, int rowColour, int grooveColour, int dimColour,
        int quietColour, int onTopColour, int onFootColour, int fillTopColour, int fillFootColour,
        int footnoteColour) {

    /** The client's own lettering and the dungeon's own stone, for a game that has asked for neither. */
    public static final MenuStyle DEFAULTS = new MenuStyle(null, null,
            0x16130F, 0x2B2620, 0x3D362C, 0x5A5042, 0x0A0806, 0x0C0A08, 0x241E19, 0x080605, 78,
            0xE8A33D, 0xFFD089, 0xD9CFBA, 0xA8322B, 0x7A7062,
            0x8B8171, 0x3A322A, 0xE08A80, 0xA69B87, 0x2A241D, 0x5A5346,
            0x5D5548, 0x4A3A1E, 0x2E2413, 0xF0BC6B, 0xA06D1F,
            0x443E35);

    public MenuStyle {
        titleFont = titleFont == null || titleFont.isBlank() ? null : titleFont;
        rowFont = rowFont == null || rowFont.isBlank() ? null : rowFont;
        veilPercent = Math.clamp(veilPercent, 0, 100);
    }
}
