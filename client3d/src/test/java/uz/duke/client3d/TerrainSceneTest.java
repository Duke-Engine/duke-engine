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

    // ---- discovery ----

    /** A three-by-three room with stone all round it, in a grid 5 cells wide. */
    private static final String ROOM = """
            #####
            #...#
            #...#
            #...#
            #####
            """;

    private static TerrainScene discovering(Node root) {
        return new TerrainScene(root, color -> null, true);
    }

    /** Whether a cell's cover is drawn — the thing that makes ground black. */
    private static boolean lidShown(Node root, int cellX, int cellY) {
        return shown(root, "fog", cellX, cellY);
    }

    private static boolean rockShown(Node root, int cellX, int cellY) {
        return shown(root, "rock", cellX, cellY);
    }

    /**
     * Found by where it stands rather than by index: a lid is laid out from the
     * cell's near corner and a rock from its centre, so each is looked up on its
     * own terms and the test cannot be fooled by a change of ordering.
     */
    private static boolean shown(Node root, String name, int cellX, int cellY) {
        float cell = uz.duke.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
        float x = name.equals("rock") ? (cellX + 0.5f) * cell : cellX * cell;
        float z = name.equals("rock") ? (cellY + 0.5f) * cell : (cellY + 1) * cell;
        for (var child : root.getChildren()) {
            var at = child.getLocalTranslation();
            if (child.getName().equals(name)
                    && Math.abs(at.x - x) < 0.01f && Math.abs(at.z - z) < 0.01f) {
                return child.getCullHint() != com.jme3.scene.Spatial.CullHint.Always;
            }
        }
        return false; // no such thing in the scene at all
    }

    /**
     * A game that did not ask for discovery gets exactly what it always got — no
     * lids, no per-cell bookkeeping, and being handed a discovery changes nothing.
     */
    @Test
    void aGameWithoutDiscoveryIsDrawnAsBefore() {
        var root = new Node("terrain");
        var terrain = scene(root);
        terrain.rebuild(MapLoader.fromText(ROOM));
        int children = root.getChildren().size();

        terrain.applyDiscovery(new Discovery(MapLoader.fromText(ROOM)));

        assertEquals(blockedCells(ROOM) + 1, children, "ground and stone, nothing else");
        assertEquals(children, root.getChildren().size(), "and nothing appeared");
        assertTrue(rockShown(root, 2, 0), "its walls are drawn whether anyone has been there or not");
    }

    /**
     * Undiscovered stone is hidden, not blacked out. The lid lies flat on the floor
     * and a wall stands six units above it, so a black lid alone would leave the
     * wall sticking up out of the dark — handing the player the shape of a room he
     * has never entered.
     */
    @Test
    void wallsNobodyHasSeenAreNotDrawnAtAll() {
        var root = new Node("terrain");
        var terrain = discovering(root);
        terrain.rebuild(MapLoader.fromText(ROOM));

        terrain.applyDiscovery(new Discovery(MapLoader.fromText(ROOM)));

        assertTrue(lidShown(root, 2, 2), "the floor is covered");
        assertTrue(!rockShown(root, 2, 0), "and the wall is not there to be seen");
    }

    /** Where he is standing, the cover comes off and the world is drawn lit. */
    @Test
    void whereHeStandsIsUncovered() {
        var root = new Node("terrain");
        var terrain = discovering(root);
        var grid = MapLoader.fromText(ROOM);
        terrain.rebuild(grid);

        terrain.applyDiscovery(seenFrom(grid, 25f, 25f, 20f));

        assertTrue(!lidShown(root, 2, 2), "the cell he is in should be open");
        assertTrue(rockShown(root, 2, 0), "and the wall beside him drawn");
    }

    /**
     * Walked out of and left behind: the wall stays on the map — that is the
     * memory — but the ground goes back under a cover, which is what tells the
     * player at a glance that he is no longer looking at it.
     */
    @Test
    void aRoomHeHasLeftKeepsItsWallsUnderCover() {
        var root = new Node("terrain");
        var terrain = discovering(root);
        var grid = MapLoader.fromText(ROOM);
        terrain.rebuild(grid);
        var seen = new Discovery(grid);

        seen.reveal(java.util.List.of(unit(25f, 25f)), 0, 20f);
        seen.reveal(java.util.List.of(unit(500f, 500f)), 0, 20f); // gone off elsewhere
        terrain.applyDiscovery(seen);

        assertTrue(rockShown(root, 2, 0), "he should still know the wall is there");
        assertTrue(lidShown(root, 2, 2), "but it is not lit any more");
    }

    private static Discovery seenFrom(uz.duke.core.pathfind.PathGrid grid,
            float x, float y, float radius) {
        var seen = new Discovery(grid);
        seen.reveal(java.util.List.of(unit(x, y)), 0, radius);
        return seen;
    }

    private static uz.duke.game.view.UnitView unit(float x, float y) {
        return new uz.duke.game.view.UnitView(
                1, "Hero", 0, x, y, 0f, 10f, 10f, false, true, false, false, -1);
    }
}
