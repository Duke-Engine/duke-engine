package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.scene.Node;
import org.junit.jupiter.api.Test;
import uz.duke.core.pathfind.MapLoader;

/**
 * A new world replaces the old one.
 *
 * <p>This is the bug that put a fresh dungeon's units on the previous dungeon's
 * walls: terrain was built once and never again. The two things worth holding
 * still are that a rebuild <em>shows the new world</em> and that it <em>throws the
 * old one away</em> — a rebuild that appended would look right for one run and
 * then eat memory for the rest of the session.
 *
 * <p>No materials are made (the factory returns none) and nothing is rendered, so
 * this runs anywhere — the shape of the scene is all that is being asked about.
 */
class TerrainSceneTest {

    private static TerrainScene scene(Node root) {
        return new TerrainScene(root, color -> null);
    }

    /** Three cells of stone in a five-wide room. */
    private static final String SMALL = """
            #####
            #...#
            #.#.#
            #####
            """;

    /** A different layout, with more stone in it. */
    private static final String OTHER = """
            #######
            #.....#
            ###.###
            #.....#
            #######
            """;

    private static int rocks(Node root) {
        return (int) root.getChildren().stream()
                .filter(child -> child.getName().equals("rock"))
                .count();
    }

    private static int blockedCells(String map) {
        var grid = MapLoader.fromText(map);
        int blocked = 0;
        for (int cy = 0; cy < grid.getHeight(); cy++) {
            for (int cx = 0; cx < grid.getWidth(); cx++) {
                if (grid.isBlocked(cx, cy)) {
                    blocked++;
                }
            }
        }
        return blocked;
    }

    @Test
    void aBuiltWorldHasGroundAndOneRockPerBlockedCell() {
        var root = new Node("terrain");
        scene(root).rebuild(MapLoader.fromText(SMALL));

        assertEquals(blockedCells(SMALL), rocks(root), "every blocked cell should be stone");
        assertEquals(rocks(root) + 1, root.getChildren().size(), "plus the ground");
    }

    /** The heart of it: rebuilding shows the new world, not both worlds. */
    @Test
    void rebuildingReplacesTheWorldRatherThanAddingToIt() {
        var root = new Node("terrain");
        var terrain = scene(root);

        terrain.rebuild(MapLoader.fromText(SMALL));
        int firstWorld = root.getChildren().size();

        terrain.rebuild(MapLoader.fromText(OTHER));

        assertEquals(blockedCells(OTHER) + 1, root.getChildren().size(),
                "the scene should hold exactly the new world");
        assertNotEquals(firstWorld, root.getChildren().size(),
                "and this second world really is a different shape");
    }

    /**
     * Dying over and over is the normal way to play a roguelike, so the scene has
     * to be the same size on the twentieth run as on the first.
     */
    @Test
    void manyRunsInARowDoNotGrowTheScene() {
        var root = new Node("terrain");
        var terrain = scene(root);

        terrain.rebuild(MapLoader.fromText(SMALL));
        int afterFirstRun = root.getChildren().size();

        for (int run = 0; run < 20; run++) {
            // Alternating layouts, as a new seed would give.
            terrain.rebuild(MapLoader.fromText(run % 2 == 0 ? OTHER : SMALL));
        }
        terrain.rebuild(MapLoader.fromText(SMALL));

        assertEquals(afterFirstRun, root.getChildren().size(),
                "twenty runs later the scene should be exactly one world again");
    }

    /** A game with no map still gets ground to stand on. */
    @Test
    void aWorldWithoutAMapStillHasGround() {
        var root = new Node("terrain");
        scene(root).rebuild(null);

        assertEquals(1, root.getChildren().size());
        assertTrue(root.getChildren().stream().anyMatch(c -> c.getName().equals("ground")));
    }
}
