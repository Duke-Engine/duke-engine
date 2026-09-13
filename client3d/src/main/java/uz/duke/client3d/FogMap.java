package uz.duke.client3d;

import com.jme3.math.Vector2f;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;
import java.nio.ByteBuffer;
import uz.duke.core.pathfind.PathGrid;

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

    private final Fog fog;
    private final int size;
    private final ByteBuffer texels;
    private final Image image;
    private final Texture2D texture;

    /** How wide and deep the current map is, which is what world x and z scale by. */
    private float worldWidth;
    private float worldHeight;

    /** What colour nothing is. The game's, until a theme says otherwise. */
    private com.jme3.math.ColorRGBA tint;

    FogMap(Fog fog) {
        this.fog = fog == null ? Fog.DEFAULT : fog;
        this.size = this.fog.textureSize();
        this.tint = this.fog.tintColour();
        this.texels = BufferUtils.createByteBuffer(size * size * BYTES_PER_TEXEL);
        fillWithDark();
        // Linear, not sRGB: the tint is a ColorRGBA everywhere else in the client
        // -- the window's own background is the same number -- and a texture the
        // card would convert on the way in would not come out the same colour.
        this.image = new Image(Image.Format.RGBA8, size, size, texels, ColorSpace.Linear);
        this.texture = new Texture2D(image);
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
     * Take the shape of a new map and forget the last one's dark.
     *
     * <p>The picture itself is built once and kept: its size is the game's answer
     * and does not change with the map, so a new floor refills it rather than
     * replacing it — which is also what lets a material hold on to the texture
     * across a run.
     */
    void resize(PathGrid grid) {
        worldWidth = grid == null ? 0f : grid.getWidth() * grid.getCellSize();
        worldHeight = grid == null ? 0f : grid.getHeight() * grid.getCellSize();
        fillWithDark();
        image.setUpdateNeeded();
    }

    /**
     * Redraw the dark for what the player now knows.
     *
     * <p>Every texel asks about its own point of ground — not the cell it falls in
     * — so a texel between two cell centres gets a value between the two and the
     * picture has a gradient in it before the card has done anything at all.
     *
     * <p>The upload is skipped when nothing moved. Standing still is the common
     * case in a crawler — reading a panel, choosing a skill — and the fog settles
     * within a second of the hero stopping.
     */
    void update(Discovery seen) {
        if (seen == null || worldWidth <= 0f || worldHeight <= 0f) {
            return;
        }
        boolean moved = false;
        for (int ty = 0; ty < size; ty++) {
            // The image's first row is the map's far edge: v runs the other way,
            // and the shader that reads this agrees with it.
            float worldY = worldHeight * (1f - (ty + 0.5f) / size);
            for (int tx = 0; tx < size; tx++) {
                float worldX = worldWidth * (tx + 0.5f) / size;
                byte dark = channel(1f - seen.lightAtPoint(worldX, worldY));
                int at = (ty * size + tx) * BYTES_PER_TEXEL + 3;
                if (texels.get(at) != dark) {
                    texels.put(at, dark);
                    moved = true;
                }
            }
        }
        if (moved) {
            image.setUpdateNeeded();
        }
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
        if (texelX < 0 || texelY < 0 || texelX >= size || texelY >= size) {
            return 1f;
        }
        return (texels.get((texelY * size + texelX) * BYTES_PER_TEXEL + 3) & 0xFF) / 255f;
    }

    /** How many texels across the picture is — the number the game named. */
    int size() {
        return size;
    }

    private void fillWithDark() {
        byte red = channel(tint.r);
        byte green = channel(tint.g);
        byte blue = channel(tint.b);
        texels.clear();
        for (int texel = 0; texel < size * size; texel++) {
            texels.put(red).put(green).put(blue).put((byte) 0xFF); // nobody has been anywhere
        }
        texels.flip();
    }

    private static byte channel(float value) {
        return (byte) Math.round(Math.clamp(value, 0f, 1f) * 255f);
    }
}
