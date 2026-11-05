package uz.dukeengine.render;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.MatParamOverride;
import com.jme3.material.RenderState;
import com.jme3.math.Matrix4f;
import com.jme3.post.SceneProcessor;
import com.jme3.profile.AppProfiler;
import com.jme3.renderer.Caps;
import com.jme3.renderer.Limits;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.Renderer;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.shader.VarType;
import com.jme3.texture.FrameBuffer;
import com.jme3.texture.FrameBuffer.FrameBufferTarget;
import com.jme3.texture.Image.Format;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.ui.Picture;
import java.util.EnumSet;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Depth, view-space normals, roughness, the light a surface sends back head on, and screen-space motion of all that stands solid in the scene, drawn once a frame
 * so that a screen-space effect reads them instead of drawing the scene again. A view nothing asks of has no such pass:
 * a filter asks in its {@code initFilter}, again at each resize as the post processor calls it then.
 *
 * <p>Solid is the Opaque bucket without blending and with depth written. PBRLighting draws its own surface here, normal
 * map, roughness and alpha discard. Any other material draws through {@code SceneBuffers.j3md}: its shape, bones and
 * instances, its roughness 1 and geometric normals. shortcut: an alpha discarding Unshaded or Lighting material is solid
 * here, give those the technique too when a game draws cut-outs with them.
 *
 * <p>shortcut: the pass runs for as long as the view does even if the filter that asked is switched off, count the askers
 * when a game switches effects while it runs.
 */
public final class SceneBuffers implements SceneProcessor {

    private static final Logger LOG = Logger.getLogger(SceneBuffers.class.getName());
    private static final String TECHNIQUE = "SceneBuffers";
    private static final EnumSet<Caps> NEEDED = EnumSet.of(Caps.FrameBufferMRT, Caps.FloatColorBuffer, Caps.FloatTexture);

    private final RenderManager renderManager;
    private final Renderer renderer;
    private final ViewPort view;
    private final Material plain;
    private final Material far;
    private final Picture farQuad = new Picture("scene buffers far");
    private final PoseMemory poses = new PoseMemory();
    private final Matrix4f previousViewProjection = new Matrix4f();
    private final Matrix4f inverse = new Matrix4f();
    private final Matrix4f reprojection = new Matrix4f();
    private final Matrix4f[] world = new Matrix4f[1];
    private final int vertexUniformVectors;
    private boolean cameraSeen;
    private boolean warnedOfBones;
    private FrameBuffer buffer;
    private Texture2D depth;
    private Texture2D normalRoughness;
    private Texture2D velocity;
    private Texture2D reflectance;

    private SceneBuffers(AssetManager assets, RenderManager renderManager, ViewPort view) {
        this.renderManager = renderManager;
        this.renderer = renderManager.getRenderer();
        this.view = view;
        this.vertexUniformVectors = renderer.getLimits().getOrDefault(Limits.VertexUniformVectors, 0);
        plain = new Material(assets, "Common/MatDefs/Duke/SceneBuffers.j3md");
        far = new Material(assets, "Common/MatDefs/Duke/SceneBuffers.j3md");
        far.selectTechnique("Far", renderManager);
        far.getAdditionalRenderState().setDepthWrite(false);
        farQuad.setWidth(1f);
        farQuad.setHeight(1f);
        farQuad.setMaterial(far);
    }

    /**
     * The buffers of {@code view} at this size, made if nothing asked for them before. Empty, with a warning, on a GPU
     * without what they take: the filter that asked carries on without them.
     */
    public static Optional<SceneBuffers> on(AssetManager assets, RenderManager renderManager, ViewPort view, int width,
            int height) {
        if (!renderManager.getRenderer().getCaps().containsAll(NEEDED)) {
            LOG.warning("this GPU cannot draw the scene buffers: no effect that reads them");
            return Optional.empty();
        }
        for (var processor : view.getProcessors()) {
            if (processor instanceof SceneBuffers found) {
                found.size(width, height);
                return Optional.of(found);
            }
        }
        var made = new SceneBuffers(assets, renderManager, view);
        made.size(width, height);
        view.addProcessor(made);
        return Optional.of(made);
    }

    /** The scene's depth, as the post processor's own depth texture holds it; 1 where nothing solid stands. */
    public Texture2D depth() {
        return depth;
    }

    /** RGBA16F: xyz a unit normal in view space, 0 where nothing solid stands; a the roughness. */
    public Texture2D normalRoughness() {
        return normalRoughness;
    }

    /** RG16F: where the point is now on the screen less where it was last frame, in texture coordinates. */
    public Texture2D velocity() {
        return velocity;
    }

    /**
     * RGBA8: rgb the light the surface sends back head on (about 4% of it on paint, the metal's own colour on a metal),
     * a 1 where it reflects the probe as PBRLighting does and 0 where it does not (every other material, the sky).
     */
    public Texture2D reflectance() {
        return reflectance;
    }

    private void size(int width, int height) {
        if (buffer != null && buffer.getWidth() == width && buffer.getHeight() == height) {
            return;
        }
        disposeBuffers();
        depth = target(width, height, Format.Depth);
        normalRoughness = target(width, height, Format.RGBA16F);
        velocity = target(width, height, Format.RG16F);
        reflectance = target(width, height, Format.RGBA8);
        buffer = new FrameBuffer(width, height, 1);
        buffer.setName("SceneBuffers");
        buffer.setMultiTarget(true);
        buffer.setDepthTarget(FrameBufferTarget.newTarget(depth));
        buffer.addColorTarget(FrameBufferTarget.newTarget(normalRoughness));
        buffer.addColorTarget(FrameBufferTarget.newTarget(velocity));
        buffer.addColorTarget(FrameBufferTarget.newTarget(reflectance));
    }

    private static Texture2D target(int width, int height, Format format) {
        var texture = new Texture2D(width, height, format);
        texture.setMinFilter(Texture.MinFilter.NearestNoMipMaps);
        texture.setMagFilter(Texture.MagFilter.Nearest);
        return texture;
    }

    @Override
    public void postQueue(RenderQueue queue) {
        var camera = view.getCamera();
        var viewProjection = camera.getViewProjectionMatrix();
        if (!cameraSeen) {
            previousViewProjection.set(viewProjection);
            cameraSeen = true;
        }
        renderer.setFrameBuffer(buffer);
        renderer.clearBuffers(true, true, false);
        renderManager.setForcedTechnique(TECHNIQUE);
        renderManager.setForcedMaterial(plain);
        var filter = renderManager.getRenderFilter();
        renderManager.setRenderFilter(this::prepare);
        queue.renderQueue(RenderQueue.Bucket.Opaque, renderManager, camera, false);
        renderManager.setRenderFilter(filter);
        renderManager.setForcedMaterial(null);
        renderManager.setForcedTechnique(null);

        previousViewProjection.mult(inverse.set(viewProjection).invertLocal(), reprojection);
        far.setMatrix4("Reprojection", reprojection);
        farQuad.updateGeometricState();
        renderManager.renderGeometry(farQuad);

        previousViewProjection.set(viewProjection);
        poses.endFrame();
        renderer.setFrameBuffer(view.getOutputFrameBuffer());
    }

    /** Runs before each geometry's draw: is it solid, and what was it last frame. */
    private boolean prepare(Geometry geometry) {
        var surface = geometry.getMaterial();
        var state = surface.getAdditionalRenderState();
        if (state.getBlendMode() != RenderState.BlendMode.Off || !state.isDepthWrite()) {
            return false;
        }
        var drawnBy = surface;
        if (surface.getMaterialDef().getTechniqueDefs(TECHNIQUE) == null) {
            drawnBy = plain;
            plain.setBoolean("UseInstancing", Boolean.TRUE.equals(surface.getParamValue("UseInstancing")));
        }
        drawnBy.setMatrix4("PreviousViewProjection", previousViewProjection);
        world[0] = geometry.isIgnoreTransform() ? Matrix4f.IDENTITY : geometry.getWorldMatrix();
        drawnBy.setMatrix4("PreviousWorldMatrix", poses.previous(geometry, world)[0]);
        var bones = bonesOf(geometry);
        if (bones != null && previousBonesFit(bones.length, vertexUniformVectors)) {
            drawnBy.setParam("PreviousBoneMatrices", VarType.Matrix4Array, poses.previous(bones, bones));
        } else {
            drawnBy.clearParam("PreviousBoneMatrices");
            if (bones != null && !warnedOfBones) {
                warnedOfBones = true;
                LOG.warning(bones.length + " bones twice over do not fit this GPU's uniforms: such bodies tell their motion as one piece");
            }
        }
        return true;
    }

    private static Matrix4f[] bonesOf(Geometry geometry) {
        for (MatParamOverride override : geometry.getWorldMatParamOverrides().getArray()) {
            if (override.isEnabled() && override.getName().equals("BoneMatrices")) {
                return (Matrix4f[]) override.getValue();
            }
        }
        return null;
    }

    /** A skinned vertex shader holds two matrices of four vectors a bone, the count rounded up as SkinningControl does it. */
    static boolean previousBonesFit(int bones, int vertexUniformVectors) {
        return (bones / 10 + 1) * 10 * 8 + 32 <= vertexUniformVectors;
    }

    private void disposeBuffers() {
        if (buffer != null) {
            buffer.dispose();
            depth.getImage().dispose();
            normalRoughness.getImage().dispose();
            velocity.getImage().dispose();
            reflectance.getImage().dispose();
        }
    }

    @Override
    public void initialize(RenderManager rm, ViewPort vp) {
    }

    @Override
    public void reshape(ViewPort vp, int w, int h) {
    }

    @Override
    public boolean isInitialized() {
        return true;
    }

    @Override
    public void preFrame(float tpf) {
    }

    @Override
    public void postFrame(FrameBuffer out) {
    }

    @Override
    public void cleanup() {
        disposeBuffers();
    }

    @Override
    public void setProfiler(AppProfiler profiler) {
    }
}
