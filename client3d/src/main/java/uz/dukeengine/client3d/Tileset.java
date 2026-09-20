package uz.dukeengine.client3d;

/**
 * A modular kit the client can build a floor out of, instead of coloured blocks.
 *
 * <p>Four pieces are enough for a dungeon: a floor tile, a wall that stands on a
 * tile's edge, a post for the notch where two walls meet at a right angle, and a
 * flight of steps for a map with more than one storey to it. Everything else a
 * kit ships — doors, prefabricated rooms, furniture — belongs to a game that lays
 * its world out by hand, not to one that generates it.
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
    private String stairs;
    private String rockFace;
    private float tileSize = 4f;
    private float wallTileSize = 0f;
    private float wallHeight = 4f;
    private float wallLift;
    private float wallShift;
    private boolean ownMaterials;
    private boolean wallFillsRock;
    private int wallClump = 1;
    private float wallSpread;
    private float wallVariety;
    private int tint = 0xFFFFFF;
    private int capTint = 0xFFFFFF;
    private float storeyShade = 1f;

    private Tileset() {
    }

    /**
     * Whether the wall piece <em>fills</em> a piece of rock rather than facing it.
     *
     * <p>Masonry faces. A wall is a surface on the boundary the player is stopped
     * at: one piece per face, one course per storey. A block of rock a single cell
     * thick is walled from both sides and that is right, because stone has two
     * faces and you can stand on either of them; a taller drop is more courses,
     * every one the same size, because that is how a wall is built.
     *
     * <p>A tree is not a surface, and every one of those is wrong for it. It has
     * one body, so drawing both faces of a thin wall drew the same tree twice —
     * once at the foot of the rock and once on the roof, with the lid between them,
     * which is what two storeys of forest looked like.
     *
     * <p>So a kit that says yes here is drawn once per piece of rock, in the middle
     * of it and <b>on top of it</b>, at its own size whatever the rock is. The rock
     * is the thing; the tree is what grows on it.
     *
     * <p>Which leaves the sides of the rock, and they are not this piece's to draw:
     * see {@link #rockFace}.
     */
    public Tileset wallFillsRock(boolean fills) {
        this.wallFillsRock = fills;
        return this;
    }

    public boolean wallFillsRock() {
        return wallFillsRock;
    }

    /**
     * How many pieces stand where the layout asks for one wall. One by default,
     * which is a wall.
     *
     * <p>More than one is for the kit whose wall is a thing rather than a surface.
     * One tree per cell on a square grid reads as a plantation — the picture the
     * eye gets is the grid, not the wood — and no amount of better art fixes that,
     * because the regularity is the problem. Three of them in a ring, each a
     * different size and facing, and the grid disappears behind them.
     *
     * @see #wallSpread
     * @see #wallVariety
     */
    public Tileset wallClump(int pieces) {
        this.wallClump = Math.max(1, pieces);
        return this;
    }

    public int getWallClump() {
        return wallClump;
    }

    /**
     * How far a clump's pieces stand from the point the plan gives them, as a
     * fraction of a cell.
     *
     * <p>A piece filling a block of rock scatters about the middle of it and keeps
     * to its own cell. A piece facing a boundary scatters <em>behind</em> the line
     * by the ring's own radius, so what leans out over open ground is canopy rather
     * than trunk: the boundary the player is stopped at stays where the pathfinder
     * put it.
     */
    public Tileset wallSpread(float fractionOfCell) {
        this.wallSpread = fractionOfCell;
        return this;
    }

    public float getWallSpread() {
        return wallSpread;
    }

    /**
     * How much the pieces of a clump differ in size, as a fraction either way —
     * {@code 0.4} is anything from four fifths to six fifths of full size.
     */
    public Tileset wallVariety(float fraction) {
        this.wallVariety = fraction;
        return this;
    }

    public float getWallVariety() {
        return wallVariety;
    }

    /**
     * How wide the <em>wall</em> pieces were modelled, when that is not the floor's
     * own tile size.
     *
     * <p>Kits are not all authored on one module. One of the kits this client draws
     * lays its rooms out of two-unit floor tiles and closes them with four-unit
     * walls — each wall spans two tiles. Scaled by the floor's number a wall like
     * that comes out twice as wide as the cell it stands on and twice as tall as
     * it should be; scaled by its own it lands exactly.
     *
     * <p>Defaults to the tile size, which is the usual case and what every kit did
     * before this existed.
     */
    public Tileset wallTileSize(float modelUnits) {
        this.wallTileSize = modelUnits;
        return this;
    }

    public float getWallTileSize() {
        return wallTileSize > 0f ? wallTileSize : tileSize;
    }

    /**
     * How far up a wall has to be moved to stand on the floor, in model units.
     *
     * <p>Where a kit puts a wall's origin is the kit's own business and they do not
     * agree: one stands its walls on the ground, another centres them, and a
     * centred wall drawn as authored is half sunk into the floor.
     */
    public Tileset wallLift(float modelUnits) {
        this.wallLift = modelUnits;
        return this;
    }

    public float getWallLift() {
        return wallLift;
    }

    /**
     * How far back a wall has to be moved for its face to land on the boundary,
     * in model units — negative moves it into the stone.
     *
     * <p>The face of a wall belongs on the line the pathfinder will not let anyone
     * cross, and its body behind that line. A kit that centres its wall slab on
     * its origin puts half the slab into the room.
     */
    public Tileset wallShift(float modelUnits) {
        this.wallShift = modelUnits;
        return this;
    }

    public float getWallShift() {
        return wallShift;
    }

    /**
     * Keep the materials the models were shipped with, rather than putting the
     * kit's own skin on every piece.
     *
     * <p>Which is right depends entirely on the kit. One drawn on a single colour
     * atlas wants one material for the lot — it is the same picture on every piece,
     * and sharing it is most of what keeps a floor of six hundred tiles cheap. One
     * that carries no texture at all and says what colour each of its parts is
     * wants exactly what it came with; giving those a single skin paints the whole
     * kit one flat grey and throws away the only colour it had.
     */
    public Tileset ownMaterials(boolean keep) {
        this.ownMaterials = keep;
        return this;
    }

    public boolean keepsOwnMaterials() {
        return ownMaterials;
    }

    /**
     * A colour multiplied over the whole kit, packed {@code 0xRRGGBB}.
     *
     * <p>White leaves it alone. What it is for is telling two floors built from the
     * same kit apart — the same stone, colder or warmer — without a second set of
     * models.
     */
    public Tileset tint(int packedRgb) {
        this.tint = packedRgb;
        return this;
    }

    public int getTint() {
        return tint;
    }

    /**
     * A second colour, over the lid on the rock only, packed {@code 0xRRGGBB}.
     *
     * <p>The lid is a floor tile — the same model, the same picture — laid on top
     * of the stone instead of under the room. That is right, and it is also the
     * reason a player cannot tell one from the other: two surfaces facing the same
     * way, drawn from the same texture, lit by one sun that meets both at the same
     * angle. The picture has a top and a bottom and nothing says which is which,
     * and the answer the player needs from it is which of the two he can walk on.
     *
     * <p>Darker here is worth more than it looks. It costs one extra material for
     * the whole floor — the skins are already kept per tint — and no extra piece,
     * no second pass and no light.
     *
     * <p>White leaves the lid looking exactly like the floor, which is what every
     * kit did before this existed.
     */
    public Tileset capTint(int packedRgb) {
        this.capTint = packedRgb;
        return this;
    }

    public int getCapTint() {
        return capTint;
    }

    /**
     * How much lighter each storey up is drawn, as a multiplier.
     *
     * <p>One is flat, which is what a one-storey map wants and what every kit had.
     * Compounded per storey, so two floors up is the square of it.
     *
     * <p>The same problem as {@link #capTint} one level out. A raised room is a
     * floor of the same tiles at a different height, and from a camera looking
     * down a slope there is very little to say it is not simply further away. It
     * costs one material per storey — three at most, since that is as many as the
     * generator builds.
     *
     * <p>Worked <b>down from the tallest storey the map has</b> rather than up from
     * the ground, because a tint multiplies and multiplying can only darken: asked
     * for a floor half again as bright, a white tile hands back the white it
     * already was and the setting quietly does nothing. Counting down says the same
     * thing the other way round — the top floor is left alone and the ones under it
     * step down — so a value above one lightens as you climb, which is what it
     * reads as.
     */
    public Tileset storeyShade(float multiplier) {
        this.storeyShade = multiplier > 0f ? multiplier : 1f;
        return this;
    }

    public float getStoreyShade() {
        return storeyShade;
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

    /**
     * A flight of steps from one storey to the next.
     *
     * <p>Optional, like the corner post: a kit without one gets steps built from
     * plain blocks, which is what every kit got before this existed. The model is
     * scaled to the cell it stands on and the storey it climbs, and turned by
     * whichever way its own steps happen to face -- kits do not agree about that
     * any more than they agree about where a wall origin sits.
     */
    public Tileset stairs(String assetPath) {
        this.stairs = assetPath;
        return this;
    }

    /**
     * What the exposed side of a block of rock is drawn with, for a kit whose own
     * wall cannot draw it.
     *
     * <p>Only a kit whose wall is a <em>thing</em> needs one. Masonry walls a
     * two-storey block of rock in two courses, and those courses <b>are</b> the
     * sides of it; a tree is drawn once on top and the sides are left to nothing,
     * so a tree crowning the rock beside a room two storeys up stands in the air.
     *
     * <p>A retaining wall, then, and it is drawn from the faces the layout worked
     * out anyway — see {@link TileLayout.Piece#ROCK_FACE}. Not a body filling the
     * block: the inside of a mass of rock is not visible and drawing it would be
     * paying for what nobody sees.
     *
     * <p>Scaled by what it measures rather than by a number written down, like the
     * stair: its height is made to fill one storey exactly, uniformly, so a kit is
     * right by being shipped and a slab is never stretched into a different shape.
     *
     * <p>Drawn only where the rock <b>stands above</b> the foot of the face. A kit
     * that lays its lids on the ground — a wood, where what you cannot walk into is
     * a tree line rather than a wall — has rock at the floor's own height almost
     * everywhere, and a retaining wall under every tree would be a stone kerb round
     * the whole forest.
     *
     * <p>A kit that names none is drawn exactly as it was before this existed.
     */
    public Tileset rockFace(String assetPath) {
        this.rockFace = assetPath;
        return this;
    }

    public String getRockFace() {
        return rockFace;
    }

    /**
     * How tall the wall piece stands, in the same model units as the tile size.
     *
     * <p>What it is for is the lid over the stone: a roof has to sit level with
     * the tops of the walls, and how tall those are is a fact about the kit rather
     * than about the map.
     */
    public Tileset wallHeight(float modelUnits) {
        this.wallHeight = modelUnits;
        return this;
    }

    public float getWallHeight() {
        return wallHeight;
    }

    /** How wide one tile is in the model's own units. KayKit's are 4. */
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

    public String getStairs() {
        return stairs;
    }

    public float getTileSize() {
        return tileSize;
    }

    /** Whether there is enough here to build a floor: ground to stand on, at least. */
    public boolean isUsable() {
        return floor != null && tileSize > 0f;
    }
}
