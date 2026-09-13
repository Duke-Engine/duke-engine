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
                1, "Rogue", 0, x, y, 0f, 10f, 10f, false, true, false, false, -1);
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

    /**
     * A corridor at the bottom and a room a storey above it, with a single cell of
     * rock between them. Nothing connects the two, which is not the point: the
     * point is the piece of rock, walled from both sides and roofed a storey up.
     */
    private static final String WALL_BETWEEN = """
            #######
            #00#11#
            #00#11#
            #######
            """;

    /** The pieces standing on one cell, whichever cell node they were filed under. */
    private static java.util.List<com.jme3.scene.Spatial> onCell(
            java.util.List<com.jme3.scene.Spatial> pieces, int cellX, int cellY, float cell) {
        return pieces.stream().filter(piece -> {
            var at = piece.getLocalTranslation();
            return Math.abs(at.x - (cellX + 0.5f) * cell) <= cell / 2f + 0.001f
                    && Math.abs(at.z - (cellY + 0.5f) * cell) <= cell / 2f + 0.001f;
        }).toList();
    }

    /**
     * Masonry draws every face of a piece of rock; a thing is drawn once, however
     * many sides it can be seen from.
     *
     * <p>This is the whole bug. One cell of rock between a corridor and a room a
     * storey above it is walled from the corridor — twice over, once per storey of
     * the drop — and again from the room on top. Three faces, which for stone is
     * three surfaces you could walk up to. For a wood it was the same tree drawn
     * three times: one at the foot of the rock, one halfway up it, and one on the
     * roof with the floor between them.
     */
    @Test
    void aKitThatFillsRockDrawsOneBodyWhereMasonryDrawsEveryFace() {
        var grid = MapLoader.fromText(WALL_BETWEEN);
        MapLoader.levels(grid, WALL_BETWEEN);
        grid.setLevelHeight(10f);
        float cell = grid.getCellSize();

        var faces = onCell(wallsOf(kit(), grid), 3, 1, cell);
        var body = onCell(wallsOf(kit().wallFillsRock(true), grid), 3, 1, cell);

        assertEquals(3, faces.size(),
                "masonry walls the rock from both sides and again on its roof");
        assertEquals(1, body.size(), "a tree has one body, however many sides it is seen from");
        assertEquals((3 + 0.5f) * cell, body.get(0).getLocalTranslation().x, 0.001f,
                "in the middle of the rock rather than on one of its faces");
    }

    /**
     * And it stands on top of the rock, at its own size, whatever the rock is.
     *
     * <p>This used to say the opposite — the body grew from the lowest floor
     * beside the rock and was scaled by however many storeys that spanned, so two
     * storeys of rock was one tree twice the size all round. It was wrong twice.
     *
     * <p>A thing cannot be made taller without being made wider, so a tree
     * standing for a two-storey rock came out twice as broad as the room next to
     * it, which is not what a tall wood looks like. And <em>where no floor beside
     * the rock was on the ground</em> — the rock ringing a room two storeys up,
     * which is most of the rock on an upper floor — the lowest face was up there
     * too. So the tree was drawn at the room's own height with nothing whatever
     * underneath it: a wood hanging in the air, which is what the first player to
     * see this reported.
     *
     * <p>What holds it up is {@link TileLayout.Piece#BASE}, asked for below.
     */
    @Test
    void andItStandsOnTopOfTheRockAtItsOwnSize() {
        var grid = MapLoader.fromText(WALL_BETWEEN);
        MapLoader.levels(grid, WALL_BETWEEN);
        grid.setLevelHeight(10f);
        float cell = grid.getCellSize();

        var faces = onCell(wallsOf(kit(), grid), 3, 1, cell);
        var body = onCell(wallsOf(kit().wallFillsRock(true), grid), 3, 1, cell).get(0);

        assertEquals(10f, body.getLocalTranslation().y, 0.001f,
                "on the rock's own roof — the rock beside it rises a storey, so the roof does");
        assertEquals(faces.get(0).getLocalScale().x, body.getLocalScale().x, 0.001f,
                "and the same size it would be anywhere: a tree is a tree, not a storey count");
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
     * A kit whose stair is shaped like a real one: four deep and four high, boxing
     * 5.1 because the newel posts at the head of the flight stand a rail above the
     * landing they guard — and with its origin at the foot of the bottom step
     * rather than in the middle, which is where KayKit puts it.
     */
    private static final class RailedStair implements TileSource {
        @Override
        public com.jme3.scene.Spatial piece(String assetPath) {
            return new com.jme3.scene.Geometry(assetPath, new com.jme3.scene.shape.Box(
                    new com.jme3.math.Vector3f(0f, 2.55f, 2f), 2f, 2.55f, 2f));
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

    /**
     * And it stands on the cell the map marks as a ramp, all of it.
     *
     * <p>Where a kit puts a stair's origin is its own business: the middle of the
     * flight, or the foot of the bottom step. Laid by its origin, the second sort
     * covers half its own cell and half of the next — hanging over the drop it was
     * meant to join, with a cell of nothing under one end.
     */
    @Test
    void aStairStandsOnItsOwnCellHoweverItsKitPutItsOrigin() {
        var grid = twoStoreys();
        float cell = grid.getCellSize();
        var root = new Node("terrain");
        new TerrainScene(root, color -> null, true, kit().stairs("stairs"), new RailedStair())
                .rebuild(grid);
        root.updateGeometricState();

        var stair = pieces(root, "stairs").get(0);
        var footprint = (com.jme3.bounding.BoundingBox) stair.getWorldBound();
        // Cell (3,1) is the ramp in this map.
        assertEquals((3 + 0.5f) * cell, footprint.getCenter().x, 0.01f,
                "half a cell out is a flight hanging over the drop it should join");
        assertEquals((1 + 0.5f) * cell, footprint.getCenter().z, 0.01f,
                "and the same across the corridor");
    }

    // ---- telling one surface from another ----

    /** A stub that writes down what colour each piece was asked for. */
    private static final class ColourWatchingTiles implements TileSource {
        private final java.util.Map<String, java.util.Set<Integer>> asked =
                new java.util.HashMap<>();

        @Override
        public com.jme3.scene.Spatial piece(String assetPath) {
            return piece(assetPath, 0xFFFFFF);
        }

        @Override
        public com.jme3.scene.Spatial piece(String assetPath, int packedRgb) {
            asked.computeIfAbsent(assetPath, ignored -> new java.util.HashSet<>()).add(packedRgb);
            return new Node(assetPath);
        }
    }

    /**
     * The lid over the rock is asked for in a different colour from the floor.
     *
     * <p>They are the same asset — the client roofs stone with a floor tile — and
     * that is exactly the trouble: the same model, the same texture, and a normal
     * pointing the same way, so one sun shades them the same and a player cannot
     * see which of the two he is allowed to walk on. Nothing about the light can
     * fix it, so a colour does.
     *
     * <p>Asked of what the scene <em>requests</em> rather than of what comes back,
     * because the colour is not a property of the piece: it goes into which skin
     * the source hands out, and a source free to ignore it is the one thing that
     * would make this setting quietly do nothing.
     */
    @Test
    void theLidOverTheRockIsAskedForInADifferentColourFromTheFloor() {
        var watching = new ColourWatchingTiles();
        new TerrainScene(new Node("terrain"), color -> null, true,
                kit().capTint(0x808080), watching).rebuild(MapLoader.fromText(ROOM));

        assertEquals(java.util.Set.of(0xFFFFFF, 0x808080), watching.asked.get("floor"),
                "the floor plain, and the same tile tinted where it roofs the rock");
    }

    /** And each storey up is asked for in a colour of its own. */
    @Test
    void andEachStoreyIsAskedForInAColourOfItsOwn() {
        var grid = twoStoreys();
        var watching = new ColourWatchingTiles();
        new TerrainScene(new Node("terrain"), color -> null, true,
                kit().storeyShade(1.2f), watching).rebuild(grid);

        assertTrue(watching.asked.get("floor").size() > 1,
                "a raised room is the same tiles higher up, and looking down a slope that is"
                        + " nothing to go on — every storey came back " + watching.asked);
    }

    /** A kit that asks for neither gets exactly the one colour it always got. */
    @Test
    void andAKitThatAsksForNeitherIsDrawnInOneColourAsBefore() {
        var watching = new ColourWatchingTiles();
        new TerrainScene(new Node("terrain"), color -> null, true, kit(), watching)
                .rebuild(twoStoreys());

        for (var entry : watching.asked.entrySet()) {
            assertEquals(java.util.Set.of(0xFFFFFF), entry.getValue(),
                    entry.getKey() + " gained a colour nobody asked for");
        }
    }


    // ---- the side of a raised block of rock ----

    /**
     * A corridor on the ground, a room a storey up and a room two storeys up —
     * which is as tall as the generator builds, and the shape the fault showed in.
     */
    private static final String TOWER = """
            ##########
            #000#1111#
            #000#1111#
            #000#11#2#
            #000#11#2#
            ##########
            """;

    /**
     * A kit shaped like the wood: its wall fills rock rather than facing it, and
     * its lids lie at the floor's own height. Both are the forest theme's own
     * settings and both matter here — the second is why a rock face is drawn only
     * where the rock actually stands above something.
     */
    private static Tileset forestLike() {
        return kit().wallFillsRock(true).wallHeight(0f);
    }

    /**
     * A stub whose rock face has a body, since a face is scaled by what it
     * measures. Shaped like the real one: four units square, one deep, standing on
     * its own origin — so a storey of ten scales it by 2.5 and it comes out a cell
     * wide, as a modular wall does.
     */
    private static final class StubTilesWithFace implements TileSource {
        @Override
        public com.jme3.scene.Spatial piece(String assetPath) {
            if (!assetPath.equals("face")) {
                return new Node(assetPath);
            }
            var node = new Node(assetPath);
            var slab = new com.jme3.scene.Geometry("slab",
                    new com.jme3.scene.shape.Box(2f, 2f, 0.5f));
            slab.setLocalTranslation(0f, 2f, 0f);
            node.attachChild(slab);
            node.updateModelBound();
            node.updateGeometricState();
            return node;
        }
    }

    private static java.util.List<com.jme3.scene.Spatial> facesOf(Tileset of) {
        var grid = MapLoader.fromText(TOWER);
        MapLoader.levels(grid, TOWER);
        grid.setLevelHeight(10f);
        var root = new Node("terrain");
        new TerrainScene(root, color -> null, true, of, new StubTilesWithFace()).rebuild(grid);
        return pieces(root, "face");
    }

    /**
     * A kit whose wall is a thing still draws the sides of its rock.
     *
     * <p>The fault the first player reported, in the terms he saw it: trees
     * hanging in the air over the upper floors. The layout was never wrong — it
     * names a face per storey, all the way down, and for masonry those faces
     * <em>are</em> the sides of the block. What went missing is that the
     * rearrangement which turns faces into one body for a wood was throwing them
     * away, so nothing whatever drew the rock and the tree on top stood on air.
     *
     * <p>Two tests hold the picture together and neither does it alone.
     * {@link NoGapsTest} says the <b>layout</b> leaves no step uncovered; this one
     * says the <b>renderer</b> still draws what the layout named. Under the fault
     * this came back empty.
     */
    @Test
    void aKitWhoseWallIsAThingStillDrawsTheSidesOfItsRock() {
        var feet = facesOf(forestLike().rockFace("face")).stream()
                .map(piece -> Math.round(piece.getLocalTranslation().y / 10f))
                .distinct().sorted().toList();

        assertEquals(java.util.List.of(0, 1), feet,
                "a block roofed two storeys up is faced at both of the storeys below it");
    }

    /**
     * And not where the rock is level with the floor beside it.
     *
     * <p>Out of doors nearly every block of rock is at the floor's own height —
     * what you cannot walk into is a tree line, not a wall — so facing those too
     * would run a stone kerb round the whole forest.
     *
     * <p>Six, and which six is the point: the corridor on this map is three cells
     * wide by four deep and only its <em>east</em> side has anything raised behind
     * it — the column of rock holding up the room a storey above. That column runs
     * on through the border rock at both ends of the corridor, where it stands a
     * storey above the tree line beside it, and that step is a face as well: four
     * along the corridor and one at each end of it. (It was four while the step
     * between two lids of rock was never drawn, which was the fault.) The other
     * three sides are rock lying at the corridor's own height, which is a wood, and
     * they get nothing; nor does the outside of the map. The count is a stand-in for
     * that sentence, so it is worth checking against the map rather than against the
     * last run.
     */
    @Test
    void andNotWhereTheRockIsLevelWithTheFloorBesideIt() {
        var onTheGround = facesOf(forestLike().rockFace("face")).stream()
                .filter(piece -> piece.getLocalTranslation().y < 0.001f)
                .toList();

        assertEquals(6, onTheGround.size(), "the corridor's east side and one step at each"
                + " end of it, and no kerb round the rest of it — got " + onTheGround.size());
        for (var face : onTheGround) {
            assertEquals(4f * 10f + 1.25f, face.getLocalTranslation().x, 0.01f,
                    "all six on the boundary with the rock that holds the upper room up,"
                            + " and set INTO the rock by half the slab, so the face itself"
                            + " lands on the line the player is stopped at");
        }
    }

    /**
     * A corridor on the ground, a room two storeys up, and two columns of rock
     * between them: the one beside the corridor lies at the corridor's height — a
     * tree line — and the one beside the room is roofed with the room.
     */
    private static final String ROCK_STEP = """
            #########
            #00##222#
            #00##222#
            #00##222#
            #########
            """;

    /**
     * The step between two lids of rock is drawn, as well as the step between a
     * floor and the rock beside it.
     *
     * <p>The fault as it was seen: a row of trees at the foot of a raised block,
     * and above them the block's side open onto the dark — while the same block
     * beside an empty floor was faced as it should be. The layout names that step a
     * ledge and always did. The renderer asked how high a ledge reaches of the rock
     * it faces, which is the lower lid and its own foot, so it was never above
     * anything and nothing was drawn.
     */
    @Test
    void theStepBetweenTwoLidsOfRockIsDrawnToo() {
        var grid = MapLoader.fromText(ROCK_STEP);
        MapLoader.levels(grid, ROCK_STEP);
        grid.setLevelHeight(10f);
        var root = new Node("terrain");
        new TerrainScene(root, color -> null, true, forestLike().rockFace("face"),
                new StubTilesWithFace()).rebuild(grid);
        float cell = grid.getCellSize();

        var step = pieces(root, "face").stream()
                .filter(piece -> Math.abs(piece.getLocalTranslation().x - (4f * cell + 1.25f)) < 0.01f)
                .filter(piece -> piece.getLocalTranslation().z > cell
                        && piece.getLocalTranslation().z < 4f * cell)
                .toList();
        var feet = step.stream()
                .map(piece -> Math.round(piece.getLocalTranslation().y / 10f))
                .distinct().sorted().toList();

        assertEquals(java.util.List.of(0, 1), feet,
                "the taller rock's side, from the tree line's lid up to its own, a storey at a time");
        assertEquals(6, step.size(), "three rows of the step, faced at both storeys it stands"
                + " -- got " + step.size());
    }

    /** A kit that names no rock face is drawn exactly as it was. */
    @Test
    void andAKitThatNamesNoRockFaceDrawsNone() {
        assertEquals(java.util.List.of(), facesOf(forestLike()));
    }
}
