package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Quad;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.view.UnitView;

/** Treads that run and wheels that roll and steer as a vehicle moves, worked out from where it stands each frame. */
class RunningGearTest {

    private static final float RATE = 3f; // lengths of the tread's picture a second
    private static final float RUN = RATE / 30f; // a frame's worth

    /** A tank as the converter names one: a bone a tread, its mesh under it, one quad of picture each. */
    private static Node tank() {
        var tank = new Node("AVPALADIN");
        for (var tread : List.of("TREADSL01", "TREADSR01")) {
            var bone = new Node(tread);
            bone.attachChild(new Geometry("AVPALADIN." + tread, new Quad(1f, 1f)));
            tank.attachChild(bone);
        }
        return tank;
    }

    private static Visuals.UnitVisual tankLook() {
        return Visuals.create().unit("Paladin", look -> look.model("models/avpaladin.glb")
                .treads("TreadsL", "TreadsR", RATE, 0.3f, 0.6f)).of("Paladin");
    }

    /** A truck: two front tyres that steer and two behind, drawn facing its z, as a model may be. */
    private static Node truck() {
        var truck = new Node("AVHUMMER");
        for (var tyre : List.of("TIRE01", "TIRE02", "TIRE03", "TIRE04")) {
            truck.attachChild(new Node(tyre));
        }
        truck.setLocalRotation(new Quaternion().fromAngles(0f, FastMath.HALF_PI, 0f)); // its facing, as buildBody's
        return truck;
    }

    private static Visuals.UnitVisual truckLook() {
        return Visuals.create().unit("Humvee", look -> look.model("models/avhummer.glb")
                .wheels(List.of("Tire03", "Tire04"), 1f, List.of("Tire01", "Tire02"), 30f)).of("Humvee");
    }

    private static UnitView at(float x, float y, float orientation) {
        return new UnitView(1, "Vehicle", 0, x, y, orientation, 100f, 100f, false, true, true, false, 0);
    }

    /** How far a tread's picture has run: its second corner's u, less the one it was made with. */
    private static float run(Node model, String tread) {
        var mesh = ((Geometry) ((Node) model.getChild(tread)).getChild(0)).getMesh();
        return mesh.getFloatBuffer(VertexBuffer.Type.TexCoord).get(2) - 1f;
    }

    /** Equal as a picture's run is: round a loop of one, where 0.99999994 is 0. */
    private static void assertRun(float expected, float actual, String message) {
        float apart = actual - expected;
        assertEquals(0f, apart - Math.round(apart), 1e-5f, message);
    }

    /** Where a direction in a tyre's own frame points in the frame the model hangs in. */
    private static Vector3f inModel(Node model, String tyre, Vector3f direction) {
        return model.getLocalRotation().mult(model.getChild(tyre).getLocalRotation().mult(direction));
    }

    @Test
    void aTankDrivenStraightRunsBothTreadsWithItAndTheyStandWhenItStandsOrCreeps() {
        var gear = new RunningGear();
        var model = tank();
        gear.dress(1, model, tankLook());
        gear.see(1, at(0f, 0f, 0f), 10);
        gear.see(1, at(0.5f, 0f, 0f), 11);
        assertRun(1f - RUN, run(model, "TREADSL01"), "a frame at full speed runs a frame's worth");
        assertRun(1f - RUN, run(model, "TREADSR01"), "both the same way");

        gear.see(1, at(0.75f, 0f, 0f), 12); // half its speed: over the drive fraction, still the whole rate
        assertRun(1f - 2 * RUN, run(model, "TREADSL01"), "half its speed, over the drive fraction: the whole rate");
        gear.see(1, at(0.75f, 0f, 0f), 13);
        gear.see(1, at(0.8f, 0f, 0f), 14); // a tenth of it: under the drive fraction, as the reference's slowing
        assertRun(1f - 2 * RUN, run(model, "TREADSL01"), "standing and creeping, the treads stand");
        assertRun(1f - 2 * RUN, run(model, "TREADSR01"), "the right stands too");
        gear.see(1, at(1.8f, 0f, 0f), 14);
        assertRun(1f - 2 * RUN, run(model, "TREADSL01"), "nothing without a new frame of the game");
    }

    @Test
    void aTankTurningInPlaceRunsItsTreadsOppositeWaysAndASwappedModelWearsThemAsTheyStood() {
        var gear = new RunningGear();
        var model = tank();
        var another = (Node) model.clone(); // another tank drawn from the file: the same meshes
        gear.dress(1, model, tankLook());
        gear.see(1, at(0f, 0f, 0f), 10);
        gear.see(1, at(0f, 0f, 0.1f), 11); // a growing facing: to its right
        assertRun(1f - RUN, run(model, "TREADSL01"), "turning right, the left runs forward");
        assertRun(RUN, run(model, "TREADSR01"), "and the right back");
        gear.see(1, at(0f, 0f, 0f), 12);
        assertRun(0f, run(model, "TREADSL01"), "turning back left, each runs back");

        var damaged = tank();
        gear.see(1, at(0f, 0f, 0.1f), 13);
        gear.dress(1, damaged, tankLook());
        assertRun(1f - RUN, run(damaged, "TREADSL01"), "the damaged model's treads stand where they ran");
        assertRun(0f, run(another, "TREADSL01"), "another tank's treads are its own");
    }

    @Test
    void aTrucksTyresTurnAFullCircleAfterTwoPiRadiiOfTravelTheTopGoingForward() {
        var gear = new RunningGear();
        var model = truck();
        gear.dress(1, model, truckLook());
        gear.see(1, at(0f, 0f, 0f), 0);
        for (int frame = 1; frame <= 10; frame++) {
            gear.see(1, at(FastMath.HALF_PI * frame / 10f, 0f, 0f), frame); // a quarter of 2π × radius 1
        }
        var top = inModel(model, "TIRE03", Vector3f.UNIT_Y);
        assertEquals(1f, top.x, 1e-4f, "a quarter circle: the top of the tyre has come round to the front");
        assertEquals(0f, top.y, 1e-4f);

        for (int frame = 11; frame <= 40; frame++) {
            gear.see(1, at(FastMath.TWO_PI * frame / 40f, 0f, 0f), frame);
        }
        top = inModel(model, "TIRE04", Vector3f.UNIT_Y);
        assertEquals(0f, top.x, 1e-4f, "a whole circle: the top is on top again");
        assertEquals(1f, top.y, 1e-4f);
    }

    @Test
    void aTrucksFrontTyresLeanIntoATurnAndTheRearOnesDoNot() {
        var gear = new RunningGear();
        var model = truck();
        gear.dress(1, model, truckLook());
        float x = 0f;
        float y = 0f;
        float facing = 0f;
        gear.see(1, at(x, y, facing), 0);
        for (int frame = 1; frame <= 30; frame++) {
            facing += 0.05f; // to its right
            x += 0.5f * FastMath.cos(facing);
            y += 0.5f * FastMath.sin(facing);
            gear.see(1, at(x, y, facing), frame);
        }
        var side = new Vector3f(0f, 0f, -1f); // the truck's left, which a tyre's axle points along at rest
        var frontAxle = inModel(model, "TIRE01", model.getLocalRotation().inverse().mult(side));
        var rearAxle = inModel(model, "TIRE03", model.getLocalRotation().inverse().mult(side));
        assertTrue(frontAxle.x > 0.4f, "the front tyre's left end has come forward: it faces right, " + frontAxle);
        assertTrue(frontAxle.x < FastMath.sin(FastMath.DEG_TO_RAD * 30f) + 1e-4f, "no further than it steers");
        assertEquals(0f, rearAxle.x, 1e-4f, "a rear tyre only rolls");
    }

    /** A truck whose four tyres stand at its corners, 3 up in its frame, and the game's corners as given. */
    private static UnitView sprung(float x, float orientation, uz.dukeengine.core.thing.Corners corners) {
        return new UnitView(1, "Vehicle", 0, x, 0f, orientation, 100f, 100f, false, true, true, false, 0, 0f, 0f, 0f,
                false, 0, List.of(), List.of(), 1f, -1, true, null, true, null, 0, 1f, false, 0f, false,
                uz.dukeengine.core.view.Turrets.NONE, 0f, 0f, corners);
    }

    @Test
    void aFrontLeftCornerSetTo2BelowDrawsThatTyre2LowerStillRollingAndSteeringAndTheRestAsTheModelHasThem() {
        var truck = truck();
        for (var tyre : List.of("TIRE01", "TIRE02", "TIRE03", "TIRE04")) {
            truck.getChild(tyre).setLocalTranslation(1f, 3f, 2f);
        }
        var look = Visuals.create().unit("Humvee", l -> l.model("models/avhummer.glb")
                .wheels(List.of("Tire03", "Tire04"), 1f, List.of("Tire01", "Tire02"), 30f)
                .wheelCorner("Tire01", Visuals.Corner.FRONT_LEFT).wheelCorner("Tire02", Visuals.Corner.FRONT_RIGHT)
                .wheelCorner("Tire03", Visuals.Corner.BACK_LEFT).wheelCorner("Tire04", Visuals.Corner.BACK_RIGHT))
                .of("Humvee");
        var gear = new RunningGear();
        gear.dress(1, truck, look);
        var level = uz.dukeengine.core.thing.Corners.LEVEL;
        gear.see(1, sprung(0f, 0f, level), 0);
        gear.see(1, sprung(2f, 0.2f, new uz.dukeengine.core.thing.Corners(-2f, 0f, 0f, 0f)), 1);

        var frontLeft = truck.getChild("TIRE01");
        assertEquals(new Vector3f(1f, 1f, 2f), frontLeft.getLocalTranslation(), "2 lower in the model's frame");
        assertTrue(!frontLeft.getLocalRotation().equals(new Quaternion()), "still rolling and steering");
        for (var tyre : List.of("TIRE02", "TIRE03", "TIRE04")) {
            assertEquals(new Vector3f(1f, 3f, 2f), truck.getChild(tyre).getLocalTranslation(), tyre + " as it stands");
        }
    }
}
