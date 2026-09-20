package uz.dukeengine.client3d;

import java.util.HashSet;
import java.util.List;
import uz.dukeengine.core.data.Group;

/**
 * How the hero's bar is put together and what it is painted in: which blocks it has and in what order, how it
 * is scaled to the window, how big its sockets are, and every colour it is drawn in.
 *
 * <p>What the bar does is the client's — it reads the status line, lights what can be clicked, sweeps a slot
 * that is reloading. What it looks like is the game's, like the fog and the pointer: the edges are the game's
 * {@link PanelSkin}, and this is the rest. A game that asks for nothing gets {@link #DEFAULTS}, which is the bar
 * as it was designed.
 *
 * <p>The sizes are in the pixels the design was drawn at — see {@link #designWidth} — and a socket asked to be
 * larger than the bar is tall is drawn as large as it fits.
 *
 * @param blocks           the blocks along the bar, left to right. One left out is not drawn and the bar
 *                         closes up where it was — unless {@code places} stands it somewhere of its own
 * @param places           the blocks that stand on their own instead, each hung from a corner or an edge of the
 *                         window on its own plate of the same stone: {@code Minimap TopRight 12 12}. A bar with
 *                         no blocks left on it is not drawn at all, and the screen is the HUD
 * @param designWidth      the window width the bar was drawn for, at which it is drawn one to one
 * @param minScale         the smallest the bar is shrunk to in a narrow window, while it still fits
 * @param maxScale         the largest it is grown to in a wide one
 * @param itemColumns      his bag: sockets across
 * @param itemRows         and down
 * @param itemSlot         how big each of them is
 * @param skillSlot        a skill's socket
 * @param ultimateSlot     the ultimate's, which is drawn proud of the others
 * @param ultimateKey      the key whose skill is the ultimate; any other key gets an ordinary socket
 * @param itemColours      what the drawing in each bag socket is coloured, socket by socket, round again
 *                         when the bag has more sockets than colours
 * @param cardTopColour    the card that opens over a skill or an attribute, at its top
 * @param cardFootColour   and at its foot
 * @param cardRuleColour   the rule under its heading
 * @param cardQuietColour  what it says in passing: what a thing costs, what it is called
 * @param cardBodyColour   what it says at length
 * @param cardValueColour  the numbers in it, which are what is being read
 */
public record PanelLook(
        @Group("Arrangement") List<PanelBlock> blocks, List<PanelPlace> places, float designWidth, float minScale,
        float maxScale,
        @Group("Sockets") int itemColumns, int itemRows, float itemSlot, float skillSlot, float ultimateSlot,
        char ultimateKey,
        @Group("Stone") int slabTopColour, int slabHighColour, int slabMidColour, int slabLowColour,
        int slabRimColour, int stoneDeepColour, int stoneColour, int stoneLitColour, int stoneDeadColour,
        int stoneDeadLitColour, int socketRimColour, int holeColour, int edgeColour, int dropColour,
        int portraitTopColour, int portraitBottomColour, int portraitRimColour, int itemTopColour,
        int itemBottomColour,
        @Group("Colours") int torchColour, int goldColour, int goldHiColour, int gainColour, int boneColour,
        int bloodColour, int arcaneColour, int manaColour, int manaDeniedColour, int manaWashColour,
        int deadColour, int glyphColdColour, int pipDarkColour, int selStoneColour, int selStoneLitColour,
        int labelColour, int lockLabelColour, int itemNumberColour, int badgeColour, int fleshColour,
        List<Integer> itemColours,
        @Group("Card") int cardTopColour, int cardFootColour, int cardRuleColour, int cardQuietColour,
        int cardBodyColour, int cardValueColour) {

    /** The bar as the design drew it. */
    public static final PanelLook DEFAULTS = new PanelLook(
            List.of(PanelBlock.MINIMAP, PanelBlock.HERO, PanelBlock.BAG, PanelBlock.SKILLS, PanelBlock.DEPTH),
            List.of(),
            1300f, 0.55f, 1.30f,
            3, 2, 42f, 58f, 64f, 'R',
            0x4A4033, 0x3A3127, 0x2B241C, 0x221C16, 0x6B5C46, 0x16130F, 0x332B22, 0x4A4033, 0x191510,
            0x231F1A, 0x453D30, 0x0C0A08, 0x100D0A, 0x0A0806, 0x4A4034, 0x241E17, 0x5A4E3C, 0x3E3529,
            0x201A14,
            0xE8A33D, 0xC9A24B, 0xF0D48A, 0x7FBF6A, 0xD9CFBA, 0xA8322B, 0x5F8C7B, 0x3E6FA8, 0xE06A5A,
            0x9EC7FF, 0x4A443B, 0x6A6154, 0x2A241D, 0x16302E, 0x2E4A46, 0x8B8171, 0x6E6555, 0x7A7062,
            0x4A3A18, 0x6B5B45,
            List.of(0xC4564A, 0x5F8C7B, 0xC9A24B, 0xE8A33D, 0x8FA8C4, 0xB08CC4),
            0x2E2820, 0x191510, 0x3A322A, 0x8A7F6C, 0xA69B87, 0xDCD2BC);

    public PanelLook {
        blocks = List.copyOf(blocks);
        places = List.copyOf(places);
        var drawn = new HashSet<>(blocks);
        if (drawn.size() != blocks.size()) {
            throw new IllegalArgumentException("a block is on the bar once, not " + blocks);
        }
        for (var place : places) {
            if (!drawn.add(place.block())) {
                throw new IllegalArgumentException(place.block() + " stands on the bar or on its own, not both");
            }
        }
        designWidth = Math.max(1f, designWidth);
        minScale = Math.max(0.05f, minScale);
        maxScale = Math.max(minScale, maxScale);
        itemColumns = Math.max(1, itemColumns);
        itemRows = Math.max(1, itemRows);
        itemSlot = Math.max(4f, itemSlot);
        skillSlot = Math.max(4f, skillSlot);
        ultimateSlot = Math.max(4f, ultimateSlot);
        itemColours = itemColours.isEmpty() ? DEFAULTS.itemColours : List.copyOf(itemColours);
    }
}
