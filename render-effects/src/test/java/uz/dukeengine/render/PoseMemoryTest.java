package uz.dukeengine.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jme3.math.Matrix4f;
import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;

class PoseMemoryTest {

    private static Matrix4f[] at(float x) {
        var matrix = new Matrix4f();
        matrix.setTranslation(new Vector3f(x, 0f, 0f));
        return new Matrix4f[] {matrix};
    }

    private static float x(Matrix4f[] pose) {
        return pose[0].toTranslationVector().x;
    }

    @Test
    void aThingFirstSeenHasNotMoved() {
        assertEquals(1f, x(new PoseMemory().previous("thing", at(1f))));
    }

    @Test
    void aThingSeenLastFrameIsToldWhereItWasThen() {
        var memory = new PoseMemory();
        memory.previous("thing", at(1f));
        memory.endFrame();
        assertEquals(1f, x(memory.previous("thing", at(2f))));
        memory.endFrame();
        assertEquals(2f, x(memory.previous("thing", at(5f))));
    }

    @Test
    void asTheSecondPieceOfASkeletonAsksItHearsTheSameAnswer() {
        var memory = new PoseMemory();
        memory.previous("skeleton", at(1f));
        memory.endFrame();
        memory.previous("skeleton", at(2f));
        assertEquals(1f, x(memory.previous("skeleton", at(2f))));
    }

    @Test
    void aThingOutOfSightForAFrameIsForgotten() {
        var memory = new PoseMemory();
        memory.previous("thing", at(1f));
        memory.endFrame();
        memory.endFrame();
        assertEquals(9f, x(memory.previous("thing", at(9f))));
    }
}
