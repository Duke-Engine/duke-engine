package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.input.InputManager;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.scene.Node;

/**
 * A scene of the game's own, drawn in the world beside whatever the simulation shows — a workshop, a stage — that no
 * thing of the simulation stands in ({@link Duke3D#scene}). Its keys and clicks come to it through {@code input}, as
 * mappings it adds there; a picture it draws apart, a screen in the room, through a view it adds to {@code views}.
 * On the window's thread, and none of it reaches the simulation.
 */
public interface SceneLayer {

    /** Given a node of its own under the world's, once the window is open. */
    void attach(Node root, AssetManager assets, Camera cam, InputManager input, RenderManager views);

    /** Each frame the world is shown. */
    default void update(float tpf) {
    }

    /** The window closing; its node is taken away after this. */
    default void detach() {
    }
}
