package uz.dukeengine.render;

import com.jme3.asset.AssetManager;
import com.jme3.asset.TextureKey;
import com.jme3.material.Material;
import com.jme3.post.Filter;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;

/**
 * The look of the finished picture, after the tone curve: a colour grade ({@link ColorLut}, read in sRGB, the way a tool
 * that makes one shows it) and a vignette that darkens the corners. The vignette is in linear light, before the grade, as
 * a lens's falloff is. An identity cube gives the picture back to within a level of 256. Either is left out of the shader when not asked for.
 */
public final class GradeFilter extends Filter {

    private final String lut;
    private final float vignette;

    /** The grade at the asset path {@code lut} (null, none) and the vignette's strength (0, none). */
    public GradeFilter(String lut, float vignette) {
        super("Grade");
        this.lut = lut;
        this.vignette = vignette;
    }

    @Override
    protected void initFilter(AssetManager manager, RenderManager renderManager, ViewPort vp, int w, int h) {
        material = new Material(manager, "Common/MatDefs/Duke/Grade.j3md");
        if (lut != null) {
            // The strip is read as numbers, not as colours: no flip and no gamma.
            var key = new TextureKey(lut, false);
            key.setGenerateMips(false);
            var cube = ColorLut.fromStrip(manager.loadTexture(key).getImage());
            material.setTexture("Lut", cube.texture());
            material.setFloat("LutSize", cube.size());
        }
        if (vignette > 0f) {
            material.setFloat("Vignette", vignette);
        }
    }

    @Override
    protected Material getMaterial() {
        return material;
    }
}
