package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.duke.core.pathfind.PathGrid;
import uz.duke.game.view.UnitView;

/**
 * Discovery: the map is black until somebody walks it, and stays open afterwards.
 *
 * <p>Held still here rather than through the client because this is where the
 * three states are decided — the renderer only paints what this says. Nothing in
 * it touches jME, a window or the simulation, which is the point: fog is a fact
 * about the viewer, not about the world.
 */
class DiscoveryTest {

    private static final int LOCAL = 0;
    private static final int ENEMY = 1;
    private static final PathGrid GRID = new PathGrid(40, 30); // 400 x 300 world units

    /** One unit of {@code player} standing at a point, which is all discovery reads. */
    private static UnitView at(int player, float x, float y) {
        return new UnitView(1, "Hero", player, x, y, 0f, 10f, 10f, false, true, false, false, -1);
    }

    private static Discovery discovery() {
        return new Discovery(GRID);
    }

    /** Before anyone has moved, there is nothing to see. */
    @Test
    void aFloorNobodyHasWalkedIsEntirelyBlack() {
        var seen = discovery();

        assertEquals(0, seen.exploredCells(), "nothing should be open yet");
        assertEquals(Discovery.State.UNSEEN, seen.stateAt(20, 15));
        assertEquals(Discovery.State.UNSEEN, seen.stateAt(0, 0));
    }

    /** Standing somewhere opens what is around it — and only what is around it. */
    @Test
    void heOpensTheGroundWithinHisSight() {
        var seen = discovery();

        seen.reveal(List.of(at(LOCAL, 205f, 155f)), LOCAL, 30f);

        assertEquals(Discovery.State.VISIBLE, seen.stateAt(20, 15), "the cell he stands in");
        assertEquals(Discovery.State.UNSEEN, seen.stateAt(30, 15), "a hundred units away");
        assertTrue(seen.exploredCells() > 0 && seen.exploredCells() < 40 * 30,
                "some of the floor, not all of it: " + seen.exploredCells());
    }

    /** Walking on leaves the way back drawn behind him. */
    @Test
    void groundHeHasLeftStaysOnTheMap() {
        var seen = discovery();
        seen.reveal(List.of(at(LOCAL, 55f, 155f)), LOCAL, 30f);
        int openedBehind = seen.exploredCells();

        seen.reveal(List.of(at(LOCAL, 355f, 155f)), LOCAL, 30f);

        assertEquals(Discovery.State.REMEMBERED, seen.stateAt(5, 15),
                "where he came from should still be drawn");
        assertEquals(Discovery.State.VISIBLE, seen.stateAt(35, 15), "and where he is now, lit");
        assertTrue(seen.exploredCells() > openedBehind,
                "the map only ever opens further: " + seen.exploredCells());
    }

    /**
     * Remembered is a state of its own, not merely "not black". The distinction is
     * the whole feature: the renderer draws walls there and the engine's fog keeps
     * monsters out of it, so collapsing the two would either blind the player or
     * show him what is prowling around a room he left.
     */
    @Test
    void rememberedIsNeitherBlackNorLit() {
        var seen = discovery();
        seen.reveal(List.of(at(LOCAL, 55f, 155f)), LOCAL, 30f);
        seen.reveal(List.of(at(LOCAL, 355f, 155f)), LOCAL, 30f);

        assertEquals(0, seen.visibleCells() - countVisible(seen),
                "visible is recomputed from scratch each time, never accumulated");
        assertTrue(seen.exploredCells() > seen.visibleCells(),
                "more has been walked than can be seen at once");
    }

    private static int countVisible(Discovery seen) {
        int lit = 0;
        for (int cy = 0; cy < GRID.getHeight(); cy++) {
            for (int cx = 0; cx < GRID.getWidth(); cx++) {
                if (seen.stateAt(cx, cy) == Discovery.State.VISIBLE) {
                    lit++;
                }
            }
        }
        return lit;
    }

    /** The dungeon is discovered by the player, not by the things hunting him. */
    @Test
    void aMonsterWanderingAboutOpensNothing() {
        var seen = discovery();

        seen.reveal(List.of(at(ENEMY, 205f, 155f)), LOCAL, 30f);

        assertEquals(0, seen.exploredCells(), "an enemy's eyes are not the player's");
        assertEquals(Discovery.State.UNSEEN, seen.stateAt(20, 15));
    }

    /**
     * The radius is a setting, and setting it further opens more. Written as a
     * comparison rather than a cell count so it says "further sight, more map"
     * instead of pinning the arithmetic of a circle.
     */
    @Test
    void seeingFurtherOpensMore() {
        var near = discovery();
        var far = discovery();

        near.reveal(List.of(at(LOCAL, 205f, 155f)), LOCAL, 30f);
        far.reveal(List.of(at(LOCAL, 205f, 155f)), LOCAL, 90f);

        assertTrue(far.exploredCells() > near.exploredCells(),
                near.exploredCells() + " cells at 30, " + far.exploredCells() + " at 90");
    }

    /** A game that never asked for discovery is not left staring at a black map. */
    @Test
    void withoutARadiusNothingIsClaimedToBeSeen() {
        var seen = discovery();

        seen.reveal(List.of(at(LOCAL, 205f, 155f)), LOCAL, 0f);

        assertEquals(0, seen.exploredCells());
    }

    /**
     * A new floor starts black. The hero keeps his levels between depths; he does
     * not keep the map, and a memory carried over would open rooms of the new
     * dungeon that nobody has been in.
     */
    @Test
    void aNewFloorIsBlackAgain() {
        var seen = discovery();
        seen.reveal(List.of(at(LOCAL, 205f, 155f)), LOCAL, 60f);
        assertTrue(seen.exploredCells() > 0);

        seen.reset(new PathGrid(40, 30));

        assertEquals(0, seen.exploredCells(), "the new floor has not been walked");
        assertEquals(Discovery.State.UNSEEN, seen.stateAt(20, 15));
    }

    /** A different-sized floor is measured by its own grid, not the last one's. */
    @Test
    void resettingTakesTheShapeOfTheNewFloor() {
        var seen = discovery();

        seen.reset(new PathGrid(12, 8));
        seen.reveal(List.of(at(LOCAL, 55f, 35f)), LOCAL, 1000f);

        assertEquals(12 * 8, seen.exploredCells(), "a sight that wide opens the whole small floor");
        assertEquals(Discovery.State.UNSEEN, seen.stateAt(20, 15), "off the map is off the map");
    }

    // ---- softening ----

    /** Run the fog on until it has stopped moving. */
    private static void settle(Discovery seen) {
        for (int frame = 0; frame < 120; frame++) {
            seen.soften(1f / 30f);
        }
    }

    /**
     * The whole point, and the thing that could quietly go wrong: softening
     * decides how bright a cell is drawn and never what the player may see.
     *
     * <p>A blur that wrote back into what is explored would open ground nobody
     * has walked -- a little more of it every frame, spreading outward for as long
     * as the run lasted.
     */
    @Test
    void softeningOpensNothing() {
        var seen = discovery();
        seen.reveal(List.of(at(LOCAL, 200f, 150f)), LOCAL, 40f);
        int opened = seen.exploredCells();
        int inSight = seen.visibleCells();

        settle(seen);

        assertEquals(opened, seen.exploredCells(), "the fog spread itself across the map");
        assertEquals(inSight, seen.visibleCells(), "and gave him eyes he has not got");
    }

    /** Ground in sight is drawn full, and ground never walked stays black. */
    @Test
    void sightIsFullAndTheUnwalkedStaysBlack() {
        var seen = discovery();
        seen.reveal(List.of(at(LOCAL, 200f, 150f)), LOCAL, 40f);

        settle(seen);

        assertTrue(seen.lightAt(20, 15) > 0.95f, "he is standing there");
        assertEquals(0f, seen.lightAt(2, 2), 0.001f, "and has never been over there");
    }

    /**
     * Between the two there is a slope rather than a step.
     *
     * <p>This is what makes the floor look like fog instead of a staircase: the
     * cells at the rim of his sight are drawn part-lit, so the boundary falls
     * across a couple of cells rather than on one line.
     */
    @Test
    void theEdgeOfSightIsASlope() {
        var seen = discovery();
        seen.reveal(List.of(at(LOCAL, 200f, 150f)), LOCAL, 40f);

        settle(seen);

        // Straight out from him along one row, from the middle of his sight to
        // well past the edge of it.
        float previous = seen.lightAt(20, 15);
        boolean sawAPartLitCell = false;
        for (int cx = 21; cx <= 26; cx++) {
            float here = seen.lightAt(cx, 15);
            assertTrue(here <= previous + 0.001f, "the light should only fall going outward");
            sawAPartLitCell |= here > 0.05f && here < 0.95f;
            previous = here;
        }
        assertTrue(sawAPartLitCell, "it went straight from lit to black, which is the staircase");
    }

    /**
     * And it takes time. A cell does not jump to its brightness the instant it is
     * revealed, or walking would flip rows of cells on and off.
     */
    @Test
    void theFogTakesAMomentToOpen() {
        var seen = discovery();
        seen.reveal(List.of(at(LOCAL, 200f, 150f)), LOCAL, 40f);

        seen.soften(1f / 30f);
        float afterOneFrame = seen.lightAt(20, 15);
        settle(seen);

        assertTrue(afterOneFrame > 0f, "it should have begun to open");
        assertTrue(afterOneFrame < seen.lightAt(20, 15) - 0.1f,
                "and it should not have arrived all at once");
    }

    /** A new floor is black again, brightness and all. */
    @Test
    void aNewFloorIsDarkFromTheFirstFrame() {
        var seen = discovery();
        seen.reveal(List.of(at(LOCAL, 200f, 150f)), LOCAL, 40f);
        settle(seen);

        seen.reset(GRID);

        assertEquals(0f, seen.lightAt(20, 15), 0.001f,
                "the last floor's light should not be shining on this one");
    }
}
