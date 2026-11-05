package uz.dukeengine.render;

import com.jme3.math.ColorRGBA;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture3D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.texture.image.ImageRaster;
import com.jme3.util.BufferUtils;
import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * A colour grade as a cube of colours: the colour that comes out for each (red, green, blue) the picture shows, as the eye
 * sees it (sRGB), read at {@code size} steps along each side and joined by linear interpolation between them.
 * {@link GradeFilter} applies it after the tone curve.
 *
 * <p>The file is a strip PNG, the usual one a grading tool exports: {@code size} squares of {@code size x size} side by side
 * (256x16 for 16 steps, the size that holds an identity exactly in 8 bits: 255 is divisible by 15, and not by 31). Square {@code b} is
 * the steps of blue; inside it red grows to the right and green downward.
 * Shipped in {@code Common/Luts}: {@code identity.png} and {@code warm-teal.png}, which pulls the shadows toward teal and the
 * highlights toward warm by a few percent: with l the sRGB luma, red + 0.045 l^2 - 0.03 (1-l)^2, green + 0.01 (1-l)^2,
 * blue + 0.035 (1-l)^2 - 0.045 l^2.
 */
public final class ColorLut {

    private final int size;
    /** Red runs fastest, then green, then blue; three floats a colour. */
    private final float[] cube;

    private ColorLut(int size, float[] cube) {
        this.size = size;
        this.cube = cube;
    }

    /** The cube that gives every colour back as it is. */
    public static ColorLut identity(int size) {
        var cube = new float[size * size * size * 3];
        for (int b = 0, i = 0; b < size; b++) {
            for (int g = 0; g < size; g++) {
                for (int r = 0; r < size; r++, i += 3) {
                    cube[i] = r / (size - 1f);
                    cube[i + 1] = g / (size - 1f);
                    cube[i + 2] = b / (size - 1f);
                }
            }
        }
        return new ColorLut(size, cube);
    }

    /** The cube a strip picture holds, {@code size * size} wide and {@code size} high. */
    public static ColorLut fromStrip(Image strip) {
        int size = strip.getHeight();
        if (size < 2 || strip.getWidth() != size * size) {
            throw new IllegalArgumentException("a colour strip is size*size wide and size high: " + strip.getWidth() + "x" + strip.getHeight());
        }
        var raster = ImageRaster.create(strip);
        var cube = new float[size * size * size * 3];
        var pixel = new ColorRGBA();
        for (int b = 0, i = 0; b < size; b++) {
            for (int g = 0; g < size; g++) {
                for (int r = 0; r < size; r++, i += 3) {
                    raster.getPixel(b * size + r, g, pixel);
                    cube[i] = pixel.r;
                    cube[i + 1] = pixel.g;
                    cube[i + 2] = pixel.b;
                }
            }
        }
        return new ColorLut(size, cube);
    }

    public int size() {
        return size;
    }

    /** The colour {@code (r, g, b)}, each 0 to 1, comes out as: trilinear between the steps, as the GPU reads it. */
    public float[] at(float r, float g, float b) {
        float[] out = new float[3];
        float x = Math.min(Math.max(r, 0f), 1f) * (size - 1);
        float y = Math.min(Math.max(g, 0f), 1f) * (size - 1);
        float z = Math.min(Math.max(b, 0f), 1f) * (size - 1);
        int x0 = Math.min((int) x, size - 2);
        int y0 = Math.min((int) y, size - 2);
        int z0 = Math.min((int) z, size - 2);
        for (int corner = 0; corner < 8; corner++) {
            int dx = corner & 1;
            int dy = corner >> 1 & 1;
            int dz = corner >> 2;
            float weight = (dx == 0 ? 1f - (x - x0) : x - x0) * (dy == 0 ? 1f - (y - y0) : y - y0) * (dz == 0 ? 1f - (z - z0) : z - z0);
            int i = (((z0 + dz) * size + y0 + dy) * size + x0 + dx) * 3;
            for (int c = 0; c < 3; c++) {
                out[c] += cube[i + c] * weight;
            }
        }
        return out;
    }

    /** The cube as a texture the GPU interpolates: floats, so the cube is held to the digit and an identity is one. */
    public Texture3D texture() {
        ByteBuffer bytes = BufferUtils.createByteBuffer(cube.length * Float.BYTES);
        bytes.asFloatBuffer().put(cube);
        var data = new ArrayList<ByteBuffer>(1);
        data.add(bytes);
        var texture = new Texture3D(new Image(Image.Format.RGB32F, size, size, size, data, ColorSpace.Linear));
        texture.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        texture.setMagFilter(Texture.MagFilter.Bilinear);
        texture.setWrap(Texture.WrapMode.EdgeClamp);
        return texture;
    }
}
