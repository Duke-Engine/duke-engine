package uz.duke.client3d;

/**
 * A modular kit the client can build a floor out of, instead of coloured blocks.
 *
 * <p>Three pieces are enough for a dungeon: a floor tile, a wall that stands on a
 * tile's edge, and a post for the notch where two walls meet at a right angle.
 * Everything else a kit ships — doors, stairs, prefabricated rooms — belongs to a
 * game that lays its world out by hand, not to one that generates it.
 *
 * <p>{@link #tileSize} is what the pieces were modelled at, and the only number
 * that has to be right: the client scales it to the map's own cell size, so a kit
 * authored at 4 units and a map of 10-unit cells meet without anything being
 * nudged into place.
 *
 * <p>A game that never asks for one is drawn exactly as before, in blocks.
 */
public final class Tileset {

    private String floor;
    private String wall;
    private String corner;
    private float tileSize = 4f;

    private Tileset() {
    }

    public static Tileset create() {
        return new Tileset();
    }

    /** The flat piece laid on open ground. */
    public Tileset floor(String assetPath) {
        this.floor = assetPath;
        return this;
    }

    /**
     * The piece that stands along one edge of a tile.
     *
     * <p>An edge piece, not a block: it is placed on the line between open ground
     * and solid, which is where the pathfinder stops the player.
     */
    public Tileset wall(String assetPath) {
        this.wall = assetPath;
        return this;
    }

    /** The post that fills the notch where two walls meet at a right angle. */
    public Tileset corner(String assetPath) {
        this.corner = assetPath;
        return this;
    }

    /** How wide one tile is in the model's own units. Kenney's kits are 4. */
    public Tileset tileSize(float modelUnits) {
        this.tileSize = modelUnits;
        return this;
    }

    public String getFloor() {
        return floor;
    }

    public String getWall() {
        return wall;
    }

    public String getCorner() {
        return corner;
    }

    public float getTileSize() {
        return tileSize;
    }

    /** Whether there is enough here to build a floor: ground to stand on, at least. */
    public boolean isUsable() {
        return floor != null && tileSize > 0f;
    }
}
