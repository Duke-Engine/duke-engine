package uz.duke.dungeon.world;

/**
 * How the dark behaves and what colour it is.
 *
 * <p>Look rather than rule, like the tile kit and a monster's colour: the simulation never reads
 * it. What the player can see does not change what is there — which is what makes fog something
 * the client may have an opinion about at all.
 *
 * @param unseenPercent     how brightly ground nobody has walked is drawn; 0 is a black floor
 * @param rememberedPercent how brightly a room he has left is drawn, as a percentage of a lit one
 * @param visiblePercent    how brightly a room in sight is drawn; 100 is the scene's own light
 * @param softenCells       how many cells the edge of the light is smeared over
 * @param openPerSecond     how fast the dark gives way, as a share of the remaining gap per second
 * @param textureSize       how many texels across the fog sheet is drawn — nothing to do with cells
 * @param tint              what unlit stone fades toward — the colour of the dark itself
 */
public record Fog(boolean lineOfSight, int unseenPercent, int rememberedPercent, int visiblePercent,
        int softenCells, int openPerSecond, int textureSize, int tint) {

    /** What a block leaves out. */
    public static final Fog DEFAULTS = new Fog(true, 0, 34, 100, 2, 7, 256, 0x000000);
}
