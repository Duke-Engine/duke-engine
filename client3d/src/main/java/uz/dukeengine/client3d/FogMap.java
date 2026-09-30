package uz.dukeengine.client3d;

import com.jme3.math.Vector2f;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;
import java.nio.ByteBuffer;
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
 *
 * <p>On a world too wide to draw whole the picture is not of the map but of the discovery's window round the camera
 * ({@link Discovery#window}), laid over the ground a copy after a copy: a place of the ground always has the same texel,
 * the texels of the ground leaving the window are the ones the ground coming into it takes, and only those are drawn as
 * the camera moves. Past the window the picture holds another place's dark, and the shader draws it never seen.
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
    /** The cells within one of a cell whose light moved. */
    private final CellSet near = new CellSet();
    /** What of the picture changed since it was last sent to the card. */
    private final TextureTiles tiles = new TextureTiles();

    /** What colour nothing is. The game's, until a theme says otherwise. */
    private com.jme3.math.ColorRGBA tint;

    /** Whether the picture is of the discovery's window round the camera, laid over and over, not of the whole map. */
    private boolean windowed;
    /** How many cells a side the window is, where the picture is of one. */
    private int windowCells;
    /** How much ground a texel is, where the picture is of a window. */
    private float texelSize;
    /** The first texel of the ground the window holds now, across and down, counted from the map's corner. */
    private int liveX;
    private int liveY;
    /** A number that moves each time the window does, for whatever is told where the picture holds. */
    private int windowVersion;

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
        windowed = false;
        texture.setWrap(Texture.WrapMode.EdgeClamp);
        worldWidth = grid == null ? 0f : grid.getWidth() * grid.getCellSize();
        worldHeight = grid == null ? 0f : grid.getHeight() * grid.getCellSize();
        cellSize = grid == null ? PathGrid.DEFAULT_CELL_SIZE : grid.getCellSize();
        cellsWide = grid == null ? 0 : grid.getWidth();
        cellsDeep = grid == null ? 0 : grid.getHeight();
        int perCell = fog.texelsPerCell();
        boolean sized = perCell > 0 && grid != null;
        if (sized || perCell == 0) {
            // The fixed size is the one it was made at, whatever a window last made it.
            int wide = sized ? Math.clamp((long) cellsWide * perCell, 1, MOST_TEXELS) : fog.textureSize();
            int deep = sized ? Math.clamp((long) cellsDeep * perCell, 1, MOST_TEXELS) : fog.textureSize();
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
     * The same, the picture of {@code seen}'s window round the camera where it keeps one ({@link Discovery#window}) —
     * {@link Fog#texelsPerCell} texels a cell, or the fog's {@link Fog#textureSize} across the window — laid over the
     * ground a copy after a copy; the whole map's where it keeps the whole map.
     */
    void resize(PathGrid grid, Discovery seen) {
        if (grid == null || seen == null || !seen.isWindowed()) {
            resize(grid);
            return;
        }
        windowed = true;
        cellSize = grid.getCellSize();
        cellsWide = grid.getWidth();
        cellsDeep = grid.getHeight();
        windowCells = seen.windowWide();
        int across = fog.texelsPerCell() > 0 ? Math.clamp((long) windowCells * fog.texelsPerCell(), 1, MOST_TEXELS)
                : fog.textureSize();
        texelSize = windowCells * cellSize / across;
        worldWidth = windowCells * cellSize;
        worldHeight = windowCells * cellSize;
        if (across != width || across != height) {
            width = across;
            height = across;
            texels = BufferUtils.createByteBuffer(width * height * BYTES_PER_TEXEL);
            image = new Image(Image.Format.RGBA8, width, height, texels, ColorSpace.Linear);
            texture.setImage(image);
        }
        // Past the last texel is the first again: a place of the ground is always the same texel.
        texture.setWrap(Texture.WrapMode.Repeat);
        fillWithDark();
        image.setUpdateNeeded();
        tiles.resize(width, height);
        everyTexel = true;
        liveX = firstTexelOf(seen.windowX());
        liveY = firstTexelOf(seen.windowY());
        windowVersion++;
    }

    /** Whether the picture is of a window round the camera. */
    boolean isWindowed() {
        return windowed;
    }

    /**
     * The ground the picture holds now, where it is of a window — x and z from, then x and z to, a texel in from its
     * edge, where the card's filtering reads no texel of another place — for the shader to draw past it never seen.
     */
    com.jme3.math.Vector4f window() {
        return new com.jme3.math.Vector4f((liveX + 1) * texelSize, (liveY + 1) * texelSize,
                (liveX + width - 1) * texelSize, (liveY + height - 1) * texelSize);
    }

    /** A number that moves each time the ground the picture holds does. */
    int windowVersion() {
        return windowVersion;
    }

    /** The first texel of the ground the window holds now, across, counted from the map's corner. */
    int liveX() {
        return liveX;
    }

    /** The same, down. */
    int liveY() {
        return liveY;
    }

    /** How much ground a texel is, where the picture is of a window. */
    float texelSize() {
        return texelSize;
    }

    /**
     * The first texel whose middle lies at or past the start of cell {@code cell}: the texel of the ground at {@code
     * (j + 0.5) * windowCells / width} cells, counted exactly.
     */
    private int firstTexelOf(int cell) {
        return (int) Math.ceilDiv(2L * cell * width - windowCells, 2L * windowCells);
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
        if (windowed) {
            updateWindow(seen);
            tiles.send(renderer, texture, image, texels);
            return;
        }
        if (everyTexel) {
            redraw(seen, 0, width - 1, 0, height - 1);
            everyTexel = false;
        } else {
            redrawNear(seen);
        }
        tiles.send(renderer, texture, image, texels);
    }

    /**
     * The window's picture brought up to the discovery: the ground come into the window drawn — its cells are among the
     * ones whose light moved — and the texels of every cell within one of a cell whose light moved, those of the ground
     * the window holds now and no others.
     */
    private void updateWindow(Discovery seen) {
        int nowX = firstTexelOf(seen.windowX());
        int nowY = firstTexelOf(seen.windowY());
        if (nowX != liveX || nowY != liveY) {
            liveX = nowX;
            liveY = nowY;
            windowVersion++;
        }
        if (everyTexel) {
            redrawWindow(seen, liveX, liveX + width - 1, liveY, liveY + height - 1);
            everyTexel = false;
            return;
        }
        gatherNear(seen);
        for (int i = 0; i < near.size(); i++) {
            int cx = near.get(i) % cellsWide;
            int cy = near.get(i) / cellsWide;
            redrawWindow(seen, firstTexelOf(cx), firstTexelOf(cx + 1) - 1, firstTexelOf(cy), firstTexelOf(cy + 1) - 1);
        }
    }

    /** The cells within one of a cell whose light moved, each once. */
    private void gatherNear(Discovery seen) {
        near.clear();
        var moved = seen.moved();
        for (int i = 0; i < moved.size(); i++) {
            int cx = moved.get(i) % cellsWide;
            int cy = moved.get(i) / cellsWide;
            for (int y = Math.max(0, cy - 1); y <= Math.min(cellsDeep - 1, cy + 1); y++) {
                for (int x = Math.max(0, cx - 1); x <= Math.min(cellsWide - 1, cx + 1); x++) {
                    near.add(y * cellsWide + x);
                }
            }
        }
    }

    /**
     * Texels {@code fromX..toX} by {@code fromY..toY} of the ground — those the window holds now of them — each asked
     * how dark its ground is, at the place of the picture a texel of the ground always has.
     */
    private void redrawWindow(Discovery seen, int fromX, int toX, int fromY, int toY) {
        for (int ty = Math.max(fromY, liveY); ty <= Math.min(toY, liveY + height - 1); ty++) {
            // The image's first row is the far edge, as the whole map's is: the ground's rows run the other way.
            int row = height - 1 - Math.floorMod(ty, height);
            float worldY = (ty + 0.5f) * texelSize;
            for (int tx = Math.max(fromX, liveX); tx <= Math.min(toX, liveX + width - 1); tx++) {
                int column = Math.floorMod(tx, width);
                byte dark = channel(1f - seen.lightAtPoint((tx + 0.5f) * texelSize, worldY));
                int at = (row * width + column) * BYTES_PER_TEXEL + 3;
                if (texels.get(at) != dark) {
                    texels.put(at, dark);
                    tiles.changed(column, row, column, row);
                }
            }
        }
    }

    /** The texels of every cell within one of a cell whose light moved; whether any of them changed. */
    private boolean redrawNear(Discovery seen) {
        if (seen.moved().isEmpty() || cellsWide == 0) {
            return false;
        }
        gatherNear(seen);
        boolean moved = false;
        for (int i = 0; i < near.size(); i++) {
            int cx = near.get(i) % cellsWide;
            int cy = near.get(i) / cellsWide;
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
