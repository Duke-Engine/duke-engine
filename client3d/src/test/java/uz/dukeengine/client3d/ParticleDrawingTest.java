package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.content.ParticleSystem;
import uz.dukeengine.core.data.Binder;
import uz.dukeengine.core.data.DukeText;

/**
 * What a particle is drawn as, worked out without a GPU: where the Z-up frame lands in this client's, a square
 * that faces the camera or lies flat, the reference's turn in 256ths, and a streak joined in birth order.
 */
class ParticleDrawingTest {

    /** The models' quarter turn about x: up the system's z is up the client's y, and its y runs into -z. */
    @Test
    void aSystemsZUpFrameIsTurnedAsTheModelsAre() {
        assertEquals(new Vector3f(1f, 3f, -2f), ParticleDrawing.toClient(1f, 2f, 3f));

        var there = new Vector3f(10f, 4f, -7f);
        var placed = LayeredEffects.placementAt(there, 0.5f);
        assertEquals(there, ParticleDrawing.toClient(placed.x(), placed.y(), placed.z()), "and back again");
        assertEquals(0.5f, placed.turn(), "a turn about the client's up is the same turn about the system's");
    }

    /** A byte of 255ths of a turn, drawn as 256ths: a step of about 1.4 degrees, truncated. */
    @Test
    void aParticlesTurnIsDrawnInTheReferencesSteps() {
        assertEquals(0f, ParticleDrawing.quantised(0f));
        assertEquals(63 * FastMath.TWO_PI / 256f, ParticleDrawing.quantised(FastMath.HALF_PI), 1e-6f,
                "a quarter turn is step 63 — 63.75, truncated");
        assertEquals(0f, ParticleDrawing.quantised(-0.001f), "a turn just short of none truncates to none");
        assertEquals(216 * FastMath.TWO_PI / 256f, ParticleDrawing.quantised(-1f), 1e-6f,
                "and a turn back of one radian, -40 steps, wraps round as a byte does");
    }

    private static Emitter burning(String block) {
        var systems = Map.of("It", new Binder().bind(DukeText.parse(block, "it.duke").getFirst(), ParticleSystem.class));
        var world = new Particles(systems::get, 1L, Particles.Ground.FLAT, Integer.MAX_VALUE, Integer.MAX_VALUE);
        var emitter = world.startAt("It", new Particles.Placement(0f, 0f, 0f, 0f));
        world.update();
        return emitter;
    }

    /** A square {@code size} on a side, facing the camera: its edges along the camera's left and up. */
    @Test
    void aParticleIsASquareFacingTheCamera() {
        var emitter = burning("""
                ParticleSystem
                  Name = It
                  Blend = ALPHA_TEST
                  Size = [4]
                  BurstCount = [1]
                  BurstDelay = [1000]
                  Lifetime = [100]
                End
                """);
        var left = new Vector3f(-1f, 0f, 0f);
        var up = new Vector3f(0f, 1f, 0f);
        var shape = ParticleDrawing.squares(emitter.particles(), false, left, up);

        assertEquals(4, shape.vertices());
        var p = shape.positions();
        assertEquals(0f, new Vector3f(p[0], p[1], p[2]).distance(new Vector3f(-2f, -2f, 0f)), 1e-6f, "bottom left");
        assertEquals(0f, new Vector3f(p[6], p[7], p[8]).distance(new Vector3f(2f, 2f, 0f)), 1e-6f,
                "top right: four across and up");
        assertEquals(List.of(0, 1, 2, 0, 2, 3), java.util.Arrays.stream(shape.indices()).boxed().toList());
    }

    /** Ground-aligned, it lies flat: every corner at the height it was born at. */
    @Test
    void aGroundAlignedParticleLiesFlat() {
        var emitter = burning("""
                ParticleSystem
                  Name = It
                  Blend = ALPHA_TEST
                  IsGroundAligned = Yes
                  Size = [6]
                  Angle = [0.7]
                  BurstCount = [1]
                  BurstDelay = [1000]
                  Lifetime = [100]
                End
                """);
        var shape = ParticleDrawing.squares(emitter.particles(), true, new Vector3f(-1f, 0f, 0f),
                new Vector3f(0f, 0f, -1f));
        var p = shape.positions();
        for (int corner = 0; corner < 4; corner++) {
            assertEquals(0f, p[corner * 3 + 1], 1e-6f, "corner " + corner + " on the ground");
        }
        float side = new Vector3f(p[0], p[1], p[2]).distance(new Vector3f(p[3], p[4], p[5]));
        assertEquals(6f, side, 1e-4f, "six on a side, turned or not");
    }

    /** A streak joins its particles in the order they were born, and its trailing end is drawn clear. */
    @Test
    void aStreakJoinsItsParticlesInOrder() {
        var systems = Map.of("It", new Binder().bind(DukeText.parse("""
                ParticleSystem
                  Name = It
                  Type = STREAK
                  Blend = ADDITIVE
                  Size = [2]
                  BurstCount = [1]
                  BurstDelay = [0]
                  Lifetime = [100]
                End
                """, "it.duke").getFirst(), ParticleSystem.class));
        var world = new Particles(systems::get, 1L, Particles.Ground.FLAT, Integer.MAX_VALUE, Integer.MAX_VALUE);
        float[] x = {0f};
        var emitter = world.start("It", () -> new Particles.Placement(x[0], 0f, 0f, 0f));
        for (int frame = 0; frame < 3; frame++) {
            world.update();
            x[0] += 10f;
        }
        var shape = ParticleDrawing.streak(emitter.particles(), new Vector3f(10f, 50f, 0f));

        assertEquals(6, shape.vertices(), "two edges a point, three points");
        assertEquals(12, shape.indices().length, "two triangles between each pair");
        var p = shape.positions();
        assertTrue(p[0] < p[6] && p[6] < p[12], "along x in the order they were born");
        assertEquals(0f, shape.colours()[3], "the trailing end drawn clear");
        assertEquals(1f, shape.colours()[3 + 16], "and the rest as they are");
    }
}
