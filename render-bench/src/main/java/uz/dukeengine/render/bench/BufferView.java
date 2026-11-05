package uz.dukeengine.render.bench;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.Vector2f;
import com.jme3.post.Filter;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import uz.dukeengine.render.SceneBuffers;

/** Shows one of the scene buffers instead of the picture, its values as they are. */
final class BufferView extends Filter {

    enum Shown { NORMALS, DEPTH, ROUGHNESS, VELOCITY }

    private final Shown shown;
    private Material material;

    BufferView(Shown shown) {
        super("BufferView");
        this.shown = shown;
    }

    @Override
    protected boolean isRequiresSceneTexture() {
        return false;
    }

    @Override
    protected void initFilter(AssetManager manager, RenderManager renderManager, ViewPort vp, int w, int h) {
        var buffers = SceneBuffers.on(manager, renderManager, vp, w, h).orElseThrow();
        material = new Material(manager, "Common/MatDefs/Duke/BufferView.j3md");
        material.setInt("Mode", shown.ordinal());
        material.setTexture("Buffer", switch (shown) {
            case NORMALS, ROUGHNESS -> buffers.normalRoughness();
            case DEPTH -> buffers.depth();
            case VELOCITY -> buffers.velocity();
        });
        material.setVector2("NearFar", new Vector2f(vp.getCamera().getFrustumNear(), vp.getCamera().getFrustumFar()));
    }

    @Override
    protected Material getMaterial() {
        return material;
    }
}
