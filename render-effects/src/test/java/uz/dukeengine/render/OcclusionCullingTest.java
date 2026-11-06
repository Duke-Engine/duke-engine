package uz.dukeengine.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.GeometryList;
import com.jme3.renderer.queue.OpaqueComparator;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Box;
import com.jme3.system.NullRenderer;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Which geometries a hidden cell leaves out, and which are drawn whatever the queries said. */
class OcclusionCullingTest {

    private static final DesktopAssetManager ASSETS = new DesktopAssetManager(true);

    /** Queries every one of which answers at once: hidden, unless its id is in {@code seen}. */
    private static final class Answering extends NullRenderer {
        final Set<Integer> seen = new HashSet<>();
        int next = 1;
        int asked;

        @Override
        public int[] generateProfilingTasks(int n) {
            var ids = new int[n];
            for (int i = 0; i < n; i++) {
                ids[i] = next++;
            }
            return ids;
        }

        @Override
        public void startOcclusionQuery(int id) {
            asked++;
        }

        @Override
        public void stopOcclusionQuery() {
        }

        @Override
        public boolean isTaskResultAvailable(int id) {
            return true;
        }

        @Override
        public boolean occlusionQueryPassed(int id) {
            return seen.contains(id);
        }
    }

    private final Answering renderer = new Answering();
    private final RenderManager manager = new RenderManager(renderer);
    private final Camera camera = new Camera(64, 64);
    private final OcclusionCulling occlusion = new OcclusionCulling(ASSETS);

    OcclusionCullingTest() {
        camera.setFrustumPerspective(60f, 1f, 0.2f, 1000f);
        camera.setLocation(Vector3f.ZERO);
        camera.lookAt(new Vector3f(0f, 0f, -1f), Vector3f.UNIT_Y);
        occlusion.initialize(manager, new ViewPort("view", camera));
        manager.setCamera(camera, false);
    }

    private static Geometry at(float size, float x, float z) {
        var geometry = new Geometry("prop", new Box(size, size, size));
        geometry.setLocalTranslation(x, 0f, z);
        geometry.updateGeometricState();
        return geometry;
    }

    /** One frame: the queue as {@code geometries}, sorted; then the questions. Returns what is left to draw. */
    private GeometryList frame(Geometry... geometries) {
        var list = new GeometryList(new OpaqueComparator());
        for (var geometry : geometries) {
            list.add(geometry);
        }
        occlusion.sort(list, camera, renderer);
        occlusion.ask(manager, camera);
        return list;
    }

    @Test
    void aHiddenCellIsLeftOutTheFrameAfterItsQuestion() {
        var far = at(0.5f, 0f, -60f);
        var ground = at(40f, 0f, -60f);
        assertEquals(2, frame(far, ground).size(), "nothing is known yet");
        assertEquals(1, renderer.asked, "the ground is larger than a cell, never asked about");
        var left = frame(far, ground);
        assertEquals(1, left.size());
        assertEquals(ground, left.get(0));
        assertEquals(1, occlusion.culled());
    }

    @Test
    void aCellSeenAgainIsDrawn() {
        var far = at(0.5f, 0f, -60f);
        frame(far);
        assertEquals(0, frame(far).size());
        // Every id from now on answers that it was seen.
        for (int id = 0; id < 1000; id++) {
            renderer.seen.add(id);
        }
        assertEquals(1, frame(far).size(), "the hidden cell's question of the last frame now says it shows");
    }

    @Test
    void aCellOutOfTheViewForAFrameForgetsItWasHidden() {
        var far = at(0.5f, 0f, -60f);
        frame(far);
        assertEquals(0, frame(far).size());
        frame();
        assertEquals(1, frame(far).size());
    }

    @Test
    void aShowingCellIsAskedOnlyOnItsTurn() {
        for (int id = 0; id < 1000; id++) {
            renderer.seen.add(id);
        }
        var far = at(0.5f, 0f, -60f);
        for (int i = 0; i < OcclusionCulling.SHOWING_EVERY * 3; i++) {
            assertEquals(1, frame(far).size());
        }
        assertEquals(4, renderer.asked, "once when new, then once every few frames");
    }

    @Test
    void theCellTheCameraStandsInIsNeverAsked() {
        var here = at(0.5f, 0.5f, -0.5f);
        frame(here);
        assertEquals(0, renderer.asked);
        assertEquals(1, frame(here).size());
    }

    @Test
    void geometriesOfOneCellShareItsQuestion() {
        frame(at(0.5f, 1f, -61f), at(0.5f, 2f, -62f), at(0.5f, 1f, -90f));
        assertEquals(2, renderer.asked);
        assertTrue(OcclusionCulling.key(new Vector3f(1f, 0f, -61f)) == OcclusionCulling.key(new Vector3f(2f, 0f, -62f)));
    }
}
