package uz.dukeengine.render;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.Matrix4f;
import com.jme3.math.Vector2f;
import com.jme3.post.Filter;
import com.jme3.post.FilterPostProcessor;
import com.jme3.renderer.Caps;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.Renderer;
import com.jme3.renderer.ViewPort;
import com.jme3.texture.FrameBuffer;
import com.jme3.texture.FrameBuffer.FrameBufferTarget;
import com.jme3.texture.Image.Format;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import java.util.logging.Logger;

/**
 * Temporal anti-aliasing. Each frame the camera's projection is shifted a fraction of a pixel (Halton 2, 3, eight in a
 * cycle), by the same matrix for the scene and for {@link SceneBuffers}, so that their depth, normals and the picture
 * line up, and the motion those buffers tell is worked out as if there were no shift: a camera standing still has none.
 * The shadow maps are drawn from their own cameras and keep still. Then the frame is summed with what the last frames
 * made of the picture, found again where {@link SceneBuffers#velocity} says the pixel was (the nearest surface of its
 * 3x3 for the motion, so that an edge follows what is in front), pulled back into the colours that 3x3 holds (variance
 * clipping in YCoCg, the history being what is false when something moves or opens up) and blended about a tenth new.
 * The sum is of Karis-weighted colour, so that a bright pixel does not set the picture flickering. Run it after the effects
 * that draw a grain (occlusion, reflections, shafts, shadows), which turn their pattern with {@link #turn}, and before
 * the bloom and the tone curve; a filter must follow it, which is what carries the picture on.
 *
 * <p>The history is dropped for a frame at the first frame, after a resize and on {@link #cut}, which a game calls when
 * the camera jumps (a new map, a teleport): motion cannot tell that from a very fast move.
 *
 * <p>shortcut: only what {@link SceneBuffers} draws has motion, so a blended (translucent) thing that moves trails the
 * history of what is behind it; give those their own motion when a game has fast translucent things. A camera that carries its
 * own projection override loses it: the jitter is put on the camera's own projection.
 */
public final class TaaFilter extends Filter {

    private static final Logger LOG = Logger.getLogger(TaaFilter.class.getName());
    private static final int SAMPLES = 8;
    /** A pattern turned with {@link #turn} repeats every this many frames: a power of two. */
    private static final int TURN = 64;

    private final Vector2f pixels = new Vector2f();
    private final Matrix4f projection = new Matrix4f();
    private ViewPort view;
    private SceneBuffers buffers;
    private Texture2D history;
    private FrameBuffer historyBuffer;
    private int width;
    private int height;
    private int frame;
    private boolean cut = true;

    public TaaFilter() {
        super("TAA");
    }

    /** The next frame is drawn with no history: the camera has jumped, or the picture is another. */
    public void cut() {
        cut = true;
    }

    /**
     * The frame number a pattern that is drawn as noise should turn with, so that TAA averages it away: this chain's TAA
     * frame if it has one on, else 0, which holds a pattern still, as it is where nothing averages it.
     */
    static int turn(FilterPostProcessor chain) {
        if (chain != null) {
            for (var it = chain.getFilterIterator(); it.hasNext();) {
                if (it.next() instanceof TaaFilter taa && taa.isEnabled() && taa.buffers != null) {
                    return taa.frame & (TURN - 1);
                }
            }
        }
        return 0;
    }

    /** The RGBA16F the picture is summed in, where the chain's own may be a packed 32 bit one that would hold a rounding. */
    @Override
    protected Format getDefaultPassTextureFormat() {
        return Format.RGBA16F;
    }

    @Override
    protected void initFilter(AssetManager manager, RenderManager renderManager, ViewPort vp, int w, int h) {
        view = vp;
        width = w;
        height = h;
        buffers = null;
        material = new Material(manager, "Common/MatDefs/Duke/Taa.j3md");
        var renderer = renderManager.getRenderer();
        if (!renderer.getCaps().contains(Caps.GLSL150) || !renderer.getCaps().contains(Caps.FloatColorBuffer)) {
            LOG.warning("this GPU cannot sum frames (GLSL 1.50, float targets): no temporal anti-aliasing");
            setEnabled(false);
            return;
        }
        var found = SceneBuffers.on(manager, renderManager, vp, w, h);
        if (found.isEmpty()) {
            setEnabled(false);
            return;
        }
        buffers = found.get();
        disposeHistory(renderer);
        history = new Texture2D(w, h, Format.RGBA16F);
        history.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        history.setMagFilter(Texture.MagFilter.Bilinear);
        historyBuffer = new FrameBuffer(w, h, 1);
        historyBuffer.setName("TAA history");
        historyBuffer.addColorTarget(FrameBufferTarget.newTarget(history));
        material.setTexture("History", history);
        material.setTexture("Depth", buffers.depth());
        material.setTexture("Velocity", buffers.velocity());
        cut = true;
    }

    @Override
    protected void preFrame(float tpf) {
        if (buffers == null) {
            return;
        }
        frame++;
        Jitter.pixels(frame, SAMPLES, pixels);
        // A pixel is 2 / width across in device coordinates.
        float x = 2f * pixels.x / width;
        float y = 2f * pixels.y / height;
        var camera = view.getCamera();
        camera.setProjectionMatrix(null);
        projection.set(camera.getProjectionMatrix());
        camera.setProjectionMatrix(Jitter.shift(projection, x, y));
        buffers.jittered(x, y);
    }

    @Override
    protected void postFrame(RenderManager renderManager, ViewPort viewPort, FrameBuffer prevFilterBuffer,
            FrameBuffer sceneBuffer) {
        if (buffers == null) {
            return;
        }
        // Everything the scene needed the shift for is drawn: whatever is drawn or asked of the camera from here is not shifted
        // (the translucent queue after the chain lands on this picture, the shift removed).
        view.getCamera().setProjectionMatrix(null);
        buffers.jittered(0f, 0f);
        material.setFloat("Fresh", cut ? 1f : 0f);
        cut = false;
    }

    @Override
    protected void postFilter(Renderer r, FrameBuffer buffer) {
        r.copyFrameBuffer(buffer, historyBuffer, true, false);
    }

    @Override
    protected void cleanUpFilter(Renderer r) {
        disposeHistory(r);
        if (view != null && buffers != null) {
            view.getCamera().setProjectionMatrix(null);
            buffers.jittered(0f, 0f);
        }
    }

    private void disposeHistory(Renderer r) {
        if (historyBuffer != null) {
            r.deleteFrameBuffer(historyBuffer);
            history.getImage().dispose();
            historyBuffer = null;
            history = null;
        }
    }

    @Override
    protected Material getMaterial() {
        return material;
    }
}
