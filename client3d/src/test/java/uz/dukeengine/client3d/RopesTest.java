package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** A rope from a bone to the ground: as long as the bone is high, there while its words are, gone with them. */
class RopesTest {

    private static final Visuals.Rope ROPE = new Visuals.Rope("RopeStart", 0xC0A060);
    private static final Visuals VISUALS = Visuals.create()
            .unit("Chinook", look -> look.model("models/chinook.glb").rope(Set.of("UNLOADING"), ROPE))
            .unit("Tank", look -> look.model("models/tank.glb"));

    private static Ropes ropes() {
        return new Ropes(new DesktopAssetManager(true), new Node("root"));
    }

    @Test
    void theRopeRunsFromTheBoneToTheGroundAndIsAsLongAsTheBoneIsHigh() {
        var ropes = ropes();

        ropes.update(List.of(new Ropes.Line(1, ROPE, new Vector3f(5f, 11f, 5f))), (x, z) -> 1f);
        assertEquals(10f, ropes.lengthOf(1), 1e-4f);

        ropes.update(List.of(new Ropes.Line(1, ROPE, new Vector3f(5f, 31f, 5f))), (x, z) -> 1f);
        assertEquals(30f, ropes.lengthOf(1), 1e-4f, "higher, longer");

        ropes.update(List.of(new Ropes.Line(1, ROPE, new Vector3f(5f, 31f, 5f))), (x, z) -> 21f);
        assertEquals(10f, ropes.lengthOf(1), 1e-4f, "and it stops at the ground it hangs over");
    }

    @Test
    void itIsHauledInOnceNoLongerGiven() {
        var ropes = ropes();
        ropes.update(List.of(new Ropes.Line(1, ROPE, new Vector3f(0f, 5f, 0f))), (x, z) -> 0f);
        ropes.update(List.of(), (x, z) -> 0f);
        assertEquals(-1f, ropes.lengthOf(1));
    }

    @Test
    void aLookHasItOnlyWhileItsWordsAreHeld() {
        assertNull(VISUALS.of("Chinook").ropeFor(Set.of()));
        assertEquals(ROPE, VISUALS.of("Chinook").ropeFor(Set.of("UNLOADING")));
        assertNull(VISUALS.of("Tank").ropeFor(Set.of("UNLOADING")));
        assertTrue(VISUALS.of("Tank").wordRopes.isEmpty());
    }
}
