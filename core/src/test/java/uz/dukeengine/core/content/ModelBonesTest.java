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

    // ---- where it is drawn: a clip's last frame, a turret, and no case ----

    private static final String ANTENNA = "models/bones/antenna.gltf";
    private static final String TANK = "models/bones/turret.gltf";

    /** An orbital cannon's FXMain, -10.33 high in the default pose and 23.97 at the last frame of its raising clip. */
    @Test
    void aBoneReadAtAClipsLastFrameIsWhereTheFilePutsItThen() {
        assertNear(new Coord3D(0f, -10.33f, 0f), ModelBones.of(ANTENNA, "FXMain"));
        assertNear(new Coord3D(0f, 23.97f, 0f), ModelBones.of(ANTENNA, "FXMain", "Raise", null, 0f));
        assertNear(new Coord3D(4f, 0f, 0f), ModelBones.of(ANTENNA, "Dish", "Raise", null, 0f), "a bone it leaves");
        assertNear(new Coord3D(0f, -10.33f, 0f), ModelBones.of(ANTENNA, "FXMain", "NoSuchClip", null, 0f));
    }

    /** A Crusader's launch bone, 8.8 ahead of its turret's pivot: turned a quarter round, a quarter round the pivot. */
    @Test
    void aBoneOnATurretTurnedAQuarterRoundIsAQuarterRoundThePivot() {
        assertNear(new Coord3D(8.8f, 5f, 0f), ModelBones.of(TANK, "TURRETMS01", null, "Turret", 0f));
        // A thing's turn runs from its forward, the file's x, toward the ground's other way, the file's z.
        assertNear(new Coord3D(0f, 5f, 8.8f), ModelBones.of(TANK, "TURRETMS01", null, "Turret", (float) Math.PI / 2f));
        assertNear(new Coord3D(0f, 5f, 0f), ModelBones.of(TANK, "Turret", null, "Turret", (float) Math.PI / 2f),
                "the pivot stays where it is");
    }

    @Test
    void aBoneAskedForInLowerCaseIsFoundWhereTheFileWritesItInCapitals() {
        assertNear(new Coord3D(8.8f, 5f, 0f), ModelBones.of(TANK, "turretms01"));
        assertNear(new Coord3D(1f, 10f, 20f), ModelBones.of(BARRACKS, "ExitStart"));
    }

    private static void assertNear(Coord3D expected, Coord3D actual, String message) {
        assertEquals(expected.x(), actual.x(), 1e-4f, message);
        assertEquals(expected.y(), actual.y(), 1e-4f, message);
        assertEquals(expected.z(), actual.z(), 1e-4f, message);
    }
}
