package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.post.SceneProcessor;
import com.jme3.profile.AppProfiler;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Spatial;
import com.jme3.shader.VarType;
import com.jme3.texture.FrameBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Things drawn again as a flat shape of their owner's colour where something stands in front of them — the reference's
 * building-occlusion option. Run first among the world's view's processors, after its picture is drawn and before any
 * filter takes it: each marked thing drawn once more with {@code Silhouette.j3md}, which passes only where the depth
 * already there is nearer. Its own parts in front of its others show too, and so does another unit in front: the
 * ceiling of one pass with no stencil.
 */
final class OcclusionMarks implements SceneProcessor {

    /** How much of the owner's colour covers what stands in front. */
    static final float ALPHA = 0.4f;

    private final Material silhouette;
    private final List<Spatial> marked = new ArrayList<>();
    private final Map<Spatial, com.jme3.material.MatParamOverride> colours = new HashMap<>();
    private RenderManager renderManager;
    private ViewPort view;

    OcclusionMarks(AssetManager assets) {
        silhouette = new Material(assets, "MatDefs/duke/Silhouette.j3md");
        silhouette.setColor("OcclusionColour", new ColorRGBA(1f, 1f, 1f, ALPHA));
    }

    /** The things to mark this frame, each with its owner's colour. */
    void mark(Map<Spatial, ColorRGBA> things) {
        for (var gone : new ArrayList<>(colours.keySet())) {
            if (!things.containsKey(gone)) {
                gone.removeMatParamOverride(colours.remove(gone));
            }
        }
        marked.clear();
        things.forEach((thing, colour) -> {
            var tinted = new ColorRGBA(colour.r, colour.g, colour.b, ALPHA);
            var was = colours.get(thing);
            if (was == null) {
                var override = new com.jme3.material.MatParamOverride(VarType.Vector4, "OcclusionColour", tinted);
                thing.addMatParamOverride(override);
                colours.put(thing, override);
            } else if (!tinted.equals(was.getValue())) {
                was.setValue(tinted);
            }
            marked.add(thing);
        });
    }

    @Override
    public void initialize(RenderManager rm, ViewPort vp) {
        this.renderManager = rm;
        this.view = vp;
    }

    @Override
    public boolean isInitialized() {
        return renderManager != null;
    }

    @Override
    public void postFrame(FrameBuffer out) {
        if (marked.isEmpty()) {
            return;
        }
        renderManager.setForcedMaterial(silhouette);
        try {
            for (var thing : marked) {
                if (thing.getParent() != null) {
                    renderManager.renderScene(thing, view);
                }
            }
            renderManager.renderViewPortQueues(view, false);
        } finally {
            renderManager.setForcedMaterial(null);
        }
    }

    @Override
    public void reshape(ViewPort vp, int w, int h) {
    }

    @Override
    public void preFrame(float tpf) {
    }

    @Override
    public void postQueue(RenderQueue rq) {
    }

    @Override
    public void cleanup() {
        mark(Map.of());
    }

    @Override
    public void setProfiler(AppProfiler profiler) {
    }
}
