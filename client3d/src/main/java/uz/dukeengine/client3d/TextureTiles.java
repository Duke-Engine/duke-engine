package uz.dukeengine.client3d;

import com.jme3.renderer.Renderer;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import com.jme3.util.BufferUtils;
import java.nio.ByteBuffer;
import java.util.BitSet;

/**
 * Which parts of a picture changed since it was last sent to the card, by square tiles of {@link #TILE} texels a side —
 * and the sending of them, a tile at a time ({@link Renderer#modifyTexture}), where a picture of the whole world sent
 * again for every texel that moved was megabytes a frame while the hero walked. The whole picture where it was never
 * sent, where most of it changed, or where there is no card to send tiles to.
 */
final class TextureTiles {

    /** How many texels a side a tile is. */
    static final int TILE = 64;

    private int width;
    private int height;
    private int across;
    private final BitSet changed = new BitSet();
    private boolean whole = true;
    private ByteBuffer patch = BufferUtils.createByteBuffer(TILE * TILE * 4);

    /** A picture of {@code width} by {@code height} texels, all of it to be sent. */
    void resize(int width, int height) {
        this.width = width;
        this.height = height;
        this.across = (width + TILE - 1) / TILE;
        changed.clear();
        whole = true;
    }

    /** The texels {@code fromX..toX} by {@code fromY..toY} changed. */
    void changed(int fromX, int fromY, int toX, int toY) {
        if (fromX > toX || fromY > toY) {
            return;
        }
        for (int y = Math.max(0, fromY) / TILE; y <= Math.min(height - 1, toY) / TILE; y++) {
            for (int x = Math.max(0, fromX) / TILE; x <= Math.min(width - 1, toX) / TILE; x++) {
                changed.set(y * across + x);
            }
        }
    }

    /** All of it changed. */
    void changedEverywhere() {
        whole = true;
    }

    /** Whether anything is waiting to be sent. */
    boolean waiting() {
        return whole || !changed.isEmpty();
    }

    /**
     * Send what changed of {@code image} — whose texels, four bytes each, are {@code texels} — to {@code texture}: the
     * changed tiles by {@code renderer}, or the whole picture.
     */
    void send(Renderer renderer, Texture2D texture, Image image, ByteBuffer texels) {
        if (!waiting()) {
            return;
        }
        int tiles = across * ((height + TILE - 1) / TILE);
        if (whole || renderer == null || changed.cardinality() * 2 > tiles) {
            image.setUpdateNeeded();
            whole = false;
            changed.clear();
            return;
        }
        for (int tile = changed.nextSetBit(0); tile >= 0; tile = changed.nextSetBit(tile + 1)) {
            int x0 = (tile % across) * TILE;
            int y0 = (tile / across) * TILE;
            int wide = Math.min(TILE, width - x0);
            int deep = Math.min(TILE, height - y0);
            patch.clear();
            for (int y = 0; y < deep; y++) {
                int from = ((y0 + y) * width + x0) * 4;
                for (int i = 0; i < wide * 4; i++) {
                    patch.put(texels.get(from + i));
                }
            }
            patch.flip();
            renderer.modifyTexture(texture, new Image(image.getFormat(), wide, deep, patch, image.getColorSpace()),
                    x0, y0);
        }
        changed.clear();
    }
}
