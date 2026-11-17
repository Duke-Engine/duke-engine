package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;

class TwoBoneIkTest {

    private static final Vector3f SHOULDER = new Vector3f(0.2f, -0.2f, 0.1f);
    private static final float UPPER = 0.3f;
    private static final float LOWER = 0.27f;
    /** Below and out to the side: the elbow bends down and out. */
    private static final Vector3f POLE = new Vector3f(0.6f, -1f, 0.1f);

    @Test
    void aTargetInReachIsTouchedWithTheBonesKeptAndTheElbowTowardsThePole() {
        var target = new Vector3f(0.25f, -0.3f, -0.35f);

        var pose = TwoBoneIk.solve(SHOULDER, UPPER, LOWER, target, POLE);

        assertEquals(0f, pose.wrist().distance(target), 0.001f);
        assertEquals(UPPER, pose.elbow().distance(SHOULDER), 1e-4f);
        assertEquals(LOWER, pose.wrist().distance(pose.elbow()), 1e-4f);
        var along = target.subtract(SHOULDER).normalizeLocal();
        var toPole = POLE.subtract(SHOULDER);
        var bend = toPole.subtract(along.mult(toPole.dot(along)));
        assertTrue(pose.elbow().subtract(SHOULDER).dot(bend) > 0f);
    }

    @Test
    void theBonesTurnFromPlusYOntoTheirPlaces() {
        var pose = TwoBoneIk.solve(SHOULDER, UPPER, LOWER, new Vector3f(0.25f, -0.3f, -0.35f), POLE);

        var upper = pose.upper().mult(Vector3f.UNIT_Y).mult(UPPER);
        assertEquals(0f, SHOULDER.add(upper).distance(pose.elbow()), 1e-4f);
        var lower = pose.lower().mult(Vector3f.UNIT_Y).mult(LOWER);
        assertEquals(0f, pose.elbow().add(lower).distance(pose.wrist()), 1e-4f);
    }

    @Test
    void outOfReachTheArmIsStraightAndFacesTheTarget() {
        var target = SHOULDER.add(0f, 0f, -2f);

        var pose = TwoBoneIk.solve(SHOULDER, UPPER, LOWER, target, POLE);

        var along = new Vector3f(0f, 0f, -1f);
        assertEquals(0f, pose.elbow().distance(SHOULDER.add(along.mult(UPPER))), 1e-4f);
        assertEquals(0f, pose.wrist().distance(SHOULDER.add(along.mult(UPPER + LOWER))), 1e-4f);
    }

    @Test
    void aTargetOnTheShoulderGivesNoNaN() {
        var pose = TwoBoneIk.solve(SHOULDER, UPPER, LOWER, SHOULDER.clone(), SHOULDER.clone());

        for (var v : new Vector3f[] {pose.elbow(), pose.wrist()}) {
            assertFalse(Float.isNaN(v.x) || Float.isNaN(v.y) || Float.isNaN(v.z), v.toString());
        }
        assertFalse(Float.isNaN(pose.upper().getW()) || Float.isNaN(pose.lower().getW()));
        assertEquals(UPPER, pose.elbow().distance(SHOULDER), 1e-4f);
    }
}
