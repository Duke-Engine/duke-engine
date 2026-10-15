package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jme3.scene.VertexBuffer;
import java.nio.FloatBuffer;
import org.junit.jupiter.api.Test;

/** Ground past the edge: a ring of cells round the map, its edge's heights carried out; and a sky's pictures counted. */
class BorderRingTest {

    /** A 4 by 3 map of 10-unit cells, rising to the east: height x / 10. */
    private static final DrawnGround SLOPE = new DrawnGround() {
        @Override
        public float cellSize() {
            return 10f;
        }

        @Override
        public int columns() {
            return 4;
        }

        @Override
        public int rows() {
            return 3;
        }

        @Override
        public float heightAt(float x, float z) {
            return x / 10f;
        }

        @Override
        public uz.dukeengine.core.pathfind.HeightMap.Diagonal diagonal(int cx, int cy) {
            return null;
        }
    };

    @Test
    void aRingOfCellsCarriesTheEdgeOutward() {
        var mesh = BorderRing.mesh(SLOPE, 2);
        int quads = (4 + 4) * (3 + 4) - 4 * 3;
        assertEquals(quads * 4, mesh.getVertexCount());
        var positions = (FloatBuffer) mesh.getBuffer(VertexBuffer.Type.Position).getData();
        float highest = -1f;
        float lowest = 99f;
        for (int i = 0; i < positions.limit(); i += 3) {
            highest = Math.max(highest, positions.get(i + 1));
            lowest = Math.min(lowest, positions.get(i + 1));
        }
        assertEquals(4f, highest, "east of the map, the east edge's height");
        assertEquals(0f, lowest, "west of it, the west edge's");
    }

    @Test
    void aSkyIsOneCubePictureOrSix() {
        assertThrows(IllegalArgumentException.class, () -> Visuals.create().skybox("a.png", "b.png"));
        assertEquals(6, Visuals.create().skybox("w", "e", "n", "s", "u", "d").getSkybox().size());
    }
}
