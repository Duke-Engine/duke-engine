package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.map.Dressed;
import uz.dukeengine.core.map.MapScenery;
import uz.dukeengine.core.map.Subdivided;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.PathGrid;

/**
 * A dressed map's scenery with a footprint stands in the simulation's way, in world units from its cells; a subdivided
 * map is walked on finer cells than it is drawn and counted in its own.
 */
class DressedMapTest {

    private record Piece(String model, float x, float y, float footprint) implements MapScenery {
    }

    private record Floor(List<Piece> scenery) implements Dressed {
        @Override
        public String name() {
            return "dressed";
        }
    }

    private record Wood(List<Piece> scenery) implements Dressed, Subdivided {
        @Override
        public String name() {
            return "wood";
        }

        @Override
        public int navigationCellsPerCell() {
            return 2;
        }
    }

    @Test
    void theSceneryWithAFootprintStandsInTheWayAndTheRestIsOnlyDrawn() {
        var game = DukeGame.create("Dressed");
        game.localPlayer(game.addPlayer("Me", java.awt.Color.CYAN));
        game.applyMapTerrain(new PathGrid(20, 20), new Floor(List.of(
                new Piece("models/boulder.glb", 10.5f, 10.5f, 1.2f),
                new Piece("models/grass.glb", 3.5f, 3.5f, 0f))));
        game.runHeadless(1);

        var logic = game.getLogic();
        var path = logic.findPath(new Coord3D(25f, 105f, 0f), new Coord3D(185f, 105f, 0f));
        assertTrue(path.getWaypoints().stream().anyMatch(way -> Math.abs(way.y() - 105f) > 15f),
                "round the boulder standing 1.2 cells about the middle of cell 10, 10");
        assertFalse(logic.getPathGrid().isBlocked(10, 10), "closing no cell: kept clear by its true distance");
        assertFalse(logic.getPathGrid().clearOfCircles(105f, 105f, 0f), "12 units round its middle");
        assertTrue(logic.getPathGrid().clearOfCircles(35f, 35f, 4f), "the grass stands in nobody's way");
    }

    @Test
    void aSubdividedMapIsWalkedOnFinerCellsAndCountedInItsOwn() {
        var game = DukeGame.create("Wood");
        game.localPlayer(game.addPlayer("Me", java.awt.Color.CYAN));
        var drawn = new PathGrid(20, 20);
        game.applyMapTerrain(drawn, new Wood(List.of(new Piece("models/tree.glb", 10.5f, 10.5f, 0.15f))));
        game.runHeadless(1);

        var logic = game.getLogic();
        assertEquals(40, logic.getPathGrid().getWidth(), "walked on cells of 5");
        assertEquals(10f, logic.cellSize(), "and every rule counted in the map's cells of 10");
        assertSame(drawn, game.getTerrain(), "drawn a tile to a cell");
        assertFalse(logic.getPathGrid().clearOfCircles(106f, 105f, 0f), "the trunk 1.5 round its middle");
        assertTrue(logic.getPathGrid().clearOfCircles(105f, 112f, 5f), "a knight beside it");
    }
}
