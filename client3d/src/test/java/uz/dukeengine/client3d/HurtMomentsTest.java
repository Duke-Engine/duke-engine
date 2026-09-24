package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.event.ObjectHurt;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.thing.ObjectId;

/** What a blow shows: major or minor by the game's threshold, and no second too soon of the same kind. */
class HurtMomentsTest {

    private static final DamageType SMALL_ARMS = DamageType.of("SMALL_ARMS");

    private static ObjectHurt blow(int frame, DamageType type, float amount) {
        return new ObjectHurt(frame, new ObjectId(7), "Tank", 2, type, amount, new ObjectId(3),
                new Coord3D(10f, 20f, 2f), new Coord3D(0f, 20f, 0f));
    }

    @Test
    void aBlowAtTheThresholdIsMajorAndOneBelowItMinor() {
        var visuals = Visuals.create().hurt("Tank", "SMALL_ARMS", 2f, 0);
        var moments = new HurtMoments(visuals::hurtRule);

        assertEquals("hurt.Tank.small_arms.major", moments.nameFor(blow(1, SMALL_ARMS, 3f)));
        assertEquals("hurt.Tank.small_arms.minor", moments.nameFor(blow(2, SMALL_ARMS, 1f)));
    }

    @Test
    void theSameKindTooSoonShowsOnceAndAnotherKindInBetweenShowsItsOwn() {
        // A throttle of 100 ms is three of the game's frames; blows 50 ms apart are one or two frames apart.
        var visuals = Visuals.create().hurt("Tank", null, 2f, 3);
        var moments = new HurtMoments(visuals::hurtRule);

        assertEquals("hurt.Tank.small_arms.minor", moments.nameFor(blow(10, SMALL_ARMS, 1f)));
        assertEquals("hurt.Tank.explosion.major", moments.nameFor(blow(11, DamageType.EXPLOSION, 5f)),
                "another kind of blow in between shows its own");
        assertNull(moments.nameFor(blow(11, SMALL_ARMS, 1f)), "the same kind 50 ms later is held back");
        assertEquals("hurt.Tank.small_arms.minor", moments.nameFor(blow(13, SMALL_ARMS, 1f)), "and shown again at 100");
    }

    @Test
    void aGameThatGaveNoRuleShowsEveryBlowAsMinor() {
        var moments = new HurtMoments(Visuals.create()::hurtRule);

        assertEquals("hurt.Tank.small_arms.minor", moments.nameFor(blow(1, SMALL_ARMS, 1000f)));
        assertEquals("hurt.Tank.small_arms.minor", moments.nameFor(blow(1, SMALL_ARMS, 1000f)));
    }

    @Test
    void theBlowFacesTheWayItCame() {
        var cue = WorldMoments.hurt(blow(1, SMALL_ARMS, 1f), null, (x, z) -> 0f);

        assertEquals(new com.jme3.math.Vector3f(10f, 2f, 20f), cue.at());
        var forward = cue.turn().mult(com.jme3.math.Vector3f.UNIT_X);
        assertEquals(1f, forward.x, 0.05f, "from the attacker at x 0 towards the tank at x 10");
    }
}
