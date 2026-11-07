package uz.dukeengine.client3d.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The camera's chase: smooth to start, a steady trail while he runs, no overshoot when he stops. */
class SpringTest {

    private static final float FRAME = 1f / 144f;
    private static final float SMOOTH = 0.25f;

    @Test
    void aRunningHeroIsTrailedByHisSpeedTimesTheSmoothTime() {
        var spring = new Spring();
        float camera = 0f;
        float hero = 0f;
        for (int frame = 0; frame < 144 * 3; frame++) {
            hero += 30f * FRAME;
            camera = spring.after(camera, hero, SMOOTH, FRAME);
        }
        assertEquals(30f * SMOOTH, hero - camera, 0.3f);
    }

    @Test
    void itGathersPaceInsteadOfJumpingToIt() {
        var spring = new Spring();
        float camera = 0f;
        float hero = 0f;
        float lastStep = 0f;
        for (int frame = 0; frame < 144; frame++) {
            hero += 30f * FRAME;
            float next = spring.after(camera, hero, SMOOTH, FRAME);
            float step = next - camera;
            assertTrue(step >= lastStep - 1e-5f, "its pace only grows while his is steady");
            assertTrue(step - lastStep < 30f * FRAME * 0.2f, "never a jolt of pace: " + (step - lastStep));
            lastStep = step;
            camera = next;
        }
    }

    @Test
    void itSettlesOnAStoppedHeroWithoutGoingPast() {
        var spring = new Spring();
        float camera = 0f;
        for (int frame = 0; frame < 144 * 3; frame++) {
            camera = spring.after(camera, 10f, SMOOTH, frame % 2 == 0 ? FRAME : 1f / 20f); // uneven frames too
            assertTrue(camera <= 10f + 1e-4f, "never past him: " + camera);
        }
        assertEquals(10f, camera, 1e-3f);
    }

    @Test
    void noSmoothingIsAtOnce() {
        assertEquals(5f, new Spring().after(0f, 5f, 0f, FRAME), 0f);
    }
}
