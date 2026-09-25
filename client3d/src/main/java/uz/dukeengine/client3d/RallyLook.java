package uz.dukeengine.client3d;

/**
 * How a selected building's rally point is shown — the reference's {@code RallyPointMarker} and {@code
 * W3DWaypointBuffer}: its flag standing on the rally point while it alone is selected, in its owner's colour on the parts
 * the game's house colour names; a node at its natural rally point and at each corner of the way; and a line through
 * them, its picture stretched once over each leg and times its colour, added to what is under it and drawn over
 * everything.
 *
 * @param flag       the flag's model, or null for none
 * @param flagClip   the clip the flag plays over and over, or null
 * @param flagFacing the way the flag faces, radians — the reference's {@code DownwindAngle}
 * @param node       the node's model, or null for none
 * @param width      how wide the line is, world units; 0 for no line
 * @param colour     the line's colour, {@code 0xRRGGBB}, its picture multiplied by it
 * @param texture    the line's picture, or null for its colour alone
 */
public record RallyLook(String flag, String flagClip, float flagFacing, String node, float width, int colour,
        String texture) {

    /** The reference's line, 1.5 wide in (0.25, 0.5, 1.0), and no model: what a game that names none is shown. */
    public static final RallyLook DEFAULT = new RallyLook(null, null, -0.785f, null, 1.5f, 0x4080FF, null);
}
