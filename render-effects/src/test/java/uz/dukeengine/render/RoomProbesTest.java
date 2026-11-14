package uz.dukeengine.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.light.LightProbe;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** When rooms are baked and whether they light the root: the bake itself, a GPU's, is put in by hand. */
class RoomProbesTest {

    private final Node root = new Node("root");
    private final List<LightProbe> baked = new ArrayList<>();
    private final List<Boolean> enclosedAsked = new ArrayList<>();
    private final RoomProbes rooms = new RoomProbes(root, (scene, probe, enclosed) -> {
        baked.add(probe);
        enclosedAsked.add(enclosed);
        return 2.5;
    });

    private static final Vector3f HALF = new Vector3f(2f, 1f, 2f);

    private boolean lit(LightProbe probe) {
        for (var light : root.getLocalLightList()) {
            if (light == probe) {
                return true;
            }
        }
        return false;
    }

    @Test
    void aPlacedRoomIsBakedAtTheNextUpdateAndThenLights() {
        var room = rooms.place(Vector3f.ZERO, HALF, true);
        assertTrue(baked.isEmpty(), "not on the caller's thread");
        assertFalse(lit(room.probe));
        assertEquals(2.5, rooms.update());
        assertEquals(List.of(room.probe), baked);
        assertEquals(List.of(true), enclosedAsked);
        assertTrue(lit(room.probe));
        assertEquals(0, rooms.update(), "nothing asked, nothing baked");
    }

    @Test
    void manyAsksInAFrameAreOneBake() {
        var room = rooms.place(Vector3f.ZERO, HALF, false);
        rooms.update();
        room.rebake();
        room.rebake();
        rooms.relight();
        rooms.update();
        assertEquals(2, baked.size());
        assertEquals(1, root.getLocalLightList().size(), "added once");
    }

    @Test
    void relightBakesEveryRoom() {
        var one = rooms.place(Vector3f.ZERO, HALF, false);
        var two = rooms.place(new Vector3f(10f, 0f, 0f), HALF, true);
        rooms.update();
        baked.clear();
        rooms.relight();
        assertEquals(5.0, rooms.update());
        assertEquals(List.of(one.probe, two.probe), baked);
    }

    @Test
    void aRemovedRoomLeavesTheRootAndIsNotBakedAgain() {
        var room = rooms.place(Vector3f.ZERO, HALF, false);
        rooms.update();
        room.remove();
        room.rebake();
        rooms.relight();
        assertEquals(0, rooms.update());
        assertFalse(lit(room.probe));
        assertEquals(0, rooms.size());
    }

    @Test
    void aRoomRemovedBeforeItsBakeIsNeverBaked() {
        rooms.place(Vector3f.ZERO, HALF, false).remove();
        rooms.update();
        assertTrue(baked.isEmpty());
        assertEquals(0, root.getLocalLightList().size());
    }
}
