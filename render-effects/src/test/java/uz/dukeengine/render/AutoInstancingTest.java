package uz.dukeengine.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Box;
import com.jme3.system.NullRenderer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What is drawn as one call and what is not; that an instance has the matrix its geometry has now; that nothing is left changed. */
class AutoInstancingTest {

    private static final DesktopAssetManager ASSETS = new DesktopAssetManager(true);
    private static final String PBR = "Common/MatDefs/Light/PBRLighting.j3md";

    private record Draw(Mesh mesh, int count, float[] instances) {
    }

    private static final class Recording extends NullRenderer {
        final List<Draw> draws = new ArrayList<>();

        @Override
        public void renderMesh(Mesh mesh, int lod, int count, VertexBuffer[] instanceData) {
            float[] copy = null;
            if (instanceData != null) {
                copy = new float[count * 16];
                ((FloatBuffer) instanceData[0].getData()).duplicate().rewind().get(copy);
            }
            draws.add(new Draw(mesh, count, copy));
        }
    }

    private final Recording renderer = new Recording();
    private final RenderManager manager = new RenderManager(renderer);
    private final Camera camera = new Camera(16, 16);
    private final Mesh box = new Box(1f, 1f, 1f);

    private Geometry prop(Mesh mesh, Material material, float x) {
        var geometry = new Geometry("prop", mesh);
        geometry.setMaterial(material);
        geometry.setLocalTranslation(x, 0f, -10f);
        geometry.updateGeometricState();
        return geometry;
    }

    private static Material material() {
        return new Material(ASSETS, PBR);
    }

    /** One frame of the opaque bucket holding these. */
    private List<Draw> draw(Geometry... geometries) {
        var queue = new RenderQueue();
        for (var geometry : geometries) {
            queue.addToQueue(geometry, RenderQueue.Bucket.Opaque);
        }
        manager.setCamera(camera, false);
        renderer.draws.clear();
        queue.renderQueue(RenderQueue.Bucket.Opaque, manager, camera, true);
        return new ArrayList<>(renderer.draws);
    }

    private Geometry[] props(Mesh mesh, Material material, int n) {
        var all = new Geometry[n];
        for (int i = 0; i < n; i++) {
            all[i] = prop(mesh, material, i);
        }
        return all;
    }

    @Test
    void withoutABatcherEveryGeometryIsACall() {
        assertEquals(8, draw(props(box, material(), 8)).size());
    }

    @Test
    void geometriesOfOneMeshAndMaterialAreOneCallWithAnInstanceEach() {
        AutoInstancing.on(manager);
        var draws = draw(props(box, material(), 8));
        assertEquals(1, draws.size());
        assertEquals(8, draws.getFirst().count());
        assertEquals(8 * 16, draws.getFirst().instances().length);
    }

    @Test
    void aFewAreDrawnAsTheyAre() {
        AutoInstancing.on(manager);
        var draws = draw(props(box, material(), AutoInstancing.MINIMUM - 1));
        assertEquals(AutoInstancing.MINIMUM - 1, draws.size());
        assertTrue(draws.stream().allMatch(d -> d.count() == 1 && d.instances() == null));
    }

    @Test
    void anotherMeshIsAnotherCall() {
        AutoInstancing.on(manager);
        var material = material();
        var other = new Box(2f, 1f, 1f);
        var all = new ArrayList<Geometry>(List.of(props(box, material, 5)));
        all.addAll(List.of(props(other, material, 6)));
        var draws = draw(all.toArray(new Geometry[0]));
        assertEquals(2, draws.size());
        assertEquals(List.of(5, 6), draws.stream().map(Draw::count).sorted().toList());
    }

    @Test
    void aCopyOfTheMaterialWithEveryParameterTheSameIsTheSameAndOneThatDiffersIsNot() {
        AutoInstancing.on(manager);
        var red = material();
        red.setColor("BaseColor", ColorRGBA.Red);
        var all = new ArrayList<Geometry>();
        for (int i = 0; i < 5; i++) {
            all.add(prop(box, red.clone(), i));
        }
        var blue = material();
        blue.setColor("BaseColor", ColorRGBA.Blue);
        for (int i = 0; i < 6; i++) {
            all.add(prop(box, blue.clone(), 10 + i));
        }
        var draws = draw(all.toArray(new Geometry[0]));
        assertEquals(List.of(5, 6), draws.stream().map(Draw::count).sorted().toList());
    }

    @Test
    void aMaterialTheCopiesOfWhichDifferEachTimeIsLeftAlone() {
        AutoInstancing.on(manager);
        var all = new Geometry[AutoInstancing.MINIMUM * 4];
        for (int i = 0; i < all.length; i++) {
            var tinted = material();
            tinted.setColor("BaseColor", new ColorRGBA(i / 16f, 0f, 0f, 1f));
            all[i] = prop(box, tinted, i);
        }
        var draws = draw(all);
        // The first four tints are batched each with the one geometry that has it: too few. All are drawn as they are.
        assertEquals(all.length, draws.size());
        assertTrue(draws.stream().allMatch(d -> d.count() == 1));
    }

    @Test
    void whatTheInstancedShaderCannotDrawAsThePlainOneIsDrawnAsItIs() {
        AutoInstancing.on(manager);
        var material = material();
        var all = props(box, material, 7);
        all[1].setLocalScale(1f, 2f, 1f);
        all[1].updateGeometricState();
        all[2].setLocalScale(-1f);
        all[2].updateGeometricState();
        var draws = draw(all);
        assertEquals(List.of(1, 1, 5), draws.stream().map(Draw::count).sorted().toList());
        assertFalse(AutoInstancing.uniform(new Vector3f(1f, 2f, 1f)));
        assertFalse(AutoInstancing.uniform(new Vector3f(-1f, -1f, -1f)));
        assertTrue(AutoInstancing.uniform(new Vector3f(3f, 3f, 3f)));
    }

    @Test
    void aMaterialThatHasNoInstancingIsDrawnAsItIs() {
        AutoInstancing.on(manager);
        var sky = new Material(ASSETS, "Common/MatDefs/Misc/Sky.j3md");
        assertNull(sky.getMaterialDef().getMaterialParam("UseInstancing"));
        assertEquals(6, draw(props(box, sky, 6)).size());
    }

    @Test
    void anInstanceHasTheMatrixItsGeometryHasNowAndOneThatMovedIsFollowed() {
        AutoInstancing.on(manager);
        var all = props(box, material(), 6);
        var first = draw(all).getFirst().instances();
        for (int i = 0; i < 6; i++) {
            assertEquals(all[i].getWorldMatrix().m03, first[i * 16 + 12], 0f, "x of instance " + i);
            assertEquals(-10f, first[i * 16 + 14], 0f);
        }
        all[3].setLocalTranslation(40f, 2f, -10f);
        all[3].rotate(0f, FastMath.HALF_PI, 0f);
        all[3].setLocalScale(2f);
        all[3].updateGeometricState();
        var next = draw(all).getFirst().instances();
        assertEquals(40f, next[3 * 16 + 12], 0f);
        assertEquals(2f, next[3 * 16 + 13], 0f);
        // The normal's quaternion turns the other way from the geometry's and is a unit one, whatever the scale.
        float x = next[3 * 16 + 3];
        float y = next[3 * 16 + 7];
        float z = next[3 * 16 + 11];
        float w = next[3 * 16 + 15];
        assertEquals(1f, x * x + y * y + z * z + w * w, 1e-5f);
        assertEquals(-FastMath.sin(FastMath.QUARTER_PI), y, 1e-5f);
        // The matrix is the world matrix, scale in it: the normal is made unit by the shader.
        assertEquals(2f * FastMath.cos(FastMath.HALF_PI), next[3 * 16], 1e-5f);
    }

    @Test
    void theMaterialIsLeftAsItWasFound() {
        AutoInstancing.on(manager);
        var material = material();
        draw(props(box, material, 6));
        assertEquals(Boolean.FALSE, material.getParamValue("UseInstancing"));
        manager.setForcedTechnique(null);
    }

    @Test
    void geometriesTheLightsReachDifferentlyAreDifferentCalls() {
        AutoInstancing.on(manager);
        var material = material();
        var lit = new Node("lit");
        lit.addLight(new DirectionalLight(new Vector3f(0f, -1f, 0f), ColorRGBA.Red));
        var all = props(box, material, 10);
        for (int i = 0; i < 5; i++) {
            lit.attachChild(all[i]);
        }
        lit.updateGeometricState();
        var draws = draw(all);
        assertEquals(2, draws.size());
        assertEquals(List.of(5, 5), draws.stream().map(Draw::count).toList());
    }

    @Test
    void aGeometryThePassTellsApartIsDrawnAlone() {
        var batcher = AutoInstancing.on(manager);
        var all = props(box, material(), 8);
        batcher.apart(geometry -> geometry == all[2]);
        var draws = draw(all);
        assertEquals(List.of(1, 7), draws.stream().map(Draw::count).sorted().toList());
        batcher.apart(null);
    }

    @Test
    void aShadowMapOfAMaterialWithItsOwnTechniqueIsBatchedAndOneDrawnByAForcedMaterialWithoutInstancingIsNot() {
        AutoInstancing.on(manager);
        var material = material();
        manager.setForcedTechnique("PreShadow");
        manager.setForcedMaterial(new Material(ASSETS, "Common/MatDefs/Shadow/PreShadow.j3md"));
        assertEquals(1, draw(props(box, material, 6)).size());
        manager.setForcedTechnique("NoSuchTechnique");
        assertEquals(6, draw(props(box, material, 6)).size());
        manager.setForcedTechnique(null);
        manager.setForcedMaterial(null);
    }

    @Test
    void theScenePassDrawsWhatMovedAloneAndWhatStoodStillTogether() {
        AutoInstancing.on(manager);
        var buffers = SceneBuffers.on(ASSETS, manager, new ViewPort("view", camera), 16, 16).orElseThrow();
        var all = props(box, material(), 8);
        // The far quad is the pass's own call; the rest are the props.
        assertEquals(List.of(1, 8), scene(buffers, all), "all still: one call");
        assertEquals(List.of(1, 8), scene(buffers, all));
        all[2].setLocalTranslation(5f, 0f, -10f);
        all[2].updateGeometricState();
        assertEquals(List.of(1, 1, 7), scene(buffers, all), "one moved: it is drawn with the motion of its own");
        assertEquals(List.of(1, 8), scene(buffers, all), "and when it stands still again, with the rest");
    }

    private List<Integer> scene(SceneBuffers buffers, Geometry... geometries) {
        var queue = new RenderQueue();
        for (var geometry : geometries) {
            queue.addToQueue(geometry, RenderQueue.Bucket.Opaque);
        }
        renderer.draws.clear();
        buffers.postQueue(queue);
        return renderer.draws.stream().map(Draw::count).sorted().toList();
    }

    @Test
    void twoMaterialsOfOneDefinitionAreTheSameByTheirParametersLeavingOutWhatDrawingLeaves() {
        var a = material();
        var b = material();
        assertTrue(AutoInstancing.same(a, b));
        a.setFloat("Roughness", 0.3f);
        assertFalse(AutoInstancing.same(a, b));
        b.setFloat("Roughness", 0.3f);
        assertTrue(AutoInstancing.same(a, b));
        a.setColor("BaseColor", ColorRGBA.Red);
        assertFalse(AutoInstancing.same(a, b), "a parameter that differs");
        a.setColor("BaseColor", (ColorRGBA) b.getParamValue("BaseColor"));
        a.setParam("PreviousWorldMatrix", com.jme3.shader.VarType.Matrix4, com.jme3.math.Matrix4f.IDENTITY);
        assertTrue(AutoInstancing.same(a, b), "what the scene buffers leave is not what the material is");
        a.setBoolean("UseInstancing", false);
        assertTrue(AutoInstancing.same(a, b), "nor is the switch this class leaves");
    }
}
