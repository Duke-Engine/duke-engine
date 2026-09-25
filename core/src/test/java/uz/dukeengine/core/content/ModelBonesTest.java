package uz.dukeengine.core.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;

/** A model file's bones where the file puts them: down the scene, each node's translation, rotation and scale. */
class ModelBonesTest {

    private static final String BARRACKS = "models/bones/barracks.gltf";

    private static void assertNear(Coord3D expected, Coord3D actual) {
        assertEquals(expected.x(), actual.x(), 1e-5f);
        assertEquals(expected.y(), actual.y(), 1e-5f);
        assertEquals(expected.z(), actual.z(), 1e-5f);
    }

    @Test
    void aBoneIsWhereTheFilePutsItThroughItsParents() {
        assertNear(new Coord3D(1f, 10f, 20f), ModelBones.of(BARRACKS, "EXITSTART"));
        assertNear(new Coord3D(1f, 0f, -6f), ModelBones.of(BARRACKS, "FIRE01"));
    }

    @Test
    void aMissingBoneOrFileSaysSo() {
        assertNull(ModelBones.of(BARRACKS, "DOCKACTION"));
        assertNull(ModelBones.of("models/bones/nowhere.glb", "EXITSTART"));
    }

    @Test
    void theBinaryFormReadsTheSame() throws Exception {
        var json = """
                {"asset":{"version":"2.0"},"nodes":[{"name":"Root","translation":[1,0,0],"scale":[2,2,2],\
                "children":[1]},{"name":"EXITSTART","translation":[0,5,10]}]}""".getBytes(StandardCharsets.UTF_8);
        int padded = (json.length + 3) / 4 * 4;
        var glb = ByteBuffer.allocate(20 + padded).order(ByteOrder.LITTLE_ENDIAN);
        glb.putInt(0x46546C67).putInt(2).putInt(20 + padded).putInt(padded).putInt(0x4E4F534A).put(json);
        while (glb.position() < glb.capacity()) {
            glb.put((byte) ' ');
        }
        var out = new ByteArrayOutputStream();
        out.write(glb.array());

        assertNear(new Coord3D(1f, 10f, 20f), ModelBones.bones(out.toByteArray()).get("EXITSTART"));
    }
}
