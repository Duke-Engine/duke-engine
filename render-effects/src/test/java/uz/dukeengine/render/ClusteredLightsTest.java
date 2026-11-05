package uz.dukeengine.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.light.DirectionalLight;
import com.jme3.light.Light;
import com.jme3.light.LightList;
import com.jme3.light.PointLight;
import com.jme3.light.SpotLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import com.jme3.system.NullRenderer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Which cluster a depth falls in, which clusters a light reaches, and the buffers the shader reads them from. */
class ClusteredLightsTest {

    private static final float NEAR = 0.1f;
    private static final float FAR = 200f;
    private static final float LOG_RANGE = (float) Math.log(FAR / NEAR);

    private static Camera camera() {
        var camera = new Camera(1600, 900);
        camera.setFrustumPerspective(60f, 1600f / 900f, NEAR, FAR);
        camera.setLocation(new Vector3f(1f, 2f, 3f));
        camera.lookAt(new Vector3f(5f, 1f, -20f), Vector3f.UNIT_Y);
        camera.update();
        return camera;
    }

    @Test
    void depthFallsInTheSliceThatStartsBelowIt() {
        assertEquals(0, ClusteredLights.slice(NEAR, NEAR, LOG_RANGE));
        assertEquals(0, ClusteredLights.slice(NEAR * 0.5f, NEAR, LOG_RANGE));
        assertEquals(ClusteredLights.SLICES - 1, ClusteredLights.slice(FAR, NEAR, LOG_RANGE));
        assertEquals(ClusteredLights.SLICES - 1, ClusteredLights.slice(FAR * 3f, NEAR, LOG_RANGE));
        for (int k = 0; k < ClusteredLights.SLICES; k++) {
            float start = ClusteredLights.sliceDepth(k, NEAR, LOG_RANGE);
            float end = ClusteredLights.sliceDepth(k + 1, NEAR, LOG_RANGE);
            assertEquals(k, ClusteredLights.slice(start * 1.001f, NEAR, LOG_RANGE));
            assertEquals(k, ClusteredLights.slice(end * 0.999f, NEAR, LOG_RANGE));
            // Evenly in the logarithm: each slice as many times deeper than the last.
            assertEquals(Math.exp(LOG_RANGE / ClusteredLights.SLICES), end / start, 1e-3);
        }
        assertEquals(FAR, ClusteredLights.sliceDepth(ClusteredLights.SLICES, NEAR, LOG_RANGE), 1e-2f);
    }

    @Test
    void sphereTouchesTheBoxItReachesAndNotOneFurther() {
        assertTrue(ClusteredLights.sphereTouches(0.5f, 0.5f, 0.5f, 0.1f, 0f, 0f, 0f, 1f, 1f, 1f));
        assertTrue(ClusteredLights.sphereTouches(1.5f, 0.5f, 0.5f, 0.51f, 0f, 0f, 0f, 1f, 1f, 1f));
        assertFalse(ClusteredLights.sphereTouches(1.5f, 0.5f, 0.5f, 0.49f, 0f, 0f, 0f, 1f, 1f, 1f));
        // Off the corner, the distance is the diagonal's, not the nearest face's.
        assertFalse(ClusteredLights.sphereTouches(2f, 2f, 2f, 1.6f, 0f, 0f, 0f, 1f, 1f, 1f));
        assertTrue(ClusteredLights.sphereTouches(2f, 2f, 2f, 1.75f, 0f, 0f, 0f, 1f, 1f, 1f));
    }

    @Test
    void coneTouchesWhatIsInItAndNotWhatIsBehindAsideOrPastIt() {
        var apex = new Vector3f(0f, 0f, 0f);
        var axis = new Vector3f(0f, 0f, 1f);
        float angle = 30f * FastMath.DEG_TO_RAD;
        float cos = FastMath.cos(angle);
        float sin = FastMath.sin(angle);
        // Ahead on the axis, within its length.
        assertTrue(ClusteredLights.coneTouches(apex, axis, cos, sin, 10f, -0.5f, -0.5f, 4f, 0.5f, 0.5f, 5f));
        // Behind the apex.
        assertFalse(ClusteredLights.coneTouches(apex, axis, cos, sin, 10f, -0.5f, -0.5f, -5f, 0.5f, 0.5f, -4f));
        // Past its length.
        assertFalse(ClusteredLights.coneTouches(apex, axis, cos, sin, 10f, -0.5f, -0.5f, 12f, 0.5f, 0.5f, 13f));
        // Well aside: 60 degrees off the axis at 5 m.
        assertFalse(ClusteredLights.coneTouches(apex, axis, cos, sin, 10f, 3.8f, -0.5f, 2.4f, 4.8f, 0.5f, 3.4f));
        // Across its side: half the box inside the cone.
        float side = 5f * FastMath.tan(angle);
        assertTrue(ClusteredLights.coneTouches(apex, axis, cos, sin, 10f, side - 0.5f, -0.5f, 4.5f, side + 0.5f, 0.5f, 5.5f));
        // Round the apex itself.
        assertTrue(ClusteredLights.coneTouches(apex, axis, cos, sin, 10f, -0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f));
    }

    /** No pixel misses a light that reaches it: every point a light reaches is in a cluster that lists the light. */
    @Test
    void everyPointALightReachesIsInAClusterThatListsIt() {
        var camera = camera();
        var random = new Random(3);
        var lights = new ArrayList<Light>();
        for (int i = 0; i < 300; i++) {
            var at = camera.getWorldCoordinates(new Vector2f(random.nextFloat() * 1800f - 100f, random.nextFloat() * 1100f - 100f),
                    camera.getViewToProjectionZ(0.2f + random.nextFloat() * 60f));
            // Each light's red says which it is, to be read back from the buffer.
            var colour = new ColorRGBA(i, 1f, 1f, 1f);
            if (i % 3 == 0) {
                var spot = new SpotLight(at, new Vector3f(random.nextFloat() - 0.5f, random.nextFloat() - 0.5f,
                        random.nextFloat() - 0.5f).normalizeLocal(), colour);
                spot.setSpotRange(0.5f + random.nextFloat() * 8f);
                spot.setSpotOuterAngle((5f + random.nextFloat() * 80f) * FastMath.DEG_TO_RAD);
                spot.setSpotInnerAngle(spot.getSpotOuterAngle() * 0.5f);
                lights.add(spot);
            } else {
                lights.add(new PointLight(at, colour, 0.3f + random.nextFloat() * 6f));
            }
        }
        var clusters = new ClusteredLights();
        clusters.sort(camera, lights);
        lights.forEach(light -> assertTrue(clusters.holds(light)));

        int checked = 0;
        for (int sample = 0; sample < 200_000; sample++) {
            float x = random.nextFloat() * 1600f;
            float y = random.nextFloat() * 900f;
            float depth = NEAR * (float) Math.exp(random.nextFloat() * Math.log(80f / NEAR));
            var point = camera.getWorldCoordinates(new Vector2f(x, y), camera.getViewToProjectionZ(depth));
            int cluster = (int) (x / 100f) + ClusteredLights.TILES_X * ((int) (y / 100f)
                    + ClusteredLights.TILES_Y * ClusteredLights.slice(depth, NEAR, LOG_RANGE));
            for (var light : lights) {
                if (!reaches(light, point)) {
                    continue;
                }
                checked++;
                int id = (int) light.getColor().r;
                assertTrue(lists(clusters, cluster, id), "light " + id + " missing from cluster " + cluster);
            }
        }
        assertTrue(checked > 1000, "too few samples in a light: " + checked);
    }

    private static boolean reaches(Light light, Vector3f point) {
        if (light instanceof SpotLight spot) {
            var to = point.subtract(spot.getPosition());
            float length = to.length();
            return length < spot.getSpotRange() * 0.999f
                    && to.normalize().dot(spot.getDirection()) > FastMath.cos(spot.getSpotOuterAngle()) + 1e-4f;
        }
        var point0 = (PointLight) light;
        return point.distance(point0.getPosition()) < point0.getRadius() * 0.999f;
    }

    private static boolean lists(ClusteredLights clusters, int cluster, int id) {
        int[] range = clusters.cluster(cluster);
        for (int n = range[0]; n < range[0] + range[1]; n++) {
            if ((int) clusters.light(clusters.index(n) * 12) == id) {
                return true;
            }
        }
        return false;
    }

    @Test
    void lightsOutOfViewAreHeldWithoutBeingSortedAndOthersAreLeftToTheirOwnPasses() {
        var camera = camera();
        var behind = new PointLight(camera.getLocation().subtract(camera.getDirection().mult(20f)), ColorRGBA.White, 3f);
        var ahead = new PointLight(camera.getLocation().add(camera.getDirection().mult(10f)), ColorRGBA.White, 3f);
        var everywhere = new PointLight(ahead.getPosition(), ColorRGBA.White, 0f);
        var off = new PointLight(ahead.getPosition(), ColorRGBA.White, 3f);
        off.setEnabled(false);
        var sun = new DirectionalLight(Vector3f.UNIT_Y.negate());
        var clusters = new ClusteredLights();
        clusters.sort(camera, List.of(behind, ahead, everywhere, off, sun));
        assertEquals(1, clusters.sorted());
        assertTrue(clusters.holds(behind));
        assertTrue(clusters.holds(ahead));
        assertFalse(clusters.holds(everywhere));
        assertFalse(clusters.holds(off));
        assertFalse(clusters.holds(sun));
    }

    @Test
    void lightsArePackedAsTheForkWritesTheirUniformsAndListedInTheGridInOrder() {
        var camera = camera();
        var ahead = camera.getLocation().add(camera.getDirection().mult(10f));
        var point = new PointLight(ahead, new ColorRGBA(1f, 0.5f, 0.25f, 1f), 4f);
        var spot = new SpotLight(ahead.add(0f, 0.5f, 0f), Vector3f.UNIT_Y.negate(), new ColorRGBA(0.1f, 0.2f, 0.3f, 1f));
        spot.setSpotRange(5f);
        var clusters = new ClusteredLights();
        clusters.sort(camera, List.of(point, spot));
        float[] expected = {1f, 0.5f, 0.25f, 1f, ahead.x, ahead.y, ahead.z, 0.25f, 0f, 0f, 0f, 0f,
            0.1f, 0.2f, 0.3f, 2f, ahead.x, ahead.y + 0.5f, ahead.z, 0.2f, 0f, -1f, 0f, spot.getPackedAngleCos()};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], clusters.light(i), 1e-6f, "float " + i);
        }
        // The middle of the screen at 10 m: both lights, in the order they came.
        int cluster = 8 + ClusteredLights.TILES_X * (4 + ClusteredLights.TILES_Y * ClusteredLights.slice(10f, NEAR, LOG_RANGE));
        int[] range = clusters.cluster(cluster);
        assertEquals(2, range[1]);
        assertEquals(0, clusters.index(range[0]));
        assertEquals(1, clusters.index(range[0] + 1));
        // Each cluster's list starts where the one before it ends.
        int next = 0;
        for (int c = 0; c < ClusteredLights.CLUSTERS; c++) {
            assertEquals(next, clusters.cluster(c)[0]);
            next += clusters.cluster(c)[1];
        }
        // A corner, far off: none.
        assertEquals(0, clusters.cluster(ClusteredLights.CLUSTERS - 1)[1]);
    }

    @Test
    void whatTheClustersLightGetsOnlyTheLightsTheyDoNotHoldAndEverythingElseAll() {
        var assets = new DesktopAssetManager(true);
        var manager = new RenderManager(new NullRenderer());
        var camera = camera();
        var ahead = camera.getLocation().add(camera.getDirection().mult(10f));
        var root = new Node("root");
        var part = new Node("part");
        root.attachChild(part);
        var pbr = new Geometry("pbr", new Box(1f, 1f, 1f));
        pbr.setMaterial(new Material(assets, "Common/MatDefs/Light/PBRLighting.j3md"));
        pbr.setLocalTranslation(ahead);
        var plain = pbr.clone(false);
        plain.setMaterial(new Material(assets, "Common/MatDefs/Light/Lighting.j3md"));
        part.attachChild(pbr);
        part.attachChild(plain);
        var sun = new DirectionalLight(Vector3f.UNIT_Y.negate());
        var lamp = new PointLight(ahead, ColorRGBA.White, 5f);
        var everywhere = new PointLight(ahead, ColorRGBA.White, 0f);
        var own = new PointLight(ahead, ColorRGBA.White, 5f);
        root.addLight(sun);
        root.addLight(lamp);
        root.addLight(everywhere);
        part.addLight(own);
        root.updateGeometricState();
        var view = new ViewPort("view", camera);
        view.attachScene(root);
        var fork = manager.getLightFilter();

        var clusters = new ClusteredLights();
        clusters.initialize(manager, view);
        clusters.preFrame(0f);
        clusters.postQueue(new RenderQueue());
        manager.setCamera(camera, false);
        assertTrue(clusters.holds(lamp));
        var lit = new LightList(null);
        manager.getLightFilter().filterLights(pbr, lit);
        assertEquals(Set.of(own, sun, everywhere), lights(lit));
        var forks = new LightList(null);
        manager.getLightFilter().filterLights(plain, forks);
        assertEquals(Set.of(own, sun, everywhere, lamp), lights(forks));
        // Another camera (a shadow map's, a reflection's) has the fork's filter for all.
        manager.setCamera(new Camera(16, 16), false);
        var elsewhere = new LightList(null);
        manager.getLightFilter().filterLights(pbr, elsewhere);
        assertTrue(lights(elsewhere).contains(sun));

        clusters.cleanup();
        assertSame(fork, manager.getLightFilter());
    }

    private static Set<Light> lights(LightList list) {
        var set = new HashSet<Light>();
        list.forEach(set::add);
        return set;
    }

    @Test
    void theShaderLibraryCountsTheClustersAsTheSortDoes() throws IOException {
        String library;
        try (InputStream in = ClusteredLights.class.getResourceAsStream("/Common/ShaderLib/LightClusters.glsllib")) {
            library = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(defines(library, "CLUSTERS_X", ClusteredLights.TILES_X));
        assertTrue(defines(library, "CLUSTERS_Y", ClusteredLights.TILES_Y));
        assertTrue(defines(library, "CLUSTERS_Z", ClusteredLights.SLICES));
        assertTrue(defines(library, "CLUSTER_ROW", ClusteredLights.ROW));
    }

    private static boolean defines(String library, String name, int value) {
        return Pattern.compile("(?m)^#define " + name + " " + value + "\\s*$").matcher(library).find();
    }
}
