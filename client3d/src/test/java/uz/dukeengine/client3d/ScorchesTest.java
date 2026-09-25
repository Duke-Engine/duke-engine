package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.material.Material;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.pathfind.HeightMap;

/** Marks burnt into the ground, laid on its rise and fall as the reference lays every scorch, and held to its 500. */
class ScorchesTest {

    /** A ridge running along the map's y at x = 100, falling a half a unit across; cells of 10, forty a side. */
    private static final DrawnGround RIDGE = new DrawnGround() {
        @Override
        public float cellSize() {
            return 10f;
        }

        @Override
        public int columns() {
            return 40;
        }

        @Override
        public int rows() {
            return 40;
        }

        @Override
        public float heightAt(float x, float z) {
            return Math.max(0f, 30f - Math.abs(x - 100f) * 0.5f);
        }

        @Override
        public HeightMap.Diagonal diagonal(int cx, int cy) {
            return cx == 10 && cy == 10 ? HeightMap.Diagonal.ANTI : HeightMap.Diagonal.MAIN;
        }
    };

    private static Scorches scorches(Node node, DrawnGround ground) {
        return new Scorches(node, () -> ground, picture -> new Material());
    }

    @Test
    void aMarkLaidAcrossARidgeLiesJustOverTheGroundAtEveryCornerUnderIt() {
        var node = new Node("marks");
        var marks = scorches(node, RIDGE);

        marks.lay(100f, 100f, 60f, "textures/scorch.png");

        var positions = marks.newest().getMesh().getFloatBuffer(VertexBuffer.Type.Position);
        var heights = new HashSet<Float>();
        for (int corner = 0; corner < positions.limit() / 3; corner++) {
            float x = positions.get(corner * 3);
            float y = positions.get(corner * 3 + 1);
            float z = positions.get(corner * 3 + 2);
            assertEquals(RIDGE.heightAt(x, z) + 0.0625f, y, 1e-5f, "at " + x + ", " + z);
            assertEquals(0f, x % 10f, 1e-5f, "on the ground's corners");
            heights.add(y);
        }
        assertTrue(heights.size() > 1, "over the ridge, not flat");
        assertEquals(13 * 13, positions.limit() / 3, "from the cell under its near edge to one past its far edge");
    }

    @Test
    void aMarkFollowsTheCutTheGroundIsDrawnAlong() {
        var marks = scorches(new Node("marks"), RIDGE);
        marks.lay(105f, 105f, 5f, "textures/scorch.png");

        // The corners of the one cell it lies in, (10, 10) to (11, 11): 0 and 1 along its near edge, 2 and 3 its far.
        var indices = marks.newest().getMesh().getIndexBuffer();
        assertEquals(0, indices.get(0));
        assertEquals(2, indices.get(1));
        assertEquals(1, indices.get(2), "cut from its second corner to its third, as that cell is drawn");
    }

    @Test
    void theFiveHundredAndFirstMarkLetsTheFirstGo() {
        var node = new Node("marks");
        var marks = scorches(node, DrawnGround.NONE);
        marks.lay(0f, 0f, 10f, "textures/scorch.png");
        var first = marks.newest();

        for (int mark = 1; mark <= 500; mark++) {
            marks.lay(mark * 100f, 0f, 10f, "textures/scorch.png");
        }

        assertEquals(Scorches.MOST, marks.count());
        assertNull(first.getParent(), "the oldest let go");
    }
}
