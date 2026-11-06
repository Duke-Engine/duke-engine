package uz.dukeengine.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.GeometryList;
import com.jme3.renderer.queue.OpaqueComparator;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Sphere;
import com.jme3.util.BufferUtils;
import org.junit.jupiter.api.Test;

/** Which level a size on the screen asks for, and which meshes get levels at all. */
class AutoLodTest {

    private final AutoLod lod = new AutoLod(Runnable::run);
    private final Camera camera = new Camera(1920, 1080);

    AutoLodTest() {
        camera.setFrustumPerspective(45f, 16f / 9f, 0.5f, 1000f);
        camera.setLocation(Vector3f.ZERO);
        camera.lookAt(new Vector3f(0f, 0f, -1f), Vector3f.UNIT_Y);
    }

    private static Geometry at(Mesh mesh, float distance) {
        var geometry = new Geometry("prop", mesh);
        geometry.setLocalTranslation(0f, 0f, -distance);
        geometry.updateGeometricState();
        return geometry;
    }

    /** Two frames: the first asks for the levels (made at once here), the second draws them. */
    private void frames(Geometry... geometries) {
        var list = new GeometryList(new OpaqueComparator());
        for (var geometry : geometries) {
            list.add(geometry);
        }
        lod.pick(list, camera);
        lod.collect();
        lod.pick(list, camera);
    }

    @Test
    void smallerOnTheScreenIsCoarser() {
        assertEquals(0, AutoLod.level(0.5f, 0, 4));
        assertEquals(1, AutoLod.level(0.05f, 0, 4));
        assertEquals(3, AutoLod.level(0.001f, 0, 4));
        assertEquals(1, AutoLod.level(0.001f, 0, 2), "no coarser than the mesh has");
    }

    @Test
    void aSizeAtTheStepDoesNotFlicker() {
        // Just under the first step: not yet coarser, and once coarser, not finer again until it is past the step.
        assertEquals(0, AutoLod.level(0.095f, 0, 4));
        assertEquals(1, AutoLod.level(0.08f, 0, 4));
        assertEquals(1, AutoLod.level(0.095f, 1, 4));
        assertEquals(0, AutoLod.level(0.11f, 1, 4));
    }

    @Test
    void screenSizeHalvesWithTwiceTheDistance() {
        float near = AutoLod.screenSize(at(new Sphere(8, 8, 1f), 10f).getWorldBound(), camera);
        float far = AutoLod.screenSize(at(new Sphere(8, 8, 1f), 20f).getWorldBound(), camera);
        assertEquals(near / 2f, far, near * 0.01f);
    }

    @Test
    void clonesShareTheLevelsOfTheirMeshAndAFarOneIsCoarser() {
        var sphere = new Sphere(24, 24, 1f);
        var near = at(sphere, 3f);
        var far = at(sphere, 400f);
        frames(near, far);
        assertTrue(sphere.getNumLodLevels() > 1, "levels made");
        assertSame(sphere.getBuffer(VertexBuffer.Type.Index), sphere.getLodLevel(0), "level 0 is the mesh's own");
        assertTrue(sphere.getLodLevel(1).getNumElements() < sphere.getLodLevel(0).getNumElements());
        assertEquals(0, near.getLodLevel());
        assertEquals(sphere.getNumLodLevels() - 1, far.getLodLevel());
    }

    @Test
    void skinnedSmallAndTheGamesOwnAreLeftAlone() {
        var skinned = new Sphere(24, 24, 1f);
        skinned.setBuffer(VertexBuffer.Type.BoneIndex, 4, BufferUtils.createByteBuffer(skinned.getVertexCount() * 4));
        var small = new Box(1f, 1f, 1f);
        var own = new Sphere(24, 24, 1f);
        var index = own.getBuffer(VertexBuffer.Type.Index);
        own.setLodLevels(new VertexBuffer[] {index, index});
        var game = at(own, 400f);
        frames(at(skinned, 400f), at(small, 400f), game);
        assertEquals(0, skinned.getNumLodLevels());
        assertEquals(0, small.getNumLodLevels());
        assertEquals(2, own.getNumLodLevels());
        assertEquals(0, game.getLodLevel(), "the game picks its own levels");
    }
}
