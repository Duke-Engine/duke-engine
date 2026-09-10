package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

        seen.reveal(List.of(at(LOCAL, 205f, 155f)), LOCAL, 30f, "Hero");

        assertEquals(Discovery.State.VISIBLE, seen.stateAt(20, 15), "the cell he stands in");
        assertEquals(Discovery.State.UNSEEN, seen.stateAt(30, 15), "a hundred units away");
        assertTrue(seen.exploredCells() > 0 && seen.exploredCells() < 40 * 30,
                "some of the floor, not all of it: " + seen.exploredCells());
    }

    /** Walking on leaves the way back drawn behind him. */
    @Test
    void groundHeHasLeftStaysOnTheMap() {
        var seen = discovery();
        seen.reveal(List.of(at(LOCAL, 55f, 155f)), LOCAL, 30f, "Hero");
        int openedBehind = seen.exploredCells();

        seen.reveal(List.of(at(LOCAL, 355f, 155f)), LOCAL, 30f, "Hero");

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
        seen.reveal(List.of(at(LOCAL, 55f, 155f)), LOCAL, 30f, "Hero");
        seen.reveal(List.of(at(LOCAL, 355f, 155f)), LOCAL, 30f, "Hero");

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

    /** One of the player's own, of whatever kind, standing somewhere. */
    private static UnitView own(String template, float x, float y) {
        return new UnitView(2, template, LOCAL, x, y, 0f, 4f, 4f, false, true, false, false, -1);
    }

    /**
     * An arrow of his own crossing the room opens nothing.
     *
     * <p>Owning a thing is not seeing through it, and this is the case where the
     * difference shows: an arrow is a unit like any other, it belongs to the
     * player, and a map opened around everything he owns is opened along the
     * flight of every shot he takes. The dark then lasts exactly as long as it
     * takes to turn round and fire into it.
     */
    @Test
    void anArrowOfHisOwnCarriesNoLight() {
        var seen = discovery();

        seen.reveal(List.of(own("Arrow", 205f, 155f)), LOCAL, 30f, "Hero");

        assertEquals(0, seen.exploredCells(), "the shot lit the floor it crossed");
        assertEquals(Discovery.State.UNSEEN, seen.stateAt(20, 15));
    }

    /** And the hero standing in the same spot opens it, so the rule is the kind. */
    @Test
    void butTheHeroInTheSameSpotOpensIt() {
        var seen = discovery();

        seen.reveal(List.of(own("Hero", 205f, 155f)), LOCAL, 30f, "Hero");

        assertEquals(Discovery.State.VISIBLE, seen.stateAt(20, 15));
    }

    /**
     * A game that names nobody is a game where everything of his has eyes.
     *
     * <p>Which is what the client did for every game before there was a name to
     * check against, and there is no reason for an RTS to lose it.
     */
    @Test
    void namingNobodyLetsEverythingSee() {
        var seen = discovery();

        seen.reveal(List.of(own("Arrow", 205f, 155f)), LOCAL, 30f, null);

        assertEquals(Discovery.State.VISIBLE, seen.stateAt(20, 15));
    }

    /** The dungeon is discovered by the player, not by the things hunting him. */
    @Test
    void aMonsterWanderingAboutOpensNothing() {
        var seen = discovery();

        seen.reveal(List.of(at(ENEMY, 205f, 155f)), LOCAL, 30f, "Hero");

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

        near.reveal(List.of(at(LOCAL, 205f, 155f)), LOCAL, 30f, "Hero");
        far.reveal(List.of(at(LOCAL, 205f, 155f)), LOCAL, 90f, "Hero");

        assertTrue(far.exploredCells() > near.exploredCells(),
                near.exploredCells() + " cells at 30, " + far.exploredCells() + " at 90");
    }

    /** A game that never asked for discovery is not left staring at a black map. */
    @Test
    void withoutARadiusNothingIsClaimedToBeSeen() {
        var seen = discovery();

        seen.reveal(List.of(at(LOCAL, 205f, 155f)), LOCAL, 0f, "Hero");

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
        seen.reveal(List.of(at(LOCAL, 205f, 155f)), LOCAL, 60f, "Hero");
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
        seen.reveal(List.of(at(LOCAL, 55f, 35f)), LOCAL, 1000f, "Hero");

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
        seen.reveal(List.of(at(LOCAL, 200f, 150f)), LOCAL, 40f, "Hero");
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
        seen.reveal(List.of(at(LOCAL, 200f, 150f)), LOCAL, 40f, "Hero");

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
        seen.reveal(List.of(at(LOCAL, 200f, 150f)), LOCAL, 40f, "Hero");

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
        seen.reveal(List.of(at(LOCAL, 200f, 150f)), LOCAL, 40f, "Hero");

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
        seen.reveal(List.of(at(LOCAL, 200f, 150f)), LOCAL, 40f, "Hero");
        settle(seen);

        seen.reset(GRID);

        assertEquals(0f, seen.lightAt(20, 15), 0.001f,
                "the last floor's light should not be shining on this one");
    }

    // ---- line of sight ----

    /** A wall down the middle, with a doorway, so a line can be blocked or not. */
    private static PathGrid walled() {
        var grid = new PathGrid(40, 30);
        for (int cy = 0; cy < 30; cy++) {
            grid.setBlocked(20, cy, cy != 15); // a wall with one gap in it
        }
        return grid;
    }

    private static Discovery seeing(PathGrid grid, boolean lineOfSight) {
        // No softening and no easing: this is about what is revealed, and the two
        // of them are about how it is drawn.
        return new Discovery(grid, new Fog(lineOfSight, 0f, 0.3f, 1f, 0, 7f, 256, 0x000000));
    }

    /**
     * Stone stops sight.
     *
     * <p>The hero stands one side of a wall and looks at it; the cell behind it is
     * inside his radius and stays dark. Without this the whole point of a dungeon
     * goes: a corridor lights the rooms on both sides of it.
     */
    @Test
    void aWallHidesWhatIsBehindIt() {
        var seen = seeing(walled(), true);

        // At cell (17, 10), well within reach of cells on both sides of the wall.
        seen.reveal(List.of(at(LOCAL, 175f, 105f)), LOCAL, 80f, "Hero");

        assertEquals(Discovery.State.VISIBLE, seen.stateAt(19, 10), "his own side of the wall");
        assertEquals(Discovery.State.VISIBLE, seen.stateAt(20, 10),
                "the wall itself is seen -- it is what he is looking at");
        assertEquals(Discovery.State.UNSEEN, seen.stateAt(21, 10),
                "and the room on the far side of it is not");
        assertEquals(Discovery.State.UNSEEN, seen.stateAt(24, 10));
    }

    /** Through the doorway he can see, because there is nothing in the way. */
    @Test
    void aDoorwayLetsSightThrough() {
        var seen = seeing(walled(), true);

        // Standing in line with the gap at (20, 15).
        seen.reveal(List.of(at(LOCAL, 175f, 155f)), LOCAL, 80f, "Hero");

        assertEquals(Discovery.State.VISIBLE, seen.stateAt(21, 15),
                "straight through the gap");
        assertEquals(Discovery.State.UNSEEN, seen.stateAt(21, 10),
                "but not through the wall beside it");
    }

    /** Turned off, the light is a circle again and does not care what it crosses. */
    @Test
    void withoutLineOfSightTheWallIsIgnored() {
        var seen = seeing(walled(), false);

        seen.reveal(List.of(at(LOCAL, 175f, 105f)), LOCAL, 80f, "Hero");

        assertEquals(Discovery.State.VISIBLE, seen.stateAt(24, 10),
                "the old behaviour, which every other game still gets");
    }

    /** Sight is symmetrical about the wall, not about the direction he walked in. */
    @Test
    void theWallStopsSightFromEitherSide() {
        var seen = seeing(walled(), true);

        seen.reveal(List.of(at(LOCAL, 235f, 105f)), LOCAL, 80f, "Hero"); // cell (23, 10)

        assertEquals(Discovery.State.VISIBLE, seen.stateAt(21, 10));
        assertEquals(Discovery.State.UNSEEN, seen.stateAt(19, 10),
                "the far side is dark whichever side he is standing on");
    }

    /**
     * How much the softening spreads the edge is the game's number, and a wider
     * one really is wider.
     */
    @Test
    void theFileDecidesHowSoftTheEdgeIs() {
        var sharp = new Discovery(GRID, new Fog(false, 0f, 0.3f, 1f, 0, 7f, 256, 0x000000));
        var soft = new Discovery(GRID, new Fog(false, 0f, 0.3f, 1f, 3, 7f, 256, 0x000000));
        var standing = List.of(at(LOCAL, 205f, 155f));

        sharp.reveal(standing, LOCAL, 40f, "Hero");
        soft.reveal(standing, LOCAL, 40f, "Hero");
        // One long step, so both have arrived at their targets.
        sharp.soften(10f);
        soft.soften(10f);

        assertEquals(1f, sharp.lightAt(20, 15), 0.001f, "no softening: full light at the centre");
        assertTrue(soft.lightAt(20, 15) < sharp.lightAt(20, 15),
                "a wide kernel borrows from the dark around it, so even the middle dims");
        assertTrue(soft.lightAt(20, 15) > 0.5f, "but it is still plainly the lit part");
    }

    /** Nothing about any of this opens a cell that was not already open. */
    @Test
    void sighteningOpensNothingByItself() {
        var seen = seeing(walled(), true);
        seen.reveal(List.of(at(LOCAL, 175f, 105f)), LOCAL, 80f, "Hero");
        int opened = seen.exploredCells();

        for (int i = 0; i < 20; i++) {
            seen.soften(0.1f);
        }

        assertEquals(opened, seen.exploredCells(), "softening reads the map, it never writes it");
    }

    // ---- height ----

    /**
     * A map with a raised room in the middle of it: cells 20 and up along x stand
     * a storey higher, and there is no stone anywhere.
     */
    private static PathGrid terraced() {
        var grid = new PathGrid(40, 30);
        grid.setLevelHeight(10f);
        for (int cy = 0; cy < 30; cy++) {
            for (int cx = 20; cx < 40; cx++) {
                grid.setLevel(cx, cy, 1);
            }
        }
        return grid;
    }

    private static Discovery seeingTerraced() {
        return new Discovery(terraced(), new Fog(true, 0f, 0.3f, 1f, 0, 7f, 256, 0x000000));
    }

    /**
     * The room upstairs is not in sight from downstairs — the whole reason for
     * building a dungeon upward.
     *
     * <p>Nothing is in the way of it: no stone, no door, an open floor the whole
     * distance. What hides it is that it is above him, and he is looking at the
     * side of its floor.
     *
     * <p>Not in sight, but not unseen either. What is being hidden is whatever
     * stands up there — the client draws no creature on a cell it cannot see —
     * and the stone itself is in plain view from below. Left properly unseen, the
     * floor above is cut out of the picture altogether, and a black rectangle in
     * the middle of a lit room reads as a hole rather than as a storey. That is
     * what it looked like on screen, and it is why the state here is remembered.
     */
    @Test
    void aRoomAStoreyUpIsOutOfSightUntilItIsClimbedTo() {
        var seen = seeingTerraced();

        seen.reveal(List.of(at(LOCAL, 155f, 155f)), LOCAL, 120f, "Hero");

        assertEquals(Discovery.State.VISIBLE, seen.stateAt(18, 15), "his own floor is open");
        assertEquals(Discovery.State.REMEMBERED, seen.stateAt(21, 15),
                "the floor above him is drawn, but nothing on it is in sight");
        assertEquals(Discovery.State.REMEMBERED, seen.stateAt(24, 15),
                "and the same further into it");
        assertFalse(seen.canSee(215f, 155f), "so a creature standing up there is hidden");
    }

    /** Climb it and it opens. */
    @Test
    void climbingToItOpensIt() {
        var seen = seeingTerraced();

        seen.reveal(List.of(at(LOCAL, 245f, 155f)), LOCAL, 120f, "Hero");

        assertEquals(Discovery.State.VISIBLE, seen.stateAt(24, 15), "he is standing on it");
        assertEquals(Discovery.State.VISIBLE, seen.stateAt(21, 15));
    }

    /**
     * And from up there he can see down over the edge.
     *
     * <p>The rule is one-sided on purpose. Standing on a balcony you can see the
     * floor below you; standing under one you cannot see the balcony. Making both
     * invisible would be simpler and would throw away the reward for climbing.
     */
    @Test
    void fromUpstairsHeLooksDownOnWhatIsBelow() {
        var seen = seeingTerraced();

        seen.reveal(List.of(at(LOCAL, 215f, 155f)), LOCAL, 120f, "Hero");

        assertEquals(Discovery.State.VISIBLE, seen.stateAt(18, 15),
                "the lower floor is in plain view from above it");
    }

    /**
     * A raised room between him and something else hides what is behind it, the
     * way a wall does.
     */
    @Test
    void aRaisedRoomBlocksTheViewOfWhatIsBeyondIt() {
        var grid = new PathGrid(40, 30);
        grid.setLevelHeight(10f);
        for (int cy = 0; cy < 30; cy++) {
            grid.setLevel(20, cy, 1); // a raised ridge one cell thick
        }
        var seen = new Discovery(grid, new Fog(true, 0f, 0.3f, 1f, 0, 7f, 256, 0x000000));

        seen.reveal(List.of(at(LOCAL, 155f, 155f)), LOCAL, 150f, "Hero");

        assertFalse(seen.canSee(205f, 155f), "the ridge itself is above him");
        assertEquals(Discovery.State.UNSEEN, seen.stateAt(25, 15),
                "and the ground beyond it is behind it, and not even remembered");
    }

    /** A flat map is discovered exactly as it was before there was any height. */
    @Test
    void aFlatMapIsUnaffectedByAnyOfThis() {
        var flat = seeing(new PathGrid(40, 30), true);
        var same = seeing(new PathGrid(40, 30), true);

        flat.reveal(List.of(at(LOCAL, 205f, 155f)), LOCAL, 60f, "Hero");
        same.reveal(List.of(at(LOCAL, 205f, 155f)), LOCAL, 60f, "Hero");

        assertEquals(same.exploredCells(), flat.exploredCells());
        assertEquals(Discovery.State.VISIBLE, flat.stateAt(24, 15),
                "open ground on one level is open ground");
    }
}
