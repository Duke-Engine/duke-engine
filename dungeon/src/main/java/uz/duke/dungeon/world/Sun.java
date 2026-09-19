package uz.duke.dungeon.world;

/**
 * The light. Look and nothing else, like the fog: the simulation never reads it.
 *
 * @param pitch           how far above the horizon the sun stands, in degrees — the number that
 *     decides whether the map has any depth in it. A floor and the lid over a wall are the same
 *     tile facing the same way, so only light arriving at an angle can shade one differently from
 *     the other; at 90 there is no angle, and a player cannot see where he may walk
 * @param yaw             which way round the compass it comes from, deciding which face is lit
 * @param strengthPercent how bright the sun is, as a percentage of its own colour
 * @param ambientPercent  how much light a face the sun never reaches still gets. Raising it stops
 *     an unlit wall being a black shape; raising it too far flattens the picture again — at 100
 *     every face is lit alike, which is what the pitch is there to prevent
 * @param colour          what colour the sunlight is, packed {@code 0xRRGGBB}
 * @param ambientTint     what colour the shadowed side is, packed {@code 0xRRGGBB}
 */
public record Sun(int pitch, int yaw, int strengthPercent, int ambientPercent, int colour, int ambientTint) {

    /** What a block leaves out. */
    public static final Sun DEFAULTS = new Sun(57, 219, 100, 50, 0xFFF7E6, 0xE6E6FF);
}
