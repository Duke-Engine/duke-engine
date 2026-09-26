package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.post.SceneProcessor;
import com.jme3.profile.AppProfiler;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.texture.FrameBuffer;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.ui.Picture;
import com.jme3.util.BufferUtils;

/**
 * The whole picture drawn through a gamma — the reference's gamma ramp ({@code DX8Wrapper::Set_Gamma}), set by its
 * display each time it is set up ({@code W3DDisplay::init}) and by its options as they change: once the frame is drawn,
 * world and interface, it is copied and drawn back through the ramp. At 1 nothing is done at all.
 */
final class GammaPass implements SceneProcessor {

    /** The reference's bounds on a gamma ({@code Set_Gamma}). */
    static final float LEAST = 0.6f;
    static final float MOST = 6f;

    private final AssetManager assets;
    private final Picture quad = new Picture("gamma");
    private float gamma = 1f;
    private RenderManager renderManager;
    private int width;
    private int height;
    /** The frame as drawn, copied; made the first frame drawn through a gamma, and again for a new size. */
    private FrameBuffer copy;
    private Material material;

    GammaPass(AssetManager assets) {
        this.assets = assets;
        quad.setWidth(1f);
        quad.setHeight(1f);
    }

    /** The gamma the picture is drawn through, bounded as the reference's is; 1 for none. */
    void setGamma(float gamma) {
        this.gamma = gamma == 1f ? 1f : Math.clamp(gamma, LEAST, MOST);
        if (material != null) {
            material.setTexture("Ramp", rampTexture(this.gamma));
        }
    }

    /** Whether the picture is drawn through a gamma, rather than as it always was. */
    boolean drawsThrough() {
        return gamma != 1f;
    }

    /**
     * What each of a channel's 256 values shows as through {@code gamma}, 0 to 255 — the reference's ramp at its
     * options' brightness and contrast, 0 and 1: value i shows as (i/256)^(1/gamma), its 16-bit word's high byte.
     */
    static byte[] ramp(float gamma) {
        var ramp = new byte[256];
        double over = 1.0 / gamma;
        for (int i = 0; i < ramp.length; i++) {
            double out = Math.clamp(Math.pow(i / 256.0, over), 0.0, 1.0);
            ramp[i] = (byte) ((int) (out * 65535) >> 8);
        }
        return ramp;
    }

    private static Texture2D rampTexture(float gamma) {
        var ramp = ramp(gamma);
        var pixels = BufferUtils.createByteBuffer(ramp.length * 4);
        for (byte value : ramp) {
            pixels.put(value).put(value).put(value).put((byte) 0xFF);
        }
        pixels.flip();
        var texture = new Texture2D(new Image(Image.Format.RGBA8, ramp.length, 1, pixels, ColorSpace.Linear));
        texture.setMagFilter(Texture.MagFilter.Nearest);
        texture.setMinFilter(Texture.MinFilter.NearestNoMipMaps);
        return texture;
    }

    @Override
    public void initialize(RenderManager renderManager, ViewPort viewPort) {
        this.renderManager = renderManager;
        reshape(viewPort, viewPort.getCamera().getWidth(), viewPort.getCamera().getHeight());
    }

    @Override
    public void reshape(ViewPort viewPort, int width, int height) {
        this.width = width;
        this.height = height;
        copy = null;
    }

    @Override
    public boolean isInitialized() {
        return renderManager != null;
    }

    @Override
    public void preFrame(float tpf) {
    }

    @Override
    public void postQueue(RenderQueue queue) {
    }

    @Override
    public void postFrame(FrameBuffer out) {
        if (gamma == 1f || renderManager == null || width <= 0 || height <= 0) {
            return;
        }
        if (copy == null) {
            var frame = new Texture2D(width, height, Image.Format.RGBA8);
            copy = new FrameBuffer(width, height, 1);
            copy.addColorTarget(FrameBuffer.FrameBufferTarget.newTarget(frame));
            if (material == null) {
                material = new Material(assets, "MatDefs/duke/Gamma.j3md");
                material.setTexture("Ramp", rampTexture(gamma));
                quad.setMaterial(material);
            }
            material.setTexture("Frame", frame);
        }
        var renderer = renderManager.getRenderer();
        renderer.copyFrameBuffer(out, copy, true, false);
        boolean srgb = renderer.isMainFrameBufferSrgb();
        renderer.setMainFrameBufferSrgb(false); // the values as they are shown, as a ramp takes them
        renderer.setFrameBuffer(out);
        quad.updateGeometricState();
        renderManager.renderGeometry(quad);
        renderer.setMainFrameBufferSrgb(srgb);
    }

    @Override
    public void cleanup() {
        renderManager = null;
        copy = null;
    }

    @Override
    public void setProfiler(AppProfiler profiler) {
    }
}
