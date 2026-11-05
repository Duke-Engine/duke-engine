package uz.dukeengine.render;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.Vector2f;
import com.jme3.post.Filter;
import com.jme3.renderer.Caps;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.texture.Image.Format;
import com.jme3.texture.Texture;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Bloom over the linear HDR picture (Jimenez 2014): the picture is halved down a chain of levels, each by a 13-tap filter,
 * and joined back up by a 3x3 tent, so every bright thing spills as wide as the widest level, in one smooth falloff. There
 * is no brightness threshold: the picture is lerped toward the joined chain by {@link #STRENGTH}, so a flat field stays as it
 * is and light is only moved, never made. The first level weighs its taps against fireflies (Karis average).
 *
 * <p>Without float colour targets (a GPU that cannot hold an HDR picture) the filter turns itself off.
 */
public final class MipBloomFilter extends Filter {

    private static final Logger LOG = Logger.getLogger(MipBloomFilter.class.getName());
    private static final int MOST_LEVELS = 6;
    private static final int SMALLEST = 4;
    /** The share of the bloom in the picture. */
    static final float STRENGTH = 0.04f;

    private boolean onlyBloom;

    public MipBloomFilter() {
        super("MipBloom");
    }

    /** Shows the bloom alone: for the bench to look at. */
    public MipBloomFilter showBloomOnly() {
        onlyBloom = true;
        return this;
    }

    /**
     * The sizes of the levels, the first half of the picture and each next half of the last (rounded down, 1 at least),
     * as many as keep 4 pixels on the short side, 6 at most, but always one: a tiny window gets a bloom of one level.
     */
    static List<int[]> levels(int width, int height) {
        var levels = new ArrayList<int[]>();
        int w = width;
        int h = height;
        do {
            w = Math.max(1, w / 2);
            h = Math.max(1, h / 2);
            levels.add(new int[] {w, h});
        } while (levels.size() < MOST_LEVELS && Math.min(w, h) / 2 >= SMALLEST);
        return levels;
    }

    @Override
    protected void initFilter(AssetManager manager, RenderManager renderManager, ViewPort vp, int w, int h) {
        postRenderPasses = new ArrayList<>();
        material = new Material(manager, "Common/MatDefs/Duke/BloomApply.j3md");
        material.setFloat("Strength", STRENGTH);
        material.setBoolean("OnlyBloom", onlyBloom);
        if (!renderManager.getRenderer().getCaps().contains(Caps.FloatColorBuffer)) {
            LOG.warning("this GPU cannot hold a float picture: the picture goes without bloom");
            setEnabled(false);
            return;
        }
        var sizes = levels(w, h);
        var down = new Pass[sizes.size()];
        var source = new int[] {w, h};
        for (int i = 0; i < down.length; i++) {
            var step = new Material(manager, "Common/MatDefs/Duke/BloomDown.j3md");
            step.setVector2("Texel", new Vector2f(1f / source[0], 1f / source[1]));
            if (i > 0) {
                step.setTexture("Texture", down[i - 1].getRenderedTexture());
            }
            step.setBoolean("Karis", i == 0);
            down[i] = pass(renderManager, i == 0, sizes.get(i), step);
            source = sizes.get(i);
        }
        // Up from the narrowest: each level takes the wider one joined so far, and its own way down.
        var joined = down[down.length - 1];
        for (int i = down.length - 2; i >= 0; i--) {
            var step = new Material(manager, "Common/MatDefs/Duke/BloomUp.j3md");
            step.setTexture("Lower", joined.getRenderedTexture());
            step.setTexture("Same", down[i].getRenderedTexture());
            step.setVector2("Texel", new Vector2f(1f / sizes.get(i + 1)[0], 1f / sizes.get(i + 1)[1]));
            joined = pass(renderManager, false, sizes.get(i), step);
        }
        material.setTexture("Bloom", joined.getRenderedTexture());
    }

    /** A level of the chain: bilinear, since both the 13 taps and the tent lean on the GPU's filter. */
    private Pass pass(RenderManager renderManager, boolean readsScene, int[] size, Material step) {
        var pass = new Pass() {
            @Override
            public boolean requiresSceneAsTexture() {
                return readsScene;
            }
        };
        pass.init(renderManager.getRenderer(), size[0], size[1], Format.RGB16F, Format.Depth, 1, step);
        pass.getRenderedTexture().setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        pass.getRenderedTexture().setMagFilter(Texture.MagFilter.Bilinear);
        postRenderPasses.add(pass);
        return pass;
    }

    @Override
    protected Material getMaterial() {
        return material;
    }
}
