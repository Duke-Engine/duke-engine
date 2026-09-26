package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Vector3f;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A ground light's sun at its own direction, level with the horizon or below it ({@code doTheLight}). */
class LowGroundSunTest {

    /** White light from one sun alone, coming from yaw 0 — travelling toward +z. */
    private static Visuals.GroundLight lit(float pitch) {
        return new Visuals.GroundLight(0x000000, List.of(new Visuals.Sun(pitch, 0f, 0xFFFFFF)));
    }

    @Test
    void aSunOnTheHorizonAddsNothingToFlatGroundAndLightsASlopeFacingIt() {
        var level = lit(0f);
        assertEquals(0f, level.at(Vector3f.UNIT_Y).r, 1e-6f, "flat ground: nothing");
        var facing = new Vector3f(0f, 1f, -1f).normalizeLocal();
        assertEquals(0.7071f, level.at(facing).r, 1e-3f, "a slope facing it: by the cosine");
    }

    @Test
    void aSunThirtyBelowLightsOnlyWhatIsTurnedDownTowardIt() {
        var below = lit(-30f);
        assertEquals(0f, below.at(Vector3f.UNIT_Y).r, 1e-6f, "flat ground: nothing");
        var turnedDown = new Vector3f(0f, -0.5f, -0.866f).normalizeLocal();
        assertEquals(1f, below.at(turnedDown).r, 1e-3f, "a face turned down toward it: all of it");
        assertEquals(0f, below.at(new Vector3f(0f, -1f, 1f).normalizeLocal()).r, 1e-6f, "one turned away: none");
    }

    @Test
    void aPitchInFractionsOfADegreeIsKept() {
        assertEquals(12.5f, new Visuals.Sun(12.5f, 0f, 0xFFFFFF).pitch());
        assertEquals(-90f, new Visuals.Sun(-120f, 0f, 0xFFFFFF).pitch());
        assertTrue(new Visuals.Sun(-90f, 0f, 0xFFFFFF).direction().y > 0.99f, "straight up from below");
    }
}
