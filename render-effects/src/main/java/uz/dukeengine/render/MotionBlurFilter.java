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
 * Motion blur over the linear HDR picture, along the screen-space motion {@link SceneBuffers#velocity} tells (which has the
 * jitter of {@link TaaFilter} taken off, so a camera standing still tells none). The longest velocity of each 16x16 tile and
 * of the tiles round it says which way to look and how far, so that a thing in motion blurs over the background beside it
 * and not only within its own silhouette (McGuire et al. 2012). A streak is the velocity times {@link Lens#shutter}, 32 pixels
 * at most; where nothing near moves as much as half a pixel the picture goes through as it is. Runs after {@link TaaFilter},
 * before the bloom.
 *
 * <p>shortcut: a perspective camera only (the depth is turned into a distance by a perspective projection); the velocity is per frame, so the streak shortens at a higher frame rate; a game that wants a fixed shutter time
 * scales {@link Lens#shutter} by its frame time. Only the solid scene has motion ({@link SceneBuffers}); a translucent thing is
 * blurred as what is behind it. The 16 taps are laid with a fixed pattern of noise, not turned each frame.
 */
public final class MotionBlurFilter extends Filter {

    private static final Logger LOG = Logger.getLogger(MotionBlurFilter.class.getName());
    /** A tile is this many pixels: as MotionBlur.frag. */
    private static final int TILE = 16;

    private final Lens lens;
    private final Vector2f depthToView = new Vector2f();
    private ViewPort view;

    public MotionBlurFilter(Lens lens) {
        super("MotionBlur");
        this.lens = lens;
    }

    @Override
    protected void initFilter(AssetManager manager, RenderManager renderManager, ViewPort vp, int w, int h) {
        view = vp;
        postRenderPasses = new ArrayList<>();
        material = new Material(manager, "Common/MatDefs/Duke/MotionBlur.j3md");
        var renderer = renderManager.getRenderer();
        if (!renderer.getCaps().contains(Caps.GLSL150) || !renderer.getCaps().contains(Caps.FloatColorBuffer)) {
            LOG.warning("this GPU cannot hold a float picture: the picture goes without motion blur");
            setEnabled(false);
            return;
        }
        var buffers = SceneBuffers.on(manager, renderManager, vp, w, h);
        if (buffers.isEmpty()) {
            setEnabled(false);
            return;
        }
        var pixels = new Vector2f(w, h);
        var tilesMaterial = new Material(manager, "Common/MatDefs/Duke/TileMax.j3md");
        tilesMaterial.setTexture("Texture", buffers.get().velocity());
        tilesMaterial.setInt("Block", TILE);
        tilesMaterial.setVector2("Pixels", pixels);
        var tiles = new Pass();
        tiles.init(renderManager.getRenderer(), (w + TILE - 1) / TILE, (h + TILE - 1) / TILE, Format.RG16F, Format.Depth, 1, tilesMaterial);
        tiles.getRenderedTexture().setMinFilter(Texture.MinFilter.NearestNoMipMaps);
        tiles.getRenderedTexture().setMagFilter(Texture.MagFilter.Nearest);
        postRenderPasses.add(tiles);
        material.setTexture("Velocity", buffers.get().velocity());
        material.setTexture("Depth", buffers.get().depth());
        material.setTexture("Tiles", tiles.getRenderedTexture());
        material.setVector2("Pixels", pixels);
        material.setFloat("Shutter", lens.shutter());
    }

    @Override
    protected void postQueue(RenderQueue queue) {
        if (view == null || material == null) {
            return;
        }
        var projection = view.getCamera().getProjectionMatrix();
        material.setVector2("ProjectionZ", depthToView.set(projection.m22, projection.m23));
    }

    @Override
    protected Material getMaterial() {
        return material;
    }
}
