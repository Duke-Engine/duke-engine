package uz.dukeengine.client3d;

import com.jme3.math.ColorRGBA;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;
import java.nio.ByteBuffer;
import uz.dukeengine.core.pathfind.PathGrid;

/**
 * The minimap's ground as one picture, a texel a cell.
 *
 * <p>It was a square a cell, each a geometry of its own and each given its material again every frame: on a floor of
 * 160 by 120 cells, nineteen thousand things drawn one at a time in the corner of the screen. A picture is one quad
 * drawn once, and a frame repaints only the cells whose state the discovery says changed.
 *
 * <p>The colours are the ones the squares had, and taken as they are written — a linear picture, as the fog's is — so
 * the little map is the same colour of nothing as the world it stands for. Without discovery it is the ground and the
 * stone, painted once.
 *
 * <p>On a world too wide for a corner of the screen the picture is not of the map but of a {@link #span} of cells round
 * where the camera looks ({@link #follow}), a cell more each way so it may slide smoothly under its quad, laid a texel
 * a cell over and over: a cell is always the same texel, and only the cells coming into the window are painted as it
 * moves.
 */
final class MinimapPicture {

    private static final int BYTES_PER_TEXEL = 4;

    private static final ColorRGBA GROUND = new ColorRGBA(0.10f, 0.14f, 0.08f, 1f);
    private static final ColorRGBA STONE = new ColorRGBA(0.35f, 0.32f, 0.26f, 1f);
    private static final ColorRGBA LIT_FLOOR = new ColorRGBA(0.16f, 0.22f, 0.13f, 1f);

    private final Texture2D texture = new Texture2D(1, 1, Image.Format.RGBA8);
    private ByteBuffer texels = BufferUtils.createByteBuffer(BYTES_PER_TEXEL);
    private Image image;
    private PathGrid grid;
    private boolean discovered;
    private boolean everyCell;
    /** What unwalked ground fades to: the fog's own colour. */
    private ColorRGBA dark = ColorRGBA.Black;
    /** What of the picture changed since it was last sent to the card. */
    private final TextureTiles tiles = new TextureTiles();

    /** Whether the picture is of a window round the camera, laid over and over, not of the whole map. */
    private boolean windowed;
    /** How many cells a side the window shows, where the picture is of one. */
    private int span;
    /** How many cells a side the window's picture holds: the span, and a cell more each way. */
    private int size;
    /** The first cell the window holds, across and down; unset until it is first followed. */
    private int originX;
    private int originY;
    private boolean placed;

    MinimapPicture() {
        texture.setMagFilter(Texture.MagFilter.Nearest); // a cell is a square, drawn crisp however large
        texture.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        texture.setWrap(Texture.WrapMode.EdgeClamp);
    }

    /**
     * Lay the picture out for {@code grid}: every cell unwalked where the map is discovered, the ground and its stone
     * where it is not.
     *
     * @param fogTint what unwalked ground fades to, the fog's colour
     */
    void rebuild(PathGrid grid, boolean discovered, ColorRGBA fogTint) {
        rebuild(grid, discovered, fogTint, 0);
    }

    /**
     * The same, the picture of {@code span} cells a side round where the camera looks where the map is wider or deeper
     * than that — each cell painted as the window {@link #follow follows} the camera over it — and of the whole map,
     * as always, where it is not, or at 0.
     */
    void rebuild(PathGrid grid, boolean discovered, ColorRGBA fogTint, int span) {
        this.grid = grid;
        this.discovered = discovered;
        this.dark = fogTint == null ? ColorRGBA.Black : fogTint;
        this.windowed = grid != null && span > 0 && (span < grid.getWidth() || span < grid.getHeight());
        this.span = windowed ? span : 0;
        this.placed = false;
        if (windowed) {
            size = span + 2;
            lay(size, size);
            // Past the last texel is the first again: a cell is always the same texel.
            texture.setWrap(Texture.WrapMode.Repeat);
            for (int row = 0; row < size; row++) {
                for (int column = 0; column < size; column++) {
                    putTexel(column, row, unseen());
                }
            }
            image.setUpdateNeeded();
            everyCell = false;
            return;
        }
        int width = grid == null ? 1 : grid.getWidth();
        int height = grid == null ? 1 : grid.getHeight();
        lay(width, height);
        texture.setWrap(Texture.WrapMode.EdgeClamp);
        if (grid == null) {
            put(0, 0, GROUND);
            return;
        }
        for (int cy = 0; cy < height; cy++) {
            for (int cx = 0; cx < width; cx++) {
                put(cx, cy, discovered ? unseen() : grid.isTerrainBlocked(cx, cy) ? STONE : GROUND);
            }
        }
        image.setUpdateNeeded();
        everyCell = discovered;
    }

    private void lay(int width, int height) {
        texels = BufferUtils.createByteBuffer(width * height * BYTES_PER_TEXEL);
        image = new Image(Image.Format.RGBA8, width, height, texels, ColorSpace.Linear);
        texture.setImage(image);
        tiles.resize(width, height);
    }

    /**
     * Repaint the cells whose state the last softening changed — every cell, the first time after a new map — and say
     * so to the card only where one did.
     */
    void paint(Discovery seen) {
        paint(seen, null);
    }

    /**
     * The same, what changed sent to the card by {@code renderer} a tile of the picture at a time ({@link
     * TextureTiles}); null sends the whole picture again, as it always was. Of a window, the cells in it.
     */
    void paint(Discovery seen, com.jme3.renderer.Renderer renderer) {
        if (!discovered || grid == null || seen == null) {
            return;
        }
        if (windowed) {
            paintWindow(seen, renderer);
            return;
        }
        int width = grid.getWidth();
        boolean painted = false;
        if (everyCell || seen.everythingChanged()) {
            for (int cell = 0; cell < width * grid.getHeight(); cell++) {
                painted |= paintCell(seen, cell % width, cell / width);
            }
            everyCell = false;
        } else {
            var changed = seen.changed();
            for (int i = 0; i < changed.size(); i++) {
                painted |= paintCell(seen, changed.get(i) % width, changed.get(i) / width);
            }
        }
        if (painted) {
            tiles.send(renderer, texture, image, texels);
        }
    }

    private void paintWindow(Discovery seen, com.jme3.renderer.Renderer renderer) {
        if (!placed) {
            return; // nothing is shown until the window is somewhere
        }
        int width = grid.getWidth();
        boolean painted = false;
        if (seen.everythingChanged()) {
            for (int cy = originY; cy < originY + size; cy++) {
                for (int cx = originX; cx < originX + size; cx++) {
                    painted |= paintWindowCell(seen, cx, cy);
                }
            }
        } else {
            var changed = seen.changed();
            for (int i = 0; i < changed.size(); i++) {
                int cx = changed.get(i) % width;
                int cy = changed.get(i) / width;
                if (cx >= originX && cy >= originY && cx < originX + size && cy < originY + size) {
                    painted |= paintWindowCell(seen, cx, cy);
                }
            }
        }
        if (painted) {
            tiles.send(renderer, texture, image, texels);
        }
    }

    /**
     * Keep the window's middle on the point ({@code worldX}, {@code worldY}) of the ground, each cell coming into it
     * painted as {@code seen} has it — or as ground and stone where the map is not discovered — and sent to the card by
     * {@code renderer}, or whole where it is null. Nothing where the picture is of the whole map.
     */
    void follow(float worldX, float worldY, Discovery seen, com.jme3.renderer.Renderer renderer) {
        if (!windowed) {
            return;
        }
        float cell = grid.getCellSize();
        int toX = (int) Math.floor(worldX / cell) - size / 2;
        int toY = (int) Math.floor(worldY / cell) - size / 2;
        if (placed && toX == originX && toY == originY) {
            return;
        }
        int fromX = originX;
        int fromY = originY;
        boolean was = placed;
        originX = toX;
        originY = toY;
        placed = true;
        boolean painted = false;
        for (int cy = toY; cy < toY + size; cy++) {
            for (int cx = toX; cx < toX + size; cx++) {
                if (was && cx >= fromX && cy >= fromY && cx < fromX + size && cy < fromY + size) {
                    continue; // in the window before, and painted then
                }
                painted |= paintWindowCell(seen, cx, cy);
            }
        }
        if (painted) {
            tiles.send(renderer, texture, image, texels);
        }
    }

    /** One cell of the window: as the discovery has it, as ground and stone without one, and unwalked off the map. */
    private boolean paintWindowCell(Discovery seen, int cx, int cy) {
        var colour = cx < 0 || cy < 0 || cx >= grid.getWidth() || cy >= grid.getHeight() ? unseen()
                : discovered && seen != null ? colourOf(seen.stateAt(cx, cy), grid.isTerrainBlocked(cx, cy),
                        grid.level(cx, cy), dark)
                : grid.isTerrainBlocked(cx, cy) ? STONE : GROUND;
        return putTexel(Math.floorMod(cx, size), size - 1 - Math.floorMod(cy, size), colour);
    }

    private boolean paintCell(Discovery seen, int cx, int cy) {
        return put(cx, cy, colourOf(seen.stateAt(cx, cy), grid.isTerrainBlocked(cx, cy), grid.level(cx, cy), dark));
    }

    /**
     * The colour a cell is drawn in: unwalked, remembered or lit, stone or floor — a floor a storey up paler — beside a
     * dark of {@code dark}. The minimap's, and the whole world's picture's.
     */
    static ColorRGBA colourOf(Discovery.State state, boolean stone, int storey, ColorRGBA dark) {
        return switch (state) {
            case UNSEEN -> dark.mult(0.45f);
            case REMEMBERED -> stone ? dark.mult(0.9f).add(new ColorRGBA(0.09f, 0.08f, 0.07f, 0f))
                    : floor(dark.mult(0.9f).add(new ColorRGBA(0.04f, 0.06f, 0.03f, 0f)), storey);
            case VISIBLE -> stone ? STONE : floor(LIT_FLOOR, storey);
        };
    }

    /** The colour a cell of a map nobody discovers is drawn in: its ground, or its stone. */
    static ColorRGBA colourOf(boolean stone) {
        return stone ? STONE : GROUND;
    }

    /**
     * A floor a storey up, paler than the one below it: the map is a plan view, and a plan view cannot show height at
     * all — two rooms one above the other are the same square of paper — so it is said in tone, as a contour map does.
     * A floor below the ground is drawn as the ground's.
     */
    private static ColorRGBA floor(ColorRGBA colour, int storey) {
        return colour.mult(1f + 0.45f * Math.max(0, storey));
    }

    private ColorRGBA unseen() {
        return dark.mult(0.45f);
    }

    /** One cell's texel; whether it changed. Row 0 of the picture is the map's far edge, drawn at the bottom. */
    private boolean put(int cx, int cy, ColorRGBA colour) {
        return putTexel(cx, image.getHeight() - 1 - cy, colour);
    }

    /** One texel of the picture; whether it changed. */
    private boolean putTexel(int column, int row, ColorRGBA colour) {
        int at = (row * image.getWidth() + column) * BYTES_PER_TEXEL;
        byte red = channel(colour.r);
        byte green = channel(colour.g);
        byte blue = channel(colour.b);
        if (texels.get(at) == red && texels.get(at + 1) == green && texels.get(at + 2) == blue
                && texels.get(at + 3) == (byte) 0xFF) {
            return false;
        }
        texels.put(at, red).put(at + 1, green).put(at + 2, blue).put(at + 3, (byte) 0xFF);
        tiles.changed(column, row, column, row);
        return true;
    }

    static byte channel(float value) {
        return (byte) Math.round(Math.clamp(value, 0f, 1f) * 255f);
    }

    /** The picture, for the quad the minimap draws it on. */
    Texture2D texture() {
        return texture;
    }

    /** Whether the picture is of a window round the camera. */
    boolean isWindowed() {
        return windowed;
    }

    /** How many cells a side the window shows; 0 for the whole map. */
    int span() {
        return span;
    }

    /** How many cells a side the window's picture holds. */
    int size() {
        return size;
    }

    /**
     * The texture's corners for a quad showing the {@link #span} of cells from cell ({@code fromX}, {@code fromY}) —
     * fractions of a cell and all — as a quad's texture coordinates run: its bottom left, bottom right, top right and
     * top left, u and v each. The picture repeats, so these run past 0 and 1 as the window has travelled.
     */
    float[] corners(float fromX, float fromY) {
        float left = fromX / size;
        float right = (fromX + span) / size;
        float top = 1f - fromY / size;
        float bottom = 1f - (fromY + span) / size;
        return new float[] {left, bottom, right, bottom, right, top, left, top};
    }

    /** One cell's colour as the picture has it, packed {@code 0xRRGGBB}. */
    int colourAt(int cx, int cy) {
        int column = windowed ? Math.floorMod(cx, size) : cx;
        int row = windowed ? size - 1 - Math.floorMod(cy, size) : image.getHeight() - 1 - cy;
        int at = (row * image.getWidth() + column) * BYTES_PER_TEXEL;
        return (texels.get(at) & 0xFF) << 16 | (texels.get(at + 1) & 0xFF) << 8 | texels.get(at + 2) & 0xFF;
    }
}
