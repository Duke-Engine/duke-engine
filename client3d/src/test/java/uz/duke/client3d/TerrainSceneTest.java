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

    /**
     * Whether a cell's stone is built into the picture.
     *
     * <p>Found by where it stands rather than by index, so the test cannot be
     * fooled by a change of ordering.
     */
    private static boolean rockShown(Node root, int cellX, int cellY) {
        float cell = uz.duke.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
        float x = (cellX + 0.5f) * cell;
        float z = (cellY + 0.5f) * cell;
        for (var child : root.getChildren()) {
            var at = child.getLocalTranslation();
            if (child.getName().equals("rock")
                    && Math.abs(at.x - x) < 0.01f && Math.abs(at.z - z) < 0.01f) {
                return child.getCullHint() != com.jme3.scene.Spatial.CullHint.Always;
            }
        }
        return false; // no such thing in the scene at all
    }

    /**
     * A game that did not ask for discovery gets exactly what it always got — no
     * per-cell bookkeeping, and being handed a discovery changes nothing.
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
     * Undiscovered stone is hidden rather than left to the fog sheet. The sheet is
     * aligned to the ground and a rock stands six units above it, so a rock is
     * dimmed by the ground a little way behind it — and a wall standing out of the
     * dark hands the player the shape of a room he has never entered.
     */
    @Test
    void wallsNobodyHasSeenAreNotDrawnAtAll() {
        var root = new Node("terrain");
        var terrain = discovering(root);
        terrain.rebuild(MapLoader.fromText(ROOM));

        terrain.applyDiscovery(new Discovery(MapLoader.fromText(ROOM)));

        assertTrue(!rockShown(root, 2, 0), "the wall is not there to be seen");
    }

    /** Where he is standing, the world is built into the picture. */
    @Test
    void whereHeStandsIsUncovered() {
        var root = new Node("terrain");
        var terrain = discovering(root);
        var grid = MapLoader.fromText(ROOM);
        terrain.rebuild(grid);

        terrain.applyDiscovery(seenFrom(grid, 25f, 25f, 20f));

        assertTrue(rockShown(root, 2, 0), "the wall beside him should be drawn");
    }

    /**
     * Walked out of and left behind: the wall stays on the map — that is the
     * memory. How dim it is drawn is the sheet's; whether it is drawn at all is
     * this class's, and the answer has to stay yes.
     */
    @Test
    void aRoomHeHasLeftKeepsItsWalls() {
        var root = new Node("terrain");
        var terrain = discovering(root);
        var grid = MapLoader.fromText(ROOM);
        terrain.rebuild(grid);
        var seen = new Discovery(grid);

        seen.reveal(java.util.List.of(unit(25f, 25f)), 0, 20f, null);
        seen.reveal(java.util.List.of(unit(500f, 500f)), 0, 20f, null); // gone off elsewhere
        settle(seen);
        terrain.applyDiscovery(seen);

        assertTrue(rockShown(root, 2, 0), "he should still know the wall is there");
    }

    /**
     * Stood at a point and looked around, with the fog given time to open.
     *
     * <p>The settling is not decoration. What is drawn follows the eased light
     * rather than the three states, so a discovery that has never been softened is
     * a map still entirely black — which is right, and is what the first frame of
     * a new floor looks like.
     */
    private static Discovery seenFrom(uz.duke.core.pathfind.PathGrid grid,
            float x, float y, float radius) {
        var seen = new Discovery(grid);
        seen.reveal(java.util.List.of(unit(x, y)), 0, radius, null);
        settle(seen);
        return seen;
    }

    private static uz.duke.game.view.UnitView unit(float x, float y) {
        return new uz.duke.game.view.UnitView(
                1, "Hero", 0, x, y, 0f, 10f, 10f, false, true, false, false, -1);
    }

    // ---- built from a modular kit ----

    /** A kit whose pieces are named nodes, so a test can see what was placed where. */
    private static final class StubTiles implements TileSource {
        @Override
        public com.jme3.scene.Spatial piece(String assetPath) {
            return new Node(assetPath);
        }
    }

    private static Tileset kit() {
        return Tileset.create().floor("floor").wall("wall").corner("corner").tileSize(4f);
    }

    /**
     * The pieces of a kind that are lying on the floor.
     *
     * <p>The lid over the rock is a floor tile as well — the same asset, laid at
     * the top of the walls — so anything counting floors has to say which it
     * means.
     */
    private static java.util.List<com.jme3.scene.Spatial> laidOnTheGround(Node root, String named) {
        return pieces(root, named).stream()
                .filter(piece -> piece.getLocalTranslation().y == 0f)
                .toList();
    }

    /** Every piece in the scene, however deeply the cell nodes nest them. */
    private static java.util.List<com.jme3.scene.Spatial> pieces(Node root, String named) {
        var found = new java.util.ArrayList<com.jme3.scene.Spatial>();
        for (var cell : root.getChildren()) {
            for (var piece : ((Node) cell).getChildren()) {
                if (piece.getName().equals(named)) {
                    found.add(piece);
                }
            }
        }
        return found;
    }

    /**
     * With a kit, the ground is tiles rather than one big plane and a block per
     * rock — and there is no plane at all, so an undiscovered cell has nothing
     * behind it to show through.
     */
    @Test
    void aTiledWorldIsBuiltOfPiecesAndHasNoGroundPlane() {
        var root = new Node("terrain");
        var terrain = new TerrainScene(root, color -> null, true, kit(), new StubTiles());

        terrain.rebuild(MapLoader.fromText(ROOM));

        assertEquals(9, laidOnTheGround(root, "floor").size(), "a floor tile per open cell");
        assertTrue(pieces(root, "wall").size() > 0, "and walls around the outside");
        assertTrue(root.getChildren().stream().noneMatch(c -> c.getName().equals("ground")),
                "the ground plane belongs to the block version");
    }

    /**
     * The rock is roofed, and the roof is up at the top of the walls.
     *
     * <p>Without it a camera looking across the room sees over the far wall into
     * an empty hole, because stone is never drawn — it is only what the walls
     * face. The lid is what makes a wall the near side of something solid.
     *
     * <p>The height is the kit's, not a guess: it is what the kit says a wall
     * stands, scaled the same way every other piece is.
     */
    @Test
    void theRockIsRoofedAtTheTopOfTheWalls() {
        var root = new Node("terrain");
        var terrain = new TerrainScene(root, color -> null, true, kit(), new StubTiles());
        var grid = MapLoader.fromText(ROOM);

        terrain.rebuild(grid);

        var lids = pieces(root, "floor").stream()
                .filter(piece -> piece.getLocalTranslation().y > 0f)
                .toList();
        assertEquals(blockedCells(ROOM), lids.size(),
                "a lid over every piece of rock, not only the ring the room touches");
        float expected = kit().getWallHeight() * (grid.getCellSize() / kit().getTileSize());
        for (var lid : lids) {
            assertEquals(expected, lid.getLocalTranslation().y, 0.001f,
                    "a lid below the wall tops is a hole with a shelf in it");
        }
    }

    /** A rebuild replaces the tiled world too, however many runs are played. */
    @Test
    void rebuildingReplacesATiledWorld() {
        var root = new Node("terrain");
        var terrain = new TerrainScene(root, color -> null, true, kit(), new StubTiles());

        terrain.rebuild(MapLoader.fromText(ROOM));
        int first = pieces(root, "floor").size();
        for (int run = 0; run < 5; run++) {
            terrain.rebuild(MapLoader.fromText(run % 2 == 0 ? SMALL : ROOM));
        }
        terrain.rebuild(MapLoader.fromText(ROOM));

        assertEquals(first, pieces(root, "floor").size(), "one world's worth, not six");
    }

    /** A kit with no corner post still builds; the notches are simply left square. */
    @Test
    void aKitWithoutEveryPieceStillBuilds() {
        var root = new Node("terrain");
        var partial = Tileset.create().floor("floor").tileSize(4f);
        var terrain = new TerrainScene(root, color -> null, true, partial, new StubTiles());

        terrain.rebuild(MapLoader.fromText(ROOM));

        assertEquals(9, pieces(root, "floor").size(),
                "the floor, and no lids: there are no walls here to roof");
        assertEquals(0, pieces(root, "wall").size(), "it never named a wall");
    }

    /** Fog works the same on tiles: unseen ground is not drawn at all. */
    @Test
    void anUndiscoveredTileIsNotDrawn() {
        var root = new Node("terrain");
        var grid = MapLoader.fromText(ROOM);
        var terrain = new TerrainScene(root, color -> null, true, kit(), new StubTiles());
        terrain.rebuild(grid);

        terrain.applyDiscovery(new Discovery(grid));

        assertTrue(everyPiece(root).allMatch(
                        piece -> piece.getCullHint() == com.jme3.scene.Spatial.CullHint.Always),
                "nobody has been anywhere, so nothing should be drawn");
    }

    /**
     * A wall at the edge of the light is still <em>drawn</em>.
     *
     * <p>The whole complaint in one assertion. Fog that switches a wall off leaves
     * a hole where a wall was — it reads as a bug in the scene rather than as
     * somewhere the player cannot see. The wall has to stay in the picture and go
     * dim, and it may only leave once the dark over it is total.
     */
    @Test
    void aWallAtTheEdgeOfTheLightGoesDimRatherThanOut() {
        var root = new Node("terrain");
        var grid = MapLoader.fromText(WIDE);
        var terrain = new TerrainScene(root, color -> null, true, kit(), new StubTiles());
        terrain.rebuild(grid);
        var seen = new Discovery(grid);

        // A tight circle at one end, so the far end of the corridor stays black.
        seen.reveal(java.util.List.of(unit(15f, 15f)), 0, 12f, null);
        settle(seen);
        terrain.applyDiscovery(seen);

        float edge = seen.lightAtPoint(35f, 15f);
        assertTrue(edge > Discovery.DARK && edge < 0.6f,
                "cell three is meant to be on the slope of the light, not off either end");
        assertTrue(drawn(root, "floor", 3, 1), "so the ground there is still drawn");
        assertTrue(drawn(root, "wall", 3, 1), "and so is the wall standing on it");
        assertTrue(!drawn(root, "floor", 7, 1), "the far end, which is black, may go");
    }

    /**
     * A piece is culled for where it <em>stands</em>, not for the cell it was
     * filed under.
     *
     * <p>Those are not the same place. A wall sits on the line between two cells
     * and a roof lies over rock that can be diagonal to the room it was built
     * with, so the cell a piece belongs to can be a cell away from the piece —
     * and a cell away, at the edge of the light, is the difference between drawn
     * and gone.
     */
    @Test
    void aPieceIsCulledForWhereItStands() {
        var root = new Node("terrain");
        var grid = MapLoader.fromText(WIDE);
        var terrain = new TerrainScene(root, color -> null, true, kit(), new StubTiles());
        terrain.rebuild(grid);
        var seen = new Discovery(grid);

        seen.reveal(java.util.List.of(unit(15f, 15f)), 0, 12f, null);
        settle(seen);
        terrain.applyDiscovery(seen);

        float cell = uz.duke.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
        for (var piece : everyPiece(root).toList()) {
            var at = piece.getLocalTranslation();
            assertEquals(seen.hiddenAt(at.x, at.z),
                    piece.getCullHint() == com.jme3.scene.Spatial.CullHint.Always,
                    "the piece at " + at.x / cell + "," + at.z / cell
                            + " should answer for its own place");
        }
    }

    /** A row of open cells long enough to run out of light half way along. */
    private static final String WIDE = """
            ##########
            #........#
            ##########
            """;

    private static java.util.stream.Stream<com.jme3.scene.Spatial> everyPiece(Node root) {
        return root.getChildren().stream()
                .flatMap(cell -> ((Node) cell).getChildren().stream());
    }

    /** Whether the named piece belonging to one cell is built into the picture. */
    private static boolean drawn(Node root, String named, int cellX, int cellY) {
        float cell = uz.duke.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
        // A wall stands on a cell's edge and a floor at its centre, so "belonging
        // to this cell" is a distance rather than a point.
        return everyPiece(root).anyMatch(piece -> piece.getName().equals(named)
                && piece.getLocalTranslation().y == 0f
                && Math.abs(piece.getLocalTranslation().x - (cellX + 0.5f) * cell) <= cell * 0.5f
                && Math.abs(piece.getLocalTranslation().z - (cellY + 0.5f) * cell) <= cell * 0.5f
                && piece.getCullHint() != com.jme3.scene.Spatial.CullHint.Always);
    }

    /** Run the fog on until it has stopped moving, so a test reads a settled floor. */
    private static void settle(Discovery seen) {
        for (int frame = 0; frame < 120; frame++) {
            seen.soften(1f / 30f);
        }
    }

    // ---- a kit whose wall is a thing rather than a surface ----

    /**
     * A corridor at the bottom, a room a storey above it, and a stair between
     * them. Read as two layers: the first says what is stone, the second how high
     * each cell stands.
     */
    private static final String TWO_STOREYS = """
            #######
            #00/11#
            #00.11#
            #######
            """;

    private static uz.duke.core.pathfind.PathGrid twoStoreys() {
        var grid = MapLoader.fromText(TWO_STOREYS);
        MapLoader.levels(grid, TWO_STOREYS);
        grid.setLevelHeight(10f);
        return grid;
    }

    private static java.util.List<com.jme3.scene.Spatial> wallsOf(Tileset kit,
            uz.duke.core.pathfind.PathGrid grid) {
        var root = new Node("terrain");
        new TerrainScene(root, color -> null, true, kit, new StubTiles()).rebuild(grid);
        return pieces(root, "wall");
    }

    /** Where a piece stands on the plan — which is what makes it one of a stack. */
    private static String spot(com.jme3.scene.Spatial piece) {
        var at = piece.getLocalTranslation();
        return Math.round(at.x * 100f) + "," + Math.round(at.z * 100f);
    }

    /**
     * Two storeys of masonry is two courses of it; two storeys of forest is one
     * bigger tree.
     *
     * <p>Stacking is right for a surface and wrong for a thing. A second tree
     * balanced on the first one's canopy, with the floor above cutting between
     * them, is what two storeys of wood looked like.
     */
    @Test
    void aKitWhoseWallGrowsPutsOnePieceWhereAStackWouldStand() {
        var grid = twoStoreys();
        var stacked = wallsOf(kit(), grid);
        var grown = wallsOf(kit().wallGrows(true), grid);

        var tall = stacked.stream()
                .collect(java.util.stream.Collectors.groupingBy(TerrainSceneTest::spot))
                .values().stream().filter(run -> run.size() == 2).findFirst()
                .orElseThrow(() -> new AssertionError("this map should wall something twice"));

        var there = grown.stream().filter(piece -> spot(piece).equals(spot(tall.get(0)))).toList();
        assertEquals(1, there.size(), "a tree does not stand on another tree's canopy");
        assertEquals(2f * tall.get(0).getLocalScale().x, there.get(0).getLocalScale().x, 0.001f,
                "and the one that stands covers what the two of them did");
        float foot = (float) tall.stream()
                .mapToDouble(piece -> piece.getLocalTranslation().y).min().orElseThrow();
        assertEquals(foot, there.get(0).getLocalTranslation().y, 0.001f,
                "grown from the foot of the run rather than from the top of it");
    }

    /**
     * Several things in a ring where one wall would stand, and not one of them on
     * the room's side of the line.
     *
     * <p>The ring is the point — one piece per cell on a square grid reads as the
     * grid — but it may not cost the player the boundary they are stopped at. What
     * leans over the floor is canopy; the trunk stays in the solid side.
     */
    @Test
    void aClumpScattersBehindTheWallLineAndNeverInFrontOfIt() {
        var grid = MapLoader.fromText(ROOM);
        float cell = grid.getCellSize();
        var root = new Node("terrain");
        new TerrainScene(root, color -> null, true,
                kit().wallClump(3).wallSpread(0.3f).wallVariety(0.5f), new StubTiles())
                .rebuild(grid);

        assertEquals(wallsOf(kit(), grid).size() * 3, pieces(root, "wall").size(),
                "three standing where one wall stood");

        int checked = 0;
        for (var cellNode : root.getChildren()) {
            var floor = ((Node) cellNode).getChildren().stream()
                    .filter(piece -> piece.getName().equals("floor"))
                    .filter(piece -> piece.getLocalTranslation().y == 0f)
                    .findFirst().orElse(null);
            if (floor == null) {
                continue; // rock, whose lid is not a floor to measure a wall against
            }
            var middle = floor.getLocalTranslation();
            for (var piece : ((Node) cellNode).getChildren()) {
                if (!piece.getName().equals("wall")) {
                    continue;
                }
                var at = piece.getLocalTranslation();
                assertTrue(Math.hypot(at.x - middle.x, at.z - middle.z) >= cell / 2f - 0.001f,
                        "a trunk on the room's side of the line is a tree you walk through");
                checked++;
            }
        }
        assertTrue(checked > 0, "the walk should have found walls to check");
    }

    /**
     * The same map grows the same wood every time it is built.
     *
     * <p>Scatter is exactly the place a {@code Math.random} creeps in, and a wood
     * that rearranged itself between one rebuild and the next is a wood nobody can
     * learn their way around.
     */
    @Test
    void theSameMapGrowsTheSameWoodTwice() {
        var grid = MapLoader.fromText(ROOM);
        var kit = kit().wallClump(3).wallSpread(0.3f).wallVariety(0.5f);

        assertEquals(placed(wallsOf(kit, grid)), placed(wallsOf(kit, grid)),
                "two builds of one map should stand the same trees in the same places");
    }

    private static java.util.List<String> placed(java.util.List<com.jme3.scene.Spatial> pieces) {
        return pieces.stream()
                .map(piece -> piece.getLocalTranslation() + " x" + piece.getLocalScale())
                .toList();
    }

    /**
     * A kit whose stair carries rails above its top step, as a real one does: four
     * deep and four high, boxing 5.1 because the newel posts at the head of the
     * flight stand a rail's height above the landing they guard.
     */
    private static final class RailedStair implements TileSource {
        @Override
        public com.jme3.scene.Spatial piece(String assetPath) {
            return new com.jme3.scene.Geometry(assetPath,
                    new com.jme3.scene.shape.Box(2f, 2.55f, 2f));
        }
    }

    /**
     * A stair is scaled by what it climbs, and what it climbs is its run — not by
     * how tall its model happens to box.
     */
    @Test
    void aStairIsScaledByWhatItClimbsRatherThanByHowTallItsModelBoxes() {
        var grid = twoStoreys();
        var root = new Node("terrain");
        new TerrainScene(root, color -> null, true, kit().stairs("stairs"), new RailedStair())
                .rebuild(grid);

        var stairs = pieces(root, "stairs");
        assertEquals(1, stairs.size(), "one flight, on the cell the map marks as a ramp");
        assertEquals(grid.getLevelHeight() / 4f, stairs.get(0).getLocalScale().y, 0.001f,
                "four model units of climb make one storey; scaling by the 5.1 the model "
                        + "boxes leaves the top step short of the floor it joins, and you can "
                        + "see through the gap");
    }
}
