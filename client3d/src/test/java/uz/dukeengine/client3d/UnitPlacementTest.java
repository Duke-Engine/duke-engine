package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;
import uz.dukeengine.game.view.UnitView;

/** A thing drawn at its own height, turned by its facing, pitch and roll — and a ground unit drawn as it was. */
class UnitPlacementTest {

    private static final WorldMoments.Floor FLAT = (x, z) -> 0f;
    /** A hill 25 high under x 50. */
    private static final WorldMoments.Floor HILL = (x, z) -> x == 50f ? 25f : 0f;

    private static UnitView view(float x, float y, float orientation, float z, float pitch, float roll,
            boolean ownHeight) {
        return new UnitView(1, "Jet", 1, x, y, orientation, 100f, 100f, false, true, true, false, -1, z, pitch, roll,
                ownHeight);
    }

    @Test
    void aThingAHundredUpIsDrawnAHundredUp() {
        assertEquals(new Vector3f(10f, 100f, 20f), UnitPlacement.where(view(10f, 20f, 0f, 100f, 0f, 0f, false), FLAT));
    }

    @Test
    void aThingAtNoHeightOverAHillIsDrawnOnTheHill() {
        assertEquals(25f, UnitPlacement.where(view(50f, 0f, 0f, 0f, 0f, 0f, false), HILL).y, 0f);
        assertEquals(-5f, UnitPlacement.where(view(50f, 0f, 0f, -5f, 0f, 0f, true), HILL).y, 0f,
                "a thing that keeps its own height is drawn there, under the hill if need be");
    }

    @Test
    void pitchTipsItsNoseUpAndRollBanksItToItsRight() {
        var nose = UnitPlacement.turn(view(0f, 0f, 1.2f, 50f, 0.5f, 0f, false)).mult(Vector3f.UNIT_X);
        assertEquals(0.5f, (float) Math.asin(nose.y), 1e-5f, "up by 0.5, whichever way it faces");

        var level = UnitPlacement.turn(view(0f, 0f, 0f, 50f, 0f, 0.4f, false));
        var right = level.mult(Vector3f.UNIT_Z);
        assertEquals(-0.4f, (float) Math.asin(right.y), 1e-5f, "its right side down by 0.4");
        assertEquals(1f, level.mult(Vector3f.UNIT_X).x, 1e-5f, "and its nose where it was");
    }

    @Test
    void aGroundUnitIsDrawnExactlyAsBefore() {
        var old = view(30f, 40f, 0.7f, 0f, 0f, 0f, false);
        var before = new UnitView(1, "Tank", 1, 30f, 40f, 0.7f, 100f, 100f, false, true, false, false, -1);

        assertEquals(new Vector3f(30f, 25f, 40f), UnitPlacement.where(before, (x, z) -> 25f),
                "on the ground under it, as updateUnitNode put it");
        assertEquals(new Quaternion().fromAngles(0f, -0.7f, 0f), UnitPlacement.turn(before), "turned by its facing alone");
        assertEquals(UnitPlacement.turn(before), UnitPlacement.turn(old));
    }
}
