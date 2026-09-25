package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.scene.VertexBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.map.MapArea;
import uz.dukeengine.core.pathfind.HeightMap;

/** A map's water as the reference draws it: standing at its height over its outline, clear at the shore; rivers. */
class WaterTest {

    private record Area(String name, boolean water, float height, List<Float> points, boolean river, int riverStart)
            implements MapArea {
    }

    /** Ground of cells of 10, forty a side, as high as {@code height} says. */
    private static DrawnGround ground(java.util.function.BiFunction<Float, Float, Float> height) {
        return new DrawnGround() {
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
                return height.apply(x, z);
            }

            @Override
            public HeightMap.Diagonal diagonal(int cx, int cy) {
                return HeightMap.Diagonal.MAIN;
            }
        };
    }

    /** A pond from cell 2 to cell 5 each way, its surface 8 steps — 5 units — up. */
    private static final Area POND = new Area("Pond", true, 8f, List.of(2f, 2f, 5f, 2f, 5f, 5f, 2f, 5f), false, 0);

    @Test
    void aSquarePondFiveOverAFlatFloorDrawsASurfaceAtFiveOverTheSquareOnly() {
        var mesh = Water.standing(POND, ground((x, z) -> 0f), 3f);
        var positions = mesh.getFloatBuffer(VertexBuffer.Type.Position);

        assertEquals(3 * 3 * 4, mesh.getVertexCount(), "the nine cells inside it");
        for (int corner = 0; corner < mesh.getVertexCount(); corner++) {
            float x = positions.get(corner * 3);
            float y = positions.get(corner * 3 + 1);
            float z = positions.get(corner * 3 + 2);
            assertEquals(5f, y, 1e-5f, "at its surface");
            assertTrue(x >= 20f && x <= 50f && z >= 20f && z <= 50f, "over the square only: " + x + ", " + z);
        }
    }

    @Test
    void overGroundOneAndAHalfUnderTheSurfaceItIsHalfClearWithAFadeDepthOfThree() {
        var mesh = Water.standing(POND, ground((x, z) -> 3.5f), 3f);
        var colours = mesh.getFloatBuffer(VertexBuffer.Type.Color);

        assertEquals(0.5f, colours.get(3), 1e-5f, "1.5 deep of a fade of 3: half");
        assertEquals(1f, Water.standing(POND, ground((x, z) -> -10f), 3f)
                .getFloatBuffer(VertexBuffer.Type.Color).get(3), 1e-5f, "past the fade depth: whole");
    }

    @Test
    void aRiversPictureRunsDownItFromItsStartAsTimePasses() {
        // Six corners: its mouth from corner 0 to 1, one bank 1, 2, … the other back 0, 5, 4.
        var river = new Area("River", true, 0f, List.of(0f, 0f, 0f, 2f, 10f, 2f, 20f, 2f, 20f, 0f, 10f, 0f), true, 0);
        var mesh = Water.river(river, ground((x, z) -> -3f));
        var uvs = mesh.getFloatBuffer(VertexBuffer.Type.TexCoord);
        assertEquals(6, mesh.getVertexCount(), "three pairs across it");
        assertEquals(0f, uvs.get(1), 1e-5f, "its picture from its start");
        assertTrue(uvs.get(9) > uvs.get(1), "and on down it");

        var look = new Visuals.WaterLook("textures/water/river.png", 150f, 0f, 0f, null, 0f, 0f, 0f, 0xB4AFAFAF, 3f,
                null, 0.25f, null);
        float first = Water.riverOffset(look, 1f);
        float second = Water.riverOffset(look, 2f);
        assertEquals(0.75f, first, 1e-5f, "a quarter of its length back a second");
        assertEquals(0.5f, second, 1e-5f, "and a quarter more the next: what it shows runs on down");
    }
}
