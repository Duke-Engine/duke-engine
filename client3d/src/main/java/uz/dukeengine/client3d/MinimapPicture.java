package uz.dukeengine.client3d;

import com.jme3.math.ColorRGBA;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;
import java.nio.ByteBuffer;
import java.util.BitSet;
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
    private int storeys;
    /** What unwalked ground fades to: the fog's own colour. */
    private ColorRGBA dark = ColorRGBA.Black;
    /** What of the picture changed since it was last sent to the card. */
    private final TextureTiles tiles = new TextureTiles();

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
        this.grid = grid;
        this.discovered = discovered;
        this.dark = fogTint == null ? ColorRGBA.Black : fogTint;
        int width = grid == null ? 1 : grid.getWidth();
        int height = grid == null ? 1 : grid.getHeight();
        storeys = 0;
        texels = BufferUtils.createByteBuffer(width * height * BYTES_PER_TEXEL);
        image = new Image(Image.Format.RGBA8, width, height, texels, ColorSpace.Linear);
        texture.setImage(image);
        tiles.resize(width, height);
        if (grid == null) {
            put(0, 0, GROUND);
            return;
        }
        for (int cy = 0; cy < height; cy++) {
            for (int cx = 0; cx < width; cx++) {
                storeys = Math.max(storeys, grid.level(cx, cy));
            }
        }
        for (int cy = 0; cy < height; cy++) {
            for (int cx = 0; cx < width; cx++) {
                put(cx, cy, discovered ? unseen() : grid.isTerrainBlocked(cx, cy) ? STONE : GROUND);
            }
        }
        image.setUpdateNeeded();
        everyCell = discovered;
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
     * TextureTiles}); null sends the whole picture again, as it always was.
     */
    void paint(Discovery seen, com.jme3.renderer.Renderer renderer) {
        if (!discovered || grid == null || seen == null) {
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

    private boolean paintCell(Discovery seen, int cx, int cy) {
        boolean stone = grid.isTerrainBlocked(cx, cy);
        int storey = Math.clamp(grid.level(cx, cy), 0, storeys);
        return put(cx, cy, switch (seen.stateAt(cx, cy)) {
            case UNSEEN -> unseen();
            case REMEMBERED -> stone ? dark.mult(0.9f).add(new ColorRGBA(0.09f, 0.08f, 0.07f, 0f))
                    : floor(dark.mult(0.9f).add(new ColorRGBA(0.04f, 0.06f, 0.03f, 0f)), storey);
            case VISIBLE -> stone ? STONE : floor(LIT_FLOOR, storey);
        });
    }

    /**
     * A floor a storey up, paler than the one below it: the map is a plan view, and a plan view cannot show height at
     * all — two rooms one above the other are the same square of paper — so it is said in tone, as a contour map does.
     */
    private static ColorRGBA floor(ColorRGBA colour, int storey) {
        return colour.mult(1f + 0.45f * storey);
    }

    private ColorRGBA unseen() {
        return dark.mult(0.45f);
    }

    /** One cell's texel; whether it changed. Row 0 of the picture is the map's far edge, drawn at the bottom. */
    private boolean put(int cx, int cy, ColorRGBA colour) {
        int row = image.getHeight() - 1 - cy;
        int at = (row * image.getWidth() + cx) * BYTES_PER_TEXEL;
        byte red = channel(colour.r);
        byte green = channel(colour.g);
        byte blue = channel(colour.b);
        if (texels.get(at) == red && texels.get(at + 1) == green && texels.get(at + 2) == blue
                && texels.get(at + 3) == (byte) 0xFF) {
            return false;
        }
        texels.put(at, red).put(at + 1, green).put(at + 2, blue).put(at + 3, (byte) 0xFF);
        tiles.changed(cx, row, cx, row);
        return true;
    }

    private static byte channel(float value) {
        return (byte) Math.round(Math.clamp(value, 0f, 1f) * 255f);
    }

    /** The picture, for the quad the minimap draws it on. */
    Texture2D texture() {
        return texture;
    }

    /** One cell's colour as the picture has it, packed {@code 0xRRGGBB}. */
    int colourAt(int cx, int cy) {
        int at = ((image.getHeight() - 1 - cy) * image.getWidth() + cx) * BYTES_PER_TEXEL;
        return (texels.get(at) & 0xFF) << 16 | (texels.get(at + 1) & 0xFF) << 8 | texels.get(at + 2) & 0xFF;
    }
}
