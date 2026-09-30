package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.pathfind.PathGrid;

/**
 * A kit's floor built a chunk at a time round where the camera looks is, chunk for chunk, the floor built whole — every
 * corner of every piece where the whole build put it, rocks turned by faces in the chunk next door included — and the
 * chunks far behind are let go.
 */
class StreamedGroundTest {

    /** Pieces with a mesh in them, a flat square tile, so a chunk has corners to compare. */
    private static final class SquareTiles implements TileSource {
        @Override
        public com.jme3.scene.Spatial piece(String assetPath) {
            var node = new Node(assetPath);
            var tile = new Geometry("tile", new com.jme3.scene.shape.Quad(4f, 4f));
            tile.rotate(-com.jme3.math.FastMath.HALF_PI, 0f, 0f);
            node.attachChild(tile);
            return node;
        }
    }

    /** Rooms cut out of rock, storeys and all. */
    private static PathGrid rooms() {
        var random = new SplittableRandom(61);
        var grid = new PathGrid(90, 70);
        for (int cy = 0; cy < grid.getHeight(); cy++) {
            for (int cx = 0; cx < grid.getWidth(); cx++) {
                grid.setBlocked(cx, cy, true);
            }
        }
        for (int room = 0; room < 40; room++) {
            int x = random.nextInt(grid.getWidth() - 12);
            int y = random.nextInt(grid.getHeight() - 10);
            int wide = 3 + random.nextInt(9);
            int deep = 3 + random.nextInt(7);
            int storey = random.nextInt(4) == 0 ? 1 : 0;
            for (int cy = y; cy < y + deep; cy++) {
                for (int cx = x; cx < x + wide; cx++) {
                    grid.setBlocked(cx, cy, false);
                    grid.setLevel(cx, cy, storey);
                }
            }
        }
        grid.setLevelHeight(10f);
        return grid;
    }

    /** Every corner of every piece under {@code chunk}, in world space, in order. */
    private static List<Vector3f> corners(Node chunk) {
        com.jme3.scene.Spatial top = chunk;
        while (top.getParent() != null) {
            top = top.getParent();
        }
        top.updateGeometricState(); // from the root down, as a scene is updated
        var corners = new ArrayList<Vector3f>();
        for (var geometry : chunk.descendantMatches(Geometry.class)) {
            var positions = geometry.getMesh().getFloatBuffer(VertexBuffer.Type.Position);
            for (int i = 0; i + 2 < positions.limit(); i += 3) {
                var local = new Vector3f(positions.get(i), positions.get(i + 1), positions.get(i + 2));
                corners.add(geometry.getWorldTransform().transformVector(local, new Vector3f()));
            }
        }
        return corners;
    }

    /** The chunk node standing at chunk ({@code x}, {@code y}) of {@code root}, by where its first corner is. */
    private static Node chunkAt(Node root, int x, int y, float cell) {
        for (var child : root.getChildren()) {
            if (!(child instanceof Node node) || corners(node).isEmpty()) {
                continue;
            }
            var first = corners(node).get(0);
            int cx = (int) Math.floor(first.x / cell);
            int cy = (int) Math.floor(first.z / cell);
            // a chunk's first piece stands in its first cells, or at their edge
            if (Math.floorDiv(Math.max(0, cx), TerrainScene.CHUNK_CELLS) == x
                    && Math.floorDiv(Math.max(0, cy), TerrainScene.CHUNK_CELLS) == y) {
                return node;
            }
        }
        return null;
    }

    private static void assertSameFloor(Tileset kit) {
        var grid = rooms();
        float cell = grid.getCellSize();
        var wholeRoot = new Node("whole");
        new TerrainScene(wholeRoot, (colour, texture) -> null, true, kit, new SquareTiles(), true).rebuild(grid);
        var streamedRoot = new Node("streamed");
        var streamed = new TerrainScene(streamedRoot, (colour, texture) -> null, true, kit, new SquareTiles(), true);
        streamed.streamWithin(2 * TerrainScene.CHUNK_CELLS);
        streamed.rebuild(grid);
        assertEquals(0, streamedRoot.getQuantity(), "nothing built until the camera looks somewhere");

        var looked = new float[][] {{150f, 120f}, {600f, 400f}, {880f, 680f}};
        for (var at : looked) {
            for (int frame = 0; frame < 40; frame++) {
                streamed.stream(at[0], at[1]);
            }
            int centreX = (int) (at[0] / cell) / TerrainScene.CHUNK_CELLS;
            int centreY = (int) (at[1] / cell) / TerrainScene.CHUNK_CELLS;
            for (int y = Math.max(0, centreY - 2); y <= Math.min(4, centreY + 2); y++) {
                for (int x = Math.max(0, centreX - 2); x <= Math.min(5, centreX + 2); x++) {
                    assertTrue(streamed.isBuilt(x, y), "chunk " + x + "," + y + " within reach is built");
                    var whole = chunkAt(wholeRoot, x, y, cell);
                    var built = chunkAt(streamedRoot, x, y, cell);
                    var expected = whole == null ? List.<Vector3f>of() : corners(whole);
                    var actual = built == null ? List.<Vector3f>of() : corners(built);
                    assertEquals(expected.size(), actual.size(), "chunk " + x + "," + y + ": as many corners");
                    for (int i = 0; i < expected.size(); i++) {
                        assertTrue(expected.get(i).distance(actual.get(i)) < 1e-3f,
                                "chunk " + x + "," + y + " corner " + i + ": " + expected.get(i) + " / " + actual.get(i));
                    }
                }
            }
        }
        assertFalse(streamed.isBuilt(0, 0), "the first corner, far behind now, let go");
    }

    @Test
    void aMasonryFloorBuiltAChunkAtATimeIsTheFloorBuiltWhole() {
        assertSameFloor(Tileset.create().floor("floor").wall("wall").corner("corner").tileSize(4f));
    }

    @Test
    void aWoodWhoseRockIsTurnedByItsFacesIsTheWoodBuiltWhole() {
        assertSameFloor(Tileset.create().floor("floor").wall("tree").tileSize(4f).wallFillsRock(true));
    }
}
