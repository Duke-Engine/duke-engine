package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.map.Dressed;
import uz.dukeengine.core.map.MapScenery;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.PathGrid;

/** A dressed map's scenery with a footprint stands in the simulation's way, laid in world units from its cells. */
class DressedMapTest {

    private record Piece(String model, float x, float y, float footprint) implements MapScenery {
    }

    private record Floor(List<Piece> scenery) implements Dressed {
        @Override
        public String name() {
            return "dressed";
        }
    }

    @Test
    void theSceneryWithAFootprintClosesItsGroundAndTheRestIsOnlyDrawn() {
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
        assertTrue(logic.getPathGrid().isBlocked(10, 10));
        assertFalse(logic.getPathGrid().isBlocked(3, 3), "the grass stands in nobody's way");
    }
}
