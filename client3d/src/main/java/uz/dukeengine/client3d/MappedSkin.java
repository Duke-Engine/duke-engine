package uz.dukeengine.client3d;

import com.jme3.math.ColorRGBA;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.texture.image.ImageRaster;
import com.jme3.util.BufferUtils;

/**
 * What a glTF model's metallic-roughness picture becomes in the client's lighting.
 *
 * <p>The client lights with Blinn-Phong over a sun and a flat ambient and has no light probe, which is why the loader's
 * PBR material is replaced (see {@link Bodies#dressModel}). A model that keeps its maps ({@link
 * Visuals.UnitVisual#keepsMaps}) still gets what that lighting can show of them: the normal map as it is, and the
 * metallic-roughness picture (occlusion in red, roughness in green, metal in blue) as one strength of highlight per
 * texel, bright where it is smooth or metal and nothing on cloth. Emissive pictures are not drawn: Blinn-Phong has no
 * place for one outside a bloom pass.
 */
final class MappedSkin {

    /**
     * How much of a smooth highlight a texel keeps with no metal in it. Almost none: leather, skin and hair reflect a few
     * percent, and on a colour as dark as his even that doubles what the sun alone gives; Lange's hair is about half rough.
     */
    static final float DIELECTRIC = 0.02f;

    /** How tight a highlight is; the strength per texel is the picture's. */
    static final float SHININESS = 24f;

    private MappedSkin() {
    }

    /**
     * The highlight strength of each texel of a metallic-roughness picture: {@code (1 - roughness)²} scaled up toward
     * 1 by metal, grey, linear. The same size; the card makes its mips.
     */
    static Texture gloss(Texture metalRough) {
        var from = metalRough.getImage();
        int width = from.getWidth();
        int height = from.getHeight();
        var raster = ImageRaster.create(from);
        var bytes = BufferUtils.createByteBuffer(width * height * 3);
        var texel = new ColorRGBA();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                raster.getPixel(x, y, texel);
                float smooth = 1f - texel.g;
                float strength = Math.min(1f, smooth * smooth * (DIELECTRIC + (1f - DIELECTRIC) * texel.b) * 1.5f);
                byte level = (byte) Math.round(strength * 255f);
                bytes.put(level).put(level).put(level);
            }
        }
        bytes.flip();
        var image = new Image(Image.Format.RGB8, width, height, bytes, ColorSpace.Linear);
        var gloss = new Texture2D(image);
        gloss.setWrap(Texture.WrapAxis.S, metalRough.getWrap(Texture.WrapAxis.S));
        gloss.setWrap(Texture.WrapAxis.T, metalRough.getWrap(Texture.WrapAxis.T));
        gloss.setMinFilter(Texture.MinFilter.Trilinear);
        gloss.setMagFilter(Texture.MagFilter.Bilinear);
        return gloss;
    }
}
