package uz.dukeengine.render;

import com.jme3.asset.AssetManager;
import com.jme3.environment.FastLightProbeFactory;
import com.jme3.light.DirectionalLight;
import com.jme3.light.LightProbe;
import com.jme3.light.OrientedBoxProbeArea;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.util.TempVars;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Light probes that belong to a place, not to the sky: a game puts a box where a room is, bakes it from its middle when it is
 * placed and again whenever the room changes, and adds it to the scene's root beside the sky's probe. Inside the box a
 * surface reflects the room (its walls where the box's faces stand, so a table's reflection of the far wall is where the far
 * wall is) and takes its diffuse light from the room; a short way past the faces the sky's probe takes over again, the weights
 * being those of {@code LightProbes.glsllib}. A scene with no such probe pays nothing.
 *
 * <p>shortcut: the bake leaves out the scene's directional lights. The sun's shadow is a screen space filter that a bake does
 * not run, so with the sun in, every wall would be lit through the roof; the room is then lit by its lamps and the sky's
 * ambient, and a sunlit floor patch is not in its reflections. Upgrade when the sun's shadows are drawn into the bake too.
 */
public final class LocalProbes {

    /** The most probes PBRLighting blends at a pixel. */
    static final int MOST = 3;
    private static final int SIZE = 128;
    private static final float NEAR = 0.05f;
    private static final float FAR = 1000f;

    private LocalProbes() {
    }

    /**
     * A box probe centred on {@code centre} whose walls are {@code halfExtents} from it along the world axes (the room's inner
     * faces: reflections are projected on them). The probe lights things within them and a short way beyond, fading out over
     * {@link ProbeBlend#FEATHER} of the extents; its area, which the light filter culls by, is that far out. Not baked yet, so
     * not lit with.
     */
    public static LightProbe box(Vector3f centre, Vector3f halfExtents) {
        if (!(halfExtents.x > 0f && halfExtents.y > 0f && halfExtents.z > 0f)) {
            throw new IllegalArgumentException("a box probe needs half extents above 0: " + halfExtents);
        }
        var probe = new LightProbe();
        probe.setAreaType(LightProbe.AreaType.OrientedBox);
        probe.setPosition(centre);
        ((OrientedBoxProbeArea) probe.getArea()).setExtent(halfExtents.mult(1f + ProbeBlend.FEATHER));
        return probe;
    }

    /**
     * Bakes {@code probe} from its centre, of everything under {@code world} but its directional lights (see the class),
     * and makes it ready to light with; the same call bakes it again. On the render thread, outside a frame's drawing, as
     * the sky's probe is.
     *
     * @return how long it took, in milliseconds
     */
    public static double bake(AssetManager assets, RenderManager renderManager, Node world, LightProbe probe) {
        long start = System.nanoTime();
        var suns = new ArrayList<DirectionalLight>();
        boolean placed = false;
        for (var light : world.getLocalLightList()) {
            if (light instanceof DirectionalLight sun) {
                suns.add(sun);
            }
            placed |= light == probe;
        }
        suns.forEach(world::removeLight);
        if (placed) {
            // Its own old light would be in its picture.
            world.removeLight(probe);
        }
        try {
            var baked = FastLightProbeFactory.makeProbe(renderManager, assets, SIZE, probe.getPosition(), NEAR, FAR, world);
            probe.setPrefilteredMap(baked.getPrefilteredEnvMap());
            probe.setNbMipMaps(baked.getNbMipMaps());
            probe.setShCoeffs(baked.getShCoeffs());
            probe.setReady(true);
        } finally {
            suns.forEach(world::addLight);
            if (placed) {
                world.addLight(probe);
            }
        }
        return (System.nanoTime() - start) / 1e6;
    }

    /**
     * The probes a view's pixels may be lit with, in the order PBRLighting blends them (narrowest first): the ready ones on
     * the roots of {@code scenes} that reach the camera's frustum. PBRLighting takes the nearest three to each thing it
     * draws; here, to the camera.
     *
     * <p>shortcut: with more than three probes in reach a pixel may be lit with a different three than these (the nearest to
     * the thing, not to the eye); upgrade when a game packs rooms closer than that.
     */
    static List<LightProbe> seenFrom(List<Spatial> scenes, Camera camera) {
        var found = new ArrayList<LightProbe>();
        var vars = TempVars.get();
        try {
            for (var scene : scenes) {
                for (var light : scene.getLocalLightList()) {
                    if (light instanceof LightProbe probe && probe.isEnabled() && probe.isReady()
                            && probe.getPrefilteredEnvMap() != null && probe.intersectsFrustum(camera, vars)) {
                        found.add(probe);
                    }
                }
            }
        } finally {
            vars.release();
        }
        found.sort(Comparator.comparingDouble(probe -> probe.getPosition().distanceSquared(camera.getLocation())));
        var nearest = new ArrayList<>(found.subList(0, Math.min(found.size(), MOST)));
        nearest.sort(LightProbe.NARROWEST_FIRST);
        return nearest;
    }
}
