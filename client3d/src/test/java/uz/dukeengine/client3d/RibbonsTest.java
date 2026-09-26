package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Vector3f;
import com.jme3.scene.VertexBuffer;
import java.nio.IntBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;

/** A stream drawn as ribbons through what rides it ({@code W3DProjectileStreamDraw}), broken at its gaps. */
class RibbonsTest {

    private static Coord3D at(float x) {
        return new Coord3D(x, 0f, 5f);
    }

    @Test
    void twoPiecesAreTwoStripsWithNoTriangleAcrossTheGap() {
        var mesh = Ribbons.mesh(List.of(List.of(at(0f), at(10f)), List.of(at(30f), at(40f), at(50f))), 2f,
                new Vector3f(25f, 100f, 0f));
        assertEquals(10, mesh.getVertexCount(), "two corners a place");
        var indices = (IntBuffer) mesh.getBuffer(VertexBuffer.Type.Index).getData();
        assertEquals(6 * 3, indices.limit(), "a quad between each two places of a piece, none between the pieces");
        for (int triangle = 0; triangle < indices.limit(); triangle += 3) {
            boolean first = indices.get(triangle) < 4;
            assertTrue(first == indices.get(triangle + 1) < 4 && first == indices.get(triangle + 2) < 4,
                    "a triangle within one piece");
        }
    }

    @Test
    void aStripIsItsWidthAcross() {
        var mesh = Ribbons.mesh(List.of(List.of(at(0f), at(10f))), 2f, new Vector3f(5f, 100f, 5f));
        var corners = mesh.getFloatBuffer(VertexBuffer.Type.Position);
        var one = new Vector3f(corners.get(0), corners.get(1), corners.get(2));
        var other = new Vector3f(corners.get(3), corners.get(4), corners.get(5));
        assertEquals(2f, one.distance(other), 1e-4f);
    }
}
