package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.light.LightProbe;
import com.jme3.post.Filter;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Node;
import java.util.ArrayList;
import java.util.logging.Logger;
import uz.dukeengine.render.AtmosphereSky;
import uz.dukeengine.render.AutoInstancing;
import uz.dukeengine.render.AutoLod;
import uz.dukeengine.render.ClusteredLights;
import uz.dukeengine.render.HeightFogFilter;
import uz.dukeengine.render.LocalShadowFilter;
import uz.dukeengine.render.PictureChain;
import uz.dukeengine.render.PictureSettings;
import uz.dukeengine.render.SkyProbe;
import uz.dukeengine.render.TaaFilter;

/**
 * What {@link Visuals#physicallyBased} adds to a scene: the light physically based materials take their ambient from,
 * a probe baked from a sky worked out of the scene's own sun and ambient, the filters over the finished picture, and
 * the sun's shadows drawn by {@link uz.dukeengine.render.SunShadowFilter}, and the local lights' by
 * {@link LocalShadowFilter}; the local lights drawn in one pass from the view's {@link ClusteredLights}, and what stands
 * many times drawn in one call each by {@link AutoInstancing}, what is small on the screen with fewer triangles by {@link AutoLod}. With {@link PictureSettings#atmosphere} the sky is the
 * atmosphere under the sun's direction, and the probe follows the sun through its hours.
 *
 * <p>Light is drawing. Nothing in the simulation reads it.
 */
final class PhysicalLook {

    private static final Logger LOG = Logger.getLogger(PhysicalLook.class.getName());

    private final AssetManager assets;
    private final RenderManager renderManager;
    private final Node root;
    private final AtmosphereSky atmosphere;
    private final LocalShadowFilter local;
    private final HeightFogFilter fog;
    private final TaaFilter taa;
    private LightProbe probe;

    PhysicalLook(AssetManager assets, RenderManager renderManager, ViewPort view, Node root, SceneLight light, PictureSettings picture) {
        this.assets = assets;
        this.renderManager = renderManager;
        this.root = root;
        this.atmosphere = picture.atmosphere() ? new AtmosphereSky(assets, renderManager, root) : null;
        var caps = renderManager.getRenderer().getCaps();
        if (picture.localShadows() > 0 && !LocalShadowFilter.supported(caps)) {
            LOG.warning("this GPU cannot draw the local lights' shadows (GLSL 1.50, frame buffers, depth textures): they stay unshadowed");
        }
        this.local = picture.localShadows() > 0 && LocalShadowFilter.supported(caps)
                ? new LocalShadowFilter(assets, picture.localShadows()) : null;
        // Ahead of the filters, whose shadow maps then draw the levels it picked.
        if (picture.autoLod()) {
            view.addProcessor(new AutoLod());
        }
        if (picture.clusteredLights() && ClusteredLights.supported(caps)) {
            view.addProcessor(new ClusteredLights());
        } else if (picture.clusteredLights()) {
            LOG.warning("this GPU cannot read light clusters (GLSL 1.50, float textures): each local light is a pass of its own");
        }
        if (picture.autoInstancing() && AutoInstancing.supported(caps)) {
            AutoInstancing.on(renderManager);
        } else if (picture.autoInstancing()) {
            LOG.warning("this GPU cannot draw instances: every model is a call of its own");
        }
        bake(light);
        light.shadows().drawnAs(picture, caps);
        var finishing = new ArrayList<Filter>();
        // After the sun's shadows, which are put in front of what finishes the picture, and before it.
        if (local != null) {
            finishing.add(local);
        }
        finishing.addAll(PictureChain.filters(picture));
        // The fog is in the chain only when the picture asks for it; the sun's cascades it reads are found by SunShadows.
        this.fog = finishing.stream().filter(HeightFogFilter.class::isInstance).map(HeightFogFilter.class::cast)
                .findFirst().orElse(null);
        this.taa = finishing.stream().filter(TaaFilter.class::isInstance).map(TaaFilter.class::cast).findFirst().orElse(null);
        light.shadows().finishWith(finishing);
    }

    /** The probe baked anew from the sun the scene is lit by now: when a new world brings its own. */
    void bake(SceneLight light) {
        // A new world is another picture: what the last frames made of the old one is not its history.
        if (taa != null) {
            taa.cut();
        }
        if (local != null) {
            // What else lights a surface when a local light's shadow takes its own share away: the ambient and some of the sun.
            local.fill(light.ambientColour().add(light.sunColour().mult(0.5f)));
        }
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
        var sun = light.lit();
        if (atmosphere != null) {
            atmosphere.follow(sun.direction().negate(), sun.colour());
        }
        if (fog != null) {
            fog.sun(sun.direction().negate(), sun.colour(), sun.ambient());
        }
    }
}
