package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.light.LightProbe;
import com.jme3.renderer.RenderManager;
import com.jme3.scene.Node;
import uz.dukeengine.render.PictureChain;
import uz.dukeengine.render.PictureSettings;
import uz.dukeengine.render.SkyProbe;

/**
 * What {@link Visuals#physicallyBased} adds to a scene: the light physically based materials take their ambient from,
 * a probe baked from a sky worked out of the scene's own sun and ambient, and the filters over the finished picture.
 *
 * <p>Light is drawing. Nothing in the simulation reads it.
 */
final class PhysicalLook {

    private final AssetManager assets;
    private final RenderManager renderManager;
    private final Node root;
    private LightProbe probe;

    PhysicalLook(AssetManager assets, RenderManager renderManager, Node root, SceneLight light) {
        this.assets = assets;
        this.renderManager = renderManager;
        this.root = root;
        bake(light);
        light.shadows().finishWith(PictureChain.filters(PictureSettings.FULL));
    }

    /**
     * The probe baked anew from the sun the scene is lit by now: when a new world brings its own.
     * shortcut: a sun easing to another hour keeps the probe it had, re-bake every few minutes of game time if a day
     * and night cycle needs it.
     */
    void bake(SceneLight light) {
        if (probe != null) {
            root.removeLight(probe);
        }
        probe = SkyProbe.bake(assets, renderManager, light.sunDirection().negate(), light.sunColour(),
                light.ambientColour());
        root.addLight(probe);
    }
}
