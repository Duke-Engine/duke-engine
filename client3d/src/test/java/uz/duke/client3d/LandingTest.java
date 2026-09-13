package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.duke.game.view.UnitView;

/**
 * A shot lands when it is gone, because nothing else says so.
 *
 * <p>The fault this guards cost the fireball and the meteor their bursts in the game
 * while every test and a staged demo drew them: the landing was hung on
 * {@code ObjectDied}, and a thing with no body never posts one.
 */
class LandingTest {

    private static final int HIS = 0;
    private static final int THEIRS = 1;

    private static UnitView shot(int player) {
        return new UnitView(41, "MageFireball", player, 10f, 20f, 0f, 0f, 0f,
                false, false, true, false, -1);
    }

    @Test
    void hisOwnShotThatIsGoneHasLanded() {
        assertTrue(Landing.arrived(shot(HIS), HIS, false),
                "his own are never hidden from him, so gone can only mean it came down");
    }

    @Test
    void theirShotLandedOnlyIfItWasInSight() {
        assertTrue(Landing.arrived(shot(THEIRS), HIS, true));
        assertFalse(Landing.arrived(shot(THEIRS), HIS, false),
                "one that flew into the dark did not land in the light");
    }

    // ---- where it bursts, if anywhere ----

    /** His fireball, flown from the origin to where it was last drawn. */
    private static Landing.Gone flewTo(float lastX, float lastZ) {
        return new Landing.Gone(41, "MageFireball", true, 0f, 0f, 0f, lastX, lastZ, 0.4f);
    }

    @Test
    void aShotThatStruckSomebodyBurstsOnHim() {
        var blows = List.of(new Landing.Blow(70f, -10f, false), new Landing.Blow(58f, 3f, false));

        var at = Landing.burstAt(flewTo(50f, 0f), blows, 30f);

        assertNotNull(at);
        assertEquals(58f, at.x, 0.001f, "on the one it struck, where its blast is centred");
        assertEquals(3f, at.z, 0.001f);
    }

    @Test
    void aShotThatStruckNobodyDoesNotBurst() {
        assertNull(Landing.burstAt(flewTo(50f, 0f), List.of(), 30f),
                "a wall hurts nobody, and nothing bursts");
        assertNull(Landing.burstAt(flewTo(50f, 0f), List.of(new Landing.Blow(150f, 0f, false)), 30f),
                "a blow across the room was somebody else's");
        assertNull(Landing.burstAt(flewTo(50f, 0f), List.of(new Landing.Blow(52f, 0f, true)), 30f),
                "and his own fireball does not strike him");
    }

    @Test
    void aMarkThatLayWhereItWasPutLandsThereWhateverIsUnderIt() {
        var mark = new Landing.Gone(58, "MeteorWarning", true, 365f, 110f, 0f, 365f, 110f, 1.5f);

        var at = Landing.burstAt(mark, List.of(), 30f);

        assertNotNull(at, "a meteor comes down whether or not anybody is under it");
        assertEquals(365f, at.x, 0.001f);
        assertEquals(110f, at.z, 0.001f);
    }

    @Test
    void aShotStoppedAtOnceIsNotAMark() {
        var intoTheWall = new Landing.Gone(41, "MageFireball", true, 5f, 0f, 0f, 5f, 0f, 0.07f);

        assertNull(Landing.burstAt(intoTheWall, List.of(), 30f),
                "still, but for a frame: a fireball cast into a wall, not a meteor's mark");
    }

    @Test
    void aCreatureNeverLands() {
        var skeleton = new UnitView(7, "Skeleton", THEIRS, 0f, 0f, 0f, 30f, 60f,
                false, true, false, false, -1);
        assertFalse(Landing.arrived(skeleton, HIS, true), "a creature leaves by dying");
        assertFalse(Landing.arrived(null, HIS, true));
    }
}
