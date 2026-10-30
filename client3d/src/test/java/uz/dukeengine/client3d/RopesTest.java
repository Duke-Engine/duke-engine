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

/**
 * A rope from a bone to the ground: as long as the bone is high, there while its words are, gone with them; let
 * down over some frames and bowing as it hangs, where it is said to.
 */
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

    @Test
    void aLookSaidToHaveSeveralRopesLetsThemAllDownEachFromItsOwnBone() {
        var one = new Visuals.Rope("RopeStart01", 0xC0A060);
        var two = new Visuals.Rope("RopeStart02", 0xC0A060);
        var flying = new Visuals.Rope("Tail", 0xFFFFFF);
        var visuals = Visuals.create().unit("Chinook", look -> look.model("models/chinook.glb")
                .rope(Set.of("UNLOADING"), one).rope(Set.of("UNLOADING"), two).rope(flying));
        var chinook = visuals.of("Chinook");

        assertEquals(List.of(one, two), chinook.ropesFor(Set.of("UNLOADING")), "both, in the order said");
        assertEquals(List.of(flying), chinook.ropesFor(Set.of()), "and those of other words not with them");

        var ropes = ropes();
        ropes.update(List.of(new Ropes.Line(1, 0, one, new Vector3f(4f, 11f, 5f)),
                new Ropes.Line(1, 1, two, new Vector3f(6f, 21f, 5f))), (x, z) -> 1f);
        assertEquals(10f, ropes.lengthOf(1, 0), 1e-4f);
        assertEquals(20f, ropes.lengthOf(1, 1), 1e-4f, "each its own line, from its own bone");

        ropes.update(List.of(new Ropes.Line(1, 0, one, new Vector3f(4f, 11f, 5f))), (x, z) -> 1f);
        assertEquals(-1f, ropes.lengthOf(1, 1), "one no longer given is hauled in alone");
        assertEquals(10f, ropes.lengthOf(1, 0), 1e-4f);
    }

    private static final Vector3f BONE = new Vector3f(5f, 101f, 5f);

    @Test
    void aRopeSaidToBeLoweredLengthensFromOneFasterEachFrameAndStopsShortOfTheGround() {
        var ropes = ropes();
        var rope = ROPE.lowered(new Visuals.Lowered(1f, 1e10f, 10f));
        var line = List.of(new Ropes.Line(1, rope, BONE));

        ropes.update(line, (x, z) -> 1f, 100);
        assertEquals(1f, ropes.lengthOf(1), 1e-4f, "1 long when first given");
        ropes.update(line, (x, z) -> 1f, 103);
        assertEquals(1f + 1 + 2 + 3, ropes.lengthOf(1), 1e-4f, "a frame's speed up each frame");
        ropes.update(line, (x, z) -> 1f, 200);
        assertEquals(90f, ropes.lengthOf(1), 1e-4f, "the ground's 100 less 10 and no more");

        var capped = ropes();
        var slow = List.of(new Ropes.Line(1, ROPE.lowered(new Visuals.Lowered(1f, 2f, 0f)), BONE));
        capped.update(slow, (x, z) -> 1f, 0);
        capped.update(slow, (x, z) -> 1f, 3);
        assertEquals(1f + 1 + 2 + 2, capped.lengthOf(1), 1e-4f, "and no faster than the top speed");
    }

    @Test
    void aRopeSaidToWobbleBowsEachPieceAlongAnAxisOfItsOwnBySineOfAPhaseThatMovesEachFrame() {
        var ropes = ropes();
        var rope = ROPE.wobbling(new Visuals.Wobble(10f, 2f, (float) (Math.PI / 2)));
        var line = List.of(new Ropes.Line(1, rope, BONE));

        ropes.update(line, (x, z) -> 1f, 0);
        var still = ropes.pointsOf(1, 0);
        assertEquals(11, still.size(), "100 long in pieces of 10");
        assertTrue(still.stream().allMatch(at -> at.x == 5f && at.z == 5f), "phase 0 is no bow");

        ropes.update(line, (x, z) -> 1f, 1);
        var bowed = ropes.pointsOf(1, 0);
        for (int i = 1; i < bowed.size(); i++) {
            assertEquals(2f, Math.hypot(bowed.get(i).x - 5f, bowed.get(i).z - 5f), 1e-4, "piece " + i + " a quarter turn on");
            assertEquals(101f - 10f * i, bowed.get(i).y, 1e-3f);
        }
        assertTrue(bowed.get(1).x != bowed.get(2).x || bowed.get(1).z != bowed.get(2).z, "not all the one way");

        ropes.update(line, (x, z) -> 1f, 5);
        assertEquals(bowed, ropes.pointsOf(1, 0), "a whole turn on, the same bow");
        assertEquals(100f, ropes.lengthOf(1), 1e-3f);
    }

    @Test
    void aRopeNotSaidToLowerOrWobbleIsOneStraightLineWhateverTheFrame() {
        var ropes = ropes();
        ropes.update(List.of(new Ropes.Line(1, ROPE, BONE)), (x, z) -> 1f, 7);
        assertEquals(List.of(BONE, new Vector3f(5f, 1f, 5f)), ropes.pointsOf(1, 0));
    }
}
