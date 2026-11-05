package uz.dukeengine.render;

import com.jme3.asset.AssetManager;
import com.jme3.light.LightProbe;
import com.jme3.material.Material;
import com.jme3.math.Matrix4f;
import com.jme3.math.Vector2f;
import com.jme3.post.Filter;
import com.jme3.renderer.Caps;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Spatial;
import java.util.List;
import java.util.logging.Logger;

/**
 * Screen space reflections over the finished HDR picture, where the probe's give way: a ray from each glossy pixel along
 * the reflection of the view is marched over {@link SceneBuffers}' depth, and where it meets a surface the picture's own
 * colour there stands for what the pixel reflects. PBRLighting has already added the light probe's reflection to the
 * picture. The filter works that term out again from the probe (its prefiltered cube, the pixel's roughness and the F0 of
 * {@link SceneBuffers#reflectance}), and takes back as much of it as the screen gives, weighted by how sure the hit is,
 * to put the hit in its place: where the ray misses, leaves the picture, goes toward the eye or the surface is rough,
 * the weight is 0 and the picture is the probe's, as it was. Fades, not cuts, in between.
 *
 * <p>The probe is the first ready one on the view's scenes; without one nothing is taken back and the hit is added. A
 * view without scene buffers has no filter (it turns itself off).
 *
 * <p>shortcut: the sun's shadow and the occlusion multiply the whole picture, ambient and its probe's reflection with it,
 * so in a shadow the probe term taken back is more than the picture has and the pixel keeps only the hit (it is never
 * taken below 0); upgrade when clustered forward (3.12) lights in one pass and leaves the probe's term in a buffer of its
 * own. Parallax, horizon fade and more than one probe are not followed, nor is the reflection blurred by roughness: it
 * fades out instead, to the probe's blur. The hit has no thickness behind it, so a thin thing's reflection can leak onto
 * the surface behind it. Orthographic cameras are not covered.
 */
public final class SsrFilter extends Filter {

    private static final Logger LOG = Logger.getLogger(SsrFilter.class.getName());
    /** How far a reflected ray goes, in world units; it fades out over the last 40%. */
    private static final float REACH = 100f;

    private final Matrix4f inverse = new Matrix4f();
    private final Matrix4f viewInverse = new Matrix4f();
    private final Vector2f depthToView = new Vector2f();
    private boolean onlyReflection;
    private ViewPort view;

    public SsrFilter() {
        super("SSR");
    }

    /** Shows how much of the reflection the screen gives alone, as grey: for the bench to look at. */
    public SsrFilter showReflectionOnly() {
        onlyReflection = true;
        return this;
    }

    /** The first probe ready to light with among the roots of {@code scenes}, or none (null). */
    static LightProbe probeIn(List<Spatial> scenes) {
        for (var scene : scenes) {
            for (var light : scene.getLocalLightList()) {
                if (light instanceof LightProbe probe && probe.isReady() && probe.getPrefilteredEnvMap() != null) {
                    return probe;
                }
            }
        }
        return null;
    }

    @Override
    protected void initFilter(AssetManager manager, RenderManager renderManager, ViewPort vp, int w, int h) {
        view = vp;
        material = new Material(manager, "Common/MatDefs/Duke/Ssr.j3md");
        if (!renderManager.getRenderer().getCaps().contains(Caps.GLSL150)) {
            LOG.warning("this GPU cannot draw reflections (GLSL 1.50): the picture goes without them");
            setEnabled(false);
            return;
        }
        var buffers = SceneBuffers.on(manager, renderManager, vp, w, h);
        if (buffers.isEmpty()) {
            setEnabled(false);
            return;
        }
        material.setTexture("Depth", buffers.get().depth());
        material.setTexture("Surface", buffers.get().normalRoughness());
        material.setTexture("Reflectance", buffers.get().reflectance());
        material.setFloat("Reach", REACH);
        material.setBoolean("OnlyReflection", onlyReflection);
    }

    @Override
    protected void postQueue(RenderQueue queue) {
        if (view == null || material == null) {
            return;
        }
        var camera = view.getCamera();
        var projection = camera.getProjectionMatrix();
        material.setMatrix4("Projection", projection);
        material.setMatrix4("ProjectionInverse", projection.invert(inverse));
        material.setMatrix4("ViewInverse", camera.getViewMatrix().invert(viewInverse));
        material.setVector2("ProjectionZ", depthToView.set(projection.m22, projection.m23));
        material.setFloat("Near", camera.getFrustumNear());
        material.setInt("Frame", TaaFilter.turn(processor));
        var probe = probeIn(view.getScenes());
        if (probe == null) {
            material.clearParam("ProbeMap");
        } else {
            material.setTexture("ProbeMap", probe.getPrefilteredEnvMap());
            material.setFloat("ProbeMips", probe.getNbMipMaps());
        }
    }

    @Override
    protected Material getMaterial() {
        return material;
    }
}
