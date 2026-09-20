package uz.dukeengine.dungeon.world;

/**
 * The modular kit a floor is drawn from when no theme says otherwise, each piece a whole path.
 *
 * <p>Look rather than rule: the simulation never reads it. It is here because it is a thing someone
 * retunes — swap the kit, swap the dungeon's whole appearance.
 *
 * @param floor null when no kit is named, meaning plain blocks
 */
public record Tiles(String floor, String wall, String corner, String stairs, float tileSize, float wallHeight,
        float wallLift, float wallShift) {

    /** What a block leaves out. */
    public static final Tiles DEFAULTS = new Tiles(null, null, null, null, 4f, 4f, 0f, 0f);
}
