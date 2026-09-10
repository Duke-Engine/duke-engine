package uz.duke.client3d;

import com.jme3.math.ColorRGBA;

/**
 * How a game wants its fog to behave and to look.
 *
 * <p>The client knows how to hide a map and open it up again; what the dark
 * should be worth is the game's. A crawler wants walls that stop sight and a
 * remembered room you can still find your way back through; a game that only
 * wants the map hidden until someone walks it wants none of that. So the numbers
 * come from the game, through {@link Visuals#fog}, and a game that never asks
 * keeps exactly what it had.
 *
 * @param lineOfSight     whether stone blocks sight, so a room is not seen through
 *                        its own wall
 * @param unseenLight     how brightly ground nobody has walked is drawn; 0 is the
 *                        black a dungeon wants, and anything above it draws the
 *                        whole map faintly from the first frame
 * @param rememberedLight how brightly ground the player has left is drawn
 * @param visibleLight    how brightly ground in sight this instant is drawn; 1 is
 *                        the scene at its own brightness and nothing over it
 * @param softenCells     how many cells the edge of the light is spread over; 0
 *                        leaves the hard staircase the cells really are
 * @param openPerSecond   how fast the fog moves toward what it ought to be, as a
 *                        share of the remaining gap each second
 * @param textureSize     how many texels across the fog layer is drawn at, whatever
 *                        the map's own size — see {@link FogOverlay}
 * @param tint            what unlit ground fades toward — the fog's own colour,
 *                        which is not black in any game worth looking at
 */
public record Fog(boolean lineOfSight, float unseenLight, float rememberedLight,
        float visibleLight, int softenCells, float openPerSecond, int textureSize, int tint) {

    /** What the client did before any game asked: no line of sight, a black dark. */
    public static final Fog DEFAULT = new Fog(false, 0f, 0.34f, 1f, 1, 7f, 256, 0x000000);

    public Fog {
        unseenLight = Math.clamp(unseenLight, 0f, 1f);
        rememberedLight = Math.clamp(rememberedLight, 0f, 1f);
        visibleLight = Math.clamp(visibleLight, 0f, 1f);
        softenCells = Math.max(0, softenCells);
        openPerSecond = Math.max(0.01f, openPerSecond);
        textureSize = Math.clamp(textureSize, 8, 2048);
    }

    /** The tint as the renderer wants it. */
    public ColorRGBA tintColour() {
        return new ColorRGBA(((tint >> 16) & 0xFF) / 255f, ((tint >> 8) & 0xFF) / 255f,
                (tint & 0xFF) / 255f, 1f);
    }
}
