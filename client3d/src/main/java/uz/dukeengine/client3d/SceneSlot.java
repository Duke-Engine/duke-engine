package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.input.InputManager;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.scene.Node;

/** Where the game's {@link SceneLayer} hangs: a node of its own under the world's, taken away with the layer. */
final class SceneSlot {

    private final Node world;
    private final Node node = new Node("scene layer");
    private SceneLayer layer;

    SceneSlot(Node world) {
        this.world = world;
    }

    void attach(SceneLayer layer, AssetManager assets, Camera cam, InputManager input, RenderManager views) {
        detach();
        if (layer == null) {
            return;
        }
        this.layer = layer;
        world.attachChild(node);
        layer.attach(node, assets, cam, input, views);
    }

    void update(float tpf) {
        if (layer != null) {
            layer.update(tpf);
        }
    }

    void detach() {
        if (layer == null) {
            return;
        }
        layer.detach();
        layer = null;
        node.detachAllChildren();
        node.removeFromParent();
    }
}
