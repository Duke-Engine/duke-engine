package uz.dukeengine.render;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.post.Filter;
import com.jme3.renderer.Caps;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.Renderer;
import com.jme3.renderer.ViewPort;
import com.jme3.texture.Image.Format;
import com.jme3.texture.Texture;
import com.jme3.util.BufferUtils;
import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * Bends an HDR picture into what a screen can show, keeping highlights' detail as film does. With {@code adapting} an eye
 * looks at the picture first: the mean log luminance of what it holds, the middle counting most, is found over three small
 * passes and the eye moves toward it over time, faster when the picture turns brighter than when it turns darker. What it
 * asks of the exposure multiplies the exposure given here, which becomes the compensation. The eye lives on the GPU, in
 * two 1x1 targets that swap each frame, so nothing is read back.
 *
 * <p>shortcut: the average is of the whole picture, sky and a sun disc included (clamped, not left out), and the middle is
 * the middle of the screen; give it a mask or a spot when a game's sky skews the eye.
 */
public final class AcesToneMap extends Filter {

    private static final Logger LOG = Logger.getLogger(AcesToneMap.class.getName());
    /** The measure is of this many texels square, reduced by {@link #BLOCK} to an eighth of that, then to one. */
    private static final int MEASURED = 64;
    private static final int BLOCK = 8;
    private static final float BRIGHTER_SECONDS = 0.5f;
    private static final float DARKER_SECONDS = 2f;
    /** A frame that took longer (a load, a stall) moves the eye as far as this one would: it did not see the time pass. */
    private static final float LONGEST_FRAME = 0.1f;

    private final float exposure;
    private final boolean adapting;
    private Material adapt;
    private Pass[] eyes;
    private int eye;
    private boolean fresh;

    public AcesToneMap(float exposure, boolean adapting) {
        super("AcesToneMap");
        this.exposure = exposure;
        this.adapting = adapting;
    }

    /** The share of the way to its mark an eye goes in {@code dt} seconds, when it takes {@code seconds} to cover most of it. */
    static float rate(float dt, float seconds) {
        return 1f - (float) Math.exp(-dt / seconds);
    }

    @Override
    protected void initFilter(AssetManager manager, RenderManager renderManager, ViewPort vp, int w, int h) {
        material = new Material(manager, "Common/MatDefs/Duke/AcesToneMap.j3md");
        material.setFloat("Exposure", exposure);
        eyes = null;
        eye = 0;
        if (!adapting) {
            return;
        }
        var renderer = renderManager.getRenderer();
        if (!renderer.getCaps().contains(Caps.GLSL150) || !renderer.getCaps().contains(Caps.FloatColorBuffer)) {
            LOG.warning("this GPU cannot hold a float picture: the eye does not adapt, the exposure stays as given");
            return;
        }
        postRenderPasses = new ArrayList<>();
        var measure = new Pass() {
            @Override
            public boolean requiresSceneAsTexture() {
                return true;
            }
        };
        measure.init(renderer, MEASURED, MEASURED, Format.RG16F, Format.Depth, 1,
                new Material(manager, "Common/MatDefs/Duke/ExposureMeasure.j3md"));
        var reduce = new Pass();
        reduce.init(renderer, MEASURED / BLOCK, MEASURED / BLOCK, Format.RG16F, Format.Depth, 1,
                new Material(manager, "Common/MatDefs/Duke/ExposureReduce.j3md"));
        reduce.getPassMaterial().setTexture("Texture", measure.getRenderedTexture());
        adapt = new Material(manager, "Common/MatDefs/Duke/ExposureAdapt.j3md");
        adapt.setTexture("Texture", reduce.getRenderedTexture());
        eyes = new Pass[2];
        for (int i = 0; i < 2; i++) {
            eyes[i] = new Pass();
            eyes[i].init(renderer, 1, 1, Format.RGBA32F, Format.Depth, 1, adapt);
            // Read at a texel's centre only, and 32 bit floats need not filter.
            eyes[i].getRenderedTexture().setMinFilter(Texture.MinFilter.NearestNoMipMaps);
            eyes[i].getRenderedTexture().setMagFilter(Texture.MagFilter.Nearest);
        }
        postRenderPasses.add(measure);
        postRenderPasses.add(reduce);
        postRenderPasses.add(eyes[0]);
        fresh = true;
    }

    @Override
    protected void preFrame(float tpf) {
        if (eyes == null) {
            return;
        }
        // The first frame, and the first after a resize, takes the picture as it is: no eye to wait for.
        adapt.setFloat("BrighterRate", fresh ? 1f : rate(Math.min(tpf, LONGEST_FRAME), BRIGHTER_SECONDS));
        adapt.setFloat("DarkerRate", fresh ? 1f : rate(Math.min(tpf, LONGEST_FRAME), DARKER_SECONDS));
        fresh = false;
        eye = 1 - eye;
        postRenderPasses.set(2, eyes[eye]);
        adapt.setTexture("Previous", eyes[1 - eye].getRenderedTexture());
        material.setTexture("Adapted", eyes[eye].getRenderedTexture());
    }

    /**
     * What the eye holds after the last frame: the mean log2 luminance it has adapted to, what that asks of the exposure,
     * and the mean log2 luminance the picture measured. Reads the GPU, so it stalls it: for the bench to log, not a game.
     * Null without an eye.
     */
    public float[] eyeNow(Renderer renderer) {
        if (eyes == null) {
            return null;
        }
        var bytes = BufferUtils.createByteBuffer(16);
        renderer.readFrameBufferWithFormat(eyes[eye].getRenderFrameBuffer(), bytes, Format.RGBA32F);
        var floats = bytes.asFloatBuffer();
        return new float[] {floats.get(0), floats.get(1), floats.get(2)};
    }

    @Override
    protected void cleanUpFilter(Renderer r) {
        // The one the list does not hold is not cleaned with it.
        if (eyes != null && postRenderPasses != null) {
            eyes[1 - eye].cleanup(r);
        }
    }

    @Override
    protected Material getMaterial() {
        return material;
    }
}
