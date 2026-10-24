package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.AssetManager;
import com.jme3.input.InputManager;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.scene.Node;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A game's own scene hangs under the world's node, is stepped each frame, and goes when it is taken off. */
class SceneLayerTest {

    private final Node world = new Node("Root Node");
    private final SceneSlot slot = new SceneSlot(world);
    private final Node bench = new Node("bench");
    private final List<Float> steps = new ArrayList<>();
    private final SceneLayer layer = new SceneLayer() {
        @Override
        public void attach(Node root, AssetManager assets, Camera cam, InputManager input, RenderManager views) {
            root.attachChild(bench);
        }

        @Override
        public void update(float tpf) {
            steps.add(tpf);
        }
    };

    @Test
    void whatTheLayerPutsUpHangsUnderTheWorld() {
        slot.attach(layer, null, null, null, null);

        assertTrue(bench.hasAncestor(world));
    }

    @Test
    void itIsSteppedWithTheFramesTime() {
        slot.attach(layer, null, null, null, null);

        slot.update(0.016f);

        assertEquals(List.of(0.016f), steps);
    }

    @Test
    void takenOffItsNodeIsGoneAndItIsSteppedNoMore() {
        slot.attach(layer, null, null, null, null);

        slot.detach();
        slot.update(0.016f);

        assertFalse(bench.hasAncestor(world));
        assertEquals(0, world.getQuantity());
        assertTrue(steps.isEmpty());
    }
}
