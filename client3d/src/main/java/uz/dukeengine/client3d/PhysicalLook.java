package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.light.LightProbe;
import com.jme3.renderer.RenderManager;
import com.jme3.scene.Node;
import uz.dukeengine.render.AtmosphereSky;
import uz.dukeengine.render.PictureChain;
import uz.dukeengine.render.PictureSettings;
import uz.dukeengine.render.SkyProbe;

/**
 * What {@link Visuals#physicallyBased} adds to a scene: the light physically based materials take their ambient from,
 * a probe baked from a sky worked out of the scene's own sun and ambient, the filters over the finished picture, and
 * the sun's shadows drawn by {@link uz.dukeengine.render.SunShadowFilter}. With {@link PictureSettings#atmosphere} the sky is the atmosphere under the sun's direction, and the probe follows
 * the sun through its hours.
 *
 * <p>Light is drawing. Nothing in the simulation reads it.
 */
final class PhysicalLook {

    private final AssetManager assets;
    private final RenderManager renderManager;
    private final Node root;
    private final AtmosphereSky atmosphere;
    private LightProbe probe;

    PhysicalLook(AssetManager assets, RenderManager renderManager, Node root, SceneLight light, PictureSettings picture) {
        this.assets = assets;
        this.renderManager = renderManager;
        this.root = root;
        this.atmosphere = picture.atmosphere() ? new AtmosphereSky(assets, renderManager, root) : null;
        bake(light);
        light.shadows().drawnAs(picture, renderManager.getRenderer().getCaps());
        light.shadows().finishWith(PictureChain.filters(picture));
    }

    /** The probe baked anew from the sun the scene is lit by now: when a new world brings its own. */
    void bake(SceneLight light) {
        if (atmosphere != null) {
            follow(light);
            return;
        }
        if (probe != null) {
            root.removeLight(probe);
        }
        probe = SkyProbe.bake(assets, renderManager, light.sunDirection().negate(), light.sunColour(),
                light.ambientColour());
        root.addLight(probe);
    }

    /**
     * Every frame: the sun as it eases from one hour to the next. The atmosphere is made again once the sun has moved a
     * few degrees, painted off the frame; without it the probe keeps the sky of the world's own sun.
     * shortcut: the drawn sky of the atmosphere is not put in the scene, a game's background stays its own;
     * {@link AtmosphereSky#sky} is what to attach when one wants it. The sun's light keeps the game's colour at any
     * hour, tint it by {@link uz.dukeengine.render.Atmosphere#transmittance} when a game wants its sunset light red.
     */
    void follow(SceneLight light) {
        if (atmosphere != null) {
            var sun = light.lit();
            atmosphere.follow(sun.direction().negate(), sun.colour());
        }
    }
}
