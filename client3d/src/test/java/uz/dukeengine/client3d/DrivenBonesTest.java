package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Quaternion;
import com.jme3.scene.Node;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.view.UnitView;

/** A trailer's hitch swung by the turn rate, straightening when the turn stops — the reference's {@code W3DTruckDraw}. */
class DrivenBonesTest {

    private static final List<Visuals.DrivenBone> HITCH = List.of(new Visuals.DrivenBone("Hitch",
            input -> BoneDrive.Pose.turned(Math.clamp(input.turnRate() * 0.2f, -30f, 30f)), 5));

    private static UnitView facing(float radians) {
        return new UnitView(1, "Truck", 0, 0f, 0f, radians, 100f, 100f, false, true, true, false, -1);
    }

    @Test
    void aBoneSwingsWhileItTurnsAndStraightensAfter() {
        var model = new Node("truck");
        var hitch = new Node("Hitch");
        model.attachChild(hitch);
        var bones = new DrivenBones();
        float now = 0f;
        float facing = 0f;
        for (int frame = 0; frame < 30; frame++) {
            facing += 0.05f; // about 86 degrees a second
            now += 1f / 30f;
            bones.drive(1, model, HITCH, facing(facing), now);
        }
        assertTrue(Math.abs(hitch.getLocalRotation().getY()) > 0.1f, "swung out");
        for (int frame = 0; frame < 60; frame++) {
            now += 1f / 30f;
            bones.drive(1, model, HITCH, facing(facing), now);
        }
        assertEquals(0f, hitch.getLocalRotation().getY(), 1e-3f, "straight again");

        var plain = new Node("box");
        var other = new Node("Body");
        plain.attachChild(other);
        bones.drive(2, plain, HITCH, facing(1f), now);
        bones.drive(2, plain, HITCH, facing(2f), now + 0.1f);
        assertEquals(Quaternion.IDENTITY, other.getLocalRotation(), "no such bone: as the file has it");
    }
}
