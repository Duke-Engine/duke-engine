package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.duke.core.pathfind.MapLoader;
import uz.duke.core.pathfind.PathGrid;

/**
 * Where each tile lands.
 *
 * <p>Half a cell out or a right angle wrong is the whole failure mode of a
 * modular kit, and both look like "the art is broken" rather than like a
 * coordinate mistake. Worked out here as arithmetic, where it can be checked
 * against the map that produced it instead of against a screenshot.
 */
class TileLayoutTest {

    /** One open cell in the middle, stone all round it. */
    private static final String ONE_ROOM = """
            ###
            #.#
            ###
            """;

    /** A three-by-one corridor, so a cell can have open neighbours. */
    private static final String CORRIDOR = """
            #####
            #...#
            #####
            """;

    private static final float CELL = PathGrid.DEFAULT_CELL_SIZE;

    private static List<TileLayout.Placement> layout(String map) {
        return TileLayout.of(MapLoader.fromText(map));
    }

    private static List<TileLayout.Placement> only(String map, TileLayout.Piece piece) {
        return layout(map).stream().filter(p -> p.piece() == piece).toList();
    }

    /** Every open cell gets a floor, in the middle of itself. */
    @Test
    void aFloorTileSitsInTheMiddleOfEveryOpenCell() {
        var floors = only(ONE_ROOM, TileLayout.Piece.FLOOR);

        assertEquals(1, floors.size());
        assertEquals(1.5f * CELL, floors.get(0).x(), 0.001f);
        assertEquals(1.5f * CELL, floors.get(0).z(), 0.001f);
    }

    /** Stone is never drawn: it is the absence of floor, and what the walls face. */
    @Test
    void solidCellsAreNotDrawn() {
        var floors = only(ONE_ROOM, TileLayout.Piece.FLOOR);

        assertEquals(1, floors.size(), "eight of the nine cells are stone");
    }

    /**
     * A wall stands on the line between the open cell and the stone, not in the
     * middle of either — which is where the pathfinder stops the player, so the
     * wall he sees and the wall he bumps into are the same wall.
     */
    @Test
    void aWallStandsOnTheBoundaryTheHeroCannotCross() {
        var walls = only(ONE_ROOM, TileLayout.Piece.WALL);

        assertEquals(4, walls.size(), "walled in on every side");
        // The eastern one: on the far edge of the cell, halfway up it.
        var east = walls.stream().filter(w -> w.x() > 1.9f * CELL).findFirst().orElseThrow();
        assertEquals(2f * CELL, east.x(), 0.001f, "the cell's own boundary");
        assertEquals(1.5f * CELL, east.z(), 0.001f, "centred on the side");
    }

    /** Each side is turned to face its own stone, and no two the same way. */
    @Test
    void thefourWallsFaceFourDifferentWays() {
        var yaws = only(ONE_ROOM, TileLayout.Piece.WALL).stream()
                .map(TileLayout.Placement::yaw)
                .distinct()
                .toList();

        assertEquals(4, yaws.size(), "a wall turned the wrong way is a wall facing the room");
    }

    /**
     * The turn is the one that puts the wall's body in the stone.
     *
     * <p>A wall piece faces along its own +Z. Turning it by the yaw here has to
     * send that face back into the room and the body out into the rock; the other
     * three turns each look plausible and each is wrong by a quarter.
     */
    @Test
    void aWallIsTurnedSoItsFaceLooksIntoTheRoom() {
        var walls = only(ONE_ROOM, TileLayout.Piece.WALL);

        for (var wall : walls) {
            double yaw = Math.toRadians(wall.yaw());
            // Where the piece's +Z ends up after the turn.
            float facingX = (float) Math.sin(yaw);
            float facingZ = (float) Math.cos(yaw);
            // From the wall, that direction has to lead back to the cell centre.
            float towardRoomX = 1.5f * CELL - wall.x();
            float towardRoomZ = 1.5f * CELL - wall.z();

            assertTrue(facingX * towardRoomX + facingZ * towardRoomZ > 0f,
                    "the wall at " + wall.x() + "," + wall.z() + " faces away from the room");
        }
    }

    /** Where two walls meet at a right angle, a post fills the notch between them. */
    @Test
    void aPostFillsTheNotchWhereTwoWallsMeet() {
        var corners = only(ONE_ROOM, TileLayout.Piece.CORNER);

        assertEquals(4, corners.size(), "a cell alone in the stone has four of them");
        assertTrue(corners.stream().anyMatch(c ->
                        Math.abs(c.x() - CELL) < 0.001f && Math.abs(c.z() - CELL) < 0.001f),
                "one should sit on the cell's own north-west corner");
    }

    /** And nowhere else: a corner between two open sides has no notch to fill. */
    @Test
    void noPostWhereThereIsNoNotch() {
        // The middle cell of the corridor is walled north and south only.
        var middle = layout(CORRIDOR).stream()
                .filter(p -> p.cellX() == 2 && p.cellY() == 1)
                .toList();

        assertEquals(0, middle.stream().filter(p -> p.piece() == TileLayout.Piece.CORNER).count(),
                "its walls run past each other rather than meeting");
        assertEquals(2, middle.stream().filter(p -> p.piece() == TileLayout.Piece.WALL).count());
    }

    /** Cells that face each other get no wall between them — that is the doorway. */
    @Test
    void thereIsNoWallBetweenTwoOpenCells() {
        var walls = only(CORRIDOR, TileLayout.Piece.WALL);

        for (var wall : walls) {
            boolean onAVerticalLineInsideTheCorridor =
                    Math.abs(wall.x() - 2f * CELL) < 0.001f || Math.abs(wall.x() - 3f * CELL) < 0.001f;
            assertTrue(!onAVerticalLineInsideTheCorridor || wall.z() < CELL || wall.z() > 2f * CELL,
                    "a wall was put across the corridor at " + wall.x() + "," + wall.z());
        }
    }

    /**
     * A piece is filed under the cell it was built for: the room, for what a room
     * is made of, and the rock itself, for the roof over it.
     */
    @Test
    void everyPieceIsFiledUnderTheCellItWasBuiltFor() {
        var grid = MapLoader.fromText(CORRIDOR);
        for (var placement : layout(CORRIDOR)) {
            assertEquals(placement.piece() == TileLayout.Piece.CAP,
                    grid.isBlocked(placement.cellX(), placement.cellY()),
                    "only a roof is filed under stone: " + placement);
        }
    }

    /**
     * A stone mass with rock in the middle of it — nothing there touches open
     * ground.
     */
    private static final String MASS = """
            #######
            #.....#
            #.###.#
            #.###.#
            #.###.#
            #.....#
            #######
            """;

    /**
     * Every piece of rock is roofed, not only the ring of it a room can reach.
     *
     * <p>Roofing the ring alone was enough while a lid belonged to the room beside
     * it, because rock further in had no room to belong to. It is not enough to
     * look at: the rock beyond the ring is a straight-edged hole in the picture,
     * and from a camera that looks across the map the roof reads as having been
     * cut off in a line.
     */
    @Test
    void everyPieceOfRockIsRoofedAndNotJustTheRing() {
        var grid = MapLoader.fromText(MASS);
        int stone = 0;
        for (int cy = 0; cy < grid.getHeight(); cy++) {
            for (int cx = 0; cx < grid.getWidth(); cx++) {
                if (grid.isBlocked(cx, cy)) {
                    stone++;
                }
            }
        }
        var caps = only(MASS, TileLayout.Piece.CAP);

        assertEquals(stone, caps.size(), "one roof per piece of rock and no more");
        assertTrue(caps.stream().anyMatch(cap -> Math.abs(cap.x() - 3.5f * CELL) < 0.001f
                        && Math.abs(cap.z() - 3.5f * CELL) < 0.001f),
                "including the middle of the mass, which no room is next to");
    }

    /** The same map lays out the same way twice, piece for piece. */
    @Test
    void theSameMapLaysOutTheSameWay() {
        assertEquals(layout(CORRIDOR), layout(CORRIDOR));
    }

    /** A game with no map lays out nothing rather than failing. */
    @Test
    void noMapLaysOutNothing() {
        assertEquals(List.of(), TileLayout.of(null));
    }

    // ---- height ----

    /**
     * A corridor at the bottom, a room a storey above it, and one stair between
     * them. Read as two layers: the first says what is stone, the second how high
     * each cell stands.
     */
    private static final String TWO_STOREYS = """
            #######
            #00/11#
            #00.11#
            #######
            """;

    private static final float STOREY = 10f;

    private static PathGrid twoStoreys() {
        var grid = MapLoader.fromText(TWO_STOREYS);
        MapLoader.levels(grid, TWO_STOREYS);
        grid.setLevelHeight(STOREY);
        return grid;
    }

    private static List<TileLayout.Placement> only(PathGrid grid, TileLayout.Piece piece) {
        return TileLayout.of(grid).stream().filter(p -> p.piece() == piece).toList();
    }

    /** Each floor tile belongs to the storey its own cell stands on. */
    @Test
    void aFloorIsLaidOnItsOwnStorey() {
        var floors = only(twoStoreys(), TileLayout.Piece.FLOOR);

        for (var floor : floors) {
            float expected = floor.cellX() >= 4 ? STOREY : 0f;
            assertEquals(expected, floor.ground(), 0.001f,
                    "the floor of cell " + floor.cellX() + "," + floor.cellY());
        }
    }

    /**
     * The edge of a raised room is walled, though there is no stone anywhere near
     * it.
     *
     * <p>This is the whole of what makes a storey read as a storey: open floor
     * ending in a drop looks like open floor, and the player walks at it and is
     * stopped by nothing he can see. The rule is the pathfinder's own — a wall
     * goes wherever a body may not cross — so the picture and the collision
     * cannot drift apart.
     */
    @Test
    void aDropIsWalledEvenWithNoStoneThere() {
        var grid = twoStoreys();
        var walls = only(grid, TileLayout.Piece.WALL);

        // The boundary between cell (3,2) at the bottom and (4,2) a storey up.
        var holdingUp = walls.stream()
                .filter(w -> Math.abs(w.x() - 4f * CELL) < 0.001f)
                .filter(w -> Math.abs(w.z() - 2.5f * CELL) < 0.001f)
                .toList();

        assertEquals(1, holdingUp.size(), "the raised room's edge should be held up");
        assertEquals(4, holdingUp.get(0).cellX(), "and the wall belongs to the higher cell");
        assertEquals(0f, holdingUp.get(0).ground(), 0.001f,
                "standing on the lower floor, reaching up to the higher one");
    }

    /** But never across the stair, which is the one place a body may cross. */
    @Test
    void theWayUpIsLeftOpen() {
        var grid = twoStoreys();

        var acrossTheStair = only(grid, TileLayout.Piece.WALL).stream()
                .filter(w -> Math.abs(w.x() - 4f * CELL) < 0.001f)
                .filter(w -> Math.abs(w.z() - 1.5f * CELL) < 0.001f)
                .toList();

        assertEquals(List.of(), acrossTheStair, "a wall was built across the way up");
    }

    /** And the steps are drawn on the ramp cell, turned toward what they climb. */
    @Test
    void aStairIsDrawnTurnedTowardWhatItClimbs() {
        var stairs = only(twoStoreys(), TileLayout.Piece.STAIR);

        assertEquals(1, stairs.size(), "one ramp cell, one flight of steps");
        var stair = stairs.get(0);
        assertEquals(3, stair.cellX());
        assertEquals(3.5f * CELL, stair.x(), 0.001f, "in the middle of its own cell");
        assertEquals(0f, stair.ground(), 0.001f, "starting on the floor it climbs from");
        // The room it serves is to the east, and jME turns +z toward +x at 90°.
        assertEquals(90f, stair.yaw(), 0.001f, "the steps should climb toward the room");
    }

    /** Rock beside a raised room is roofed at the room's height, not the corridor's. */
    @Test
    void theLidOverRockRisesWithTheRoomsBesideIt() {
        var caps = only(twoStoreys(), TileLayout.Piece.CAP);

        var besideTheRoom = caps.stream()
                .filter(c -> c.cellX() == 6 && c.cellY() == 1).findFirst().orElseThrow();
        var besideTheCorridor = caps.stream()
                .filter(c -> c.cellX() == 0 && c.cellY() == 1).findFirst().orElseThrow();

        assertEquals(STOREY, besideTheRoom.ground(), 0.001f);
        assertEquals(0f, besideTheCorridor.ground(), 0.001f);
    }

    /** A map with no height in it lays out exactly as it always did. */
    @Test
    void aFlatMapIsUntouched() {
        for (var placement : layout(CORRIDOR)) {
            assertEquals(0f, placement.ground(), 0f,
                    placement.piece() + " at " + placement.cellX() + "," + placement.cellY());
            assertTrue(placement.piece() != TileLayout.Piece.STAIR,
                    "a flat map has nothing to climb");
        }
    }
}
