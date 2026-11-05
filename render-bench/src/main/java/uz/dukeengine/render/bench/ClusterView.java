package uz.dukeengine.render.bench;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.Vector2f;
import com.jme3.post.Filter;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import java.util.Objects;
import uz.dukeengine.render.ClusteredLights;

/** How many lights the cluster of each pixel has, as a heat over the picture's grey, the tiles' edges drawn. */
final class ClusterView extends Filter {

    private final ClusteredLights clusters;
    private Material material;

    ClusterView(ClusteredLights clusters) {
        super("ClusterView");
        this.clusters = Objects.requireNonNull(clusters, "show-clusters needs the clusters on");
    }

    @Override
    protected boolean isRequiresDepthTexture() {
        return true;
    }

    @Override
    protected void initFilter(AssetManager manager, RenderManager renderManager, ViewPort vp, int w, int h) {
        material = new Material(manager, "Common/MatDefs/Duke/ClusterView.j3md");
        material.setVector2("NearFar", new Vector2f(vp.getCamera().getFrustumNear(), vp.getCamera().getFrustumFar()));
    }

    @Override
    protected void preFrame(float tpf) {
        material.setTexture("Grid", clusters.grid());
        material.setVector4("Scale", clusters.scale());
    }

    @Override
    protected Material getMaterial() {
        return material;
    }
}
