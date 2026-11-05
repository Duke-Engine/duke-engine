package uz.dukeengine.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.Matrix4f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.Caps;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Box;
import com.jme3.system.NullRenderer;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class SceneBuffersTest {

    private static final DesktopAssetManager ASSETS = new DesktopAssetManager(true);
    private final RenderManager manager = new RenderManager(new NullRenderer());
    private final ViewPort view = new ViewPort("view", new Camera(16, 16));

    private static Geometry box(String materialDef) {
        var box = new Geometry("box", new Box(1f, 1f, 1f));
        box.setMaterial(new Material(ASSETS, materialDef));
        box.updateGeometricState();
        return box;
    }

    private SceneBuffers buffers() {
        return SceneBuffers.on(ASSETS, manager, view, 16, 16).orElseThrow();
    }

    /** Draws the pass over one frame's queue holding {@code geometry}, as the view would. */
    private void frame(SceneBuffers buffers, Geometry geometry) {
        var queue = new RenderQueue();
        queue.addToQueue(geometry, RenderQueue.Bucket.Opaque);
        buffers.postQueue(queue);
    }

    @Test
    void aViewNothingAskedOfHasNoPass() {
        assertTrue(view.getProcessors().isEmpty());
    }

    @Test
    void askingTwiceAnswersOnePassAndANewSizeNewTextures() {
        var first = buffers();
        var normals = first.normalRoughness();
        assertSame(first, buffers());
        assertSame(normals, first.normalRoughness());
        assertEquals(1, view.getProcessors().size());
        SceneBuffers.on(ASSETS, manager, view, 32, 32);
        assertNotSame(normals, first.normalRoughness());
        assertEquals(32, first.velocity().getImage().getWidth());
        assertEquals(32, first.reflectance().getImage().getWidth());
    }

    @Test
    void aGpuWithoutWhatTheyTakeHasNoBuffersAndNoPass() {
        var poor = new RenderManager(new NullRenderer() {
            @Override
            public EnumSet<Caps> getCaps() {
                return EnumSet.noneOf(Caps.class);
            }
        });
        assertTrue(SceneBuffers.on(ASSETS, poor, view, 16, 16).isEmpty());
        assertTrue(view.getProcessors().isEmpty());
    }

    @Test
    void aPhysicalMaterialHearsWhereItsThingWasLastFrame() {
        var buffers = buffers();
        var box = box("Common/MatDefs/Light/PBRLighting.j3md");
        frame(buffers, box);
        box.setLocalTranslation(new Vector3f(3f, 0f, 0f));
        box.updateGeometricState();
        frame(buffers, box);
        var previous = (Matrix4f) box.getMaterial().getParamValue("PreviousWorldMatrix");
        assertEquals(0f, previous.toTranslationVector().x, "it stood at the origin");
    }

    @Test
    void aSeeThroughThingIsNoSurface() {
        var buffers = buffers();
        var glass = box("Common/MatDefs/Light/PBRLighting.j3md");
        glass.getMaterial().getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        frame(buffers, glass);
        assertNull(glass.getMaterial().getParamValue("PreviousWorldMatrix"));
    }

    @Test
    void aMaterialWithoutTheTechniqueIsDrawnWithoutTouchingItAndThePassLeavesNothingForced() {
        var plain = box("Common/MatDefs/Misc/Unshaded.j3md");
        frame(buffers(), plain);
        assertNull(manager.getForcedTechnique());
        assertNull(manager.getRenderFilter());
        assertNull(plain.getMaterial().getParam("PreviousWorldMatrix"));
    }

    @Test
    void theTwoSkinnedPosesMustFitTheVertexUniforms() {
        assertTrue(SceneBuffers.previousBonesFit(64, 1024), "a usual body on an NVIDIA GPU");
        assertFalse(SceneBuffers.previousBonesFit(130, 1024), "the same GPU runs out");
    }

    @Test
    void thePhysicalMaterialHasTheTechnique() {
        assertNotNull(new Material(ASSETS, "Common/MatDefs/Light/PBRLighting.j3md").getMaterialDef()
                .getTechniqueDefs("SceneBuffers"));
    }
}
