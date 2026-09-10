package uz.duke.client3d;

import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.FastMath;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;
import java.nio.ByteBuffer;
import java.util.function.Function;
import uz.duke.core.pathfind.PathGrid;

/**
 * The dark, as one sheet laid over the whole map.
 *
 * <p>Fog used to be a property of the ground: each cell was drawn at its own
 * brightness, so the map came out as a field of squares ten units across with a
 * hard step between every one of them. Softening it did not help, because the
 * softening was happening at the size of a cell — the blur was real, and then it
 * was rounded back to one value per tile and painted flat across it.
 *
 * <p>So the fog stops being a property of the ground at all. It is a texture: a
 * grid of darkness whose size is the game's to choose and has nothing to do with
 * how big a cell is, drawn as a single sheet stretched over the map. The card
 * interpolates between texels for nothing, which is what takes the corners off —
 * and it is doing it at the size of a pixel rather than the size of a tile.
 *
 * <p>Drawn last and without a depth test, so it covers the whole picture and not
 * just the floor: a wall, a chest, a monster standing in a room the hero has left
 * are all behind the same sheet and dim together.
 *
 * <p><b>Where it hangs matters.</b> A flat sheet lines up with the world at
 * exactly one height, and everything above or below that is dimmed by the fog a
 * little way behind it instead of its own — a fifth of a cell for every unit of
 * height, at this camera. So it hangs at the height the <em>walls</em> stand,
 * not at the floor. The roof over a piece of rock is a tile with a wall along the
 * edge of it, and a sheet at floor height covers half of it and leaves the rest
 * lit, which is exactly as ugly as it sounds. The floor, which is what is then
 * out of line, has no edges of its own — only a gradient, and a gradient slid by
 * two-thirds of a cell is the same gradient.
 *
 * <p>No shader anywhere in it. The whole thing is an alpha texture and the
 * blending the card does for every transparent surface.
 */
final class FogOverlay {

    /** Just clear of the wall tops, so nothing z-fights the sheet. */
    private static final float LIFT = 0.05f;

    private static final int BYTES_PER_TEXEL = 4; // rgb: the tint. a: how dark.

    private final Node parent;
    private final Function<Texture, Material> material;
    private final Fog fog;

    private Geometry sheet;
    private Image image;
    private ByteBuffer texels;
    private int size;
    private float worldWidth;
    private float worldHeight;

    FogOverlay(Node parent, Function<Texture, Material> material, Fog fog) {
        this.parent = parent;
        this.material = material;
        this.fog = fog == null ? Fog.DEFAULT : fog;
    }

    /**
     * Lay a sheet over {@code grid} at the height the world stands, discarding
     * whatever map was under the last one.
     *
     * <p>Like the terrain, rebuilding <b>replaces</b>: a roguelike lays out a new
     * floor every run, and a second sheet over the first would leave the previous
     * dungeon's dark on top of this one's.
     */
    void rebuild(PathGrid grid, float standingHeight) {
        if (sheet != null) {
            parent.detachChild(sheet);
            sheet = null;
            image = null;
            texels = null;
        }
        if (grid == null) {
            return;
        }
        worldWidth = grid.getWidth() * grid.getCellSize();
        worldHeight = grid.getHeight() * grid.getCellSize();
        size = fog.textureSize();
        texels = BufferUtils.createByteBuffer(size * size * BYTES_PER_TEXEL);
        var tint = fog.tintColour();
        byte red = channel(tint.r);
        byte green = channel(tint.g);
        byte blue = channel(tint.b);
        for (int texel = 0; texel < size * size; texel++) {
            texels.put(red).put(green).put(blue).put((byte) 0xFF); // nobody has been anywhere
        }
        texels.flip();
        // Linear, not sRGB: the tint is a ColorRGBA everywhere else in the client
        // -- the window's own background is the same number -- and a texture the
        // card would convert on the way in would not come out the same colour.
        image = new Image(Image.Format.RGBA8, size, size, texels, ColorSpace.Linear);

        var texture = new Texture2D(image);
        // The whole point of the sheet: the card fills in between texels. Nearest
        // would put the squares straight back, only smaller ones.
        texture.setMagFilter(Texture.MagFilter.Bilinear);
        texture.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        // The map's edge is the edge of the dark, not the start of it again.
        texture.setWrap(Texture.WrapMode.EdgeClamp);

        sheet = new Geometry("fog", new Quad(worldWidth, worldHeight));
        var skin = material.apply(texture);
        if (skin != null) {
            skin.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            // Over everything, not over the floor. A wall stands ten units above
            // the sheet, so a sheet that respected depth would be under it and the
            // wall would be the one thing in the room the dark never reached.
            skin.getAdditionalRenderState().setDepthTest(false);
            skin.getAdditionalRenderState().setDepthWrite(false);
        }
        sheet.setMaterial(skin);
        sheet.setQueueBucket(RenderQueue.Bucket.Translucent);
        // Laid out like the ground quad, which spans upward from the z it sits at.
        sheet.rotate(-FastMath.HALF_PI, 0, 0);
        sheet.setLocalTranslation(0, standingHeight + LIFT, worldHeight);
        parent.attachChild(sheet);
    }

    /**
     * Redraw the dark for what the player now knows.
     *
     * <p>Every texel asks the discovery how bright its own point of ground is —
     * not which cell it falls in — so a texel between two cell centres gets a
     * value between the two and the sheet has a gradient in it before the card has
     * done anything at all.
     *
     * <p>The upload is skipped when nothing moved. Standing still is the common
     * case in a crawler — reading a panel, choosing a power — and the fog settles
     * within a second of the hero stopping.
     */
    void update(Discovery seen) {
        if (sheet == null || seen == null) {
            return;
        }
        boolean moved = false;
        for (int ty = 0; ty < size; ty++) {
            // The sheet is laid down like the ground plane, whose own +y runs back
            // up the map: the image's first row is the far edge, not the near one.
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

    private static byte channel(float value) {
        return (byte) Math.round(Math.clamp(value, 0f, 1f) * 255f);
    }

    /** How dark one texel is drawn, from 0 for clear to 1 for the tint alone. */
    float darknessAt(int texelX, int texelY) {
        if (texels == null || texelX < 0 || texelY < 0 || texelX >= size || texelY >= size) {
            return 1f;
        }
        return (texels.get((texelY * size + texelX) * BYTES_PER_TEXEL + 3) & 0xFF) / 255f;
    }

    /** How many texels across the sheet is — the number the game named. */
    int size() {
        return sheet == null ? 0 : size;
    }
}
