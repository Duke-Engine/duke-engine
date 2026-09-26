package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.material.Material;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.HeightMap;
import uz.dukeengine.game.DukeGame;

/** Pieces of pictures laid along the ground as the reference lays its roads: over its rise and fall, in layers. */
class StripsTest {

    /** Ground rising a fifth of a unit a unit along x, in cells of 10, fifty a side. */
    private static final DrawnGround SLOPE = ground((x, z) -> x * 0.2f);

    private static DrawnGround ground(java.util.function.BiFunction<Float, Float, Float> height) {
        return new DrawnGround() {
            @Override
            public float cellSize() {
                return 10f;
            }

            @Override
            public int columns() {
                return 50;
            }

            @Override
            public int rows() {
                return 50;
            }

            @Override
            public float heightAt(float x, float z) {
                return height.apply(x, z);
            }

            @Override
            public HeightMap.Diagonal diagonal(int cx, int cy) {
                return HeightMap.Diagonal.MAIN;
            }
        };
    }

    /** A road from x = {@code from} to {@code to} along z = 100, {@code width} across. */
    private static DukeGame.StripPiece road(float from, float to, float width, int layer) {
        float half = width / 2f;
        return new DukeGame.StripPiece("textures/roads/asphalt.png", 0f, 0.1f, 1f, 0.2f,
                new Coord3D(from, 100f - half, 0f), new Coord3D(to, 100f - half, 0f),
                new Coord3D(to, 100f + half, 0f), new Coord3D(from, 100f + half, 0f), layer);
    }

    @Test
    void aStripOverASlopeLiesJustOverTheHighestGroundAcrossEachColumn() {
        var mesh = Strips.mesh(SLOPE, List.of(road(50f, 350f, 35f, 0)));
        var positions = mesh.getFloatBuffer(VertexBuffer.Type.Position);
        int vertices = positions.limit() / 3;
        assertTrue(vertices >= 4 && vertices % 2 == 0, "two edges a column: " + vertices);
        for (int corner = 0; corner < vertices; corner += 2) {
            float x = positions.get(corner * 3);
            float y = positions.get(corner * 3 + 1);
            assertEquals(y, positions.get(corner * 3 + 4), 1e-5f, "flat across");
            float highest = Math.max(SLOPE.highestCorner(x, 82.5f), SLOPE.highestCorner(x, 117.5f));
            assertEquals(highest + 0.078f, y, 1e-4f, "0.078 over the highest corner under the column at " + x);
        }
        assertEquals(50f, positions.get(0), 1e-4f, "from its start");
        assertEquals(350f, positions.get((vertices - 2) * 3), 1e-4f, "to its end");
    }

    @Test
    void overFlatGroundItsMiddleColumnsAreLeftOut() {
        var mesh = Strips.mesh(ground((x, z) -> 3f), List.of(road(0f, 300f, 35f, 0)));

        assertEquals(4, mesh.getVertexCount(), "the line from its first column to its last lies on every other");
        assertEquals(2, mesh.getTriangleCount());
    }

    @Test
    void aLaterKindIsDrawnOverAnEarlierOneWhereTheyCross() {
        var node = new Node("strips");
        var strips = new Strips(node, () -> SLOPE, picture -> new Material());
        var dirt = road(0f, 300f, 20f, 0);
        var crossing = new DukeGame.StripPiece("textures/roads/crosswalk.png", 0f, 0f, 1f, 1f,
                new Coord3D(140f, 50f, 0f), new Coord3D(140f, 150f, 0f), new Coord3D(160f, 150f, 0f),
                new Coord3D(160f, 50f, 0f), 1);

        strips.lay(List.of(crossing, dirt));

        var laid = strips.laid();
        assertEquals(2, laid.size());
        int crossingLayer = laid.getFirst().getUserData(OverlayOrder.LAYER);
        int dirtLayer = laid.get(1).getUserData(OverlayOrder.LAYER);
        assertTrue(crossingLayer > dirtLayer, "the later kind over the earlier, whatever order they came in");
        assertTrue(dirtLayer >= Strips.ABOVE_OVERLAYS, "and both over the ground's overlays");
    }

    // ---- a straight mapping of the ground ----

    /** Every laid point's u and v, by where it lies across the map. */
    private static void eachPoint(com.jme3.scene.Mesh mesh, java.util.function.BiConsumer<float[], float[]> told) {
        var at = mesh.getFloatBuffer(com.jme3.scene.VertexBuffer.Type.Position);
        var uv = mesh.getFloatBuffer(com.jme3.scene.VertexBuffer.Type.TexCoord);
        for (int point = 0; point < mesh.getVertexCount(); point++) {
            told.accept(new float[] {at.get(point * 3), at.get(point * 3 + 2)},
                    new float[] {uv.get(point * 2), uv.get(point * 2 + 1)});
        }
    }

    @Test
    void aTrapezoidWithAMappingDrawsEachLaidPointAtTheMappingsUAndV() {
        var mapping = new DukeGame.StripPiece.Mapping(0.25f, 0.01f, 0.002f, 0.5f, -0.003f, 0.02f);
        var trapezoid = new DukeGame.StripPiece("textures/roads/asphalt.png", 0f, 0f, 1f, 1f,
                new Coord3D(100f, 100f, 0f), new Coord3D(200f, 100f, 0f), new Coord3D(180f, 130f, 0f),
                new Coord3D(110f, 130f, 0f), 0, mapping);
        eachPoint(Strips.mesh(ground((x, z) -> 0f), List.of(trapezoid)), (where, uv) -> {
            assertEquals(mapping.u(where[0], where[1]), uv[0], 1e-4f);
            assertEquals(mapping.v(where[0], where[1]), uv[1], 1e-4f);
        });
    }

    @Test
    void aRectangleGivenTheEquivalentMappingDrawsAsItsRectangleFormDoes() {
        var rectangle = road(50f, 350f, 30f, 0); // u 0 to 1 along 300, v 0.1 to 0.2 across 30 from z 85
        var mapped = new DukeGame.StripPiece(rectangle.picture(), 0f, 0f, 0f, 0f, rectangle.a(), rectangle.b(),
                rectangle.c(), rectangle.d(), 0, new DukeGame.StripPiece.Mapping(-50f / 300f, 1f / 300f, 0f,
                        0.1f - 85f * 0.1f / 30f, 0f, 0.1f / 30f));
        var plain = Strips.mesh(SLOPE, List.of(rectangle)).getFloatBuffer(com.jme3.scene.VertexBuffer.Type.TexCoord);
        var laid = Strips.mesh(SLOPE, List.of(mapped)).getFloatBuffer(com.jme3.scene.VertexBuffer.Type.TexCoord);
        assertEquals(plain.limit(), laid.limit());
        for (int at = 0; at < plain.limit(); at++) {
            assertEquals(plain.get(at), laid.get(at), 1e-4f);
        }
    }
}
