package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import org.junit.jupiter.api.Test;

/** A line through the scene's standing scenery: walls stop it, an arch lets it by, small things and put-away cells do not. */
class SceneRayTest {

    private static final Vector3f ALONG_X = new Vector3f(1f, 0f, 0f);

    /** A block {@code wide} across x, {@code tall} high and {@code deep} across z, its foot at the ground and its x from {@code x}. */
    private static Geometry block(Node into, float x, float wide, float tall, float z, float deep) {
        var geometry = new Geometry("block", new Box(wide / 2f, tall / 2f, deep / 2f));
        geometry.setLocalTranslation(x + wide / 2f, tall / 2f, z);
        into.attachChild(geometry);
        return geometry;
    }

    private static float run(Node scene, float height) {
        scene.updateGeometricState();
        return SceneRay.run(scene, new Vector3f(0f, height, 0f), ALONG_X, 30f, 12f);
    }

    @Test
    void aWallStopsTheLineAtItsFace() {
        var scene = new Node();
        block(scene, 20f, 4f, 80f, 0f, 40f);
        assertEquals(20f, run(scene, 10f), 1e-3f);
        assertEquals(30f, SceneRay.run(scene, new Vector3f(0f, 10f, 0f), new Vector3f(-1f, 0f, 0f), 30f, 12f),
                "pointing away from it the line is clear");
        assertEquals(15f, SceneRay.run(scene, new Vector3f(0f, 10f, 0f), ALONG_X, 15f, 12f), "short of it, all of it");
    }

    @Test
    void anArchedGateIsOpenUnderItsArchAndAWallOverIt() {
        var gate = new Node();
        block(gate, 20f, 4f, 20f, -10f, 10f); // the pier on one side of the passage
        block(gate, 20f, 4f, 20f, 10f, 10f); // and on the other
        block(gate, 20f, 4f, 40f, 0f, 10f).move(0f, 20f, 0f); // the lintel over the passage, from 20 high up
        assertEquals(30f, run(gate, 8f), "through the passage at the height of his head");
        assertEquals(20f, run(gate, 40f), "over it the lintel is a wall");
    }

    @Test
    void aBarrelAndAStoneAreSeenOver() {
        var scene = new Node();
        block(scene, 10f, 3f, 6f, 0f, 3f);
        assertEquals(30f, run(scene, 3f), "lower than the data's height: not in the way");
        block(scene, 20f, 3f, 14f, 0f, 3f);
        assertEquals(20f, run(scene, 3f), "high enough: in the way");
    }

    @Test
    void aCellPutAwayStopsNothing() {
        var scene = new Node();
        var cell = new Node();
        scene.attachChild(cell);
        block(cell, 20f, 4f, 80f, 0f, 40f);
        cell.setCullHint(Spatial.CullHint.Always);
        assertEquals(30f, run(scene, 10f), "what the fog has hidden is not a wall to the camera");
    }
}
