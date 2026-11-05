package uz.dukeengine.render;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector4f;
import com.jme3.post.Filter;
import com.jme3.renderer.Caps;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.Renderer;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.texture.FrameBuffer;
import com.jme3.texture.Texture;
import java.util.logging.Logger;

/**
 * Draws the {@link Water} of the view, the first thing in the chain. The engine's main pass leaves the Translucent bucket alone;
 * this filter copies the picture so far (the sky, the ground, everything solid but the water) on to its output, with its depth
 * for the water to be tested against, and draws the bucket over it with the picture itself and its depth for the water to read:
 * the water is not blended over the picture but made of it. Everything after this filter, SSR and the fog and the rest, sees
 * a picture with the water in it, and {@link SceneBuffers} (which draws the same surface in its own pass) has its depth, its
 * normals and its reflectance, so that they treat it as the glossy surface it is.
 *
 * <p>The bucket is flushed here: a game that also puts a {@code TranslucentBucketFilter} later in the chain finds it empty.
 * Whatever else a game draws in that bucket is drawn here too, onto the picture, before the effects.
 */
public final class WaterFilter extends Filter {

    private static final Logger LOG = Logger.getLogger(WaterFilter.class.getName());

    private final Vector4f projection = new Vector4f();
    private RenderManager renderManager;
    private ViewPort view;
    private Texture depth;
    private FrameBuffer scene;

    public WaterFilter() {
        super("Water");
    }

    @Override
    protected void initFilter(AssetManager manager, RenderManager rm, ViewPort vp, int w, int h) {
        renderManager = rm;
        view = vp;
        // The pass is a copy: the picture is what the water refracts.
        material = new Material(manager, "Common/MatDefs/Post/Overlay.j3md");
        material.setColor("Color", ColorRGBA.White);
        if (!rm.getRenderer().getCaps().contains(Caps.GLSL150)) {
            LOG.warning("this GPU cannot draw water (GLSL 1.50): the water is left out");
            setEnabled(false);
        }
    }

    @Override
    protected boolean isRequiresDepthTexture() {
        return true;
    }

    @Override
    protected boolean isRequiresBilinear() {
        return true;
    }

    @Override
    protected void setDepthTexture(Texture depthTexture) {
        depth = depthTexture;
    }

    @Override
    protected void postFrame(RenderManager rm, ViewPort vp, FrameBuffer previous, FrameBuffer sceneBuffer) {
        scene = sceneBuffer;
    }

    @Override
    protected void postFilter(Renderer renderer, FrameBuffer output) {
        var camera = view.getCamera();
        var matrix = camera.getProjectionMatrix();
        projection.set(matrix.m00, matrix.m11, matrix.m22, matrix.m23);
        // The pass copied the colour; the solid scene's depth is put in with it, for the water to hide behind what stands in front.
        renderer.copyFrameBuffer(scene, output, false, true);
        renderManager.setCamera(camera, false);
        renderer.setFrameBuffer(output);
        var filter = renderManager.getRenderFilter();
        renderManager.setRenderFilter(this::prepare);
        view.getQueue().renderQueue(RenderQueue.Bucket.Translucent, renderManager, camera, true);
        renderManager.setRenderFilter(filter);
    }

    /** Runs before each geometry's draw: a water surface is given the picture, its depth and the projection. */
    private boolean prepare(Geometry geometry) {
        var surface = geometry.getMaterial();
        if (surface.getMaterialDef().getMaterialParam("Behind") != null) {
            surface.setTexture("Scene", (Texture) material.getParam("Texture").getValue());
            surface.setTexture("Behind", depth);
            surface.setVector4("Projection", projection);
        }
        return true;
    }

    @Override
    protected Material getMaterial() {
        return material;
    }
}
