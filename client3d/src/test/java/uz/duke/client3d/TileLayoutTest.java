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

    /** Every piece knows which open cell it belongs to, because fog is per cell. */
    @Test
    void everyPieceIsFiledUnderTheCellItWasSeenFrom() {
        for (var placement : layout(CORRIDOR)) {
            assertTrue(placement.cellY() == 1 && placement.cellX() >= 1 && placement.cellX() <= 3,
                    "a piece was filed under stone: " + placement);
        }
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
}
