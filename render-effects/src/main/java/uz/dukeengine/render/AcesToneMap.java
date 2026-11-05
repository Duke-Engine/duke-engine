package uz.dukeengine.render;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.post.Filter;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;

/** Bends an HDR picture into what a screen can show, keeping highlights' detail as film does. */
public final class AcesToneMap extends Filter {

    private final float exposure;
    private Material material;

    public AcesToneMap(float exposure) {
        super("AcesToneMap");
        this.exposure = exposure;
    }

    @Override
    protected void initFilter(AssetManager manager, RenderManager renderManager, ViewPort vp, int w, int h) {
        material = new Material(manager, "Common/MatDefs/Duke/AcesToneMap.j3md");
        material.setFloat("Exposure", exposure);
    }

    @Override
    protected Material getMaterial() {
        return material;
    }
}
