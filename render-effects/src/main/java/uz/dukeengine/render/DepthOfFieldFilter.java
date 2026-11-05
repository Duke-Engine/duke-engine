package uz.dukeengine.render;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.Vector2f;
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
 * Depth of field over the linear HDR picture: what stands off the plane {@link Lens#focusDistance} is spread over a disc
 * whose size is that of a real lens ({@link Lens#confusionPixels}), from the scene's depth. At half size the picture is
 * split into the far field and the near field, each gathered over a disc of 48 taps as wide as the widest disc of the
 * neighbourhood (found by a tile maximum), then read back at full size: a pixel in focus is left sharp, so that the blur
 * of what lies behind never runs onto it, and the near field covers what is in focus beyond it, as the out-of-focus
 * edge of a near thing does. Every disc is weighed by its own area, so light is moved and not made. Runs after {@link TaaFilter}
 * so that it blurs a settled picture; the gather is a fixed pattern, so that it does not flicker where nothing averages it.
 *
 * <p>shortcut: a perspective camera only (the depth is turned into a distance by a perspective projection); only the solid scene has a depth ({@link SceneBuffers}), so a translucent thing is blurred as what is behind it;
 * a disc is at most 48 pixels across at any size of picture; the gather is a disc and not the shape of a real aperture's blades;
 * the focus distance is the setting's, so a game that racks focus builds the filter again.
 */
public final class DepthOfFieldFilter extends Filter {

    private static final Logger LOG = Logger.getLogger(DepthOfFieldFilter.class.getName());
    /** A tile of the tile maximum is this many pixels of the half size picture: as DofGather.frag. */
    private static final int TILE = 8;

    private final Lens lens;
    private final Vector2f depthToView = new Vector2f();
    private ViewPort view;
    private int width;
    private Pass prefilter;

    public DepthOfFieldFilter(Lens lens) {
        super("DepthOfField");
        this.lens = lens;
    }

    @Override
    protected void initFilter(AssetManager manager, RenderManager renderManager, ViewPort vp, int w, int h) {
        view = vp;
        width = w;
        postRenderPasses = new ArrayList<>();
        material = new Material(manager, "Common/MatDefs/Duke/DofComposite.j3md");
        var renderer = renderManager.getRenderer();
        if (!renderer.getCaps().contains(Caps.GLSL150) || !renderer.getCaps().contains(Caps.FloatColorBuffer)) {
            LOG.warning("this GPU cannot hold a float picture: the picture goes without depth of field");
            setEnabled(false);
            return;
        }
        var buffers = SceneBuffers.on(manager, renderManager, vp, w, h);
        if (buffers.isEmpty()) {
            setEnabled(false);
            return;
        }
        int halfW = Math.max(1, w / 2);
        int halfH = Math.max(1, h / 2);
        var depth = buffers.get().depth();
        var first = new Material(manager, "Common/MatDefs/Duke/DofPrefilter.j3md");
        first.setTexture("Depth", depth);
        prefilter = new Pass() {
            @Override
            public boolean requiresSceneAsTexture() {
                return true;
            }
        };
        pass(renderManager, prefilter, halfW, halfH, Format.RGBA16F, first);
        var tilesMaterial = new Material(manager, "Common/MatDefs/Duke/TileMax.j3md");
        tilesMaterial.setTexture("Texture", prefilter.getRenderedTexture());
        tilesMaterial.setInt("Block", TILE);
        var tiles = pass(renderManager, new Pass(), (halfW + TILE - 1) / TILE, (halfH + TILE - 1) / TILE, Format.RG16F, tilesMaterial);
        var far = gather(manager, renderManager, halfW, halfH, tiles, false);
        var near = gather(manager, renderManager, halfW, halfH, tiles, true);
        material.setTexture("Depth", depth);
        material.setTexture("Far", far.getRenderedTexture());
        material.setTexture("Near", near.getRenderedTexture());
    }

    private Pass gather(AssetManager manager, RenderManager renderManager, int w, int h, Pass tiles, boolean near) {
        var step = new Material(manager, "Common/MatDefs/Duke/DofGather.j3md");
        step.setTexture("Texture", prefilter.getRenderedTexture());
        step.setTexture("Tiles", tiles.getRenderedTexture());
        step.setBoolean("Near", near);
        return pass(renderManager, new Pass(), w, h, Format.RGBA16F, step);
    }

    /** A step of the chain: bilinear, since the gather's taps and the full size read lean on the GPU's filter. */
    private Pass pass(RenderManager renderManager, Pass pass, int w, int h, Format format, Material step) {
        pass.init(renderManager.getRenderer(), w, h, format, Format.Depth, 1, step);
        pass.getRenderedTexture().setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        pass.getRenderedTexture().setMagFilter(Texture.MagFilter.Bilinear);
        postRenderPasses.add(pass);
        return pass;
    }

    @Override
    protected void postQueue(RenderQueue queue) {
        if (view == null || prefilter == null) {
            return;
        }
        var projection = view.getCamera().getProjectionMatrix();
        depthToView.set(projection.m22, projection.m23);
        float reach = lens.reachPixels(projection.m00, width);
        for (var step : new Material[] {prefilter.getPassMaterial(), material}) {
            step.setVector2("ProjectionZ", depthToView);
            step.setFloat("Focus", lens.focusDistance());
            step.setFloat("Reach", reach);
        }
    }

    @Override
    protected Material getMaterial() {
        return material;
    }
}
