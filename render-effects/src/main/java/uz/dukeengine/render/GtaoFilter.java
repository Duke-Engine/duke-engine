package uz.dukeengine.render;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.Matrix4f;
import com.jme3.post.Filter;
import com.jme3.renderer.Caps;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.texture.Image.Format;
import com.jme3.texture.Texture;
import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * Ground truth ambient occlusion (Jimenez et al. 2016) over a finished picture: how much of the sky a pixel sees, found
 * from the horizons its surroundings leave in a few directions, over the {@link SceneBuffers}' depth and normals instead
 * of a normal pass of its own. A second pass takes the directions of each 3x3 patch back together over what is one surface,
 * and darkens the colour by that, lighter where the surface is, as the light bounces about in a crease.
 *
 * <p>Without scene buffers (a GPU that cannot draw them) the filter turns itself off.
 *
 * <p>shortcut: the picture is darkened whole, direct sun with the ambient, where the occlusion should take the ambient
 * only; upgrade when clustered forward (3.12) lights in one pass and can read this filter's visibility there. Orthographic
 * cameras are not covered: the view direction is taken from the position.
 */
public final class GtaoFilter extends Filter {

    private static final Logger LOG = Logger.getLogger(GtaoFilter.class.getName());

    private final float radius;
    private final Matrix4f inverse = new Matrix4f();
    private boolean onlyOcclusion;
    private ViewPort view;
    private Material occlusion;

    /** @param radius how far in world units something can stand and still darken a crease */
    public GtaoFilter(float radius) {
        super("GTAO");
        this.radius = radius;
    }

    /** Shows the visibility alone, as grey: for the bench to look at. */
    public GtaoFilter showOcclusionOnly() {
        onlyOcclusion = true;
        return this;
    }

    @Override
    protected void initFilter(AssetManager manager, RenderManager renderManager, ViewPort vp, int w, int h) {
        view = vp;
        postRenderPasses = new ArrayList<>();
        material = new Material(manager, "Common/MatDefs/Duke/GtaoApply.j3md");
        if (!renderManager.getRenderer().getCaps().contains(Caps.GLSL150)) {
            LOG.warning("this GPU cannot draw ambient occlusion (GLSL 1.50): the picture goes without it");
            setEnabled(false);
            return;
        }
        var buffers = SceneBuffers.on(manager, renderManager, vp, w, h);
        if (buffers.isEmpty()) {
            setEnabled(false);
            return;
        }
        occlusion = new Material(manager, "Common/MatDefs/Duke/Gtao.j3md");
        occlusion.setTexture("Depth", buffers.get().depth());
        occlusion.setTexture("Surface", buffers.get().normalRoughness());
        occlusion.setFloat("Radius", radius);
        var pass = new Pass("GTAO");
        pass.init(renderManager.getRenderer(), w, h, Format.RG16F, Format.Depth, 1, occlusion);
        pass.getRenderedTexture().setMinFilter(Texture.MinFilter.NearestNoMipMaps);
        pass.getRenderedTexture().setMagFilter(Texture.MagFilter.Nearest);
        postRenderPasses.add(pass);
        material.setTexture("Occlusion", pass.getRenderedTexture());
        material.setBoolean("OnlyOcclusion", onlyOcclusion);
    }

    @Override
    protected void postQueue(RenderQueue queue) {
        if (occlusion == null) {
            return;
        }
        var projection = view.getCamera().getProjectionMatrix();
        occlusion.setMatrix4("ProjectionInverse", projection.invert(inverse));
        // Pixels a world unit makes at a distance of 1: the focal length of the picture.
        occlusion.setFloat("Focal", projection.m11 * view.getCamera().getHeight() * 0.5f);
    }

    @Override
    protected Material getMaterial() {
        return material;
    }
}
