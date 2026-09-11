package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.core.math.Coord3D;
import uz.duke.core.pathfind.PathGrid;

/**
 * An order pointed at somewhere unreachable still sends him somewhere.
 *
 * <p>The fault being pinned down is a silent one: clicking into stone, or into a
 * room with no door, used to post an order the search could not answer, and the
 * hero simply stood there. Nothing on screen separated that from a broken game,
 * and it is the kind of thing that comes back the moment someone re-tunes the
 * map — so each shape of unreachable place gets its own test.
 */
class DestinationTest {

    /** A plain open floor, big enough to have a far side. */
    private static PathGrid openFloor() {
        return new PathGrid(20, 20); // cells of 10, so the map is 200 x 200
    }

    private static Coord3D at(float x, float y) {
        return new Coord3D(x, y, 0f);
    }

    @Test
    void anOrdinaryClickIsPassedThroughUntouched() {
        var grid = openFloor();
        var wanted = at(137.5f, 92.25f);

        var target = Destination.asCloseAsHeCanGet(grid, at(25f, 25f), wanted);

        // The same object, not merely an equal one: a click he can walk to must
        // keep the exact spot the player picked, not be snapped to a cell centre.
        assertSame(wanted, target, "a reachable click should be left exactly as it was");
    }

    @Test
    void withNoMapAtAllTheClickStands() {
        var wanted = at(40f, 40f);

        assertSame(wanted, Destination.asCloseAsHeCanGet(null, at(10f, 10f), wanted),
                "a game without terrain has nothing to clamp against");
    }

    @Test
    void aClickIntoStoneStopsAtTheEdgeOfIt() {
        var grid = openFloor();
        for (int x = 10; x < 16; x++) {
            for (int y = 4; y < 16; y++) {
                grid.setBlocked(x, y, true); // a slab of rock down the middle
            }
        }
        var hero = at(25f, 95f); // cell 2,9 — well to the west of it

        var target = Destination.asCloseAsHeCanGet(grid, hero, at(125f, 95f)); // cell 12,9

        int cx = grid.toCellX(target);
        int cy = grid.toCellY(target);
        assertFalse(grid.isTerrainBlocked(cx, cy), "he cannot be sent into the rock itself");
        assertEquals(9, cx, "he should stop against the rock's near face");
        assertEquals(9, cy, "and straight across from where he was pointed");
    }

    @Test
    void aSealedRoomIsNotEnteredThroughItsWall() {
        var grid = openFloor();
        // A room from 12,12 to 16,16 with a wall all the way round it.
        for (int x = 11; x <= 17; x++) {
            for (int y = 11; y <= 17; y++) {
                boolean wall = x == 11 || x == 17 || y == 11 || y == 17;
                grid.setBlocked(x, y, wall);
            }
        }

        var target = Destination.asCloseAsHeCanGet(grid, at(25f, 25f), at(145f, 145f));

        int cx = grid.toCellX(target);
        int cy = grid.toCellY(target);
        assertTrue(cx <= 10 || cy <= 10, "the open floor inside a sealed room is still unreachable: "
                + "he was sent to " + cx + "," + cy);
        assertFalse(grid.isTerrainBlocked(cx, cy), "and never into the wall");
    }

    @Test
    void aLedgeWithNoStairUpToItIsOutOfReach() {
        var grid = openFloor();
        grid.setLevelHeight(20f);
        for (int x = 12; x < 18; x++) {
            for (int y = 12; y < 18; y++) {
                grid.setLevel(x, y, 1); // a storey up, and nothing joins it
            }
        }

        var target = Destination.asCloseAsHeCanGet(grid, at(25f, 25f), at(145f, 145f));

        assertEquals(0, grid.level(grid.toCellX(target), grid.toCellY(target)),
                "a floor he has no stair to is as unreachable as stone");
    }

    @Test
    void aStairMakesTheSameLedgeReachable() {
        var grid = openFloor();
        grid.setLevelHeight(20f);
        for (int x = 12; x < 18; x++) {
            for (int y = 12; y < 18; y++) {
                grid.setLevel(x, y, 1);
            }
        }
        grid.setRamp(12, 14, true); // one way up, on the flat side

        var wanted = at(145f, 145f);

        assertSame(wanted, Destination.asCloseAsHeCanGet(grid, at(25f, 145f), wanted),
                "with a stair it is an ordinary walk and the click should stand");
    }

    /**
     * A statue in the doorway is a wall with a statue in it.
     *
     * <p>This test used to say the opposite, and used to be right about the wrong
     * thing. Its worry was a <em>creature</em> in the doorway — cutting an order
     * short because a skeleton happened to be standing there at the instant of the
     * click would be a worse fault than the one this class fixes, and an
     * intermittent one, which is the hardest kind to find. That worry stands. What
     * was wrong was the way it was written: creatures are never in this layer at
     * all. The grid's obstacle layer is baked from things that <b>cannot move</b>
     * — see {@code GameLogic.refreshStaticObstacles}, and
     * {@code WedgedTest.aCreatureIsNeverBakedIntoTheMap} for the same fact
     * measured on a running game — so putting a creature in it to prove creatures
     * are exempt proved nothing, and kept the real fault alive: a click on a
     * barrel was an order the mover's own search could not answer, and the hero
     * stood there.
     */
    @Test
    void furnitureInTheDoorwayDoesShortenTheOrder() {
        var grid = openFloor();
        for (int y = 0; y < 20; y++) {
            grid.setBlocked(10, y, y != 9); // a wall with one gap in it
        }
        grid.beginObstacles();
        grid.setObstacle(10, 9); // and a statue standing in the gap
        grid.commitObstacles();

        var target = Destination.asCloseAsHeCanGet(grid, at(25f, 95f), at(155f, 95f));

        assertTrue(grid.toCellX(target) < 10,
                "the only way through is stopped up, so he stays on his own side: he was sent to "
                        + grid.toCellX(target) + "," + grid.toCellY(target));
    }

    /**
     * A click on the barrel in the middle of the room walks him up to the barrel.
     *
     * <p>The plainest shape of the fault and the last one left: nothing is between
     * them, the floor all round it is open, and the one cell he was pointed at is
     * the one cell he cannot stand in. Judged by the map alone that click was
     * reachable, so it was passed through untouched — and then the mover's own
     * search, which does know about the barrel, found no route and he never moved.
     */
    @Test
    void aClickOnFurnitureWalksHimUpToIt() {
        var grid = openFloor();
        grid.beginObstacles();
        grid.setObstacle(10, 10); // one barrel, standing in the open
        grid.commitObstacles();
        var barrel = at(105f, 105f); // the middle of that cell

        var target = Destination.asCloseAsHeCanGet(grid, at(25f, 105f), barrel);

        assertFalse(grid.isBlocked(grid.toCellX(target), grid.toCellY(target)),
                "he cannot be sent to stand inside the barrel");
        assertTrue(target.distance(barrel) <= 15f,
                "he should stop against it rather than short of it, but stopped "
                        + target.distance(barrel) + " away");
    }

    @Test
    void aHeroWalledInOnEverySideKeepsHisOwnGround() {
        var grid = openFloor();
        for (int x = 1; x <= 3; x++) {
            for (int y = 1; y <= 3; y++) {
                grid.setBlocked(x, y, !(x == 2 && y == 2)); // one open cell, ringed
            }
        }

        var target = Destination.asCloseAsHeCanGet(grid, at(25f, 25f), at(155f, 155f));

        assertEquals(2, grid.toCellX(target), "with nowhere to go he stays where he is");
        assertEquals(2, grid.toCellY(target), "with nowhere to go he stays where he is");
    }

    @Test
    void heIsNotSqueezedDiagonallyBetweenTwoCorners() {
        var grid = openFloor();
        // A wall with a "gap" that is only a diagonal, which no body fits through.
        for (int y = 0; y < 20; y++) {
            grid.setBlocked(10, y, true);
        }
        for (int y = 0; y < 20; y++) {
            grid.setBlocked(11, y, y != 8);
        }
        grid.setBlocked(10, 9, false);

        var target = Destination.asCloseAsHeCanGet(grid, at(25f, 95f), at(175f, 85f));

        assertTrue(grid.toCellX(target) <= 10,
                "cutting the corner between two walls is not a route, so he stops short");
    }
}
