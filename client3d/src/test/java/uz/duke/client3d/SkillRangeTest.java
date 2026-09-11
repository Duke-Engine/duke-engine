package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.core.math.Coord3D;

/**
 * Where a skill really goes when the player points past the edge of its ring.
 *
 * <p>The rule is small and the two ways of getting it wrong are both bad. Refuse
 * the click, and a ring becomes a fence the player has to aim inside of — which
 * is a skill he fumbles under pressure. Obey it literally, and the ring is a lie
 * and the cooldown is spent on a blast that landed short of where he pointed.
 * Pulling the point back to the edge is what he meant every time.
 *
 * <p>Written out here rather than trusted to the drawing, because the simulation
 * clamps the same click again on its own side — a command may arrive from a
 * machine running a different version, or none — and the two have to agree
 * exactly or the picture and the damage part company.
 */
class SkillRangeTest {

    private static final SkillRange SHOT =
            new SkillRange('Q', SkillRange.Shape.AT_A_SPOT, 60f, 12f);

    private static Coord3D at(float x, float y) {
        return new Coord3D(x, y, 0f);
    }

    @Test
    void aClickInsideTheRingIsLeftExactlyWhereItWas() {
        var wanted = at(130f, 100f); // 30 from a hero at 100,100

        assertSame(wanted, SHOT.within(at(100f, 100f), wanted),
                "a click he can reach should keep the exact spot he picked");
        assertTrue(SHOT.inReach(at(100f, 100f), wanted));
    }

    @Test
    void aClickPastTheEdgeIsPulledBackToIt() {
        var hero = at(100f, 100f);

        var landed = SHOT.within(hero, at(400f, 100f));

        assertEquals(60f, hero.distance(landed), 0.01f, "it lands on the ring, not beyond it");
        assertEquals(100f, landed.y(), 0.001f, "and in the direction he pointed");
        assertFalse(SHOT.inReach(hero, at(400f, 100f)), "which the ring should have said");
    }

    /** Pulled back along the line he pointed, not to the nearest axis. */
    @Test
    void itKeepsTheDirectionHePointed() {
        var hero = at(0f, 0f);

        var landed = SHOT.within(hero, at(300f, 400f)); // 3-4-5, so 500 out

        assertEquals(36f, landed.x(), 0.01f, "three fifths of the reach");
        assertEquals(48f, landed.y(), 0.01f, "and four fifths of it");
    }

    /** A spot on an upper floor stays on that floor. */
    @Test
    void theHeightIsTheSpotsAndNotHis() {
        var landed = SHOT.within(at(0f, 0f), new Coord3D(300f, 0f, 40f));

        assertEquals(40f, landed.z(), 0.001f, "a blast aimed upstairs lands upstairs");
    }

    @Test
    void pointingAtHisOwnFeetIsNotADivisionByZero() {
        var hero = at(50f, 50f);

        var landed = SHOT.within(hero, at(50f, 50f));

        assertEquals(50f, landed.x(), 0.001f);
        assertEquals(50f, landed.y(), 0.001f);
    }

    /**
     * Which shapes wait for a click, and which are held.
     *
     * <p>The split the whole input change rests on: a skill the player has to
     * point at waits for his click, and one that goes off around him is drawn
     * while the key is down and cast when he lets go. Getting a shape onto the
     * wrong side of this makes a skill either uncastable or uncancellable.
     */
    @Test
    void onlyTheShapesWithNothingToPointAtAreHeld() {
        for (var shape : SkillRange.Shape.values()) {
            var range = new SkillRange('Q', shape, 10f, 2f);
            assertEquals(!range.needsAiming(), range.castOnRelease(),
                    shape + " should either be aimed or be held, and never both or neither");
        }
        assertTrue(new SkillRange('Q', SkillRange.Shape.AT_A_CREATURE, 1f, 0f).needsAiming());
        assertTrue(new SkillRange('Q', SkillRange.Shape.AT_A_SPOT, 1f, 0f).needsAiming());
        assertTrue(new SkillRange('Q', SkillRange.Shape.DOWN_A_LANE, 1f, 0f).needsAiming());
        assertTrue(new SkillRange('Q', SkillRange.Shape.AROUND_HIM, 1f, 0f).castOnRelease());
        assertTrue(new SkillRange('Q', SkillRange.Shape.ON_HIMSELF, 1f, 0f).castOnRelease());
    }
}
