package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;

class HandoffTest {

    private static final float DT = 1f / 60f;
    private static final float SECONDS = 0.3f;

    private static Transform target(float time) {
        // Walking away at 1.5 m/s, turning at 1 rad/s.
        return new Transform(new Vector3f(1f + 1.5f * time, 1.2f, -2f),
                new Quaternion().fromAngleAxis(time, Vector3f.UNIT_Y));
    }

    @Test
    void itLeavesExactlyFromWhereItWasAndEndsExactlyOnAMovingTarget() {
        var from = new Transform(new Vector3f(-1f, 0.8f, 0.5f), new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_X));
        var handoff = new Handoff();
        handoff.start(from, SECONDS);
        var first = handoff.at(target(0f), 0f);
        assertEquals(0f, first.getTranslation().distance(from.getTranslation()), 1e-6f);
        assertEquals(1f, Math.abs(first.getRotation().dot(from.getRotation())), 1e-6f);

        float time = 0f;
        var last = first;
        float fastest = 0f;
        float turnFastest = 0f;
        for (int i = 0; i < 60; i++) {
            time += DT;
            var now = handoff.at(target(time), DT);
            fastest = Math.max(fastest, now.getTranslation().distance(last.getTranslation()) / DT);
            float dot = Math.min(1f, Math.abs(now.getRotation().dot(last.getRotation())));
            turnFastest = Math.max(turnFastest, 2f * FastMath.acos(dot) / DT);
            last = now;
            if (time > SECONDS + DT) {
                assertFalse(handoff.running());
                assertEquals(0f, now.getTranslation().distance(target(time).getTranslation()), 1e-6f);
            }
        }
        // Smoothstep's top speed is 1.5 times the straight one, plus the target's own.
        float gap = from.getTranslation().distance(target(SECONDS).getTranslation());
        assertTrue(fastest <= 1.5f * gap / SECONDS + 1.5f + 0.01f, "no jump: " + fastest + " m/s");
        assertTrue(turnFastest <= 1.5f * FastMath.PI / SECONDS + 1f, "no snap in the turn: " + turnFastest + " rad/s");
    }

    @Test
    void withNoTimeOrNoStartItIsTheTarget() {
        var handoff = new Handoff();
        assertFalse(handoff.running());
        assertEquals(target(1f).getTranslation(), handoff.at(target(1f), DT).getTranslation());
        handoff.start(new Transform(), 0f);
        assertTrue(handoff.running());
        assertEquals(target(2f).getTranslation(), handoff.at(target(2f), 0f).getTranslation());
        assertFalse(handoff.running());
    }
}
