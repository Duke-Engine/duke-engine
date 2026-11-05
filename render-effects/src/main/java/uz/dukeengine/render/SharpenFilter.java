package uz.dukeengine.render;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.post.Filter;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;

/**
 * A light sharpen (contrast adaptive: the push is weaker the nearer a channel is to black or white, and an edge that
 * already has bite is left alone) for the picture {@link TaaFilter} softens by summing frames. Over the tone-mapped picture,
 * so it works on what the eye sees and nothing overshoots into a halo.
 */
public final class SharpenFilter extends Filter {

    /** Small on purpose: it gives back what the sum of frames took, not more. */
    private static final float STRENGTH = 0.3f;

    public SharpenFilter() {
        super("Sharpen");
    }

    @Override
    protected void initFilter(AssetManager manager, RenderManager renderManager, ViewPort vp, int w, int h) {
        material = new Material(manager, "Common/MatDefs/Duke/Sharpen.j3md");
        material.setFloat("Strength", STRENGTH);
    }

    @Override
    protected Material getMaterial() {
        return material;
    }
}
