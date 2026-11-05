package uz.dukeengine.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.light.DirectionalLight;
import com.jme3.light.LightProbe;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.texture.Image;
import com.jme3.texture.TextureCubeMap;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Which probes a view lights with, and in what order: the ones PBRLighting has blended, so that SSR takes back what it added. */
class LocalProbesTest {

    private final Camera camera = new Camera(64, 64);

    LocalProbesTest() {
        camera.setFrustumPerspective(60f, 1f, 0.1f, 1000f);
        camera.setLocation(Vector3f.ZERO);
        camera.lookAtDirection(Vector3f.UNIT_Z.negate(), Vector3f.UNIT_Y);
        camera.update();
    }

    private static LightProbe ready(LightProbe probe) {
        probe.setPrefilteredMap(new TextureCubeMap(4, 4, Image.Format.RGB16F));
        probe.setReady(true);
        return probe;
    }

    private static LightProbe room(float z, float half) {
        return ready(LocalProbes.box(new Vector3f(0f, 0f, z), new Vector3f(half, half, half)));
    }

    private static LightProbe sky() {
        var sky = ready(new LightProbe());
        sky.getArea().setRadius(100000f);
        return sky;
    }

    @Test
    void aBoxProbeIsABoxOfTheExtentsGiven() {
        var probe = LocalProbes.box(new Vector3f(1f, 2f, 3f), new Vector3f(4f, 5f, 6f));
        assertEquals(LightProbe.AreaType.OrientedBox, probe.getAreaType());
        var matrix = probe.getUniformMatrix();
        assertEquals(1f, matrix.m03);
        assertEquals(3f, matrix.m23);
        // The area is the walls and the feather beyond them.
        assertEquals(4f * (1f + ProbeBlend.FEATHER), matrix.m30, 1e-5f);
        assertEquals(5f * (1f + ProbeBlend.FEATHER), matrix.m31, 1e-5f);
        assertEquals(6f * (1f + ProbeBlend.FEATHER), matrix.m32, 1e-5f);
        assertFalse(probe.isReady());
    }

    @Test
    void aBoxWithoutVolumeIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> LocalProbes.box(Vector3f.ZERO, new Vector3f(1f, 0f, 1f)));
    }

    @Test
    void theNarrowestComesFirstWhateverTheOrderTheyWereAdded() {
        var scene = new Node("scene");
        var hall = room(-20f, 15f);
        var closet = room(-10f, 3f);
        var heaven = sky();
        scene.addLight(heaven);
        scene.addLight(hall);
        scene.addLight(closet);
        assertEquals(List.of(closet, hall, heaven), LocalProbes.seenFrom(List.of(scene), camera));
    }

    @Test
    void aProbeNotReadyOrWithoutACubeOrOffTheViewIsNotInTheSet() {
        var scene = new Node("scene");
        var unbaked = LocalProbes.box(new Vector3f(0f, 0f, -10f), new Vector3f(3f, 3f, 3f));
        var behind = room(30f, 3f);
        var off = ready(LocalProbes.box(new Vector3f(0f, 0f, -10f), new Vector3f(3f, 3f, 3f)));
        off.setEnabled(false);
        var cubeless = new LightProbe();
        cubeless.setReady(true);
        scene.addLight(new DirectionalLight());
        List.of(unbaked, behind, off, cubeless).forEach(scene::addLight);
        assertTrue(LocalProbes.seenFrom(List.of(scene), camera).isEmpty());
    }

    @Test
    void withMoreThanThreeTheNearestToTheEyeAreKeptNarrowestFirst() {
        var scene = new Node("scene");
        var near = room(-10f, 4f);
        var nearer = room(-8f, 6f);
        var middle = room(-14f, 2f);
        var far = room(-60f, 1f);
        List.of(far, middle, near, nearer).forEach(scene::addLight);
        assertEquals(List.of(middle, near, nearer), LocalProbes.seenFrom(List.of(scene), camera));
    }

    @Test
    void theRootsOfEverySceneOfTheViewAreLookedAt() {
        var first = new Node("first");
        var second = new Node("second");
        var one = room(-10f, 3f);
        var other = room(-12f, 5f);
        first.addLight(one);
        second.addLight(other);
        var seen = LocalProbes.seenFrom(List.of(first, second), camera);
        assertEquals(2, seen.size());
        assertSame(one, seen.get(0));
    }
}
