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
 * @param rememberedLight how brightly ground the player has left is drawn, against
 *                        1 for ground in sight
 * @param softenCells     how many cells the edge of the light is spread over; 0
 *                        leaves the hard staircase the cells really are
 * @param openPerSecond   how fast the fog moves toward what it ought to be, as a
 *                        share of the remaining gap each second
 * @param tint            what unlit stone fades toward — the fog's own colour,
 *                        which is not black in any game worth looking at
 */
public record Fog(boolean lineOfSight, float rememberedLight, int softenCells,
        float openPerSecond, int tint) {

    /** What the client did before any game asked: no line of sight, a black dark. */
    public static final Fog DEFAULT = new Fog(false, 0.34f, 1, 7f, 0x000000);

    public Fog {
        rememberedLight = Math.clamp(rememberedLight, 0f, 1f);
        softenCells = Math.max(0, softenCells);
        openPerSecond = Math.max(0.01f, openPerSecond);
    }

    /** The tint as the renderer wants it. */
    public ColorRGBA tintColour() {
        return new ColorRGBA(((tint >> 16) & 0xFF) / 255f, ((tint >> 8) & 0xFF) / 255f,
                (tint & 0xFF) / 255f, 1f);
    }
}
