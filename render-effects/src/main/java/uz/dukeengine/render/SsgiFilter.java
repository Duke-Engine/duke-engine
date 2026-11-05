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
import java.util.List;
import java.util.logging.Logger;

/**
 * One bounce of diffuse light off what the screen shows, over {@link SceneBuffers}: a red wall tints the floor by it, the
 * shade side of a thing in low sun is lit by the ground the sun lights. At half resolution, each pixel sends two rays a
 * frame into the hemisphere about its normal (cosine weighted, so that the average of what they find is the irradiance),
 * marched over the depth buffer; what a ray finds is the lit picture's own colour there, this frame's, as the filter
 * receives it. The rays of the last frames are summed (found again where {@link SceneBuffers#velocity} says the pixel was,
 * clipped to what this frame's 3x3 holds), smoothed over what is one surface, brought up to full resolution by depth and
 * normal, and added times the surface's diffuse colour from {@link SceneBuffers#albedo}. A ray that misses brings nothing:
 * the sky's light is the probe's, and what stands in its way is darkened by {@link GtaoFilter}, which comes before this,
 * so that the bounce is not darkened by the occlusion it is the other half of.
 *
 * <p>Without scene buffers (a GPU that cannot draw them) the filter turns itself off.
 *
 * <p>shortcut: only what the screen shows bounces: light off what is behind the camera, off the screen or behind the nearest
 * surface is not in it, and a ray ends at {@link #REACH}; upgrade with a probe or a voxel scene when a game's rooms need the
 * rest. One bounce, with this frame's picture: no light of the bounce is bounced again. A camera that jumps leaves the sum
 * of the old view for a few frames, where depth and the 3x3 do not reject it. Vertex colour does not tint the surface's colour.
 */
public final class SsgiFilter extends Filter {

    private static final Logger LOG = Logger.getLogger(SsgiFilter.class.getName());
    /** How far a ray goes, in world units: a room, the gap between two houses. */
    static final float REACH = 6f;
    /** The frames a half-resolution pixel's four full-resolution ones are visited in. */
    private static final int BLOCK = 4;
    private static final int TURN = 64;

    private final Matrix4f inverse = new Matrix4f();
    private boolean onlyGi;
    private ViewPort view;
    private Pass trace;
    private Pass blur;
    private final Pass[] sums = new Pass[2];
    private Material traceMaterial;
    private Material blurMaterial;
    private List<Pass> drawing = List.of();
    private int frame;

    public SsgiFilter() {
        super("SSGI");
    }

    /** Shows the bounce alone, as it is added: for the bench to look at. */
    public SsgiFilter showBounceOnly() {
        onlyGi = true;
        return this;
    }

    /**
     * The full-resolution pixel of its 2x2 block that the half-resolution pixel {@code (x, y)} stands for in this frame: the
     * four of the block in four frames, neighbours at different ones. The same as {@code ssgiSource} in Ssgi.glsllib.
     */
    static int[] source(int x, int y, int frame) {
        int corner = (frame + x + 2 * y) & (BLOCK - 1);
        return new int[] {x * 2 + (corner & 1), y * 2 + (corner >> 1)};
    }

    @Override
    protected Format getDefaultPassTextureFormat() {
        return Format.RGBA16F;
    }

    @Override
    protected void initFilter(AssetManager manager, RenderManager renderManager, ViewPort vp, int w, int h) {
        view = vp;
        postRenderPasses = new ArrayList<>();
        drawing = List.of();
        material = new Material(manager, "Common/MatDefs/Duke/SsgiApply.j3md");
        var renderer = renderManager.getRenderer();
        if (!renderer.getCaps().contains(Caps.GLSL150) || !renderer.getCaps().contains(Caps.FloatColorBuffer)) {
            LOG.warning("this GPU cannot draw global illumination (GLSL 1.50, float targets): the picture goes without it");
            setEnabled(false);
            return;
        }
        var found = SceneBuffers.on(manager, renderManager, vp, w, h);
        if (found.isEmpty()) {
            setEnabled(false);
            return;
        }
        var buffers = found.get();
        int lowW = Math.max(1, w / 2);
        int lowH = Math.max(1, h / 2);

        traceMaterial = new Material(manager, "Common/MatDefs/Duke/Ssgi.j3md");
        traceMaterial.setTexture("Depth", buffers.depth());
        traceMaterial.setTexture("Surface", buffers.normalRoughness());
        traceMaterial.setFloat("Reach", REACH);
        // The lit picture is what the rays find: this pass is handed it as its Texture.
        trace = new Pass("SSGI rays") {
            @Override
            public boolean requiresSceneAsTexture() {
                return true;
            }
        };
        trace.init(renderer, lowW, lowH, Format.RGBA16F, Format.Depth, 1, traceMaterial);

        // Two sums, each reading the other's: this frame's is what the next one finds.
        var accumulate = new Material[2];
        for (int i = 0; i < 2; i++) {
            accumulate[i] = new Material(manager, "Common/MatDefs/Duke/SsgiAccumulate.j3md");
            accumulate[i].setTexture("Raw", trace.getRenderedTexture());
            accumulate[i].setTexture("Velocity", buffers.velocity());
            sums[i] = new Pass("SSGI sum " + i);
            sums[i].init(renderer, lowW, lowH, Format.RGBA16F, Format.Depth, 1, accumulate[i]);
        }
        accumulate[0].setTexture("History", sums[1].getRenderedTexture());
        accumulate[1].setTexture("History", sums[0].getRenderedTexture());

        blurMaterial = new Material(manager, "Common/MatDefs/Duke/SsgiBlur.j3md");
        blurMaterial.setTexture("Surface", buffers.normalRoughness());
        blur = new Pass("SSGI smooth");
        blur.init(renderer, lowW, lowH, Format.RGBA16F, Format.Depth, 1, blurMaterial);

        trace.getRenderedTexture().setMinFilter(Texture.MinFilter.NearestNoMipMaps);
        trace.getRenderedTexture().setMagFilter(Texture.MagFilter.Nearest);
        blur.getRenderedTexture().setMinFilter(Texture.MinFilter.NearestNoMipMaps);
        blur.getRenderedTexture().setMagFilter(Texture.MagFilter.Nearest);
        // The history is read between its pixels, along the motion.
        for (var sum : sums) {
            sum.getRenderedTexture().setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
            sum.getRenderedTexture().setMagFilter(Texture.MagFilter.Bilinear);
        }
        postRenderPasses.add(trace);
        postRenderPasses.add(sums[0]);
        postRenderPasses.add(sums[1]);
        postRenderPasses.add(blur);

        material.setTexture("Gi", blur.getRenderedTexture());
        material.setTexture("Depth", buffers.depth());
        material.setTexture("Surface", buffers.normalRoughness());
        material.setTexture("Albedo", buffers.albedo());
        material.setBoolean("OnlyGi", onlyGi);
    }

    @Override
    protected void postQueue(RenderQueue queue) {
        if (view == null || trace == null) {
            return;
        }
        frame = (frame + 1) & (TURN - 1);
        var camera = view.getCamera();
        var projection = camera.getProjectionMatrix();
        projection.invert(inverse);
        traceMaterial.setMatrix4("Projection", projection);
        traceMaterial.setMatrix4("ProjectionInverse", inverse);
        traceMaterial.setFloat("Near", camera.getFrustumNear());
        traceMaterial.setInt("Frame", frame);
        material.setMatrix4("ProjectionInverse", inverse);
        int now = frame & 1;
        sums[now].getPassMaterial().setInt("Frame", frame);
        blurMaterial.setTexture("In", sums[now].getRenderedTexture());
        drawing = List.of(trace, sums[now], blur);
    }

    @Override
    protected List<Pass> getPostRenderPasses() {
        return drawing;
    }

    @Override
    protected Material getMaterial() {
        return material;
    }
}
