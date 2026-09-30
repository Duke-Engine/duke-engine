package uz.dukeengine.client3d;

import com.jme3.math.Vector2f;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;
import java.nio.ByteBuffer;
import java.util.BitSet;
import uz.dukeengine.core.pathfind.PathGrid;

/**
 * The dark, as a picture of the map rather than as a property of anything in it.
 *
 * <p>Fog began as a shade per cell, painted flat over every tile in that cell —
 * a floor of ten-unit squares, however carefully the numbers behind them had been
 * blurred. The softening was real; it was happening at the size of a cell.
 *
 * <p>So the dark becomes a texture whose size the game chooses and which has
 * nothing to do with how big a cell is. Every texel asks the discovery how bright
 * its own point of ground is rather than which cell it falls in, and the card
 * fills in between texels for nothing.
 *
 * <p>What the texture is <em>not</em> is a sheet hung over the world. That was the
 * first attempt and it has a flaw no amount of care removes: a flat sheet lines up
 * with the world at one height only, so a surface at any other height is dimmed by
 * the ground a fifth of a cell behind it for every unit it stands above the floor.
 * A wall would go dark halfway up. Instead the terrain's own material samples this
 * texture by world x and z — see {@code MatDefs/duke/FoggedTerrain.frag} — so every
 * fragment of every surface takes the dark at the place it actually stands, and
 * height plays no part in it at all.
 *
 * <p>This class owns the picture and nothing else: no geometry, no material, no
 * decision about how it is drawn.
 */
final class FogMap {

    private static final int BYTES_PER_TEXEL = 4; // rgb: the tint. a: how dark.

    /** The widest a picture sized to its map is made. */
    private static final int MOST_TEXELS = 4096;

    private final Fog fog;
    private int width;
    private int height;
    private ByteBuffer texels;
    private Image image;
    private final Texture2D texture;

    /** How wide and deep the current map is, which is what world x and z scale by. */
    private float worldWidth;
    private float worldHeight;
    private float cellSize = PathGrid.DEFAULT_CELL_SIZE;
    private int cellsWide;
    private int cellsDeep;
    /** Every texel to be drawn at the next update, as for a new map. */
    private boolean everyTexel = true;
    private final BitSet near = new BitSet();
    /** What of the picture changed since it was last sent to the card. */
    private final TextureTiles tiles = new TextureTiles();

    /** What colour nothing is. The game's, until a theme says otherwise. */
    private com.jme3.math.ColorRGBA tint;

    FogMap(Fog fog) {
        this.fog = fog == null ? Fog.DEFAULT : fog;
        this.tint = this.fog.tintColour();
        this.width = this.fog.textureSize();
        this.height = this.fog.textureSize();
        this.texels = BufferUtils.createByteBuffer(width * height * BYTES_PER_TEXEL);
        fillWithDark();
        // Linear, not sRGB: the tint is a ColorRGBA everywhere else in the client
        // -- the window's own background is the same number -- and a texture the
        // card would convert on the way in would not come out the same colour.
        this.image = new Image(Image.Format.RGBA8, width, height, texels, ColorSpace.Linear);
        this.texture = new Texture2D(image);
        tiles.resize(width, height);
        // The whole point of the texture: the card fills in between texels.
        // Nearest would put the squares straight back, only smaller ones.
        texture.setMagFilter(Texture.MagFilter.Bilinear);
        texture.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        // The map's edge is the edge of the dark, not the start of it again.
        texture.setWrap(Texture.WrapMode.EdgeClamp);
    }

    /**
     * What colour the dark is from now on.
     *
     * <p>A game whose floors are meant to look like different places wants a
     * different nothing on each of them — bluish under ice, red under lava. It
     * costs nothing to change because the tint is written into the picture's own
     * texels rather than set on a material: the next fill paints it, and a new
     * floor is about to ask for one.
     */
    void tint(com.jme3.math.ColorRGBA colour) {
        this.tint = colour == null ? fog.tintColour() : colour;
    }

    /**
     * The dark in a new colour at once: every texel's tint rewritten, the darkness kept, and the picture sent again —
     * the fog turning from one look's colour to the next as the camera crosses between them.
     */
    void recolour(com.jme3.math.ColorRGBA colour) {
        tint(colour);
        byte red = channel(tint.r);
        byte green = channel(tint.g);
        byte blue = channel(tint.b);
        for (int texel = 0; texel < width * height; texel++) {
            int at = texel * BYTES_PER_TEXEL;
            texels.put(at, red).put(at + 1, green).put(at + 2, blue);
        }
        image.setUpdateNeeded();
        tiles.resize(width, height);
    }

    /** What colour the texel's dark is, packed {@code 0xRRGGBB}. */
    int tintAt(int texelX, int texelY) {
        int at = (texelY * width + texelX) * BYTES_PER_TEXEL;
        return (texels.get(at) & 0xFF) << 16 | (texels.get(at + 1) & 0xFF) << 8 | texels.get(at + 2) & 0xFF;
    }

    /**
     * Take the shape of a new map and forget the last one's dark.
     *
     * <p>The texture is built once and kept, so a material holds on to it across a run. At a fixed size it is
     * refilled; sized to its map ({@link Fog#texelsPerCell}) it is given a picture the size of the new map — a floor
     * of 160 by 120 cells as sharp as one of 50 by 36, where one fixed picture stretched over both drew the larger in
     * blurred blocks.
     */
    void resize(PathGrid grid) {
        worldWidth = grid == null ? 0f : grid.getWidth() * grid.getCellSize();
        worldHeight = grid == null ? 0f : grid.getHeight() * grid.getCellSize();
        cellSize = grid == null ? PathGrid.DEFAULT_CELL_SIZE : grid.getCellSize();
        cellsWide = grid == null ? 0 : grid.getWidth();
        cellsDeep = grid == null ? 0 : grid.getHeight();
        int perCell = fog.texelsPerCell();
        if (perCell > 0 && grid != null) {
            int wide = Math.clamp((long) cellsWide * perCell, 1, MOST_TEXELS);
            int deep = Math.clamp((long) cellsDeep * perCell, 1, MOST_TEXELS);
            if (wide != width || deep != height) {
                width = wide;
                height = deep;
                texels = BufferUtils.createByteBuffer(width * height * BYTES_PER_TEXEL);
                image = new Image(Image.Format.RGBA8, width, height, texels, ColorSpace.Linear);
                texture.setImage(image);
            }
        }
        fillWithDark();
        image.setUpdateNeeded();
        tiles.resize(width, height);
        everyTexel = true;
    }

    /**
     * Redraw the dark for what the player now knows.
     *
     * <p>Every texel asks about its own point of ground — not the cell it falls in
     * — so a texel between two cell centres gets a value between the two and the
     * picture has a gradient in it before the card has done anything at all.
     *
     * <p>Only the texels whose light could have moved are asked: a texel reads the four cell centres round it, so those
     * within a cell of a cell whose light moved ({@link Discovery#movedCells}). And the upload is skipped when nothing
     * moved. Standing still is the common case in a crawler — reading a panel, choosing a skill — and the fog settles
     * within a second of the hero stopping.
     */
    void update(Discovery seen) {
        update(seen, null);
    }

    /**
     * The same, what changed sent to the card by {@code renderer} a tile of the picture at a time ({@link
     * TextureTiles}); null sends the whole picture again, as it always was.
     */
    void update(Discovery seen, com.jme3.renderer.Renderer renderer) {
        if (seen == null || worldWidth <= 0f || worldHeight <= 0f) {
            return;
        }
        if (everyTexel) {
            redraw(seen, 0, width - 1, 0, height - 1);
            everyTexel = false;
        } else {
            redrawNear(seen, seen.movedCells());
        }
        tiles.send(renderer, texture, image, texels);
    }

    /** The texels of every cell within one of a cell whose light moved; whether any of them changed. */
    private boolean redrawNear(Discovery seen, BitSet movedCells) {
        if (movedCells.isEmpty() || cellsWide == 0) {
            return false;
        }
        near.clear();
        for (int at = movedCells.nextSetBit(0); at >= 0; at = movedCells.nextSetBit(at + 1)) {
            int cx = at % cellsWide;
            int cy = at / cellsWide;
            int fromX = Math.max(0, cx - 1);
            int toX = Math.min(cellsWide - 1, cx + 1);
            for (int y = Math.max(0, cy - 1); y <= Math.min(cellsDeep - 1, cy + 1); y++) {
                near.set(y * cellsWide + fromX, y * cellsWide + toX + 1);
            }
        }
        boolean moved = false;
        for (int at = near.nextSetBit(0); at >= 0; at = near.nextSetBit(at + 1)) {
            int cx = at % cellsWide;
            int cy = at / cellsWide;
            // The texels whose middles stand on this cell's ground: x across, and y the other way round, the image's
            // first row being the map's far edge.
            int fromX = firstTexelFrom(cx * cellSize / worldWidth, width);
            int toX = firstTexelFrom((cx + 1) * cellSize / worldWidth, width) - 1;
            int fromY = firstTexelFrom(1f - (cy + 1) * cellSize / worldHeight, height);
            int toY = firstTexelFrom(1f - cy * cellSize / worldHeight, height) - 1;
            moved |= redraw(seen, fromX, toX, fromY, toY);
        }
        return moved;
    }

    /** The first texel of {@code texels} whose middle lies at or past {@code share} of the way across. */
    private static int firstTexelFrom(float share, int texels) {
        return Math.clamp((int) Math.ceil(share * texels - 0.5f), 0, texels);
    }

    /** Texels {@code fromX..toX} by {@code fromY..toY}, each asked how dark its ground is; whether any changed. */
    private boolean redraw(Discovery seen, int fromX, int toX, int fromY, int toY) {
        boolean moved = false;
        int leftmost = Integer.MAX_VALUE;
        int rightmost = -1;
        int topmost = Integer.MAX_VALUE;
        int bottommost = -1;
        for (int ty = fromY; ty <= toY; ty++) {
            // The image's first row is the map's far edge: v runs the other way,
            // and the shader that reads this agrees with it.
            float worldY = worldHeight * (1f - (ty + 0.5f) / height);
            for (int tx = fromX; tx <= toX; tx++) {
                float worldX = worldWidth * (tx + 0.5f) / width;
                byte dark = channel(1f - seen.lightAtPoint(worldX, worldY));
                int at = (ty * width + tx) * BYTES_PER_TEXEL + 3;
                if (texels.get(at) != dark) {
                    texels.put(at, dark);
                    moved = true;
                    leftmost = Math.min(leftmost, tx);
                    rightmost = Math.max(rightmost, tx);
                    topmost = Math.min(topmost, ty);
                    bottommost = Math.max(bottommost, ty);
                }
            }
        }
        tiles.changed(leftmost, topmost, rightmost, bottommost);
        return moved;
    }

    /** The picture, for whatever material wants to read the dark out of it. */
    Texture texture() {
        return texture;
    }

    /** The map's width and depth, which is what a world position divides by. */
    Vector2f worldSize() {
        return new Vector2f(Math.max(worldWidth, 1f), Math.max(worldHeight, 1f));
    }

    /** How dark one texel is drawn, from 0 for clear to 1 for the tint alone. */
    float darknessAt(int texelX, int texelY) {
        if (texelX < 0 || texelY < 0 || texelX >= width || texelY >= height) {
            return 1f;
        }
        return (texels.get((texelY * width + texelX) * BYTES_PER_TEXEL + 3) & 0xFF) / 255f;
    }

    /** How many texels across the picture is — the number the game named, at a fixed size. */
    int size() {
        return width;
    }

    /** How many texels across and deep the picture is now. */
    int width() {
        return width;
    }

    int height() {
        return height;
    }

    private void fillWithDark() {
        byte red = channel(tint.r);
        byte green = channel(tint.g);
        byte blue = channel(tint.b);
        texels.clear();
        for (int texel = 0; texel < width * height; texel++) {
            texels.put(red).put(green).put(blue).put((byte) 0xFF); // nobody has been anywhere
        }
        texels.flip();
    }

    private static byte channel(float value) {
        return (byte) Math.round(Math.clamp(value, 0f, 1f) * 255f);
    }
}
